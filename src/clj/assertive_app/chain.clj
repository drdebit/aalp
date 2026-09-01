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

   -> {item #{roles}}"
  [events]
  (reduce (fn [acc assertions]
            (reduce (fn [acc [assertion-key role]]
                      (if-let [{:keys [item]} (physical (get assertions assertion-key))]
                        (update acc item (fnil conj #{}) role)
                        acc))
                    acc
                    {:receives :acquired
                     :consumes :consumed
                     :creates  :created
                     :provides :provided}))
          {} events))

(def ^:private kind->position
  {:raw-material  :raw-materials
   :finished-good :finished-goods
   :equipment     :equipment-or-other
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
       ;; The emergent case: created by one transformation and consumed
       ;; by another. Nothing declares this and nothing can -- it is only
       ;; true because of what came after.
       (and (:created roles) (:consumed roles))  :work-in-process

       ;; Otherwise the firm's classification of the thing stands.
       kind                                      kind

       ;; No catalogue entry: read what we can from the chain alone.
       (:created roles)                          :finished-goods
       (and (:acquired roles) (:consumed roles)) :raw-materials
       (and (:acquired roles) (:provided roles)) :finished-goods
       (:acquired roles)                         :equipment-or-other
       :else                                     nil))))

(def position-accounts
  "The double-entry label for each position. A translation, and only
   that: renaming these changes nothing about what was asserted."
  {:raw-materials       "Raw Materials Inventory"
   :work-in-process     "Work in Process"
   :finished-goods      "Finished Goods Inventory"
   :equipment-or-other  "Equipment (Fixed Asset)"
   :service             "Service Cost"})

(defn inventory-account
  "The account an item's movements hit, given the chain and the firm's
   catalogue. nil when neither determines a position."
  ([events item] (inventory-account events item nil))
  ([events item item-kinds]
   (some-> (inventory-position events item item-kinds) position-accounts)))
