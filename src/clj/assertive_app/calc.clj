(ns assertive-app.calc
  "The arithmetic behind a `reports` assertion, in one place.

   An adjusting entry's amount is not asserted, it is worked out — and
   the working is part of the record, not a step that happens off to one
   side and hands back a number. The research model records a
   calculation as an event with its own provenance for exactly this
   reason: a figure you cannot reconstruct is a figure nobody can audit.

   So the same functions serve three callers. The calculation builder
   uses them to produce the figure; the student's `reports` assertion
   carries the inputs that produced it; and the derivation uses them
   again to check that the figure on the entry is the one those inputs
   imply. Two implementations of one formula would let the check pass
   against the wrong arithmetic, which is worse than no check.")

(defn- n [x] (cond (number? x) (double x)
                   (string? x) (try (Double/parseDouble (clojure.string/trim x))
                                    (catch Exception _ nil))
                   :else nil))

(defn systematic-allocation
  "(Cost − Salvage) ÷ Life ÷ periods per year. Depreciation, and
   amortisation, which is the same arithmetic on a thing you cannot
   touch."
  [{:keys [asset-cost salvage-value useful-life periods-per-year]}]
  (when-let [c (n asset-cost)]
    (when-let [life (n useful-life)]
      (when (pos? life)
        (let [per-year (or (n periods-per-year) 12.0)
              annual (/ (- c (or (n salvage-value) 0.0)) life)]
          (when (pos? per-year)
            {:value (/ annual per-year) :annual-value annual}))))))

(defn time-based
  "(Original ÷ Total periods) × periods elapsed. A prepayment used up."
  [{:keys [original-amount total-periods periods-elapsed]}]
  (when-let [amt (n original-amount)]
    (when-let [total (n total-periods)]
      (when (pos? total)
        (let [each (/ amt total)]
          {:value (* each (or (n periods-elapsed) 0.0)) :period-amount each})))))

(defn accrual
  "Principal × rate × time. Interest, earned or owed."
  [{:keys [principal annual-rate time-fraction]}]
  (when-let [p (n principal)]
    (when-let [r (n annual-rate)]
      {:value (* p (/ r 100.0) (or (n time-fraction) 0.0))})))

(defn percent-of-sales
  "Credit sales × bad-debt %. An income-statement method: it asks what
   share of THIS period's sales will not arrive, and adds that to the
   allowance without looking at what is already in it."
  [{:keys [credit-sales bad-debt-percent]}]
  (when-let [s (n credit-sales)]
    (when-let [p (n bad-debt-percent)]
      {:value (* s (/ p 100.0))})))

(defn estimation
  "Σ (amount × (1 − confidence)). The allowance read off the confidences
   somebody recorded when each sale was made."
  [receivables]
  (let [priced (mapv (fn [r]
                       (let [amt (or (n (:amount r)) 0.0)
                             conf (or (n (:confidence r)) 100.0)]
                         (assoc r :amount amt
                                  :expected-loss (* amt (/ (- 100.0 conf) 100.0))
                                  :non-collection-rate (- 100.0 conf))))
                     receivables)]
    {:value (reduce + 0.0 (map :expected-loss priced))
     :receivables priced
     :total-receivables (reduce + 0.0 (map :amount priced))}))

(defn aging
  "Σ (each age class × its rate) — the allowance the schedule calls for.

   The expense is that less whatever is already in the allowance, which
   is the step most often got backwards, so both are returned and named."
  [rows rates existing]
  (let [priced (mapv (fn [{:keys [key total] :as row}]
                       (let [rate (or (n (get rates key))
                                      (n (get rates (name key)))
                                      0.0)]
                         (assoc row :rate rate
                                    :estimated (* (or (n total) 0.0) (/ rate 100.0)))))
                     rows)
        required (reduce + 0.0 (map :estimated priced))]
    {:value (- required (or (n existing) 0.0))
     :required required
     :existing (or (n existing) 0.0)
     :buckets priced
     :total-receivables (reduce + 0.0 (map (comp #(or (n %) 0.0) :total) priced))}))

(defn result
  "The figure a basis and a set of inputs come to, for the bases whose
   inputs are all typed in. The two that read the record -- estimation
   and aging -- take their data as an argument and are called directly."
  [basis inputs]
  (case (some-> basis name)
    "systematic-allocation" (systematic-allocation inputs)
    "time-based"            (time-based inputs)
    "accrual"               (accrual inputs)
    "percent-of-sales"      (percent-of-sales inputs)
    nil))
