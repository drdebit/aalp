(ns assertive-app.views
  "Reagent components for the UI."
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [assertive-app.state :as state]
            [assertive-app.api :as api]))

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
       (str "0 - Cash Transactions" (when-not (contains? unlocked 0) " 🔒"))]
      [:option {:value 1 :disabled (not (contains? unlocked 1))}
       (str "1 - Credit Transactions" (when-not (contains? unlocked 1) " 🔒"))]
      [:option {:value 2 :disabled (not (contains? unlocked 2))}
       (str "2 - Production/Transformation" (when-not (contains? unlocked 2) " 🔒"))]
      [:option {:value 3 :disabled (not (contains? unlocked 3))}
       (str "3 - Legal/Regulatory" (when-not (contains? unlocked 3) " 🔒"))]]
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
          ;; Only show physical-item when unit is "physical-unit" (Goods/Services)
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
                          [:span.linkage " ← " (format-linkage debit-linkage)])]
                       [:div.entry-line
                        [:span.credit "CR: " (:credit entry)]
                        (when credit-linkage
                          [:span.linkage " ← " (format-linkage credit-linkage)])]]))
                  ;; Show counterparty on its own line
                  (when counterparty-str
                    [:div.counterparty-line
                     [:span.linkage "Counterparty: " counterparty-str]])]))

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
    (str "$" (.toLocaleString (js/Number amount)))
    "$0"))

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
  "Panel for selecting actions in simulation mode."
  []
  (let [actions (state/simulation-available-actions)
        pending (state/pending-transaction)
        staged (state/staged-action)]
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
          [action-button action])]

       ;; Loading
       :else
       [:div.no-actions
        [:p "Loading available actions..."]
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

(defn simulation-content
  "Main content for simulation mode."
  []
  (let [pending (state/pending-transaction)
        last-tx (state/last-completed-transaction)]
    [:div.simulation-container
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
         [assertion-panel]
         [feedback-panel]])]
     [:div.simulation-actions
      [:button.reset-btn
       {:on-click #(when (js/confirm "Reset your business? This will clear all progress.")
                    (api/reset-simulation! nil))}
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
        [assertion-panel])
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
