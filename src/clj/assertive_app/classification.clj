(ns assertive-app.classification
  "Core classification engine for matching assertions to transaction types."
  (:require [clojure.set]
            [clojure.string]))

;; Helper function to create assertion code -> label lookup
(defn- assertion-code-to-label
  "Creates a map from assertion codes to their human-readable labels"
  [assertions-map]
  (into {}
        (for [[_domain assertions] assertions-map
              assertion assertions]
          [(:code assertion) (:label assertion)])))

;; ==================== Physical Items: Single Source of Truth ====================
;; This is the authoritative definition for all physical items in the system.
;; All dropdown options, account mappings, hints, and labels derive from this.

(def physical-items
  "Unified definition of physical items with their properties and account mappings.
   This is the SINGLE SOURCE OF TRUTH for all item-related data in the system.

   Classification Properties:
   - :label - Human-readable name
   - :description - What this item is (shown in hints)
   - :account - Journal entry account when receiving this item
   - :provides-account - Account when providing/selling (defaults to Revenue)
   - :account-type - :asset, :revenue, :expense for classification
   - :enables - What this item allows [:production, :sale, :capability]
   - :available-for - Which assertions can use this [:provides, :receives]
   - :unlock-level - Level at which the item→account mapping is revealed in feedback

   Simulation Properties:
   - :unit-cost - Cost per unit in simulation (nil for non-purchasable items)
   - :category - :raw-material, :equipment, :finished-good, :service
   - :sellable? - Can this item be sold?
   - :purchasable? - Can this item be purchased?"
  {:blank-tshirts
   {:label "Blank T-Shirts"
    :description "raw materials for production"
    :account "Raw Materials Inventory"
    :account-type :asset
    :enables [:production]
    :available-for #{:provides :receives}
    :unlock-level 0
    ;; Simulation properties
    :unit-cost 5
    :category :raw-material
    :purchasable? true
    :sellable? false}

   :ink-cartridges
   {:label "Ink Cartridges"
    :description "supplies for production"
    :account "Raw Materials Inventory"
    :account-type :asset
    :enables [:production]
    :available-for #{:provides :receives}
    :unlock-level 0
    ;; Simulation properties
    :unit-cost 25
    :category :raw-material
    :purchasable? true
    :sellable? false}

   :t-shirt-printer
   {:label "T-shirt Printer"
    :description "equipment enabling production"
    :account "Equipment (Fixed Asset)"
    :account-type :asset
    :enables [:capability]
    :available-for #{:receives}
    :unlock-level 0
    ;; Simulation properties
    :unit-cost 3000
    :category :equipment
    :purchasable? true
    :sellable? false}

   :printed-tshirts
   {:label "Printed T-Shirts"
    :description "finished goods for sale"
    :account "Finished Goods Inventory"
    :provides-account "Revenue"
    :account-type :asset
    :enables [:sale]
    :available-for #{:provides :receives}
    :unlock-level 0  ;; Must be level 0 since cash-sale template is level 0
    ;; Simulation properties
    :unit-cost nil  ;; Not directly purchasable
    :sell-price 25
    :category :finished-good
    :purchasable? false
    :sellable? true}}
   ;; Note: Services removed from physical-items - use effort-unit for service transactions
   )

;; ==================== Unit Type Options ====================
;; Single source of truth for unit type dropdown options used across assertions

(def unit-type-options
  "Standard unit type options for provides/receives/requires/expects assertions."
  [{:value "monetary-unit" :label "Cash/Money"}
   {:value "physical-unit" :label "Physical Units"}
   {:value "time-unit" :label "Time"}
   {:value "effort-unit" :label "Effort/Labor"}])

;; Derived helpers for simulation
(def purchasable-inventory-items
  "Items that can be purchased in simulation mode (derived from physical-items)."
  (into {}
        (for [[k v] physical-items
              :when (and (:purchasable? v) (= :raw-material (:category v)))]
          [k v])))

(def equipment-items
  "Equipment items that can be purchased (derived from physical-items)."
  (into {}
        (for [[k v] physical-items
              :when (and (:purchasable? v) (= :equipment (:category v)))]
          [k v])))

;; Derived helpers from physical-items
(defn physical-item-options
  "Generate dropdown options for an assertion type (:provides or :receives).
   Filters items by what they're available for and optionally by unlock level."
  [assertion-type & {:keys [max-level] :or {max-level 99}}]
  (vec
   (for [[item-key item-def] physical-items
         :when (and (contains? (:available-for item-def) assertion-type)
                    (<= (:unlock-level item-def) max-level))]
     {:value (name item-key)
      :label (str (:label item-def) " (" (:description item-def) ")")})))

(defn get-physical-item-account
  "Get the account for a physical item, considering whether providing or receiving."
  [item-key assertion-type]
  (let [item-def (get physical-items (keyword item-key))]
    (if (and (= assertion-type :provides) (:provides-account item-def))
      (:provides-account item-def)
      (:account item-def))))

(defn get-physical-item-label
  "Get the display label with account info for a physical item."
  [item-key]
  (when-let [item-def (get physical-items (keyword item-key))]
    (str (:label item-def) " (" (:account item-def) ")")))

(defn physical-item-hint-label
  "Get a hint-appropriate label showing what account the item maps to."
  [item-key]
  (when-let [item-def (get physical-items (keyword item-key))]
    (str (:label item-def) " (" (:account item-def) ")")))

(defn equipment-options
  "Generate dropdown options for equipment items (for is-allowed-by assertion)."
  [& {:keys [max-level] :or {max-level 99}}]
  (vec
   (for [[item-key item-def] physical-items
         :when (and (= (:category item-def) :equipment)
                    (<= (:unlock-level item-def) max-level))]
     {:value (name item-key)
      :label (:label item-def)})))

(defn resolve-physical-item-options
  "Resolve :derives-from-physical-items-* markers to actual options.
   Called when sending assertions to frontend."
  [assertions-map level]
  (into {}
        (for [[domain assertions] assertions-map]
          [domain
           (mapv (fn [assertion]
                   (if-let [params (:parameters assertion)]
                     (assoc assertion :parameters
                            (into {}
                                  (for [[param-key param-spec] params]
                                    [param-key
                                     (if (keyword? (:options param-spec))
                                       ;; Resolve the marker to actual options
                                       (let [options-key (:options param-spec)]
                                         (assoc param-spec :options
                                                (case options-key
                                                  :derives-from-physical-items-provides
                                                  (physical-item-options :provides :max-level level)
                                                  :derives-from-physical-items-receives
                                                  (physical-item-options :receives :max-level level)
                                                  :derives-from-physical-items-equipment
                                                  (equipment-options :max-level level)
                                                  ;; Default: keep as-is
                                                  (:options param-spec))))
                                       param-spec)])))
                     assertion))
                 assertions)])))

;; ==================== Assertion Definitions ====================
(def available-assertions
  (array-map
   ;; Event domain - applies to all events, not just exchanges
   :event
   [{:code :has-date
     :label "Has Date"
     :description "The date when the event occurred"
     :level 0
     :domain :event
     :parameterized true
     :parameters {:date {:type :date
                         :label "Event date"}}}]

   :exchange
   [{:code :has-counterparty
     :label "Has Counterparty"
     :description "Identifies the other party to the transaction"
     :level 0
     :domain :exchange
     :parameterized true
     :parameters {:name {:type :text
                         :label "Counterparty name"
                         :optional true}}}

    {:code :provides
     :label "Provides"
     :description "The entity gives something in an exchange"
     :level 0
     :domain :exchange
     :parameterized true
     :parameters {:unit {:type :dropdown
                         :label "Unit type"
                         :options unit-type-options}
                  ;; Physical item specifies what kind of goods (appears when unit is physical-unit)
                  ;; Options derived from physical-items single source of truth
                  :physical-item {:type :dropdown
                                  :label "Physical item"
                                  :options :derives-from-physical-items-provides}
                  :quantity {:type :number
                             :label "Quantity"
                             :optional true}}}

    {:code :receives
     :label "Receives"
     :description "The entity gets something in an exchange"
     :level 0
     :domain :exchange
     :parameterized true
     :parameters {:unit {:type :dropdown
                         :label "Unit type"
                         :options unit-type-options}
                  ;; Physical item specifies what kind of goods (appears when unit is physical-unit)
                  ;; Options derived from physical-items single source of truth
                  :physical-item {:type :dropdown
                                  :label "Physical item"
                                  :options :derives-from-physical-items-receives}
                  :quantity {:type :number
                             :label "Quantity"
                             :optional true}}}]

   :forward-looking
   [{:code :requires
     :label "Requires"
     :description "Creates an obligation for a future event"
     :level 1
     :domain :forward-looking
     :parameterized true
     :parameters {:action {:type :dropdown
                           :label "Future event"
                           :options [{:value "provides" :label "Provide (give something)"}
                                     {:value "receives" :label "Receive (get something)"}]}
                  :unit {:type :dropdown
                         :label "Unit type"
                         :options unit-type-options}
                  :quantity {:type :number
                             :label "Quantity"
                             :optional true}}}

    {:code :expects
     :label "Expects"
     :description "Anticipates a future event (uncertain, with confidence level)"
     :level 1
     :domain :forward-looking
     :parameterized true
     :parameters {:action {:type :dropdown
                           :label "Expected event"
                           :options [{:value "provides" :label "Provide (give something)"}
                                     {:value "receives" :label "Receive (get something)"}]}
                  :unit {:type :dropdown
                         :label "Unit type"
                         :options unit-type-options}
                  :quantity {:type :number
                             :label "Quantity"
                             :optional true}
                  :confidence {:type :number
                               :label "Confidence level (0-1)"
                               :optional true}}}

    {:code :allows
     :label "Allows"
     :description "Enables or permits future events to occur"
     :level 1
     :domain :forward-looking}]

   :transformation
   [;; Input assertions - each captures a specific type of resource consumed
    {:code :consumes-inventory
     :label "Consumes Inventory"
     :description "Uses inventory items (raw materials) in production"
     :level 2
     :domain :transformation
     :parameterized true
     :parameters {:item {:type :text
                         :label "Item name"}
                  :quantity {:type :number
                             :label "Quantity"}
                  :unit-cost {:type :currency
                              :label "Unit cost ($)"}}}

    {:code :consumes-supplies
     :label "Consumes Supplies"
     :description "Uses supplies (ink, packaging, indirect materials) in production"
     :level 2
     :domain :transformation
     :parameterized true
     :parameters {:item {:type :text
                         :label "Supply type"}
                  :quantity {:type :number
                             :label "Quantity"}
                  :unit-cost {:type :currency
                              :label "Unit cost ($)"}}}

    {:code :consumes-labor
     :label "Consumes Labor"
     :description "Uses labor/effort in production"
     :level 2
     :domain :transformation
     :parameterized true
     :parameters {:hours {:type :number
                          :label "Hours"}
                  :rate {:type :currency
                         :label "Hourly rate ($)"}}}

    {:code :is-allowed-by
     :label "Is Allowed By"
     :description "This event is enabled by a prior capability (equipment purchase enables production)"
     :level 2
     :domain :transformation
     :parameterized true
     :parameters {:capacity {:type :dropdown
                             :label "Enabled by"
                             :options :derives-from-physical-items-equipment}}}

    ;; Output assertion - cost is derived from sum of inputs
    {:code :creates-finished-goods
     :label "Creates Finished Goods"
     :description "Produces finished goods from transformation of inputs"
     :level 2
     :domain :transformation
     :parameterized true
     :parameters {:item {:type :text
                         :label "Product name"}
                  :quantity {:type :number
                             :label "Quantity"}}}

    ;; Keep generic versions for simpler scenarios or backward compatibility
    {:code :consumes
     :label "Consumes (Simple)"
     :description "Generic consumption for simple transformations"
     :level 2
     :domain :transformation
     :parameterized true
     :parameters {:unit {:type :dropdown
                         :label "What is consumed"
                         :options [{:value "raw-materials" :label "Raw Materials"}
                                   {:value "supplies" :label "Supplies"}
                                   {:value "effort" :label "Effort/Labor"}]}
                  :quantity {:type :number
                             :label "Quantity"
                             :optional true}}}

    {:code :creates
     :label "Creates (Simple)"
     :description "Generic creation for simple transformations"
     :level 2
     :domain :transformation
     :parameterized true
     :parameters {:unit {:type :dropdown
                         :label "What is created"
                         :options [{:value "finished-goods" :label "Finished Goods"}
                                   {:value "service-output" :label "Service/Deliverable"}
                                   {:value "intellectual-property" :label "Design/IP"}]}
                  :quantity {:type :number
                             :label "Quantity"
                             :optional true}}}]

   :legal-regulatory
   [;; Note: is-allowed-by is now unified in transformation domain (L2 for equipment, L4 for legal)
    ;; When we expand to L4, we'll add legal framework options to the transformation domain assertion

    {:code :is-required-by
     :label "Is Required By"
     :description "References the legal/regulatory framework that mandates this event"
     :level 4
     :domain :legal-regulatory
     :parameterized true
     :parameters {:framework {:type :dropdown
                              :label "Requiring framework"
                              :options [{:value "tax-code" :label "Tax Code (IRS/State)"}
                                        {:value "sec-regulations" :label "SEC Regulations"}
                                        {:value "employment-law" :label "Employment Law (min wage, etc.)"}
                                        {:value "environmental-regs" :label "Environmental Regulations"}
                                        {:value "industry-regs" :label "Industry-Specific Regulations"}]}}}

    {:code :is-protected-by
     :label "Is Protected By"
     :description "References the legal/regulatory framework that protects this event"
     :level 4
     :domain :legal-regulatory
     :parameterized true
     :parameters {:framework {:type :dropdown
                              :label "Protective framework"
                              :options [{:value "copyright" :label "Copyright Law"}
                                        {:value "trademark" :label "Trademark Law"}
                                        {:value "patent" :label "Patent Law"}
                                        {:value "contract-law" :label "Contract Law"}
                                        {:value "trade-secret" :label "Trade Secret Law"}]}}}]

   :recognition
   [{:code :reports
     :label "Reports"
     :description "Recognizes an amount to be reported on the financial statements"
     :level 3
     :domain :recognition
     :parameterized true
     :parameters {:category {:type :dropdown
                             :label "Recognition type"
                             :options [{:value "revenue" :label "Revenue (Income Statement)"}
                                       {:value "expense" :label "Expense/Cost (Income Statement)"}
                                       {:value "gain" :label "Gain (Income Statement)"}
                                       {:value "loss" :label "Loss (Income Statement)"}]}
                  :basis {:type :dropdown
                          :label "Measurement basis"
                          :options [{:value "cash-received" :label "Equal to cash received"}
                                    {:value "cash-paid" :label "Equal to cash paid"}
                                    {:value "cost-of-goods" :label "Equal to cost of goods provided"}
                                    {:value "service-value" :label "Equal to value of service performed"}]}}}]

   :state-modification
   [{:code :modifies
     :label "Modifies"
     :description "Updates or changes a prior assertion"
     :level 4
     :domain :state-modification}

    {:code :fulfills
     :label "Fulfills"
     :description "Satisfies a prior expectation or requirement"
     :level 4
     :domain :state-modification}]))

;; Create lookup map from assertion codes to labels
(def assertion-labels
  (assertion-code-to-label available-assertions))

;; Available accounts for journal entry construction, organized by level and type
(def accounts-by-level
  "Accounts available at each level for JE construction problems.
   Progressively unlocks more complex accounts as students advance."
  {0 {:asset ["Cash" "Raw Materials Inventory" "Equipment" "Prepaid Expense"]
      :liability ["Accounts Payable"]
      :revenue ["Revenue"]
      :expense ["Cost of Goods Sold" "Expense"]}

   1 {:asset ["Cash" "Accounts Receivable" "Raw Materials Inventory" "Equipment" "Prepaid Expense"]
      :liability ["Accounts Payable" "Notes Payable"]
      :revenue ["Revenue"]
      :expense ["Cost of Goods Sold" "Expense"]}

   2 {:asset ["Cash" "Accounts Receivable" "Raw Materials Inventory" "Finished Goods Inventory"
              "Work in Process" "Equipment" "Prepaid Expense"]
      :liability ["Accounts Payable" "Notes Payable" "Wages Payable"]
      :revenue ["Revenue" "Service Revenue"]
      :expense ["Cost of Goods Sold" "Expense" "Wage Expense"]}})

(defn get-accounts-for-level
  "Returns all accounts available at the specified level."
  [level]
  (get accounts-by-level level (get accounts-by-level 0)))

;; Distance calculation weights for classification matching
(def distance-weights
  "Weights used to calculate distance between student assertions and classifications.
   Higher weights = more severe penalty."
  {:missing-assertion 1.0      ;; Missing a required assertion
   :prohibited-assertion 2.0    ;; Including a prohibited assertion (serious error)
   :unrequired-assertion 0.5    ;; Including extra assertions not required
   :parameter-mismatch 1.5})    ;; Wrong parameter value (e.g., wrong unit type)

;; Template builder functions to reduce repetition in classification definitions

(defn cash-exchange
  "Template for cash exchange transactions (simultaneous exchange, no future obligation).
   entity-provides and entity-receives can be :cash or :goods/:services
   physical-item specifies the specific item (e.g., 'blank-tshirts', 't-shirt-printer')"
  [description journal-entry & {:keys [provides-unit provides-item receives-unit physical-item note examples level]
                                 :or {level 0}}]
  (let [base {:required #{:has-date :provides :receives :has-counterparty}
              :prohibited #{:requires :expects :allows}  ;; Cash exchanges don't involve capability recognition
              :description description
              :journal-entry journal-entry
              :level level}
        params (merge {}
                     (when provides-unit
                       {:provides (merge {:unit provides-unit}
                                        (when provides-item {:physical-item provides-item}))})
                     (when receives-unit
                       {:receives (merge {:unit receives-unit}
                                        (when physical-item {:physical-item physical-item}))}))]
    (cond-> base
      (seq params) (assoc :required-parameters params)
      note (assoc :note note)
      examples (assoc :examples examples))))

(defn credit-transaction
  "Template for credit transactions (receive/provide now, obligation/expectation for future).
   future-assertion is either :requires (obligation) or :expects (expectation)
   physical-item specifies the specific item being received (e.g., 'blank-tshirts', 't-shirt-printer')"
  [description journal-entry future-assertion & {:keys [present-action present-unit
                                                         future-action future-unit
                                                         physical-item provides-item note examples level]
                                                  :or {level 1}}]
  (let [present-assertion (if (= present-action :provides) :provides :receives)
        base {:required #{:has-date present-assertion :has-counterparty future-assertion}
              :prohibited #{}
              :description description
              :journal-entry journal-entry
              :level level}
        params (merge {}
                     (when present-unit
                       {present-assertion (merge {:unit present-unit}
                                                 (when (and (= present-action :receives) physical-item)
                                                   {:physical-item physical-item})
                                                 (when (and (= present-action :provides) provides-item)
                                                   {:physical-item provides-item}))})
                     (when (and future-action future-unit)
                       {future-assertion {:action future-action :unit future-unit}}))]
    (cond-> base
      (seq params) (assoc :required-parameters params)
      note (assoc :note note)
      examples (assoc :examples examples))))

;; Classification rules - using research assertions
(def classifications
  {;; ==================== Level 3: Sales with Recognition ====================
   ;; Sales require reporting assertions because we recognize revenue and COGS

   :cash-sale
   {:required #{:has-date :provides :receives :has-counterparty :reports}
    :prohibited #{:requires :expects}
    :required-parameters {:provides {:unit "physical-unit" :physical-item "printed-tshirts"}
                          :receives {:unit "monetary-unit"}}
    :description "Cash sale with revenue and cost recognition"
    :journal-entry [{:debit "Cash" :credit "Revenue" :entry-label "Revenue Recognition"}
                    {:debit "Cost of Goods Sold" :credit "Finished Goods Inventory" :entry-label "Cost Recognition"}]
    :note "A sale requires recognizing both revenue (equal to cash received) and the cost of goods sold (equal to inventory cost)."
    :examples ["SP sells printed t-shirts for cash, recognizing revenue and COGS"]
    :level 3}

   :cash-inventory-purchase
   (cash-exchange
     "Cash purchase of raw materials (provide cash, receive materials for production)"
     [{:debit "Raw Materials Inventory" :credit "Cash"}]
     :provides-unit "monetary-unit"
     :receives-unit "physical-unit"
     ;; physical-item will be specified in templates (blank-tshirts, ink-cartridges)
     ;; The account mapping will determine Raw Materials Inventory from the physical-item
     :examples ["SP purchases blank t-shirts for cash"
                "SP purchases ink cartridges for cash"])

   :cash-equipment-purchase
   (cash-exchange
     "Cash purchase of equipment (provide cash, receive long-term asset)"
     [{:debit "Equipment (Fixed Asset)" :credit "Cash"}]
     :provides-unit "monetary-unit"
     :receives-unit "physical-unit"
     :physical-item "t-shirt-printer"
     :examples ["SP purchases t-shirt printer for $3,000 cash"])

   :inventory-purchase-on-credit
   (credit-transaction
     "Credit purchase of raw materials (receive materials now, obligation to pay later)"
     [{:debit "Raw Materials Inventory" :credit "Accounts Payable"}]
     :requires
     :present-action :receives
     :present-unit "physical-unit"
     ;; physical-item will be specified in templates (blank-tshirts, ink-cartridges)
     :future-action "provides"
     :future-unit "monetary-unit"
     :note "SP receives raw materials immediately but requires (is obligated to) provide cash in the future."
     :examples ["SP receives blank t-shirts, requires payment in 60 days"
                "SP receives ink cartridges, requires payment in 30 days"])

   :equipment-purchase-on-credit
   (credit-transaction
     "Credit purchase of equipment (receive equipment now, obligation to pay later)"
     [{:debit "Equipment (Fixed Asset)" :credit "Accounts Payable"}]
     :requires
     :present-action :receives
     :present-unit "physical-unit"
     :physical-item "t-shirt-printer"
     :future-action "provides"
     :future-unit "monetary-unit"
     :note "SP receives equipment immediately but requires (is obligated to) provide cash in the future."
     :examples ["SP receives t-shirt printer, requires future payment of $3,000"])

   :sale-on-credit
   {:required #{:has-date :provides :has-counterparty :expects :reports}
    :prohibited #{:receives :requires}
    :required-parameters {:provides {:unit "physical-unit" :physical-item "printed-tshirts"}
                          :expects {:action "receives" :unit "monetary-unit"}}
    :description "Credit sale with revenue and cost recognition"
    :journal-entry [{:debit "Accounts Receivable" :credit "Revenue" :entry-label "Revenue Recognition"}
                    {:debit "Cost of Goods Sold" :credit "Finished Goods Inventory" :entry-label "Cost Recognition"}]
    :note "A credit sale recognizes revenue (equal to expected payment) and COGS. The receivable represents the expected future cash."
    :examples ["SP provides printed t-shirts on credit, recognizing revenue and COGS"]
    :level 3}

   :deferred-revenue
   (credit-transaction
     "Deferred revenue (receive payment now, obligation to provide goods later)"
     [{:debit "Cash" :credit "Deferred Revenue (Liability)"}]
     :requires
     :present-action :receives
     :present-unit "monetary-unit"
     :future-action "provides"
     :future-unit "physical-unit"
     :note "SP receives cash immediately but requires (is obligated to) provide goods/services in the future."
     :examples ["SP receives $10,000 advance payment for future custom orders"
                "SP receives prepayment for 6-month service contract"])

   :prepaid-expense
   (credit-transaction
     "Prepaid expense (provide payment now, expect to receive goods/services later)"
     [{:debit "Prepaid Expense (Asset)" :credit "Cash"}]
     :expects
     :present-action :provides
     :present-unit "monetary-unit"
     :future-action "receives"
     :future-unit "physical-unit"
     :note "SP provides cash immediately and expects to receive goods/services in the future."
     :examples ["SP pays $6,000 for 12-month insurance policy in advance"
                "SP prepays $3,000 for rent covering next 3 months"])

   ;; ==================== Level 2: Transformation Events ====================
   ;; Key insight: Transformations have NO counterparty - they are internal processes

   ;; Comprehensive production using detailed input assertions
   :production-full
   {:required #{:consumes-inventory :consumes-supplies :consumes-labor :is-allowed-by :creates-finished-goods}
    :prohibited #{:has-counterparty :provides :receives}
    :description "Full production: inventory + supplies + labor → finished goods"
    :journal-entry :derived  ;; Special marker - JE is calculated from assertion values
    :note "Complete production transformation. Journal entry is derived from costs:
           DR Finished Goods (total cost)
              CR Raw Materials Inventory (inventory cost)
              CR Supplies (supplies cost)
              CR Wages Payable (labor cost)"
    :examples ["SP transforms blank t-shirts + ink + labor into printed t-shirts"]
    :level 2}

   ;; Production with just inventory and labor (no supplies)
   :production-inventory-labor
   {:required #{:consumes-inventory :consumes-labor :is-allowed-by :creates-finished-goods}
    :prohibited #{:has-counterparty :provides :receives}
    :description "Production: inventory + labor → finished goods"
    :journal-entry :derived
    :note "Production using raw materials and labor. Supplies consumed separately if any."
    :examples ["SP uses blank t-shirts and labor to create printed t-shirts"]
    :level 2}

   ;; Simpler classifications using generic assertions (for backward compatibility)
   :production-raw-to-wip
   {:required #{:consumes :creates}
    :required-parameters {:consumes {:unit "raw-materials"}
                          :creates {:unit "work-in-process"}}
    :prohibited #{:has-counterparty :provides :receives}
    :description "Production: Raw materials → Work in Process"
    :journal-entry [{:debit "Work in Process" :credit "Raw Materials"}]
    :note "Internal transformation - no counterparty involved. Raw materials are consumed to create partially completed goods."
    :examples ["SP moves blank t-shirts into production"
               "SP begins printing process on inventory"]
    :level 2}

   :production-wip-to-finished
   {:required #{:consumes :creates}
    :required-parameters {:consumes {:unit "work-in-process"}
                          :creates {:unit "finished-goods"}}
    :prohibited #{:has-counterparty :provides :receives}
    :description "Production: Work in Process → Finished Goods"
    :journal-entry [{:debit "Finished Goods" :credit "Work in Process"}]
    :note "Completing production - WIP becomes saleable finished goods."
    :examples ["SP completes printing and packaging of t-shirts"
               "SP transfers completed shirts to finished goods inventory"]
    :level 2}

   :production-direct
   {:required #{:consumes :creates :is-allowed-by}
    :required-parameters {:consumes {:unit "raw-materials"}
                          :creates {:unit "finished-goods"}
                          :is-allowed-by {:capacity "t-shirt-printer"}}
    :prohibited #{:has-counterparty :provides :receives}
    :description "Direct production: Raw materials → Finished Goods (enabled by equipment)"
    :journal-entry [{:debit "Finished Goods" :credit "Raw Materials"}]
    :note "Production uses equipment purchased earlier. This connects back to your equipment purchase!"
    :examples ["SP uses t-shirt printer to convert blank shirts to printed shirts"
               "SP uses equipment to transform raw materials into finished goods"]
    :level 2}

   :production-with-labor
   {:required #{:consumes :creates}
    :required-parameters {:consumes {:unit "effort"}
                          :creates {:unit "work-in-process"}}
    :prohibited #{:has-counterparty :provides :receives}
    :description "Labor applied to production"
    :journal-entry [{:debit "Work in Process" :credit "Wages Payable"}]
    :note "Recording labor effort consumed in production process."
    :examples ["SP's workers spend time printing t-shirts"
               "Production staff applies effort to manufacturing"]
    :level 2}

   :service-delivery
   {:required #{:consumes :creates}
    :required-parameters {:consumes {:unit "effort"}
                          :creates {:unit "service-output"}}
    :prohibited #{:has-counterparty :provides :receives}
    :description "Service creation through effort"
    :journal-entry [{:debit "Service Cost" :credit "Wages Payable"}]
    :note "Converting labor effort into service deliverables."
    :examples ["SP's designer creates custom artwork"
               "SP provides consulting services"]
    :level 2}

   :design-creation
   {:required #{:consumes :creates}
    :required-parameters {:consumes {:unit "effort"}
                          :creates {:unit "intellectual-property"}}
    :prohibited #{:has-counterparty :provides :receives}
    :description "Creating intellectual property/designs"
    :journal-entry [{:debit "Design Asset" :credit "Wages Payable"}]
    :note "Effort transformed into intellectual property (designs, artwork, etc.)."
    :examples ["SP's designer creates new t-shirt design"
               "SP develops proprietary printing technique"]
    :level 2}

   :supplies-consumption
   {:required #{:consumes :creates}
    :required-parameters {:consumes {:unit "supplies"}
                          :creates {:unit "work-in-process"}}
    :prohibited #{:has-counterparty :provides :receives}
    :description "Supplies consumed in production"
    :journal-entry [{:debit "Work in Process" :credit "Supplies"}]
    :note "Indirect materials (ink, packaging) consumed during production."
    :examples ["SP uses ink to print designs on t-shirts"
               "SP consumes packaging materials"]
    :level 2}

   ;; Legacy/simple production (for backward compatibility)
   :production
   {:required #{:consumes :creates}
    :prohibited #{:has-counterparty}
    :description "Internal transformation (consume inputs, create outputs)"
    :journal-entry [{:debit "Work in Process" :credit "Raw Materials"}]
    :examples ["SP consumes blank t-shirt and ink to create printed t-shirt"]
    :level 2}

   :expense-recognition
   {:required #{:consumes :provides}
    :prohibited #{:creates :receives}
    :description "Consume benefits in operations with payment"
    :journal-entry [{:debit "Expense" :credit "Cash/Payable"}]
    :examples ["SP consumes utilities to operate the business"]}

   :capability-acquisition
   {:required #{:provides :receives :has-counterparty :allows}
    :required-parameters {:provides {:unit "monetary-unit"}
                          :receives {:unit "physical-unit" :physical-item "t-shirt-printer"}}
    :prohibited #{}
    :description "Equipment purchase with explicit capability recognition"
    :journal-entry [{:debit "Equipment" :credit "Cash"}]
    :note "Like cash equipment purchase, but student explicitly recognizes the capability created."
    :examples ["SP purchases t-shirt printer that allows future shirt printing"]
    :level 2}

   ;; ==================== Level 4: Legal & Regulatory Context ====================
   ;; Key insight: Events exist within legal frameworks that enable, require, or protect them

   :sale-under-ucc
   {:required #{:provides :receives :has-counterparty :is-allowed-by}
    :required-parameters {:provides {:unit "physical-unit"}
                          :receives {:unit "monetary-unit"}
                          :is-allowed-by {:framework "ucc"}}
    :prohibited #{}
    :description "Commercial sale enabled by Uniform Commercial Code"
    :journal-entry [{:debit "Cash" :credit "Revenue"}]
    :note "The UCC provides the legal framework that makes commercial sales enforceable."
    :examples ["SP sells t-shirts under standard commercial law"
               "SP engages in commerce enabled by UCC Article 2"]
    :level 4}

   :employment-under-law
   {:required #{:provides :receives :has-counterparty :is-allowed-by :is-required-by}
    :required-parameters {:provides {:unit "monetary-unit"}
                          :receives {:unit "effort"}
                          :is-allowed-by {:framework "employment-law"}
                          :is-required-by {:framework "employment-law"}}
    :prohibited #{}
    :description "Employment relationship with legal requirements"
    :journal-entry [{:debit "Wage Expense" :credit "Wages Payable"}]
    :note "Employment is both enabled by and subject to employment law (minimum wage, benefits, etc.)."
    :examples ["SP hires employee subject to minimum wage requirements"
               "SP employs staff under labor law framework"]
    :level 4}

   :copyright-protected-creation
   {:required #{:consumes :creates :is-protected-by}
    :required-parameters {:consumes {:unit "effort"}
                          :creates {:unit "intellectual-property"}
                          :is-protected-by {:framework "copyright"}}
    :prohibited #{:has-counterparty}
    :description "Creative work protected by copyright"
    :journal-entry [{:debit "Design Asset" :credit "Wages Payable"}]
    :note "Original creative works are automatically protected by copyright law."
    :examples ["SP creates original t-shirt design protected by copyright"
               "SP develops artwork that receives copyright protection"]
    :level 4}

   :trademark-protected-brand
   {:required #{:consumes :creates :is-protected-by}
    :required-parameters {:consumes {:unit "effort"}
                          :creates {:unit "intellectual-property"}
                          :is-protected-by {:framework "trademark"}}
    :prohibited #{:has-counterparty}
    :description "Brand/logo protected by trademark"
    :journal-entry [{:debit "Intangible Asset" :credit "Wages Payable"}]
    :note "Trademarks protect brand names, logos, and distinctive marks used in commerce."
    :examples ["SP creates company logo protected by trademark"
               "SP develops brand identity with trademark protection"]
    :level 4}

   :tax-required-filing
   {:required #{:provides :is-required-by}
    :required-parameters {:provides {:unit "monetary-unit"}
                          :is-required-by {:framework "tax-code"}}
    :prohibited #{:receives}
    :description "Tax payment required by law"
    :journal-entry [{:debit "Tax Expense" :credit "Cash"}]
    :note "Tax payments are mandated by federal and state tax codes."
    :examples ["SP files and pays quarterly estimated taxes"
               "SP remits sales tax as required by state law"]
    :level 4}

   :regulatory-compliance
   {:required #{:provides :is-required-by}
    :required-parameters {:provides {:unit "monetary-unit"}
                          :is-required-by {:framework "industry-regs"}}
    :prohibited #{}
    :description "Payment for regulatory compliance"
    :journal-entry [{:debit "Compliance Expense" :credit "Cash"}]
    :note "Many industries have specific regulations requiring fees, certifications, or compliance costs."
    :examples ["SP pays for required business license"
               "SP obtains industry-required certification"]
    :level 4}

   :contract-protected-agreement
   {:required #{:provides :receives :has-counterparty :expects :is-protected-by}
    :required-parameters {:is-protected-by {:framework "contract-law"}}
    :prohibited #{}
    :description "Agreement protected by contract law"
    :journal-entry [{:debit "Accounts Receivable" :credit "Revenue"}]
    :note "Contract law enables parties to create legally binding agreements with enforceable terms."
    :examples ["SP enters sales contract with legal protections"
               "SP signs service agreement enforceable under contract law"]
    :level 4}

   :business-formation
   {:required #{:provides :is-allowed-by}
    :required-parameters {:provides {:unit "monetary-unit"}
                          :is-allowed-by {:framework "state-business-law"}}
    :prohibited #{:receives}
    :description "Business entity formation under state law"
    :journal-entry [{:debit "Organization Costs" :credit "Cash"}]
    :note "State business laws enable formation of LLCs, corporations, and other legal entities."
    :examples ["SP forms LLC under state business law"
               "SP incorporates business as permitted by state statutes"]
    :level 4}

   :gaap-compliant-reporting
   {:required #{:is-allowed-by :is-required-by}
    :required-parameters {:is-allowed-by {:framework "gaap"}
                          :is-required-by {:framework "sec-regulations"}}
    :prohibited #{}
    :description "Financial reporting under GAAP/SEC requirements"
    :journal-entry [{:debit "Reporting Expense" :credit "Accounts Payable"}]
    :note "Public companies must report financials under GAAP as required by SEC regulations."
    :examples ["SP prepares GAAP-compliant financial statements"
               "SP files SEC-required quarterly report"]
    :level 4}})

;; ==================== Assertion-Account Mapping ====================
;; Maps assertions and their parameters to accounts and JE effects.
;; This enables the UI to show which assertion ties to which account.

;; Build accounts-by-physical-item dynamically from physical-items
(def ^:private accounts-by-physical-item
  "Derived map of physical item keys to their receiving accounts."
  (into {:default "Asset"}
        (for [[item-key item-def] physical-items]
          [item-key (:account item-def)])))

(def assertion-account-mapping
  "Maps assertion patterns to their corresponding accounts and JE effects."
  {:provides
   {:monetary-unit {:account "Cash" :effect :credit :description "Providing cash"}
    :physical-unit {:account "Revenue" :effect :credit :description "Providing goods/services"}}

   :receives
   {:monetary-unit {:account "Cash" :effect :debit :description "Receiving cash"}
    :physical-unit {:accounts-by-physical-item accounts-by-physical-item
                    :effect :debit
                    :description "Receiving physical asset"}}

   :requires
   {:provides-monetary-unit {:account "Accounts Payable" :effect :credit
                             :description "Obligation to pay (liability)"}
    :provides-physical-unit {:account "Deferred Revenue (Liability)" :effect :credit
                             :description "Obligation to deliver (liability)"}}

   :expects
   {:receives-monetary-unit {:account "Accounts Receivable" :effect :debit
                             :description "Expected to receive payment (asset)"}
    :receives-physical-unit {:account "Prepaid Expense (Asset)" :effect :debit
                             :description "Expected to receive goods/services (asset)"}}})

;; Derived helper - kept for backward compatibility but now derives from physical-items
(def physical-item-accounts
  "Maps physical items to their journal entry accounts (derived from physical-items)."
  (into {}
        (for [[item-key item-def] physical-items]
          [(name item-key)
           (if (:provides-account item-def)
             (:provides-account item-def)  ;; Use provides-account for items that can be sold
             (:account item-def))])))

(defn resolve-assertion-to-account
  "Given an assertion and its parameters, determine the linked account.
   Returns a map with :account, :effect, and :description if a mapping exists."
  [assertion-code params]
  (case assertion-code
    :has-counterparty
    ;; Counterparty applies to both sides of the transaction
    {:assertion :has-counterparty
     :params params
     :description "The other party to the transaction"}

    :provides
    (let [unit (or (:unit params) "monetary-unit")
          mapping (get-in assertion-account-mapping [:provides (keyword unit)])]
      (when mapping
        (assoc mapping :assertion :provides :params params)))

    :receives
    (let [unit (or (:unit params) "monetary-unit")
          physical-item (:physical-item params)]
      (if (= unit "physical-unit")
        (let [base-mapping (get-in assertion-account-mapping [:receives :physical-unit])
              account (get-in base-mapping [:accounts-by-physical-item (keyword physical-item)]
                              (get-in base-mapping [:accounts-by-physical-item :default]))]
          {:account account
           :effect (:effect base-mapping)
           :description (:description base-mapping)
           :assertion :receives
           :params params
           :physical-item physical-item})
        (let [mapping (get-in assertion-account-mapping [:receives (keyword unit)])]
          (when mapping
            (assoc mapping :assertion :receives :params params)))))

    :requires
    (let [action (:action params)
          unit (:unit params)
          key (keyword (str action "-" unit))
          mapping (get-in assertion-account-mapping [:requires key])]
      (when mapping
        (assoc mapping :assertion :requires :params params)))

    :expects
    (let [action (:action params)
          unit (:unit params)
          key (keyword (str action "-" unit))
          mapping (get-in assertion-account-mapping [:expects key])]
      (when mapping
        (assoc mapping :assertion :expects :params params)))

    ;; No mapping for other assertion types
    nil))

(defn build-assertion-linkages
  "Build a map of assertion linkages from the selected assertions.
   Returns a map of assertion-code -> linkage-data."
  [assertions-map]
  (into {}
        (for [[code params] assertions-map
              :let [linkage (resolve-assertion-to-account code params)]
              :when linkage]
          [code linkage])))

(defn parameters-match?
  "Check if student parameters match required parameters for an assertion."
  [student-params required-params]
  (every? (fn [[param-key param-value]]
            (= (get student-params param-key) param-value))
          required-params))

(defn generate-dynamic-hints
  "Generate dynamic hints by comparing student assertions to a classification pattern.
   Returns a map with :missing-assertions, :incorrect-assertions, :missing-parameters."
  [assertions-map classification-key]
  (let [classification (get classifications classification-key)
        {:keys [required prohibited required-parameters]} classification
        assertion-keys (set (keys assertions-map))

        ;; Missing assertions
        missing-assertions (clojure.set/difference required assertion-keys)

        ;; Prohibited assertions that are present
        incorrect-assertions (clojure.set/intersection assertion-keys prohibited)

        ;; Assertions present but with wrong/missing parameters
        missing-parameters (when required-parameters
                            (into {}
                                  (for [[assertion-code required-params] required-parameters
                                        :when (contains? assertion-keys assertion-code)
                                        :let [student-params (get assertions-map assertion-code)]
                                        [param-key param-value] required-params
                                        :when (not= (get student-params param-key) param-value)]
                                    [assertion-code {param-key param-value}])))]

    {:missing-assertions missing-assertions
     :incorrect-assertions incorrect-assertions
     :missing-parameters missing-parameters}))

(defn- format-param-key
  "Convert parameter key to human-readable form."
  [param-key]
  (case param-key
    :unit "Unit Type"
    :physical-item "Item"
    :quantity "Quantity"
    :action "Action"
    :name "Name"
    :date "Date"
    :category "Recognition Type"
    :basis "Measurement Basis"
    :capacity "Enabled by"
    ;; Default: capitalize and replace hyphens with spaces
    (clojure.string/replace (clojure.string/capitalize (name param-key)) "-" " ")))

(defn- format-param-value
  "Convert parameter value to human-readable form."
  [param-key param-value]
  (case param-key
    :unit (case param-value
            "monetary-unit" "Cash/Money"
            "physical-unit" "Physical Units"
            "time-unit" "Time"
            "effort-unit" "Effort/Labor"
            param-value)
    :physical-item (if-let [item-def (get physical-items (keyword param-value))]
                     (:label item-def)
                     param-value)
    :capacity (if-let [item-def (get physical-items (keyword param-value))]
                (:label item-def)
                param-value)
    :action (case param-value
              "provides" "Provide"
              "receives" "Receive"
              param-value)
    :category (case param-value
                "revenue" "Revenue"
                "expense" "Expense/Cost"
                "gain" "Gain"
                "loss" "Loss"
                param-value)
    :basis (case param-value
             "cash-received" "Equal to cash received"
             "cash-paid" "Equal to cash paid"
             "cost-of-goods" "Equal to cost of goods provided"
             "service-value" "Equal to value of service performed"
             param-value)
    ;; Default: just return value
    param-value))

(defn format-hints
  "Format hints from dynamic hint data into human-readable strings."
  [hint-data]
  (let [{:keys [missing-assertions incorrect-assertions missing-parameters]} hint-data
        hints []]
    (cond-> hints
      (seq missing-assertions)
      (conj (str "Missing assertions: "
                (clojure.string/join ", " (map #(get assertion-labels % (name %))
                                               missing-assertions))))

      (seq incorrect-assertions)
      (conj (str "Incorrect assertions: "
                (clojure.string/join ", " (map #(get assertion-labels % (name %))
                                               incorrect-assertions))))

      (seq missing-parameters)
      (into (for [[assertion-code params] missing-parameters]
              (str "For " (get assertion-labels assertion-code (name assertion-code))
                   ": "
                   (clojure.string/join ", " (for [[param-key param-value] params]
                                               (str (format-param-key param-key)
                                                    " should be "
                                                    (format-param-value param-key param-value))))))))))

(defn augment-journal-entry
  "Add quantity information to journal entry if available."
  [journal-entry assertions-map]
  (let [;; Try to find a quantity from expects/receives (for credit sales)
        expects-qty (get-in assertions-map [:expects :quantity])
        requires-qty (get-in assertions-map [:requires :quantity])
        receives-qty (get-in assertions-map [:receives :quantity])
        provides-qty (get-in assertions-map [:provides :quantity])
        ;; Use the first quantity we find
        qty (or expects-qty requires-qty receives-qty provides-qty)]
    (if qty
      (mapv (fn [entry]
              (assoc entry
                     :debit (str (:debit entry) " $" qty)
                     :credit (str (:credit entry) " $" qty)))
            journal-entry)
      journal-entry)))

(defn classify-transaction
  "Match student-selected assertions to classification rules.
   student-assertions can be either:
   - A set of keywords (old format): #{:provides :receives}
   - A map with parameters (new format): {:provides {:unit 'physical-unit'} :receives {:unit 'monetary-unit'}}
   correct-classification (optional): The expected correct classification for this problem"
  [student-assertions & {:keys [correct-classification]}]
  (let [;; Convert to map format if it's a set
        assertions-map (if (set? student-assertions)
                         (into {} (map (fn [k] [k {}]) student-assertions))
                         student-assertions)
        assertion-keys (set (keys assertions-map))

        has-any-parameters? (some (fn [[_ params]] (seq params)) assertions-map)

        exact-matches (for [[class-key {:keys [required prohibited optional required-parameters requires-missing-parameters]}] classifications
                            :when (and
                                   ;; All required assertions present
                                   (clojure.set/subset? required assertion-keys)
                                   ;; No prohibited assertions present
                                   (empty? (clojure.set/intersection assertion-keys prohibited))
                                   ;; No extra assertions beyond required+optional
                                   (clojure.set/subset? assertion-keys
                                                       (clojure.set/union required (or optional #{})))
                                   ;; If requires-missing-parameters, check that NO parameters are specified
                                   (or (not requires-missing-parameters)
                                       (not has-any-parameters?))
                                   ;; Check parameters match if required-parameters specified
                                   (or (nil? required-parameters)
                                       (every? (fn [[assertion-code required-params]]
                                                 (parameters-match?
                                                   (get assertions-map assertion-code)
                                                   required-params))
                                               required-parameters)))]
                        class-key)

        all-distances (when (empty? exact-matches)
                        (for [[class-key {:keys [required prohibited optional required-parameters]}] classifications]
                          (let [optional-set (or optional #{})
                                allowed-set (clojure.set/union required optional-set)
                                missing (clojure.set/difference required assertion-keys)
                                extra-prohibited (clojure.set/intersection assertion-keys prohibited)
                                extra-unrequired (clojure.set/difference assertion-keys allowed-set)
                                ;; Calculate parameter mismatches for assertions that ARE present
                                present-assertions (clojure.set/intersection required assertion-keys)
                                param-mismatches (if (and required-parameters (seq present-assertions))
                                                   (count
                                                     (filter (fn [assertion-code]
                                                               (when-let [required-params (get required-parameters assertion-code)]
                                                                 (not (parameters-match?
                                                                        (get assertions-map assertion-code)
                                                                        required-params))))
                                                             present-assertions))
                                                   0)
                                distance (+ (* (:missing-assertion distance-weights) (count missing))
                                           (* (:prohibited-assertion distance-weights) (count extra-prohibited))
                                           (* (:unrequired-assertion distance-weights) (count extra-unrequired))
                                           (* (:parameter-mismatch distance-weights) param-mismatches))]
                            {:type class-key
                             :distance distance
                             :missing missing
                             :extra-prohibited extra-prohibited
                             :extra-unrequired extra-unrequired
                             :param-mismatches param-mismatches})))

        closest (when all-distances
                  ;; Sort by distance first, then use tiebreakers
                  (first (sort-by (juxt :distance
                                       ;; Tiebreaker 1: Prefer more required assertions present (negative for desc sort)
                                       #(- (count (clojure.set/intersection
                                                    (get-in classifications [(:type %) :required])
                                                    assertion-keys)))
                                       ;; Tiebreaker 2: Prefer lower level (simpler concepts)
                                       #(get-in classifications [(:type %) :level] 0)
                                       ;; Tiebreaker 3: Prefer more prohibitions (stricter rules, negative for desc)
                                       #(- (count (get-in classifications [(:type %) :prohibited]))))
                                  all-distances)))]

    ;; Build assertion linkages for all assertions
    (let [linkages (build-assertion-linkages assertions-map)
          ;; When multiple exact matches, prefer the most specific one (most required-parameters)
          best-exact-match (when (seq exact-matches)
                            (first (sort-by
                                    (fn [class-key]
                                      (let [req-params (get-in classifications [class-key :required-parameters])]
                                        ;; Negative for descending sort - more params = more specific
                                        (- (reduce + 0 (map (comp count val) req-params)))))
                                    exact-matches)))]
      {:exact-matches exact-matches
       :closest closest
       :assertion-linkages linkages
       :feedback (cond
                   (seq exact-matches)
                   (let [match-key best-exact-match
                         classification (get classifications match-key)
                         ;; Check if this is the correct classification
                         is-correct-match (or (nil? correct-classification)
                                             (= match-key correct-classification))
                         status (if is-correct-match :correct :incorrect)
                         ;; Augment journal entry with quantities from student's parameters
                         augmented-classification (update classification :journal-entry
                                                          augment-journal-entry assertions-map)]
                     {:status status
                      :message (if is-correct-match
                                 (str "Correct! This is: " (:description classification))
                                 (str "Your assertions describe: " (:description classification)
                                      " But that's not what this transaction is. Re-read the narrative."))
                      ;; Include classification for both correct and incorrect (for detailed feedback)
                      :classification augmented-classification
                      :assertion-linkages linkages
                      ;; For incorrect answers, also include what the correct answer would be
                      :correct-classification (when (and (not is-correct-match) correct-classification)
                                               (let [correct-class (get classifications correct-classification)]
                                                 {:type correct-classification
                                                  :description (:description correct-class)
                                                  :journal-entry (:journal-entry correct-class)}))
                      :hints (when (not is-correct-match)
                               (let [matched-desc (:description classification)
                                     correct-desc (get-in classifications [correct-classification :description])]
                                 [(str "Your assertions describe: " matched-desc)
                                  (str "But this transaction is: " correct-desc)
                                  "Check what the entity is providing vs. receiving."]))})

                   closest
                   (let [closest-classification (get classifications (:type closest))
                         closest-desc (:description closest-classification)
                         correct-class (when correct-classification
                                        (get classifications correct-classification))
                         correct-desc (when correct-class (:description correct-class))
                         ;; Generate parameter-level hints against correct classification
                         hint-data (generate-dynamic-hints assertions-map
                                                           (or correct-classification (:type closest)))
                         param-hints (format-hints hint-data)
                         ;; Augment closest classification with quantities for JE display
                         augmented-closest (update closest-classification :journal-entry
                                                   augment-journal-entry assertions-map)
                         ;; Build narrative hints similar to exact-match style
                         narrative-hints (if (and correct-classification
                                                  (not= (:type closest) correct-classification))
                                          [(str "Your assertions are closest to: " closest-desc)
                                           (str "But this transaction is: " correct-desc)]
                                          [(str "Your assertions are closest to: " closest-desc)])
                         ;; Combine narrative with parameter-specific hints
                         all-hints (into narrative-hints param-hints)]
                     {:status :incorrect
                      :message (first narrative-hints)
                      ;; Include classification for JE display (what student's assertions describe)
                      :classification augmented-closest
                      :assertion-linkages linkages
                      ;; Include correct classification for JE comparison
                      :correct-classification (when correct-class
                                               {:type correct-classification
                                                :description correct-desc
                                                :journal-entry (:journal-entry correct-class)})
                      :hints all-hints})

                   :else
                   {:status :indeterminate
                    :message "Unable to classify with current assertions. Try reviewing the transaction."})})))

;; Problem generation - using research assertions
(def transaction-templates
  {:cash-inventory-purchase
   {:narrative-template "On {date}, you purchase {quantity} {inventory-type} from {vendor} for ${amount} cash."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "monetary-unit" :quantity :amount}
                          :receives {:unit "physical-unit" :physical-item :physical-item :quantity :quantity}
                          :has-counterparty {:name :vendor}}
    :correct-classification :cash-inventory-purchase
    :level 0
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :inventory-type ["blank t-shirts" "ink cartridges"]
                :physical-item ["blank-tshirts" "ink-cartridges"]  ;; Maps to account via physical-item-accounts
                :vendor ["PrintSupplyCo" "TextileDirect" "InkMasters"]
                :quantity [20 50 100]
                :amount [100 250 500 1000]}}

   :cash-equipment-purchase
   {:narrative-template "On {date}, you purchase {equipment-type} from {vendor} for ${amount} cash."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "monetary-unit" :quantity :amount}
                          :receives {:unit "physical-unit" :physical-item "t-shirt-printer" :quantity 1}
                          :has-counterparty {:name :vendor}}
    :correct-classification :cash-equipment-purchase
    :level 0
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :equipment-type ["a T-shirt Printer"]
                :vendor ["PrinterWorld" "EquipmentDirect" "BusinessSupply"]
                :amount [3000]}}

   :cash-sale
   {:narrative-template "On {date}, you sell {quantity} printed t-shirts to {customer} for ${amount} cash. The t-shirts cost ${cogs} to produce."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "physical-unit" :physical-item "printed-tshirts" :quantity :quantity}
                          :receives {:unit "monetary-unit" :quantity :amount}
                          :has-counterparty {:name :customer}
                          ;; Reports assertions for revenue and COGS recognition
                          :reports {:category "revenue" :basis "cash-received"}}
    :correct-classification :cash-sale
    :level 3
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :customer ["LocalSportsTeam" "CampusBoutique" "EventPlannersCo"]
                :quantity [10 25 50]
                :amount [250 625 1250]
                :cogs [100 250 500]}}  ;; Cost of goods sold (paired with quantity)

   :credit-inventory-purchase
   {:narrative-template "On {date}, you receive {quantity} {inventory-type} from {vendor}, agreeing to pay ${amount} in {days} days."
    :required-assertions {:has-date {:date :date}
                          :receives {:unit "physical-unit" :physical-item :physical-item :quantity :quantity}
                          :has-counterparty {:name :vendor}
                          :requires {:action "provides" :unit "monetary-unit" :quantity :amount}}
    :correct-classification :inventory-purchase-on-credit
    :level 1
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :inventory-type ["blank t-shirts" "ink cartridges"]
                :physical-item ["blank-tshirts" "ink-cartridges"]
                :vendor ["PrintSupplyCo" "TextileDirect" "InkMasters"]
                :quantity [20 50 100]
                :amount [100 250 500 1000]
                :days [30 60 90]}}

   :credit-equipment-purchase
   {:narrative-template "On {date}, you receive {equipment-type} from {vendor}, agreeing to pay ${amount} in {days} days."
    :required-assertions {:has-date {:date :date}
                          :receives {:unit "physical-unit" :physical-item "t-shirt-printer" :quantity 1}
                          :has-counterparty {:name :vendor}
                          :requires {:action "provides" :unit "monetary-unit" :quantity :amount}}
    :correct-classification :equipment-purchase-on-credit
    :level 1
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :equipment-type ["a T-shirt Printer"]
                :vendor ["PrinterWorld" "EquipmentDirect" "BusinessSupply"]
                :amount [3000]
                :days [30 60 90]}}

   :credit-sale
   {:narrative-template "On {date}, you provide {quantity} printed t-shirts to {customer}, expecting payment of ${amount} in {days} days. The t-shirts cost ${cogs} to produce."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "physical-unit" :physical-item "printed-tshirts" :quantity :quantity}
                          :has-counterparty {:name :customer}
                          :expects {:action "receives" :unit "monetary-unit" :quantity :amount}
                          ;; Reports assertions for revenue and COGS recognition
                          :reports {:category "revenue" :basis "cash-received"}}
    :correct-classification :sale-on-credit
    :level 3
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :customer ["LocalSportsTeam" "CampusBoutique" "EventPlannersCo"]
                :quantity [10 25 50]
                :amount [250 625 1250]
                :cogs [100 250 500]   ;; Cost of goods sold (paired with quantity)
                :days [30 60 90]}}

   :prepayment
   {:narrative-template "On {date}, SP receives ${amount} advance payment from {customer} for {service} to be delivered in {days} days."
    :required-assertions {:has-date {:date :date}
                          :receives {:unit "monetary-unit" :quantity :amount}
                          :has-counterparty {:name :customer}
                          :requires {:action "provides" :unit "physical-unit"}}
    :correct-classification :deferred-revenue
    :level 1
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :customer ["Customer-A" "RetailStore-X" "CorporateClient-001"]
                :service ["custom t-shirt printing services" "merchandise fulfillment" "a bulk order"]
                :amount [2000 5000 10000 15000 20000]
                :days [30 60 90 180]}}

   :prepaid-expense-transaction
   {:narrative-template "On {date}, SP pays ${amount} to {vendor} for {service} covering the next {months} months."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "monetary-unit" :quantity :amount}
                          :has-counterparty {:name :vendor}
                          :expects {:action "receives" :unit "physical-unit"}}
    :correct-classification :prepaid-expense
    :level 1
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :vendor ["InsuranceCo" "Landlord" "ServiceProvider"]
                :service ["insurance coverage" "rent" "maintenance services"]
                :amount [3000 6000 9000 12000 18000]
                :months [3 6 12 24]}}

   ;; ==================== Level 2: Transformation Templates ====================
   ;; Key teaching point: Transformations have NO counterparty - internal processes
   ;; Detailed production templates use specific assertions with costs for JE derivation

   :production-tshirt-printing
   {:narrative-template "On {date}, SP prints {quantity} t-shirts using the following resources:
• {quantity} blank t-shirts from inventory @ ${tshirt-cost} each
• {ink-quantity} oz of ink @ ${ink-cost} per oz
• {labor-hours} hours of labor @ ${labor-rate}/hour
• The t-shirt printer (purchased earlier)

The printed t-shirts are now finished goods ready for sale."
    :required-assertions {:has-date {:date :date}
                          :consumes-inventory {:item "blank t-shirts" :quantity :quantity :unit-cost :tshirt-cost}
                          :consumes-supplies {:item "ink" :quantity :ink-quantity :unit-cost :ink-cost}
                          :consumes-labor {:hours :labor-hours :rate :labor-rate}
                          :is-allowed-by {:capacity "t-shirt-printer"}
                          :creates-finished-goods {:item "printed t-shirts" :quantity :quantity}}
    :correct-classification :production-full
    :level 2
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :quantity [10 25 50]
                :tshirt-cost [4 5 6]
                :ink-quantity [5 10 25]      ;; oz of ink used
                :ink-cost [2 2.50 3]         ;; cost per oz
                :labor-hours [1 2 4]
                :labor-rate [15 18 20]}}

   :production-simple-printing
   {:narrative-template "On {date}, SP's production team prints {quantity} custom t-shirts:
• {quantity} blank t-shirts @ ${tshirt-cost} each
• {labor-hours} hours of labor @ ${labor-rate}/hour
• Using the t-shirt printer

The printed t-shirts are now finished goods ready for sale."
    :required-assertions {:has-date {:date :date}
                          :consumes-inventory {:item "blank t-shirts" :quantity :quantity :unit-cost :tshirt-cost}
                          :consumes-labor {:hours :labor-hours :rate :labor-rate}
                          :is-allowed-by {:capacity "t-shirt-printer"}
                          :creates-finished-goods {:item "printed t-shirts" :quantity :quantity}}
    :correct-classification :production-inventory-labor
    :level 2
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :quantity [10 25 50 100]
                :tshirt-cost [4 5 6]
                :labor-hours [1 2 3 4]
                :labor-rate [15 18 20]}}

   ;; Simpler templates using generic assertions (for introductory problems)
   :production-raw-to-wip
   {:narrative-template "On {date}, SP takes {quantity} {raw-material} from inventory and begins the printing process. The blank shirts are now in production but not yet complete."
    :required-assertions {:has-date {:date :date}
                          :consumes {:unit "raw-materials"}
                          :creates {:unit "work-in-process"}}
    :correct-classification :production-raw-to-wip
    :level 2
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :quantity [10 25 50 100 200]
                :raw-material ["blank t-shirts" "unprinted shirts" "blank merchandise"]}}

   :production-wip-to-finished
   {:narrative-template "On {date}, SP completes the printing and packaging of {quantity} t-shirts. The shirts are now ready for sale."
    :required-assertions {:has-date {:date :date}
                          :consumes {:unit "work-in-process"}
                          :creates {:unit "finished-goods"}}
    :correct-classification :production-wip-to-finished
    :level 2
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :quantity [10 25 50 100 200]}}

   :production-direct
   {:narrative-template "On {date}, SP produces {quantity} {product}, converting raw materials into finished goods ready for sale.

This production is allowed by having the t-shirt printer you purchased earlier."
    :required-assertions {:has-date {:date :date}
                          :consumes {:unit "raw-materials"}
                          :creates {:unit "finished-goods"}
                          :is-allowed-by {:capacity "t-shirt-printer"}}
    :correct-classification :production-direct
    :level 2
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :quantity [10 25 50 100]
                :product ["printed t-shirts" "custom t-shirts" "branded shirts"]}}

   :production-labor
   {:narrative-template "On {date}, SP's production staff spends {hours} hours operating the printing equipment, applying their labor to the manufacturing process."
    :required-assertions {:has-date {:date :date}
                          :consumes {:unit "effort"}
                          :creates {:unit "work-in-process"}}
    :correct-classification :production-with-labor
    :level 2
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :hours [2 4 8 16 40]}}

   :supplies-used
   {:narrative-template "On {date}, SP uses {supplies} during the printing process. These supplies are consumed to produce the printed shirts."
    :required-assertions {:has-date {:date :date}
                          :consumes {:unit "supplies"}
                          :creates {:unit "work-in-process"}}
    :correct-classification :supplies-consumption
    :level 2
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :supplies ["ink cartridges" "specialty inks" "printing supplies" "packaging materials"]}}

   :design-creation
   {:narrative-template "On {date}, SP's designer spends {hours} hours creating a new {design-type} for the upcoming product line."
    :required-assertions {:has-date {:date :date}
                          :consumes {:unit "effort"}
                          :creates {:unit "intellectual-property"}}
    :correct-classification :design-creation
    :level 2
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :hours [4 8 16 24 40]
                :design-type ["t-shirt design" "logo" "artwork" "graphic design" "product concept"]}}

   :service-creation
   {:narrative-template "On {date}, SP's team spends {hours} hours providing {service} to fulfill a customer order."
    :required-assertions {:has-date {:date :date}
                          :consumes {:unit "effort"}
                          :creates {:unit "service-output"}}
    :correct-classification :service-delivery
    :level 2
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :hours [2 4 8 16]
                :service ["custom printing services" "design consultation" "rush order processing"]}}

   :capability-purchase
   {:narrative-template "On {date}, you purchase a T-shirt Printer from {vendor} for ${amount} cash, which allows you to print custom t-shirts in the future."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "monetary-unit"}
                          :receives {:unit "physical-unit" :physical-item "t-shirt-printer"}
                          :has-counterparty {:name :vendor}
                          :allows {}}
    :correct-classification :capability-acquisition
    :level 2
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :vendor ["PrinterWorld" "EquipmentDirect" "BusinessSupply"]
                :amount [3000]}}

   ;; ==================== Level 4: Legal & Regulatory Templates ====================
   ;; Key teaching point: Business events exist within legal frameworks

   :ucc-sale
   {:narrative-template "On {date}, SP sells {quantity} {product} to {customer} for ${amount}. This commercial transaction is conducted under the standard framework of the Uniform Commercial Code."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "physical-unit"}
                          :receives {:unit "monetary-unit"}
                          :has-counterparty {:name :customer}
                          :is-allowed-by {:framework "ucc"}}
    :correct-classification :sale-under-ucc
    :level 4
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :quantity [10 25 50 100]
                :product ["printed t-shirts" "custom merchandise" "branded apparel"]
                :customer ["RetailCo" "WholesaleBuyer" "CorporateClient"]
                :amount [500 1000 2500 5000]}}

   :hire-employee
   {:narrative-template "On {date}, SP hires {employee} as a {position}, agreeing to pay ${wage}/hour. This employment relationship is governed by federal and state employment laws including minimum wage requirements."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "monetary-unit"}
                          :receives {:unit "effort"}
                          :has-counterparty {:name :employee}
                          :is-allowed-by {:framework "employment-law"}
                          :is-required-by {:framework "employment-law"}}
    :correct-classification :employment-under-law
    :level 4
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :employee ["Alex" "Jordan" "Taylor" "Morgan"]
                :position ["production assistant" "designer" "sales associate" "warehouse worker"]
                :wage [15 18 20 25]}}

   :copyright-design
   {:narrative-template "On {date}, SP's designer spends {hours} hours creating an original {design-type}. As an original creative work, this design is automatically protected by copyright law."
    :required-assertions {:has-date {:date :date}
                          :consumes {:unit "effort"}
                          :creates {:unit "intellectual-property"}
                          :is-protected-by {:framework "copyright"}}
    :correct-classification :copyright-protected-creation
    :level 4
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :hours [8 16 24 40]
                :design-type ["t-shirt graphic" "logo design" "illustration" "pattern artwork"]}}

   :trademark-brand
   {:narrative-template "On {date}, SP develops a distinctive {brand-element} for the company. SP registers this as a trademark to protect the brand identity in commerce."
    :required-assertions {:has-date {:date :date}
                          :consumes {:unit "effort"}
                          :creates {:unit "intellectual-property"}
                          :is-protected-by {:framework "trademark"}}
    :correct-classification :trademark-protected-brand
    :level 4
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :brand-element ["company logo" "brand name" "product line name" "distinctive slogan"]}}

   :pay-taxes
   {:narrative-template "On {date}, SP calculates and pays ${amount} in {tax-type} as required by the tax code. Failure to pay would result in penalties and interest."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "monetary-unit"}
                          :is-required-by {:framework "tax-code"}}
    :correct-classification :tax-required-filing
    :level 4
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :amount [500 1000 2500 5000 10000]
                :tax-type ["quarterly estimated income taxes" "sales tax" "payroll taxes" "state franchise tax"]}}

   :business-license
   {:narrative-template "On {date}, SP pays ${amount} to obtain a {license-type}. This is required by {authority} regulations to operate legally."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "monetary-unit"}
                          :is-required-by {:framework "industry-regs"}}
    :correct-classification :regulatory-compliance
    :level 4
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :amount [100 250 500 1000]
                :license-type ["business license" "seller's permit" "occupational license" "zoning permit"]
                :authority ["city" "county" "state" "federal"]}}

   :form-llc
   {:narrative-template "On {date}, SP pays ${amount} to the state to form an LLC (Limited Liability Company). State business law enables this legal structure that protects the owner's personal assets."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "monetary-unit"}
                          :is-allowed-by {:framework "state-business-law"}}
    :correct-classification :business-formation
    :level 4
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :amount [100 150 250 500]}}

   :contract-sale
   {:narrative-template "On {date}, SP enters into a written contract with {customer} to provide {product} for ${amount}, with payment expected in {days} days. The contract is legally enforceable under contract law."
    :required-assertions {:has-date {:date :date}
                          :provides {:unit "physical-unit"}
                          :receives {:unit "monetary-unit"}
                          :has-counterparty {:name :customer}
                          :expects {:action "receives" :unit "monetary-unit"}
                          :is-protected-by {:framework "contract-law"}}
    :correct-classification :contract-protected-agreement
    :level 4
    :variables {:date ["2026-01-15" "2026-02-03" "2026-03-10" "2026-04-22" "2026-05-05"]
                :customer ["MajorRetailer" "CorporateClient" "WholesaleBuyer"]
                :product ["custom merchandise" "bulk t-shirt order" "branded apparel"]
                :amount [5000 10000 25000 50000]
                :days [30 60 90]}}})

(def month-names
  ["January" "February" "March" "April" "May" "June"
   "July" "August" "September" "October" "November" "December"])

(defn format-iso-date
  "Convert ISO date (2025-01-15) to readable format (January 15, 2025)."
  [iso-date]
  (if (and iso-date (re-matches #"\d{4}-\d{2}-\d{2}" iso-date))
    (let [[year month day] (clojure.string/split iso-date #"-")
          month-idx (dec (Integer/parseInt month))
          day-num (Integer/parseInt day)]
      (str (nth month-names month-idx) " " day-num ", " year))
    iso-date))

(defn apply-template
  "Replace {variables} in template string with actual values.
   Dates in ISO format are converted to readable format."
  [template vars]
  (reduce (fn [s [k v]]
            (let [display-value (if (= k :date) (format-iso-date v) v)]
              (clojure.string/replace s (str "{" (name k) "}") (str display-value))))
          template
          vars))

(defn resolve-assertion-values
  "Resolve variable references in required-assertions.
   When a value is a keyword that matches a variable key, substitute it.
   Otherwise, keep the value as-is.
   Handles nested maps (assertions with multiple parameters)."
  [required-assertions vars]
  (letfn [(resolve-value [v]
            (if (and (keyword? v) (contains? vars v))
              (get vars v)
              v))
          (resolve-map [m]
            (into {} (for [[k v] m]
                       [k (cond
                            (map? v) (resolve-map v)
                            :else (resolve-value v))])))]
    (if (map? required-assertions)
      (into {} (for [[assertion-key assertion-params] required-assertions]
                 [assertion-key (if (map? assertion-params)
                                  (resolve-map assertion-params)
                                  (resolve-value assertion-params))]))
      ;; For set-based assertions (old format), return as-is
      required-assertions)))

(defn- select-paired-variables
  "Select variables with same-length arrays using the same index.
   This ensures correlated values like quantity/amount stay paired.
   Variables with unique lengths are selected independently."
  [variables]
  (let [;; Group variable keys by the length of their option arrays
        by-length (group-by (fn [[_k options]] (count options)) variables)
        ;; For each length group, pick a random index once and apply to all
        selected (for [[length vars-in-group] by-length
                       :let [idx (rand-int length)]]
                   (for [[k options] vars-in-group]
                     [k (nth options idx)]))]
    (into {} (apply concat selected))))

(defn generate-problem
  "Generate a random problem at the specified level.
   Can generate forward (narrative -> assertions), reverse (journal entry -> assertions),
   or construct (narrative -> create journal entry) problems."
  [level & {:keys [problem-type show-assertions] :or {problem-type :forward show-assertions false}}]
  (let [available-templates (filter #(<= (:level (val %)) level) transaction-templates)
        [template-key template] (rand-nth (seq available-templates))
        ;; Use indexed selection for same-length variable arrays to keep values paired
        vars (select-paired-variables (:variables template))
        narrative (apply-template (:narrative-template template) vars)
        classification (get classifications (:correct-classification template))

        ;; For reverse problems, create minimal context and inject amounts into journal entries
        context (when (= problem-type :reverse)
                  {:counterparty (or (:vendor vars) (:customer vars))
                   :date "Transaction date"})  ;; Could randomize dates later

        ;; Add amounts to journal entry for reverse problems
        journal-entry (when (= problem-type :reverse)
                        (let [amount (:amount vars)]
                          (if amount
                            (mapv (fn [entry]
                                   {:debit (str (:debit entry) " $" (format "%,d" amount))
                                    :credit (str (:credit entry) " $" (format "%,d" amount))})
                                 (:journal-entry classification))
                            (:journal-entry classification))))

        ;; Resolve variable references in required-assertions
        resolved-assertions (resolve-assertion-values (:required-assertions template) vars)

        base-problem {:id (str (random-uuid))
                      :template template-key
                      :correct-assertions resolved-assertions
                      :correct-classification (:correct-classification template)
                      :level level                        ; Student's current level
                      :template-level (:level template)   ; Template's difficulty level
                      :variables vars
                      :problem-type problem-type}]

    (cond
      (= problem-type :reverse)
      (assoc base-problem
             :journal-entry journal-entry
             :context context
             :narrative narrative)  ;; Store narrative for feedback display

      (= problem-type :construct)
      (assoc base-problem
             :narrative narrative
             :correct-journal-entry (:journal-entry classification)
             :correct-amount (:amount vars)
             :show-assertions? show-assertions
             :available-accounts (get-accounts-for-level level))

      :else  ;; forward
      (assoc base-problem
             :narrative narrative))))

(defn- strip-amount-from-account
  "Removes dollar amount from account name (e.g., 'Cash $1,500' -> 'Cash')."
  [account-str]
  (-> account-str
      (clojure.string/split #" \$")
      first
      clojure.string/trim))

(defn- classify-account-type
  "Determines the account type (asset, liability, revenue, expense) from account name."
  [account-name]
  (cond
    (re-find #"(?i)(cash|receivable|inventory|equipment|prepaid|asset)" account-name) :asset
    (re-find #"(?i)(payable|notes payable|liability)" account-name) :liability
    (re-find #"(?i)revenue" account-name) :revenue
    (re-find #"(?i)(expense|cost)" account-name) :expense
    :else :unknown))

(defn- explain-debit-effect
  "Explains what debiting an account means."
  [account-name account-type]
  (case account-type
    :asset (str "Debiting " account-name " would increase this asset")
    :liability (str "Debiting " account-name " would decrease this liability")
    :revenue (str "Debiting " account-name " would decrease revenue")
    :expense (str "Debiting " account-name " would increase this expense")
    (str "Debiting " account-name)))

(defn- explain-credit-effect
  "Explains what crediting an account means."
  [account-name account-type]
  (case account-type
    :asset (str "Crediting " account-name " would decrease this asset")
    :liability (str "Crediting " account-name " would increase this liability")
    :revenue (str "Crediting " account-name " would increase revenue")
    :expense (str "Crediting " account-name " would decrease this expense")
    (str "Crediting " account-name)))

(defn- generate-debit-hints
  "Generates specific hints for incorrect debit account selection."
  [student-account correct-account assertions]
  (let [student-type (classify-account-type student-account)
        correct-type (classify-account-type correct-account)
        hints [(str "You selected " student-account ". "
                   (explain-debit-effect student-account student-type) ".")]]
    (cond-> hints
      ;; If assertions show what entity receives
      (:receives assertions)
      (conj (let [unit (get-in assertions [:receives :unit])
                  physical-item (get-in assertions [:receives :physical-item])]
              (str "But the assertions show the entity receives "
                   (cond
                     (and (= unit "physical-unit") physical-item)
                     ;; Use physical-item-hint-label derived from physical-items
                     (physical-item-hint-label physical-item)
                     (= unit "physical-unit") "a physical asset"
                     (= unit "monetary-unit") "money (cash)"
                     :else "something")
                   ". What account represents what's being received?")))

      ;; If they selected revenue/expense for an exchange transaction
      (and (#{:revenue :expense} student-type)
           (:receives assertions)
           (:provides assertions))
      (conj "This is an exchange transaction (providing one thing for another), not a revenue or expense transaction."))))

(defn- generate-credit-hints
  "Generates specific hints for incorrect credit account selection."
  [student-account correct-account assertions]
  (let [student-type (classify-account-type student-account)
        correct-type (classify-account-type correct-account)
        hints [(str "You selected " student-account ". "
                   (explain-credit-effect student-account student-type) ".")]]
    (cond-> hints
      ;; If assertions show what entity provides
      (:provides assertions)
      (conj (let [unit (get-in assertions [:provides :unit])]
              (str "But the assertions show the entity provides "
                   (cond
                     (= unit "physical-unit") "a physical asset"
                     (= unit "monetary-unit") "money (cash)"
                     :else "something")
                   ". What account represents what's being given up?")))

      ;; If assertions show a future obligation is created
      (:requires assertions)
      (conj (let [action (get-in assertions [:requires :action])]
              (str "The assertions show this creates a future obligation"
                   (when action (str " to " (name action)))
                   ". What account represents owing something?")))

      ;; If they selected revenue but no revenue assertions
      (and (= student-type :revenue)
           (not (:expects assertions)))
      (conj "Revenue is typically credited when earning income, but check the assertions - is this an income-generating transaction?"))))

(defn validate-journal-entry
  "Validates student's constructed journal entry against correct answer.
   Returns {:correct? boolean :feedback string :status keyword}"
  [student-je correct-je-entries correct-assertions]
  (let [correct-entry (first correct-je-entries)  ;; Assume single-entry JEs for now
        correct-debit (strip-amount-from-account (:debit correct-entry))
        correct-credit (strip-amount-from-account (:credit correct-entry))
        student-debit (:debit-account student-je)
        student-credit (:credit-account student-je)
        student-amount (when-let [amt (:amount student-je)]
                        (if (string? amt) (Integer/parseInt amt) amt))
        correct-amount (:correct-amount student-je)]  ;; Passed from problem

    (cond
      (not= student-debit correct-debit)
      {:correct? false
       :status :incorrect
       :feedback (str "Debit account incorrect. You selected \"" student-debit
                     "\", but this transaction affects a different account on the debit side.")
       :hints (generate-debit-hints student-debit correct-debit correct-assertions)}

      (not= student-credit correct-credit)
      {:correct? false
       :status :incorrect
       :feedback (str "Credit account incorrect. You selected \"" student-credit
                     "\", but this transaction affects a different account on the credit side.")
       :hints (generate-credit-hints student-credit correct-credit correct-assertions)}

      (and student-amount correct-amount (not= student-amount correct-amount))
      {:correct? false
       :status :incorrect
       :feedback (str "Amount is incorrect. You entered $" student-amount
                     ", but the transaction amount is different.")
       :hints ["Check the narrative for the transaction amount."]}

      :else
      {:correct? true
       :status :correct
       :feedback "Correct! Your journal entry accurately records this transaction."
       :classification correct-entry})))
