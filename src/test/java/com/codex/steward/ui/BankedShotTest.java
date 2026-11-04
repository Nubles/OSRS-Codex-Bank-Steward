package com.codex.steward.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.codex.steward.core.BankedXp;
import com.codex.steward.core.Catalog;
import com.codex.steward.core.Memory;
import com.codex.steward.core.Reckoning;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Paints the Banked page headless. With {@code -Dcodex.shots=<dir>} it saves the pictures, and
 * with {@code -Dcodex.icons=<dir>} it draws item pictures from {@code <id>.png} files there.
 */
public class BankedShotTest
{
	private static final int WIDTH = 217;

	private static Map<Integer, Long> bank()
	{
		Map<Integer, Long> b = new HashMap<>();
		b.put(ItemID.DRAGON_BONES, 1240L);
		b.put(ItemID.DRAGON_BONES_SUPERIOR, 85L);
		b.put(ItemID.BIG_BONES, 600L);
		b.put(ItemID.INFERNAL_ASHES, 140L);
		b.put(ItemID.UNIDENTIFIED_RANARR, 1204L);
		b.put(ItemID.UNIDENTIFIED_SNAPDRAGON, 420L);
		b.put(ItemID.UNIDENTIFIED_TOADFLAX, 300L);
		b.put(ItemID.UNIDENTIFIED_TORSTOL, 60L);
		b.put(ItemID.VIAL_WATER, 1500L);
		b.put(ItemID.SNAPE_GRASS, 860L);
		b.put(ItemID.RED_SPIDERS_EGGS, 420L);
		b.put(ItemID.RAW_SHARK, 2300L);
		b.put(ItemID.TBWT_RAW_KARAMBWAN, 4000L);
		b.put(ItemID.RAW_ANGLERFISH, 640L);
		b.put(ItemID.YEW_LOGS, 3400L);
		b.put(ItemID.MAGIC_LOGS, 900L);
		b.put(ItemID.BOW_STRING, 2000L);
		b.put(ItemID.UNCUT_DIAMOND, 120L);
		b.put(ItemID.UNCUT_DRAGONSTONE, 40L);
		b.put(ItemID.DRAGONHIDE_BLUE, 600L);
		b.put(ItemID.MOLTEN_GLASS, 2000L);
		b.put(ItemID.IRON_ORE, 1800L);
		b.put(ItemID.COAL, 2400L);
		b.put(ItemID.MITHRIL_ORE, 300L);
		b.put(ItemID.RUNITE_BAR, 40L);
		b.put(ItemID.PLANK_OAK, 1000L);
		b.put(ItemID.PLANK_MAHOGANY, 300L);
		return b;
	}

	private static Map<Integer, String> names()
	{
		Map<Integer, String> n = new HashMap<>();
		n.put(ItemID.DRAGON_BONES, "Dragon bones");
		n.put(ItemID.DRAGON_BONES_SUPERIOR, "Superior dragon bones");
		n.put(ItemID.BIG_BONES, "Big bones");
		n.put(ItemID.INFERNAL_ASHES, "Infernal ashes");
		n.put(ItemID.UNIDENTIFIED_RANARR, "Grimy ranarr weed");
		n.put(ItemID.UNIDENTIFIED_SNAPDRAGON, "Grimy snapdragon");
		n.put(ItemID.UNIDENTIFIED_TOADFLAX, "Grimy toadflax");
		n.put(ItemID.UNIDENTIFIED_TORSTOL, "Grimy torstol");
		n.put(ItemID.VIAL_WATER, "Vial of water");
		n.put(ItemID.SNAPE_GRASS, "Snape grass");
		n.put(ItemID.RED_SPIDERS_EGGS, "Red spiders' eggs");
		n.put(ItemID.CRUSHED_BIRD_NEST, "Crushed nest");
		n.put(ItemID.TOADS_LEGS, "Toad's legs");
		n.put(ItemID.RAW_SHARK, "Raw shark");
		n.put(ItemID.TBWT_RAW_KARAMBWAN, "Raw karambwan");
		n.put(ItemID.RAW_ANGLERFISH, "Raw anglerfish");
		n.put(ItemID.YEW_LOGS, "Yew logs");
		n.put(ItemID.MAGIC_LOGS, "Magic logs");
		n.put(ItemID.BOW_STRING, "Bow string");
		n.put(ItemID.UNCUT_DIAMOND, "Uncut diamond");
		n.put(ItemID.UNCUT_DRAGONSTONE, "Uncut dragonstone");
		n.put(ItemID.DRAGONHIDE_BLUE, "Blue dragonhide");
		n.put(ItemID.MOLTEN_GLASS, "Molten glass");
		n.put(ItemID.IRON_ORE, "Iron ore");
		n.put(ItemID.COAL, "Coal");
		n.put(ItemID.MITHRIL_ORE, "Mithril ore");
		n.put(ItemID.RUNITE_BAR, "Runite bar");
		n.put(ItemID.PLANK_OAK, "Oak plank");
		n.put(ItemID.PLANK_MAHOGANY, "Mahogany plank");
		n.put(ItemID.POT_EMPTY, "Pot");
		n.put(ItemID.BUCKET_ECTOPLASM, "Bucket of slime");
		return n;
	}

	private static Map<Skill, Integer> levels()
	{
		Map<Skill, Integer> l = new EnumMap<>(Skill.class);
		l.put(Skill.PRAYER, 61);
		l.put(Skill.HERBLORE, 72);
		l.put(Skill.COOKING, 80);
		l.put(Skill.FLETCHING, 70);
		l.put(Skill.FIREMAKING, 66);
		l.put(Skill.CRAFTING, 63);
		l.put(Skill.SMITHING, 50);
		l.put(Skill.CONSTRUCTION, 55);
		return l;
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

	private static BankedXp.Report report(BankedXp.Method method, Map<Integer, Long> bank)
	{
		Map<Skill, Integer> lv = levels();
		return BankedXp.of(bank, s -> Experience.getXpForLevel(lv.getOrDefault(s, 1)) + 20_000, true, method);
	}

	@Test
	public void theBankedPagePaints() throws Exception
	{
		StackPanel stack = new StackPanel(8, 8);
		BankedXp.Report r = report(BankedXp.Method.GILDED_ALTAR, bank());
		BankedPage.build(stack, r, names(), icons());
		assertEquals(BankedXp.SKILLS.size(), r.getSkills().size());
		String text = text(stack);
		assertTrue(text, text.contains("Short"));
		shoot(stack, "banked-page.png", 0);
		shoot(stack, "banked-top.png", 760);

		StackPanel ecto = new StackPanel(8, 8);
		Map<Integer, Long> bones = new HashMap<>();
		bones.put(ItemID.DRAGON_BONES, 1240L);
		bones.put(ItemID.BIG_BONES, 600L);
		bones.put(ItemID.POT_EMPTY, 400L);
		bones.put(ItemID.BUCKET_ECTOPLASM, 250L);
		bones.put(ItemID.INFERNAL_ASHES, 140L);
		BankedPage.build(ecto, report(BankedXp.Method.ECTOFUNTUS, bones), names(), icons());
		assertTrue(text(ecto).contains("Bucket of slime"));
		shoot(ecto, "banked-ectofuntus.png", 0);

		StackPanel none = new StackPanel(8, 8);
		BankedPage.build(none, BankedXp.Report.EMPTY, names(), icons());
		shoot(none, "banked-empty.png", 0);
	}

	@Test
	public void theFifthTabFitsTheStrip() throws Exception
	{
		Map<Integer, Long> bank = bank();
		Memory memory = new Memory();
		memory.observeBank(bank, 100);
		Reckoning reckoning = Reckoning.of(new Catalog(new Catalog.Data()), memory, bank, Collections.emptySet(), new boolean[0],
			id -> 100, id -> true, new Reckoning.Rules(90, 14), 110);
		StewardPanel panel = new StewardPanel(icons(), null);
		panel.update(reckoning, null, Collections.emptyList(), names(), "Gilded Owl", StewardPanel.State.READY, false, false,
			new StewardPanel.Extras(null, null, null, null, null, null, null, report(BankedXp.Method.GILDED_ALTAR, bank)));
		panel.showTab(4);
		panel.setSize(225, 900);
		shoot(panel, "banked-panel.png", 900);
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

	private static void shoot(JComponent c, String name, int maxHeight) throws Exception
	{
		int width = c instanceof StewardPanel ? 225 : WIDTH;
		c.setSize(width, 10);
		layout(c);
		Dimension pref = c.getPreferredSize();
		int h = maxHeight > 0 ? (c instanceof StewardPanel ? maxHeight : Math.min(maxHeight, pref.height)) : pref.height;
		c.setSize(width, c instanceof StewardPanel ? h : pref.height);
		layout(c);
		layout(c);
		String dir = System.getProperty("codex.shots");
		if (dir == null)
		{
			return;
		}
		int scale = 2;
		BufferedImage img = new BufferedImage(width * scale, h * scale, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.scale(scale, scale);
		g.setColor(Theme.INK);
		g.fillRect(0, 0, width, h);
		g.setClip(0, 0, width, h);
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
