package com.codex.steward.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * Shown until the Steward has watched the bank long enough to call anything dusty: how many
 * days it has watched, against the threshold, as an hourglass of ash filling a gilt bar.
 */
class DustWatch extends JComponent
{
	private final long watched;
	private final int needed;

	DustWatch(long watched, int needed)
	{
		this.watched = watched;
		this.needed = needed;
		setOpaque(false);
		setPreferredSize(new Dimension(10, 62));
		setToolTipText("Items count as dust once they sit untouched for " + needed + " days, so the Steward must watch that long first");
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, h, Theme.CARD));
		g.fill(shape);
		g.setColor(Theme.STROKE_SOFT);
		g.draw(shape);
		g.setClip(shape);
		g.setPaint(new GradientPaint(0, 0, Theme.DUST, 0, h, Theme.alpha(Theme.DUST, 90)));
		g.fillRect(0, 0, 3, h);
		g.setClip(null);

		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		Ui.shadowed(g, "Watching for dust", 12, 8 + fm.getAscent(), Theme.DUST);
		String day = "day " + Math.min(watched, needed) + " of " + needed;
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(day, w - small.stringWidth(day) - 10, 8 + fm.getAscent());

		int bx = 12;
		int by = 30;
		int bw = w - 22;
		RoundRectangle2D track = new RoundRectangle2D.Float(bx, by, bw, 6, 6, 6);
		g.setColor(Theme.INK_DEEP);
		g.fill(track);
		double share = Math.max(0, Math.min(1, watched / (double) needed));
		float fill = (float) Math.max(4, bw * share);
		g.setPaint(new GradientPaint(bx, 0, Theme.mix(Theme.DUST, Theme.INK, 0.5), bx + bw, 0, Theme.GOLD));
		g.fill(new RoundRectangle2D.Float(bx, by, fill, 6, 6, 6));
		g.setColor(Theme.STROKE_SOFT);
		g.draw(track);

		g.setColor(Theme.TEXT_FAINT);
		long left = Math.max(0, needed - watched);
		g.drawString(Ui.clip(left == 1 ? "First dust can show tomorrow." : "First dust can show in " + left + " days.", small, bw), bx, h - 9);
		g.dispose();
	}
}
