(ns assertive-app.je-conformance
  "Conformance of the derivation rulebook against the classification
   templates.

   The 45 hand-written :journal-entry templates are no longer a second
   way to produce an entry -- they are the ORACLE. For each
   classification we synthesise the canonical assertion selection from
   its own :required / :required-parameters spec, run it through the
   single derivation, and compare.

   Three outcomes, and only the first two are acceptable:
     :match   -- the rulebook derives exactly the template's accounts
     :gap     -- the rulebook has no rule yet (a tracked work item)
     :diverge -- the rulebook derives DIFFERENT accounts (a defect)

   A :diverge is what the old two-path design produced silently and is
   the failure this harness exists to make loud."
  (:require [assertive-app.classification :as c]
            [assertive-app.je-derive :as jd]
            [clojure.string :as str]))

(def item-kinds
  "SP's catalogue: what KIND of thing each item is. Firm policy, not a
   student assertion -- the positions raw materials / work in process /
   finished goods are resolved from this plus the chain, never booked."
  (into {} (map (fn [[k v]] [k (:category v)])) c/physical-items))

(def ^:private sample-qty
  "Representative quantities. The monetary and physical figures are
   deliberately DIFFERENT so that any rule spending a physical count as
   money shows up as a wrong amount rather than a coincidence."
  {:monetary 3000 :physical 7})

(defn- template-account-set [class-key]
  (let [je (get-in c/classifications [class-key :journal-entry])
        je (cond (map? je) [je] (sequential? je) (filter map? je) :else [])]
    (set (remove nil? (mapcat (juxt :debit :credit) je)))))

(defn- representative-item
  "The classification specs say only \"physical-unit\"; the rulebook keys
   on WHICH physical thing it is (that is the point -- SP's rulebook
   decides the account from the item). Infer a representative item from
   the template's own accounts so the comparison is apples to apples."
  [class-key]
  (let [t (template-account-set class-key)]
    (cond
      (t "Equipment (Fixed Asset)")   "t-shirt-printer"
      (t "Equipment")                 "t-shirt-printer"
      (t "Raw Materials Inventory")   "blank-tshirts"
      (or (t "Finished Goods Inventory") (t "Revenue")) "printed-tshirts"
      :else                           "blank-tshirts")))

(defn canonical-selection
  "Synthesise a student's selection for a classification from its own
   spec: every required assertion, carrying its required parameters, a
   representative quantity in the right denomination, and -- for
   physical flows -- a representative item."
  [class-key]
  (let [{:keys [required required-parameters]} (get c/classifications class-key)
        item (representative-item class-key)]
    (into {}
          (for [a required]
            [a (let [params (get required-parameters a {})
                     params (into {} (remove (fn [[_ v]] (= :any v)) params))
                     unit (:unit params)]
                 (cond-> params
                   (= "monetary-unit" unit) (assoc :quantity (:monetary sample-qty))
                   ;; A transformation must take one thing and make
                   ;; another. Handing consumes and creates the SAME item
                   ;; would describe a thing that turns into itself, which
                   ;; the position resolver reads (correctly) as work in
                   ;; process -- an artefact of the generator, not of the
                   ;; rulebook. So inputs and outputs get distinct items.
                   (= "physical-unit" unit)
                   (-> (assoc :quantity (:physical sample-qty))
                       (update :physical-item
                               #(or % (case a
                                        :consumes "blank-tshirts"
                                        :creates  "printed-tshirts"
                                        item))))
                   (= :has-date a)          (assoc :date "2026-01-15")
                   (= :has-counterparty a)  (assoc :party "Acme Co")))]))))

(def account-aliases
  "Account names are a TRANSLATION of assertions into the double-entry
   vocabulary -- a label chosen at report time, not data. The same
   underlying position may be labelled \"Equipment\" or
   \"Equipment (Fixed Asset)\" with no difference to what was asserted,
   and a student could rename it in a report without changing a thing.

   So conformance compares canonical labels: a naming difference is not
   a disagreement about the entry. It IS worth knowing about, though --
   two labels for one position would open two ledger accounts -- so the
   aliases are listed explicitly here rather than normalised away by
   fuzzy matching."
  ;; Finished Goods / Raw Materials were aliased here until the templates
  ;; were canonicalised. That was real drift, not a rename, and it is gone.
  {"Equipment"     "Equipment (Fixed Asset)"
   "Wages Expense" "Wage Expense"})

(def ^:private adjusting-aliases
  "An accrued wage and an accrued interest are the same position with
   different words; so are a prepaid insurance and a prepaid rent. The
   rulebook names the position; the templates name the instance."
  {"Wages Expense" "Accrued Expense" "Wage Expense" "Accrued Expense" "Interest Expense" "Accrued Expense"
   "Wages Payable" "Accrued Liabilities" "Interest Payable" "Accrued Liabilities"
   "Prepaid Insurance" "Prepaid Expense" "Prepaid Rent" "Prepaid Expense"
   "Deferred Revenue (Liability)" "Unearned Revenue"
   ;; The residual claim under a different entity form: the same position,
   ;; a corporation's word for it.
   "Common Stock" "Owner's Capital"})

(defn canonical-account [a]
  (let [a (get account-aliases a a)]
    (get adjusting-aliases a a)))

(defn- template-accounts [class-key]
  (let [je (get-in c/classifications [class-key :journal-entry])
        je (cond (map? je) [je] (sequential? je) (filter map? je) :else [])]
    (set (map canonical-account (remove nil? (mapcat (juxt :debit :credit) je))))))

(defn- derived-accounts [entry]
  (set (map (comp canonical-account :account) (:lines entry))))

(def prior-chain
  "The paragraph a sentence arrives in.

   Classification is not always a property of the event in front of you:
   blank shirts are an input because SP holds a printer that consumes
   them, and that fact lives in an earlier event. Checking a
   classification against a single synthesised event would ask it to be
   self-sufficient in a way the model deliberately is not, so every check
   runs against a business that has already said what it can do."
  [{:has-date {:date "2026-01-02"}
    :provides {:unit "monetary-unit" :quantity 3000}
    :receives {:unit "physical-unit" :physical-item "t-shirt-printer" :quantity 1}
    :has-counterparty {:name "EquipmentDirect"}
    :allows {:consumes-items ["blank-tshirts" "ink-cartridges"] :creates-item "printed-tshirts"}}])

(defn check
  "Conformance result for one classification."
  [class-key]
  (let [sel   (canonical-selection class-key)
        entry (jd/derive-entry sel {} {:item-kinds item-kinds :events prior-chain})
        tmpl  (template-accounts class-key)
        drv   (derived-accounts entry)
        extra  (remove tmpl drv)
        status (cond (not (:covered? entry))   :gap      ;; no rule fires yet
                     (= tmpl drv)              :match
                     (seq extra)               :conflict ;; derives an account the template does not name
                     :else                     :partial)];; derives a subset -- rules still owed
    {:classification class-key
     :level    (get-in c/classifications [class-key :level])
     :status   status
     :template (vec (sort tmpl))
     :derived  (vec (sort drv))
     :missing  (vec (sort (remove drv tmpl)))
     :extra    (vec (sort (remove tmpl drv)))
     :amount   (:amount entry)
     :balanced? (:balanced? entry)
     :unresolved (mapv :account (:unresolved entry))}))

(defn check-all []
  (mapv check (sort (keys c/classifications))))

;; --- the invariants that make the original bug unrepresentable -----------

(defn amount-violations
  "Any line whose amount is not the transaction's monetary amount.
   This is the direct guard against banking a physical count."
  [results]
  (for [{:keys [classification amount]} results
        :when (and amount (not= amount (:monetary sample-qty)))]
    {:classification classification :amount amount
     :expected (:monetary sample-qty)}))

(defn conflicts  [results] (filter #(= :conflict (:status %)) results))
(defn partials   [results] (filter #(= :partial  (:status %)) results))
(defn gaps       [results] (filter #(= :gap (:status %)) results))
(defn matches    [results] (filter #(= :match (:status %)) results))

(defn report []
  (let [rs (check-all)]
    (println (format "conformance: %d match  %d partial  %d gap  %d CONFLICT  (of %d)"
                     (count (matches rs)) (count (partials rs)) (count (gaps rs))
                     (count (conflicts rs)) (count rs)))
    (println)
    (when (seq (conflicts rs))
      (println "CONFLICTS -- rulebook derives an account the template does not name (review):")
      (doseq [r (conflicts rs)]
        (println (format "  %-28s L%-3s template=%s\n%34sderived  =%s"
                         (name (:classification r)) (str (:level r))
                         (:template r) "" (:derived r))))
      (println))
    (when (seq (partials rs))
      (println "PARTIAL -- some lines derive, rules still owed for the rest:")
      (doseq [r (partials rs)]
        (println (format "  %-28s L%-3s missing %s"
                         (name (:classification r)) (str (:level r)) (:missing r))))
      (println))
    (let [v (amount-violations rs)]
      (println (format "amount violations (physical count spent as money): %d" (count v)))
      (doseq [x v] (println "   !" (:classification x) "->" (:amount x)))
      (println))
    (println "GAPS -- no rule yet, the build-out queue (by level):")
    (doseq [[lvl g] (sort-by (comp str key) (group-by :level (gaps rs)))]
      (println (format "  L%-3s %s" (str lvl)
                       (str/join ", " (map (comp name :classification) g)))))
    (println)
    (println "MATCHES:" (str/join ", " (map (comp name :classification) (matches rs))))
    :done))
