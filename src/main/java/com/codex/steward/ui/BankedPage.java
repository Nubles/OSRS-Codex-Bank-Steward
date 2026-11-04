package com.codex.steward.ui;

import com.codex.steward.core.Advice;
import com.codex.steward.core.BankedXp;
import com.codex.steward.core.BankedXp.Line;
import com.codex.steward.core.BankedXp.Method;
import com.codex.steward.core.BankedXp.Recipe;
import com.codex.steward.core.BankedXp.Skilled;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.api.Skill;

/**
 * The Banked page: what the bank, pack and worn items would give if processed, skill by skill.
 * A ledger of every skill with the levels it would climb leads, then a card for each skill with
 * its materials, what a short secondary holds back, the next recipe a level opens, and the time
 * at the wiki's typical rate.
 */
final class BankedPage
{
	/** Materials a skill card lists before the rest are counted. */
	static final int LINES = 6;
	private static final int WIDTH = StewardPanel.WIDTH;

	private BankedPage()
	{
	}

	static void build(StackPanel stack, BankedXp.Report report, Map<Integer, String> names, ItemIcons icons)
	{
		if (report.isEmpty())
		{
			stack.add(StewardPanel.note("Nothing banked to train with", "Herbs, raw fish, logs, gems, hides, ore, bars, planks, bones and ashes in your bank, pack or worn land here with the experience they hold."));
			return;
		}
		stack.add(new Ledger(report));
		for (Skilled s : report.getSkills())
		{
			stack.add(card(s, report, names, icons));
		}
		stack.add(Ui.wrapped("Counts your bank as last seen, your pack and what you wear, at the best recipe your level allows "
			+ "today. Logs count in Fletching and Firemaking alike, so use each once. Burnt food and failed smelts are not "
			+ "taken off. Times use the OSRS Wiki's typical rates and leave out what it gives no rate for.", Theme.small(),
			Theme.TEXT_FAINT, WIDTH - 20));
	}

	private static String name(Map<Integer, String> names, int id)
	{
		String n = names.get(id);
		return n == null ? "Item " + id : n;
	}

	private static String skillName(Skill s)
	{
		return s.getName();
	}

	private static Card card(Skilled s, BankedXp.Report report, Map<Integer, String> names, ItemIcons icons)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.accent(s.levels() > 0 ? Theme.GOLD : Theme.GOLD_DIM);
		card.add(new Climb(s, report.isLevelsKnown()), BorderLayout.NORTH);

		JPanel body = new JPanel(new BorderLayout(0, 6));
		body.setOpaque(false);
		JPanel rows = new JPanel(new GridLayout(0, 1, 0, 1));
		rows.setOpaque(false);
		List<Line> lines = s.getLines();
		for (int i = 0; i < lines.size() && i < LINES; i++)
		{
			rows.add(row(lines.get(i), s.getSkill(), names, icons));
		}
		body.add(rows, BorderLayout.NORTH);

		StackPanel notes = new StackPanel(4, 0);
		if (lines.size() > LINES)
		{
			double rest = 0;
			for (int i = LINES; i < lines.size(); i++)
			{
				rest += lines.get(i).getXp();
			}
			notes.add(Ui.label("and " + (lines.size() - LINES) + " more, " + Format.amount(Math.round(rest)) + " xp", Theme.small(),
				Theme.TEXT_FAINT));
		}
		if (s.getSkill() == Skill.PRAYER && !report.getPrayer().isEmpty())
		{
			notes.add(new Altars(report, s));
		}
		if (!s.getMissing().isEmpty())
		{
			notes.add(heldBack(s, names));
		}
		String next = next(s, names);
		if (next != null)
		{
			notes.add(Ui.wrapped(next, Theme.small(), Theme.TEAL, WIDTH - 40));
		}
		String time = time(s);
		if (time != null)
		{
			notes.add(Ui.wrapped(time, Theme.small(), Theme.TEXT_MUTED, WIDTH - 40));
		}
		body.add(notes, BorderLayout.CENTER);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	/** One material: what it is made into, and the experience it holds. */
	private static AdviceRow row(Line l, Skill skill, Map<Integer, String> names, ItemIcons icons)
	{
		Recipe main = l.main();
		Advice a = new Advice(l.getItemId(), l.getCount(), 0, true, Advice.Verdict.DUST, null, 0);
		String note;
		Color accent = Theme.GOLD;
		String more = null;
		if (main == null)
		{
			Recipe best = l.getBest();
			if (best == null)
			{
				note = "Needs " + l.getNeedsLevel() + " " + skillName(skill);
				accent = Theme.TEXT_MUTED;
			}
			else
			{
				note = shortest(l, names);
				accent = Theme.EMBER;
			}
		}
		else if (l.getHeldBack() > 0 && !l.getMissing().isEmpty())
		{
			// What holds it back says more than the recipe: the tooltip has both.
			note = shortest(l, names);
			accent = Theme.EMBER;
		}
		else
		{
			note = main.getName();
			if (l.getUses().size() > 1)
			{
				more = "+" + (l.getUses().size() - 1);
			}
		}
		AdviceRow row = new AdviceRow(a, name(names, l.getItemId()), note, more, icons, null, null, false)
			.accent(accent).amount(l.getXp() > 0 ? Format.amount(Math.round(l.getXp())) : "-");
		if (main == null)
		{
			row.ghost();
		}
		row.setToolTipText(tooltip(l, skill, names));
		return row;
	}

	/** The secondary that holds a material back most: "Short 990 Bucket of slime". */
	private static String shortest(Line l, Map<Integer, String> names)
	{
		Map.Entry<Integer, Long> most = null;
		for (Map.Entry<Integer, Long> e : l.getMissing().entrySet())
		{
			if (most == null || e.getValue() > most.getValue())
			{
				most = e;
			}
		}
		return most == null ? "Short of secondaries" : "Short " + Format.quantity(most.getValue()) + " " + name(names, most.getKey());
	}

	private static String tooltip(Line l, Skill skill, Map<Integer, String> names)
	{
		StringBuilder tip = new StringBuilder("<html>").append(Ui.escape(name(names, l.getItemId()))).append(" x ")
			.append(Format.quantity(l.getCount()));
		for (BankedXp.Use u : l.getUses())
		{
			Recipe r = u.getRecipe();
			tip.append("<br>").append(Format.quantity(u.getUnits())).append(" to ").append(Ui.escape(steps(r)))
				.append(": ").append(xpEach(r.getXp())).append(" xp each");
			if (!r.getNeeds().isEmpty())
			{
				tip.append(", with ").append(Ui.escape(needs(r, names)));
			}
		}
		if (l.idle() > 0)
		{
			tip.append("<br>").append(Format.quantity(l.idle())).append(" left as they are");
		}
		if (!l.getMissing().isEmpty())
		{
			tip.append("<br>For all of it as ").append(Ui.escape(l.getBest().getName())).append(": ")
				.append(Ui.escape(shortList(l.getMissing(), names, 3))).append(" more, +")
				.append(Format.amount(Math.round(l.getHeldBack()))).append(" xp");
		}
		if (l.getNext() != null)
		{
			tip.append("<br>At ").append(l.getNext().getLevel()).append(' ').append(skillName(skill)).append(": ")
				.append(Ui.escape(l.getNext().getName())).append(", ").append(xpEach(l.getNext().getXp())).append(" xp each");
		}
		return tip.append("</html>").toString();
	}

	/** "Clean Ranarr, Ranarr potion (unf), Prayer potion(3)" without the steps that give nothing. */
	private static String steps(Recipe r)
	{
		List<String> out = new ArrayList<>();
		for (BankedXp.Step s : r.getSteps())
		{
			if (s.getXp() > 0 || r.getSteps().size() == 1)
			{
				out.add(s.getName());
			}
		}
		return String.join(", then ", out);
	}

	private static String needs(Recipe r, Map<Integer, String> names)
	{
		List<String> out = new ArrayList<>();
		r.getNeeds().forEach((id, n) -> out.add((n > 1 ? n + " " : "") + name(names, id)));
		return String.join(" and ", out);
	}

	private static String xpEach(double xp)
	{
		return xp == Math.rint(xp) ? String.valueOf((long) xp) : String.format(Locale.ENGLISH, "%.1f", xp);
	}

	/** "340 Snape grass, 120 Vial of water" with at most this many named. */
	private static String shortList(Map<Integer, Long> missing, Map<Integer, String> names, int most)
	{
		List<String> out = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : missing.entrySet())
		{
			if (out.size() == most)
			{
				out.add("more");
				break;
			}
			out.add(Format.quantity(e.getValue()) + " " + name(names, e.getKey()));
		}
		return String.join(", ", out);
	}

	/** What the short secondaries hold back, skill-wide. */
	private static JComponent heldBack(Skilled s, Map<Integer, String> names)
	{
		String text = "Short " + shortList(s.getMissing(), names, 3) + ". With them: +" + Format.amount(Math.round(s.getHeldBack())) + " xp";
		if (s.getSkill() == Skill.PRAYER)
		{
			text += " at the Ectofuntus";
		}
		return Ui.wrapped(text, Theme.small(), Theme.EMBER, WIDTH - 40);
	}

	/** The nearest better recipe a level opens, across the skill's materials. */
	private static String next(Skilled s, Map<Integer, String> names)
	{
		Line pick = null;
		for (Line l : s.getLines())
		{
			if (l.getNext() != null && (pick == null || l.getNext().getLevel() < pick.getNext().getLevel()))
			{
				pick = l;
			}
		}
		if (pick == null)
		{
			return null;
		}
		Recipe n = pick.getNext();
		double gain = pick.getCount() * n.getXp() - pick.getXp();
		String what = s.getSkill() == Skill.PRAYER ? name(names, pick.getItemId()) + " at the " + n.getName().toLowerCase(Locale.ENGLISH)
			: name(names, pick.getItemId()) + " into " + n.getName().toLowerCase(Locale.ENGLISH);
		if (n.getName().equals("Bury") || n.getName().equals("Clean") || n.getName().equals("Burn"))
		{
			what = n.getName() + " " + name(names, pick.getItemId()).toLowerCase(Locale.ENGLISH);
		}
		return "At " + n.getLevel() + ": " + what + (gain > 0 ? ", +" + Format.amount(Math.round(gain)) + " xp" : "");
	}

	/** "About 3h 10m at 2,500 potions an hour (wiki). Not timed: cleaning herbs." */
	static String time(Skilled s)
	{
		String untimed = String.join(", ", s.getUntimed());
		if (s.getHours() > 0)
		{
			return "About " + hours(s.getHours()) + " at " + String.join(" and ", s.getRates()) + " (wiki)."
				+ (untimed.isEmpty() ? "" : " Not timed: " + untimed + ".");
		}
		return untimed.isEmpty() ? null : "The wiki gives no rate for " + untimed + ", so no time is given.";
	}

	static String hours(double h)
	{
		long minutes = Math.round(h * 60);
		if (minutes < 1)
		{
			return "a minute";
		}
		if (minutes < 60)
		{
			return minutes + "m";
		}
		return minutes / 60 + "h " + minutes % 60 + "m";
	}

	/** A skill's header: its name, the experience it holds, and the climb from level now to level reached. */
	static final class Climb extends JComponent
	{
		private final Skilled s;
		private final boolean known;

		Climb(Skilled s, boolean known)
		{
			this.s = s;
			this.known = known;
			setPreferredSize(new Dimension(10, 42));
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			String xp = "+" + Format.amount(Math.round(s.getXp())) + " xp";
			int base = bold.getAscent();
			Ui.shadowed(g, skillName(s.getSkill()), 0, base, Theme.GOLD_BRIGHT);
			Ui.shadowed(g, xp, w - bold.stringWidth(xp), base, s.getXp() > 0 ? Theme.GOLD : Theme.TEXT_FAINT);

			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			int y2 = base + 4 + small.getAscent();
			String climb;
			if (!known)
			{
				climb = "Log in to see the levels";
			}
			else if (s.levels() > 0)
			{
				climb = "Level " + s.levelNow() + " to " + s.levelAfter() + (s.levelAfter() < 99 ? ", " + Format.percent(s.towardNext()) + " to " + (s.levelAfter() + 1) : "");
			}
			else
			{
				climb = "Level " + s.levelNow() + (s.levelNow() < 99 ? ", " + Format.percent(s.towardNext()) + " to " + (s.levelNow() + 1) : "");
			}
			g.setColor(Theme.TEXT_MUTED);
			g.drawString(Ui.clip(climb, small, w - 30), 0, y2);
			if (known && s.levels() > 0)
			{
				String plus = "+" + s.levels();
				Ui.shadowed(g, plus, w - small.stringWidth(plus), y2, Theme.TEAL);
			}

			// The climb as a bar: what is had now in ink, what the bank adds in gold.
			int by = y2 + 5;
			int bh = 5;
			RoundRectangle2D track = new RoundRectangle2D.Float(0, by, w - 1, bh, bh, bh);
			g.setColor(Theme.INK_DEEP);
			g.fill(track);
			if (known)
			{
				double from = progress(s.getXpNow());
				double to = progress(s.xpAfter());
				int x0 = (int) Math.round(from * (w - 1));
				int x1 = (int) Math.max(x0 + (s.getXp() > 0 ? 2 : 0), Math.round(to * (w - 1)));
				g.setClip(track);
				g.setColor(Theme.mix(Theme.TEXT_FAINT, Theme.INK_DEEP, 0.2));
				g.fillRect(0, by, x0, bh);
				g.setPaint(new GradientPaint(x0, 0, Theme.GOLD_DIM, x1, 0, Theme.GOLD_BRIGHT));
				g.fillRect(x0, by, x1 - x0, bh);
				g.setClip(null);
			}
			g.setColor(Theme.STROKE_SOFT);
			g.draw(track);
			g.dispose();
		}
	}

	/**
	 * Where a skill stands on the road to 99, as a share of the experience 99 takes: the bars of
	 * the ledger and the cards read on one scale, so a few levels in the 90s look as long as they are.
	 */
	static double progress(long xp)
	{
		return Math.min(1, xp / (double) net.runelite.api.Experience.getXpForLevel(99));
	}

	/** Every skill on one tablet: level now, level reached, and the experience held. */
	static final class Ledger extends JComponent
	{
		private static final int ROW = 17;
		private final BankedXp.Report report;

		Ledger(BankedXp.Report report)
		{
			this.report = report;
			setPreferredSize(new Dimension(10, 40 + ROW * report.getSkills().size() + 6));
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
			g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, h, Theme.INK_DEEP));
			g.fill(shape);
			g.setColor(Theme.alpha(Theme.GOLD, 110));
			g.draw(shape);
			g.setColor(Theme.alpha(Theme.GOLD, 160));
			g.fillRect(Theme.RADIUS, 1, w - Theme.RADIUS * 2, 1);

			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			g.setColor(Theme.TEXT_MUTED);
			g.drawString("BANKED EXPERIENCE", 10, 6 + small.getAscent());
			Skilled top = report.biggest();
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			String headline;
			if (!report.isLevelsKnown() || top.levels() == 0)
			{
				double sum = 0;
				for (Skilled s : report.getSkills())
				{
					sum += s.getXp();
				}
				headline = Format.amount(Math.round(sum)) + " xp waiting";
			}
			else
			{
				headline = skillName(top.getSkill()) + " " + top.levelNow() + " → " + top.levelAfter();
			}
			Ui.shadowed(g, Ui.clip(headline, bold, w - 20), 10, 8 + small.getHeight() + bold.getAscent(), Theme.GOLD_BRIGHT);

			int y = 40;
			g.setFont(Theme.small());
			int nameW = 0;
			for (Skilled s : report.getSkills())
			{
				nameW = Math.max(nameW, small.stringWidth(skillName(s.getSkill())));
			}
			int barX = 10 + nameW + 6;
			int xpW = small.stringWidth("88.8M");
			int lvlW = small.stringWidth("99>99");
			int barEnd = w - 10 - xpW - 6 - lvlW - 6;
			for (Skilled s : report.getSkills())
			{
				int base = y + small.getAscent();
				boolean best = s == top && report.isLevelsKnown() && s.levels() > 0;
				g.setColor(best ? Theme.GOLD_BRIGHT : Theme.TEXT);
				g.drawString(skillName(s.getSkill()), 10, base);
				String xp = Format.amount(Math.round(s.getXp()));
				g.setColor(s.getXp() > 0 ? Theme.GOLD : Theme.TEXT_FAINT);
				g.drawString(xp, w - 10 - small.stringWidth(xp), base);
				String lv = report.isLevelsKnown() ? (s.levels() > 0 ? s.levelNow() + ">" + s.levelAfter() : String.valueOf(s.levelNow())) : "";
				g.setColor(s.levels() > 0 ? Theme.TEAL : Theme.TEXT_FAINT);
				g.drawString(lv.replace(">", "›"), barEnd + 6, base);
				// The bar: the level now in ink, the levels the bank adds in gold.
				int by = y + 4;
				int bw = Math.max(10, barEnd - barX);
				g.setColor(Theme.INK_DEEP);
				g.fillRect(barX, by, bw, 4);
				if (report.isLevelsKnown())
				{
					int x0 = barX + (int) Math.round(s.levelNow() / 99.0 * bw);
					int x1 = barX + (int) Math.round(s.levelAfter() / 99.0 * bw);
					g.setColor(Theme.mix(Theme.TEXT_FAINT, Theme.INK_DEEP, 0.2));
					g.fillRect(barX, by, x0 - barX, 4);
					if (x1 > x0)
					{
						g.setPaint(new GradientPaint(x0, 0, Theme.GOLD_DIM, x1, 0, Theme.GOLD_BRIGHT));
						g.fillRect(x0, by, x1 - x0, 4);
					}
				}
				y += ROW;
			}
			g.dispose();
		}
	}

	/** The four places bones can go, side by side: the experience and level each would give. */
	static final class Altars extends JComponent
	{
		private static final int ROW = 14;
		private final BankedXp.Report report;
		private final Skilled prayer;

		Altars(BankedXp.Report report, Skilled prayer)
		{
			this.report = report;
			this.prayer = prayer;
			setPreferredSize(new Dimension(10, 6 + ROW * Method.values().length + 14));
			setOpaque(false);
			setToolTipText("<html>Gilded altar: both incense burners lit.<br>Ectofuntus: a pot and a bucket of slime a bone."
				+ "<br>Chaos altar: in the Wilderness; half the offerings keep their bone.<br>Pick one in the Steward's settings.</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			g.setFont(Theme.small());
			FontMetrics fm = g.getFontMetrics();
			int y = 4;
			for (Method m : Method.values())
			{
				Double xp = report.getPrayer().get(m);
				if (xp == null)
				{
					continue;
				}
				boolean chosen = m == report.getMethod();
				int base = y + fm.getAscent();
				if (chosen)
				{
					g.setColor(Theme.alpha(Theme.GOLD, 28));
					g.fillRoundRect(0, y - 1, w, ROW, 6, 6);
				}
				g.setColor(chosen ? Theme.GOLD_BRIGHT : Theme.TEXT_MUTED);
				g.drawString((chosen ? "◆ " : "   ") + m.getLabel(), 2, base);
				String amount = Format.amount(Math.round(xp));
				String lv = report.isLevelsKnown() ? "lvl " + BankedXp.level(prayer.getXpNow() + Math.round(xp)) : "";
				g.setColor(chosen ? Theme.GOLD : Theme.TEXT_MUTED);
				g.drawString(amount, w - 4 - fm.stringWidth("lvl 99") - 8 - fm.stringWidth(amount), base);
				g.setColor(chosen ? Theme.TEAL : Theme.TEXT_FAINT);
				g.drawString(lv, w - 4 - fm.stringWidth(lv), base);
				y += ROW;
			}
			g.setColor(Theme.TEXT_FAINT);
			g.drawString("Ashes are only scattered.", 2, y + fm.getAscent() + 1);
			g.dispose();
		}
	}
}
