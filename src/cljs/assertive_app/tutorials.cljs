(ns assertive-app.tutorials
  "Level-based tutorial content with reading sections and quiz questions.

   Each level (0-7) has:
   - :title, :subtitle — display text
   - :sections — vector of {:heading :content} for the tutorial reader
   - :quiz — vector of {:id :question :choices :correct :explanation} for the MC quiz

   Quiz grading is client-side. Server is called once on success to persist completion.

   Tutorial content matches the backend grading templates in classification.clj:
   - L0: Cash purchases (provides, receives, has-counterparty)
   - L1: Credit transactions (requires, expects — including credit sales)
   - L2: Production (consumes, creates, is-allowed-by)
   - L3: Cash sales (provides, receives, has-counterparty)
   - L4: Adjusting entries (introduces reports — calculated recognitions)
   - L5: Equity transactions
   - L6: Notes and interest
   - L7: Capstone review")

;; ==================== Level Tutorials ====================

(def level-tutorials
  "Tutorial content and quiz questions keyed by level (0-7)."
  {0
   {:title "Level 0: Cash Transactions"
    :subtitle "Learn the basics of recording business exchanges"
    :sections
    [{:heading "Your Story: You Run a T-Shirt Company"
      :content "Welcome! In this course, **you** run SP's T-Shirt Company. You'll buy a printer, stock up on blank shirts and ink, print designs, and sell them.

Every business keeps a record of what happens — every purchase, every sale. That record is called the company's **books**, and keeping it accurately is what accounting *is*.

Your job in Year 1 is to be the company's bookkeeper: things will happen in the business, and you will record them.

Don't worry if you've never done anything like this. We'll go one small step at a time, and you'll get plenty of practice — with full feedback — before anything counts."}

     {:heading "Recording = Describing What Happened"
      :content "Here's the whole idea of this course in one sentence:

**To record a transaction, you describe what happened — in a structured way.**

Think about how you'd tell a friend about buying a coffee this morning:

*\"This morning I paid $5 at Campus Coffee and got a latte.\"*

That one sentence contains four facts:
- **When** it happened (this morning)
- **Who** the other party was (Campus Coffee)
- **What you gave** ($5)
- **What you got** (a latte)

That's it. That's a complete record of a transaction. In this platform, each of those four facts is called an **assertion** — a statement about the transaction that is true. You'll record business events by selecting the assertions that describe them."}

     {:heading "Meet the Four Assertions"
      :content "At this level you only need **four** assertions. Here they are, using the coffee example:

**has-date** — *when* did it happen?
(This morning. In business: the transaction date.)

**has-counterparty** — *who* was the other party?
(Campus Coffee. In business: the vendor you buy from, or the customer you sell to.)

**provides** — *what did your company give up?*
(The $5. \"Provides\" always describes what goes OUT of your company.)

**receives** — *what did your company get?*
(The latte. \"Receives\" always describes what comes IN to your company.)

::assertions
has-date: this morning
has-counterparty: Campus Coffee
provides: $5 (money going out)
receives: 1 latte (a thing coming in)
::

Every cash purchase you record at this level uses exactly these four. When in doubt, ask the friend-telling-the-story questions: when? who? what went out? what came in?"}

     {:heading "Two Kinds of Stuff: Money and Things"
      :content "When you fill in **provides** and **receives**, the platform asks what *kind* of thing moved. At this level there are only two kinds:

**monetary-unit** — money. Dollars. Cash.

**physical-unit** — things. Blank t-shirts, ink cartridges, a t-shirt printer.

In a cash purchase, the pattern is always the same:
- Your company **provides** money (monetary-unit)
- Your company **receives** things (physical-unit)

You'll also enter *how much*: the dollar amount for the money, and the quantity (how many) for the things. Both numbers are right there in the transaction description — you never have to guess."}

     {:heading "Worked Example, Step by Step"
      :content "Let's record one together. Suppose you see this transaction:

*\"On January 8, you purchase 50 blank t-shirts from TextileDirect for $150 cash.\"*

Here is exactly what you'd do, click by click:

**Step 1.** Read the sentence slowly. Find the four facts: the date (January 8), the other party (TextileDirect), what went out ($150), what came in (50 blank t-shirts).

**Step 2.** **Has Date** is usually already selected with the date filled in — just check that it matches.

**Step 3.** Click **Provides**. Choose **monetary-unit** (money went out), and enter the amount: 150.

**Step 4.** Click **Receives**. Choose **physical-unit** (things came in), pick the item (blank t-shirts), and enter the quantity: 50.

**Step 5.** Click **Has Counterparty** and enter who: TextileDirect.

**Step 6.** Read your sentence back at the top of the panel — it should retell the story. Then click **Submit**.

::assertions
has-date: January 8
has-counterparty: TextileDirect
provides: $150 (monetary-unit)
receives: 50 blank t-shirts (physical-unit)
::

That's the entire skill at this level. Every problem is this same pattern with different details."}

     {:heading "Where the Journal Entry Comes From"
      :content "Accountants summarize every transaction in a two-line format called a **journal entry**, using the words **debit** (DR) and **credit** (CR). You do NOT need to build these yourself — the platform builds them *from your assertions* and shows you the result.

The translation rule is simple:

- What you **receive** is **debited** — it came IN
- What you **provide** is **credited** — it went OUT

So for the t-shirt purchase:

::journal
DR Raw Materials Inventory $150
CR Cash $150
::

Read it as: \"t-shirts came in ($150 worth), cash went out ($150).\" Buying equipment works exactly the same way — a $3,000 printer gives DR Equipment, CR Cash.

Watch the **derived journal entry panel** every time you record: it shows which of *your* assertions produced each line, and you can click any line to see the rule that built it. Over the semester, this is how debits and credits start to feel obvious instead of memorized."}

     {:heading "Practice First — Mistakes Are Free"
      :content "Next you'll take a short quiz on this reading, and then do a **practice round**.

Practice problems are a sandbox: they do **not** go into your company's books, and you get complete feedback on every answer — what you got right, what you missed, and why. Get enough right and you'll unlock the real thing: recording Year 1 of your company's books.

Two things to remember once you ARE keeping the books:

1. **Your books record whatever you enter.** Real bookkeeping doesn't stop you when you're wrong — errors sit in the records until someone finds them. (You'll do that finding yourself, later, at year-end.)

2. **You can always come back here.** There's a *Review Tutorial* button whenever you need to re-read any of this — including during practice. Using it is smart, not cheating."}]

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
      :choices ["receives (monetary-unit)" "provides (monetary-unit)" "requires (monetary-unit)" "expects (monetary-unit)"]
      :correct 1
      :explanation "SP **provides** cash (monetary-unit) to the supplier. 'Provides' always describes what your company gives up in an exchange."}

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
      :choices ["provides physical-unit, receives monetary-unit, has-counterparty" "provides monetary-unit, receives physical-unit, has-counterparty" "requires monetary-unit, receives physical-unit, has-counterparty" "provides monetary-unit, creates physical-unit, has-counterparty"]
      :correct 1
      :explanation "SP **provides** cash (monetary-unit), **receives** ink cartridges (physical-unit), and there is a vendor (**has-counterparty**). 'Requires' is for credit transactions, and 'creates' is for production."}]}

   1
   {:title "Level 1: Credit Transactions"
    :subtitle "Obligations, expectations, and the time dimension"
    :sections
    [{:heading "Buying Now, Paying Later"
      :content "In Level 0, everything happened at once: you handed over cash, you got your stuff, done. But think about how life actually works:

*You order a pizza online. The pizza arrives now — your card gets charged at the end of the month.*

The pizza place gave you something today and trusts you to pay later. Businesses do this constantly — it's called buying **on credit** (or \"on account\").

Here's the recording puzzle: on the day the ink cartridges arrive, what actually happened? You RECEIVED cartridges... but you didn't provide anything yet. No money moved!

Something did happen, though — something real that must be recorded: **you now owe money**. A promise to pay was created. Level 1 is about recording promises."}

     {:heading "New Assertion: 'Requires' (a Promise Someone Must Keep)"
      :content "**requires** — records an obligation: someone MUST do something in the future.

When SP buys ink cartridges on 30-day credit for $100, here's the full description of what happened that day:

::assertions
has-date: the purchase date
has-counterparty: InkMasters
receives: 50 ink cartridges (physical-unit)
requires: SP must provide $100 (monetary-unit) by the due date
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

Cartridges came in (debit, same as always). But instead of cash going out, a **debt** was created — accountants call money you owe vendors **Accounts Payable**. A `requires` where SP owes is what accountants call a **liability**."}

     {:heading "One Question to Rule Them All: Do We Also Need 'Expects'?"
      :content "There's a second future-looking assertion:

**expects** — records a belief about the future, with a confidence level: how sure are we this will actually happen?

So when do you need it? Ask one question:

**\"Who controls whether the promise gets kept?\"**

*Case 1: SP promises to pay InkMasters.* Who controls whether SP pays? **SP does.** There's nothing uncertain to assess about your own choices — you know you'll pay your bills. So: **requires only, no expects.**

*Case 2: a customer promises to pay SP.* Who controls that? **The customer** — not you. Will they actually pay? Probably... but not certainly. That's a genuine uncertainty about someone ELSE, so SP records **requires** (the promise exists) AND **expects** (how confident SP is — say, 92%).

Memorize the question, not a list of cases. Every credit transaction at this level is solved by asking: who controls whether the promise gets kept? **Your own promise → requires only. Someone else's promise to you → requires + expects.**"}

     {:heading "Selling on Credit: The Other Chair"
      :content "Now flip the pizza situation around: this time, **SP is the pizza place.** A customer takes SP's t-shirts today and promises to pay later.

**Example: SP sells 10 printed t-shirts to CampusBoutique on 30-day credit for $250.**

Walk through the four questions plus the new one:

- When? The sale date. (**has-date**)
- Who? CampusBoutique. (**has-counterparty**)
- What went out? 10 printed t-shirts. (**provides** — yes, provides! SP gave up real shirts today.)
- What came in? *Nothing yet* — no receives.
- Any promise? Yes: the customer must pay $250 by the due date. (**requires**)

And now the control question: who controls whether THIS promise gets kept? **The customer.** So SP also records:

- How confident is SP of actually getting paid? (**expects**, with a confidence level — say 92%)

::assertions
has-date: the sale date
has-counterparty: CampusBoutique
provides: 10 printed t-shirts (physical-unit)
requires: customer must provide $250 (monetary-unit) by the due date
expects: 92% confident of receiving that $250
::

The confidence number isn't busywork — at year-end it feeds directly into estimating how much of what customers owe will actually arrive (accountants call this *bad debt*). You're doing real accounting judgment when you set it.

**The journal entry:** a promise from a customer is money SP is owed — accountants call that an **asset** named **Accounts Receivable**. And giving up goods in exchange for a payment promise? Double-entry calls that **Revenue**:

::journal
DR Accounts Receivable $250
CR Revenue $250
::

Notice something important: \"revenue\" was never one of your assertions. You just described what happened — goods out, payment promised — and *revenue is the name double-entry gives that pattern*. Categories come from patterns of facts. That's the deepest idea in this course, arriving quietly."}

     {:heading "Paying in Advance (Both Directions)"
      :content "Two last patterns — this time the MONEY moves first and the goods or services come later. Same tools, same control question.

**A customer pays SP in advance** (say, a $500 deposit for a big custom order):

- receives: $500 cash — money came IN today
- requires: SP must deliver the shirts by the promised date
- Control question: who controls delivery? **SP does** → no expects

::journal
DR Cash $500
CR Deferred Revenue $500
::

That credit line might surprise you: SP got cash but hasn't EARNED it yet — SP owes the customer shirts. An unearned advance is a **liability** (accountants call it *Deferred Revenue*). It flips to real revenue when SP delivers.

**SP pays a vendor in advance** (say, $600 for a year of insurance):

- provides: $600 cash — money went OUT today
- expects: SP will receive coverage over the coming year (with confidence)
- Control question: who controls delivering the coverage? **The vendor** → expects, not requires

::journal
DR Prepaid Expense $600
CR Cash $600
::

SP paid but hasn't USED anything yet — the right to future coverage is something SP owns: an **asset** (*Prepaid Expense*).

**The whole level in one table:**

| Situation | Whose promise? | Who controls it? | Assertions |
|-----------|-----------|---------------|-----------|
| Credit purchase | SP owes vendor | SP | **requires** only |
| Credit sale | Customer owes SP | Customer | **requires** + **expects** |
| Customer pays in advance | SP owes delivery | SP | **requires** only |
| SP pays in advance | Vendor owes service | Vendor | **expects** |

One question solves all four rows: **who controls whether the promise gets kept?** Your own promise → requires. Someone else's → expects (plus requires when there's a legal obligation, as in a credit sale)."}

     {:heading "How to Approach Every Level 1 Problem"
      :content "A recipe you can follow every single time:

**Step 1.** Read the narrative. Ask: did money move TODAY? Did goods move TODAY? Record only what actually moved: **provides** for out, **receives** for in. If it didn't move today, don't select it.

**Step 2.** Ask: was a promise made about the FUTURE? If yes, select **requires** and fill in what must be provided, how much, and by when — it's all in the narrative.

**Step 3.** Ask the control question: is the promise SP's to keep, or someone else's? Someone else's → add **expects** with your confidence level.

**Step 4.** Date and counterparty, same as always.

**Step 5.** Read your sentence back — does it retell the story? Submit, then study the derived journal entry panel: click each line and see which of your assertions produced it.

If you get stuck during practice, the **Review Tutorial** button brings you back here. Mistakes in practice cost nothing — that's what it's for."}]

    :quiz
    [{:id :l1-q1
      :question "When SP buys materials on credit, which assertions apply?"
      :choices ["receives, requires, expects, has-counterparty" "receives, requires, has-counterparty" "provides, requires, has-counterparty" "receives, expects, has-counterparty"]
      :correct 1
      :explanation "Credit purchases need **receives** (the goods), **requires** (obligation to pay), and **has-counterparty**. No **expects** — SP controls its own payments, so there's no uncertainty to assess."}

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
      :question "Deferred revenue uses 'requires' but not 'expects'. Why?"
      :choices ["Because the customer hasn't paid yet" "Because SP controls its own delivery — no uncertainty to assess" "Because deferred revenue is always a liability" "Because expects is only for sales"]
      :correct 1
      :explanation "SP received payment and is obligated (**requires**) to deliver goods/services. Since SP controls its own delivery, there's no need for a confidence assessment (**expects**). The same logic applies to credit purchases — when you control the action, requires alone is enough."}]}

   2
   {:title "Level 2: Production and Transformation"
    :subtitle "Transform raw materials into finished goods"
    :sections
    [{:heading "Internal Transformations"
      :content "Levels 0 and 1 covered **exchange transactions** — trading with external parties using provides, receives, requires, expects, and has-counterparty.

Level 2 introduces **internal transformations** — using your resources to create new products. No counterparty, no exchange — just transformation."}

     {:heading "The Transformation Assertions"
      :content "Production transactions use different assertions:

**consumes** — Uses up resources (inputs to production)

**creates** — Produces new resources (outputs from production)

**is-allowed-by** — Links to the equipment that enables production

Notice: **No counterparty!** This happens entirely within your business.

Remember when you purchased that t-shirt printer in Level 0? The printer **allows** production — and production references this connection through **is-allowed-by**. Equipment enables transformation."}

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

Now that you can produce finished goods, you'll be ready to sell them in Level 3!"}]

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
   {:title "Level 3: Cash Sales"
    :subtitle "Sell your products and understand revenue"
    :sections
    [{:heading "Selling What You've Made"
      :content "You've purchased materials (L0), learned about credit transactions (L1), and produced finished goods (L2). Now it's time to sell those printed t-shirts for cash!

You already saw credit sales in Level 1. Cash sales are simpler — payment is immediate, so there's no `requires` or `expects`."}

     {:heading "The Cash Sale Pattern"
      :content "When a customer pays immediately:

**Example: Sell 10 printed t-shirts for $250 cash**
- has-counterparty: Customer
- provides: 10 printed t-shirts (physical-unit)
- receives: $250 cash (monetary-unit)

These are the same assertions as a cash purchase — just reversed:
- **Purchase:** provides monetary-unit, receives physical-unit
- **Sale:** provides physical-unit, receives monetary-unit

→ **Journal Entry:** DR Cash $250, CR Revenue $250"}

     {:heading "Revenue: From Assertions to Journal Entry"
      :content "Here's an important insight: **revenue is not an assertion.** There's no 'reports revenue' assertion you need to select.

Instead, revenue **emerges from the assertion pattern.** In assertive accounting, the assertions record *what happened*: SP provided goods and received cash from a customer. The journal entry system then maps this to the appropriate accounts.

**What assertive accounting sees:**
- SP provides physical goods (inventory leaves)
- SP receives monetary units (cash comes in)
- There is a counterparty (customer)

**What double-entry sees:**
- DR Cash — an asset increases
- CR Revenue — income is earned

The revenue credit follows from the *meaning* of the exchange: when you provide goods to a customer for monetary units, that's a sale, and the monetary value is revenue. The assertions capture the economic reality; the journal entry categorizes it."}

     {:heading "Cost of Goods Sold"
      :content "When you sell inventory, two things happen economically:

1. **Revenue** — Monetary units received for goods provided
2. **Cost of Goods Sold** — The cost of the inventory you gave up

→ Full Journal Entries:
- DR Cash $250, CR Revenue $250 (revenue side)
- DR Cost of Goods Sold $100, CR Finished Goods Inventory $100 (cost side)

**Compare cash sales vs. credit sales:**

| | Cash Sale | Credit Sale |
|---|-----------|-------------|
| **provides** | physical-unit | physical-unit |
| **receives** | monetary-unit | — |
| **requires** | — | Customer must pay |
| **expects** | — | Confidence level |

Both types provide goods to a customer. The difference is whether payment is immediate (**receives**) or deferred (**requires** + **expects**). In both cases, revenue emerges from the exchange pattern — not from a separate assertion."}]

    :quiz
    [{:id :l3-q1
      :question "Which assertions describe a cash sale of printed t-shirts?"
      :choices ["provides monetary-unit, receives physical-unit, has-counterparty" "provides physical-unit, receives monetary-unit, has-counterparty" "provides physical-unit, expects monetary-unit, has-counterparty" "provides physical-unit, requires monetary-unit, has-counterparty"]
      :correct 1
      :explanation "A cash sale: SP **provides** goods (physical-unit), **receives** cash (monetary-unit), and there's a customer (**has-counterparty**). No requires/expects because payment is immediate."}

     {:id :l3-q2
      :question "How is revenue recognized in assertive accounting?"
      :choices ["Through a special 'reports revenue' assertion" "Revenue emerges from providing goods to a customer for monetary units" "The student must calculate revenue separately" "Revenue is only recorded at year-end"]
      :correct 1
      :explanation "Revenue **emerges from the assertion pattern**. When SP provides goods and receives (or is owed) monetary units from a customer, the journal entry system maps that exchange to Revenue. There's no separate revenue assertion."}

     {:id :l3-q3
      :question "What is the FULL journal entry when SP sells t-shirts that cost $100 to produce for $250 cash?"
      :choices ["DR Cash $250, CR Revenue $250 only" "DR Cash $250, CR Revenue $250; DR COGS $100, CR Finished Goods $100" "DR Revenue $250, CR Cash $250" "DR Cash $150, CR Revenue $150 (net profit only)"]
      :correct 1
      :explanation "A sale triggers BOTH revenue recognition (DR Cash, CR Revenue) and cost recognition (DR Cost of Goods Sold, CR Finished Goods Inventory). Both the revenue and its associated cost are recognized."}

     {:id :l3-q4
      :question "A cash sale and a cash purchase use the same assertions but reversed. What changes?"
      :choices ["Different assertions entirely" "Provides and receives swap — purchase provides cash for goods, sale provides goods for cash" "Sales don't use has-counterparty" "Purchases don't use provides"]
      :correct 1
      :explanation "Cash purchase: **provides** monetary-unit, **receives** physical-unit. Cash sale: **provides** physical-unit, **receives** monetary-unit. The assertions mirror each other — it's the direction of the exchange that differs."}]}

   4
   {:title "Level 4: Adjusting Entries"
    :subtitle "Match revenues and expenses to the correct period"
    :sections
    [{:heading "End-of-Period Adjustments"
      :content "At the end of each accounting period, we need to make sure revenues and expenses are recorded in the **correct period**. This is the matching principle.

Adjusting entries ensure:
- Expenses are recognized when incurred (not just when paid)
- Revenues are recognized when earned (not just when received)
- Assets reflect their current value"}

     {:heading "The 'Reports' Assertion"
      :content "Adjusting entries introduce a new assertion:

**reports** — Explicitly recognizes a calculated amount based on some method or basis

Up to now, journal entries have followed from what happened in the transaction. You didn't need to assert 'this is revenue' because revenue emerged from the exchange pattern (providing goods for payment). You didn't need to assert 'this is an expense' because the cost followed from providing inventory.

But adjusting entries are different. There's **no exchange** to derive from. Equipment silently loses value. Interest accumulates daily. Prepaid benefits expire. These need **explicit recognition** — you must assert what's being recognized and how it was calculated.

Common bases:
- **systematic-allocation** — Depreciation (spreading cost over time)
- **estimation** — Bad debts (predicting future losses)
- **accrual** — Wages/interest (recognizing expense before payment)
- **time-based** — Prepaid expenses (recognizing expired benefits)

Unlike exchanges, adjusting entries have **no counterparty** — they are internal recognitions of economic reality."}

     {:heading "Depreciation"
      :content "Equipment loses value over time. We allocate its cost over its useful life:

**Example: Monthly depreciation on $3,000 printer with 5-year life**
- reports: expense (systematic-allocation basis)
- consumes: asset-value

**The Pattern:**
- Asset value is consumed (credited via contra-account)
- Expense is recognized (debited)

→ **Journal Entry:** DR Depreciation Expense, CR Accumulated Depreciation

Note: Accumulated Depreciation is a **contra-asset** that reduces equipment value on the balance sheet."}

     {:heading "Accrued Expenses and Prepaid Adjustments"
      :content "**Accrued Expenses** — expenses incurred before payment:
- Wages: employees worked but payday hasn't arrived
- Interest: accumulates daily on loans
- reports: expense (accrual basis), requires: future payment
→ DR Expense, CR Payable

**Prepaid Adjustments** — 'using up' prepaid assets over time:
- Insurance, rent paid in advance
- reports: expense (time-based), consumes: prepaid-benefit
→ DR Expense, CR Prepaid Asset

Like production, adjusting entries have **no counterparty** — they're internal recognitions.

**Why adjusting entries need 'reports' but sales don't:**
In a sale, revenue follows from the exchange pattern — you provided goods and received payment, so revenue emerges. In an adjusting entry, there's no exchange — you need `reports` to explicitly assert what's being recognized and how it was calculated."}]

    :quiz
    [{:id :l4-q1
      :question "Why do adjusting entries need the 'reports' assertion when sales don't?"
      :choices ["Because adjusting entries are more important" "Because there's no exchange pattern to derive from — recognition must be explicit" "Because sales never affect expenses" "Because reports is only for the balance sheet"]
      :correct 1
      :explanation "In sales, revenue emerges from the assertion pattern (providing goods for payment). In adjusting entries, there's **no exchange** — depreciation, accruals, and prepaid consumption must be explicitly asserted through **reports** with a calculation basis."}

     {:id :l4-q2
      :question "Why don't adjusting entries have a 'has-counterparty' assertion?"
      :choices ["Because they always involve cash" "Because they are estimates, not actual transactions" "Because they are internal recognitions, not exchanges with external parties" "Because they only affect the income statement"]
      :correct 2
      :explanation "Adjusting entries are **internal recognitions** of economic reality (like equipment losing value or wages being earned). No external party is involved in these entries."}

     {:id :l4-q3
      :question "What is the journal entry for recording monthly depreciation on equipment?"
      :choices ["DR Equipment, CR Cash" "DR Depreciation Expense, CR Equipment" "DR Depreciation Expense, CR Accumulated Depreciation" "DR Accumulated Depreciation, CR Depreciation Expense"]
      :correct 2
      :explanation "Depreciation recognizes the expense (debit) and reduces the asset's book value through the contra-asset Accumulated Depreciation (credit), not by crediting Equipment directly."}

     {:id :l4-q4
      :question "Which assertions describe accruing wages that employees have earned but not yet been paid?"
      :choices ["provides monetary-unit, has-counterparty" "reports expense (accrual), requires future payment" "receives physical-unit, reports expense" "consumes asset-value, creates liability"]
      :correct 1
      :explanation "Wage accrual **reports** an expense (on an accrual basis — incurred but not paid) and **requires** future payment (creating Wages Payable). No cash changes hands yet."}]}

   5
   {:title "Level 5: Equity Transactions"
    :subtitle "Record owner investments, withdrawals, and dividends"
    :sections
    [{:heading "Owner Transactions"
      :content "So far, we've focused on operating transactions — buying, selling, producing, and adjusting. Now we'll record transactions with **owners**:

- **Owner investments** — Putting money into the business
- **Owner withdrawals** — Taking money out of the business
- **Stock issuance** — Corporations selling shares
- **Dividends** — Returning profits to shareholders

These transactions affect **equity**, not revenue or expense."}

     {:heading "Owner Investments"
      :content "When an owner contributes capital:

**Example: Pat invests $20,000 for 20% ownership**
- has-counterparty: Pat (owner)
- receives: $20,000 (monetary-unit)
- provides: ownership-interest

→ **Journal Entry:** DR Cash $20,000, CR Owner's Capital $20,000

Note: This isn't revenue! The company isn't earning money — it's receiving investment."}

     {:heading "Dividends and Withdrawals"
      :content "**Dividends** return profits to shareholders (two-step process):

**Declaration:** reports distribution, requires future cash payment
→ DR Retained Earnings, CR Dividends Payable

**Payment:** provides cash, has-counterparty (shareholders)
→ DR Dividends Payable, CR Cash

**Owner Withdrawals** (sole proprietorships):
- provides: cash (monetary-unit), has-counterparty: owner
→ DR Owner's Drawing, CR Cash

Note: Neither dividends nor withdrawals are expenses — they're returns of capital."}

     {:heading "The Equity Pattern"
      :content "**Key insight:** Equity transactions change the balance sheet composition without affecting income.

| Transaction | Effect on Assets | Effect on Equity |
|-------------|-----------------|------------------|
| Investment | + Cash | + Capital |
| Withdrawal | - Cash | - Drawing |
| Dividend Declaration | No change | - Retained Earnings, + Payable |
| Dividend Payment | - Cash | - Payable |

Equity transactions use the same assertion framework — provides, receives, requires, reports — but the accounts affected are equity accounts."}]

    :quiz
    [{:id :l5-q1
      :question "When an owner invests $10,000 cash into the business, is this revenue?"
      :choices ["Yes — the business is receiving money" "No — it's an equity investment, not earned revenue" "Yes — it increases the cash account" "No — it's an expense"]
      :correct 1
      :explanation "Owner investment is **not revenue**. Revenue is earned from business operations. An investment increases equity (Owner's Capital), not revenue. DR Cash, CR Owner's Capital."}

     {:id :l5-q2
      :question "What assertions describe an owner withdrawing $1,000 from the business?"
      :choices ["receives monetary-unit, has-counterparty" "provides monetary-unit, has-counterparty" "reports expense, provides monetary-unit" "requires monetary-unit, has-counterparty"]
      :correct 1
      :explanation "The company **provides** cash (monetary-unit) to the owner (**has-counterparty**). This creates DR Owner's Drawing, CR Cash. It's not an expense."}

     {:id :l5-q3
      :question "In the two-step dividend process, what happens at declaration?"
      :choices ["Cash is paid to shareholders" "Retained Earnings decreases and a payable is created" "Revenue is recorded" "Equipment is distributed to owners"]
      :correct 1
      :explanation "At declaration, the board commits to paying dividends: **Retained Earnings decreases** (debit) and **Dividends Payable is created** (credit). Cash doesn't move until the payment step."}]}

   6
   {:title "Level 6: Notes and Interest"
    :subtitle "Borrow and lend with formal promissory notes"
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
- receives: $10,000 (monetary-unit)
- requires: future repayment

→ **Journal Entry:** DR Cash $10,000, CR Notes Payable $10,000

This is like a credit purchase, but the obligation is a **formal note**, not just accounts payable."}

     {:heading "Interest and Repayment"
      :content "**Interest accrues** continuously on borrowed money:

Monthly interest on $10,000 at 8%: $10,000 x 8% / 12 = ~$67/month

**Accrual entry:** reports expense (accrual), requires future payment
→ DR Interest Expense, CR Interest Payable

**Interest payment:** provides cash, has-counterparty
→ DR Interest Payable, CR Cash

**Note repayment:** provides cash, has-counterparty
→ DR Notes Payable, CR Cash"}

     {:heading "Lending (Notes Receivable)"
      :content "SP can also be the lender:

**Example: Lend $5,000 to supplier**
- has-counterparty: Supplier
- provides: $5,000 (monetary-unit)
- expects: future repayment with interest

→ DR Notes Receivable, CR Cash

**Interest revenue accrual:**
- reports: revenue (accrual), expects: future receipt
→ DR Interest Receivable, CR Interest Revenue

Lending is the mirror of borrowing — the same assertions apply in reverse."}]

    :quiz
    [{:id :l6-q1
      :question "How does a Notes Payable differ from Accounts Payable?"
      :choices ["Notes Payable is for smaller amounts" "Notes Payable is a formal written promise, usually with interest" "Accounts Payable always involves equipment" "There is no difference"]
      :correct 1
      :explanation "Notes Payable are **formal written promises** to pay, typically involving interest and a specific maturity date. Accounts Payable are less formal obligations from routine purchases on credit."}

     {:id :l6-q2
      :question "Which assertions describe SP borrowing $10,000 from a bank via a promissory note?"
      :choices ["provides monetary-unit, has-counterparty" "receives monetary-unit, requires future repayment, has-counterparty" "reports revenue, receives monetary-unit" "expects monetary-unit, has-counterparty"]
      :correct 1
      :explanation "SP **receives** cash, **requires** future repayment (creating Notes Payable), and the bank is the **counterparty**. This is like a credit purchase — receives now, pays later."}

     {:id :l6-q3
      :question "When SP accrues interest expense on a loan, what is the journal entry?"
      :choices ["DR Cash, CR Interest Revenue" "DR Interest Expense, CR Interest Payable" "DR Notes Payable, CR Cash" "DR Interest Payable, CR Interest Expense"]
      :correct 1
      :explanation "Interest accrual **reports** an expense and **requires** future payment: DR Interest Expense (recognizing the cost), CR Interest Payable (creating the obligation)."}

     {:id :l6-q4
      :question "When SP lends money to a supplier, which assertion creates the Notes Receivable?"
      :choices ["requires (monetary-unit)" "provides (monetary-unit)" "expects (monetary-unit)" "reports (revenue)"]
      :correct 2
      :explanation "When lending, SP **expects** future repayment — this creates Notes Receivable (an asset). SP also **provides** cash now and has a counterparty."}]}

   7
   {:title "Level 7: Capstone Review"
    :subtitle "Bringing it all together — the complete assertion framework"
    :sections
    [{:heading "The Complete Framework"
      :content "Congratulations on making it to Level 7! Let's review the complete assertion framework you've mastered:

**Exchange Assertions:**

| Assertion | Used For | Journal Entry Effect |
|-----------|----------|---------------------|
| **has-date** | Every transaction | Records when it happened |
| **has-counterparty** | Exchanges with others | Identifies the other party |
| **provides** | Giving something now | Credit to asset/equity |
| **receives** | Getting something now | Debit to asset |
| **requires** | Legal obligation (future) | Credit to liability |
| **expects** | Confidence in future event | Debit to asset (receivable) |

**Transformation Assertions:**

| Assertion | Used For | Journal Entry Effect |
|-----------|----------|---------------------|
| **consumes** | Using up resources | Credit to asset (input) |
| **creates** | Producing new resources | Debit to asset (output) |
| **is-allowed-by** | Equipment enabling production | Links to equipment |

**Recognition Assertion:**

| Assertion | Used For | Journal Entry Effect |
|-----------|----------|---------------------|
| **reports** | Calculated recognitions (adjustments) | Debit/Credit per type |

**Key insight:** Revenue doesn't need its own assertion. When you provide goods and receive (or are owed) payment, revenue emerges from that exchange pattern. But adjusting entries like depreciation and accruals have no exchange — they need `reports` to explicitly recognize calculated amounts."}

     {:heading "Transaction Categories"
      :content "Every transaction falls into one of these categories:

**Exchange Transactions** (L0-L1, L3, L5-L6):
- Always have a counterparty
- Use provides/receives for immediate exchanges
- Use requires/expects for future obligations
- Revenue emerges from the sale pattern — no separate assertion needed

**Internal Transformations** (L2):
- No counterparty
- Use consumes/creates for production
- is-allowed-by links to enabling equipment

**Adjusting Entries** (L4):
- No counterparty
- Use reports for calculated recognitions
- May use consumes (prepaid), requires (accruals)
- reports is needed because there's no exchange pattern to derive from"}

     {:heading "From Assertions to Journal Entries"
      :content "The beauty of assertive accounting: once you identify the correct assertions, the journal entry follows logically.

**Debit rules:**
- receives → Debit what comes in
- creates → Debit what's produced
- expects → Debit the receivable

**Credit rules:**
- provides → Credit what goes out
- consumes → Credit what's used up
- requires → Credit the payable

**The requires/expects asymmetry:**
- Credit purchase: **requires** only (SP controls payment)
- Credit sale: **requires** + **expects** (customer controls payment)
- Deferred revenue: **requires** only (SP controls delivery)
- Prepaid expense: **expects** only (vendor delivers service)

**Revenue:** Emerges from providing goods/services for monetary payment — the exchange pattern itself.

**Adjustments:** Use **reports** to explicitly recognize amounts where no exchange occurred."}

     {:heading "Ready for the Final Quiz"
      :content "This capstone quiz covers all levels. You'll see questions that require you to identify assertion patterns across different transaction types.

Once you pass, you'll have demonstrated mastery of the complete assertive accounting framework. Good luck!"}]

    :quiz
    [{:id :l7-q1
      :question "Which transaction type does NOT have a counterparty?"
      :choices ["Credit purchase of inventory" "Cash sale of goods" "Monthly depreciation adjustment" "Owner investment"]
      :correct 2
      :explanation "**Depreciation** is an adjusting entry — an internal recognition of asset value declining over time. No external party is involved. Purchases, sales, and owner transactions all involve counterparties."}

     {:id :l7-q2
      :question "SP sells t-shirts on credit. Which assertions are required?"
      :choices ["provides, receives, has-counterparty" "provides, requires, expects, has-counterparty" "provides, expects, has-counterparty" "provides, requires, reports, has-counterparty"]
      :correct 1
      :explanation "Credit sales need **provides** (goods delivered), **requires** (legal payment obligation), **expects** (confidence in payment), and **has-counterparty**. No receives (payment hasn't happened yet) and no reports (revenue emerges from the exchange pattern)."}

     {:id :l7-q3
      :question "SP buys raw materials on credit, produces finished goods, then sells them for cash. Which step uses 'consumes' and 'creates'?"
      :choices ["Buying raw materials" "Producing finished goods" "Selling finished goods" "Paying the vendor"]
      :correct 1
      :explanation "**Production** is the step that uses consumes (raw materials used up) and creates (finished goods produced). Buying and selling are exchange transactions; paying is settling an obligation."}

     {:id :l7-q4
      :question "Why do adjusting entries need the 'reports' assertion but sales don't?"
      :choices ["Because sales are less important" "Because sales have an exchange pattern that implies revenue; adjustments have no exchange to derive from" "Because reports is only for expenses" "Because sales only affect the balance sheet"]
      :correct 1
      :explanation "In a sale, revenue emerges from the exchange: providing goods for payment. In an adjusting entry (depreciation, accruals), there's **no exchange** — you must explicitly assert what's being recognized and how. That's what **reports** is for."}]}})

;; ==================== Accessors ====================

;; ==================== Practice drill configuration ====================
;; The mastery bar between a level's tutorial and Year 1 recording.
;; Raised from 4-of-5 for the GSU 2101 pilot population: more reps
;; before anything counts, at ~1-2 minutes per problem.

(def ^:private default-drill-config
  {:round-size 10 :pass-count 8})

(def ^:private drill-configs
  "Per-level overrides of the drill mastery bar."
  {})

(defn drill-config [level]
  (merge default-drill-config (get drill-configs level)))

(defn get-level-tutorial
  "Returns tutorial data for the specified level (0-7)."
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

(defn all-levels
  "Returns all tutorial level numbers in order."
  []
  (sort (keys level-tutorials)))

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
