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
;; Query helper — eliminates the if-level branching duplication
;; ---------------------------------------------------------------------------

(defn- attempt-query
  "Run a Datalog query against attempts with optional filters.
   Automatically adds :attempt/user and :attempt/level where-clauses
   and input bindings when user-id/level are provided.

   find-clause: Datalog find spec (e.g., '[:find ?missing (count ?a)])
   base-where:  base where clauses (vector of vectors)
   opts:        {:user-id X :level N} — filters to add"
  [find-clause base-where {:keys [user-id level]}]
  (let [where (cond-> base-where
                user-id (conj '[?a :attempt/user ?user])
                level   (conj '[?a :attempt/level ?level]))
        in    (cond-> '[$]
                user-id (conj '?user)
                level   (conj '?level))
        args  (cond-> [(schema/db)]
                user-id (conj user-id)
                level   (conj level))]
    (apply d/q {:find find-clause :in in :where where} args)))

;; ---------------------------------------------------------------------------
;; Real-time: per-student weakness detection
;; ---------------------------------------------------------------------------

(defn student-weakness-report
  "Identify assertion types a student struggles with most.
   Returns a ranked list of assertion types by how often they're missed.

   Options:
     :level - filter to a specific level"
  [user-id & {:keys [level]}]
  (let [opts {:user-id user-id :level level}
        missing-data (attempt-query
                       '[:find ?missing (count ?a)]
                       '[[?a :attempt/missing-assertions ?missing]]
                       opts)
        extra-data (attempt-query
                     '[:find ?extra (count ?a)]
                     '[[?a :attempt/extra-assertions ?extra]]
                     opts)
        total-incorrect (or (attempt-query
                              '[:find (count ?a) .]
                              '[[?a :attempt/correct? false]]
                              opts)
                            0)]
    {:most-missed (->> missing-data
                       (sort-by second >)
                       (mapv (fn [[atype cnt]]
                               {:assertion-type atype
                                :times-missed cnt
                                :rate (when (pos? total-incorrect)
                                        (double (/ cnt total-incorrect)))})))
     :most-over-used (->> extra-data
                          (sort-by second >)
                          (mapv (fn [[atype cnt]]
                                  {:assertion-type atype
                                   :times-extra cnt
                                   :rate (when (pos? total-incorrect)
                                           (double (/ cnt total-incorrect)))})))
     :total-incorrect total-incorrect}))

(defn student-needs-practice?
  "Quick check: does a student need more practice on a specific assertion type?
   Returns true if they've missed this assertion in >30% of recent incorrect attempts."
  [user-id assertion-type & {:keys [level threshold] :or {threshold 0.3}}]
  (let [opts {:user-id user-id :level level}
        missed (or (attempt-query
                     '[:find (count ?a) .]
                     [['?a :attempt/missing-assertions assertion-type]]
                     opts)
                   0)
        total (or (attempt-query
                    '[:find (count ?a) .]
                    '[[?a :attempt/correct? false]]
                    opts)
                  0)]
    (and (pos? total)
         (>= (/ missed total) threshold))))

(defn student-classification-accuracy
  "Per-classification-type accuracy for a student.
   Returns map of classification keyword -> {:correct N :total N :rate 0.0-1.0}."
  [user-id & {:keys [level]}]
  (let [attempts (attempt-query
                   '[:find ?class ?correct]
                   '[[?a :attempt/correct-classification ?class]
                     [?a :attempt/correct? ?correct]]
                   {:user-id user-id :level level})]
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

   Options:
     :level - filter to a specific level"
  [& {:keys [level]}]
  (let [opts {:level level}
        missing-data (attempt-query
                       '[:find ?missing (count ?a)]
                       '[[?a :attempt/missing-assertions ?missing]]
                       opts)
        total-incorrect (or (attempt-query
                              '[:find (count ?a) .]
                              '[[?a :attempt/correct? false]]
                              opts)
                            0)]
    (->> missing-data
         (sort-by second >)
         (mapv (fn [[atype cnt]]
                 {:assertion-type atype
                  :times-missed cnt
                  :miss-rate (when (pos? total-incorrect)
                               (double (/ cnt total-incorrect)))})))))

(defn classification-difficulty-ranking
  "Rank classifications by success rate across all students.

   Options:
     :level - filter to a specific level"
  [& {:keys [level]}]
  (let [attempts (attempt-query
                   '[:find ?class ?correct]
                   '[[?a :attempt/correct-classification ?class]
                     [?a :attempt/correct? ?correct]]
                   {:level level})]
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

   Returns {:masters {:count N :avg-assertion-rate F :avg-je-rate F}
            :struggling {:count N :avg-assertion-rate F :avg-je-rate F}}"
  []
  (let [db (schema/db)
        ;; Batch query: all forward attempts [user correct?]
        classify-data (d/q '[:find ?u ?correct
                             :where
                             [?a :attempt/user ?u]
                             [?a :attempt/problem-type :forward]
                             [?a :attempt/correct? ?correct]]
                           db)
        ;; Batch query: all construct attempts [user correct?]
        je-data (d/q '[:find ?u ?correct
                       :where
                       [?a :attempt/user ?u]
                       [?a :attempt/problem-type :construct]
                       [?a :attempt/correct? ?correct]]
                     db)
        ;; Group by user
        classify-by-user (group-by first classify-data)
        je-by-user (group-by first je-data)
        ;; Users who have both types
        common-users (clojure.set/intersection
                       (set (keys classify-by-user))
                       (set (keys je-by-user)))]
    (->> common-users
         (map (fn [uid]
                (let [c-entries (get classify-by-user uid)
                      c-total (count c-entries)
                      c-correct (count (filter second c-entries))
                      j-entries (get je-by-user uid)
                      j-total (count j-entries)
                      j-correct (count (filter second j-entries))]
                  {:assertion-rate (double (/ c-correct c-total))
                   :je-rate (double (/ j-correct j-total))})))
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
