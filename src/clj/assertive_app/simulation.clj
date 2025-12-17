(ns assertive-app.simulation
  "Business simulation engine for AALP.

  This module manages the game-like business simulation where students:
  - Start with initial capital
  - Choose actions to perform (purchases, production, sales)
  - Build a persistent ledger of correctly-classified transactions

  Actions are gated by:
  - Level requirements (L0-L1 for purchases, L2 for production/sales)
  - Business state prerequisites (need inventory to sell, need A/P to pay)"
  (:require [datomic.api :as d]
            [assertive-app.schema :as schema]
            [assertive-app.classification :as classification]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; ==================== Configuration ====================

(def STARTING_CASH 10000M)
(def MOVES_PER_PERIOD 5)
(def STARTING_DATE "2026-01-01")

;; ==================== Action Definitions ====================

(def actions
  "Action definitions with level requirements, prerequisites, and state effects.

  Each action maps to a transaction template and specifies:
  - :label - Display name for UI
  - :level - Minimum level required to perform this action
  - :template-key - Key in classification/transaction-templates
  - :prerequisites - Conditions that must be met (checked against business-state)
  - :effects - Functions that modify business state when transaction completes"

  {:purchase-materials-cash
   {:label "Purchase Raw Materials (Cash)"
    :description "Buy blank t-shirts, paying cash"
    :level 0
    :template-key :cash-inventory-purchase
    :prerequisites {:min-cash 100}
    :effects {:cash :subtract-amount
              :raw-materials :add-quantity}}

   :purchase-materials-credit
   {:label "Purchase Raw Materials (Credit)"
    :description "Buy blank t-shirts on account, pay later"
    :level 1
    :template-key :credit-inventory-purchase
    :prerequisites {}
    :effects {:accounts-payable :add-vendor-amount
              :raw-materials :add-quantity}}

   :purchase-equipment-cash
   {:label "Purchase Equipment (Cash)"
    :description "Buy a t-shirt printer, paying cash"
    :level 0
    :template-key :cash-equipment-purchase
    :prerequisites {:min-cash 1000}
    :effects {:cash :subtract-amount
              :equipment :add-equipment}}

   :purchase-equipment-credit
   {:label "Purchase Equipment (Credit)"
    :description "Buy a t-shirt printer on account"
    :level 1
    :template-key :credit-equipment-purchase
    :prerequisites {}
    :effects {:accounts-payable :add-vendor-amount
              :equipment :add-equipment}}

   :pay-vendor
   {:label "Pay Vendor"
    :description "Pay an outstanding account payable"
    :level 1
    :template-key :vendor-payment  ;; Custom template in simulation
    :prerequisites {:has-payable true}
    :effects {:cash :subtract-amount
              :accounts-payable :subtract-vendor-amount}}

   :produce-tshirts
   {:label "Produce T-Shirts"
    :description "Print designs on blank t-shirts"
    :level 2
    :template-key :production-direct
    :prerequisites {:min-raw-materials 10
                    :has-equipment :t-shirt-printer}
    :effects {:raw-materials :subtract-quantity-consumed
              :finished-goods :add-quantity-produced}}

   :sell-tshirts-cash
   {:label "Sell T-Shirts (Cash)"
    :description "Sell printed t-shirts for cash"
    :level 2
    :template-key :cash-sale
    :prerequisites {:min-finished-goods 1}
    :effects {:finished-goods :subtract-quantity
              :cash :add-amount}}

   :sell-tshirts-credit
   {:label "Sell T-Shirts (Credit)"
    :description "Sell printed t-shirts on account"
    :level 2
    :template-key :credit-sale
    :prerequisites {:min-finished-goods 1}
    :effects {:finished-goods :subtract-quantity
              :accounts-receivable :add-customer-amount}}

   :collect-receivable
   {:label "Collect from Customer"
    :description "Receive payment for credit sale"
    :level 2
    :template-key :customer-collection  ;; Custom template in simulation
    :prerequisites {:has-receivable true}
    :effects {:cash :add-amount
              :accounts-receivable :subtract-customer-amount}}})

;; ==================== Business State Helpers ====================

(defn parse-edn-field
  "Parse an EDN string field, returning default if nil or empty."
  [value default]
  (if (and value (seq value))
    (edn/read-string value)
    default))

(defn business-state->map
  "Convert a Datomic business-state entity to a Clojure map."
  [entity]
  (when entity
    {:current-period (:business-state/current-period entity 1)
     :moves-remaining (:business-state/moves-remaining entity MOVES_PER_PERIOD)
     :cash (:business-state/cash entity STARTING_CASH)
     :raw-materials (:business-state/raw-materials entity 0)
     :finished-goods (:business-state/finished-goods entity 0)
     :equipment (parse-edn-field (:business-state/equipment entity) #{})
     :accounts-payable (parse-edn-field (:business-state/accounts-payable entity) {})
     :accounts-receivable (parse-edn-field (:business-state/accounts-receivable entity) {})
     :simulation-date (:business-state/simulation-date entity STARTING_DATE)}))

(defn initialize-business-state
  "Create initial business state map for a new simulation."
  []
  {:current-period 1
   :moves-remaining MOVES_PER_PERIOD
   :cash STARTING_CASH
   :raw-materials 0
   :finished-goods 0
   :equipment #{}
   :accounts-payable {}
   :accounts-receivable {}
   :simulation-date STARTING_DATE})

(defn business-state->tx-data
  "Convert a business state map to Datomic transaction data."
  [user-id state]
  {:business-state/user user-id
   :business-state/current-period (:current-period state)
   :business-state/moves-remaining (:moves-remaining state)
   :business-state/cash (bigdec (:cash state))
   :business-state/raw-materials (:raw-materials state)
   :business-state/finished-goods (:finished-goods state)
   :business-state/equipment (pr-str (:equipment state))
   :business-state/accounts-payable (pr-str (:accounts-payable state))
   :business-state/accounts-receivable (pr-str (:accounts-receivable state))
   :business-state/simulation-date (:simulation-date state)})

;; ==================== Prerequisite Checking ====================

(defn check-prerequisites
  "Check if an action's prerequisites are met given current business state.
  Returns {:ok true} or {:ok false :reason \"...\"}"
  [action-key business-state user-level]
  (let [action (get actions action-key)
        prereqs (:prerequisites action {})]
    (cond
      ;; Action doesn't exist
      (nil? action)
      {:ok false :reason "Unknown action"}

      ;; Level requirement
      (< user-level (:level action 0))
      {:ok false :reason (str "Requires Level " (:level action))}

      ;; Minimum cash
      (and (:min-cash prereqs)
           (< (:cash business-state 0) (:min-cash prereqs)))
      {:ok false :reason (str "Need at least $" (:min-cash prereqs) " cash")}

      ;; Minimum raw materials
      (and (:min-raw-materials prereqs)
           (< (:raw-materials business-state 0) (:min-raw-materials prereqs)))
      {:ok false :reason (str "Need at least " (:min-raw-materials prereqs) " raw material units")}

      ;; Minimum finished goods
      (and (:min-finished-goods prereqs)
           (< (:finished-goods business-state 0) (:min-finished-goods prereqs)))
      {:ok false :reason (str "Need at least " (:min-finished-goods prereqs) " finished goods")}

      ;; Equipment requirement
      (and (:has-equipment prereqs)
           (not (contains? (:equipment business-state #{}) (:has-equipment prereqs))))
      {:ok false :reason (str "Need equipment: " (name (:has-equipment prereqs)))}

      ;; Has accounts payable
      (and (:has-payable prereqs)
           (not (some pos? (vals (:accounts-payable business-state {})))))
      {:ok false :reason "No outstanding payables"}

      ;; Has accounts receivable
      (and (:has-receivable prereqs)
           (not (some pos? (vals (:accounts-receivable business-state {})))))
      {:ok false :reason "No outstanding receivables"}

      ;; All checks passed
      :else
      {:ok true})))

(defn available-actions
  "Return list of actions available to user based on level and business state.
  Includes both available and locked actions with reasons."
  [user-level business-state]
  (for [[action-key action] actions
        :let [check (check-prerequisites action-key business-state user-level)]]
    {:key action-key
     :label (:label action)
     :description (:description action)
     :level (:level action)
     :available (:ok check)
     :reason (when-not (:ok check) (:reason check))}))

(defn action-available?
  "Quick check if an action is available."
  [action-key business-state user-level]
  (:ok (check-prerequisites action-key business-state user-level)))

;; ==================== State Effects ====================

(defn apply-effect
  "Apply a single effect to business state.
  Effect types are symbolic and resolved based on the effect key and variables."
  [state effect-type variables]
  (case effect-type
    ;; Cash effects
    :subtract-amount
    (update state :cash - (bigdec (:amount variables)))

    :add-amount
    (update state :cash + (bigdec (:amount variables)))

    ;; Inventory effects
    :add-quantity
    (update state :raw-materials + (:quantity variables))

    :subtract-quantity
    (update state :finished-goods - (:quantity variables))

    :subtract-quantity-consumed
    (update state :raw-materials - (:quantity-consumed variables (:quantity variables)))

    :add-quantity-produced
    (update state :finished-goods + (:quantity-produced variables (:quantity variables)))

    ;; Equipment effects
    :add-equipment
    (update state :equipment conj (keyword (:equipment-type variables "t-shirt-printer")))

    ;; Accounts Payable effects
    :add-vendor-amount
    (update state :accounts-payable
            (fn [ap]
              (update (or ap {}) (:vendor variables) (fnil + 0M) (bigdec (:amount variables)))))

    :subtract-vendor-amount
    (update state :accounts-payable
            (fn [ap]
              (let [vendor (:vendor variables)
                    new-ap (update (or ap {}) vendor - (bigdec (:amount variables)))]
                ;; Remove vendor if balance is zero or negative
                (into {} (filter (fn [[_ v]] (pos? v)) new-ap)))))

    ;; Accounts Receivable effects
    :add-customer-amount
    (update state :accounts-receivable
            (fn [ar]
              (update (or ar {}) (:customer variables) (fnil + 0M) (bigdec (:amount variables)))))

    :subtract-customer-amount
    (update state :accounts-receivable
            (fn [ar]
              (let [customer (:customer variables)
                    new-ar (update (or ar {}) customer - (bigdec (:amount variables)))]
                ;; Remove customer if balance is zero or negative
                (into {} (filter (fn [[_ v]] (pos? v)) new-ar)))))

    ;; Unknown effect - no change
    state))

(defn apply-effects
  "Apply all effects for an action to business state."
  [business-state action-key variables]
  (let [effects (:effects (get actions action-key) {})]
    (reduce (fn [state [_field effect-type]]
              (apply-effect state effect-type variables))
            business-state
            effects)))

(defn decrement-moves
  "Decrement moves remaining. If zero, advance period."
  [state]
  (let [new-moves (dec (:moves-remaining state 1))]
    (if (<= new-moves 0)
      ;; Advance period
      (-> state
          (update :current-period inc)
          (assoc :moves-remaining MOVES_PER_PERIOD))
      (assoc state :moves-remaining new-moves))))

(defn advance-simulation-date
  "Advance the simulation date by a few days (randomized for variety)."
  [state]
  (let [current-date (:simulation-date state STARTING_DATE)
        ;; Parse the date and add 1-5 days
        [year month day] (map #(Integer/parseInt %) (clojure.string/split current-date #"-"))
        days-to-add (+ 1 (rand-int 5))
        new-day (+ day days-to-add)
        ;; Simple date handling (doesn't account for month boundaries perfectly, but good enough)
        [new-month new-day] (if (> new-day 28)
                              [(inc month) (- new-day 28)]
                              [month new-day])
        [new-year new-month] (if (> new-month 12)
                               [(inc year) (- new-month 12)]
                               [year new-month])
        new-date (format "%04d-%02d-%02d" new-year new-month new-day)]
    (assoc state :simulation-date new-date)))

;; ==================== Custom Templates ====================
;; Templates for actions not in classification.clj

(def simulation-templates
  "Custom templates for simulation-specific actions."
  {:vendor-payment
   {:narrative-template "On {date}, SP pays {vendor} ${amount} for a previous credit purchase."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "monetary-unit" :quantity :amount}
                          :fulfills {:action "requires" :unit "monetary-unit" :quantity :amount}
                          :has-counterparty {:name :vendor}}
    :correct-classification :vendor-payment
    :level 1}

   :customer-collection
   {:narrative-template "On {date}, SP receives ${amount} from {customer} for a previous credit sale."
    :required-assertions {:has-date {:date :date}
                          :receives {:unit "monetary-unit" :quantity :amount}
                          :fulfills {:action "expects" :unit "monetary-unit" :quantity :amount}
                          :has-counterparty {:name :customer}}
    :correct-classification :customer-collection
    :level 2}})

;; ==================== Transaction Generation ====================

(defn- get-template
  "Get template from classification or simulation templates."
  [template-key]
  (or (get classification/transaction-templates template-key)
      (get simulation-templates template-key)))

(defn- apply-template-string
  "Replace {variables} in template string with actual values."
  [template-str vars]
  (reduce (fn [s [k v]]
            (str/replace s (str "{" (name k) "}") (str v)))
          template-str
          vars))

(defn generate-transaction
  "Generate a transaction for the given action.
  Returns problem data including narrative, variables, and correct assertions."
  [action-key business-state]
  (let [action (get actions action-key)
        template-key (:template-key action)
        template (get-template template-key)]
    (when template
      (let [sim-date (:simulation-date business-state STARTING_DATE)
            ;; Generate random variables from template options
            base-vars (into {:date sim-date}
                           (for [[k options] (:variables template {})]
                             [k (if (sequential? options)
                                  (rand-nth options)
                                  options)]))
            ;; For vendor payment, use existing vendor from A/P
            vars (if (= action-key :pay-vendor)
                   (let [ap (:accounts-payable business-state {})
                         vendor (first (keys ap))
                         amount (get ap vendor 0)]
                     (assoc base-vars :vendor vendor :amount (long amount)))
                   base-vars)
            ;; For customer collection, use existing customer from A/R
            vars (if (= action-key :collect-receivable)
                   (let [ar (:accounts-receivable business-state {})
                         customer (first (keys ar))
                         amount (get ar customer 0)]
                     (assoc vars :customer customer :amount (long amount)))
                   vars)
            ;; Generate narrative from template
            narrative (apply-template-string (:narrative-template template) vars)
            ;; Resolve variable references in required-assertions
            resolved-assertions (classification/resolve-assertion-values
                                  (:required-assertions template)
                                  vars)]
        {:problem-id (str (java.util.UUID/randomUUID))
         :template-key template-key
         :narrative narrative
         :variables vars
         :required-assertions resolved-assertions
         :correct-classification (:correct-classification template)
         :action-type action-key
         :level (:level action)}))))

;; ==================== Database Operations ====================

(defn get-business-state
  "Get or initialize business state for a user."
  [user-id]
  (let [db (schema/db)
        entity (d/entity db [:business-state/user user-id])]
    (if entity
      (business-state->map entity)
      (initialize-business-state))))

(defn save-business-state!
  "Save business state to database."
  [user-id state]
  (let [tx-data (business-state->tx-data user-id state)]
    @(d/transact (schema/get-conn) [tx-data])))

(defn get-pending-transaction
  "Get pending transaction for a user, if any."
  [user-id]
  (let [db (schema/db)
        entity (d/entity db [:pending-tx/user user-id])]
    (when entity
      {:action-type (:pending-tx/action-type entity)
       :narrative (:pending-tx/narrative entity)
       :variables (parse-edn-field (:pending-tx/variables entity) {})
       :correct-assertions (parse-edn-field (:pending-tx/correct-assertions entity) {})
       :attempts (:pending-tx/attempts entity 0)
       :created-at (:pending-tx/created-at entity)
       :template-key (:pending-tx/template-key entity)
       :problem-id (:pending-tx/problem-id entity)})))

(defn save-pending-transaction!
  "Save a pending transaction for a user."
  [user-id tx]
  (let [tx-data {:pending-tx/user user-id
                 :pending-tx/action-type (:action-type tx)
                 :pending-tx/narrative (:narrative tx)
                 :pending-tx/variables (pr-str (:variables tx))
                 :pending-tx/correct-assertions (pr-str (:correct-assertions tx))
                 :pending-tx/attempts (or (:attempts tx) 0)
                 :pending-tx/created-at (or (:created-at tx) (java.util.Date.))
                 :pending-tx/template-key (:template-key tx)
                 :pending-tx/problem-id (or (:problem-id tx) (str (java.util.UUID/randomUUID)))}]
    @(d/transact (schema/get-conn) [tx-data])))

(defn clear-pending-transaction!
  "Remove pending transaction for a user."
  [user-id]
  (let [db (schema/db)
        entity-id (d/q '[:find ?e .
                         :in $ ?user
                         :where [?e :pending-tx/user ?user]]
                       db user-id)]
    (when entity-id
      @(d/transact (schema/get-conn) [[:db/retractEntity entity-id]]))))

(defn increment-pending-attempts!
  "Increment the attempt count for pending transaction."
  [user-id]
  (let [db (schema/db)
        current-attempts (or (d/q '[:find ?attempts .
                                    :in $ ?user
                                    :where
                                    [?e :pending-tx/user ?user]
                                    [?e :pending-tx/attempts ?attempts]]
                                  db user-id)
                             0)]
    @(d/transact (schema/get-conn)
                 [{:pending-tx/user user-id
                   :pending-tx/attempts (inc current-attempts)}])))

;; ==================== Ledger Operations ====================

(defn save-ledger-entry!
  "Save a correctly-classified transaction to the ledger."
  [user-id entry]
  (let [tx-data {:ledger-entry/id (java.util.UUID/randomUUID)
                 :ledger-entry/user user-id
                 :ledger-entry/date (:date entry)
                 :ledger-entry/period (:period entry)
                 :ledger-entry/action-type (:action-type entry)
                 :ledger-entry/narrative (:narrative entry)
                 :ledger-entry/variables (pr-str (:variables entry))
                 :ledger-entry/assertions (pr-str (:assertions entry))
                 :ledger-entry/journal-entry (pr-str (:journal-entry entry))
                 :ledger-entry/created-at (java.util.Date.)
                 :ledger-entry/template-key (:template-key entry)}]
    @(d/transact (schema/get-conn) [tx-data])))

(defn get-ledger
  "Get all ledger entries for a user, sorted by date."
  [user-id]
  (let [db (schema/db)
        entries (d/q '[:find [(pull ?e [:ledger-entry/id
                                        :ledger-entry/date
                                        :ledger-entry/period
                                        :ledger-entry/action-type
                                        :ledger-entry/narrative
                                        :ledger-entry/variables
                                        :ledger-entry/assertions
                                        :ledger-entry/journal-entry
                                        :ledger-entry/created-at
                                        :ledger-entry/template-key]) ...]
                       :in $ ?user
                       :where [?e :ledger-entry/user ?user]]
                     db user-id)]
    (->> entries
         (map (fn [e]
                {:id (:ledger-entry/id e)
                 :date (:ledger-entry/date e)
                 :period (:ledger-entry/period e)
                 :action-type (:ledger-entry/action-type e)
                 :narrative (:ledger-entry/narrative e)
                 :variables (parse-edn-field (:ledger-entry/variables e) {})
                 :assertions (parse-edn-field (:ledger-entry/assertions e) {})
                 :journal-entry (parse-edn-field (:ledger-entry/journal-entry e) {})
                 :created-at (:ledger-entry/created-at e)
                 :template-key (:ledger-entry/template-key e)}))
         (sort-by :date))))

;; ==================== High-Level Operations ====================

(defn start-action!
  "Start a new action, creating a pending transaction.
  Returns the generated problem or an error."
  [user-id action-key user-level]
  (let [business-state (get-business-state user-id)
        pending (get-pending-transaction user-id)]
    (cond
      ;; Already have a pending transaction
      pending
      {:error "Complete the pending transaction first"
       :pending-transaction pending}

      ;; Check prerequisites
      (not (action-available? action-key business-state user-level))
      (let [check (check-prerequisites action-key business-state user-level)]
        {:error (:reason check)})

      ;; Generate and save transaction
      :else
      (let [tx (generate-transaction action-key business-state)]
        (if tx
          (let [pending-tx {:action-type action-key
                            :narrative (:narrative tx)
                            :variables (:variables tx)
                            :correct-assertions (:required-assertions tx)
                            :attempts 0
                            :template-key (:template-key tx)
                            :problem-id (:problem-id tx)}]
            (save-pending-transaction! user-id pending-tx)
            {:success true
             :narrative (:narrative tx)
             :variables (:variables tx)
             :problem-id (:problem-id tx)
             :action-type action-key
             :level (get-in actions [action-key :level])})
          {:error "Failed to generate transaction"})))))

(defn complete-transaction!
  "Complete a pending transaction after correct classification.
  Updates business state and records ledger entry."
  [user-id pending-tx journal-entry]
  (let [business-state (get-business-state user-id)
        action-key (:action-type pending-tx)
        variables (:variables pending-tx)
        ;; Apply effects to business state
        new-state (-> business-state
                      (apply-effects action-key variables)
                      (decrement-moves)
                      (advance-simulation-date))
        ;; Create ledger entry
        ledger-entry {:date (:date variables (:simulation-date business-state))
                      :period (:current-period business-state)
                      :action-type action-key
                      :narrative (:narrative pending-tx)
                      :variables variables
                      :assertions (:correct-assertions pending-tx)
                      :journal-entry journal-entry
                      :template-key (:template-key pending-tx)}]
    ;; Save everything
    (save-business-state! user-id new-state)
    (save-ledger-entry! user-id ledger-entry)
    (clear-pending-transaction! user-id)
    {:success true
     :business-state new-state
     :ledger-entry ledger-entry}))

(defn reset-simulation!
  "Reset a user's simulation to initial state.
  Clears business state, pending transaction, and ledger."
  [user-id]
  (let [conn (schema/get-conn)
        db (d/db conn)
        ;; Find all entities to retract
        business-state-id (d/q '[:find ?e .
                                 :in $ ?user
                                 :where [?e :business-state/user ?user]]
                               db user-id)
        pending-tx-id (d/q '[:find ?e .
                             :in $ ?user
                             :where [?e :pending-tx/user ?user]]
                           db user-id)
        ledger-ids (d/q '[:find [?e ...]
                          :in $ ?user
                          :where [?e :ledger-entry/user ?user]]
                        db user-id)
        retractions (concat
                      (when business-state-id [[:db/retractEntity business-state-id]])
                      (when pending-tx-id [[:db/retractEntity pending-tx-id]])
                      (map (fn [id] [:db/retractEntity id]) ledger-ids))]
    (when (seq retractions)
      @(d/transact conn retractions))
    {:success true
     :business-state (initialize-business-state)}))
