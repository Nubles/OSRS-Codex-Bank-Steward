package com.codex.steward.ui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/**
 * Tabs drawn as one engraved strip: the chosen segment is lit from below by a
 * gold glow, the rest sit quietly in the ink.
 */
public class Segmented extends JComponent
{
	private final String[] labels;
	private final boolean large;
	private final Animator slide = new Animator(this);
	private int selected;
	private int hover = -1;
	private IntConsumer listener;

	public Segmented(boolean large, String... labels)
	{
		this.labels = labels;
		this.large = large;
		setFont(large ? Theme.title() : Theme.small());
		int h = large ? 28 : 22;
		setPreferredSize(new Dimension(10, h));
		setMinimumSize(new Dimension(10, h));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int index = indexAt(e.getX());
				if (index != hover)
				{
					hover = index;
					repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = -1;
				repaint();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				setSelected(indexAt(e.getX()), true);
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	public void onChange(IntConsumer listener)
	{
		this.listener = listener;
	}

	public int getSelected()
	{
		return selected;
	}

	public void setSelected(int index, boolean notify)
	{
		if (index < 0 || index >= labels.length)
		{
			return;
		}
		boolean changed = index != selected;
		selected = index;
		slide.set(index, true);
		repaint();
		if (changed && notify && listener != null)
		{
			listener.accept(index);
		}
	}

	private int indexAt(int x)
	{
		float[] edges = edges(fitting(this::getFontMetrics), Math.max(1, getWidth()));
		for (int i = 0; i < labels.length; i++)
		{
			if (x < edges[i + 1])
			{
				return i;
			}
		}
		return labels.length - 1;
	}

	/**
	 * Where each segment starts, and the last one ends. Segments are equal while every label fits
	 * one; when one does not (five tabs in a narrow panel), each is as wide as its label and the
	 * room left over is shared out evenly.
	 */
	private float[] edges(java.awt.Font font, int w)
	{
		FontMetrics fm = getFontMetrics(font);
		float[] edges = new float[labels.length + 1];
		float seg = w / (float) labels.length;
		boolean fits = true;
		int sum = 0;
		for (String label : labels)
		{
			fits &= fm.stringWidth(label) <= seg - 8;
			sum += fm.stringWidth(label);
		}
		float spare = (w - sum) / (float) labels.length;
		for (int i = 0; i < labels.length; i++)
		{
			edges[i + 1] = edges[i] + (fits ? seg : fm.stringWidth(labels[i]) + spare);
		}
		edges[labels.length] = w;
		return edges;
	}

	/**
	 * The strip's own font when every label fits its segment, else the plain font, else the small
	 * one: four tabs in a narrow panel do not run into each other.
	 */
	private java.awt.Font fitting(java.util.function.Function<java.awt.Font, FontMetrics> metrics)
	{
		float seg = Math.max(1, getWidth()) / (float) labels.length;
		java.awt.Font[] fonts = large ? new java.awt.Font[]{getFont(), Theme.body(), Theme.small()} : new java.awt.Font[]{getFont()};
		for (java.awt.Font f : fonts)
		{
			FontMetrics fm = metrics.apply(f);
			boolean fits = true;
			for (String label : labels)
			{
				fits &= fm.stringWidth(label) <= seg - 8;
			}
			if (fits)
			{
				return f;
			}
		}
		// Nothing fits equal segments: the largest font whose labels fit side by side with room between.
		for (java.awt.Font f : fonts)
		{
			FontMetrics fm = metrics.apply(f);
			int sum = 0;
			for (String label : labels)
			{
				sum += fm.stringWidth(label) + 12;
			}
			if (sum <= getWidth())
			{
				return f;
			}
		}
		return fonts[fonts.length - 1];
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		java.awt.Font font = fitting(this::getFontMetrics);
		float[] edges = edges(font, w);

		RoundRectangle2D strip = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 8, 8);
		g.setColor(Theme.INK_DEEP);
		g.fill(strip);

		// The lit segment slides between edges as the animation runs through the indexes.
		double at = Math.max(0, Math.min(labels.length - 1, slide.get()));
		int lo = (int) Math.floor(at);
		int hi = Math.min(labels.length - 1, lo + 1);
		float t = (float) (at - lo);
		float x = edges[lo] + (edges[hi] - edges[lo]) * t;
		float seg = (edges[lo + 1] - edges[lo]) + ((edges[hi + 1] - edges[hi]) - (edges[lo + 1] - edges[lo])) * t;
		RoundRectangle2D lit = new RoundRectangle2D.Float(x + 2, 2, seg - 4, h - 4, 6, 6);
		g.setPaint(new GradientPaint(0, 2, Theme.CARD_TOP, 0, h, Theme.mix(Theme.CARD_TOP, Theme.GOLD_DIM, 0.35)));
		g.fill(lit);
		g.setColor(Theme.alpha(Theme.GOLD, 120));
		g.draw(lit);
		g.setColor(Theme.GOLD_BRIGHT);
		g.fillRect(Math.round(x + seg * 0.3f), h - 4, Math.round(seg * 0.4f), 1);

		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		for (int i = 0; i < labels.length; i++)
		{
			float sw = edges[i + 1] - edges[i];
			int tx = Math.round(edges[i] + (sw - fm.stringWidth(labels[i])) / 2);
			int ty = (h + fm.getAscent()) / 2 - (large ? 2 : 1);
			java.awt.Color color = i == selected ? Theme.GOLD_BRIGHT : i == hover ? Theme.TEXT : Theme.TEXT_MUTED;
			Ui.shadowed(g, labels[i], tx, ty, color);
		}
		g.setColor(Theme.STROKE_SOFT);
		g.draw(strip);
		g.dispose();
	}
}
