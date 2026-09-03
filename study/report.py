#!/usr/bin/env python3
"""Aggregate a cohort of student runs into runs/report-<cohort>.md."""
import argparse
import json
import os
from collections import Counter, defaultdict

import assess

HERE = os.path.dirname(os.path.abspath(__file__))


def load(run):
    def j(name, default):
        p = os.path.join(run, name)
        return json.load(open(p)) if os.path.exists(p) else default
    events = []
    p = os.path.join(run, "events.jsonl")
    if os.path.exists(p):
        events = [json.loads(l) for l in open(p)]
    return {"summary": j("summary.json", {}), "grades": j("grades.json", {}), "gaps": j("gaps.json", {}),
            "learning": j("learning.json", {}), "posttest": j("posttest.json", {}), "pretest": j("pretest.json", {}),
            "meta": j("meta.json", {}), "events": events}


def mean(xs):
    xs = [x for x in xs if x is not None]
    return round(sum(xs) / len(xs), 2) if xs else None


def fmt(x):
    return "—" if x is None else str(x)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cohort", required=True)
    ap.add_argument("ids", nargs="+")
    a = ap.parse_args()
    runs = {i: load(os.path.join(HERE, "runs", i)) for i in a.ids}
    out = [f"# AALP headless-student study — cohort {a.cohort}", ""]

    # ---- cohort table ----
    out += ["## Students", "", "| id | persona | model | turns | path finished | walkthrough eps | quiz rounds (L0/L1) | drill L0 | drill L1 | cost |",
            "|---|---|---|---|---|---|---|---|---|---|"]
    for i, r in runs.items():
        s, l = r["summary"], r["learning"]
        ps = l.get("platform_summary", {})
        wt = ps.get("walkthrough", {})
        q = ps.get("quiz", {})
        d = ps.get("drill", {})

        def drill(lv):
            x = d.get(lv)
            if not x:
                # unfinished drill: look at events
                subs = [e for e in r["events"] if e["kind"] == "submit" and e["location"].startswith(f"drill {lv}")]
                return f"not passed ({sum(e['correct'] for e in subs)}/{len(subs)} right)" if subs else "—"
            return f"passed r{x['rounds']} ({x['correct_last_round']}/{x['attempted_last_round']}, {x['entry_path']})"
        out.append(f"| {i} | {s.get('persona')} | {s.get('model')} | {l.get('turns')} | {'yes' if l.get('finished_path') else 'no: ' + str(l.get('stopped_at'))} | "
                   f"{wt.get('episodes_completed')}/5{' (left)' if wt.get('left_early') else ''} | {len(q.get('L0', []))}/{len(q.get('L1', []))} | {drill('L0')} | {drill('L1')} | ${s.get('cost_usd', 0)} |")

    # ---- learning gains ----
    out += ["", "## Assessment (0-2 per item, rubric-graded)", "",
            "Pre-tested items are asked before and after; the rest presuppose the platform and are post-only.", ""]
    head = "| item | concept | " + " | ".join(f"{i} pre→post" for i in runs) + " | mean pre | mean post |"
    out += [head, "|" + "---|" * (len(runs) + 4)]
    by_tag = defaultdict(lambda: {"pre": [], "post": []})
    for it in assess.ITEMS:
        cells, pres, posts = [], [], []
        for i, r in runs.items():
            g = r["grades"].get(it["id"], {})
            pre = g.get("pre", {}).get("score")
            post = g.get("post", {}).get("score")
            pres.append(pre)
            posts.append(post)
            cells.append(f"{fmt(pre)}→{fmt(post)}" if it["pre"] else f"{fmt(post)}")
        by_tag[it["tag"].split(":")[0]]["pre"] += pres
        by_tag[it["tag"].split(":")[0]]["post"] += posts
        out.append(f"| {it['id']} | {it['tag']} | " + " | ".join(cells) + f" | {fmt(mean(pres)) if it['pre'] else '—'} | {fmt(mean(posts))} |")
    out += ["", "| area | mean pre (pre-tested items) | mean post (all items) |", "|---|---|---|"]
    for tag, v in by_tag.items():
        out.append(f"| {'double-entry' if tag == 'de' else 'assertive accounting'} | {fmt(mean(v['pre']))} | {fmt(mean(v['post']))} |")

    # ---- misconceptions ----
    out += ["", "### Misconceptions the grader named (post-test)", ""]
    mis = Counter()
    examples = defaultdict(list)
    for i, r in runs.items():
        for iid, g in r["grades"].items():
            for m in g.get("post", {}).get("misconceptions", []) or []:
                key = f"{iid}: {m}"
                mis[key] += 1
                examples[key].append(i)
    for k, n in mis.most_common(25):
        out.append(f"- ({', '.join(examples[k])}) {k}")

    # ---- drill error patterns ----
    out += ["", "## Drill error patterns (from platform events)", ""]
    subs = [dict(e, student=i) for i, r in runs.items() for e in r["events"] if e["kind"] == "submit"]
    if subs:
        by_t = defaultdict(list)
        for e in subs:
            by_t[e.get("template")].append(e)
        out += ["| template | attempts | correct | most-missed assertions | most common 'closest' |", "|---|---|---|---|---|"]
        for t, es in sorted(by_t.items(), key=lambda kv: -len(kv[1])):
            missing = Counter(m for e in es if not e["correct"] for m in (e.get("missing") or []))
            closest = Counter(e.get("closest") for e in es if not e["correct"] and e.get("closest"))
            out.append(f"| {t} | {len(es)} | {sum(e['correct'] for e in es)} | {', '.join(f'{k}×{v}' for k, v in missing.most_common(3)) or '—'} | {(closest.most_common(1)[0][0] if closest else '—')[:70]} |")
        we = [e for i, r in runs.items() for e in r["events"] if e["kind"] == "worked_example"]
        out.append(f"\nWorked examples requested: {len(we)}" + (f" ({Counter(e.get('template') for e in we).most_common(3)})" if we else ""))
    else:
        out.append("No drill submissions recorded.")

    # ---- confusions by location ----
    out += ["", "## Where students got confused (gap analysis of think-aloud transcripts)", ""]
    conf = [dict(c, student=i) for i, r in runs.items() for c in (r["gaps"] or {}).get("confusions", [])]
    if conf:
        buckets = defaultdict(list)
        for c in conf:
            loc = c.get("location", "")
            key = "walkthrough" if "walkthrough" in loc.lower() or "episode" in loc.lower() else \
                  "drill" if "drill" in loc.lower() or "practice" in loc.lower() else \
                  "tutorial/quiz" if "tutorial" in loc.lower() or "quiz" in loc.lower() or "section" in loc.lower() else "other"
            buckets[key].append(c)
        out.append("| area | confusions | unresolved |")
        out.append("|---|---|---|")
        for k, cs in buckets.items():
            out.append(f"| {k} | {len(cs)} | {sum(1 for c in cs if not c.get('resolved'))} |")
        out.append("")
        for k, cs in buckets.items():
            out.append(f"### {k}")
            out.append("")
            for c in cs:
                out.append(f"- **{c['student']} t{c.get('turn')} — {c.get('location')}** {'(unresolved)' if not c.get('resolved') else ''}  ")
                out.append(f"  {c.get('what_confused_them')}  ")
                out.append(f"  *Cause:* {c.get('platform_cause')}  ")
                out.append(f"  *Suggested:* {c.get('suggested_revision')}")
            out.append("")
    else:
        out.append("No confusions reported.")

    # ---- learning moments and bugs ----
    out += ["## What clicked", ""]
    for i, r in runs.items():
        for m in (r["gaps"] or {}).get("learning_moments", [])[:6]:
            out.append(f"- {i} t{m.get('turn')} — {m.get('location')}: {m.get('what_clicked')}")
    out += ["", "## Possible bugs flagged by the analyst", ""]
    for i, r in runs.items():
        for b in (r["gaps"] or {}).get("possible_bugs", []):
            out.append(f"- ({i}) {b}")
    out += ["", "## Students' own words: most confusing part (post-test m1)", ""]
    for i, r in runs.items():
        out.append(f"- **{i}** ({r['summary'].get('persona')}): {(r['posttest'].get('m1') or {}).get('answer', '')}")
    out += ["", "## Analyst overall notes", ""]
    for i, r in runs.items():
        out.append(f"- **{i}**: {(r['gaps'] or {}).get('overall', '')}")

    path = os.path.join(HERE, "runs", f"report-{a.cohort}.md")
    open(path, "w").write("\n".join(out) + "\n")
    print("wrote", path)


if __name__ == "__main__":
    main()
