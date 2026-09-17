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

(defn- held
  "The item, count and denomination of a flow that brings a thing into
   the business or sends one out.

   Physical units and intellectual property are both things the business
   holds, and both can be capital. What separates them is the
   denomination -- which is exactly what double-entry means by physical
   substance, and the only thing 2101 uses to tell the two apart. Reading
   both here is what lets a design be capital without being equipment.

   Formerly `physical`, and named for the only denomination it read."
  [params]
  (when-let [denom (case (some-> (:unit params) name)
                     "physical-unit"         :physical
                     "intellectual-property" :intangible
                     nil)]
    (when-let [item (some-> (:physical-item params) name)]
      {:item item
       :denomination denom
       :units (let [q (:quantity params)]
                (cond (number? q) q
                      (string? q) (try (Double/parseDouble (str/trim q))
                                       (catch Exception _ nil))
                      :else nil))})))

(defn capacity-item
  "What an `is-allowed-by` points at, as an item name. The research
   example points at the EVENT that granted the capability -- the printer
   purchase -- and so may the platform now that events carry
   identifiers; an item name is still read, for older records."
  [cap events]
  (let [cap (name cap)
        by-id (into {} (for [e events
                             :let [id (some-> (:has-identifier e) name)
                                   it (:item (held (:receives e)))]
                             :when (and id it)]
                         [id it]))]
    (get by-id cap cap)))

(defn allows-inputs
  "What a capability takes in, as item names. The research example's
   `allows` consumes a list -- a blank shirt AND ink -- so the assertion
   carries :consumes-items; a lone :consumes-item is the one-element case
   and is still read."
  [allows]
  (->> (or (seq (:consumes-items allows))
           (some-> (:consumes-item allows) vector))
       (keep #(when (and % (not= "" %)) (name %)))
       vec))

(defn- helds
  "Every held thing under an assertion. A transformation consumes more
   than one thing -- a blank shirt AND ink -- so a flow assertion may
   hold a list; a single map is the one-element case."
  [v]
  (keep held (cond (nil? v) [] (sequential? v) v :else [v])))

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
                                        (helds (get assertions assertion-key))))
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
                        (update acc (capacity-item cap events) (fnil conj #{}) :enables)
                        acc)
                  ;; The same fact stated forward, at acquisition: this
                  ;; event asserts that what it receives turns one thing
                  ;; into another. The thing received is productive; the
                  ;; thing it consumes is an input; the thing it creates
                  ;; is an output -- three positions from one assertion,
                  ;; which is why the pair is worth asking for and the
                  ;; full recipe is not.
                  allows (:allows assertions)
                  acc (if-let [{:keys [item]} (and allows (held (:receives assertions)))]
                        (update acc item (fnil conj #{}) :enables)
                        acc)
                  acc (reduce (fn [acc i] (update acc i (fnil conj #{}) :consumable))
                              acc (allows-inputs allows))
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

(defn item-denomination
  "What kind of thing the record says this is -- :physical or
   :intangible -- read from the events that brought it in or made it.

   This is the assertion that carries physical substance. A student who
   records a design as intellectual property has SAID it has none; that
   is the whole of the distinction 2101 draws, and it belongs in the
   record rather than in a catalogue. nil means nothing has said."
  [events item]
  (let [item (some-> item name)]
    (->> events
         (mapcat (fn [e] (concat (helds (:receives e)) (helds (:creates e)))))
         (filter #(= item (:item %)))
         (some :denomination))))

(defn position-account
  "The label for a position.

   Both a press and a design are capital -- kept for use, not used up by
   it -- and double-entry files them under different names. What
   separates them is physical substance, and the record says that: a
   thing denominated in intellectual property has none.

   The catalogue is consulted only where the record is silent, for
   entries made before the denomination was asserted. It is a fallback,
   not the answer."
  ([position item item-kinds] (position-account position item item-kinds nil))
  ([position item item-kinds denomination]
   (if (and (= :capital position)
            (or (= :intangible denomination)
                (and (nil? denomination)
                     (= :intangible (get item-kinds (keyword (or item "")))))))
     "Design (Intangible Asset)"
     (position-accounts position))))

(defn inventory-account
  "The account an item's movements hit, given the chain and the firm's
   catalogue. nil when neither determines a position."
  ([events item] (inventory-account events item nil))
  ([events item item-kinds]
   (some-> (inventory-position events item item-kinds)
           (position-account item item-kinds))))

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
                              (helds (get assertions assertion-key))))
                    acc
                    {:receives 1 :creates 1 :consumes -1 :provides -1}))
          {} events))

(defn batches
  "The events that brought `item` into the business, with what each has
   left: the batches a later event can point at. Only outflows that
   name a batch draw it down; an unnamed outflow is priced on the
   average and belongs to no batch in particular.

   -> [{:id s :date s :units n :left n}]"
  [events item]
  (let [item (some-> item name)
        drawn (reduce (fn [acc assertions]
                        (reduce (fn [acc {:keys [from-event] :as f}]
                                  (if (and from-event (= item (some-> (:physical-item f) name)))
                                    (update acc (name from-event) (fnil + 0) (or (:units (held f)) 0))
                                    acc))
                                acc
                                (mapcat #(let [v (get assertions %)]
                                           (cond (nil? v) [] (sequential? v) v :else [v]))
                                        [:provides :consumes])))
                      {} events)]
    (vec (for [assertions events
               :let [id (some-> (:has-identifier assertions) name)]
               :when id
               in (concat (helds (:receives assertions)) (helds (:creates assertions)))
               :when (= item (:item in))]
           {:id id
            :date (get-in assertions [:has-date :date])
            :units (:units in)
            :left (- (:units in) (get drawn id 0))}))))

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

;; ---------------------------------------------------------------------------
;; Promises the record still carries
;; ---------------------------------------------------------------------------

(defn- num-qty
  "A quantity as a number, however it was written."
  [q]
  (cond (number? q) q
        (string? q) (try (Double/parseDouble (str/trim q)) (catch Exception _ nil))
        :else nil))

(defn months-between
  "Whole months from one YYYY-MM-DD to another, counting the month the
   later date falls in when it has run at least as far into it.

   The term of a prepayment is not an assertion and does not need to be:
   the record says when the money went out and by when the thing is due,
   and the months between them is a reading of that, the same way a
   position is a reading of the chain."
  [from to]
  (let [parse #(try (java.time.LocalDate/parse (str %)) (catch Exception _ nil))]
    (when-let [a (parse from)]
      (when-let [b (parse to)]
        (let [whole (.between java.time.temporal.ChronoUnit/MONTHS a b)
              ;; A year of cover bought on 2 January and owed through 31
              ;; December is twelve months, not eleven and a tail; a part
              ;; month of a fortnight or more counts as one.
              tail  (.between java.time.temporal.ChronoUnit/DAYS (.plusMonths a whole) b)]
          (max 1 (long (cond-> whole (>= tail 15) inc))))))))

(defn- promise-kind
  "Which of the four a promise is.

   The promise alone does not say: `requires to receive money` is a
   customer's debt after goods went out and a refund due after money went
   out. What decides it is what moved in the SAME event -- which is the
   Level 1 table exactly, read off the record instead of off a page.

     :receivable  goods went out, money is to come back
     :payable     goods came in, money is to go out
     :prepaid     money went out, goods or a service are to come back
     :advance     money came in, goods or a service are to go out
     :borrowing   money came in, money is to go back -- nothing was
                  bought, so the promise is the whole of the exchange
     :lending     money went out, money is to come back"
  [assertions requires]
  (let [act       (some-> (:action requires) name)
        money?    (= "monetary-unit" (some-> (:unit requires) name))
        out-money (monetary-qty (:provides assertions))
        in-money  (monetary-qty (:receives assertions))
        out-goods (seq (helds (:provides assertions)))
        in-goods  (seq (helds (:receives assertions)))]
    (cond
      (and money? (= "receives" act) out-goods) :receivable
      (and money? (= "provides" act) in-goods)  :payable
      (and (not money?) (= "receives" act) out-money) :prepaid
      (and (not money?) (= "provides" act) in-money)  :advance
      ;; Money for money: no goods on either side, which is what makes
      ;; it a loan rather than a purchase on terms.
      (and money? (= "provides" act) in-money)  :borrowing
      (and money? (= "receives" act) out-money) :lending)))

(defn promises
  "The promises the record still carries, one per `requires` asserted.

   Sibling query to on-hand: that one asks what the business is holding,
   this one what it owes and is owed. Both are readings, and neither is
   a balance anyone posted.

   Any `expects` asserted in the same event travels with the promise,
   because a probability is recorded against a promise and is wanted
   wherever the promise is -- an allowance for what customers owe is that
   query and nothing more. A promise an event names as fulfilled is
   settled and is not returned.

   -> [{:id :kind :date :due-date :action :unit :quantity :counterparty
        :item :confidence :months}]"
  [events]
  (let [settled (into #{} (keep #(some-> (get-in % [:fulfills :event]) name) events))]
    (vec (for [a events
               :let [req  (:requires a)
                     id   (some-> (:has-identifier a) name)
                     kind (when req (promise-kind a req))]
               :when (and kind (not (contains? settled id)))]
           (let [date (get-in a [:has-date :date])
                 due  (:due-date req)]
             (cond-> {:id id
                      :kind kind
                      :date date
                      :due-date due
                      :action (some-> (:action req) name)
                      :unit (some-> (:unit req) name)
                      :quantity (num-qty (:quantity req))
;; What the promise is worth in money. On a debt
                      ;; that is the sum promised; everywhere else it is
                      ;; the money that moved in the same event -- the
                      ;; principal of a loan, or what a prepayment or an
                      ;; advance was paid.
                      :amount (case kind
                                (:receivable :payable) (num-qty (:quantity req))
                                (:prepaid :lending)    (monetary-qty (:provides a))
                                (:advance :borrowing)  (monetary-qty (:receives a)))
                      :counterparty (get-in a [:has-counterparty :name])}
               (:physical-item req) (assoc :item (name (:physical-item req)))
               (:service-item req)  (assoc :item (name (:service-item req)))
               (get-in a [:expects :confidence])
               (assoc :confidence (num-qty (get-in a [:expects :confidence])))
               (and date due (months-between date due))
               (assoc :months (months-between date due))))))))

(defn credit-sales
  "What the record says was sold on credit, up to and including `as-of`.

   Not the same question as `promises`: that one asks what is still
   owed, and drops a debt as soon as it is settled. The percent-of-sales
   method asks what was SOLD on credit in the period, collected or not —
   an income-statement question, which is why the two methods can
   disagree and why it is worth seeing them disagree.

   A credit sale is goods out with money promised back, which is the
   same pattern the receivable is read from.

   -> {:total n :items [{:id :date :counterparty :amount}]}"
  [events as-of]
  (let [items (vec (for [a events
                         :let [req (:requires a)
                               date (get-in a [:has-date :date])]
                         :when (and req
                                    (= "receives" (some-> (:action req) name))
                                    (= "monetary-unit" (some-> (:unit req) name))
                                    (seq (helds (:provides a)))
                                    (or (nil? as-of) (nil? date)
                                        (<= (compare (str date) (str as-of)) 0)))]
                     {:id (some-> (:has-identifier a) name)
                      :date date
                      :counterparty (get-in a [:has-counterparty :name])
                      :amount (num-qty (:quantity req))}))]
    {:total (reduce + 0 (keep :amount items))
     :items items}))

(defn promises-of
  "The open promises of one kind."
  [events kind]
  (filterv #(= kind (:kind %)) (promises events)))

(def aging-buckets
  "The classes an aged schedule sorts receivables into. ACCT 2101's
   exhibit uses these five, and the rate rises with each because the
   longer an amount is past due, the less of it arrives."
  [{:key :not-due   :label "Not yet due"    :from nil :to 0   :default-rate 2}
   {:key :d1-30     :label "1 to 30 days"   :from 1   :to 30  :default-rate 5}
   {:key :d31-60    :label "31 to 60 days"  :from 31  :to 60  :default-rate 10}
   {:key :d61-90    :label "61 to 90 days"  :from 61  :to 90  :default-rate 25}
   {:key :over-90   :label "Over 90 days"   :from 91  :to nil :default-rate 40}])

(defn days-between
  "Whole days from one YYYY-MM-DD to another; negative when the second
   date is the earlier."
  [from to]
  (let [parse #(try (java.time.LocalDate/parse (str %)) (catch Exception _ nil))]
    (when-let [a (parse from)]
      (when-let [b (parse to)]
        (.between java.time.temporal.ChronoUnit/DAYS a b)))))

(defn- bucket-for [days]
  (:key (first (filter (fn [{:keys [from to]}]
                         (and (or (nil? from) (>= days from))
                              (or (nil? to) (<= days to))))
                       aging-buckets))))

(defn aging
  "What customers owe, sorted by how long it is past due at `as-of`.

   The same query as `promises`, bucketed. Nothing new is asserted and
   nothing new is stored: every open receivable already carries the day
   it was made and the day it falls due, so how overdue it is on any
   given date is a reading, like a position or a cost.

   Worth having beside the confidence figures rather than instead of
   them. A confidence is what somebody judged about one customer at the
   time of one sale; an age is what the record can see now, about every
   customer at once, without anyone having judged anything.

   -> [{:key :label :days-label :items [...] :total n}] in order, empty
   buckets included so the schedule reads like a schedule."
  [events as-of]
  (let [open (filter #(= :receivable (:kind %)) (promises events))
        aged (map (fn [p]
                    (let [d (or (some-> (:due-date p) (->> (days-between as-of))) 0)
                          ;; days-between due->as-of: positive when the due
                          ;; date is still ahead, so past-due is its negation.
                          past (- d)]
                      (assoc p :days-past-due (max 0 past)
                               :bucket (bucket-for (max 0 past)))))
                  open)]
    (vec (for [{:keys [key label]} aging-buckets
               :let [items (filterv #(= key (:bucket %)) aged)]]
           {:key key
            :label label
            :items items
            :total (reduce + 0 (keep :amount items))}))))

(defn capital-assets
  "What the record says the business holds to use rather than to sell,
   and what each cost.

   Read the same way everything else here is read: an item whose position
   is :capital, and the event that brought it in. Nothing is looked up --
   a thing is capital because the record says it enables a transformation
   without being used up by it, which is also the reason it is the thing
   that depreciates.

   -> [{:id :date :item :denomination :cost :counterparty}]"
  [events]
  (vec (for [a events
             :let [in (held (:receives a))]
             :when (and in (= :capital (inventory-position events (:item in))))
             :let [cost (monetary-qty (:provides a))]]
         (cond-> {:id (some-> (:has-identifier a) name)
                  :date (get-in a [:has-date :date])
                  :item (:item in)
                  :denomination (:denomination in)
                  :counterparty (get-in a [:has-counterparty :name])}
           cost (assoc :cost cost)))))

(defn capabilities
  "The transformations the record says SP can perform, and what each one
   rests on.

   A capability is asserted, not configured: an event that receives a
   thing and states what that thing allows -- turns blanks into printed
   shirts -- is what makes production possible afterwards.

   -> [{:enabler item :consumes #{item ...} :creates item}]"
  [events]
  (vec (keep (fn [assertions]
               (let [allows  (:allows assertions)
                     inputs  (allows-inputs allows)
                     creates (:creates-item allows)
                     enabler (:item (held (:receives assertions)))]
                 (when (and (seq inputs) creates)
                   {:enabler  enabler
                    :consumes (set inputs)
                    :creates  (name creates)})))
             events)))

(defn- fmt [n] (if (and (number? n) (== n (long n))) (long n) n))

(defn- provision-problem
  "You can only provide what you have. Present tense only: nested under
   `expects` or `allows` the same flow is about the future and
   constrains nothing."
  [selections events]
  (when-let [{:keys [item units]} (held (:provides selections))]
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
      (helds (:consumes selections)))))

(defn- capability-problem
  "A transformation needs something that makes it possible.

   This is the question the record answers with `allows`: SP bought a
   printer AND said what it does. Buying it alone leaves the record
   silent about whether anything can be turned into anything -- which is
   exactly the gap a student should meet, and fill themselves, rather
   than be told about in advance."
  [selections events]
  (let [ins (vec (helds (:consumes selections)))
        in  (first ins)
        out (first (helds (:creates selections)))]
    (when (and in out)
      (let [caps  (capabilities events)
            held  (on-hand events)
            ;; Every input consumed has to be one the capability takes.
            match (first (filter #(and (every? (:consumes %) (map :item ins))
                                       (= (:creates %) (:item out)))
                                 caps))
            said  (fn [c] (str (str/join " and " (sort (:consumes c))) " into " (:creates c)))
            used  (str/join " and " (map :item ins))]
        (cond
          (nil? match)
          {:kind :no-capability
           :consumes used :creates (:item out)
           :message
           (if (seq caps)
             (str "Nothing in your record says SP can turn " used " into "
                  (:item out) ". What SP can do: "
                  (str/join "; " (map said caps))
                  ".")
             (str "Nothing in your record says SP can turn " used " into "
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
                                  acc (helds (get assertions assertion-key))))
                        acc
                        {:receives :acquired :consumes :consumed
                         :creates  :created  :provides :provided})
            acc (if-let [cap (get-in assertions [:is-allowed-by :capacity])]
                  (add acc (capacity-item cap events) :enables) acc)
            allows (:allows assertions)
            acc (if-let [{:keys [item]} (and allows (held (:receives assertions)))]
                  (add acc item :enables) acc)
            acc (reduce (fn [acc i] (add acc i :consumable)) acc (allows-inputs allows))
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
