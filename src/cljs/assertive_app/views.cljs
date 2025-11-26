(ns assertive-app.views
  "Reagent components for the UI."
  (:require [reagent.core :as r]
            [assertive-app.state :as state]
            [assertive-app.api :as api]))

(defn level-selector []
  (let [current-level (state/current-level)
        current-problem-type (state/problem-type)]
    [:div.level-selector
     [:label "Level: "]
     [:select {:value current-level
               :on-change #(let [new-level (js/parseInt (.. % -target -value))]
                            (swap! state/app-state assoc :current-level new-level)
                            (api/fetch-assertions! new-level)
                            (api/fetch-problem! new-level))}
      [:option {:value 0} "0 - Cash Transactions"]
      [:option {:value 1} "1 - Credit Transactions"]
      [:option {:value 2} "2 - Production/Transformation"]]
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
         ^{:key (str "debit-" account)}
         [:option {:value account} account])]
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
         ^{:key (str "credit-" account)}
         [:option {:value account} account])]]]))

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

      :else
      [:div (str "Unknown parameter type: " param-type)])))

(defn assertion-button [assertion]
  (let [assertion-code (:code assertion)
        selected-assertions (state/selected-assertions)
        selected? (contains? selected-assertions assertion-code)
        current-params (get selected-assertions assertion-code)]
    [:div.assertion-item
     [:button.assertion-button
      {:class (when selected? "selected")
       :on-click #(state/toggle-assertion! assertion-code)
       :title (:description assertion)}  ;; Show description on hover
      [:div.assertion-label (:label assertion)]]

     ;; Show parameter inputs if assertion is selected and parameterized
     (when (and selected? (:parameterized assertion))
       [:div.parameters
        (for [[param-key param-spec] (:parameters assertion)]
          ;; Only show asset-type when unit is "physical-unit"
          (when (or (not= param-key :asset-type)
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

     ;; Show Submit/Clear buttons before feedback is received
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
          [:button.primary
           {:on-click #(api/submit-answer!)
            :disabled (empty? (keys selected))}
           "Submit Answer"])
        [:button.secondary
         {:on-click #(if is-construct?
                      (state/clear-je-fields!)
                      (state/clear-selections!))}
         "Clear"]])

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
                :incorrect "Needs work"
                :incomplete "⚠ Incomplete"
                :indeterminate "Cannot classify"
                (str "Status: " (:status feedback)))]
         [:p (:message feedback)]]

        ;; For reverse problems, show the transaction narrative after submission
        (when (and is-reverse? (:narrative problem))
          [:div.narrative-reveal
           [:h4 "Transaction:"]
           [:p (:narrative problem)]])

        (when-let [hints (:hints feedback)]
          (when (seq hints)
            [:div.hints
             [:h4 "Hints:"]
             [:ul
              (for [hint hints]
                ^{:key hint}
                [:li hint])]]))

        ;; Only show journal entry for forward problems (reverse problems already show it)
        (when (and (not is-reverse?) (some? (:classification feedback)))
          (let [classification (:classification feedback)]
            [:div
             (when-let [journal-entries (:journal-entry classification)]
               (when (seq journal-entries)
                 [:div.journal-entry
                  [:h4 "Journal Entry:"]
                  (for [entry journal-entries]
                    ^{:key (str (:debit entry) "-" (:credit entry))}
                    [:div.entry
                     [:span.debit "DR: " (:debit entry)]
                     [:span.credit "CR: " (:credit entry)]])]))
             (when-let [note (:note classification)]
               [:p.note note])]))

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

(defn main-app []
  (let [problem (state/current-problem)
        is-construct? (= (:problem-type problem) "construct")]
    [:div.app-container
     [:header
      [:h1 "Assertive Accounting Learning Platform"]
      [:p.subtitle "Learn accounting through logical assertions"]]
     [:div {:class (if is-construct? "two-column-layout" "three-column-layout")}
      [narrative-panel]
      (when-not is-construct?
        [assertion-panel])
      [feedback-panel]]]))
