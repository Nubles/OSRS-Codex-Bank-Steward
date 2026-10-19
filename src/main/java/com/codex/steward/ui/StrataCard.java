package com.codex.steward.ui;

import com.codex.steward.core.Strata;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import javax.swing.JComponent;

/**
 * The bank as a core sample: a column of rock, newest layer on top, each as tall as the kinds of
 * item that last moved in it, beside the share of the bank that has not moved in three months and
 * the items lying deepest, greyed with the dust they have gathered.
 */
class StrataCard extends JComponent
{
	/** Fresh at the top, darkening with age the way rock does with depth. */
	static final Color[] RAMP = {
		Theme.TEAL,
		Theme.GOLD_BRIGHT,
		Theme.GOLD,
		Theme.GOLD_DIM,
		Theme.mix(Theme.DUST, Theme.INK, 0.4),
		Theme.mix(Theme.DUST, Theme.INK, 0.62),
	};
	private static final int PAD = 12;
	private static final int TOP = 54;
	private static final int BAND_MIN = 16;
	private static final int CORE_H = 132;
	private static final int CORE_W = 30;
	private static final int WELL_W = 36;
	private static final int WELL_H = 32;
	private static final int PER_ROW = 5;

	private final Strata strata;
	private final ItemIcons icons;
	/** Handed over each time an icon is asked for, so a row waiting on a picture is told once. */
	private final Runnable repaint = this::repaint;
	private final IntFunction<String> names;
	private final int coreH;
	private final int vaultY;
	private final int height;

	StrataCard(Strata strata, ItemIcons icons, Map<Integer, String> names)
	{
		this.strata = strata;
		this.icons = icons;
		this.names = id -> names.getOrDefault(id, "Item " + id);
		int layers = strata.getLayers().size();
		coreH = Math.max(CORE_H, layers * BAND_MIN);
		int rows = (strata.getOldest().size() + PER_ROW - 1) / PER_ROW;
		vaultY = TOP + coreH + 16;
		height = vaultY + (rows == 0 ? 0 : 18 + rows * (WELL_H + 14)) + 26;
		setOpaque(false);
		setPreferredSize(new Dimension(10, height));
		setMinimumSize(new Dimension(10, height));
		StringBuilder tip = new StringBuilder("<html>Deepest in your bank:");
		for (Strata.Old o : strata.getOldest())
		{
			tip.append("<br>").append(Ui.escape(this.names.apply(o.getItemId()))).append(", ").append(age(o));
		}
		setToolTipText(strata.getOldest().isEmpty() ? null : tip.append("</html>").toString());
	}

	/** Band heights for the layers: every layer gets room for its label, the rest goes by count. */
	static int[] bands(List<Strata.Layer> layers, int total, int height)
	{
		int[] out = new int[layers.size()];
		if (layers.isEmpty())
		{
			return out;
		}
		int spare = height - BAND_MIN * layers.size();
		int used = 0;
		int counted = 0;
		for (Strata.Layer l : layers)
		{
			counted += l.getCount();
		}
		for (int i = 0; i < out.length; i++)
		{
			int share = counted == 0 ? 0 : (int) Math.floor(spare * (layers.get(i).getCount() / (double) counted));
			out[i] = BAND_MIN + Math.max(0, share);
			used += out[i];
		}
		// Rounding left over goes to the biggest layer, so the column is exactly its height.
		int big = 0;
		for (int i = 1; i < out.length; i++)
		{
			if (layers.get(i).getCount() > layers.get(big).getCount())
			{
				big = i;
			}
		}
		out[big] += height - used;
		return out;
	}

	private static String age(Strata.Old o)
	{
		long d = o.getDays();
		String text = d >= 365 ? (d / 365) + (d >= 730 ? " years" : " year") : d >= 60 ? (d / 30) + " months" : Format.days(d);
		return o.isAtLeast() ? text + " or more" : text;
	}

	/**
	 * A share as a whole percentage that never rounds to all or none when it is not: 199 of 200 is
	 * 99%, and 1 of 400 is under 1%.
	 */
	static String share(int part, int whole)
	{
		if (whole <= 0 || part <= 0)
		{
			return "0%";
		}
		if (part >= whole)
		{
			return "100%";
		}
		long pct = Math.round(100.0 * part / whole);
		return pct < 1 ? "<1%" : Math.min(99, pct) + "%";
	}

	/** The headline before three months are watched: how long, and the words after it. */
	static String[] watched(long days)
	{
		return days <= 0 ? new String[]{"Day 1", "of watching"} : new String[]{Format.days(days), "watched so far"};
	}

	/** The short age under an icon: "45d", "8mo", "2y", with a plus when it may be older. */
	static String shortAge(Strata.Old o)
	{
		long d = o.getDays();
		String text = d >= 365 ? (d / 365) + "y" : d >= 60 ? (d / 30) + "mo" : d + "d";
		return o.isAtLeast() ? text + "+" : text;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, height - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, height, Theme.CARD));
		g.fill(shape);
		g.setColor(Theme.STROKE_SOFT);
		g.draw(shape);
		g.setClip(shape);
		g.setPaint(new GradientPaint(0, 0, Theme.TEAL, 0, height, Theme.alpha(Theme.DUST, 90)));
		g.fillRect(0, 0, 3, height);
		g.setClip(null);

		// The heading, and the share of the bank that has lain still three months or more.
		g.setFont(Theme.body());
		FontMetrics body = g.getFontMetrics();
		Ui.shadowed(g, "Your bank in layers", PAD, 8 + body.getAscent(), Theme.GOLD);
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		String kinds = Format.amount(strata.getTotal()) + " items";
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(kinds, w - PAD - small.stringWidth(kinds), 8 + body.getAscent());
		int hy = 8 + body.getHeight() + 2;
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		String figure;
		String says;
		Color tint;
		if (strata.getWatched() >= Strata.DEEP)
		{
			int pct = (int) Math.round(100.0 * strata.getDeep() / Math.max(1, strata.getTotal()));
			figure = share(strata.getDeep(), strata.getTotal());
			says = "unmoved in 3 months";
			tint = pct >= 50 ? Theme.OUTCLASSED : Theme.DUST;
		}
		else
		{
			String[] watching = watched(strata.getWatched());
			figure = watching[0];
			says = watching[1];
			tint = Theme.TEAL;
		}
		Ui.shadowed(g, figure, PAD, hy + bold.getAscent(), tint);
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_MUTED);
		int fx = PAD + bold.stringWidth(figure) + 6;
		g.drawString(Ui.clip(says, small, w - fx - PAD), fx, hy + bold.getAscent());

		// The core sample.
		List<Strata.Layer> layers = strata.getLayers();
		int[] bands = bands(layers, strata.getTotal(), coreH);
		int cx = PAD;
		Shape core = new RoundRectangle2D.Float(cx, TOP, CORE_W, coreH, 8, 8);
		g.setColor(Theme.INK_DEEP);
		g.fill(core);
		Shape clip = g.getClip();
		g.clip(core);
		int y = TOP;
		int lx = cx + CORE_W + 10;
		int lw = w - lx - PAD;
		for (int i = 0; i < layers.size(); i++)
		{
			Strata.Layer layer = layers.get(i);
			Color c = RAMP[Math.min(RAMP.length - 1, i)];
			int bh = bands[i];
			g.setPaint(new GradientPaint(cx, y, c, cx + CORE_W, y + bh, Theme.mix(c, Theme.INK_DEEP, 0.45)));
			g.fillRect(cx, y, CORE_W, bh);
			// Sediment: a few darker grains, the same every time for the same layer.
			g.setColor(Theme.alpha(Theme.INK_DEEP, 70));
			for (int k = 0; k < bh / 5; k++)
			{
				int gx = cx + 3 + (int) ((k * 37L + i * 11L) % (CORE_W - 6));
				int gy = y + 2 + (int) ((k * 13L + i * 7L) % Math.max(1, bh - 4));
				g.fillRect(gx, gy, 2, 1);
			}
			if (i > 0)
			{
				g.setColor(Theme.alpha(Theme.INK_DEEP, 160));
				g.drawLine(cx, y, cx + CORE_W, y);
			}
			y += bh;
		}
		if (!layers.isEmpty() && layers.get(layers.size() - 1).isOpen())
		{
			// The deepest layer watching has reached may run deeper: it fades out rather than ends.
			g.setPaint(new GradientPaint(0, TOP + coreH - 14, Theme.alpha(Theme.INK_DEEP, 0), 0, TOP + coreH, Theme.INK_DEEP));
			g.fillRect(cx, TOP + coreH - 14, CORE_W, 14);
		}
		g.setClip(clip);
		g.setColor(Theme.alpha(Theme.GOLD, 90));
		g.draw(new RoundRectangle2D.Float(cx - 0.5f, TOP - 0.5f, CORE_W + 1, coreH + 1, 8, 8));

		// Each layer's name and count beside its band.
		y = TOP;
		for (int i = 0; i < layers.size(); i++)
		{
			Strata.Layer layer = layers.get(i);
			int bh = bands[i];
			int base = y + bh / 2 + small.getAscent() / 2 - 1;
			Color c = RAMP[Math.min(RAMP.length - 1, i)];
			String count = Format.amount(layer.getCount());
			int cw = small.stringWidth(count);
			String name = layer.getName();
			g.setColor(layer.getCount() == 0 ? Theme.TEXT_FAINT : Theme.TEXT);
			g.drawString(Ui.clip(name, small, lw - cw - 8), lx, base);
			Ui.shadowed(g, count, w - PAD - cw, base, i >= 4 ? Theme.TEXT_MUTED : c);
			// A hairline from the band to its label, so a thin band still points at its own.
			g.setColor(Theme.alpha(c, 90));
			g.drawLine(cx + CORE_W + 2, y + bh / 2, lx - 3, y + bh / 2);
			y += bh;
		}

		// The deepest items, greyed by their dust.
		List<Strata.Old> oldest = strata.getOldest();
		if (!oldest.isEmpty())
		{
			SectionHeader.paint(g, "Deepest in the vault", null, PAD, vaultY, w - PAD * 2, 16);
			int gap = (w - PAD * 2 - PER_ROW * WELL_W) / Math.max(1, PER_ROW - 1);
			for (int i = 0; i < oldest.size(); i++)
			{
				Strata.Old o = oldest.get(i);
				int wx = PAD + (i % PER_ROW) * (WELL_W + gap);
				int wy = vaultY + 20 + (i / PER_ROW) * (WELL_H + 14);
				RoundRectangle2D well = new RoundRectangle2D.Float(wx + 0.5f, wy + 0.5f, WELL_W - 1, WELL_H - 1, 7, 7);
				g.setPaint(new GradientPaint(0, wy, Theme.INK_DEEP, 0, wy + WELL_H, Theme.mix(Theme.INK_DEEP, Theme.CARD, 0.6)));
				g.fill(well);
				g.setColor(Theme.STROKE_SOFT);
				g.draw(well);
				BufferedImage icon = icons == null ? null : icons.icon(o.getItemId(), repaint);
				if (icon != null)
				{
					Composite was = g.getComposite();
					// Older lies dimmer: a year or more is half there.
					float dust = (float) Math.min(0.5, o.getDays() / 730.0);
					g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f - dust));
					g.drawImage(icon, wx + (WELL_W - icon.getWidth()) / 2, wy + (WELL_H - icon.getHeight()) / 2, null);
					g.setComposite(was);
				}
				g.setPaint(new GradientPaint(0, wy, Theme.alpha(Theme.DUST, 0), 0, wy + WELL_H, Theme.alpha(Theme.DUST, (int) Math.min(70, o.getDays() / 4))));
				g.fill(well);
				String a = shortAge(o);
				g.setColor(Theme.TEXT_FAINT);
				g.drawString(a, wx + (WELL_W - small.stringWidth(a)) / 2, wy + WELL_H + small.getAscent() + 1);
			}
		}

		g.setColor(Theme.TEXT_FAINT);
		// Ages are only known back to the first look, so the foot says how far that is.
		String foot = strata.getWatched() <= 0 ? "Watching began today" : "Watching for " + Format.days(strata.getWatched());
		g.drawString(Ui.clip(foot, small, w - PAD * 2), PAD, height - 9);
		g.dispose();
	}
}
