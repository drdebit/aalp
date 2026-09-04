"""A text-mode client of AALP for headless students.

It reproduces the student's path through the real app in guided mode --
walkthrough episodes, level tutorial reading, the quiz, the practice
drill with its round mechanics -- rendering the same copy the browser
renders (pulled from the platform's own episodes.cljs and
tutorials.cljs via dump_content.clj) and calling the same backend
endpoints with the same payloads (api.cljs). Disclosure is mirrored
too: a journal-entry line's rule text and drill-down appear only when
the student opens the line, the walkthrough's `then` text only after
the step is done, and the derived entry in a drill only after commit.

Where the UI's affordances are visual (buttons, dropdowns), the screen
lists them as actions with the values the dropdown would offer."""
import copy
import json
import time
from collections import OrderedDict

ITEM_LABELS = {
    "blank-tshirts": "Blank T-Shirts",
    "ink-cartridges": "Ink Cartridges",
    "t-shirt-printer": "T-Shirt Printer",
    "printed-tshirts": "Printed T-Shirts",
    "logo-design": "Logo Design",
}
# The sentence builder hard-codes these (views.cljs render-provides/receives-fragment).
PROVIDES_ITEMS = ["printed-tshirts", "blank-tshirts"]
RECEIVES_ITEMS = ["printed-tshirts", "blank-tshirts", "ink-cartridges", "t-shirt-printer", "logo-design"]
UNIT_WORD = {"monetary-unit": "cash", "physical-unit": "physical units", "time-unit": "time",
             "effort-unit": "effort/labor", "service-unit": "a service", "ownership-units": "ownership units"}
FLOW_CODES = ("consumes", "creates")
# Legacy vocabulary entries the sentence builder never renders.
HIDDEN_CODES = {"consumes-inventory", "consumes-supplies", "consumes-labor", "creates-finished-goods"}
DRILL_CONFIG = {"round_size": 10, "pass_count": 8, "streak_pass": 5}  # tutorials/drill-config
STUCK_SECTIONS = {  # tutorials/stuck-sections
    0: {"provides": 2, "receives": 2, "has-counterparty": 2, "has-date": 2, "default": 2},
    1: {"requires": 1, "expects": 2, "default": 5},
    2: {"consumes": 1, "creates": 1, "is-allowed-by": 1, "default": 1},
}


def money(a):
    if a is None:
        return "—"
    try:
        a = float(a)
    except (TypeError, ValueError):
        return str(a)
    return f"${a:,.2f}" if a != int(a) else f"${int(a):,}"


class Platform:
    def __init__(self, client, content, stages, event_log=None):
        self.c = client
        self.episodes = content["episodes"]
        self.wt_level = content["walkthrough-assertion-level"]
        self.tutorials = content["tutorials"]
        self.stages = list(stages)
        self.stage = None
        self.phase = "done"
        self.level = 0
        self.vocab_cache = {}
        self.selected = OrderedDict()
        self.stash = {}          # explore mode: params of toggled-off assertions
        self.derived = None
        self.explore = False
        self.expanded = None
        self.feedback = None
        self.problem = None
        self.served_at = None
        self.drill = None
        self.wt = None
        self.walk_events = []
        self.reading = None
        self.quiz = None
        self.event_log = event_log
        self.summary = {"walkthrough": {"episodes_completed": 0, "left_early": False},
                        "quiz": {}, "drill": {}, "tutorials_completed": []}
        try:
            gs = client.guided_state()
            self.guided_vars = gs.get("variables") or {}
        except Exception:
            self.guided_vars = {}
        self._next_stage()

    # ------------------------------------------------------------ logging
    def log(self, kind, **data):
        if self.event_log:
            with open(self.event_log, "a") as f:
                f.write(json.dumps({"t": time.time(), "kind": kind, "phase": self.phase,
                                    "location": self.location(), **data}) + "\n")

    def location(self):
        if self.phase == "walkthrough" and self.wt:
            ep = self.episodes[self.wt["episode"]]
            return f"walkthrough ep{self.wt['episode']+1} '{ep['title']}' step {self.wt['step']+1}"
        if self.phase in ("reading",):
            return f"tutorial L{self.level} section {self.reading['index']+1}"
        if self.phase in ("quiz", "quiz_results"):
            return f"tutorial L{self.level} quiz"
        if self.phase == "drill" and self.drill:
            return f"drill L{self.level} round {self.drill['round']} problem {self.drill['attempted']+1} ({(self.problem or {}).get('template')})"
        if self.phase == "gate":
            return f"tutorial gate L{self.level}"
        return self.phase

    # ------------------------------------------------------------ stages
    @property
    def done(self):
        return self.phase == "done"

    def _next_stage(self):
        self.selected = OrderedDict()
        self.feedback = None
        self.derived = None
        self.explore = False
        self.expanded = None
        if not self.stages:
            self.phase = "done"
            self.stage = None
            return
        st = self.stages.pop(0)
        self.stage = st
        if st == "walkthrough":
            self.phase = "walkthrough"
            self.wt = {"episode": 0, "step": 0}
            self.walk_events = []
            self._derive()
        elif st.startswith("tutorial:"):
            self.level = int(st.split(":")[1])
            self.phase = "gate"
        elif st.startswith("drill:"):
            self.level = int(st.split(":")[1])
            self._start_drill("direct")
        else:
            raise ValueError(f"unknown stage {st}")
        self.log("stage", stage=st)

    # ------------------------------------------------------------ vocabulary
    def vocab(self, level):
        if level not in self.vocab_cache:
            groups = self.c.assertions(level)
            flat = []
            for dom, lst in groups.items():
                for a in lst:
                    if a["code"] not in HIDDEN_CODES:
                        flat.append(dict(a, domain=dom))
            self.vocab_cache[level] = flat
        return self.vocab_cache[level]

    def palette(self):
        if self.phase == "walkthrough":
            allowed = set(self.episodes[self.wt["episode"]]["palette"])
            return [a for a in self.vocab(self.wt_level) if a["code"] in allowed]
        return self.vocab(self.level)

    def param_options(self, code, key):
        for a in self.vocab(max(self.level, self.wt_level if self.phase == "walkthrough" else 0)):
            if a["code"] == code:
                return (a.get("parameters") or {}).get(key, {}).get("options") or []
        return []

    # ------------------------------------------------------------ derivation
    def _variables(self):
        if self.phase == "walkthrough":
            return {}   # api.cljs derive-je!: the walkthrough's numbers are the student's own
        return (self.problem or {}).get("variables") or {}

    def _prior_events(self):
        if self.phase == "walkthrough":
            return self.walk_events
        return (self.problem or {}).get("prior-events") or []

    def _derive(self):
        try:
            self.derived = self.c.derive_je(self._sel_payload(), self._variables(), self._prior_events(),
                                            isolated=self.phase in ("walkthrough", "drill"))
        except Exception as e:
            self.derived = {"error": str(e)}

    def _sel_payload(self):
        return {k: v for k, v in self.selected.items()}

    # ------------------------------------------------------------ walkthrough logic
    def _wt_step(self):
        ep = self.episodes[self.wt["episode"]]
        return ep, ep["steps"][self.wt["step"]]

    def _step_complete(self, step):
        do = step.get("do")
        if not do:
            return True
        kind = do.get("kind")
        if kind == "read":
            return True
        if kind == "set-date":
            return "has-date" in self.selected
        if kind == "remove":
            return do.get("code") not in self.selected
        if kind == "assert":
            sel = self.selected.get(do["code"])
            if sel is None:
                return False
            if isinstance(sel, list) or do["code"] in FLOW_CODES:
                return True
            return all(str(sel.get(k)) == str(v) for k, v in (do.get("params") or {}).items())
        return True

    # ------------------------------------------------------------ drill logic
    def _start_drill(self, entry_path):
        self.drill = {"active": True, "level": self.level, "attempted": 0, "correct": 0,
                      "streak": 0, "round": 1, "miss_streak": 0, "miss_assertions": {},
                      "worked_example": False, "entry_path": entry_path, **DRILL_CONFIG,
                      "history": []}
        self.phase = "drill"
        self._fetch_problem()

    def _fetch_problem(self):
        self.problem = self.c.generate_problem(self.level, "forward")
        self.selected = OrderedDict()
        self.feedback = None
        self.derived = None
        self.explore = False
        self.expanded = None
        self.stash = {}
        self.selected["has-date"] = {}
        d = (self.problem.get("variables") or {}).get("date")
        if d:
            self.selected["has-date"]["date"] = d
        self.served_at = time.time()
        self.log("problem_served", template=self.problem.get("template"),
                 narrative=self.problem.get("narrative"))

    def _drill_status(self):
        d = self.drill
        remaining = d["round_size"] - d["attempted"]
        streak_passed = d["streak_pass"] and d["streak"] >= d["streak_pass"]
        passed = d["correct"] >= d["pass_count"] or streak_passed
        streak_reachable = d["streak_pass"] and (d["streak"] + remaining) >= d["streak_pass"]
        unreachable = (d["correct"] + remaining) < d["pass_count"] and not streak_reachable
        return passed, streak_passed, unreachable

    def _finish_drill(self):
        d = self.drill
        self.summary["drill"][f"L{self.level}"] = {
            "rounds": d["round"], "attempted_last_round": d["attempted"],
            "correct_last_round": d["correct"], "entry_path": d["entry_path"],
            "history": d["history"], "passed": True}
        self.summary["tutorials_completed"].append(self.level)
        try:
            self.c.complete_tutorial(self.level)
        except Exception:
            pass
        self.drill = None
        self.problem = None
        self.log("drill_passed", level=self.level)
        self._next_stage()

    # ------------------------------------------------------------ actions
    def apply(self, action):
        """Apply a student action. Returns a status string (what the UI
        would show in response, or why the click did nothing)."""
        t = (action or {}).get("type", "")
        try:
            handler = getattr(self, "act_" + t.replace("-", "_"), None)
            if handler is None:
                return f"There is no '{t}' button on this screen."
            return handler(action) or "OK."
        except ActionError as e:
            return str(e)

    # -- builder actions (walkthrough + drill) --
    def _builder_open(self):
        if self.phase == "walkthrough":
            return True
        if self.phase == "drill":
            return self.feedback is None and not self.drill.get("worked_example")
        return False

    def _code_available(self, code):
        return any(a["code"] == code for a in self.palette())

    def _normalize_params(self, code, params):
        params = dict(params or {})
        if code in FLOW_CODES:
            flows = params.get("flows")
            if flows is None and ("physical-item" in params or "quantity" in params):
                flows = [{"quantity": params.get("quantity"), "physical-item": params.get("physical-item")}]
            out = []
            for fl in flows or []:
                f = {"unit": "physical-unit"}
                if fl.get("quantity") not in (None, ""):
                    f["quantity"] = _num(fl["quantity"])
                if fl.get("physical-item"):
                    if fl["physical-item"] not in ITEM_LABELS:
                        raise ActionError(f"'{fl['physical-item']}' is not in the item dropdown. Options: {', '.join(ITEM_LABELS)}.")
                    f["physical-item"] = fl["physical-item"]
                out.append(f)
            # The UI stores a single flow as a map and only several as a vector
            # (state.cljs update-flow-parameter!); mirror that shape exactly.
            if len(out) == 1:
                return out[0]
            return out or {}
        clean = {}
        for k, v in params.items():
            if v in (None, ""):
                continue
            if k == "quantity":
                clean[k] = _num(v)
            elif k == "confidence":
                clean[k] = _num(v)
            else:
                clean[k] = str(v)
        if code in ("provides", "receives", "requires"):
            unit = clean.get("unit")
            if unit and unit not in UNIT_WORD:
                raise ActionError(f"'{unit}' is not a unit type. The dropdown offers: {', '.join(UNIT_WORD)}.")
            if "physical-item" in clean:
                allowed = PROVIDES_ITEMS if code == "provides" else RECEIVES_ITEMS
                if clean["physical-item"] not in allowed:
                    raise ActionError(f"'{clean['physical-item']}' is not in the item dropdown for {code}. Options: {', '.join(allowed)}.")
                clean.setdefault("unit", "physical-unit")
        if code == "allows":
            items = params.get("consumes-items")
            if items is None and "consumes-item" in params:
                items = [params["consumes-item"]]
            if isinstance(items, str):
                items = [items]
            if items is not None:
                items = [i for i in items if i]
                for i in items:
                    if i not in ITEM_LABELS:
                        raise ActionError(f"'{i}' is not in the dropdown for consumes-items. Options: {', '.join(ITEM_LABELS)}.")
                clean["consumes-items"] = items
            clean.pop("consumes-item", None)
            if "creates-item" in clean and clean["creates-item"] not in ITEM_LABELS:
                raise ActionError(f"'{clean['creates-item']}' is not in the dropdown for creates-item. Options: {', '.join(ITEM_LABELS)}.")
        if code == "is-allowed-by":
            opts = [o["value"] for o in self.param_options("is-allowed-by", "capacity")]
            if "capacity" in clean and opts and clean["capacity"] not in opts:
                raise ActionError(f"'{clean['capacity']}' is not in the dropdown. Options: {', '.join(opts)}.")
        # Mirror views.cljs auto-populate-assertion!: the verb is fixed and
        # the unit defaults to the other side of the present exchange.
        if code == "requires":
            clean["action"] = "receives" if self.selected.get("provides", {}).get("unit") == "physical-unit" else "provides"
            clean.setdefault("unit", "physical-unit" if self.selected.get("receives", {}).get("unit") == "monetary-unit" else "monetary-unit")
        if code == "expects":
            clean["action"] = "receives"
            clean.setdefault("unit", "service-unit" if self.selected.get("provides", {}).get("unit") == "monetary-unit" else "monetary-unit")
        return clean

    def act_add_assertion(self, a):
        if not self._builder_open():
            raise ActionError("The sentence builder is not open right now.")
        code = a.get("code")
        if not code or not self._code_available(code):
            raise ActionError(f"'{code}' is not among the assertions offered on this screen.")
        params = self._normalize_params(code, a.get("params"))
        already = code in self.selected
        if already and code not in FLOW_CODES:
            self.selected[code].update(params)
        else:
            self.selected[code] = params
        self.stash.pop(code, None)
        self._after_builder_change()
        self.log("assert", code=code, params=params, updated=already)
        return f"{'Updated' if already else 'Added'} {code}."

    def act_set_params(self, a):
        code = a.get("code")
        if code not in self.selected:
            raise ActionError(f"{code} is not in your sentence yet; add it first.")
        return self.act_add_assertion(a)

    def act_remove_assertion(self, a):
        if not self._builder_open() and not self.explore:
            raise ActionError("The sentence builder is not open right now.")
        code = a.get("code")
        if code not in self.selected:
            raise ActionError(f"{code} is not in your sentence.")
        del self.selected[code]
        self._after_builder_change()
        self.log("remove", code=code)
        return f"Removed {code}."

    def act_clear(self, a):
        if self.phase != "drill" or not self._builder_open():
            raise ActionError("No Clear button here.")
        d = (self.problem.get("variables") or {}).get("date")
        self.selected = OrderedDict([("has-date", {"date": d} if d else {})])
        return "Cleared."

    def _after_builder_change(self):
        self.expanded = None
        if self.phase == "walkthrough":
            self._derive()

    # -- explore mode (walkthrough always; drill after feedback) --
    def act_explore(self, a):
        if self.derived is None:
            raise ActionError("There is no derived entry to explore yet.")
        self.explore = not self.explore
        self._derive()
        return "Exploring: toggle assertions on and off and watch the entry change." if self.explore else "Explore off."

    def act_toggle_assertion(self, a):
        if not self.explore:
            raise ActionError("Turn Explore on first.")
        code = a.get("code")
        if code in self.selected:
            self.stash[code] = self.selected.pop(code)
            msg = f"{code} switched off."
        elif code in self.stash:
            self.selected[code] = self.stash.pop(code)
            msg = f"{code} switched back on."
        else:
            raise ActionError(f"{code} is not one of your assertions.")
        self.expanded = None
        self._derive()
        self.log("explore_toggle", code=code)
        return msg

    def act_open_line(self, a):
        if not self.derived or not self.derived.get("lines"):
            raise ActionError("There are no entry lines to open.")
        i = a.get("index")
        try:
            i = int(i)
            self.derived["lines"][i]
        except (TypeError, ValueError, IndexError):
            raise ActionError(f"No line {i}. Lines are numbered from 0.")
        self.expanded = None if self.expanded == i else i
        self.log("open_line", index=i, account=self.derived["lines"][i].get("account"))
        return "Opened." if self.expanded is not None else "Closed."

    def act_quit(self, a):
        """The student closes the site. Always available; logged as such."""
        self.summary["quit"] = {"at": self.location(), "reason": a.get("reason", "")}
        self.log("quit", reason=a.get("reason", ""))
        self.stages = []
        self.phase = "done"
        return "You closed the platform."

    def act_close_line(self, a):
        self.expanded = None
        return "Closed."

    # -- walkthrough --
    def act_next(self, a):
        if self.phase != "walkthrough":
            raise ActionError("No Next button here.")
        ep, st = self._wt_step()
        if not self._step_complete(st):
            raise ActionError("Do the step first — the explanation is about what appears when you do.")
        nsteps = len(ep["steps"])
        neps = len(self.episodes)
        last = self.wt["step"] + 1 == nsteps and self.wt["episode"] + 1 == neps
        self.log("wt_next")
        if last:
            self.summary["walkthrough"]["episodes_completed"] = neps
            self.log("walkthrough_finished")
            self.wt = None
            self._next_stage()
            return "Walkthrough finished."
        if self.wt["step"] + 1 < nsteps:
            self.wt["step"] += 1
            self.expanded = None
            return "Next step."
        # episode boundary: what was built joins the chain
        if self.selected:
            self.walk_events.append(copy.deepcopy(self._sel_payload()))
        self.summary["walkthrough"]["episodes_completed"] = self.wt["episode"] + 1
        self.wt = {"episode": self.wt["episode"] + 1, "step": 0}
        self.selected = OrderedDict()
        self.stash = {}
        self.explore = False
        self.expanded = None
        self._derive()
        return "New episode."

    def act_leave_walkthrough(self, a):
        if self.phase != "walkthrough":
            raise ActionError("No such button here.")
        self.summary["walkthrough"]["left_early"] = True
        self.log("walkthrough_left")
        self.wt = None
        self._next_stage()
        return "Left the walkthrough."

    # -- gate --
    def act_start_tutorial(self, a):
        if self.phase != "gate":
            raise ActionError("No such button here.")
        self.reading = {"index": 0, "review_only": False}
        self.phase = "reading"
        self.log("tutorial_started", level=self.level)
        return "Tutorial opened."

    def act_test_out(self, a):
        if self.phase != "gate":
            raise ActionError("No such button here.")
        self.log("test_out", level=self.level)
        self._start_drill("test-out")
        return "Practice round started."

    # -- reading --
    def _sections(self):
        return self.tutorials[str(self.level)]["sections"]

    def act_next_section(self, a):
        if self.phase != "reading":
            raise ActionError("No such button here.")
        if self.reading["index"] + 1 >= len(self._sections()):
            raise ActionError("This is the last section.")
        self.reading["index"] += 1
        return "OK."

    def act_prev_section(self, a):
        if self.phase != "reading":
            raise ActionError("No such button here.")
        if self.reading["index"] == 0:
            raise ActionError("This is the first section.")
        self.reading["index"] -= 1
        return "OK."

    def act_go_to_section(self, a):
        if self.phase != "reading":
            raise ActionError("No such button here.")
        i = int(a.get("index", 0))
        if not 0 <= i < len(self._sections()):
            raise ActionError("No such section.")
        self.reading["index"] = i
        return "OK."

    def act_take_quiz(self, a):
        if self.phase != "reading" or self.reading["review_only"]:
            raise ActionError("No such button here.")
        if self.reading["index"] + 1 < len(self._sections()):
            raise ActionError("The quiz button appears on the last section.")
        qs = self.tutorials[str(self.level)]["quiz"]
        self.quiz = {"questions": qs, "answers": {}, "retry": False, "results": None, "rounds": 0}
        self.phase = "quiz"
        return "Quiz opened."

    def act_back_to_drill(self, a):
        if self.phase != "reading" or not self.reading["review_only"]:
            raise ActionError("No such button here.")
        self.phase = "drill"
        return "Back to the practice round."

    # -- quiz --
    def act_answer(self, a):
        if self.phase != "quiz":
            raise ActionError("No quiz open.")
        qid = str(a.get("question_id"))
        qs = {q["id"]: q for q in self.quiz["questions"]}
        if qid not in qs:
            raise ActionError(f"No question '{qid}'.")
        ch = a.get("choice")
        try:
            ch = int(ch)
            qs[qid]["choices"][ch]
        except (TypeError, ValueError, IndexError):
            raise ActionError("Choice must be the number of one of the listed options.")
        self.quiz["answers"][qid] = ch
        return f"Answered {qid}."

    def act_submit_quiz(self, a):
        if self.phase != "quiz":
            raise ActionError("No quiz open.")
        qs = self.quiz["questions"]
        if any(q["id"] not in self.quiz["answers"] for q in qs):
            raise ActionError("Answer every question first.")
        results, missed = [], []
        for q in qs:
            ui = self.quiz["answers"][q["id"]]
            ok = ui == q["correct"]
            results.append({"question": q["question"], "correct": ok,
                            "user_answer": q["choices"][ui], "correct_answer": q["choices"][q["correct"]],
                            "explanation": q["explanation"]})
            if not ok:
                missed.append(q)
        self.quiz["results"] = results
        self.quiz["missed"] = missed
        self.quiz["rounds"] += 1
        self.phase = "quiz_results"
        key = f"L{self.level}"
        self.summary["quiz"].setdefault(key, []).append(
            {"correct": sum(r["correct"] for r in results), "total": len(results),
             "missed": [q["id"] for q in missed]})
        self.log("quiz_submitted", correct=sum(r["correct"] for r in results), total=len(results),
                 missed=[q["id"] for q in missed])
        return "Graded."

    def act_try_again(self, a):
        if self.phase != "quiz_results" or not self.quiz["missed"]:
            raise ActionError("No such button here.")
        self.quiz = {"questions": self.quiz["missed"], "answers": {}, "retry": True,
                     "results": None, "rounds": self.quiz["rounds"]}
        self.phase = "quiz"
        return "Retrying the missed questions."

    def act_continue(self, a):
        if self.phase != "quiz_results" or self.quiz["missed"]:
            raise ActionError("No such button here.")
        self._start_drill("tutorial")
        return "Practice round started."

    # -- drill --
    def act_submit(self, a):
        if self.phase != "drill" or not self._builder_open():
            raise ActionError("Nothing to submit right now.")
        if not self.selected:
            raise ActionError("Select at least one assertion first.")
        p = self.problem
        payload = {"selected-assertions": self._sel_payload(),
                   "correct-classification": p.get("correct-classification"),
                   "prior-events": p.get("prior-events") or [],
                   "problem-id": p.get("id"), "problem-type": p.get("problem-type") or "forward",
                   "level": p.get("level", 0), "template-level": p.get("template-level"),
                   "template-key": p.get("template"),
                   "seconds-elapsed": int(time.time() - (self.served_at or time.time())),
                   "drill-entry": self.drill["entry_path"]}
        resp = self.c.classify(payload)
        self.feedback = resp.get("feedback") or {}
        self._derive()
        status = self.feedback.get("status")
        correct = status == "correct"
        missing = sorted(set((p.get("correct-assertions") or {}).keys()) - set(self.selected.keys()))
        d = self.drill
        d["attempted"] += 1
        d["correct"] += 1 if correct else 0
        d["streak"] = d["streak"] + 1 if correct else 0
        d["miss_streak"] = 0 if correct else d["miss_streak"] + 1
        if not correct:
            for m in missing:
                d["miss_assertions"][m] = d["miss_assertions"].get(m, 0) + 1
        d["history"].append({"round": d["round"], "template": p.get("template"), "correct": correct,
                             "status": status, "selected": self._sel_payload(),
                             "correct_assertions": p.get("correct-assertions"),
                             "closest": self.feedback.get("message")})
        self.log("submit", template=p.get("template"), correct=correct, status=status,
                 selected=self._sel_payload(), missing=missing, hints=self.feedback.get("hints"))
        return "Submitted."

    def act_worked_example(self, a):
        if self.phase != "drill" or not self._builder_open():
            raise ActionError("No such button here.")
        p = self.problem
        self.selected = OrderedDict(p.get("correct-assertions") or {})
        self.drill["worked_example"] = True
        self._derive()
        try:
            self.c.worked_example_viewed({"problem-id": p.get("id"), "level": p.get("level", 0),
                                          "template-key": p.get("template"),
                                          "drill-entry": self.drill["entry_path"],
                                          "seconds-elapsed": int(time.time() - (self.served_at or time.time()))})
        except Exception:
            pass
        self.drill["history"].append({"round": self.drill["round"], "template": p.get("template"),
                                      "worked_example": True})
        self.log("worked_example", template=p.get("template"))
        return "Worked example shown. This problem no longer counts."

    def act_fresh_problem(self, a):
        if self.phase != "drill" or not self.drill.get("worked_example"):
            raise ActionError("No such button here.")
        self.drill["worked_example"] = False
        self._fetch_problem()
        return "Fresh problem."

    def act_next_problem(self, a):
        if self.phase != "drill" or self.feedback is None:
            raise ActionError("No such button here.")
        passed, _, unreachable = self._drill_status()
        if passed or unreachable:
            raise ActionError("The round is over; use the button shown.")
        self._fetch_problem()
        return "Next problem."

    def act_finish_drill(self, a):
        if self.phase != "drill" or self.feedback is None:
            raise ActionError("No such button here.")
        passed, _, _ = self._drill_status()
        if not passed:
            raise ActionError("You have not passed the round yet.")
        self._finish_drill()
        return "Practice round passed."

    def act_fresh_round(self, a):
        if self.phase != "drill" or self.feedback is None:
            raise ActionError("No such button here.")
        _, _, unreachable = self._drill_status()
        if not unreachable:
            raise ActionError("No such button here.")
        d = self.drill
        d.update({"attempted": 0, "correct": 0, "streak": 0, "miss_streak": 0,
                  "miss_assertions": {}, "worked_example": False, "round": d["round"] + 1})
        self.log("fresh_round", round=d["round"])
        self._fetch_problem()
        return "Fresh round."

    def act_read_tutorial(self, a):
        if self.phase != "drill" or self.feedback is None:
            raise ActionError("No such button here.")
        _, _, unreachable = self._drill_status()
        if not (unreachable and self.drill["entry_path"] == "test-out"):
            raise ActionError("No such button here.")
        self.drill = None
        self.problem = None
        self.feedback = None
        self.reading = {"index": 0, "review_only": False}
        self.phase = "reading"
        return "Tutorial opened."

    def act_review_tutorial(self, a):
        if self.phase != "drill":
            raise ActionError("No such button here.")
        d = self.drill
        idx = 0
        if d["miss_streak"] >= 2 and d["miss_assertions"]:
            worst = max(d["miss_assertions"].items(), key=lambda kv: kv[1])[0]
            m = STUCK_SECTIONS.get(self.level, {})
            idx = min(m.get(worst, m.get("default", 0)), len(self._sections()) - 1)
        self.reading = {"index": idx, "review_only": True}
        self.phase = "reading"
        self.log("review_tutorial", section=idx)
        return "Tutorial opened for review."

    # ------------------------------------------------------------ rendering
    def render(self):
        f = getattr(self, "render_" + self.phase)
        return f() + '\n(Always available: {"type":"quit","reason":"..."} closes the site for good — only if you would really give up and leave.)'


    def _sentence(self):
        if not self.selected:
            return "  (nothing said yet)"
        out = []
        for code, p in self.selected.items():
            if code == "has-date":
                out.append(f"  On {p.get('date') or '(date not set)'}   [has-date]")
            elif code in ("provides", "receives"):
                q = p.get("quantity", "(qty not set)")
                unit = p.get("unit")
                if unit == "physical-unit":
                    thing = ITEM_LABELS.get(p.get("physical-item"), "(item not chosen)")
                elif unit:
                    thing = UNIT_WORD.get(unit, unit)
                else:
                    thing = "(unit type not set)"
                out.append(f"  the business {code} {q} {thing}   [{code}: {_kv(p)}]")
            elif code == "has-counterparty":
                out.append(f"  with {p.get('name') or '(party name not set)'}   [has-counterparty]")
            elif code == "allows":
                ins = " and ".join(ITEM_LABELS.get(i, "(item)") for i in (p.get("consumes-items") or [])) or "(inputs)"
                out.append(f"  This makes possible: which allows SP to turn {ins} into {ITEM_LABELS.get(p.get('creates-item'), '(item)')}   [allows: {_kv(p)}]")
            elif code == "is-allowed-by":
                out.append(f"  This is: enabled by {ITEM_LABELS.get(p.get('capacity'), '(item)')}   [is-allowed-by: {_kv(p)}]")
            elif code in FLOW_CODES:
                fls = p if isinstance(p, list) else ([p] if p else [])
                flows = "; ".join(f"{fl.get('quantity', '(qty)')} × {ITEM_LABELS.get(fl.get('physical-item'), '(item)')}" for fl in fls) or "(nothing yet)"
                out.append(f"  {'Consumes' if code == 'consumes' else 'Creates'}: {flows}   [{code}]")
            elif code == "requires":
                party = self.selected.get("has-counterparty", {}).get("name") or "the counterparty"
                unit = {"physical-unit": "goods", "service-unit": "services"}.get(p.get("unit"), "cash")
                if p.get("action") == "receives":
                    out.append(f"  This creates an obligation: The business is to receive {p.get('quantity', '(amount)')} {unit} from {party} by {p.get('due-date', '(due date)')} — a claim the business holds; {party} must provide it.   [requires: {_kv(p)}]")
                else:
                    out.append(f"  This creates an obligation: The business must provide {p.get('quantity', '(amount)')} {unit} to {party} by {p.get('due-date', '(due date)')} — a debt the business owes.   [requires: {_kv(p)}]")
            elif code == "expects":
                # views.cljs: a prepaid (money out + expects) reads "expects to receive services"
                prepaid = self.selected.get("provides", {}).get("unit") == "monetary-unit"
                what = {"physical-unit": "goods", "service-unit": "services", "monetary-unit": "cash"}.get(p.get("unit"), "goods or services" if prepaid else "cash")
                out.append(f"  Expectation of fulfillment: the business expects to receive {what} with {p.get('confidence', '(?)')}% confidence   [expects: {_kv(p)}]")
            else:
                out.append(f"  [{code}: {_kv(p)}]")
        return "\n".join(out)

    def _palette_text(self):
        lines = ["Assertions you can add (each takes the fields shown; dropdown values are given as value = label):"]
        for a in self.palette():
            code = a["code"]
            fields = []
            params = a.get("parameters") or {}
            if code in ("provides", "receives"):
                units = ", ".join(f"{o['value']} = {o['label']}" for o in params.get("unit", {}).get("options", []))
                items = PROVIDES_ITEMS if code == "provides" else RECEIVES_ITEMS
                fields = [f"unit (dropdown: {units})", "quantity (number)",
                          "physical-item (only when unit = physical-unit; dropdown: " + ", ".join(f"{i} = {ITEM_LABELS[i]}" for i in items) + ")"]
            elif code in FLOW_CODES:
                fields = ["flows: a list of {quantity, physical-item}; add another row for a second input. physical-item dropdown: " + ", ".join(f"{i} = {ITEM_LABELS[i]}" for i in RECEIVES_ITEMS)]
            elif code == "allows":
                fields = ["consumes-items: a list of inputs, e.g. [\"blank-tshirts\", \"ink-cartridges\"] (dropdown: " + ", ".join(f"{i} = {ITEM_LABELS[i]}" for i in RECEIVES_ITEMS) + ")",
                          "creates-item (dropdown: " + ", ".join(f"{i} = {ITEM_LABELS[i]}" for i in RECEIVES_ITEMS) + ")"]
            else:
                if code == "requires":
                    params = {k: v for k, v in params.items() if k != "action"}
                if code == "expects":
                    params = dict(params, unit={"type": "dropdown", "label": "what", "options": [{"value": "monetary-unit", "label": "cash"}, {"value": "physical-unit", "label": "goods or services"}]})
                for k, spec in params.items():
                    desc = spec.get("type", "")
                    if spec.get("options"):
                        desc = "dropdown: " + ", ".join(f"{o['value']} = {o['label']}" for o in spec["options"])
                    elif spec.get("type") == "date":
                        desc = "date, YYYY-MM-DD"
                    elif spec.get("type") == "percentage":
                        desc = f"percentage {spec.get('min', 0)}-{spec.get('max', 100)}"
                    fields.append(f"{k} ({desc}{'; optional' if spec.get('optional') else ''})")
            mark = " (already in your sentence)" if code in self.selected else ""
            lines.append(f"  - {code} — \"{a.get('label')}\": {a.get('description', '')}{mark}")
            if fields:
                lines.append("      fields: " + "; ".join(fields))
        return "\n".join(lines)

    def _derived_text(self, header=True):
        d = self.derived
        if not d:
            return ""
        if "error" in d:
            return f"[derivation unavailable: {d['error']}]"
        out = []
        for u in d.get("unsupported") or []:
            out.append(f"  ! {u.get('message')}")
        if header:
            out.append("--- What your assertions produce ---" + ("   [Explore: ON — toggle assertions and watch]" if self.explore else "   [button: Explore]"))
        lines = d.get("lines") or []
        ph = d.get("placeholders") or []
        if not lines and not ph:
            out.append("  No journal-entry lines yet -- nothing in SP's rulebook matches these assertions.")
        for i, ln in enumerate(lines):
            side = "DR" if ln.get("side") == "debit" else "CR"
            amt = money(ln.get("amount")) if ln.get("amount") is not None else "—"
            prov = " + ".join(ln.get("provenance") or [])
            lbl = f" ({ln['entry-label']})" if ln.get("entry-label") else ""
            out.append(f"  [{i}] {side} {ln.get('account')}{lbl}  {amt}   ← {prov}" + ("   (open)" if self.expanded == i else ""))
            if self.expanded == i:
                out.append(f"        Rule: {ln.get('rule-text')}")
                if ln.get("assertions"):
                    out.append("        Underneath this line: " + "; ".join(f"{k} {_kv(v)}" if isinstance(v, dict) else f"{k} {v}" for k, v in ln["assertions"].items()))
                for e in ln.get("established-by") or []:
                    a_ = e.get("assertions") or {}
                    if a_.get("allows"):
                        txt = f"you said this turns {' and '.join(a_['allows'].get('consumes-items') or [a_['allows'].get('consumes-item')])} into {a_['allows'].get('creates-item')}"
                    else:
                        txt = "recorded as " + ", ".join(a_.keys())
                    out.append(f"        Decided earlier: {e.get('date') or ''} {txt}")
                if ln.get("unresolved-reason"):
                    out.append(f"        {ln['unresolved-reason']}")
        for p in ph:
            side = "DR" if p.get("side") == "debit" else "CR"
            out.append(f"      {side} ???  ?   {p.get('prompt')}")
        if lines:
            t = d.get("totals") or {}
            out.append("  ✓ Balanced" if t.get("balanced?") else f"  Debits {money(t.get('debits'))} vs credits {money(t.get('credits'))} -- not balanced yet")
        if d.get("context"):
            out.append("  Context: " + "; ".join(f"{c.get('code')} {c.get('role')}" for c in d["context"]))
        if d.get("not-reflected"):
            out.append("  Recorded -- but not reflected:")
            for nr in d["not-reflected"]:
                out.append(f"    [{nr.get('code')}] {nr.get('text')}")
        if lines:
            out.append("  (click a line to see the rule behind it: open_line with its [index])")
        return "\n".join(out)

    def _builder_actions(self):
        acts = ['{"type":"add_assertion","code":"<code>","params":{...}}  (adding one already in the sentence updates its fields)',
                '{"type":"remove_assertion","code":"<code>"}']
        if self.derived and self.derived.get("lines"):
            acts.append('{"type":"open_line","index":<n>} / {"type":"close_line"}')
        return acts

    def render_walkthrough(self):
        ep, st = self._wt_step()
        done = self._step_complete(st)
        n = len(ep["steps"])
        out = [f"=== WALKTHROUGH — Episode {self.wt['episode']+1} — {ep['title']}   (step {self.wt['step']+1} of {n}) ===",
               st["say"]]
        do = st.get("do")
        if do and not done:
            kind = do.get("kind")
            todo = {"set-date": "Add the date to continue.", "read": "Have a look, then carry on.",
                    "remove": f"Switch {do.get('code')} off to continue.",
                    "assert": f"Add {do.get('code')} to continue."}.get(kind, "")
            out.append(f"  → {todo}")
        if done and st.get("then"):
            out.append(st["then"])
        last = self.wt["step"] + 1 == n and self.wt["episode"] + 1 == len(self.episodes)
        out.append(f"[button: {'Finish' if last else 'Next'}{'' if done else ' — disabled: Do the step first — the explanation is about what appears when you do.'}]   [button: Leave the walkthrough]")
        out.append("")
        out.append("--- Your sentence (the sentence builder) ---")
        out.append(self._sentence())
        out.append(self._palette_text())
        out.append("")
        out.append(self._derived_text())
        out.append("")
        acts = self._builder_actions() + ['{"type":"next"}' + ("" if done else " (disabled until the step is done)"),
                                          '{"type":"leave_walkthrough"}',
                                          '{"type":"explore"} toggles Explore; while exploring: {"type":"toggle_assertion","code":"<code>"}']
        out.append("Actions available: " + " | ".join(acts))
        return "\n".join(out)

    def render_gate(self):
        t = self.tutorials[str(self.level)]
        return "\n".join([
            f"=== SP's T-Shirt Business ===",
            f"Welcome to {t['title']}",
            t.get("subtitle", ""),
            "Learn the assertions, pass a short practice round (mistakes there are free), and start recording in your books.",
            "[button: Start Tutorial]   [button: Think you already know this? Skip to the practice round]",
            "",
            'Actions available: {"type":"start_tutorial"} | {"type":"test_out"}'])

    def render_reading(self):
        t = self.tutorials[str(self.level)]
        secs = self._sections()
        i = self.reading["index"]
        s = secs[i]
        last = i + 1 == len(secs)
        out = [f"=== {t['title']} — {t.get('subtitle', '')} ===",
               f"Section {i+1} of {len(secs)}: {s['heading']}", "", s["content"], "",
               "Sections: " + " | ".join(f"{j+1}. {x['heading']}" for j, x in enumerate(secs))]
        acts = []
        if i > 0:
            acts.append('{"type":"prev_section"}')
        if not last:
            acts.append('{"type":"next_section"}')
        acts.append('{"type":"go_to_section","index":<n>} (0-based)')
        if self.reading["review_only"]:
            acts.append('{"type":"back_to_drill"}')
        elif last:
            acts.append('{"type":"take_quiz"}  [button: Take the Quiz]')
        out.append("Actions available: " + " | ".join(acts))
        return "\n".join(out)

    def render_quiz(self):
        t = self.tutorials[str(self.level)]
        out = [f"=== {t['title']} — Quiz{' (retry: the questions you missed)' if self.quiz['retry'] else ''} ===",
               "Answer every question, then submit."]
        for q in self.quiz["questions"]:
            out.append(f"\nQuestion {q['id']}: {q['question']}")
            for j, ch in enumerate(q["choices"]):
                mark = " (your answer)" if self.quiz["answers"].get(q["id"]) == j else ""
                out.append(f"   {j}. {ch}{mark}")
        out.append("")
        out.append('Actions available: {"type":"answer","question_id":"<id>","choice":<n>} | {"type":"submit_quiz"} (once all are answered)')
        return "\n".join(out)

    def render_quiz_results(self):
        r = self.quiz["results"]
        missed = self.quiz["missed"]
        total, correct = len(r), sum(x["correct"] for x in r)
        if not missed:
            return "\n".join(["=== Tutorial Complete! ===",
                              f"You answered all {total} questions correctly.",
                              "[button: Continue to Practice]", "",
                              'Actions available: {"type":"continue"}'])
        out = ["=== Review Your Answers ===",
               f"You got {correct} of {total} correct. Review the missed questions below:"]
        for x in r:
            if not x["correct"]:
                out += [f"\n{x['question']}", f"  Your answer: {x['user_answer']}",
                        f"  Correct answer: {x['correct_answer']}", f"  {x['explanation']}"]
        out += ["", "[button: Try Again]", 'Actions available: {"type":"try_again"}']
        return "\n".join(out)

    def render_drill(self):
        d = self.drill
        p = self.problem or {}
        streak_note = f" · {d['streak']} in a row" if d["streak_pass"] and d["streak"] >= 2 else ""
        out = [f"=== Practice round {d['round']} ===",
               f"Other people's businesses, not SP's: each problem is a different company with its own books, and nothing carries over between problems. Mistakes here are free. Get {d['pass_count']} of {d['round_size']} right — or {d['streak_pass']} in a row — to start recording.",
               f"This round: {d['correct']} correct of {d['attempted']} attempted{streak_note}   [button: Review Tutorial]",
               "", "--- Transaction ---", p.get("narrative", ""), "",
               "--- Your sentence (the sentence builder) ---", self._sentence()]
        acts = []
        if d.get("worked_example"):
            out += ["", "=== Worked example ===",
                    "The sentence builder now shows the full set of assertions for this transaction, and below is the journal entry they produce — click a line to see the rule behind it. This problem won't count toward your round, and neither does looking.",
                    self._derived_text(), "", "[button: Try a fresh problem]"]
            acts = ['{"type":"fresh_problem"}', '{"type":"open_line","index":<n>}']
        elif self.feedback is None:
            out += [self._palette_text(), "", "--- Feedback ---",
                    f"Assertions selected: {len(self.selected)}" if self.selected else "",
                    "[button: Submit Answer]  [button: Clear]  [button: Show me a worked example]"]
            acts = self._builder_actions() + ['{"type":"submit"}', '{"type":"clear"}',
                                              '{"type":"worked_example"}', '{"type":"review_tutorial"}']
        else:
            out += ["", "--- Feedback ---"] + self._feedback_text()
            out.append(self._derived_text())
            if d["miss_streak"] >= 2:
                worst = max(d["miss_assertions"].items(), key=lambda kv: kv[1])[0] if d["miss_assertions"] else None
                if worst:
                    out.append(f"That's {d['miss_streak']} in a row — and it keeps coming down to {worst}. Mistakes here are free; a quick re-read might help.  [button: Re-read that section]")
                else:
                    out.append(f"That's {d['miss_streak']} in a row. Mistakes here are free; a quick re-read might help.  [button: Re-read that section]")
                acts.append('{"type":"review_tutorial"}')
            passed, streak_passed, unreachable = self._drill_status()
            if passed:
                lbl = f"{d['streak_pass']} in a row — start recording Year 1 →" if streak_passed and d["correct"] < d["pass_count"] else "You're ready — start recording Year 1 →"
                out.append(f"[button: {lbl}]")
                acts.append('{"type":"finish_drill"}')
            elif unreachable:
                if d["entry_path"] == "test-out":
                    out.append(f"[button: Not quite ({d['correct']} of {d['attempted']}) — read the tutorial]   [button: Try another round instead]")
                    acts += ['{"type":"read_tutorial"}', '{"type":"fresh_round"}']
                else:
                    out.append(f"[button: Not quite ({d['correct']} of {d['attempted']}) — try a fresh round]")
                    acts.append('{"type":"fresh_round"}')
            else:
                out.append("[button: Next Practice Problem]")
                acts.append('{"type":"next_problem"}')
            acts += ['{"type":"open_line","index":<n>}',
                     '{"type":"explore"} then {"type":"toggle_assertion","code":"<code>"}']
        out.append("")
        out.append("Actions available: " + " | ".join(a for a in acts if a))
        return "\n".join(out)

    def _feedback_text(self):
        fb = self.feedback
        status = fb.get("status")
        head = {"correct": "✓ Correct!", "incorrect": "✗ Incorrect", "incomplete": "⚠ Incomplete",
                "indeterminate": "Cannot classify"}.get(status, f"Status: {status}")
        out = [head]
        if fb.get("message") and status != "correct":
            out.append(fb["message"])
        cls = fb.get("classification") or {}
        link = fb.get("assertion-linkages") or {}
        incorrect = status == "incorrect"

        def entry_lines(entries, with_link):
            res = []
            for e in entries or []:
                dl = _linkage_for(link, e.get("debit"), "debit") if with_link else None
                cl = _linkage_for(link, e.get("credit"), "credit") if with_link else None
                res.append(f"  DR: {e.get('debit')} {money(e.get('amount')) if e.get('amount') is not None else ''}{'  ← ' + dl if dl else ''}")
                res.append(f"  CR: {e.get('credit')} {money(e.get('amount')) if e.get('amount') is not None else ''}{'  ← ' + cl if cl else ''}")
            return res
        if cls.get("journal-entry"):
            out.append("Your assertions would produce this (incorrect) entry:" if incorrect else "Journal Entry:")
            out += entry_lines(cls["journal-entry"], True)
        cc = fb.get("correct-classification") or {}
        if incorrect and cc.get("journal-entry"):
            out.append("The correct entry should be:")
            out += entry_lines(cc["journal-entry"], False)
        if cls.get("note"):
            out.append(f"Note: {cls['note']}")
        if fb.get("hints"):
            out.append("Why was this wrong?")
            out += [f"  - {h}" for h in fb["hints"]]
        return out

    def render_done(self):
        return "=== The session is over. ==="


class ActionError(Exception):
    pass


def _num(v):
    try:
        f = float(v)
        return int(f) if f == int(f) else f
    except (TypeError, ValueError):
        raise ActionError(f"'{v}' is not a number.")


def _kv(p):
    if isinstance(p, list):
        return "; ".join(_kv(x) for x in p)
    return ", ".join(f"{k} {v}" for k, v in (p or {}).items())


def _linkage_for(linkages, account, effect):
    for code, l in (linkages or {}).items():
        if l.get("account") == account and l.get("effect") == effect:
            return code
    return None
