(ns assertive-app.server
  "Ring server for the assertive accounting educational platform."
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.util.response :as response]
            [compojure.core :refer [defroutes GET POST OPTIONS]]
            [compojure.route :as route]
            [assertive-app.classification :as classification]))

(defn wrap-cors
  "Middleware to enable CORS for development"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
          (assoc-in [:headers "Access-Control-Allow-Methods"] "GET, POST, PUT, DELETE, OPTIONS")
          (assoc-in [:headers "Access-Control-Allow-Headers"] "Content-Type, Authorization")))))

(defroutes app-routes
  ;; Serve static files
  (GET "/" [] (response/resource-response "index.html" {:root "public"}))
  (route/resources "/")

  ;; API endpoints
  (GET "/api/health" []
    (response/response {:status "ok"}))

  (GET "/api/assertions" [level]
    (let [student-level (if level (Integer/parseInt level) 0)
          ;; Filter assertions based on student level
          filtered-assertions (into {}
                                   (for [[domain assertions] classification/available-assertions]
                                     [domain (filter #(<= (:level % 0) student-level) assertions)]))]
      (response/response
        {:assertions filtered-assertions})))

  (POST "/api/classify" {body :body}
    (let [selected-assertions-raw (:selected-assertions body)
          ;; Convert to keyword keys if it's a map, otherwise convert to set
          selected-assertions (if (map? selected-assertions-raw)
                                (into {} (map (fn [[k v]] [(keyword k) v])
                                             selected-assertions-raw))
                                (set (map keyword selected-assertions-raw)))
          correct-classification (when-let [cc (:correct-classification body)]
                                   (keyword cc))
          result (classification/classify-transaction selected-assertions
                                                     :correct-classification correct-classification)]
      (response/response result)))

  (POST "/api/generate-problem" {body :body}
    (let [level (:level body 0)
          problem-type (keyword (get body :problem-type "forward"))
          show-assertions? (:show-assertions body false)
          problem (classification/generate-problem level
                                                  :problem-type problem-type
                                                  :show-assertions show-assertions?)]
      (response/response problem)))

  (POST "/api/validate-je" {body :body}
    (let [student-je {:debit-account (:debit-account body)
                      :credit-account (:credit-account body)
                      :amount (:amount body)
                      :correct-amount (:correct-amount body)}
          correct-je (:correct-journal-entry body)
          correct-assertions (:correct-assertions body)
          validation (classification/validate-journal-entry student-je correct-je correct-assertions)]
      (response/response {:validation validation})))

  ;; CORS preflight
  (OPTIONS "*" []
    (response/response {:status "ok"}))

  ;; 404
  (route/not-found "Not Found"))

(def app
  (-> app-routes
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
