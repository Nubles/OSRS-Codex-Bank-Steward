#!/usr/bin/env python3
"""Builds src/main/resources/com/codex/steward/steward-data.json.

Inputs:
  1. The items of every quest, miniquest and achievement diary, read by running Quest Helper's
     own helpers (github.com/Zoinkwiz/quest-helper, BSD 2-Clause) with tools/ExtractQuestHelper.java.
     See tools/README.md for the exact steps.
  2. RuneLite's gameval ItemID.java, for the constant names behind the lasting-use list.
  3. RuneLite's item names (static.runelite.net cache/item/names.json), for the name lists and
     for linking items that share a name.

Usage: python3 tools/build_data.py quest-helper-items.json path/to/gameval/ItemID.java names.json

The output has, for each task, "needs" (what the quest asks you to bring; the only way an item
can be called spent) and "soft" (everything else the quest names, including items picked up
during it, and every other item sharing a name with anything it names; these only ever hold an
item back). Getting this wrong in the unsafe direction tells a player to sell something they
still need, so every list below errs towards keeping.
"""
import json
import re
import sys

OUT = 'src/main/resources/com/codex/steward/steward-data.json'

# An item named by more tasks than this, or only as one choice in a broad "any of" group,
# is a general good (a tool, a rune, food) rather than something bought for a quest.
MAX_TASKS = 5
MAX_GROUP = 4

# Items with a use long after the quest or diary that names them: diary rewards, capes,
# access items, boss keys and the like. The Steward never calls these spent or dusty.
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

# The same idea matched on in-game names: capes, gear sets, tools and keys that keep earning
# their slot after the quest that first asked for them.
LASTING_NAMES = [
    r'(?i)cape(\(t\))?$', r'(?i)\bvoid\b', r'(?i)secateurs', r'(?i)barbarian rod', r'^Bottomless', r'(?i)lantern',
    r'^Proselyte', r'^Initiate', r'^Keris', r'^Excalibur', r'(?i)ogre bow$', r'(?i)machete', r'^Toolkit$', r'^Dwarven helmet',
    r'(?i)greegree', r'(?i)catspeak', r'(?i)ghostspeak', r'(?i)^salve', r'(?i)^(ring|amulet|necklace|bracelet) of',
    r'(?i)^(dragon|rune|abyssal|barrows|granite|crystal) ', r'(?i)(whip|blowpipe|trident|godsword)',
    r"(?i)^ava's", r'(?i)^boots of (brimstone|stone)', r'^Slayer gloves', r'^Witchwood icon', r'^Insulated boots',
    r'^Spiked boots', r'^Mirror shield', r'^Fishbowl helmet', r'^Diving apparatus', r'(?i)prospector', r'^Seal of Passage',
    r"^V's shield", r'^Vyre noble', r"^M'speak amulet", r'(?i)butterfly net', r'^Bird snare', r'^Big fishing net',
    r'^Gardening trowel', r'^Shears$', r'^Mining helmet', r'^Desert (robe|shirt|boots)', r'^Commorb',
    # Quest rewards and access items that are untradeable or hard to replace.
    r'^(Silverlight|Darklight|Arclight|Emberlight)', r'^Battered key$', r"^Glarial's (pebble|amulet|urn)",
    r'^Monkey talisman$', r'^Karambwan vessel$', r'(?i)^camulet$', r'^Costume needle$', r'^Pet rock$',
    r'^Stuffed ', r'^Mounted ', r'^Enchanted key$', r'^Dark totem', r'^Ring of visibility$', r'^Wolfbane$',
    r'^Rod of ivandis', r'^Holy wrench$', r'(?i)^(holy|unholy|book of) (book|balance|war|law|darkness)$',
    r'^(Mind|Body|Chaos|Cosmic|Elemental) (helmet|body|shield)$', r'(?i)gauntlets$', r'(?i)^ancient (staff|mace)$',
    r"^Drakan's medallion$", r'(?i)neitiznot', r'(?i)^(berserker|archer|farseer|warrior) helm$', r'(?i)lyre$',
    r'^Crystal (saw|seed|teleport seed)$', r'^Barrelchest anchor$', r'^Bone (dagger|key)$', r'^Ring of shadows',
    r'^Lunar (helm|torso|legs|gloves|boots|cape|amulet|ring|staff)', r'^Moonclan ', r'^Sled$', r'^Gadderhammer',
    r"^Ghommal's", r'^Fighter (torso|hat)', r'^Penance ', r'^Graceful', r'^Angler', r'^Pyromancer', r'^Lumberjack',
    r'^Farmer\'s', r'^Rogue ', r'^Spirit angler', r'^Golden apron', r'^(Holy|Unholy) symbol$', r'^Ring of the gods',
    r'^Seers\' ', r'^Ardougne cloak', r'^Ancient shard', r'^Book of (knowledge|arcane)', r'^Lamp$',
    r'(?i)^(medium|large|small) pouch$', r'^Rune pouch', r'^Herb sack', r'^Gem bag', r'^Coal bag', r'^Seed box',
    r'^Fish barrel', r'^Log basket', r'^Tackle box', r'^Plank sack', r'^Looting bag', r'^Huntsman',
    r'^Flamtaer hammer$',
]

# Supplies, tools and raw materials that stay useful whatever is done, matched on the in-game
# name. These are never called spent (dust still applies): potions and jewellery charges,
# teleports, pouches and traps, every gathering and processing material, ammunition, slayer
# gear and currencies.
STAPLE_NAMES = [
    r'\(\d+\)$', r'(?i)teleport', r'(?i)pouch', r'(?i) trap$', r'^Secateurs', r'(?i)^(rake|spade|seed dibber|trowel|watering can)',
    r'(?i)^(knife|hammer|chisel|tinderbox|needle|saw|pestle and mortar|glassblowing pipe|rope)$', r'(?i)(pickaxe|axe|harpoon|fishing rod|lobster pot|small fishing net)$',
    r'(?i)^(antipoison|antidote|super|prayer potion|stamina|energy potion)', r'(?i)^(law|nature|cosmic|astral|death|blood|soul|wrath) rune$',
    # Ores, bars, stone, logs and planks.
    r'(?i) ore$', r'(?i) bar$', r'(?i)^(coal|clay|soft clay|limestone|limestone brick|sandstone.*|granite.*|marble block|magic stone)$',
    r'(?i)logs$', r'(?i)plank$', r'(?i)nails$', r'(?i)^(gold leaf|bolt of cloth|clockwork|steel bar)$',
    # Gems.
    r'(?i)^(uncut )?(opal|jade|red topaz|sapphire|emerald|ruby|diamond|dragonstone|onyx|zenyte)$',
    # Herbs, unfinished potions and secondaries.
    r'(?i)^(grimy )?(guam leaf|marrentill|tarromin|harralander|ranarr weed|toadflax|irit leaf|avantoe|kwuarm|snapdragon|'
    r'cadantine|lantadyme|dwarf weed|torstol|huasca)$', r'(?i)potion \(unf\)$',
    r"(?i)^(eye of newt|snape grass|limpwurt root|red spiders' eggs|white berries|jangerberries|unicorn horn( dust)?|"
    r"goat horn dust|chocolate (bar|dust)|wine of zamorak|potato cactus|crushed nest|bird nest|zulrah's scales|ashes|"
    r"volcanic ash|mort myre fungus|toad's legs|dragon scale dust|lava scale shard|nettles|cactus spine|amylase crystal|"
    r"ground charcoal|charcoal|garlic|garlic powder|silver dust|blue dragon scale|crushed superior dragon bones)$",
    # Farming.
    r'(?i) seed$', r'(?i) sapling$', r'(?i)compost$', r'(?i)^(plant cure|plant pot|filled plant pot|empty plant pot)$',
    # Crafting, fletching and runecrafting materials.
    r'(?i)^(wool|ball of wool|flax|bow string|leather|hard leather|cowhide|molten glass|bucket of sand|soda ash|seaweed|'
    r'silk|thread|feather|arrow shaft|headless arrow|tiara|unpowered orb|pure essence|rune essence|dark essence block|'
    r'dense essence block|linen|swamp tar|swamp paste|unfired bowl|empty fishbowl|fishbowl|bronze wire|barley malt|'
    r'dashing kebbit fur|willow branch|volcanic sulphur|scroll of redirection|roe|caviar|bucket of slime)$',
    r'(?i) dye$', r'(?i)^(opal|jade|topaz|sapphire|emerald|ruby|diamond|dragonstone|onyx|zenyte|gold|silver) '
    r'(amulet|ring|necklace|bracelet)( \(u\))?$',
    r'(?i)dragonhide$', r'(?i)dragon leather$', r'(?i)\(u\)$', r'(?i)^(shield (left|right) half)$',
    # Cooking ingredients and containers.
    r'(?i)^raw ', r'(?i)^(egg|bucket of milk|pot of flour|pot of cornflour|pot of cream|grain|onion|cabbage|potato|tomato|cheese|'
    r'spice|banana|lemon|orange|pineapple|coconut|grapes|cadava berries|redberries|dwellberries|vanilla pod|'
    r'bread dough|pastry dough|pizza base|beer|asgarnian ale|wizard\'s mind bomb|dwarven stout|greenman\'s ale|vodka|'
    r'jug|jug of water|bucket|bucket of water|bowl|bowl of water|vial|vial of water|beer glass|empty cup|cup of tea|'
    r'pie dish|cake tin|iron spit|gianne dough|gnome spice|equa leaves)$', r'(?i)mould$',
    # Hunter, fishing and slayer supplies, light sources, ammunition and currencies.
    r'(?i) jar$', r'(?i)^(teasing stick|noose wand|rabbit snare|fishing bait|dark fishing bait|sandworms|fishing explosive|'
    r'enchanted gem|bag of salt|ice cooler|fungicide spray|fungicide|rock hammer|rock thrownhammer|nose peg|earmuffs|facemask|'
    r'spiny helmet|slayer bell|leaf-bladed (sword|spear|battleaxe)|unlit bug lantern|lit bug lantern|king worm|'
    r'red vine worm|muddy key)$',
    r'(?i)(candle|torch)$', r'(?i)(arrow|arrows|bolts|dart|darts|javelin|thrownaxe|cannonball)(\(p\+*\))?$',
    r'(?i)^(ecto-token|trading sticks|shantay pass|mark of grace|numulite|tokkul|platinum token|stardust|golden nugget|'
    r'unidentified minerals|crystal shard|ancient essence|molch pearl|abyssal pearls)$',
    r'(?i)^(big |dragon |wyvern |lava dragon |superior dragon |dagannoth |babydragon |wolf |bat |monkey |jogre |zogre |'
    r'ourg |fayrg |raurg |burnt |shaikahan |wyrm |drake |hydra )?bones$',
]

# Runes, essence and coins: always useful, whatever quests are done.
STAPLES = list(range(554, 567)) + [9075, 21880, 4694, 4695, 4696, 4697, 4698, 4699, 1436, 7936, 995, 13204]


def item_names(path):
    text = open(path, encoding='utf-8').read()
    top = text.split('public static final class Cert')[0]
    return [(m.group(1), int(m.group(2))) for m in re.finditer(r'public static final int (\w+) = (\d+);', top)]


def matching(names, expressions):
    patterns = [re.compile(p) for p in expressions]
    return {int(i) for i, n in names.items() if any(p.search(n) for p in patterns)}


def entries(raw, staples):
    """A task's needs as [qty, ids...], merged where the same items are named twice."""
    merged = {}
    for n in raw:
        ids = [i for i in n['ids'] if i not in staples]
        if not ids:
            continue
        key = tuple(ids)
        merged[key] = max(merged.get(key, 1), n['qty'])
    return merged


def main(items_path, itemid_path, names_path):
    tasks_in = json.load(open(items_path))
    names = json.load(open(names_path, encoding='utf-8'))
    by_name = {}
    for i, n in names.items():
        if n and n.lower() != 'null':
            by_name.setdefault(n, []).append(int(i))
    staples = set(STAPLES)

    tasks, needs, soft = [], [], []
    users, wide = {}, set()
    for t in tasks_in:
        task = {'name': t['name'], 'kind': t['kind']}
        if 'quest' in t:
            task['quest'] = t['quest']
        else:
            task['varbit'] = t['varbit']
            task['value'] = t['value']
        index = len(tasks)
        tasks.append(task)
        hard = entries(t["needs"], staples)
        loose = entries(t.get("soft", []), staples)
        named = set()
        for ids, qty in hard.items():
            needs.append([index, qty] + list(ids))
            named.update(ids)
            if len(ids) > MAX_GROUP:
                wide.update(ids)
        # Everything else the task names, and every item sharing a name with anything it names,
        # holds an item back but never makes one spent.
        extra = {}
        for ids, qty in list(hard.items()) + list(loose.items()):
            kin = []
            for i in ids:
                for k in by_name.get(names.get(str(i), ''), []):
                    if k not in staples and k not in ids and k not in kin:
                        kin.append(k)
            if (ids, qty) not in hard.items():
                extra[ids] = max(extra.get(ids, 1), qty)
                named.update(ids)
            if kin:
                key = tuple(kin)
                extra[key] = max(extra.get(key, 1), qty)
        for ids, qty in extra.items():
            soft.append([index, qty] + list(ids))
        for i in named:
            users[i] = users.get(i, 0) + 1

    general = sorted(staples | matching(names, STAPLE_NAMES) | wide | {i for i, c in users.items() if c > MAX_TASKS})
    patterns = [re.compile(p) for p in LASTING]
    lasting = sorted({i for name, i in item_names(itemid_path)
                      if not name.startswith('DUMMY') and any(p.search(name) for p in patterns)}
                     | matching(names, LASTING_NAMES))
    out = {
        'source': 'Generated by tools/build_data.py from Quest Helper (github.com/Zoinkwiz/quest-helper, BSD 2-Clause). Do not hand-edit.',
        'tasks': tasks,
        'needs': needs,
        'soft': soft,
        'general': general,
        'lasting': lasting,
    }
    with open(OUT, 'w') as f:
        json.dump(out, f, separators=(',', ':'))
    print(len(tasks), 'tasks,', len(needs), 'needs,', len(soft), 'soft,', len(general), 'general,', len(lasting), 'lasting')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3])
