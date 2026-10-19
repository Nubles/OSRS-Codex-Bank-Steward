package com.codex.steward;

import com.codex.steward.core.Advice;
import com.codex.steward.core.BankItems;
import com.codex.steward.core.BankSnapshot;
import com.codex.steward.core.BankView;
import com.codex.steward.core.Catalog;
import com.codex.steward.core.ClearOut;
import com.codex.steward.core.Memory;
import com.codex.steward.core.Placeholders;
import com.codex.steward.core.Plans;
import com.codex.steward.core.Reckoning;
import com.codex.steward.core.SavingsGoal;
import com.codex.steward.core.Shortfall;
import com.codex.steward.core.Task;
import com.codex.steward.core.Tidy;
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
import java.util.Arrays;
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
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
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
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
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
@PluginDependency(BankTagsPlugin.class)
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

	/** RuneLite's Bank Tags, through which the bank can be shown filtered to one of the Steward's lists. */
	@Inject
	private TagManager tagManager;

	@Inject
	private BankTagsService bankTags;

	private final BankMarks marks = new BankMarks();
	private final Map<Integer, String> names = new HashMap<>();
	private final Map<Integer, Boolean> tradeable = new HashMap<>();
	private final Map<Integer, Integer> alchPrice = new HashMap<>();
	/** Whether the game offers to drink an item, which tells a potion from a charged amulet. */
	private final Map<Integer, Boolean> drinkable = new HashMap<>();
	/** The bank's empty placeholders as last seen this login: the item each holds a slot for, to its own id. */
	private Map<Integer, Integer> placeholderIds = Collections.emptyMap();
	/** Empty placeholders worth releasing, and potions worth decanting, for the bank as last seen. */
	private List<Placeholders.Line> oldPlaceholders = Collections.emptyList();
	private List<Tidy.Decant> decants = Collections.emptyList();
	/** Codex Gear Roadmap's savings goal as it saved it, or null; and how far the list takes it. */
	private String savingsJson;
	private SavingsGoal savingsGoal;
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
	/** What each pinned Navigator goal still needs that the player does not own. */
	private List<Shortfall.Goal> toGet = Collections.emptyList();
	/** How many of each item is carried and worn. */
	private Map<Integer, Long> heldCounts = Collections.emptyMap();
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
	/** The lists the bank can be shown filtered to, as last published; read by Bank Tags on the client thread. */
	private BankView clearView = BankView.EMPTY;
	private BankView tidyView = BankView.EMPTY;

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
		// The plugin is the same object when turned off and on again: what was read last time is not this login's.
		progressRead = false;
		heldSeen = false;
		summaryShown = false;
		summaryDue = false;
		ironman = false;
		held = Collections.emptySet();
		heldCounts = Collections.emptyMap();
		lastProgressRead = ticks - PROGRESS_TICKS;
		clearView = BankView.EMPTY;
		tidyView = BankView.EMPTY;
		tagManager.registerTag(BankView.CLEAR_OUT, id -> clearView.contains(id, itemManager.canonicalize(id)));
		tagManager.registerTag(BankView.TIDY, id -> tidyView.contains(id, id));
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

			@Override
			public void showInBank(String view)
			{
				clientThread.invokeLater(() -> CodexBankStewardPlugin.this.showInBank(view));
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
					unsaved |= memory.observePlaceholders(placeholders(bank), today());
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
		tagManager.unregisterTag(BankView.CLEAR_OUT);
		tagManager.unregisterTag(BankView.TIDY);
		clientThread.invokeLater(() ->
		{
			// A view left open would show an empty bank with the Steward gone.
			closeView();
			clearView = BankView.EMPTY;
			tidyView = BankView.EMPTY;
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
			drinkable.clear();
			placeholderIds = Collections.emptyMap();
			oldPlaceholders = Collections.emptyList();
			decants = Collections.emptyList();
			savingsJson = null;
			savingsGoal = null;
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
		if (configManager.getRSProfileKey() == null)
		{
			// Logging out can leave no profile at all until the next login. The account's memory
			// stays in hand, saved, so the panel still shows its bank as last seen; the next login
			// loads another account's only if it is another account.
			save();
			writeSnapshot();
			return;
		}
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
		heldCounts = Collections.emptyMap();
		heldSeen = false;
		summaryShown = false;
		summaryDue = false;
		progressDirty = true;
		reckonDirty = true;
		placeholderIds = Collections.emptyMap();
		oldPlaceholders = Collections.emptyList();
	}

	/**
	 * Makes sure the memory in hand is this account's. The profile changed event can arrive after
	 * the first bank or inventory event of a new login, and one account's bank must never be
	 * written into another's memory.
	 */
	private boolean ensureProfile()
	{
		String key = configManager.getRSProfileKey();
		if (key == null)
		{
			// Not known yet: nothing seen now can be filed under an account.
			return false;
		}
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
			|| Plans.NAVIGATOR.equals(group) && Plans.GOALS_KEY.equals(event.getKey())
			|| SavingsGoal.GROUP.equals(group) && SavingsGoal.KEY.equals(event.getKey());
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
		String savings = profile != null && config.codexPlans() ? configManager.getConfiguration(SavingsGoal.GROUP, profile, SavingsGoal.KEY) : null;
		if (!Objects.equals(savings, savingsJson))
		{
			savingsJson = savings;
			reckonDirty = true;
		}
		// Quartermaster saves "quarters" whenever the bank changes; only a real change re-reckons.
		if (!read.equals(plans))
		{
			plans = read;
			reckonDirty = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			// Bank Tags remembers the open tab for the next visit; a filtered view lasts one visit.
			closeView();
		}
	}

	/** Closes the bank's filtered view if it is one of the Steward's. Client thread only. */
	private void closeView()
	{
		String active = bankTags.getActiveTag();
		if (BankView.CLEAR_OUT.equals(active) || BankView.TIDY.equals(active))
		{
			bankTags.closeBankTag();
		}
	}

	/** Shows the bank filtered to one of the Steward's lists, or says in the chat box why it cannot. */
	private void showInBank(String tag)
	{
		BankView view = BankView.TIDY.equals(tag) ? tidyView : clearView;
		// Bank Tags is on unless turned off, the way RuneLite itself decides it.
		boolean tagsOn = !"false".equals(configManager.getConfiguration(RuneLiteConfig.GROUP_NAME, "banktagsplugin"));
		BankView.Refusal refusal = view.refusal(bankOpen(), tagsOn);
		if (refusal != null)
		{
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(new ChatMessageBuilder()
					.append(ChatColorType.HIGHLIGHT).append("Codex Bank Steward: ")
					.append(ChatColorType.NORMAL).append(BankView.explain(refusal, tag))
					.build())
				.build());
			return;
		}
		bankTags.openBankTag(tag, BankTagsService.OPTION_NO_LAYOUT);
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
			unsaved |= memory.observePlaceholders(placeholders(event.getItemContainer()), today());
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
		if (memory != null && memory.setLevels(client.getRealSkillLevel(Skill.MINING), client.getRealSkillLevel(Skill.WOODCUTTING)))
		{
			unsaved = true;
			changed = true;
		}
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
		Map<Integer, Long> counts = new HashMap<>();
		for (int id : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			contents(client.getItemContainer(id)).forEach((item, q) -> counts.merge(item, q, Long::sum));
		}
		Set<Integer> now = new HashSet<>(counts.keySet());
		held = now;
		heldCounts = counts;
		heldSeen |= client.getItemContainer(InventoryID.INV) != null;
		if (memory != null)
		{
			unsaved |= memory.observeHeld(now, today());
		}
		reckonDirty = true;
	}

	private Map<Integer, Long> contents(ItemContainer container)
	{
		if (container == null)
		{
			return new HashMap<>();
		}
		Item[] slots = container.getItems();
		int[] ids = new int[slots.length];
		int[] quantities = new int[slots.length];
		for (int i = 0; i < slots.length; i++)
		{
			ids[i] = slots[i].getId();
			quantities[i] = slots[i].getQuantity();
		}
		return BankItems.count(ids, quantities, this::isPlaceholder, itemManager::canonicalize);
	}

	private boolean isPlaceholder(int itemId)
	{
		return itemManager.getItemComposition(itemId).getPlaceholderTemplateId() != -1;
	}

	/**
	 * The bank's empty placeholders: the items they hold a slot for. Their own ids are kept for
	 * the bank marks, which see the placeholder rather than the item.
	 */
	private Set<Integer> placeholders(ItemContainer bank)
	{
		Map<Integer, Integer> ids = new HashMap<>();
		for (Item item : bank.getItems())
		{
			if (item.getId() >= 0 && isPlaceholder(item.getId()))
			{
				ids.put(itemManager.canonicalize(item.getId()), item.getId());
			}
		}
		placeholderIds = ids;
		return ids.keySet();
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
			id -> tradeable.getOrDefault(id, false), rules, plans, memory.getLevels(), today());
		for (Advice a : reckoning.getOutclassed())
		{
			// The faster tool may be carried rather than banked; its name is shown with the slower.
			describe(a.getBetter());
		}
		Map<Integer, Long> owned = new HashMap<>(bank);
		heldCounts.forEach((id, q) -> owned.merge(id, q, Long::sum));
		// Until this login's quest progress is read, every quest looks unfinished.
		toGet = !progressRead ? Collections.emptyList() : Shortfall.of(catalog, plans, done, owned, itemManager::getItemPrice, id ->
		{
			describe(id);
			return tradeable.getOrDefault(id, false);
		});
		List<Advice> going = new ArrayList<>(reckoning.getSpent());
		going.addAll(reckoning.getDust());
		going.addAll(reckoning.getOutclassed());
		plan = ClearOut.plan(going, alch, itemManager.getItemPrice(ItemID.NATURERUNE));
		// Potions already on the list are better sold than decanted.
		Map<Integer, Long> keeping = new HashMap<>(bank);
		for (Advice a : going)
		{
			keeping.remove(a.getItemId());
		}
		decants = Tidy.decants(keeping, names::get, id -> drinkable.getOrDefault(id, false));
		// Until this login's quest progress is read, every quest looks unfinished; the list in hand stands.
		for (int id : memory.getPlaceholders().keySet())
		{
			describe(id);
		}
		if (progressRead)
		{
			oldPlaceholders = Placeholders.old(catalog, memory, held, done, plans, rules, today());
		}
		else
		{
			// Progress is not read while logged out, but a placeholder kept or gone since still leaves the list.
			List<Placeholders.Line> still = new ArrayList<>();
			for (Placeholders.Line l : oldPlaceholders)
			{
				if (!memory.isKept(l.getItemId()) && memory.getPlaceholders().containsKey(l.getItemId()))
				{
					still.add(l);
				}
			}
			oldPlaceholders = still;
		}
		savingsGoal = ironman || savingsJson == null ? null
			: SavingsGoal.read(gson, savingsJson, owned, itemManager::getItemPrice, plan.totalGain());
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
			String[] actions = item.getInventoryActions();
			drinkable.put(itemId, actions != null && Arrays.asList(actions).contains("Drink"));
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
		if (profile != null && !config.shareBank() && configManager.getRSProfileConfiguration(CodexBankStewardConfig.GROUP, BankSnapshot.KEY) != null)
		{
			// Sharing was turned off while this account was not logged in: take its saved bank away now.
			configManager.unsetConfiguration(CodexBankStewardConfig.GROUP, profile, BankSnapshot.KEY);
			lastSnapshot = null;
		}
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
		int outclassed = reckoning.getOutclassed().size();
		List<String> parts = new ArrayList<>();
		if (spent > 0)
		{
			parts.add(spent + (spent == 1 ? " item is" : " items are") + " done with");
		}
		if (dust > 0)
		{
			parts.add(dust + (parts.isEmpty() ? dust == 1 ? " item is" : " items are" : dust == 1 ? " is" : " are") + " gathering dust");
		}
		if (outclassed > 0)
		{
			parts.add(outclassed + (outclassed == 1 ? " tool is" : " tools are") + " outclassed");
		}
		StringBuilder what = new StringBuilder();
		for (int i = 0; i < parts.size(); i++)
		{
			what.append(i == 0 ? "" : i == parts.size() - 1 ? " and " : ", ").append(parts.get(i));
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

	/** What the panel shows of the goals' shortfall, so an unchanged one is not drawn again. */
	private static String toGetKey(List<Shortfall.Goal> goals)
	{
		StringBuilder key = new StringBuilder();
		for (Shortfall.Goal g : goals)
		{
			key.append(g.getName()).append(':').append(g.getCost()).append(':').append(g.getMore()).append(':').append(g.getUntradeable());
			for (Shortfall.Line l : g.getLines())
			{
				key.append(',').append(l.getItemId()).append('x').append(l.getMissing());
			}
			key.append(';');
		}
		return key.toString();
	}

	/** What the panel shows of tidying and the savings goal, so an unchanged one is not drawn again. */
	private static String extrasKey(List<Placeholders.Line> placeholders, List<Integer> kept, List<Tidy.Decant> decants, SavingsGoal goal)
	{
		StringBuilder key = new StringBuilder();
		for (Placeholders.Line l : placeholders)
		{
			key.append(l.getItemId()).append(l.getWhy().ordinal()).append(':').append(l.getDays()).append(',');
		}
		key.append(kept).append(';');
		for (Tidy.Decant d : decants)
		{
			key.append(d.getName()).append(':').append(d.getHeld()).append(',');
		}
		if (goal != null)
		{
			key.append(';').append(goal.getName()).append(':').append(goal.getCost()).append(':').append(goal.getCoins())
				.append(':').append(goal.getGain());
		}
		return key.toString();
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
			for (Advice a : shown.getOutclassed())
			{
				marked.put(a.getItemId(), Advice.Verdict.OUTCLASSED);
			}
		}
		marks.setMarks(marked);
		Set<Integer> releasable = new HashSet<>();
		List<Placeholders.Line> shownPlaceholders = shown == null ? Collections.emptyList() : oldPlaceholders;
		for (Placeholders.Line l : shownPlaceholders)
		{
			Integer own = placeholderIds.get(l.getItemId());
			if (own != null)
			{
				releasable.add(own);
			}
		}
		marks.setPlaceholders(releasable);
		List<Integer> keptPlaceholders = new ArrayList<>();
		if (shown != null && memory != null)
		{
			for (int id : memory.getPlaceholders().keySet())
			{
				if (memory.isKept(id))
				{
					keptPlaceholders.add(id);
				}
			}
			Collections.sort(keptPlaceholders);
		}
		List<Tidy.Decant> shownDecants = shown == null ? Collections.emptyList() : decants;
		SavingsGoal shownGoal = shown == null ? null : savingsGoal;
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
		List<Shortfall.Goal> shownToGet = shown == null ? Collections.emptyList() : toGet;
		clearView = BankView.clearOut(shownPlan, session, cleared);
		tidyView = BankView.tidy(releasable, shownDecants);
		String key = (shown == null ? "none" : String.valueOf(shown.fingerprint())) + "|" + state + "|" + name + "|" + alch
			+ "|" + ironman + "|" + toGetKey(shownToGet)
			+ "|" + (shownPlan == null ? 0 : shownPlan.totalGain()) + "|" + (session == null ? -1 : session.getStarted() + ":" + session.getItems().size())
			+ "|" + cleared + "|" + extrasKey(shownPlaceholders, keptPlaceholders, shownDecants, shownGoal) + "|" + releasable;
		if (key.equals(lastPublished))
		{
			return;
		}
		lastPublished = key;
		Map<Integer, String> shownNames = new HashMap<>(names);
		if (target != null)
		{
			StewardPanel.Clearing clearing = new StewardPanel.Clearing(shownPlan, session, cleared);
			boolean iron = ironman;
			StewardPanel.Extras extras = new StewardPanel.Extras(shownPlaceholders, keptPlaceholders, shownDecants, shownGoal);
			SwingUtilities.invokeLater(() -> target.update(shown, clearing, shownToGet, shownNames, name, state, alch, iron, extras));
		}
	}
}
