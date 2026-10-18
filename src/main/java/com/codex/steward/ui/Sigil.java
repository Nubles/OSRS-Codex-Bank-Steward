package com.codex.steward.ui;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** The Steward's mark: a gilt bank key laid on the slant, with a teal stone set in its bow. */
public final class Sigil
{
	private Sigil()
	{
	}

	public static BufferedImage icon(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Theme.smooth(g);
		paint(g, 0, 0, size);
		g.dispose();
		return image;
	}

	/** Draws the mark in a size by size box. */
	public static void paint(Graphics2D g, float x, float y, float size)
	{
		float s = size / 16f;

		// The key is drawn upright on a 16 unit grid, then turned to lie on the slant.
		Area key = new Area(new Ellipse2D.Float(4.6f, 0.4f, 6.8f, 6.8f));
		key.subtract(new Area(new Ellipse2D.Float(6.3f, 2.1f, 3.4f, 3.4f)));
		key.add(new Area(new RoundRectangle2D.Float(7.1f, 6.4f, 1.8f, 9.2f, 1f, 1f)));
		key.add(new Area(new Rectangle2D.Float(8.6f, 11.6f, 2.6f, 1.4f)));
		key.add(new Area(new Rectangle2D.Float(8.6f, 13.9f, 2f, 1.5f)));
		key.add(new Area(new Rectangle2D.Float(6.2f, 7.4f, 3.6f, 0.9f)));

		AffineTransform at = new AffineTransform();
		at.translate(x, y);
		at.scale(s, s);
		at.rotate(Math.toRadians(-38), 8, 8);
		at.translate(0, 0.2);
		Area shape = key.createTransformedArea(at);

		g.setColor(Theme.alpha(Theme.INK_DEEP, 200));
		g.setStroke(new BasicStroke(Math.max(1.2f, 1.4f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(shape);
		g.setPaint(new GradientPaint(x, y, Theme.GOLD_BRIGHT, x + size, y + size, Theme.GOLD_DIM));
		g.fill(shape);
		g.setStroke(new BasicStroke(Math.max(0.5f, 0.45f * s)));
		g.setColor(Theme.alpha(Theme.INK_DEEP, 160));
		g.draw(shape);

		// The stone in the bow.
		float[] centre = {8f, 3.8f};
		at.transform(centre, 0, centre, 0, 1);
		float r = 1.45f * s;
		g.setColor(Theme.alpha(Theme.TEAL, 90));
		g.fill(new Ellipse2D.Float(centre[0] - r * 1.6f, centre[1] - r * 1.6f, r * 3.2f, r * 3.2f));
		g.setColor(Theme.mix(Theme.TEAL, Theme.TEXT, 0.35));
		g.fill(new Ellipse2D.Float(centre[0] - r, centre[1] - r, r * 2, r * 2));
	}
}
