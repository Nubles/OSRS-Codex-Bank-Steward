package com.codex.steward;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CodexBankStewardPluginRunner
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CodexBankStewardPlugin.class);
		RuneLite.main(args);
	}
}
