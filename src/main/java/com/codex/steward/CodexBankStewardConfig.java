package com.codex.steward;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CodexBankStewardConfig.GROUP)
public interface CodexBankStewardConfig extends Config
{
	String GROUP = "codexbanksteward";

	@Range(min = 14, max = 730)
	@Units(" days")
	@ConfigItem(
		keyName = "dustDays",
		name = "Dust after",
		description = "An item untouched in your bank for this long is listed as gathering dust",
		position = 0
	)
	default int dustDays()
	{
		return 90;
	}

	@Range(min = 0, max = 90)
	@Units(" days")
	@ConfigItem(
		keyName = "graceDays",
		name = "Recent use grace",
		description = "An item whose quests are done is not listed while it has moved within this many days, since you are still using it",
		position = 1
	)
	default int graceDays()
	{
		return 14;
	}

	enum ValueBy
	{
		/** The Grand Exchange, or high alchemy on an ironman. */
		AUTO("Auto"),
		GRAND_EXCHANGE("Grand Exchange"),
		HIGH_ALCHEMY("High alchemy");

		private final String label;

		ValueBy(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "valueBy",
		name = "Value items at",
		description = "What the list's gold is reckoned at. Auto uses Grand Exchange prices, or high alchemy on ironman accounts, which cannot use the Grand Exchange",
		position = 2
	)
	default ValueBy valueBy()
	{
		return ValueBy.AUTO;
	}

	@ConfigItem(
		keyName = "bankMarks",
		name = "Mark items in the bank",
		description = "Mark the Steward's list on the items in your bank: a gold diamond for done with, a ring for dusty",
		position = 3
	)
	default boolean bankMarks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankSummary",
		name = "Summary at the bank",
		description = "The first time you open your bank each login, write in the chat box what the Steward's list could free",
		position = 4
	)
	default boolean bankSummary()
	{
		return true;
	}

	@ConfigItem(
		keyName = "codexPlans",
		name = "Keep kit and goal items",
		description = "Never list what a Codex Quartermaster kit takes or what a goal pinned in Codex Navigator needs, and say which kit or goal keeps it. Only reads what those plugins save; it does nothing when they are not installed",
		position = 5
	)
	default boolean codexPlans()
	{
		return true;
	}

	@ConfigItem(
		keyName = "guardStash",
		name = "Guard clue STASH gear",
		description = "Never call gear that fills a clue STASH unit done with, even when every quest that wanted it is done. Dusty STASH gear is still listed, marked as STASH gear",
		position = 6
	)
	default boolean guardStash()
	{
		return true;
	}

	@ConfigItem(
		keyName = "untradeableDust",
		name = "Show untradeable dust",
		description = "Also list untradeable items untouched past the dust time, apart and with a warning. They are never counted in the gold or slots, since one may be a reward you cannot get back",
		position = 7
	)
	default boolean untradeableDust()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareBank",
		name = "Share bank with Codex",
		description = "Save your whole bank as last seen in your RuneLite profile, so other Codex plugins (Plunder Pilot, Pest Control Pilot, Quartermaster) know what is in it without opening it themselves. Turning it off removes the saved copy",
		position = 8
	)
	default boolean shareBank()
	{
		return true;
	}
}
