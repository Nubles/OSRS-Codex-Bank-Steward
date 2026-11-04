package com.codex.steward.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Gear for the outgrown-gear tests and pictures. The plugin reads stats from RuneLite's item
 * manager at runtime; these stand in for them. Attack, strength and speed follow Codex Gear
 * Roadmap's data (built from osrs-dps-calc); defences are rounded, illustrative figures that keep
 * each piece's order against the others in its slot. Ids are the game's.
 */
public final class GearFixture
{
	public static final int WHIP = 4151;
	public static final int RUNE_SCIMITAR = 1333;
	public static final int ADAMANT_SCIMITAR = 1331;
	public static final int MITHRIL_SCIMITAR = 1329;
	public static final int RUNE_MACE = 1432;
	public static final int GRANITE_MAUL = 4153;
	public static final int STAFF_OF_FIRE = 1387;
	public static final int MAGIC_SHORTBOW = 861;
	public static final int MAPLE_SHORTBOW = 853;
	public static final int BANDOS_CHESTPLATE = 11832;
	public static final int RUNE_PLATEBODY = 1127;
	public static final int ADAMANT_PLATEBODY = 1123;
	public static final int FACEGUARD = 24271;
	public static final int RUNE_FULL_HELM = 1163;
	public static final int MYSTIC_HAT = 4089;
	public static final int DRAGON_BOOTS = 11840;
	public static final int RUNE_BOOTS = 4131;
	public static final int FURY = 6585;
	public static final int AMULET_OF_POWER = 1731;
	public static final int AMULET_OF_STRENGTH = 1725;
	public static final int GLORY = 1712;
	public static final int DEFENDER = 12954;
	public static final int RUNE_KITESHIELD = 1201;
	public static final int BLACK_DHIDE_BODY = 2503;
	public static final int GREEN_DHIDE_BODY = 1135;
	public static final int SNAKESKIN_BODY = 6322;
	public static final int SARADOMIN_DHIDE_BODY = 10386;
	public static final int DHAROKS_PLATEBODY = 4720;
	public static final int BLACK_MASK = 8921;
	public static final int DRAGON_PLATELEGS = 4087;
	public static final int RUNE_PLATELEGS = 1079;
	public static final int FIGHTER_TORSO = 10551;
	public static final int CHEF_HAT = 1949;

	private static final String[] WIELD = {null, "Wield", null, null, "Drop"};
	private static final String[] WEAR = {null, "Wear", null, null, "Drop"};

	public static final Map<Integer, Outgrown.Item> ITEMS = new HashMap<>();
	public static final Map<Integer, String> NAMES = new HashMap<>();

	static
	{
		// slot, 2h, astab, aslash, acrush, amagic, arange, dstab, dslash, dcrush, dmagic, drange, str, rstr, mdmg, prayer, speed
		wield(WHIP, "Abyssal whip", s(3, false, 0, 82, 0, 0, 0, 0, 0, 0, 0, 0, 82, 0, 0, 0, 4));
		wield(RUNE_SCIMITAR, "Rune scimitar", s(3, false, 7, 45, -2, 0, 0, 0, 1, 0, 0, 0, 44, 0, 0, 0, 4));
		wield(ADAMANT_SCIMITAR, "Adamant scimitar", s(3, false, 6, 29, -2, 0, 0, 0, 1, 0, 0, 0, 28, 0, 0, 0, 4));
		wield(MITHRIL_SCIMITAR, "Mithril scimitar", s(3, false, 5, 21, -2, 0, 0, 0, 1, 0, 0, 0, 20, 0, 0, 0, 4));
		wield(RUNE_MACE, "Rune mace", s(3, false, 20, -2, 39, 0, 0, 0, 0, 0, 0, 0, 36, 0, 0, 3, 5));
		wield(GRANITE_MAUL, "Granite maul", s(3, true, 0, 0, 81, 0, 0, 0, 0, 0, 0, 0, 79, 0, 0, 0, 7));
		wield(STAFF_OF_FIRE, "Staff of fire", s(3, false, 3, -1, 9, 10, 0, 2, 3, 1, 10, 0, 6, 0, 0, 0, 5));
		wield(MAGIC_SHORTBOW, "Magic shortbow", s(3, true, 0, 0, 0, 0, 69, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4));
		wield(MAPLE_SHORTBOW, "Maple shortbow", s(3, true, 0, 0, 0, 0, 29, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4));
		wear(BANDOS_CHESTPLATE, "Bandos chestplate", s(4, false, 0, 0, 0, -15, -10, 98, 93, 105, -6, 133, 4, 0, 0, 1, 0));
		wear(RUNE_PLATEBODY, "Rune platebody", s(4, false, 0, 0, 0, -30, -15, 82, 80, 72, -6, 80, 0, 0, 0, 0, 0));
		wear(ADAMANT_PLATEBODY, "Adamant platebody", s(4, false, 0, 0, 0, -30, -15, 65, 63, 55, -6, 63, 0, 0, 0, 0, 0));
		wear(FACEGUARD, "Neitiznot faceguard", s(0, false, 0, 0, 0, 0, 0, 34, 34, 34, 5, 34, 6, 0, 0, 3, 0));
		wear(RUNE_FULL_HELM, "Rune full helm", s(0, false, 0, 0, 0, -6, -3, 30, 32, 27, -1, 30, 0, 0, 0, 0, 0));
		wear(MYSTIC_HAT, "Mystic hat", s(0, false, 0, 0, 0, 4, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0));
		wear(DRAGON_BOOTS, "Dragon boots", s(10, false, 0, 0, 0, -3, -1, 16, 17, 18, 0, 0, 4, 0, 0, 0, 0));
		wear(RUNE_BOOTS, "Rune boots", s(10, false, 0, 0, 0, -3, -1, 12, 13, 14, 0, 0, 2, 0, 0, 0, 0));
		wear(FURY, "Amulet of fury", s(2, false, 10, 10, 10, 10, 10, 15, 15, 15, 15, 15, 8, 0, 0, 5, 0));
		wear(AMULET_OF_POWER, "Amulet of power", s(2, false, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 0, 0, 1, 0));
		wear(AMULET_OF_STRENGTH, "Amulet of strength", s(2, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0, 0));
		ITEMS.put(GLORY, new Outgrown.Item("Amulet of glory(4)", true, false, new String[]{null, "Wear", "Rub", null, "Drop"},
			s(2, false, 10, 10, 10, 10, 10, 3, 3, 3, 3, 3, 6, 0, 0, 3, 0)));
		NAMES.put(GLORY, "Amulet of glory(4)");
		ITEMS.put(DEFENDER, new Outgrown.Item("Dragon defender", false, false, WIELD, s(5, false, 25, 24, 23, -3, -2, 25, 24, 23, -3, -2, 6, 0, 0, 0, 0)));
		NAMES.put(DEFENDER, "Dragon defender");
		wear(RUNE_KITESHIELD, "Rune kiteshield", s(5, false, 0, 0, 0, -8, -2, 44, 48, 46, -1, 46, 0, 0, 0, 0, 0));
		wear(BLACK_DHIDE_BODY, "Black d'hide body", s(4, false, 0, 0, 0, -15, 30, 55, 47, 60, 50, 57, 0, 0, 0, 0, 0));
		wear(GREEN_DHIDE_BODY, "Green d'hide body", s(4, false, 0, 0, 0, -15, 15, 18, 27, 24, 20, 35, 0, 0, 0, 0, 0));
		wear(SNAKESKIN_BODY, "Snakeskin body", s(4, false, 0, 0, 0, -5, 12, 25, 28, 32, 15, 35, 0, 0, 0, 0, 0));
		wear(SARADOMIN_DHIDE_BODY, "Saradomin d'hide body", s(4, false, 0, 0, 0, -15, 30, 55, 47, 60, 50, 57, 0, 0, 0, 1, 0));
		wear(DHAROKS_PLATEBODY, "Dharok's platebody", s(4, false, 0, 0, 0, -30, -15, 122, 120, 112, -6, 112, 0, 0, 0, 0, 0));
		wear(BLACK_MASK, "Black mask", s(0, false, 0, 0, 0, -6, -3, 30, 32, 27, -1, 30, 0, 0, 0, 0, 0));
		wear(DRAGON_PLATELEGS, "Dragon platelegs", s(7, false, 0, 0, 0, -21, -11, 68, 66, 63, -4, 65, 0, 0, 0, 0, 0));
		wear(RUNE_PLATELEGS, "Rune platelegs", s(7, false, 0, 0, 0, -21, -11, 51, 49, 47, -4, 49, 0, 0, 0, 0, 0));
		ITEMS.put(FIGHTER_TORSO, new Outgrown.Item("Fighter torso", false, false, WEAR, s(4, false, 0, 0, 0, -40, 0, 62, 62, 62, 0, 0, 4, 0, 0, 1, 0)));
		NAMES.put(FIGHTER_TORSO, "Fighter torso");
		wear(CHEF_HAT, "Chef's hat", s(0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
	}

	private GearFixture()
	{
	}

	private static Outgrown.Stats s(int slot, boolean twoHanded, int astab, int aslash, int acrush, int amagic, int arange,
		int dstab, int dslash, int dcrush, int dmagic, int drange, int str, int rstr, float mdmg, int prayer, int speed)
	{
		return new Outgrown.Stats(slot, twoHanded, astab, aslash, acrush, amagic, arange, dstab, dslash, dcrush, dmagic, drange,
			str, rstr, mdmg, prayer, speed);
	}

	private static void wield(int id, String name, Outgrown.Stats stats)
	{
		ITEMS.put(id, new Outgrown.Item(name, true, false, WIELD, stats));
		NAMES.put(id, name);
	}

	private static void wear(int id, String name, Outgrown.Stats stats)
	{
		ITEMS.put(id, new Outgrown.Item(name, true, false, WEAR, stats));
		NAMES.put(id, name);
	}

	public static Outgrown.Stats stats(int id)
	{
		return ITEMS.get(id).stats;
	}
}
