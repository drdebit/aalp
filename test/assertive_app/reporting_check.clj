(ns assertive-app.reporting-check
  "The fourth oracle: the Reporting lesson's figures over Harbor Line's
   year, and what the grader says about a few compositions. Run after
   anything that touches readings, costs or reporting.clj:

     DATOMIC_DB_PASSWORD=dummy clojure -M:test -e \\
       \"(require 'assertive-app.reporting-check) (assertive-app.reporting-check/report)\""
  (:require [assertive-app.reporting :as r]
            [assertive-app.balance-conformance :as bc]))

(defn- chips [& kvs] (mapv (fn [[k v]] {:kind k :value v}) (partition 2 kvs)))

(def ^:private expected
  "LESSON-REPORTING-DESIGN.org: accrual 2,160 / 1,180; cash 2,110 / 640."
  {:accrual-revenue 2160 :cash-revenue 2110 :accrual-cogs 1180 :cash-cogs 640
   ;; Goods in at cost 800 + 750 + 600; goods out at cost to date is 2026's
   ;; 1,180 plus Sale-000's 50 shirts at $4. Their difference, 770, is what
   ;; the batch-by-batch valuation says is on the shelf.
   :goods-received 2150 :goods-sold-to-date 1380})

(defn- check [label ok?]
  (println (if ok? "  ok   " "  FAIL ") label)
  ok?)

(defn report []
  (println "Harbor Line Shirts, 2026:")
  (let [figures (every? identity
                        (for [[k want] expected
                              :let [have (r/report-figure :harbor-line k)]]
                          (check (str (name k) " = " have) (and have (== have want)))))
        g (fn [task c] (r/grade-composition :harbor-line task c))
        cases
        [(check "a paid-for include equals the unpaid exclude (by extension)"
                (:correct? (g :cash-cogs {:includes (chips "when" "period" "batch" "paid" "flow" "goods-out" "party" "customer")
                                          :calc {:total "goods-cost"}})))
         (check "money in for accrual revenue names the credit sales it misses"
                (let [{:keys [correct? messages]} (g :accrual-revenue {:includes (chips "flow" "money-in" "party" "customer" "when" "period")
                                                                        :calc {:total "money-in"}})]
                  (and (not correct?) (some #(re-find #"Sale-004 belongs" %) messages))))
         (check "no period names last year's sale"
                (let [{:keys [correct? messages]} (g :accrual-revenue {:includes (chips "flow" "goods-out" "party" "customer")
                                                                        :calc {:total "consideration"}})]
                  (and (not correct?) (some #(re-find #"Sale-000 is in your report" %) messages))))
         (check "right events, wrong total"
                (let [{:keys [correct? messages]} (g :accrual-cogs {:includes (chips "flow" "goods-out" "party" "customer" "when" "period")
                                                                     :calc {:total "consideration"}})]
                  (and (not correct?) (some #(re-find #"collects the right events" %) messages))))
         (check "nothing to collect by"
                (not (:correct? (g :accrual-cogs {:includes [] :calc {}}))))
         (check "the company's two reports are in the record"
                (= ["Report-accrual-revenue" "Report-cash-revenue"] (mapv :id (r/company-reports :harbor-line))))
         (check "ending inventory agrees with the batch-by-batch valuation"
                (let [diff (- (r/report-figure :harbor-line :goods-received)
                              (r/report-figure :harbor-line :goods-sold-to-date))
                      on-hand (reduce + 0M (vals (bc/valued-on-hand (:events (get r/records :harbor-line)))))]
                  (println "       difference" diff " on hand" on-hand)
                  (== diff on-hand)))
         (check "ending inventory the wrong way round is told so"
                (let [{:keys [correct? messages]} (r/grade-difference :ending-inventory [:goods-sold-to-date :goods-received])]
                  (and (not correct?) (some #(re-find #"comes first" %) messages))))
         (check "ending inventory built right"
                (:correct? (r/grade-difference :ending-inventory [:goods-received :goods-sold-to-date])))]]
    (println (if (and figures (every? identity cases)) "ALL AGREE" "DISAGREEMENTS -- see above"))
    :done))
