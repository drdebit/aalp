"""Pre/post assessment items, rubrics, and the grader/gap-analysis prompts.

Items tagged `pre` are asked before the student touches the platform and
again after; the rest presuppose the platform experience and are asked
only afterwards. Each is free-response, graded 0-2 against a rubric by
a separate grader model that never sees the student's persona."""

ITEMS = [
    # ---- double-entry ----
    {"id": "de1", "tag": "de:debit-credit", "pre": True,
     "q": "In your own words: what is a journal entry, and what do 'debit' and 'credit' mean?",
     "rubric": "1 pt: a journal entry records one business event as at least one debit line and one credit line whose amounts balance. 1 pt: debit/credit are the two sides (left/right) — assets increase with debits and decrease with credits; equity, liabilities and revenue increase with credits. Zero if debit is described as 'money out' / credit as 'money in' in the bank-statement sense with nothing else right."},
    {"id": "de2", "tag": "de:cash-purchase", "pre": True,
     "q": "SP's business buys a t-shirt printer for $3,000 cash. Write the journal entry: which account is debited, which is credited, and the amounts.",
     "rubric": "1 pt: debit Equipment (or a clearly equivalent asset account) $3,000. 1 pt: credit Cash $3,000. Reversed sides score 0 on both."},
    {"id": "de3", "tag": "de:credit-purchase", "pre": True,
     "q": "SP's business buys $500 of blank t-shirts on account — it will pay the supplier in 30 days. Write the journal entry.",
     "rubric": "1 pt: debit Raw Materials Inventory (or Inventory / materials) $500. 1 pt: credit Accounts Payable (or a clearly named liability to the supplier) $500 — NOT Cash."},
    {"id": "de4", "tag": "de:sale-cogs", "pre": True,
     "q": "SP's business sells 5 printed shirts to a customer for $125 cash. Those 5 shirts cost $30 in total to make. Write every line of the journal entry (or entries).",
     "rubric": "1 pt: debit Cash $125 and credit (Sales) Revenue $125. 1 pt: debit Cost of Goods Sold $30 and credit Finished Goods Inventory $30. Half credit is not given; each pair scores as a unit."},
    {"id": "de5", "tag": "de:why-balance", "pre": True,
     "q": "Why does a journal entry always balance? And if a business simply receives $20,000 from its owner, what is on the other side of the entry, and why?",
     "rubric": "1 pt: it balances because every event has two sides — what came in and what went out, or what claim was created; the two sides are the same event measured in money. 1 pt: the other side is Owner's Capital / equity, because nothing went out and no debt was created, so what remains is the owner's claim (the residual)."},
    # ---- assertive accounting, general/transfer (asked pre and post) ----
    {"id": "aa8", "tag": "aa:deferred-revenue", "pre": True,
     "q": "SP's business receives $2,000 today from a customer for custom shirts it will deliver next month. Describe how you would record this — what statements (assertions) you'd make about the event — and what the journal entry would be.",
     "rubric": "1 pt: the business receives money now AND takes on an obligation to provide shirts later (some form of 'requires'/'must provide'/'owes delivery'). 1 pt: entry is debit Cash $2,000, credit a liability such as Deferred/Unearned Revenue $2,000 — explicitly NOT revenue yet."},
    {"id": "aa9", "tag": "aa:novel-transfer", "pre": True,
     "q": "A new situation you have not seen: the business's accountant prepares its tax return and sends a bill for $300, due next month. How would you record it — what statements would you make about the event — and what do you think the journal entry would be? Say what you are unsure about.",
     "rubric": "1 pt: the business receives a service (work done for it, used up as done) from a counterparty and takes on an obligation to provide $300 money later (requires / must pay) — no cash moves today. 1 pt: entry is debit an expense (Services / Accounting / Professional fees Expense) $300 and credit Accounts Payable (or a clearly named liability) $300 — NOT an asset and NOT Cash; a stated uncertainty (whether preparing a return leaves anything lasting) is acceptable but the expense conclusion should be reached."},
    # ---- assertive accounting, platform-specific (post only) ----
    {"id": "aa1", "tag": "aa:assertion-concept", "pre": False,
     "q": "What is an 'assertion' in the system you just used? Name the assertions you used and say in a few words what each one says about an event.",
     "rubric": "1 pt: an assertion is a plain statement about what happened in an event (who, when, what went out, what came in, what it enables), from which the journal entry is derived. 1 pt: names at least four of has-date, receives, provides, has-counterparty, allows, consumes, creates, is-allowed-by with correct meanings."},
    {"id": "aa2", "tag": "aa:equity-residual", "pre": False,
     "q": "When the business was funded you said only that it received $20,000 (plus when and from whom). Owner's Capital appeared on the credit side by itself. Why that account — and not Revenue, or a loan?",
     "rubric": "1 pt: money came in and nothing went out and no obligation to repay was created, so what is left is the contributor's claim — equity is the residual. 1 pt: revenue would need goods or services provided to a counterparty; a loan would need an obligation (requires) to repay."},
    {"id": "aa3", "tag": "aa:allows-role", "pre": False,
     "q": "The same printer could be Equipment for SP and Inventory for a shop that resells printers. Which assertion told the record which one it was, and what happened to the entry when you switched that assertion off?",
     "rubric": "1 pt: the 'allows' assertion — saying what the printer is for (turns blank shirts into printed ones). 1 pt: with it off, the Equipment line disappeared / the debit became unclassified because the record no longer said what the machine was for."},
    {"id": "aa4", "tag": "aa:inherited-classification", "pre": False,
     "q": "When you bought blank t-shirts you never said they were 'raw materials', yet the entry said Raw Materials Inventory. How did the record know?",
     "rubric": "1 pt: because of what was said earlier about the printer — the 'allows' assertion said the printer turns blank t-shirts into printed ones, so blank shirts are an input ('decided earlier'). 1 pt: the general point: the account is read off the chain of prior assertions rather than asserted for the item; the same four assertions produced a new account."},
    {"id": "aa5", "tag": "aa:recorded-not-reflected", "pre": False,
     "q": "What does 'Recorded — but not reflected' mean? Give an example from what you did and explain why the journal entry cannot show it.",
     "rubric": "1 pt: things that are in the record but produce no journal-entry line. 1 pt: a correct example (the 200 ownership units; or is-allowed-by / allows) and the reason: double-entry measures in money (the monetary unit assumption) so a count or a permission has no line, though it stays in the record."},
    {"id": "aa6", "tag": "aa:cost-flow", "pre": False,
     "q": "On the production entry, an amount appeared for the shirts used up without you typing it. Where did it come from? And when shirts were later sold, where did Cost of Goods Sold get its amount?",
     "rubric": "1 pt: from what the business paid for those shirts when it bought them (the earlier purchase event / price per shirt). 1 pt: COGS was priced from the production event — the finished shirts carry the cost of what went into them, so the record worked out the cost of the ones sold from the day they were printed."},
    {"id": "aa7", "tag": "aa:production-no-counterparty", "pre": False,
     "q": "Why is there no counterparty on the production entry? What is different about that event compared with a purchase or a sale?",
     "rubric": "1 pt: nothing entered or left the business — nobody else was involved; something inside it changed form (consumes -> creates). 1 pt: purchases and sales are exchanges with another party (provides/receives with a counterparty), production is an internal transformation enabled by the printer."},
]

META_ITEMS = [
    {"id": "m1", "q": "What was the most confusing part of the platform for you, and what would you change about it?"},
    {"id": "m2", "q": "On a 1-5 scale, how well do you feel you now understand (a) journal entries and (b) assertions? Give both numbers and one sentence on each."},
]

ANSWER_SCHEMA = {"type": "object",
                 "properties": {"answer": {"type": "string"},
                                "confidence": {"type": "integer", "minimum": 1, "maximum": 5}},
                 "required": ["answer", "confidence"]}

GRADE_SCHEMA = {"type": "object",
                "properties": {"score": {"type": "integer", "minimum": 0, "maximum": 2},
                               "rationale": {"type": "string"},
                               "misconceptions": {"type": "array", "items": {"type": "string"}}},
                "required": ["score", "rationale", "misconceptions"]}

GAP_SCHEMA = {"type": "object",
              "properties": {
                  "confusions": {"type": "array", "items": {"type": "object", "properties": {
                      "turn": {"type": "integer"}, "location": {"type": "string"},
                      "what_confused_them": {"type": "string"},
                      "platform_cause": {"type": "string"},
                      "resolved": {"type": "boolean"},
                      "suggested_revision": {"type": "string"}},
                      "required": ["turn", "location", "what_confused_them", "platform_cause", "resolved", "suggested_revision"]}},
                  "learning_moments": {"type": "array", "items": {"type": "object", "properties": {
                      "turn": {"type": "integer"}, "location": {"type": "string"}, "what_clicked": {"type": "string"}},
                      "required": ["turn", "location", "what_clicked"]}},
                  "possible_bugs": {"type": "array", "items": {"type": "string"}},
                  "overall": {"type": "string"}},
              "required": ["confusions", "learning_moments", "possible_bugs", "overall"]}

GRADER_SYSTEM = ("You grade short free-response answers from accounting students against a rubric. "
                 "Be strict but fair: award a point only when the rubric's idea is actually present, in any wording. "
                 "Do not reward length. Name concrete misconceptions when you see them. Output JSON only.")

GAP_SYSTEM = ("You are an education researcher analysing a think-aloud transcript of a student using an "
              "accounting learning platform. The platform teaches 'assertive accounting': students describe "
              "events with assertions (has-date, receives, provides, has-counterparty, allows, consumes, creates, "
              "is-allowed-by, requires, expects) and the journal entry is derived from them. Find every moment "
              "the student was confused, stuck, misled, or formed a wrong belief; say what on the platform caused "
              "it (copy, feedback, missing explanation, UI affordance, or a plausible bug) and whether it was "
              "later resolved. Only report confusion the student's OWN WORDS or ACTIONS show (a wrong belief stated, a wrong "
              "action, a stated 'I don't get', repeated failed attempts) — never infer confusion from what the platform "
              "did not say, and do not pad the list; a clean transcript may have zero confusions. Also list moments where something clearly clicked, and anything that looks like a "
              "software defect (an answer key rejecting a correct answer, contradictory feedback, a warning that "
              "made no sense). Quote the student briefly where useful. Output JSON only.")


def grade_prompt(item, answer):
    return (f"QUESTION: {item['q']}\n\nRUBRIC (0-2 points): {item['rubric']}\n\n"
            f"STUDENT ANSWER:\n{answer}\n\nGrade it.")
