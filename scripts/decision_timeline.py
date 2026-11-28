#!/usr/bin/env python3
"""Readable timeline of an AltoClef decision log or flight recorder dump.

The decision log is written to <minecraft>/altoclef/logs/<session>.jsonl (one JSON object per line),
dumps to <session>-t<tick>-<trigger>.json. See docs/task-architecture.md, section 2.6.

Examples:
  scripts/decision_timeline.py versions/1.21.1/run/altoclef/logs/2026-11-28_09-30-12.jsonl
  scripts/decision_timeline.py LOG --type switch,death        only chain switches and deaths
  scripts/decision_timeline.py LOG --no-tasks                 everything except task start/stop
  scripts/decision_timeline.py LOG --from 4000 --to 4600      tick range
  scripts/decision_timeline.py LOG --grep "Mob Defense"       lines containing a text
  scripts/decision_timeline.py LOG --summary                  only the summaries
  scripts/decision_timeline.py DUMP.json                      dump: one line per tick (chain, path, hp)
"""
import argparse
import json
import sys

TASK_TYPES = {"task_start", "task_stop", "task_interrupt"}


def fmt_time(ms):
    if ms is None:
        return "     -  "
    s = int(ms) // 1000
    return f"{s // 3600:2d}:{s // 60 % 60:02d}:{s % 60:02d}"


def short_priorities(prios):
    if not prios:
        return ""
    parts = [f"{name}={value:g}" for name, value in prios.items() if value is not None]
    return " [" + ", ".join(parts) + "]"


def describe(event, verbose):
    t = event.get("type", "?")
    msg = event.get("msg", "")
    extra = ""
    if t == "switch":
        extra = f" ({event.get('reason')})" + short_priorities(event.get("priorities"))
        if verbose and event.get("path"):
            extra += "\n" + " " * 30 + " > ".join(event["path"])
    elif t == "summary":
        per_chain = ", ".join(f"{k} {v:g}s" for k, v in (event.get("secondsPerChain") or {}).items())
        extra = f" {event.get('seconds', 0):g}s, switches {event.get('switches')}, deaths {len(event.get('deaths') or [])}"
        if per_chain:
            extra += f" | {per_chain}"
    elif verbose and t not in TASK_TYPES:
        hidden = {"type", "msg", "tick", "t", "pos", "dim", "hp", "food"}
        rest = {k: v for k, v in event.items() if k not in hidden}
        if rest:
            extra = " " + json.dumps(rest, ensure_ascii=False)
    return f"{t:<16} {msg}{extra}"


def status(event):
    pos = event.get("pos")
    where = f"{event.get('dim', '')[:3]:<3} {pos[0]:>7.0f} {pos[1]:>4.0f} {pos[2]:>7.0f}" if pos else " " * 25
    hp = event.get("hp")
    food = event.get("food")
    vitals = f"hp {hp:>4g} food {food:>2}" if hp is not None else " " * 15
    return f"{where}  {vitals}"


def read_log(path):
    with open(path, encoding="utf-8") as f:
        for number, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            try:
                yield json.loads(line)
            except json.JSONDecodeError as e:
                print(f"{path}:{number}: not JSON ({e})", file=sys.stderr)


def show_log(args):
    types = set(args.type.split(",")) if args.type else None
    for event in read_log(args.file):
        t = event.get("type")
        tick = event.get("tick", 0)
        if types and t not in types:
            continue
        if args.summary and t != "summary":
            continue
        if args.no_tasks and t in TASK_TYPES:
            continue
        if args.start is not None and tick < args.start:
            continue
        if args.end is not None and tick > args.end:
            continue
        text = describe(event, args.verbose)
        if args.grep and args.grep.lower() not in json.dumps(event, ensure_ascii=False).lower():
            continue
        print(f"{fmt_time(event.get('t'))} {tick:>7}  {status(event)}  {text}")


def show_dump(args, dump):
    print(f"Dump: {dump.get('trigger')} - {dump.get('detail')} (session {dump.get('session')}, tick {dump.get('tick')})")
    summary = dump.get("summary") or {}
    print(f"Run: {summary.get('run')}, {summary.get('seconds', 0):g}s, switches {summary.get('switches')}, deaths {summary.get('deaths')}")
    previous = None
    for snap in dump.get("snapshots", []):
        tick = snap.get("tick", 0)
        if args.start is not None and tick < args.start:
            continue
        if args.end is not None and tick > args.end:
            continue
        line = f"{snap.get('chain')}: " + " > ".join(snap.get("path") or [])
        if args.verbose:
            line += short_priorities(snap.get("priorities"))
        # Only print a tick when chain or task path changed (unless --verbose).
        if line == previous and not args.verbose:
            continue
        previous = line
        if args.grep and args.grep.lower() not in json.dumps(snap, ensure_ascii=False).lower():
            continue
        print(f"{fmt_time(snap.get('t'))} {tick:>7}  {status(snap)}  {line}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("file", help="decision log (.jsonl) or dump (.json)")
    parser.add_argument("--type", help="comma separated event types (switch, task_start, death, command, ...)")
    parser.add_argument("--no-tasks", action="store_true", help="hide task start/stop/interrupt")
    parser.add_argument("--summary", action="store_true", help="only summary lines")
    parser.add_argument("--from", dest="start", type=int, help="first tick")
    parser.add_argument("--to", dest="end", type=int, help="last tick")
    parser.add_argument("--grep", help="only events containing this text (case-insensitive)")
    parser.add_argument("-v", "--verbose", action="store_true", help="show task paths, all priorities and fields")
    args = parser.parse_args()

    if args.file.endswith(".json"):
        with open(args.file, encoding="utf-8") as f:
            show_dump(args, json.load(f))
    else:
        show_log(args)


if __name__ == "__main__":
    try:
        main()
    except BrokenPipeError:
        pass
