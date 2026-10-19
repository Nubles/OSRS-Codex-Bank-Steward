#!/usr/bin/env python3
"""Adds clue STASH gear and Codex Navigator goal chains to steward-data.json.

Run after build_data.py; running it again replaces what it added before.

    python3 tools/add_links.py src/main/resources/com/codex/steward/steward-data.json \
        build/stash.json path/to/OSRS-Codex-Navigator/src/main/resources/com/codex/navigator/codex-data.json

build/stash.json comes from tools/ExtractStash.java (see tools/README.md).

"stash" is a list of [itemId, tierMask] (beginner 1, easy 2, medium 4, hard 8, elite 16, master 32).
"goals" maps each goal key Navigator saves ("quest:<name>", "diary:<name>") to the Steward
task indexes the goal covers: the quest or diary itself and every quest it needs first, all the
way down. Navigator's quest cape goal covers every quest and is worked out in the plugin.
"""
import json
import sys

RFD = {
    "Another Cook's Quest": "RFD - Start",
    "Culinaromancer": "RFD - Finale",
}
DIARY_AREAS = {
    "Kourend": "Kourend & Kebos",
    "Lumbridge": "Lumbridge & Draynor",
    "Western": "Western Provinces",
}


def steward_names(nav_quest):
    """The Steward task names for one of Navigator's quest names."""
    if nav_quest == "Shield of Arrav":
        return ["Shield of Arrav - Phoenix Gang", "Shield of Arrav - Black Arm Gang"]
    prefix = "Recipe for Disaster - "
    if nav_quest.startswith(prefix):
        part = nav_quest[len(prefix):]
        return [RFD.get(part, "RFD - " + part)]
    return [nav_quest]


def main(data_path, stash_path, nav_path):
    data = json.load(open(data_path))
    stash = json.load(open(stash_path))
    nav = json.load(open(nav_path))
    index = {t["name"]: i for i, t in enumerate(data["tasks"])}
    quests = {q["name"]: q for q in nav["quests"]}

    def tasks_of(nav_quest):
        return [index[n] for n in steward_names(nav_quest) if n in index]

    def chain(nav_quest, seen):
        if nav_quest in seen:
            return set()
        seen.add(nav_quest)
        out = set(tasks_of(nav_quest))
        for p in quests.get(nav_quest, {}).get("prereqs", []):
            out |= chain(p, seen)
        return out

    goals = {}
    missing = []
    for name in quests:
        covered = chain(name, set())
        if covered:
            goals["quest:" + name] = sorted(covered)
        else:
            missing.append(name)
    for d in nav["diaries"]:
        area = DIARY_AREAS.get(d["area"], d["area"])
        own = index.get(area + " " + d["tier"] + " Diary")
        covered = set() if own is None else {own}
        seen = set()
        names = list(quests) if d.get("allQuests") else d.get("quests", [])
        for q in names:
            covered |= chain(q, seen)
        if covered:
            goals["diary:" + d["name"]] = sorted(covered)
        else:
            missing.append(d["name"])

    data["stash"] = sorted([int(i), int(m)] for i, m in stash)
    data["goals"] = dict(sorted(goals.items()))
    with open(data_path, "w") as f:
        json.dump(data, f, separators=(",", ":"))
    print("stash items:", len(data["stash"]), "goals:", len(goals), "with no Steward task:", missing, file=sys.stderr)


if __name__ == "__main__":
    main(*sys.argv[1:4])
