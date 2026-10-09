(ns assertive-app.tutorials
  "Level-based tutorial content with reading sections and quiz questions.

   Each level has:
   - :title, :subtitle — display text
   - :sections — vector of {:heading :content} for the tutorial reader
   - :quiz — vector of {:id :question :choices :correct :explanation} for the MC quiz

   Quiz grading is client-side. Server is called once on success to persist completion.

   Tutorial content matches the backend grading templates in classification.clj:
   - L0: Cash purchases and cash sales (provides, receives,
         has-counterparty; expects/allows for what a purchase is for;
         revenue and cost of goods sold)
   - L1: Credit transactions (requires, expects — including credit sales)
   - L2: Production (consumes, creates, is-allowed-by)
   - L4: Legal and regulatory context
   - L5: Adjusting entries (introduces reports — calculated recognitions)
   - L6: Equity transactions
   - L7: Notes and interest
   - L8: Capstone review

   There is no Level 3. It taught cash sales, which the Level 0 drill
   has served since 2026-09-07; its revenue and cost-of-goods-sold
   sections moved into Level 0 on 2026-09-29, so a student meets the
   costing step having been taught it. The numbers are keys, not
   positions: the path is (all-levels), in order.")

;; ==================== Level Tutorials ====================

(def welcome
  "The welcome page: what accounting by assertion is, shown once before
   the first lesson's gate and reopenable from the header. Text agreed
   with Matt 2026-10-06 (WELCOME-PAGE.org)."
  {:heading "Accounting by assertion"
   :body "Every business keeps a record of the events that affect it: what it buys, what it sells, what it owes and is owed. That record becomes the business's books, and keeping those books is what accounting is. In this course you will learn to keep the books.

Most accounting courses teach bookkeeping as a placement of dollars into accounts. When a business buys equipment, debit this and credit that; when it sells on credit, a different set of debits and credits. Each kind of transaction has its own mapping to the accounts.

This platform teaches those mappings too, but in a slightly different way. Instead of affecting the accounts directly, you will declare what is true about the event. The business provided money and received a printer. The event occurred on January 3, and involved another party called PrinterWorld. The business bought the printer to print logos on blank shirts. These declarations are called **assertions** and they often take the form of ordinary words such as *provides* and *receives* to describe who, when, and what the thing is for.

The journal entries derive from these assertions. If the business bought shirts to print, they are classified as raw materials; if they were bought to sell on as they are, the same shirts are finished goods. The account follows from the assertions, based on the generally accepted accounting principles you will encounter everywhere else in financial accounting.

Simple events take a few assertions. Complicated ones take more. Assertions are designed so that you can say as many true things about an event as you like. But only some of these assertions will affect the journal entry."
   :lessons-heading "How the lessons go"
   :lessons "Each lesson opens with a short reading and a few questions, followed by practice problems. You will periodically look back at earlier lessons to refresh your memory. The practice problems belong to small businesses like the one in the readings, each with its own books, and mistakes in practice cost nothing. The course ends with a year of one business's books, which you keep, check, correct and report on yourself."
   :button "Begin"})

(def level-tutorials
  "Tutorial content and quiz questions keyed by level."
  {0
   {:title "Level 0: Cash Transactions"
    :subtitle "Learn the basics of recording business exchanges."
    ;; The gate's front page (CLOSING-ENTRY-STUDY.md §4). Six slots; the
    ;; vocabulary one is computed from what the server actually offers,
    ;; so it cannot drift. Slot 3 is a way of READING the event and must
    ;; not resolve to an account before the assertions are made -- a
    ;; procedure over accounts is the pedagogy this platform exists to
    ;; replace. Slot 6, the minimal pair, is the centerpiece.
    :orientation
    {:framing "Every event in this lesson is a trade. The business says what it gave, what it got, and why it wanted the one in place of the other."
     :protocol ["What went out? — **provides**"
                "What came in? — **receives**"
                "Who was on the other side? — **has-counterparty**"
                "When? — **has-date**"
                "And if something came in: what is it FOR? — **expects**, or **allows** when the thing is a machine"]
     :example
     {:narrative "On January 8, SP purchases 50 blank t-shirts from TextileDirect for $150 cash, to print on."
      :assertions ["has-date: January 8"
                   "has-counterparty: TextileDirect"
                   "provides: $150 (cash)"
                   "receives: 50 blank t-shirts (physical units)"
                   "expects: to use them up making printed t-shirts — 95% sure"]
      :entry ["DR Raw Materials Inventory $150" "CR Cash $150"]}
     :pair
     {:same "SP **provides** money and **receives** something, from a counterparty."
      ;; Equipment first, so the point below follows the two shirt rows
      ;; it is about.
      :a {:when "Receives a t-shirt printer, allows it to print blank shirts into printed ones"
          :becomes "Equipment"}
      :b {:when "Receives 50 blank t-shirts, expects to print on them"
          :becomes "Raw Materials Inventory"}
      :c {:when "Receives 50 blank t-shirts, expects to sell them on as they are"
          :becomes "Merchandise Inventory"}
      :point "Same shirts, same money, same vendor: a different account classification. Asset accounts are chosen according to their anticipated future use."}
     :effect
     {:holds "Before: $10,000 cash. After: $9,850 cash and 50 shirts. The business is no poorer — it swapped one asset for another."
      :may-or-must "Before: nothing. After: the shirts are committed to printing. Nothing in the journal entry records that, and it is the reason they are raw materials rather than stock for sale."}
     :reminder "An account is not a name you look up. It is what falls out of what you said."}
    :sections
    [{:heading "The Story: A T-Shirt Company"
      :content "Welcome! The examples in these lessons follow SP's T-Shirt Company: it buys a printer, stocks up on blank shirts and ink, prints designs, and sells them. The practice problems come from other small shops like it, each with its own books.

Every business keeps a record of what happens — every purchase, every sale. That record is called the company's **books**, and keeping it accurately is what accounting *is*.

Your job is to be the bookkeeper: things happen in a business, and you record them.

Don't worry if you've never done anything like this. We'll go one small step at a time, and you'll get plenty of practice."} 

     {:heading "Recording = Describing What Happened"
      :content "Here's the whole idea in one sentence:

**To record a transaction, you describe what happened in a structured way.**

Think about how you'd tell a friend about buying a coffee this morning:

*\"This morning I paid $5 at Campus Coffee and got a latte.\"*

That one sentence contains four facts:
**When** it happened (this morning);
**Who** the other party was (Campus Coffee);
**What you gave** ($5);
**What you got** (a latte)

That's it. That's a complete record of a transaction. In this platform, each of those four facts is called an **assertion** — a statement about the transaction that is true. You'll record business events by selecting the assertions that describe them."}

     {:heading "Meet the Four Assertions"
      :content "Four assertions describe a simple exchange. Here they are, using the coffee example:

**has-date** — *when* did it happen?
(This morning. In business: the transaction date.)

**has-counterparty** — *who* was the other party?
(Campus Coffee. In business: the vendor you buy from, or the customer you sell to.)

**provides** — *what did you give up?*
(The $5. \"Provides\" always describes what goes OUT of your company.)

**receives** — *what did you get?*
(The latte. \"Receives\" always describes what comes IN to your company.)

::assertions
has-date: this morning
has-counterparty: Campus Coffee
provides: $5 (money going out)
receives: 1 latte (a thing coming in)
::

Every cash purchase you record in this lesson uses these four. When in doubt, ask the friend-telling-the-story questions: when? who? what went out? what came in?

Those four describe the exchange itself. A business usually wants to record more: what the thing is *for*, and what the transaction implies about the *future*. Two more assertions carry that. **allows** states a purpose. A t-shirt printer turns blank shirts into printed ones, and without **allows** the record cannot tell whether the printer is equipment or stock to resell. **expects** states an implication for the future, and it can carry a purpose too: you bought these shirts expecting to print on them. There is no limit on how much an event may assert. A simple transaction just needs fewer assertions than a complicated one."}

     {:heading "Two Kinds of Stuff: Money and Things"
      :content "When you fill in **provides** and **receives**, the platform asks what *kind* of thing moved. In this lesson there are three kinds:

A **monetary-unit**. That is, money. Dollars. The menu calls it **cash**, because that is the one monetary unit this lesson uses.

A **physical-unit**. That is, things: blank t-shirts, ink cartridges, a t-shirt printer. The menu calls them **physical units**.

**A service**. Work done for the business — a technician servicing the printer — used up as it is done, so nothing is left to hold afterward. You will meet one in the practice round.

In a cash purchase, the pattern is always the same:
Your company **provides** money (cash), and
**receives** things (physical units)

You'll also enter *quantities*: the dollar amount for the money, and the quantity of the things."}

     {:heading "What Did You Buy Them For?"
      :content "One more thing, and it is the one that does the most work.

*You buy 100 blank t-shirts for $500.* So does the shop across the road. Same shirts, same money, same supplier — and the two purchases are not the same transaction, because you are going to print on yours and they are going to sell theirs exactly as they are.

Nothing about a shirt tells you which. **You have to say.**

::assertions
has-date: the purchase date
has-counterparty: TextileDirect
provides: $500 (cash)
receives: 100 blank t-shirts (physical units)
expects: to use them up making printed t-shirts — 95% sure
::

That last one is **expects**: a future event, and how sure you are of it. Here it is your own plan, and the plan is what decides the account:

- Going to use them up making something → they are **raw materials**, an input.
- Going to sell them as they are → they are **merchandise**, stock waiting for a buyer.

::journal
DR Raw Materials Inventory $500
CR Cash $500
::

Why 95% and not 100%? Because you might not. The press could break, the order could fall through, you might end up selling the blanks on. Recording how sure you are is recording something true — and you will meet this assertion again in the next lesson, pointed at things other people do."}

     {:heading "Worked Example, Step by Step"
      :content "Let's record one together. Suppose you see this transaction:

*\"On January 8, you purchase 50 blank t-shirts from TextileDirect for $150 cash.\"*

Here is exactly what you'd do, click by click:

**Step 1.** Read the sentence slowly. Find the four facts: the date (January 8), the other party (TextileDirect), what went out ($150), what came in (50 blank t-shirts).

**Step 2.** **Has Date** is already filled in with the date of the transaction. Check that it matches the date in the problem — January 8 here — not today's date.

**Step 3.** Click **Provides**. Choose **cash** (money went out — cash is a monetary-unit), and enter the amount: 150.

**Step 4.** Click **Receives**. Choose **physical units** (things came in), pick the item (blank t-shirts), and enter the quantity: 50.

**Step 5.** Click **Has Counterparty** and enter who: TextileDirect.

**Step 6.** Click **Expects** and say what the shirts are for. You print on shirts, so: *use them up making something* → printed t-shirts. Then set how sure you are — 95% is honest for a plan you fully intend but do not entirely control.

**Step 7.** Read your sentence back at the top of the panel — it should retell the story. Then click **Submit**.

::assertions
has-date: January 8
has-counterparty: TextileDirect
provides: $150 (cash)
receives: 50 blank t-shirts (physical units)
expects: to use them up making printed t-shirts — 95% sure
::

Four of those say what moved. The fifth says what it was for, and it is the one that decides whether the shirts land in Raw Materials or in Merchandise Inventory. Every purchase in this lesson is this same pattern with different details — and where nothing came in, there is nothing to say a purpose for, so **expects** sits out."}

     {:heading "Where the Journal Entry Comes From"
      :content "Double-entry accountants record a transaction in a **journal entry** consisting of **debits** (DR) and **credits** (CR). For the purposes of this platform, you do NOT need to build these journal entries yourself: the platform builds them *from your assertions* and shows you the result. 

A journal entry places the monetary value of the transaction into two or more accounts that indicate what the transaction means to the business. 

::journal
DR Raw Materials Inventory $150
CR Cash $150
::

In this example, cash is money the business holds. It is an **asset** because it can be put to future use. The shirts are an asset too, and a more specific one: they are held to be *used up* making printed shirts. That future use is what \"Raw Materials Inventory\" indicates. A printer is held to *make* things and is still there afterward, so it is \"Equipment.\" Money spent on maintenance buys nothing that lasts, so it is an **expense**. Every account name is an answer to the same question: *what is this for, and what is left afterward?* 

That is where your assertions come in. The system can only choose the accounts for your journal entry by knowing the effect of the transaction on the business, and you are the one who specifies that effect. Whether the amounts are recorded as debits or credits is based on the accounting equation. The platform fills in that part for you.

**The same assertion somewhere else is a different transaction.** Nothing about a t-shirt decides anything; the arrangement does.

| What you assert | What it lands in |
|-----------------|------------------|
| provides money, receives shirts | the shirts, in Raw Materials or Merchandise Inventory — your **expects** picks which |
| provides shirts, receives money | Revenue, and the cost of those shirts in Cost of Goods Sold |

Same four assertions. Swap which side the shirts are on and every account changes, because a business that hands over shirts and takes money has done something different from one that hands over money and takes shirts.

Watch the **derived journal entry panel** every time you record: it shows which of *your* assertions produced each line, and you can click any line to see *why the thing got that name*. When a line says '(not yet classified)', the record is telling you it doesn't yet know the meaning of the transaction.

The panel is live: it redraws as you build, and nothing is recorded until you submit. So the cheapest way to see how much work a single assertion is doing is to take one away and watch. Remove **has-counterparty** from a sale and the Revenue line goes with it — because goods leaving with nobody on the other side is not a sale, and the platform will not pretend otherwise."}

     {:heading "Selling: The Same Words, the Other Way Round"
      :content "A sale uses the same four assertions. Only the direction changes: things go out, money comes in.

::assertions
has-date: March 3
has-counterparty: CampusBoutique
provides: 10 printed t-shirts (physical units)
receives: $250 (cash)
::

No **expects** here: what came in is money, and money does not need a purpose to be money.

**Revenue itself is not an assertion.** There is nothing to classify a sale as  *revenue*. Assertions record what happened: goods went to a customer, money came in. The revenue rules applied by this platform are GAAP's, the ones taught in ACCT 2101. Under those rules this event qualifies as revenue, recognized when the goods are provided. A different set of revenue recognition rules could recognize this pattern differently.

**A sale under GAAP is composed of two entries.** The first records what the sale earned:

::journal
DR Cash $250
CR Sales Revenue $250
::

The second records what it cost. The shirts that went out cost the business something to make, and that cost belongs against this sale, as **Cost of Goods Sold**:

::journal
DR Cost of Goods Sold $100
CR Finished Goods Inventory $100
::

**Which shirts went out?** The cost depends on which goods left, and only the record can say. A business may hold several batches of the same shirt, made or bought at different costs. Once your sale is recorded, the platform asks you to **name the batch** the goods came out of: it lists the batches the business holds, how many each has left, and what each one cost. **Any batch that holds those goods, with enough of them, is a fair answer.** The business decides which shirts it sold, and the cost of goods sold follows from that decision: name a batch that cost $5.00 a shirt and the sale costs $125; name one that cost $5.60 and the same sale costs $140 and shows $15 less profit. Same sale, same revenue, different cost — which is why the record keeps *which* batch and not just how many. Naming the batch the goods came out of is called **specific identification**: the cost of goods sold is the cost of the very units that were sold. Pick a batch that holds something else, or not enough, and it tells you — try again, nothing is lost."}

     {:heading "Practice First — Mistakes Are Free"
      :content "Next you'll take a short quiz on this reading, and then do a **practice round**.

Practice problems are a sandbox: nothing carries over from one to the next, and you get complete feedback on every answer — what you got right, what you missed, and why. Get enough right and the lesson is complete. A short check-in follows — what you can now say, and a quick look back at earlier lessons — and then the next lesson.

**You can always come back here.** There's a *Review Tutorial* button whenever you need to re-read any of this, including during practice, and every finished lesson stays open to re-read. Using the tutorial is smart, not cheating."}]

    :quiz
    [{:id :l0-q0a
      :question "You pay $5 at Campus Coffee and get a latte. In assertion terms, what did you RECEIVE?"
      :choices ["The latte" "The $5" "Campus Coffee" "The morning"]
      :correct 0
      :explanation "**Receives** is what comes IN to you — the latte. The $5 went OUT (that's **provides**), Campus Coffee is the **counterparty**, and the morning is the **date**."}

     {:id :l0-q0b
      :question "Which assertion answers the question: 'WHO was the other party in this transaction?'"
      :choices ["has-date" "provides" "has-counterparty" "receives"]
      :correct 2
      :explanation "**Has-counterparty** names the other party — the vendor you buy from or the customer you sell to. The four questions: when? (has-date) who? (has-counterparty) what went out? (provides) what came in? (receives)"}

     {:id :l0-q1
      :question "When SP buys blank t-shirts for $500 cash, which assertion describes what SP gives up?"
      :choices ["receives (cash)" "provides (cash)" "requires (cash)" "expects (cash)"]
      :correct 1
      :explanation "SP **provides** cash to the supplier. 'Provides' always describes what your company gives up in an exchange."}

     {:id :l0-q2
      :question "In a cash purchase, what does the 'has-counterparty' assertion indicate?"
      :choices ["The transaction involves an internal transfer" "There is another party involved in the exchange" "The transaction requires future payment" "The company is reporting revenue"]
      :correct 1
      :explanation "'Has-counterparty' indicates there is another party (vendor, customer, etc.) involved in the exchange. Internal operations like production don't have a counterparty."}

     {:id :l0-q3
      :question "When SP receives equipment in a cash purchase, what happens in the journal entry?"
      :choices ["Equipment is credited (goes out)" "Cash is debited (comes in)" "Equipment is debited (comes in)" "Equipment is reported as expense"]
      :correct 2
      :explanation "What you **receive** gets **debited** — the asset is coming into the company. So Equipment (the relevant asset account) is debited."}

     {:id :l0-q4
      :question "Which set of assertions correctly describes SP buying ink cartridges for $200 cash?"
      :choices ["provides physical units, receives cash, has-counterparty" "provides cash, receives physical units, has-counterparty" "requires cash, receives physical units, has-counterparty" "provides cash, creates physical units, has-counterparty"]
      :correct 1
      :explanation "SP **provides** cash, **receives** ink cartridges (physical units), and there is a vendor (**has-counterparty**). 'Requires' is for credit transactions, and 'creates' is for production."}

     {:id :l0-q5
      :question "How is revenue recognized in assertive accounting?"
      :choices ["Through a special 'reports revenue' assertion" "Revenue emerges from providing goods to a customer for monetary units" "The student must calculate revenue separately" "Revenue is only recorded at year-end"]
      :correct 1
      :explanation "Revenue **emerges from the assertion pattern**. When SP provides goods and receives money from a customer, that exchange is a sale, and the platform credits Revenue. There's no separate revenue assertion."}

     {:id :l0-q6
      :question "SP sells t-shirts that cost $100 to make for $250 cash. What is the FULL journal entry?"
      :choices ["DR Cash $250, CR Sales Revenue $250 only" "DR Cash $250, CR Sales Revenue $250; DR COGS $100, CR Finished Goods $100" "DR Revenue $250, CR Cash $250" "DR Cash $150, CR Sales Revenue $150 (net profit only)"]
      :correct 1
      :explanation "A sale is two entries: what it earned (DR Cash, CR Sales Revenue) and what it cost (DR Cost of Goods Sold, CR Finished Goods Inventory). Which shirts went out decides the cost — that is why you name the batch."}]}

   1
   {:title "Level 1: Credit Transactions"
    :subtitle "Obligations, expectations, and the time dimension"
    ;; One new word, six new classifications. This is the level where
    ;; the framework's claim is most visible, so the pair does the work.
    :orientation
    {:framing "This lesson adds one assertion. With it, the business can say what a party owes, in either direction."
     :protocol ["Everything from the first lesson still applies: what moved today, and what for."
                "Then: was a promise made about the future? — **requires**, naming who must do what, by when"
                "Then: is the outcome in the business's hands? If not, how likely is it? — **expects**"]
     :example
     {:narrative "On February 3, SP sells 25 printed t-shirts to CampusBoutique. CampusBoutique agrees to pay $625 within 30 days."
      :assertions ["has-date: February 3"
                   "has-counterparty: CampusBoutique"
                   "provides: 25 printed t-shirts (physical units)"
                   "requires: SP is to receive $625 by March 5 — the customer's promise"
                   "expects: 92% confident of receiving it"]
      :entry ["DR Accounts Receivable $625" "CR Sales Revenue $625"]}
     :pair
     {:same "The business makes one assertion, **requires**, about money that has not yet changed hands."
      :a {:when "Goods came IN, and money is to go out"
          :becomes "Accounts Payable — a debt"}
      :b {:when "Goods went OUT, and money will come in"
          :becomes "Accounts Receivable — a claim"}
      :point "The same word, in the same place in the sentence. Which way the goods went is the entire difference between owing and being owed."}
     :effect
     {:holds "Before: 25 shirts. After: no shirts, and a claim on CampusBoutique worth $625."
      :may-or-must "Before: nothing owed either way. After: somebody else must pay — and SP has recorded how sure it is they will. That confidence is not reflected in the journal entry, but is used to create the allowance for doubtful accounts at period end."}
     :reminder "One new assertion: requires. It records the presence of an unmet obligation."}
    :sections
    [{:heading "Buying Now, Paying Later"
      :content "In the first lesson everything happened at once: cash out, goods in, done.

*The garage fixes your car this morning and hands you an invoice, payable in 30 days. You drive away owing them money.*

You owe **the garage** — the people who did the work. That is what makes it credit, and businesses do it constantly: buying **on credit**, or \"on account\".

So on the day the ink cartridges arrive, what happened? You received cartridges and provided nothing. No money changed hands, but a promise did: **you now owe money**.

This lesson is about recording promises."}

     {:heading "New Assertion: 'Requires' (a Promise Someone Must Keep)"
      :content "**requires** — records a promise: a named party must do a specific thing by a specific date. Most often it is the business's own promise.

When SP buys ink cartridges on 30-day credit for $100, here's the full description of what happened that day:

::assertions
has-date: the purchase date
has-counterparty: InkMasters
receives: 50 ink cartridges (physical units)
requires: SP must provide $100 (cash) by the due date
::

Compare that to a cash purchase, and notice **what changed**:

- **provides is GONE** — SP hasn't given anything yet. Nothing went out today. Don't select it!
- **requires is NEW** — it records the promise: money must go out later, by a due date.

When you select **requires** in the platform, you'll fill in what must be provided (money), how much ($100), and by when (the due date). All three are in the transaction description.

The journal entry follows the same in/out logic as before, with one new account:

::journal
DR Raw Materials Inventory $100
CR Accounts Payable $100
::

Cartridges came in (debit, same as always). But instead of cash going out, a **debt** was created. Accountants call money you owe vendors **Accounts Payable**. A **requires** asserting that SP owes a counterparty is what accountants call a **liability**."}

     {:heading "Promises and Probabilities"
      :content "**requires** records a promise. **expects** records a probability. They are different kinds of statements, not two halves of one choice: a transaction may carry one, the other, both, or neither.

You have used **expects** already. Every time you bought materials you said what they were for — *we expect to use these up making printed t-shirts* — and that is an expectation about a future event with a number on how sure you are. What changes here is only where it points: at somebody else's action instead of your own plan.

**requires** — a named party must do a specific thing by a specific date. Either direction: SP owes, or SP is owed.

**expects** — how likely an outcome is, with a confidence level.

*A customer owes SP $250.* Both. **requires** records the promise — that is where Accounts Receivable comes from — and **expects** records how likely the money is to arrive: say 92%.

*SP owes InkMasters $100.* The promise: **requires**. That is Accounts Payable.

*SP pays a vendor in advance.* Whether the goods turn up relies on the vendor, so SP records how sure it is using **expects**.

Note that we use **expects** to record other people's promises but not our own. Theoretically we could, and there are circumstances such as financial distress when we probably should. This platform assumes a that we intend to keep our own promises with 100% probability. But an **expects** assertion could be added to these events if there is doubt about whether we will keep our promise."}
     {:heading "Selling on Credit: The Other Chair"
      :content "Now flip it around: this time **SP is the one who delivers first and waits to be paid**; in our earlier example, this is the garage's side of the story. A customer takes SP's t-shirts today and promises to pay later.

**Example: SP sells 10 printed t-shirts to CampusBoutique on 30-day credit for $250.**

Walk through the four questions plus the new one:

- When? The sale date. (**has-date**)
- Who? CampusBoutique. (**has-counterparty**)
- What went out? 10 printed t-shirts. (**provides** — yes, provides! SP gave up real shirts today.)
- What came in? *Nothing yet* — no receives.
- Any promise? Yes: the customer must pay $250 by the due date. (**requires** — recorded from SP's side, as what SP *is to receive*. Provides and receives are always SP's own actions; SP never \"provides\" something a customer hands over.)

And now the part SP does not control: will this customer actually pay? So SP records that too:

- How confident is SP of actually getting paid? (**expects**, with a confidence level — say 92%)

::assertions
has-date: the sale date
has-counterparty: CampusBoutique
provides: 10 printed t-shirts (physical units)
requires: SP is to receive $250 (cash) by the due date — the customer's promise
expects: 92% confident of receiving that $250
::

The confidence number isn't busywork: at year-end it feeds directly into estimating how much of what customers owe will actually arrive (accountants call this *bad debt*). You're exercising real accounting judgment when you set it.

**The journal entry:** a promise from a customer is money SP is owed. This promise becomes an **asset** named **Accounts Receivable**. And giving up goods in exchange for a payment promise? GAAP calls that **Revenue**:

::journal
DR Accounts Receivable $250
CR Sales Revenue $250
::

Notice *when* the revenue appears: today, the day the shirts went out — not next month when the money arrives. SP has done its part, and what is left is a claim on the customer. Recording revenue when it is earned rather than when it is collected is at the heart of accrual accounting, and a credit sale is a simple place where you can see the difference."}

     {:heading "Paying in Advance (Both Directions)"
      :content "Two last patterns — this time the MONEY moves first and the goods or services come later. Same tools, same question about who controls the outcome.

**A customer pays SP in advance** (say, a $500 deposit for a big custom order):

- receives: $500 cash — money came IN today
- requires: SP must deliver the shirts by the promised date
- Is the promise in SP's hands? Yes. SP decides whether it delivers, so there is nothing to estimate: **expects** is optional (usually left off)

::journal
DR Cash $500
CR Unearned Revenue $500
::

That credit line might surprise you: SP got cash but hasn't EARNED it yet — SP owes the customer shirts. An unearned advance is a **liability** (accountants call it *Unearned Revenue*). It flips to real revenue when SP delivers.

**SP pays a vendor in advance** (say, $600 for a year of insurance):

- provides: $600 cash — money went OUT today
- expects: SP will receive coverage over the coming year, with a confidence level
- Is there a promise? Yes. The policy binds the insurer to provide coverage, so **requires** records it, the same as any other promise.
- Is the promise in SP's hands? **No**. The insurer delivers, not SP, so SP also records how sure it is of getting what it paid for → **expects**.

::journal
DR Prepaid Expense $600
CR Cash $600
::

SP paid but hasn't USED anything yet. The right to future coverage is something SP owns: an **asset** (*Prepaid Expense*).

**The whole level in one table:**

| Situation | Who must act? | In SP's hands? | Assertions |
|-----------|-----------|---------------|-----------|
| Credit purchase | SP owes vendor | Yes — SP decides whether it pays | **requires** (expects optional) |
| Credit sale | Customer owes SP | No — SP cannot make them pay | **requires** + **expects** |
| Customer pays in advance | SP owes delivery | Yes — SP decides whether it delivers | **requires** (expects optional) |
| SP pays in advance | Vendor owes delivery | No — the vendor delivers, not SP | **requires** + **expects** |

Every row records the promise with **requires**. What the third column decides is whether a probability goes beside it: **is the outcome in SP's hands?** If it is, we assume 100%. If it is not, **expects** records how likely the promise is to be kept.

**The promise determines the account classification.**  The same assertion, **requires**, under different conditions:

- In a credit purchase or a credit sale it stands **in place of** money that did not move. So the line that would have been Cash is something else instead: **Accounts Payable** when SP owes, **Accounts Receivable** when SP is owed.
- In a prepayment it sits **beside** money that really did move. Cash is still credited and the promise is what SP got in exchange. This is a **prepaid Expense** when SP paid ahead. It is **Unearned Revenue** when SP was paid before delivering.

The account is determined by who makes the promise and for what. You can experiment with this during practice: place **requires** on different sides of the exchange and see what happens to the entry."}

     {:heading "How to Approach Every Credit Problem"
      :content "A recipe you can follow:

**Step 1.** Read the narrative. Ask: did money move TODAY? Did goods move TODAY? Record only what actually moved: **provides** for out, **receives** for in. If it didn't move today, don't select it.

**Step 2.** Ask: was a promise made about the FUTURE? If yes, select **requires** and fill in what must be provided, how much, and by when.

**Step 3.** Ask who controls the outcome. If it is not SP — a customer paying, a vendor delivering — add **expects** with how sure SP is that it happens.

**Step 4.** Date and counterparty, same as always.

**Step 5.** Read your sentence back — does it retell the story? Submit, then study the derived journal entry panel: click each line and see which of your assertions produced it.

If you get stuck during practice, the **Review Tutorial** button brings you back here. Mistakes during practice are part of the process."}]

    :quiz
    [{:id :l1-q1
      :question "When SP buys materials on credit, which assertions apply?"
      :choices ["receives, requires, expects, has-counterparty" "receives, requires, has-counterparty" "provides, requires, has-counterparty" "receives, expects, has-counterparty"]
      :correct 1
      :explanation "Credit purchases need **receives** (the goods), **requires** (obligation to pay), and **has-counterparty**. **expects** is optional here — paying is SP's own decision, so there is usually nothing to estimate, though nothing stops you."}

     {:id :l1-q2
      :question "Why does a credit sale need BOTH 'requires' and 'expects'?"
      :choices ["They mean the same thing" "Requires creates the legal obligation; expects assesses whether the customer will actually pay" "Requires is for the goods; expects is for the cash" "Only the confidence level matters, not the obligation"]
      :correct 1
      :explanation "**Requires** establishes the legal payment obligation (the customer must pay). **Expects** captures SP's confidence that the customer will actually pay. Both are needed — the obligation exists regardless of confidence."}

     {:id :l1-q3
      :question "What is the journal entry for a credit purchase of equipment?"
      :choices ["DR Cash, CR Equipment" "DR Equipment, CR Accounts Payable" "DR Accounts Payable, CR Equipment" "DR Equipment, CR Revenue"]
      :correct 1
      :explanation "SP **receives** equipment (debit) and **requires** future payment (credit to Accounts Payable). The asset comes in and a liability is created."}

     {:id :l1-q4
      :question "A company records 'expects' at 100% on its own promise to pay a vendor next month. Is that wrong?"
      :choices ["Yes — expects is only for outcomes outside the company's control" "No — the company may record its confidence even in something it controls, and the entry is the same either way" "Yes — a company cannot be uncertain about itself" "No — but only because the amount is small"]
      :correct 1
      :explanation "Neither required nor forbidden. **requires** records the promise; **expects** records how likely an outcome is. Paying its own bills is in the company's hands, so there is usually nothing to estimate — but it may record a figure anyway. A number under 100% on its own promise would say something about its finances, and be worth noticing."}]}

   2
   {:title "Level 2: Production and Transformation"
    :subtitle "Transform raw materials into finished goods"
    ;; Passing this round leads straight into Intellectual Property, with
    ;; one check-in at the end of both (Matt, 2026-10-08).
    :continues? true
    ;; The first level where nothing crosses the business boundary. The
    ;; pair turns on exactly that: ten shirts leaving the shelf is a
    ;; transformation or a sale depending on whether anyone was on the
    ;; other side.
    :orientation
    {:framing "Every event we've covered so far has had someone on the other side. This lesson is the first where the business acts on its own, with no counterparty."
     :protocol ["Everything from the first two lessons still applies — but check first: **was anyone on the other side?** If not, this is not an exchange."
                "What was used up? — **consumes**"
                "What came into being? — **creates**"
                "What made it possible? — **is-allowed-by**, pointing back at the equipment that was bought to do this"]
     :example
     {:narrative "On March 14, SP prints 10 custom t-shirts using the printer it bought earlier. The blank shirts cost $3 each when they were bought."
      :assertions ["has-date: March 14"
                   "consumes: 10 blank t-shirts (raw materials)"
                   "creates: 10 printed t-shirts (finished goods)"
                   "is-allowed-by: the t-shirt printer"]
      :entry ["DR Finished Goods Inventory $30" "CR Raw Materials Inventory $30"]}
     ;; Not the same words: a transformation and a sale are two kinds of
     ;; event that both move inventory (Matt, 2026-10-08).
     :pair-heading "The same shirts, a different event"
     :pair
     {:same "Ten blank t-shirts leave the shelf."
      :a {:when "Consumed, and printed shirts created — nobody else involved"
          :becomes "Finished Goods Inventory"}
      :b {:when "Provided to a counterparty, who pays for them"
          :becomes "Sales Revenue and Cost of Goods Sold"}
      :point "The shirts leave the shelf either way. Whether anything crossed the business's boundary is what decides between moving value inside the business and making a profit."}
     :effect
     {:holds "Before: 10 blank shirts. After: 10 printed ones. Nothing was gained or lost — value changed form."
      :may-or-must "Before: the shirts were committed to printing. After: that commitment is discharged, and the printer's **allows** has been drawn on once more."}
     :reminder "No counterparty is not a missing assertion because a transformation has no counterparty."}
    :sections
    [{:heading "Internal Transformations"
      :content "The first two lessons covered **exchange transactions** — trading with external parties using provides, receives, requires, expects, and has-counterparty.

This lesson introduces **internal transformations** — using your resources to create new products. No counterparty, no exchange — just transformation."}

     {:heading "The Transformation Assertions"
      :content "Production transactions use different assertions:

**consumes** — Uses up resources (inputs to production)

**creates** — Produces new resources (outputs from production)

**is-allowed-by** — Links to productive and legal capacities that enable production

**No counterparty!** This happens entirely within your business.

Remember the t-shirt printer from the first lesson? The printer **allows** production — and production references this connection through **is-allowed-by**. Equipment enables transformation."}

     {:heading "Printing T-Shirts"
      :content "When SP prints t-shirts:

**Example: Print 10 custom t-shirts**
- consumes: 10 blank t-shirts (raw materials)
- creates: 10 printed t-shirts (finished goods)
- is-allowed-by: T-shirt Printer (equipment)

**The Pattern:**
- What you **create** gets **debited** (building up finished goods)
- What you **consume** gets **credited** (using up raw materials)

→ **Journal Entry:** DR Finished Goods Inventory, CR Raw Materials Inventory

Production may also consume labor and supplies — the journal entry captures all input costs."}

     {:heading "Why Production is Different"
      :content "**With exchanges:**
- provides/receives → Value moves between you and someone else
- Always involves a counterparty

**With production:**
- consumes/creates → Value moves between YOUR OWN asset accounts
- No counterparty — happens internally
- is-allowed-by connects the transformation to the equipment that makes it possible

Both blank t-shirts and printed t-shirts are your assets. Production just changes the form of your inventory.

Now you can see where the goods you sell come from — and why each batch carries its own cost."}]

    :quiz
    [{:id :l2-q1
      :question "Why don't production transactions have a 'has-counterparty' assertion?"
      :choices ["Because they always use cash" "Because production happens internally within the company" "Because equipment is involved" "Because there is no journal entry for production"]
      :correct 1
      :explanation "Production is an **internal transformation** — no external party is involved. You're converting one type of your own asset (raw materials) into another (finished goods)."}

     {:id :l2-q2
      :question "In a production transaction, what does the 'consumes' assertion represent?"
      :choices ["Cash spent on labor" "Resources used up as inputs to production" "Revenue earned from sales" "Equipment depreciation"]
      :correct 1
      :explanation "'Consumes' indicates resources that are **used up** as inputs. In t-shirt production, blank t-shirts are consumed (their inventory is reduced)."}

     {:id :l2-q3
      :question "What is the journal entry for producing 10 printed t-shirts from raw materials?"
      :choices ["DR Cash, CR Inventory" "DR Raw Materials, CR Finished Goods" "DR Finished Goods, CR Raw Materials" "DR Equipment, CR Raw Materials"]
      :correct 2
      :explanation "What you **create** (finished goods) is debited, and what you **consume** (raw materials) is credited. Value moves between your own asset accounts."}]}

   3
   {:title "Level 3: Intellectual Property"
    :subtitle "Buy a design, and print a new line of shirts"
    ;; The second half of production (DESIGNS-AND-VARIETIES.org). A design
    ;; is bought the way the printer was, and is worth having for one
    ;; reason: it lets the business sell shirts it could not sell before.
    ;; Its check-in closes Production and Transformation as well.
    :checkin-covers [2 3]
    :orientation
    {:framing "The printer gave the business the capacity to print. A design gives it something new to print and sell: a new line of shirts. This lesson records buying that capacity, and then using it."
     :protocol ["Buying a design is a purchase, like buying the printer: **provides** cash, **receives** the design, from a counterparty."
                "It is a right, not a thing, so the design is denominated in **intellectual property**. The problem names which design it is."
                "What does it make possible? — **allows**: turning blank shirts and ink into shirts of *that* design."
                "Printing those shirts rests on two capacities — **is-allowed-by** the printer and the design."]
     :example
     {:narrative "On June 5, Northside pays Pixel & Thread Studio $400 for a logo design, Night Owl, that it will print on its blank t-shirts."
      :assertions ["has-date: June 5"
                   "has-counterparty: Pixel & Thread Studio"
                   "provides: $400 (cash)"
                   "receives: a logo design called Night Owl (intellectual property)"
                   "allows: turning blank t-shirts and ink into Night Owl t-shirts"]
      :entry ["DR Design (Intangible Asset) $400" "CR Cash $400"]}
     :pair
     {:same "Northside pays for something that lets it print shirts, and will use it for years."
      :a {:when "A t-shirt printer — a machine, counted in physical units"
          :becomes "Equipment"}
      :b {:when "The Night Owl design — a right, counted in intellectual property"
          :becomes "Design (Intangible Asset)"}
      :point "Both are capacities the business bought and will keep using, so both are assets. What separates them is physical substance: a right has none."}
     :effect
     {:holds "Before: $400 cash. After: the Night Owl design. The business is no poorer; it swapped cash for a right."
      :may-or-must "Before: it could print only plain shirts with its own logo. After: it can print and sell Night Owl shirts too — a new product line."}
     :reminder "A design allows printing its own shirts. Plain printed shirts still need only the printer."}
    :sections
    [{:heading "Another Capacity, Bought"
      :content "You have already recorded a business buying the capacity to make things: the t-shirt printer. It **provides** cash, **receives** the printer, and says what the printer **allows** — turning blank shirts and ink into printed shirts. That capacity is what makes the printer an asset, Equipment, rather than an expense.

A design is bought the same way, and for the same kind of reason. *Northside pays Pixel & Thread Studio $400 for a logo design, Night Owl, that it will print on its blank t-shirts.*

::assertions
provides: $400 (cash)
receives: a logo design called Night Owl (intellectual property)
allows: turning blank t-shirts and ink into Night Owl t-shirts
::

::journal
DR Design (Intangible Asset) $400
CR Cash $400
::

The one difference is what the business received. The printer is a thing you can touch. The design is a **right** — the right to print Night Owl — and a right has no physical substance. The record says so by denominating it in **intellectual property**, and that is what makes it an **intangible** asset rather than equipment."}

     {:heading "What the Design Is Worth: A New Product Line"
      :content "The printer will print anything. Until now, the shirts it made were plain printed shirts with the company's own logo.

The Night Owl design is worth $400 to Northside because it lets the business sell something it could not sell before: **Night Owl t-shirts**. That is the value of this intellectual property — it expands the product line into a new shirt design.

So the design allows something narrower than the printer does. The printer allows printed shirts in general; the design allows printing **its own** design:

::assertions
allows: turning blank t-shirts and ink into Night Owl t-shirts
::

And Night Owl shirts are a product of their own. They have their own batches and their own cost, and when they are sold, the sale names Night Owl shirts — just as a sale names the batch the goods came out of. Plain printed shirts carry on as before."}

     {:heading "Printing a Design: Two Capacities"
      :content "When Northside prints Night Owl shirts, two things made it possible: the printer, which does the printing, and the design, which says what is printed.

*On August 11, Northside uses 20 blank t-shirts and 2 ink cartridges to print 20 t-shirts with its Night Owl design.*

::assertions
consumes: 20 blank t-shirts (physical units)
consumes: 2 ink cartridges (physical units)
creates: 20 Night Owl t-shirts (physical units)
is-allowed-by: the printer purchase, and the Night Owl design purchase
::

::journal
DR Finished Goods Inventory
CR Raw Materials Inventory
::

The entry is the same as for plain shirts: value moves from raw materials into finished goods, at what the materials cost. What is new is in the record. These shirts are Night Owl shirts, and they rest on two earlier purchases. When what you consume and create matches what those purchases allow, the sentence builder finds both and fills in **is-allowed-by** for you.

Printing **plain** shirts still needs only the printer. In the practice round you will meet both, so read each problem for which shirts are printed."}]

    :quiz
    [{:id :l3-q1
      :question "Northside pays $400 for a logo design it will print on its shirts. Why is the design recorded as an asset?"
      :choices ["Because cash was paid for it" "Because it is a capacity the business will keep using to make products, as the printer is" "Because designs never lose value" "Because it has physical substance"]
      :correct 1
      :explanation "Like the printer, the design is a capacity the business bought and will keep using: it **allows** printing Night Owl shirts. That is what makes it an asset."}

     {:id :l3-q2
      :question "What separates the bought design from the bought printer in the journal entry?"
      :choices ["The design is an expense" "The design has no physical substance, so it is an intangible asset rather than equipment" "The printer is not an asset" "Nothing; both are Equipment"]
      :correct 1
      :explanation "Both are assets. The design is a right, denominated in **intellectual property**, with no physical substance — so it is **Design (Intangible Asset)**, not Equipment."}

     {:id :l3-q3
      :question "Northside prints 20 Night Owl t-shirts. What is the production allowed by?"
      :choices ["Only the printer" "Only the Night Owl design" "The printer and the Night Owl design" "Nothing; production needs no is-allowed-by"]
      :correct 2
      :explanation "The printer does the printing and the design says what is printed, so the production is **is-allowed-by** both. Plain shirts would need only the printer."}]}

   4
   {:title "Level 4: Legal and Regulatory Context"
    :subtitle "When a rule requires a payment, and names the expense"
    ;; 2101 keeps the one legal assertion that changes the entry: money
    ;; paid because a rule required it. What a law allows or protects --
    ;; the UCC, forming an LLC, contracts, copyright -- changes nothing a
    ;; 2101 entry shows, and waits for Intermediate (Matt, 2026-10-08).
    :orientation
    {:framing "Every payment so far bought something. This lesson is about payments that buy nothing the business can keep: money paid because a rule required it. The rule is what names the expense."
     :protocol ["Everything so far still applies: what changed hands, which way, what was promised, how sure."
                "If money went out and nothing came in, ask why it was paid. Did a rule **require** it? — **is-required-by**"
                "Name the rule — the tax code, or the regulator that requires a license. The rule is what names the expense."]
     :example
     {:narrative "On March 12, Northside Tees pays $500 in quarterly estimated income taxes."
      :assertions ["has-date: March 12"
                   "has-counterparty: the IRS"
                   "provides: $500 (cash)"
                   "is-required-by: the tax code"]
      :entry ["DR Income Tax Expense $500" "CR Cash $500"]}
     :pair-heading "The same payment, a different rule"
     :pair
     {:same "The business **provides** money because a rule required it, and nothing comes back."
      :a {:when "…the tax code required it: the quarterly estimated tax"
          :becomes "Income Tax Expense"}
      :b {:when "…a regulator required it: the license to operate a print shop"
          :becomes "Licenses Expense"}
      :point "No goods and no service came back, so nothing is held afterward. What decides the account is the rule that required the payment."}
     :effect
     {:holds "Before: $500 more cash. After: $500 less, and nothing new held. The tax bought this period's right to operate, and that is used up as the period goes."
      :may-or-must "Before: the business must pay its quarterly estimate. After: that duty is met, and the record says which rule imposed it — something no journal entry names."}
     :reminder "Money out with nothing coming back has no account of its own. The rule that required it names the expense."}
    :sections
    [{:heading "The Third Party Who Was Always There"
      :content "Every transaction so far has been between two parties who chose to deal with each other. SP and a vendor. SP and a customer.

But some payments are not chosen. *Northside Tees pays $500 in quarterly estimated income taxes.* Nobody sold Northside anything. It paid because a rule outside the two parties required it.

That third party — the law — stands behind business all the time. This lesson names it where naming it changes the journal entry: when a rule requires the business to pay, and nothing comes back."}

     {:heading "New Assertion: 'Is Required By' (money out because a rule said so)"
      :content "**is-required-by** — names the law or rule that made this event compulsory.

*On March 12, Northside Tees pays the IRS $500 in quarterly estimated income taxes.*

::assertions
has-date: the payment date
has-counterparty: the IRS
provides: $500 (cash)
is-required-by: the tax code
::

Notice what is missing: **no receives**. There is a counterparty — the IRS — but money went out and nothing came in. In the lessons so far that never happened — every payment bought something, and the something was what you debited.

::journal
DR Income Tax Expense $500
CR Cash $500
::

So where does the debit come from? From **is-required-by**. Money that leaves under a rule, with nothing coming back, has bought no asset the business can hold or sell. What it bought is the right to keep operating this period, and that is gone when the period is. It is an **expense**, and the rule that compelled it is what names the expense.

This is the first assertion you have met that decides an account on its own, with no flow of goods to read."}

     {:heading "A License: Same Pattern, Different Rule"
      :content "*Northside Tees pays the county $200 for the license it needs to operate a print shop.*

::assertions
has-date: the payment date
has-counterparty: the county
provides: $200 (cash)
is-required-by: licenses and permits
::

::journal
DR Licenses Expense $200
CR Cash $200
::

The shape is the tax payment's: money out, nothing in, a rule that required it. The rule is different, and so is the expense it names. The tax code gives **Income Tax Expense**; a regulator's requirement gives **Licenses Expense**.

And if the assertions name no rule at all? Then money went out, nothing came in, and nothing says why. The entry has no debit to make, and it stays unbalanced until the record says what the payment was for."}

     {:heading "The Whole Lesson in One Table"
      :content "| Payment | is-required-by | Entry |
|---------|----------------|-------|
| Quarterly estimated tax | the tax code | DR Income Tax Expense, CR Cash |
| License to operate | licenses and permits | DR Licenses Expense, CR Cash |

**The recipe, extended:**

**Steps 1–4.** As before. What moved, which way, what was promised, how sure.

**Step 5.** If money went out and nothing came in, ask whether a rule required it. If one did, say so with **is-required-by** — the rule names the expense."}]

    :quiz
    [{:id :l4-q1
      :question "SP pays $500 in quarterly taxes. Why is the debit an expense rather than an asset?"
      :choices ["Because taxes are always expenses" "Because nothing came back that SP can hold or sell — is-required-by names money out under a rule" "Because the amount is small" "Because SP has no choice about paying"]
      :correct 1
      :explanation "There is no **receives**. Money left and nothing came in, so there is no asset to carry forward — what it bought was this period's right to operate. **is-required-by** is what tells the entry that, and it names the expense: the tax code gives Income Tax Expense, a regulator's rules give Licenses Expense."}

     {:id :l4-q2
      :question "Northside pays $200 for the license a regulator requires it to hold to operate. What is the entry?"
      :choices ["DR License (asset), CR Cash" "DR Licenses Expense, CR Cash" "DR Income Tax Expense, CR Cash" "No entry — a license is not a transaction"]
      :correct 1
      :explanation "Money out, nothing in, and a rule that required it — the tax payment's shape. The rule here is a regulator's, so it names **Licenses Expense**."}

     {:id :l4-q3
      :question "SP pays $500 and nothing comes back, but the assertions name no rule. What does the entry show?"
      :choices ["Income Tax Expense" "An unbalanced entry: nothing says why the money left, so there is no debit" "Dividends" "Cash Expense"]
      :correct 1
      :explanation "Money out with nothing in has no account of its own. Until the record says why it was paid — a rule that required it, or something received — there is nothing to debit."}]}

   5
   {:title "Level 5: Adjusting Entries"
    :subtitle "Match revenues and expenses to the correct period"
    ;; Two new words. reports was tagged Level 3 while a Level 3 existed,
    ;; and fulfills Level 4, which never used it; earning an advance, here,
    ;; is the first problem to ask for either. Levels 6-7 add none.
    :orientation
    {:framing "Two new words. **reports**, because every entry so far followed from something that happened, and these follow from time passing: with no exchange to fix the amount, the business has to state it and say how it was worked out. And **fulfills**, for when what happened is an earlier promise being kept."
     :protocol ["There is no event to read. Ask instead: **what did the passing of the period do?**"
                "Something was used up quietly, or something was earned or incurred before any money changed hands."
                "Then: how much, and on what basis? — **reports**, carrying the calculation, because the need for a report is the only event that occurs."
                "Then, as always: does this leave somebody owing something? — **requires**"
                "Or does it keep a promise already on the books — an advance now earned? — **fulfills**, naming it"]
     :example
     {:narrative "On December 31, SP records one month of depreciation on the $3,000 printer, which it expects to use for five years."
      :assertions ["has-date: December 31"
                   "reports: $50 expense, on a systematic-allocation basis ($3,000 ÷ 60 months)"]
      :entry ["DR Depreciation Expense $50" "CR Accumulated Depreciation $50"]}
     ;; An accrual adds requires, so not "the same words" (Matt, 2026-10-08).
     :pair-heading "The same expense, a different credit"
     :pair
     {:same "At the end of the period, the business **reports** an expense that no exchange produced."
      :a {:when "…the printer's cost, spread over the years it will be used"
          :becomes "Accumulated Depreciation — the printer is carried at less"}
      :b {:when "…wages employees have earned but not yet been paid; **requires** names who is owed"
          :becomes "Wages Payable — owed to the employees"}
      :point "Both reduce this period's income. What differs is the credit: depreciation uses up part of something the business already holds; an accrual records a debt to someone who has done the work."}
     :effect
     {:holds "Before: the printer carried at $3,000. After: carried at $2,950 — $50 of its usefulness used up this month. No cash moved."
      :may-or-must "Nothing is owed to anyone, before or after. An accrual would add a promise — **requires** — and that is the difference you can see in the assertions."}
     :reminder "An adjusting entry records what the passing of time did. There is no exchange to read, so **reports** states the amount and how it was worked out."}
    :sections
    [{:heading "End-of-Period Adjustments"
      :content "At the end of each accounting period, adjusting entries make sure revenues and expenses are recorded in the **correct period**. That is the **accrual basis** of accounting: under the **revenue recognition principle**, revenue is recorded when it is earned, and under the **expense recognition (matching) principle**, expenses are recorded when they are incurred to earn it — whenever the cash moves.

You have already done this once. In the credit lesson, a credit sale recorded revenue the day the goods went out, not the day the money arrived, because providing the goods is what earned it. Adjusting entries apply the same rule where no exchange marks the moment, only the passing of time.

They come in two kinds:
- **Accruals** — earned or incurred *before* cash moves. **Accrued revenue** (earned, but not collected) and **accrued expenses** (incurred, but not yet paid), such as wages employees have earned.
- **Deferrals** — cash moved *first*. **Unearned revenue** (cash received in advance) becomes revenue as it is earned, and a **prepaid expense** (cash paid, expense not yet incurred) becomes an expense as it is used up.

**Depreciation** works like a prepaid expense over a longer life: the cost of an asset used for years, spread over those years."}

     {:heading "The 'Reports' Assertion"
      :content "Adjusting entries add one new assertion, and it carries the weight here:

**reports** — Explicitly recognizes a calculated amount based on some method or basis

It states an amount that no exchange produced.

Up to now, journal entries have followed from what happened in the transaction. You didn't need to assert 'this is revenue' because revenue emerged from the exchange pattern (providing goods for payment). You didn't need to assert 'this is an expense' because the cost followed from providing inventory.

But adjusting entries are different. There's **no exchange** to derive from. Equipment silently loses value. Interest accumulates daily. Prepaid benefits expire. These need **explicit recognition** — you must assert what's being recognized and how it was calculated.

Common bases:
- **systematic-allocation** — Depreciation (spreading cost over time)
- **estimation** — Bad debts (predicting future losses)
- **accrual** — Wages/interest (recognizing expense before payment)
- **time-based** — Prepaid expenses (recognizing expired benefits)

**A note on the journal entries.** An assertive record holds assertions, not journal entries. The entries in these lessons are what double-entry would record for the same events. For most events, double-entry's accounts already hold the totals the statements need, which is why only these end-of-period adjustments have to be worked out. In an assertive record, every total on a statement is a report: the year's sales as much as the wages still owed.

Nothing is exchanged when a report is issued, so there is no **has-counterparty**. Where somebody is owed — employees for wages, a lender for interest — **requires** names them."}

     {:heading "Depreciation"
      :content "Equipment loses value over time. We allocate its cost over its useful life:

**Example: Monthly depreciation on $3,000 printer with 5-year life**
- reports: expense (systematic-allocation basis), with the calculation: cost, salvage value, useful life

**The Pattern:**
- The asset's recorded value is written down (credited via a contra-account, Accumulated Depreciation) — nothing else has to be asserted; the basis says so
- Expense is recognized (debited)

→ **Journal Entry:** DR Depreciation Expense, CR Accumulated Depreciation

Note: Accumulated Depreciation is a **contra-asset** that reduces equipment value on the balance sheet."}

     {:heading "Accrued Expenses and Prepaid Expenses"
      :content "**Accrued expenses** (incurred, but not yet paid): employees have worked but payday hasn't arrived, or interest has built up on a loan. The promise to pay is already in the record — it was made when the employees did the work, or when the loan was signed. At the end of the period, the business reports the expense that has built up:

::assertions
reports: wage expense, on an accrual basis
::

The report does not create the debt. It gathers what the record already says is owed, so the statements can show it. Who is owed names the payable: employees give **Wages Payable**, a lender gives **Interest Payable**.

→ DR Wage Expense, CR Wages Payable

**Prepaid expenses** (cash paid, expense not yet incurred): insurance or rent paid in advance is used up as time passes. At the end of the period the business reports the part that has been used up:

::assertions
reports: insurance expense, on a time basis
::

What was used up is part of the prepaid asset, so the prepaid asset goes down.

→ DR Insurance Expense, CR Prepaid Expense

As in production, nothing is exchanged today, so there is no **has-counterparty**.

**Why adjusting entries need `reports` but sales don't:**

A sale changes the financial statements by *happening*. The day the shirts go out, the entry is made, and the accounts carry it from then on: the year's sales revenue is simply every sale added up.

A prepaid expense, an unpaid wage or the printer changes the statements by *existing*. Nothing happens on any particular day — a little of the insurance is used up every day, wages build up every hour worked, the printer wears a little every month. No event marks those changes, so at the end of the period the business has to work out what time has done, and say so. This is one of the uses for **reports**: the amount, and how it was worked out."}

     {:heading "Unearned Revenue: Keeping a Promise"
      :content "In the credit lesson, a customer paid ahead for shirts not yet made. The business recorded the cash and a promise — **requires** it to provide the shirts — and the promise sat on the books as **Unearned Revenue** (cash received in advance), a liability.

*On March 20, Blue Heron Printing delivers 12 of the 24 shirts LocalSportsTeam paid $600 for in advance. That much of the advance is now earned: $300.*

Nothing is exchanged today; the money came in weeks ago. What happened is that the business kept part of a promise, and that needs one more word:

**fulfills** — names the earlier promise this event keeps.

::assertions
has-date: March 20
reports: $300 revenue, earned
fulfills: the advance LocalSportsTeam paid on February 1
::

::journal
DR Unearned Revenue $300
CR Revenue $300
::

**reports** says how much has been earned and on what basis. **fulfills** says which promise it came out of — you choose it from the promises the record still holds open. The liability that promise put on the books is smaller by what was delivered, and the revenue is recognized now, when it was earned.

The same word records the promises kept most often of all: a customer paying for goods it bought on credit, and the business paying a supplier for goods it bought on credit.

*On March 1, the chess club pays the $125 it owed for the shirts it bought on January 20.*

::assertions
has-date: March 1
has-counterparty: the chess club
receives: $125 (cash)
fulfills: the credit sale of January 20
::

::journal
DR Cash $125
CR Accounts Receivable $125
::

*On April 1, the business pays InkMasters the $40 it owes for ink delivered on March 2.*

::assertions
has-date: April 1
has-counterparty: InkMasters
provides: $40 (cash)
fulfills: the ink bought on credit on March 2
::

::journal
DR Accounts Payable $40
CR Cash $40
::

Nothing is sold or bought on either day. The revenue was earned when the shirts went out; the ink came in when the promise was made. Paying clears what the promise put on the books — a claim, or a debt — and **fulfills** says which one.

You will meet **fulfills** again whenever a promise is kept: a declared dividend paid, a loan repaid, accrued interest settled. It is always the same question — *which earlier promise does this keep?* — and the answer always comes from the record."}]

    :quiz
    [{:id :l5-q1
      :question "Why do adjusting entries need the 'reports' assertion when sales don't?"
      :choices ["Because adjusting entries are more important" "Because there's no exchange pattern to derive from — recognition must be explicit" "Because sales never affect expenses" "Because reports is only for the balance sheet"]
      :correct 1
      :explanation "In sales, revenue emerges from the assertion pattern (providing goods for payment). In adjusting entries, there's **no exchange** — depreciation, accruals, and prepaid consumption must be explicitly asserted through **reports** with a calculation basis."}

     {:id :l5-q2
      :question "Why don't adjusting entries have a 'has-counterparty' assertion?"
      :choices ["Because they always involve cash" "Because they are estimates, not actual transactions" "Because nothing is exchanged with another party that day" "Because they only affect the income statement"]
      :correct 2
      :explanation "Nothing is exchanged on the day of an adjusting entry: no one hands the business anything or takes anything from it. **has-counterparty** is for exchanges. Where somebody is owed — employees, for accrued wages — **requires** names them."}

     {:id :l5-q3
      :question "What is the journal entry for recording monthly depreciation on equipment?"
      :choices ["DR Equipment, CR Cash" "DR Depreciation Expense, CR Equipment" "DR Depreciation Expense, CR Accumulated Depreciation" "DR Accumulated Depreciation, CR Depreciation Expense"]
      :correct 2
      :explanation "Depreciation recognizes the expense (debit) and reduces the asset's book value through the contra-asset Accumulated Depreciation (credit), not by crediting Equipment directly."}

     {:id :l5-q4
      :question "Which assertions describe accruing wages that employees have earned but not yet been paid?"
      :choices ["provides cash, has-counterparty" "reports expense (accrual), requires future payment" "receives physical units, reports expense" "consumes asset-value, creates liability"]
      :correct 1
      :explanation "Wage accrual **reports** an expense (on an accrual basis — incurred but not paid) and **requires** future payment (creating Wages Payable). No cash changes hands yet."}

     {:id :l5-q6
      :question "A customer pays $125 it owed for shirts bought on credit last month. What does the business record?"
      :choices ["Revenue of $125, because money came in" "receives $125, fulfills the credit sale — clearing Accounts Receivable" "A new credit sale" "Nothing, because the sale was already recorded"]
      :correct 1
      :explanation "The revenue was recognized when the shirts went out. Today a promise is kept: **receives** the money, **fulfills** the credit sale, and the claim it put on the books — Accounts Receivable — is cleared."}

     {:id :l5-q5
      :question "A customer paid $600 in advance for 24 shirts. Today the business delivers 12. Which assertions record what happened today?"
      :choices ["provides 12 shirts, receives $300, has-counterparty" "reports $300 revenue earned, fulfills the advance" "requires the customer to pay $300" "reports $600 revenue earned"]
      :correct 1
      :explanation "No money moves today — it came in with the advance. What happens is that half the promise is kept: **reports** the $300 earned, and **fulfills** names the advance it came out of. Unearned Revenue goes down by $300 and Revenue goes up by the same."}]}

   9
   {:title "Level 9: Reporting"
    :subtitle "Ask the record a question, and say how you asked it"
    ;; Keyed 9 because the numbers are keys; it sits after Adjusting
    ;; Entries in lesson-sequence. Its round is not a drill but a short run
    ;; of report tasks over a fixed company year (reporting.clj), graded by
    ;; how each report is composed. LESSON-REPORTING-DESIGN.org.
    :kind :reporting
    :orientation
    {:framing "Everything so far recorded one event at a time. A report reads many: it collects the events that answer a question, and totals something about them. This lesson asks two questions of one company's year — what did it earn from selling goods, and what did those goods cost — on two different bases."
     :protocol-heading "How to read a report"
     :protocol ["**Which events?** Goods going out, money coming in, money going out, goods coming in."
                "**Whose?** Who the other party is to the business: a customer, a supplier, an owner, a lender. The record reads it from what each event does."
                "**Any condition?** For the tax cash basis, only goods from batches that had been paid for."
                "**When?** The reporting year, or every date in the record."
                "**Total what?** What was received or promised for the goods, the money received, or what the goods cost."]
     :pair-heading "The same events, a different report"
     :pair
     {:same "Harbor Line Shirts' 2026, asked **what did you earn from selling goods?**"
      :a {:when "…collect goods provided to customers, and total what was received or promised for them"
          :becomes "Accrual revenue, $2,160"}
      :b {:when "…collect money received from customers"
          :becomes "Cash revenue, $2,110"}
      :point "Nothing in the record changed. A credit sale made in November and not yet paid counts in the first and not the second; last December's sale, collected in January, counts in the second and not the first."}
     :effect
     {:holds "Before and after: exactly the same. A report moves nothing."
      :may-or-must "After: the business has said what its revenue was, how it was worked out, and on which basis. The report is itself something asserted — and it can be checked, because the way it was composed is kept with it."}
     :reminder "A report is a question asked of the record. Ask it differently and the same events give a different, equally true answer."}
    :sections
    [{:heading "From Entries to Reports"
      :content "Every lesson so far recorded one event: what moved, who was on the other side, what was promised. A **report** reads many events at once.

It does two things, and only two:

1. **Collect** the events that answer the question — the sales, say, and not the purchases.
2. **Total** something about them — what the customers paid, or what the goods cost.

Nothing in the record changes when you report on it. What changes is what you know about the year.

This is where an assertive record and double-entry part ways most clearly. Double-entry keeps running totals — accounts — so the year's sales revenue is already sitting in an account when the year ends. An assertive record keeps the events themselves, and every total is a report built from them: the sales, the cost of the goods sold, the wages still owed. The journal entries you have seen throughout are double-entry's equivalents of those events, shown because they are what 2101 teaches."}

     {:heading "Whose Money? The Counterparty's Role"
      :content "Money comes in from customers when they pay, from owners when they invest, from lenders when they lend. Only the first is revenue.

The record knows which is which, and not from a name. It reads **who the other party is to the business** from what each event does: someone the business provides goods to, or who keeps a promise to pay for goods, is a **customer**; someone it buys from is a **supplier**; someone who puts money in for a share of the business, or takes it out, is an **owner**.

So a report asks for, say, *money received from customers*, and the owner's investment stays out without anyone having to remember it."}

     {:heading "Two Bases for the Same Year"
      :content "**Accrual basis.** Revenue is earned when the goods go out, whether the customer paid on the spot or promised to pay later — the rule from the credit lesson. The **cost of goods sold** is what the goods that went out cost, taken from the batch they came out of.

**Tax cash basis.** A small business may keep its tax books on the cash method even though it holds inventory (IRC 448(c) and 471(c); in 2026, average gross receipts of $32 million or less). Receipts count **when received**: a cash sale, and a customer paying what they owed. The cost of goods is deducted in the year the goods are **sold, and only if they have been paid for** — the later of the two (Treas. Reg. 1.471-1(b)(4)). Goods bought on credit and sold before the supplier is paid are not deductible yet.

| | Accrual | Tax cash |
|---|---|---|
| Revenue | goods provided to customers, paid or promised | money received from customers |
| Cost of goods sold | cost of goods that went out | the same, only from batches paid for |

Neither is wrong. They answer different questions, and a business may have to answer both."}

     {:heading "Gross Profit"
      :content "**Gross margin** is revenue less the cost of the goods sold: what selling the goods earned before any other cost of running the business.

It is not a new walk over the events. It is arithmetic over two reports you already have — so it is built from them, on the same basis:

- accrual gross profit = accrual revenue − accrual cost of goods sold
- cash gross profit = cash revenue − cash cost of goods sold

Mixing the bases gives a number that means nothing."}

     {:heading "What You Will Do"
      :content "You will work with one company's year: Harbor Line Shirts, a wholesaler of blank shirts. Its record is on screen the whole time.

1. **Read** two revenue reports that are already built, and see which events each one collects.
2. **Compose** the accrual cost of goods sold, from blank.
3. **Change** it into the cash-basis cost of goods sold.
4. **Build** both gross profits from your reports.

Each report is checked **part by part** — which events, whose, what condition, when, what is totaled — not just by its figure. A figure can come out right by accident, and wrong for reasons that are not your composition's fault."}]

    :quiz
    [{:id :l9-q1
      :question "The owner puts $10,000 into the business. Why does a report of money received from customers leave it out?"
      :choices ["Because the amount is too large" "Because the owner is not a customer: the record reads who the other party is from what the event does" "Because investments are recorded in a separate book" "Because only credit sales count as revenue"]
      :correct 1
      :explanation "The owner received a share of the business for the money — that is what makes them an **owner**, not a customer. A report that collects money from customers leaves the investment out because of what the event says, not because anyone remembered to exclude it."}

     {:id :l9-q2
      :question "A sale on credit in November, not paid by December 31. In which of the year's revenue reports does it count?"
      :choices ["Both" "Accrual revenue only" "Cash revenue only" "Neither"]
      :correct 1
      :explanation "On the **accrual** basis the revenue is earned when the goods go out. On the **cash** basis nothing is received until the customer pays — next year."}

     {:id :l9-q3
      :question "Under the tax cash method, shirts bought on credit in September and sold in October, with the supplier not paid until January. When is their cost deductible?"
      :choices ["September, when bought" "October, when sold" "January, when paid — the later of sold and paid" "Never"]
      :correct 2
      :explanation "Inventory treated as non-incidental materials and supplies is deductible in the year it is used or sold, **or** the year it is paid for, **whichever is later** (Treas. Reg. 1.471-1(b)(4)). Sold in October, paid in January: January."}

     {:id :l9-q4
      :question "What is gross profit on the cash basis?"
      :choices ["Cash revenue less accrual cost of goods sold" "Cash revenue less cash cost of goods sold" "All money in less all money out" "Accrual revenue less cash cost of goods sold"]
      :correct 1
      :explanation "Both reports on the **same basis**. Mixing bases gives a number that answers no question."}]}

   6
   {:title "Level 6: Equity Transactions"
    :subtitle "Record stock issued to owners, and dividends"
    :orientation
    {:framing "No new words again. What is new is who is on the other side — and that an owner is not a customer, however similar the money looks."
     :protocol ["Read the exchange exactly as you always have: what went out, what came in, who was on the other side."
                "Then ask the question this lesson turns on: **is that counterparty an owner?**"
                "If they are, what the business hands over is a claim on itself — not goods, not a service."
                "For a dividend, read it twice: the declaration promises (**reports** and **requires**), and the payment later keeps that promise — **fulfills**, naming the declaration."]
     :example
     {:narrative "On April 2, Pat invests $20,000 in SP in exchange for a 20% ownership interest."
      :assertions ["has-date: April 2"
                   "has-counterparty: Pat (owner)"
                   "receives: $20,000 (cash)"
                   "provides: ownership units"]
      :entry ["DR Cash $20,000" "CR Common Stock $20,000"]}
     :pair
     {:same "SP **receives** $20,000 from a counterparty."
      :a {:when "…having provided printed t-shirts"
          :becomes "Sales Revenue"}
      :b {:when "…having provided a share of the business itself"
          :becomes "Common Stock"}
      :point "Identical money, identical assertion. What SP gave back is the whole difference between earning and being funded — and only one of them makes the business better off by its own effort."}
     :effect
     {:holds "Before: $9,850 cash. After: $29,850. The business holds more, and has done nothing to earn it."
      :may-or-must "Before: nothing. After: Pat has a claim on the business that no repayment date attaches to. That is what makes it equity rather than a loan."}
     :reminder "Revenue is not \"money came in.\" It is money that came in because something was provided to a customer."}
    :sections
    [{:heading "Owner Transactions"
      :content "So far, we've focused on operating transactions — buying, selling, producing, and adjusting. Now we'll record transactions with **owners**:

- **Owner investments** — Putting money into the business, for stock
- **Stock issuance** — Corporations selling shares
- **Dividends** — Returning profits to stockholders

These transactions affect **equity**, not revenue or expense."}

     {:heading "Owner Investments"
      :content "When an owner contributes capital:

**Example: Pat invests $20,000 for 20% ownership**
- has-counterparty: Pat (owner)
- receives: $20,000 (cash)
- provides: ownership units

→ **Journal Entry:** DR Cash $20,000, CR Common Stock $20,000

Note: This isn't revenue! The company isn't earning money — it's receiving investment."}

     {:heading "Dividends"
      :content "**Dividends** return profits to stockholders (two-step process):

**Declaration:** reports a distribution, requires a future cash payment to the stockholders
→ DR Dividends, CR Dividends Payable

Dividends is a temporary account, like an expense: it is closed to Retained Earnings at year end. Nothing has been paid yet — the declaration is a promise, and the payable is what the promise puts on the books.

**Payment:** provides cash, has-counterparty (the stockholders), fulfills the declaration — the promise this payment keeps
→ DR Dividends Payable, CR Cash

Note: Dividends are not an expense. They are a distribution of the business's earnings to its stockholders."}

     {:heading "The Equity Pattern"
      :content "**Key insight:** Equity transactions change the balance sheet composition without affecting income.

| Transaction | Effect on Assets | Effect on Equity |
|-------------|-----------------|------------------|
| Investment | + Cash | + Common Stock |
| Dividend Declaration | No change | - Dividends (closed to Retained Earnings at year end), + Payable |
| Dividend Payment | - Cash | - Payable |

Equity transactions use the same assertion framework — provides, receives, requires, reports — but the accounts affected are equity accounts."}]

    :quiz
    [{:id :l6-q1
      :question "When an owner invests $10,000 cash into the business, is this revenue?"
      :choices ["Yes — the business is receiving money" "No — it's an equity investment, not earned revenue" "Yes — it increases the cash account" "No — it's an expense"]
      :correct 1
      :explanation "Owner investment is **not revenue**. Revenue is earned from business operations. An investment increases equity (Common Stock), not revenue. DR Cash, CR Common Stock."}

     {:id :l6-q2
      :question "SP's board declared a $500 dividend in March, and SP pays it in April. Which assertions record the payment?"
      :choices ["reports $500 expense, provides cash" "provides $500 cash, has-counterparty the stockholders, fulfills the declaration" "provides cash, receives ownership units" "requires the stockholders to pay $500"]
      :correct 1
      :explanation "The payment keeps the promise the declaration made: **provides** the cash to the stockholders and **fulfills** the declaration. That clears Dividends Payable: DR Dividends Payable, CR Cash. It is not an expense."}

     {:id :l6-q3
      :question "In the two-step dividend process, what happens at declaration?"
      :choices ["Cash is paid to stockholders" "Dividends is debited and a payable is created — Retained Earnings falls only when Dividends is closed at year end" "Revenue is recorded" "Equipment is distributed to owners"]
      :correct 1
      :explanation "At declaration, the board commits to paying dividends: **Retained Earnings decreases** (debit) and **Dividends Payable is created** (credit). Cash doesn't move until the payment step."}]}

   7
   {:title "Level 7: Notes and Interest"
    :subtitle "Borrow and lend with formal promissory notes"
    ;; The pair mirrors chain.clj's `promise-kind` exactly: :borrowing
    ;; and :lending are the two branches where money faces money.
    :orientation
    {:framing "Still no new words. What is new is that money faces money: for the first time, nothing was bought."
     :protocol ["Read the exchange as always — and notice what is missing: **no goods on either side.**"
                "That absence is what makes it a loan rather than a purchase on terms."
                "Then: which way is the money to come back? — **requires**, naming who must pay whom, and when."
                "Interest is not part of the loan. It accrues as time passes, and is read with the adjusting-entries pattern: **reports** the amount, **requires** the payment."]
     :example
     {:narrative "On June 1, SP borrows $10,000 from First National at 8% for twelve months."
      :assertions ["has-date: June 1"
                   "has-counterparty: First National"
                   "receives: $10,000 (cash)"
                   "requires: SP is to provide $10,000 by June 1 next year"]
      :entry ["DR Cash $10,000" "CR Notes Payable $10,000"]}
     :pair
     {:same "Money moves one way, and **requires** says it is to move back."
      :a {:when "Money came IN, and is to go back out"
          :becomes "Notes Payable"}
      :b {:when "Money went OUT, and is to come back"
          :becomes "Notes Receivable"}
      :point "This is the credit lesson's table again, with money in place of goods. The same word decides both, and which direction the cash first moved decides which way round it falls."}
     :effect
     {:holds "Before: $9,850 cash. After: $19,850 — and none of it earned. Borrowing makes a business no richer."
      :may-or-must "Before: nothing owed. After: $10,000 due on a fixed date, and interest building every day that passes without any event to record it."}
     :reminder "Borrowing and lending are mirrors. Read which way the money went first, and the account follows."}
    :sections
    [{:heading "Formal Borrowing and Lending"
      :content "Notes payable and receivable are **formal written promises** to pay a specific amount, usually with interest. They're more formal than accounts payable/receivable.

Key concepts:
- **Principal** — The amount borrowed
- **Interest** — The cost of borrowing (charged over time)
- **Maturity** — When the note is due"}

     {:heading "Borrowing with a Note Payable"
      :content "When SP borrows from a bank:

**Example: Borrow $10,000 at 8% for 12 months**
- has-counterparty: Bank
- receives: $10,000 (cash)
- requires: future repayment

→ **Journal Entry:** DR Cash $10,000, CR Notes Payable $10,000

This is like a credit purchase, but the obligation is a **formal note**, not just accounts payable."}

     {:heading "Interest and Repayment"
      :content "**Interest accrues** continuously on borrowed money:

Monthly interest on $10,000 at 8%: $10,000 x 8% / 12 = ~$67/month

**Accrual entry:** reports expense (accrual), requires a future payment — to the lender, which is what names it interest
→ DR Interest Expense, CR Interest Payable

**Interest payment:** provides cash, has-counterparty, fulfills the accrual — the promise this payment keeps
→ DR Interest Payable, CR Cash

**Note repayment:** provides cash, has-counterparty, fulfills the note — the promise made when the money was borrowed
→ DR Notes Payable, CR Cash"}

     {:heading "Lending (Notes Receivable)"
      :content "SP can also be the lender:

**Example: Lend $5,000 to supplier**
- has-counterparty: Supplier
- provides: $5,000 (cash)
- requires: the supplier is to provide $5,000 plus interest at maturity
- expects: 90% confident of collecting it

→ DR Notes Receivable, CR Cash

Read that against the credit sale from the credit lesson and it is the same shape. **requires** is what creates the asset — the borrower is bound, exactly as a credit customer is — and **expects** sits beside it because repaying is the borrower's decision, not SP's. Lending money differs from selling on credit only in what SP handed over: cash rather than merchandise.

**Interest revenue accrual:**
- reports: revenue (accrual basis)
- requires: the borrower is to provide the interest
→ DR Interest Receivable, CR Interest Revenue

The mirror of the expense accrual, promise and all. No exchange marks the day — a month passed on a note that already exists — so `reports` states the amount, and `requires` says who is to pay it. Naming the borrower is what makes the claim Interest Receivable.

Lending is the mirror of borrowing — the same assertions apply in reverse."}]

    :quiz
    [{:id :l7-q1
      :question "How does a Notes Payable differ from Accounts Payable?"
      :choices ["Notes Payable is for smaller amounts" "Notes Payable is a formal written promise, usually with interest" "Accounts Payable always involves equipment" "There is no difference"]
      :correct 1
      :explanation "Notes Payable are **formal written promises** to pay, typically involving interest and a specific maturity date. Accounts Payable are less formal obligations from routine purchases on credit."}

     {:id :l7-q2
      :question "Which assertions describe SP borrowing $10,000 from a bank via a promissory note?"
      :choices ["provides cash, has-counterparty" "receives cash, requires future repayment, has-counterparty" "reports revenue, receives cash" "expects cash, has-counterparty"]
      :correct 1
      :explanation "SP **receives** cash, **requires** future repayment (creating Notes Payable), and the bank is the **counterparty**. This is like a credit purchase — receives now, pays later."}

     {:id :l7-q3
      :question "When SP accrues interest expense on a loan, what is the journal entry?"
      :choices ["DR Cash, CR Interest Revenue" "DR Interest Expense, CR Interest Payable" "DR Notes Payable, CR Cash" "DR Interest Payable, CR Interest Expense"]
      :correct 1
      :explanation "Interest accrual **reports** an expense and **requires** future payment: DR Interest Expense (recognizing the cost), CR Interest Payable (creating the obligation)."}

     {:id :l7-q4
      :question "When SP lends money to a supplier, which assertion creates the Notes Receivable?"
      :choices ["requires (cash)" "provides (cash)" "expects (cash)" "reports (revenue)"]
      :correct 0
      :explanation "**requires** creates the Notes Receivable. The borrower is bound to repay, and a promise is what makes an asset — whichever way it runs. SP also **provides** the cash now, and **expects** records how likely repayment is, which affects the allowance rather than the receivable."}]}

   8
   {:title "Level 8: Capstone Review"
    :subtitle "Bringing it all together — the complete assertion framework"
    ;; After its mixed practice round: one company's year, recorded by the
    ;; student, reviewed and corrected, then reported on (capstone.clj).
    :capstone? true
    ;; No new words, and the whole reading at once: the capstone's drill
    ;; is every lesson's patterns mixed, so the protocol is every lesson's
    ;; question in the order an event raises them.
    :orientation
    {:framing "Nothing new to learn: every word is already yours. The practice round mixes every lesson, so each event has to be read from the beginning, not recognized."
     :protocol ["What moved today, and which way? — **provides**, **receives**"
                "Who was on the other side? — **has-counterparty**. If nobody, it is a transformation or an adjustment."
                "If something came in: what is it for? — **expects**, or **allows** for a machine"
                "Was a promise made, or kept? — **requires**, **fulfills**. If it is owed to the business, how sure? — **expects**"
                "Did goods change form? — **consumes**, **creates**, **is-allowed-by**"
                "Did time pass rather than anything happen? — **reports**, and how it was worked out"
                "Did a rule require the payment? — **is-required-by**, which names the expense"]
     :example
     {:narrative "On April 15, Northside Tees pays the $500 dividend its board declared on March 15."
      :assertions ["has-date: April 15"
                   "has-counterparty: Stockholders"
                   "provides: $500 (cash)"
                   "fulfills: the dividend declared on March 15"]
      :entry ["DR Dividends Payable $500" "CR Cash $500"]}
     :pair-heading "The same dividend, two events"
     :pair
     {:same "The board decides to pay its stockholders $500, and the business later pays it."
      :a {:when "…the declaration: **reports** a distribution and **requires** the payment"
          :becomes "Dividends, and Dividends Payable"}
      :b {:when "…the payment: **provides** the cash and **fulfills** the declaration"
          :becomes "Dividends Payable cleared, and Cash"}
      :point "One distribution, two events. The declaration is the promise and puts the debt on the books; the payment keeps the promise and clears it."}
     :effect
     {:holds "Before: $500 more cash, and $500 owed to the stockholders. After: both gone."
      :may-or-must "Before: the business must pay the stockholders by April 15. After: nothing is owed, and the record ties the payment to the declaration it kept."}
     :reminder "Every account in this course fell out of a sentence made from the same small vocabulary. That is the whole claim."}
    :sections
    [{:heading "The Complete Framework"
      :content "You've reached the capstone. Let's review the complete assertion framework you've mastered:

**Exchange Assertions:**

| Assertion | Used For | Journal Entry Effect |
|-----------|----------|---------------------|
| **has-date** | Every transaction | Records when it happened |
| **has-counterparty** | Exchanges with others | Identifies the other party — and without one, goods going out are not a sale |
| **provides** | Giving something now | Credit what goes out; goods provided to a customer also earn Revenue |
| **receives** | Getting something now | Debit what comes in — an asset, or an expense if it is used up as it arrives |
| **requires** | A promise someone must keep | Owed *to* the business: a claim, debited (Accounts Receivable, Notes Receivable, Prepaid Expense). Owed *by* it: a debt, credited (Accounts Payable, Unearned Revenue, Notes Payable) |
| **fulfills** | Keeping an earlier promise | Settles the claim or debt that promise created |

**What It Is For:**

| Assertion | Used For | Journal Entry Effect |
|-----------|----------|---------------------|
| **expects** (on goods bought) | What the business means to do with them | Picks the account: Raw Materials if they will be used up making something, Merchandise Inventory if they will be sold as they are |
| **allows** (on a machine bought) | What it makes possible | Places it as Equipment: it produces, and is still there afterward |
| **expects** (on a promise owed to the business) | How likely it is to be kept | No line now — at period end it becomes the allowance for doubtful accounts, and Bad Debt Expense |

**Legal Context:**

| Assertion | Used For | Journal Entry Effect |
|-----------|----------|---------------------|
| **is-required-by** | The rule that required a payment | Names the expense when money went out and nothing came back — a tax, a license |

**Transformation Assertions:**

| Assertion | Used For | Journal Entry Effect |
|-----------|----------|---------------------|
| **consumes** | Using up resources | Credit to asset (input) |
| **creates** | Producing new resources | Debit to asset (output) |
| **is-allowed-by** | The equipment that makes production possible | Links the run to the equipment |

**Recognition Assertion:**

| Assertion | Used For | Journal Entry Effect |
|-----------|----------|---------------------|
| **reports** | Calculated recognitions (adjustments) | Debit/Credit per type |

**Key insight, recalled from earlier lessons:** providing the goods earns the revenue, which fixes *when* it is recorded; and revenue needs no assertion of its own, because it emerges from the exchange pattern. Adjusting entries have no exchange to emerge from — so they need `reports` to say what is being recognized, and how it was worked out."}

     {:heading "Transaction Categories"
      :content "Every transaction falls into one of these categories:

**Exchange Transactions** — buying, selling, borrowing, lending, owners putting money in or taking it out:
- Always have a counterparty
- Use provides/receives for what moves now
- Use requires for promises, and expects for how likely one owed to the business is to be kept
- Use fulfills when an earlier promise is kept
- On goods bought, say what they are for: expects, or allows for a machine
- Revenue emerges from the sale pattern — no separate assertion needed

**Internal Transformations** — production:
- No counterparty
- Use consumes/creates for production
- is-allowed-by links to enabling equipment

**Legal Context** — taxes and licenses:
- is-required-by names the rule behind a payment that buys nothing, and that rule names the expense
- Usually beside an exchange; sometimes the law is the whole reason money moved

**Adjusting Entries** — the end of a period:
- No counterparty
- Use reports for calculated recognitions
- May use requires (an accrual: what is owed, and to whom), fulfills (an advance now earned)
- reports is needed because there's no exchange pattern to derive from"}

     {:heading "From Assertions to Journal Entries"
      :content "The beauty of assertive accounting: once you identify the correct assertions, the journal entry follows logically.

**Debit rules:**
- receives → Debit what comes in
- creates → Debit what's produced
- requires → Debit the claim (Accounts Receivable, Prepaid Expense) when the business is the one owed

**Credit rules:**
- provides → Credit what goes out
- consumes → Credit what's used up
- requires → Credit the obligation (Accounts Payable, Unearned Revenue) when the business is the one who owes

**requires** appears in both lists on purpose. One assertion, two
accounts, and the direction of the promise is the whole difference —
that is the single fact this lesson is testing.

**expects** appears in neither, and reaches the books in exactly one
place. On a credit sale the promise does all the work in the entry: it
makes the goods going out a sale, it fixes the amount, and it puts the
claim on the books. The confidence changes none of that. What it does
is later — at period end the allowance for doubtful accounts is
estimated from those confidences, and the charge for it is **Bad Debt
Expense**, matched against the sales that produced the debts. Revenue
is never reduced.

**The requires/expects asymmetry:**
- Credit purchase: **requires** (SP's own promise — a probability is optional) + **expects** saying what the goods are for
- Credit sale: **requires** + **expects** (will the customer pay? a probability worth recording)
- Deferred revenue: **requires** (SP's own promise to deliver — a probability is optional)
- Prepaid expense: **requires** + **expects** (the contract binds the vendor; delivering is still the vendor's doing)

**Revenue:** Emerges from providing goods/services for monetary payment — the exchange pattern itself.

**Adjustments:** Use **reports** to explicitly recognize amounts where no exchange occurred."}

     {:heading "Ready for the Final Quiz"
      :content "This capstone quiz covers every lesson. You'll see questions that require you to identify assertion patterns across different transaction types.

Once you pass, you'll have demonstrated mastery of the complete assertive accounting framework. Good luck!"}]

    :quiz
    [{:id :l8-q1
      :question "Which transaction type does NOT have a counterparty?"
      :choices ["Credit purchase of inventory" "Cash sale of goods" "Monthly depreciation adjustment" "Owner investment"]
      :correct 2
      :explanation "**Depreciation** is an adjusting entry — an internal recognition of asset value declining over time. No external party is involved. Purchases, sales, and owner transactions all involve counterparties."}

     {:id :l8-q2
      :question "SP sells t-shirts on credit. Which assertions are required?"
      :choices ["provides, receives, has-counterparty" "provides, requires, expects, has-counterparty" "provides, expects, has-counterparty" "provides, requires, reports, has-counterparty"]
      :correct 1
      :explanation "Credit sales need **provides** (goods delivered), **requires** (legal payment obligation), **expects** (confidence in payment), and **has-counterparty**. No receives (payment hasn't happened yet) and no reports (revenue emerges from the exchange pattern)."}

     {:id :l8-q3
      :question "SP buys raw materials on credit, produces finished goods, then sells them for cash. Which step uses 'consumes' and 'creates'?"
      :choices ["Buying raw materials" "Producing finished goods" "Selling finished goods" "Paying the vendor"]
      :correct 1
      :explanation "**Production** is the step that uses consumes (raw materials used up) and creates (finished goods produced). Buying and selling are exchange transactions; paying is settling an obligation."}

     {:id :l8-q4
      :question "Why do adjusting entries need the 'reports' assertion but sales don't?"
      :choices ["Because sales are less important" "Because sales have an exchange pattern that implies revenue; adjustments have no exchange to derive from" "Because reports is only for expenses" "Because sales only affect the balance sheet"]
      :correct 1
      :explanation "In a sale, revenue emerges from the exchange: providing goods for payment. In an adjusting entry (depreciation, accruals), there's **no exchange** — you must explicitly assert what's being recognized and how. That's what **reports** is for."}]}})

;; ==================== Accessors ====================

;; ==================== Practice drill configuration ====================
;; The mastery bar between a level's tutorial and Year 1 recording.
;; Raised from 4-of-5 for the GSU 2101 pilot population: more reps
;; before anything counts, at ~1-2 minutes per problem.
;; :streak-pass is the ALEKS-derived early exit (ALEKS-DERIVED-MECHANICS.md
;; §4): streak-pass consecutive correct passes the round immediately — in a
;; free-response answer space a streak can't be lucked into, so fluent
;; students exit in streak-pass problems instead of a minimum of pass-count.

(def ^:private default-drill-config
  {:round-size 10 :pass-count 8 :streak-pass 5})

(def ^:private drill-configs
  "Per-level overrides of the drill mastery bar. :levels keeps a round to
   those lessons' patterns rather than reviewing everything before it."
  {;; Mostly printing; the printer purchase that sets it up comes too.
   2 {:levels [2] :weights {:production-direct 4}}
   ;; The bought design, a design's variety, plain printing and the
   ;; printer: the second half of production, practiced as one, mostly
   ;; the design and printing it.
   3 {:levels [2 3] :weights {:design-variety-printing 4 :cash-design-purchase 3 :production-direct 2}}
   ;; Taxes and licenses only: a short lesson, a short round.
   4 {:levels [4] :weights {:pay-taxes 1 :business-license 1}}})

(defn drill-config [level]
  (merge default-drill-config (get drill-configs level)))

;; ==================== Stuck detection ====================
;; After consecutive drill misses, the nudge deep-links to the tutorial
;; section that teaches the assertion the student keeps omitting
;; (ALEKS-DERIVED-MECHANICS.md, stuck detection). Indexes reference each
;; level's :sections vector — keep in sync when sections are reordered.

(def ^:private stuck-sections
  {0 {:provides 2 :receives 2 :has-counterparty 2 :has-date 2 :default 2}
   1 {:requires 1 :expects 2 :default 5}
   2 {:consumes 1 :creates 1 :is-allowed-by 1 :default 1}
   3 {:receives 0 :allows 1 :is-allowed-by 2 :default 0}})

(defn stuck-section
  "Where to send a stuck student: the section teaching the given missed
   assertion at this level. Returns {:index n :heading s}, or nil when
   the level has no tutorial."
  [level assertion]
  (when-let [sections (get-in level-tutorials [level :sections])]
    (let [m (get stuck-sections level {})
          idx (min (get m assertion (get m :default 0))
                   (dec (count sections)))]
      {:index idx :heading (:heading (nth sections idx))})))

(defn orientation-for
  "The gate's front page for a level, or nil where none is authored yet.

   Levels without one fall back to the vocabulary slot alone, which is
   computed and therefore always available -- a thinner page, not a
   broken one."
  [level]
  (get-in level-tutorials [level :orientation]))

(defn get-level-tutorial
  "Returns tutorial data for the specified level."
  [level]
  (get level-tutorials level))

(defn level-tutorial-exists?
  "Checks if a tutorial exists for the specified level."
  [level]
  (contains? level-tutorials level))

(defn get-tutorial-sections
  "Returns the reading sections for a level's tutorial."
  [level]
  (get-in level-tutorials [level :sections]))

(defn get-quiz-questions
  "Returns the quiz questions for a level's tutorial."
  [level]
  (get-in level-tutorials [level :quiz]))

(def lesson-sequence
  "The order students meet the lessons in. The numbers are keys, not
   positions: Reporting (9) comes after Adjusting Entries, where every
   kind of event its reports read has been taught."
  [0 1 2 3 4 5 9 6 7 8])

(defn all-levels
  "Every lesson, in the order students meet them."
  []
  (filterv #(contains? level-tutorials %) lesson-sequence))

(defn earlier-lessons
  "The lessons before this one in the sequence."
  [level]
  (vec (take-while #(not= % level) (all-levels))))

(defn continues?
  "Does passing this lesson's round lead straight into the next lesson,
   with the check-in coming at the end of that one?"
  [level]
  (true? (get-in level-tutorials [level :continues?])))

(defn lesson-after
  "The lesson that follows this one in the sequence, or nil."
  [level]
  (second (drop-while #(not= % level) (all-levels))))

(defn checkin-levels
  "The lessons a check-in closes: its own, or those it says it covers."
  [level]
  (or (get-in level-tutorials [level :checkin-covers]) [level]))

(defn capstone-lesson?
  "Does this lesson end with the student's own year?"
  [level]
  (true? (get-in level-tutorials [level :capstone?])))

(defn reporting-lesson?
  "Is this lesson's round a run of report tasks rather than a drill?"
  [level]
  (= :reporting (get-in level-tutorials [level :kind])))

(defn max-level
  "Returns the highest tutorial level number."
  []
  (apply max (keys level-tutorials)))

;; ==================== Legacy Compatibility ====================
;; Keep stage-related functions working for simulation mode until fully migrated

(def stages
  "Stage definitions — now delegates to level-tutorials for content.
   Stage N maps to Level (N-1)."
  (into {}
        (for [stage (range 1 8)]
          (let [level (dec stage)
                tutorial (get level-tutorials level)]
            [stage {:title (:title tutorial)
                    :subtitle (:subtitle tutorial)
                    :mastery-required 3
                    :tutorial {:sections (:sections tutorial)}}]))))

(defn get-stage [stage-num]
  (get stages stage-num))

(defn get-tutorial [stage-num]
  (get-in stages [stage-num :tutorial]))

(defn stage-exists? [stage-num]
  (contains? stages stage-num))

(defn get-unlocked-actions [stage-num]
  ;; Return all actions for simulation — gating is now done by tutorial completion
  ;; NOTE: keys must match simulation.clj's action keys exactly; a mismatch
  ;; silently hides the action (this bit us: materials vs inventory).
  #{:purchase-inventory-cash :purchase-equipment-cash
    :purchase-inventory-credit :purchase-equipment-credit
    :pay-vendor
    :sell-tshirts-cash :sell-tshirts-credit
    :collect-receivable
    :produce-tshirts
    :record-depreciation :adjust-prepaid :accrue-wages :accrue-interest
    :owner-invest :issue-stock :declare-dividend :pay-dividend :owner-withdraw
    :borrow-note :repay-note :pay-interest :lend-note})

(defn get-mastery-required [stage-num]
  (get-in stages [stage-num :mastery-required] 3))

(defn get-prerequisite [stage-num]
  (when (> stage-num 1) (dec stage-num)))

(defn all-stages []
  (sort (keys stages)))

(defn max-stage []
  (apply max (keys stages)))
