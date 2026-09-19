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

   `scope` decides which record each event is read against:

     :as-posted    only the events before it — what the student saw at
                   the time, and what their stored journal entry says
     :as-read-now  the whole record — what the assertions say today

   The two differ, and the difference is the framework working rather
   than a fault. A shop's first purchase of blank shirts posts to
   `not yet classified`: at that moment nothing has said what blank
   shirts are FOR, and the system declines to guess. Once the shop sells
   some, the same purchase reads as finished goods — the position was
   never asserted, so it answers correctly at every date, and a date
   later than the purchase is a date that knows more.

   Cost is priced from the events before, in both scopes. What a thing
   costs is settled when it moves; only what it IS stays open."
  [events scope]
  (reduce
    (fn [acc [i ev]]
      (let [prior (vec (take i events))
            entry (jd/derive-je ev {} {:events (if (= :as-posted scope) prior events)
                                       :current ev
                                       :item-kinds sim/item-kinds
                                       ;; Without a cost basis every cost line
                                       ;; comes out unpriced and drops silently
                                       ;; out of the totals — the oracle would
                                       ;; agree with itself by omission.
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

(defn- fmt [n]
  (let [d (double n)]
    (if (== d (Math/rint d)) (str (long d)) (format "%.2f" d))))

(defn- total [balances accounts]
  (reduce + 0M (for [[a v] balances :when (accounts a)] v)))

(defn valued-on-hand
  "What the record says the goods still on hand cost, by position.

   The counting half of a reading is easy to check and proves little:
   `on-hand` says 55 blank shirts and so does anyone. The question worth
   asking is whether the money agrees — whether the Raw Materials
   balance the entries built up equals the cost of the shirts the chain
   says are still there.

   Valued batch by batch at what each batch cost, which is how an
   outflow is priced when it names its lot. If outflows were priced one
   way and the remainder is worth another, the two sides part company
   and this is where it shows."
  [events]
  (let [basis (cost/cost-basis events)]
    (reduce (fn [acc item]
              (let [pos (chain/inventory-position events item)
                    v   (reduce + 0M (for [b (chain/batches events item)
                                           :when (pos? (:left b))]
                                       (bigdec (or (cost/cost-of basis item (:left b) (:id b)) 0))))]
                (cond-> acc (and pos (pos? v)) (update pos (fnil + 0M) v))))
            {}
            (keys (chain/on-hand events)))))

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
     :valued (valued-on-hand events)
     :raw-materials (into {} (at :raw-materials))
     :finished-goods (into {} (at :finished-goods))}))

(defn check
  "Compare the two opinions of one record.

   -> {:agree? bool :rows [{:figure :entries :readings :ok?}]}"
  [events]
  (let [bal  (trial-balance events :as-read-now)
        then (trial-balance events :as-posted)
        r    (readings events)
        ;; The money positions, then the goods -- valued, not counted.
        held (fn [pos] {:figure (chain/position-accounts pos)
                        :entries (get bal (chain/position-accounts pos) 0M)
                        :readings (get (:valued r) pos 0M)})
        rows (into [{:figure "Cash"                :entries (total bal cash-accounts)        :readings (:cash r)}
                    {:figure "Accounts Payable"    :entries (- (total bal payable-accounts)) :readings (:payable r)}
                    {:figure "Accounts Receivable" :entries (total bal receivable-accounts)  :readings (:receivable r)}]
                   (map held [:raw-materials :work-in-process :finished-goods :capital]))
        ;; To the cent: a weighted average divides, and a book out by
        ;; 1e-15 is a book that balances.
        rows (mapv #(assoc % :ok? (< (abs (- (double (:entries %)) (double (:readings %)))) 0.005)) rows)
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
     ;; Accounts whose answer has changed since they were posted. Not
     ;; errors: lines the record has since become able to classify.
     :since-posted (into {} (for [a (distinct (concat (keys bal) (keys then)))
                                  :let [now (get bal a 0M) was (get then a 0M)]
                                  :when (>= (abs (- (double now) (double was))) 0.005)]
                              [a {:as-posted was :as-read-now now}]))
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
          {:keys [agree? rows units residual balances since-posted]} (check events)]
      (println (format "\n%s record — %d events — %s"
                       (name kind) (count events)
                       (if agree? "entries and readings AGREE" "DISAGREEMENT")))
      (doseq [{:keys [figure entries readings ok?]} rows]
        (println (format "  %-26s entries %12s   readings %12s   %s"
                         figure (fmt entries) (fmt readings) (if ok? "ok" "<-- differ"))))
      (println "  on hand, counted:" (pr-str units))
      (println (format "  trial balance residual %s%s"
                       (str residual)
                       (if (zero? residual)
                         "  (debits = credits)"
                         "  <-- unbalanced: lines the rulebook does not derive yet")))
      (when (seq since-posted)
        (println "  reads differently now than when posted:")
        (doseq [[a {:keys [as-posted as-read-now]}] since-posted]
          (println (format "      %-30s was %10s   now %10s" a (fmt as-posted) (fmt as-read-now)))))
      (when-not (zero? residual)
        (doseq [[a v] (sort-by (comp - abs second) balances)
                :when (not (zero? v))]
          (println (format "      %-34s %12s" a (str v)))))))
  :done)
