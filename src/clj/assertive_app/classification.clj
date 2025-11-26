(ns assertive-app.classification
  "Core classification engine for matching assertions to transaction types.")

;; Helper function to create assertion code -> label lookup
(defn- assertion-code-to-label
  "Creates a map from assertion codes to their human-readable labels"
  [assertions-map]
  (into {}
        (for [[_domain assertions] assertions-map
              assertion assertions]
          [(:code assertion) (:label assertion)])))

;; Assertion definitions - aligned with research framework
(def available-assertions
  {:exchange
   [{:code :provides
     :label "Provides"
     :description "The entity gives something in an exchange"
     :level 0
     :domain :exchange
     :parameterized true
     :parameters {:unit {:type :dropdown
                         :label "Unit type"
                         :options [{:value "monetary-unit" :label "Cash/Money"}
                                   {:value "physical-unit" :label "Goods/Services"}
                                   {:value "time-unit" :label "Time"}
                                   {:value "effort-unit" :label "Effort/Labor"}]}
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
                         :options [{:value "monetary-unit" :label "Cash/Money"}
                                   {:value "physical-unit" :label "Goods/Services"}
                                   {:value "time-unit" :label "Time"}
                                   {:value "effort-unit" :label "Effort/Labor"}]}
                  :asset-type {:type :dropdown
                               :label "Asset type"
                               :options [{:value "inventory" :label "Inventory (for resale/production)"}
                                         {:value "equipment" :label "Equipment (long-term use)"}]
                               :optional true}
                  :quantity {:type :number
                             :label "Quantity"
                             :optional true}}}

    {:code :has-counterparty
     :label "Has Counterparty"
     :description "Identifies the other party to the transaction"
     :level 0
     :domain :exchange}]

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
                         :options [{:value "monetary-unit" :label "Cash/Money"}
                                   {:value "physical-unit" :label "Goods/Services"}
                                   {:value "time-unit" :label "Time"}
                                   {:value "effort-unit" :label "Effort/Labor"}]}
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
                         :options [{:value "monetary-unit" :label "Cash/Money"}
                                   {:value "physical-unit" :label "Goods/Services"}
                                   {:value "time-unit" :label "Time"}
                                   {:value "effort-unit" :label "Effort/Labor"}]}
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
   [{:code :consumes
     :label "Consumes"
     :description "Uses up resources in an internal transformation"
     :level 2
     :domain :transformation}

    {:code :creates
     :label "Creates"
     :description "Produces new resources through internal transformation"
     :level 2
     :domain :transformation}]

   :legal-regulatory
   [{:code :is-allowed-by
     :label "Is Allowed By"
     :description "References the legal/regulatory framework that permits this event"
     :level 3
     :domain :legal-regulatory}

    {:code :is-required-by
     :label "Is Required By"
     :description "References the legal/regulatory framework that mandates this event"
     :level 3
     :domain :legal-regulatory}

    {:code :is-protected-by
     :label "Is Protected By"
     :description "References the legal/regulatory framework that protects this event"
     :level 3
     :domain :legal-regulatory}]

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
     :domain :state-modification}]})

;; Create lookup map from assertion codes to labels
(def assertion-labels
  (assertion-code-to-label available-assertions))

;; Available accounts for journal entry construction, organized by level and type
(def accounts-by-level
  "Accounts available at each level for JE construction problems.
   Progressively unlocks more complex accounts as students advance."
  {0 {:asset ["Cash" "Inventory" "Equipment" "Prepaid Expense"]
      :liability ["Accounts Payable"]
      :revenue ["Revenue"]
      :expense ["Cost of Goods Sold" "Expense"]}

   1 {:asset ["Cash" "Accounts Receivable" "Inventory" "Equipment" "Prepaid Expense"]
      :liability ["Accounts Payable" "Notes Payable"]
      :revenue ["Revenue"]
      :expense ["Cost of Goods Sold" "Expense"]}

   2 {:asset ["Cash" "Accounts Receivable" "Inventory" "Raw Materials"
              "Work in Process" "Finished Goods" "Equipment" "Prepaid Expense"]
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
   asset-type is optional (e.g., 'inventory' or 'equipment')"
  [description journal-entry & {:keys [provides-unit receives-unit asset-type note examples level]
                                 :or {level 0}}]
  (let [base {:required #{:provides :receives :has-counterparty}
              :prohibited #{:requires :expects :allows}  ;; Cash exchanges don't involve capability recognition
              :description description
              :journal-entry journal-entry
              :level level}
        params (merge {}
                     (when provides-unit {:provides {:unit provides-unit}})
                     (when receives-unit
                       {:receives (merge {:unit receives-unit}
                                        (when asset-type {:asset-type asset-type}))}))]
    (cond-> base
      (seq params) (assoc :required-parameters params)
      note (assoc :note note)
      examples (assoc :examples examples))))

(defn credit-transaction
  "Template for credit transactions (receive/provide now, obligation/expectation for future).
   future-assertion is either :requires (obligation) or :expects (expectation)"
  [description journal-entry future-assertion & {:keys [present-action present-unit
                                                         future-action future-unit
                                                         asset-type note examples level]
                                                  :or {level 1}}]
  (let [present-assertion (if (= present-action :provides) :provides :receives)
        base {:required #{present-assertion :has-counterparty future-assertion}
              :prohibited #{}
              :description description
              :journal-entry journal-entry
              :level level}
        params (merge {}
                     (when present-unit
                       {present-assertion (merge {:unit present-unit}
                                                 (when asset-type {:asset-type asset-type}))})
                     (when (and future-action future-unit)
                       {future-assertion {:action future-action :unit future-unit}}))]
    (cond-> base
      (seq params) (assoc :required-parameters params)
      note (assoc :note note)
      examples (assoc :examples examples))))

;; Classification rules - using research assertions
(def classifications
  {:cash-sale
   (cash-exchange
     "Cash sale (provide goods/services, receive cash)"
     [{:debit "Cash" :credit "Revenue"}]
     :provides-unit "physical-unit"
     :receives-unit "monetary-unit"
     :note "Simplified - in practice would also record: DR: Cost of Goods Sold, CR: Inventory"
     :examples ["SP sells printed t-shirt for $25 cash"
                "SP provides consulting services for $1,000 cash"])

   :cash-inventory-purchase
   (cash-exchange
     "Cash purchase of inventory (provide cash, receive inventory for resale)"
     [{:debit "Inventory" :credit "Cash"}]
     :provides-unit "monetary-unit"
     :receives-unit "physical-unit"
     :asset-type "inventory"
     :examples ["SP purchases blank t-shirts for $5,000 cash"
                "SP purchases ink supplies for $1,200 cash"])

   :cash-equipment-purchase
   (cash-exchange
     "Cash purchase of equipment (provide cash, receive long-term asset)"
     [{:debit "Equipment (Fixed Asset)" :credit "Cash"}]
     :provides-unit "monetary-unit"
     :receives-unit "physical-unit"
     :asset-type "equipment"
     :examples ["SP purchases t-shirt printer for $12,000 cash"
                "SP purchases office furniture for $3,000 cash"])

   :inventory-purchase-on-credit
   (credit-transaction
     "Credit purchase of inventory (receive inventory now, obligation to pay later)"
     [{:debit "Inventory" :credit "Accounts Payable"}]
     :requires
     :present-action :receives
     :present-unit "physical-unit"
     :asset-type "inventory"
     :future-action "provides"
     :future-unit "monetary-unit"
     :note "SP receives inventory immediately but requires (is obligated to) provide cash in the future."
     :examples ["SP receives 200 blank t-shirts, requires payment in 60 days"
                "SP receives ink supplies, requires payment of $800 in 30 days"])

   :equipment-purchase-on-credit
   (credit-transaction
     "Credit purchase of equipment (receive equipment now, obligation to pay later)"
     [{:debit "Equipment (Fixed Asset)" :credit "Accounts Payable"}]
     :requires
     :present-action :receives
     :present-unit "physical-unit"
     :asset-type "equipment"
     :future-action "provides"
     :future-unit "monetary-unit"
     :note "SP receives equipment immediately but requires (is obligated to) provide cash in the future."
     :examples ["SP receives t-shirt printer, requires future payment of $12,000"
                "SP receives office furniture, requires payment in 90 days"])

   :sale-on-credit
   (credit-transaction
     "Credit sale (provide goods now, expect payment later)"
     [{:debit "Accounts Receivable" :credit "Revenue"}]
     :expects
     :present-action :provides
     :present-unit "physical-unit"
     :future-action "receives"
     :future-unit "monetary-unit"
     :note "SP provides goods immediately and expects to receive cash in the future (uncertain - customer may not pay)."
     :examples ["SP provides printed t-shirts, expects payment in 30 days"
                "SP provides custom apparel, expects $2,500 payment later"])

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

   :production
   {:required #{:consumes :creates}
    :prohibited #{:has-counterparty}
    :description "Internal transformation (consume inputs, create outputs)"
    :journal-entry [{:debit "Work in Process" :credit "Raw Materials"}]
    :examples ["SP consumes blank t-shirt and ink to create printed t-shirt"]}

   :expense-recognition
   {:required #{:consumes :provides}
    :prohibited #{:creates :receives}
    :description "Consume benefits in operations with payment"
    :journal-entry [{:debit "Expense" :credit "Cash/Payable"}]
    :examples ["SP consumes utilities to operate the business"]}

   :capability-acquisition
   {:required #{:provides :receives :has-counterparty :allows}
    :required-parameters {:provides {:unit "monetary-unit"}
                          :receives {:unit "physical-unit" :asset-type "equipment"}}
    :prohibited #{}
    :description "Equipment purchase with explicit capability recognition"
    :journal-entry [{:debit "Equipment" :credit "Cash"}]
    :note "Like cash equipment purchase, but student explicitly recognizes the capability created."
    :examples ["SP purchases printer that allows future shirt printing"]
    :level 2}})

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
                   ", you need to specify: "
                   (clojure.string/join ", " (for [[param-key param-value] params]
                                               (str param-key " = " param-value)))))))))

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

    {:exact-matches exact-matches
     :closest closest
     :feedback (cond
                 (seq exact-matches)
                 (let [match-key (first exact-matches)
                       classification (get classifications match-key)
                       ;; Check if this is the correct classification
                       is-correct-match (or (nil? correct-classification)
                                           (= match-key correct-classification))
                       status (if is-correct-match :correct :incorrect)]
                   {:status status
                    :message (if is-correct-match
                               (str "Correct! This is: " (:description classification))
                               (str "Your assertions describe: " (:description classification)
                                    " But that's not what this transaction is. Re-read the narrative."))
                    :classification (when is-correct-match
                                      ;; Augment journal entry with quantities from parameters
                                      (update classification :journal-entry
                                              augment-journal-entry assertions-map))
                    :hints (when (not is-correct-match)
                             (let [matched-desc (:description classification)
                                   correct-desc (get-in classifications [correct-classification :description])]
                               [(str "Your assertions describe: " matched-desc)
                                (str "But this transaction is: " correct-desc)
                                "Check what the entity is providing vs. receiving."]))})

                 closest
                 (let [closest-classification (get classifications (:type closest))
                       ;; Generate hints against correct classification if provided, otherwise closest
                       hint-target (or correct-classification (:type closest))
                       hint-data (generate-dynamic-hints assertions-map hint-target)
                       dynamic-hints (format-hints hint-data)]
                   {:status :incorrect
                    :message (str "Your assertions are closest to: "
                                 (:description closest-classification))
                    :hints dynamic-hints})

                 :else
                 {:status :indeterminate
                  :message "Unable to classify with current assertions. Try reviewing the transaction."})}))

;; Problem generation - using research assertions
(def transaction-templates
  {:cash-inventory-purchase
   {:narrative-template "SP purchases {inventory-type} from {vendor} for ${amount} cash."
    :required-assertions {:provides {:unit "monetary-unit"}
                          :receives {:unit "physical-unit" :asset-type "inventory"}
                          :has-counterparty {}}
    :correct-classification :cash-inventory-purchase
    :level 0
    :variables {:inventory-type ["blank t-shirts" "ink supplies" "packaging materials" "raw materials"]
                :vendor ["Vendor-A" "TShirtVendor" "SupplyCo"]
                :amount [500 1000 2000 3000 5000]}}

   :cash-equipment-purchase
   {:narrative-template "SP purchases {equipment-type} from {vendor} for ${amount} cash."
    :required-assertions {:provides {:unit "monetary-unit"}
                          :receives {:unit "physical-unit" :asset-type "equipment"}
                          :has-counterparty {}}
    :correct-classification :cash-equipment-purchase
    :level 0
    :variables {:equipment-type ["a t-shirt printer" "office furniture" "a computer system" "production equipment"]
                :vendor ["OfficeSupplyCo" "EquipmentVendor" "TechSupply"]
                :amount [2000 5000 8000 10000 12000 15000]}}

   :cash-sale
   {:narrative-template "SP sells {product} to {customer} for ${amount} cash."
    :required-assertions {:provides {:unit "physical-unit"}
                          :receives {:unit "monetary-unit"}
                          :has-counterparty {}}
    :correct-classification :cash-sale
    :level 0
    :variables {:product ["printed t-shirts" "custom apparel" "merchandise"]
                :customer ["Customer-A" "Customer-B" "RetailStore-X"]
                :amount [500 1000 2500 5000]}}

   :credit-inventory-purchase
   {:narrative-template "SP receives {inventory-type} from {vendor}, agreeing to pay ${amount} in {days} days."
    :required-assertions {:receives {:unit "physical-unit" :asset-type "inventory"}
                          :has-counterparty {}
                          :requires {:action "provides" :unit "monetary-unit"}}
    :correct-classification :inventory-purchase-on-credit
    :level 1
    :variables {:inventory-type ["blank t-shirts" "ink supplies" "packaging materials" "raw materials"]
                :vendor ["Vendor-A" "TShirtVendor" "InkVendor"]
                :amount [500 1000 2000 3000 5000]
                :days [30 60 90]}}

   :credit-equipment-purchase
   {:narrative-template "SP receives {equipment-type} from {vendor}, agreeing to pay ${amount} in {days} days."
    :required-assertions {:receives {:unit "physical-unit" :asset-type "equipment"}
                          :has-counterparty {}
                          :requires {:action "provides" :unit "monetary-unit"}}
    :correct-classification :equipment-purchase-on-credit
    :level 1
    :variables {:equipment-type ["a t-shirt printer" "office furniture" "a computer system" "production equipment"]
                :vendor ["OfficeSupplyCo" "EquipmentVendor" "TechSupply"]
                :amount [5000 8000 10000 12000 15000]
                :days [30 60 90]}}

   :credit-sale
   {:narrative-template "SP provides {product} to {customer}, expecting payment of ${amount} in {days} days."
    :required-assertions {:provides {:unit "physical-unit"}
                          :has-counterparty {}
                          :expects {:action "receives" :unit "monetary-unit"}}
    :correct-classification :sale-on-credit
    :level 1
    :variables {:product ["printed t-shirts" "custom designs" "merchandise"]
                :customer ["Customer-A" "RegularCustomer-001" "RetailStore-X"]
                :amount [500 1000 2500 5000 7500]
                :days [30 60 90]}}

   :prepayment
   {:narrative-template "SP receives ${amount} advance payment from {customer} for {service} to be delivered in {days} days."
    :required-assertions {:receives {:unit "monetary-unit"}
                          :has-counterparty {}
                          :requires {:action "provides" :unit "physical-unit"}}
    :correct-classification :deferred-revenue
    :level 1
    :variables {:customer ["Customer-A" "RetailStore-X" "CorporateClient-001"]
                :service ["custom t-shirt printing services" "merchandise fulfillment" "a bulk order"]
                :amount [2000 5000 10000 15000 20000]
                :days [30 60 90 180]}}

   :prepaid-expense-transaction
   {:narrative-template "SP pays ${amount} to {vendor} for {service} covering the next {months} months."
    :required-assertions {:provides {:unit "monetary-unit"}
                          :has-counterparty {}
                          :expects {:action "receives" :unit "physical-unit"}}
    :correct-classification :prepaid-expense
    :level 1
    :variables {:vendor ["InsuranceCo" "Landlord" "ServiceProvider"]
                :service ["insurance coverage" "rent" "maintenance services"]
                :amount [3000 6000 9000 12000 18000]
                :months [3 6 12 24]}}

   :production-event
   {:narrative-template "SP uses {input} and ink to create {output}."
    :required-assertions #{:consumes :creates}
    :level 2
    :variables {:input ["blank t-shirts" "unprinted inventory"]
                :output ["printed t-shirts" "finished products"]}}

   :capability-purchase
   {:narrative-template "SP purchases {equipment} for ${amount}, which allows SP to {capability}."
    :required-assertions #{:provides :receives :has-counterparty :allows}
    :level 2
    :variables {:equipment ["a t-shirt printer" "production equipment" "an ink cartridge system"]
                :capability ["print custom t-shirts" "produce merchandise" "create designs"]
                :amount [5000 8000 12000 15000]}}})

(defn apply-template
  "Replace {variables} in template string with actual values."
  [template vars]
  (reduce (fn [s [k v]]
            (clojure.string/replace s (str "{" (name k) "}") (str v)))
          template
          vars))

(defn generate-problem
  "Generate a random problem at the specified level.
   Can generate forward (narrative -> assertions), reverse (journal entry -> assertions),
   or construct (narrative -> create journal entry) problems."
  [level & {:keys [problem-type show-assertions] :or {problem-type :forward show-assertions false}}]
  (let [available-templates (filter #(<= (:level (val %)) level) transaction-templates)
        [template-key template] (rand-nth (seq available-templates))
        vars (into {} (for [[k options] (:variables template)]
                        [k (rand-nth options)]))
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

        base-problem {:id (str (random-uuid))
                      :template template-key
                      :correct-assertions (:required-assertions template)
                      :correct-classification (:correct-classification template)
                      :level level
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
                  asset-type (get-in assertions [:receives :asset-type])]
              (str "But the assertions show the entity receives "
                   (cond
                     (= unit "physical-unit") (str "a physical asset"
                                                  (when asset-type (str " (" (name asset-type) ")")))
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
