package com.codex.steward.ui;

import com.codex.steward.core.Architect;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The designed bank in miniature: a strip of tab buttons like the bank's own, and the chosen tab
 * drawn eight items to a row as Bank Tags will lay it out, with each kit framed and named.
 */
class ArchitectCard extends JComponent
{
	static final int PAD = 10;
	static final int CELL = 22;
	static final int TAB = 22;
	/** Rows drawn before the rest of a long tab is summed up. */
	static final int MAX_ROWS = 14;
	private static final int STRIP_Y = 30;
	private static final int NAME_Y = STRIP_Y + TAB + 8;
	private static final int GRID_Y = NAME_Y + 34;

	private final Architect.Design design;
	private final int selected;
	private final Set<String> copied;
	private final ItemIcons icons;
	private final Map<Integer, String> names;
	private final Runnable repaint = this::repaint;
	private final int shownRows;
	private final int hiddenRows;
	private final int height;
	private int hoverTab = -1;

	/**
	 * @param selected the tab shown
	 * @param copied tags already copied for Bank Tags
	 * @param onSelect picks a tab by index; null when the strip cannot be clicked
	 */
	ArchitectCard(Architect.Design design, int selected, Set<String> copied, ItemIcons icons, Map<Integer, String> names, IntConsumer onSelect)
	{
		this.design = design;
		this.selected = Math.max(0, Math.min(design.getTabs().size() - 1, selected));
		this.copied = copied;
		this.icons = icons;
		this.names = names;
		Architect.Tab tab = tab();
		int rows = tab == null ? 0 : tab.rows() + (tab.getBlocks().isEmpty() ? 0 : 1);
		shownRows = Math.min(rows, MAX_ROWS);
		hiddenRows = rows - shownRows;
		height = GRID_Y + shownRows * CELL + (hiddenRows > 0 ? 18 : 4) + PAD;
		setOpaque(false);
		setPreferredSize(new Dimension(10, height));
		setMinimumSize(new Dimension(10, height));
		setToolTipText("");
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int i = tabAt(e.getX(), e.getY());
				if (i != hoverTab)
				{
					hoverTab = i;
					setCursor(i >= 0 && onSelect != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
					repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hoverTab = -1;
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				int i = tabAt(e.getX(), e.getY());
				if (onSelect != null && i >= 0 && SwingUtilities.isLeftMouseButton(e))
				{
					onSelect.accept(i);
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	private Architect.Tab tab()
	{
		List<Architect.Tab> tabs = design.getTabs();
		return tabs.isEmpty() ? null : tabs.get(selected);
	}

	/** What each tab holds and how it is laid out, in a few words. */
	static String describe(Architect.Purpose p)
	{
		switch (p)
		{
			case KITS:
				return "Each kit shaped like your inventory";
			case MELEE:
			case RANGED:
			case MAGIC:
				return "Weapons first, then head to feet";
			case SUPPLIES:
				return "Coins, food, potions by kind, runes";
			case TELEPORTS:
				return "Jewellery, then tablets and scrolls";
			case SKILLING:
				return "Tools, outfits, then resources";
			case QUESTS:
				return "Kept for unfinished quests, goals first";
			case KEEPSAKES:
				return "Untradeable things with no other home";
			default:
				return "On the clear-out list, most valuable first";
		}
	}

	private int stripStep(int w)
	{
		int n = Math.max(1, design.getTabs().size());
		return Math.min(TAB + 4, (w - PAD * 2) / n);
	}

	/** A tab button's size: as the bank's own when there is room, smaller when many tabs share the strip. */
	private int wellSize(int w)
	{
		return Math.min(TAB, stripStep(w) - 2);
	}

	private int gridX(int w)
	{
		return (w - Architect.COLUMNS * CELL) / 2;
	}

	/** The tab button under a point, or -1. */
	int tabAt(int x, int y)
	{
		if (y < STRIP_Y || y >= STRIP_Y + TAB)
		{
			return -1;
		}
		int step = stripStep(getWidth());
		int i = (x - PAD) / Math.max(1, step);
		return x >= PAD && i >= 0 && i < design.getTabs().size() && (x - PAD) % step < wellSize(getWidth()) ? i : -1;
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		int t = tabAt(e.getX(), e.getY());
		if (t >= 0)
		{
			Architect.Tab tab = design.getTabs().get(t);
			return tab.getPurpose().getLabel() + ": " + tab.items() + (tab.items() == 1 ? " item" : " items")
				+ (copied.contains(tab.getTag()) ? ", copied" : "");
		}
		Architect.Tab tab = tab();
		int gx = gridX(getWidth());
		if (tab == null || e.getY() < GRID_Y || e.getX() < gx || e.getX() >= gx + Architect.COLUMNS * CELL)
		{
			return null;
		}
		int row = (e.getY() - GRID_Y) / CELL;
		int col = (e.getX() - gx) / CELL;
		if (row >= shownRows)
		{
			return null;
		}
		int id = tab.itemAt(row * Architect.COLUMNS + col);
		return id > 0 ? names.getOrDefault(id, "Item " + id) : null;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		int w = getWidth();
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, height - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, height, Theme.CARD));
		g.fill(shape);
		g.setColor(Theme.alpha(Theme.GOLD, 70));
		g.draw(shape);
		g.setClip(shape);
		g.setPaint(new GradientPaint(0, 0, Theme.GOLD, 0, height, Theme.alpha(Theme.GOLD_DIM, 60)));
		g.fillRect(0, 0, 3, height);
		g.setClip(null);

		g.setFont(Theme.body());
		FontMetrics body = g.getFontMetrics();
		Ui.shadowed(g, "Your bank, redesigned", PAD, 8 + body.getAscent(), Theme.GOLD);
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int n = design.getTabs().size();
		String count = n == 1 ? "1 tab" : n + " tabs";
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(count, w - PAD - small.stringWidth(count), 8 + body.getAscent());

		strip(g, w);

		Architect.Tab tab = tab();
		if (tab == null)
		{
			g.dispose();
			return;
		}
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		Ui.shadowed(g, tab.getPurpose().getLabel(), PAD, NAME_Y + bold.getAscent(), Theme.GOLD_BRIGHT);
		g.setFont(Theme.small());
		String items = Format.amount(tab.items()) + (tab.items() == 1 ? " item" : " items");
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(items, w - PAD - small.stringWidth(items), NAME_Y + bold.getAscent());
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(Ui.clip(describe(tab.getPurpose()), small, w - PAD * 2), PAD, NAME_Y + bold.getHeight() + small.getAscent());

		grid(g, w, tab, small);
		g.dispose();
	}

	/** The tab buttons, as the bank draws its tabs: an item in a well, the open one lit. */
	private void strip(Graphics2D g, int w)
	{
		int step = stripStep(w);
		int size = wellSize(w);
		List<Architect.Tab> tabs = design.getTabs();
		for (int i = 0; i < tabs.size(); i++)
		{
			Architect.Tab t = tabs.get(i);
			int x = PAD + i * step;
			boolean on = i == selected;
			RoundRectangle2D well = new RoundRectangle2D.Float(x + 0.5f, STRIP_Y + 0.5f, size - 1, TAB - 1, 6, 6);
			g.setPaint(on ? new GradientPaint(0, STRIP_Y, Theme.mix(Theme.CARD_TOP, Theme.GOLD_DIM, 0.45), 0, STRIP_Y + TAB, Theme.CARD_TOP)
				: new GradientPaint(0, STRIP_Y, Theme.INK_DEEP, 0, STRIP_Y + TAB, Theme.mix(Theme.INK_DEEP, Theme.CARD, 0.5)));
			g.fill(well);
			g.setColor(on ? Theme.GOLD_BRIGHT : i == hoverTab ? Theme.GOLD_DIM : Theme.STROKE);
			g.draw(well);
			icon(g, t.icon(), x + 1, STRIP_Y + 1, size - 2, on ? 1f : 0.75f);
			if (copied.contains(t.getTag()))
			{
				tick(g, x + size - 9, STRIP_Y + TAB - 9);
			}
		}
		// The open tab's underline, as the bank marks it.
		int x = PAD + selected * step;
		g.setColor(Theme.GOLD_BRIGHT);
		g.fillRect(x + 4, STRIP_Y + TAB + 2, size - 8, 2);
	}

	/** A teal disc with a dark tick: this tab has been copied already. */
	private static void tick(Graphics2D g, int x, int y)
	{
		g.setColor(Theme.INK_DEEP);
		g.fillOval(x - 1, y - 1, 10, 10);
		g.setColor(Theme.TEAL);
		g.fillOval(x, y, 8, 8);
		g.setColor(Theme.INK_DEEP);
		Path2D p = new Path2D.Float();
		p.moveTo(x + 2f, y + 4f);
		p.lineTo(x + 3.5f, y + 5.8f);
		p.lineTo(x + 6.2f, y + 2.3f);
		g.setStroke(new java.awt.BasicStroke(1.3f));
		g.draw(p);
		g.setStroke(new java.awt.BasicStroke(1f));
	}

	private void grid(Graphics2D g, int w, Architect.Tab tab, FontMetrics small)
	{
		int gx = gridX(w);
		// The bank's dark ground, slot by slot.
		RoundRectangle2D ground = new RoundRectangle2D.Float(gx - 3, GRID_Y - 3, Architect.COLUMNS * CELL + 6, shownRows * CELL + 6, 8, 8);
		g.setColor(Theme.INK_DEEP);
		g.fill(ground);
		g.setColor(Theme.STROKE_SOFT);
		g.draw(ground);
		for (int row = 0; row < shownRows; row++)
		{
			for (int col = 0; col < Architect.COLUMNS; col++)
			{
				int x = gx + col * CELL;
				int y = GRID_Y + row * CELL;
				int id = tab.itemAt(row * Architect.COLUMNS + col);
				if (id > 0)
				{
					g.setColor(Theme.alpha(Theme.CARD, 200));
					g.fillRoundRect(x + 1, y + 1, CELL - 2, CELL - 2, 4, 4);
					icon(g, id, x + 1, y + 1, CELL - 2, 1f);
				}
				else
				{
					g.setColor(Theme.alpha(Theme.STROKE_SOFT, 90));
					g.fillRect(x + CELL / 2 - 1, y + CELL / 2 - 1, 2, 2);
				}
			}
		}
		// Each kit framed, its name under it.
		for (Architect.Block b : tab.getBlocks())
		{
			if (b.getRow() >= shownRows)
			{
				continue;
			}
			int x = gx + b.getColumn() * CELL;
			int y = GRID_Y + b.getRow() * CELL;
			int bh = Math.min(b.getHeight(), shownRows - b.getRow()) * CELL;
			g.setColor(Theme.alpha(Theme.GOLD, 120));
			g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, b.getWidth() * CELL - 1, bh - 1, 6, 6));
			int labelRow = b.getRow() + b.getHeight();
			if (labelRow < shownRows)
			{
				int ly = GRID_Y + labelRow * CELL + (CELL + small.getAscent()) / 2 - 1;
				Ui.shadowed(g, Ui.clip(b.getName(), small, b.getWidth() * CELL - 4), x + 3, ly, Theme.GOLD);
			}
		}
		if (hiddenRows > 0)
		{
			int y = GRID_Y + shownRows * CELL;
			g.setPaint(new GradientPaint(0, y - CELL, Theme.alpha(Theme.INK_DEEP, 0), 0, y + 2, Theme.INK_DEEP));
			g.fillRect(gx - 2, y - CELL, Architect.COLUMNS * CELL + 4, CELL + 2);
			String more = "and " + hiddenRows + (hiddenRows == 1 ? " more row" : " more rows");
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(more, gx, y + 6 + small.getAscent());
		}
	}

	private void icon(Graphics2D g, int id, int x, int y, int size, float alpha)
	{
		BufferedImage img = icons == null ? null : icons.icon(id, repaint);
		if (img == null)
		{
			return;
		}
		// Item pictures are 36 by 32: scaled to fit the slot, keeping their shape.
		double scale = Math.min(size / (double) img.getWidth(), size / (double) img.getHeight());
		int iw = (int) Math.round(img.getWidth() * scale);
		int ih = (int) Math.round(img.getHeight() * scale);
		java.awt.Composite was = g.getComposite();
		if (alpha < 1f)
		{
			g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
		}
		g.drawImage(img, x + (size - iw) / 2, y + (size - ih) / 2, iw, ih, null);
		g.setComposite(was);
	}

	/** The colour a moves count is told in: green when the bank is nearly there already. */
	static Color movesColor(int moves, int compared)
	{
		if (moves <= 0 || compared <= 0)
		{
			return Theme.GREEN;
		}
		double inPlace = 1 - moves / (double) compared;
		return Theme.progressColor(inPlace);
	}
}
