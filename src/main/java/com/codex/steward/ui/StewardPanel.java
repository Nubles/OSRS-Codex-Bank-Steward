package com.codex.steward.ui;

import com.codex.steward.core.Advice;
import com.codex.steward.core.ClearOut;
import com.codex.steward.core.Memory;
import com.codex.steward.core.Reckoning;
import com.codex.steward.core.Shortfall;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

		/** Begins a guided clear-out of the list as it stands. */
		void startClearOut();

		/** Ends the clear-out under way. */
		void endClearOut();
	}

	/** The guided clear-out: the plan for the list now, and the clear-out under way, if any. */
	public static final class Clearing
	{
		final ClearOut plan;
		final Memory.Session session;
		final Set<Integer> cleared;

		/**
		 * @param plan the plan for the list as it stands, or null
		 * @param session the clear-out under way, or null
		 * @param cleared the session's items that are gone
		 */
		public Clearing(ClearOut plan, Memory.Session session, List<Integer> cleared)
		{
			this.plan = plan;
			this.session = session;
			this.cleared = cleared == null ? Collections.emptySet() : new HashSet<>(cleared);
		}
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
	private Clearing clearing = new Clearing(null, null, null);
	private Map<Integer, String> names = Collections.emptyMap();
	private State state = State.LOGGED_OUT;
	private boolean alch;
	private List<Shortfall.Goal> toGet = Collections.emptyList();
	private boolean iron;

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
	public void update(Reckoning reckoning, Clearing clearing, Map<Integer, String> names, String player, State state, boolean alch)
	{
		update(reckoning, clearing, Collections.emptyList(), names, player, state, alch, false);
	}

	/**
	 * @param toGet what each pinned Navigator goal still needs that the player does not own
	 * @param iron an iron account, which gathers rather than buys
	 */
	public void update(Reckoning reckoning, Clearing clearing, List<Shortfall.Goal> toGet, Map<Integer, String> names, String player,
		State state, boolean alch, boolean iron)
	{
		this.toGet = toGet == null ? Collections.emptyList() : toGet;
		this.iron = iron;
		this.reckoning = reckoning;
		this.clearing = clearing == null ? new Clearing(null, null, null) : clearing;
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

		if (clearing.session != null)
		{
			session(stack, clearing.session);
		}
		else
		{
			if (clearing.plan != null && !clearing.plan.isEmpty())
			{
				stack.add(ClearOutCard.plan(clearing.plan, actions == null ? null : actions::startClearOut));
			}
			lists(stack, spent, dust);
		}

		List<Advice> spared = reckoning.getSpared();
		List<Advice> outclassed = reckoning.getOutclassed();
		if (clearing.session == null && !outclassed.isEmpty())
		{
			stack.add(new SectionHeader("Outclassed tools", Format.coins(value(outclassed))));
			stack.add(rows(outclassed, true));
			more(stack, outclassed);
		}
		if (!spared.isEmpty())
		{
			stack.add(new SectionHeader("Kept for your plans", count(spared.size())));
			stack.add(rows(spared, false));
			more(stack, spared);
		}
		List<Advice> untradeable = reckoning.getUntradeableDust();
		if (!untradeable.isEmpty())
		{
			stack.add(new SectionHeader("Untradeable, untouched", count(untradeable.size())));
			Card warn = note("Look before you drop", "These cannot be bought back, and some may be rewards you cannot get again. They are never in the totals above.");
			warn.accent(Theme.EMBER);
			stack.add(warn);
			stack.add(rows(untradeable, true));
			more(stack, untradeable);
		}
		stack.add(Ui.wrapped("Click an item to keep it off the list. Nothing is ever sold or dropped for you.", Theme.small(), Theme.TEXT_FAINT, WIDTH - 20));
		replace(clear, stack);
	}

	/** The done-with and dusty lists, as the Steward sees them before a clear-out. */
	private void lists(StackPanel stack, List<Advice> spent, List<Advice> dust)
	{
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
	}

	/** A clear-out under way: its progress, what is left in order, what is gone, and what was left off. */
	private void session(StackPanel stack, Memory.Session session)
	{
		List<Memory.Planned> left = new ArrayList<>();
		List<Memory.Planned> gone = new ArrayList<>();
		List<Memory.Planned> off = new ArrayList<>();
		long freed = 0;
		long worth = 0;
		for (Memory.Planned p : session.getItems())
		{
			Advice now = reckoning.adviceFor(p.getItemId());
			if (clearing.cleared.contains(p.getItemId()))
			{
				gone.add(p);
				freed += p.getGain();
				worth += p.getGain();
			}
			else if (now != null && !ClearOut.listed(now))
			{
				off.add(p);
			}
			else
			{
				left.add(p);
				worth += p.getGain();
			}
		}
		stack.add(ClearOutCard.progress(gone.size(), gone.size() + left.size(), freed, worth,
			actions == null ? null : actions::endClearOut));
		if (!left.isEmpty())
		{
			long leftWorth = worth - freed;
			stack.add(new SectionHeader("Next up", leftWorth > 0 ? Format.coins(leftWorth) : null));
			stack.add(steps(left, false));
			if (left.size() > ROWS)
			{
				stack.add(Ui.label("and " + (left.size() - ROWS) + " more", Theme.small(), Theme.TEXT_FAINT));
			}
		}
		if (!gone.isEmpty())
		{
			stack.add(new SectionHeader("Gone", freed > 0 ? Format.coins(freed) : null));
			stack.add(steps(gone, true));
		}
		if (!off.isEmpty())
		{
			stack.add(new SectionHeader("Left off", count(off.size())));
			stack.add(steps(off, false));
		}
	}

	private JPanel steps(List<Memory.Planned> list, boolean gone)
	{
		JPanel rows = new JPanel(new GridLayout(0, 1, 0, 1));
		rows.setOpaque(false);
		for (int i = 0; i < list.size() && i < ROWS; i++)
		{
			rows.add(step(list.get(i), reckoning.adviceFor(list.get(i).getItemId()), gone));
		}
		return rows;
	}

	/** One item of a clear-out, with its route; it says so when the Steward has changed its mind. */
	private AdviceRow step(Memory.Planned p, Advice current, boolean gone)
	{
		ClearOut.Route route = p.getRoute();
		Advice a = current != null ? current
			: new Advice(p.getItemId(), p.getQuantity(), 0, route != ClearOut.Route.DROP, Advice.Verdict.SPENT, null, 0);
		String note;
		if (gone)
		{
			note = "Gone from your bank";
		}
		else if (current != null && (current.getVerdict() == Advice.Verdict.KEPT || current.getVerdict() == Advice.Verdict.NEEDED
			|| current.getVerdict() == Advice.Verdict.SPARED))
		{
			note = current.getVerdict() == Advice.Verdict.KEPT ? "You kept it since"
				: current.getVerdict() == Advice.Verdict.SPARED && !current.getClaims().isEmpty() ? current.getClaims().get(0).shortText()
				: "Needed again";
		}
		else
		{
			note = how(route, a);
		}
		boolean off = !gone && current != null && !ClearOut.listed(current);
		AdviceRow row = new AdviceRow(a, name(p.getItemId()), note, null, icons, null, null, alch)
			.amount(off ? "-" : route == ClearOut.Route.DROP ? "Drop" : Format.coins(p.getGain()));
		if (route != ClearOut.Route.DROP && !off)
		{
			row.route(route.getLabel().toLowerCase(), Theme.route(route));
		}
		return gone ? row.cleared() : row;
	}

	/** How to clear an item, in a few words. */
	private static String how(ClearOut.Route route, Advice a)
	{
		switch (route)
		{
			case SELL:
				return "After the 2% tax";
			case ALCH:
				return a.getQuantity() > 1 ? a.getQuantity() + " casts, less runes" : "One cast, less the rune";
			default:
				return a.isTradeable() ? "Worth nothing to sell" : "Cannot be traded";
		}
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
		int forGoals = 0;
		for (String task : byTask.keySet())
		{
			if (!reckoning.goalsOf(task).isEmpty())
			{
				forGoals++;
			}
		}
		head.add(Ui.label(byTask.isEmpty() ? "Nothing held back" : "Keep for unfinished work", Theme.title(), Theme.GOLD_BRIGHT), BorderLayout.NORTH);
		head.add(Ui.wrapped(byTask.isEmpty()
			? "None of your " + open + " unfinished quests and diaries needs anything in your bank."
			: byTask.size() + " of your " + open + " unfinished quests and diaries need " + count(heldItems.size())
				+ (heldValue > 0 ? " worth " + Format.coins(heldValue) : "") + " from your bank. These are never listed to clear out."
				+ (forGoals > 0 ? " Work toward your Navigator goals comes first." : ""),
			Theme.small(), Theme.TEXT_MUTED, WIDTH - 30), BorderLayout.CENTER);
		stack.add(head);
		for (Shortfall.Goal goal : toGet)
		{
			shortfall(stack, goal);
		}
		for (Map.Entry<String, List<Advice>> e : byTask.entrySet())
		{
			long held = value(e.getValue());
			stack.add(new SectionHeader(e.getKey(), held > 0 ? Format.coins(held) : null));
			List<String> goals = reckoning.goalsOf(e.getKey());
			if (!goals.isEmpty())
			{
				String line = goals.get(0).equals(e.getKey()) ? "Your Navigator goal" : "On the way to " + goals.get(0);
				stack.add(new GoalLine(goals.size() > 1 ? line + " +" + (goals.size() - 1) : line, goals));
			}
			stack.add(rows(e.getValue(), false, e.getKey()));
		}
		replace(needed, stack);
	}

	/** Rows shown per goal before the rest are summed up. */
	private static final int TO_GET_ROWS = 8;

	/** What a pinned goal still needs that the player does not own, most costly first. */
	private void shortfall(StackPanel stack, Shortfall.Goal goal)
	{
		List<Shortfall.Line> lines = goal.getLines();
		stack.add(new SectionHeader("To get: " + goal.getName(), iron || goal.getCost() == 0 ? count(lines.size() + goal.getMore()) : Format.coins(goal.getCost())));
		if (lines.isEmpty())
		{
			stack.add(note("You own what it needs", goal.getUntradeable() > 0
				? "Everything you can buy is in your bank or pack. Its quests hand you the rest."
				: "Everything its " + goal.getTasksLeft() + (goal.getTasksLeft() == 1 ? " quest or diary" : " quests and diaries") + " ask you to bring is in your bank or pack."));
			return;
		}
		JPanel rows = new JPanel(new GridLayout(0, 1, 0, 1));
		rows.setOpaque(false);
		for (int i = 0; i < lines.size() && i < TO_GET_ROWS; i++)
		{
			Shortfall.Line l = lines.get(i);
			Advice a = new Advice(l.getItemId(), l.getMissing(), l.getPrice(), true, Advice.Verdict.NEEDED, l.getTasks(), 0);
			String note = l.getHave() > 0 ? "Have " + l.getHave() + " of " + l.getNeeded()
				: l.getChoices() > 1 ? "Or one of " + (l.getChoices() - 1) + " others" : l.getTasks().get(0);
			String more = l.getTasks().size() > 1 && l.getHave() == 0 && l.getChoices() == 1 ? "+" + (l.getTasks().size() - 1) : null;
			AdviceRow row = new AdviceRow(a, name(l.getItemId()), note, more, icons, null, null, false);
			rows.add(row.amount(iron ? "Gather" : l.getCost() > 0 ? Format.coins(l.getCost()) : "-"));
		}
		stack.add(rows);
		int hidden = lines.size() - Math.min(lines.size(), TO_GET_ROWS) + goal.getMore();
		StringBuilder tail = new StringBuilder();
		if (hidden > 0)
		{
			long rest = 0;
			for (int i = TO_GET_ROWS; i < lines.size(); i++)
			{
				rest += lines.get(i).getCost();
			}
			tail.append("And ").append(hidden).append(hidden == 1 ? " more item" : " more items")
				.append(iron || rest == 0 ? "" : ", about " + Format.coins(rest)).append(". ");
		}
		if (goal.getUntradeable() > 0)
		{
			tail.append("Quest items its quests hand you are left out.");
		}
		if (tail.length() > 0)
		{
			stack.add(Ui.wrapped(tail.toString().trim(), Theme.small(), Theme.TEXT_FAINT, WIDTH - 20));
		}
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
			AdviceRow row = new AdviceRow(a, name(a.getItemId()), note(a, section), more(a), icons, action, run, alch);
			ClearOut.Step step = stepFor(a);
			if (step != null && step.getRoute() != ClearOut.Route.DROP)
			{
				row.route(step.getRoute().getLabel().toLowerCase(), Theme.route(step.getRoute()));
			}
			rows.add(row);
		}
		return rows;
	}

	/** The clear-out plan's step for an item on the done-with or dusty list. */
	private ClearOut.Step stepFor(Advice a)
	{
		if (clearing.plan == null || !ClearOut.listed(a))
		{
			return null;
		}
		for (ClearOut.Step step : clearing.plan.getSteps())
		{
			if (step.getAdvice().getItemId() == a.getItemId())
			{
				return step;
			}
		}
		return null;
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
				return a.getClaims().isEmpty() ? "Untouched " + untouched(a) : "STASH gear, " + untouched(a);
			case SPARED:
				return a.getClaims().isEmpty() ? "Kept for a plan" : a.getClaims().get(0).shortText();
			case OUTCLASSED:
				return name(a.getBetter()) + " is faster";
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
		if (a.getVerdict() == Advice.Verdict.SPARED)
		{
			int extra = a.getClaims().size() - 1;
			return extra > 0 ? "+" + extra : null;
		}
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
