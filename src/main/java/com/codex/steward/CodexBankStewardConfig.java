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

	@ConfigItem(
		keyName = "bankMarks",
		name = "Mark items in the bank",
		description = "Mark the Steward's list on the items in your bank: a gold diamond for done with, a ring for dusty",
		position = 2
	)
	default boolean bankMarks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankSummary",
		name = "Summary at the bank",
		description = "The first time you open your bank each login, write in the chat box what the Steward's list could free",
		position = 3
	)
	default boolean bankSummary()
	{
		return true;
	}
}
