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
                                (if-let [{:keys [item]} (physical (get assertions assertion-key))]
                                  (update acc item (fnil conj #{}) role)
                                  acc))
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
  "The position an item occupies.

   Two sources, and the distinction matters:

     the firm's own policy -- what KIND of thing this is (a blank shirt
     is an input, a printer is capital). That is SP's rulebook, declared
     in the item catalogue, stable, and not something a student asserts.

     the chain -- what has actually HAPPENED to it. This is what makes a
     position move: a printed shirt is a finished good until some later
     event consumes it, at which point it was in process all along.

   The chain wins where it speaks, because it is evidence; the catalogue
   supplies the baseline for items nothing has happened to yet, so a
   purchase can be posted the day it is made. nil when neither says.

     :raw-materials  :work-in-process  :finished-goods
     :equipment-or-other  :service"
  ([events item] (inventory-position events item nil))
  ([events item item-kinds]
   (let [item  (some-> item name)
         roles (get (item-roles events) item)
         kind  (get kind->position (get item-kinds (keyword item)))]
     (cond
       ;; Productive: the record shows this thing enabling transformations.
       ;; It is not consumed by them -- it is what makes them possible.
       ;; That is the whole content of "capital", and it is asserted, not
       ;; declared: a printer is capital because SP recorded it turning
       ;; blank shirts into printed ones.
       (and (:enables roles) (not (:consumed roles)))  :capital

       ;; Between stages: created by one transformation and consumed by
       ;; another. Only later events can make this true.
       (and (:created roles) (:consumed roles))        :work-in-process

       ;; Consumed by a transformation -- an input, whatever it is called.
       (:consumed roles)                               :raw-materials

       ;; Produced by a transformation, or bought and sold on untouched.
       (:created roles)                                :finished-goods
       (and (:acquired roles) (:provided roles))       :finished-goods

       ;; Declared by some capability's `allows`, before anything has
       ;; actually happened to it. Weaker than evidence of a real
       ;; transformation, stronger than a catalogue: a student SAID this
       ;; is what the printer turns into what.
       (:producible roles)                             :finished-goods
       (:consumable roles)                             :raw-materials

       ;; Nothing yet asserted about what this thing DOES. SP may keep a
       ;; catalogue to save the student saying so every time, but it is a
       ;; convenience standing in for an assertion, not a separate kind of
       ;; truth -- and it loses to anything the record actually says.
       kind                                            kind

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
  (reduce (fn [acc assertions]
            (reduce (fn [acc [assertion-key sign]]
                      (if-let [{:keys [item units]} (physical (get assertions assertion-key))]
                        (update acc item (fnil + 0) (* sign (or units 0)))
                        acc))
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

(defn unsupported-provision
  "Can SP provide what this event says it provides?

   You can only provide what you have. That constraint is on the PRESENT
   tense only: `provides` is a claim about now, and the record either
   bears it out or it does not. Nested under `expects` or `allows` --
   which in the flat form means `requires`, `expects`, `allows` -- the
   same flow is a claim about the future and constrains nothing. SP may
   promise to deliver shirts it has not made yet; it may not hand over
   shirts it does not have.

   Returns nil when the record supports the event, otherwise a map
   describing what is missing, ready to be shown to the student."
  [selections events]
  (when-let [{:keys [item units]} (physical (:provides selections))]
    (let [available (get (on-hand events) item 0)
          wanted    (or units 0)]
      (when (> wanted available)
        {:item item
         :requested wanted
         :available available
         :message
         (if (<= available 0)
           (str "You have no " item " to provide. Nothing in your record "
                "shows SP acquiring or producing any.")
           (str "You have " (if (== available (long available)) (long available) available)
                " " item " on hand, but this event provides " wanted "."))}))))
