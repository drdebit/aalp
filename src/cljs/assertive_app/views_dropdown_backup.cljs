(ns assertive-app.views-dropdown-backup
  "BACKUP: Original dropdown-based assertion panel components.

   This file preserves the dropdown interface before integrating the sentence-builder UI.
   To restore: copy these components back to views.cljs and replace sentence-builder usage.

   Key components:
   - parameter-input: Individual parameter inputs (dropdown, number, text, etc.)
   - assertion-button: Clickable button for selecting assertions with inline parameters
   - assertion-group: Groups assertions by domain
   - assertion-panel: Main panel showing all available assertions

   Date backed up: 2026-01-16"
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [assertive-app.state :as state]))

;; ==================== Dropdown-Based Parameter Input ====================

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

;; ==================== Auto-Population Logic ====================

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

;; ==================== Assertion Button ====================

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

;; ==================== Assertion Group ====================

(defn assertion-group [domain-key assertions]
  [:div.assertion-group
   [:h4 (name domain-key)]
   (for [assertion assertions]
     ^{:key (:code assertion)}
     [assertion-button assertion])])

;; ==================== Main Assertion Panel (Dropdown-based) ====================

(defn assertion-panel
  "Original dropdown-based assertion panel.

   Shows assertions grouped by domain, with expandable parameter inputs
   appearing below each selected assertion button.

   Usage: Replace [sentence-builder] with [assertion-panel] in views.cljs to restore."
  []
  (let [assertions (state/available-assertions)]
    [:div.column.assertion-panel
     [:h2 "Select Assertions"]
     [:div.assertion-groups
      (for [[domain-key domain-assertions] assertions]
        ^{:key domain-key}
        [assertion-group domain-key domain-assertions])]]))

;; ==================== CSS Note ====================
;; The dropdown interface uses these CSS classes:
;;   .assertion-panel - Main container
;;   .assertion-groups - Groups container
;;   .assertion-group - Single domain group
;;   .assertion-item - Single assertion container
;;   .assertion-button - Clickable button
;;   .assertion-button.selected - Selected state
;;   .assertion-label - Button text
;;   .parameters - Parameter inputs container
;;   .parameter - Single parameter row
;;   .parameter-input - Input field
;;   .parameter-input.date-input - Date picker
