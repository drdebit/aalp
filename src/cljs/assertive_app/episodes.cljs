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
      ;; Switching an assertion off is the step: the explanation is about
      ;; what disappears, and it makes no sense until it has.
      :remove    (not (contains? selected code))
      :assert    (boolean (has-params? selected code (or params {})))
      true)))

(defn event-id-for
  "The identifier the event built in this episode gets when it joins the
   chain: the episode's id, unless a :new-event step inside the episode
   started a second one, in which case the latest such step names it."
  [ep]
  (name (or (some :event-id (:steps ep)) (:id ep))))

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

     {:say "It's January 1st, 2026, and today the business is funded. SP puts in $20,000. Let's record it. Every event starts with when it happened."
      :do {:kind :set-date}
      :then "Good. That's an assertion — a plain statement about the world. True, but on its own it doesn't say much."}

     {:say "The business received $20,000. Say so."
      :do {:kind :assert :code :receives :params {:unit "monetary-unit"}}
      :then "Two lines appeared at once. Cash, an asset, on the left — the debit side. And Owner's Capital on the right — the credit side. Why that account? Money came in and nothing went out with it. The business gave up no goods and took on no debt. What's left is a claim by whoever put the money in. That's what equity is — not a kind of transaction, but the part left over. As for left and right: that is a convention the rest of the course covers. Assets and expenses live on the left, claims and revenue on the right, and a thing grows on its own side. The platform applies it for you, and every line will say how it did."}

     {:say "The business didn't get that money for nothing. SP received 200 ownership units in return — the certificate, the stake, the thing that says how much of the business is theirs. Say that too."
      :do {:kind :assert :code :provides :params {:unit "ownership-units"}}
      :then "Now look carefully. Nothing changed. The entry is exactly what it was."}

     {:say "Look under the entry, at \"In the chain, not on the entry\". The chain is everything the business has said — the list on the left is it, and it grows with every episode. The entry is what double-entry can measure of the chain, in money."
      :do {:kind :read}
      :then "There they are. 200 units is a count, not an amount of money, so no line can carry it — that's the monetary unit assumption, and it's what keeps every entry addable and comparable. The units stay in the chain, and we work out percentages from them later. The entry is doing exactly what it should; the chain simply holds more than the entry measures."}

     {:say "One more: who."
      :do {:kind :assert :code :has-counterparty}
      :then "The entry didn't change again. Counterparty doesn't get a line of its own — it tells you which account fits, without ever appearing on one. Later, when a second person invests, this is what keeps them apart."}]}

   {:id :printer
    :title "SP buys a printer"
    :palette #{:has-date :provides :receives :has-counterparty :allows}
    :steps
    [{:say "January 2nd. The business has $20,000, and today it spends $3,000 on a t-shirt printer. Start with when."
      :do {:kind :set-date}}

     {:say "The business paid $3,000. Say so."
      :do {:kind :assert :code :provides :params {:unit "monetary-unit"}}
      :then "Cash again, but on the credit side this time. Money leaving is a credit. You didn't pick that — it followed from what you said."}

     {:say "Something has to balance it. What did the business get?"
      :do {:kind :assert :code :receives :params {:unit "physical-unit"}}
      :then "Hmm. Still not finished. The record knows the business got a printer. It doesn't know what to call it."}

     {:say "Here's the interesting part. A printer isn't automatically equipment. A shop that resells printers would call the very same machine inventory. So the record has to say what this one is for."
      :then "Nothing to do yet — just worth knowing before the next step."}

     {:say "SP bought it to turn blank t-shirts and ink into printed ones. Say that — both inputs."
      :do {:kind :assert :code :allows}
      :then "There it is. Equipment. Not because printers are equipment — because you said this one is productive."}

     {:say "Who did the business buy it from? The shop was PrinterWorld."
      :do {:kind :assert :code :has-counterparty}
      :then "Good. It balances, and every line came from something you said."}

     {:say "Try something. Open the entry's Explore control and switch `allows` off."
      :do {:kind :remove :code :allows}
      :then "Equipment disappears. The record stopped saying what the machine is for, so it stopped knowing what to call it."}

     {:say "Now switch it back on."
      :do {:kind :assert :code :allows}
      :then "And it's back. Nothing about the printer changed; what the record says about it did. That is the whole trick, and you will see it again."}

     {:say "One thing to hold on to before moving on. Every account name is an answer to the same question: what is this thing for, and what is left afterwards?"
      :then "Cash is money the business holds — an asset, because it can be put to any future use. The printer is held to make things and is still there afterwards: Equipment. Shirts will be held to be used up making printed ones: Raw Materials. Money spent on a repair buys nothing that lasts: an expense. The record can only name a thing once it knows what it is for, and you are the one who says so."}

     {:say "Next, a design to print — and then blank t-shirts."
      :then "Because of what you said today, the record will already know what the shirts are for."}]}

   ;; The same lesson as the printer, on something with no weight: a
   ;; design is an asset because the record says what it is for and it
   ;; is not used up by that use. Sits between the printer and the
   ;; shirts so `allows` is seen twice in a row on two different things.
   {:id :design
    :title "SP buys a design, and the printer is serviced"
    :palette #{:has-date :provides :receives :has-counterparty :allows}
    :steps
    [{:say "January 3rd. A printer prints something, so today SP pays a designer $400 for a logo to put on the shirts. When?"
      :do {:kind :set-date}}

     {:say "The business paid $400."
      :do {:kind :assert :code :provides :params {:unit "monetary-unit"}}}

     {:say "And received a design — one logo."
      :do {:kind :assert :code :receives :params {:unit "physical-unit"}}
      :then "Not yet classified, again. A design isn't automatically anything. A design studio would sell it on; the record has to hear what SP will do with it."}

     {:say "SP bought it to print on blank shirts, with ink, making printed ones. Same as the printer: say what it allows."
      :do {:kind :assert :code :allows}
      :then "Design (Intangible Asset). Nothing you can drop on your foot — and an asset for exactly the reason the printer was: it makes printed shirts possible, and it is still there after every shirt. Kept for a future use, not used up by it. That is what \"asset\" means."}

     {:say "Who did the business pay? Ada Okafor, the designer."
      :do {:kind :assert :code :has-counterparty}
      :then "Balanced. Two very different things, one reason, one account family."}

     ;; The other answer to "what is left afterwards?": nothing. A second
     ;; event in the same episode, so the contrast sits beside the design
     ;; rather than a whole episode later.
     {:say "Same afternoon, and the contrast. The printer needs a service: a technician from PrinterWorld comes out and SP pays $60. New event — start with when."
      :new-event true
      :event-id :service
      :do {:kind :set-date}}

     {:say "The business paid $60."
      :do {:kind :assert :code :provides :params {:unit "monetary-unit"}}}

     {:say "And received a service — work done for it. Pick \"Service\" as the unit."
      :do {:kind :assert :code :receives :params {:unit "service-unit"}}
      :then "Services Expense, straight away — no \"not yet classified\", no allows to add. A service is used up as it is done. Nothing is left to keep for a future use, so there is nothing to call an asset. That is what an expense is. Money went out for the design and for the servicing; one bought something that stays, one something that doesn't, and the record knows which because you said what each was for."}]}

   {:id :materials
    :title "SP buys shirts and ink"
    :palette #{:has-date :provides :receives :has-counterparty}
    :steps
    [{:say "January 4th. Blank t-shirts, $100 for twenty. Same shape as before: when, what went out, what came in, and who."
      :do {:kind :set-date}}

     {:say "The business paid $100."
      :do {:kind :assert :code :provides :params {:unit "monetary-unit"}}}

     {:say "And received twenty blank t-shirts."
      :do {:kind :assert :code :receives :params {:unit "physical-unit"}}
      :then "Raw Materials Inventory. Notice you didn't say that — you never told the record these were materials."}

     {:say "So where did it come from? Open that line and look under \"Decided earlier\"."
      :do {:kind :read}
      :then "There's January 2nd. The shirts are an input because you said the printer turns blank t-shirts and ink into printed ones. The reason for today's entry was written down two days ago."}

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
    [{:say "Same day, January 4th. Ink next — two cartridges for $20. You know the shape by now: when, what went out, what came in, who."
      :do {:kind :set-date}}

     {:say "The business paid $20."
      :do {:kind :assert :code :provides :params {:unit "monetary-unit"}}}

     {:say "And received two ink cartridges."
      :do {:kind :assert :code :receives :params {:unit "physical-unit"}}
      :then "Raw Materials Inventory again. Not because ink is like a shirt — because when you bought the printer you said it takes ink as well as shirts. Open the line: the reason is the same day's decision."}

     {:say "And who sold it — InkMasters."
      :do {:kind :assert :code :has-counterparty}
      :then "Now the business has everything it needs to print."}]}

   {:id :production
    :title "SP prints shirts"
    :palette #{:has-date :consumes :creates :is-allowed-by}
    :steps
    [{:say "January 5th. Nothing is bought or sold today: the business turns ten blank shirts and one ink cartridge into ten printed shirts. When did it happen?"
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
    [{:say "January 7th. Campus Boutique buys four printed shirts for $100. When?"
      :do {:kind :set-date}}

     {:say "Four shirts left the business."
      :do {:kind :assert :code :provides :params {:unit "physical-unit"}}}

     {:say "And $100 came in."
      :do {:kind :assert :code :receives :params {:unit "monetary-unit"}}}

     {:say "Say who bought them: Campus Boutique."
      :do {:kind :assert :code :has-counterparty}
      :then "Four lines now. Cash and Revenue — and notice you never asserted \"revenue\". It appeared because goods went out to somebody in exchange for money, and that pattern is what revenue means. You have been building up to this entry since episode one."}

     {:say "Look at the other two lines: Cost of Goods Sold, and Finished Goods going back down, $24. You never typed that number. Where did it come from? Which four shirts were these? Look in the chain for shirts the business can sell, and on the provides line pick the batch they came from."
      :do {:kind :assert :code :provides :params {:from-event "production"}}
      :then "Four of the ten printed on January 5th, at $6 each: $24. And the $6 was $5 of blank shirt bought on the 4th and $1 of ink. Open the Cost of Goods Sold line and the batch is named there. Nothing was typed; the cost was carried forward, event by event, from what the business paid. No new assertions again — same vocabulary, new arrangement, two accounts you hadn't met."}

     {:say "That was SP's first week, as a lesson. What comes next is practice, on other people's businesses — each problem is a different company with its own chain, and nothing carries over. SP's own books start after that."
      :then "Same vocabulary throughout. Go and use it."}]}])

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
