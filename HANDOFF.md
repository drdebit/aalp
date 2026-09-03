# AALP — where things stand (2026-09-03)

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
    ep4  CR Raw Materials 50 + CR Raw Materials 10 / DR Finished Goods 60
    ep5  DR Cash 100 / CR Revenue 100, DR COGS 24 / CR Finished Goods 24

The 24 is four shirts at the 6 each that flowed from ep4 from ep3.
Nothing typed. No constraint violations anywhere in the chain.

## Open questions, in rough priority order

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
