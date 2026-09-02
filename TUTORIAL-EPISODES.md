# The walkthrough: SP, episode by episode

The teaching spine is the business, not the vocabulary. Episodes run in
the order the business runs, because the record already enforces that
order: you cannot provide money you have not raised, cannot hold
inventory without something to use it for, cannot produce without
materials, cannot sell what you have not made.

Practice ("your turn") stays organised by pattern, with fresh scenarios.
**Process teaches; pattern trains.**

Legal structure stays in the background. SP is a point-of-sale business,
so contractual assertions for revenue recognition are elided for now —
they belong later, as another way of looking at events already recorded.

## The arc

| # | episode | new assertions | what appears |
|---|---------|----------------|--------------|
| 1 | SP puts money in | `has-date`, `receives`, `has-counterparty` | Cash, Owner's Capital |
| 2 | SP buys a printer | `provides`, `allows` | Equipment |
| 3 | SP buys shirts and ink | — none — | Raw Materials Inventory |
| 4 | SP prints shirts | `consumes`, `creates`, `is-allowed-by` | Finished Goods |
| 5 | SP sells shirts | — none — | Revenue, Cost of Goods Sold |

**Two of the five episodes introduce no new assertions at all** and still
produce accounts the student has not seen. That is the whole claim of the
framework, arriving as the shape of the curriculum rather than as a
paragraph about it: new classifications out of the same vocabulary,
arranged differently.

---

## Episode 1 — SP puts money in

**1.** SP wants to start printing t-shirts. Before anything else, SP needs
money to work with. Today SP puts $10,000 of their own savings into the
business.

**2.** *Every event starts with when it happened.*
→ set the date
*Good. That's an assertion — a plain statement about the world. It's true,
but on its own it doesn't say much.*

**3.** *The business received $10,000. Say so.*
→ add `receives` → money → 10000
*Two lines appeared at once. Cash on the debit side — money arriving is a
debit. And Owner's Capital on the credit side.*
*Why that second one? Money came in and nothing went out with it. SP gave
up no goods and took on no debt. What's left is a claim by whoever put the
money in. That's what equity is — not a kind of transaction, but the part
left over.*

**4.** *One more thing worth recording: who.*
→ add `has-counterparty` → SP
*Notice the entry didn't change. Counterparty doesn't get a line of its
own — it tells you which account fits, without ever appearing on one.*

---

## Episode 2 — SP buys a printer

**1.** SP has $10,000. Today SP spends $3,000 of it on a t-shirt printer.

**2.** *Start with when.*
→ set the date

**3.** *SP paid $3,000. Say so.*
→ add `provides` → money → 3000
*Cash again, but on the credit side this time. Money leaving is a credit.
You didn't pick that — it followed from what you said.*

**4.** *Something has to balance it. What did SP get?*
→ add `receives` → a thing → t-shirt printer → 1
*Hmm. Still not finished. The record knows SP got a printer. It doesn't
know what to call it.*

**5.** *Here's the interesting part. A printer isn't automatically
equipment. A shop that resells printers would call the very same machine
inventory.*
*So the record has to say what this one is for.*

**6.** *SP bought it to turn blank t-shirts into printed ones. Say that.*
→ add `allows` → consumes blank t-shirts → creates printed t-shirts
*There it is. Equipment. Not because printers are equipment — because you
said this one is productive.*

**7.** *Who did SP buy it from?*
→ add `has-counterparty`
*Good. It balances. Every line came from something you said.*

**8.** *Try something. Switch `allows` off and watch.*
→ Explore toggle
*Equipment disappeared. The record stopped saying what the machine is for,
so it stopped knowing what to call it. Switch it back on.*

**9.** *Tomorrow SP buys blank t-shirts. Because of what you said today,
the record will already know what they're for.*

---

## Episode 3 — SP buys shirts and ink  *(no new assertions)*

The payoff. Same four assertions as episode 2, minus `allows`. The shirts
land in Raw Materials Inventory without the student saying anything about
what they are, and the drill-down explains why: *"2026-01-02 — you said
this turns blank t-shirts into printed t-shirts."*

Worth doing twice, once for shirts and once for ink, so that "raw
materials" is plainly about the role and not about shirts.

The step to write carefully is the one that asks *why* it said Raw
Materials Inventory, and sends the student to the drill-down to find an
answer that lives four days earlier.

## Episode 4 — SP prints shirts

Three new assertions, and the first event with no counterparty at all —
nothing leaves the business. Consuming two different things is the point:
one entry, a credit per input, and the finished goods absorbing the total.
Cost arrives here without ever being typed in: it comes from what SP paid
in episode 3.

## Episode 5 — SP sells shirts  *(no new assertions)*

`provides` goods instead of money, and Revenue appears — a word the
student has never asserted and cannot assert. Then the cost side, priced
from episode 4, which is where "what did they cost" becomes a question
with an answer already in the record.
