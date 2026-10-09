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

(defn record-for-engine
  "A fixed record as engine-ready events, each carrying its readings.
   Every event is asserted by the company, so a composition over it is
   scoped to the company rather than to the student viewing it."
  [record-key]
  (when-let [{:keys [company events]} (get records record-key)]
    (let [rs (readings/readings-of events)]
      (vec (for [e events
                 :let [id (:has-identifier e)]]
             {:event-id     id
              :assertions   (dissoc e :has-identifier)
              :date         (get-in e [:has-date :date])
              :asserted-by  company
              :counterparty (get-in e [:has-counterparty :name])
              :readings     (get rs id)})))))

;; ---------------------------------------------------------------------------
;; Compositions
;; ---------------------------------------------------------------------------
;; What a student builds, in parts. Each part is one choice, which is what
;; makes grading by composition possible: every part can be compared with
;; the right one, and said about.
;;
;;   :flow   which events: goods out, money in, money out, goods in
;;   :party  who the other party is to the business, or anyone
;;   :paid   only goods from batches that had been paid for
;;   :period the reporting year, or every date in the record
;;   :total  what is added up

(def ^:private flows
  {"goods-out" {:assertion-types #{:provides} :provides-unit :physical}
   "money-in"  {:assertion-types #{:receives} :receives-unit :monetary}
   "money-out" {:assertion-types #{:provides} :provides-unit :monetary}
   "goods-in"  {:assertion-types #{:receives} :receives-unit :physical}})

(def ^:private parties #{"customer" "supplier" "owner" "lender" "borrower"})

(def ^:private totals
  {"consideration" :reading/consideration
   "money-in"      :receives
   "money-out"     :provides
   "goods-cost"    :reading/goods-cost})

(defn composition->spec
  "A student's composition, whitelisted, as an engine collects-spec and
   aggregate over one fixed record. Nothing arrives but choices from these
   lists; anything else is dropped."
  [record-or-period {:keys [flow party paid period total]}]
  (let [{:keys [from to]} (if (map? record-or-period)
                            record-or-period
                            (get-in records [record-or-period :period]))]
    {:spec {:includes (cond-> (get flows flow {})
                        (contains? parties party)
                        (assoc-in [:readings :counterparty-role] (keyword party))
                        (true? paid)
                        (assoc-in [:readings :batch-paid] true)
                        (= "year" period)
                        (assoc :date-from from :date-to to))}
     :aggregate (get totals total)}))

(def canonical
  "The reports the lesson asks for, as the composition that makes each."
  {:accrual-revenue {:flow "goods-out" :party "customer" :period "year" :total "consideration"}
   :cash-revenue    {:flow "money-in"  :party "customer" :period "year" :total "money-in"}
   :accrual-cogs    {:flow "goods-out" :party "customer" :period "year" :total "goods-cost"}
   :cash-cogs       {:flow "goods-out" :party "customer" :paid true :period "year" :total "goods-cost"}})

(def gross-margins
  "Each gross profit, as the two reports it takes the difference of."
  {:accrual-gross-margin [:accrual-revenue :accrual-cogs]
   :cash-gross-margin    [:cash-revenue :cash-cogs]})

;; ---------------------------------------------------------------------------
;; Grading, part by part
;; ---------------------------------------------------------------------------
;; The figure can be wrong for reasons the composition is not, and right
;; by accident when it is -- so the composition is what is graded, and
;; each part is said about on its own.

(def ^:private part-order [:flow :party :paid :period :total])

(defn- part-feedback
  "What to say about a part that is not what the report needs."
  [report part]
  (let [cost? (#{:accrual-cogs :cash-cogs} report)
        cash? (#{:cash-revenue :cash-cogs} report)]
    (case part
      :flow   (cond cost? "Cost of goods sold is about goods that went out: collect events where the business provides goods."
                    cash? "Cash revenue is money coming in: collect events where the business receives money."
                    :else "Accrual revenue is earned by providing the goods: collect events where the business provides goods.")
      :party  "Only customers: money and goods also move between the business and its suppliers and its owner, and none of that is a sale."
      :paid   (if (= report :cash-cogs)
                "Under the tax cash method a cost counts only once the goods are paid for: keep only goods from batches that had been paid for."
                "On the accrual basis the cost of goods sold counts when the goods go out, paid for or not: leave the paid-for condition off.")
      :period "The report is for the year: limit it to the reporting period, or last year's events come in too."
      :total  (cond cost? "Total what the goods cost — not what the customer paid for them."
                    cash? "Total the money received."
                    :else "Total what was received or promised for the goods: a credit sale is revenue at the price agreed, before any money arrives."))))

(defn- normalize [c]
  {:flow (:flow c) :party (when (contains? parties (:party c)) (:party c))
   :paid (true? (:paid c)) :period (if (= "year" (:period c)) "year" "all")
   :total (:total c)})

(defn grade-composition
  "Compare a composition with the one the report needs, part by part.
   -> {:correct? bool :parts [{:part k :ok? bool :message s}]}"
  [report composition]
  (when-let [want (get canonical report)]
    (let [have (normalize composition)
          want (normalize want)
          parts (vec (for [p part-order
                           :let [ok? (= (get have p) (get want p))]]
                       (cond-> {:part p :ok? ok?}
                         (not ok?) (assoc :message (part-feedback report p)))))]
      {:correct? (every? :ok? parts) :parts parts})))

(defn grade-gross-margin
  "A gross profit is right when it takes the right revenue less the right
   cost, in that order."
  [margin [first-report second-report]]
  (when-let [[rev cogs] (get gross-margins margin)]
    (let [ok-rev  (= first-report rev)
          ok-cost (= second-report cogs)]
      {:correct? (and ok-rev ok-cost)
       :parts [(cond-> {:part :revenue :ok? ok-rev}
                 (not ok-rev) (assoc :message (if (= first-report cogs)
                                                "Revenue comes first: gross profit is revenue less the cost of what was sold."
                                                "Use the revenue report on the same basis as the cost.")))
               (cond-> {:part :cost :ok? ok-cost}
                 (not ok-cost) (assoc :message "Take away the cost of goods sold on the same basis as the revenue."))]})))

;; ---------------------------------------------------------------------------
;; Preview
;; ---------------------------------------------------------------------------

(def ^:private store-for
  "One engine store per fixed record: the record never changes."
  (memoize (fn [record-key] (engine/store-of (record-for-engine record-key)))))

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

(defn record-view
  "What the student sees of a fixed record."
  [record-key]
  (when-let [{:keys [company blurb period events]} (get records record-key)]
    {:company company :blurb blurb :period period
     :events (mapv event-summary events)}))

(defn preview
  "Run a composition over a fixed record: the figure, and the events it
   collected. Free: refining a report against what it collects is the
   lesson."
  [record-key composition]
  (let [{:keys [spec aggregate]} (composition->spec record-key composition)]
    (when (and (get records record-key) aggregate (seq (:includes spec)))
      (let [r (collects/collect-and-aggregate (store-for record-key) spec aggregate :sum)]
        {:figure (get-in r [:result :value])
         :count (:count r)
         :collected (vec (sort (map (comp :event/id :event) (:events r))))}))))

(defn report-figure
  "What one of the lesson's reports comes to, composed the right way."
  [record-key report]
  (some->> (get canonical report) (preview record-key) :figure))
