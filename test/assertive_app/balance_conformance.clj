(ns assertive-app.balance-conformance
  "Do the entries and the readings agree?

   Two independent paths lead from the same record to the same business.
   One posts every event's derived journal entry and totals the accounts.
   The other reads the chain: `cash-on-hand`, `on-hand` against
   `inventory-position`, `promises-of`. Nothing connects them — the
   rulebook in je_derive.clj and the readers in chain.clj were written
   separately, for different purposes — so when they agree it is
   evidence, and when they disagree one of them is wrong.

   This became worth testing on 2026-09-18, when the simulation's cash,
   inventory and obligations stopped being stored totals and became
   readings. Before that a disagreement showed up as a display oddity;
   now the reading IS the business state, and the trial balance is the
   only other opinion in the building.

   Sibling to je_conformance, which asks a narrower question: whether
   one event's entry names the accounts its classification says it
   should. This asks whether a whole record adds up."
  (:require [assertive-app.chain :as chain]
            [assertive-app.cost-basis :as cost]
            [assertive-app.classification :as c]
            [assertive-app.je-derive :as jd]
            [assertive-app.simulation :as sim]
            [clojure.string :as str]))

(def ^:private cash-accounts #{"Cash"})
(def ^:private payable-accounts #{"Accounts Payable"})
(def ^:private receivable-accounts #{"Accounts Receivable"})

(defn trial-balance
  "Post every event in a record and total the accounts.

   Each event is derived in the context of the ones before it, which is
   how it was derived when it happened: a cost of goods sold is priced
   from the lots that existed at the time."
  [events]
  (reduce
    (fn [acc [i ev]]
      (let [prior (vec (take i events))
            ;; The same context the live derivation builds
            ;; (simulation/derivation-context-for): without the cost
            ;; basis every cost line comes out unpriced and silently
            ;; drops out of the totals, which would make this oracle
            ;; agree with itself by omission.
            entry (jd/derive-je ev {} {:events prior
                                       :current ev
                                       :item-kinds sim/item-kinds
                                       :cost-basis (cost/cost-basis prior)})]
        (reduce (fn [acc {:keys [side account amount]}]
                  (if (and account (number? amount))
                    (update acc account (fnil + 0M)
                            (cond-> (bigdec amount) (= :credit side) -))
                    acc))
                acc
                (:lines entry))))
    {}
    (map-indexed vector events)))

(defn- total [balances accounts]
  (reduce + 0M (for [[a v] balances :when (accounts a)] v)))

(defn- readings
  "The same business, read off the chain."
  [events]
  (let [held (chain/on-hand events)
        at   (fn [pos] (for [[item n] held
                             :when (and (pos? n) (= pos (chain/inventory-position events item)))]
                         [item n]))
        owed (fn [kind] (reduce + 0M (for [p (chain/promises-of events kind)]
                                       (bigdec (or (:amount p) 0)))))]
    {:cash (bigdec (chain/cash-on-hand events))
     :payable (owed :payable)
     :receivable (owed :receivable)
     :raw-materials (into {} (at :raw-materials))
     :finished-goods (into {} (at :finished-goods))}))

(defn check
  "Compare the two opinions of one record.

   -> {:agree? bool :rows [{:figure :entries :readings :ok?}]}"
  [events]
  (let [bal (trial-balance events)
        r   (readings events)
        rows [{:figure "Cash"                :entries (total bal cash-accounts)       :readings (:cash r)}
              {:figure "Accounts Payable"    :entries (- (total bal payable-accounts)) :readings (:payable r)}
              {:figure "Accounts Receivable" :entries (total bal receivable-accounts)  :readings (:receivable r)}]
        rows (mapv #(assoc % :ok? (zero? (- (:entries %) (:readings %)))) rows)
        ;; And the books' own question, asked of the whole record rather
        ;; than one entry: do the debits equal the credits? A zero here
        ;; is the trial balance balancing. It will not be zero while the
        ;; build-out queue has gaps -- a note payable that derives its
        ;; cash debit and not its credit leaves the difference behind --
        ;; so this reports rather than asserts, and the number IS the
        ;; size of what is still owed to the rulebook.
        ;; To the cent. A weighted-average cost basis divides, so a
        ;; balanced book can land on 0E-15 rather than 0, and calling
        ;; that unbalanced would cry wolf at arithmetic rather than at
        ;; a missing rule.
        residual (let [r (reduce + 0M (vals bal))]
                   (if (< (abs (double r)) 0.005) 0M r))]
    {:agree? (every? :ok? rows)
     :rows rows
     :residual residual
     :balances bal
     :units (select-keys r [:raw-materials :finished-goods])}))

(defn- fixture
  "A practice company's record: funded, a press, materials, two printing
   runs, four sales on credit, a prepayment, an advance and a loan --
   plus a purchase on account, which the backstories do not make and
   which is the only way to put anything in Accounts Payable. Without it
   that row compares nothing against nothing."
  [kind]
  (conj (:events (c/practice-backstory
                   (first (filter #(= kind (:kind %)) c/practice-companies))
                   #{:receivable :prepaid :advance :borrowing}))
        {:has-identifier "Ink-002"
         :has-date {:date "2026-03-02"}
         :receives {:unit "physical-unit" :physical-item "ink-cartridges" :quantity 8}
         :requires {:action "provides" :unit "monetary-unit"
                    :quantity 200 :due-date "2026-04-01"}
         :has-counterparty {:name "InkMasters"}}))

(defn report []
  (doseq [kind [:printer :reseller]]
    (let [events (fixture kind)
          {:keys [agree? rows units residual balances]} (check events)]
      (println (format "\n%s record — %d events — %s"
                       (name kind) (count events)
                       (if agree? "entries and readings AGREE" "DISAGREEMENT")))
      (doseq [{:keys [figure entries readings ok?]} rows]
        (println (format "  %-22s entries %12s   readings %12s   %s"
                         figure (str entries) (str readings) (if ok? "ok" "<-- differ"))))
      (println "  on hand (units, not money):" (pr-str units))
      (println (format "  trial balance residual %s%s"
                       (str residual)
                       (if (zero? residual)
                         "  (debits = credits)"
                         "  <-- unbalanced: lines the rulebook does not derive yet")))
      (when-not (zero? residual)
        (doseq [[a v] (sort-by (comp - abs second) balances)
                :when (not (zero? v))]
          (println (format "      %-34s %12s" a (str v)))))))
  :done)
