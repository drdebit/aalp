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
