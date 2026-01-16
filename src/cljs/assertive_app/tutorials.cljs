(ns assertive-app.tutorials
  "Tutorial content for each level of the assertive accounting platform.")

(def tutorials
  {0 {:title "Level 0: Cash Transactions"
      :level 0
      :sections
      [{:heading "Learning Double-Entry Through Assertions"
        :content "Traditional accounting courses teach you to memorize journal entries for each transaction type. This application teaches you a better way: **identify what's true about the transaction (assertions), and the journal entry follows logically**.

You'll learn to recognize patterns: the same assertions always lead to the same journal entry."}

       {:heading "The Three Core Assertions"
        :content "Every exchange transaction has three key properties:

**1. provides** - What the entity gives up
**2. receives** - What the entity gets
**3. has-counterparty** - There's another party to the exchange

Each of these can involve either **monetary units** (cash, accounts payable/receivable) or **physical units** (equipment, inventory, services)."}

       {:heading "Assertions → Journal Entries"
        :content "Here's how assertions map to accounts:

**Cash Purchase of Asset:**
- provides: cash (monetary)
- receives: asset (physical)
→ Journal Entry: DR Asset, CR Cash

**Cash Sale/Revenue:**
- provides: goods/service (physical)
- receives: cash (monetary)
→ Journal Entry: DR Cash, CR Revenue

**Cash Expense:**
- provides: cash (monetary)
- receives: service consumed (physical)
→ Journal Entry: DR Expense, CR Cash

Notice the pattern: **what you receive gets debited, what you provide gets credited**."}

       {:heading "Why This Approach Works"
        :content "Instead of memorizing 'when you buy equipment, debit equipment and credit cash,' you learn to ask:
- What did we provide? (Cash → Credit)
- What did we receive? (Equipment → Debit)

This reasoning works for **every transaction**, not just the ones you've memorized. You're learning the logic, not just the answers."}]}

   1 {:title "Level 1: Credit Transactions"
      :level 1
      :sections
      [{:heading "Adding the Time Dimension"
        :content "Level 0 transactions happened immediately: you gave cash, you got the asset. But most business transactions involve **credit** - payment happens later.

This requires two new assertions:
- **requires** - Creates an obligation to do something in the future
- **expects** - Creates a right to receive something in the future"}

       {:heading "Credit Purchases → Accounts Payable"
        :content "When you buy on credit, you receive the asset now but will pay cash later:

**Assertions:**
- receives: asset (physical)
- requires: future payment (monetary)
- has-counterparty

**Journal Entry:** DR Asset, CR Accounts Payable

The 'requires' assertion tells you: **we owe something = liability (credit)**"}

       {:heading "Credit Sales → Accounts Receivable"
        :content "When you sell on credit, you provide goods/service now but will receive cash later:

**Assertions:**
- provides: goods/service (physical)
- expects: future payment (monetary)
- has-counterparty

**Journal Entry:** DR Accounts Receivable, CR Revenue

The 'expects' assertion tells you: **we're owed something = asset (debit)**"}

       {:heading "Prepaid Expenses"
        :content "When you pay for services in advance (like insurance), you'll receive the benefit later:

**Assertions:**
- provides: cash (monetary)
- expects: future service/benefit (physical)
- has-counterparty

**Journal Entry:** DR Prepaid Expense (Asset), CR Cash

You **expect to receive something** → Asset (debit). Notice: prepaid expense is an asset, not an expense!"}

       {:heading "The Pattern"
        :content "**Temporal assertions determine the account:**

- **requires** (you owe) → Liability → **Credit** the liability
- **expects** (you're owed) → Asset → **Debit** the asset

Plus the regular rules:
- **receives** → Debit what you get
- **provides** → Credit what you give

This logic works for every transaction."}]}

   2 {:title "Level 2: Production and Transformation"
      :level 2
      :sections
      [{:heading "Internal vs. External Transactions"
        :content "Levels 0 and 1 covered **exchange transactions** - trading with external parties (provides, receives, has-counterparty).

Level 2 introduces **internal transformations** - using resources to create new products. No counterparty, no exchange - just transformation."}

       {:heading "The Transform Assertions"
        :content "Production transactions use different assertions:

- **consumes** - Uses up resources (inputs)
- **creates** - Produces new resources (outputs)
- **asset-type** - Specifies what kind of asset

No counterparty assertion - this happens internally!"}

       {:heading "Manufacturing Journal Entries"
        :content "When you manufacture/produce, you're moving value between asset accounts:

**Example: Printing t-shirts**
- consumes: blank shirts (raw materials)
- creates: printed shirts (finished goods)

**Journal Entry:** DR Finished Goods, CR Raw Materials

**Pattern:** Debit the asset created, credit the asset consumed."}

       {:heading "Why Production is Different"
        :content "With exchanges:
- provides/receives → One account goes up, another goes down
- Always involves a counterparty

With production:
- consumes/creates → Value moves between YOUR asset accounts
- No counterparty - happens internally

Example: Using raw materials to make products moves value from 'Raw Materials Inventory' to 'Finished Goods Inventory'. Both are your assets."}

       {:heading "The Complete Framework"
        :content "You now have the full logic:

**Exchange assertions** (counterparty present):
- provides → Credit
- receives → Debit
- requires → Credit a liability
- expects → Debit an asset

**Transform assertions** (no counterparty):
- consumes → Credit (using up)
- creates → Debit (building up)

This covers every transaction type in financial accounting."}]}})

(defn get-tutorial [level]
  "Returns tutorial content for the specified level, or nil if no tutorial exists."
  (get tutorials level))

(defn tutorial-exists? [level]
  "Checks if a tutorial exists for the specified level."
  (contains? tutorials level))
