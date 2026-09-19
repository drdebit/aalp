(ns assertive-app.telemetry
  "How a student got to their answer, not just what it was.

   `progress/record-attempt!` already answers \"did they get it right,
   and how long did it take\". This answers the questions that only the
   process can: which assertion did they reach for first, how many times
   did they change their mind, did they look at the derived entry at
   all, how long did they sit on a tutorial section before paging on.

   The cheapest source is the derivation endpoint. It already receives
   the student's whole selection every time anything changes, so logging
   there records the construction of an answer keystroke by keystroke
   without the client doing anything.

   Two rules, both load-bearing:

   Never fail the request. A telemetry write that throws, blocks or
   slows a student's page is worse than no telemetry, so writes are
   fire-and-forget and every error is swallowed here.

   Never collect what is not about learning. This is research data about
   students -- it belongs behind whatever consent and review the study
   has -- and the way to keep that manageable is to keep the data narrow
   on purpose."
  (:require [clojure.set :as set]
            [datomic.api :as d]
            [assertive-app.schema :as schema]
            [clojure.tools.logging :as log]))

(defn record!
  "Note that something happened. Returns nil, always, and quickly.

   Not deref'd: the transaction is handed to Datomic and the request
   carries on. A student waiting on an analytics write would be the
   wrong trade every time."
  ([user-id kind payload] (record! user-id kind nil payload))
  ([user-id kind problem-id payload]
   (try
     (when (and user-id kind)
       (d/transact (schema/get-conn)
         [(cond-> {:telemetry/user user-id
                   :telemetry/at (java.util.Date.)
                   :telemetry/kind kind
                   :telemetry/payload (pr-str payload)}
            problem-id (assoc :telemetry/problem-id (str problem-id)))]))
     nil
     (catch Exception e
       (log/warn e "Telemetry write failed (ignored)" {:kind kind})
       nil))))

(defn record-batch!
  "A client's buffered events, in one transaction.

   Batched because the interesting client-side signals -- a tutorial
   section paged, a journal-entry line opened -- happen far too often to
   be worth a round trip each."
  [user-id events]
  (try
    (let [now (java.util.Date.)
          tx (vec (for [{:keys [kind problem-id payload at]} events
                        :when kind]
                    (cond-> {:telemetry/user user-id
                             ;; The client's clock is not trusted for
                             ;; ordering across students, but its own
                             ;; relative timings are what matter, so it
                             ;; travels in the payload rather than here.
                             :telemetry/at now
                             :telemetry/kind (keyword kind)
                             :telemetry/payload (pr-str (cond-> payload
                                                          at (assoc :client-at at)))}
                      problem-id (assoc :telemetry/problem-id (str problem-id)))))]
      (when (seq tx)
        (d/transact (schema/get-conn) tx))
      (count tx))
    (catch Exception e
      (log/warn e "Telemetry batch failed (ignored)")
      0)))

(defn events-for
  "One student's stream, oldest first. For analysis, not for the app."
  [user-id & {:keys [kind]}]
  (let [db (schema/db)
        rows (d/q '[:find [(pull ?e [:telemetry/at :telemetry/kind
                                     :telemetry/problem-id :telemetry/payload]) ...]
                    :in $ ?u
                    :where [?e :telemetry/user ?u]]
                  db user-id)]
    (->> rows
         (filter #(or (nil? kind) (= kind (:telemetry/kind %))))
         (sort-by :telemetry/at)
         vec)))

;; ---------------------------------------------------------------------------
;; Reading the stream
;; ---------------------------------------------------------------------------

(defn construction-shape
  "How an answer was built, per problem.

   The question this exists for: is a student reasoning forward from the
   transaction to the assertions, or backward from a journal entry they
   already know? The two leave different traces.

   Forward looks like accumulation -- the set of assertions grows, one
   at a time, and rarely shrinks. Backward looks like search: the set
   shrinks and regrows as combinations are tried against a target the
   student already has in mind.

   `:removals` counts the steps where the set got smaller and is the
   sharpest single number here. `:looked?` says whether they ever opened
   a line to ask why an account was what it was, which is the other half
   -- a student hill-climbing does not usually need the rule, and a
   student reasoning forward often does.

   Neither shape is wrong. Matt's call, 2026-09-19: a student who knows
   the course and is now learning the assertions is entitled to work
   backwards, and both directions are wanted. This only makes it
   visible.

   -> {problem-id {:derives :removals :additions :final :looked? :span-seconds}}"
  [user-id]
  (let [evs (events-for user-id)
        by-problem (group-by :telemetry/problem-id evs)]
    (into {}
          (for [[pid es] by-problem
                :when pid
                :let [derives (filter #(= :derive (:telemetry/kind %)) es)
                      sets (for [d derives]
                             (set (:codes (read-string (:telemetry/payload d)))))
                      steps (map vector sets (rest sets))
                      times (keep :telemetry/at es)]]
            [pid {:derives (count derives)
                  ;; A step that dropped an assertion. Searching does
                  ;; this; accumulating does not.
                  :removals (count (filter (fn [[a b]] (seq (set/difference a b))) steps))
                  :additions (count (filter (fn [[a b]] (seq (set/difference b a))) steps))
                  :final (vec (sort (or (last sets) [])))
                  :looked? (boolean (some #(= :line-opened (:telemetry/kind %)) es))
                  :span-seconds (when (seq times)
                                  (quot (- (.getTime (apply max-key #(.getTime %) times))
                                           (.getTime (apply min-key #(.getTime %) times)))
                                        1000))}]))))

(defn- users-with-telemetry [db]
  (d/q '[:find [?u ...] :where [_ :telemetry/user ?u]] db))

(defn- email-of [db u]
  (:user/email (d/pull db [:user/email] u)))

(defn- median
  "A double, always. Averaging two integers in Clojure gives a Ratio,
   which prints as 11/2 and reads as a typo."
  [xs]
  (when (seq xs)
    (let [v (vec (sort xs)) n (count v)]
      (double (if (odd? n)
                (nth v (quot n 2))
                (/ (+ (nth v (dec (quot n 2))) (nth v (quot n 2))) 2))))))

(defn- fmt-num [x]
  (when x (if (== x (Math/rint x)) (str (long x)) (format "%.1f" x))))

(defn- dwell-rows
  "Every section a student left, with how long it held them."
  [evs]
  (for [e evs
        :when (= :section-left (:telemetry/kind e))
        :let [p (read-string (:telemetry/payload e))]
        :when (:dwell-ms p)]
    p))

(defn report
  "Where students are spending their effort, and where they are going
   back.

   Three questions, which is all this is for:

   Which patterns are hard to BUILD -- many derives, many removals --
   as distinct from hard to get right, which the attempt records already
   answer. A pattern with a high removal count is one students cannot
   see their way to.

   Which tutorial sections hold them, and which they go back to. A
   section paged in two seconds was skipped; one dwelt on for minutes
   was work; one returned to was not enough the first time.

   And whether anyone reads the derivation. A line opened is a student
   asking why an account is what it is, which is the whole claim of the
   platform actually being taken up."
  []
  (let [db (schema/db)
        users (users-with-telemetry db)]
    (println (format "telemetry: %d student(s)\n" (count users)))
    ;; ---- per student
    (doseq [u (sort users)]
      (let [evs (events-for u)
            shapes (vals (construction-shape u))
            dwells (dwell-rows evs)]
        (println (format "%s" (or (email-of db u) (str u))))
        (println (format "   %d problems built · median %s derives · %d removals total · looked at a rule on %d of them"
                         (count shapes)
                         (fmt-num (median (map :derives shapes)))
                         (reduce + 0 (map :removals shapes))
                         (count (filter :looked? shapes))))
        (when (seq dwells)
          (println (format "   %d sections read · median %ds · %d gone back to"
                           (count dwells)
                           (long (quot (or (median (map :dwell-ms dwells)) 0) 1000))
                           (count (filter #(= "back" (:direction %)) dwells)))))))
    ;; ---- across everyone
    (let [all (mapcat #(vals (construction-shape %)) users)
          all-dwell (mapcat #(dwell-rows (events-for %)) users)]
      (when (seq all-dwell)
        (println "\nsections that held them longest")
        (doseq [[k rows] (->> all-dwell
                              (group-by (juxt :level :heading))
                              (sort-by #(- (or (median (map :dwell-ms (val %))) 0)))
                              (take 5))]
          (println (format "   L%s  %-42s %4ds  (%d readings, %d back)"
                           (str (first k)) (str (second k))
                           (long (quot (or (median (map :dwell-ms rows)) 0) 1000))
                           (count rows)
                           (count (filter #(= "back" (:direction %)) rows))))))
      (when (seq all)
        (println "\nhow answers were built")
        (println (format "   %d problems · %d built with no removals at all · %d with three or more"
                         (count all)
                         (count (filter #(zero? (:removals %)) all))
                         (count (filter #(>= (:removals %) 3) all))))
        (println (format "   a rule was opened on %d of them" (count (filter :looked? all))))))
    :done))
