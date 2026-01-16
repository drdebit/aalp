(ns assertive-app.tutorials
  "Tutorial content for progressive learning in the simulation.

   Each stage introduces new assertion concepts and unlocks new transaction types.
   Students must complete tutorials and demonstrate mastery before advancing.")

;; ==================== Stage Definitions ====================
;; Stages control the progressive unlocking of content

(def stages
  "Stage definitions with tutorial content, unlocked actions, and mastery requirements."
  {1 {:title "Stage 1: Cash Transactions"
      :subtitle "Learn the basics of recording business exchanges"
      :unlocked-actions #{:purchase-materials-cash :purchase-equipment-cash}
      :mastery-required 3  ;; Number of successful transactions to advance
      :tutorial
      {:sections
       [{:heading "Welcome to SP's T-Shirt Business!"
         :content "You're now running SP's t-shirt printing company. Your job is to make business decisions and record them correctly using **assertions**—logical statements about what happened.

This is different from memorizing journal entries. Instead, you'll learn to identify what's true about each transaction, and the journal entry will follow logically."}

        {:heading "The Three Core Assertions"
         :content "Every exchange transaction has three key properties:

**1. has-counterparty** — There's another party to the exchange (a vendor, customer, etc.)

**2. provides** — What your company gives up in the exchange

**3. receives** — What your company gets in the exchange

Each of these involves either **monetary units** (cash) or **physical units** (equipment, inventory, services)."}

        {:heading "Your First Transactions: Cash Purchases"
         :content "Let's start simple. When SP buys something for cash:

**Example: Buying blank t-shirts for $500**
- has-counterparty: T-Shirt Supplier
- provides: $500 (monetary-unit)
- receives: Blank t-shirts (physical-unit)

**The Pattern:**
- What you **receive** gets **debited** (it's coming IN)
- What you **provide** gets **credited** (it's going OUT)

→ **Journal Entry:** DR Raw Materials Inventory, CR Cash"}

        {:heading "Try It Yourself"
         :content "You'll now see available actions on the right. Start by making some cash purchases:
- **Purchase Materials** — Buy blank t-shirts or ink
- **Purchase Equipment** — Buy the t-shirt printer

For each transaction:
1. Read the narrative carefully
2. Select the assertions that describe what happened
3. Fill in the parameters (what type of unit, etc.)
4. Submit and see if you got it right

After **3 successful transactions**, you'll unlock the next stage!"}]}}

   2 {:title "Stage 2: Credit Transactions"
      :subtitle "Add the time dimension to your transactions"
      :unlocked-actions #{:purchase-materials-cash :purchase-equipment-cash
                          :purchase-materials-credit :purchase-equipment-credit
                          :pay-vendor}
      :mastery-required 3
      :prerequisite-stage 1
      :tutorial
      {:sections
       [{:heading "Adding the Time Dimension"
         :content "In Stage 1, everything happened immediately: you gave cash, you got the asset. But most business transactions involve **credit**—payment happens later.

This requires two new assertions:

**requires** — Creates an obligation to do something in the future (you OWE)

**expects** — Creates a right to receive something in the future (you're OWED)"}

        {:heading "Credit Purchases → Accounts Payable"
         :content "When you buy on credit, you receive the asset now but will pay cash later:

**Example: Buy ink cartridges on 30-day credit**
- has-counterparty: Ink Supplier
- receives: Ink cartridges (physical-unit)
- requires: future payment of $100 (monetary-unit)

Notice: No **provides** yet—you haven't given anything! But you **require** (are obligated) to provide cash later.

**The Pattern:**
- **requires** (you owe something) → **Liability** → Credit

→ **Journal Entry:** DR Raw Materials Inventory, CR Accounts Payable"}

        {:heading "Paying Off Accounts Payable"
         :content "When you later pay the vendor:

- has-counterparty: Ink Supplier
- provides: $100 cash (monetary-unit)

This is straightforward—you're providing cash to satisfy the obligation.

→ **Journal Entry:** DR Accounts Payable, CR Cash

The liability goes away (debit) and cash goes out (credit)."}

        {:heading "The Temporal Pattern"
         :content "**Time-based assertions determine the account:**

| Assertion | Meaning | Account Type | Effect |
|-----------|---------|--------------|--------|
| **requires** | You owe something | Liability | Credit |
| **expects** | You're owed something | Asset | Debit |

Plus the regular rules:
- **receives** → Debit what you get
- **provides** → Credit what you give

This logic works for every transaction."}

        {:heading "New Actions Unlocked"
         :content "You can now:
- **Purchase on Credit** — Buy materials or equipment with future payment
- **Pay Vendor** — Pay off accounts payable

Complete **3 successful credit transactions** to unlock Stage 3!"}]}}

   3 {:title "Stage 3: Sales"
      :subtitle "Record revenue when you sell your products"
      :unlocked-actions #{:purchase-materials-cash :purchase-equipment-cash
                          :purchase-materials-credit :purchase-equipment-credit
                          :pay-vendor
                          :sell-tshirts-cash :sell-tshirts-credit
                          :collect-receivable}
      :mastery-required 3
      :prerequisite-stage 2
      :tutorial
      {:sections
       [{:heading "Time to Sell!"
         :content "You've purchased materials and equipment. Now let's record sales.

Sales are the mirror image of purchases:
- In a **purchase**, you **receive** goods and **provide** (or **require** to provide) cash
- In a **sale**, you **provide** goods and **receive** (or **expect** to receive) cash"}

        {:heading "Cash Sales"
         :content "When a customer pays immediately:

**Example: Sell 10 printed t-shirts for $250 cash**
- has-counterparty: Customer
- provides: 10 printed t-shirts (physical-unit)
- receives: $250 cash (monetary-unit)

**The Pattern:**
- You **provide** goods → Credit Revenue
- You **receive** cash → Debit Cash

→ **Journal Entry:** DR Cash $250, CR Revenue $250"}

        {:heading "Credit Sales → Accounts Receivable"
         :content "When payment comes later:

**Example: Sell t-shirts on 30-day credit**
- has-counterparty: Customer
- provides: 10 printed t-shirts (physical-unit)
- expects: future receipt of $250 (monetary-unit)

Notice: No **receives** yet! But you **expect** to receive cash later.

**The Pattern:**
- **expects** (you're owed) → **Asset** → Debit Accounts Receivable

→ **Journal Entry:** DR Accounts Receivable $250, CR Revenue $250"}

        {:heading "Collecting Receivables"
         :content "When the customer pays:

- has-counterparty: Customer
- receives: $250 cash (monetary-unit)

Simple—you're receiving cash that was expected.

→ **Journal Entry:** DR Cash, CR Accounts Receivable

The receivable goes away (credit) and cash comes in (debit)."}

        {:heading "The Symmetry"
         :content "Notice how purchases and sales mirror each other:

| Situation | Purchase | Sale |
|-----------|----------|------|
| Immediate | provides cash, receives goods | provides goods, receives cash |
| On credit | receives goods, requires payment | provides goods, expects payment |
| Settlement | provides cash (pay vendor) | receives cash (collect receivable) |

The same four assertions handle both sides!

Complete **3 successful sales** to unlock Stage 4!"}]}}

   4 {:title "Stage 4: Production"
      :subtitle "Transform raw materials into finished goods"
      :unlocked-actions #{:purchase-materials-cash :purchase-equipment-cash
                          :purchase-materials-credit :purchase-equipment-credit
                          :pay-vendor
                          :sell-tshirts-cash :sell-tshirts-credit
                          :collect-receivable
                          :produce-tshirts}
      :mastery-required 3
      :prerequisite-stage 3
      :tutorial
      {:sections
       [{:heading "Internal Transformations"
         :content "Stages 1-3 covered **exchange transactions**—trading with external parties using provides, receives, and has-counterparty.

Stage 4 introduces **internal transformations**—using your resources to create new products. No counterparty, no exchange—just transformation."}

        {:heading "The Transform Assertions"
         :content "Production transactions use different assertions:

**consumes** — Uses up resources (inputs to production)

**creates** — Produces new resources (outputs from production)

**is-allowed-by** — Links to the equipment that enables production

Notice: **No counterparty!** This happens entirely within your business."}

        {:heading "Printing T-Shirts"
         :content "When SP prints t-shirts:

**Example: Print 10 custom t-shirts**
- consumes: 10 blank t-shirts (raw materials)
- consumes: labor time
- creates: 10 printed t-shirts (finished goods)
- is-allowed-by: T-shirt Printer (equipment)

**The Pattern:**
- What you **create** gets **debited** (building up finished goods)
- What you **consume** gets **credited** (using up raw materials)

→ **Journal Entry:** DR Finished Goods Inventory, CR Raw Materials Inventory"}

        {:heading "Why Production is Different"
         :content "**With exchanges:**
- provides/receives → Value moves between you and someone else
- Always involves a counterparty

**With production:**
- consumes/creates → Value moves between YOUR OWN asset accounts
- No counterparty—happens internally

Both blank t-shirts and printed t-shirts are your assets. Production just changes the form of your inventory."}

        {:heading "Prerequisites for Production"
         :content "Before you can produce, you need:
1. **Raw materials** (blank t-shirts) — from purchasing
2. **Equipment** (t-shirt printer) — from purchasing

The simulation tracks this! You can't produce without the inputs.

Complete **3 successful production transactions** to master the basics!"}]}}

   5 {:title "Stage 5: Adjusting Entries"
      :subtitle "Match revenues and expenses to the correct period"
      :unlocked-actions #{:purchase-materials-cash :purchase-equipment-cash
                          :purchase-materials-credit :purchase-equipment-credit
                          :pay-vendor
                          :sell-tshirts-cash :sell-tshirts-credit
                          :collect-receivable
                          :produce-tshirts
                          :record-depreciation :adjust-prepaid :accrue-wages :accrue-interest}
      :mastery-required 3
      :prerequisite-stage 4
      :tutorial
      {:sections
       [{:heading "End-of-Period Adjustments"
         :content "At the end of each accounting period, we need to make sure revenues and expenses are recorded in the **correct period**. This is the matching principle.

Adjusting entries ensure:
- Expenses are recognized when incurred (not just when paid)
- Revenues are recognized when earned (not just when received)
- Assets reflect their current value"}

        {:heading "The 'reports' Assertion"
         :content "Adjusting entries introduce a new assertion:

**reports** — Records a calculated amount based on some method or basis

Unlike exchanges (provides/receives) or transformations (consumes/creates), adjusting entries are about **recognizing** economic reality that hasn't yet been recorded.

Common bases:
- **systematic-allocation** — Depreciation (spreading cost over time)
- **estimation** — Bad debts (predicting future losses)
- **accrual** — Wages/interest (recognizing expense before payment)
- **time-based** — Prepaid expenses (recognizing expired benefits)"}

        {:heading "Depreciation"
         :content "Equipment loses value over time. We allocate its cost over its useful life:

**Example: Monthly depreciation on $3,000 printer with 5-year life**
- has-date: End of month
- reports: expense (systematic-allocation basis)
- consumes: asset-value

**The Pattern:**
- Asset value is consumed (credited via contra-account)
- Expense is recognized (debited)

→ **Journal Entry:** DR Depreciation Expense, CR Accumulated Depreciation

Note: Accumulated Depreciation is a **contra-asset** that reduces equipment value."}

        {:heading "Accrued Expenses"
         :content "Some expenses are incurred before they're paid:

**Wages Accrual** — Employees worked, but payday hasn't arrived
- reports: expense (accrual basis)
- requires: future payment

**Interest Accrual** — Interest accumulates daily on loans
- reports: expense (accrual basis)
- requires: future payment

**The Pattern:**
- **requires** → Creates a liability (wages/interest payable)
- **reports** → Recognizes the expense now

→ **Journal Entry:** DR Expense, CR Payable"}

        {:heading "Prepaid Expense Adjustments"
         :content "When you prepay for something (insurance, rent), you have an asset. As time passes, you 'use up' the prepayment:

**Example: $1,200 annual insurance, one month used**
- has-date: End of month
- reports: expense (time-based)
- consumes: prepaid-benefit

**The Pattern:**
- The prepaid asset is consumed (credited)
- The expense is recognized (debited)

→ **Journal Entry:** DR Insurance Expense $100, CR Prepaid Insurance $100"}

        {:heading "No Counterparty!"
         :content "Like production, adjusting entries have **no counterparty**. They don't involve trading with anyone—they're internal recognitions of economic changes.

Key differences from exchanges:
- No provides/receives (nothing changing hands)
- Uses **reports** to recognize calculated amounts
- May use **consumes** for using up prepayments/asset value
- May use **requires** to recognize new obligations

Complete **3 successful adjusting entries** to unlock Stage 6!"}]}}

   6 {:title "Stage 6: Equity Transactions"
      :subtitle "Record owner investments, withdrawals, and dividends"
      :unlocked-actions #{:purchase-materials-cash :purchase-equipment-cash
                          :purchase-materials-credit :purchase-equipment-credit
                          :pay-vendor
                          :sell-tshirts-cash :sell-tshirts-credit
                          :collect-receivable
                          :produce-tshirts
                          :record-depreciation :adjust-prepaid :accrue-wages :accrue-interest
                          :owner-invest :issue-stock :declare-dividend :pay-dividend :owner-withdraw}
      :mastery-required 3
      :prerequisite-stage 5
      :tutorial
      {:sections
       [{:heading "Owner Transactions"
         :content "So far, we've focused on operating transactions—buying, selling, producing. Now we'll record transactions with **owners**:

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

**The Pattern:**
- Company **receives** cash → Debit Cash (asset increases)
- Company **provides** ownership → Credit Owner's Capital (equity increases)

→ **Journal Entry:** DR Cash $20,000, CR Owner's Capital $20,000

Note: This isn't revenue! The company isn't earning money—it's receiving investment."}

        {:heading "Stock Issuance (Corporations)"
         :content "For corporations, stock issuance is similar but uses different accounts:

**Example: Issue 1,000 shares at $10 each**
- has-counterparty: Investor
- receives: $10,000 (monetary-unit)
- provides: stock-shares

→ **Journal Entry:** DR Cash, CR Common Stock

The company receives cash in exchange for shares of ownership."}

        {:heading "Dividend Declaration & Payment"
         :content "Dividends return profits to owners. It's a **two-step process**:

**Step 1: Declaration** (Board declares dividend)
- reports: distribution (declared)
- requires: future cash payment

→ **Journal Entry:** DR Retained Earnings, CR Dividends Payable

The company now has an obligation to pay.

**Step 2: Payment** (Cash paid to shareholders)
- has-counterparty: Shareholders
- provides: cash (monetary-unit)
- fulfills: the dividend obligation

→ **Journal Entry:** DR Dividends Payable, CR Cash"}

        {:heading "Owner Withdrawals"
         :content "In sole proprietorships and partnerships, owners can withdraw funds:

**Example: SP withdraws $1,000 for personal use**
- has-counterparty: SP (owner)
- provides: $1,000 (monetary-unit)

**The Pattern:**
- Cash goes out → Credit Cash
- Owner's equity decreases → Debit Owner's Drawing

→ **Journal Entry:** DR Owner's Drawing, CR Cash

Note: This is NOT an expense! It's a return of capital to the owner."}

        {:heading "The Equity Pattern"
         :content "**Key insight:** Equity transactions change the **composition** of the balance sheet without affecting income.

| Transaction | Effect on Assets | Effect on Equity |
|-------------|-----------------|------------------|
| Investment | + Cash | + Capital |
| Withdrawal | - Cash | - Drawing |
| Dividend Declaration | No change | - Retained Earnings, + Payable |
| Dividend Payment | - Cash | - Payable |

Complete **3 successful equity transactions** to unlock Stage 7!"}]}}

   7 {:title "Stage 7: Notes & Interest"
      :subtitle "Borrow and lend with formal promissory notes"
      :unlocked-actions #{:purchase-materials-cash :purchase-equipment-cash
                          :purchase-materials-credit :purchase-equipment-credit
                          :pay-vendor
                          :sell-tshirts-cash :sell-tshirts-credit
                          :collect-receivable
                          :produce-tshirts
                          :record-depreciation :adjust-prepaid :accrue-wages :accrue-interest
                          :owner-invest :issue-stock :declare-dividend :pay-dividend :owner-withdraw
                          :borrow-note :repay-note :pay-interest :lend-note}
      :mastery-required 3
      :prerequisite-stage 6
      :tutorial
      {:sections
       [{:heading "Formal Borrowing & Lending"
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

**The Pattern:**
- **receives** cash → Debit Cash
- **requires** repayment → Credit Notes Payable

→ **Journal Entry:** DR Cash $10,000, CR Notes Payable $10,000

Notice: This is like a credit purchase, but the obligation is a **formal note**, not just accounts payable."}

        {:heading "Interest Over Time"
         :content "Interest accrues continuously on borrowed money. Even if you don't pay it yet, you owe it:

**Monthly interest on $10,000 at 8%:**
- $10,000 × 8% ÷ 12 = $67/month

**Accrual entry (from Stage 5):**
- reports: expense (accrual)
- requires: future payment

→ **Journal Entry:** DR Interest Expense, CR Interest Payable

**Payment entry:**
- provides: cash
- fulfills: the interest obligation

→ **Journal Entry:** DR Interest Payable, CR Cash"}

        {:heading "Repaying the Note"
         :content "When the note matures, you repay the principal:

**Example: Repay $10,000 note**
- has-counterparty: Bank
- provides: $10,000 (monetary-unit)
- fulfills: the note obligation

→ **Journal Entry:** DR Notes Payable, CR Cash

The **fulfills** assertion connects this payment back to the original borrowing."}

        {:heading "Lending with a Note Receivable"
         :content "SP can also be the lender:

**Example: Lend $5,000 to supplier**
- has-counterparty: Supplier
- provides: $5,000 (monetary-unit)
- expects: future repayment with interest

→ **Journal Entry:** DR Notes Receivable, CR Cash

**Interest revenue accrual:**
- reports: revenue (accrual)
- expects: future receipt

→ **Journal Entry:** DR Interest Receivable, CR Interest Revenue"}

        {:heading "The Complete Picture"
         :content "You've now mastered the full ACCT 2101 transaction cycle:

| Stage | Focus | Key Assertions |
|-------|-------|----------------|
| 1 | Cash exchanges | provides, receives, has-counterparty |
| 2 | Credit | requires, expects |
| 3 | Sales | provides goods, receives/expects cash |
| 4 | Production | consumes, creates, is-allowed-by |
| 5 | Adjusting | reports, accruals, depreciation |
| 6 | Equity | owner transactions, dividends |
| 7 | Notes | formal borrowing, interest |

**Congratulations!** You've completed the simulation course."}]}}})

;; ==================== Helper Functions ====================

(defn get-stage [stage-num]
  "Returns stage data for the specified stage number."
  (get stages stage-num))

(defn get-tutorial [stage-num]
  "Returns tutorial content for the specified stage."
  (get-in stages [stage-num :tutorial]))

(defn stage-exists? [stage-num]
  "Checks if a stage exists."
  (contains? stages stage-num))

(defn get-unlocked-actions [stage-num]
  "Returns the set of actions unlocked at this stage."
  (get-in stages [stage-num :unlocked-actions] #{}))

(defn get-mastery-required [stage-num]
  "Returns the number of successful transactions required to advance."
  (get-in stages [stage-num :mastery-required] 3))

(defn get-prerequisite [stage-num]
  "Returns the prerequisite stage number, or nil if none."
  (get-in stages [stage-num :prerequisite-stage]))

(defn all-stages []
  "Returns all stage numbers in order."
  (sort (keys stages)))

(defn max-stage []
  "Returns the highest stage number."
  (apply max (keys stages)))

;; ==================== Legacy Level-Based Tutorials ====================
;; Kept for backward compatibility with practice mode

(def tutorials
  "Level-based tutorials for practice mode (legacy).
   Maps level numbers to tutorial content."
  {0 {:title "Level 0: Cash Transactions"
      :level 0
      :sections (:sections (get-in stages [1 :tutorial]))}

   1 {:title "Level 1: Credit Transactions"
      :level 1
      :sections (:sections (get-in stages [2 :tutorial]))}

   2 {:title "Level 2: Production and Transformation"
      :level 2
      :sections (:sections (get-in stages [4 :tutorial]))}})

(defn get-level-tutorial [level]
  "Returns tutorial content for the specified level (legacy practice mode)."
  (get tutorials level))

(defn level-tutorial-exists? [level]
  "Checks if a tutorial exists for the specified level."
  (contains? tutorials level))
