(ns assertive-app.je-derive
  "Live journal-entry derivation from selected assertions -- the firm
   rulebook made executable.

   Dual-fluency principle (platform design notes): students see which
   assertions are load-bearing for which line of the journal entry.
   Each rule maps an assertion pattern (possibly context-dependent) to
   one JE line, with plain-language rule text -- so every derived line
   can answer 'which rule produced you?', and every selected assertion
   that produces no line is surfaced as RECORDED BUT NOT REFLECTED:
   the recording-vs-reporting distinction as a UI element.

   The derivation is faithful, not corrective: wrong assertions produce
   wrong (or partial) journal entries without comment. Partial entries
   render missing sides as prompts, not errors."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn- parse-num [s]
  (try (Double/parseDouble (str/trim s)) (catch Exception _ nil)))

(defn- num-or-nil [v]
  (cond
    (number? v) v
    (string? v) (parse-num v)
    :else nil))

(defn- params-match?
  "Do the selected params for an assertion satisfy the rule's param
   pattern? Pattern values may be a string (equality) or a set (any-of)."
  [selected-params pattern]
  (every? (fn [[k v]]
            (let [actual (get selected-params k)]
              (if (set? v) (contains? v actual) (= actual v))))
          pattern))

(defn- assertion-matches?
  "Is the rule's :when (or a context condition) satisfied by the
   student's selections? Returns the matched assertion code or nil."
  [selections {:keys [assertion params]}]
  (when-let [sel (get selections assertion)]
    (when (params-match? sel (or params {}))
      assertion)))

(defn- context-satisfied?
  "Check a rule's :context {:all-of [...] :none-of [...]} conditions.
   Returns {:ok? bool :used [codes]} -- used codes become provenance."
  [selections {:keys [all-of none-of]}]
  (let [matched (mapv #(assertion-matches? selections %) (or all-of []))
        blocked (some #(assertion-matches? selections %) (or none-of []))]
    {:ok?  (and (every? some? matched) (nil? blocked))
     :used (vec (remove nil? matched))}))

;; ---------------------------------------------------------------------------
;; The firm rulebook
;;
;; Account names match classification.clj's vocabulary exactly.
;; :amount is one of
;;   :flow      -- the matched assertion's own :quantity parameter
;;   :monetary  -- the quantity on whichever selected assertion carries
;;                 monetary units (receives/provides/requires)
;;   :unknown   -- no amount derivable from assertions (renders as ?)
;; ---------------------------------------------------------------------------

(def rulebook
  [;; -------- Money flows --------
   {:id :cash-in
    :when {:assertion :receives :params {:unit "monetary-unit"}}
    :line {:side :debit :account "Cash"}
    :amount :flow
    :text "Money coming in increases Cash, an asset. Assets increase with debits."}

   {:id :cash-out
    :when {:assertion :provides :params {:unit "monetary-unit"}}
    :line {:side :credit :account "Cash"}
    :amount :flow
    :text "Money going out decreases Cash. Assets decrease with credits."}

   ;; -------- Goods received: the account depends on WHAT the thing is
   ;; (SP's rulebook: raw materials vs equipment vs finished goods) ----
   {:id :raw-materials-in
    :when {:assertion :receives
           :params {:unit "physical-unit"
                    :physical-item #{"blank-tshirts" "ink-cartridges"}}}
    :line {:side :debit :account "Raw Materials Inventory"}
    :amount :monetary
    :text "SP's rulebook: blank shirts and ink are inputs to production, so they are Raw Materials Inventory -- an asset, recorded at what SP gave (or owes) for them."}

   {:id :equipment-in
    :when {:assertion :receives
           :params {:unit "physical-unit" :physical-item "t-shirt-printer"}}
    :line {:side :debit :account "Equipment (Fixed Asset)"}
    :amount :monetary
    :text "SP's rulebook: the printer is used for years, not sold to customers, so it is Equipment -- a long-term asset."}

   {:id :finished-goods-in
    :when {:assertion :receives
           :params {:unit "physical-unit" :physical-item "printed-tshirts"}}
    :line {:side :debit :account "Finished Goods Inventory"}
    :amount :monetary
    :text "SP's rulebook: printed shirts are ready to sell, so they are Finished Goods Inventory."}

   ;; -------- Goods provided: revenue needs a counterparty ------------
   {:id :revenue
    :when {:assertion :provides
           :params {:unit "physical-unit" :physical-item "printed-tshirts"}}
    :context {:all-of [{:assertion :has-counterparty}]}
    :line {:side :credit :account "Revenue"}
    :amount :monetary
    :entry-label "Revenue Recognition"
    :text "SP's rulebook: providing finished goods to a counterparty is a sale. Revenue is credited for what the counterparty gives (or owes) in return. Notice: 'Revenue' is a label applied to this PATTERN of assertions, not a fact SP observed."}

   {:id :cogs
    :when {:assertion :provides
           :params {:unit "physical-unit" :physical-item "printed-tshirts"}}
    :context {:all-of [{:assertion :has-counterparty}]}
    :line {:side :debit :account "Cost of Goods Sold"}
    :amount :unknown
    :entry-label "Cost Recognition"
    :text "The shirts SP gave up had a cost. That cost leaves inventory and becomes an expense -- at cost, which the assertions about THIS exchange do not carry. (It lives in the production events.)"}

   {:id :cogs-inventory
    :when {:assertion :provides
           :params {:unit "physical-unit" :physical-item "printed-tshirts"}}
    :context {:all-of [{:assertion :has-counterparty}]}
    :line {:side :credit :account "Finished Goods Inventory"}
    :amount :unknown
    :entry-label "Cost Recognition"
    :text "The finished goods asset decreases by the same cost."}

   ;; -------- Obligations: SAME assertion, different account,
   ;;          depending on which way the goods flowed ---------------
   {:id :receivable
    :when {:assertion :requires
           :params {:action "provides" :unit "monetary-unit"}}
    :context {:all-of [{:assertion :provides :params {:unit "physical-unit"}}]}
    :line {:side :debit :account "Accounts Receivable"}
    :amount :flow
    :text "SP provided goods and someone is now required to provide money: a right to collect. SP's rulebook calls that Accounts Receivable -- an asset. The SAME 'requires' assertion becomes a liability when the goods flow the other way."}

   {:id :payable
    :when {:assertion :requires
           :params {:action "provides" :unit "monetary-unit"}}
    :context {:all-of [{:assertion :receives :params {:unit "physical-unit"}}]
              :none-of [{:assertion :provides :params {:unit "physical-unit"}}]}
    :line {:side :credit :account "Accounts Payable"}
    :amount :flow
    :text "SP received goods and is required to provide money later: an obligation. SP's rulebook calls that Accounts Payable -- a liability. The SAME 'requires' assertion becomes an asset when the goods flow the other way."}

   ;; -------- Cash in advance of goods --------------------------------
   {:id :deferred-revenue
    :when {:assertion :requires
           :params {:action "provides" :unit "physical-unit"}}
    :context {:all-of [{:assertion :receives :params {:unit "monetary-unit"}}]}
    :line {:side :credit :account "Deferred Revenue (Liability)"}
    :amount :monetary
    :text "SP took the money first and still owes the goods. Until the goods are provided, the cash is a liability -- Deferred Revenue -- not earned revenue."}

   ;; -------- Production -----------------------------------------------
   {:id :production-out
    :when {:assertion :consumes :params {:unit "physical-unit"}}
    :line {:side :credit :account "Raw Materials"}
    :amount :unknown
    :text "Production used up raw materials; the asset decreases at cost."}

   {:id :production-in
    :when {:assertion :creates :params {:unit "physical-unit"}}
    :line {:side :debit :account "Finished Goods"}
    :amount :unknown
    :text "Production created finished goods; the asset increases at the cost of what went in."}])

;; ---------------------------------------------------------------------------
;; Recorded-but-not-reflected explanations
;; ---------------------------------------------------------------------------

(def not-reflected-texts
  {:expects "Recorded -- but not reflected. Double-entry has no place for a probability-weighted expectation. The assertion stays in the record; the journal entry cannot see it."
   :is-allowed-by "Recorded -- but not reflected. The legal authority for this event lives in the assertion record; no account exists for it."
   :allows "Recorded -- but not reflected. What this event makes possible in the future produces no journal entry today."
   :is-required-by "Recorded -- but not reflected. The framework requiring this event has no account."
   :reports "Recorded -- but not reflected here. Reporting assertions drive calculations, not journal-entry lines."})

(def context-roles
  {:has-date "stamps the entry's date"
   :has-counterparty "identifies the other party -- it decides WHICH account fits, without appearing on any line"})

;; ---------------------------------------------------------------------------
;; Amount resolution
;; ---------------------------------------------------------------------------

(defn- monetary-amount
  "Find the dollar amount among the student's selections: the :quantity
   parameter of any monetary-unit flow, else the problem's :amount."
  [selections variables]
  (or (some (fn [code]
              (let [p (get selections code)]
                (when (= (:unit p) "monetary-unit")
                  (num-or-nil (:quantity p)))))
            [:receives :provides :requires])
      (num-or-nil (:amount variables))))

(defn- resolve-amount [amount-kind matched-code selections variables]
  (case amount-kind
    :flow     (or (num-or-nil (get-in selections [matched-code :quantity]))
                  (monetary-amount selections variables))
    :monetary (monetary-amount selections variables)
    :unknown  nil
    nil))

;; ---------------------------------------------------------------------------
;; Derivation
;; ---------------------------------------------------------------------------

(defn derive-je
  "Derive a journal entry from the student's selected assertions.

   selections: {assertion-code {param-key value}} (as sent to /classify)
   variables:  the problem's variables map (for the :amount fallback)

   Returns
   {:lines         [{:side :debit|:credit :account s :amount n|nil
                     :provenance [codes] :rule-id kw :rule-text s
                     :entry-label s|nil}]
    :placeholders  [{:side kw :prompt s}]   ;; the missing-side prompts
    :context       [{:code kw :role s}]
    :not-reflected [{:code kw :text s}]
    :totals        {:debits n :credits n :balanced? bool}}

   Faithful derivation: no reference to any 'correct' classification."
  [selections variables]
  (let [selections (into {} (map (fn [[k v]] [(keyword k) (or v {})]) selections))
        fired (keep (fn [rule]
                      (when-let [matched (assertion-matches? selections (:when rule))]
                        (let [{:keys [ok? used]} (context-satisfied? selections (or (:context rule) {}))]
                          (when ok?
                            (assoc rule :matched matched :context-used used)))))
                    rulebook)
        lines (mapv (fn [{:keys [line amount matched context-used id text entry-label]}]
                      {:side (:side line)
                       :account (:account line)
                       :amount (resolve-amount amount matched selections variables)
                       :provenance (vec (distinct (cons matched context-used)))
                       :rule-id id
                       :rule-text text
                       :entry-label entry-label})
                    fired)
        line-producing (set (mapcat :provenance lines))
        ;; Context assertions that shaped lines (or always-context ones)
        context (vec (keep (fn [[code role]]
                             (when (contains? selections code)
                               {:code code :role role}))
                           context-roles))
        context-codes (set (map :code context))
        not-reflected (vec (keep (fn [code]
                                   (when-not (or (line-producing code)
                                                 (context-codes code))
                                     {:code code
                                      :text (get not-reflected-texts code
                                                 "Recorded -- but no rule in SP's rulebook produces a journal-entry line from this assertion.")}))
                                 (keys selections)))
        sum-side (fn [side]
                   (reduce + 0 (keep #(when (= side (:side %)) (:amount %)) lines)))
        debits (sum-side :debit)
        credits (sum-side :credit)
        has-debit? (some #(= :debit (:side %)) lines)
        has-credit? (some #(= :credit (:side %)) lines)
        placeholders (cond-> []
                       (and has-debit? (not has-credit?))
                       (conj {:side :credit
                              :prompt "Something must balance this. What did SP give up, or come to owe? The assertions do not say yet."})
                       (and has-credit? (not has-debit?))
                       (conj {:side :debit
                              :prompt "Something must balance this. What did SP get, or settle? The assertions do not say yet."}))]
    {:lines lines
     :placeholders placeholders
     :context context
     :not-reflected not-reflected
     :totals {:debits debits
              :credits credits
              :balanced? (and (pos? (+ debits credits))
                              (== debits credits)
                              (empty? placeholders))}}))
