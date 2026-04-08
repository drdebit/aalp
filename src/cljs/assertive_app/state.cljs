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

    ;; Tutorial quiz gating
    :completed-tutorials #{}
    :tutorial-quiz {:active? false
                    :level nil
                    :phase :reading  ;; :reading -> :quiz -> :results -> :retry (loops)
                    :section-index 0
                    :quiz-answers {}
                    :quiz-results nil
                    :missed-questions []
                    :retry-round false
                    :review-only? false}

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
  (let [code (keyword assertion-code)]
    (swap! app-state update :selected-assertions
           (fn [selected]
             (if (contains? selected code)
               (dissoc selected code)
               (assoc selected code {}))))))

(defn update-assertion-parameter! [assertion-code param-key param-value]
  (swap! app-state assoc-in [:selected-assertions (keyword assertion-code) param-key] param-value))

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
  (let [unlocked (set (:unlocked-levels user-data [0]))
        completed (set (:completed-tutorials user-data []))]
    (swap! app-state assoc
           :user {:id (:user-id user-data)
                  :email (:email user-data)}
           :session-token (:session-token user-data)
           :logged-in? true
           :login-error nil
           :current-level (:current-level user-data 0)
           :unlocked-levels unlocked
           :completed-tutorials completed
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
  (let [unlocked (set (:unlocked-levels progress-data [0]))
        completed (set (:completed-tutorials progress-data []))]
    ;; Only update progress and unlocked-levels, never change current-level
    ;; User controls their level via the dropdown
    (swap! app-state assoc
           :progress progress-data
           :unlocked-levels unlocked
           :completed-tutorials completed)))

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

;; Financial Statements state
(defn financial-statements []
  (get-in @app-state [:simulation :financial-statements]))

(defn set-financial-statements! [statements]
  (swap! app-state assoc-in [:simulation :financial-statements] statements))

(defn show-statements? []
  (get-in @app-state [:simulation :show-statements?] false))

(defn set-show-statements! [show?]
  (swap! app-state assoc-in [:simulation :show-statements?] show?))

(defn toggle-statements! []
  (swap! app-state update-in [:simulation :show-statements?] not))

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
          :last-completed-transaction nil
          :tutorial {:current-stage 1
                     :stage-successes {}
                     :tutorials-viewed #{}
                     :show-tutorial? true
                     :section-index 0}}))

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

;; ==================== Tutorial Progress ====================
;; Tracks stage progression and tutorial completion in simulation mode

(defn tutorial-state []
  "Returns the current tutorial state."
  (get-in @app-state [:simulation :tutorial] {}))

(defn current-stage []
  "Returns the current tutorial stage (1-4)."
  (get-in @app-state [:simulation :tutorial :current-stage] 1))

(defn stage-successes []
  "Returns map of stage -> success count."
  (get-in @app-state [:simulation :tutorial :stage-successes] {}))

(defn tutorials-viewed []
  "Returns set of stage numbers whose tutorials have been viewed."
  (get-in @app-state [:simulation :tutorial :tutorials-viewed] #{}))

(defn show-tutorial? []
  "Returns true if tutorial modal should be shown."
  (get-in @app-state [:simulation :tutorial :show-tutorial?] false))

(defn tutorial-section-index []
  "Returns current section index within tutorial."
  (get-in @app-state [:simulation :tutorial :section-index] 0))

(defn set-current-stage! [stage]
  "Set the current tutorial stage."
  (swap! app-state assoc-in [:simulation :tutorial :current-stage] stage))

(defn set-show-tutorial! [show?]
  "Show or hide the tutorial modal."
  (swap! app-state assoc-in [:simulation :tutorial :show-tutorial?] show?))

(defn set-tutorial-section-index! [idx]
  "Set the current section index within the tutorial."
  (swap! app-state assoc-in [:simulation :tutorial :section-index] idx))

(defn mark-tutorial-viewed! [stage]
  "Mark a stage's tutorial as viewed."
  (swap! app-state update-in [:simulation :tutorial :tutorials-viewed]
         (fn [viewed] (conj (or viewed #{}) stage))))

(defn tutorial-viewed? [stage]
  "Check if a stage's tutorial has been viewed."
  (contains? (tutorials-viewed) stage))

(defn increment-stage-success! [stage]
  "Increment the success count for a stage."
  (swap! app-state update-in [:simulation :tutorial :stage-successes stage]
         (fn [count] (inc (or count 0)))))

(defn get-stage-success-count [stage]
  "Get the number of successful transactions for a stage."
  (get-in @app-state [:simulation :tutorial :stage-successes stage] 0))

(defn stage-mastered? [stage mastery-required]
  "Check if a stage has been mastered (enough successful transactions)."
  (>= (get-stage-success-count stage) mastery-required))

(defn advance-stage!
  "Advance to the next stage if current stage is mastered."
  [next-stage]
  (swap! app-state assoc-in [:simulation :tutorial :current-stage] next-stage)
  ;; Automatically show the new stage's tutorial
  (set-tutorial-section-index! 0)
  (set-show-tutorial! true))

(defn init-tutorial-state!
  "Initialize tutorial state for a new simulation or from server data."
  [& [server-data]]
  (let [default-state {:current-stage 1
                       :stage-successes {}
                       :tutorials-viewed #{}
                       :show-tutorial? true  ;; Show tutorial on first load
                       :section-index 0}
        state (if server-data
                (merge default-state server-data)
                default-state)]
    (swap! app-state assoc-in [:simulation :tutorial] state)))

(defn reset-tutorial-state!
  "Reset tutorial progress."
  []
  (swap! app-state assoc-in [:simulation :tutorial]
         {:current-stage 1
          :stage-successes {}
          :tutorials-viewed #{}
          :show-tutorial? true
          :section-index 0}))

;; ==================== Calculation Builder ====================
;; State for the interactive calculation builder UI

(defn calculation-schemas []
  "Get calculation schemas fetched from backend."
  (get-in @app-state [:calculation :schemas] {}))

(defn set-calculation-schemas! [schemas]
  "Store calculation schemas from backend."
  (swap! app-state assoc-in [:calculation :schemas] schemas))

(defn receivables-summary []
  "Get receivables summary for bad debt calculation."
  (get-in @app-state [:calculation :receivables-summary] {}))

(defn set-receivables-summary! [summary]
  "Store receivables summary from backend."
  (swap! app-state assoc-in [:calculation :receivables-summary] summary))

(defn calculation-inputs []
  "Get current calculation input values."
  (get-in @app-state [:calculation :inputs] {}))

(defn set-calculation-input! [input-key value]
  "Set a single calculation input value."
  (swap! app-state assoc-in [:calculation :inputs input-key] value))

(defn clear-calculation-inputs! []
  "Clear all calculation input values."
  (swap! app-state assoc-in [:calculation :inputs] {}))

(defn calculation-result []
  "Get the current calculation result."
  (get-in @app-state [:calculation :result]))

(defn set-calculation-result! [result]
  "Store a calculation result."
  (swap! app-state assoc-in [:calculation :result] result))

(defn clear-calculation-result! []
  "Clear the calculation result."
  (swap! app-state assoc-in [:calculation :result] nil))


;; ==================== Tutorial Quiz Gating ====================
;; State for the tutorial reading + quiz flow that gates level access

(defn completed-tutorials []
  "Returns set of completed tutorial level numbers."
  (:completed-tutorials @app-state #{}))

(defn tutorial-completed? [level]
  "Check if a specific level's tutorial has been completed."
  (contains? (:completed-tutorials @app-state #{}) level))

(defn tutorial-quiz-state []
  "Returns the current tutorial-quiz state map."
  (:tutorial-quiz @app-state))

(defn tutorial-quiz-active? []
  "Returns true if the tutorial-quiz overlay is active."
  (get-in @app-state [:tutorial-quiz :active?] false))

(defn tutorial-quiz-phase []
  "Returns the current phase: :reading, :quiz, :results, or :retry."
  (get-in @app-state [:tutorial-quiz :phase] :reading))

(defn tutorial-quiz-level []
  "Returns the level of the active tutorial quiz."
  (get-in @app-state [:tutorial-quiz :level]))

(defn tutorial-quiz-section-index []
  "Returns the current section index in the reader."
  (get-in @app-state [:tutorial-quiz :section-index] 0))

(defn tutorial-quiz-answers []
  "Returns map of question-id -> selected choice index."
  (get-in @app-state [:tutorial-quiz :quiz-answers] {}))

(defn tutorial-quiz-results []
  "Returns the quiz results: vector of {:question :correct? :user-answer :correct-answer :explanation}."
  (get-in @app-state [:tutorial-quiz :quiz-results]))

(defn tutorial-quiz-missed []
  "Returns vector of missed question data for retry."
  (get-in @app-state [:tutorial-quiz :missed-questions] []))

(defn tutorial-quiz-retry? []
  "Returns true if we're in a retry round."
  (get-in @app-state [:tutorial-quiz :retry-round] false))

(defn tutorial-quiz-review-only? []
  "Returns true if the tutorial is open for review (no quiz required)."
  (get-in @app-state [:tutorial-quiz :review-only?] false))

(defn start-tutorial-quiz!
  "Open the tutorial-quiz flow for a given level."
  [level & {:keys [review-only?] :or {review-only? false}}]
  (swap! app-state assoc :tutorial-quiz
         {:active? true
          :level level
          :phase :reading
          :section-index 0
          :quiz-answers {}
          :quiz-results nil
          :missed-questions []
          :retry-round false
          :review-only? review-only?}))

(defn set-tutorial-quiz-section! [idx]
  "Set the current reading section index."
  (swap! app-state assoc-in [:tutorial-quiz :section-index] idx))

(defn advance-to-quiz!
  "Move from reading phase to quiz phase."
  []
  (swap! app-state assoc-in [:tutorial-quiz :phase] :quiz)
  (swap! app-state assoc-in [:tutorial-quiz :quiz-answers] {}))

(defn set-quiz-answer! [question-id choice-idx]
  "Record the user's answer for a quiz question."
  (swap! app-state assoc-in [:tutorial-quiz :quiz-answers question-id] choice-idx))

(defn set-quiz-results! [results missed]
  "Set quiz results and missed questions. Moves to :results phase."
  (swap! app-state assoc-in [:tutorial-quiz :quiz-results] results)
  (swap! app-state assoc-in [:tutorial-quiz :missed-questions] missed)
  (swap! app-state assoc-in [:tutorial-quiz :phase] :results))

(defn start-retry-round! []
  "Start a retry round with only the missed questions."
  (swap! app-state assoc-in [:tutorial-quiz :phase] :quiz)
  (swap! app-state assoc-in [:tutorial-quiz :quiz-answers] {})
  (swap! app-state assoc-in [:tutorial-quiz :quiz-results] nil)
  (swap! app-state assoc-in [:tutorial-quiz :retry-round] true))

(defn mark-tutorial-completed-local! [level]
  "Add a level to the local completed-tutorials set."
  (swap! app-state update :completed-tutorials (fn [s] (conj (or s #{}) level))))

(defn close-tutorial-quiz! []
  "Close the tutorial-quiz overlay and reset its state."
  (swap! app-state assoc :tutorial-quiz
         {:active? false
          :level nil
          :phase :reading
          :section-index 0
          :quiz-answers {}
          :quiz-results nil
          :missed-questions []
          :retry-round false
          :review-only? false}))
