(ns assertive-app.api
  "API client for backend communication."
  (:require [ajax.core :refer [GET POST]]
            [assertive-app.state :as state]
            [assertive-app.tutorials :as tutorials]))

;; Forward declarations for functions used before definition
(declare fetch-assertions! fetch-problem! fetch-ledger!)

;; Detect if we're running under a subpath (e.g., /aalp/)
;; and adjust API base accordingly
(defn detect-api-base []
  (let [pathname (.-pathname js/location)]
    (cond
      ;; Running under /aalp/ - use /aalp/api
      (re-find #"^/aalp" pathname) "/aalp/api"
      ;; Default - use /api
      :else "/api")))

(def api-base (detect-api-base))

;; LocalStorage key for session persistence
(def session-storage-key "aalp-session")

;; ==================== Error Handler Factory ====================

(defn make-error-handler
  "Factory for creating consistent error handlers.
   Options:
   - :message - User-facing error message (required)
   - :set-loading? - Whether to set loading to false (default true)
   - :log-label - Label for console log (optional, defaults to :message)
   - :extract-body? - Whether to extract error from response body (default false)"
  [{:keys [message set-loading? log-label extract-body?]
    :or {set-loading? true}}]
  (fn [error]
    (let [error-msg (if extract-body?
                      (or (get-in error [:response :error]) message)
                      message)]
      (state/set-error! error-msg))
    (when set-loading?
      (state/set-loading! false))
    (println (or log-label message) error)))

(defn silent-error-handler
  "Error handler that only logs to console (no user-facing error)."
  [label]
  (fn [error]
    (println label error)))

;; ==================== Auth Helpers ====================

(defn auth-headers
  "Returns headers map with session token if logged in."
  []
  (when-let [token (state/session-token)]
    {"x-session-token" token}))

(defn save-session!
  "Save session token to localStorage."
  [token]
  (when token
    (.setItem js/localStorage session-storage-key token)))

(defn clear-session!
  "Remove session token from localStorage."
  []
  (.removeItem js/localStorage session-storage-key))

(defn get-saved-session
  "Get session token from localStorage."
  []
  (.getItem js/localStorage session-storage-key))

;; ==================== Authentication ====================

(defn login!
  "Login with email. On success, saves session and fetches initial data."
  [email]
  (state/set-loading! true)
  (state/clear-login-error!)
  (POST (str api-base "/login")
    {:params {:email email}
     :format :json
     :response-format :json
     :keywords? true
     :handler (fn [response]
                ;; Save session
                (save-session! (:session-token response))
                ;; Update state
                (state/set-user! response)
                ;; Fetch initial data
                (fetch-assertions! (:current-level response 0))
                (fetch-problem! (:current-level response 0))
                (state/set-loading! false))
     :error-handler (fn [error]
                      (state/set-login-error! "Login failed. Please check your email.")
                      (state/set-loading! false)
                      (println "Login error:" error))}))

(defn logout!
  "Clear session and reset state."
  []
  (clear-session!)
  (state/logout!))

(defn restore-session!
  "Try to restore session from localStorage on app init."
  []
  (when-let [token (get-saved-session)]
    (state/set-loading! true)
    (GET (str api-base "/progress")
      {:headers {"x-session-token" token}
       :response-format :json
       :keywords? true
       :handler (fn [response]
                  (let [user-level (:current-level response 0)]
                    ;; Session valid - restore state including current-level
                    (swap! state/app-state assoc
                           :session-token token
                           :logged-in? true
                           :current-level user-level)
                    (state/update-progress! response)
                    ;; Fetch assertions and problem at user's level
                    (fetch-assertions! user-level)
                    (fetch-problem! user-level)
                    (state/set-loading! false)))
       :error-handler (fn [_]
                        ;; Invalid session - clear it
                        (clear-session!)
                        (state/set-loading! false))})))

;; ==================== Data Fetching ====================

(defn fetch-assertions! [level]
  (GET (str api-base "/assertions")
    {:params {:level level}
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-available-assertions! (:assertions response)))
     :error-handler (make-error-handler {:message "Failed to load assertions"
                                          :set-loading? false})}))

(defn fetch-problem! [level]
  (state/set-loading! true)
  (POST (str api-base "/generate-problem")
    {:params {:level level
              :problem-type (state/problem-type)}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-current-problem! response)
                (state/clear-selections!)
                (state/clear-feedback!)
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to load problem"})}))

;; ==================== Answer Submission ====================

(defn submit-answer!
  "Submit assertion-based answer. Includes problem metadata for tracking."
  []
  (state/set-loading! true)
  (let [problem (state/current-problem)
        correct-classification (:correct-classification problem)]
    (POST (str api-base "/classify")
      {:params {:selected-assertions (state/selected-assertions)
                :correct-classification correct-classification
                ;; Include metadata for progress tracking
                :problem-id (:id problem)
                :problem-type (or (:problem-type problem) "forward")
                :level (:level problem 0)
                :template-level (:template-level problem)  ; Template's actual difficulty
                :template-key (:template problem)}
       :format :json
       :headers (auth-headers)
       :response-format :json
       :keywords? true
       :handler (fn [response]
                  (state/set-feedback! (:feedback response))
                  ;; Update progress if included in response
                  (when-let [progress (:progress response)]
                    (state/update-progress! progress))
                  (state/set-loading! false))
       :error-handler (make-error-handler {:message "Failed to submit answer"})})))

(defn submit-je!
  "Submit journal entry for construct mode. Includes problem metadata for tracking."
  []
  (state/set-loading! true)
  (let [problem (state/current-problem)
        student-je (state/get-constructed-je)
        correct-je (:correct-journal-entry problem)
        correct-amount (:correct-amount problem)
        correct-assertions (:correct-assertions problem)]
    (POST (str api-base "/validate-je")
      {:params (merge student-je
                      {:correct-journal-entry correct-je
                       :correct-amount correct-amount
                       :correct-assertions correct-assertions
                       ;; Include metadata for progress tracking
                       :problem-id (:id problem)
                       :level (:level problem 0)
                       :template-key (:template problem)})
       :format :json
       :headers (auth-headers)
       :response-format :json
       :keywords? true
       :handler (fn [response]
                  (state/set-feedback! (:validation response))
                  ;; Update progress if included in response
                  (when-let [progress (:progress response)]
                    (state/update-progress! progress))
                  (state/set-loading! false))
       :error-handler (make-error-handler {:message "Failed to validate journal entry"})})))

;; ==================== Business Simulation ====================

(defn fetch-simulation-state!
  "Fetch current simulation state including business state and available actions."
  []
  (state/set-loading! true)
  (GET (str api-base "/simulation/state")
    {:headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-simulation-state! response)
                ;; If there's a pending transaction, set it as current problem
                (when-let [pending (:pending-transaction response)]
                  (state/set-current-problem!
                    {:narrative (:narrative pending)
                     :id (:problem-id pending)
                     :template (:template-key pending)
                     :correct-assertions (:correct-assertions pending)}))
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to fetch simulation state"})}))

(defn fetch-action-schemas!
  "Fetch action parameter schemas from backend."
  []
  (GET (str api-base "/simulation/action-schemas")
    {:response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-action-schemas! (:schemas response)))
     :error-handler (silent-error-handler "Error fetching action schemas:")}))

(defn start-simulation-action!
  "Start a new action in simulation mode. Optionally accepts student-provided parameters."
  ([action-key] (start-simulation-action! action-key {}))
  ([action-key params]
   (state/set-loading! true)
   (state/clear-feedback!)
   (state/clear-staged-action!)
   (state/clear-last-completed-transaction!)
   (POST (str api-base "/simulation/start-action")
     {:params {:action-key action-key :variables params}
      :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                ;; Set pending transaction as current problem
                (state/set-pending-transaction!
                  {:action-type (:action-type response)
                   :narrative (:narrative response)
                   :variables (:variables response)
                   :problem-id (:problem-id response)
                   :level (:level response)
                   :attempts 0})
                (state/set-current-problem!
                  {:narrative (:narrative response)
                   :id (:problem-id response)
                   :variables (:variables response)
                   :action-type (:action-type response)})
                (state/clear-selections!)
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to start action"
                                          :extract-body? true})})))

(defn submit-simulation-answer!
  "Submit answer for simulation mode. Handles both correct and incorrect responses.
   Tracks stage progress and advances when mastery is achieved."
  []
  (state/set-loading! true)
  (POST (str api-base "/simulation/classify")
    {:params {:selected-assertions (state/selected-assertions)}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-feedback! (:feedback response))
                (state/update-simulation-after-classify! response)
                ;; If correct, handle success and stage progression
                (when (:correct? response)
                  (let [current-stage (state/current-stage)
                        mastery-required (tutorials/get-mastery-required current-stage)]
                    ;; Increment success count for current stage
                    (state/increment-stage-success! current-stage)
                    ;; Check if stage is now mastered
                    (let [new-success-count (state/get-stage-success-count current-stage)]
                      (when (and (>= new-success-count mastery-required)
                                 (< current-stage (tutorials/max-stage)))
                        ;; Advance to next stage after a short delay
                        (js/setTimeout
                         #(state/advance-stage! (inc current-stage))
                         1500))))
                  ;; Clear problem state
                  (state/set-current-problem! nil)
                  (state/clear-selections!)
                  ;; Store last completed transaction for confirmation display
                  (state/set-last-completed-transaction! (:ledger-entry response))
                  ;; Refresh the ledger
                  (fetch-ledger!))
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to submit answer"
                                          :extract-body? true})}))

(defn fetch-ledger!
  "Fetch user's transaction ledger."
  []
  (GET (str api-base "/simulation/ledger")
    {:headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-ledger! (:entries response)))
     :error-handler (silent-error-handler "Error fetching ledger:")}))

(defn reset-simulation!
  "Reset user's simulation to initial state."
  [on-success]
  (state/set-loading! true)
  (POST (str api-base "/simulation/reset")
    {:format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/reset-simulation!)
                (state/set-business-state! (:business-state response))
                (state/set-current-problem! nil)
                (state/clear-feedback!)
                (state/clear-selections!)
                (fetch-simulation-state!)
                (when on-success (on-success))
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to reset simulation"})}))

(defn cancel-transaction!
  "Cancel the pending transaction without affecting business state."
  []
  (POST (str api-base "/simulation/cancel")
    {:format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [_response]
                (state/set-pending-transaction! nil)
                (state/set-current-problem! nil)
                (state/clear-feedback!)
                (state/clear-selections!)
                (fetch-simulation-state!))
     :error-handler (make-error-handler {:message "Failed to cancel transaction"
                                          :set-loading? false})}))

(defn advance-period!
  "Advance to the next period in simulation."
  []
  (state/set-loading! true)
  (POST (str api-base "/simulation/advance-period")
    {:format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-business-state! (:business-state response))
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to advance period"})}))

(defn fetch-financial-statements!
  "Fetch generated financial statements from ledger."
  []
  (state/set-loading! true)
  (GET (str api-base "/simulation/statements")
    {:headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-financial-statements! response)
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to fetch financial statements"})}))

;; ==================== Calculation Builder ====================

(defn fetch-calculation-schemas!
  "Fetch all calculation schemas for the calculation builder UI."
  []
  (GET (str api-base "/calculation-schemas")
    {:response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-calculation-schemas! (:schemas response)))
     :error-handler (silent-error-handler "Error fetching calculation schemas:")}))

(defn fetch-receivables-summary!
  "Fetch outstanding receivables for bad debt calculation."
  []
  (GET (str api-base "/simulation/receivables")
    {:headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-receivables-summary! response))
     :error-handler (silent-error-handler "Error fetching receivables:")}))

(defn calculate!
  "Calculate result for a given basis and inputs.
   Calls on-result with the calculation result."
  [basis inputs on-result]
  (POST (str api-base "/calculate")
    {:params {:basis basis :inputs inputs}
     :format :json
     :response-format :json
     :keywords? true
     :handler on-result
     :error-handler (fn [error]
                      (on-result {:error (or (get-in error [:response :error])
                                             "Calculation failed")}))}))
