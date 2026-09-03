(ns assertive-app.episodes
  "The walkthrough: SP's business, in the order it happens.

   The teaching spine is the business, not the vocabulary, because the
   record already enforces the order -- you cannot provide money you have
   not raised, hold inventory with nothing to use it for, produce without
   materials, or sell what you have not made.

   A step is say / do / then, mirroring the rhythm of a notebook cell:
   a sentence setting up one action, the action, and a sentence reading
   back what appeared. Most steps ask for exactly one assertion. Some ask
   for nothing at all and only say something -- the walkthrough is not an
   examination, and the drill is where mastery is checked.

   :palette is what the student may add at that point. Assertions arrive
   when the business needs them rather than all at once.")

;; ---------------------------------------------------------------------------
;; Step completion
;; ---------------------------------------------------------------------------

(defn- has-params?
  [selected code params]
  (when-let [sel (get selected (keyword code))]
    (every? (fn [[k v]] (= (get sel k) v)) params)))

(defn step-complete?
  "Has the student done what this step asked? Steps with no :do are
   complete as soon as they are read."
  [step selected]
  (let [{:keys [kind code params]} (:do step)]
    (case kind
      nil        true
      :read      true
      :set-date  (contains? selected :has-date)
      :assert    (boolean (has-params? selected code (or params {})))
      true)))

;; ---------------------------------------------------------------------------
;; The episodes
;; ---------------------------------------------------------------------------

(def episodes
  [{:id :funding
    :title "The business is funded"
    :palette #{:has-date :receives :provides :has-counterparty}
    :steps
    [{:say "SP the person and SP's T-Shirts the business are two different things. The business has its own money, owes its own debts, and keeps its own books."
      :then "That separation is why any of this works. If they were the same, \"SP puts money in\" would just be somebody moving money between their own pockets."}

     {:say "Here's the part to hold on to: you are keeping the business's books."
      :then "Every assertion you make is the business saying what happened to it. When the business receives $20,000, that's money arriving — even though the person who handed it over is $20,000 poorer."}

     {:say "Today the business is funded. SP puts in $20,000. Let's record it. Every event starts with when it happened."
      :do {:kind :set-date}
      :then "Good. That's an assertion — a plain statement about the world. True, but on its own it doesn't say much."}

     {:say "The business received $20,000. Say so."
      :do {:kind :assert :code :receives :params {:unit "monetary-unit"}}
      :then "Two lines appeared at once. Cash on the debit side — money arriving is a debit. And Owner's Capital on the credit side. Why that one? Money came in and nothing went out with it. The business gave up no goods and took on no debt. What's left is a claim by whoever put the money in. That's what equity is — not a kind of transaction, but the part left over."}

     {:say "The business didn't get that money for nothing. SP received 200 ownership units in return — the certificate, the stake, the thing that says how much of the business is theirs. Say that too."
      :do {:kind :assert :code :provides :params {:unit "ownership-units"}}
      :then "Now look carefully. Nothing changed. The entry is exactly what it was."}

     {:say "Scroll down to \"Recorded — but not reflected\"."
      :do {:kind :read}
      :then "There they are. Double-entry measures in money, and 200 units is a count, not an amount — so no line carries it. That's the monetary unit assumption, and it's what keeps every entry addable and comparable. The units stay in your record, and we work out percentages from them later. The entry is doing exactly what it should; you're simply keeping track of more than it measures."}

     {:say "One more: who."
      :do {:kind :assert :code :has-counterparty}
      :then "The entry didn't change again. Counterparty doesn't get a line of its own — it tells you which account fits, without ever appearing on one. Later, when a second person invests, this is what keeps them apart."}]}

   {:id :printer
    :title "SP buys a printer"
    :palette #{:has-date :provides :receives :has-counterparty :allows}
    :steps
    [{:say "The business has $20,000. Today it spends $3,000 on a t-shirt printer. Start with when."
      :do {:kind :set-date}}

     {:say "The business paid $3,000. Say so."
      :do {:kind :assert :code :provides :params {:unit "monetary-unit"}}
      :then "Cash again, but on the credit side this time. Money leaving is a credit. You didn't pick that — it followed from what you said."}

     {:say "Something has to balance it. What did the business get?"
      :do {:kind :assert :code :receives :params {:unit "physical-unit"}}
      :then "Hmm. Still not finished. The record knows the business got a printer. It doesn't know what to call it."}

     {:say "Here's the interesting part. A printer isn't automatically equipment. A shop that resells printers would call the very same machine inventory. So the record has to say what this one is for."
      :then "Nothing to do yet — just worth knowing before the next step."}

     {:say "SP bought it to turn blank t-shirts into printed ones. Say that."
      :do {:kind :assert :code :allows}
      :then "There it is. Equipment. Not because printers are equipment — because you said this one is productive."}

     {:say "Who did the business buy it from? The shop was PrinterWorld."
      :do {:kind :assert :code :has-counterparty}
      :then "Good. It balances, and every line came from something you said."}

     {:say "Try something. Open the entry's Explore control and switch `allows` off."
      :do {:kind :read}
      :then "Equipment disappears. The record stops saying what the machine is for, so it stops knowing what to call it. Switch it back on."}

     {:say "Tomorrow the business buys blank t-shirts."
      :then "Because of what you said today, the record will already know what they're for."}]}

   {:id :materials
    :title "SP buys shirts and ink"
    :palette #{:has-date :provides :receives :has-counterparty}
    :steps
    [{:say "Blank t-shirts, $100 for twenty. Same shape as yesterday: when, what went out, what came in, and who."
      :do {:kind :set-date}}

     {:say "The business paid $100."
      :do {:kind :assert :code :provides :params {:unit "monetary-unit"}}}

     {:say "And received twenty blank t-shirts."
      :do {:kind :assert :code :receives :params {:unit "physical-unit"}}
      :then "Raw Materials Inventory. Notice you didn't say that — you never told the record these were materials."}

     {:say "So where did it come from? Open that line and look under \"Decided earlier\"."
      :do {:kind :read}
      :then "There's yesterday. The shirts are an input because you said the printer turns blank t-shirts into printed ones. The reason for today's entry was written down yesterday."}

     {:say "Finish it off: who sold them? TextileDirect."
      :do {:kind :assert :code :has-counterparty}
      :then "Done. And you learned no new assertions today — the same four you already had produced an account you hadn't seen."}]}

   ;; Ink gets its own entry because an episode builds one event, and the
   ;; printing that follows consumes ink as well as shirts. Without this
   ;; the record refuses the production event: you cannot use what you
   ;; never bought. Doing it twice also makes the point that raw
   ;; materials is about the role, not about shirts.
   {:id :ink
    :title "SP buys ink"
    :palette #{:has-date :provides :receives :has-counterparty}
    :steps
    [{:say "Ink next — two cartridges for $20. You know the shape by now: when, what went out, what came in, who."
      :do {:kind :set-date}}

     {:say "The business paid $20."
      :do {:kind :assert :code :provides :params {:unit "monetary-unit"}}}

     {:say "And received two ink cartridges."
      :do {:kind :assert :code :receives :params {:unit "physical-unit"}}
      :then "Hmm. \"Not yet classified.\" Yesterday the shirts landed in Raw Materials because the record knew what they were for. Nobody has said what ink is for — the printer's capability only mentions shirts. The record won't guess. It will know the day the ink gets used."}

     {:say "And who sold it — InkMasters."
      :do {:kind :assert :code :has-counterparty}
      :then "Now the business has everything it needs to print."}]}

   {:id :production
    :title "SP prints shirts"
    :palette #{:has-date :consumes :creates :is-allowed-by}
    :steps
    [{:say "Today nothing is bought or sold. The business turns ten blank shirts and one ink cartridge into ten printed shirts. When did it happen?"
      :do {:kind :set-date}}

     {:say "Ten blank shirts were used up. Say so."
      :do {:kind :assert :code :consumes}
      :then "Raw Materials Inventory, on the credit side — the shirts left. And look at the amount: $50. You didn't type that. It's what the business paid for those ten shirts."}

     {:say "Ten printed shirts now exist that didn't before."
      :do {:kind :assert :code :creates}
      :then "Finished Goods Inventory. The new shirts are worth what went into them."}

     {:say "One more. What made this possible?"
      :do {:kind :assert :code :is-allowed-by}
      :then "The printer. Notice there's no counterparty on this entry at all — nobody else was involved. Nothing entered or left the business; something inside it changed form."}]}

   {:id :sale
    :title "SP sells shirts"
    :palette #{:has-date :provides :receives :has-counterparty}
    :steps
    [{:say "A customer buys four printed shirts for $100. When?"
      :do {:kind :set-date}}

     {:say "Four shirts left the business."
      :do {:kind :assert :code :provides :params {:unit "physical-unit"}}}

     {:say "And $100 came in."
      :do {:kind :assert :code :receives :params {:unit "monetary-unit"}}}

     {:say "Say who bought them."
      :do {:kind :assert :code :has-counterparty}
      :then "Four lines now. Cash and Revenue — and notice you never asserted \"revenue\". It appeared because goods went out to somebody in exchange for money, and that pattern is what revenue means. You have been building up to this entry since episode one."}

     {:say "Look at the other two lines."
      :do {:kind :read}
      :then "Cost of Goods Sold, and Finished Goods going back down — priced at what those shirts cost to make, which the record worked out from the day you printed them. No new assertions again. Same vocabulary, new arrangement, two accounts you hadn't met."}]}])

(def assertion-level
  "The level whose assertion vocabulary the walkthrough needs.

   The palette decides what a student is OFFERED at each step, but it can
   only narrow what is available, and availability is keyed to the level
   the student has reached. The walkthrough is a tour of the whole first
   year -- it reaches production by episode four -- so it needs the
   vocabulary of the last episode from the start, and lets the palette do
   the pedagogical narrowing."
  2)

(defn episode [idx] (get episodes idx))
(defn episode-count [] (count episodes))
(defn step [ep-idx st-idx] (get-in episodes [ep-idx :steps st-idx]))
(defn step-count [ep-idx] (count (:steps (episode ep-idx))))
