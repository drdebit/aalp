(ns assertive-app.readings
  "What the record says about each event, attached for reports to collect
   and total by.

   The engine stores and matches readings; it does not compute them. These
   are AALP's readings of AALP's record -- the same ones the derivation
   makes when it names an account -- computed once, as a record loads, and
   carried on each event:

     :counterparty-role  who the other party is to the business:
                         :customer, :supplier, :owner, :lender, :borrower.
                         Read from what the event does, and from the
                         promise it makes or keeps -- never from a name.
     :makes / :keeps     the kind of promise the event makes, or keeps
     :consideration      money received or promised for goods provided
     :goods-cost         what the goods provided cost, priced from the
                         batch named, as the derivation prices a sale
     :goods-received-cost what the goods received cost: the money
                         provided for them, or promised
     :batch-paid         whether the batch those goods came out of had
                         been paid for by the end of the record: bought
                         for cash, or bought on credit with the payable
                         kept since. A printed batch is paid for when
                         everything it consumed was.

   A reading the record cannot make is left off, rather than guessed."
  (:require [assertive-app.chain :as chain]
            [assertive-app.cost-basis :as cost]
            [assertive-app.je-derive :as jd]))

(defn- id-of [e] (some-> (:has-identifier e) name))

(defn- flows [v] (cond (nil? v) [] (sequential? v) v :else [v]))

(defn- unit-of [flow] (some-> (:unit flow) name))

(defn- money
  "The money in a flow, when it is money."
  [flow]
  (when (= "monetary-unit" (unit-of flow))
    (let [q (:quantity flow)]
      (cond (number? q) q
            (string? q) (try (Double/parseDouble q) (catch Exception _ nil))))))

(defn- goods-out? [e] (some #(= "physical-unit" (unit-of %)) (flows (:provides e))))
(defn- goods-in? [e]
  (some #(#{"physical-unit" "service-unit" "intellectual-property"} (unit-of %)) (flows (:receives e))))
(defn- ownership? [e]
  (some #(= "ownership-units" (unit-of %)) (concat (flows (:provides e)) (flows (:receives e)))))
(defn- money-out [e] (reduce + 0 (keep money (flows (:provides e)))))
(defn- money-in [e] (reduce + 0 (keep money (flows (:receives e)))))

(defn- kept-event [e by-id]
  (some-> (chain/kept-promise e) chain/promise-event-id by-id))

(defn- role-by-promise [kind]
  (case kind
    (:receivable :advance) :customer
    (:payable :prepaid)    :supplier
    :borrowing             :lender
    :lending               :borrower
    :declared              :owner
    nil))

(defn- counterparty-role
  [e makes keeps]
  (when (:has-counterparty e)
    (or (role-by-promise keeps)
        (role-by-promise makes)
        (cond
          (ownership? e) :owner
          (goods-out? e) :customer
          (goods-in? e)  :supplier
          ;; Money one way and nothing else: the owner's investment or
          ;; draw, as the derivation reads it.
          (and (pos? (money-in e)) (not (:provides e))) :owner
          (and (pos? (money-out e)) (not (:receives e))
               (not-any? e [:is-required-by :is-allowed-by :is-protected-by])) :owner))))

(defn- consideration
  "Money received, or promised to be received, for goods provided."
  [e]
  (when (goods-out? e)
    (let [promised (when (= "receives" (some-> (get-in e [:requires :action]) name))
                     (money (:requires e)))
          total (+ (money-in e) (or promised 0))]
      (when (pos? total) total))))

(defn- physical-in? [e] (some #(= "physical-unit" (unit-of %)) (flows (:receives e))))

(defn- goods-received-cost
  "What the goods received cost: the money provided for them, or
   promised for them. The mirror of consideration."
  [e]
  (when (physical-in? e)
    (let [promised (when (= "provides" (some-> (get-in e [:requires :action]) name))
                     (money (:requires e)))
          total (+ (money-out e) (or promised 0))]
      (when (pos? total) total))))

(defn- goods-cost
  "The cost of the goods provided, as the derivation prices it: from the
   batch named, against the record as it stood."
  [e events basis]
  (when (goods-out? e)
    (let [lines (:lines (jd/derive-je e {} {:events events :cost-basis basis}))
          cogs  (keep #(when (= "Cost of Goods Sold" (:account %)) (:amount %)) lines)]
      (when (and (seq cogs) (every? number? cogs))
        (reduce + cogs)))))

(defn- batch-paid?
  "Had the batch these goods came out of been paid for by the end of the
   record? Bought for cash, or on credit with the payable kept since; a
   batch the business made is paid for when everything it consumed was."
  ([batch-id by-id kept-ids] (batch-paid? batch-id by-id kept-ids #{}))
  ([batch-id by-id kept-ids seen]
   (when-let [b (by-id batch-id)]
     (when-not (contains? seen batch-id)
       (cond
         (seq (flows (:creates b)))
         (let [inputs (keep #(some-> (:from-event %) name) (flows (:consumes b)))]
           (and (seq inputs)
                (every? #(batch-paid? % by-id kept-ids (conj seen batch-id)) inputs)))
         (= :payable (chain/promise-kind-of b)) (contains? kept-ids batch-id)
         (pos? (money-out b)) true
         :else false)))))

(defn readings-of
  "Readings for every event in a record, keyed by event id. `events` are
   the record's assertion maps, each with a :has-identifier, in date
   order."
  [events]
  (let [by-id    (into {} (keep (fn [e] (when-let [i (id-of e)] [i e]))) events)
        kept-ids (into #{} (keep #(some-> (chain/kept-promise %) chain/promise-event-id)) events)
        basis    (cost/cost-basis events)]
    (into {}
          (for [e events
                :let [id    (id-of e)
                      makes (chain/promise-kind-of e)
                      keeps (some-> (kept-event e by-id) chain/promise-kind-of)
                      batch (some #(some-> (:from-event %) name) (flows (:provides e)))
                      r (cond-> {}
                          makes (assoc :makes makes)
                          keeps (assoc :keeps keeps)
                          (counterparty-role e makes keeps)
                          (assoc :counterparty-role (counterparty-role e makes keeps))
                          (consideration e) (assoc :consideration (consideration e))
                          (goods-cost e events basis) (assoc :goods-cost (goods-cost e events basis))
                          (goods-received-cost e) (assoc :goods-received-cost (goods-received-cost e))
                          batch (assoc :batch-paid (boolean (batch-paid? batch by-id kept-ids))))]
                :when id]
            [id r]))))
