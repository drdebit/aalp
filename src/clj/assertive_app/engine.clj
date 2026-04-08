(ns assertive-app.engine
  "Thin wrapper around assertive-engine for storing classified events.
   Engine failures are logged but never propagate to the teaching flow."
  (:require [assertive-engine.adapter.aalp :as aalp]
            [assertive-engine.model.event :as event]
            [assertive-engine.store.protocol :as store]
            [assertive-engine.store.memory :as mem]
            [assertive-engine.compute.collects :as collects]
            [clojure.tools.logging :as log]))

;; ---------------------------------------------------------------------------
;; Store lifecycle
;; ---------------------------------------------------------------------------

(defonce ^:private engine-store (atom nil))

(defn init!
  "Initialize the engine store. Call once at app startup.
   Accepts a store instance, or creates an in-memory store as default.

   For Datomic:
     (require '[assertive-engine.store.datomic :as dat])
     (init! (dat/create-datomic-store uri))"
  ([]
   (init! (mem/create-memory-store)))
  ([store]
   (reset! engine-store store)
   (log/info "Assertive engine initialized" {:healthy? (store/healthy? store)})))

(defn store
  "Get the current engine store, or nil if not initialized."
  []
  @engine-store)

;; ---------------------------------------------------------------------------
;; Store events (failure-tolerant)
;; ---------------------------------------------------------------------------

(defn store-classified-event!
  "Store a correctly-classified event in the assertive engine.
   Never throws — engine failure is logged as a warning.

   Params:
     :assertions   - AALP assertion map (student selections)
     :event-id     - optional explicit event ID
     :date         - event date
     :asserted-by  - user identifier (student ID)
     :template-key - AALP classification key (e.g., :cash-sale)
     :counterparty - optional counterparty name

   Returns event-id on success, nil on failure."
  [params]
  (if-let [s @engine-store]
    (try
      (aalp/store-classified-event! (assoc params :store s))
      (catch Exception e
        (log/warn e "Failed to store event in assertive-engine (non-fatal)")
        nil))
    (do
      (log/debug "Engine store not initialized, skipping event storage")
      nil)))

;; ---------------------------------------------------------------------------
;; Query helpers (failure-tolerant)
;; ---------------------------------------------------------------------------

(defn get-event
  "Retrieve an event from the engine, or nil."
  [event-id]
  (when-let [s @engine-store]
    (try
      (store/get-event s event-id)
      (catch Exception e
        (log/warn e "Engine query failed")
        nil))))

(defn get-user-events
  "Get events asserted by a specific user, filtered by assertion type."
  [user-id atype & {:keys [from to limit] :or {limit 100}}]
  (when-let [s @engine-store]
    (try
      (->> (store/find-by-assertion-type s atype {:from from :to to :limit limit})
           (filter #(= user-id (get-in % [:event :event/asserted-by])))
           vec)
      (catch Exception e
        (log/warn e "Engine query failed")
        nil))))

(defn traverse-chain
  "Traverse an event chain, or nil on failure."
  [event-id & {:keys [direction depth] :or {direction :both depth 10}}]
  (when-let [s @engine-store]
    (try
      (store/traverse-chain s event-id direction depth)
      (catch Exception e
        (log/warn e "Engine traversal failed")
        nil))))

(defn get-user-event-count
  "Count events for a user."
  [user-id]
  (when-let [s @engine-store]
    (try
      (count (store/match-pattern s {:asserted-by (str user-id)}))
      (catch Exception e
        (log/warn e "Engine query failed")
        nil))))

(defn get-user-events-by-date
  "Get all events for a user within a date range."
  [user-id & {:keys [from to limit] :or {limit 200}}]
  (when-let [s @engine-store]
    (try
      (let [pattern (cond-> {:asserted-by (str user-id)}
                      from (assoc :date-from from)
                      to (assoc :date-to to))
            events (store/match-pattern s pattern)]
        (->> events
             (sort-by (comp :event/date :event))
             (take limit)
             vec))
      (catch Exception e
        (log/warn e "Engine query failed")
        nil))))

(defn get-user-summary
  "Aggregate summary of a user's events: revenue, costs, event counts by type."
  [user-id]
  (when-let [s @engine-store]
    (try
      (let [user-str (str user-id)
            all-events (store/match-pattern s {:asserted-by user-str})
            event-ids (mapv (comp :event/id :event) all-events)
            ;; Count by assertion type
            type-counts (->> all-events
                             (mapcat (comp :event/assertion-types :event))
                             frequencies)
            ;; Cash revenue (events with :receives monetary, no :requires)
            cash-rev (collects/cash-revenue s user-str)
            ;; Accrual revenue
            accrual-rev (collects/accrual-revenue s user-str)]
        {:event-count (count all-events)
         :assertion-type-counts type-counts
         :cash-revenue {:count (:count cash-rev)
                        :total (get-in cash-rev [:result :value])}
         :accrual-revenue {:count (:count accrual-rev)
                           :total (get-in accrual-rev [:result :value])}})
      (catch Exception e
        (log/warn e "Engine summary failed")
        nil))))

;; ---------------------------------------------------------------------------
;; Response formatting (convert engine data to JSON-friendly maps)
;; ---------------------------------------------------------------------------

(defn format-event-for-response
  "Convert an engine event-with-assertions to a JSON-friendly map,
   including the AALP-format assertion view."
  [{:keys [event assertions depth] :as ewa}]
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
