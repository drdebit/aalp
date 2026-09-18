# Measurement choice — one record, many defensible answers

*2026-09-18 (author + Claude Opus 5). Prompted by Alison Hollingsworth's
paper managerial escape room (BrewCo Coffee Roasters), now being retired;
the usable parts are preserved here. Extends a thread from an earlier
session on the GAAP asset definition. Nothing built yet — this is the
design record for a family of exercises the platform can already almost
support.*

## The principle

We teach students that a number has *a* way of being calculated. It
doesn't. It has inputs, and **which inputs you admit is a judgment made
in context** — one that a competent practitioner has to make, defend, and
be able to redo differently when the context changes.

Traditional instruction hides this, because the textbook hands over a
table of costs and the table *is* the scope decision, already made by
someone else, invisibly. The student executes a formula over a given and
never sees that the given was a choice.

Assertive accounting can expose it, because **the record holds more than
any one report draws from**. Scope is not baked into the ledger; it is
asserted at reporting time, by the student, with `collects` / `includes`
/ `excludes`, and recorded with provenance. Change the exclusions and you
get a different, equally recorded, equally defensible number — and the
difference between the two is the lesson.

## Why this is native here, and not elsewhere

The Report Builder already demonstrates this once, for revenue:

> Students discover the two revenues differ only in their exclude chips,
> over identical events — the multiple-definitions demonstration,
> experienced before explained.
> — `REPORT-BUILDER-DESIGN.md`, Stage 1

That is the whole idea, currently applied to exactly one pair (accrual
vs. cash revenue). What follows generalizes it along three axes. **No new
vocabulary is required** — which, per the frozen-vocabulary constraint in
`CURRICULUM-ROADMAP.md`, is the point: this is the same words arranged
differently, at the reporting layer rather than the recording layer.

Double-entry cannot do this without re-keying the books, because a
journal entry collapses the judgment into the number at the moment of
recording. Once bad debt is a single allowance figure, the per-customer
confidences that produced it are gone. In AALP they are still in the
record, one per event.

## What already exists

Most of the machinery is shipped. This is assembly, not construction.

| Capability | Where | State |
|---|---|---|
| Compose scope as `includes`/`excludes` over own ledger, preview freely | `POST /api/engine/compose/preview` | shipped |
| Record a composition as a first-class event with provenance and input back-refs | `POST /api/engine/compose/record` | shipped |
| Per-event confidence on every `expects` (`has-confidence-level`, 0–1) | `classification.clj:648` ff. | shipped |
| Receivables retrievable *with* their confidences | `simulation.clj:1385` `get-receivables-summary` | shipped |
| Expected loss per receivable = amount × (1 − confidence) | `simulation.clj:1450` ff. | shipped |
| Aging as a *reading* over the same events, needing no new assertion | `chain.clj:492` `aging` | shipped |
| Multiple calculation bases already coexisting for one number | `classification.clj:247` `calculation-schemas` | shipped |
| Builder UI, DSL mirror, exemplar presets, L3 gating | Report Builder v1 | shipped |
| Machine identity per production event, as provenance | `example.clj:252` `is-allowed-by` → equipment purchase | in research example |
| Lot identity on consumed materials | `example.clj:253` `{:unprinted-t-shirt "TShirtPurchase-001"}` | in research example |
| Cost-basis selection over lot-identified consumption | `cost_basis.clj` | shipped |
| Energy as a denomination (`kWh`) | `schema.clj:57` | research schema only — **not in app** |
| A third party's conversion factor, recorded with provenance | `example.clj:606` `EmissionsFromElectric` | in research example |

The comment at `chain.clj:500` is already reaching for this idea:

> A confidence is what somebody judged about one customer at the time of
> one sale; an age is what the record can see now, about every customer
> at once, without anyone having judged anything.

Two routes to the same quantity, differing in what they admit as
evidence. Worth having **beside** each other, not instead.

---

## Family A — Scope choice: which costs count

**The exercise.** Compute break-even (or CVP, or a product cost) over the
student's own recorded events, more than once, admitting different cost
sets each time. Then ask which one answers the question actually asked.

This is the backward extension of a BrewCo-style problem: instead of
being handed a cost table, the student *collects* costs from events they
recorded, and the collection is the work.

**The error as the asset.** Alison's Lock 3 instructs students to compute
break-even using manufacturing costs only — excluding fixed S&A and
variable selling. It yields 1,260 bags. The company's real break-even is
2,368. Lock 1, in the same packet, correctly uses all variable costs. So
the packet teaches a full-cost CM and a partial-cost CM with no
explanation of the difference.

In a paper escape room that is a flaw (the partial figure exists only to
make the lock code four digits). **In AALP it is the assignment.** Both
numbers are computable from one record; both are recordable as reports
with their selection logic attached; and the question "why are these
different, and when would a manager want each?" is a better question than
either number. A manufacturing break-even is a real thing — it answers
"can the plant cover its own fixed overhead?" — it is simply not the
company's break-even, and a student who can say why has learned something
a formula cannot teach.

**Progression.** Read two given compositions → modify one chip and
observe → compose both from blank and defend the choice. (The Report
Builder's existing three-stage pedagogy, unchanged.)

## Family B — Withheld granularity: proxy, then reveal

**The exercise.** The simulation knows the truth at a granularity the
student is not given. Which machine printed each shirt. Electricity drawn
per run. Minutes of labor per unit. The system then says, in effect:

> *Assume this information isn't available. What would be a reasonable way
> to estimate it?*

The student builds a proxy — allocate by machine hours, by units, by
direct labor, by a plantwide rate — records it as a report with its basis
asserted, and gets a number.

**Then the system reveals the truth it was holding**, and shows the gap.

**Why the reveal is the whole thing.** Every allocation method students
meet is taught as a procedure, and the honest question — *how wrong is
it?* — is unanswerable in a textbook, because the textbook has no ground
truth either. Here the platform *does*. It can say: your plantwide rate
put $3,384 on Espresso Blend; ABC put $3,456; the actual traced
consumption was $X. It can show which product each method over- and
under-costs, and by how much, against a truth that exists.

That converts "ABC is more accurate" from an assertion the student has to
take on faith into a measurement the student takes themselves. It also
teaches the real lesson: a proxy is not a failure, it is a priced
decision, and the price is knowable.

### What the SP example already has (checked 2026-09-18)

Electricity is in the research example — but **not in `consumes`**, and
the gap is the opportunity.

- **`kWh` is a first-class denomination.** `schema.clj:57–58`:
  `is-denominated-in-unit → is-denominated-in-energy-unit → :kWh`,
  alongside monetary, physical, time, effort and intellectual units.
- **The production recipe does not consume it.** The printer's `allows`
  block (`example.clj:116–128`) and the actual print event
  (`Printed-001`, line 252) each consume exactly four things: one
  unprinted shirt, 1/100 ink cartridge, 1/60 hour, one unit of employee
  commitment. No energy.
- **Electricity enters as `receives`,** bought in bulk from LocalElectric,
  and the per-shirt figure is *computed* in `emissions-per-shirt-calc`
  (`example.clj:620`):

  ```
  unit-emissions × (sums :kWhs ÷ sums :printed-t-shirts)
  ```

  Total kWh received ÷ total shirts created. **That is a plantwide rate,
  run on energy instead of dollars.** It cannot know which machine or
  which shirt drew more power.

So **the proxy side of Family B is already modeled**, in the canonical
research example, without anyone having set out to model it. What is
missing is the ground truth to reveal against — bulk `receives` ÷
`creates` never produces a per-shirt truth.

**Which makes Family B cheap.** Adding kWh to the recipe's `consumes` is
a *data* change: a fifth entry in an existing vector, using two
constructs that already exist. No schema work and no new vocabulary. It
then yields precisely the structure the exercise needs — a
`consumes`-traced figure and a `receives ÷ creates` averaged figure, two
routes to one number differing in what they admit as evidence.
Structurally the same pairing as confidence-vs-aging at `chain.clj:500`.

**One port needed.** All of the above lives in the *research* reference
(`example.clj`, `schema.clj`). The app has no energy unit — `unit-type-options`
(`classification.clj:123`) offers monetary, physical, time, effort,
service and ownership only. Adding `energy-unit` adopts an existing
branch of the research vocabulary rather than inventing one, so it stays
inside the frozen-vocabulary constraint.

### Two things the example already does better than this design assumed

**Machine identity is present — as provenance, not as an attribute.**
`Printed-001` carries `:is-allowed-by "EquipmentPurchase-001"`, so every
printed shirt points at the equipment that authorized it; with several
printers, each with its own purchase event, the record knows which one
ran. Likewise the consumed shirt is `{:unprinted-t-shirt "TShirtPurchase-001"}`
— lot-level traceability, already there. That is also what makes
specific-identification vs. FIFO a **Family A** exercise over the same
events (`cost_basis.clj` exists).

**The emissions conversion factor is an event, not a constant.**
LocalElectric asserts 0.855 lb CO₂ per kWh in `EmissionsFromElectric`,
which `modifies`/`fulfills` an information request SP made of them
(`example.clj:586`, `606`). The factor therefore carries provenance from
a third party, is dated, and is revisable — and if it is revised, every
report built on it changes and the student can trace exactly why.

This is a stronger demonstration than the proxy-and-reveal exercise as
originally drafted, and it should lead. A student who has only ever met
conversion factors as constants printed in a textbook has no way to ask
*who says so, when did they say it, and what happens to my numbers if
they change their mind?* Here all three are answerable by walking the
chain. **Measurement choice is not only about which of your own events
you admit; it is also about whose assertions you are standing on.**

### How the reveal happens (decided 2026-09-18)

The question was whether to withhold granular data by **display policy**
(record it, have the UI refuse to show it) or **generation policy** (a
coarse student ledger and a fine instructor-held one). **Neither.** Both
are wrong, for reasons worth keeping:

- *Display policy leaks, in four places.* `format-event-for-response`
  (`engine.clj:211`) returns the full `:aalp-assertions` set on
  `/api/engine/event/:id`, `/api/engine/chain/:id`, `/api/engine/events`,
  and in the `:events` of `/api/engine/compose/preview`. Two of those
  power chain traversal and input click-through — *designed* pedagogical
  features. Hiding kWh means filtering it out of the very view whose
  purpose is showing what feeds a number.
- *Display policy is also incoherent.* It means the student's own
  business metered its own shop and the student is not allowed to see the
  reading. That is a blindfold, not a judgment exercise, and a sharp
  student is right to find it silly.
- *Generation policy crosses the streams.* Two ledgers for one business
  breaks the rule that a student can always see what feeds into what.

**Instead, make the reveal an act in the world.** The granular draw is
not hidden — it *does not exist in SP's record*, because nobody
submetered. The student proxies from bulk purchases, and the truth
arrives because somebody asks for it and a third party furnishes it.

The SP example already has the pattern:
`ElectricEmissionsInformationRequest` is a `requires` that LocalElectric
`fulfills` (`example.clj:586`, `606`). Nothing is invented.

This yields: nothing to leak, one ledger, full inspectability, and a
reveal that is itself a recorded event with provenance and a date.

**Two tiers, decided:**

- **Undergraduate — the utility report.** A third party can furnish
  interval data for a period *already past*, so the traced figure and the
  student's proxy cover the same period. Clean comparison, no
  confounding. This is the default and the one to build.
- **Graduate — paying for measurement.** The student decides whether to
  install submetering: an event, with a cost in money and in SP's time
  (the pattern already exists — the environmental report `consumes` two
  hours of SPHire). Finer measurement begins only from the date it is
  paid for.

The graduate tier introduces **period confounding** — a pre-submetering
proxy compared against post-submetering reality — and that is a feature,
not a defect. Students meet exactly this in practice: the better
measurement starts when you start measuring, and the comparison you want
is with a period you can no longer observe. Naming the confound and
reasoning about it under uncertainty is the graduate-level exercise.

Sequencing: build the utility-report reveal first; submetering is a later
addition that reuses the same machinery and adds the cost decision.
2. **Where does the threshold live in Family C?** A chip in the composer
   (student-chosen, per report) or a global view toggle (fast comparison,
   less deliberate)? The composer is more honest and slower; the toggle
   demonstrates better. Possibly both — toggle to notice, composer to
   commit.
3. **How much ground truth to reveal, and when?** Full reveal risks
   turning a judgment exercise into an accuracy contest. Revealing only
   the *direction and magnitude* of the error may teach more than the
   exact figure.
4. **Does a recorded report that was later re-cut need to reference the
   earlier one?** `modifies` exists. If a student records break-even
   three ways, the relationship between the three is itself assertable,
   and probably should be.

**Design constraints.**

- The withheld data must be *generated as truth first*, then hidden —
  not reverse-engineered to make a method look good. If it is fabricated
  to favour ABC, the exercise is a lie and a sharp student will smell it.
- The reveal must come **after** the student commits, or it is not a
  judgment, it is a lookup. Same rule as the Report Builder's
  free-preview/deliberate-record split, and the same rule as the
  orientation page's worked example (`CLOSING-ENTRY-STUDY.md` §4).
- Some proxies should *win* on some events. If the finest-grained method
  is always best, the student learns "more data is better" rather than
  "match the method to the decision and the cost of measuring."

## Family C — Confidence cutoffs: assets under different admissions

**The background** (from an earlier session, restated for the record).
FASB Concepts Statement No. 8, Chapter 4 (December 2021) replaced the
CON 6 asset definition. "Probable" is gone: an asset is now *a present
right of an entity to an economic benefit*. Likelihood moved out of the
**definition** of the element. It still lives downstream — in recognition
and measurement, in reserves and allowances, and in specific standards
that retain their own thresholds — but the element itself no longer
carries a probability test.

**The exercise.** A decision-maker may reasonably want to see the
statements under a *different* admission rule than the one GAAP settles
on. So: re-cut the balance sheet admitting only rights above a confidence
threshold. 100%, 90%, 80%, 50%. Watch total assets, equity, and every
ratio built on them move.

This is close to trivial to build and hard to overstate the value of.
Every `expects` already carries `has-confidence-level`. The composition
is one more `excludes` clause — a threshold filter — over events the
student already recorded. The student sees:

- the same company, four balance sheets, no falsification anywhere
- which specific rights fall out at each cut, by click-through to the
  originating events (back-refs already exist)
- that the allowance method is *one* answer to uncertainty — expected
  value — and that a threshold rule is a different and sometimes more
  useful one
- why a lender, an insurer, and a shareholder might each want a different
  cut, and that the record can serve all three without being rewritten

**The pedagogical payload.** Students come away understanding that
financial statements are a *view*, produced by admission rules, and that
the rules are chosen — by standard-setters, deliberately, with reasons
that can be argued about. That is the conceptual-framework course taught
as an experiment rather than as a list of definitions to memorize.

It also pairs directly with the existing bad-debt work: the confidences
the student assigned during credit sales already feed the allowance
calculation (`classification.clj:310`). Family C walks the *same*
confidences a different way. Same record, same numbers in it, different
question asked of them.

---

## The BrewCo case, preserved

The paper version is being retired. The scenario is worth keeping — the
numbers are clean (every figure exact, no rounding anywhere) and it is a
ready-made managerial case to re-express assertively. All figures below
verified 2026-09-18.

**Company:** BrewCo Coffee Roasters, 12-oz bags, two lines (Espresso
Blend, House Blend).

*CVP (annual):* price $20.00; variable mfg $8.50; variable selling $4.00;
fixed $90,000; target net income $93,750. → CM $7.50; required
contribution $183,750; **24,500 bags**. (FC +25% → 27,500 bags, i.e.
3,000 more.)

*ABC (per period):* pools — setups $3,600 / 20; inspections $2,700 / 60;
orders $2,160 / 30; total $8,460. Espresso usage — 8 / 24 / 13. Rates —
$180 / $45 / $72. Allocated — $1,440 + $1,080 + $936 = **$3,456**.
Plantwide comparison: Espresso 200 of 500 DL hrs → $16.92/hr → $3,384.
**ABC assigns $72 more.**

*Break-even & special order (monthly):* price $45.00; variable mfg
$27.00; variable selling $5.00; fixed mfg OH $22,680; fixed S&A $8,100;
current volume 11,430 bags; capacity 13,000. Manufacturing-only
break-even **1,260 bags**; full break-even **2,368 bags**. Hotel special
order: 800 bags at $36, no selling cost → CM $9.00/bag → **+$7,200/month**;
capacity holds (11,430 + 800 = 12,230 ≤ 13,000).

**What to keep, translating it.** The scenario, the numbers, and the
three-topic spine. **What to drop:** the code-extraction mechanic — a
padlock is a binary oracle that cannot say *which* input was wrong, which
is the same undiagnostic-feedback failure noted in `CLOSING-ENTRY-STUDY.md`
§1. AALP's derived journal entry is the better version of that lock: it
turns, and it explains itself.

**What to fix, translating it.** Part B (accept/reject the special order)
is the real managerial judgment and it gates nothing — the lock code comes
from Part A alone, so a team racing the clock skips the thinking. The
assertive version should make the judgment the recorded act.

## Where this plugs in

- **Report Builder**, as Stage 4: compositions that differ in scope, run
  side by side, with the difference as the object of study. Families A
  and C are threshold/inclusion chips over existing machinery.
- **Managerial entry point** (`CURRICULUM-ROADMAP.md`, Medium-term):
  Family A *is* the CVP half of that bullet, done over the student's own
  chain rather than a given table.
- **Cross-course case continuity** (Long-term): Family C is an
  intermediate/conceptual-framework exercise over an intro-built ledger.
- **Synthetic scale with an answer key** (Long-term): Family B is the
  same instructor-held-ground-truth pattern, shrunk to one business and
  one decision. The generator work is shared.

## Open questions

1. ~~**Does Family B need new recording, or only new generation?**~~
   **Answered 2026-09-18 — cheap**, and the reveal mechanism is settled
   (see *How the reveal happens*, above). Machine identity is already carried
   as provenance (`is-allowed-by` → the equipment purchase event) and
   lot identity as a qualified denomination
   (`{:unprinted-t-shirt "TShirtPurchase-001"}`). Energy is a sanctioned
   denomination in the research schema but is absent from the production
   recipe's `consumes`, and absent from the app's `unit-type-options`
   entirely. So the work is: (a) add `energy-unit` to the app's unit
   options, (b) add a kWh line to the production recipe. Both are data,
   not schema. See *What the SP example already has*, above.
