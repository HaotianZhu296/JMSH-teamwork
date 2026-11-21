#!/usr/bin/env python3
"""Per-profile statistics of measures_v2.csv, printed as a Markdown table.

Usage: python3 scripts/dataset_profile_stats.py [measures_v2.csv] [config/profile_split.csv]

Uses only the Python standard library. Rows are reduced to 1 Hz the same way the simulator does it
(keep every second row of a profile, starting with the first), so "seconds" are simulated seconds.
"""
import csv
import sys

THRESHOLDS = (80.0, 95.0, 100.0, 120.0)


def main():
    data_file = sys.argv[1] if len(sys.argv) > 1 else "data/measures_v2.csv"
    split_file = sys.argv[2] if len(sys.argv) > 2 else "config/profile_split.csv"

    with open(split_file, newline="") as f:
        split = {int(r["profile_id"]): r["split"] for r in csv.DictReader(f)}

    stats = {}
    position = {}
    with open(data_file, newline="") as f:
        for row in csv.DictReader(f):
            profile = int(float(row["profile_id"]))
            index = position.get(profile, 0)
            position[profile] = index + 1
            if index % 2:
                continue
            s = stats.setdefault(profile, {"n": 0, "max": float("-inf"), "over": [0] * len(THRESHOLDS)})
            temp = float(row["stator_winding"])
            s["n"] += 1
            s["max"] = max(s["max"], temp)
            for i, limit in enumerate(THRESHOLDS):
                if temp >= limit:
                    s["over"][i] += 1

    missing = sorted(set(stats) - set(split))
    if missing:
        sys.exit("profiles not in the split file: %s" % missing)

    header = "| Profile | Split | Seconds (1 Hz) | Max stator winding (°C) | " + " | ".join(
        "Seconds >= %g °C" % t for t in THRESHOLDS) + " |"
    print(header)
    print("|" + "---|" * (4 + len(THRESHOLDS)))
    for profile in sorted(stats):
        s = stats[profile]
        print("| %d | %s | %d | %.1f | %s |" % (
            profile, split[profile], s["n"], s["max"], " | ".join(str(c) for c in s["over"])))

    total = sum(s["n"] for s in stats.values())
    print()
    print("Profiles: %d, total simulated seconds: %d" % (len(stats), total))
    for name in ("train", "validation", "test"):
        ids = [p for p in stats if split[p] == name]
        seconds = sum(stats[p]["n"] for p in ids)
        hot = sum(1 for p in ids if stats[p]["max"] >= THRESHOLDS[1])
        print("- %s: %d profiles, %d s, %d profiles reach %g °C" % (name, len(ids), seconds, hot, THRESHOLDS[1]))


if __name__ == "__main__":
    main()
