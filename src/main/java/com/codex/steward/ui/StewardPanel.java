package com.codex.steward.ui;

import com.codex.steward.core.Advice;
import com.codex.steward.core.Reckoning;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/**
 * The Steward's side panel: the crest, then three pages. Clear out lists what can go, Needed
 * lists what unfinished quests and diaries still hold back, and Kept lists what the player chose
 * to keep off the list.
 */
public class StewardPanel extends PluginPanel
{
	static final int WIDTH = 200;
	private static final String[] TABS = {"clear", "needed", "kept"};
	private static final int ROWS = 60;

	/** What the panel has to show. */
	public enum State
	{
		LOGGED_OUT,
		WAITING_FOR_BANK,
		READY
	}

	/** What the panel asks of the plugin. */
	public interface Actions
	{
		void keep(int itemId, boolean kept);
	}

	private final ItemIcons icons;
	private final Actions actions;
	private final Crest crest = new Crest();
	private final Segmented tabs = new Segmented(true, "Clear out", "Needed", "Kept");
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane clear = scroller();
	private final JScrollPane needed = scroller();
	private final JScrollPane kept = scroller();

	private Reckoning reckoning;
	private Map<Integer, String> names = Collections.emptyMap();
	private State state = State.LOGGED_OUT;
	private boolean alch;

	public StewardPanel(ItemIcons icons, Actions actions)
	{
		super(false);
		this.icons = icons;
		this.actions = actions;
		setLayout(new BorderLayout());
		setBackground(Theme.INK);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(crest, BorderLayout.NORTH);
		JPanel tabHolder = new JPanel(new BorderLayout());
		tabHolder.setOpaque(false);
		tabHolder.setBorder(new EmptyBorder(8, 8, 2, 8));
		tabHolder.add(tabs, BorderLayout.CENTER);
		north.add(tabHolder, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);

		content.setOpaque(false);
		content.add(clear, TABS[0]);
		content.add(needed, TABS[1]);
		content.add(kept, TABS[2]);
		add(content, BorderLayout.CENTER);
		tabs.onChange(this::showTab);
		rebuild();
	}

	private static JScrollPane scroller()
	{
		JScrollPane scroll = new JScrollPane();
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		return scroll;
	}

	/**
	 * Called on the Swing thread whenever the reckoning changes.
	 *
	 * @param reckoning the latest reckoning, or null when no bank is known
	 * @param names item names for every item the reckoning mentions
	 * @param alch values are high alchemy prices rather than the Grand Exchange's
	 */
	public void update(Reckoning reckoning, Map<Integer, String> names, String player, State state, boolean alch)
	{
		this.reckoning = reckoning;
		this.names = names;
		this.state = state;
		this.alch = alch;
		updateCrest(player);
		rebuild();
	}

	/** Opens one tab, for previews and tests. */
	public void showTab(int index)
	{
		tabs.setSelected(index, false);
		cards.show(content, TABS[index]);
	}

	private void updateCrest(String player)
	{
		if (reckoning == null)
		{
			crest.update(player, null, state == State.LOGGED_OUT ? "Log in to take stock" : "Open your bank once", null, alch);
			return;
		}
		String status = state == State.LOGGED_OUT ? "Logged out, bank as last seen" : null;
		crest.update(player, reckoning.freedValue(), status, new String[]{
			String.valueOf(reckoning.freedSlots()),
			reckoning.getTasksDone() + "/" + reckoning.getTasksTotal(),
			String.valueOf(reckoning.getKept().size())}, alch);
	}

	private void rebuild()
	{
		rebuildClear();
		rebuildNeeded();
		rebuildKept();
	}

	private boolean waiting(StackPanel stack)
	{
		if (reckoning != null)
		{
			return false;
		}
		if (state == State.LOGGED_OUT)
		{
			stack.add(note("The Steward is waiting", "Log in and Codex Bank Steward goes through your bank: what you can sell or drop because its quests and diaries are done, and what has sat untouched for months."));
		}
		else
		{
			stack.add(note("Open your bank once", "The Steward takes stock the first time it sees your bank. After that it remembers the bank as last seen and notices what you use."));
		}
		return true;
	}

	private void rebuildClear()
	{
		StackPanel stack = new StackPanel(8, 8);
		if (waiting(stack))
		{
			replace(clear, stack);
			return;
		}
		List<Advice> spent = reckoning.getSpent();
		List<Advice> dust = reckoning.getDust();

		JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 6));
		tiles.setOpaque(false);
		tiles.add(new StatTile(Format.coins(value(spent)), "Done with", count(spent.size()), Theme.SPENT));
		tiles.add(new StatTile(Format.coins(value(dust)), "Dusty", count(dust.size()), Theme.DUST));
		stack.add(tiles);

		stack.add(new SectionHeader("Their work is done", spent.isEmpty() ? null : Format.coins(value(spent))));
		if (spent.isEmpty())
		{
			stack.add(note("Nothing spent yet", "Items bought or kept for a quest or diary land here once every quest and diary that needs them is done."));
		}
		else
		{
			stack.add(rows(spent, true));
			more(stack, spent);
		}

		stack.add(new SectionHeader("Gathering dust", dust.isEmpty() ? null : Format.coins(value(dust))));
		if (reckoning.getWatchedDays() < reckoning.getDustDays())
		{
			stack.add(new DustWatch(reckoning.getWatchedDays(), reckoning.getDustDays()));
		}
		else if (dust.isEmpty())
		{
			stack.add(note("No dust", "Everything in your bank has moved in the last " + reckoning.getDustDays() + " days."));
		}
		else
		{
			stack.add(rows(dust, true));
			more(stack, dust);
		}
		stack.add(Ui.wrapped("Click an item to keep it off the list. Nothing is ever sold or dropped for you.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 20));
		replace(clear, stack);
	}

	private void rebuildNeeded()
	{
		StackPanel stack = new StackPanel(8, 8);
		if (waiting(stack))
		{
			replace(needed, stack);
			return;
		}
		Map<String, List<Advice>> byTask = reckoning.getNeeded();
		Card head = new Card(new BorderLayout(0, 4));
		head.highlighted(true);
		Set<Integer> heldItems = new HashSet<>();
		long heldValue = 0;
		for (List<Advice> list : byTask.values())
		{
			for (Advice a : list)
			{
				if (heldItems.add(a.getItemId()))
				{
					heldValue += a.getValue();
				}
			}
		}
		int open = reckoning.getTasksTotal() - reckoning.getTasksDone();
		head.add(Ui.label(byTask.isEmpty() ? "Nothing held back" : "Keep for unfinished work", Theme.title(), Theme.GOLD_BRIGHT), BorderLayout.NORTH);
		head.add(Ui.wrapped(byTask.isEmpty()
			? "None of your " + open + " unfinished quests and diaries needs anything in your bank."
			: byTask.size() + " of your " + open + " unfinished quests and diaries need " + count(heldItems.size())
				+ (heldValue > 0 ? " worth " + Format.coins(heldValue) : "") + " from your bank. These are never listed to clear out.",
			Theme.small(), Theme.TEXT_MUTED, WIDTH - 30), BorderLayout.CENTER);
		stack.add(head);
		for (Map.Entry<String, List<Advice>> e : byTask.entrySet())
		{
			long held = value(e.getValue());
			stack.add(new SectionHeader(e.getKey(), held > 0 ? Format.coins(held) : null));
			stack.add(rows(e.getValue(), false, e.getKey()));
		}
		replace(needed, stack);
	}

	private void rebuildKept()
	{
		StackPanel stack = new StackPanel(8, 8);
		if (waiting(stack))
		{
			replace(kept, stack);
			return;
		}
		List<Advice> list = reckoning.getKept();
		if (list.isEmpty())
		{
			stack.add(note("Nothing kept aside", "Click anything on the Clear out page to keep it. Kept items stay off the list until you let them go."));
		}
		else
		{
			stack.add(new SectionHeader("Kept aside", count(list.size())));
			stack.add(rows(list, true));
		}
		replace(kept, stack);
	}

	private JPanel rows(List<Advice> list, boolean withAction)
	{
		return rows(list, withAction, null);
	}

	/** @param section the quest a Needed section is for, so a row names the others */
	private JPanel rows(List<Advice> list, boolean withAction, String section)
	{
		JPanel rows = new JPanel(new GridLayout(0, 1, 0, 1));
		rows.setOpaque(false);
		for (int i = 0; i < list.size() && i < ROWS; i++)
		{
			Advice a = list.get(i);
			String action = null;
			Runnable run = null;
			if (withAction && actions != null)
			{
				boolean isKept = a.getVerdict() == Advice.Verdict.KEPT;
				action = isKept ? "Let it go" : "Keep it";
				run = () -> actions.keep(a.getItemId(), !isKept);
			}
			rows.add(new AdviceRow(a, name(a.getItemId()), note(a, section), more(a), icons, action, run, alch));
		}
		return rows;
	}

	private void more(StackPanel stack, List<Advice> list)
	{
		if (list.size() > ROWS)
		{
			stack.add(Ui.label("and " + (list.size() - ROWS) + " more, worth " + Format.coins(value(list.subList(ROWS, list.size()))),
				Theme.small(), Theme.TEXT_FAINT));
		}
	}

	/**
	 * The Steward's reason, in a few words. The section header already says whether the work is
	 * done or still owed, so a row names the quest itself.
	 */
	private String note(Advice a, String section)
	{
		List<String> tasks = a.getTasks();
		switch (a.getVerdict())
		{
			case SPENT:
				return tasks.isEmpty() ? "Its quests are done" : tasks.get(0);
			case DUST:
				return "Untouched " + untouched(a);
			case NEEDED:
				// Listed under one quest already; name another that wants it too.
				for (String task : tasks)
				{
					if (!task.equals(section))
					{
						return "Also " + task;
					}
				}
				return a.isTradeable() ? "Only this quest" : "Untradeable";
			default:
				return a.getUntouchedDays() > 0 ? "Kept, untouched " + untouched(a) : "Kept";
		}
	}

	/** A count of further quests that is never clipped away, like "+2". */
	private static String more(Advice a)
	{
		int extra = a.getTasks().size() - (a.getVerdict() == Advice.Verdict.NEEDED ? 2 : 1);
		return extra > 0 && a.getVerdict() != Advice.Verdict.DUST && a.getVerdict() != Advice.Verdict.KEPT ? "+" + extra : null;
	}

	/** "134 days", or "210+ days" when it has not moved since the Steward began watching. */
	private String untouched(Advice a)
	{
		boolean sinceWatching = reckoning != null && a.getUntouchedDays() >= reckoning.getWatchedDays();
		long days = a.getUntouchedDays();
		return sinceWatching && days > 1 ? days + "+ days" : Format.days(days);
	}

	private String name(int itemId)
	{
		String name = names.get(itemId);
		return name == null ? "Item " + itemId : name;
	}

	private static long value(List<Advice> list)
	{
		long total = 0;
		for (Advice a : list)
		{
			total += a.getValue();
		}
		return total;
	}

	private static String count(int n)
	{
		return n == 1 ? "1 item" : n + " items";
	}

	static Card note(String title, String text)
	{
		Card card = new Card(new BorderLayout(0, 4));
		card.accent(Theme.GOLD_DIM);
		card.add(Ui.label(title, Theme.body(), Theme.GOLD), BorderLayout.NORTH);
		card.add(Ui.wrapped(text, Theme.small(), Theme.TEXT_MUTED, WIDTH - 30), BorderLayout.CENTER);
		return card;
	}

	private static void replace(JScrollPane scroll, JComponent view)
	{
		int position = scroll.getVerticalScrollBar().getValue();
		scroll.setViewportView(view);
		scroll.revalidate();
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(position));
	}
}
