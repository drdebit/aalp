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

;; ==================== Game Constants ====================
(def STARTING_CASH 10000M)
(def MOVES_PER_PERIOD 5)
(def STARTING_DATE "2026-01-01")

;; ==================== Derived from classification/physical-items ====================
;; These are derived from the single source of truth in classification.clj

(def inventory-types
  "Purchasable inventory items (derived from classification/physical-items)."
  classification/purchasable-inventory-items)

(def equipment-types
  "Purchasable equipment items (derived from classification/physical-items)."
  classification/equipment-items)

(defn get-item-unit-cost
  "Get the unit cost for any physical item.
   Accepts optional default value (defaults to 5)."
  ([item-key] (get-item-unit-cost item-key 5))
  ([item-key default]
   (or (get-in classification/physical-items [(keyword item-key) :unit-cost])
       default)))

(defn get-item-sell-price
  "Get the sell price for any sellable item.
   Accepts optional default value (defaults to 25)."
  ([item-key] (get-item-sell-price item-key 25))
  ([item-key default]
   (or (get-in classification/physical-items [(keyword item-key) :sell-price])
       default)))

;; Production recipe - what's needed to produce finished goods
(def production-recipe
  "Resources consumed to produce one batch of printed t-shirts."
  {:blank-tshirts 10      ;; 10 blank shirts per batch
   :ink-cartridges 1      ;; 1 ink cartridge per batch
   :output-quantity 10})  ;; Produces 10 finished shirts

;; ==================== Action Definitions ====================

(def actions
  "Action definitions with level requirements, prerequisites, and state effects.

  Each action maps to a transaction template and specifies:
  - :label - Display name for UI
  - :level - Minimum level required to perform this action
  - :template-key - Key in classification/transaction-templates
  - :prerequisites - Conditions that must be met (checked against business-state)
  - :effects - Functions that modify business state when transaction completes"

  {:purchase-inventory-cash
   {:label "Buy Raw Materials (Cash)"
    :description "Select and purchase blank t-shirts or ink cartridges for cash"
    :level 0
    :template-key :cash-inventory-purchase
    :prerequisites {:min-cash 100}
    :effects {:cash :subtract-amount
              :inventory :add-inventory-item}}

   :purchase-inventory-credit
   {:label "Buy Raw Materials (Credit)"
    :description "Select and purchase blank t-shirts or ink cartridges on account"
    :level 1
    :template-key :credit-inventory-purchase
    :prerequisites {}
    :effects {:accounts-payable :add-vendor-amount
              :inventory :add-inventory-item}}

   :purchase-equipment-cash
   {:label "Buy Equipment (Cash)"
    :description "Buy a T-shirt Printer for $3,000 - this allows future production"
    :level 0
    :template-key :cash-equipment-purchase
    :prerequisites {:min-cash 3000}
    :effects {:cash :subtract-amount
              :equipment :add-equipment}}

   :purchase-equipment-credit
   {:label "Buy Equipment (Credit)"
    :description "Buy a T-shirt Printer for $3,000 on account - this allows future production"
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
   {:label "Produce Printed T-Shirts"
    :description "Use equipment to transform raw materials into finished goods (is-allowed-by T-shirt Printer)"
    :level 2
    :template-key :production-direct
    :prerequisites {:has-equipment :t-shirt-printer
                    :min-inventory {:blank-tshirts 10
                                   :ink-cartridges 1}}
    :effects {:inventory :consume-production-inputs
              :finished-goods :add-quantity-produced}}

   :sell-tshirts-cash
   {:label "Sell T-Shirts (Cash)"
    :description "Sell printed t-shirts for cash - requires revenue and cost recognition"
    :level 3
    :template-key :cash-sale
    :prerequisites {:min-finished-goods 1}
    :effects {:finished-goods :subtract-quantity
              :cash :add-amount}}

   :sell-tshirts-credit
   {:label "Sell T-Shirts (Credit)"
    :description "Sell printed t-shirts on account - requires revenue and cost recognition"
    :level 3
    :template-key :credit-sale
    :prerequisites {:min-finished-goods 1}
    :effects {:finished-goods :subtract-quantity
              :accounts-receivable :add-customer-amount}}

   :collect-receivable
   {:label "Collect from Customer"
    :description "Receive payment for a previous credit sale"
    :level 3
    :template-key :customer-collection  ;; Custom template in simulation
    :prerequisites {:has-receivable true}
    :effects {:cash :add-amount
              :accounts-receivable :subtract-customer-amount}}

   ;; ==================== Stage 5: Adjusting Entries ====================

   :record-depreciation
   {:label "Record Depreciation"
    :description "Record monthly depreciation expense on equipment"
    :level 5
    :template-key :record-depreciation
    :prerequisites {:has-equipment :t-shirt-printer}
    :effects {:depreciation :add-accumulated}}

   :accrue-wages
   {:label "Accrue Wages"
    :description "Accrue wages earned but not yet paid"
    :level 5
    :template-key :accrue-wages
    :prerequisites {}
    :effects {:wages-payable :add-amount}}

   :accrue-interest
   {:label "Accrue Interest"
    :description "Accrue interest expense on notes payable"
    :level 5
    :template-key :accrue-interest
    :prerequisites {:has-notes-payable true}
    :effects {:interest-payable :add-amount}}

   :adjust-prepaid
   {:label "Adjust Prepaid Expense"
    :description "Recognize expense from prepaid asset"
    :level 5
    :template-key :adjust-prepaid-expense
    :prerequisites {:has-prepaid true}
    :effects {:prepaid :subtract-amount}}

   ;; ==================== Stage 6: Equity Transactions ====================

   :owner-invest
   {:label "Owner Investment"
    :description "Owner contributes capital to the business"
    :level 6
    :template-key :owner-invests-cash
    :prerequisites {}
    :effects {:cash :add-amount
              :equity :add-capital}}

   :issue-stock
   {:label "Issue Common Stock"
    :description "Issue shares of stock for cash"
    :level 6
    :template-key :issue-common-stock
    :prerequisites {}
    :effects {:cash :add-amount
              :equity :add-stock}}

   :declare-dividend
   {:label "Declare Dividend"
    :description "Board declares a cash dividend to shareholders"
    :level 6
    :template-key :declare-dividend
    :prerequisites {:min-retained-earnings 100}
    :effects {:dividends-payable :add-amount}}

   :pay-dividend
   {:label "Pay Dividend"
    :description "Pay previously declared dividend"
    :level 6
    :template-key :pay-dividend
    :prerequisites {:has-dividends-payable true}
    :effects {:cash :subtract-amount
              :dividends-payable :subtract-amount}}

   :owner-withdraw
   {:label "Owner Withdrawal"
    :description "Owner withdraws funds for personal use"
    :level 6
    :template-key :owner-withdraws-cash
    :prerequisites {:min-cash 100}
    :effects {:cash :subtract-amount
              :equity :subtract-drawing}}

   ;; ==================== Stage 7: Notes & Interest ====================

   :borrow-note
   {:label "Borrow with Note"
    :description "Borrow money from bank with formal promissory note"
    :level 7
    :template-key :borrow-with-note
    :prerequisites {}
    :effects {:cash :add-amount
              :notes-payable :add-amount}}

   :repay-note
   {:label "Repay Note Principal"
    :description "Pay off the principal on a note payable"
    :level 7
    :template-key :repay-note-principal
    :prerequisites {:has-notes-payable true}
    :effects {:cash :subtract-amount
              :notes-payable :subtract-amount}}

   :pay-interest
   {:label "Pay Interest"
    :description "Pay accrued interest on notes payable"
    :level 7
    :template-key :pay-interest-on-note
    :prerequisites {:has-interest-payable true}
    :effects {:cash :subtract-amount
              :interest-payable :subtract-amount}}

   :lend-note
   {:label "Lend with Note"
    :description "Lend money and receive a promissory note"
    :level 7
    :template-key :lend-with-note
    :prerequisites {:min-cash 1000}
    :effects {:cash :subtract-amount
              :notes-receivable :add-amount}}})

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
     :inventory (parse-edn-field (:business-state/inventory entity)
                                 {:blank-tshirts 0 :ink-cartridges 0})
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
   :inventory {:blank-tshirts 0
               :ink-cartridges 0}
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
   :business-state/inventory (pr-str (:inventory state))
   :business-state/finished-goods (:finished-goods state)
   :business-state/equipment (pr-str (:equipment state))
   :business-state/accounts-payable (pr-str (:accounts-payable state))
   :business-state/accounts-receivable (pr-str (:accounts-receivable state))
   :business-state/simulation-date (:simulation-date state)})

;; ==================== Prerequisite Checking ====================

(defn- check-inventory-requirements
  "Check if all required inventory items are available.
  Returns nil if all ok, or a reason string if not."
  [inventory required-inventory]
  (some (fn [[item-key required-qty]]
          (let [have (get inventory item-key 0)
                item-label (get-in classification/physical-items [item-key :label] (name item-key))]
            (when (< have required-qty)
              (str "Need " required-qty " " item-label " (have " have ")"))))
        required-inventory))

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
      {:ok false :reason (str "Unlock in Practice Mode (L" (:level action) ")")}

      ;; Minimum cash
      (and (:min-cash prereqs)
           (< (:cash business-state 0) (:min-cash prereqs)))
      {:ok false :reason (str "Need at least $" (:min-cash prereqs) " cash")}

      ;; Minimum inventory (new structured check)
      (and (:min-inventory prereqs)
           (check-inventory-requirements (:inventory business-state {}) (:min-inventory prereqs)))
      {:ok false :reason (check-inventory-requirements (:inventory business-state {}) (:min-inventory prereqs))}

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

    ;; New inventory effects (using inventory map)
    :add-inventory-item
    (let [item-type (keyword (:inventory-type variables))
          qty (:quantity variables)]
      (update-in state [:inventory item-type] (fnil + 0) qty))

    :consume-production-inputs
    ;; Consume items according to production recipe
    (let [recipe production-recipe]
      (-> state
          (update-in [:inventory :blank-tshirts] - (:blank-tshirts recipe))
          (update-in [:inventory :ink-cartridges] - (:ink-cartridges recipe))))

    ;; Finished goods effects
    :subtract-quantity
    (update state :finished-goods - (:quantity variables))

    :add-quantity-produced
    (update state :finished-goods + (:output-quantity production-recipe))

    ;; Equipment effects
    :add-equipment
    (update state :equipment conj :t-shirt-printer)

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
   {:narrative-template "On {date}, you pay {vendor} ${amount} for a previous credit purchase."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "monetary-unit" :quantity :amount}
                          :fulfills {:action "requires" :unit "monetary-unit" :quantity :amount}
                          :has-counterparty {:name :vendor}}
    :correct-classification :vendor-payment
    :level 1}

   :customer-collection
   {:narrative-template "On {date}, you receive ${amount} from {customer} for a previous credit sale."
    :required-assertions {:has-date {:date :date}
                          :receives {:unit "monetary-unit" :quantity :amount}
                          :fulfills {:action "expects" :unit "monetary-unit" :quantity :amount}
                          :has-counterparty {:name :customer}}
    :correct-classification :customer-collection
    :level 2}})

;; ==================== Transaction Generation ====================

;; Simulation-specific variable options (derived from physical-items where possible)
(def simulation-variables
  "Override template variables with simulation-appropriate options."
  {:equipment-type [(str "a " (:label (first (vals equipment-types))))]
   ;; Inventory options derived from purchasable-inventory-items
   :inventory-options (vec (for [[k v] inventory-types]
                             {:type k :label (:label v) :unit-cost (:unit-cost v)}))
   :vendor ["PrintSupplyCo" "TextileDirect" "InkMasters" "GarmentWholesale"]
   :customer ["LocalSportsTeam" "CampusBoutique" "EventPlannersCo" "RetailPartner"]
   :product ["printed t-shirts" "custom t-shirts" "branded shirts"]})

;; ==================== Action Schemas ====================
;; Single source of truth for action parameters - frontend derives from this

(defn- inventory-type-options
  "Generate inventory type options with prices from physical-items."
  []
  (vec (for [[k v] inventory-types]
         {:value (name k)
          :label (str (:label v) " ($" (:unit-cost v) "/unit)")})))

(def action-schemas
  "Defines parameter schemas for each action type.
   Frontend fetches this to render parameter forms dynamically.

   Parameter types:
   - :select - Dropdown with :options or :options-fn
   - :number - Numeric input with :min and optional :max-fn (business state key)

   Options format:
   - :options - Static list of {:value :label} or strings
   - :options-fn - Keyword referencing a dynamic option generator"
  {:purchase-inventory-cash
   {:parameters
    [{:key :inventory-type
      :label "Item Type"
      :type :select
      :options-fn :inventory-types}
     {:key :quantity
      :label "Quantity"
      :type :number
      :min 1}
     {:key :vendor
      :label "Vendor"
      :type :select
      :options-fn :vendors}]}

   :purchase-inventory-credit
   {:parameters
    [{:key :inventory-type
      :label "Item Type"
      :type :select
      :options-fn :inventory-types}
     {:key :quantity
      :label "Quantity"
      :type :number
      :min 1}
     {:key :vendor
      :label "Vendor"
      :type :select
      :options-fn :vendors}]}

   :sell-tshirts-cash
   {:parameters
    [{:key :quantity
      :label "Quantity"
      :type :number
      :min 1
      :max-fn :finished-goods}
     {:key :customer
      :label "Customer"
      :type :select
      :options-fn :customers}]}

   :sell-tshirts-credit
   {:parameters
    [{:key :quantity
      :label "Quantity"
      :type :number
      :min 1
      :max-fn :finished-goods}
     {:key :customer
      :label "Customer"
      :type :select
      :options-fn :customers}]}})

(defn resolve-action-schema-options
  "Resolve :options-fn references to actual option values."
  [schema]
  (update schema :parameters
          (fn [params]
            (vec (for [param params]
                   (if-let [opts-fn (:options-fn param)]
                     (-> param
                         (dissoc :options-fn)
                         (assoc :options
                                (case opts-fn
                                  :inventory-types (inventory-type-options)
                                  :vendors (vec (for [v (:vendor simulation-variables)]
                                                  {:value v :label v}))
                                  :customers (vec (for [c (:customer simulation-variables)]
                                                    {:value c :label c}))
                                  [])))
                     param))))))

(defn get-action-schemas
  "Get all action schemas with options resolved."
  []
  (into {}
        (for [[action-key schema] action-schemas]
          [action-key (resolve-action-schema-options schema)])))

(defn- simulation-vars
  "Generate variables appropriate for the simulation context.
   Student-provided vars override defaults where applicable."
  [action-key business-state template-vars student-vars]
  (let [cash (:cash business-state 0)]
    (case action-key
      ;; Inventory purchases: student chooses type and quantity
      :purchase-inventory-cash
      (let [inv-type (or (keyword (:inventory-type student-vars))
                         :blank-tshirts)
            unit-cost (get-item-unit-cost inv-type)
            max-qty (int (/ cash unit-cost))
            qty (or (:quantity student-vars)
                    (min 20 (max 1 max-qty)))
            amount (* qty unit-cost)]
        {:inventory-type (name inv-type)
         :physical-item (name inv-type)  ;; For classification system
         :quantity qty
         :amount amount
         :vendor (or (:vendor student-vars)
                     (rand-nth (:vendor simulation-variables)))})

      :purchase-inventory-credit
      (let [inv-type (or (keyword (:inventory-type student-vars))
                         :blank-tshirts)
            unit-cost (get-item-unit-cost inv-type)
            qty (or (:quantity student-vars) 20)
            amount (* qty unit-cost)]
        {:inventory-type (name inv-type)
         :physical-item (name inv-type)  ;; For classification system
         :quantity qty
         :amount amount
         :vendor (or (:vendor student-vars)
                     (rand-nth (:vendor simulation-variables)))})

      :purchase-equipment-cash
      {:amount 3000  ;; Fixed price for T-shirt Printer
       :equipment-type (first (:equipment-type simulation-variables))
       :vendor (or (:vendor student-vars)
                   (rand-nth (:vendor simulation-variables)))}

      :purchase-equipment-credit
      {:amount 3000  ;; Fixed price for T-shirt Printer
       :equipment-type (first (:equipment-type simulation-variables))
       :vendor (or (:vendor student-vars)
                   (rand-nth (:vendor simulation-variables)))}

      ;; Sales
      :sell-tshirts-cash
      (let [qty (or (:quantity student-vars)
                    (min 10 (:finished-goods business-state 1)))
            sell-price (get-item-sell-price :printed-tshirts)
            amount (* qty sell-price)]
        {:quantity qty
         :product (rand-nth (:product simulation-variables))
         :customer (or (:customer student-vars)
                       (rand-nth (:customer simulation-variables)))
         :amount amount})

      :sell-tshirts-credit
      (let [qty (or (:quantity student-vars)
                    (min 10 (:finished-goods business-state 1)))
            sell-price (get-item-sell-price :printed-tshirts)
            amount (* qty sell-price)]
        {:quantity qty
         :product (rand-nth (:product simulation-variables))
         :customer (or (:customer student-vars)
                       (rand-nth (:customer simulation-variables)))
         :amount amount})

      ;; Production - uses fixed recipe
      :produce-tshirts
      {:quantity-consumed (:blank-tshirts production-recipe)
       :quantity-produced (:output-quantity production-recipe)}

      ;; Default: no overrides
      {})))

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
  Accepts optional student-provided variables that override defaults.
  Returns problem data including narrative, variables, and correct assertions."
  ([action-key business-state] (generate-transaction action-key business-state {}))
  ([action-key business-state student-vars]
   (let [action (get actions action-key)
         template-key (:template-key action)
         template (get-template template-key)]
     (when template
       (let [sim-date (:simulation-date business-state STARTING_DATE)
             ;; Filter out nil/empty student vars first
             clean-student-vars (into {} (filter (fn [[_ v]] (and v (not= v ""))) student-vars))
             ;; Get simulation-specific variable overrides (which now use student-vars)
             sim-overrides (simulation-vars action-key business-state (:variables template {}) clean-student-vars)
             ;; Generate base variables from template options (excluding :date - we use sim-date)
             base-vars (into {}
                            (for [[k options] (:variables template {})
                                  :when (not= k :date)]  ;; Don't use template's random dates
                              [k (if (sequential? options)
                                   (rand-nth options)
                                   options)]))
             ;; Merge: base < simulation-overrides (simulation-vars already used student-vars)
             ;; Always use simulation date, never template random dates
             vars (assoc (merge base-vars sim-overrides) :date sim-date)
            ;; For vendor payment, use existing vendor from A/P
            vars (if (= action-key :pay-vendor)
                   (let [ap (:accounts-payable business-state {})
                         vendor (first (keys ap))
                         amount (get ap vendor 0)]
                     (assoc vars :vendor vendor :amount (long amount)))
                   vars)
            ;; For customer collection, use existing customer from A/R
            vars (if (= action-key :collect-receivable)
                   (let [ar (:accounts-receivable business-state {})
                         customer (first (keys ar))
                         amount (get ar customer 0)]
                     (assoc vars :customer customer :amount (long amount)))
                   vars)
            ;; For sales, calculate amount from quantity * price per unit
            vars (if (and (contains? #{:sell-tshirts-cash :sell-tshirts-credit} action-key)
                          (nil? (:amount vars)))
                   (let [qty (:quantity vars 1)
                         price-per-unit (rand-nth [15 20 25 30])]  ;; $15-30 per shirt
                     (assoc vars :amount (* qty price-per-unit)))
                   vars)
            ;; For production, set produced = consumed
            vars (if (and (= action-key :produce-tshirts)
                          (nil? (:quantity-produced vars)))
                   (assoc vars :quantity-produced (:quantity-consumed vars 10))
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
         :level (:level action)})))))

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

;; ==================== Financial Statement Generation ====================

(def account-classifications
  "Maps account names to their financial statement classification."
  {;; Assets (Balance Sheet)
   "Cash" {:type :asset :statement :balance-sheet :normal :debit}
   "Accounts Receivable" {:type :asset :statement :balance-sheet :normal :debit}
   "Notes Receivable" {:type :asset :statement :balance-sheet :normal :debit}
   "Interest Receivable" {:type :asset :statement :balance-sheet :normal :debit}
   "Raw Materials Inventory" {:type :asset :statement :balance-sheet :normal :debit}
   "Finished Goods Inventory" {:type :asset :statement :balance-sheet :normal :debit}
   "Work in Process" {:type :asset :statement :balance-sheet :normal :debit}
   "Equipment" {:type :asset :statement :balance-sheet :normal :debit}
   "Equipment (Fixed Asset)" {:type :asset :statement :balance-sheet :normal :debit}
   "Prepaid Expense" {:type :asset :statement :balance-sheet :normal :debit}
   "Prepaid Expense (Asset)" {:type :asset :statement :balance-sheet :normal :debit}
   "Prepaid Insurance" {:type :asset :statement :balance-sheet :normal :debit}
   "Design Asset" {:type :asset :statement :balance-sheet :normal :debit}
   "Intangible Asset" {:type :asset :statement :balance-sheet :normal :debit}
   ;; Contra-Assets
   "Accumulated Depreciation" {:type :contra-asset :statement :balance-sheet :normal :credit}
   "Allowance for Doubtful Accounts" {:type :contra-asset :statement :balance-sheet :normal :credit}
   ;; Liabilities (Balance Sheet)
   "Accounts Payable" {:type :liability :statement :balance-sheet :normal :credit}
   "Notes Payable" {:type :liability :statement :balance-sheet :normal :credit}
   "Wages Payable" {:type :liability :statement :balance-sheet :normal :credit}
   "Interest Payable" {:type :liability :statement :balance-sheet :normal :credit}
   "Dividends Payable" {:type :liability :statement :balance-sheet :normal :credit}
   "Deferred Revenue (Liability)" {:type :liability :statement :balance-sheet :normal :credit}
   "Unearned Revenue" {:type :liability :statement :balance-sheet :normal :credit}
   ;; Equity (Balance Sheet)
   "Owner's Capital" {:type :equity :statement :balance-sheet :normal :credit}
   "Common Stock" {:type :equity :statement :balance-sheet :normal :credit}
   "Retained Earnings" {:type :equity :statement :balance-sheet :normal :credit}
   "Owner's Drawing" {:type :equity :statement :balance-sheet :normal :debit}
   ;; Revenue (Income Statement)
   "Revenue" {:type :revenue :statement :income-statement :normal :credit}
   "Service Revenue" {:type :revenue :statement :income-statement :normal :credit}
   "Interest Revenue" {:type :revenue :statement :income-statement :normal :credit}
   ;; Expenses (Income Statement)
   "Cost of Goods Sold" {:type :expense :statement :income-statement :normal :debit}
   "Expense" {:type :expense :statement :income-statement :normal :debit}
   "Wage Expense" {:type :expense :statement :income-statement :normal :debit}
   "Wages Expense" {:type :expense :statement :income-statement :normal :debit}
   "Depreciation Expense" {:type :expense :statement :income-statement :normal :debit}
   "Bad Debt Expense" {:type :expense :statement :income-statement :normal :debit}
   "Interest Expense" {:type :expense :statement :income-statement :normal :debit}
   "Insurance Expense" {:type :expense :statement :income-statement :normal :debit}
   "Tax Expense" {:type :expense :statement :income-statement :normal :debit}
   "Compliance Expense" {:type :expense :statement :income-statement :normal :debit}
   "Reporting Expense" {:type :expense :statement :income-statement :normal :debit}
   "Organization Costs" {:type :expense :statement :income-statement :normal :debit}})

(defn- parse-amount
  "Extract numeric amount from account string like 'Cash $1,000'."
  [account-str]
  (if-let [match (re-find #"\$([0-9,]+(?:\.[0-9]+)?)" account-str)]
    (-> (second match)
        (str/replace "," "")
        (Double/parseDouble))
    0))

(defn- extract-account-name
  "Extract account name without amount, e.g., 'Cash $1,000' -> 'Cash'."
  [account-str]
  (-> account-str
      (str/replace #"\s*\$[0-9,]+(?:\.[0-9]+)?\s*$" "")
      str/trim))

(defn- process-journal-entry
  "Process a single journal entry and update account balances.
   Journal entry format: {:debit 'Account $amount' :credit 'Account $amount'}"
  [balances entry]
  (let [debit-str (:debit entry)
        credit-str (:credit entry)
        debit-account (extract-account-name debit-str)
        credit-account (extract-account-name credit-str)
        ;; Try to get amount from debit first, then credit
        amount (let [d-amt (parse-amount debit-str)
                     c-amt (parse-amount credit-str)]
                 (if (pos? d-amt) d-amt c-amt))]
    (if (pos? amount)
      (-> balances
          (update debit-account (fnil + 0M) (bigdec amount))
          (update credit-account (fnil - 0M) (bigdec amount)))
      balances)))

(defn- process-ledger-entries
  "Process all ledger entries and compute account balances.
   Uses debit-positive convention: debits add, credits subtract."
  [entries]
  (reduce
    (fn [balances entry]
      (let [je (:journal-entry entry)]
        (cond
          ;; Single journal entry (map)
          (and (map? je) (:debit je))
          (process-journal-entry balances je)
          ;; Multiple journal entries (vector of maps)
          (vector? je)
          (reduce process-journal-entry balances je)
          ;; No journal entry
          :else balances)))
    {}
    entries))

(defn- account-balance-to-normal
  "Convert account balance to normal (positive) representation.
   Assets/expenses are debit-normal (positive when debit > credit).
   Liabilities/equity/revenue are credit-normal (positive when credit > debit)."
  [account-name balance]
  (let [classification (get account-classifications account-name
                            {:type :unknown :normal :debit})]
    (if (= :credit (:normal classification))
      (* -1 balance)  ;; Credit-normal accounts: negate to show positive
      balance)))

(defn generate-financial-statements
  "Generate financial statements from ledger entries.
   Returns balance sheet and income statement with account balances."
  [user-id]
  (let [entries (get-ledger user-id)
        ;; Get raw balances (debit-positive convention)
        raw-balances (process-ledger-entries entries)
        ;; Convert to normal representation and classify
        classified-accounts
        (for [[account raw-balance] raw-balances
              :let [classification (get account-classifications account
                                        {:type :unknown :statement :balance-sheet :normal :debit})
                    balance (account-balance-to-normal account raw-balance)]
              :when (not (zero? balance))]
          {:account account
           :balance balance
           :type (:type classification)
           :statement (:statement classification)})
        ;; Group by statement
        by-statement (group-by :statement classified-accounts)
        ;; Build balance sheet
        bs-accounts (get by-statement :balance-sheet [])
        assets (filter #(= :asset (:type %)) bs-accounts)
        contra-assets (filter #(= :contra-asset (:type %)) bs-accounts)
        liabilities (filter #(= :liability (:type %)) bs-accounts)
        equity (filter #(= :equity (:type %)) bs-accounts)
        ;; Build income statement
        is-accounts (get by-statement :income-statement [])
        revenues (filter #(= :revenue (:type %)) is-accounts)
        expenses (filter #(= :expense (:type %)) is-accounts)
        ;; Calculate totals
        total-assets (reduce + 0M (map :balance assets))
        total-contra-assets (reduce + 0M (map :balance contra-assets))
        net-assets (- total-assets total-contra-assets)
        total-liabilities (reduce + 0M (map :balance liabilities))
        total-equity (reduce + 0M (map :balance equity))
        total-revenue (reduce + 0M (map :balance revenues))
        total-expenses (reduce + 0M (map :balance expenses))
        net-income (- total-revenue total-expenses)]
    {:balance-sheet
     {:assets (vec (sort-by :account assets))
      :contra-assets (vec (sort-by :account contra-assets))
      :liabilities (vec (sort-by :account liabilities))
      :equity (vec (sort-by :account equity))
      :totals {:assets total-assets
               :contra-assets total-contra-assets
               :net-assets net-assets
               :liabilities total-liabilities
               :equity total-equity
               :liabilities-and-equity (+ total-liabilities total-equity)}}
     :income-statement
     {:revenues (vec (sort-by :account revenues))
      :expenses (vec (sort-by :account expenses))
      :totals {:revenue total-revenue
               :expenses total-expenses
               :net-income net-income}}
     :transaction-count (count entries)
     :as-of-date (when (seq entries) (:date (last entries)))}))

;; ==================== High-Level Operations ====================

(defn start-action!
  "Start a new action, creating a pending transaction.
  Accepts optional student-provided variables that override defaults.
  Returns the generated problem or an error."
  ([user-id action-key user-level] (start-action! user-id action-key user-level {}))
  ([user-id action-key user-level student-vars]
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
       (let [tx (generate-transaction action-key business-state student-vars)]
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
          {:error "Failed to generate transaction"}))))))

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

;; ==================== Outstanding Receivables for Bad Debt Calculation ====================

(defn get-outstanding-receivables
  "Get all outstanding receivables with their confidence levels from credit sales.
   Returns a list of {:customer <name> :amount <number> :confidence <0-100> :date <string> :entry-id <uuid>}
   Only includes receivables that haven't been collected yet (still in accounts-receivable)."
  [user-id]
  (let [ledger (get-ledger user-id)
        business-state (get-business-state user-id)
        current-ar (:accounts-receivable business-state {})
        ;; Find credit sale entries
        credit-sales (->> ledger
                          (filter #(= (:action-type %) :sell-tshirts-credit))
                          (map (fn [entry]
                                 (let [vars (:variables entry)
                                       assertions (:assertions entry)]
                                   {:entry-id (:id entry)
                                    :date (:date entry)
                                    :customer (:customer vars)
                                    :amount (:amount vars)
                                    ;; Get confidence from expects assertion
                                    :confidence (or (get-in assertions [:expects :confidence])
                                                    ;; Fallback to 100% if not found
                                                    100)}))))
        ;; Filter to only include customers who still have outstanding balances
        ;; and match the amounts (in case of partial payments in future)
        outstanding (->> credit-sales
                         (filter (fn [{:keys [customer amount]}]
                                   ;; Customer still has outstanding balance
                                   (when-let [ar-amount (get current-ar customer)]
                                     (> ar-amount 0)))))]
    outstanding))

(defn get-receivables-summary
  "Get a summary of outstanding receivables for the calculation builder UI.
   Includes the individual receivables and the calculated bad debt amount."
  [user-id]
  (let [receivables (get-outstanding-receivables user-id)
        ;; Calculate expected loss for each receivable
        with-expected-loss
        (map (fn [r]
               (let [confidence (or (:confidence r) 100)
                     expected-loss (* (:amount r) (/ (- 100 confidence) 100))]
                 (assoc r
                        :expected-loss expected-loss
                        :non-collection-rate (- 100 confidence))))
             receivables)
        total-receivables (reduce + 0 (map :amount receivables))
        total-bad-debt (reduce + 0 (map :expected-loss with-expected-loss))]
    {:receivables with-expected-loss
     :total-receivables total-receivables
     :total-bad-debt total-bad-debt
     :display (format "$%.2f" total-bad-debt)}))
