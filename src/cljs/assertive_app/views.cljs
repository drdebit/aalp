(ns assertive-app.views
  "Reagent components for the UI."
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [assertive-app.state :as state]
            [assertive-app.api :as api]
            [assertive-app.tutorials :as tutorials]))

;; ==================== Authentication Components ====================

(defn login-view []
  (let [email-input (r/atom "")
        submitting? (r/atom false)]
    (fn []
      [:div.login-container
       [:div.login-box
        [:h1 "Assertive Accounting"]
        [:h2 "Learning Platform"]
        [:p.login-subtitle "Learn accounting through logical assertions"]
        (when-let [error (state/login-error)]
          [:div.login-error error])
        [:form.login-form
         {:on-submit (fn [e]
                       (.preventDefault e)
                       (when (and (seq @email-input) (not @submitting?))
                         (reset! submitting? true)
                         (api/login! @email-input)))}
         [:input.login-input
          {:type "email"
           :placeholder "Enter your email"
           :value @email-input
           :disabled @submitting?
           :on-change #(reset! email-input (.. % -target -value))}]
         [:button.login-button
          {:type "submit"
           :disabled (or (empty? @email-input) @submitting?)}
          (if @submitting? "Signing in..." "Sign In")]]
        [:p.login-note "Use your school email to track your progress"]]])))

;; ==================== Progress Components ====================

(defn progress-dot [filled?]
  [:span.progress-dot {:class (when filled? "filled")}])

(defn level-progress-bar [level]
  (let [progress (state/progress)
        ;; JSON keys become keywords like :0, :1 when parsed with keywords? true
        level-key (keyword (str level))
        level-stats (get-in progress [:level-stats level-key] {})
        correct-count (min 5 (:correct-count level-stats 0))
        unlocked-next? (:unlocked-next level-stats false)]
    [:div.level-progress
     [:span.level-label (str "L" level ":")]
     [:div.progress-dots
      (for [i (range 5)]
        ^{:key i}
        [progress-dot (< i correct-count)])]
     (when unlocked-next?
       [:span.unlock-badge "✓"])]))

(defn progress-panel []
  (let [unlocked (state/unlocked-levels)
        current (state/current-level)]
    [:div.progress-panel
     [:div.progress-levels
      (for [level [0 1 2 3]]
        ^{:key level}
        [:div.progress-level-row {:class (when (= level current) "current")}
         [level-progress-bar level]
         (when-not (contains? unlocked level)
           [:span.locked-icon "🔒"])])]]))

(defn user-header []
  (let [user (state/user)]
    [:div.user-header
     [:div.user-info
      [:span.user-email (:email user)]
      [progress-panel]]
     [:button.logout-button
      {:on-click #(api/logout!)}
      "Sign Out"]]))

;; ==================== Level Selector ====================

(defn level-selector []
  (let [current-level (state/current-level)
        current-problem-type (state/problem-type)
        unlocked (state/unlocked-levels)]
    [:div.level-selector
     [:label "Level: "]
     [:select {:value current-level
               :on-change #(let [new-level (js/parseInt (.. % -target -value))]
                            (when (contains? unlocked new-level)
                              (swap! state/app-state assoc :current-level new-level)
                              (api/fetch-assertions! new-level)
                              (api/fetch-problem! new-level)))}
      [:option {:value 0 :disabled (not (contains? unlocked 0))}
       (str "0 - Cash Purchases" (when-not (contains? unlocked 0) " 🔒"))]
      [:option {:value 1 :disabled (not (contains? unlocked 1))}
       (str "1 - Credit Purchases" (when-not (contains? unlocked 1) " 🔒"))]
      [:option {:value 2 :disabled (not (contains? unlocked 2))}
       (str "2 - Transformations" (when-not (contains? unlocked 2) " 🔒"))]
      [:option {:value 3 :disabled (not (contains? unlocked 3))}
       (str "3 - Sales & Recognition" (when-not (contains? unlocked 3) " 🔒"))]
      [:option {:value 4 :disabled (not (contains? unlocked 4))}
       (str "4 - Legal/Regulatory" (when-not (contains? unlocked 4) " 🔒"))]]
     [:label {:style {:margin-left "20px"}}
      "Mode: "]
     [:select {:value current-problem-type
               :on-change #(let [new-type (.. % -target -value)]
                            (swap! state/app-state assoc :problem-type new-type)
                            (state/clear-je-fields!)  ;; Clear JE fields when switching modes
                            (api/fetch-problem! current-level))}
      [:option {:value "forward"} "Forward (narrative → assertions)"]
      [:option {:value "reverse"} "Reverse (JE → assertions)"]
      [:option {:value "construct"} "Construct (create JE)"]]]))

;; ==================== Account-Assertion Hints for Construct View ====================

(def account-assertion-hints
  "Maps accounts to the assertions that typically link to them."
  {"Cash" {:debit "Receives (monetary-unit)" :credit "Provides (monetary-unit)"}
   "Inventory" {:debit "Receives (physical-unit, inventory)"}
   "Equipment (Fixed Asset)" {:debit "Receives (physical-unit, equipment)"}
   "Accounts Payable" {:credit "Requires (provides monetary-unit)"}
   "Accounts Receivable" {:debit "Expects (receives monetary-unit)"}
   "Revenue" {:credit "Provides (physical-unit)"}
   "Service Revenue" {:credit "Provides (physical-unit)"}
   "Deferred Revenue (Liability)" {:credit "Requires (provides physical-unit)"}
   "Prepaid Expense (Asset)" {:debit "Expects (receives physical-unit)"}
   "Raw Materials" {:debit "Receives (physical-unit)"}
   "Work in Process" {:debit "Creates (physical-unit)"}
   "Finished Goods" {:debit "Creates (physical-unit)"}
   "Cost of Goods Sold" {:debit "Consumes (physical-unit)"}
   "Expense" {:debit "Consumes/Provides value"}
   "Wage Expense" {:debit "Receives labor"}
   "Wages Payable" {:credit "Requires payment"}
   "Notes Payable" {:credit "Requires (provides monetary-unit)"}})

(defn- get-account-hint
  "Get the assertion hint for an account and effect (:debit or :credit)."
  [account effect]
  (get-in account-assertion-hints [account effect]))

(defn je-constructor [problem & {:keys [disabled?] :or {disabled? false}}]
  (let [accounts (:available-accounts problem)
        all-accounts (vec (concat (:asset accounts [])
                                 (:liability accounts [])
                                 (:revenue accounts [])
                                 (:expense accounts [])))
        debit-acct (:je-debit-account @state/app-state)
        credit-acct (:je-credit-account @state/app-state)
        amount (:je-amount @state/app-state)]
    [:div.je-constructor
     [:h3 (if disabled? "Your Journal Entry:" "Create the Journal Entry:")]
     [:div.je-row
      [:label "Debit:"]
      [:select.account-select
       {:value (or debit-acct "")
        :disabled disabled?
        :on-change #(state/set-je-debit-account! (.. % -target -value))}
       [:option {:value ""} "-- Select Debit Account --"]
       (for [account all-accounts]
         (let [hint (get-account-hint account :debit)]
           ^{:key (str "debit-" account)}
           [:option {:value account
                     :title (when hint (str "← " hint))}
            (if hint
              (str account " ← " hint)
              account)]))]
      [:input.amount-input
       {:type "number"
        :placeholder "Amount"
        :value (or amount "")
        :disabled disabled?
        :on-change #(state/set-je-amount! (.. % -target -value))}]]

     [:div.je-row
      [:label "Credit:"]
      [:select.account-select
       {:value (or credit-acct "")
        :disabled disabled?
        :on-change #(state/set-je-credit-account! (.. % -target -value))}
       [:option {:value ""} "-- Select Credit Account --"]
       (for [account all-accounts]
         (let [hint (get-account-hint account :credit)]
           ^{:key (str "credit-" account)}
           [:option {:value account
                     :title (when hint (str "← " hint))}
            (if hint
              (str account " ← " hint)
              account)]))]]]))

(defn narrative-panel []
  (let [problem (state/current-problem)
        is-reverse? (= (:problem-type problem) "reverse")
        is-construct? (= (:problem-type problem) "construct")]
    [:div.column.narrative-panel
     [:h2 (cond
            is-reverse? "Journal Entry"
            is-construct? "Transaction"
            :else "Transaction")]
     [level-selector]
     (if problem
       (cond
         is-reverse?
         ;; Reverse problem: Show journal entry + minimal context
         [:div.reverse-problem
          (when-let [context (:context problem)]
            [:div.context
             (when (:counterparty context)
               [:p [:strong "Counterparty: "] (:counterparty context)])
             (when (:date context)
               [:p [:strong "Date: "] (:date context)])])
          [:div.journal-entry
           [:h3 "Journal Entry:"]
           (for [entry (:journal-entry problem)]
             ^{:key (str (:debit entry) "-" (:credit entry))}
             [:div.entry
              [:div.debit [:strong "DR: "] (:debit entry)]
              [:div.credit [:strong "CR: "] (:credit entry)]])]]

         is-construct?
         ;; Construct problem: Show narrative only (JE constructor moved to feedback panel)
         [:div.construct-problem
          [:div.narrative
           [:p (:narrative problem)]]]

         :else
         ;; Forward problem: Show narrative as usual
         [:div.narrative
          [:p (:narrative problem)]])
       [:p.loading "Loading problem..."])]))

(defn parameter-input [assertion-code param-key param-spec current-value]
  (let [param-type (:type param-spec)]
    (cond
      (or (= param-type "dropdown") (= param-type :dropdown))
      [:select.parameter-input
       {:value (or current-value "")
        :on-change #(state/update-assertion-parameter!
                     assertion-code
                     param-key
                     (.. % -target -value))}
       [:option {:value ""} (str "-- Select " (:label param-spec) " --")]
       (for [option (:options param-spec)]
         ^{:key (:value option)}
         [:option {:value (:value option)} (:label option)])]

      (or (= param-type "number") (= param-type :number))
      [:input.parameter-input
       {:type "number"
        :placeholder (:label param-spec)
        :value (or current-value "")
        :on-change #(state/update-assertion-parameter!
                     assertion-code
                     param-key
                     (.. % -target -value))}]

      (or (= param-type "text") (= param-type :text))
      [:input.parameter-input
       {:type "text"
        :placeholder (:label param-spec)
        :value (or current-value "")
        :on-change #(state/update-assertion-parameter!
                     assertion-code
                     param-key
                     (.. % -target -value))}]

      (or (= param-type "currency") (= param-type :currency))
      [:input.parameter-input
       {:type "number"
        :step "0.01"
        :placeholder (str (:label param-spec) " ($)")
        :value (or current-value "")
        :on-change #(state/update-assertion-parameter!
                     assertion-code
                     param-key
                     (js/parseFloat (.. % -target -value)))}]

      (or (= param-type "date") (= param-type :date))
      [:input.parameter-input.date-input
       {:type "date"
        :min "2026-01-01"
        :max "2026-12-31"
        :value (or current-value "")
        :on-change #(state/update-assertion-parameter!
                     assertion-code
                     param-key
                     (.. % -target -value))}]

      :else
      [:div (str "Unknown parameter type: " param-type)])))

(defn- auto-populate-assertion!
  "Auto-populate assertion parameters at L1+ when assertion is selected."
  [assertion-code]
  (let [level (state/current-level)
        problem (state/current-problem)
        vars (:variables problem)]
    (when (>= level 1)
      (case assertion-code
        ;; Auto-populate Has Date from problem variables
        :has-date
        (when-let [date (:date vars)]
          (state/update-assertion-parameter! :has-date :date date))

        ;; Auto-populate Has Counterparty from problem variables
        :has-counterparty
        (when-let [counterparty (or (:vendor vars) (:customer vars) (:employee vars))]
          (state/update-assertion-parameter! :has-counterparty :name counterparty))

        ;; No auto-population for other assertions
        nil))))

(defn assertion-button [assertion]
  (let [assertion-code (:code assertion)
        selected-assertions (state/selected-assertions)
        selected? (contains? selected-assertions assertion-code)
        current-params (get selected-assertions assertion-code)]
    [:div.assertion-item
     [:button.assertion-button
      {:class (when selected? "selected")
       :on-click #(do
                    (state/toggle-assertion! assertion-code)
                    ;; After toggling ON, auto-populate if applicable
                    (when-not selected?  ; was not selected, now is
                      (auto-populate-assertion! assertion-code)))
       :title (:description assertion)}  ;; Show description on hover
      [:div.assertion-label (:label assertion)]]

     ;; Show parameter inputs if assertion is selected and parameterized
     (when (and selected? (:parameterized assertion))
       [:div.parameters
        (for [[param-key param-spec] (:parameters assertion)]
          ;; Only show physical-item when unit is "physical-unit" (Physical Units)
          (when (or (not= param-key :physical-item)
                    (= (get current-params :unit) "physical-unit"))
            ^{:key param-key}
            [:div.parameter
             [:label (str (:label param-spec)
                         (when (:optional param-spec) " (optional)")
                         ":")]
             [parameter-input assertion-code param-key param-spec
              (get current-params param-key)]]))])]))

(defn assertion-group [domain-key assertions]
  [:div.assertion-group
   [:h4 (name domain-key)]
   (for [assertion assertions]
     ^{:key (:code assertion)}
     [assertion-button assertion])])

(defn assertion-panel []
  (let [assertions (state/available-assertions)]
    [:div.column.assertion-panel
     [:h2 "Select Assertions"]
     [:div.assertion-groups
      (for [[domain-key domain-assertions] assertions]
        ^{:key domain-key}
        [assertion-group domain-key domain-assertions])]]))

;; ==================== Sentence Builder Components ====================
;; New UI for building assertions as natural language sentences

(defn- format-unit-display
  "Format a unit selection for display in the sentence."
  [{:keys [unit physical-item quantity]}]
  (case unit
    "monetary-unit" (str "$" (or quantity "___") " Cash")
    "physical-unit" (str (or quantity "___") " "
                         (or (some-> physical-item (str/replace "-" " ") str/capitalize)
                             "[select item]"))
    "time-unit" (str (or quantity "___") " hours")
    "effort-unit" (str (or quantity "___") " labor effort")
    (str (or quantity "___") " " (or unit "[select unit]"))))

(defn- format-date-display
  "Format a date for display in the sentence."
  [date-str]
  (if (and date-str (re-matches #"\d{4}-\d{2}-\d{2}" date-str))
    (let [[year month day] (str/split date-str #"-")
          months ["January" "February" "March" "April" "May" "June"
                  "July" "August" "September" "October" "November" "December"]
          month-idx (dec (js/parseInt month))
          day-num (js/parseInt day)]
      (str (nth months month-idx) " " day-num ", " year))
    (or date-str "[select date]")))

(defn- inline-date-input
  "Inline date input for sentence builder."
  [assertion-code param-key current-value]
  [:input.sentence-input.date-input
   {:type "date"
    :min "2026-01-01"
    :max "2026-12-31"
    :value (or current-value "")
    :on-change #(state/update-assertion-parameter!
                 assertion-code param-key
                 (.. % -target -value))}])

(defn- inline-number-input
  "Inline number input for sentence builder."
  [assertion-code param-key current-value placeholder]
  [:input.sentence-input.number-input
   {:type "number"
    :placeholder (or placeholder "___")
    :value (or current-value "")
    :style {:width (str (max 3 (count (str current-value))) "ch")}
    :on-change #(state/update-assertion-parameter!
                 assertion-code param-key
                 (.. % -target -value))}])

(defn- inline-text-input
  "Inline text input for sentence builder."
  [assertion-code param-key current-value placeholder]
  [:input.sentence-input.text-input
   {:type "text"
    :placeholder (or placeholder "___")
    :value (or current-value "")
    :style {:width (str (max 8 (+ 2 (count (str current-value)))) "ch")}
    :on-change #(state/update-assertion-parameter!
                 assertion-code param-key
                 (.. % -target -value))}])

(defn- inline-dropdown
  "Inline dropdown for sentence builder."
  [assertion-code param-key options current-value placeholder]
  [:select.sentence-input.dropdown-input
   {:value (or current-value "")
    :on-change #(state/update-assertion-parameter!
                 assertion-code param-key
                 (.. % -target -value))}
   [:option {:value ""} (or placeholder "select...")]
   (for [option options]
     ^{:key (:value option)}
     [:option {:value (:value option)} (:label option)])])

(defn- confidence-slider
  "Confidence level slider with percentage display."
  [assertion-code current-value]
  (let [value (or current-value 85)]
    [:span.confidence-input
     [:input.sentence-input.slider-input
      {:type "range"
       :min 0
       :max 100
       :step 5
       :value value
       :on-change #(state/update-assertion-parameter!
                    assertion-code :confidence
                    (js/parseInt (.. % -target -value)))}]
     [:span.confidence-value (str value "%")]]))

(defn- customer-context-display
  "Display customer payment history context for confidence decisions."
  [customer-name customer-profiles]
  (when-let [profile (get customer-profiles customer-name)]
    [:div.customer-context
     [:div.context-header "Customer Payment History:"]
     [:div.context-body
      [:p [:strong customer-name] " has paid on time for "
       [:span.highlight (str (:total-orders profile) " orders")] "."]
      [:p "Historical payment rate: "
       [:span.highlight (str (:history-rate profile) "%")]]
      [:p "Industry average: "
       [:span.highlight (str (:industry-avg profile) "%")]]]]))

(defn- vendor-context-display
  "Display vendor reliability context for prepaid expense confidence decisions."
  [vendor-name vendor-profiles]
  (when-let [profile (get vendor-profiles vendor-name)]
    [:div.customer-context  ;; Reuse same styling
     [:div.context-header "Vendor Reliability:"]
     [:div.context-body
      [:p [:strong vendor-name] " has been in business for "
       [:span.highlight (str (:years-in-business profile) " years")] "."]
      [:p "Historical reliability rate: "
       [:span.highlight (str (:reliability-rate profile) "%")]]
      [:p "Industry average: "
       [:span.highlight (str (:industry-avg profile) "%")]]]]))

(defn- sentence-section
  "A section of the sentence (main, obligation, expectation, etc.)."
  [section-type label children]
  [:div.sentence-section {:class (name section-type)}
   (when label
     [:div.section-label label])
   [:div.section-content children]])

(defn- remove-assertion-button
  "Small button to remove an assertion from the sentence."
  [assertion-code]
  [:button.remove-assertion
   {:on-click #(state/toggle-assertion! assertion-code)
    :title "Remove this assertion"}
   "×"])

(defn- render-provides-fragment
  "Render the 'provides' part of the sentence."
  [params]
  (let [unit-options [{:value "monetary-unit" :label "Cash/Money"}
                      {:value "physical-unit" :label "Physical Units"}]
        item-options [{:value "printed-tshirts" :label "Printed T-Shirts"}
                      {:value "blank-tshirts" :label "Blank T-Shirts"}]]
    [:span.assertion-fragment.provides
     [:span.verb "provides "]
     [inline-number-input :provides :quantity (:quantity params) "qty"]
     " "
     [inline-dropdown :provides :unit unit-options (:unit params) "unit type"]
     (when (= (:unit params) "physical-unit")
       [:span " "
        [inline-dropdown :provides :physical-item item-options (:physical-item params) "item"]])
     [remove-assertion-button :provides]]))

(defn- render-receives-fragment
  "Render the 'receives' part of the sentence."
  [params]
  (let [unit-options [{:value "monetary-unit" :label "Cash/Money"}
                      {:value "physical-unit" :label "Physical Units"}]
        item-options [{:value "printed-tshirts" :label "Printed T-Shirts"}
                      {:value "blank-tshirts" :label "Blank T-Shirts"}
                      {:value "ink-cartridges" :label "Ink Cartridges"}
                      {:value "t-shirt-printer" :label "T-Shirt Printer"}]]
    [:span.assertion-fragment.receives
     [:span.verb "receives "]
     [inline-number-input :receives :quantity (:quantity params) "qty"]
     " "
     [inline-dropdown :receives :unit unit-options (:unit params) "unit type"]
     (when (= (:unit params) "physical-unit")
       [:span " "
        [inline-dropdown :receives :physical-item item-options (:physical-item params) "item"]])
     [remove-assertion-button :receives]]))

(defn- render-counterparty-fragment
  "Render the counterparty part of the sentence."
  [params connector]
  [:span.assertion-fragment.counterparty
   [:span.connector (str " " connector " ")]
   [inline-text-input :has-counterparty :name (:name params) "party name"]
   [remove-assertion-button :has-counterparty]])

(defn- render-requires-section
  "Render the 'requires' obligation section.
   Context-aware: determines who is obligated based on main event structure.
   - Credit sale (SP provides goods) → counterparty must provide cash
   - Credit purchase (SP receives goods) → SP must provide cash to counterparty"
  [params counterparty-name is-purchase?]
  (let [action-options [{:value "provides" :label "Cash/Money"}
                        {:value "receives" :label "Physical Units"}]
        unit-options [{:value "monetary-unit" :label "Cash/Money"}
                      {:value "physical-unit" :label "Physical Units"}]
        ;; Determine obligated party and recipient based on transaction type
        obligated-party (if is-purchase? "SP" (or counterparty-name "The counterparty"))
        recipient (if is-purchase? (or counterparty-name "the vendor") "SP")]
    [sentence-section :obligation "This creates an obligation:"
     [:div.requires-content
      [:span.party-name obligated-party]
      [:span " must provide "]
      [inline-number-input :requires :quantity (:quantity params) "amount"]
      " "
      [inline-dropdown :requires :unit unit-options (:unit params) "unit"]
      [:span (str " to " recipient " by ")]
      [inline-date-input :requires :due-date (:due-date params)]
      [remove-assertion-button :requires]]]))

(defn- render-expects-section
  "Render the 'expects' confidence section with context.
   Context-aware: shows customer context for credit sales, vendor context for prepaid expenses."
  [params counterparty-name customer-profiles vendor-profiles is-prepaid?]
  (let [context-label (if is-prepaid?
                        "SP expects to receive services with"
                        "SP expects payment with")]
    [sentence-section :expectation "Expectation of fulfillment:"
     [:div.expects-content
      ;; Show appropriate context based on transaction type
      (cond
        (and is-prepaid? vendor-profiles)
        [vendor-context-display counterparty-name vendor-profiles]

        (and (not is-prepaid?) customer-profiles)
        [customer-context-display counterparty-name customer-profiles])

      [:div.confidence-row
       [:span context-label " "]
       [confidence-slider :expects (:confidence params)]
       [:span " confidence."]
       [remove-assertion-button :expects]]]]))

;; ==================== Calculation Builder Components ====================

(defn- calculation-input-field
  "Render a single input field for the calculation builder."
  [input-def current-value on-change]
  (let [{:keys [key label type required min max default options]} input-def
        input-key (keyword key)]
    [:div.calc-input-field
     [:label {:for (name input-key)} label
      (when required [:span.required " *"])]
     (case type
       :currency
       [:div.currency-input
        [:span.currency-symbol "$"]
        [:input {:type "number"
                 :id (name input-key)
                 :value (or current-value default "")
                 :min (or min 0)
                 :step "0.01"
                 :on-change #(on-change input-key (js/parseFloat (.. % -target -value)))}]]

       :number
       [:input {:type "number"
                :id (name input-key)
                :value (or current-value default "")
                :min (or min 1)
                :max (or max 100)
                :on-change #(on-change input-key (js/parseInt (.. % -target -value)))}]

       :dropdown
       (let [opts (or options [])
             ;; Handle both map format {:value, :label} and simple values
             get-value (fn [opt] (if (map? opt) (:value opt) opt))
             get-label (fn [opt] (if (map? opt) (:label opt) (str opt)))
             default-val (get-value (first opts))]
         [:select {:id (name input-key)
                   :value (or current-value default-val "")
                   :on-change #(let [v (.. % -target -value)
                                     ;; Parse numeric values
                                     parsed (js/parseInt v)]
                                 (on-change input-key (if (js/isNaN parsed) v parsed)))}
          (for [opt opts]
            ^{:key (get-value opt)}
            [:option {:value (get-value opt)} (get-label opt)])])

       :percentage
       [:div.percentage-input
        [:input {:type "number"
                 :id (name input-key)
                 :value (or current-value default "")
                 :min 0
                 :max 100
                 :step "0.01"
                 :on-change #(on-change input-key (js/parseFloat (.. % -target -value)))}]
        [:span.percentage-symbol "%"]]

       ;; Default to text input
       [:input {:type "text"
                :id (name input-key)
                :value (or current-value default "")
                :on-change #(on-change input-key (.. % -target -value))}])]))

(defn- bad-debt-calculation-display
  "Display the data-driven bad debt calculation from receivables."
  []
  (r/create-class
   {:component-did-mount
    (fn [_]
      ;; Fetch receivables when component mounts
      (api/fetch-receivables-summary!))

    :reagent-render
    (fn []
      (let [summary (state/receivables-summary)
            receivables (:receivables summary [])
            total-ar (:total-receivables summary 0)
            total-bad-debt (:total-bad-debt summary 0)]
        [:div.bad-debt-calculation
         [:div.calc-educational-note
          [:p "Bad debt expense is calculated from the confidence levels you assigned when recording credit sales."]
          [:p.formula "Formula: Σ (Receivable Amount × (1 - Confidence))"]]

         (if (empty? receivables)
           [:div.no-receivables
            [:p "No outstanding receivables found."]
            [:p.hint "Complete some credit sales first to see bad debt calculation."]]

           [:div.receivables-table
            [:table
             [:thead
              [:tr
               [:th "Customer"]
               [:th "Amount"]
               [:th "Confidence"]
               [:th "Expected Loss"]]]
             [:tbody
              (for [{:keys [customer amount confidence]} receivables]
                (let [loss (* amount (- 1 (/ confidence 100)))]
                  ^{:key customer}
                  [:tr
                   [:td customer]
                   [:td.amount (str "$" (.toFixed amount 2))]
                   [:td.confidence (str confidence "%")]
                   [:td.loss (str "$" (.toFixed loss 2))]]))
              [:tr.total-row
               [:td "Total"]
               [:td.amount (str "$" (.toFixed total-ar 2))]
               [:td ""]
               [:td.loss.total (str "$" (.toFixed total-bad-debt 2))]]]]

            [:div.calc-result
             [:span.result-label "Bad Debt Expense: "]
             [:span.result-value (str "$" (.toFixed total-bad-debt 2))]]])]))}))

(defn- formula-builder
  "Interactive formula builder for calculations like depreciation."
  [basis]
  (let [schemas (state/calculation-schemas)
        schema (get schemas (keyword basis))
        inputs (state/calculation-inputs)
        result (state/calculation-result)]
    (if (nil? schema)
      [:div.loading-schema "Loading calculation schema..."]
      [:div.formula-builder
       ;; Formula display
       [:div.formula-display
        [:span.formula-label "Formula: "]
        [:span.formula (:formula-display schema)]]

       ;; Input fields
       [:div.calc-inputs
        (for [input-def (:inputs schema)]
          ^{:key (:key input-def)}
          [calculation-input-field
           input-def
           (get inputs (keyword (:key input-def)))
           (fn [k v] (state/set-calculation-input! k v))])]

       ;; Calculate button
       [:div.calc-actions
        [:button.calculate-btn
         {:on-click #(api/calculate!
                      basis
                      inputs
                      (fn [response]
                        (state/set-calculation-result! response)
                        ;; Also update the assertion parameter with calculated value
                        (when-let [amount (:value response)]
                          (state/update-assertion-parameter! :reports :amount amount))))}
         "Calculate"]]

       ;; Result display
       (when result
         [:div.calc-result
          {:class (when (:error result) "error")}
          (if (:error result)
            [:span.error-message (:error result)]
            [:div
             [:span.result-label (:result-label schema) ": "]
             [:span.result-value (or (:display result)
                                     (str "$" (.toFixed (:value result) 2)))]])])])))

(defn- calculation-builder
  "Main calculation builder component - shows appropriate UI based on basis type."
  [basis]
  (r/create-class
   {:component-did-mount
    (fn [_]
      ;; Fetch calculation schemas when component mounts
      (api/fetch-calculation-schemas!))

    :reagent-render
    (fn [basis]
      (when basis
        (let [simple-bases #{"earned" "cash-received" "cash-paid"}]
          (when-not (simple-bases basis)
            [:div.calculation-builder
             [:h4 "Calculate Amount"]
             (case basis
               "estimation" [bad-debt-calculation-display]
               ;; All others use the formula builder
               [formula-builder basis])]))))}))

(defn- render-reports-section
  "Render the 'reports' recognition section."
  [params]
  (let [category-options [{:value "revenue" :label "Revenue"}
                          {:value "expense" :label "Expense"}
                          {:value "gain" :label "Gain"}
                          {:value "loss" :label "Loss"}]
        basis-options [{:value "earned" :label "Performance obligation satisfied"}
                       {:value "cash-received" :label "Equal to cash received"}
                       {:value "cash-paid" :label "Equal to cash paid"}
                       {:value "systematic-allocation" :label "Systematic allocation (depreciation)"}
                       {:value "estimation" :label "Estimation (bad debt)"}
                       {:value "time-based" :label "Passage of time (prepaid)"}
                       {:value "accrual" :label "Accrual over time (interest)"}]
        selected-basis (:basis params)]
    [:div.reports-section
     [sentence-section :recognition "Recognition:"
      [:div.reports-content
       [:span "SP reports "]
       [inline-dropdown :reports :category category-options (:category params) "type"]
       [:span " calculated by "]
       [inline-dropdown :reports :basis basis-options selected-basis "method"]
       [remove-assertion-button :reports]]]

     ;; Show calculation builder when a non-simple basis is selected
     (when selected-basis
       [calculation-builder selected-basis])]))

(defn- add-assertion-menu
  "Menu to add new assertions to the sentence."
  [selected-assertions available-assertions]
  (let [show-menu? (r/atom false)
        ;; Flatten available assertions and filter out already selected
        all-assertions (for [[_domain assertions] available-assertions
                             assertion assertions
                             :when (not (contains? selected-assertions (:code assertion)))]
                         assertion)]
    (fn [selected-assertions available-assertions]
      [:div.add-assertion-menu
       [:button.add-assertion-button
        {:on-click #(swap! show-menu? not)}
        (if @show-menu? "Cancel" "+ Add Assertion")]
       (when @show-menu?
         [:div.assertion-menu-dropdown
          (for [assertion all-assertions]
            ^{:key (:code assertion)}
            [:button.menu-item
             {:on-click #(do
                          (state/toggle-assertion! (:code assertion))
                          (auto-populate-assertion! (:code assertion))
                          (reset! show-menu? false))
              :title (:description assertion)}
             (:label assertion)])])])))

(defn sentence-builder
  "Main sentence builder component - builds assertions as natural language."
  []
  (let [selected (state/selected-assertions)
        available (state/available-assertions)
        problem (state/current-problem)
        counterparty-name (get-in selected [:has-counterparty :name])
        customer-profiles (get-in problem [:customer-profiles])
        vendor-profiles (get-in problem [:vendor-profiles])
        ;; Determine transaction type based on what SP provides/receives
        ;; Purchase: SP receives (goods) without providing (goods) - may provide cash
        ;; Prepaid expense: SP provides (cash) and expects to receive services
        is-purchase? (and (contains? selected :receives)
                          (not (contains? selected :provides)))
        ;; Prepaid: SP provides monetary-unit (cash) and expects to receive something
        is-prepaid? (and (contains? selected :provides)
                         (= (get-in selected [:provides :unit]) "monetary-unit")
                         (contains? selected :expects))]
    [:div.sentence-builder
     [:h2 "Build Your Assertion"]

     ;; Main sentence section
     [:div.sentence-container
      ;; Date fragment (always starts the sentence if present)
      (when (contains? selected :has-date)
        [:div.main-sentence
         [:span "On "]
         [inline-date-input :has-date :date (get-in selected [:has-date :date])]
         [:span ", SP "]
         [remove-assertion-button :has-date]

         ;; Provides fragment
         (when (contains? selected :provides)
           [render-provides-fragment (:provides selected)])

         ;; Counterparty after provides
         (when (and (contains? selected :provides)
                    (contains? selected :has-counterparty))
           [render-counterparty-fragment (:has-counterparty selected) "to"])

         ;; Receives fragment
         (when (contains? selected :receives)
           [:span
            (when (contains? selected :provides) [:span.connector " and "])
            [render-receives-fragment (:receives selected)]])

         ;; Counterparty after receives (if no provides)
         (when (and (not (contains? selected :provides))
                    (contains? selected :receives)
                    (contains? selected :has-counterparty))
           [render-counterparty-fragment (:has-counterparty selected) "from"])

         [:span "."]])

      ;; If no date selected, show placeholder
      (when-not (contains? selected :has-date)
        [:div.sentence-placeholder
         [:span.placeholder-text "Start by adding assertions to build your sentence..."]])

      ;; Requires section (obligation) - context-aware for purchases vs sales
      (when (contains? selected :requires)
        [render-requires-section (:requires selected) counterparty-name is-purchase?])

      ;; Expects section (confidence) - context-aware for sales vs prepaid expenses
      (when (contains? selected :expects)
        [render-expects-section (:expects selected) counterparty-name
         customer-profiles vendor-profiles is-prepaid?])

      ;; Reports section (recognition)
      (when (contains? selected :reports)
        [render-reports-section (:reports selected)])]

     ;; Add assertion menu
     [add-assertion-menu selected available]]))

;; ==================== Assertion Linkage Display ====================

(defn- strip-amount
  "Remove the amount portion from account names like 'Cash $5,000' -> 'Cash'"
  [account-name]
  (when account-name
    (str/replace account-name #"\s+\$[\d,.]+" "")))

(defn- find-linkage-for-account
  "Find the assertion linkage that corresponds to this account and effect."
  [linkages account-name effect]
  (when (and linkages account-name)
    (let [clean-account (strip-amount account-name)
          effect-str (name effect)]  ;; Convert :debit/:credit to "debit"/"credit"
      (->> linkages
           (filter (fn [[_code data]]
                     (let [data-effect (if (keyword? (:effect data))
                                         (name (:effect data))
                                         (:effect data))]
                       (and (= (:account data) clean-account)
                            (= data-effect effect-str)))))
           first))))

(defn- format-linkage
  "Format linkage for display: 'receives (physical-unit, blank-tshirts)'"
  [[code data]]
  (let [params (:params data)
        assertion-name (name code)
        unit (:unit params)
        physical-item (:physical-item params)
        ;; Build parameter string like "(physical-unit, blank-tshirts)" or "(monetary-unit)"
        param-parts (cond-> []
                      unit (conj unit)
                      physical-item (conj physical-item))
        param-str (when (seq param-parts)
                    (str "(" (str/join ", " param-parts) ")"))]
    (if param-str
      (str assertion-name " " param-str)
      assertion-name)))

(defn- format-counterparty-linkage
  "Format the counterparty info for display"
  [linkages]
  (when-let [cp-data (get linkages :has-counterparty)]
    (let [name (get-in cp-data [:params :name])]
      (when name
        (str "has-counterparty (" name ")")))))

(defn feedback-panel []
  (let [feedback (state/feedback)
        selected (state/selected-assertions)
        problem (state/current-problem)
        is-reverse? (= (:problem-type problem) "reverse")
        is-construct? (= (:problem-type problem) "construct")
        je (state/get-constructed-je)]
    [:div.column.feedback-panel
     [:h2 "Feedback"]

     ;; Show JE constructor for construct mode (disabled after submission)
     (when is-construct?
       [je-constructor problem :disabled? (some? feedback)])

     ;; Show Submit/Clear/Cancel buttons before feedback is received
     (when (nil? feedback)
       [:div.actions
        (if is-construct?
          ;; For construct mode: submit JE
          [:button.primary
           {:on-click #(api/submit-je!)
            :disabled (or (nil? (:debit-account je))
                         (nil? (:credit-account je))
                         (nil? (:amount je)))}
           "Submit Journal Entry"]
          ;; For assertion modes: submit assertions
          ;; Use simulation-specific submit when in simulation mode
          [:button.primary
           {:on-click #(if (state/simulation-mode?)
                        (api/submit-simulation-answer!)
                        (api/submit-answer!))
            :disabled (empty? (keys selected))}
           "Submit Answer"])
        [:button.secondary
         {:on-click #(if is-construct?
                      (state/clear-je-fields!)
                      (state/clear-selections!))}
         "Clear"]
        ;; Cancel button only in simulation mode
        (when (state/simulation-mode?)
          [:button.cancel-btn
           {:on-click #(api/cancel-transaction!)}
           "Cancel Transaction"])])

     (cond
       ;; Show current selection status
       (and (nil? feedback) (seq selected))
       [:div.status
        [:p "Assertions selected: " (count (keys selected))]]

       ;; Show feedback after submission
       (some? feedback)
       [:div.feedback
        [:div.status {:class (name (:status feedback))}
         [:h3 (case (:status feedback)
                :correct "✓ Correct!"
                :incorrect "✗ Incorrect"
                :incomplete "⚠ Incomplete"
                :indeterminate "Cannot classify"
                (str "Status: " (:status feedback)))]]

        ;; Show journal entries FIRST (before hints) for forward problems
        (when (and (not is-reverse?) (some? (:classification feedback)))
          (let [classification (:classification feedback)
                linkages (:assertion-linkages feedback)
                counterparty-str (format-counterparty-linkage linkages)
                is-incorrect? (= (:status feedback) :incorrect)
                correct-class (:correct-classification feedback)]
            [:div.je-comparison
             ;; Student's answer (incorrect or correct)
             (when-let [journal-entries (:journal-entry classification)]
               (when (seq journal-entries)
                 [:div.journal-entry {:class (when is-incorrect? "incorrect-je")}
                  [:h4 (if is-incorrect?
                         "Your assertions would produce this (incorrect) entry:"
                         "Journal Entry:")]
                  (for [entry journal-entries]
                    (let [debit-linkage (find-linkage-for-account linkages (:debit entry) :debit)
                          credit-linkage (find-linkage-for-account linkages (:credit entry) :credit)]
                      ^{:key (str (:debit entry) "-" (:credit entry))}
                      [:div.entry
                       [:div.entry-line
                        [:span.debit "DR: " (:debit entry)]
                        (when debit-linkage
                          [:span.linkage " ← " (format-linkage debit-linkage)])
                        (when counterparty-str
                          [:span.linkage ", " counterparty-str])]
                       [:div.entry-line
                        [:span.credit "CR: " (:credit entry)]
                        (when credit-linkage
                          [:span.linkage " ← " (format-linkage credit-linkage)])
                        (when counterparty-str
                          [:span.linkage ", " counterparty-str])]]))]))

             ;; For incorrect answers, also show what the correct JE should be
             (when (and is-incorrect? correct-class)
               (when-let [correct-entries (:journal-entry correct-class)]
                 (when (seq correct-entries)
                   [:div.journal-entry.correct-je
                    [:h4 "The correct entry should be:"]
                    (for [entry correct-entries]
                      ^{:key (str "correct-" (:debit entry) "-" (:credit entry))}
                      [:div.entry
                       [:div.entry-line
                        [:span.debit "DR: " (:debit entry)]]
                       [:div.entry-line
                        [:span.credit "CR: " (:credit entry)]]])])))

             (when-let [note (:note classification)]
               [:p.note note])]))

        ;; For reverse problems, show the transaction narrative after submission
        (when (and is-reverse? (:narrative problem))
          [:div.narrative-reveal
           [:h4 "Transaction:"]
           [:p (:narrative problem)]])

        ;; Hints come after the JE comparison
        (when-let [hints (:hints feedback)]
          (when (seq hints)
            [:div.hints
             [:h4 "Why was this wrong?"]
             [:ul
              (for [hint hints]
                ^{:key hint}
                [:li hint])]]))

        [:div.actions
         [:button.primary
          {:on-click #(do
                        (api/fetch-problem! (state/current-level))
                        (state/clear-feedback!)
                        (when is-construct? (state/clear-je-fields!)))}
          "Next Problem"]]]

       :else
       [:div.instructions
        [:p "Read the transaction and select the relevant assertions."]
        [:p "The assertions describe the properties of the transaction."]
        [:p "When you're ready, click 'Submit Answer'."]])]))

(defn app-content []
  (let [problem (state/current-problem)
        is-construct? (= (:problem-type problem) "construct")]
    [:div.app-container
     [:header
      [:div.header-left
       [:h1 "Assertive Accounting Learning Platform"]]
      [user-header]]
     [:div {:class (if is-construct? "two-column-layout" "three-column-layout")}
      [narrative-panel]
      (when-not is-construct?
        [assertion-panel])
      [feedback-panel]]]))

;; ==================== Business Simulation Components ====================

(defn format-currency
  "Format a number as currency."
  [amount]
  (if amount
    (str "$" (.toLocaleString (js/Math.abs amount) "en-US" #js {:minimumFractionDigits 2 :maximumFractionDigits 2}))
    "$0.00"))

(defn business-dashboard
  "Shows current business state metrics."
  []
  (let [bstate (state/business-state)]
    (when bstate
      [:div.business-dashboard
       [:h3 "Your Business Status"]
       [:div.metrics-grid
        [:div.metric
         [:span.metric-label "Cash"]
         [:span.metric-value (format-currency (:cash bstate))]]
        [:div.metric
         [:span.metric-label "Blank T-Shirts"]
         [:span.metric-value (str (get-in bstate [:inventory :blank-tshirts] 0) " units")]]
        [:div.metric
         [:span.metric-label "Ink Cartridges"]
         [:span.metric-value (str (get-in bstate [:inventory :ink-cartridges] 0) " units")]]
        [:div.metric
         [:span.metric-label "Finished Goods"]
         [:span.metric-value (str (:finished-goods bstate 0) " printed shirts")]]
        (when (seq (:accounts-payable bstate))
          [:div.metric
           [:span.metric-label "Accounts Payable"]
           [:span.metric-value
            (format-currency (reduce + 0 (vals (:accounts-payable bstate))))]])
        (when (seq (:accounts-receivable bstate))
          [:div.metric
           [:span.metric-label "Accounts Receivable"]
           [:span.metric-value
            (format-currency (reduce + 0 (vals (:accounts-receivable bstate))))]])
        (when (seq (:equipment bstate))
          [:div.metric
           [:span.metric-label "Equipment"]
           [:span.metric-value
            (str/join ", " (map name (:equipment bstate)))]])]
       [:div.period-info
        [:span (str "Period " (:current-period bstate 1))]
        [:span.moves (str "Moves: " (:moves-remaining bstate 5) " remaining")]
        [:span.sim-date (str "Date: " (:simulation-date bstate))]]])))

;; Actions that require student parameter selection
(def actions-requiring-params
  "These actions need student input before starting."
  #{:purchase-inventory-cash :purchase-inventory-credit})

(defn action-button
  "Renders a single action button.
   Actions in actions-requiring-params are staged for parameter entry;
   others start directly."
  [{:keys [key label description level available reason]}]
  [:button.action-btn
   {:class (when-not available "disabled")
    :disabled (not available)
    :title (if available description reason)
    :on-click #(when available
                 (if (contains? actions-requiring-params key)
                   (state/set-staged-action! key)
                   (api/start-simulation-action! key)))}
   [:span.action-label label]
   [:span.action-level (str "L" level)]
   (when-not available
     [:span.action-reason reason])])

;; Parameter definitions for each action type
(def action-parameters
  "Defines the parameters students can enter for each action type."
  {:purchase-inventory-cash
   [{:key :inventory-type :label "Item Type" :type :select
     :options [["blank-tshirts" "Blank T-Shirts ($5/unit)"]
               ["ink-cartridges" "Ink Cartridges ($25/unit)"]]}
    {:key :quantity :label "Quantity" :type :number :min 1}
    {:key :vendor :label "Vendor" :type :select
     :options ["PrintSupplyCo" "TextileDirect" "InkMasters" "GarmentWholesale"]}]

   :purchase-inventory-credit
   [{:key :inventory-type :label "Item Type" :type :select
     :options [["blank-tshirts" "Blank T-Shirts ($5/unit)"]
               ["ink-cartridges" "Ink Cartridges ($25/unit)"]]}
    {:key :quantity :label "Quantity" :type :number :min 1}
    {:key :vendor :label "Vendor" :type :select
     :options ["PrintSupplyCo" "TextileDirect" "InkMasters" "GarmentWholesale"]}]

   :sell-tshirts-cash
   [{:key :quantity :label "Quantity" :type :number :min 1 :max-fn :finished-goods}
    {:key :customer :label "Customer" :type :select
     :options ["LocalSportsTeam" "CampusBoutique" "EventPlannersCo" "RetailPartner"]}]

   :sell-tshirts-credit
   [{:key :quantity :label "Quantity" :type :number :min 1 :max-fn :finished-goods}
    {:key :customer :label "Customer" :type :select
     :options ["LocalSportsTeam" "CampusBoutique" "EventPlannersCo" "RetailPartner"]}]})

(defn param-input
  "Render a single parameter input field.
   Options can be in multiple formats:
   - Maps: [{:value 'v1' :label 'Label 1'} ...]
   - Tuples: [['value1' 'Label 1'] ...]
   - Strings: ['Option1' 'Option2']"
  [param-def business-state]
  (let [k (:key param-def)
        current-val (get (state/staged-params) k)
        max-val (when-let [max-fn (:max-fn param-def)]
                  (get business-state max-fn))]
    [:div.param-field
     [:label (:label param-def)]
     (case (:type param-def)
       :number
       [:input {:type "number"
                :min (:min param-def 0)
                :max (or max-val 999999)
                :value (or current-val "")
                :placeholder (str (:label param-def)
                                 (when max-val (str " (max: " max-val ")")))
                :on-change #(state/update-staged-param! k (js/parseInt (.. % -target -value)))}]

       :select
       [:select {:value (or current-val "")
                 :on-change #(state/update-staged-param! k (.. % -target -value))}
        [:option {:value ""} (str "Select " (:label param-def))]
        (for [opt (:options param-def)]
          ;; Handle map {:value :label}, tuple [value label], or string formats
          (let [[value label] (cond
                                (map? opt) [(:value opt) (:label opt)]
                                (vector? opt) opt
                                :else [opt opt])]
            ^{:key value}
            [:option {:value value} label]))])]))

(defn action-param-form
  "Form for entering parameters for a staged action."
  []
  (let [staged (state/staged-action)
        params (state/staged-params)
        business-state (state/business-state)
        ;; Use dynamic schemas from backend, fallback to hardcoded for compatibility
        schemas (state/action-schemas)
        param-defs (or (get-in schemas [staged :parameters])
                       (get action-parameters staged []))
        action-info (first (filter #(= (:key %) staged) (state/simulation-available-actions)))]
    (when staged
      [:div.param-form
       [:h4 (str "Configure: " (:label action-info (:name staged)))]
       [:div.param-fields
        (for [param-def param-defs]
          ^{:key (:key param-def)}
          [param-input param-def business-state])]
       [:div.param-actions
        [:button.start-tx-btn
         {:on-click #(api/start-simulation-action! staged params)}
         "Start Transaction"]
        [:button.cancel-btn
         {:on-click #(state/clear-staged-action!)}
         "Cancel"]]])))

(defn action-selection-panel
  "Panel for selecting actions in simulation mode.
   Filters actions based on the current tutorial stage."
  []
  (let [all-actions (state/simulation-available-actions)
        pending (state/pending-transaction)
        staged (state/staged-action)
        current-stage (state/current-stage)
        unlocked-action-keys (tutorials/get-unlocked-actions current-stage)
        ;; Filter actions to only show those unlocked at current stage
        actions (filter #(contains? unlocked-action-keys (keyword (:key %))) all-actions)
        ;; Check if there are locked actions the student can't access yet
        locked-count (- (count all-actions) (count actions))]
    [:div.action-panel
     [:h3 "What do you want to do?"]
     (cond
       ;; Has pending transaction - show info only
       pending
       [:div.pending-alert
        [:p.pending-message "Complete the current transaction to continue."]
        [:span.pending-attempts (str "Attempts: " (:attempts pending 0))]]

       ;; Has staged action - show parameter form
       staged
       [action-param-form]

       ;; Show action buttons
       (seq actions)
       [:div.action-buttons
        (for [action actions]
          ^{:key (:key action)}
          [action-button action])
        ;; Show hint about locked actions
        (when (pos? locked-count)
          [:div.locked-actions-hint
           [:p (str locked-count " more action" (when (> locked-count 1) "s")
                    " available at higher stages")]])]

       ;; No actions available
       :else
       [:div.no-actions
        [:p "No actions available yet."]
        [:p.hint "Complete the tutorial to unlock actions."]
        [:button.refresh-btn
         {:on-click #(api/fetch-simulation-state!)}
         "Refresh"]])]))

(defn ledger-entry-row
  "Renders a single ledger entry."
  [entry]
  (let [je (:journal-entry entry)]
    [:tr.ledger-row
     [:td.ledger-date (:date entry)]
     [:td.ledger-desc (subs (:narrative entry) 0 (min 60 (count (:narrative entry))))
      (when (> (count (:narrative entry)) 60) "...")]
     [:td.ledger-debit (:debit je)]
     [:td.ledger-credit (:credit je)]
     [:td.ledger-amount (format-currency (:amount (:variables entry)))]]))

(defn ledger-view
  "Shows the user's transaction ledger."
  []
  (let [entries (state/ledger)]
    [:div.ledger-view
     [:h3 "SP's Transaction Ledger"]
     (if (seq entries)
       [:table.ledger-table
        [:thead
         [:tr
          [:th "Date"]
          [:th "Description"]
          [:th "Debit"]
          [:th "Credit"]
          [:th "Amount"]]]
        [:tbody
         (for [entry entries]
           ^{:key (str (:id entry))}
           [ledger-entry-row entry])]]
       [:p.empty-ledger "No transactions recorded yet. Start by selecting an action above!"])]))

;; Action type labels for display
(def action-labels
  {:purchase-inventory-cash "Purchasing Inventory (Cash)"
   :purchase-inventory-credit "Purchasing Inventory (on Credit)"
   :purchase-equipment-cash "Purchasing Equipment (Cash)"
   :purchase-equipment-credit "Purchasing Equipment (on Credit)"
   :sell-tshirts-cash "Selling T-Shirts (Cash)"
   :sell-tshirts-credit "Selling T-Shirts (on Credit)"
   :produce-tshirts "Producing T-Shirts"
   :pay-vendor "Paying Vendor"
   :collect-receivable "Collecting from Customer"})

(def inventory-type-labels
  "Human-readable labels for inventory types (raw materials)."
  {"blank-tshirts" "Blank T-Shirts (Raw Materials)"
   "ink-cartridges" "Ink Cartridges (Raw Materials)"
   :blank-tshirts "Blank T-Shirts (Raw Materials)"
   :ink-cartridges "Ink Cartridges (Raw Materials)"})

(defn transaction-summary-panel
  "Shows structured transaction details instead of narrative prose."
  []
  (let [pending (state/pending-transaction)
        vars (:variables pending)]
    [:div.column.transaction-panel
     [:h2 "Your Transaction"]
     (when pending
       [:div.transaction-summary
        [:div.transaction-type
         [:strong (get action-labels (:action-type pending) "Transaction")]]
        [:div.transaction-date
         [:span.label "Date: "] [:span.value (:date vars)]]

        ;; Show relevant details based on transaction type
        (when-let [equip (:equipment-type vars)]
          [:div.transaction-detail
           [:span.label "Equipment: "] [:span.value equip]])

        ;; Show inventory type (new)
        (when-let [inv-type (:inventory-type vars)]
          [:div.transaction-detail
           [:span.label "Item: "] [:span.value (get inventory-type-labels inv-type inv-type)]])

        (when-let [qty (:quantity vars)]
          [:div.transaction-detail
           [:span.label "Quantity: "] [:span.value qty " units"]])

        (when-let [qty (:quantity-consumed vars)]
          [:div.transaction-detail
           [:span.label "Materials Used: "] [:span.value qty " units"]])

        (when-let [vendor (:vendor vars)]
          [:div.transaction-detail
           [:span.label "Vendor: "] [:span.value vendor]])

        (when-let [customer (:customer vars)]
          [:div.transaction-detail
           [:span.label "Customer: "] [:span.value customer]])

        (when-let [amt (:amount vars)]
          [:div.transaction-amount
           [:span.label "Amount: "] [:span.value "$" amt]])])]))

;; ==================== Tutorial Components ====================

(defn- render-markdown
  "Simple markdown-like rendering for tutorial content.
   Handles **bold**, tables, and basic formatting."
  [text]
  (if (str/blank? text)
    [:span]
    (let [;; Split into paragraphs
          paragraphs (str/split text #"\n\n+")]
      [:div.markdown-content
       (for [[idx para] (map-indexed vector paragraphs)]
         ^{:key idx}
         (cond
           ;; Table detection (starts with |)
           (str/starts-with? (str/trim para) "|")
           (let [lines (str/split-lines para)
                 ;; Filter out separator lines (|---|---|)
                 data-lines (remove #(re-matches #"\|[-|\s]+\|" %) lines)
                 rows (for [line data-lines]
                        (-> line
                            (str/replace #"^\||\|$" "")
                            (str/split #"\|")
                            (->> (map str/trim))))]
             [:table.tutorial-table {:key idx}
              [:thead
               [:tr
                (for [[cidx cell] (map-indexed vector (first rows))]
                  ^{:key cidx}
                  [:th cell])]]
              [:tbody
               (for [[ridx row] (map-indexed vector (rest rows))]
                 ^{:key ridx}
                 [:tr
                  (for [[cidx cell] (map-indexed vector row)]
                    ^{:key cidx}
                    [:td cell])])]])

           ;; List items (starts with - or *)
           (re-find #"^[\-\*]\s" (str/trim para))
           [:ul {:key idx}
            (for [[lidx item] (map-indexed vector (str/split-lines para))]
              ^{:key lidx}
              [:li (-> item
                       (str/replace #"^[\-\*]\s+" "")
                       (str/replace #"\*\*([^*]+)\*\*" [:strong "$1"]))])]

           ;; Arrow notation (→)
           (str/includes? para "→")
           [:p.conclusion {:key idx}
            (-> para
                (str/replace #"\*\*([^*]+)\*\*"
                             (fn [[_ content]] (str "BOLD_START" content "BOLD_END")))
                (str/split #"(BOLD_START|BOLD_END)")
                (->> (map-indexed
                      (fn [i part]
                        (if (even? i)
                          [:span {:key i} part]
                          [:strong {:key i} part])))))]

           ;; Regular paragraph with bold support
           :else
           [:p {:key idx}
            (-> para
                (str/replace #"\*\*([^*]+)\*\*"
                             (fn [[_ content]] (str "BOLD_START" content "BOLD_END")))
                (str/split #"(BOLD_START|BOLD_END)")
                (->> (map-indexed
                      (fn [i part]
                        (if (even? i)
                          [:span {:key i} part]
                          [:strong {:key i} part])))))]))])))

(defn stage-progress-indicator
  "Shows progress through stages and current stage status."
  []
  (let [current-stage (state/current-stage)
        all-stages (tutorials/all-stages)]
    [:div.stage-progress
     (for [stage all-stages]
       (let [success-count (state/get-stage-success-count stage)
             mastery-required (tutorials/get-mastery-required stage)
             is-current? (= stage current-stage)
             is-complete? (>= success-count mastery-required)
             is-locked? (> stage current-stage)]
         ^{:key stage}
         [:div.stage-indicator
          {:class (str (when is-current? "current ")
                       (when is-complete? "complete ")
                       (when is-locked? "locked "))}
          [:span.stage-num stage]
          (when (and is-current? (not is-complete?))
            [:span.stage-progress-dots
             (for [i (range mastery-required)]
               ^{:key i}
               [:span.progress-dot {:class (when (< i success-count) "filled")}])])
          (when is-complete?
            [:span.checkmark "✓"])]))]))

(defn tutorial-modal
  "Modal overlay displaying tutorial content for the current stage."
  []
  (let [current-stage (state/current-stage)
        stage-data (tutorials/get-stage current-stage)
        tutorial (:tutorial stage-data)
        sections (:sections tutorial)
        section-idx (state/tutorial-section-index)
        current-section (get sections section-idx)
        total-sections (count sections)
        is-last-section? (= section-idx (dec total-sections))
        is-first-section? (= section-idx 0)]
    [:div.tutorial-overlay
     [:div.tutorial-modal
      [:div.tutorial-header
       [:h1 (:title stage-data)]
       [:p.subtitle (:subtitle stage-data)]
       [:div.section-indicator
        (str "Section " (inc section-idx) " of " total-sections)]]

      [:div.tutorial-content
       [:h2 (:heading current-section)]
       [render-markdown (:content current-section)]]

      [:div.tutorial-footer
       [:div.tutorial-nav
        [:button.nav-btn.prev
         {:disabled is-first-section?
          :on-click #(state/set-tutorial-section-index! (dec section-idx))}
         "← Previous"]

        [:div.section-dots
         (for [i (range total-sections)]
           ^{:key i}
           [:span.section-dot
            {:class (when (= i section-idx) "active")
             :on-click #(state/set-tutorial-section-index! i)}])]

        (if is-last-section?
          [:button.nav-btn.start
           {:on-click (fn []
                        (state/mark-tutorial-viewed! current-stage)
                        (state/set-show-tutorial! false))}
           "Start Learning →"]
          [:button.nav-btn.next
           {:on-click #(state/set-tutorial-section-index! (inc section-idx))}
           "Next →"])]]]]))

(defn tutorial-trigger-button
  "Button to re-open the tutorial for current stage."
  []
  (let [current-stage (state/current-stage)
        stage-data (tutorials/get-stage current-stage)]
    [:button.tutorial-trigger
     {:on-click (fn []
                  (state/set-tutorial-section-index! 0)
                  (state/set-show-tutorial! true))}
     [:span.help-icon "?"]
     [:span.btn-text (str "Review " (:title stage-data))]]))

(defn stage-advancement-notification
  "Shows when student advances to a new stage."
  []
  (let [current-stage (state/current-stage)
        stage-data (tutorials/get-stage current-stage)]
    [:div.stage-notification
     [:div.notification-content
      [:span.celebration "🎉"]
      [:h3 "Stage Complete!"]
      [:p (str "You've mastered Stage " (dec current-stage) ". Ready for " (:title stage-data) "?")]
      [:button.start-btn
       {:on-click (fn []
                    (state/set-tutorial-section-index! 0)
                    (state/set-show-tutorial! true))}
       "Start Next Stage"]]]))

;; ==================== Transaction Confirmation ====================

(defn transaction-confirmation
  "Shows a confirmation message after successfully completing a transaction."
  []
  (when-let [tx (state/last-completed-transaction)]
    (let [je (:journal-entry tx)
          vars (:variables tx)
          amount (:amount vars)]
      [:div.transaction-confirmation
       [:div.confirmation-header
        [:span.checkmark "✓"]
        [:span.message "Transaction recorded successfully!"]
        [:button.dismiss-btn
         {:on-click #(state/clear-last-completed-transaction!)}
         "Dismiss"]]
       ;; Show the journal entry
       (when je
         [:div.confirmation-je
          [:h4 "Journal Entry:"]
          [:table.je-table
           [:tbody
            [:tr.je-debit
             [:td.je-account (:debit je)]
             [:td.je-amount (str "$" amount)]
             [:td ""]]
            [:tr.je-credit
             [:td.je-account (str "\u00A0\u00A0\u00A0\u00A0" (:credit je))]  ;; Indent credit
             [:td ""]
             [:td.je-amount (str "$" amount)]]]]])])))

(defn account-row
  "Render a single account row in a statement."
  [{:keys [account balance]}]
  [:tr
   [:td.account-name account]
   [:td.account-balance (format-currency balance)]])

(defn financial-statements-panel
  "Display the generated financial statements."
  []
  (let [statements (state/financial-statements)]
    (if (nil? statements)
      [:div.statements-loading
       [:p "Loading financial statements..."]]
      (let [bs (:balance-sheet statements)
            is (:income-statement statements)
            tx-count (:transaction-count statements)
            as-of (:as-of-date statements)]
        [:div.financial-statements
         [:div.statements-header
          [:h2 "Financial Statements"]
          (when as-of
            [:p.as-of-date "As of " as-of " (" tx-count " transactions)"])
          [:button.close-statements
           {:on-click #(state/set-show-statements! false)}
           "×"]]

         (if (zero? tx-count)
           [:div.no-transactions
            [:p "No transactions recorded yet."]
            [:p "Complete some transactions in the simulation to see your financial statements."]]

           [:div.statements-content
            ;; Income Statement
            [:div.statement-section
             [:h3 "Income Statement"]
             [:table.statement-table
              [:thead
               [:tr [:th "Account"] [:th "Amount"]]]
              [:tbody
               ;; Revenues
               (when (seq (:revenues is))
                 [:<>
                  [:tr.section-header [:td {:colSpan 2} "Revenue"]]
                  (for [rev (:revenues is)]
                    ^{:key (:account rev)} [account-row rev])
                  [:tr.subtotal
                   [:td "Total Revenue"]
                   [:td.account-balance (format-currency (get-in is [:totals :revenue]))]]])

               ;; Expenses
               (when (seq (:expenses is))
                 [:<>
                  [:tr.section-header [:td {:colSpan 2} "Expenses"]]
                  (for [exp (:expenses is)]
                    ^{:key (:account exp)} [account-row exp])
                  [:tr.subtotal
                   [:td "Total Expenses"]
                   [:td.account-balance (format-currency (get-in is [:totals :expenses]))]]])

               ;; Net Income
               [:tr.total-row
                [:td [:strong "Net Income"]]
                [:td.account-balance {:class (if (neg? (get-in is [:totals :net-income])) "negative" "positive")}
                 (format-currency (get-in is [:totals :net-income]))]]]]]

            ;; Balance Sheet
            [:div.statement-section
             [:h3 "Balance Sheet"]
             [:table.statement-table
              [:thead
               [:tr [:th "Account"] [:th "Amount"]]]
              [:tbody
               ;; Assets
               (when (seq (:assets bs))
                 [:<>
                  [:tr.section-header [:td {:colSpan 2} "Assets"]]
                  (for [asset (:assets bs)]
                    ^{:key (:account asset)} [account-row asset])])

               ;; Contra-Assets
               (when (seq (:contra-assets bs))
                 [:<>
                  (for [ca (:contra-assets bs)]
                    ^{:key (:account ca)}
                    [:tr
                     [:td.account-name.contra (str "Less: " (:account ca))]
                     [:td.account-balance.contra (str "(" (format-currency (:balance ca)) ")")]])])

               [:tr.subtotal
                [:td "Total Assets"]
                [:td.account-balance (format-currency (get-in bs [:totals :net-assets]))]]

               ;; Liabilities
               (when (seq (:liabilities bs))
                 [:<>
                  [:tr.section-header [:td {:colSpan 2} "Liabilities"]]
                  (for [liab (:liabilities bs)]
                    ^{:key (:account liab)} [account-row liab])
                  [:tr.subtotal
                   [:td "Total Liabilities"]
                   [:td.account-balance (format-currency (get-in bs [:totals :liabilities]))]]])

               ;; Equity
               (when (seq (:equity bs))
                 [:<>
                  [:tr.section-header [:td {:colSpan 2} "Equity"]]
                  (for [eq (:equity bs)]
                    ^{:key (:account eq)} [account-row eq])
                  [:tr.subtotal
                   [:td "Total Equity"]
                   [:td.account-balance (format-currency (get-in bs [:totals :equity]))]]])

               [:tr.total-row
                [:td [:strong "Total Liabilities + Equity"]]
                [:td.account-balance (format-currency (get-in bs [:totals :liabilities-and-equity]))]]]]]

            ;; Balance check
            (let [assets (get-in bs [:totals :net-assets])
                  liab-eq (get-in bs [:totals :liabilities-and-equity])
                  balanced? (= assets liab-eq)]
              [:div.balance-check {:class (if balanced? "balanced" "unbalanced")}
               (if balanced?
                 [:span "✓ Balance Sheet is balanced"]
                 [:span "⚠ Balance Sheet is not balanced (difference: "
                  (format-currency (- assets liab-eq)) ")"])])])]))))

(defn simulation-content
  "Main content for simulation mode."
  []
  (let [pending (state/pending-transaction)
        last-tx (state/last-completed-transaction)
        show-tutorial? (state/show-tutorial?)
        show-statements? (state/show-statements?)]
    [:div.simulation-container
     ;; Tutorial modal overlay (when active)
     (when show-tutorial?
       [tutorial-modal])

     ;; Financial statements overlay (when active)
     (when show-statements?
       [:div.statements-overlay
        [financial-statements-panel]])

     ;; Stage progress bar at top
     [:div.stage-progress-bar
      [stage-progress-indicator]
      [tutorial-trigger-button]]

     [:div.simulation-top
      [business-dashboard]
      ;; When no pending transaction, show ledger directly below dashboard
      (when-not pending
        [:div.ledger-inline
         ;; Show confirmation if just completed a transaction
         [transaction-confirmation]
         [:div.ledger-section
          [:h3 "Transaction Ledger"]
          [ledger-view]]])
      [action-selection-panel]]
     ;; When there's a pending transaction, show the three-column layout
     [:div.simulation-main
      (when pending
        [:div.three-column-layout
         [transaction-summary-panel]
         [sentence-builder]
         [feedback-panel]])]
     [:div.simulation-actions
      [:button.statements-btn
       {:on-click #(do
                     (api/fetch-financial-statements!)
                     (state/set-show-statements! true))}
       "View Financial Statements"]
      [:button.reset-btn
       {:on-click #(when (js/confirm "Reset your business? This will clear all progress.")
                    (api/reset-simulation! nil)
                    (state/reset-tutorial-state!))}
       "Reset Business"]]]))

(defn mode-toggle
  "Toggle between practice and simulation modes."
  []
  (let [mode (state/app-mode)]
    [:div.mode-toggle
     [:span "Mode:"]
     [:button.mode-btn
      {:class (when (= mode :practice) "active")
       :on-click #(do
                    (state/set-app-mode! :practice)
                    (api/fetch-assertions! (state/current-level))
                    (api/fetch-problem! (state/current-level)))}
      "Practice"]
     [:button.mode-btn
      {:class (when (= mode :simulation) "active")
       :on-click #(do
                    (state/set-app-mode! :simulation)
                    (state/set-current-problem! nil)  ;; Clear practice problem
                    (state/clear-feedback!)
                    ;; Initialize tutorial state if not already set
                    (when-not (state/tutorial-viewed? (state/current-stage))
                      (state/init-tutorial-state!))
                    (api/fetch-simulation-state!)
                    (api/fetch-action-schemas!)  ;; Fetch action parameter schemas
                    (api/fetch-ledger!)
                    (api/fetch-assertions! (state/current-level)))}
      "Simulation"]]))

(defn simulation-header []
  (let [user (state/user)]
    [:div.user-header
     [:div.user-info
      [:span.user-email (:email user)]
      [mode-toggle]]
     [:button.logout-button
      {:on-click #(api/logout!)}
      "Sign Out"]]))

(defn simulation-app-content []
  [:div.app-container.simulation-mode
   [:header
    [:div.header-left
     [:h1 "SP's T-Shirt Business"]]
    [simulation-header]]
   [simulation-content]])

(defn practice-app-content []
  (let [problem (state/current-problem)
        is-construct? (= (:problem-type problem) "construct")]
    [:div.app-container
     [:header
      [:div.header-left
       [:h1 "Assertive Accounting Learning Platform"]]
      [:div.user-header
       [:div.user-info
        [:span.user-email (:email (state/user))]
        [mode-toggle]
        [progress-panel]]
       [:button.logout-button
        {:on-click #(api/logout!)}
        "Sign Out"]]]
     [:div {:class (if is-construct? "two-column-layout" "three-column-layout")}
      [narrative-panel]
      (when-not is-construct?
        [sentence-builder])
      [feedback-panel]]]))

(defn main-app []
  (let [loading? (:loading? @state/app-state)
        logged-in? (state/logged-in?)
        mode (state/app-mode)]
    (cond
      ;; Show loading spinner during session restore
      (and loading? (not logged-in?))
      [:div.loading-container
       [:div.loading-spinner]
       [:p "Loading..."]]

      ;; Show login if not logged in
      (not logged-in?)
      [login-view]

      ;; Show simulation mode
      (= mode :simulation)
      [simulation-app-content]

      ;; Show practice mode (default)
      :else
      [practice-app-content])))
