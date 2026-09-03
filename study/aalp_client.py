"""Thin client for the AALP backend. Mirrors exactly the requests the real
frontend (api.cljs) makes, so the text-mode student exercises the same
server paths a browser student does."""
import json
import urllib.request
import urllib.error

DEFAULT_BASE = "http://choochoo.dyn.gsu.edu:3000/api"


class AalpClient:
    def __init__(self, base=DEFAULT_BASE, timeout=30):
        self.base = base.rstrip("/")
        self.timeout = timeout
        self.token = None
        self.user = None

    def _req(self, method, path, body=None, params=None):
        url = self.base + path
        if params:
            url += "?" + "&".join(f"{k}={v}" for k, v in params.items())
        data = json.dumps(body).encode() if body is not None else None
        headers = {"content-type": "application/json", "accept": "application/json"}
        if self.token:
            headers["x-session-token"] = self.token
        req = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as r:
                raw = r.read().decode()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as e:
            raw = e.read().decode(errors="replace")
            raise RuntimeError(f"{method} {path} -> {e.code}: {raw[:300]}") from None

    # ---- auth / progress ----
    def login(self, email):
        r = self._req("POST", "/login", {"email": email})
        self.token = r["session-token"]
        self.user = r
        return r

    def progress(self):
        return self._req("GET", "/progress")

    def guided_state(self):
        return self._req("GET", "/guided/state")

    def reset_simulation(self):
        return self._req("POST", "/simulation/reset", {})

    # ---- vocabulary / problems ----
    def assertions(self, level):
        return self._req("GET", "/assertions", params={"level": level})["assertions"]

    def generate_problem(self, level, problem_type="forward"):
        return self._req("POST", "/generate-problem",
                         {"level": level, "problem-type": problem_type})

    def classify(self, payload):
        return self._req("POST", "/classify", payload)

    def derive_je(self, selected, variables, prior_events):
        return self._req("POST", "/derive-je",
                         {"selected-assertions": selected,
                          "variables": variables or {},
                          "prior-events": prior_events or []})

    def worked_example_viewed(self, payload):
        return self._req("POST", "/worked-example-viewed", payload)

    def complete_tutorial(self, level):
        return self._req("POST", "/tutorial/complete", {"level": level})
