package com.codex.steward.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.List;
import javax.swing.JComponent;

/** A small pennant under a Needed heading: this quest is on the way to a goal pinned in Codex Navigator. */
class GoalLine extends JComponent
{
	private final String text;

	GoalLine(String text, List<String> goals)
	{
		this.text = text;
		setOpaque(false);
		setPreferredSize(new Dimension(10, 14));
		StringBuilder tip = new StringBuilder("<html>Pinned in Codex Navigator:");
		for (String goal : goals)
		{
			tip.append("<br>").append(Ui.escape(goal));
		}
		setToolTipText(tip.append("</html>").toString());
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int h = getHeight();
		// A pennant on a pole.
		g.setColor(Theme.alpha(Theme.TEAL, 200));
		g.fillRect(14, 2, 1, h - 3);
		Path2D flag = new Path2D.Float();
		flag.moveTo(15, 2);
		flag.lineTo(23, 5);
		flag.lineTo(15, 8);
		flag.closePath();
		g.fill(flag);
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		g.setColor(Theme.TEAL);
		g.drawString(Ui.clip(text, fm, getWidth() - 30), 28, (h + fm.getAscent()) / 2 - 1);
		g.dispose();
	}
}
