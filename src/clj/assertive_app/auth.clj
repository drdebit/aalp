(ns assertive-app.auth
  "Authentication for AALP - email-based with UUID session tokens."
  (:require [assertive-app.schema :as schema]
            [datomic.api :as d]
            [clojure.string :as str]))

(defn generate-session-token
  "Generate a new random UUID session token."
  []
  (java.util.UUID/randomUUID))

(defn normalize-email
  "Normalize email to lowercase and trim whitespace."
  [email]
  (-> email str str/trim str/lower-case))

(defn get-user-by-email
  "Look up user by email address. Returns user entity map or nil."
  [email]
  (when email
    (d/q '[:find (pull ?u [*]) .
           :in $ ?email
           :where [?u :user/email ?email]]
         (schema/db) (normalize-email email))))

(defn get-user-by-token
  "Look up user by session token. Returns user entity map with progress or nil."
  [token]
  (when (and token (not (str/blank? (str token))))
    (try
      (let [token-uuid (cond
                         (uuid? token) token
                         (string? token) (parse-uuid token)
                         :else nil)]
        (when token-uuid
          (d/q '[:find (pull ?u [* {:progress/_user [:db/id
                                                     :progress/current-level
                                                     :progress/unlocked-levels]}]) .
                 :in $ ?token
                 :where [?u :user/session-token ?token]]
               (schema/db) token-uuid)))
      (catch Exception _ nil))))

(defn login!
  "Login or register a user by email. Returns user map with session-token.
   Creates new user with initial progress if email not found."
  [email]
  (let [email (normalize-email email)
        token (generate-session-token)
        existing (get-user-by-email email)
        now (java.util.Date.)]
    (if existing
      ;; Existing user - update session token
      (do
        @(d/transact (schema/get-conn)
           [{:db/id (:db/id existing)
             :user/session-token token
             :user/last-login now}])
        (assoc existing :user/session-token token))
      ;; New user - create user and progress in same transaction
      (do
        @(d/transact (schema/get-conn)
           [;; Create user
            [:db/add "new-user" :user/email email]
            [:db/add "new-user" :user/session-token token]
            [:db/add "new-user" :user/created-at now]
            [:db/add "new-user" :user/last-login now]
            ;; Create progress linked to user
            [:db/add "new-progress" :progress/user "new-user"]
            [:db/add "new-progress" :progress/current-level 0]
            [:db/add "new-progress" :progress/unlocked-levels 0]])
        ;; Fetch the newly created user
        (get-user-by-email email)))))

(defn logout!
  "Clear session token for a user (optional - for explicit logout)."
  [user-id]
  (when user-id
    @(d/transact (schema/get-conn)
       [[:db/retract user-id :user/session-token]])))
