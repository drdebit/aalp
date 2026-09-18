(ns assertive-app.engine
  "The engine computes; it does not store.

   AALP's own Datomic database is the single store of record. Every
   event a student records lives there as assertions, and everything
   here is built from those events on demand and thrown away with the
   request. Nothing in an engine store is a fact that would be missed if
   the store vanished mid-sentence.

   That was the design in April 2026, when the engine was added
   *alongside* the authoritative record as an index, and failure
   tolerance was the right call because losing an index costs you
   queries rather than records. It stopped being true on 2026-07-07,
   when the Report Builder began writing student-recorded reports to the
   engine and nowhere else: a second store of record, with a different
   lifetime from the first, and a catch branch that silently swapped it
   for memory. See ENGINE-STORE-DIVERGENCE.md.

   A student's whole year is a few dozen events, so building a store per
   request costs nothing worth measuring, and the failure mode is gone
   rather than guarded. When cross-cohort analytics makes a standing
   index worth having, it can be added as a cache over the same record
   without changing what is authoritative."
  (:require [assertive-engine.adapter.aalp :as aalp]
            [assertive-engine.model.event :as event]
            [assertive-engine.store.protocol :as store]
            [assertive-engine.store.memory :as mem]
            [assertive-engine.compute.collects :as collects]
            [assertive-engine.compute.derive :as derive]
            [clojure.tools.logging :as log]))

;; ---------------------------------------------------------------------------
;; Building a store for one record
;; ---------------------------------------------------------------------------

(defmacro ^:private with-engine*
  "Execute body against store `s`. Returns nil if there is no store or
   if body throws: a query that fails costs the student a panel, never
   their record, because their record is not in here."
  [s & body]
  `(when ~s
     (try ~@body
       (catch Exception e#
         (log/warn e# "Engine query failed (non-fatal)")
         nil))))

(defn store-of
  "An engine store holding one student's record and nothing else.

   `events` are engine-ready maps -- :assertions, :date, :asserted-by,
   and an explicit :event-id so that identity is stable across rebuilds.
   That last point matters: chains and recorded reports refer to events
   by id, and an id minted fresh on every rebuild would break both."
  [events]
  (let [s (mem/create-memory-store)]
    (doseq [e events]
      (try
        (aalp/store-classified-event! (assoc e :store s))
        (catch Exception ex
          ;; One unreadable row should not cost the student the rest of
          ;; their record.
          (log/warn ex "Could not load an event into the engine store"
                    {:event-id (:event-id e)}))))
    s))

(defn replay-report
  "Put a recorded report back into a store exactly as it was recorded.

   NOT by re-running its query. The spec and the figure are both part of
   what was asserted -- the student reported THIS number on THIS date
   from THESE events -- and recomputing would quietly replace the report
   with what the record says today. Comparing the two is a question
   worth asking; answering it silently is not."
  [s {:keys [opts result count input-ids]}]
  (try
    (let [{:keys [event assertions]}
          (derive/build-derived-event opts {:result result :count count} (vec input-ids))]
      (store/store-event! s event assertions))
    (catch Exception ex
      (log/warn ex "Could not replay a recorded report" {:event-id (:event-id opts)})
      nil)))

;; ---------------------------------------------------------------------------
;; Query helpers
;; ---------------------------------------------------------------------------

(defn get-event
  "Retrieve an event from a store, or nil."
  [s event-id]
  (with-engine* s (store/get-event s event-id)))

(defn get-user-events
  "Get events asserted by a specific user, filtered by assertion type."
  [s user-id atype & {:keys [from to limit] :or {limit 100}}]
  (with-engine* s
    (let [pattern (cond-> {:assertion-types #{atype}
                           :asserted-by (str user-id)}
                    from (assoc :date-from from)
                    to (assoc :date-to to))
          events (store/match-pattern s pattern)]
      (->> events
           (sort-by (comp :event/date :event))
           (take limit)
           vec))))

(defn traverse-chain
  "Traverse an event chain, or nil on failure."
  [s event-id & {:keys [direction depth] :or {direction :both depth 10}}]
  (with-engine* s (store/traverse-chain s event-id direction depth)))

(defn get-user-event-count
  "Count events for a user."
  [s user-id]
  (with-engine* s (count (store/match-pattern s {:asserted-by (str user-id)}))))

(defn get-user-events-by-date
  "Get all events for a user within a date range."
  [s user-id & {:keys [from to limit] :or {limit 200}}]
  (with-engine* s
    (let [pattern (cond-> {:asserted-by (str user-id)}
                    from (assoc :date-from from)
                    to (assoc :date-to to))
          events (store/match-pattern s pattern)]
      (->> events
           (sort-by (comp :event/date :event))
           (take limit)
           vec))))

(defn get-user-summary
  "Aggregate summary of a user's events: revenue, costs, event counts by type."
  [s user-id]
  (with-engine* s
    (let [user-str (str user-id)
          all-events (store/match-pattern s {:asserted-by user-str})
          type-counts (->> all-events
                           (mapcat (comp :event/assertion-types :event))
                           frequencies)
          cash-rev (collects/cash-revenue s user-str)
          accrual-rev (collects/accrual-revenue s user-str)]
      {:event-count (count all-events)
       :assertion-type-counts type-counts
       :cash-revenue {:count (:count cash-rev)
                      :total (get-in cash-rev [:result :value])}
       :accrual-revenue {:count (:count accrual-rev)
                         :total (get-in accrual-rev [:result :value])}})))

;; ---------------------------------------------------------------------------
;; Student-composed reports (calculation assembly)
;;
;; Pedagogical core of "reporting downstream from recording": students
;; compose collects/includes/excludes specs over their OWN recorded
;; events, preview the result freely, and then RECORD the composition --
;; at which point the report becomes a first-class event in their ledger
;; carrying its own selection logic, result, authority, and asserter.
;; ---------------------------------------------------------------------------

(declare format-event-for-response)

(def ^:private allowed-pattern-keys
  #{:assertion-types :any-assertion-types :counterparty
    :date-from :date-to :provides-unit :receives-unit})

(def ^:private allowed-categories #{:revenue :expense :gain :loss :distribution})
(def ^:private allowed-bases
  #{:cash-received :cash-paid :cost-of-goods :service-value :earned
    :systematic-allocation :estimation :time-based :accrual :declared})
(def ^:private allowed-ops #{:sum :count :avg})
(def ^:private allowed-aggregate-types
  #{:provides :receives :consumes :creates})

(defn- coerce-pattern
  "Whitelist and coerce a JSON-shaped pattern into engine form.
   Only declarative keys survive; nothing executable can arrive over
   the wire."
  [p]
  (when (map? p)
    (cond-> (select-keys p allowed-pattern-keys)
      (:assertion-types p)     (update :assertion-types #(set (map keyword %)))
      (:any-assertion-types p) (update :any-assertion-types #(set (map keyword %)))
      (:provides-unit p)       (update :provides-unit keyword)
      (:receives-unit p)       (update :receives-unit keyword))))

(defn sanitize-composition-spec
  "Coerce a student's composition into an engine collects-spec, scoped
   to the student's own events (asserted-by is forced, not trusted)."
  [user-id {:keys [includes excludes]}]
  (cond-> {:includes (assoc (or (coerce-pattern includes) {})
                            :asserted-by (str user-id))}
    (coerce-pattern excludes) (assoc :excludes (coerce-pattern excludes))))

(defn- coerce-enum [v allowed fallback]
  (let [k (keyword v)]
    (if (contains? allowed k) k fallback)))

(defn preview-composition
  "Execute a student's composition without recording it. Free iteration
   is deliberate: refining a report against counterexamples is the
   lesson. Returns {:result Q :count N :events [...]} or nil."
  [s user-id spec {:keys [aggregate-type op]}]
  (with-engine* s
    (let [clean (sanitize-composition-spec user-id spec)
          atype (coerce-enum aggregate-type allowed-aggregate-types :receives)
          op*   (coerce-enum op allowed-ops :sum)
          agg   (collects/collect-and-aggregate s clean atype op*)]
      {:result (:result agg)
       :count  (:count agg)
       :events (mapv format-event-for-response (:events agg))})))

(defn compose-and-record
  "Run a student's composition and hand back both the figure and
   everything needed to keep it.

   This does not persist. It cannot: the store it runs against is thrown
   away with the request. `:record` is the durable form -- the spec that
   selected the events, the operation applied, the figure it came to,
   and the ids it collected -- for the caller to write to the store of
   record. Spec AND figure, because a report that kept only its query
   would answer differently every time the record grew, and one that
   kept only its number could not be argued with."
  [s user-id {:keys [event-id date allowed-by category basis aggregate-type op]} spec]
  (with-engine* s
    (let [clean (sanitize-composition-spec user-id spec)
          opts  {:event-id       (or event-id
                                     (str "Report-" user-id "-" (java.util.UUID/randomUUID)))
                 :date           (or date (str (java.time.LocalDate/now)))
                 :asserted-by    (str user-id)
                 :allowed-by     allowed-by
                 :collects-spec  clean
                 :aggregate-type (coerce-enum aggregate-type allowed-aggregate-types :receives)
                 :op             (coerce-enum op allowed-ops :sum)
                 :category       (coerce-enum category allowed-categories :revenue)
                 :basis          (coerce-enum basis allowed-bases :declared)}
          agg   (collects/collect-and-aggregate s clean (:aggregate-type opts) (:op opts))
          input-ids (mapv (comp :event/id :event) (:events agg))
          {:keys [event assertions]} (derive/build-derived-event opts agg input-ids)]
      ;; Into the ephemeral store as well, so this request's response
      ;; sees the record it just added to.
      (store/store-event! s event assertions)
      {:event-id  (:event/id event)
       :result    (:result agg)
       :count     (:count agg)
       :input-ids input-ids
       :record    {:opts opts
                   :result (:result agg)
                   :count (:count agg)
                   :input-ids input-ids}})))

;; ---------------------------------------------------------------------------
;; Response formatting
;; ---------------------------------------------------------------------------

(defn format-event-for-response
  "Convert an engine event-with-assertions to a JSON-friendly map,
   including the AALP-format assertion view."
  [{:keys [event assertions depth]}]
  (let [canonical (event/reconstitute-event
                    {:event event :assertions assertions})]
    (cond-> {:event-id (:event/id event)
             :date (:event/date event)
             :asserted-by (:event/asserted-by event)
             :assertion-types (vec (:event/assertion-types event #{}))
             :counterparties (vec (:event/counterparties event #{}))
             :targets (vec (:event/targets event #{}))
             :aalp-assertions (aalp/canonical->aalp canonical)}
      depth (assoc :depth depth))))
