(ns assertive-app.analytics
  "Learning analytics for AALP.

   Real-time queries use indexed Datomic attributes on attempts
   (Option B: missing-assertions, extra-assertions, selected-assertion-types).

   Batch analytics parse the full classification-diff EDN (Option A)
   for deeper analysis."
  (:require [assertive-app.schema :as schema]
            [datomic.api :as d]
            [clojure.edn :as edn]))

;; ---------------------------------------------------------------------------
;; Real-time: per-student weakness detection
;; ---------------------------------------------------------------------------

(defn student-weakness-report
  "Identify assertion types a student struggles with most.
   Returns a ranked list of assertion types by how often they're missed.

   Options:
     :level  - filter to a specific level
     :limit  - max recent attempts to analyze (default 50)"
  [user-id & {:keys [level limit] :or {limit 50}}]
  (let [db (schema/db)
        ;; Find attempts with missing assertions
        missing-data (if level
                       (d/q '[:find ?missing (count ?a)
                              :in $ ?user ?level
                              :where
                              [?a :attempt/user ?user]
                              [?a :attempt/level ?level]
                              [?a :attempt/missing-assertions ?missing]]
                            db user-id level)
                       (d/q '[:find ?missing (count ?a)
                              :in $ ?user
                              :where
                              [?a :attempt/user ?user]
                              [?a :attempt/missing-assertions ?missing]]
                            db user-id))
        ;; Find attempts with prohibited extras
        extra-data (if level
                     (d/q '[:find ?extra (count ?a)
                            :in $ ?user ?level
                            :where
                            [?a :attempt/user ?user]
                            [?a :attempt/level ?level]
                            [?a :attempt/extra-assertions ?extra]]
                          db user-id level)
                     (d/q '[:find ?extra (count ?a)
                            :in $ ?user
                            :where
                            [?a :attempt/user ?user]
                            [?a :attempt/extra-assertions ?extra]]
                          db user-id))
        ;; Total incorrect attempts for normalization
        total-incorrect (or (if level
                              (d/q '[:find (count ?a) .
                                     :in $ ?user ?level
                                     :where
                                     [?a :attempt/user ?user]
                                     [?a :attempt/level ?level]
                                     [?a :attempt/correct? false]]
                                   db user-id level)
                              (d/q '[:find (count ?a) .
                                     :in $ ?user
                                     :where
                                     [?a :attempt/user ?user]
                                     [?a :attempt/correct? false]]
                                   db user-id))
                            0)]
    {:most-missed (->> missing-data
                       (sort-by second >)
                       (mapv (fn [[atype count]]
                               {:assertion-type atype
                                :times-missed count
                                :rate (when (pos? total-incorrect)
                                        (double (/ count total-incorrect)))})))
     :most-over-used (->> extra-data
                          (sort-by second >)
                          (mapv (fn [[atype count]]
                                  {:assertion-type atype
                                   :times-extra count
                                   :rate (when (pos? total-incorrect)
                                           (double (/ count total-incorrect)))})))
     :total-incorrect total-incorrect}))

(defn student-needs-practice?
  "Quick check: does a student need more practice on a specific assertion type?
   Returns true if they've missed this assertion in >30% of recent incorrect attempts."
  [user-id assertion-type & {:keys [level threshold] :or {threshold 0.3}}]
  (let [db (schema/db)
        ;; Count attempts where this assertion was missed
        missed (or (if level
                     (d/q '[:find (count ?a) .
                            :in $ ?user ?level ?atype
                            :where
                            [?a :attempt/user ?user]
                            [?a :attempt/level ?level]
                            [?a :attempt/missing-assertions ?atype]]
                          db user-id level assertion-type)
                     (d/q '[:find (count ?a) .
                            :in $ ?user ?atype
                            :where
                            [?a :attempt/user ?user]
                            [?a :attempt/missing-assertions ?atype]]
                          db user-id assertion-type))
                   0)
        ;; Count total incorrect attempts
        total (or (if level
                    (d/q '[:find (count ?a) .
                           :in $ ?user ?level
                           :where
                           [?a :attempt/user ?user]
                           [?a :attempt/level ?level]
                           [?a :attempt/correct? false]]
                         db user-id level)
                    (d/q '[:find (count ?a) .
                           :in $ ?user
                           :where
                           [?a :attempt/user ?user]
                           [?a :attempt/correct? false]]
                         db user-id))
                  0)]
    (and (pos? total)
         (>= (/ missed total) threshold))))

(defn student-classification-accuracy
  "Per-classification-type accuracy for a student.
   Returns map of classification keyword -> {:correct N :total N :rate 0.0-1.0}."
  [user-id & {:keys [level]}]
  (let [db (schema/db)
        ;; Get all attempts with correct-classification
        attempts (if level
                   (d/q '[:find ?class ?correct
                          :in $ ?user ?level
                          :where
                          [?a :attempt/user ?user]
                          [?a :attempt/level ?level]
                          [?a :attempt/correct-classification ?class]
                          [?a :attempt/correct? ?correct]]
                        db user-id level)
                   (d/q '[:find ?class ?correct
                          :in $ ?user
                          :where
                          [?a :attempt/user ?user]
                          [?a :attempt/correct-classification ?class]
                          [?a :attempt/correct? ?correct]]
                        db user-id))]
    (->> attempts
         (group-by first)
         (map (fn [[class-key entries]]
                (let [total (count entries)
                      correct (count (filter second entries))]
                  [class-key {:correct correct
                              :total total
                              :rate (double (/ correct total))}])))
         (into {})
         (sort-by (comp :rate val)))))

;; ---------------------------------------------------------------------------
;; Cohort: cross-student patterns
;; ---------------------------------------------------------------------------

(defn assertion-difficulty-ranking
  "Rank assertion types by how often they're missed across all students.
   Returns sorted list from hardest to easiest.

   Options:
     :level - filter to a specific level"
  [& {:keys [level]}]
  (let [db (schema/db)
        missing-data (if level
                       (d/q '[:find ?missing (count ?a)
                              :in $ ?level
                              :where
                              [?a :attempt/level ?level]
                              [?a :attempt/missing-assertions ?missing]]
                            db level)
                       (d/q '[:find ?missing (count ?a)
                              :where
                              [?a :attempt/missing-assertions ?missing]]
                            db))
        total-incorrect (or (if level
                              (d/q '[:find (count ?a) .
                                     :in $ ?level
                                     :where
                                     [?a :attempt/level ?level]
                                     [?a :attempt/correct? false]]
                                   db level)
                              (d/q '[:find (count ?a) .
                                     :where
                                     [?a :attempt/correct? false]]
                                   db))
                            0)]
    (->> missing-data
         (sort-by second >)
         (mapv (fn [[atype count]]
                 {:assertion-type atype
                  :times-missed count
                  :miss-rate (when (pos? total-incorrect)
                               (double (/ count total-incorrect)))})))))

(defn classification-difficulty-ranking
  "Rank classifications by success rate across all students.
   Returns sorted list from hardest to easiest.

   Options:
     :level - filter to a specific level"
  [& {:keys [level]}]
  (let [db (schema/db)
        attempts (if level
                   (d/q '[:find ?class ?correct
                          :in $ ?level
                          :where
                          [?a :attempt/level ?level]
                          [?a :attempt/correct-classification ?class]
                          [?a :attempt/correct? ?correct]]
                        db level)
                   (d/q '[:find ?class ?correct
                          :where
                          [?a :attempt/correct-classification ?class]
                          [?a :attempt/correct? ?correct]]
                        db))]
    (->> attempts
         (group-by first)
         (map (fn [[class-key entries]]
                (let [total (count entries)
                      correct (count (filter second entries))]
                  {:classification class-key
                   :correct correct
                   :total total
                   :rate (double (/ correct total))})))
         (sort-by :rate))))

;; ---------------------------------------------------------------------------
;; Batch: deep EDN-based analysis
;; ---------------------------------------------------------------------------

(defn parameter-mismatch-details
  "Parse classification-diff EDN from attempts to identify specific
   parameter mismatches. Returns detailed breakdown.

   Options:
     :level     - filter to a specific level
     :class-key - filter to a specific classification
     :user-id   - filter to a specific user"
  [& {:keys [level class-key user-id]}]
  (let [db (schema/db)
        ;; Build query dynamically based on provided filters
        where-clauses (cond-> '[[?a :attempt/classification-diff _]]
                        user-id   (conj '[?a :attempt/user ?user])
                        level     (conj '[?a :attempt/level ?level])
                        class-key (conj '[?a :attempt/correct-classification ?class]))
        in-clause (cond-> '[$]
                    user-id   (conj '?user)
                    level     (conj '?level)
                    class-key (conj '?class))
        args (cond-> [db]
               user-id   (conj user-id)
               level     (conj level)
               class-key (conj class-key))
        attempts (apply d/q
                        {:find '[(pull ?a [:attempt/classification-diff
                                           :attempt/correct-classification
                                           :attempt/level
                                           :attempt/correct?])]
                         :in in-clause
                         :where where-clauses}
                        args)
        ;; Parse EDN diffs
        parsed (->> attempts
                    (map first)
                    (keep (fn [a]
                            (when-let [diff-str (:attempt/classification-diff a)]
                              (try
                                (assoc a :diff (edn/read-string diff-str))
                                (catch Exception _ nil))))))]
    {:total-analyzed (count parsed)
     :with-param-mismatches (count (filter #(pos? (get-in % [:diff :param-mismatches] 0)) parsed))
     :details (->> parsed
                   (filter #(pos? (get-in % [:diff :param-mismatches] 0)))
                   (mapv (fn [a]
                           {:classification (:attempt/correct-classification a)
                            :level (:attempt/level a)
                            :distance (get-in a [:diff :distance])
                            :missing (get-in a [:diff :missing])
                            :param-mismatches (get-in a [:diff :param-mismatches])
                            :param-matches (get-in a [:diff :param-matches])})))}))

;; ---------------------------------------------------------------------------
;; JE effectiveness: do assertion skills predict JE accuracy?
;; ---------------------------------------------------------------------------

(defn je-correlation
  "Correlate assertion classification accuracy with journal entry accuracy.
   Groups students by assertion mastery level and compares JE success rates.

   Returns {:assertion-masters {:je-correct N :je-total N :je-rate F}
            :assertion-struggling {:je-correct N :je-total N :je-rate F}}"
  []
  (let [db (schema/db)
        ;; Get all users with attempts
        users (d/q '[:find [?u ...]
                     :where [?a :attempt/user ?u]]
                   db)]
    (->> users
         (map (fn [user-id]
                (let [;; Assertion accuracy
                      classify-attempts (d/q '[:find ?correct
                                               :in $ ?user
                                               :where
                                               [?a :attempt/user ?user]
                                               [?a :attempt/problem-type :forward]
                                               [?a :attempt/correct? ?correct]]
                                             db user-id)
                      classify-total (count classify-attempts)
                      classify-correct (count (filter first classify-attempts))
                      ;; JE accuracy
                      je-attempts (d/q '[:find ?correct
                                         :in $ ?user
                                         :where
                                         [?a :attempt/user ?user]
                                         [?a :attempt/problem-type :construct]
                                         [?a :attempt/correct? ?correct]]
                                       db user-id)
                      je-total (count je-attempts)
                      je-correct (count (filter first je-attempts))]
                  (when (and (pos? classify-total) (pos? je-total))
                    {:user-id user-id
                     :assertion-rate (double (/ classify-correct classify-total))
                     :je-rate (double (/ je-correct je-total))}))))
         (remove nil?)
         (group-by #(if (>= (:assertion-rate %) 0.7) :masters :struggling))
         (map (fn [[group users]]
                [group {:count (count users)
                        :avg-assertion-rate (when (seq users)
                                              (/ (reduce + (map :assertion-rate users))
                                                 (count users)))
                        :avg-je-rate (when (seq users)
                                       (/ (reduce + (map :je-rate users))
                                          (count users)))}]))
         (into {}))))
