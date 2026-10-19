package com.codex.steward.ui;

import com.codex.steward.core.Advice;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import net.runelite.client.ui.FontManager;

/**
 * The Codex look: warm ink, aged gold and a single arcane teal, drawn with the
 * game's own fonts so the panel reads as part of Gielinor rather than a web page.
 * The Steward marks what is done with in gold, what has gathered dust in ash, what is still
 * owed to unfinished work in the arcane teal and what the player chose to keep in violet, so an
 * item's colour says its verdict before its note is read.
 */
public final class Theme
{
	public static final Color INK = new Color(23, 20, 16);
	public static final Color INK_DEEP = new Color(16, 14, 11);
	public static final Color CARD = new Color(34, 30, 24);
	public static final Color CARD_TOP = new Color(42, 37, 29);
	public static final Color CARD_HOVER = new Color(48, 42, 33);
	public static final Color STROKE = new Color(66, 56, 42);
	public static final Color STROKE_SOFT = new Color(48, 41, 32);

	public static final Color GOLD = new Color(214, 178, 106);
	public static final Color GOLD_BRIGHT = new Color(246, 214, 144);
	public static final Color GOLD_DIM = new Color(132, 108, 66);
	public static final Color TEAL = new Color(96, 184, 170);
	public static final Color GREEN = new Color(130, 196, 108);
	public static final Color EMBER = new Color(222, 134, 86);
	public static final Color BLOCKED = new Color(176, 92, 84);
	public static final Color BLOOD = new Color(204, 70, 60);
	public static final Color BLOOD_DEEP = new Color(120, 32, 28);
	public static final Color VIOLET = new Color(176, 132, 222);

	public static final Color TEXT = new Color(234, 224, 202);
	public static final Color TEXT_MUTED = new Color(160, 148, 126);
	public static final Color TEXT_FAINT = new Color(110, 100, 84);

	public static final int RADIUS = 10;
	public static final int GUTTER = 8;

	private Theme()
	{
	}

	public static Font title()
	{
		return FontManager.getRunescapeBoldFont();
	}

	public static Font body()
	{
		return FontManager.getRunescapeFont();
	}

	public static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	public static Color alpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}

	public static Color mix(Color a, Color b, double t)
	{
		t = Math.max(0, Math.min(1, t));
		return new Color(
			(int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
			(int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
			(int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
	}

	/** Progress colour: ember when just begun, gold through the middle, green when nearly there. */
	public static Color progressColor(double progress)
	{
		if (progress >= 1)
		{
			return GREEN;
		}
		if (progress < 0.5)
		{
			return mix(EMBER, GOLD, progress * 2);
		}
		return mix(GOLD, TEAL, (progress - 0.5) * 2);
	}

	/** Item quantities in the game's own colours: yellow, white from 100K, green from 10M. */
	public static final Color QTY_YELLOW = new Color(255, 255, 0);
	public static final Color QTY_WHITE = new Color(255, 255, 255);
	public static final Color QTY_GREEN = new Color(0, 255, 128);

	public static final Color ASH = new Color(176, 156, 128);
	public static final Color SPENT = GOLD;
	public static final Color DUST = ASH;
	public static final Color NEEDED = TEAL;
	public static final Color KEPT = VIOLET;
	/** Kept for a plan: a kit, a goal or a STASH unit. A paler teal than the needed. */
	public static final Color SPARED = new Color(132, 196, 204);

	/** How a clear-out gets rid of an item: gold for a sale, ember for the fire of an alch, ash for a drop. */
	public static Color route(com.codex.steward.core.ClearOut.Route route)
	{
		switch (route)
		{
			case SELL:
				return GOLD;
			case ALCH:
				return EMBER;
			default:
				return ASH;
		}
	}

	public static Color verdict(Advice.Verdict verdict)
	{
		switch (verdict)
		{
			case SPENT:
				return SPENT;
			case DUST:
				return DUST;
			case NEEDED:
				return NEEDED;
			case SPARED:
				return SPARED;
			default:
				return KEPT;
		}
	}

	/** Smooth shapes, but the pixel fonts stay crisp. */
	public static void smooth(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
	}
}
