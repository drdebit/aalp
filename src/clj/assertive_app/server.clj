(ns assertive-app.server
  "Ring server for the assertive accounting educational platform."
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.util.response :as response]
            [compojure.core :refer [defroutes GET POST OPTIONS]]
            [compojure.route :as route]
            [assertive-app.classification :as classification]
            [assertive-app.auth :as auth]
            [assertive-app.progress :as progress]
            [assertive-app.simulation :as simulation]))

(defn wrap-cors
  "Middleware to enable CORS for development"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
          (assoc-in [:headers "Access-Control-Allow-Methods"] "GET, POST, PUT, DELETE, OPTIONS")
          (assoc-in [:headers "Access-Control-Allow-Headers"] "Content-Type, Authorization, x-session-token")))))

(defn wrap-auth
  "Middleware to validate session token. Adds :user to request if valid token present."
  [handler]
  (fn [request]
    (let [token (get-in request [:headers "x-session-token"])
          user (when token (auth/get-user-by-token token))]
      (handler (assoc request :user user)))))

(defroutes app-routes
  ;; Serve static files
  (GET "/" [] (response/resource-response "index.html" {:root "public"}))
  (route/resources "/")

  ;; API endpoints
  (GET "/api/health" []
    (response/response {:status "ok"}))

  ;; ==================== Authentication ====================

  (POST "/api/login" {body :body}
    (let [email (:email body)]
      (if (and email (re-matches #"^[^@]+@[^@]+\.[^@]+$" email))
        (let [user (auth/login! email)
              prog (progress/get-user-progress (:db/id user))]
          (response/response
            {:user-id (str (:db/id user))
             :email (:user/email user)
             :session-token (str (:user/session-token user))
             :current-level (:current-level prog)
             :unlocked-levels (:unlocked-levels prog)
             :level-stats (:level-stats prog)}))
        {:status 400 :body {:error "Valid email required"}})))

  (GET "/api/progress" request
    (if-let [user (:user request)]
      (response/response (progress/get-user-progress (:db/id user)))
      {:status 401 :body {:error "Authentication required"}}))

  (GET "/api/history" request
    (if-let [user (:user request)]
      (let [level (some-> (get-in request [:params "level"]) Integer/parseInt)]
        (response/response
          {:attempts (progress/get-attempt-history (:db/id user) :level level)}))
      {:status 401 :body {:error "Authentication required"}}))

  ;; ==================== Assertions & Classification ====================

  (GET "/api/assertions" [level]
    (let [student-level (if level (Integer/parseInt level) 0)
          ;; Filter assertions based on student level
          filtered-assertions (into {}
                                   (for [[domain assertions] classification/available-assertions]
                                     [domain (filter #(<= (:level % 0) student-level) assertions)]))
          ;; Resolve physical-item options from single source of truth
          resolved-assertions (classification/resolve-physical-item-options
                               filtered-assertions student-level)]
      (response/response
        {:assertions resolved-assertions})))

  (POST "/api/classify" {body :body :as request}
    (let [selected-assertions-raw (:selected-assertions body)
          ;; Recursively convert all keys to keywords (assertion codes and params)
          keywordize-map (fn keywordize-map [m]
                           (if (map? m)
                             (into {} (map (fn [[k v]] [(keyword k) (keywordize-map v)]) m))
                             m))
          selected-assertions (if (map? selected-assertions-raw)
                                (keywordize-map selected-assertions-raw)
                                (set (map keyword selected-assertions-raw)))
          correct-classification (when-let [cc (:correct-classification body)]
                                   (keyword cc))
          result (classification/classify-transaction selected-assertions
                                                     :correct-classification correct-classification)
          correct? (= :correct (get-in result [:feedback :status]))]

      ;; Return response - include progress if authenticated
      (if-let [user (:user request)]
        (let [updated-progress (progress/record-attempt!
                                 {:user-id (:db/id user)
                                  :problem-id (:problem-id body)
                                  :problem-type (or (:problem-type body) "forward")
                                  :level (or (:level body) 0)
                                  :template-level (:template-level body)  ; Template's actual difficulty
                                  :template-key (:template-key body)
                                  :selected-assertions selected-assertions-raw
                                  :correct correct?
                                  :feedback-status (name (get-in result [:feedback :status] :indeterminate))})]
          (response/response (assoc result :progress updated-progress)))
        ;; Response without progress for unauthenticated users
        (response/response result))))

  (POST "/api/generate-problem" {body :body}
    (let [level (:level body 0)
          problem-type (keyword (get body :problem-type "forward"))
          show-assertions? (:show-assertions body false)
          problem (classification/generate-problem level
                                                  :problem-type problem-type
                                                  :show-assertions show-assertions?)]
      (response/response problem)))

  (POST "/api/validate-je" {body :body :as request}
    (let [student-je {:debit-account (:debit-account body)
                      :credit-account (:credit-account body)
                      :amount (:amount body)
                      :correct-amount (:correct-amount body)}
          correct-je (:correct-journal-entry body)
          correct-assertions (:correct-assertions body)
          validation (classification/validate-journal-entry student-je correct-je correct-assertions)
          correct? (:correct? validation)]

      ;; Return response - include progress if authenticated
      (if-let [user (:user request)]
        (let [updated-progress (progress/record-attempt!
                                 {:user-id (:db/id user)
                                  :problem-id (:problem-id body)
                                  :problem-type "construct"
                                  :level (or (:level body) 0)
                                  :template-key (:template-key body)
                                  :je-debit (:debit-account body)
                                  :je-credit (:credit-account body)
                                  :je-amount (:amount body)
                                  :correct correct?
                                  :feedback-status (name (:status validation :indeterminate))})]
          (response/response {:validation validation :progress updated-progress}))
        ;; Response without progress for unauthenticated users
        (response/response {:validation validation}))))

  ;; ==================== Business Simulation ====================

  (GET "/api/simulation/state" request
    (if-let [user (:user request)]
      (let [user-id (:db/id user)
            user-level (or (:current-level (progress/get-user-progress user-id)) 0)
            business-state (simulation/get-business-state user-id)
            pending (simulation/get-pending-transaction user-id)
            available (simulation/available-actions user-level business-state)]
        (response/response
          {:business-state business-state
           :pending-transaction pending
           :available-actions available
           :user-level user-level}))
      {:status 401 :body {:error "Authentication required"}}))

  (GET "/api/simulation/action-schemas" []
    (response/response
      {:schemas (simulation/get-action-schemas)}))

  (POST "/api/simulation/start-action" {body :body :as request}
    (if-let [user (:user request)]
      (let [user-id (:db/id user)
            action-key (keyword (:action-key body))
            ;; Student-provided variables (optional)
            student-vars (or (:variables body) {})
            user-level (or (:current-level (progress/get-user-progress user-id)) 0)
            result (simulation/start-action! user-id action-key user-level student-vars)]
        (if (:error result)
          {:status 400 :body {:error (:error result)
                              :pending-transaction (:pending-transaction result)}}
          (response/response result)))
      {:status 401 :body {:error "Authentication required"}}))

  (POST "/api/simulation/classify" {body :body :as request}
    (if-let [user (:user request)]
      (let [user-id (:db/id user)
            pending (simulation/get-pending-transaction user-id)]
        (if pending
          (let [;; Parse selected assertions
                selected-assertions-raw (:selected-assertions body)
                keywordize-map (fn keywordize-map [m]
                                 (if (map? m)
                                   (into {} (map (fn [[k v]] [(keyword k) (keywordize-map v)]) m))
                                   m))
                selected-assertions (if (map? selected-assertions-raw)
                                      (keywordize-map selected-assertions-raw)
                                      (set (map keyword selected-assertions-raw)))
                ;; Classify using correct classification from pending tx
                correct-classification (:correct-classification pending)
                result (classification/classify-transaction selected-assertions
                                                           :correct-classification correct-classification)
                correct? (= :correct (get-in result [:feedback :status]))]

            ;; Increment attempts
            (simulation/increment-pending-attempts! user-id)

            (if correct?
              ;; Complete the transaction and update state
              (let [journal-entry (first (get-in result [:classification :journal-entry]))
                    completion (simulation/complete-transaction! user-id pending journal-entry)
                    user-level (or (:current-level (progress/get-user-progress user-id)) 0)
                    new-available (simulation/available-actions user-level (:business-state completion))]
                ;; Also record in progress system
                (progress/record-attempt!
                  {:user-id user-id
                   :problem-id (:problem-id pending)
                   :problem-type "simulation"
                   :level user-level
                   :template-key (:template-key pending)
                   :selected-assertions selected-assertions-raw
                   :correct true
                   :feedback-status "correct"})
                (response/response
                  {:feedback (:feedback result)
                   :classification (:classification result)
                   :correct? true
                   :ledger-entry (:ledger-entry completion)
                   :business-state (:business-state completion)
                   :available-actions new-available
                   :pending-transaction nil}))
              ;; Wrong answer - keep pending, return feedback
              (let [updated-pending (simulation/get-pending-transaction user-id)
                    user-level (or (:current-level (progress/get-user-progress user-id)) 0)]
                ;; Record attempt for analytics
                (progress/record-attempt!
                  {:user-id user-id
                   :problem-id (:problem-id pending)
                   :problem-type "simulation"
                   :level user-level
                   :template-key (:template-key pending)
                   :selected-assertions selected-assertions-raw
                   :correct false
                   :feedback-status "incorrect"})
                (response/response
                  {:feedback (:feedback result)
                   :classification (:classification result)
                   :correct? false
                   :pending-transaction updated-pending}))))
          {:status 400 :body {:error "No pending transaction"}}))
      {:status 401 :body {:error "Authentication required"}}))

  (GET "/api/simulation/ledger" request
    (if-let [user (:user request)]
      (response/response
        {:entries (simulation/get-ledger (:db/id user))})
      {:status 401 :body {:error "Authentication required"}}))

  (POST "/api/simulation/reset" request
    (if-let [user (:user request)]
      (let [result (simulation/reset-simulation! (:db/id user))]
        (response/response result))
      {:status 401 :body {:error "Authentication required"}}))

  (POST "/api/simulation/advance-period" request
    (if-let [user (:user request)]
      (let [user-id (:db/id user)
            state (simulation/get-business-state user-id)
            new-state (-> state
                          (update :current-period inc)
                          (assoc :moves-remaining simulation/MOVES_PER_PERIOD))]
        (simulation/save-business-state! user-id new-state)
        (response/response {:business-state new-state}))
      {:status 401 :body {:error "Authentication required"}}))

  (POST "/api/simulation/cancel" request
    (if-let [user (:user request)]
      (do
        (simulation/clear-pending-transaction! (:db/id user))
        (response/response {:success true}))
      {:status 401 :body {:error "Authentication required"}}))

  (GET "/api/simulation/statements" request
    (if-let [user (:user request)]
      (response/response
        (simulation/generate-financial-statements (:db/id user)))
      {:status 401 :body {:error "Authentication required"}}))

  ;; ==================== Calculation Builder Endpoints ====================

  (GET "/api/calculation-schemas" []
    ;; Return all calculation schemas for the frontend
    (response/response
      {:schemas classification/calculation-schemas}))

  (GET "/api/calculation-schema/:basis" [basis]
    ;; Return schema for a specific basis type
    (if-let [schema (classification/get-calculation-schema basis)]
      (response/response schema)
      {:status 404 :body {:error (str "Unknown calculation basis: " basis)}}))

  (GET "/api/simulation/receivables" request
    ;; Get outstanding receivables for bad debt calculation
    (if-let [user (:user request)]
      (response/response
        (simulation/get-receivables-summary (:db/id user)))
      {:status 401 :body {:error "Authentication required"}}))

  (POST "/api/calculate" {body :body}
    ;; Calculate result for a given basis and inputs
    (let [{:keys [basis inputs]} body]
      (if-let [result (classification/calculate-result basis inputs)]
        (response/response result)
        {:status 400 :body {:error "Invalid calculation parameters"}})))

  ;; CORS preflight
  (OPTIONS "*" []
    (response/response {:status "ok"}))

  ;; 404
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      wrap-auth
      wrap-keyword-params
      wrap-params
      (wrap-json-body {:keywords? true})
      wrap-json-response
      wrap-cors))

(defn start-server
  "Start the development server on port 3000"
  [& [port]]
  (let [port (or port 3000)]
    (jetty/run-jetty app {:port port :join? false})
    (println (str "Server started on http://localhost:" port))))

(defn -main [& args]
  (start-server))
