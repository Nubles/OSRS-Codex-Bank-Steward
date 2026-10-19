package com.codex.steward.ui;

import com.codex.steward.core.ClearOut;
import com.codex.steward.core.SavingsGoal;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The guided clear-out, at the top of the Clear out page. Before it begins: what the list brings
 * by each route, with a button to begin. While it runs: how much is gone, as a gilt bar.
 */
final class ClearOutCard
{
	private ClearOutCard()
	{
	}

	/** The plan, before a clear-out begins. */
	static Card plan(ClearOut plan, Runnable begin)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.highlighted(true);
		card.add(heading("Clear-out plan", Format.coins(plan.totalGain()), Theme.GOLD_BRIGHT), BorderLayout.NORTH);
		card.add(new RouteLines(plan), BorderLayout.CENTER);
		JPanel south = new JPanel(new BorderLayout(0, 6));
		south.setOpaque(false);
		south.add(Ui.wrapped("Most valuable first. Each is ticked off once it leaves your bank and pack.",
			Theme.small(), Theme.TEXT_MUTED, StewardPanel.WIDTH - 30), BorderLayout.NORTH);
		if (begin != null)
		{
			south.add(buttons(new CodexButton("Begin the clear-out", true, begin)), BorderLayout.SOUTH);
		}
		card.add(south, BorderLayout.SOUTH);
		return card;
	}

	/**
	 * The upgrade Codex Gear Roadmap is saving for, and how far selling the list takes it: a bar
	 * with the coins in gold and what the list adds in teal.
	 */
	static Card goal(SavingsGoal goal)
	{
		Card card = new Card(new BorderLayout(0, 6));
		card.accent(Theme.TEAL);
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.add(Ui.label("Toward your goal", Theme.small(), Theme.TEXT_FAINT), BorderLayout.WEST);
		head.add(Ui.label(Format.coins(goal.getCost()), Theme.body(), Theme.GOLD), BorderLayout.EAST);
		JPanel top = new JPanel(new BorderLayout(0, 2));
		top.setOpaque(false);
		top.add(head, BorderLayout.NORTH);
		JComponent name = Ui.label(goal.getName(), Theme.title(), Theme.GOLD_BRIGHT);
		name.setToolTipText(goal.getName() + ", the savings goal in Codex Gear Roadmap");
		top.add(name, BorderLayout.SOUTH);
		card.add(top, BorderLayout.NORTH);
		card.add(new SplitBar(goal.coinsShare(), goal.withListShare()), BorderLayout.CENTER);
		int before = (int) Math.floor(goal.coinsShare() * 100);
		int after = (int) Math.floor(goal.withListShare() * 100);
		String text = goal.listBuysIt()
			? "Your coins cover " + before + "%. Selling this list covers the rest."
			: "Your coins cover " + before + "%. Selling this list takes you to " + after + "%, "
				+ Format.coins(goal.shortAfterList()) + " short.";
		card.add(Ui.wrapped(text, Theme.small(), Theme.TEXT_MUTED, StewardPanel.WIDTH - 30), BorderLayout.SOUTH);
		return card;
	}

	/**
	 * A clear-out under way.
	 *
	 * @param gone items cleared so far, of {@code total}
	 * @param freed what the cleared items brought, of {@code worth}
	 */
	static Card progress(int gone, int total, long freed, long worth, Runnable finish)
	{
		// With every item left off there is nothing to do: the clear-out reads as finished.
		boolean done = gone >= total;
		Card card = new Card(new BorderLayout(0, 6));
		card.highlighted(true);
		card.accent(done ? Theme.GREEN : Theme.GOLD);
		card.add(heading(done ? "Clear-out done" : "Clearing out", gone + " / " + total, done ? Theme.GREEN : Theme.GOLD_BRIGHT),
			BorderLayout.NORTH);
		card.add(new Bar(total == 0 ? 1 : gone / (double) total), BorderLayout.CENTER);
		JPanel south = new JPanel(new BorderLayout(0, 6));
		south.setOpaque(false);
		String slots = gone == 1 ? "1 slot" : gone + " slots";
		String text = total == 0
			? "Nothing is left to clear: everything on it is kept or needed again."
			: done
			? "Every item is gone: " + slots + (freed > 0 ? " and about " + Format.coins(freed) + " freed." : " freed.")
			: slots + " freed so far" + (worth > 0 ? ", " + Format.coins(freed) + " of " + Format.coins(worth) + "." : ".");
		south.add(Ui.wrapped(text, Theme.small(), Theme.TEXT_MUTED, StewardPanel.WIDTH - 30), BorderLayout.NORTH);
		if (finish != null)
		{
			south.add(buttons(new CodexButton(done ? "Close" : "Stop here", done, finish)), BorderLayout.SOUTH);
		}
		card.add(south, BorderLayout.SOUTH);
		return card;
	}

	private static JPanel heading(String title, String trailing, Color color)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(Ui.label(title, Theme.title(), color), BorderLayout.WEST);
		row.add(Ui.label(trailing, Theme.body(), Theme.GOLD), BorderLayout.EAST);
		return row;
	}

	private static JPanel buttons(JComponent button)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.add(button);
		return row;
	}

	/** One line for each way of clearing: a diamond in its colour, how many, and what it brings. */
	private static final class RouteLines extends JComponent
	{
		private static final int LINE = 17;
		private final List<String[]> lines = new ArrayList<>();
		private final List<Color> colors = new ArrayList<>();

		RouteLines(ClearOut plan)
		{
			for (ClearOut.Route r : ClearOut.Route.values())
			{
				int n = plan.count(r);
				if (n == 0)
				{
					continue;
				}
				String what = r == ClearOut.Route.SELL ? "Sell" : r == ClearOut.Route.ALCH ? "High alch" : "Drop";
				String value = r == ClearOut.Route.DROP ? (n == 1 ? "1 slot" : n + " slots") : Format.coins(plan.gain(r));
				lines.add(new String[]{what, n == 1 ? "1 item" : n + " items", value});
				colors.add(Theme.route(r));
			}
			setOpaque(false);
			setPreferredSize(new Dimension(10, LINE * lines.size()));
			setToolTipText("<html>Sales are after the Grand Exchange's 2% tax.<br>Alchs are after the nature rune.</html>");
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			g.setFont(Theme.body());
			FontMetrics fm = g.getFontMetrics();
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			for (int i = 0; i < lines.size(); i++)
			{
				String[] line = lines.get(i);
				Color c = colors.get(i);
				int mid = i * LINE + LINE / 2;
				Path2D diamond = new Path2D.Float();
				diamond.moveTo(4, mid - 4);
				diamond.lineTo(8, mid);
				diamond.lineTo(4, mid + 4);
				diamond.lineTo(0, mid);
				diamond.closePath();
				g.setColor(c);
				g.fill(diamond);
				int base = mid + fm.getAscent() / 2 - 1;
				g.setFont(Theme.body());
				Ui.shadowed(g, line[0], 14, base, Theme.TEXT);
				int vw = fm.stringWidth(line[2]);
				Ui.shadowed(g, line[2], w - vw, base, c);
				g.setFont(Theme.small());
				g.setColor(Theme.TEXT_FAINT);
				int cx = 14 + fm.stringWidth(line[0]) + 6;
				g.drawString(Ui.clip(line[1], small, w - vw - cx - 6), cx, base);
			}
			g.dispose();
		}
	}

	/** Two shares on one track: the coins held in gold, and what the list adds after them in teal. */
	private static final class SplitBar extends JComponent
	{
		private final double coins;
		private final double total;

		SplitBar(double coins, double total)
		{
			this.coins = Math.max(0, Math.min(1, coins));
			this.total = Math.max(this.coins, Math.min(1, total));
			setOpaque(false);
			setPreferredSize(new Dimension(10, 10));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			RoundRectangle2D track = new RoundRectangle2D.Float(0.5f, 2, w - 1, 6, 6, 6);
			g.setColor(Theme.INK_DEEP);
			g.fill(track);
			g.clip(track);
			float c = (float) ((w - 1) * coins);
			float t = (float) ((w - 1) * total);
			if (t > c)
			{
				Color end = total >= 1 ? Theme.GREEN : Theme.TEAL;
				g.setPaint(new GradientPaint(c, 0, Theme.alpha(Theme.TEAL, 120), Math.max(c + 1, t), 0, end));
				g.fill(new RoundRectangle2D.Float(0.5f, 2, t, 6, 6, 6));
			}
			if (c > 0)
			{
				g.setPaint(new GradientPaint(0, 0, Theme.GOLD_DIM, Math.max(1, c), 0, Theme.GOLD_BRIGHT));
				g.fill(new RoundRectangle2D.Float(0.5f, 2, Math.max(3, c), 6, 6, 6));
			}
			g.setClip(null);
			g.setColor(Theme.STROKE_SOFT);
			g.draw(track);
			g.dispose();
		}
	}

	/** A gilt progress bar that turns green when the clear-out is done. */
	private static final class Bar extends JComponent
	{
		private final double share;

		Bar(double share)
		{
			this.share = Math.max(0, Math.min(1, share));
			setOpaque(false);
			setPreferredSize(new Dimension(10, 10));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			RoundRectangle2D track = new RoundRectangle2D.Float(0.5f, 2, w - 1, 6, 6, 6);
			g.setColor(Theme.INK_DEEP);
			g.fill(track);
			if (share > 0)
			{
				float fill = (float) Math.max(6, (w - 1) * share);
				Color end = share >= 1 ? Theme.GREEN : Theme.GOLD_BRIGHT;
				g.setPaint(new GradientPaint(0, 0, Theme.GOLD_DIM, fill, 0, end));
				g.fill(new RoundRectangle2D.Float(0.5f, 2, fill, 6, 6, 6));
			}
			g.setColor(Theme.STROKE_SOFT);
			g.draw(track);
			g.dispose();
		}
	}
}
