(ns assertive-app.state
  "Global application state management using Reagent atoms."
  (:require [reagent.core :as r]))

;; Application state
(defonce app-state
  (r/atom
   {:current-problem nil
    :available-assertions {}
    :selected-assertions {}  ; Changed from set to map to store parameters
    :feedback nil
    :current-level 0
    :problem-type "forward"  ; "forward", "reverse", or "construct"
    :unlocked-levels #{0}
    :loading? false
    :error nil
    ;; Journal entry construction fields
    :je-debit-account nil
    :je-credit-account nil
    :je-amount nil}))

;; State accessors
(defn current-problem []
  (:current-problem @app-state))

(defn available-assertions []
  (:available-assertions @app-state))

(defn selected-assertions []
  (:selected-assertions @app-state))

(defn feedback []
  (:feedback @app-state))

(defn current-level []
  (:current-level @app-state))

(defn problem-type []
  (:problem-type @app-state))

;; State mutators
(defn set-current-problem! [problem]
  (swap! app-state assoc :current-problem problem))

(defn set-available-assertions! [assertions]
  (swap! app-state assoc :available-assertions assertions))

(defn toggle-assertion! [assertion-code]
  (swap! app-state update :selected-assertions
         (fn [selected]
           (if (contains? selected assertion-code)
             (dissoc selected assertion-code)
             (assoc selected assertion-code {})))))

(defn update-assertion-parameter! [assertion-code param-key param-value]
  (swap! app-state assoc-in [:selected-assertions assertion-code param-key] param-value))

(defn clear-selections! []
  (swap! app-state assoc :selected-assertions {}))

(defn set-feedback! [feedback]
  (swap! app-state assoc :feedback feedback))

(defn clear-feedback! []
  (swap! app-state assoc :feedback nil))

(defn set-loading! [loading?]
  (swap! app-state assoc :loading? loading?))

(defn set-error! [error]
  (swap! app-state assoc :error error))

;; Journal entry construction mutators
(defn set-je-debit-account! [account]
  (swap! app-state assoc :je-debit-account account))

(defn set-je-credit-account! [account]
  (swap! app-state assoc :je-credit-account account))

(defn set-je-amount! [amount]
  (swap! app-state assoc :je-amount amount))

(defn clear-je-fields! []
  (swap! app-state assoc
         :je-debit-account nil
         :je-credit-account nil
         :je-amount nil))

(defn get-constructed-je []
  {:debit-account (:je-debit-account @app-state)
   :credit-account (:je-credit-account @app-state)
   :amount (:je-amount @app-state)})
