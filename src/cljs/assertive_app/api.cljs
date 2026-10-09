(ns assertive-app.api
  "API client for backend communication."
  (:require [ajax.core :refer [GET POST]]
            [clojure.set :as set]
            [assertive-app.state :as state]
            [assertive-app.tutorials :as tutorials]))

;; Forward declarations for functions used before definition
(declare flush-telemetry! fetch-assertions! fetch-problem! fetch-ledger! derive-je!
         fetch-guided-state! fetch-simulation-state! fetch-action-schemas!
         enter-app!)

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
  "The handler for requests whose failure the student cannot act on.

   Not silent any more. A failed derivation used to leave the previous
   entry on screen with no sign anything had happened, and a rejected
   tutorial completion sent a student back to the same lesson on every
   login; both were 400s and 500s that this handler swallowed. Now the
   error goes to the console with its label, and a notice appears at
   the top of the page until the next request of any kind succeeds or
   the student dismisses it."
  [label]
  (fn [error]
    (let [status (:status error)
          body   (:response error)
          detail (or (:error body) (:message body) (:status-text error) "")]
      (js/console.error label (clj->js error))
      (state/set-server-error!
        (str label " "
             (when status (str "(" status ") "))
             (if (string? detail) detail (pr-str detail)))))))

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
                ;; The lessons, or the two-act arc (Guided Year ->
                ;; simulation): the server's flow decides.
                (enter-app!)
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
                           :current-level user-level
                           :completed-tutorials (set (:completed-tutorials response [])))
                    (state/update-progress! response)
                    (state/set-flow! (:flow response))
                    ;; A practice round left unfinished is picked up where
                    ;; it stopped. Restored BEFORE the guided state is
                    ;; fetched, because that is what decides which view
                    ;; renders -- and a student mid-round belongs in the
                    ;; drill, not at the day they have not reached yet.
                    ;; The guided state is fetched either way -- it is
                    ;; what sets the mode and the day the student is on.
                    ;; Resuming the drill first means fetch-guided-state!
                    ;; can see it and leave the drill's problem alone.
                    (when-let [drill (:drill-state response)]
                      (state/resume-drill! drill))
                    (enter-app!)
                    (when-let [drill (:drill-state response)]
                      (fetch-problem! (:level drill user-level)))
                    (state/set-loading! false)))
       :error-handler (fn [_]
                        ;; Invalid session - clear it
                        (clear-session!)
                        (state/set-loading! false))})))

;; ==================== The Guided Year ====================

(defn submit-simulation-costing!
  "The simulation's matching step. Same act as the Guided Year's: name
   the lot, and let the record price it or refuse it."
  [entry-id batch on-refused]
  (state/set-loading! true)
  (POST (str api-base "/simulation/cost")
    {:params {:entry-id entry-id :batch batch}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-loading! false)
                (if (:ok? response)
                  (fetch-simulation-state!)
                  (on-refused (:reason response))))
     :error-handler (make-error-handler {:message "Could not record which goods went out"})}))

(defn submit-costing!
  "Identify the goods that went out of a sale already recorded.

   Not a submission in the graded sense -- nothing is asserted about the
   exchange that was not asserted when it was booked. Either the record
   can price the lot named, or it says why it cannot, and says so in its
   own words rather than ours."
  [entry-id batch on-refused]
  (state/set-loading! true)
  (POST (str api-base "/guided/cost")
    {:params {:entry-id entry-id :batch batch}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-loading! false)
                (if (:ok? response)
                  (fetch-guided-state!)
                  (on-refused (:reason response))))
     :error-handler (make-error-handler {:message "Could not record which goods went out"})}))

(defn fetch-guided-state!
  "Entry point for the two-act arc. Loads the current Guided Year day
   and routes: Year 1 -> guided view (narrative + sentence builder),
   Year 2 -> the autonomous simulation, inheriting Year 1's state."
  []
  (GET (str api-base "/guided/state")
    {:headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-guided-day! response)
                (when-let [bs (:business-state response)]
                  (state/set-business-state! bs))
                (if (= "year2" (:phase response))
                  (do
                    (state/set-app-mode! :simulation)
                    (fetch-simulation-state!)
                    (fetch-action-schemas!)
                    (fetch-ledger!)
                    (fetch-assertions! (state/current-level)))
                  (let [level (:level response 0)]
                    (state/set-app-mode! :guided)
                    (state/stamp-problem-served!)
                    (state/set-current-level! level)
                    (fetch-assertions! level)
                    ;; Year 1 needs its own ledger too: the parties the
                    ;; student has already dealt with are what make the
                    ;; counterparty dropdown a question rather than a
                    ;; single answer handed over.
                    (fetch-ledger!)
                    ;; A student mid-practice-round belongs in the round,
                    ;; not at the day they have not reached yet: leave
                    ;; their problem and their selections alone.
                    (when (and (= "transaction" (:entry-type response))
                               (not (state/drill-active?)))
                      (state/set-current-problem!
                        {:id (str "guided-day-" (:day response))
                         :narrative (:narrative response)
                         :variables (:variables response)
                         :level level
                         :problem-type "forward"
                         :guided? true})
                      (state/clear-selections!)
                      ;; Pre-select has-date so the sentence is always visible
                      (state/toggle-assertion! :has-date)
                      (when-let [date (:date response)]
                        (state/update-assertion-parameter! :has-date :date date))
                      (state/clear-feedback!)))))
     :error-handler (make-error-handler {:message "Failed to load your business year"})}))

(defn submit-guided-answer!
  "Record the student's assertions for the current scripted day.
   Commits as asserted — the response confirms recording and carries
   the derived JE, never a correct/incorrect verdict."
  []
  (state/set-loading! true)
  (POST (str api-base "/guided/submit")
    {:params {:selected-assertions (state/selected-assertions)
              ;; Time-on-task: raw serve-to-submit seconds
              :seconds-elapsed (state/seconds-since-served)}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-guided-result! response)
                (state/set-derived-je! (:derived-je response))
                (when-let [bs (:business-state response)]
                  (state/set-business-state! bs))
                (state/clear-selections!)
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to record the transaction"})}))

(defn submit-guided-gate!
  "Resolve a corridor decision; the resulting transaction auto-enters."
  [quantity]
  (state/set-loading! true)
  (POST (str api-base "/guided/gate")
    {:params {:quantity quantity}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-guided-result! response)
                (state/set-derived-je! (:derived-je response))
                (when-let [bs (:business-state response)]
                  (state/set-business-state! bs))
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to record your decision"})}))

(defn guided-continue!
  "Advance from a recorded day to the next script entry."
  []
  (state/clear-guided-result!)
  (state/set-derived-je! nil)
  (fetch-guided-state!))

;; ==================== The lessons (pilot flow) ====================

(defn next-lesson
  "The first lesson whose tutorial is not yet complete, or nil when every
   one is. Read from completions rather than from the stored level, so a
   student's place is where their work says it is."
  []
  (first (remove state/tutorial-completed? (tutorials/all-levels))))

(defn enter-lessons!
  "Put the student in front of the lesson they are on."
  []
  (state/set-app-mode! :lessons)
  (let [level (or (when (state/drill-active?) (:level (state/drill-state)))
                  (next-lesson)
                  (last (tutorials/all-levels)))]
    (state/set-current-level! level)
    (fetch-assertions! level)))

(defn enter-app!
  "After login: the lessons, or the Guided Year and simulation."
  []
  (if (state/lessons-flow?)
    (enter-lessons!)
    (fetch-guided-state!)))

(defn fetch-lesson-summary!
  "What the lesson's problems produce, for its check-in."
  [level]
  (GET (str api-base "/lessons/summary")
    ;; A check-in can close more than one lesson (Production, then
    ;; Intellectual Property): ask for all of them.
    {:params (let [ls (tutorials/checkin-levels level)]
               (if (next ls) {:levels (clojure.string/join "," ls)} {:level level}))
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler #(state/set-checkin-summary! (:classifications %))
     :error-handler (silent-error-handler "Lesson summary error:")}))

(defn fetch-retention-problem!
  "The next look-back problem: from lessons before this one, not one
   already served in this check-in."
  [level]
  (fetch-problem! level {:levels (tutorials/earlier-lessons level)
                         :served (get-in @state/app-state [:checkin :served] [])}))

;; ==================== The reporting lesson ====================

(defn fetch-reporting-record! []
  (GET (str api-base "/lessons/reporting/record")
    {:params {:record "harbor-line"}
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler state/set-reporting-record!
     :error-handler (silent-error-handler "Reporting record error:")}))

(defn preview-report!
  "Run a composition over the lesson's record, free, and keep the figure
   and the events it collected under `key`."
  [key composition]
  (POST (str api-base "/lessons/reporting/preview")
    {:params {:record "harbor-line" :composition composition}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler #(state/set-reporting-preview! key %)
     :error-handler (silent-error-handler "Report preview error:")}))

(defn grade-report!
  "Check a report task: a composition, or for a gross profit the two
   reports it combines."
  [task {:keys [composition inputs]}]
  (POST (str api-base "/lessons/reporting/grade")
    {:params (cond-> {:record "harbor-line" :task (name task)
                      :level (:level (state/reporting))}
               composition (assoc :composition composition)
               inputs (assoc :inputs (mapv name inputs)))
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler #(state/set-reporting-grade! task %)
     :error-handler (make-error-handler {:message "Could not check the report"})}))

(defn start-reporting-round!
  "Open the reporting lesson's round: the record, and the two revenue
   reports to read, already built."
  [level]
  (state/start-reporting! level)
  (fetch-reporting-record!)
  (preview-report! :accrual-revenue {:flow "goods-out" :party "customer" :period "year" :total "consideration"})
  (preview-report! :cash-revenue {:flow "money-in" :party "customer" :period "year" :total "money-in"}))

;; ==================== The capstone's own year ====================

(declare capstone-load-next!)

(defn- capstone-load-transaction!
  "Put one transaction of the year in front of the student, read against
   their own books as they stood when it happened. `selections` pre-fills
   the sentence -- the entry being revised."
  [tx selections]
  (state/set-current-problem!
    {:id (str "capstone-" (:id tx))
     :narrative (:narrative tx)
     :prior-events (:prior-events tx)
     :variables {:date (:date tx)}
     :problem-type "forward"
     :level 8
     :capstone-tx (:id tx)})
  (state/set-derived-je! nil)
  (state/clear-feedback!)
  (if (seq selections)
    (state/set-selected-assertions! selections)
    (do (state/clear-selections!)
        (state/toggle-assertion! :has-date)
        (state/update-assertion-parameter! :has-date :date (:date tx))))
  (state/stamp-problem-served!)
  (derive-je!))

(defn fetch-capstone-state!
  "The year and the student's entries. `then` runs with the data."
  ([] (fetch-capstone-state! nil))
  ([then]
   (GET (str api-base "/capstone/state")
     {:headers (auth-headers)
      :response-format :json
      :keywords? true
      :handler (fn [data]
                 (state/set-capstone-data! data)
                 (when then (then data)))
      :error-handler (make-error-handler {:message "Could not load the year"})})))

(defn capstone-load-next!
  "The next transaction not yet recorded, or on to the review."
  [data]
  (if-let [tx (first (remove :entry (:transactions data)))]
    (capstone-load-transaction! tx nil)
    (do (state/set-current-problem! nil)
        (state/set-capstone-phase! :review))))

(defn start-capstone-round! [level]
  (state/start-capstone! level)
  (fetch-capstone-state! capstone-load-next!))

(defn capstone-revise!
  "Open an entry again, as the student recorded it."
  [tx-id]
  (when-let [tx (first (filter #(= tx-id (:id %)) (:transactions (:data (state/capstone)))))]
    (state/set-capstone-correcting! tx-id nil)
    (state/set-capstone-phase! :correct)
    (capstone-load-transaction! tx (:entry tx))))

(defn capstone-record!
  "Record the entry in front of the student. While the year is first being
   recorded nothing is said about it; a revision is told whether it now
   says what the transaction says."
  []
  (let [cp (state/capstone)
        tx (:capstone-tx (state/current-problem))]
    (state/set-loading! true)
    (POST (str api-base "/capstone/record")
      {:params {:tx tx :selected-assertions (state/selected-assertions)}
       :format :json
       :headers (auth-headers)
       :response-format :json
       :keywords? true
       :handler (fn [verdict]
                  (state/set-loading! false)
                  (if (= :correct (:phase cp))
                    (do (state/set-capstone-verdict! verdict)
                        (fetch-capstone-state!))
                    (fetch-capstone-state! capstone-load-next!)))
       :error-handler (make-error-handler {:message "Could not record the entry"})})))

(defn capstone-preview! [key composition]
  (POST (str api-base "/capstone/preview")
    {:params {:composition composition}
     :format :json :headers (auth-headers)
     :response-format :json :keywords? true
     :handler #(state/set-capstone-preview! key %)
     :error-handler (silent-error-handler "Capstone preview error:")}))

(defn capstone-grade! [task {:keys [composition inputs]}]
  (POST (str api-base "/capstone/grade")
    {:params (cond-> {:task (name task)}
               composition (assoc :composition composition)
               inputs (assoc :inputs (mapv name inputs)))
     :format :json :headers (auth-headers)
     :response-format :json :keywords? true
     :handler #(state/set-capstone-grade! task %)
     :error-handler (make-error-handler {:message "Could not check the report"})}))

;; ==================== Tutorial Completion ====================

(defn complete-tutorial!
  "POST to /api/tutorial/complete on quiz success.
   Updates local state immediately, then persists to server."
  [level]
  (state/mark-tutorial-completed-local! level)
  (POST (str api-base "/tutorial/complete")
    {:params {:level level}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [_response]
                (println "Tutorial" level "completion saved"))
     :error-handler (silent-error-handler "Error saving tutorial completion:")}))

(defn welcome-seen!
  "Begin was pressed on the welcome page: not shown again for this account."
  []
  (state/set-welcomed!)
  (POST (str api-base "/welcome")
    {:format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [_] nil)
     :error-handler (silent-error-handler "Error saving the welcome page:")}))

;; ==================== Data Fetching ====================

(defn fetch-assertions! [level]
  (GET (str api-base "/assertions")
    {:params {:level level}
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-available-assertions! (:assertions response))
                (state/set-vocabulary! (:vocabulary response)))
     :error-handler (make-error-handler {:message "Failed to load assertions"
                                          :set-loading? false})}))

(defn fetch-problem!
  "Serve a problem at `level`. Options override the drill's own bookkeeping:
   a retention check passes :below (only earlier lessons' patterns) and
   its own :served."
  [level & [{:keys [below served levels]}]]
  (state/set-loading! true)
  (POST (str api-base "/generate-problem")
    {:params (cond-> {:level level
                      :problem-type (state/problem-type)
                      ;; Patterns already served this round, so the next draw
                      ;; prefers one the student has not met yet.
                      :served (vec (or served (get-in @state/app-state [:drill :served] [])))
                      ;; Patterns missed earlier: they come round again before the
                      ;; round can be passed without them.
                      :missed (vec (if (or below levels) [] (get-in @state/app-state [:drill :missed] [])))}
               below (assoc :below below)
               levels (assoc :levels (vec levels))
               ;; A round that keeps to its own lessons' patterns
               ;; (Intellectual Property: production and designs only).
               (and (nil? below) (nil? levels) (state/drill-active?)
                    (:levels (tutorials/drill-config level)))
               (assoc :levels (vec (:levels (tutorials/drill-config level))))
               (and (state/drill-active?) (:weights (tutorials/drill-config level)))
               (assoc :weights (:weights (tutorials/drill-config level))))
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-current-problem! response)
                (state/set-je-peek! false)
                (state/set-derived-je! nil)
                (state/clear-selections!)
                ;; Pre-select has-date so the sentence is always visible
                (state/toggle-assertion! :has-date)
                (when-let [date (get-in response [:variables :date])]
                  (state/update-assertion-parameter! :has-date :date date))
                (state/clear-feedback!)
                (state/stamp-problem-served!)
                (when (or below levels) (state/note-retention-served! (:template response)))
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to load problem"})}))

;; ==================== Answer Submission ====================

(defn save-drill-state!
  "Keep the round in progress on the server, so closing the app costs a
   student their place and not the round. Fire-and-forget: a failure
   here must never interrupt the drill, it only costs a resume point."
  [drill]
  (POST (str api-base "/drill/state")
    {:params {:drill drill}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [_])
     :error-handler (silent-error-handler "Could not save the practice round:")}))

(defn submit-answer!
  "Submit assertion-based answer. Includes problem metadata for tracking."
  []
  (state/set-loading! true)
  (let [problem (state/current-problem)
        correct-classification (:correct-classification problem)]
    (POST (str api-base "/classify")
      {:params (cond-> {:selected-assertions (state/selected-assertions)
                        ;; Whose books: asserts the full form saved with it.
                        :company (:company problem)
                        :correct-classification correct-classification
                        ;; The company's record, so the grader reads the
                        ;; same paragraph the derived panel does.
                        :prior-events (:prior-events problem)
                        ;; Not graded: priced into the correct entry the
                        ;; feedback shows after a miss.
                        :correct-assertions (:correct-assertions problem)
                        ;; Include metadata for progress tracking
                        :problem-id (:id problem)
                        ;; A look back at an earlier lesson is recorded as
                        ;; such: it counts toward nothing, and the pilot can
                        ;; tell retention from practice.
                        :problem-type (if (state/retention-active?)
                                        "retention"
                                        (or (:problem-type problem) "forward"))
                        :level (:level problem 0)
                        :template-level (:template-level problem)  ; Template's actual difficulty
                        :template-key (:template problem)
                        ;; Time-on-task: raw serve-to-submit seconds
                        :seconds-elapsed (state/seconds-since-served)}
                 ;; Drill provenance: lets analytics separate test-out
                 ;; entrants from post-tutorial drillers
                 (state/drill-active?)
                 (assoc :drill-entry
                        (name (:entry-path (state/drill-state) :tutorial))))
       :format :json
       :headers (auth-headers)
       :response-format :json
       :keywords? true
       :handler (fn [response]
                  (state/set-feedback! (:feedback response))
                  ;; Dual fluency: show the JE the student's assertions produce
                  (derive-je!)
                  ;; Tutorial drill: count this attempt toward the round.
                  ;; On a miss, the omitted assertions (diffed client-side
                  ;; against the problem's answer key) feed stuck detection.
                  (when (state/drill-active?)
                    (let [status (get-in response [:feedback :status])
                          correct? (contains? #{"correct" :correct} status)
                          missing (when-not correct?
                                    (set/difference
                                      (set (keys (:correct-assertions problem)))
                                      (set (keys (state/selected-assertions)))))]
                      (state/record-drill-result! correct? missing)
                      ;; ...and keep it, so leaving now does not undo it.
                      (save-drill-state! (state/drill-state))))
                  (when (state/retention-active?)
                    (state/record-retention-result!
                      {:correct? (contains? #{"correct" :correct} (get-in response [:feedback :status]))
                       :template-level (:template-level problem)
                       :description (get-in response [:feedback :correct-classification :description])}))
                  ;; The buffer is never more likely to be abandoned than
                  ;; just after an answer goes in.
                  (flush-telemetry!)
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
                       :template-key (:template problem)
                       ;; Time-on-task: raw serve-to-submit seconds
                       :seconds-elapsed (state/seconds-since-served)})
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
                     ;; The transaction's own parties, so the sentence
                     ;; builder can offer them rather than ask for typing.
                     :variables (:variables pending)
                     :correct-assertions (:correct-assertions pending)})
                  (state/stamp-problem-served!))
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
                ;; Pre-select has-date so the sentence is always visible
                (state/toggle-assertion! :has-date)
                (when-let [date (get-in response [:variables :date])]
                  (state/update-assertion-parameter! :has-date :date date))
                (state/stamp-problem-served!)
                (state/set-loading! false))
     :error-handler (make-error-handler {:message "Failed to start action"
                                          :extract-body? true})})))

(defn submit-simulation-answer!
  "Submit answer for simulation mode. Handles both correct and incorrect responses.
   Tracks stage progress and advances when mastery is achieved."
  []
  (state/set-loading! true)
  (POST (str api-base "/simulation/classify")
    {:params {:selected-assertions (state/selected-assertions)
              ;; Time-on-task: raw serve-to-submit seconds
              :seconds-elapsed (state/seconds-since-served)}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-feedback! (:feedback response))
                ;; Retry-until-correct: the next attempt on this same
                ;; transaction measures from this feedback, not the
                ;; original serve
                (state/stamp-problem-served!)
                ;; Dual fluency: show the JE the student's assertions produce
                (derive-je!)
                (state/update-simulation-after-classify! response)
                ;; A correct sale may leave the books owing a cost match.
                ;; The classify response cannot know that -- the
                ;; obligation is derived from the ledger the sale has
                ;; just joined -- so ask the server what it now says.
                (when (:correct? response)
                  (fetch-simulation-state!))
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
                (state/clear-guided-result!)
                ;; Resetting the business restarts the two-act arc at Year 1, Day 1
                (fetch-guided-state!)
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

;; ==================== Dual Fluency: JE derivation ====================
;; Derive the journal entry the student's own assertions produce --
;; faithful, partial where underspecified, silent about correctness.

(defonce ^:private derive-seq
  ;; The number of the latest derivation asked for. Responses arrive in
  ;; whatever order the server finishes them, and a slow one for an
  ;; earlier state of the sentence used to overwrite the answer for the
  ;; current state -- an entry that stayed unpriced after the amount was
  ;; typed, until something else changed. Only the newest applies.
  (atom 0))

(defn derive-je!
  "Fetch the derived JE for the current selections."
  []
  (let [n (swap! derive-seq inc)]
  (POST (str api-base "/derive-je")
    {:params {:selected-assertions (state/selected-assertions)
              ;; No :variables. Their only use in the derivation is the
              ;; :amount fallback, which priced every line the student had
              ;; not yet given a quantity -- with the problem's own answer.
              ;; Add the obligation and the entry showed its dollar figure
              ;; before any number was typed. The walkthrough met this first ($3,000 on a
              ;; shirt purchase, from the guided day underneath); it is
              ;; the same fault everywhere. An unpriced line shows as
              ;; unpriced. The worked example still prices itself: the
              ;; canonical assertions carry their amounts.
              ;; What the student established earlier in the walkthrough.
              ;; Sent rather than stored: the walkthrough teaches, and a
              ;; student working through it twice should not accumulate
              ;; two printers in their books.
              :prior-events (if (state/walkthrough-active?)
                              (state/walkthrough-events)
                              ;; A practice problem carries the record of
                              ;; the company it belongs to.
                              (:prior-events (state/current-problem)))
              ;; Neither a lesson nor another company's problem is read
              ;; against SP's own ledger.
              :isolated (or (state/walkthrough-active?) (state/drill-active?)
                            (state/capstone-active?))}
     :format :json
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (when (= n @derive-seq)
                  (state/clear-server-error!)
                  (state/set-derived-je! response)))
     :error-handler (silent-error-handler "JE derivation error:")})))

(defn show-worked-example!
  "ALEKS-style worked example for the current drill problem: fill the
   sentence builder with the canonical assertions, derive their JE with
   per-line provenance, and forfeit the problem — it can no longer be
   submitted and won't count toward the round. Viewing is logged
   server-side (never as progress) for the pilot analytics."
  []
  (let [problem (state/current-problem)]
    (state/set-selected-assertions! (:correct-assertions problem))
    (state/set-drill-worked-example! true)
    (derive-je!)
    (POST (str api-base "/worked-example-viewed")
      {:params {:problem-id (:id problem)
                :level (:level problem 0)
                :template-key (:template problem)
                :drill-entry (name (:entry-path (state/drill-state) :tutorial))
                ;; Time-on-task: how long they worked before asking
                :seconds-elapsed (state/seconds-since-served)}
       :format :json
       :headers (auth-headers)
       :response-format :json
       :keywords? true
       :handler (fn [_response] nil)
       :error-handler (silent-error-handler "Error logging worked example:")})))

(def ^:private derive-je-timer (atom nil))

(defn derive-je-debounced!
  "Debounced derivation (recompute as students change their assertions)."
  []
  (when-let [t @derive-je-timer] (js/clearTimeout t))
  (reset! derive-je-timer (js/setTimeout derive-je! 250)))

;; The entry moves as you assert. Everywhere, now -- it used to be the
;; walkthrough only, with a note here saying this was not the moment to
;; change it. 2026-09-19 is: assertions articulating into an entry is
;; what the platform is FOR, and watching it happen beats reading that
;; it happens.
;;
;; It leaks nothing. The derivation reads the student's own assertions
;; and never the correct classification, so no amount of looking reveals
;; the key -- and a student who sees "(not yet classified)" and adds
;; `allows` has just learned the thing the drill is for. A student who
;; already knows the entry from their accounting class can work
;; backwards to the assertions that produce it, which is a real skill
;; the reverse problems test on purpose.
;;
;; Watching :selected-assertions rather than hooking each control means
;; parameter edits count too: changing a unit from goods to cash redraws
;; the entry, which is where the lesson is sharpest.
(defonce ^:private live-derivation
  (add-watch state/app-state ::live-derive
             (fn [_ _ old new]
               (when (and (seq (:selected-assertions new))
                          (not= (:selected-assertions old)
                                (:selected-assertions new)))
                 (derive-je-debounced!)))))

;; ==================== Telemetry ====================
;; What the server cannot see for itself: a tutorial section paged, a
;; journal-entry line opened, the chain expanded. The derivation
;; endpoint already records the construction of an answer, so nothing
;; about assertions belongs here.
;;
;; Buffered, because these happen far too often to be worth a round trip
;; each, and flushed on a timer and whenever an answer is submitted --
;; the moment a buffer is most likely to be abandoned.

(defonce ^:private telemetry-buffer (atom []))

(defn flush-telemetry! []
  (let [events @telemetry-buffer]
    (when (seq events)
      (reset! telemetry-buffer [])
      (POST (str api-base "/telemetry")
        {:params {:events events}
         :format :json
         :headers (auth-headers)
         :response-format :json
         :keywords? true
         :handler (fn [_])
         ;; Losing analytics must never cost a student anything, so a
         ;; failed flush is dropped rather than retried into a loop.
         :error-handler (fn [_])}))))

(defn note!
  "Note something the student did. Never blocks, never fails."
  ([kind payload] (note! kind nil payload))
  ([kind problem-id payload]
   (swap! telemetry-buffer conj
          {:kind (name kind)
           :problem-id (when problem-id (str problem-id))
           :at (.toISOString (js/Date.))
           :payload payload})
   (when (>= (count @telemetry-buffer) 25)
     (flush-telemetry!))))

;; ---- Dwell ----
;; How long a student sat on a tutorial section before paging on. The
;; client is the only thing that can answer this: the server never sees
;; a section being read, and a section paged past in two seconds and one
;; dwelt on for two minutes are the two ends of "where do they stumble".

(defonce ^:private section-clock (atom nil))

(defn note-section-left!
  "Leaving a tutorial section, with how long it held them.

   The first call of a sitting only starts the clock -- there is no
   previous section to time -- so a tutorial opened and immediately
   closed reports nothing rather than reporting a lie."
  [payload]
  (let [now (.getTime (js/Date.))
        started @section-clock]
    (reset! section-clock now)
    (when started
      (note! :section-left (assoc payload :dwell-ms (- now started))))))

(defn start-section-clock! []
  (reset! section-clock (.getTime (js/Date.))))

(defonce ^:private telemetry-timer
  (js/setInterval flush-telemetry! 20000))

;; A closing tab should not take the last few events with it.
(defonce ^:private telemetry-unload
  (.addEventListener js/window "pagehide" flush-telemetry!))

;; ==================== Report Builder ====================
;; Students compose collects/includes/excludes reports over their own
;; engine events. Preview freely; record deliberately.

(defn composition->spec
  "Convert the builder's composition state into the wire spec.
   Sets become vectors (JSON has no sets); empty groups are omitted."
  [{:keys [include-types exclude-types unit-filter date-from date-to]}]
  {:includes (cond-> {}
               (seq include-types) (assoc :assertion-types (mapv name include-types))
               unit-filter         (assoc :receives-unit unit-filter)
               (seq date-from)     (assoc :date-from date-from)
               (seq date-to)       (assoc :date-to date-to))
   :excludes (when (seq exclude-types)
               {:any-assertion-types (mapv name exclude-types)})})

(defn rb-preview!
  "Preview the current composition against the student's own events."
  []
  (let [comp* (state/rb-composition)]
    (state/set-rb-previewing! true)
    (POST (str api-base "/engine/compose/preview")
      {:params {:spec (composition->spec comp*)
                :aggregate-type (:aggregate-type comp*)
                :op (:op comp*)}
       :format :json
       :headers (auth-headers)
       :response-format :json
       :keywords? true
       :handler (fn [response]
                  (state/set-rb-preview! response))
       :error-handler (fn [error]
                        (state/set-rb-previewing! false)
                        (state/set-error! "Report preview failed")
                        (println "Report preview error:" error))})))

(defn fetch-my-reports!
  "Fetch the student's recorded report events. Derived reports are the
   only events carrying a collects assertion, so type=collects selects
   exactly them."
  []
  (GET (str api-base "/engine/events")
    {:params {:type "collects"}
     :headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-rb-my-reports! (:events response)))
     :error-handler (silent-error-handler "Error fetching reports:")}))

(defn rb-record!
  "Record the current composition as a first-class event in the
   student's ledger. This is the deliberate act; confirmation happens
   in the UI before calling."
  []
  (let [comp* (state/rb-composition)
        report-name (not-empty (:report-name comp*))]
    (state/set-loading! true)
    (POST (str api-base "/engine/compose/record")
      {:params (cond-> {:spec (composition->spec comp*)
                        :aggregate-type (:aggregate-type comp*)
                        :op (:op comp*)
                        :category (:category comp*)
                        :basis (:basis comp*)}
                 (not-empty (:allowed-by comp*)) (assoc :allowed-by (:allowed-by comp*))
                 report-name (assoc :event-id report-name))
       :format :json
       :headers (auth-headers)
       :response-format :json
       :keywords? true
       :handler (fn [response]
                  (state/set-rb-recorded! response)
                  (fetch-my-reports!)
                  (state/set-loading! false))
       :error-handler (make-error-handler {:message "Failed to record report"
                                           :extract-body? true})})))

(defn fetch-rb-input-event!
  "Fetch one input event for the expanded-report click-through view."
  [event-id]
  (GET (str api-base "/engine/event/" event-id)
    {:headers (auth-headers)
     :response-format :json
     :keywords? true
     :handler (fn [response]
                (state/set-rb-input-event-detail! (:event response)))
     :error-handler (silent-error-handler "Error fetching input event:")}))
