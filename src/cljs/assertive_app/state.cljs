(ns assertive-app.state
  "Global application state management using Reagent atoms."
  (:require [reagent.core :as r]))

;; Application state
(defonce app-state
  (r/atom
   {;; Authentication
    :user nil              ; User map: {:id, :email}
    :session-token nil     ; UUID string for API auth
    :logged-in? false
    :login-error nil

    ;; Progress tracking
    :progress nil          ; {:current-level, :unlocked-levels, :level-stats}

    ;; Problem state
    :current-problem nil
    :available-assertions {}
    :selected-assertions {}  ; Map to store parameters
    :feedback nil
    :current-level 0
    :problem-type "forward"  ; "forward", "reverse", or "construct"
    :unlocked-levels #{0 1 2 3 4}  ;; All levels unlocked for testing
    :loading? false
    :error nil

    ;; Journal entry construction fields
    :je-debit-account nil
    :je-credit-account nil
    :je-amount nil

    ;; App mode: :practice or :simulation
    :app-mode :practice

    ;; Business Simulation state
    :simulation {:business-state nil      ; Current business state
                 :pending-transaction nil  ; Transaction awaiting classification
                 :available-actions []     ; Actions user can take
                 :ledger []                ; Completed transactions
                 :user-level 0}}))

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

;; ==================== Authentication ====================

(defn user []
  (:user @app-state))

(defn session-token []
  (:session-token @app-state))

(defn logged-in? []
  (:logged-in? @app-state))

(defn login-error []
  (:login-error @app-state))

(defn progress []
  (:progress @app-state))

(defn unlocked-levels []
  (:unlocked-levels @app-state))

(defn set-user!
  "Set user info after successful login. Updates all auth-related state."
  [user-data]
  (let [unlocked (set (:unlocked-levels user-data [0]))]
    (swap! app-state assoc
           :user {:id (:user-id user-data)
                  :email (:email user-data)}
           :session-token (:session-token user-data)
           :logged-in? true
           :login-error nil
           :current-level (:current-level user-data 0)
           :unlocked-levels unlocked
           :progress {:current-level (:current-level user-data 0)
                      :unlocked-levels (vec unlocked)
                      :level-stats (:level-stats user-data {})})))

(defn set-login-error! [error]
  (swap! app-state assoc :login-error error))

(defn clear-login-error! []
  (swap! app-state assoc :login-error nil))

(defn logout!
  "Clear all auth state and reset to logged-out state."
  []
  (swap! app-state assoc
         :user nil
         :session-token nil
         :logged-in? false
         :login-error nil
         :progress nil
         :current-level 0
         :unlocked-levels #{0 1 2 3}))  ;; All levels unlocked for testing

(defn update-progress!
  "Update progress state from server response.
   Note: Does NOT update :current-level - that's user-controlled via the dropdown.
   The server's current-level represents the user's 'home' level, but the UI
   level should persist until the user explicitly changes it."
  [progress-data]
  (let [unlocked (set (:unlocked-levels progress-data [0]))]
    ;; Only update progress and unlocked-levels, never change current-level
    ;; User controls their level via the dropdown
    (swap! app-state assoc
           :progress progress-data
           :unlocked-levels unlocked)))

;; ==================== App Mode ====================

(defn app-mode []
  (:app-mode @app-state))

(defn set-app-mode! [mode]
  (swap! app-state assoc :app-mode mode))

(defn simulation-mode? []
  (= :simulation (:app-mode @app-state)))

(defn practice-mode? []
  (= :practice (:app-mode @app-state)))

;; ==================== Business Simulation ====================

(defn business-state []
  (get-in @app-state [:simulation :business-state]))

(defn pending-transaction []
  (get-in @app-state [:simulation :pending-transaction]))

(defn simulation-available-actions []
  (get-in @app-state [:simulation :available-actions]))

(defn ledger []
  (get-in @app-state [:simulation :ledger]))

(defn simulation-user-level []
  (get-in @app-state [:simulation :user-level]))

(defn set-simulation-state!
  "Update entire simulation state from server response."
  [sim-state]
  (swap! app-state update :simulation merge
         {:business-state (:business-state sim-state)
          :pending-transaction (:pending-transaction sim-state)
          :available-actions (:available-actions sim-state)
          :user-level (:user-level sim-state)}))

(defn set-business-state! [state]
  (swap! app-state assoc-in [:simulation :business-state] state))

(defn set-pending-transaction! [tx]
  (swap! app-state assoc-in [:simulation :pending-transaction] tx))

(defn set-simulation-available-actions! [actions]
  (swap! app-state assoc-in [:simulation :available-actions] actions))

(defn set-ledger! [entries]
  (swap! app-state assoc-in [:simulation :ledger] entries))

(defn action-schemas []
  (get-in @app-state [:simulation :action-schemas] {}))

(defn set-action-schemas! [schemas]
  (swap! app-state assoc-in [:simulation :action-schemas] schemas))

(defn clear-pending-transaction! []
  (swap! app-state assoc-in [:simulation :pending-transaction] nil))

(defn last-completed-transaction []
  (get-in @app-state [:simulation :last-completed-transaction]))

(defn set-last-completed-transaction! [tx]
  (swap! app-state assoc-in [:simulation :last-completed-transaction] tx))

(defn clear-last-completed-transaction! []
  (swap! app-state assoc-in [:simulation :last-completed-transaction] nil))

(defn update-simulation-after-classify!
  "Update simulation state after a classify response."
  [response]
  (when (:business-state response)
    (set-business-state! (:business-state response)))
  (when (:available-actions response)
    (set-simulation-available-actions! (:available-actions response)))
  (swap! app-state assoc-in [:simulation :pending-transaction]
         (:pending-transaction response)))

(defn reset-simulation!
  "Reset simulation state to initial values."
  []
  (swap! app-state assoc :simulation
         {:business-state nil
          :pending-transaction nil
          :available-actions []
          :ledger []
          :user-level 0
          :staged-action nil
          :staged-params {}
          :last-completed-transaction nil}))

;; Staged action state (for parameter entry before starting transaction)
(defn staged-action []
  (get-in @app-state [:simulation :staged-action]))

(defn staged-params []
  (get-in @app-state [:simulation :staged-params] {}))

(defn set-staged-action!
  "Set the action being configured (before transaction starts)."
  [action-key]
  (swap! app-state assoc-in [:simulation :staged-action] action-key)
  (swap! app-state assoc-in [:simulation :staged-params] {}))

(defn clear-staged-action! []
  (swap! app-state assoc-in [:simulation :staged-action] nil)
  (swap! app-state assoc-in [:simulation :staged-params] {}))

(defn update-staged-param!
  "Update a single parameter for the staged action."
  [param-key value]
  (swap! app-state assoc-in [:simulation :staged-params param-key] value))
