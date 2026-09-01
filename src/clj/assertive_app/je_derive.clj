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
  (:require [clojure.string :as str]
            [assertive-engine.model.quantity :as q]
            [assertive-app.cost-basis :as cost]))

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

   ;; -------- Labour received ------------------------------------------
   {:id :wage-expense
    :when {:assertion :receives
           :params {:unit #{"effort" "effort-unit"}}}
    :line {:side :debit :account "Wage Expense"}
    :amount :monetary
    :text "SP received effort -- someone's labour. Labour is consumed as it is given: there is no asset to carry forward, so it is an expense in the period. What SP owes or paid for it is the money side of the same exchange."}

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
    :amount :cost-basis
    :entry-label "Cost Recognition"
    :text "The shirts SP gave up had a cost. That cost leaves inventory and becomes an expense -- at cost, which the assertions about THIS exchange do not carry. (It lives in the production events.)"}

   {:id :cogs-inventory
    :when {:assertion :provides
           :params {:unit "physical-unit" :physical-item "printed-tshirts"}}
    :context {:all-of [{:assertion :has-counterparty}]}
    :line {:side :credit :account "Finished Goods Inventory"}
    :amount :cost-basis
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
    :line {:side :credit :account "Raw Materials Inventory"}
    :amount :input-cost
    :text "Production used up raw materials; the asset decreases at cost."}

   {:id :production-in
    :when {:assertion :creates :params {:unit "physical-unit"}}
    :line {:side :debit :account "Finished Goods Inventory"}
    :amount :input-cost
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

(defn params->quantity
  "AALP's flat assertion params -> an assertive-engine quantity.

   The engine's quantity, {:value N :unit {:unit-type K :unit U}}, is the
   substrate. Denomination travels WITH the value, so nothing downstream
   can mistake seven t-shirts for seven dollars: they are no longer both
   just the number 7. This is the translation seam -- assertions in,
   engine quantities out; account labels come later and only for display."
  [{:keys [unit physical-item quantity]}]
  (when-let [v (num-or-nil quantity)]
    (case (some-> unit name)
      "monetary-unit"      (q/monetary v)
      "physical-unit"      (q/physical v (keyword (or physical-item "unspecified")))
      ("time-unit" "time") (q/time-qty v)
      ("effort-unit" "effort") (q/effort v)
      nil)))

(defn monetary-quantity?
  "Is this engine quantity denominated in money? A journal-entry line is
   measured in currency, so only such a quantity may price one."
  [qty]
  (= :monetary (get-in qty [:unit :unit-type])))

(defn monetary?
  "Is this assertion's selection denominated in money?"
  [params]
  (monetary-quantity? (params->quantity params)))

(defn- monetary-amount
  "The monetary quantity among the student's selections, else the
   problem's :amount variable lifted into one.

   Order matters only when several flows are monetary; it follows the
   money the entry is measured by -- cash that actually moved
   (receives / provides) before an amount merely owed or expected."
  [selections variables]
  (or (some (fn [code]
              (let [qty (params->quantity (get selections code))]
                (when (monetary-quantity? qty) qty)))
            [:receives :provides :requires :expects])
      (when-let [v (num-or-nil (:amount variables))] (q/monetary v))))

(defn- resolve-amount
  "Resolve one line's amount as an engine quantity.
   Returns {:quantity q|nil :unresolved? bool}.

   :flow uses the matched assertion's own quantity, but only when that
   quantity is itself monetary -- otherwise it falls back to the
   transaction's monetary amount rather than spending a physical count.
   :unknown means the assertions genuinely do not carry this figure
   (a cost that lives in other events); it stays nil and is marked
   unresolved so nothing downstream invents a number for it."
  ([amount-kind matched-code selections variables]
   (resolve-amount amount-kind matched-code selections variables nil))
  ([amount-kind matched-code selections variables context]
   (let [basis (:cost-basis context)
         priced (fn [item units reason]
                  (if-let [c (cost/cost-of basis item units)]
                    {:quantity (q/monetary c) :unresolved? false}
                    {:quantity nil :unresolved? true :unresolved-reason reason}))]
     (case amount-kind
       :flow     (let [own (params->quantity (get selections matched-code))]
                   {:quantity (if (monetary-quantity? own)
                                own
                                (monetary-amount selections variables))
                    :unresolved? false})
       :monetary {:quantity (monetary-amount selections variables) :unresolved? false}

       ;; What the goods COST -- not what they sold for. Recovered from
       ;; the events that acquired or produced them (see cost-basis).
       :cost-basis (let [p (get selections matched-code)]
                     (priced (:physical-item p) (:quantity p)
                             "These goods have no recorded cost yet -- nothing in the ledger records acquiring or producing them."))

       ;; A transformation is worth what went into it.
       :input-cost (let [p (get selections :consumes)]
                     (priced (:physical-item p) (:quantity p)
                             "The consumed materials have no recorded cost yet -- nothing in the ledger records acquiring them."))

       :unknown  {:quantity nil :unresolved? true
                  :unresolved-reason "The assertions of this event do not carry this figure."}
       {:quantity nil :unresolved? true}))))

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

   context: optional {:cost-basis {item {:unit-cost n}}} -- what prior
   events establish the goods cost. Supplying it lets the cost lines be
   priced from the record; omitting it leaves them honestly unpriced.

   Faithful derivation: no reference to any 'correct' classification."
  ([selections variables] (derive-je selections variables nil))
  ([selections variables context]
  (let [selections (into {} (map (fn [[k v]] [(keyword k) (or v {})]) selections))
        fired (keep (fn [rule]
                      (when-let [matched (assertion-matches? selections (:when rule))]
                        (let [{:keys [ok? used]} (context-satisfied? selections (or (:context rule) {}))]
                          (when ok?
                            (assoc rule :matched matched :context-used used)))))
                    rulebook)
        lines (mapv (fn [{:keys [line amount matched context-used id text entry-label]}]
                      (let [{:keys [quantity unresolved? unresolved-reason]}
                            (resolve-amount amount matched selections variables context)
                            prov (vec (distinct (cons matched context-used)))]
                        {:side (:side line)
                         :account (:account line)
                         ;; The typed quantity is the real value; :amount is
                         ;; its scalar projection, kept for the wire and the
                         ;; ledger. Every posted amount is money by
                         ;; construction -- resolve-amount admits no other
                         ;; denomination.
                         :quantity quantity
                         :amount (:value quantity)
                         :amount-unit (get-in quantity [:unit :unit-type])
                         :unresolved? unresolved?
                         :unresolved-reason unresolved-reason
                         :provenance prov
                         ;; Drill-down payload: the assertions themselves,
                         ;; so a student opening this line finds assertions
                         ;; underneath and nothing else.
                         :assertions (select-keys selections prov)
                         :rule-id id
                         :rule-text text
                         :entry-label entry-label}))
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
     ;; Lines the assertions do not price. Never invent a figure for
     ;; these -- surfacing them IS the lesson (the cost of goods sold
     ;; lives in the production events, not in this exchange).
     :unresolved (vec (keep (fn [l] (when (:unresolved? l)
                                      {:account (:account l) :side (:side l)
                                       :rule-id (:rule-id l)
                                       :reason (:unresolved-reason l)}))
                            lines))
     :totals {:debits debits
              :credits credits
              :balanced? (and (pos? (+ debits credits))
                              (== debits credits)
                              (empty? placeholders))
              :fully-priced? (and (pos? (+ debits credits))
                                  (== debits credits)
                                  (empty? placeholders)
                                  (not-any? :unresolved? lines))}})))

;; ---------------------------------------------------------------------------
;; The canonical entry -- one shape, used by feedback, ledger and statements
;; ---------------------------------------------------------------------------

(defn derive-entry
  "The journal entry for a set of assertions, in the one shape the rest
   of the system stores and renders.

   This is the single authority for BOTH which lines exist and what they
   are worth. Nothing downstream may re-derive an amount, and nothing
   may parse one back out of a display string -- that round trip is what
   let a physical count be banked as dollars.

   {:lines [{:side :debit :account s :amount n|nil :amount-unit :monetary|nil
             :unresolved? bool :provenance [codes] :rule-id kw :rule-text s
             :entry-label s|nil}]
    :amount        n|nil    ;; the transaction's monetary amount
    :balanced?     bool
    :fully-priced? bool     ;; balanced AND every line carries an amount
    :unresolved    [...]
    :covered?      bool}    ;; did the rulebook produce any line at all?"
  ([selections] (derive-entry selections {} nil))
  ([selections variables] (derive-entry selections variables nil))
  ([selections variables context]
   (let [d    (derive-je selections variables context)
         sel  (into {} (map (fn [[k v]] [(keyword k) (or v {})]) selections))
         qty  (monetary-amount sel variables)]
     {:lines         (:lines d)
      :quantity      qty
      :amount        (:value qty)
      :balanced?     (get-in d [:totals :balanced?])
      :fully-priced? (get-in d [:totals :fully-priced?])
      :unresolved    (:unresolved d)
      :covered?      (boolean (seq (:lines d)))})))
