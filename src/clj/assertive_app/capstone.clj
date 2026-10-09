(ns assertive-app.capstone
  "The capstone's own year: one company's 2026, recorded by the student,
   reviewed and corrected, then reported on (LESSON-REPORTING-DESIGN.org).

   The same year for everyone, coherent by construction -- every sale draws
   on a batch that was really bought, every payment keeps a promise that
   was really made. What differs between students is only what they
   recorded. Each transaction is read against the student's own books so
   far, so a mistake early in the year is still there when a later entry
   is made, and when the year is reported on.

   Entries are kept as graded attempts (problem type capstone, one problem
   id per transaction); the latest attempt for a transaction is the entry
   in the books, so correcting one is recording it again."
  (:require [assertive-app.schema :as schema]
            [assertive-app.classification :as classification]
            [assertive-app.je-derive :as jd]
            [assertive-app.cost-basis :as cost]
            [assertive-app.readings :as readings]
            [assertive-app.reporting :as reporting]
            [assertive-app.engine :as engine]
            [assertive-engine.compute.collects :as collects]
            [clojure.edn :as edn]
            [datomic.api :as d]))

;; ---------------------------------------------------------------------------
;; The year
;; ---------------------------------------------------------------------------
;; Campus Threads buys blank shirts wholesale and sells them on. Built, like
;; Harbor Line's year, so the accrual and tax cash bases differ for three
;; reasons: last year's credit sale collected in January, a credit sale not
;; collected by year end, and a batch bought on credit and not yet paid for.
;; A cleaning service and an owner's draw are money out that is not the
;; cost of goods.
;;
;; With every entry right -- accrual: revenue 2,870, cost of goods sold
;; 1,420, gross profit 1,450. Cash: revenue 2,350, cost 940, margin 1,410.

(def company "Campus Threads")

(def blurb "Campus Threads buys plain blank t-shirts wholesale and sells them on to clubs and teams, as they are. It prints nothing and owns no press. This is its 2026, and you are keeping its books.")

(def ^:private merchandise
  {:action "provides" :unit "physical-unit" :physical-item "blank-tshirts" :confidence 95})

(def opening
  "What the books already hold when the student starts: given, not recorded."
  [{:has-identifier "Funding-000" :has-date {:date "2025-11-01"}
    :receives {:unit "monetary-unit" :quantity 8000}
    :provides {:unit "ownership-units" :quantity 100}
    :has-counterparty {:name "the owner"}}
   {:has-identifier "Lot-A" :has-date {:date "2025-11-10"}
    :provides {:unit "monetary-unit" :quantity 600}
    :receives {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 150}
    :expects merchandise
    :has-counterparty {:name "TextileDirect"}}
   {:has-identifier "Sale-P" :has-date {:date "2025-12-12"}
    :provides {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 40 :from-event "Lot-A"}
    :requires {:action "receives" :unit "monetary-unit" :quantity 360 :due-date "2026-01-11"}
    :expects {:action "receives" :unit "monetary-unit" :confidence 90}
    :has-counterparty {:name "Westside Soccer"}}])

(def transactions
  "The year, in order: what happened, and the entry that says it."
  [{:id "T01" :date "2026-01-09" :classification :receivable-collection
    :narrative "On January 9, Westside Soccer pays the $360 it owed for the shirts it bought in December."
    :answer {:has-date {:date "2026-01-09"} :has-counterparty {:name "Westside Soccer"}
             :receives {:unit "monetary-unit" :quantity 360}
             :fulfills {:action "requires" :event "Sale-P/requires"}}}
   {:id "T02" :date "2026-01-20" :classification :merchandise-purchase-on-credit
    :narrative "On January 20, Campus Threads takes delivery of 200 blank t-shirts from PrintSupplyCo, to sell on as they are, and agrees to pay $1,000 by February 19."
    :answer {:has-date {:date "2026-01-20"} :has-counterparty {:name "PrintSupplyCo"}
             :receives {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 200}
             :requires {:action "provides" :unit "monetary-unit" :quantity 1000 :due-date "2026-02-19"}
             :expects merchandise}}
   {:id "T03" :date "2026-02-05" :classification :cash-sale
    :narrative "On February 5, Campus Threads sells 60 blank t-shirts from the November batch (Lot-A) to the debate team for $540 cash."
    :answer {:has-date {:date "2026-02-05"} :has-counterparty {:name "the debate team"}
             :provides {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 60 :from-event "Lot-A"}
             :receives {:unit "monetary-unit" :quantity 540}}}
   {:id "T04" :date "2026-02-19" :classification :payable-payment
    :narrative "On February 19, Campus Threads pays PrintSupplyCo the $1,000 it owes for January's shirts."
    :answer {:has-date {:date "2026-02-19"} :has-counterparty {:name "PrintSupplyCo"}
             :provides {:unit "monetary-unit" :quantity 1000}
             :fulfills {:action "requires" :event "T02/requires"}}}
   {:id "T05" :date "2026-03-15" :classification :sale-on-credit
    :narrative "On March 15, Campus Threads sells 100 of January's shirts (T02) to Lakeside Rowing Club for $950, to be paid by April 14. It is 90% sure they will pay."
    :answer {:has-date {:date "2026-03-15"} :has-counterparty {:name "Lakeside Rowing Club"}
             :provides {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 100 :from-event "T02"}
             :requires {:action "receives" :unit "monetary-unit" :quantity 950 :due-date "2026-04-14"}
             :expects {:action "receives" :unit "monetary-unit" :confidence 90}}}
   {:id "T06" :date "2026-04-10" :classification :receivable-collection
    :narrative "On April 10, Lakeside Rowing Club pays the $950 it owes."
    :answer {:has-date {:date "2026-04-10"} :has-counterparty {:name "Lakeside Rowing Club"}
             :receives {:unit "monetary-unit" :quantity 950}
             :fulfills {:action "requires" :event "T05/requires"}}}
   {:id "T07" :date "2026-05-01" :classification :service-purchase
    :narrative "On May 1, Campus Threads pays CleanSweep $250 to deep-clean the stockroom."
    :answer {:has-date {:date "2026-05-01"} :has-counterparty {:name "CleanSweep"}
             :provides {:unit "monetary-unit" :quantity 250}
             :receives {:unit "service-unit" :quantity 1}}}
   {:id "T08" :date "2026-08-03" :classification :merchandise-purchase-on-credit
    :narrative "On August 3, Campus Threads takes delivery of 120 blank t-shirts from TextileDirect, to sell on, and agrees to pay $720 by January 31 next year."
    :answer {:has-date {:date "2026-08-03"} :has-counterparty {:name "TextileDirect"}
             :receives {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 120}
             :requires {:action "provides" :unit "monetary-unit" :quantity 720 :due-date "2027-01-31"}
             :expects merchandise}}
   {:id "T09" :date "2026-09-12" :classification :cash-sale
    :narrative "On September 12, Campus Threads sells the last 50 shirts of the November batch (Lot-A) to the art club for $500 cash."
    :answer {:has-date {:date "2026-09-12"} :has-counterparty {:name "the art club"}
             :provides {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 50 :from-event "Lot-A"}
             :receives {:unit "monetary-unit" :quantity 500}}}
   {:id "T10" :date "2026-10-20" :classification :sale-on-credit
    :narrative "On October 20, Campus Threads sells 80 of August's shirts (T08) to Harbor Youth League for $880, to be paid by January 19 next year. It is 85% sure they will pay."
    :answer {:has-date {:date "2026-10-20"} :has-counterparty {:name "Harbor Youth League"}
             :provides {:unit "physical-unit" :physical-item "blank-tshirts" :quantity 80 :from-event "T08"}
             :requires {:action "receives" :unit "monetary-unit" :quantity 880 :due-date "2027-01-19"}
             :expects {:action "receives" :unit "monetary-unit" :confidence 85}}}
   {:id "T11" :date "2026-12-10" :classification :owner-withdrawal
    :narrative "On December 10, the owner takes $1,500 out of the business for personal use, giving up that much of their ownership interest."
    :answer {:has-date {:date "2026-12-10"} :has-counterparty {:name "the owner"}
             :provides {:unit "monetary-unit" :quantity 1500}
             :receives {:unit "ownership-units"}}}])

(def ^:private by-id (into {} (map (juxt :id identity)) transactions))

(def period {:from "2026-01-01" :to "2026-12-31"})

;; ---------------------------------------------------------------------------
;; The student's entries
;; ---------------------------------------------------------------------------

(defn- problem-id [tx-id] (str "capstone-" tx-id))

(defn entries
  "The student's entry for each transaction they have recorded: the latest
   attempt. -> {tx-id {:assertions {...} :at inst :corrected? bool}}"
  [user-id]
  (let [rows (d/q '[:find ?pid ?dt ?sel
                    :in $ ?user
                    :where
                    [?a :attempt/user ?user]
                    [?a :attempt/problem-type :capstone]
                    [?a :attempt/problem-id ?pid]
                    [?a :attempt/datetime ?dt]
                    [?a :attempt/selected-assertions ?sel]]
                  (schema/db) user-id)]
    (into {}
          (for [[pid rs] (group-by first rows)
                :let [sorted (sort-by second rs)
                      [_ at sel] (last sorted)
                      tx-id (subs pid (count "capstone-"))]
                :when (contains? by-id tx-id)]
            [tx-id {:assertions (try (edn/read-string sel) (catch Exception _ {}))
                    :at at
                    :corrected? (> (count sorted) 1)}]))))

(defn- numbered
  "An entry's figures as numbers. The sentence's inputs put a quantity in
   as the text typed, and the correct books hold numbers, so a report
   that adds the two up -- money in, over the student's books -- fell
   over on the first one. The engine reads what is stored, so this is
   where the text becomes a number."
  [assertions]
  (into {}
        (for [[k v] assertions]
          [k (if (map? v)
               (into {}
                     (for [[pk pv] v]
                       [pk (if (and (#{:quantity :amount :confidence} pk) (string? pv))
                             (let [n (try (Double/parseDouble pv) (catch Exception _ nil))]
                               (cond (nil? n) pv
                                     (== n (Math/rint n)) (long n)
                                     :else n))
                             pv)]))
               v)])))

(defn- as-event [tx-id assertions]
  (assoc (numbered assertions) :has-identifier tx-id))

(defn books
  "The student's books: the opening, then each entry they recorded, in the
   order of the year. `upto` stops before that transaction -- the books as
   they stood when it was recorded."
  ([user-entries] (books user-entries nil))
  ([user-entries upto]
   (into (vec opening)
         (for [{:keys [id]} (take-while #(not= upto (:id %)) transactions)
               :let [e (get-in user-entries [id :assertions])]
               :when (seq e)]
           (as-event id e)))))

(def correct-books
  "The books with every entry right."
  (into (vec opening) (map #(as-event (:id %) (:answer %)) transactions)))

(defn- correct-books-upto [tx-id]
  (into (vec opening) (map #(as-event (:id %) (:answer %))
                           (take-while #(not= tx-id (:id %)) transactions))))

;; ---------------------------------------------------------------------------
;; Does an entry say what the transaction says?
;; ---------------------------------------------------------------------------
;; Judged against the right books up to that point, so each entry is judged
;; on its own: a payment that keeps the right promise is right even if the
;; student mis-recorded the promise, and that mistake shows at the promise.

(defn- lines-of [assertions events]
  (->> (:lines (jd/derive-je assertions {} {:events events :cost-basis (cost/cost-basis events)}))
       (map (fn [l] [(name (:side l)) (:account l)
                     (some-> (:amount l) double Math/round)]))
       set))

(defn- classify [tx assertions events]
  (classification/classify-transaction
    assertions
    :correct-classification (:classification tx)
    :context {:events events :cost-basis (cost/cost-basis events)}))

(defn judge
  "Does this entry say what the transaction says? -> {:matches? bool
   :hints [...]}. Matching means the entry derives the same lines, at the
   same amounts, as the right one."
  [tx-id assertions]
  (let [tx     (by-id tx-id)
        events (correct-books-upto tx-id)
        same?  (= (lines-of assertions events) (lines-of (:answer tx) events))
        r      (classify tx assertions events)
        hints  (get-in r [:feedback :hints])]
    {:matches? same?
     :hints (when-not same?
              (if (and (= :correct (get-in r [:feedback :status])) (empty? hints))
                ;; The right kind of entry, with something in it that is not
                ;; what happened: a figure, a date, or which batch the goods
                ;; came out of. The classification cannot see those; the
                ;; lines they produce can.
                ["The kind of entry is right. Check what is in it against the transaction: the amounts, and which batch the goods came out of."]
                hints))}))

;; ---------------------------------------------------------------------------
;; State for the client
;; ---------------------------------------------------------------------------

(defn state
  "Everything the capstone's round needs: the year, the student's entries
   and, for each, whether it says what the transaction says."
  [user-id]
  (let [es (entries user-id)]
    {:company company :blurb blurb :period period
     :opening (mapv reporting/event-summary opening)
     ;; The books as the student has kept them, as lines to read.
     :books (mapv reporting/event-summary (books es))
     :transactions
     (vec (for [{:keys [id date narrative]} transactions
                :let [e (get es id)]]
            (cond-> {:id id :date date :narrative narrative
                     :prior-events (books es id)}
              e (assoc :entry (:assertions e)
                       :corrected? (:corrected? e)
                       :matches? (:matches? (judge id (:assertions e)))))))}))

;; ---------------------------------------------------------------------------
;; Reports over the student's books
;; ---------------------------------------------------------------------------

(defn- store-of-books [events]
  (let [rs (readings/readings-of events)]
    (engine/store-of
      (vec (for [e events
                 :let [id (:has-identifier e)]]
             {:event-id id :assertions (dissoc e :has-identifier)
              :date (get-in e [:has-date :date]) :asserted-by company
              :counterparty (get-in e [:has-counterparty :name])
              :readings (get rs id)})))))

(defn- figure [events composition]
  (let [{:keys [spec aggregate]} (reporting/composition->spec period composition)]
    (when (and aggregate (seq (:includes spec)))
      (let [r (collects/collect-and-aggregate (store-of-books events) spec aggregate :sum)]
        {:figure (get-in r [:result :value])
         :count (:count r)
         :collected (vec (sort (map (comp :event/id :event) (:events r))))}))))

(defn preview
  "A composition over the student's own books."
  [user-id composition]
  (figure (books (entries user-id)) composition))

(defn compare-report
  "One of the lesson's reports over the student's books and over the right
   ones, and which of the student's entries the difference comes from:
   those that differ from the right entry and were collected by either.
   When the figures agree, a wrong entry the report collected is named as
   :unaffected -- wrong, but not in anything this report totals."
  [user-id report]
  (when-let [c (get reporting/canonical report)]
    (let [es (entries user-id)
          mine (figure (books es) c)
          right (figure correct-books c)
          touched (into (set (:collected mine)) (:collected right))
          culprits (vec (for [{:keys [id]} transactions
                              :let [e (get-in es [id :assertions])]
                              :when (and (contains? touched id)
                                         (or (nil? e) (not (:matches? (judge id e)))))]
                          id))]
      (if (= (some-> (:figure mine) double) (some-> (:figure right) double))
        {:yours (:figure mine) :right (:figure right) :entries [] :unaffected culprits}
        {:yours (:figure mine) :right (:figure right) :entries culprits}))))
