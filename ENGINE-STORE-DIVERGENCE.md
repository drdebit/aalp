# RESOLVED — the engine store can fall back to memory, silently

> **This fault no longer exists. Kept for the history, which is the
> part worth reading.** Resolved 2026-09-18/19 by removing the thing
> that could diverge rather than by guarding it, and verified
> 2026-09-20: `create-datomic-store` has no callers left in `src/clj`,
> `init-engine!` is gone from `server.clj`, and a restarted backend
> prints no "Engine store:" line at all — because there is no longer a
> store to choose.
>
> What replaced it: `engine/store-of` builds an in-memory store from
> one student's events per request and discards it. Nothing in an
> engine store is a fact any more, so losing one costs nothing.
> Recorded reports — the thing this document called the worst of it —
> live in AALP's own Datomic as `:recorded-report/*`, carrying both the
> composition's spec and its figure.
>
> The five ranked fixes below are all answered. Items 1, 2, 3 and 5 by
> the rewrite; item 4 (reconciliation) is moot, since a `ledger-entry`
> can no longer point at an engine event that outlived its store.
> `:business-state` — named at the end as the same category error one
> level down — is also done: seven attributes retracted, the rest read
> from the chain by `business-report`.
>
> Two vestiges, both harmless and both deliberate: `engine-db-uri` is a
> stub in `schema.clj` with no callers, and `:ledger-entry/engine-event-id`
> is still written, now identifying an event inside a store that lives
> for one request.
>
> Everything below is as filed on 2026-09-18 and describes the world
> before that. Read it for how an index added *alongside* the record in
> April became load-bearing in July without anyone revisiting the
> storage architecture — that is the general lesson, and it is why the
> fix ended up being architectural rather than a try/catch.

---

## As filed, 2026-09-18

*Found 2026-09-18 (author + Claude Opus 5) while scoping
`MEASUREMENT-CHOICE-DESIGN.md`. Read-only investigation; **nothing was
changed**. Filed for the session that owns the code.*

## One sentence

When `DATOMIC_DB_PASSWORD` is set but the `aalp-engine` database cannot
be opened, the backend falls back to an **in-memory** engine store while
the main database stays **persistent** — so ledger entries survive
restarts, the assertion chains they point at do not, and nothing anywhere
says so.

## The code path

`server.clj:684` `init-engine!`:

```clojure
(if (System/getenv "DATOMIC_DB_PASSWORD")
  (try
    (engine/init! (engine-datomic/create-datomic-store schema/engine-db-uri))
    (println "Engine store: Datomic (persistent, aalp-engine)")
    (catch Exception e
      (println "Engine Datomic store failed; falling back to in-memory:" ...)
      (engine/init!)))                 ; <-- in-memory
  (do
    (println "Engine store: in-memory (DATOMIC_DB_PASSWORD not set)")
    (engine/init!)))
```

**The no-password branch is fine.** `schema.clj:411` and `417` both test
the same `in-memory?` predicate, so with no password *both* databases are
`datomic:mem://…`. Everything is ephemeral together, consistently. That
is dev mode, documented, and harmless.

**The catch branch is the fault.** Password present → `db-uri` is the
real Postgres-backed Datomic → `:ledger-entry`, `:business-state` and
`:user` all persist. But `aalp-engine` is a *separate database on the
same transactor* (`schema.clj:417`), and if opening it throws, only the
engine drops to memory. The two stores now have different lifetimes.

Plausible causes: `aalp-engine` never created on a *new* transactor (a
fresh machine, a rebuilt storage); transactor restarted after the backend,
or otherwise unreachable at the moment of that one call; storage
permissions. Memory pressure was an early guess and is now the least
likely — see the CLAUDE.md note under *Checked on choochoo*.

## Why it is silent

1. After fallback the store is initialized and healthy, so
   `with-engine` (`engine.clj:40`) succeeds.
2. `store-classified-event!` returns a real event-id.
3. `simulation.clj:1344` writes that id into
   `:ledger-entry/engine-event-id`. The row looks complete.
4. Students keep working. Nothing in the UI differs.
5. On the next restart the engine store is empty. Ledger rows persist,
   carrying `engine-event-id` values that now point at nothing.

The only signal is a `println` at startup, which goes to
`/tmp/aalp.log` and scrolls away. There is no health endpoint, no
reconciliation, and no recovery — in-memory data is gone.

## What breaks, concretely

The two stores are read by different features:

| Surface | Reads | Survives this? |
|---|---|---|
| Ledger view (`/api/simulation/ledger`) | `:ledger-entry` rows (`simulation.clj:959`) | yes |
| Business state (cash, A/P, A/R) | `:business-state`, incrementally updated | yes |
| Chain view / input click-through | engine (`/api/engine/chain/:id`) | **no** |
| Report Builder preview and compose | engine (`/api/engine/compose/*`) | **no** |
| **Recorded reports** | engine **only** — no `:ledger-entry` twin | **no** |

So a student sees their full transaction history in the ledger while
their reports collect over an empty or partial event set — silently
wrong numbers, not an error.

**Recorded reports are the worst of it.** `record-composition!`
(`engine.clj:190`) writes to the engine and nowhere else. Per
`REPORT-BUILDER-DESIGN.md`, recording is deliberately framed to the
student as *"This becomes part of your record, with your name on it."*
Under this fallback that promise is false, and the student is never told.

## How it got this way (git history, 2026-09-18)

Not an incomplete migration. **It was never framed as a migration at
all** — the engine was added *beside* the existing store, and then
quietly became load-bearing without the storage architecture being
revisited.

| Commit | Date | What it did |
|---|---|---|
| `47ef222` | — | Simulation mode + Datomic. `:ledger-entry` EDN blobs are the store. |
| `0df847b` | 2026-04-08 | Integrate assertive-engine "for storing classified simulation events in decomposed, indexed form **alongside** the existing EDN blob storage." A "thin failure-tolerant wrapper." |
| `c8f6d54` / `a870caf` | 2026-07-07 | Report Builder, backend then frontend. Student-recorded reports live in the engine **only**. |
| `11f8a27` | 2026-07-07 | "Persist the engine store: Datomic-backed when credentials present." |

Read `0df847b` carefully: *alongside*. In April the engine was an index
sitting beside the authoritative blob, and "failure-tolerant" was the
right call for an index — losing it cost you queries, not records.

Then `11f8a27`'s own message says what happened next:

> Student-recorded reports and decomposed events previously lived in the
> in-memory engine store and **evaporated on every backend restart**.

**This exact failure has already happened once.** The fix made the store
Datomic-backed when credentials are present — but kept the fallback,
justified as "in dev or on any failure — the teaching flow never depends
on it." That justification was inherited from April and was **already
stale on the day it was written**: the Report Builder shipped in the same
day's commits, and its recorded reports have no home but the engine.

**So the catch branch still reproduces the bug `11f8a27` was written to
fix.** The no-password door was closed; the open-failed door was left
open, and behind it is the same room.

## What the engine was supposed to be

Its own `CLAUDE.md` is unambiguous about the intended division:

> It decomposes canonical nested assertion maps (the research format)
> into a semi-decomposed stored form with denormalized indexes, enabling
> cross-event queries, chain traversal, pattern matching, and aggregation
> — all impossible with **the opaque EDN blobs in the AALP Datomic
> store**.

The blobs were never meant to be the query surface. But they are still
the ledger's source of truth for display, and `:ledger-entry/assertions`
still carries a `pr-str` copy of the same assertions the engine holds
properly. Both halves of that sentence are the problem.

## What finishing the migration would mean

Stated so the decision is explicit rather than drifted-into:

1. **The engine becomes authoritative for assertions.** One write. The
   `:ledger-entry/assertions` blob stops being a second copy — the row
   keeps only what display needs that the engine does not hold
   (narrative, action-type, template-key, the derived JE if it is not
   recomputable).
2. **`:business-state` becomes derived.** Today it is incrementally
   updated by `apply-effects` on the prior state and never recomputed, so
   it is a third representation that can drift from both. Recomputing
   from the chain makes cash, A/P and A/R answers *readings*, which is
   also what the framework says they are.
3. **The dual write gets a boundary, or stops being dual.** As long as
   two stores are written in sequence with one of them swallowing its own
   failures, they can disagree and nothing will notice.

Item 1 is the one that matters for correctness. Item 2 is the one that
matters philosophically — a position that is stored rather than read is
exactly the thing the vocabulary work has been removing elsewhere (see
the `ownership-units` note at `classification.clj:123`: *"Assert the
units; report the ratio."*).

## The assumption that expired

`init-engine!`'s docstring says the fallback is safe because "the
teaching flow never depends on it." **That was true in April and is not
true now.** The Report Builder depends on the engine entirely, and all
three families in `MEASUREMENT-CHOICE-DESIGN.md` compose over it. The
comment preserves, in the code, an assumption the system outgrew on
2026-07-07 — and it is the sentence a reader will hit first when deciding
whether the fallback is safe to leave alone. **Fix item 1 below, whatever
else is or is not done.**

## Severity

Not corruption, and not a fault on the healthy path: with `aalp-engine`
present and the transactor well, everything works as designed. It is a
**silent-degradation** fault — invisible while it happens, unrecoverable
afterwards.

Checked 2026-09-18: it has not fired in the two runs still on disk, so
there is **no evidence any student work has been lost**. Probability is
lower than first assumed (the memory-pressure theory does not hold up).
What has not changed is that the fault is unguarded, undetectable after
the fact, and sits under a routine `restart-backend.sh` on the machine a
pilot will run on.

## Suggested fixes, cheapest first

1. **Fix `init-engine!`'s docstring** (`server.clj:678`). Do this even if
   nothing else is done today. It currently reads:

   > Falls back to in-memory (dev, or on any failure) -- the teaching
   > flow never depends on it.

   That last clause is false and has been since 2026-07-07. It is also
   the first sentence anyone reads when deciding whether the fallback is
   safe to leave alone, so it will keep producing the wrong decision
   until it is changed. Replace it with what is now true: the Report
   Builder stores recorded reports here and nowhere else, so an in-memory
   engine loses student work. Costs a minute; stops the next reader —
   human or model — from re-deriving the same wrong conclusion.

2. **Fail loudly instead of falling back.** If the password is set, a
   failure to open `aalp-engine` is a misconfiguration, not a condition
   to paper over. Refusing to start is better than running wrong.
   One-line change to the catch branch.
3. **Surface the store mode.** Add engine store type + health to an
   existing status/summary response so the condition is visible without
   reading a log.
4. **Reconcile on startup.** Count `:ledger-entry` rows with an
   `engine-event-id` whose event is absent from the engine; log or expose
   the number. Detection without needing anyone to have watched the boot.
5. **Finish the migration.** The real fix; see *What finishing the
   migration would mean*, above. Three representations of the same facts
   with no reconciliation is the "crossed streams" risk named in the
   measurement-choice discussion, occurring inside a single student's
   instance.

## Checked on choochoo, 2026-09-18

**The fallback has never fired, on the evidence that still exists — no
student work has been lost through this path.** The fault is real and the
door is still open; nobody has walked through it.

| Log | Run started | Engine store line | "falling back" | "Engine operation failed" |
|---|---|---|---|---|
| `/tmp/aalp.log` | 2026-09-18 19:33 | `Datomic (persistent, aalp-engine)` | 0 | 0 |
| `/tmp/aalp-restart.log` | 2026-09-16 17:59 | `Datomic (persistent, aalp-engine)` | 0 | 0 |

`aalp-engine` **exists and opens cleanly.** From the startup banner:

```
INFO assertive-engine.store.datomic -- Datomic schema installed
INFO assertive-app.engine -- Assertive engine initialized {:healthy? true}
Engine store: Datomic (persistent, aalp-engine)
```

That line prints only when `create-datomic-store` returns without
throwing. Backend up 23h at time of check, zero engine errors in 785 KB
of log.

**Caveat — the log history is two runs deep.** `restart-backend.sh`
redirects with `>`, which truncates, so each file holds exactly one run
(confirmed: one "Engine store:" line in each). "Never fired" means "did
not fire in the two starts still on disk," back to 2026-09-16. Earlier
history is unrecoverable. If this matters, append (`>>`) or timestamp the
log filename.

**Still unchecked: reconciliation.** Whether any `:ledger-entry` rows
carry an `engine-event-id` whose event is absent from the engine. Attempted
via a direct Postgres query; the attempt was blocked by a safety
classifier (it handled the DB password and the SQL had been obfuscated to
dodge quote-escaping, which reads as evasive) and was not retried another
way. Doing it properly needs a short Clojure process on choochoo — this is
fix item 4, and it is the check that would turn "no evidence of loss" into
"confirmed no loss."

### Unrelated, found while checking: CLAUDE.md's memory note is stale

CLAUDE.md's *Memory Constraints* section says the server has "limited RAM
(~4GB) with no swap" and treats memory as a binding constraint on running
several JVMs. Choochoo actually reports **15.7 GB total, 9.9 GB
available**, with the backend, transactor and shadow-cljs all up.

Worth correcting, and not only for tidiness: transactor memory pressure
was the most plausible trigger for the `aalp-engine` open failing while
the main DB stayed healthy. On the real numbers that is much less likely,
which lowers the probability of the fault firing without changing the
fact that it is unguarded.

## Verified, and not

**Verified by reading:** the fallback path; that both URIs share the
`in-memory?` predicate; the two write sites (`guided.clj:190/209`,
`simulation.clj:1329/1348`); that the ledger and the engine are read by
different endpoints; that recorded reports have no ledger twin; that
practice mode (`/api/classify`) writes to neither store — **the refactor
holds, practice is clean.**

**Checked live on choochoo (see that section):** `aalp-engine` exists and
opens; the fallback has not fired in either surviving log.

**Still not verified:** reconciliation — whether any `:ledger-entry` row
points at an engine event that is not there. Fix item 4.
