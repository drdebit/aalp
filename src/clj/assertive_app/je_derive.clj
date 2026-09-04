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
            [assertive-app.cost-basis :as cost]
            [assertive-app.chain :as chain]))

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

(defn as-flows
  "A flow assertion's value as a sequence.

   A transformation consumes more than one thing: printing a shirt takes
   a blank shirt AND ink. The research model writes `consumes` as a list
   for exactly this reason, and a single map is just the one-element
   case. Normalising here means every rule downstream fires once per
   thing consumed rather than once per assertion, which is what makes a
   multi-input production entry come out with a credit per input."
  [v]
  (cond (nil? v) []
        (sequential? v) (vec v)
        (map? v) [v]
        :else []))

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

   ;; -------- Money in with nothing going out: the residual ----------
   {:id :owner-capital
    :when {:assertion :receives :params {:unit "monetary-unit"}}
    :context {:none-of [{:assertion :provides :params {:unit "physical-unit"}}
                        {:assertion :requires}
                        {:assertion :modifies}
                        {:assertion :consumes}]}
    :line {:side :credit :account "Owner's Capital"}
    :amount :monetary
    :text "Money came in and nothing went out with it. SP gave up no goods, took on no obligation to repay, and settled nothing owed. What is left is a claim by whoever put the money in, against whatever the business has -- and that is what equity IS. Not a kind of transaction, but the part left over once you have accounted for what the business owes."}

   ;; -------- Goods received: the account is the item's POSITION -------
   ;; Not three rules keyed on which item it is. One rule that asks where
   ;; the thing sits in the chain of what SP has recorded: an input, a
   ;; thing between stages, a thing ready to sell, capital held for use.
   ;; Raw materials and work in process are readings of the record, so
   ;; they are resolved here rather than asserted by the student.
   {:id :goods-in
    :when {:assertion :receives :params {:unit "physical-unit"}}
    :line {:side :debit :account :position}
    :amount :monetary
    :text :position}

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
    ;; The business is to RECEIVE money: provides and receives are always
    ;; the business's own actions, so a customer's promise is recorded as
    ;; what the business will receive, not as the customer providing.
    :when {:assertion :requires
           :params {:action "receives" :unit "monetary-unit"}}
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
    :line {:side :credit :account :position}
    :amount :input-cost
    :text "Production used up raw materials; the asset decreases at cost."}

   {:id :production-in
    :when {:assertion :creates :params {:unit "physical-unit"}}
    :line {:side :debit :account :position}
    :amount :total-input-cost
    :text "Production created finished goods; the asset increases at the cost of what went in."}])

;; ---------------------------------------------------------------------------
;; Recorded-but-not-reflected explanations
;; ---------------------------------------------------------------------------

(def position-texts
  "Why the item landed in this account. The position is read off the
   chain (and SP's catalogue of what kind of thing each item is), so the
   explanation names the reasoning rather than the item."
  {:raw-materials      "SP's rulebook: this is an input -- something bought to be used up in production, not sold as it is. Inputs are Raw Materials Inventory, an asset, carried at what SP gave (or owes) for them."
   :work-in-process    "This item was created by one transformation and consumed by another: it is caught between stages. Nobody asserted that -- it is true because of what the record shows happened next, and it would stop being true if nothing further consumed it."
   :finished-goods     "SP's rulebook: this is ready to sell as it stands, so it is Finished Goods Inventory -- an asset held for sale."
   :equipment-or-other "SP's rulebook: this is used for years rather than sold to customers, so it is Equipment -- a long-term asset."
   :service            "A service consumed rather than a thing held: it is a cost of the period, not an asset."})

(def not-reflected-texts
  "Why an assertion produces no line.

   These are not shortcomings. Double-entry records completed exchanges
   measured in money, and that convention -- the monetary unit
   assumption, chiefly -- is what makes entries comparable, addable and
   auditable in the first place. The cost of it is that anything without
   a money measure has nowhere to go, which is normally handled by
   writing it down somewhere else, or not at all.

   Keeping those assertions is the point: the entry stays exactly as
   double-entry intends, and the rest of what SP knows stays with it."
  {:expects "Recorded -- but not reflected. Double-entry records what has happened, measured in money, and an expectation is neither settled nor a money amount. It stays in your record, and it is what lets you compare later what you expected with what occurred."
   :is-allowed-by "Recorded -- but not reflected. The authority for an event is not itself an exchange, so no account carries it. Keeping it is what lets an entry be traced back to the rule that permitted it."
   :allows "Recorded -- but not reflected. Nothing has changed hands yet, so there is nothing for double-entry to measure today. It still decides how later events are classified -- you have seen it do that."
   :is-required-by "Recorded -- but not reflected. The framework requiring an event is not an exchange, so no account carries it."
   :reports "Recorded -- but not reflected here. Reporting assertions drive calculations rather than journal-entry lines."})

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
      ;; A claim on the business: countable, additive, commensurable
      ;; between holders -- and deliberately NOT monetary, so it can
      ;; never price a journal-entry line. Units are recorded; what they
      ;; are worth is a different question with a different answer.
      ("ownership-units" "ownership-unit") (q/quantity v :claim :ownership-unit)
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
  ([amount-kind matched-code matched-params selections variables]
   (resolve-amount amount-kind matched-code matched-params selections variables nil))
  ([amount-kind matched-code matched-params selections variables context]
   (let [basis (:cost-basis context)
         priced (fn [item units reason]
                  (if-let [c (cost/cost-of basis item units)]
                    {:quantity (q/monetary c) :unresolved? false}
                    {:quantity nil :unresolved? true :unresolved-reason reason}))]
     (case amount-kind
       :flow     (let [own (params->quantity matched-params)]
                   {:quantity (if (monetary-quantity? own)
                                own
                                (monetary-amount selections variables))
                    :unresolved? false})
       :monetary {:quantity (monetary-amount selections variables) :unresolved? false}

       ;; What the goods COST -- not what they sold for. Recovered from
       ;; the events that acquired or produced them (see cost-basis).
       :cost-basis (let [p matched-params]
                     (priced (:physical-item p) (:quantity p)
                             "These goods have no recorded cost yet -- nothing in the ledger records acquiring or producing them."))

       ;; A transformation is worth what went into it. For a consumed
       ;; line that is this input's own cost; for the created line it is
       ;; the total of everything consumed, since the output is worth the
       ;; sum of what made it.
       :input-cost (let [p matched-params]
                     (priced (:physical-item p) (:quantity p)
                             "The consumed materials have no recorded cost yet -- nothing in the ledger records acquiring them."))
       :total-input-cost
       (let [inputs (as-flows (:consumes selections))
             costs  (map #(cost/cost-of basis (:physical-item %) (:quantity %)) inputs)]
         (if (and (seq costs) (every? some? costs))
           {:quantity (q/monetary (reduce + costs)) :unresolved? false}
           {:quantity nil :unresolved? true
            :unresolved-reason "The materials consumed have no recorded cost yet -- nothing in the ledger records acquiring them."}))

       :unknown  {:quantity nil :unresolved? true
                  :unresolved-reason "The assertions of this event do not carry this figure."}
       {:quantity nil :unresolved? true}))))

;; ---------------------------------------------------------------------------
;; Derivation
;; ---------------------------------------------------------------------------

(defn- resolve-position
  "The position of the item this line is about, read from the chain the
   student has recorded (including the event being booked) plus SP's
   catalogue of what kind of thing each item is."
  [flow context]
  (let [item (:physical-item flow)
        ;; The event being booked is part of its own chain: the materials
        ;; it consumes are being consumed now, which is what makes them
        ;; inputs rather than merely things once bought.
        events (concat (:events context) [(:current context)])]
    (chain/inventory-position events item (:item-kinds context))))

(defn- established-elsewhere
  "The EARLIER events that made this item what it is.

   A student looking at the purchase of blank shirts sees only that
   shirts were received; the reason those shirts are an input was given
   when the printer was bought. Without this, the drill-down promise --
   assertions underneath, and nothing else -- holds inside an event and
   quietly fails across the chain, which is where the interesting part
   of the record lives.

   The event being booked is excluded: its assertions are already on
   display."
  [flow context]
  (let [item    (:physical-item flow)
        current (:current context)
        events  (concat (:events context) [current])]
    (when-let [{:keys [because]} (chain/position-basis events item)]
      (->> because
           (remove #(= current (:event %)))
           (mapv (fn [{:keys [role event]}]
                   {:role role
                    :date (get-in event [:has-date :date])
                    :assertions (select-keys event [:allows :is-allowed-by :receives
                                                    :consumes :creates :provides])}))
           (take 2)
           vec))))

(defn- resolve-line-account
  "An :account of :position is resolved from the chain; anything else is
   the literal label the rule names."
  [account flow context]
  (if (= :position account)
    (or (some-> (resolve-position flow context)
                chain/position-accounts)
        ;; Not an account. The record has not said what this thing is,
        ;; and naming it something plausible would paper over exactly the
        ;; gap the student needs to see.
        "(not yet classified)")
    account))

(defn- resolve-line-text
  [text flow context]
  (if (= :position text)
    (or (some-> (resolve-position flow context) position-texts)
        "This item has no recorded position yet.")
    text))

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
        context (assoc context :current selections)
        ;; One firing per matching FLOW, not per assertion: consuming a
        ;; blank shirt and an ink cartridge is two things consumed, and
        ;; the entry needs a line for each.
        fired (mapcat (fn [rule]
                        (let [{:keys [assertion params]} (:when rule)
                              flows (as-flows (get selections assertion))
                              hits  (filterv #(params-match? % (or params {})) flows)
                              {:keys [ok? used]} (context-satisfied? selections (or (:context rule) {}))]
                          (when (and ok? (seq hits))
                            (mapv (fn [flow]
                                    (assoc rule :matched assertion
                                                :matched-params flow
                                                :context-used used))
                                  hits))))
                      rulebook)
        lines (mapv (fn [{:keys [line amount matched matched-params context-used id text entry-label]}]
                      (let [{:keys [quantity unresolved? unresolved-reason]}
                            (resolve-amount amount matched matched-params selections variables context)
                            prov (vec (distinct (cons matched context-used)))]
                        {:side (:side line)
                         :account (resolve-line-account (:account line) matched-params context)
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
                         :rule-text (resolve-line-text text matched-params context)
                         ;; Why this account, when the reason is not in
                         ;; this event.
                         :established-by (when (= :position (:account line))
                                           (seq (established-elsewhere matched-params context)))
                         :entry-label entry-label}))
                    fired)
        line-producing (set (mapcat :provenance lines))
        ;; Context assertions that shaped lines (or always-context ones)
        context (vec (keep (fn [[code role]]
                             (when (contains? selections code)
                               {:code code :role role}))
                           context-roles))
        context-codes (set (map :code context))
        claim? (fn [code]
                 (= :claim (get-in (params->quantity (get selections code)) [:unit :unit-type])))
        not-reflected (vec (keep (fn [code]
                                   (when-not (or (line-producing code)
                                                 (context-codes code))
                                     {:code code
                                      :text (cond
                                              ;; A claim on the business is countable and
                                              ;; recorded, and double-entry has no line for
                                              ;; it. Who owns the business, and how much of
                                              ;; it, is not something a journal entry can
                                              ;; say -- the ownership schedule is computed
                                              ;; from the record instead.
                                              (claim? code)
                                              "Recorded -- but not reflected. Double-entry measures in money, and 200 units is a count rather than an amount -- that is the monetary unit assumption doing its job, and it is what keeps every entry addable. The units stay in your record, and the ownership schedule is worked out from them."

                                              :else
                                              (get not-reflected-texts code
                                                   "Recorded -- but no rule in SP's rulebook produces a journal-entry line from this assertion."))}))
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
