package com.codex.steward.overlay;

import com.codex.steward.core.Advice;
import com.codex.steward.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.Map;
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

	public BankMarks()
	{
		showOnBank();
	}

	public void setMarks(Map<Integer, Advice.Verdict> marks)
	{
		this.marks = marks;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem item)
	{
		if (!enabled)
		{
			return;
		}
		Advice.Verdict verdict = marks.get(itemId);
		if (verdict == null)
		{
			return;
		}
		Rectangle r = item.getCanvasBounds();
		Theme.smooth(g);
		Color color = Theme.verdict(verdict);
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(color, 70));
		g.draw(new RoundRectangle2D.Float(r.x + 0.5f, r.y + 0.5f, r.width - 1, r.height - 1, 6, 6));

		float cx = r.x + r.width - 5.5f;
		float cy = r.y + r.height - 5.5f;
		if (verdict == Advice.Verdict.SPENT)
		{
			Path2D diamond = new Path2D.Float();
			diamond.moveTo(cx, cy - 4.5f);
			diamond.lineTo(cx + 4.5f, cy);
			diamond.lineTo(cx, cy + 4.5f);
			diamond.lineTo(cx - 4.5f, cy);
			diamond.closePath();
			g.setColor(Theme.INK_DEEP);
			g.setStroke(new BasicStroke(2f));
			g.draw(diamond);
			g.setColor(Theme.GOLD_BRIGHT);
			g.fill(diamond);
		}
		else
		{
			Ellipse2D ring = new Ellipse2D.Float(cx - 3.5f, cy - 3.5f, 7, 7);
			g.setColor(Theme.INK_DEEP);
			g.setStroke(new BasicStroke(3f));
			g.draw(ring);
			g.setColor(color);
			g.setStroke(new BasicStroke(1.4f));
			g.draw(ring);
		}
	}
}
