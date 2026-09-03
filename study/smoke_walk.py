"""Scripted end-to-end check of the text-mode client: a perfect student
walks all five episodes, then answers one drill problem wrongly and one
correctly. Prints the screens at the interesting points."""
import json, sys
from aalp_client import AalpClient
from aalp_platform import Platform

c = AalpClient(); c.login("smoke-walk@study.test")
content = json.load(open("content/content.json"))
p = Platform(c, content, ["walkthrough", "tutorial:0"])
script = {
 0: [{"type":"next"},{"type":"next"},
     {"type":"add_assertion","code":"has-date","params":{"date":"2026-01-01"}},{"type":"next"},
     {"type":"add_assertion","code":"receives","params":{"unit":"monetary-unit","quantity":20000}},{"type":"next"},
     {"type":"add_assertion","code":"provides","params":{"unit":"ownership-units","quantity":200}},{"type":"next"},
     {"type":"next"},
     {"type":"add_assertion","code":"has-counterparty","params":{"name":"SP"}},{"type":"next"}],
 1: [{"type":"add_assertion","code":"has-date","params":{"date":"2026-01-02"}},{"type":"next"},
     {"type":"add_assertion","code":"provides","params":{"unit":"monetary-unit","quantity":3000}},{"type":"next"},
     {"type":"add_assertion","code":"receives","params":{"unit":"physical-unit","physical-item":"t-shirt-printer","quantity":1}},{"type":"next"},
     {"type":"next"},
     {"type":"add_assertion","code":"allows","params":{"consumes-item":"blank-tshirts","creates-item":"printed-tshirts"}},{"type":"next"},
     {"type":"add_assertion","code":"has-counterparty","params":{"name":"PrinterWorld"}},{"type":"next"},
     {"type":"explore"},{"type":"toggle_assertion","code":"allows"},{"type":"toggle_assertion","code":"allows"},{"type":"explore"},{"type":"next"},
     {"type":"next"}],
 2: [{"type":"add_assertion","code":"has-date","params":{"date":"2026-01-03"}},{"type":"next"},
     {"type":"add_assertion","code":"provides","params":{"unit":"monetary-unit","quantity":100}},{"type":"next"},
     {"type":"add_assertion","code":"receives","params":{"unit":"physical-unit","physical-item":"blank-tshirts","quantity":20}},{"type":"next"},
     {"type":"open_line","index":0},{"type":"next"},
     {"type":"add_assertion","code":"has-counterparty","params":{"name":"TextileDirect"}},{"type":"next"}],
 3: [{"type":"add_assertion","code":"has-date","params":{"date":"2026-01-03"}},{"type":"next"},
     {"type":"add_assertion","code":"provides","params":{"unit":"monetary-unit","quantity":20}},{"type":"next"},
     {"type":"add_assertion","code":"receives","params":{"unit":"physical-unit","physical-item":"ink-cartridges","quantity":2}},{"type":"next"},
     {"type":"add_assertion","code":"has-counterparty","params":{"name":"InkMasters"}},{"type":"next"}],
 4: [{"type":"add_assertion","code":"has-date","params":{"date":"2026-01-04"}},{"type":"next"},
     {"type":"add_assertion","code":"consumes","params":{"flows":[{"quantity":10,"physical-item":"blank-tshirts"},{"quantity":1,"physical-item":"ink-cartridges"}]}},{"type":"next"},
     {"type":"add_assertion","code":"creates","params":{"flows":[{"quantity":10,"physical-item":"printed-tshirts"}]}},{"type":"next"},
     {"type":"add_assertion","code":"is-allowed-by","params":{"capacity":"t-shirt-printer"}},{"type":"next"}],
 5: [{"type":"add_assertion","code":"has-date","params":{"date":"2026-01-05"}},{"type":"next"},
     {"type":"add_assertion","code":"provides","params":{"unit":"physical-unit","physical-item":"printed-tshirts","quantity":4}},{"type":"next"},
     {"type":"add_assertion","code":"receives","params":{"unit":"monetary-unit","quantity":100}},{"type":"next"},
     {"type":"add_assertion","code":"has-counterparty","params":{"name":"Customer"}},{"type":"next"},
     {"type":"open_line","index":2},{"type":"next"}],
}
show = {(0,4),(1,7),(2,3),(4,3),(5,4)}
while p.phase == "walkthrough":
    ep = p.wt["episode"]
    for a in script[ep]:
        if (ep, p.wt["step"] if p.wt else -1) in show and a["type"] in ("open_line","explore","toggle_assertion") or (ep,p.wt["step"] if p.wt else -1) in show and a["type"]=="next":
            print(p.render()); print("......")
        r = p.apply(a)
        if r not in ("OK.","Next step.","New episode.") and not r.startswith(("Added","Updated","Opened","Closed","Explor","allows switched","Walkthrough")):
            print("!!", a, "->", r)
        if p.phase != "walkthrough": break
print("PHASE NOW", p.phase, p.summary["walkthrough"])
print("=========== GATE"); print(p.render())
print(p.apply({"type":"test_out"}))
print("=========== DRILL"); print(p.render())
prob = p.problem
wrong = {"type":"add_assertion","code":"receives","params":{"unit":"monetary-unit","quantity":prob["variables"].get("amount",1)}}
print(p.apply(wrong)); print(p.apply({"type":"submit"}))
print("=========== FEEDBACK (wrong)"); print(p.render())
print(p.apply({"type":"open_line","index":0})); print(p.render().split("--- What your assertions produce ---")[1][:900])
print(p.apply({"type":"next_problem"}))
prob = p.problem
for code, params in prob["correct-assertions"].items():
    print(p.apply({"type":"add_assertion","code":code,"params":params}))
print(p.apply({"type":"submit"}))
print("=========== FEEDBACK (right)"); print("\n".join(p.render().split("--- Feedback ---")[1].splitlines()[:25]))
print("drill:", {k:v for k,v in p.drill.items() if k!="history"})
