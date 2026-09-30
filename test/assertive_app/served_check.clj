(ns assertive-app.served-check
  "The third oracle: does every problem the drill can serve answer itself?

   The two conformance oracles check the rulebook against the
   classifications and the balances against the readings. Neither looks
   at the problems as served -- with their generated record and
   variables -- and that is where six Level 2 problems were found
   deriving nothing from their own correct answer (2026-09-29).

   For a sample of generated problems, the correct answer must derive:
     - lines at all, none of them '(not yet classified)' or '(which…)',
       balanced unless a lot is still to be named;
     - the same amounts whether or not the problem's variables are sent,
       i.e. the answer prices itself rather than borrowing the answer's
       amount (the live panel stopped sending variables on 2026-09-28).

   Run from the repo root:
     clojure -Sdeps '{:paths [\"src/clj\" \"src/cljs\" \"resources\" \"test\"]}' \\
       -M -e \"(require 'assertive-app.served-check) (assertive-app.served-check/report)\""
  (:require [assertive-app.classification :as c]
            [assertive-app.je-derive :as jd]
            [assertive-app.cost-basis :as cost]))

(defn- lines-of [assertions variables events]
  (mapv (juxt :side :account :amount)
        (:lines (jd/derive-je assertions variables {:events events :cost-basis (cost/cost-basis events)}))))

(defn check-problem
  "Problems with one generated problem's own answer, or nil."
  [p]
  (let [ev   (:prior-events p)
        a    (:correct-assertions p)
        r    (jd/derive-je a {} {:events ev :cost-basis (cost/cost-basis ev)})
        ls   (:lines r)
        side (fn [s] (reduce + 0 (keep #(when (= s (:side %)) (:amount %)) ls)))
        issues (cond-> []
                 (empty? ls) (conj "derives no lines")
                 (some #(re-find #"^\(" (str (:account %))) ls) (conj "an unclassified line")
                 (and (seq ls) (not (some :needs-lot? ls))
                      (> (Math/abs (double (- (side :debit) (side :credit)))) 0.01))
                 (conj "unbalanced")
                 (not= (lines-of a {} ev) (lines-of a (:variables p) ev))
                 (conj "prices differently with the variables sent"))]
    (when (seq issues)
      {:template (:template p) :level (:template-level p) :issues issues
       :lines (mapv (juxt :side :account :amount) ls)})))

(defn check-all
  "Sample problems at the top level and collect every template with an
   issue. -> {:templates-seen n :failures {template {...}}}"
  ([] (check-all 800))
  ([n]
   (let [ps (repeatedly n #(c/generate-problem 9))]
     {:templates-seen (count (distinct (map :template ps)))
      :failures (into (sorted-map) (keep (fn [p] (when-let [f (check-problem p)] [(:template p) f]))) ps)})))

(defn report []
  (let [{:keys [templates-seen failures]} (check-all)]
    (println (format "served problems: %d templates seen, %d with issues" templates-seen (count failures)))
    (doseq [[t {:keys [level issues lines]}] failures]
      (println (format "  L%-2s %-30s %s  %s" (str level) (name t) issues lines)))
    :done))
