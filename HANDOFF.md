# AALP — where things stand (2026-09-03, evening)

Written to pick up cold in a new session. Read this, then
`TUTORIAL-EPISODES.md` for the walkthrough copy.

## Running it

Everything runs on **choochoo** (`ssh choochoo`, `~/clojure/aalp`), which
is the pilot host. The local clone at `~/Dropbox/clojure/aalp` is the
working master; GitHub `origin` tracks it commit for commit.

    http://choochoo.dyn.gsu.edu:8081     the app
    http://choochoo.dyn.gsu.edu:3000/api the backend

Deploy: commit, push, then on choochoo `git pull --ff-only origin master`.
The shadow-cljs watch picks up `.cljs` changes on its own; a `.clj`
change needs `./restart-backend.sh`.

**Headless students** live in `study/` (README there). They are the
fastest way to find out whether a change teaches:

    bb study/dump_content.clj                       # refresh the copy they see
    cd study && ./run_cohort.sh c3 "s21:novice:haiku s22:trad:haiku" --max-turns 240
    python3 smoke_walk.py                           # scripted perfect student, no model

Each student is a `claude -p` session with no tools and a replaced system
prompt, so it sees only the screen; the text-mode client mirrors the
guided-mode path and `api.cljs` payloads. Read `runs/<id>/transcript.md`
before trusting a grade. Test users are `<id>@study.test`. A cohort of
four on haiku takes ~35 min and counts against the Claude subscription
(cohort c2's post-tests hit the session limit and were re-sat by
`study/runs/resume-c2.sh`; `--resume-assess SID` does that for any run).

The local machine **cannot** run the backend — its Datomic transactor
fails postgres auth and has since Feb 2026. Local is for editing,
compiling and the conformance harness:

    npx shadow-cljs compile app
    DATOMIC_DB_PASSWORD=dummy clojure -M:test -e \
      "(require 'assertive-app.je-conformance) (assertive-app.je-conformance/report)"

Two operational traps, both cost an hour each:
- `ssh host 'cmd &'` hangs the channel waiting on the backgrounded
  process's file descriptors. Use `ssh -n` and redirect everything.
- `restart-backend.sh` used to kill shadow-cljs, because `lsof -i:3000`
  without `-sTCP:LISTEN` matches processes holding *client* connections
  and shadow proxies `/api` there. Fixed on choochoo, with a `.bak`
  beside it. **The script is not in git** (it holds credentials), so a
  recreated copy will reintroduce the bug.

## What the system now believes

The through-line of this work: **journal entries are derived from
assertions, and nothing else is a source of truth.**

- `je_derive.clj` is the single authority for which lines exist and what
  they are worth. Nothing downstream re-derives an amount or parses one
  out of a display string.
- Amounts are unit-typed engine quantities, so a physical count can never
  be spent as money. `resolve-amount` admits only monetary denominations.
- **Positions are read off the chain, never asserted.** Raw materials,
  work in process, finished goods, capital — all resolved at query time
  by `chain.clj` from what the record says happened. `nil` means the
  record underspecifies the thing, which is the honest answer and where
  the student has work to do.
- **What a thing IS cannot be looked up.** The item catalogue is gone
  from position resolution: a printer bought by a machine reseller is
  inventory, and it is the same printer. Capital is capital because the
  record shows it enabling a transformation without being consumed.
- **Cost is a query.** Cost of goods sold comes from the events that
  acquired or produced the goods, weighted average, in one named place
  (`cost_basis.clj`). FIFO would collect the same events and report a
  different figure; both are expressible.
- **The record refuses what it cannot bear.** You can only provide what
  you have — present tense only; nested under `expects`/`allows` it is a
  claim about the future and constrains nothing. Production needs
  materials and a capability. Every refusal carries a message.
- **Labels are a translation, not data.** Renaming an account changes
  nothing about what was asserted.

## What the first two cohorts found (2026-09-03)

Cohort c1 ran against the platform as it stood that morning; c2 after
the fixes below. Reports: `study/runs/report-c1.md`, `report-c2.md`.

- **The walkthrough teaches.** All eight students finished it; post-only
  items (why Owner's Capital, what `allows` decides, inherited Raw
  Materials, recorded-but-not-reflected, production without a
  counterparty) scored 2/2 almost everywhere; debit/credit meaning rose
  from 0.5 to 1.75 mean. Two copy points survived into c2's transcripts
  and are now fixed: the sentence builder said "SP receives ... from
  SP" right after episode one had separated SP from the business
  (subject is now "the business"), and episode two never named the
  printer's vendor.
- **The drill did not.** In c1, 0 of 43 practice submissions were right:
  the practice problem is a sentence with no paragraph, so positions
  read off the chain could never be met, the equipment key lacked the
  `allows` its classification demanded, half the level-1 problems could
  not be generated (a marker value counted as an option list), and
  `:any` was compared literally. Fixed in `4d57ab6`; in c2 all 20
  level-0 submissions were right and every student streak-passed.
- **Level 1 was unanswerable in the browser** (c2): nothing set `action`
  or `unit` on `requires`/`expects`, and every level-1 classification
  requires both. Fixed in `a10e250` -- defaults on add, a visible unit
  control. Cohort c3 then passed level 1: all three students streak-
  passed both drills and finished the path; the one recurring miss was
  the printer on credit, which did not permit `allows` (fixed, `892d536`).
- **What c3 says about teaching, with the drill working**
  (`study/runs/report-c3.md`). Post-only assertive-accounting items were
  2/2 for the novice and hasty personas. The traditional-accounting
  persona scored 0 on inherited classification and cost flow: the item
  dropdown said "(raw materials for production)", so they concluded the
  system "has a lookup table" (labels are bare names now), and the drill's
  sale narrative says "the t-shirts cost $250 to produce", so cost looked
  like it came from the problem text rather than the record. Both students
  and the analyst note that debit/credit is taught only by example, never
  as a convention; that `requires` never says whose promise it is; that
  services and intangibles never appear, so the novel-transfer item is a
  guess; and that an unfinished walkthrough line showed $3,000 from the
  guided day underneath (fixed). The ink lesson and the drill key
  disagreed; resolved by multi-input `allows`, see open question 0.
- **A fresh business could not print.** Episode 3 never bought ink, so
  episode 4's production was refused and episode 5's cost was unpriced;
  the hand verification in the morning had ink in a ledger. There is an
  ink episode now, and `chain/on-hand` reads multi-flow consumes/creates.
- **Students do not stop.** Given an unpassable drill, three of four
  haiku students eventually refused to continue rather than leave; they
  now have a quit action. The persona firewall leaks: three "novices"
  wrote correct journal entries on the pre-test. Post-only items and the
  think-aloud are the trustworthy signal.

## Conformance (the oracle)

`test/assertive_app/je_conformance.clj`. The 45 hand-written
`:journal-entry` templates are no longer a rival producer; they are the
oracle. Current state, 43 classifications:

    11 match   11 partial   18 gap   3 conflict   0 amount violations

- **conflict** = the rulebook derives an account the template does not
  name. All three are the template disagreeing with its own assertions:
  `employment-under-law` (credits Wages Payable while asserting
  `provides` money, which is Cash), `sale-under-ucc` (omits the cost pair
  its own assertions produce), `stock-issuance` (derives Owner's Capital
  where the template says Common Stock — the same residual position under
  a different entity form, a labelling question).
- **partial / gap** = the rulebook build-out queue, by level.

## The curriculum

**Process teaches; pattern trains.** The walkthrough follows SP's
business in order; the drill stays organised by pattern with fresh
scenarios and keeps all the ALEKS mechanics (test-out, 5-streak, worked
examples, stuck detection, time-on-task).

Five episodes in `src/cljs/assertive_app/episodes.cljs`, copy in
`TUTORIAL-EPISODES.md`:

| # | episode | new assertions |
|---|---------|----------------|
| 1 | the business is funded | has-date, receives, provides, has-counterparty |
| 2 | SP buys a printer | allows |
| 3 | SP buys shirts and ink | — none — |
| 4 | SP prints shirts | consumes, creates, is-allowed-by |
| 5 | SP sells shirts | — none — |

Two of the five introduce no new assertions and still produce accounts
the student has not seen. That is the framework's claim arriving as the
shape of the curriculum.

A step is `:say / :do / :then` — one sentence setting up one action, the
action, one sentence reading back what appeared. The `:then` is withheld
until the step is done, because it talks about what is now on screen.
`:palette` decides what is offered at each step, so concepts arrive one
at a time.

Tone (Matt's): short and conversational, "do this. Good. Now do that."
Friendly, not babyish. **Do not bash double-entry** — the class teaches
it. "Recorded but not reflected" is the *monetary unit assumption* doing
its job, which is what makes entries addable; the assertion layer keeps
track of the rest.

## Verified working, by hand in the browser

All five episodes, end to end:

    ep1  DR Cash 20,000 / CR Owner's Capital 20,000; the 200 ownership
         units recorded but not reflected
    ep2  printer -> Equipment (Fixed Asset), and only because `allows`
         said what it is for
    ep3  blank shirts -> Raw Materials Inventory, drill-down showing
         "DECIDED EARLIER 2026-01-02 you said this turns blank-tshirts
         into printed-tshirts"
    ep3b ink -> "(not yet classified)" until it is used (see open question 0)
    ep4  CR Raw Materials 50 + CR Raw Materials 10 / DR Finished Goods 60
    ep5  DR Cash 100 / CR Revenue 100, DR COGS 24 / CR Finished Goods 24

The 24 is four shirts at the 6 each that flowed from ep4 from ep3.
Nothing typed. No constraint violations anywhere in the chain. Re-verified
against choochoo by `study/smoke_walk.py` (fresh user, empty ledger) on
2026-09-03 evening.

## Open questions, in rough priority order

0. **Resolved (2026-09-03, Matt):** `allows` now consumes a list, as the
   research example does -- `{:consumes-items ["blank-tshirts"
   "ink-cartridges"] :creates-item "printed-tshirts"}` -- so ink is Raw
   Materials the day it is bought. A lone `:consumes-item` in an older
   record is still read as the one-element case. Still open: in the drill
   the derived panel shows "(not yet classified)" for shirts while the
   feedback says Raw Materials, because `/api/derive-je` has no paragraph
   either. Options: give the drill a canonical SP paragraph as
   `prior-events` (numbers in sale problems will not fit one), or let a
   standalone derivation fall back to the catalogue in one named place.
0b. **Level-2 production keys** (`production-raw-to-wip`,
   `production-wip-to-finished`) grade themselves wrong, and
   `production-direct`'s narrative leaves `{quantity-consumed}` unfilled.
   The level-3 drill serves them.

1. **Equity wants a verb the vocabulary does not have.** `provides
   ownership-units` is kept deliberately — the certificate does go to the
   shareholder, and without it equity is defined purely by absence, which
   is fragile. But the business did not *have* 200 units and hand them
   over; it brought them into existence. Debt is a promise and `requires`
   carries it. Equity enables and requires and does several things at
   once. Simplifying is right for an intro class; the full chain would
   suit a business law course.
2. **The rulebook build-out queue** — 18 gaps and 11 partials above.
   L5 adjusting entries are all reachable by students today and produce
   a template entry with a blank amount while the panel says nothing
   matches. That inconsistency is the one students would actually hit.
3. **Category-units still in specs**: `intellectual-property`,
   `service-output`. Not obviously physical flows; may want their own
   denominations the way `ownership-units` got one.
4. **`capability-acquisition` vs `cash-equipment-purchase`** now overlap
   — both are an equipment purchase with `allows`. One should absorb the
   other.
5. **Episodes are not recorded to the ledger.** The walkthrough carries
   its own chain and sends it as `:prior-events`. If the walkthrough
   becomes the student's actual Year 1 recording, that changes.
6. **The simulation/"game" is set aside** for the pilot, by decision.
   Its ledger and statements have never been exercised end to end since
   the derivation rework.

## Things that look like bugs and are not

- A **hidden Chrome tab** freezes `requestAnimationFrame`, so Reagent
  never repaints and screenshots time out; it also clamps `setTimeout`,
  so the debounced derivation takes ~5s instead of 250ms. Both are
  automation artifacts. Drive the DOM with `javascript_tool`, call
  `reagent.core.flush()` after clicks, and read state on the *next* tool
  call — reads lag one call behind.
