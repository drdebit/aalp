"""A sealed model session driven through `claude -p`.

The student must see nothing but the screen text the harness hands it:
no tools, a replaced system prompt, and a working directory outside the
repo so no CLAUDE.md about the platform is loaded. Multi-turn memory
comes from --session-id / --resume, so a student's confusion, learning
and post-test all live in one conversation."""
import json
import os
import subprocess
import time
import uuid

CWD = os.path.expanduser("~/.cache/aalp-study/cwd")


class Session:
    def __init__(self, model, system_prompt, name="session", log_path=None, max_retries=3):
        self.model = model
        self.system_prompt = system_prompt
        self.name = name
        self.sid = str(uuid.uuid4())
        self.started = False
        self.log_path = log_path
        self.max_retries = max_retries
        self.total_cost = 0.0
        self.calls = 0
        os.makedirs(CWD, exist_ok=True)

    def ask(self, message, schema=None, timeout=300):
        cmd = ["claude", "-p", "--model", self.model, "--tools", "",
               "--output-format", "json", "--system-prompt", self.system_prompt]
        cmd += ["--resume", self.sid] if self.started else ["--session-id", self.sid]
        if schema is not None:
            cmd += ["--json-schema", json.dumps(schema)]
        last_err = None
        for attempt in range(self.max_retries):
            t0 = time.time()
            try:
                p = subprocess.run(cmd, input=message, capture_output=True, text=True,
                                   cwd=CWD, timeout=timeout)
            except subprocess.TimeoutExpired:
                last_err = "timeout"
                continue
            secs = time.time() - t0
            try:
                out = json.loads(p.stdout)
            except json.JSONDecodeError:
                last_err = f"non-json stdout: {p.stdout[:200]!r} stderr: {p.stderr[:300]!r}"
                time.sleep(2 * (attempt + 1))
                continue
            if out.get("is_error"):
                last_err = f"error result: {str(out.get('result'))[:300]}"
                time.sleep(2 * (attempt + 1))
                continue
            self.started = True
            self.calls += 1
            self.total_cost += float(out.get("total_cost_usd") or 0)
            result = out.get("structured_output") if schema is not None else out.get("result")
            if schema is not None and result is None:
                # Fall back to parsing the text result as JSON.
                try:
                    result = json.loads(out.get("result") or "")
                except Exception:
                    last_err = f"no structured output: {str(out.get('result'))[:300]}"
                    continue
            if self.log_path:
                with open(self.log_path, "a") as f:
                    f.write(json.dumps({"name": self.name, "secs": round(secs, 1),
                                        "cost": out.get("total_cost_usd"),
                                        "message": message, "result": result}) + "\n")
            return result, secs
        raise RuntimeError(f"{self.name}: model call failed after retries: {last_err}")
