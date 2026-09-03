#!/usr/bin/env bash
# Run a cohort of headless students in parallel, then build the report.
#   ./run_cohort.sh <cohort-name> "<id>:<persona>:<model> ..." [extra student.py args]
# e.g. ./run_cohort.sh c1 "s01:novice:haiku s02:trad:haiku" --max-turns 240
set -u
cd "$(dirname "$0")"
cohort=$1; shift
spec=$1; shift
mkdir -p runs/logs
pids=()
for s in $spec; do
  IFS=: read -r id persona model <<<"$s"
  echo "starting $id ($persona, $model)"
  python3 student.py --id "$id" --persona "$persona" --model "$model" "$@" > "runs/logs/$id.log" 2>&1 &
  pids+=($!)
  sleep 5
done
for p in "${pids[@]}"; do wait "$p"; done
python3 report.py --cohort "$cohort" $(for s in $spec; do echo "${s%%:*}"; done)
echo "cohort $cohort finished: runs/report-$cohort.md"
