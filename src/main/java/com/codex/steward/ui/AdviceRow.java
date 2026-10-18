package com.codex.steward.ui;

import com.codex.steward.core.Advice;
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

	private final Advice advice;
	private final String name;
	private final String note;
	private final String more;
	private final boolean alch;
	private final ItemIcons icons;
	private boolean hover;

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
		for (String task : advice.getTasks())
		{
			tip.append("<br>").append(advice.getVerdict() == Advice.Verdict.NEEDED ? "Still for " : "Done: ").append(Ui.escape(task));
		}
		return tip.append("</html>").toString();
	}

	/** What the right of the row says: the sale value, or how the slot is freed when it cannot be sold. */
	private String amount()
	{
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
		Color accent = Theme.verdict(advice.getVerdict());

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
		BufferedImage icon = icons == null ? null : icons.icon(advice.getItemId(), this::repaint);
		if (icon != null)
		{
			g.drawImage(icon, wx + 2 + (WELL - icon.getWidth()) / 2, y + (WELL - icon.getHeight()) / 2 + 1, null);
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
		Color amountColor = !advice.isTradeable() ? Theme.TEXT_FAINT : advice.getVerdict() == Advice.Verdict.NEEDED ? Theme.TEXT_MUTED : Theme.GOLD;
		Ui.shadowed(g, amount, w - aw - 2, base, amountColor);
		Ui.shadowed(g, Ui.clip(name, fm, w - tx - aw - 10), tx, base, Theme.TEXT);

		if (note != null)
		{
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			g.setColor(Theme.mix(Theme.TEXT_FAINT, accent, 0.35));
			int room = w - tx - 2;
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
