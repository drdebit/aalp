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
| 1 | the business is funded | `has-date`, `receives`, `provides`, `has-counterparty` | Cash, Owner's Capital |
| 2 | SP buys a printer | `allows` | Equipment |
| 2b | SP buys a design | — none — | Design (Intangible Asset) |
| 3 | SP buys shirts and ink | — none — | Raw Materials Inventory |
| 3b | SP buys ink | — none — | Raw Materials Inventory (again) |
| 4 | SP prints shirts | `consumes`, `creates`, `is-allowed-by` | Finished Goods |
| 4b | the printer is serviced | — none — | Services Expense |
| 5 | SP sells shirts | — none — | Revenue, Cost of Goods Sold |

**Two of the five episodes introduce no new assertions at all** and still
produce accounts the student has not seen. That is the whole claim of the
framework, arriving as the shape of the curriculum rather than as a
paragraph about it: new classifications out of the same vocabulary,
arranged differently.

---

## Episode 1 — the business is funded

Two things have to land before the first assertion, because the rest of
the course leans on them.

**1.** *SP the person and SP's T-Shirts the business are two different
things. The business has its own money, owes its own debts, and keeps its
own books. That separation is the reason any of this works: if they were
the same, "SP puts money in" would just be SP moving money between
pockets.*

**2.** *And here is the part to hold on to: **you are keeping the
business's books.** Every assertion you make is the business saying what
happened to it. When the business receives $10,000, that is money
arriving — even though the person who handed it over is $10,000 poorer.*

**3.** Today the business is funded. SP puts in $20,000 and, in return,
receives a stake in the business.

**4.** *Every event starts with when it happened.*
→ set the date
*Good. That's an assertion — a plain statement about the world. It's true,
but on its own it doesn't say much.*

**5.** *The business received $20,000. Say so.*
→ add `receives` → money → 20000
*Two lines appeared at once. Cash on the debit side — money arriving is a
debit. And Owner's Capital on the credit side.*
*Why that second one? Money came in and nothing went out with it. The
business gave up no goods and took on no debt. What is left is a claim by
whoever put the money in. That is what equity is — not a kind of
transaction, but the part left over.*

**6.** *The business didn't get that money for nothing. SP received 200
ownership units in return. Say that too.*
→ add `provides` → ownership units → 200
*Now look carefully. **Nothing changed.** The entry is the same.*

**7.** *Scroll down to "Recorded — but not reflected".*
*There they are. Double-entry has no line for who owns the business. The
200 units are in the record, and later we will work out percentages from
them — but the journal entry cannot say it.*
*This is worth noticing early: the record holds more than the entry shows.*

**8.** *One more: who.*
→ add `has-counterparty` → SP
*The entry didn't change again. Counterparty doesn't get a line of its own
— it tells you which account fits, without ever appearing on one.*
*Later, when a second person invests, this is what keeps the two of them
apart.*

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


---

## A note on ownership units, and what is still unresolved

The paper's equity issuance provides `ownership-percentage`. Units are
used here instead, because a percentage is not a quantity: it does not
add, and a holder's percentage changes when somebody ELSE is issued
units, without that holder asserting anything. That is the same property
that made work in process a position rather than an assertion. Assert the
units; report the ratio, at a date.

This also makes the LLC / corporation difference a matter of which unit
is issued rather than a different structure, and extends to further
members for free: each issuance is an event with its own counterparty and
count.

`provides` is kept, deliberately, and it is a simplification we are
choosing rather than one we failed to notice. Every other use of
`provides` means the business gave up something it HAD, and the business
did not have 200 units lying around — it brought them into existence.
But a certificate does go to the shareholder, and something has to carry
the equity issuance's assertion set, so `provides ownership-units` stands
for now.

There is a mechanical reason to keep it as well. Without it, equity is
defined purely by ABSENCE: money in, and no goods out, no obligation, no
settlement of anything owed. Definitions by absence are fragile — a
collection that forgets `modifies` would look like equity. A positive
marker makes the classification say what it IS rather than what it is
not.

What equity actually seems to want is a verb the vocabulary does not
have. Debt is a promise and `requires` carries it. Equity enables, and
requires, and does several other things at once, because an issuance
encodes contractual terms: voting, distribution, liquidation preference,
transfer restrictions. That is a lot of assertion chain for an intro
class and almost none of it changes the journal entry — which is exactly
why simplifying here is right, and why the full version would be a good
exercise for a business law course rather than this one.

Left open, deliberately, with the simplification chosen on purpose.
