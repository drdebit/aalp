#!/usr/bin/env python3
"""Run one headless student through AALP and assess what they learned.

    python3 student.py --id s01 --persona novice --model haiku \
        --stages walkthrough,tutorial:0,tutorial:1 --max-turns 220

Writes runs/<id>/: transcript.jsonl (every screen, think-aloud and
action), transcript.md (readable), events.jsonl (platform events),
pretest.json, posttest.json, grades.json, gaps.json, summary.json."""
import argparse
import json
import os
import sys
import time
from datetime import datetime

from aalp_client import AalpClient
from aalp_platform import Platform
from llm import Session
import assess

PERSONAS = {
    "novice": ("Jordan", "a second-year business undergraduate who has never taken an accounting course. "
               "Reasonably bright and diligent, reads carefully, but knows nothing about bookkeeping: "
               "'debit' means a debit card to you, and you have never seen a journal entry."),
    "trad": ("Priya", "a junior who passed a traditional introductory accounting course a year ago. You know "
             "debits and credits, T-accounts and the standard journal entries fairly well, but you have never "
             "seen 'assertions'. You tend to think in accounts first ('this is DR Equipment, CR Cash') and may "
             "find the assertion framing roundabout — say so when you do."),
    "hasty": ("Marcus", "a sophomore who is busy and impatient. You skim text, try things quickly, and only "
              "re-read when you are stuck. Not careless about being right — just in a hurry, and you skip "
              "explanations you think you already get. You have had no accounting beyond a personal-finance unit in high school."),
}

STUDENT_SYSTEM = """You are {name}, {persona}

You are using an online accounting learning platform ("SP's T-Shirt Business") as part of a course. You will be shown the screen as text, one screen per turn, and you act by choosing ONE of the actions the screen lists.

Rules of the game — follow them exactly:
- You know only what your background gives you plus what the screen has shown you so far. Do NOT draw on outside knowledge of accounting or of this platform. If you catch yourself "knowing" something the platform has not taught, treat it as a guess and say so in your think-aloud.
- Be a real student, not an assistant: read, think in the first person, get confused, guess, make mistakes, re-read, get things right. Never mention being an AI, never refer to "the user", never ask anyone a question — nobody is there. Decide and act.
- Every turn respond with JSON: {{"think_aloud": "...", "action": {{...}}}}.
  * think_aloud: 2-5 sentences in your own voice: what you notice on the screen, what you don't understand, what you plan to do and why. This is the most important thing you produce — be candid about what is unclear.
  * action: exactly one action from the screen's "Actions available" list, in the JSON shape shown. Fill in real values (dates as YYYY-MM-DD, numbers as numbers, dropdown values exactly as listed).
- Read the whole screen each turn, especially text that appeared because of your last action. When the platform tells you to look at something, look (open the line) before moving on — once; don't loop.
- In the practice round, decide on your full set of assertions before you submit; you can add several across turns and then submit. Use 'Show me a worked example' only when you are genuinely stuck.
"""

TURN_SCHEMA = {"type": "object",
               "properties": {"think_aloud": {"type": "string"},
                              "action": {"type": "object",
                                         "properties": {"type": {"type": "string"},
                                                        "code": {"type": "string"},
                                                        "params": {"type": "object"},
                                                        "index": {"type": "integer"},
                                                        "question_id": {"type": "string"},
                                                        "choice": {"type": "integer"}},
                                         "required": ["type"]}},
               "required": ["think_aloud", "action"]}

PRETEST_INTRO = ("Before you start the platform, a short pre-check — not graded for the course, just to see where you "
                 "are starting from. Answer each question from whatever you currently know, in your own words. "
                 "It is completely fine to say you don't know; guess where you can and say it's a guess. "
                 "Reply with JSON: {\"answer\": \"...\", \"confidence\": 1-5} (5 = very sure).\n\nQuestion: ")
POSTTEST_INTRO = ("The learning session is over. Now a short exam, not graded for the course. Answer each question "
                  "from your own understanding — what you took away from the platform — in your own words. You cannot "
                  "look anything up. Be honest about what you do not know. "
                  "Reply with JSON: {\"answer\": \"...\", \"confidence\": 1-5} (5 = very sure).\n\nQuestion: ")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", required=True)
    ap.add_argument("--persona", default="novice", choices=sorted(PERSONAS))
    ap.add_argument("--model", default="haiku")
    ap.add_argument("--grader-model", default="sonnet")
    ap.add_argument("--stages", default="walkthrough,tutorial:0,tutorial:1")
    ap.add_argument("--max-turns", type=int, default=220)
    ap.add_argument("--base", default=None)
    ap.add_argument("--items", default=None, help="comma-separated item ids to assess (default: all)")
    ap.add_argument("--no-pretest", action="store_true")
    ap.add_argument("--no-grading", action="store_true")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "runs"))
    a = ap.parse_args()

    here = os.path.dirname(os.path.abspath(__file__))
    run = os.path.join(a.out, a.id)
    os.makedirs(run, exist_ok=True)
    content = json.load(open(os.path.join(here, "content", "content.json")))
    name, persona = PERSONAS[a.persona]
    system = STUDENT_SYSTEM.format(name=name, persona=persona)
    items = assess.ITEMS if not a.items else [i for i in assess.ITEMS if i["id"] in a.items.split(",")]
    llm_log = os.path.join(run, "llm_calls.jsonl")
    meta = {"id": a.id, "persona": a.persona, "name": name, "model": a.model, "grader_model": a.grader_model,
            "stages": a.stages.split(","), "started": datetime.now().isoformat(timespec="seconds")}
    json.dump(meta, open(os.path.join(run, "meta.json"), "w"), indent=1)

    def say(msg):
        print(f"[{a.id} {time.strftime('%H:%M:%S')}] {msg}", flush=True)

    # ---------------- pre-test ----------------
    pretest = {}
    if not a.no_pretest:
        pre = Session(a.model, system, name="pretest", log_path=llm_log)
        for it in [i for i in items if i["pre"]]:
            r, _ = pre.ask(PRETEST_INTRO + it["q"], schema=assess.ANSWER_SCHEMA)
            pretest[it["id"]] = r
            say(f"pretest {it['id']}: conf {r.get('confidence')}")
        json.dump(pretest, open(os.path.join(run, "pretest.json"), "w"), indent=1)

    # ---------------- learning ----------------
    client = AalpClient(a.base) if a.base else AalpClient()
    client.login(f"{a.id}@study.test")
    try:
        client.reset_simulation()
    except Exception:
        pass
    plat = Platform(client, content, a.stages.split(","), event_log=os.path.join(run, "events.jsonl"))
    stu = Session(a.model, system, name="student", log_path=llm_log)
    tpath = os.path.join(run, "transcript.jsonl")
    mdpath = os.path.join(run, "transcript.md")
    with open(mdpath, "w") as md:
        md.write(f"# {a.id} — {name} ({a.persona}, {a.model})\n\n")
    status = "You have just opened the platform."
    turn = 0
    stuck_count = 0
    while not plat.done and turn < a.max_turns:
        turn += 1
        screen = plat.render()
        loc = plat.location()
        msg = f"[Turn {turn}. Result of your last action: {status}]\n\n{screen}\n\nRespond with JSON: {{\"think_aloud\": \"...\", \"action\": {{...}}}}"
        try:
            r, secs = stu.ask(msg, schema=TURN_SCHEMA)
        except Exception as e:
            say(f"model failure: {e}")
            break
        think = r.get("think_aloud", "")
        action = r.get("action") or {}
        status = plat.apply(action)
        rec = {"turn": turn, "phase": plat.phase, "location": loc, "screen": screen,
               "think_aloud": think, "action": action, "result": status, "secs": round(secs, 1)}
        with open(tpath, "a") as f:
            f.write(json.dumps(rec) + "\n")
        with open(mdpath, "a") as md:
            md.write(f"## Turn {turn} — {loc}\n\n> {think}\n\n`{json.dumps(action)}` → {status}\n\n")
        say(f"t{turn} {loc[:60]} | {action.get('type')} -> {status[:60]}")
        # A student who keeps clicking a missing button is stuck on the UI; that is itself data, but bound it.
        stuck_count = stuck_count + 1 if status.startswith(("There is no", "No such")) else 0
        if stuck_count >= 8:
            say("student stuck on unavailable actions 8 turns running; ending learning phase")
            break
    learning = {"turns": turn, "finished_path": plat.done, "stopped_at": plat.location(),
                "platform_summary": plat.summary, "cost_usd": stu.total_cost}
    json.dump(learning, open(os.path.join(run, "learning.json"), "w"), indent=1)

    # ---------------- post-test ----------------
    posttest = {}
    for it in items + assess.META_ITEMS:
        try:
            r, _ = stu.ask(POSTTEST_INTRO + it["q"], schema=assess.ANSWER_SCHEMA)
        except Exception as e:
            r = {"answer": f"(no answer: {e})", "confidence": 0}
        posttest[it["id"]] = r
        say(f"posttest {it['id']}: conf {r.get('confidence')}")
    json.dump(posttest, open(os.path.join(run, "posttest.json"), "w"), indent=1)

    # ---------------- grading ----------------
    grades = {}
    gaps = None
    if not a.no_grading:
        grader = Session(a.grader_model, assess.GRADER_SYSTEM, name="grader", log_path=llm_log)
        for it in items:
            g = {}
            for when, bank in (("pre", pretest), ("post", posttest)):
                if it["id"] in bank:
                    try:
                        g[when], _ = grader.ask(assess.grade_prompt(it, bank[it["id"]].get("answer", "")),
                                                schema=assess.GRADE_SCHEMA)
                    except Exception as e:
                        g[when] = {"score": None, "rationale": f"grader failed: {e}", "misconceptions": []}
            grades[it["id"]] = g
            say(f"graded {it['id']}: pre {g.get('pre', {}).get('score')} post {g.get('post', {}).get('score')}")
        json.dump(grades, open(os.path.join(run, "grades.json"), "w"), indent=1)

        # gap analysis over the think-aloud transcript
        lines = []
        for rec in map(json.loads, open(tpath)):
            lines.append(f"turn {rec['turn']} [{rec['location']}] THINK: {rec['think_aloud']} | ACTION: {json.dumps(rec['action'])} | RESULT: {rec['result']}")
        transcript = "\n".join(lines)
        analyst = Session(a.grader_model, assess.GAP_SYSTEM, name="gap-analysis", log_path=llm_log)
        prompt = (f"Student persona: {persona}\nPath: {a.stages}\n\nTRANSCRIPT (think-aloud, action, platform response per turn):\n{transcript}\n\n"
                  f"POST-TEST ANSWERS (for cross-reference):\n{json.dumps({k: v.get('answer') for k, v in posttest.items()}, indent=1)}\n\nAnalyse.")
        try:
            gaps, _ = analyst.ask(prompt, schema=assess.GAP_SCHEMA)
        except Exception as e:
            gaps = {"error": str(e)}
        json.dump(gaps, open(os.path.join(run, "gaps.json"), "w"), indent=1)

    summary = {**meta, "finished": datetime.now().isoformat(timespec="seconds"), "learning": learning,
               "pretest": {k: v.get("confidence") for k, v in pretest.items()},
               "posttest_confidence": {k: v.get("confidence") for k, v in posttest.items()},
               "grades": {k: {w: g.get(w, {}).get("score") for w in ("pre", "post")} for k, g in grades.items()},
               "meta_answers": {k: posttest.get(k, {}).get("answer") for k in ("m1", "m2")},
               "n_confusions": len((gaps or {}).get("confusions", [])) if gaps else None,
               "cost_usd": round(stu.total_cost, 3)}
    json.dump(summary, open(os.path.join(run, "summary.json"), "w"), indent=1)
    say("done")


if __name__ == "__main__":
    main()
