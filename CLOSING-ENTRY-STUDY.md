# "Closing Entry" — Study of Alison Hollingsworth's Accounting Escape Room

*2026-09-09 (author + Claude Opus 5). Written from a full playthrough of
Level 1 (all five rooms, ~9½ minutes) of the preview at
`https://bolt.new/~/sb1-qkx55qzf`. Nothing in AALP was changed. This
document exists so the work can be picked up in a later session.*

Three questions, in order: what is the game, what should we borrow, and
what would it cost to make AALP feel like a sibling of it.

---

## 1. The game

**Name:** *Closing Entry: Accounting Escape*. React + Vite + Tailwind,
Supabase behind it (leaderboard, access codes, admin auth). Built in
Bolt, currently unpublished — it only runs inside the Bolt editor's
WebContainer preview.

### Shape

Five **levels** (Foundations → Core Principles → Advanced Practice →
Professional Standards → Expert Challenge). Each level is five
**rooms**. 25 rooms total, 5,500 max points. Level 1 is free with just a
nickname; levels 2–5 need an access code from the instructor.

Level 1's five rooms, in order, are the intro-financial arc:

| # | Room | Max | Task format |
|---|------|-----|-------------|
| 1 | Journal Entries | 200 | Build 5 journal entries from narratives; account dropdown + amount + Dr/Cr toggle per line |
| 2 | Account Classification | 150 | 15 accounts × 3 stages: classify (ALERE) → normal balance → which statement |
| 3 | Trial Balance | 200 | 11 accounts: net the ledger Dr/Cr, place each balance in the right TB column, foot both |
| 4 | Adjusting Entries | 200 | 5 scenarios tagged accrual / deferral / depreciation; one Dr account, one Cr account, one amount |
| 5 | Financial Statements | 250 | 3 steps: net income → ending capital → balance sheet totals, each feeding the next |

### The loop

Enter room → **orientation page** → do the work → "Room Clear!" +N pts →
next orientation. After room 5: confetti, total score, and **one digit of
a five-digit escape code**. One digit per level; finish all five levels
and you have the full code. That's the meta-goal, and it's the reason to
come back.

### Orientation pages

Every room opens with the same template before any input is asked for:

- a one-sentence framing ("Every financial event affects at least two accounts…")
- **How it works** — three numbered steps
- a mnemonic or framework panel (**DEAD / COIL** for Dr/Cr direction, **ALERE** for classification)
- a worked **example** in monospace, formatted like a real entry
- an **accounting-equation effect** panel showing A / L / E *before* and *after*
- a **key reminder** strip
- then, and only then, "Start Room N"

This is the strongest thing in the app. It is a genuine instructional
page, not a tooltip.

### Scoring

Score = room points + a time bonus, minus 50 per hint. My run: 1,000 room
points + 444 time bonus, 0 hints, = **1,444**. Leaderboard sorts on total
score and shows time and hint count alongside.

Notably: **wrong attempts cost nothing.** I deliberately botched
transaction 2 in room 1 (credited Service Revenue on a receivable
collection) and still finished the room at the full 200. The only
pressure is the clock and the hint budget.

### Chrome and affordances

- Fixed top bar: logo, ⏱ timer, 🏆 score, 💡 hints used (`0/3`), then Leaderboard / Admin / New Game.
- Five-node **room stepper** with padlocks on rooms not yet reached, check marks on cleared ones.
- Per-room header card: "Room 3 of 5", title, one-line subtitle, a `Key Concept:` chip, "Max Points: 200", and the hint button labelled with its price — **"Need a Hint? (−50 pts)"**.
- Within a room, a second progress line: "Transaction 3 of 5" + "Correct: 2/5" + a bar.
- A floating **calculator** button, bottom right, that opens a real keypad over the page.
- **Live validation** on the journal-entry form: a `Dr: $12,000 | ✓ Balanced | Cr: $12,000` strip appears as soon as the two sides agree, and "Check Answer" stays disabled until then.
- Collapsible reference panels that **swap by context** — the room-4 guide changes between Accrual, Deferral, and Depreciation as the adjustment type changes.
- Problems are **parameterised**: the same transaction came up as $12,000 on one run and $16,000 on the next.

### Instructor side

`Admin` is an email/password sign-in described as "Manage student access
codes." I did not sign in. So the teacher workflow appears to be: hand
out codes, watch the leaderboard. No item analytics visible from outside.

### Where it is weak

Worth knowing, because these are the places where AALP is already ahead:

1. **Feedback is undiagnostic.** Every wrong journal entry returns the same string: *"Incorrect accounts. Check which accounts are affected and which side they belong on."* It does not say which line is wrong, or what your entry would have meant. AALP's nearest-classification hinting is a generation better than this.
2. **Hints are generic and mispriced.** The room-1 hint is *"Remember: Debits are on the left, Credits on the right"* — the same for all five transactions. Worse, the modal **shows you the hint text before you pay for it**, so the 50-point charge is voluntary and pointless.
3. **A wrong answer is free.** Nothing accumulates against you, so there's no incentive to think before submitting. Guess-and-check clears every room.
4. **A reference/scenario mismatch.** Room 4, adjustment 4 is an unearned-revenue deferral, but the "Deferral" reference panel only ever shows the prepaid-expense pattern (Dr expense / Cr prepaid). A student following the panel literally gets it wrong.
5. **A state bug.** Opening and closing the hint modal dropped a journal-entry line I had already filled in.
6. **Two visual languages.** Landing page and rooms are dark slate; level select, leaderboard, and the completion screen are white. It reads as unfinished rather than deliberate.
7. **No persistence of work.** There is no ledger, no accumulated books, no consequences carrying between rooms — beyond the three steps inside room 5.

---

## 2. What we should borrow

The honest summary: **AALP's model is deeper; Alison's packaging is
better.** Almost everything worth taking is presentation and pacing, not
pedagogy.

### Take these

**A. The orientation page as a first-class screen.** Concept framing →
how it works → framework panel → worked example → *effect on the
accounting equation, before and after* → key reminder → start. AALP has
the tutorial/quiz system and the Guided Year, but the "what does this do
to A = L + E" panel is a clean, cheap, high-value addition, and it maps
directly onto assertive accounting: show what the *assertions* did to the
equation, before and after. This is the single best idea in her app.

**B. Live structural validation before submission.** The
`Dr / ✓ Balanced / Cr` strip with a disabled submit until the entry
balances. AALP's sentence builder should tell you, continuously, whether
what you've built is *well-formed* — separate from whether it is
*correct*. Cheap to add, removes a whole class of pointless failed
submissions.

**C. The stepper + "N of M" + "correct: X/Y" chrome.** Students always
know where they are, how much is left, and how they're doing. AALP's
level progression is currently much less legible on screen than it is in
the data model.

**D. Context-swapping reference panels.** Room 4 changes its reference
guide with the adjustment type. AALP's equivalent is obvious: surface the
assertion definitions relevant to *this* transaction's shape, collapsed
by default, rather than making the student carry the whole vocabulary.

**E. Staged decomposition within one object.** Room 2 asks the same 15
accounts three times — classify, then normal balance, then statement —
and each stage's answer is displayed as a given for the next. Room 5 does
the same across three statements. This is articulation made visible, and
it is exactly the move assertive accounting wants: assertions → derived
classification → derived entry, each stage carrying forward.

**F. The floating calculator.** Trivial, and students will use it.

### Take these, with modification

**G. Points and a clock.** Useful for a 2101 pilot, and it makes the two
apps feel like one course. But *invert her incentives*: in AALP, a wrong
submission should cost something and a hint should cost less, because
AALP's whole thesis is that reasoning beats guessing. Her scheme rewards
guess-and-check, which would actively undermine our assertion work.

**H. The escape-code meta-goal.** A five-part reward assembled across
five levels is genuinely motivating and costs almost nothing to build.
The AALP analogue could be assembling something with more meaning — a
complete set of financial statements for SP's year, one statement earned
per level — rather than an arbitrary digit.

**I. A leaderboard.** Only if it ranks on something we want more of.
Ranking on speed in AALP would be wrong. Rank on first-attempt accuracy,
or on unaided completions.

### Do not take

- **Undiagnostic feedback.** This is AALP's actual competitive advantage. Do not regress toward "that's wrong, try again."
- **Free wrong answers.** See G.
- **Access codes as the business model.** Not our problem.
- **The fixed 10-account chart of accounts.** AALP derives accounts from `physical-items`; that single-source-of-truth is worth more than her convenience.

---

## 3. Conforming AALP's look and feel

### What each app looks like now

**Closing Entry** — Tailwind defaults, competently used:

| Role | Value |
|---|---|
| Page background | `bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900` |
| Nav | `bg-slate-900/90 backdrop-blur-sm border-b border-slate-700`, fixed |
| Body text | white; muted `slate-400` (`#94a3b8`), secondary `slate-300` |
| Cards | `slate-800/50` on `border-slate-700`, `rounded-xl` |
| Primary action | `bg-gradient-to-r from-emerald-600 to-teal-600`, `rounded-xl`, `shadow-emerald-500/30` |
| Accent text | `emerald-400` (`#34d399`), `emerald-600` (`#059669`) |
| Warning / hints | `yellow-500` (`#eab308`) |
| Debit / Credit | emerald vs amber for the toggle; blue vs teal for balance-sheet vs income-statement |
| Type | **Inter**, 16px base, one family throughout |
| Container | `max-w-7xl` |

**AALP** — `resources/public/css/style.css`, 4,406 lines, **no CSS
custom properties at all**, and *two* visual languages layered on top of
each other:

1. **Legacy Flat-UI** (the practice and simulation screens): `#2c3e50`
   midnight blue, `#3498db` blue, `#27ae60` green, `#ecf0f1`/`#bdc3c7`
   greys, `#f5f5f5` page, system font stack. ~200 occurrences. This is
   the 2014 Bootstrap look and it is not deliberate.

2. **"Editorial Ledger"** (tutorial, quiz, report builder — the newer
   work), which the stylesheet documents by name at line 2656:

   > Typography: Source Serif 4 (headings) + DM Sans (body) + JetBrains Mono (assertions)
   > Palette: Deep navy (`#1a2332`), warm parchment (`#f9f6f0`), copper accent (`#b87333`),
   > sage success (`#4a7c59`), slate (`#64748b`)

   Fonts are already loaded in `index.html`. This layer is genuinely
   good and genuinely ours — it looks like a ledger and reads like a
   book, which is the right register for a system about articulation
   rather than arcade play.

### Decision (2026-09-09)

**Keep AALP's look and feel. Do not conform to Closing Entry.** Author's
call, after reviewing both. Phase 0 below (the token layer) is still
worth doing on its own merits, because it is what makes the two
competing AALP palettes into one; phases 1–3 are re-scoped as *structural*
borrowings, not visual ones.

Separately, the author has told Alison he'd like to sit down and settle a
single app brand for the whole department, so she is expecting that
changes may be asked of Closing Entry rather than of AALP. That
conversation supersedes the "one open question" at the end of this
section.

### Recommendation

**Do not repaint AALP as dark-slate-and-emerald.** That trades a
distinctive, meaningful, already-documented design language for Tailwind
defaults. Editorial Ledger says something about what the app is;
`slate-900 + emerald-600` says only "made in 2026."

What actually helps a student moving between the two apps is not palette.
It is **structural predictability**: where the score lives, what a
progress stepper looks like, where the "check my answer" button is, how a
correct/incorrect state announces itself. Adopt Alison's *layout
conventions*; keep our own *skin*.

So the plan is: **finish Editorial Ledger, borrow her structure.**

### Plan

**Phase 0 — Token layer (prerequisite for everything else)**

The blocker is that 4,406 lines of hardcoded hex cannot be re-themed. So:

1. Add a `:root` block at the top of `style.css` defining the Editorial
   Ledger palette and type scale as custom properties — `--ink`,
   `--parchment`, `--copper`, `--sage`, `--slate`, `--rule`,
   `--font-display`, `--font-body`, `--font-mono`, plus spacing and
   radius steps.
2. Mechanically replace the legacy Flat-UI hexes with token references,
   mapping: `#2c3e50` → `--ink`, `#3498db` → `--copper` (or a new
   `--accent`), `#27ae60` → `--sage`, `#e74c3c` → a new `--vermilion`,
   `#ecf0f1`/`#f5f5f5` → `--parchment` steps.
3. Set `body { font-family: var(--font-body) }` so DM Sans applies
   everywhere, not only in the tutorial overlay.

This one phase alone removes most of the "two apps in a trenchcoat"
feeling, and it is a find-and-replace with visual verification, not a
redesign.

**Phase 1 — Adopt the structural chrome**

4. **Persistent status bar.** Fixed top strip carrying: current level,
   score or mastery indicator, hints used, and the mode toggle. Mirror
   her information *order* so the two apps scan the same way.
5. **Level/stage stepper.** Numbered nodes, locked (padlock) / current /
   cleared (check), with a connecting rule. Smaller than first estimated:
   `stage-progress-indicator` (views.cljs:2807) already renders exactly
   this — current / complete / locked, with progress dots and check
   marks. What's missing is placement (it isn't in the persistent chrome)
   and the connecting rule. Restyling, not building.
6. **Task header card.** "Transaction 3 of 5" + title + one-line
   subtitle + a `Key Concept:` chip + the max points/mastery target +
   the hint control, priced on its face.
7. **Inner progress line** — "N of M" and "correct: X/Y" with a bar,
   above the work area.

**Phase 2 — Adopt the interaction patterns**

8. **Orientation screen per level**, built from the Editorial Ledger
   components that already exist in the tutorial system: framing → how it
   works → assertion-vocabulary panel → worked example → **effect on
   A = L + E, before and after** → key reminder → start.
9. **Live well-formedness strip** under the sentence builder: is the
   assertion set complete and internally consistent, independent of
   whether it's the right answer. Disable submit until well-formed.
10. **Context-scoped reference panel**, collapsible, showing only the
    assertions in play for the current transaction shape.
11. **Floating calculator**, same affordance as hers.
12. **Room-clear moment.** A brief, unmissable success state. AALP
    currently advances quietly; her "Room Clear! +200 pts" overlay is
    two seconds of payoff that costs nothing and is what students
    remember.

**Phase 3 — Optional game layer** *(only if we run a joint 2101 pilot)*

13. Points and a timer, with the incentives inverted — wrong submissions
    cost, hints cost less.
14. A cumulative reward assembled across levels: one financial statement
    for SP's year per level, rather than a digit.

### Cost

Phase 0 is a day of careful mechanical work plus a visual pass. Phase 1
is presentational and touches `views.cljs` plus the new tokens. Phase 2
is real feature work — item 8 is the largest, and item 9 needs a
well-formedness predicate in `classification.clj` that is distinct from
the correctness check. Phase 3 needs schema additions and should not be
started without a pilot to justify it.

### One open question for Alison

Do the two apps need to share a *palette*, or only a *shape*? If she is
willing to move Closing Entry onto Editorial Ledger, the convergence gets
much cheaper and much better-looking than the reverse. Worth asking
before Phase 0 starts, because it changes which direction the tokens
point.

---

## 4. The orientation page

*Worked out in conversation, 2026-09-09. This is the one borrowing from
Closing Entry we're confident about, and the version below is
substantially different from hers — better, because assertive accounting
gives it more to say.*

### The principle: AA is a language, not a nomenclature

Double-entry hands the student a **catalogue of names** and asks them to
match each event to one. The skill is lookup, and DEAD/COIL is a mnemonic
precisely because the rule underneath has no reason to it — you memorise
it because there is nothing to understand.

Assertive accounting hands the student a **closed vocabulary and a
grammar**. Accounts are not names to be matched; they are *meanings that
fall out of well-formed utterances about events*. The student is learning
to **speak**, not to look up.

The curriculum in `TUTORIAL-EPISODES.md` already encodes this and says so:

> **Two of the five episodes introduce no new assertions at all** and
> still produce accounts the student has not seen. That is the whole
> claim of the framework, arriving as the shape of the curriculum rather
> than as a paragraph about it.

The consequence for design: **episodes 2b, 3 and 3b are the most
important episodes, not the lightest ones.** Every instinct will be to
give a no-new-vocabulary episode a thinner orientation. Do the opposite.
Those are the episodes where the thesis is visible.

### The evidence, counted

From `classification.clj` — `available-assertions` (line 466) against
`classifications` (line 1317):

| Level | New assertions | New classifications |
|-------|----------------|---------------------|
| 0 | 5 | — |
| 1 | 2 | 6 |
| 2 | 7 | 9 |
| 3 | 1 | 1 |
| 4 | 4 | 9 |
| 5 | **0** | 6 |
| 6 | **0** | 5 |
| 7 | **0** | 5 |
| | **19 total** | **41 total** |

**The whole language is nineteen words, and it stops growing at level 4.**
Levels 5, 6 and 7 add sixteen new classifications and not one new
assertion.

This is not a curiosity. It means the minimal-pair treatment is not a
special case for a few episodes — it is **the dominant mode for the top
half of the curriculum**, three entire levels whose only content is
rearrangement. Whatever we build for episodes 2b/3/3b is the thing
levels 5–7 will run on.

### The centrepiece: minimal pairs

For an episode that introduces no new assertions, the main panel of the
orientation is a **minimal pair** — the linguist's instrument, and the
sharpest possible demonstration that meaning here is compositional:

> **You have said this before.** Episode 1: SP `provides` money,
> `receives` a stake in the business → **Owner's Capital**.
>
> **Today you say it again.** Episode 3: SP `provides` money, `receives`
> shirts → **Raw Materials Inventory**.
>
> **The only difference** is what the `receives` is denominated in. Same
> two words, same arrangement; a different account falls out. Nothing was
> added to the vocabulary — the vocabulary was *used differently*.

One pair shown is worth more than any number of paragraphs claiming it.
For episodes that *do* add vocabulary, the same slot holds the new word
in a sentence the student can already read, so the addition is felt as an
extension of something known rather than as a new rule.

### Anatomy

Six slots, mapped from hers, each with our version. Note that slots 3 and
5 are where a naive port would go wrong.

| # | Slot | Hers | Ours |
|---|------|------|------|
| 1 | Framing | one sentence on the concept | one sentence on what this episode lets the business *say* |
| 2 | Vocabulary | DEAD / COIL, ALERE — fixed mnemonics | the **cumulative** lexicon with today's additions marked; may legitimately read *"no new words today"* |
| 3 | How it works | an **algorithm**: find the accounts → apply the mnemonic → check it balances | a **reading protocol** over the event: what did the business give up? what did it get? what may it now do? what does it now owe? |
| 4 | Worked example | narrative → journal entry | the full chain — narrative → assertions → derived classification → derived entry, via `derived-je-panel`, clickable to the rule |
| 5 | Effect | three boxes: A / L / E, before and after | **two rows**: what SP *holds* before/after, and what SP *may or must do* before/after |
| 6 | Key reminder | one strip | unchanged |

**Slot 3 is the trap.** Her "how it works" is a procedure over accounts.
Porting that shape imports exactly the pedagogy AALP exists to replace.
Ours has to be a way of *reading the event*, and it must not resolve to
an account until the assertions have been made.

**Slot 5 is where we beat her outright.** She can only draw three boxes,
because three boxes is all double-entry has. We can draw a second row
that no journal entry anywhere records: what the business now `expects`,
`requires`, and `allows`. Episode 2 is the demonstration that writes
itself — SP buys the printer, the balance sheet barely twitches, and
`allows` opens an entire future. That one panel argues for the framework
better than any prose we could write.

### Ordering: the orientation *is* the gate's front page

Not before the gate, not after it. `tutorial-gate` (views.cljs:2771)
currently renders a title, a subtitle, one line of boilerplate, and two
buttons — including the test-out offer, *"Think you already know this?
Skip to the practice round."* The student is being asked to make a real
decision on a screen that tells them nothing about what the episode
contains.

Fill that screen with the orientation and the test-out mechanic starts
working as designed: vocabulary, reading protocol, worked chain and
before/after are all present, so "do I already know this?" becomes an
informed judgement instead of a gamble. No step is added to the flow — a
hollow one is filled. The gate still gates; both exits are unchanged.

Then the orientation **detaches and persists**: pinned and collapsible
inside the task itself, free, for the whole episode, and reachable
alongside `tutorial-review-button`.

### Redundant? No — one source, two tempos

- The **walkthrough** runs in *narrative time*. It moves the business
  forward and interleaves actions. Much of its payload lands once and
  only once: episode 1's "the business and SP are two different people"
  is a revelation, and nobody re-reads a revelation.
- The **orientation** runs in *reference time*. Atemporal, consultable,
  re-read because you forgot rather than because you failed to grasp.

The orientation is **standalone in one direction only.** A student who
takes the test-out path and never reads the walkthrough can work from the
orientation alone — that is what test-out means. The walkthrough is what
makes the orientation *mean* something, but it is spent once consumed.

### The help ladder

Most of this already exists. The orientation becomes rung 0.

| Rung | Surface | Cost | Scope |
|------|---------|------|-------|
| 0 | Orientation, pinned and collapsible | free, always | the episode |
| 1 | Walkthrough re-read — on demand, or via stuck detection | free | the targeted section |
| 2 | Derive the JE from *your own* assertions | free | your current attempt |
| 3 | `worked-example-panel` | forfeits the problem | the canonical answer |

Stuck detection must keep routing to **rung 1**, not rung 0. If the
orientation were sufficient the student would not be stuck.

### Keeping rung 0 and rung 3 apart

Both show a canonical chain, so distinguish them by *transaction*: the
orientation's example is fixed and author-chosen for the episode; the
drill's is the problem actually under attempt, and it costs the problem.
Different transaction, different price, no leak. **The orientation's
worked example must never be the transaction the student is about to be
asked to record** — only a sibling of it. Worked example, then variation.

### Authoring: one source

Each episode gets a **structured header** alongside the narrative body:

- vocabulary delta (may be empty — and empty is a headline, not a gap)
- the reading protocol for this episode
- a reference to the canonical worked example
- the before/after holdings and obligations
- the minimal pair

**Most of the first field already exists.** Episodes carry `:palette`
(`episodes.cljs:71`) — `#{:has-date :receives :provides :has-counterparty}`
for funding, the same plus `:allows` for printer. The delta is a
set-difference against the previous episode's palette. No new authoring
for slot 2, and the delta cannot drift from what the builder actually
offers, because it *is* what the builder offers.

The **orientation renders the header; the walkthrough renders the body.**
One file, two renderings, and they cannot drift apart over a semester.

That header is also the right place to stamp **instance identity** when
the platform grows to simulations of real companies — *which* set of
books this episode is being recorded into belongs at the threshold, next
to *what can be said here*. Crossed streams between datasets are the
failure mode to design against, and the threshold is where a student
either sees the boundary or doesn't.

### Consequence: the gate fires on vocabulary change

If the header carries the vocabulary delta, the gate no longer has to
fire on every episode — it fires when there are **new words to be tested
on**. Episodes 2b, 3 and 3b pass straight through with orientation only.

Less friction, and the gate means something when it does appear. It also
puts the framework's central claim into the *mechanics*: the episodes
that teach you the most are the ones that ask you to learn nothing new.

### Decisions (2026-09-10)

**1. The vocabulary panel shows the whole cumulative lexicon, always.**

Not scoped to the words in play. The "overwhelming" objection is empty —
nineteen words total, five at level 0 — but the real argument is the
other way round: **a scoped panel hides the exact fact we are trying to
teach.** A student who only ever sees the words currently in play never
gets to notice that the list stopped growing while the accounts kept
appearing. The whole-lexicon panel, with today's additions marked and the
marker sometimes empty, *is* the argument.

Two states per word: *new this episode* / *known*. Words not yet unlocked
are **not named** — progressive disclosure still holds — but the panel
carries a line to the effect of *"19 words in the whole language. You
have 5."* Finiteness is the point; enumeration is not.

**2. Minimal pairs come in two flavours. Episode 1 already has one.**

- **Historical pair** — this episode against an earlier one. Available
  from episode 2 onward. (`provides`/`receives` → Owner's Capital in
  episode 1; the same two words → Raw Materials Inventory in episode 3.)
- **Counterfactual pair** — this event against a near-miss that never
  happens in the curriculum. Available always, including episode 1.

Use a historical pair where one exists; fall back to counterfactual. Slot
4 is therefore never empty.

Episode 1 needs nothing written. Its `:choose` step (`episodes.cljs`,
funding) already *is* a counterfactual pair:

> *"What would have made that credit Revenue instead of Owner's
> Capital?"* → *"Shirts going out to them in exchange for it."*

Same event shape, one difference, different account.

Worth noting a second pair in the same episode, along a different axis:
the student asserts `provides` 200 ownership units and the narration
says *"Now look carefully. Nothing changed. The entry is exactly what it
was."* A change in the **chain** that produces no change in the
**entry**. That one is arguably the most important pair in the course,
and it belongs in episode 1's orientation too.

**3. Both scopes — episode and level — but one renderer.**

Episodes and levels are different axes, and a per-episode-only
orientation would leave two-thirds of the platform uncovered:

- `level-tutorials` (`tutorials.cljs:23`) covers **levels 0–7**.
- `episodes` (`episodes.cljs:69`) is **7 episodes**, and they belong to
  **guided mode only**.
- Practice mode and simulation mode work in *levels* and have no
  episodes at all. Levels 5–7 have no episodes to derive from.

So the orientation is a **renderer over a header**, not a screen: episode
header in guided mode, level header everywhere else. One component, two
scopes — which is why this doesn't become a fourth surface.

**Investment agreed:** add `:level` to each episode map, so a level's
header can be *composed* from the episodes inside it rather than authored
a second time. Without that mapping the one-source principle breaks at
exactly the seam where it matters most.

For levels 5–7, which have no episodes, the header is authored directly —
and since those levels add no assertions at all, their orientation is
almost entirely minimal pairs. The treatment that looked like a special
case for three episodes turns out to be the general one.

### What's left open

Nothing blocking. Two things to settle when the work starts:

- The exact wording of the reading protocol (slot 3) per episode. It must
  not resolve to an account before the assertions are made; beyond that
  it's a writing problem, not a design one.
- Whether the before/after panel's second row (`expects` / `requires` /
  `allows`) renders as prose or as a structured list. Episode 2 is the
  test case — if `allows` reads well there, it reads well everywhere.

---

## 5. Postscript — the paper managerial escape room (2026-09-18)

Alison also shared a **separate, printed** escape room: *BrewCo Coffee
Roasters: A Cost Accounting Crisis* — managerial, not financial. Teams of
2–4, 45–60 minutes, three physical combination locks. Three clue packets
(CVP → ABC → break-even/special order) each yield a 4-digit code; each
opened lock releases a sealed geography riddle; the three riddles trace a
spy's route (Vienna → Monrovia → Buenos Aires) along the historic coffee
trade. Seven documents: instructor key, three clue packets, answer sheet,
formula reference, riddle cards.

**All three lock codes verified exact** — 2450, 3456, 1260 — with no
rounding anywhere, including the ABC-vs-plantwide bonus ($72) and the
special-order margin ($7,200). Built backwards from the codes, carefully.

**Not sent back to Alison** — she is retiring the paper version, so the
production bug found during review (riddle answers printed in dark red
*inside* the sealed-envelope cards, defeating all three riddles) is moot.
Recorded here only so it isn't rediscovered.

**The usable parts have moved to `MEASUREMENT-CHOICE-DESIGN.md`**, which
this review prompted: the verified BrewCo scenario is preserved there as
a ready-made case, and the packet's one real weakness — a "break-even"
that silently excludes selling costs so the answer would be four digits —
turns out to be an *asset* in an assertive setting, where both figures
are computable from one record and the difference between them is the
assignment.

Two observations from this artifact that generalize, both already noted
against the app in §1 and reinforced here:

- **A padlock is a binary oracle.** It cannot say which input was wrong.
  Worse than the app's single generic string, and the same failure.
  AALP's derived journal entry is the better lock: it turns, and it
  explains itself.
- **The scored path and the thinking path come apart.** Part B — accept
  or reject the special order — is the real managerial judgment and gates
  nothing; the code comes from Part A alone, so a team racing the clock
  skips exactly the part worth doing. Same pattern as the app's
  cost-free wrong answers. Whatever we build, the interesting judgment
  has to be the thing that counts.
