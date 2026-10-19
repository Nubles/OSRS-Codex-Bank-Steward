package com.codex.steward;

import com.codex.steward.core.Advice;
import com.codex.steward.core.BankSnapshot;
import com.codex.steward.core.Catalog;
import com.codex.steward.core.ClearOut;
import com.codex.steward.core.Memory;
import com.codex.steward.core.Plans;
import com.codex.steward.core.Reckoning;
import com.codex.steward.core.Task;
import com.codex.steward.overlay.BankMarks;
import com.codex.steward.ui.Format;
import com.codex.steward.ui.ItemIcons;
import com.codex.steward.ui.Sigil;
import com.codex.steward.ui.StewardPanel;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntToLongFunction;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Codex Bank Steward",
	description = "What in your bank is safe to sell or drop: items whose quests and diaries are done, and items untouched for months, with the gold and slots they free",
	tags = {"bank", "clean", "sell", "drop", "quest items", "diary", "space", "slots", "codex"}
)
public class CodexBankStewardPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(CodexBankStewardPlugin.class);
	private static final String MEMORY_KEY = "memory";
	private static final String DATA = "steward-data.json";
	/**
	 * Ticks between reading quest and diary progress while varbits are changing. Varbits change
	 * all the time in combat and skilling, and a read runs a client script for every quest.
	 */
	private static final int PROGRESS_TICKS = 25;
	/** Ticks between saves while something has changed. */
	private static final int SAVE_TICKS = 50;
	/** Ticks between fresh reckonings, so prices and the day stay current. */
	private static final int REFRESH_TICKS = 500;
	/**
	 * Ticks of quiet after a bank change before the shared snapshot is written, so a withdraw of
	 * many items in a row writes it once.
	 */
	private static final int SNAPSHOT_SETTLE_TICKS = 2;
	/** An unchanged bank's snapshot is written again after this long, so its time stays fresh. */
	private static final long SNAPSHOT_REFRESH_MS = 5 * 60_000L;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private CodexBankStewardConfig config;

	private final BankMarks marks = new BankMarks();
	private final Map<Integer, String> names = new HashMap<>();
	private final Map<Integer, Boolean> tradeable = new HashMap<>();
	private final Map<Integer, Integer> alchPrice = new HashMap<>();
	/** The account is an ironman of any kind, read with quest progress. */
	private boolean ironman;
	private final Map<String, Quest> quests = new HashMap<>();

	private Catalog catalog;
	private StewardPanel panel;
	private NavigationButton navButton;

	private String profile;
	private Memory memory;
	private Reckoning reckoning;
	private ClearOut plan;
	/** Kits and goals read from the other Codex plugins for this account. */
	private Plans plans = Plans.none();
	private Set<Integer> held = Collections.emptySet();
	/**
	 * The inventory has been read this login. Until it has, what is carried is unknown, and a
	 * clear-out must not take an item still in the pack for one that is gone.
	 */
	private boolean heldSeen;
	private boolean[] done = new boolean[0];
	private boolean progressDirty;
	private boolean reckonDirty;
	private boolean unsaved;
	private boolean summaryDue;
	/** The bank summary has been written this login. */
	private boolean summaryShown;
	/** Quest and diary progress has been read at least once this login. */
	private boolean progressRead;
	private int ticks;
	private int lastProgressRead = -PROGRESS_TICKS;
	/** What the panel was last handed, so an unchanged reckoning is not drawn again. */
	private String lastPublished;
	/** The bank was seen and the shared snapshot is still to be written; with when it was seen. */
	private boolean snapshotDue;
	private long bankSeenAt;
	private int bankSeenTick;
	/** The snapshot last written for this profile, or null. */
	private BankSnapshot lastSnapshot;

	@Provides
	CodexBankStewardConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexBankStewardConfig.class);
	}

	@Override
	protected void startUp()
	{
		catalog = loadCatalog();
		lastPublished = null;
		for (Quest quest : Quest.values())
		{
			quests.put(quest.name(), quest);
		}
		done = new boolean[catalog.getTasks().size()];
		ItemIcons icons = (itemId, onLoaded) ->
		{
			AsyncBufferedImage image = itemManager.getImage(itemId);
			image.onLoaded(onLoaded);
			return image;
		};
		panel = new StewardPanel(icons, new StewardPanel.Actions()
		{
			@Override
			public void keep(int itemId, boolean kept)
			{
				clientThread.invokeLater(() -> CodexBankStewardPlugin.this.keep(itemId, kept));
			}

			@Override
			public void startClearOut()
			{
				clientThread.invokeLater(CodexBankStewardPlugin.this::startClearOut);
			}

			@Override
			public void endClearOut()
			{
				clientThread.invokeLater(CodexBankStewardPlugin.this::endClearOut);
			}
		});
		navButton = NavigationButton.builder()
			.tooltip("Codex Bank Steward")
			.icon(Sigil.icon(16))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		marks.setEnabled(config.bankMarks());
		overlayManager.add(marks);
		clientThread.invokeLater(() ->
		{
			loadProfile();
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				progressDirty = true;
				observeHeld();
				ItemContainer bank = client.getItemContainer(InventoryID.BANK);
				if (bank != null && memory != null)
				{
					unsaved |= memory.observeBank(contents(bank), today());
					if (bankOpen())
					{
						bankSeen();
					}
				}
			}
			reckonDirty = true;
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(marks);
		clientToolbar.removeNavigation(navButton);
		clientThread.invokeLater(() ->
		{
			save();
			writeSnapshot();
			memory = null;
			lastSnapshot = null;
			snapshotDue = false;
			profile = null;
			reckoning = null;
			plan = null;
			plans = Plans.none();
			names.clear();
			tradeable.clear();
			alchPrice.clear();
		});
		panel = null;
	}

	/** The bundled list of which quests and diaries need which items. */
	private Catalog loadCatalog()
	{
		try (InputStream in = CodexBankStewardPlugin.class.getResourceAsStream(DATA))
		{
			if (in == null)
			{
				throw new IOException("missing " + DATA);
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				return new Catalog(gson.fromJson(reader, Catalog.Data.class));
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Could not read the Steward's quest and diary data", e);
			return new Catalog(new Catalog.Data());
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		// ConfigManager writes its changes to disk in this same event, before a save queued on the
		// client thread runs, so a save made here has to be sent on by hand or it is never written.
		CompletableFuture<Void> done = new CompletableFuture<>();
		clientThread.invokeLater(() ->
		{
			try
			{
				boolean saved = save();
				if (writeSnapshot() || saved)
				{
					configManager.sendConfig();
				}
			}
			finally
			{
				done.complete(null);
			}
		});
		event.waitFor(done);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			ensureProfile();
			progressDirty = true;
			reckonDirty = true;
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			save();
			writeSnapshot();
			summaryDue = false;
			summaryShown = false;
			progressRead = false;
			heldSeen = false;
			reckonDirty = true;
			publish();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		switchProfile();
	}

	/**
	 * Saves the old account's memory and loads the new one's. Progress read for the old account
	 * is forgotten, so nothing is called spent until the new account's quests have been read.
	 */
	private void switchProfile()
	{
		save();
		writeSnapshot();
		loadProfile();
		done = new boolean[catalog.getTasks().size()];
		ironman = false;
		progressRead = false;
		held = Collections.emptySet();
		heldSeen = false;
		summaryShown = false;
		summaryDue = false;
		progressDirty = true;
		reckonDirty = true;
	}

	/**
	 * Makes sure the memory in hand is this account's. The profile changed event can arrive after
	 * the first bank or inventory event of a new login, and one account's bank must never be
	 * written into another's memory.
	 */
	private boolean ensureProfile()
	{
		String key = configManager.getRSProfileKey();
		if (!Objects.equals(key, profile))
		{
			switchProfile();
		}
		return memory != null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		String group = event.getGroup();
		// The Steward's own saved memory and bank snapshot are not options: saving them changes nothing shown.
		boolean ours = CodexBankStewardConfig.GROUP.equals(group) && !MEMORY_KEY.equals(event.getKey())
			&& !BankSnapshot.KEY.equals(event.getKey());
		boolean plansChanged = Plans.QUARTERMASTER.equals(group)
			&& (Plans.KITS_KEY.equals(event.getKey()) || Plans.QUARTERS_KEY.equals(event.getKey()))
			|| Plans.NAVIGATOR.equals(group) && Plans.GOALS_KEY.equals(event.getKey());
		if (!ours && !plansChanged)
		{
			return;
		}
		if (ours)
		{
			marks.setEnabled(config.bankMarks());
		}
		if (ours && "shareBank".equals(event.getKey()))
		{
			clientThread.invokeLater(this::shareBankChanged);
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (ours && !"codexPlans".equals(event.getKey()) && !plansChanged)
			{
				reckonDirty = true;
			}
			else
			{
				readPlans();
			}
			// No ticks run while logged out, so the panel is redrawn here.
			if (memory != null && client.getGameState() != GameState.LOGGED_IN)
			{
				reckon();
				publish();
			}
		});
	}

	/**
	 * Reads the kits Codex Quartermaster and the goals Codex Navigator saved for this account. Only
	 * reads: neither plugin needs to be installed, and what they save is never changed here.
	 */
	private void readPlans()
	{
		Plans read = Plans.none();
		if (profile != null && config.codexPlans())
		{
			List<Plans.Kit> kits = Plans.readKits(gson,
				configManager.getConfiguration(Plans.QUARTERMASTER, profile, Plans.KITS_KEY),
				configManager.getConfiguration(Plans.QUARTERMASTER, profile, Plans.QUARTERS_KEY));
			List<String> goals = Plans.readGoals(gson, configManager.getConfiguration(Plans.NAVIGATOR, profile, Plans.GOALS_KEY));
			read = new Plans(kits, goals);
		}
		// Quartermaster saves "quarters" whenever the bank changes; only a real change re-reckons.
		if (!read.equals(plans))
		{
			plans = read;
			reckonDirty = true;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		progressDirty = true;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id != InventoryID.BANK && id != InventoryID.INV && id != InventoryID.WORN)
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN || !ensureProfile())
		{
			return;
		}
		if (id == InventoryID.BANK)
		{
			if (!summaryShown)
			{
				summaryDue = true;
			}
			unsaved |= memory.observeBank(contents(event.getItemContainer()), today());
			reckonDirty = true;
			bankSeen();
		}
		else if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			observeHeld();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (memory == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		ticks++;
		if (progressDirty && ticks - lastProgressRead >= PROGRESS_TICKS)
		{
			progressDirty = false;
			lastProgressRead = ticks;
			reckonDirty |= readProgress();
			progressRead = true;
		}
		if (reckonDirty || ticks % REFRESH_TICKS == 0)
		{
			reckon();
			publish();
			// Wait for quest progress, or the summary would undercount what is done with.
			if (summaryDue && reckoning != null && progressRead)
			{
				summaryDue = false;
				summaryShown = true;
				summary();
			}
		}
		if (unsaved && ticks % SAVE_TICKS == 0)
		{
			save();
		}
		if (snapshotDue && ticks - bankSeenTick >= SNAPSHOT_SETTLE_TICKS)
		{
			writeSnapshot();
		}
	}

	/** The bank window is open now; its item container outlives it until logout. */
	private boolean bankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return client.getGameState() == GameState.LOGGED_IN && bank != null && !bank.isHidden();
	}

	/** The bank was just seen: the shared snapshot is written once it settles. */
	private void bankSeen()
	{
		snapshotDue = true;
		bankSeenAt = System.currentTimeMillis();
		bankSeenTick = ticks;
	}

	/**
	 * Publishes the bank as last seen for the other Codex plugins (see {@link BankSnapshot}).
	 * Written only when the items changed or the last one is getting old, and only for the
	 * profile whose bank it is.
	 *
	 * @return whether anything was written
	 */
	private boolean writeSnapshot()
	{
		if (!snapshotDue)
		{
			return false;
		}
		snapshotDue = false;
		if (profile == null || memory == null || !memory.hasSeenBank() || !config.shareBank())
		{
			return false;
		}
		BankSnapshot snap = BankSnapshot.of(memory.getBank(), bankSeenAt);
		if (snap.sameItems(lastSnapshot) && bankSeenAt - lastSnapshot.getSavedAt() < SNAPSHOT_REFRESH_MS)
		{
			return false;
		}
		lastSnapshot = snap;
		configManager.setConfiguration(CodexBankStewardConfig.GROUP, profile, BankSnapshot.KEY, gson.toJson(snap));
		return true;
	}

	/** Sharing was turned on or off: write the snapshot when the bank is next seen, or take the saved one away. */
	private void shareBankChanged()
	{
		if (profile == null)
		{
			return;
		}
		if (config.shareBank())
		{
			// Written at the next bank visit, or now if the bank is open: only then is its time known.
			lastSnapshot = null;
			if (bankOpen())
			{
				bankSeen();
			}
		}
		else
		{
			snapshotDue = false;
			lastSnapshot = null;
			configManager.unsetConfiguration(CodexBankStewardConfig.GROUP, profile, BankSnapshot.KEY);
		}
	}

	private void keep(int itemId, boolean kept)
	{
		if (memory == null)
		{
			return;
		}
		memory.setKept(itemId, kept);
		unsaved = true;
		reckonDirty = true;
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			reckon();
			publish();
		}
	}

	/** Reads which quests and diaries are done; returns true when anything changed. */
	private boolean readProgress()
	{
		List<Task> tasks = catalog.getTasks();
		boolean changed = false;
		boolean iron = client.getVarbitValue(VarbitID.IRONMAN) != 0;
		if (iron != ironman)
		{
			ironman = iron;
			changed = true;
		}
		for (int i = 0; i < tasks.size(); i++)
		{
			Task task = tasks.get(i);
			boolean finished;
			if (task.usesVarbit())
			{
				finished = task.isFinishedAt(client.getVarbitValue(task.getVarbit()));
			}
			else
			{
				Quest quest = quests.get(task.getQuest());
				finished = quest != null && quest.getState(client) == QuestState.FINISHED;
			}
			if (done[i] != finished)
			{
				done[i] = finished;
				changed = true;
			}
		}
		return changed;
	}

	private void observeHeld()
	{
		Set<Integer> now = new HashSet<>();
		for (int id : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			now.addAll(contents(client.getItemContainer(id)).keySet());
		}
		held = now;
		heldSeen |= client.getItemContainer(InventoryID.INV) != null;
		if (memory != null)
		{
			unsaved |= memory.observeHeld(now, today());
		}
		reckonDirty = true;
	}

	private Map<Integer, Long> contents(ItemContainer container)
	{
		Map<Integer, Long> items = new HashMap<>();
		if (container == null)
		{
			return items;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() < 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			items.merge(itemManager.canonicalize(item.getId()), (long) item.getQuantity(), Long::sum);
		}
		return items;
	}

	private static long today()
	{
		return LocalDate.now().toEpochDay();
	}

	private void reckon()
	{
		reckonDirty = false;
		if (memory == null || !memory.hasSeenBank())
		{
			reckoning = null;
			return;
		}
		Map<Integer, Long> bank = memory.getBank();
		for (int id : bank.keySet())
		{
			describe(id);
		}
		Memory.Session session = memory.getSession();
		if (session != null)
		{
			for (Memory.Planned p : session.getItems())
			{
				describe(p.getItemId());
			}
		}
		Reckoning.Rules rules = new Reckoning.Rules(config.dustDays(), config.graceDays(), config.guardStash(), config.untradeableDust());
		boolean alch = byAlchemy();
		IntToLongFunction byAlch = id -> alchPrice.getOrDefault(id, 0);
		IntToLongFunction byGe = itemManager::getItemPrice;
		reckoning = Reckoning.of(catalog, memory, bank, held, done, alch ? byAlch : byGe, byGe, byAlch,
			id -> tradeable.getOrDefault(id, false), rules, plans, today());
		List<Advice> going = new ArrayList<>(reckoning.getSpent());
		going.addAll(reckoning.getDust());
		plan = ClearOut.plan(going, alch, itemManager.getItemPrice(ItemID.NATURERUNE));
		List<Integer> cleared = session == null ? null : ClearOut.cleared(session, bank, held);
		if (session != null && !session.isAnnounced() && heldSeen && client.getGameState() == GameState.LOGGED_IN
			&& ClearOut.finished(session, cleared, ClearOut.leftOff(session, reckoning, cleared)))
		{
			session.setAnnounced(true);
			unsaved = true;
			announceClearOut(session);
		}
	}

	private void startClearOut()
	{
		if (memory == null || plan == null || plan.isEmpty())
		{
			return;
		}
		List<Memory.Planned> items = new ArrayList<>();
		for (ClearOut.Step step : plan.getSteps())
		{
			items.add(new Memory.Planned(step.getAdvice().getItemId(), step.getAdvice().getQuantity(), step.getRoute(), step.getGain()));
		}
		memory.setSession(new Memory.Session(today(), items));
		unsaved = true;
		reckonDirty = true;
		reckon();
		publish();
	}

	private void endClearOut()
	{
		if (memory == null || memory.getSession() == null)
		{
			return;
		}
		memory.setSession(null);
		unsaved = true;
		reckonDirty = true;
		reckon();
		publish();
	}

	/** One chat line when everything a clear-out set out to clear is gone. */
	private void announceClearOut(Memory.Session session)
	{
		long gain = 0;
		int slots = 0;
		for (int id : ClearOut.cleared(session, memory.getBank(), held))
		{
			gain += session.get(id).getGain();
			slots++;
		}
		ChatMessageBuilder line = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT).append("Codex Bank Steward: ")
			.append(ChatColorType.NORMAL).append("clear-out done. You freed ")
			.append(ChatColorType.HIGHLIGHT).append(slots + (slots == 1 ? " slot" : " slots"));
		if (gain > 0)
		{
			line.append(ChatColorType.NORMAL).append(" and about ")
				.append(ChatColorType.HIGHLIGHT).append(Format.coins(gain) + " gp");
		}
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(line.append(ChatColorType.NORMAL).append(".").build())
			.build());
	}

	private void describe(int itemId)
	{
		if (!names.containsKey(itemId))
		{
			ItemComposition item = itemManager.getItemComposition(itemId);
			names.put(itemId, item.getName());
			tradeable.put(itemId, item.isTradeable());
			alchPrice.put(itemId, item.getHaPrice());
		}
	}

	/** Whether the list is valued at high alchemy rather than the Grand Exchange. */
	private boolean byAlchemy()
	{
		CodexBankStewardConfig.ValueBy by = config.valueBy();
		return by == CodexBankStewardConfig.ValueBy.HIGH_ALCHEMY || by == CodexBankStewardConfig.ValueBy.AUTO && ironman;
	}

	private void loadProfile()
	{
		profile = configManager.getRSProfileKey();
		memory = null;
		reckoning = null;
		snapshotDue = false;
		lastSnapshot = profile == null ? null
			: BankSnapshot.read(gson, configManager.getRSProfileConfiguration(CodexBankStewardConfig.GROUP, BankSnapshot.KEY));
		plan = null;
		plans = Plans.none();
		if (profile == null)
		{
			return;
		}
		try
		{
			String saved = configManager.getRSProfileConfiguration(CodexBankStewardConfig.GROUP, MEMORY_KEY);
			if (saved != null && !saved.isEmpty())
			{
				Memory read = gson.fromJson(saved, Memory.class);
				memory = read == null ? null : read.repair();
			}
		}
		catch (JsonParseException e)
		{
			log.warn("Could not read the Steward's saved memory, starting afresh", e);
		}
		if (memory == null)
		{
			memory = new Memory();
		}
		unsaved = false;
		reckonDirty = true;
		readPlans();
	}

	/** Writes to the profile if anything changed, and says whether it did. */
	private boolean save()
	{
		if (profile == null || memory == null || !unsaved)
		{
			return false;
		}
		unsaved = false;
		memory.prune();
		configManager.setConfiguration(CodexBankStewardConfig.GROUP, profile, MEMORY_KEY, gson.toJson(memory));
		return true;
	}

	private void summary()
	{
		if (!config.bankSummary() || reckoning.freedSlots() == 0)
		{
			return;
		}
		int spent = reckoning.getSpent().size();
		int dust = reckoning.getDust().size();
		StringBuilder what = new StringBuilder();
		if (spent > 0)
		{
			what.append(spent).append(spent == 1 ? " item is" : " items are").append(" done with");
		}
		if (dust > 0)
		{
			what.append(spent > 0 ? " and " : "").append(dust).append(dust == 1 ? " is" : " are").append(" gathering dust");
		}
		ChatMessageBuilder line = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT).append("Codex Bank Steward: ")
			.append(ChatColorType.NORMAL).append(what + ". Clearing them frees ")
			.append(ChatColorType.HIGHLIGHT).append(reckoning.freedSlots() + (reckoning.freedSlots() == 1 ? " slot" : " slots"))
			.append(ChatColorType.NORMAL).append(" and ")
			.append(ChatColorType.HIGHLIGHT).append(Format.coins(reckoning.freedValue()) + " gp" + (byAlchemy() ? " in alchs" : ""))
			.append(ChatColorType.NORMAL).append(".");
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(line.build())
			.build());
	}

	/** Hands the panel and the bank marks the latest reckoning; it is immutable, so Swing may keep it. */
	private void publish()
	{
		StewardPanel target = panel;
		Reckoning shown = reckoning;
		Map<Integer, Advice.Verdict> marked = new HashMap<>();
		if (shown != null)
		{
			for (Advice a : shown.getSpent())
			{
				marked.put(a.getItemId(), Advice.Verdict.SPENT);
			}
			for (Advice a : shown.getDust())
			{
				marked.put(a.getItemId(), Advice.Verdict.DUST);
			}
		}
		marks.setMarks(marked);
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		Player player = client.getLocalPlayer();
		String name = loggedIn && player != null ? player.getName() : null;
		StewardPanel.State state = !loggedIn ? StewardPanel.State.LOGGED_OUT
			: shown == null ? StewardPanel.State.WAITING_FOR_BANK : StewardPanel.State.READY;
		boolean alch = byAlchemy();
		ClearOut shownPlan = shown == null ? null : plan;
		Memory.Session session = memory == null || shown == null ? null : memory.getSession();
		List<Integer> cleared = session == null ? Collections.emptyList()
			: ClearOut.cleared(session, memory.getBank(), held);
		String key = (shown == null ? "none" : String.valueOf(shown.fingerprint())) + "|" + state + "|" + name + "|" + alch
			+ "|" + (shownPlan == null ? 0 : shownPlan.totalGain()) + "|" + (session == null ? -1 : session.getStarted() + ":" + session.getItems().size())
			+ "|" + cleared;
		if (key.equals(lastPublished))
		{
			return;
		}
		lastPublished = key;
		Map<Integer, String> shownNames = new HashMap<>(names);
		if (target != null)
		{
			StewardPanel.Clearing clearing = new StewardPanel.Clearing(shownPlan, session, cleared);
			SwingUtilities.invokeLater(() -> target.update(shown, clearing, shownNames, name, state, alch));
		}
	}
}
