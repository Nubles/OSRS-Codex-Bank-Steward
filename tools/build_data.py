#!/usr/bin/env python3
"""Builds src/main/resources/com/codex/steward/steward-data.json.

Inputs:
  1. The item requirements of every quest, miniquest and achievement diary, read by running
     Quest Helper's own helpers (github.com/Zoinkwiz/quest-helper, BSD 2-Clause) with
     tools/ExtractQuestHelper.java. See tools/README.md for the exact steps.
  2. RuneLite's gameval ItemID.java, for the constant names behind the lasting-use list.
  3. RuneLite's item names (static.runelite.net cache/item/names.json), for the staples list.

Usage: python3 tools/build_data.py quest-helper-items.json path/to/gameval/ItemID.java names.json
"""
import json
import re
import sys

OUT = 'src/main/resources/com/codex/steward/steward-data.json'

# An item needed by more tasks than this, or only as one choice in a broad "any of" group,
# is a general good (a tool, a rune, food) rather than something bought for a quest.
MAX_TASKS = 5
MAX_GROUP = 4

# Items with a use long after the quest or diary that names them: diary rewards, capes,
# access items and the like. The Steward never calls these spent.
LASTING = [
    r'GREEGREE', r'^ARDY_CAPE_', r'^ATJUN_GLOVES_', r'^MORYTANIA_LEGS_', r'^FREMENNIK_BOOTS_',
    r'^VARROCK_ARMOUR_', r'^FALADOR_SHIELD_', r'^DESERT_AMULET_', r'^SEERS_HEADBAND_',
    r'^WILDERNESS_SWORD_', r'^LUMBRIDGE_RING_', r'^WESTERN_BANNER_', r'^ZEAH_BLESSING_',
    r'^SKILLCAPE_', r'^XERIC_TALISMAN', r'^ANTIDRAGONBREATHSHIELD', r'^DRAGONFIRE_(SHIELD|WARD)',
    r'^AVAS_', r'^RING_OF_CHAROS', r'^IBANSTAFF', r'KHAREDSTS_MEMOIRS', r'^BOOK_OF_THE_DEAD',
    r'^PEST_VOID_KNIGHT', r'^ELITE_VOID', r'CRYSTALSHARD_NECKLACE', r'^SILVER_SICKLE',
    r'_FLAIL$', r'^BURGH_ROD_COMMAND_FINAL', r'^AMULET_OF_GHOSTSPEAK', r'^ECTOPHIAL',
    r'^DRAMEN_STAFF$', r'^LUNAR_MOONCLAN_LIMINAL_STAFF$', r'^PRIF_TELEPORT_SEED',
    r'^EXPLORER_BACKPACK', r'^MOURNING_GAS_MASK', r'^SLAYER_HELM', r'^BARROWS_',
]

# The same idea matched on in-game names: capes, gear sets and tools that keep earning their
# slot after the quest that first asked for them.
LASTING_NAMES = [
    r'(?i)cape(\(t\))?$', r'(?i)\bvoid\b', r'(?i)secateurs', r'(?i)barbarian rod', r'^Bottomless', r'(?i)lantern',
    r'^Proselyte', r'^Initiate', r'^Keris', r'^Excalibur', r'(?i)ogre bow$', r'(?i)machete', r'^Toolkit$', r'^Dwarven helmet',
    r'(?i)greegree', r'(?i)catspeak', r'(?i)ghostspeak', r'(?i)^salve', r'(?i)^(ring|amulet|necklace|bracelet) of',
    r'(?i)^(dragon|rune|abyssal|barrows|granite|crystal) ', r'(?i)(whip|blowpipe|trident|godsword)',
    r"(?i)^ava's", r'(?i)^boots of (brimstone|stone)', r'^Slayer gloves', r'^Witchwood icon', r'^Insulated boots',
    r'^Spiked boots', r'^Mirror shield', r'^Fishbowl helmet', r'^Diving apparatus', r'(?i)prospector', r'^Seal of Passage',
    r"^V's shield", r'^Vyre noble', r"^M'speak amulet", r'(?i)butterfly net', r'^Bird snare', r'^Big fishing net',
    r'^Gardening trowel', r'^Shears$', r'^Mining helmet', r'^Desert (robe|shirt|boots)', r'^Commorb',
]

# Supplies and tools that stay useful whatever is done, matched on the in-game name: doses of
# potions and charges of jewellery, teleports, pouches and traps, farming tools.
STAPLE_NAMES = [
    r'\(\d+\)$', r'(?i)teleport', r'(?i)pouch', r'(?i) trap$', r'^Secateurs', r'(?i)^(rake|spade|seed dibber|trowel|watering can)',
    r'(?i)^(knife|hammer|chisel|tinderbox|needle|saw|pestle and mortar|glassblowing pipe|rope)$', r'(?i)(pickaxe|axe|harpoon|fishing rod|lobster pot|small fishing net)$',
    r'(?i)^(antipoison|antidote|super|prayer potion|stamina|energy potion)', r'(?i)^(law|nature|cosmic|astral|death|blood|soul|wrath) rune$',
]

# Runes, essence and coins: always useful, whatever quests are done.
STAPLES = list(range(554, 567)) + [9075, 21880, 4694, 4695, 4696, 4697, 4698, 4699, 1436, 7936, 995, 13204]


def item_names(path):
    text = open(path, encoding='utf-8').read()
    top = text.split('public static final class Cert')[0]
    return [(m.group(1), int(m.group(2))) for m in re.finditer(r'public static final int (\w+) = (\d+);', top)]


def matching(names_path, expressions):
    names = json.load(open(names_path, encoding='utf-8'))
    patterns = [re.compile(p) for p in expressions]
    return {int(i) for i, n in names.items() if any(p.search(n) for p in patterns)}


def main(items_path, itemid_path, names_path):
    tasks_in = json.load(open(items_path))
    tasks, needs = [], []
    users, wide = {}, set()
    for t in tasks_in:
        task = {'name': t['name'], 'kind': t['kind']}
        if t['kind'] == 'diary':
            task['varbit'] = t['varbit']
            task['value'] = t['value']
        else:
            task['quest'] = t['quest']
        index = len(tasks)
        tasks.append(task)
        seen = set()
        for n in t['needs']:
            ids = [i for i in n['ids'] if i not in STAPLES]
            if not ids:
                continue
            needs.append([index, n['qty']] + ids)
            for i in ids:
                if len(ids) > MAX_GROUP:
                    wide.add(i)
                if i not in seen:
                    users[i] = users.get(i, 0) + 1
                    seen.add(i)
    general = sorted(set(STAPLES) | matching(names_path, STAPLE_NAMES) | wide | {i for i, c in users.items() if c > MAX_TASKS})
    patterns = [re.compile(p) for p in LASTING]
    lasting = sorted({i for name, i in item_names(itemid_path)
                      if not name.startswith('DUMMY') and any(p.search(name) for p in patterns)}
                     | matching(names_path, LASTING_NAMES))
    out = {
        'source': 'Generated by tools/build_data.py from Quest Helper (github.com/Zoinkwiz/quest-helper, BSD 2-Clause). Do not hand-edit.',
        'tasks': tasks,
        'needs': needs,
        'general': general,
        'lasting': lasting,
    }
    with open(OUT, 'w') as f:
        json.dump(out, f, separators=(',', ':'))
    print(len(tasks), 'tasks,', len(needs), 'needs,', len(general), 'general,', len(lasting), 'lasting')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3])
