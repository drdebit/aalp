#!/usr/bin/env bash
# Run a cohort of headless learners through learner-lab, then report.
#   ./run_cohort.sh <cohort> "<id>:<profile> ..." [extra learner-lab run args]
# e.g. ./run_cohort.sh c7 "s71:novice-business-undergrad s72:traditional-intro-accounting s73:hasty-sophomore" --max-turns 260
set -u
cd "$(dirname "$0")"
LAB=~/.claude/skills/learner-lab/learnerlab/cli.py
cohort=$1; shift
spec=$1; shift
mkdir -p runs/logs
pids=(); ids=()
for s in $spec; do
  IFS=: read -r id profile <<<"$s"
  echo "starting $id ($profile)"
  python3 "$LAB" run --adapter adapter.py:Adapter --adapter-args "{\"learner_id\":\"$id\"}" \
    --profile "profiles/$profile.yaml" --items items.yaml --id "$id" --out runs "$@" > "runs/logs/$id.log" 2>&1 &
  pids+=($!); ids+=("$id")
  sleep 5
done
for p in "${pids[@]}"; do wait "$p"; done
python3 "$LAB" cohort --cohort "$cohort" --runs runs --items items.yaml --ids "${ids[@]}"
echo "cohort $cohort finished: runs/report-$cohort.md"
