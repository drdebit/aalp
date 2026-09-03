(ns assertive-app.chain
  "Where a thing sits in the chain of recorded events.

   Raw materials, work in process and finished goods are not properties
   of an item and are not things a student asserts. They are positions:
   readings of what the record says happened to a thing, resolved when
   the question is asked. The same blank t-shirt is raw material before
   it is consumed and part of a finished good after; nothing about the
   shirt changed, only the chain around it.

   So a student books `consumes 10 blank-tshirts` and
   `creates 10 printed-tshirts`. Nobody books `work in process`. The
   journal entry may well SAY Work in Process -- that is a translation
   into the double-entry vocabulary, the same kind of move as calling a
   printer `Equipment (Fixed Asset)` -- but the assertion record
   underneath contains only the transformation.

   This matters beyond tidiness: a position that is asserted is frozen
   at booking time and cannot be revised by later events, while a
   position that is derived answers correctly at every date. Goods in
   process at year end become finished goods in January without anyone
   restating anything.

   Sibling query to cost-basis, which asks what the same chain says a
   thing cost."
  (:require [clojure.string :as str]))

(defn- physical
  "The item and count of a physical flow, if this is one."
  [params]
  (when (= "physical-unit" (some-> (:unit params) name))
    (when-let [item (some-> (:physical-item params) name)]
      {:item item
       :units (let [q (:quantity params)]
                (cond (number? q) q
                      (string? q) (try (Double/parseDouble (str/trim q))
                                       (catch Exception _ nil))
                      :else nil))})))

(defn- physicals
  "Every physical flow under an assertion. A transformation consumes more
   than one thing -- a blank shirt AND ink -- so a flow assertion may
   hold a list; a single map is the one-element case."
  [v]
  (keep physical (cond (nil? v) [] (sequential? v) v :else [v])))

(defn item-roles
  "What the chain says about each item, as a set of roles.

     :acquired  received from a counterparty
     :consumed  consumed by a transformation
     :created   created by a transformation
     :provided  provided to a counterparty
     :enables   named as what allowed a transformation, or acquired in an
                event asserting that it allows future ones
     :consumable / :producible
                named by an `allows` as what some capability turns into
                what -- a declared position, before anything has happened

   -> {item #{roles}}"
  [events]
  (reduce (fn [acc assertions]
            (let [acc (reduce (fn [acc [assertion-key role]]
                                (reduce (fn [acc {:keys [item]}]
                                          (update acc item (fnil conj #{}) role))
                                        acc
                                        (physicals (get assertions assertion-key))))
                              acc
                              {:receives :acquired
                               :consumes :consumed
                               :creates  :created
                               :provides :provided})
                  ;; A transformation naming what allowed it: the thing
                  ;; named is productive. This is the assertion that makes
                  ;; a printer capital -- it turns blanks into printed
                  ;; shirts without being used up in doing so.
                  acc (if-let [cap (get-in assertions [:is-allowed-by :capacity])]
                        (update acc (name cap) (fnil conj #{}) :enables)
                        acc)
                  ;; The same fact stated forward, at acquisition: this
                  ;; event asserts that what it receives turns one thing
                  ;; into another. The thing received is productive; the
                  ;; thing it consumes is an input; the thing it creates
                  ;; is an output -- three positions from one assertion,
                  ;; which is why the pair is worth asking for and the
                  ;; full recipe is not.
                  allows (:allows assertions)
                  acc (if-let [{:keys [item]} (and allows (physical (:receives assertions)))]
                        (update acc item (fnil conj #{}) :enables)
                        acc)
                  acc (if-let [i (:consumes-item allows)]
                        (update acc (name i) (fnil conj #{}) :consumable)
                        acc)
                  acc (if-let [i (:creates-item allows)]
                        (update acc (name i) (fnil conj #{}) :producible)
                        acc)]
              acc))
          {} events))

(def ^:private kind->position
  "SP's catalogue, used ONLY as a convenience fallback -- see
   inventory-position. Nothing here is a fact the assertions could not
   state for themselves."
  {:raw-material  :raw-materials
   :finished-good :finished-goods
   :equipment     :capital
   :service       :service})

(defn inventory-position
  "The position an item occupies, read only from the record.

   What a thing IS cannot be looked up. It is not a fact about the thing:
   a t-shirt printer bought by a machine reseller is inventory, and the
   printer is identical either way. Only what SP asserted about it
   distinguishes the two, so only the record is consulted.

     :capital          enables a transformation, not consumed by it
     :work-in-process  created by one transformation, consumed by another
     :raw-materials    consumed by a transformation, or named as an input
                       by a capability SP holds
     :finished-goods   created by one, named as an output, or bought and
                       sold on untouched

   nil means the record UNDERSPECIFIES it -- the thing was acquired and
   nothing says what for. That is not a failure to look something up; it
   is the honest answer, and it is where the student has work to do. To
   place a transaction in an account you must first say what the thing
   is, and nil is the system declining to say it for you."
  ;; The three-arity form takes a catalogue and ignores it, so callers
  ;; need not all change at once.
  ([events item _ignored-catalogue] (inventory-position events item))
  ([events item]
   (let [item  (some-> item name)
         roles (get (item-roles events) item)]
     (cond
       ;; Productive: the record shows this thing enabling transformations
       ;; without being consumed by them. That is what capital means, and
       ;; SP had to say it.
       (and (:enables roles) (not (:consumed roles)))  :capital

       ;; Between stages: created by one transformation, consumed by
       ;; another. Only later events can make this true.
       (and (:created roles) (:consumed roles))        :work-in-process

       (:consumed roles)                               :raw-materials

       (:created roles)                                :finished-goods
       (and (:acquired roles) (:provided roles))       :finished-goods

       ;; Named by a capability as what it turns into what: the student
       ;; said the printer consumes blanks, so blanks are inputs before
       ;; any production has run.
       (:producible roles)                             :finished-goods
       (:consumable roles)                             :raw-materials

       :else                                           nil))))

(def position-accounts
  "The double-entry label for each position. A translation, and only
   that: renaming these changes nothing about what was asserted."
  {:raw-materials   "Raw Materials Inventory"
   :work-in-process "Work in Process"
   :finished-goods  "Finished Goods Inventory"
   :capital         "Equipment (Fixed Asset)"
   :service         "Service Cost"})

(defn inventory-account
  "The account an item's movements hit, given the chain and the firm's
   catalogue. nil when neither determines a position."
  ([events item] (inventory-account events item nil))
  ([events item item-kinds]
   (some-> (inventory-position events item item-kinds) position-accounts)))

;; ---------------------------------------------------------------------------
;; What SP actually has
;; ---------------------------------------------------------------------------

(defn on-hand
  "Net units of each item the record leaves SP holding.

     + received from a counterparty
     + created by a transformation
     - consumed by a transformation
     - provided to a counterparty

   -> {item units}. An item nothing has happened to is simply absent,
   which is not the same as zero and reads differently to a student."
  [events]
  ;; A transformation may consume or create more than one thing, and the
  ;; client stores several flows as a vector. Read them all; reading only
  ;; a single map made a two-input production event invisible here while
  ;; cost-basis and item-roles saw it, so the record priced the shirts
  ;; and then said SP had none to sell.
  (reduce (fn [acc assertions]
            (reduce (fn [acc [assertion-key sign]]
                      (reduce (fn [acc {:keys [item units]}]
                                (update acc item (fnil + 0) (* sign (or units 0))))
                              acc
                              (physicals (get assertions assertion-key))))
                    acc
                    {:receives 1 :creates 1 :consumes -1 :provides -1}))
          {} events))

(defn- monetary-qty [params]
  (when (= "monetary-unit" (some-> (:unit params) name))
    (let [q (:quantity params)]
      (cond (number? q) q
            (string? q) (try (Double/parseDouble (str/trim q)) (catch Exception _ nil))
            :else nil))))

(defn cash-on-hand
  "Money in less money out, across the record."
  [events]
  (reduce (fn [total assertions]
            (+ total
               (or (monetary-qty (:receives assertions)) 0)
               (- (or (monetary-qty (:provides assertions)) 0))))
          0 events))

(defn capabilities
  "The transformations the record says SP can perform, and what each one
   rests on.

   A capability is asserted, not configured: an event that receives a
   thing and states what that thing allows -- turns blanks into printed
   shirts -- is what makes production possible afterwards.

   -> [{:enabler item :consumes item :creates item}]"
  [events]
  (vec (keep (fn [assertions]
               (let [{:keys [consumes-item creates-item]} (:allows assertions)
                     enabler (:item (physical (:receives assertions)))]
                 (when (and consumes-item creates-item)
                   {:enabler  enabler
                    :consumes (name consumes-item)
                    :creates  (name creates-item)})))
             events)))

(defn- fmt [n] (if (and (number? n) (== n (long n))) (long n) n))

(defn- provision-problem
  "You can only provide what you have. Present tense only: nested under
   `expects` or `allows` the same flow is about the future and
   constrains nothing."
  [selections events]
  (when-let [{:keys [item units]} (physical (:provides selections))]
    (let [available (get (on-hand events) item 0)
          wanted    (or units 0)]
      (when (> wanted available)
        {:kind :cannot-provide
         :item item :requested wanted :available available
         :message
         (if (<= available 0)
           (str "You have no " item " to provide. Nothing in your record "
                "shows SP acquiring or producing any.")
           (str "You have " (fmt available) " " item " on hand, but this "
                "event provides " (fmt wanted) "."))}))))

(defn- cash-problem
  "You can only provide what you have, and money is a thing you have.

   Only asked of a record that contains events. An empty record does not
   say SP is broke -- it says nothing, and a practice problem standing on
   its own has no business being told the firm cannot afford it."
  [selections events]
  (when (seq events)
    (when-let [wanted (monetary-qty (:provides selections))]
      (let [available (cash-on-hand events)]
        (when (> wanted available)
          {:kind :cannot-pay
           :requested wanted :available available
           :message
           (if (<= available 0)
             (str "SP has no cash to provide. Nothing in your record shows money "
                  "coming in -- where would the " (fmt wanted) " come from?")
             (str "SP has " (fmt available) " on record, but this event provides "
                  (fmt wanted) "."))})))))

(defn- consumption-problem
  "Consuming is taking too: production cannot use up what SP does not
   hold."
  [selections events]
  (let [hand (on-hand events)]
    (some (fn [{:keys [item units]}]
      (let [available (get hand item 0)
            wanted    (or units 0)]
        (when (> wanted available)
        {:kind :cannot-consume
         :item item :requested wanted :available available
         :message
         (if (<= available 0)
           (str "You have no " item " to use. Production consumes materials "
                "SP has acquired; nothing in your record shows any.")
           (str "You have " (fmt available) " " item " on hand, but this "
                "event consumes " (fmt wanted) "."))})))
      (physicals (:consumes selections)))))

(defn- capability-problem
  "A transformation needs something that makes it possible.

   This is the question the record answers with `allows`: SP bought a
   printer AND said what it does. Buying it alone leaves the record
   silent about whether anything can be turned into anything -- which is
   exactly the gap a student should meet, and fill themselves, rather
   than be told about in advance."
  [selections events]
  (let [in  (first (physicals (:consumes selections)))
        out (first (physicals (:creates selections)))]
    (when (and in out)
      (let [caps  (capabilities events)
            held  (on-hand events)
            match (first (filter #(and (= (:consumes %) (:item in))
                                       (= (:creates %) (:item out)))
                                 caps))]
        (cond
          (nil? match)
          {:kind :no-capability
           :consumes (:item in) :creates (:item out)
           :message
           (if (seq caps)
             (str "Nothing in your record says SP can turn " (:item in) " into "
                  (:item out) ". What SP can do: "
                  (str/join "; " (map #(str (:consumes %) " into " (:creates %)) caps))
                  ".")
             (str "Nothing in your record says SP can turn " (:item in) " into "
                  (:item out) ". What did SP acquire that makes this possible, "
                  "and what did you say it allows?"))}

          ;; The capability was asserted, but SP no longer holds the thing
          ;; it rested on -- sold, or consumed by something else.
          (and (:enabler match) (<= (get held (:enabler match) 0) 0))
          {:kind :capability-not-held
           :enabler (:enabler match)
           :message (str "SP no longer holds the " (:enabler match)
                         " that made this possible.")})))))

(defn unsupported
  "Everything the record cannot bear about this event.

   Kept apart from the journal-entry derivation on purpose: that stays a
   faithful reading of whatever the student asserted, wrong or right,
   while this says whether the record supports the assertions at all.

   -> [] when the record bears the event out."
  [selections events]
  (vec (keep #(% selections events)
             [provision-problem cash-problem consumption-problem capability-problem])))

(defn unsupported-provision
  "Back-compat single-problem view: the first thing the record cannot
   bear, or nil."
  [selections events]
  (first (unsupported selections events)))

;; ---------------------------------------------------------------------------
;; Why a thing is what it is
;; ---------------------------------------------------------------------------

(defn item-role-events
  "Which events gave each item each of its roles.

   item-roles answers WHAT the record says about a thing; this answers
   WHERE it says it. The difference matters because the answer is usually
   somewhere else: blank shirts are an input because of something said
   when the printer was bought, and a student looking at the purchase of
   the shirts has no way to see that.

   -> {item {role [event ...]}}"
  [events]
  (reduce
    (fn [acc assertions]
      (let [add (fn [acc item role]
                  (update-in acc [item role] (fnil conj []) assertions))
            acc (reduce (fn [acc [assertion-key role]]
                          (reduce (fn [acc {:keys [item]}] (add acc item role))
                                  acc (physicals (get assertions assertion-key))))
                        acc
                        {:receives :acquired :consumes :consumed
                         :creates  :created  :provides :provided})
            acc (if-let [cap (get-in assertions [:is-allowed-by :capacity])]
                  (add acc (name cap) :enables) acc)
            allows (:allows assertions)
            acc (if-let [{:keys [item]} (and allows (physical (:receives assertions)))]
                  (add acc item :enables) acc)
            acc (if-let [i (:consumes-item allows)] (add acc (name i) :consumable) acc)
            acc (if-let [i (:creates-item allows)]  (add acc (name i) :producible) acc)]
        acc))
    {} events))

(def ^:private position-justified-by
  "Which roles account for each position, so the explanation names the
   thing that actually decided it rather than everything on file."
  {:capital          [:enables]
   :work-in-process  [:created :consumed]
   :raw-materials    [:consumed :consumable]
   :finished-goods   [:created :producible :provided :acquired]})

(defn position-basis
  "The position an item occupies, and the events that established it.

   -> {:position kw
       :because [{:role kw :event <assertions>} ...]}

   Returns nil where the record does not determine a position -- there is
   nothing to explain, which is itself the thing to say."
  [events item]
  (when-let [pos (inventory-position events item)]
    (let [by-role (get (item-role-events events) (some-> item name))]
      {:position pos
       :because (vec (for [role (get position-justified-by pos)
                           :let [evs (get by-role role)]
                           :when (seq evs)
                           ev evs]
                       {:role role :event ev}))})))
