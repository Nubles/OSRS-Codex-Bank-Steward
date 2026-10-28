package com.codex.steward.ui;

import com.codex.steward.core.Advice;
import com.codex.steward.core.Claim;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * One item from the bank: the item in a dark well like a bank slot with its count in the game's
 * own colours, its name and the Steward's reason under it, and on the right what it would sell
 * for. A thin bar down the left edge carries the verdict's colour. Clicking offers to keep the
 * item off the list, or to let it back on.
 */
class AdviceRow extends JComponent
{
	private static final int WELL = 32;
	/** Quests named in a row's tooltip before the rest are counted. */
	static final int TIP_TASKS = 8;

	private final Advice advice;
	private final String name;
	private final String note;
	private final String more;
	private final boolean alch;
	private final ItemIcons icons;
	/** Handed over each time an icon is asked for, so a row waiting on a picture is told once. */
	private final Runnable repaint = this::repaint;
	private boolean hover;
	private String route;
	private Color routeColor;
	private String amountText;
	private boolean cleared;
	private Color accent;
	private boolean ghost;

	/**
	 * @param more a short tail for the note that is never clipped, like "+2", or null
	 * @param action label for the click menu, or null for a row with no action
	 * @param onAction run when the menu item is chosen
	 * @param alch prices are high alchemy values
	 */
	AdviceRow(Advice advice, String name, String note, String more, ItemIcons icons, String action, Runnable onAction, boolean alch)
	{
		this.advice = advice;
		this.name = name;
		this.note = note;
		this.more = more;
		this.alch = alch;
		this.icons = icons;
		setOpaque(false);
		setPreferredSize(new Dimension(10, WELL + 6));
		setToolTipText(tooltip());
		if (action != null)
		{
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			JPopupMenu menu = new JPopupMenu();
			JMenuItem item = new JMenuItem(action);
			item.addActionListener(e -> onAction.run());
			menu.add(item);
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (contains(e.getPoint()))
					{
						menu.show(AdviceRow.this, e.getX(), e.getY());
					}
				}
			});
		}
	}

	/** A clear-out route under the amount, like "sell", in its colour. */
	AdviceRow route(String label, Color color)
	{
		route = label;
		routeColor = color;
		return this;
	}

	/** Shows this on the right in place of the stack's value, such as what a sale brings after tax. */
	AdviceRow amount(String text)
	{
		amountText = text;
		return this;
	}

	/** Draws the edge bar and note in this colour instead of the verdict's. */
	AdviceRow accent(Color color)
	{
		accent = color;
		return this;
	}

	/** Draws the item faded, as the bank draws an empty placeholder. */
	AdviceRow ghost()
	{
		ghost = true;
		return this;
	}

	/** Drawn faded with a tick: the clear-out has seen it go. */
	AdviceRow cleared()
	{
		cleared = true;
		return this;
	}

	private String tooltip()
	{
		StringBuilder tip = new StringBuilder("<html>").append(Ui.escape(name));
		if (advice.getQuantity() > 1)
		{
			tip.append(" x ").append(Format.quantity(advice.getQuantity()));
		}
		if (advice.isTradeable() && advice.getPrice() > 0)
		{
			tip.append("<br>").append(Format.coins(advice.getPrice())).append(alch ? " gp each to alch" : " gp each");
		}
		else if (!advice.isTradeable())
		{
			tip.append("<br>Cannot be traded");
		}
		List<String> tasks = advice.getTasks();
		for (int i = 0; i < tasks.size() && i < TIP_TASKS; i++)
		{
			tip.append("<br>").append(advice.getVerdict() == Advice.Verdict.NEEDED ? "Still for " : "Done: ").append(Ui.escape(tasks.get(i)));
		}
		if (tasks.size() > TIP_TASKS)
		{
			// A spade or coins is wanted by dozens of quests: a tooltip naming them all ran off the screen.
			tip.append("<br>and ").append(tasks.size() - TIP_TASKS).append(" more");
		}
		for (Claim claim : advice.getClaims())
		{
			tip.append("<br>").append(Ui.escape(claim.longText()));
		}
		return tip.append("</html>").toString();
	}

	/** What the right of the row says: the sale value, or how the slot is freed when it cannot be sold. */
	private String amount()
	{
		if (amountText != null)
		{
			return amountText;
		}
		if (!advice.isTradeable())
		{
			return advice.getVerdict() == Advice.Verdict.SPENT || advice.getVerdict() == Advice.Verdict.DUST ? "Drop" : "-";
		}
		return Format.coins(advice.getValue());
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		Color accent = this.accent != null ? this.accent : Theme.verdict(advice.getVerdict());
		if (cleared)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
		}

		if (hover)
		{
			g.setColor(Theme.alpha(Theme.GOLD, 18));
			g.fill(new RoundRectangle2D.Float(0, 0, w, h, 8, 8));
		}

		int y = 3;
		g.setPaint(new GradientPaint(0, y, accent, 0, y + WELL, Theme.alpha(accent, 60)));
		g.fillRect(0, y + 3, 2, WELL - 6);

		int wx = 5;
		RoundRectangle2D well = new RoundRectangle2D.Float(wx + 0.5f, y + 0.5f, WELL + 4, WELL - 1, 7, 7);
		g.setPaint(new GradientPaint(0, y, Theme.INK_DEEP, 0, y + WELL, Theme.mix(Theme.INK_DEEP, Theme.CARD, 0.6)));
		g.fill(well);
		g.setColor(hover ? Theme.alpha(Theme.GOLD, 90) : Theme.STROKE_SOFT);
		g.draw(well);
		BufferedImage icon = icons == null ? null : icons.icon(advice.getItemId(), repaint);
		if (icon != null)
		{
			Graphics2D ig = (Graphics2D) g.create();
			if (ghost)
			{
				ig.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, cleared ? 0.2f : 0.4f));
			}
			ig.drawImage(icon, wx + 2 + (WELL - icon.getWidth()) / 2, y + (WELL - icon.getHeight()) / 2 + 1, null);
			ig.dispose();
		}
		if (advice.getQuantity() > 1)
		{
			g.setFont(Theme.small());
			Ui.shadowed(g, Format.stack(advice.getQuantity()), wx + 2, y + 10, Format.stackColor(advice.getQuantity()));
		}

		int tx = wx + WELL + 12;
		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		String amount = amount();
		int aw = fm.stringWidth(amount);
		int base = y + fm.getAscent() + 1;
		Color amountColor = this.accent != null ? this.accent : !advice.isTradeable() ? Theme.TEXT_FAINT : advice.getVerdict() == Advice.Verdict.NEEDED ? Theme.TEXT_MUTED : Theme.GOLD;
		Ui.shadowed(g, amount, w - aw - 2, base, amountColor);
		Ui.shadowed(g, Ui.clip(name, fm, w - tx - aw - 10), tx, base, Theme.TEXT);

		g.setFont(Theme.small());
		FontMetrics routeFm = g.getFontMetrics();
		int routeW = 0;
		if (route != null)
		{
			routeW = routeFm.stringWidth(route) + 6;
			g.setColor(routeColor == null ? Theme.TEXT_MUTED : routeColor);
			g.drawString(route, w - routeW + 4, y + WELL - 5);
		}
		if (cleared)
		{
			// A gold tick over the well: this one is gone.
			g.setComposite(AlphaComposite.SrcOver);
			g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(Theme.GREEN);
			int cx = wx + WELL - 6;
			int cy = y + WELL - 8;
			g.drawPolyline(new int[]{cx - 6, cx - 2, cx + 6}, new int[]{cy, cy + 4, cy - 5}, 3);
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
		}

		if (note != null)
		{
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			g.setColor(Theme.mix(Theme.TEXT_FAINT, accent, 0.35));
			int room = w - tx - 2 - routeW;
			if (more != null)
			{
				int mw = small.stringWidth(more);
				room -= mw + 4;
				String shown = Ui.clip(note, small, room);
				g.drawString(shown, tx, y + WELL - 5);
				g.setColor(Theme.mix(Theme.TEXT_MUTED, accent, 0.5));
				g.drawString(more, tx + small.stringWidth(shown) + 4, y + WELL - 5);
			}
			else
			{
				g.drawString(Ui.clip(note, small, room), tx, y + WELL - 5);
			}
		}
		g.dispose();
	}
}
