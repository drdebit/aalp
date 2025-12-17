(ns assertive-app.api
  "API client for backend communication."
  (:require [ajax.core :refer [GET POST]]
            [assertive-app.state :as state]))

;; Forward declarations for functions used before definition
(declare fetch-assertions! fetch-problem!)

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
                  ;; Session valid - restore state
                  (swap! state/app-state assoc
                         :session-token token
                         :logged-in? true)
                  (state/update-progress! response)
                  ;; Fetch assertions and problem
                  (fetch-assertions! (:current-level response 0))
                  (fetch-problem! (:current-level response 0))
                  (state/set-loading! false))
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
     :error-handler (fn [error]
                      (state/set-error! "Failed to load assertions")
                      (println "Error loading assertions:" error))}))

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
     :error-handler (fn [error]
                      (state/set-error! "Failed to load problem")
                      (state/set-loading! false)
                      (println "Error loading problem:" error))}))

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
       :error-handler (fn [error]
                        (state/set-error! "Failed to submit answer")
                        (state/set-loading! false)
                        (println "Error submitting answer:" error))})))

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
       :error-handler (fn [error]
                        (state/set-error! "Failed to validate journal entry")
                        (state/set-loading! false)
                        (println "Error validating JE:" error))})))

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
     :error-handler (fn [error]
                      (state/set-error! "Failed to fetch simulation state")
                      (state/set-loading! false)
                      (println "Error fetching simulation state:" error))}))

(defn start-simulation-action!
  "Start a new action in simulation mode."
  [action-key]
  (state/set-loading! true)
  (state/clear-feedback!)
  (POST (str api-base "/simulation/start-action")
    {:params {:action-key action-key}
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
     :error-handler (fn [error]
                      (let [err-body (get-in error [:response :error])]
                        (state/set-error! (or err-body "Failed to start action")))
                      (state/set-loading! false)
                      (println "Error starting action:" error))}))

(defn submit-simulation-answer!
  "Submit answer for simulation mode. Handles both correct and incorrect responses."
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
                ;; If correct, clear the problem
                (when (:correct? response)
                  (state/set-current-problem! nil)
                  (state/clear-selections!))
                (state/set-loading! false))
     :error-handler (fn [error]
                      (let [err-body (get-in error [:response :error])]
                        (state/set-error! (or err-body "Failed to submit answer")))
                      (state/set-loading! false)
                      (println "Error submitting simulation answer:" error))}))

(defn fetch-ledger!
  "Fetch user's transaction ledger."
  []
  (GET (str api-base "/simulation/ledger")
    {:headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-ledger! (:entries response)))
     :error-handler (fn [error]
                      (println "Error fetching ledger:" error))}))

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
     :error-handler (fn [error]
                      (state/set-error! "Failed to reset simulation")
                      (state/set-loading! false)
                      (println "Error resetting simulation:" error))}))

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
     :error-handler (fn [error]
                      (state/set-error! "Failed to advance period")
                      (state/set-loading! false)
                      (println "Error advancing period:" error))}))
