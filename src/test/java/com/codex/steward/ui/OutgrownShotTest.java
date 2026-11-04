package com.codex.steward.ui;

import static com.codex.steward.core.GearFixture.*;
import static org.junit.Assert.assertTrue;
import com.codex.steward.core.Advice;
import com.codex.steward.core.Catalog;
import com.codex.steward.core.ClearOut;
import com.codex.steward.core.GearFixture;
import com.codex.steward.core.Memory;
import com.codex.steward.core.Outgrown;
import com.codex.steward.core.Plans;
import com.codex.steward.core.Reckoning;
import com.codex.steward.core.Tools;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import org.junit.Test;

/**
 * Paints the Clear out page's outgrown gear headless. With {@code -Dcodex.shots=<dir>} it saves the
 * pictures, and with {@code -Dcodex.icons=<dir>} it draws item pictures from {@code <id>.png} there.
 */
public class OutgrownShotTest
{
	private static final long TODAY = 20_000;

	private static final int[] BANK = {RUNE_SCIMITAR, ADAMANT_SCIMITAR, MITHRIL_SCIMITAR, RUNE_MACE, RUNE_PLATEBODY, RUNE_FULL_HELM,
		MYSTIC_HAT, RUNE_BOOTS, AMULET_OF_POWER, AMULET_OF_STRENGTH, GLORY, GREEN_DHIDE_BODY, SNAKESKIN_BODY, MAPLE_SHORTBOW,
		GRANITE_MAUL, STAFF_OF_FIRE, SARADOMIN_DHIDE_BODY, DHAROKS_PLATEBODY, BLACK_MASK, RUNE_KITESHIELD, FIGHTER_TORSO, CHEF_HAT,
		RUNE_PLATELEGS, BLACK_DHIDE_BODY, MAGIC_SHORTBOW};
	private static final int[] WORN = {WHIP, BANDOS_CHESTPLATE, FACEGUARD, DRAGON_BOOTS, FURY, DEFENDER, DRAGON_PLATELEGS};

	/** Illustrative Grand Exchange prices. */
	private static long price(int id)
	{
		switch (id)
		{
			case RUNE_PLATEBODY:
				return 38_400;
			case RUNE_PLATELEGS:
				return 37_900;
			case RUNE_FULL_HELM:
				return 20_600;
			case RUNE_SCIMITAR:
				return 14_900;
			case MAPLE_SHORTBOW:
				return 120;
			case GREEN_DHIDE_BODY:
				return 3_900;
			case SNAKESKIN_BODY:
				return 2_100;
			case RUNE_BOOTS:
				return 7_800;
			case AMULET_OF_POWER:
				return 2_200;
			case ADAMANT_SCIMITAR:
				return 1_100;
			case MITHRIL_SCIMITAR:
				return 320;
			case MYSTIC_HAT:
				return 11_000;
			case GRANITE_MAUL:
				return 640_000;
			case DHAROKS_PLATEBODY:
				return 1_700_000;
			case BLACK_MASK:
				return 820_000;
			default:
				return 9_000;
		}
	}

	private static Reckoning reckoning(Set<Outgrown.Style> styles)
	{
		Map<Integer, Long> bank = new HashMap<>();
		for (int id : BANK)
		{
			bank.put(id, 1L);
		}
		Memory memory = new Memory();
		memory.observeBank(bank, TODAY - 30);
		memory.observeWorn(Arrays.stream(WORN).boxed().collect(java.util.stream.Collectors.toList()), TODAY);
		memory.observeWorn(Arrays.asList(BLACK_DHIDE_BODY, MAGIC_SHORTBOW), TODAY - 20);
		for (Outgrown.Style s : styles)
		{
			memory.observeStyle(s, TODAY - 2);
		}
		Set<Integer> held = Arrays.stream(WORN).boxed().collect(java.util.stream.Collectors.toSet());
		Set<Integer> owned = new HashSet<>(bank.keySet());
		owned.addAll(held);
		Catalog.Data data = new Catalog.Data();
		Catalog catalog = new Catalog(data);
		Outgrown.Account account = new Outgrown.Account(memory.stylesUsed(TODAY, Outgrown.WORN_DAYS), memory.getWorn(), TODAY,
			Collections.emptySet(), null);
		Map<Integer, Outgrown.Verdict> gear = Reckoning.judgeGear(catalog, bank, owned, GearFixture.ITEMS::get, account);
		return Reckoning.of(catalog, memory, bank, held, new boolean[0], OutgrownShotTest::price, OutgrownShotTest::price,
			id -> price(id) / 2, id -> GearFixture.ITEMS.get(id).isTradeable(), new Reckoning.Rules(90, 14), Plans.none(),
			Tools.Levels.UNKNOWN, gear, TODAY);
	}

	private static Map<Integer, String> names()
	{
		return new HashMap<>(GearFixture.NAMES);
	}

	@Test
	public void outgrownGearPaints() throws Exception
	{
		Reckoning r = reckoning(EnumSet.of(Outgrown.Style.MELEE, Outgrown.Style.RANGED));
		assertTrue(r.getOutgrown().size() >= 6);
		JComponent page = page(r);
		String text = text(page);
		assertTrue(text, text.contains("Abyssal whip is at least as good"));
		assertTrue(text, text.contains("special attack (OSRS Wiki)"));
		assertTrue(text, text.contains("1 untradeable"));
		shoot(page, "outgrown-page.png", "OUTGROWN GEAR");

		// Before the Steward has seen a fight, nothing is outgrown and every piece says why.
		Reckoning none = reckoning(EnumSet.noneOf(Outgrown.Style.class));
		assertTrue(none.getOutgrown().isEmpty());
		JComponent waiting = page(none);
		assertTrue(text(waiting).contains("Fight a little"));
		shoot(waiting, "outgrown-before-a-fight.png", "OUTGROWN GEAR");

		// A magic user keeps the mystic hat the faceguard beats for melee alone.
		Reckoning mage = reckoning(EnumSet.allOf(Outgrown.Style.class));
		for (Advice a : mage.getOutgrown())
		{
			assertTrue(a.getItemId() != MYSTIC_HAT);
		}
	}

	private static JComponent page(Reckoning r) throws Exception
	{
		StewardPanel panel = new StewardPanel(icons(), new StewardPanel.Actions()
		{
			@Override
			public void keep(int itemId, boolean kept)
			{
			}

			@Override
			public void startClearOut()
			{
			}

			@Override
			public void endClearOut()
			{
			}
		});
		List<Advice> going = new ArrayList<>(r.getSpent());
		going.addAll(r.getDust());
		going.addAll(r.getOutgrown());
		panel.update(r, new StewardPanel.Clearing(ClearOut.plan(going, false, 100), null, null), names(), "Gilded Owl",
			StewardPanel.State.READY, false);
		panel.showTab(0);
		panel.setSize(225, 900);
		Field f = StewardPanel.class.getDeclaredField("clear");
		f.setAccessible(true);
		JScrollPane scroll = (JScrollPane) f.get(panel);
		return (JComponent) scroll.getViewport().getView();
	}

	private static ItemIcons icons()
	{
		String dir = System.getProperty("codex.icons");
		Map<Integer, BufferedImage> cache = new HashMap<>();
		return (id, done) ->
		{
			if (dir == null)
			{
				return null;
			}
			return cache.computeIfAbsent(id, k ->
			{
				try
				{
					File f = new File(dir, k + ".png");
					return f.exists() ? ImageIO.read(f) : null;
				}
				catch (Exception e)
				{
					return null;
				}
			});
		};
	}

	private static String text(Component c)
	{
		StringBuilder sb = new StringBuilder();
		if (c instanceof JLabel)
		{
			sb.append(((JLabel) c).getText()).append('\n');
		}
		if (c instanceof JComponent && ((JComponent) c).getToolTipText() != null)
		{
			sb.append(((JComponent) c).getToolTipText()).append('\n');
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				sb.append(text(child));
			}
		}
		return sb.toString();
	}

	/** The y of the section header with this text inside the page, or 0. */
	private static int find(Container c, String header, int dy)
	{
		for (Component child : c.getComponents())
		{
			if (child instanceof SectionHeader && header.equals(((SectionHeader) child).getText()))
			{
				return dy + child.getY();
			}
			if (child instanceof Container)
			{
				int y = find((Container) child, header, dy + child.getY());
				if (y > 0)
				{
					return y;
				}
			}
		}
		return 0;
	}

	private static void shoot(JComponent c, String name, String from) throws Exception
	{
		int width = 217;
		c.setSize(width, 10);
		layout(c);
		Dimension pref = c.getPreferredSize();
		c.setSize(width, pref.height);
		layout(c);
		layout(c);
		int top = Math.max(0, find(c, from, 0) - 8);
		String dir = System.getProperty("codex.shots");
		if (dir == null)
		{
			return;
		}
		int h = pref.height - top;
		int scale = 2;
		BufferedImage img = new BufferedImage(width * scale, h * scale, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.scale(scale, scale);
		g.setColor(Theme.INK);
		g.fillRect(0, 0, width, h);
		g.translate(0, -top);
		g.setClip(0, top, width, h);
		c.printAll(g);
		g.dispose();
		File out = new File(dir);
		out.mkdirs();
		ImageIO.write(img, "png", new File(out, name));
	}

	private static void layout(Component c)
	{
		c.doLayout();
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				layout(child);
			}
		}
	}
}
