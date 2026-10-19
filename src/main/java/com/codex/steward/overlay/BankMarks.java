package com.codex.steward.overlay;

import com.codex.steward.core.Advice;
import com.codex.steward.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Marks the Steward's list in the bank itself: a gilt diamond on items whose work is done and a
 * ring of ash on items gathering dust, each with a faint rim round the slot, so the list can be
 * cleared straight from the bank without looking back at the panel.
 */
public class BankMarks extends WidgetItemOverlay
{
	private volatile Map<Integer, Advice.Verdict> marks = Collections.emptyMap();
	private volatile boolean enabled = true;
	/** Empty placeholders worth releasing, by the placeholder's own item id as the bank shows it. */
	private volatile Set<Integer> placeholders = Collections.emptySet();

	public BankMarks()
	{
		showOnBank();
	}

	public void setMarks(Map<Integer, Advice.Verdict> marks)
	{
		this.marks = marks;
	}

	public void setPlaceholders(Set<Integer> placeholders)
	{
		this.placeholders = placeholders;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	// Everything a mark is drawn with, made once: the bank draws every slot every frame.
	private static final BasicStroke THIN = new BasicStroke(1f);
	private static final BasicStroke EDGE = new BasicStroke(2f);
	private static final BasicStroke RING_EDGE = new BasicStroke(3f);
	private static final BasicStroke RING = new BasicStroke(1.4f);
	private static final BasicStroke DASHED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0f);
	private static final BasicStroke CROSS_EDGE = new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final BasicStroke CROSS = new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final Color RELEASE_RIM = Theme.alpha(Theme.TEXT_MUTED, 110);
	private static final Map<Advice.Verdict, Color> RIMS = new EnumMap<>(Advice.Verdict.class);
	/** The badge shapes, centred on the origin. */
	private static final Path2D DIAMOND = new Path2D.Float();
	private static final Ellipse2D BADGE_RING = new Ellipse2D.Float(-3.5f, -3.5f, 7, 7);
	private static final Line2D CROSS_A = new Line2D.Float(-3, -3, 3, 3);
	private static final Line2D CROSS_B = new Line2D.Float(-3, 3, 3, -3);

	static
	{
		for (Advice.Verdict v : Advice.Verdict.values())
		{
			RIMS.put(v, Theme.alpha(Theme.verdict(v), 70));
		}
		DIAMOND.moveTo(0, -4.5f);
		DIAMOND.lineTo(4.5f, 0);
		DIAMOND.lineTo(0, 4.5f);
		DIAMOND.lineTo(-4.5f, 0);
		DIAMOND.closePath();
	}

	private final RoundRectangle2D rim = new RoundRectangle2D.Float();

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		if (!enabled)
		{
			return;
		}
		if (placeholders.contains(itemId))
		{
			releaseMark(g, item.getCanvasBounds());
			return;
		}
		Advice.Verdict verdict = marks.get(itemId);
		if (verdict == null)
		{
			return;
		}
		Rectangle r = item.getCanvasBounds();
		Theme.smooth(g);
		g.setStroke(THIN);
		g.setColor(RIMS.get(verdict));
		rim.setRoundRect(r.x + 0.5f, r.y + 0.5f, r.width - 1, r.height - 1, 6, 6);
		g.draw(rim);

		double cx = r.x + r.width - 5.5;
		double cy = r.y + r.height - 5.5;
		g.translate(cx, cy);
		if (verdict == Advice.Verdict.SPENT)
		{
			g.setColor(Theme.INK_DEEP);
			g.setStroke(EDGE);
			g.draw(DIAMOND);
			g.setColor(Theme.GOLD_BRIGHT);
			g.fill(DIAMOND);
		}
		else
		{
			g.setColor(Theme.INK_DEEP);
			g.setStroke(RING_EDGE);
			g.draw(BADGE_RING);
			g.setColor(Theme.verdict(verdict));
			g.setStroke(RING);
			g.draw(BADGE_RING);
		}
		g.translate(-cx, -cy);
	}

	/** A dashed ash rim and a small cross: an empty placeholder that can be released. */
	private void releaseMark(Graphics2D g, Rectangle r)
	{
		Theme.smooth(g);
		g.setColor(RELEASE_RIM);
		g.setStroke(DASHED);
		rim.setRoundRect(r.x + 0.5f, r.y + 0.5f, r.width - 1, r.height - 1, 6, 6);
		g.draw(rim);
		double cx = r.x + r.width - 5.5;
		double cy = r.y + r.height - 5.5;
		g.translate(cx, cy);
		g.setStroke(CROSS_EDGE);
		g.setColor(Theme.INK_DEEP);
		g.draw(CROSS_A);
		g.draw(CROSS_B);
		g.setStroke(CROSS);
		g.setColor(Theme.TEXT_MUTED);
		g.draw(CROSS_A);
		g.draw(CROSS_B);
		g.translate(-cx, -cy);
	}
}
