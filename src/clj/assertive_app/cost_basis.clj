(ns assertive-app.cost-basis
  "What did the goods cost? Answered from the events already recorded.

   A sale's own assertions say what was provided, what was received and
   from whom. They do NOT say what the goods cost -- that figure was
   established earlier, when the goods were acquired or produced. In the
   assertive model this is not a missing field to be guessed at but a
   query over prior events: collects/includes the acquisitions, reports
   a unit cost. The ledger already stores each event's assertions, so
   the cost is recoverable without inventing anything.

   Two ways an item comes to have a cost:

     acquired  receives <n physical>  +  provides/requires <m monetary>
               -> unit cost m/n

     produced  consumes <inputs>  ->  creates <n physical>
               -> unit cost (total cost of the inputs) / n, where each
                  input is valued at ITS unit cost -- cost flows forward
                  through the events that transformed it

   Cost-flow assumption: weighted average across everything recorded to
   date. That is a policy choice, not a fact, which is why it lives in
   one named place. FIFO would collect the same events and report a
   different figure; in this model both are expressible and could
   coexist as competing reports over the same record.

   Returns nil rather than a number when the record does not support an
   answer. A sale of goods never acquired or produced is not priced at
   zero -- it is unpriced, and that is a thing for the student to see."
  (:require [clojure.string :as str]))

(defn- num-or-nil [v]
  (cond (number? v) v
        (string? v) (try (Double/parseDouble (str/trim v)) (catch Exception _ nil))
        :else nil))

(defn- monetary-qty [params]
  (when (= "monetary-unit" (some-> (:unit params) name))
    (num-or-nil (:quantity params))))

(defn- physical [params]
  (when (= "physical-unit" (some-> (:unit params) name))
    (when-let [n (num-or-nil (:quantity params))]
      {:item (some-> (:physical-item params) name) :units n})))

(defn- physicals
  "Every physical flow under an assertion. A transformation consumes more
   than one thing -- a blank shirt AND ink -- so a flow assertion may
   hold a list; a single map is the one-element case."
  [v]
  (keep physical (cond (nil? v) [] (sequential? v) v :else [v])))

(defn acquisition
  "If these assertions record acquiring physical goods for money -- paid
   now (provides) or owed (requires) -- return {:item :units :cost}."
  [assertions]
  (let [{:keys [item units]} (first (physicals (:receives assertions)))
        cost (or (monetary-qty (:provides assertions))
                 (monetary-qty (:requires assertions)))]
    (when (and item units cost (pos? units))
      {:item item :units units :cost cost})))

(defn production
  "If these assertions record a transformation -- consuming inputs to
   create something -- return {:item :units :inputs [{:item :units}]}.
   The output's cost is the cost of its inputs, so it can only be valued
   once those inputs have one."
  [assertions]
  (let [out (first (physicals (:creates assertions)))
        in  (vec (physicals (:consumes assertions)))]
    (when (and out (pos? (:units out)))
      {:item (:item out) :units (:units out) :inputs in})))

(defn- accumulate [basis {:keys [item units cost]}]
  (-> basis
      (update-in [item :units] (fnil + 0) units)
      (update-in [item :cost] (fnil + 0) cost)))

(defn- unit-cost [basis item]
  (let [{:keys [units cost]} (get basis item)]
    (when (and units cost (pos? units)) (/ (double cost) units))))

(defn cost-basis
  "Weighted-average unit cost per item, from a chronological seq of prior
   events' assertion maps.

   Acquisitions establish cost directly. Production carries it forward:
   the created goods are valued at the cost of what was consumed, so a
   printed t-shirt costs what the blank shirt cost. Production whose
   inputs are not yet valued contributes nothing rather than a zero.

   -> {item {:units n :cost n :unit-cost n}}"
  [prior-events]
  (let [{:keys [basis by-event]}
        (reduce (fn [{:keys [basis by-event] :as acc} assertions]
                  (let [id (some-> (:has-identifier assertions) name)
                        note (fn [basis {:keys [item units cost]}]
                               ;; Each priced event is remembered on its
                               ;; own, so a later event can point at it:
                               ;; specific identification, when asked for.
                               (cond-> by-event
                                 id (assoc id {:item item :units units :cost cost
                                               :unit-cost (/ (double cost) units)})))]
                    (if-let [a (acquisition assertions)]
                      {:basis (accumulate basis a) :by-event (note basis a)}
                      (if-let [p (production assertions)]
                        (let [in-cost (reduce
                                        (fn [tot {:keys [item units]}]
                                          (if-let [uc (unit-cost basis item)]
                                            (+ tot (* uc units))
                                            (reduced nil)))
                                        0 (:inputs p))]
                          (if in-cost
                            (let [made {:item (:item p) :units (:units p) :cost in-cost}]
                              {:basis (accumulate basis made) :by-event (note basis made)})
                            acc))
                        acc))))
                {:basis {} :by-event {}} prior-events)]
    (assoc (into {} (for [[item v] basis
                          :let [uc (unit-cost basis item)]
                          :when uc]
                      [item (assoc v :unit-cost uc)]))
           :by-event by-event)))

(defn cost-of
  "Cost of `units` of `item` under this basis, or nil when the record
   does not price it. With `from-event`, the units are the ones that
   event acquired or made and cost what THEY cost -- specific
   identification; without it, the weighted average."
  ([basis item units] (cost-of basis item units nil))
  ([basis item units from-event]
   (when-let [n (num-or-nil units)]
     (let [item (some-> item name)
           ev   (when from-event (get-in basis [:by-event (name from-event)]))]
       (if (and ev (= (:item ev) item))
         (* (:unit-cost ev) n)
         (when-let [uc (get-in basis [item :unit-cost])]
           (* uc n)))))))
