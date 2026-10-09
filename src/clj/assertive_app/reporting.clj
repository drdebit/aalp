(ns assertive-app.reporting
  "The reporting lesson: fixed company records, the reports composed over
   them, and grading by composition (LESSON-REPORTING-DESIGN.org).

   Practice problems draw a fresh company record every time. A report
   needs the opposite: the same year for everyone, so the answers are
   knowable and a composition can be checked against the right one."
  (:require [assertive-app.readings :as readings]
            [assertive-app.chain :as chain]
            [assertive-app.engine :as engine]
            [assertive-engine.compute.collects :as collects]
            [assertive-engine.query.pattern :as pattern]
            [assertive-engine.store.protocol :as store]
            [clojure.set :as set]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Fixed records
;; ---------------------------------------------------------------------------
;; Harbor Line Shirts, a wholesaler of blank shirts, and its 2026. The
;; year is built so the two bases disagree, and for more than one reason:
;;
;;   - a 2025 credit sale collected in January 2026: cash revenue this
;;     year, accrual revenue last year
;;   - a November credit sale not collected by year end: accrual revenue,
;;     not cash
;;   - a batch bought on credit in September and not paid by year end: its
;;     shirts' cost is accrual cost of goods sold, and not deductible under
;;     the tax cash method (Reg. 1.471-1(b)(4): the later of paid and sold)
;;
;; and so a composition has to leave things out: a cleaning service and an
;; owner's draw are money out that is not the cost of goods.
;;
;; 2026 figures -- accrual: revenue 2,160, cost of goods sold 1,180, gross
;; margin 980. Cash: revenue 2,110, cost of goods sold 640, gross profit
;; 1,470. The tests check the compositions come to these.

(def ^:private merchandise
  {:action "provides" :unit "physical-unit" :physical-item "blank-tshirts" :confidence 95})

(def records
  {:harbor-line
   {:company "Harbor Line Shirts"
    :blurb "Harbor Line Shirts is a wholesaler: it buys blank shirts by the box and sells them on to schools, clubs and shops. It has never printed a shirt."
    :period {:from "2026-01-01" :to "2026-12-31"}
    ;; Reports the company has already made, in its record for the
    ;; student to read before composing one.
    :reports [:accrual-revenue :cash-revenue]
    :events
    [{:has-identifier "Funding-001" :has-date {:date "2025-12-01"}
      :receives {:unit "monetary-unit" :quantity 10000}
      :provides {:unit "ownership-units" :quantity 100}
      :has-counterparty {:name "the owner"}}
     {:has-identifier "Shirts-001" :has-date {:date "2025-12-03"}
      :provides {:unit "monetary-unit" :quantity 800}
      :receives {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 200}
      :expects merchandise
      :has-counterparty {:name "TextileDirect"}}
     {:has-identifier "Sale-000" :has-date {:date "2025-12-20"}
      :provides {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 50 :from-event "Shirts-001"}
      :requires {:action "receives" :unit "monetary-unit" :quantity 450 :due-date "2026-01-19"}
      :expects {:action "receives" :unit "monetary-unit" :confidence 90}
      :has-counterparty {:name "Ridgeway Middle School"}}
     {:has-identifier "Collect-000" :has-date {:date "2026-01-15"}
      :receives {:unit "monetary-unit" :quantity 450}
      :fulfills {:action "requires" :event "Sale-000/requires"}
      :has-counterparty {:name "Ridgeway Middle School"}}
     {:has-identifier "Shirts-002" :has-date {:date "2026-02-02"}
      :receives {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 150}
      :requires {:action "provides" :unit "monetary-unit" :quantity 750 :due-date "2026-03-04"}
      :expects merchandise
      :has-counterparty {:name "PrintSupplyCo"}}
     {:has-identifier "Sale-001" :has-date {:date "2026-02-14"}
      :provides {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 60 :from-event "Shirts-001"}
      :receives {:unit "monetary-unit" :quantity 540}
      :has-counterparty {:name "the chess club"}}
     {:has-identifier "Pay-001" :has-date {:date "2026-03-04"}
      :provides {:unit "monetary-unit" :quantity 750}
      :fulfills {:action "requires" :event "Shirts-002/requires"}
      :has-counterparty {:name "PrintSupplyCo"}}
     {:has-identifier "Sale-002" :has-date {:date "2026-04-10"}
      :provides {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 80 :from-event "Shirts-002"}
      :requires {:action "receives" :unit "monetary-unit" :quantity 720 :due-date "2026-05-10"}
      :expects {:action "receives" :unit "monetary-unit" :confidence 88}
      :has-counterparty {:name "Fairview Running Club"}}
     {:has-identifier "Collect-002" :has-date {:date "2026-05-08"}
      :receives {:unit "monetary-unit" :quantity 720}
      :fulfills {:action "requires" :event "Sale-002/requires"}
      :has-counterparty {:name "Fairview Running Club"}}
     {:has-identifier "Service-001" :has-date {:date "2026-06-01"}
      :provides {:unit "monetary-unit" :quantity 300}
      :receives {:unit "service-unit" :quantity 1 :service-item "stockroom cleaning"}
      :has-counterparty {:name "CleanSweep"}}
     {:has-identifier "Shirts-003" :has-date {:date "2026-09-01"}
      :receives {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 100}
      :requires {:action "provides" :unit "monetary-unit" :quantity 600 :due-date "2027-01-15"}
      :expects merchandise
      :has-counterparty {:name "TextileDirect"}}
     {:has-identifier "Sale-003" :has-date {:date "2026-10-05"}
      :provides {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 40 :from-event "Shirts-003"}
      :receives {:unit "monetary-unit" :quantity 400}
      :has-counterparty {:name "Delmar Coffee Roasters"}}
     {:has-identifier "Sale-004" :has-date {:date "2026-11-20"}
      :provides {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 50 :from-event "Shirts-003"}
      :requires {:action "receives" :unit "monetary-unit" :quantity 500 :due-date "2027-01-05"}
      :expects {:action "receives" :unit "monetary-unit" :confidence 85}
      :has-counterparty {:name "Harbor Youth League"}}
     ;; A dividend declared and paid: money out that is not the cost of
     ;; goods (it was an owner's draw until 2026-10-08; 2101 is corporate).
     {:has-identifier "Dividend-001" :has-date {:date "2026-12-01"}
      :reports {:category "distribution" :basis "declared" :amount 1000}
      :requires {:action "provides" :unit "monetary-unit" :quantity 1000 :due-date "2026-12-15"}}
     {:has-identifier "DivPay-001" :has-date {:date "2026-12-15"}
      :provides {:unit "monetary-unit" :quantity 1000}
      :has-counterparty {:name "Stockholders"}
      :fulfills {:action "requires" :event "Dividend-001/requires"}}]}})

(defn events-for-engine
  "A record's events as engine-ready maps, each carrying its readings.
   Every event is asserted by the company, so a composition over it is
   scoped to the company rather than to the student viewing it."
  [company events]
  (let [rs (readings/readings-of events)]
    (vec (for [e events
               :let [id (:has-identifier e)]]
           {:event-id     id
            :assertions   (dissoc e :has-identifier)
            :date         (get-in e [:has-date :date])
            :asserted-by  company
            :counterparty (get-in e [:has-counterparty :name])
            :readings     (get rs id)}))))

(defn record-for-engine
  "A fixed record as engine-ready events."
  [record-key]
  (when-let [{:keys [company events]} (get records record-key)]
    (events-for-engine company events)))

(defn- describe-flow [verb f]
  (let [q (:quantity f)]
    (if (= "monetary-unit" (some-> (:unit f) name))
      (str verb " $" q)
      (str verb " " q " " (str/replace (str (or (:physical-item f) (:service-item f) (some-> (:unit f) name) "")) "-" " ")))))

(defn event-summary
  "An event as a line a student can read in the record."
  [e]
  (let [flows #(cond (nil? %) [] (sequential? %) % :else [%])]
    {:id (:has-identifier e)
     :date (get-in e [:has-date :date])
     :counterparty (get-in e [:has-counterparty :name])
     :says (str/join "; " (concat (map #(describe-flow "provides" %) (flows (:provides e)))
                                  (map #(describe-flow "receives" %) (flows (:receives e)))
                                  (when-let [r (:requires e)]
                                    [(str "requires: " (if (= "receives" (some-> (:action r) name)) "to receive" "to provide")
                                          " $" (:quantity r) " by " (:due-date r))])
                                  (when-let [p (chain/kept-promise e)] [(str "keeps the promise made in " (chain/promise-event-id p))])))}))


;; ---------------------------------------------------------------------------
;; Criteria: the chips a report is composed from
;; ---------------------------------------------------------------------------
;; A composition is criteria to collect by, criteria to leave out by, and
;; a calculation (SELECTION-AND-CALCULATION.org):
;;
;;   {:includes [{:kind "flow" :value "goods-out"} ...]
;;    :excludes [{:kind "batch" :value "unpaid"}]
;;    :calc     {:total "goods-cost"}}
;;
;; Each chip is a question the record can answer about an event. Its
;; :pattern is the engine form of that question; its :fact says, of one
;; event, how the question comes out -- which is what feedback is built
;; from. Nothing arrives from the client but chips from this list.

(defn- role-phrase [r]
  (case r
    :customer "a customer" :supplier "a supplier" :owner "an owner"
    :lender "a lender" :borrower "a borrower" nil))

(def criteria
  [{:kind "flow" :value "goods-out" :group "What the business did"
    :phrase "the business provides goods"
    :pattern {:assertion-types #{:provides} :provides-unit :physical}}
   {:kind "flow" :value "money-in" :group "What the business did"
    :phrase "the business receives money"
    :pattern {:assertion-types #{:receives} :receives-unit :monetary}}
   {:kind "flow" :value "money-out" :group "What the business did"
    :phrase "the business provides money"
    :pattern {:assertion-types #{:provides} :provides-unit :monetary}}
   {:kind "flow" :value "goods-in" :group "What the business did"
    :phrase "the business receives goods"
    :pattern {:assertion-types #{:receives} :receives-unit :physical}}
   {:kind "party" :value "customer" :group "Who the other party is"
    :phrase "the other party is a customer"
    :pattern {:readings {:counterparty-role :customer}}}
   {:kind "party" :value "supplier" :group "Who the other party is"
    :phrase "the other party is a supplier"
    :pattern {:readings {:counterparty-role :supplier}}}
   {:kind "party" :value "owner" :group "Who the other party is"
    :phrase "the other party is an owner"
    :pattern {:readings {:counterparty-role :owner}}}
   {:kind "party" :value "lender" :group "Who the other party is"
    :phrase "the other party is a lender"
    :pattern {:readings {:counterparty-role :lender}}}
   {:kind "when" :value "period" :group "When"
    :phrase "in the reporting year"
    :pattern (fn [{:keys [from to]}] {:date-from from :date-to to})}
   {:kind "batch" :value "paid" :group "Condition"
    :phrase "the goods came from a batch that had been paid for"
    :pattern {:readings {:batch-paid true}}}
   {:kind "batch" :value "unpaid" :group "Condition"
    :phrase "the goods came from a batch not yet paid for"
    :pattern {:readings {:batch-paid false}}}])

(def totals
  "The calculation: what is added up over the collected events. A
   :reading totals a reading the record made of each event; a :quantity
   totals that assertion's money."
  [{:value "consideration" :phrase "what was received or promised for the goods" :reading :consideration}
   {:value "money-in"      :phrase "the money received" :quantity :receives}
   {:value "money-out"     :phrase "the money paid" :quantity :provides}
   {:value "goods-cost"    :phrase "what the goods cost" :reading :goods-cost}])

(defn vocabulary
  "What the client offers: every chip and every total, with its words."
  []
  {:criteria (mapv #(select-keys % [:kind :value :phrase :group]) criteria)
   :totals   (mapv #(select-keys % [:value :phrase]) totals)})

(defn- chip-def [{:keys [kind value]}]
  (some #(when (and (= kind (:kind %)) (= value (:value %))) %) criteria))

(defn- total-def [v] (some #(when (= v (:value %)) %) totals))

(defn- chip [c] (select-keys c [:kind :value]))

(defn normalize
  "A composition as the client sent it, whitelisted: only chips from the
   list, each once, in the order given; only a total from the list."
  [c]
  (let [chips (fn [v] (vec (distinct (for [x (if (sequential? v) v [])
                                           :let [x (chip (if (map? x) x {}))]
                                           :when (chip-def x)]
                                       x))))]
    {:includes (chips (:includes c))
     :excludes (chips (:excludes c))
     :calc {:total (when (total-def (get-in c [:calc :total])) (get-in c [:calc :total]))}}))

(defn- chip-pattern [c period]
  (let [p (:pattern (chip-def c))]
    (if (fn? p) (p period) p)))

(defn- merge-patterns
  "Several chips' patterns as one: every question must come out yes."
  [ps]
  (reduce (fn [a p]
            (merge-with (fn [x y]
                          (cond (and (set? x) (set? y)) (into x y)
                                (and (map? x) (map? y)) (merge x y)
                                :else y))
                        a p))
          {} ps))

(defn describe-composition
  "A composition as a sentence, for the record and for feedback."
  [{:keys [includes excludes calc]}]
  (str "collect events where " (str/join ", " (map #(:phrase (chip-def %)) includes))
       (when (seq excludes) (str ", but not where " (str/join ", " (map #(:phrase (chip-def %)) excludes))))
       "; then total " (or (:phrase (total-def (:total calc))) "?")))

;; ---------------------------------------------------------------------------
;; The reports the lesson asks for
;; ---------------------------------------------------------------------------

(defn- chips [& kvs] (mapv (fn [[k v]] {:kind k :value v}) (partition 2 kvs)))

(def canonical
  "The reports the lesson asks for, as the composition that makes each."
  {:accrual-revenue {:includes (chips "flow" "goods-out" "party" "customer" "when" "period")
                     :excludes [] :calc {:total "consideration"}}
   :cash-revenue    {:includes (chips "flow" "money-in" "party" "customer" "when" "period")
                     :excludes [] :calc {:total "money-in"}}
   :accrual-cogs    {:includes (chips "flow" "goods-out" "party" "customer" "when" "period")
                     :excludes [] :calc {:total "goods-cost"}}
   :cash-cogs       {:includes (chips "flow" "goods-out" "party" "customer" "when" "period")
                     :excludes (chips "batch" "unpaid") :calc {:total "goods-cost"}}})

(def report-names
  {:accrual-revenue "Accrual revenue" :cash-revenue "Cash revenue"
   :accrual-cogs "Cost of goods sold (accrual)" :cash-cogs "Cost of goods sold (cash)"})

(def report-categories
  "What kind of figure each report is, for the recorded report."
  {:accrual-revenue "revenue" :cash-revenue "revenue" :accrual-cogs "expense" :cash-cogs "expense"})

(def gross-margins
  "Each gross profit, as the two reports it takes the difference of."
  {:accrual-gross-margin [:accrual-revenue :accrual-cogs]
   :cash-gross-margin    [:cash-revenue :cash-cogs]})

;; ---------------------------------------------------------------------------
;; Collecting
;; ---------------------------------------------------------------------------
;; A context is a record to report over: its events (assertion maps with
;; identifiers), the company asserting them, the reporting period, and
;; optionally a store already built from them.

(def ^:private store-for
  "One engine store per fixed record: the record never changes."
  (memoize (fn [record-key] (engine/store-of (record-for-engine record-key)))))

(defn record-context
  "A fixed record as something to report over."
  [record-key]
  (when-let [{:keys [company events period]} (get records record-key)]
    {:events events :company company :period period :store (store-for record-key)}))

(defn- store-of-context [{:keys [store company events]}]
  (or store (engine/store-of (events-for-engine company events))))

(defn- summaries-of
  "Every event in a context as a line, by id, with its readings beside it."
  [{:keys [events]}]
  (let [rs (readings/readings-of events)]
    (into {} (for [e events
                   :let [id (some-> (:has-identifier e) name)]
                   :when id]
               [id (assoc (event-summary e) :readings (get rs id))]))))

(defn- total-of [store kept {:keys [reading quantity]}]
  (cond
    reading (let [vals (filter number? (keep #(get-in % [:event :event/readings reading]) kept))]
              (when (seq vals) (reduce + vals)))
    quantity (let [ids (mapv (comp :event/id :event) kept)]
               (when (seq ids)
                 (get-in (store/aggregate-quantity store ids quantity :sum) [:result :value])))))

(defn collect
  "Run a composition over a context: the figure, and the events it
   collected. With nothing to collect by it collects nothing -- a report
   that says nothing about which events is not a report of everything.
   -> {:figure n-or-nil :count n :collected [ids]}"
  [ctx composition]
  (let [{:keys [includes excludes calc]} (normalize composition)]
    (if (empty? includes)
      {:figure nil :count 0 :collected []}
      (let [store    (store-of-context ctx)
            period   (:period ctx)
            inc-pat  (merge-patterns (map #(chip-pattern % period) includes))
            ex-preds (map #(pattern/compile-pattern (chip-pattern % period)) excludes)
            found    (collects/collect-events store {:includes inc-pat})
            kept     (vec (remove (fn [ewa] (some #(% ewa) ex-preds)) found))
            td       (total-def (:total calc))]
        {:figure    (when td (total-of store kept td))
         :count     (count kept)
         :collected (vec (sort (map (comp :event/id :event) kept)))}))))

(defn preview
  "Run a composition over a fixed record, free: refining a report against
   what it collects is the lesson."
  [record-key composition]
  (some-> (record-context record-key) (collect composition)))

(defn report-figure
  "What one of the lesson's reports comes to, composed the right way."
  [record-key report]
  (some->> (get canonical report) (preview record-key) :figure))

;; ---------------------------------------------------------------------------
;; Grading: by what was collected, then by what was totalled
;; ---------------------------------------------------------------------------
;; The student's selection and the right one are both run over the
;; record. The same events and the same total is right, however it was
;; composed. A different selection is explained by its difference: each
;; event let in that should not be, or left out that belongs, is named,
;; with the question that decides it and how that question comes out
;; for that event -- so the feedback names the student's own choice.

(defn- holds?
  "Does this chip's question come out yes for this event?"
  [chip ewa period]
  ((pattern/compile-pattern (chip-pattern chip period)) ewa))

(defn- fact
  "How a chip's question comes out for one event, in words."
  [{:keys [kind]} {:keys [says counterparty date readings]}]
  (case kind
    "flow"  (str "it says: " says)
    "party" (cond
              (nil? counterparty) "there is no other party to it"
              (role-phrase (:counterparty-role readings))
              (str "the other party, " counterparty ", is " (role-phrase (:counterparty-role readings)))
              :else (str "the other party, " counterparty ", is none of these to the business"))
    "when"  (str "it is dated " date ", outside the reporting year")
    "batch" (cond
              (nil? (:batch-paid readings)) "no goods went out in it, so there is no batch to ask about"
              (:batch-paid readings) "the batch those goods came out of had been paid for by the end of the record"
              :else "the batch those goods came out of had not been paid for by the end of the record")
    ""))

(defn- extra-message
  "An event the student's report collects and the right one does not."
  [name id summary ewa want period]
  (let [failed (concat (remove #(holds? % ewa period) (:includes want))
                       (filter #(holds? % ewa period) (:excludes want)))
        c      (first failed)]
    (str id " is in your report, and " name " leaves it out"
         (if c (str ": " (fact c summary) ".") "."))))

(defn- missing-message
  "An event the right report collects and the student's does not."
  [name id summary ewa have period]
  (let [failed (concat (remove #(holds? % ewa period) (:includes have))
                       (filter #(holds? % ewa period) (:excludes have)))
        c      (first failed)]
    (str id " belongs in " name ", and your report leaves it out"
         (if c
           (str ": “" (:phrase (chip-def c)) "” does not hold for it — " (fact c summary) ".")
           "."))))

(defn grade
  "Compare a composition with the report it is meant to be, over a
   context. -> {:correct? bool :figure n :messages [s ...]}"
  [report composition ctx]
  (when-let [want (get canonical report)]
    (let [name    (get report-names report)
          have    (normalize composition)
          mine    (collect ctx have)
          right   (collect ctx want)
          S       (set (:collected mine))
          C       (set (:collected right))
          extras  (sort (set/difference S C))
          missing (sort (set/difference C S))
          store   (store-of-context ctx)
          ewa-of  (fn [id] (first (collects/collect-events store {:includes {:custom (fn [e] (= id (get-in e [:event :event/id])))}})))
          sums    (summaries-of ctx)
          period  (:period ctx)
          same-calc? (= (get-in have [:calc :total]) (get-in want [:calc :total]))
          correct? (and (seq S) (= S C) same-calc?)
          msgs
          (cond
            (empty? (:includes have))
            ["Nothing to collect by yet: add what the events must say to be collected."]

            (and (= S C) (not same-calc?))
            [(str "Your report collects the right events. It totals "
                  (or (:phrase (total-def (get-in have [:calc :total]))) "nothing yet") "; "
                  name " totals " (:phrase (total-def (get-in want [:calc :total]))) ".")]

            :else
            (vec (concat
                   (for [id (take 3 extras)] (extra-message name id (get sums id) (ewa-of id) want period))
                   (when (> (count extras) 3) [(str "…and " (- (count extras) 3) " more that " name " leaves out.")])
                   (for [id (take 3 missing)] (missing-message name id (get sums id) (ewa-of id) have period))
                   (when (> (count missing) 3) [(str "…and " (- (count missing) 3) " more that belong in " name ".")])
                   (when (and (= S C) (not same-calc?)) []))))]
      {:correct? (boolean correct?)
       :figure   (:figure mine)
       :count    (:count mine)
       :collected (:collected mine)
       :messages (if correct? [] msgs)})))

(defn grade-composition
  "A composition against one of the lesson's reports, over a fixed record."
  [record-key report composition]
  (some->> (record-context record-key) (grade report composition)))

(defn grade-gross-margin
  "A gross profit is right when it takes the right revenue less the right
   cost, in that order."
  [margin [first-report second-report]]
  (when-let [[rev cogs] (get gross-margins margin)]
    (let [ok-rev  (= first-report rev)
          ok-cost (= second-report cogs)]
      {:correct? (and ok-rev ok-cost)
       :messages (vec (remove nil?
                        [(when-not ok-rev
                           (if (= first-report cogs)
                             "Revenue comes first: gross profit is revenue less the cost of what was sold."
                             "Use the revenue report on the same basis as the cost."))
                         (when-not ok-cost
                           "Take away the cost of goods sold on the same basis as the revenue.")]))})))

;; ---------------------------------------------------------------------------
;; Reports in the record
;; ---------------------------------------------------------------------------
;; A report is an event: dated at the period's end, saying what it
;; collected and what it came to. The company's own reports are in the
;; fixed record to be read; the student's, once composed right, are kept
;; (recorded-report, simulation.clj) and shown beside them.

(defn report-line
  "A report as a line in the record, like an event's."
  [{:keys [id key date by composition figure count collected]}]
  {:id id :key (name key) :name (get report-names (keyword key)) :date date :by by
   :report true
   :composition composition :figure figure :count count :collected collected
   :says (str "reports " (get report-names (keyword key)) ": "
              (if figure (format "$%,.2f" (double figure)) "nothing")
              " — " (describe-composition composition))})

(defn company-reports
  "The reports a fixed record already holds, run over it."
  [record-key]
  (when-let [{:keys [company period reports]} (get records record-key)]
    (vec (for [k reports
               :let [c (get canonical k)
                     r (preview record-key c)]]
           (report-line {:id (str "Report-" (name k)) :key k :date (:to period) :by company
                         :composition c :figure (:figure r) :count (:count r) :collected (:collected r)})))))

(defn recorded-report
  "What is kept when a student's report is right: the event it is, and
   what it collected and came to."
  [user-id record-key task composition {:keys [figure count collected]}]
  (let [{:keys [period]} (get records record-key)
        c (normalize composition)]
    {:opts {:event-id (str "Report-" user-id "-" (name record-key) "-" (name task))
            :date (:to period) :asserted-by (str user-id)
            :lesson "reporting" :record (name record-key) :task (name task)}
     :event {:has-identifier (str "Report-" (name task))
             :has-date {:date (:to period)}
             :reports {:category (get report-categories task) :basis "composed" :amount figure
                       :collects (select-keys c [:includes :excludes]) :calc (:calc c)}}
     :composition c
     :result (when figure {:value figure :unit {:unit-type :monetary :unit :USD}})
     :count count
     :input-ids collected}))

(defn student-reports
  "A student's recorded reports over this record, as lines."
  [record-key recorded]
  (vec (for [{:keys [opts composition result count input-ids]} recorded
             :when (and (= "reporting" (:lesson opts)) (= (name record-key) (:record opts)))]
         (report-line {:id (str "Report-" (:task opts)) :key (keyword (:task opts)) :date (:date opts) :by "you"
                       :composition composition :figure (:value result) :count count :collected input-ids}))))

(defn record-view
  "What the student sees of a fixed record: its events, its reports and
   theirs, and the words a report is composed from."
  ([record-key] (record-view record-key []))
  ([record-key recorded]
   (when-let [{:keys [company blurb period events]} (get records record-key)]
     (merge {:company company :blurb blurb :period period
             :events (mapv event-summary events)
             :reports (into (company-reports record-key) (student-reports record-key recorded))}
            (vocabulary)))))
