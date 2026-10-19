package com.codex.steward;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CodexBankStewardConfig.GROUP)
public interface CodexBankStewardConfig extends Config
{
	String GROUP = "codexbanksteward";

	@ConfigSection(
		name = "What to list",
		description = "Which items the Steward calls done with or dusty, and what they are worth",
		position = 0
	)
	String listing = "listing";

	@ConfigSection(
		name = "At the bank",
		description = "What the Steward shows while your bank is open",
		position = 1
	)
	String atTheBank = "atTheBank";

	@ConfigSection(
		name = "Codex plugins",
		description = "What the Steward reads from and shares with the other Codex plugins",
		position = 2
	)
	String codex = "codex";

	@Range(min = 14, max = 730)
	@Units(" days")
	@ConfigItem(
		keyName = "dustDays",
		name = "Dust after",
		description = "List an item as gathering dust once it has sat untouched in your bank this long",
		position = 0,
		section = listing
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
		description = "Leave an item off the done-with list while it has moved within this many days, since you are still using it",
		position = 1,
		section = listing
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
		description = "Count the list's gold at Grand Exchange prices or high alchemy; Auto picks high alchemy on ironman accounts",
		position = 2,
		section = listing
	)
	default ValueBy valueBy()
	{
		return ValueBy.AUTO;
	}

	@ConfigItem(
		keyName = "bankMarks",
		name = "Mark items in the bank",
		description = "Mark the Steward's list on your bank items: a gold diamond for done with, a ring for dusty, a cross on placeholders you can release",
		position = 0,
		section = atTheBank
	)
	default boolean bankMarks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankSummary",
		name = "Summary at the bank",
		description = "Say in the chat box what the list could free the first time you open your bank each login",
		position = 1,
		section = atTheBank
	)
	default boolean bankSummary()
	{
		return true;
	}

	@ConfigItem(
		keyName = "codexPlans",
		name = "Keep kit and goal items",
		description = "Never list what a Codex Quartermaster kit or a Codex Navigator goal needs, and show a Codex Gear Roadmap savings goal",
		position = 0,
		section = codex
	)
	default boolean codexPlans()
	{
		return true;
	}

	@ConfigItem(
		keyName = "guardStash",
		name = "Guard clue STASH gear",
		description = "Never call gear that fills a clue STASH unit done with, even once its quests are done",
		position = 3,
		section = listing
	)
	default boolean guardStash()
	{
		return true;
	}

	@ConfigItem(
		keyName = "untradeableDust",
		name = "Show untradeable dust",
		description = "Also list dusty untradeable items, apart and with a warning, never counted in the gold or slots",
		position = 4,
		section = listing
	)
	default boolean untradeableDust()
	{
		return false;
	}

	@ConfigItem(
		keyName = "shareBank",
		name = "Share bank with Codex",
		description = "Save your bank as last seen so other Codex plugins can count it without opening it; turning it off removes the copy",
		position = 1,
		section = codex
	)
	default boolean shareBank()
	{
		return true;
	}
}
