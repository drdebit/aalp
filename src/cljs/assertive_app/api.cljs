(ns assertive-app.api
  "API client for backend communication."
  (:require [ajax.core :refer [GET POST]]
            [assertive-app.state :as state]))

;; Use relative URLs to work with both local and remote access
(def api-base "/api")

(defn fetch-assertions! [level]
  (GET (str api-base "/assertions")
    {:params {:level level}
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

(defn submit-answer! []
  (state/set-loading! true)
  (let [problem (state/current-problem)
        correct-classification (:correct-classification problem)]
    (POST (str api-base "/classify")
      {:params {:selected-assertions (state/selected-assertions)
                :correct-classification correct-classification}
       :format :json
       :response-format :json
       :keywords? true
       :handler (fn [response]
                  (state/set-feedback! (:feedback response))
                  (state/set-loading! false))
       :error-handler (fn [error]
                        (state/set-error! "Failed to submit answer")
                        (state/set-loading! false)
                        (println "Error submitting answer:" error))})))

(defn submit-je! []
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
                       :correct-assertions correct-assertions})
       :format :json
       :response-format :json
       :keywords? true
       :handler (fn [response]
                  (state/set-feedback! (:validation response))
                  (state/set-loading! false))
       :error-handler (fn [error]
                        (state/set-error! "Failed to validate journal entry")
                        (state/set-loading! false)
                        (println "Error validating JE:" error))})))
