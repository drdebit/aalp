(ns assertive-app.guided
  "The Guided Year (Year 1 of the two-act arc).

  A scripted business year delivered as narrative transactions the student
  records in assertions. Transactions commit AS ASSERTED — errors persist in
  the ledger (consequences surface at period close), while business-state
  effects follow the canonical event (reality vs. record can diverge; that
  divergence is the pedagogy). Corridor decision gates auto-enter their
  transactions with the canonical record, shown to the student as a derived
  artifact. When the script is exhausted the student enters Year 2: the
  existing autonomous simulation, inheriting Year 1's closing state."
  (:require [datomic.api :as d]
            [assertive-app.schema :as schema]
            [assertive-app.classification :as classification]
            [assertive-app.simulation :as simulation]
            [assertive-app.je-derive :as je-derive]
            [assertive-app.engine :as engine]))

;; ==================== The script ====================
;; Demo script: L0 cash world -> L1 credit, two corridor gates.
;; The full scripted year (through production, sales, and adjusting
;; entries) is authored content layered onto this same structure.
;; Every :template-key must exist in classification/transaction-templates
;; and every :action-key in simulation's actions (effects lookup).

(def script
  [{:day 1 :level 0 :type :transaction
    :template-key :cash-equipment-purchase
    :action-key :purchase-equipment-cash
    :variables {:date "2026-01-05" :equipment-type "a T-shirt Printer"
                :vendor "PrinterWorld" :amount 3000}}

   {:day 2 :level 0 :type :transaction
    :template-key :cash-inventory-purchase
    :action-key :purchase-inventory-cash
    :variables {:date "2026-01-08" :quantity 50
                :inventory-type "blank t-shirts" :physical-item "blank-tshirts"
                :vendor "TextileDirect" :amount 150}}

   {:day 3 :level 0 :type :transaction
    :template-key :cash-inventory-purchase
    :action-key :purchase-inventory-cash
    :variables {:date "2026-01-12" :quantity 20
                :inventory-type "ink cartridges" :physical-item "ink-cartridges"
                :vendor "InkMasters" :amount 40}}

   {:day 4 :level 0 :type :gate
    :template-key :cash-inventory-purchase
    :action-key :purchase-inventory-cash
    :prompt "TextileDirect offers blank t-shirts at $3.00 each. Each production run will use 10 shirts. How many do you buy?"
    :unit-price 3 :min 10 :max 300
    :variables {:date "2026-01-19"
                :inventory-type "blank t-shirts" :physical-item "blank-tshirts"
                :vendor "TextileDirect"}}

   {:day 5 :level 1 :type :transaction
    :template-key :credit-inventory-purchase
    :action-key :purchase-inventory-credit
    :variables {:date "2026-02-02" :quantity 100
                :inventory-type "blank t-shirts" :physical-item "blank-tshirts"
                :vendor "PrintSupplyCo" :amount 300
                :days 30 :due-date "2026-03-04"}}

   {:day 6 :level 1 :type :transaction
    :template-key :cash-inventory-purchase
    :action-key :purchase-inventory-cash
    :variables {:date "2026-02-10" :quantity 30
                :inventory-type "ink cartridges" :physical-item "ink-cartridges"
                :vendor "InkMasters" :amount 60}}

   {:day 7 :level 1 :type :gate
    :template-key :cash-inventory-purchase
    :action-key :purchase-inventory-cash
    :prompt "InkMasters offers ink cartridges at $2.00 each. Each production run will use one cartridge. How many do you buy?"
    :unit-price 2 :min 5 :max 100
    :variables {:date "2026-02-17"
                :inventory-type "ink cartridges" :physical-item "ink-cartridges"
                :vendor "InkMasters"}}

   {:day 8 :level 1 :type :transaction
    :template-key :credit-inventory-purchase
    :action-key :purchase-inventory-credit
    :variables {:date "2026-03-01" :quantity 50
                :inventory-type "blank t-shirts" :physical-item "blank-tshirts"
                :vendor "TextileDirect" :amount 150
                :days 60 :due-date "2026-04-30"}}])

;; ==================== Script position ====================

(defn get-position [user-id]
  (or (d/q '[:find ?p . :in $ ?u
             :where [?e :progress/user ?u] [?e :progress/guided-position ?p]]
           (schema/db) user-id)
      0))

(defn set-position! [user-id pos]
  ;; :progress/user is unique identity, so this upserts even if the
  ;; progress entity doesn't exist yet.
  @(d/transact (schema/get-conn)
               [{:progress/user user-id :progress/guided-position pos}]))

;; ==================== Payloads ====================

(defn- entry-template [entry]
  (get classification/transaction-templates (:template-key entry)))

(defn- gate-effective-max
  "Bound the gate's max by what the student can afford."
  [entry business-state]
  (let [cash (or (:cash business-state) 0)
        affordable (int (quot cash (:unit-price entry)))]
    (min (:max entry) affordable)))

(defn day-payload
  "The current script entry rendered for the client. Never includes
   correct assertions or classification — those stay server-side."
  [user-id]
  (let [pos (get-position user-id)
        total (count script)]
    (if (>= pos total)
      {:phase "year2" :total total}
      (let [entry (nth script pos)
            base {:phase "year1"
                  :day (:day entry)
                  :total total
                  :level (:level entry)
                  :date (get-in entry [:variables :date])}]
        (case (:type entry)
          :transaction
          (assoc base
                 :entry-type "transaction"
                 :narrative (classification/apply-template
                              (:narrative-template (entry-template entry))
                              (:variables entry))
                 ;; Variables are shown in the narrative anyway; the client
                 ;; uses them for L1+ auto-fill of date/counterparty.
                 :variables (:variables entry))
          :gate
          (assoc base
                 :entry-type "gate"
                 :gate {:prompt (:prompt entry)
                        :unit-price (:unit-price entry)
                        :item-label (get-in entry [:variables :inventory-type])
                        :vendor (get-in entry [:variables :vendor])
                        :min (:min entry)
                        :max (gate-effective-max entry (simulation/get-business-state user-id))}))))))

;; ==================== Persistence helpers ====================

(defn- persist-entry!
  "Apply canonical effects to business state and write a ledger entry.
   The ledger entry's :journal-entry and :assertions are the RECORD
   (as-asserted for transactions, canonical for gate auto-entries);
   effects always follow the canonical event. Extra archaeology fields
   (:guided-day :correct? :canonical-je) ride inside the variables EDN
   blob to avoid schema churn — the period-close diff reads them there."
  [user-id entry vars assertions journal-entry {:keys [correct? canonical-je]}]
  (let [business-state (simulation/get-business-state user-id)
        new-state (-> business-state
                      (simulation/apply-effects (:action-key entry) vars)
                      (assoc :simulation-date (:date vars)))
        narrative (classification/apply-template
                    (:narrative-template (entry-template entry)) vars)
        engine-event-id (engine/store-classified-event!
                          {:assertions assertions
                           :date (:date vars)
                           :asserted-by (str user-id)
                           :template-key (:template-key entry)
                           :counterparty (:customer vars (:vendor vars))})
        ledger-entry (cond-> {:date (:date vars)
                              :period (:current-period business-state 1)
                              :action-type (:action-key entry)
                              :narrative narrative
                              :variables (assoc vars
                                                :guided-day (:day entry)
                                                :correct? correct?
                                                :canonical-je canonical-je)
                              :assertions assertions
                              :journal-entry journal-entry
                              :template-key (:template-key entry)}
                       engine-event-id (assoc :engine-event-id engine-event-id))]
    (simulation/save-business-state! user-id new-state)
    (simulation/save-ledger-entry! user-id ledger-entry)
    {:business-state new-state
     :narrative narrative
     :ledger-entry ledger-entry}))

(defn- canonical-je [entry]
  (first (:journal-entry (get classification/classifications
                              (:correct-classification (entry-template entry))))))

;; ==================== Student submission (commit as asserted) ====================

(defn submit-transaction!
  "Record the student's assertions for the current scripted day.
   No correct/incorrect reveal — the record commits as asserted. The
   ledger carries the JE their assertions actually produce (or an
   unclassified marker); correctness is recorded silently for analytics."
  [user-id selected-assertions]
  (let [pos (get-position user-id)
        entry (nth script pos)
        template (entry-template entry)
        vars (:variables entry)
        result (classification/classify-transaction
                 selected-assertions
                 :correct-classification (:correct-classification template))
        correct? (= :correct (get-in result [:feedback :status]))
        ;; The (amount-augmented) classification lives under :feedback —
        ;; this is the JE the student's OWN assertions produce, right or wrong.
        student-je (or (first (get-in result [:feedback :classification :journal-entry]))
                       {:debit "Unclassified" :credit "Unclassified"})
        persisted (persist-entry! user-id entry vars selected-assertions student-je
                                  {:correct? correct?
                                   :canonical-je (canonical-je entry)})]
    (set-position! user-id (inc pos))
    {:recorded true
     :narrative (:narrative persisted)
     :journal-entry student-je
     :derived-je (je-derive/derive-je selected-assertions vars)
     :business-state (:business-state persisted)
     :next (day-payload user-id)
     ;; silent analytics payload consumed by the route handler
     ::analytics {:entry entry :correct? correct? :result result}}))

;; ==================== Gate submission (auto-entered) ====================

(defn submit-gate!
  "Resolve a corridor decision: the student picks a quantity, the
   resulting transaction enters automatically with the canonical record."
  [user-id quantity]
  (let [pos (get-position user-id)
        entry (nth script pos)
        template (entry-template entry)
        business-state (simulation/get-business-state user-id)
        qty (-> (or quantity (:min entry))
                int
                (max (:min entry))
                (min (gate-effective-max entry business-state)))
        amount (* qty (:unit-price entry))
        vars (assoc (:variables entry) :quantity qty :amount amount)
        assertions (classification/resolve-assertion-values
                     (:required-assertions template) vars)
        je (canonical-je entry)
        persisted (persist-entry! user-id entry vars assertions je
                                  {:correct? true :canonical-je je})]
    (set-position! user-id (inc pos))
    {:auto-entered true
     :quantity qty
     :amount amount
     :narrative (:narrative persisted)
     :journal-entry je
     :derived-je (je-derive/derive-je assertions vars)
     :business-state (:business-state persisted)
     :next (day-payload user-id)}))
