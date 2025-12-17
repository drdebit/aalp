(ns assertive-app.progress
  "Progress tracking and level unlocking for AALP."
  (:require [assertive-app.schema :as schema]
            [datomic.api :as d]))

;; Number of correct answers needed at a level to unlock the next level
(def CORRECT_TO_UNLOCK 5)

(defn get-user-progress
  "Get user's current progress state.
   Returns {:current-level, :unlocked-levels, :level-stats}"
  [user-id]
  (let [db (schema/db)
        ;; Get main progress entity
        progress (d/q '[:find (pull ?p [:progress/current-level :progress/unlocked-levels]) .
                        :in $ ?user
                        :where [?p :progress/user ?user]]
                      db user-id)
        ;; Get per-level stats
        level-stats (d/q '[:find ?level ?correct ?total ?unlocked
                           :in $ ?user
                           :where
                           [?lp :level-progress/user ?user]
                           [?lp :level-progress/level ?level]
                           [?lp :level-progress/correct-count ?correct]
                           [?lp :level-progress/total-attempts ?total]
                           [(get-else $ ?lp :level-progress/unlocked-next false) ?unlocked]]
                         db user-id)]
    {:current-level (or (:progress/current-level progress) 0)
     :unlocked-levels (vec (or (:progress/unlocked-levels progress) [0]))
     :level-stats (into {} (for [[level correct total unlocked] level-stats]
                             [level {:correct-count correct
                                     :total-attempts total
                                     :unlocked-next unlocked}]))}))

(defn- find-level-progress
  "Find level-progress entity for user+level combination."
  [db user-id level]
  (d/q '[:find ?lp .
         :in $ ?user ?level
         :where
         [?lp :level-progress/user ?user]
         [?lp :level-progress/level ?level]]
       db user-id level))

(defn- find-progress-entity
  "Find the progress entity for a user."
  [db user-id]
  (d/q '[:find ?p .
         :in $ ?user
         :where [?p :progress/user ?user]]
       db user-id))

(defn- check-and-unlock-next-level!
  "Check if user has earned enough correct answers to unlock next level.
   If so, update progress to unlock next level and advance current-level."
  [user-id level]
  (let [db (schema/db)
        lp-id (find-level-progress db user-id level)]
    (when lp-id
      (let [lp (d/pull db [:level-progress/correct-count :level-progress/unlocked-next :db/id] lp-id)]
        (when (and (>= (:level-progress/correct-count lp 0) CORRECT_TO_UNLOCK)
                   (not (:level-progress/unlocked-next lp)))
          ;; Unlock next level
          (let [next-level (inc level)
                progress-id (find-progress-entity db user-id)]
            (when progress-id
              @(d/transact (schema/get-conn)
                 [;; Mark this level's progress as having unlocked next
                  [:db/add lp-id :level-progress/unlocked-next true]
                  ;; Add next level to unlocked levels
                  [:db/add progress-id :progress/unlocked-levels next-level]
                  ;; Advance current-level so user starts at new level on next login
                  [:db/add progress-id :progress/current-level next-level]]))))))))

(defn- update-level-progress!
  "Update or create level-progress for a user at a specific level."
  [user-id level correct?]
  (let [db (schema/db)
        existing-lp (find-level-progress db user-id level)]
    (if existing-lp
      ;; Update existing level progress
      (let [current (d/pull db [:level-progress/correct-count :level-progress/total-attempts] existing-lp)]
        @(d/transact (schema/get-conn)
           [{:db/id existing-lp
             :level-progress/correct-count (+ (:level-progress/correct-count current 0)
                                              (if correct? 1 0))
             :level-progress/total-attempts (+ (:level-progress/total-attempts current 0) 1)}]))
      ;; Create new level progress
      @(d/transact (schema/get-conn)
         [{:level-progress/user user-id
           :level-progress/level level
           :level-progress/correct-count (if correct? 1 0)
           :level-progress/total-attempts 1
           :level-progress/unlocked-next false}]))))

(defn record-attempt!
  "Record a problem attempt. Returns updated progress.

   Required keys:
   - :user-id - Datomic entity id of the user
   - :problem-id - UUID string from generate-problem
   - :problem-type - \"forward\", \"reverse\", or \"construct\"
   - :level - integer level (0, 1, 2) - student's current level
   - :correct - boolean
   - :feedback-status - \"correct\", \"incorrect\", or \"indeterminate\"

   Optional keys:
   - :template-level - integer level of the template (its actual difficulty)
   - :template-key - keyword like :cash-sale
   - :selected-assertions - map of assertions
   - :je-debit, :je-credit, :je-amount - for construct mode

   Progress is only counted toward level unlock when template-level >= level.
   This ensures students only advance by completing problems at their current level,
   not by answering easier review problems."
  [{:keys [user-id problem-id problem-type level template-level template-key
           selected-assertions je-debit je-credit je-amount
           correct feedback-status]}]
  (let [now (java.util.Date.)
        ;; Build base attempt transaction
        attempt-tx (cond-> {:attempt/user user-id
                            :attempt/problem-id (str problem-id)
                            :attempt/problem-type (keyword problem-type)
                            :attempt/level (long level)
                            :attempt/datetime now
                            :attempt/correct? (boolean correct)
                            :attempt/feedback-status (keyword feedback-status)}
                     ;; Add optional fields
                     template-key
                     (assoc :attempt/template-key (keyword template-key))

                     selected-assertions
                     (assoc :attempt/selected-assertions (pr-str selected-assertions))

                     je-debit
                     (assoc :attempt/je-debit-account je-debit)

                     je-credit
                     (assoc :attempt/je-credit-account je-credit)

                     je-amount
                     (assoc :attempt/je-amount (bigdec je-amount)))]

    ;; Record the attempt
    @(d/transact (schema/get-conn) [attempt-tx])

    ;; Only count progress if template is at or above student's current level
    ;; This prevents advancement by answering easier review problems
    ;; If template-level not provided (legacy), count all progress
    (let [counts-for-progress? (or (nil? template-level)
                                   (>= template-level level))]
      (when counts-for-progress?
        ;; Update level progress
        (update-level-progress! user-id level correct)

        ;; Check for unlock (only if correct)
        (when correct
          (check-and-unlock-next-level! user-id level))))

    ;; Return updated progress
    (get-user-progress user-id)))

(defn get-attempt-history
  "Get recent attempts for a user, optionally filtered by level.
   Returns vector of attempt maps, most recent first."
  [user-id & {:keys [level limit] :or {limit 50}}]
  (let [db (schema/db)
        base-query '[:find [(pull ?a [*]) ...]
                     :in $ ?user
                     :where
                     [?a :attempt/user ?user]
                     [?a :attempt/datetime ?dt]]
        level-query '[:find [(pull ?a [*]) ...]
                      :in $ ?user ?level
                      :where
                      [?a :attempt/user ?user]
                      [?a :attempt/level ?level]
                      [?a :attempt/datetime ?dt]]
        results (if level
                  (d/q level-query db user-id level)
                  (d/q base-query db user-id))]
    (->> results
         (sort-by :attempt/datetime #(compare %2 %1))  ; Descending
         (take limit)
         vec)))

(defn get-level-stats
  "Get detailed statistics for a user at a specific level."
  [user-id level]
  (let [db (schema/db)
        lp-id (find-level-progress db user-id level)]
    (if lp-id
      (let [lp (d/pull db [:level-progress/correct-count
                          :level-progress/total-attempts
                          :level-progress/unlocked-next] lp-id)]
        {:level level
         :correct-count (:level-progress/correct-count lp 0)
         :total-attempts (:level-progress/total-attempts lp 0)
         :unlocked-next (:level-progress/unlocked-next lp false)
         :progress-toward-unlock (min CORRECT_TO_UNLOCK
                                      (:level-progress/correct-count lp 0))
         :needs-for-unlock (max 0 (- CORRECT_TO_UNLOCK
                                     (:level-progress/correct-count lp 0)))})
      {:level level
       :correct-count 0
       :total-attempts 0
       :unlocked-next false
       :progress-toward-unlock 0
       :needs-for-unlock CORRECT_TO_UNLOCK})))
