(ns assertive-app.schema
  "Datomic schema and database connection for AALP."
  (:require [datomic.api :as d]
            [clojure.string :as str]))

(def schema
  [;; ==================== User Entity ====================
   {:db/ident :user/email
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "User email address (unique identifier)"}

   {:db/ident :user/display-name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Optional display name"}

   {:db/ident :user/session-token
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "Current session token for authentication"}

   {:db/ident :user/created-at
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc "When user account was created"}

   {:db/ident :user/last-login
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc "Last login timestamp"}

   ;; ==================== Progress Entity ====================
   ;; One per user, tracks overall progress
   {:db/ident :progress/user
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "Reference to user (one progress per user)"}

   {:db/ident :progress/current-level
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Current working level (0, 1, or 2)"}

   {:db/ident :progress/unlocked-levels
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/many
    :db/doc "Set of unlocked level numbers"}

   ;; ==================== Level Progress Entity ====================
   ;; Tracks correct count per user per level for unlock logic
   {:db/ident :level-progress/user
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Reference to user"}

   {:db/ident :level-progress/level
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Level number (0, 1, 2)"}

   {:db/ident :level-progress/correct-count
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Number of correct answers at this level"}

   {:db/ident :level-progress/total-attempts
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Total attempts at this level"}

   {:db/ident :level-progress/unlocked-next
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "Whether this level's progress unlocked the next level"}

   ;; ==================== Problem Attempt Entity ====================
   ;; Logs each submission for analytics
   {:db/ident :attempt/user
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Reference to user who made attempt"}

   {:db/ident :attempt/problem-id
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "UUID from generate-problem"}

   {:db/ident :attempt/problem-type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc ":forward, :reverse, or :construct"}

   {:db/ident :attempt/level
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Level at which attempt was made"}

   {:db/ident :attempt/template-key
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc "Template key, e.g., :cash-sale, :credit-inventory-purchase"}

   {:db/ident :attempt/selected-assertions
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "EDN string of assertions map"}

   {:db/ident :attempt/je-debit-account
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Debit account for construct mode"}

   {:db/ident :attempt/je-credit-account
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Credit account for construct mode"}

   {:db/ident :attempt/je-amount
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "Amount for construct mode"}

   {:db/ident :attempt/correct?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "Whether the attempt was correct"}

   {:db/ident :attempt/feedback-status
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc ":correct, :incorrect, or :indeterminate"}

   {:db/ident :attempt/datetime
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/index true
    :db/doc "When attempt was submitted"}

   ;; ==================== Business State Entity ====================
   ;; Tracks current simulation state per user
   {:db/ident :business-state/user
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "Reference to user (one business state per user)"}

   {:db/ident :business-state/current-period
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Current simulation period (1, 2, 3...)"}

   {:db/ident :business-state/moves-remaining
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Actions remaining in current period"}

   {:db/ident :business-state/cash
    :db/valueType :db.type/bigdec
    :db/cardinality :db.cardinality/one
    :db/doc "Cash balance"}

   {:db/ident :business-state/raw-materials
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Raw material units (blank t-shirts)"}

   {:db/ident :business-state/finished-goods
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Finished goods units (printed t-shirts)"}

   {:db/ident :business-state/equipment
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "EDN set of owned equipment keywords"}

   {:db/ident :business-state/accounts-payable
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "EDN map of vendor -> amount owed"}

   {:db/ident :business-state/accounts-receivable
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "EDN map of customer -> amount owed"}

   {:db/ident :business-state/simulation-date
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Current in-simulation date (ISO format)"}

   ;; ==================== Pending Transaction Entity ====================
   ;; Tracks transaction awaiting correct classification
   {:db/ident :pending-tx/user
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "Reference to user (one pending tx per user)"}

   {:db/ident :pending-tx/action-type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc "Action type e.g. :purchase-materials-cash"}

   {:db/ident :pending-tx/narrative
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Transaction narrative text"}

   {:db/ident :pending-tx/variables
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "EDN map of generated variable values"}

   {:db/ident :pending-tx/correct-assertions
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "EDN map of correct assertion selections"}

   {:db/ident :pending-tx/attempts
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Number of attempts so far"}

   {:db/ident :pending-tx/created-at
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc "When pending transaction was created"}

   {:db/ident :pending-tx/template-key
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc "Template used to generate this transaction"}

   {:db/ident :pending-tx/problem-id
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "UUID for this specific problem instance"}

   ;; ==================== Ledger Entry Entity ====================
   ;; Records correctly classified transactions
   {:db/ident :ledger-entry/id
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "Unique identifier for ledger entry"}

   {:db/ident :ledger-entry/user
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "Reference to user who owns this entry"}

   {:db/ident :ledger-entry/date
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "In-simulation date (ISO format)"}

   {:db/ident :ledger-entry/period
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one
    :db/doc "Simulation period when recorded"}

   {:db/ident :ledger-entry/action-type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc "Action type that generated this entry"}

   {:db/ident :ledger-entry/narrative
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Transaction narrative text"}

   {:db/ident :ledger-entry/variables
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "EDN map of variable values"}

   {:db/ident :ledger-entry/assertions
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "EDN map of assertion selections"}

   {:db/ident :ledger-entry/journal-entry
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "EDN map with :debit :credit :amount"}

   {:db/ident :ledger-entry/created-at
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc "When entry was recorded"}

   {:db/ident :ledger-entry/template-key
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/doc "Template used for this transaction"}])

;; Database connection URI
;; Uses existing Datomic transactor with PostgreSQL backing store
(defn- get-db-password []
  (let [pw (System/getenv "DATOMIC_DB_PASSWORD")]
    (when (str/blank? pw)
      (throw (ex-info "DATOMIC_DB_PASSWORD environment variable not set" {})))
    ;; URL-encode special characters (same as accrue-backend)
    (-> pw
        (str/replace "&" "%26")
        (str/replace "@" "%40")
        (str/replace "!" "%21"))))

(def db-uri
  (str "datomic:sql://aalp?jdbc:postgresql://localhost:5432/datomic?user=postgres&password="
       (get-db-password)))

(defn init-db!
  "Create database and transact schema if needed. Returns connection."
  []
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    @(d/transact conn schema)
    conn))

(defonce conn
  (delay (init-db!)))

(defn get-conn
  "Get the database connection, initializing if needed."
  []
  @conn)

(defn db
  "Get current database value."
  []
  (d/db (get-conn)))
