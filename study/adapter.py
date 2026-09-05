"""learner-lab adapter for AALP: wraps the text-mode client."""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from aalp_client import AalpClient  # noqa: E402
from aalp_platform import Platform  # noqa: E402

ACTION_SCHEMA = {"type": "object",
                 "properties": {"type": {"type": "string"}, "code": {"type": "string"}, "params": {"type": "object"},
                                "index": {"type": "integer"}, "question_id": {"type": "string"}, "choice": {"type": "integer"},
                                "reason": {"type": "string"}},
                 "required": ["type"]}


class Adapter(Platform):
    action_schema = ACTION_SCHEMA
    action_help = ("Pick exactly one action from the screen's \"Actions available\" list, in the JSON shape shown "
                   "(dates as YYYY-MM-DD, numbers as numbers, dropdown values exactly as listed).")

    def __init__(self, stages=("walkthrough", "tutorial:0", "tutorial:1"), email=None, base=None, event_log=None, learner_id=None):
        client = AalpClient(base) if base else AalpClient()
        client.login(email or f"{learner_id or os.path.basename(os.path.dirname(event_log or 'x/y'))}@study.test")
        try:
            client.reset_simulation()
        except Exception:
            pass
        content = json.load(open(os.path.join(HERE, "content", "content.json")))
        super().__init__(client, content, list(stages), event_log=event_log)
