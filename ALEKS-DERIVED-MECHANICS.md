# ALEKS-Derived Mechanics — Evaluation and Decisions

*2026-08-07 (author + Claude Fable 5). Outcome of the ALEKS exploration
handed off in `ALEKS-PEDAGOGY-PLAN.md`. Each of the six candidate
mappings is evaluated against the Guided Year as actually built
(guided.clj, progress.clj, the tutorial→quiz→drill flow in
tutorials.cljs/views.cljs) and sorted: parameter tweak / new mechanic /
doesn't fit.*

**State correction to the handoff:** the drill mastery bar is no longer
4-of-5. It was raised to **8-of-10** for the GSU 2101 pilot population
(commit d192d00), per-level configurable via `tutorials/drill-config`
(`{:round-size 10 :pass-count 8}`), with early pass (pass the moment
`correct ≥ pass-count`), unreachable-round detection offering a fresh
round, and a Review Tutorial button inside the drill header.

## Verdicts at a glance

| ALEKS mechanic | Verdict | When |
|---|---|---|
| Initial Knowledge Check | Adaptive IKC: doesn't fit. Cheap adaptation: **drill test-out** (flow tweak) | Near-term |
| The Pie | Adopt as **assertion-family mastery map** (small new mechanic, rides roadmap item 5) | With item 5 |
| Ready-to-learn selection | Doesn't fit Year 1; principle already covered (Year 2 prerequisites, roadmap item 6) | — |
| Streak mastery | Keep 8-of-10; add **streak early-pass** (parameter tweak) | Near-term |
| Progress knowledge checks | Adopt as **retention re-checks**, adapted: inform, never relock (new mechanic) | Spring 2027 |
| Dated modules | Defer; keep one lesson (deadline-vs-gate validation) | Spring 2027 |

## 1. Initial Knowledge Check → placement

**Adaptive IKC: doesn't fit.** ALEKS places students inside a
curriculum much of which they may already know. AALP's assertion
vocabulary is novel to every entrant — there is no prior assertion
mastery to detect. What an entering student may already have
(debit/credit fluency, reading journal entries) is the *derived
artifact* in AALP, not the skill being placed; it shortens their path
through the tutorial reading, not through the vocabulary itself.
Building adaptive item selection and ability estimation to discover
"this student can skip the coffee-purchase analogy" is machinery far in
excess of the payoff.

**The cheap adaptation that captures the value: drill test-out.** The
drill already *is* the mastery instrument — the tutorial reading is
instruction, the drill is the gate. So placement needs no new
instrument: offer "Think you already know this? Try the practice round
now" at the top of each level's tutorial. Passing completes the
tutorial through the existing `complete-tutorial!` path; failing routes
into the reading (the Review Tutorial button already exists in the
drill header, so the return path is built). ALEKS's "I Don't Know"
option maps to simply bailing out of the test-out into the reading, no
penalty — drill rounds are already free.

A guessing student cannot luck through: the assertion builder is
effectively free-response (large answer space), which is exactly why
ALEKS trusts free-response items for placement. The 8-of-10 gate keeps
its rigor regardless of entry path.

*Build: small, views-only flow change. Study-validity note: for the
Fall 2026 pilot, log test-out usage (who attempted, who passed) so the
two entry paths are distinguishable in the data; the compressed
two-week window makes time savings for fluent students worth the
non-uniformity.*

## 2. The Pie → assertion-family mastery map

**Adopt, resliced.** ALEKS's Pie shows topic coverage; AALP's guided
year already shows coverage natively (day N of total — script position
is progress). What the student *cannot* currently see is the dimension
that matters here: **which assertion families are solid and which are
shaky**. So the AALP pie is sliced by assertion family (exchange,
temporal/forward-looking, transformation, recognition, legal), filled
by rolling per-assertion accuracy.

The fill data is already recorded per attempt:
`:attempt/missing-assertions`, `:attempt/extra-assertions`,
`:attempt/selected-assertion-types`, `:attempt/distance`,
`:attempt/param-mismatch-count`. The mastery map is the
**student-facing surface of roadmap item 5** (assertion-distance
grading) and shares its aggregation with item 6's instructor heatmap —
one aggregation, two views. It is also where retention re-checks
(below) surface decay: a slice that was full can dim.

*Build: small–moderate; a per-assertion-family rolling-accuracy
endpoint over existing attempt data plus one visual. Schedule with
roadmap item 5, not before — the distance metric defines the fill.*

## 3. Ready-to-learn selection → next-drill selection

**Doesn't fit Year 1, and shouldn't.** The script's linearity is
load-bearing: authored coherence (ascending dates, sane balances,
inventory sufficient for later sales), bounded divergence for
cross-student comparability (a study-validity requirement), and the
corridor-decision design principle that choice is about *deciding*, not
sequencing. Offering topic choice inside Year 1 would trade those away
for a freedom ALEKS needs only because it has no narrative spine.

The principle already lives where it belongs:

- **Year 2 is ready-to-learn selection.** Available actions are gated
  by business-state prerequisites plus tutorial completion — the
  student picks among currently-possible actions. That *is* the ALEKS
  mechanic, with the business state playing the prerequisite graph.
- **Within-drill selection is roadmap item 6.** Misconception-adaptive
  generation targets each student's weak assertions — stronger than
  offering choice, because the platform knows the weak spots
  per-assertion, which ALEKS's topic granularity can't see.

No new mechanic.

## 4. Streak mastery vs the 8-of-10 bar

**Explicit decision: keep the ratio bar, graft on a streak early-pass.**

The comparison the handoff asked for:

- *Consecutive-correct (ALEKS: 5-in-a-row)* is the cleaner mastery
  signal — in a free-response answer space a streak is nearly
  impossible to luck into (true in ALEKS, equally true of the assertion
  builder). It also ends the drill the moment mastery is demonstrated:
  a fluent student exits in 5 problems.
- *Ratio (8-of-10)* tolerates careless slips — a streak restarts on any
  slip, which for the pilot population (non-majors, morale-sensitive,
  ~1–2 min per problem) converts one misclick into five more required
  problems. The round structure also bounds session length predictably,
  which the compressed fall pilot needs for scheduling. And the bar was
  *deliberately* raised to 8-of-10 for exactly this population a month
  ago; re-lowering the effective bar by switching wholesale to
  5-in-a-row would relitigate that decision without new evidence.

Both signals indicate mastery; accept either. The drill passes when
**8-of-10 in the round OR 5 consecutive correct at any point**. Config
becomes `{:round-size 10 :pass-count 8 :streak-pass 5}`, per-level
overridable like the rest of `drill-config`. The fluent student (and
the test-out student from §1, compounding nicely) exits in 5; the
careful-but-fallible student is not punished for a slip; a
5-streak-then-collapse pattern passes, which is acceptable because
collapse after a genuine free-response streak is noise, not absence of
mastery.

*Build: tiny — one field in drill-config, one condition in the drill
state/pass logic, streak tracked alongside `:attempted`/`:correct`.*

## 5. Progress knowledge checks → retention re-checks

**Adopt for the semester-long run, with ALEKS's one wrong idea
removed.** Nothing in the platform currently looks backward: level
unlocks are permanent, and forward script motion never re-samples
mastered material. (The script *does* interleave — L0 templates recur
on L1 days — but that's authored variety, not a retention instrument.)
This is a real gap for the Spring 2027 semester-long version; the
two-week fall pilot has no forgetting window, so nothing to build this
fall.

Mechanism: reuse the drill component wholesale. At period close —
alongside the consequence report, "before you close the books, a quick
check" — a short **unledgered** re-check round samples templates from
completed levels. Sandbox rules apply: complete feedback, mistakes
free, analytics recorded.

The adaptation: **ALEKS unmasters; AALP must not.** Relocking
recording mid-year would fight the errors-persist ledger, the script's
schedule, and the sandbox/books distinction the tutorials explicitly
teach. Re-check outcomes instead:

1. dim the corresponding mastery-map slice (§2), and
2. feed the misconception-adaptive generator (roadmap item 6), which
   serves targeted drills on the decayed assertions.

Retention loop without gating — the same "exam readiness, not forward
march" principle as Accrue's 20% review sampling, expressed through
AALP's own consequence-not-blocking architecture.

*Build: moderate — scheduling hook at period close, sampling policy
over completed-level templates, mastery-map integration. Spring 2027.*

## 6. Dated modules

**Defer, as the handoff suggested.** "Scheduling is a parameter, not a
design" already covers this: the guided year is schedule-independent by
construction. The one ALEKS lesson worth keeping for when instructor
deadlines arrive (Spring 2027 semester-long): **validate deadlines
against the gate structure**, as ALEKS validates module dates against
its prerequisite graph. A deadline on script day N implies every level
gate up to N's level is passable by then at the configured drill bar;
warn the instructor at setup time when pacing is infeasible, don't
discover it mid-term.

## Second pass (2026-08-07, verified in ALEKS student view)

A second look at ALEKS with the Guided Year in mind surfaced two more
mechanics, both adopted. The Explanation behavior was verified live in
the student view of the disposable class:

- Every learning-mode problem offers **Explanation** pre-answer, one
  click, no confirmation.
- It shows the worked solution **for that exact instance**, ending with
  the literal answer.
- Viewing **forfeits the instance**: Check is replaced by "More
  Practice"; the only way forward is a fresh instance.
- **No penalty**: the topic progress meter is untouched (and it is
  indeed 5 segments — 5-to-mastery confirmed).

**7. On-demand worked example (shipped 2026-08-07).** The drill gains
"Show me a worked example": the sentence builder fills with the
canonical assertions, the dual-fluency panel derives their JE with
per-line provenance (the existing rulebook UI *is* the explanation),
and the problem is forfeited — submit disappears, "Try a fresh
problem" replaces it, and no drill counter moves. ALEKS's forfeit rule
kept intact: help is always available and never punished, but a helped
problem is never evidence of mastery. Views are logged as
`:attempt/problem-type :worked-example` / `:feedback-status :explained`
(never counted as progress) so the pilot analytics can see who leans
on explanations and where.

**8. Stuck detection (shipped 2026-08-07).** ALEKS routes a student to
the explanation after repeated misses; AALP can aim better. After two
consecutive drill misses, a nudge names the assertion the student
keeps omitting (diffed client-side against the problem's answer key,
accumulated per round) and deep-links to the tutorial section that
teaches it (`stuck-sections` map in tutorials.cljs — keep in sync if
sections are reordered).

Recorded as decisions, no build yet: **learned vs. retained** — ALEKS
treats "learned in learning mode" and "retained through a knowledge
check" as different evidence; when the mastery map (§2) and retention
re-checks (§5) ship in spring, map slices must distinguish
drill-passed from recheck-survived. **Time-on-task** — ALEKS leans on
it for instructor reports; already on the roadmap's instrumentation
list, pilot-relevant. Considered and not taken: nudge/messaging
system, gradebook integration, QuickTables-style fluency drills.

## Already aligned (no action)

Two ALEKS properties the platform already has, worth recording so they
aren't re-proposed: worked explanations available without penalty
(the drill's complete feedback path plus the in-drill Review Tutorial
button), and wrong answers costing nothing but progress toward the bar
(drill rounds are free and repeatable; only the round resets).

## Build list

Near-term (small, pre-pilot candidates):

1. **Drill test-out** (§1) — shipped 2026-08-07. Guided-mode gate
   offers "skip to the practice round"; a dead round routes to the
   reading (fresh round as alternative); entry path recorded per
   attempt as `:attempt/drill-entry` for the pilot analytics.
2. **Streak early-pass** (§4) — shipped 2026-08-07. `:streak-pass 5`
   in drill-config; a dead-by-ratio round stays alive while a streak
   remains reachable.

With roadmap item 5:

3. **Assertion-family mastery map** (§2) — aggregation endpoint +
   visual; shared with item 6's instructor heatmap.

Spring 2027:

4. **Retention re-checks at period close** (§5).
5. **Deadline-vs-gate validation** (§6), if dated scheduling ships.

Rejected: adaptive Initial Knowledge Check (§1); ready-to-learn
sequence choice in Year 1 (§3); mastery revocation that relocks
recording (§5).
