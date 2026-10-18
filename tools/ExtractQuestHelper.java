import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.questhelper.panel.PanelDetails;
import com.questhelper.questhelpers.QuestDetails;
import com.questhelper.questhelpers.QuestHelper;
import com.questhelper.questinfo.QuestHelperQuest;
import com.questhelper.requirements.Requirement;
import com.questhelper.requirements.item.ItemRequirement;
import com.questhelper.requirements.item.ItemRequirements;
import com.questhelper.requirements.util.LogicType;
import com.questhelper.steps.ConditionalStep;
import com.questhelper.steps.QuestStep;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.runelite.api.Quest;

/**
 * Reads every quest, miniquest and achievement diary tier from Quest Helper by running its own
 * helpers, and prints what each one needs as JSON. A build-time tool only; it is not part of the
 * plugin. See tools/README.md.
 *
 * <p>"needs" are the helper's item requirements: what the quest asks you to bring. "soft" are
 * everything else the helper mentions: recommended items, and items named by its sidebar panels
 * and by its steps, which includes items picked up during the quest. The Steward holds an item
 * back for any of them while the quest is unfinished, but calls an item spent only through
 * "needs".</p>
 */
public class ExtractQuestHelper
{
	private static Object field(Class<?> type, Object o, String name) throws Exception
	{
		for (Class<?> c = type; c != null; c = c.getSuperclass())
		{
			try
			{
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(o);
			}
			catch (NoSuchFieldException e)
			{
				// look further up
			}
		}
		return null;
	}

	private static void flatten(ItemRequirement r, List<JsonObject> out)
	{
		if (r instanceof ItemRequirements)
		{
			ItemRequirements rs = (ItemRequirements) r;
			if (rs.getLogicType() == LogicType.OR)
			{
				// One of the children: merge every id into a single "any of" group.
				Set<Integer> ids = new LinkedHashSet<>();
				int qty = 1;
				for (ItemRequirement c : rs.getItemRequirements())
				{
					ids.addAll(c.getAllIds());
					qty = Math.max(qty, c.getQuantity());
				}
				add(rs.getName(), new ArrayList<>(ids), qty, out);
			}
			else
			{
				for (ItemRequirement c : rs.getItemRequirements())
				{
					flatten(c, out);
				}
			}
			return;
		}
		add(r.getName(), r.getAllIds(), r.getQuantity(), out);
	}

	private static void add(String name, List<Integer> ids, int qty, List<JsonObject> out)
	{
		List<Integer> real = new ArrayList<>();
		for (Integer id : ids)
		{
			if (id != null && id > 0 && !real.contains(id))
			{
				real.add(id);
			}
		}
		if (real.isEmpty())
		{
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("name", name);
		JsonArray a = new JsonArray();
		real.forEach(a::add);
		o.add("ids", a);
		o.addProperty("qty", Math.max(1, qty));
		out.add(o);
	}

	private static void inject(Object h) throws Exception
	{
		for (Class<?> c = h.getClass(); c != null; c = c.getSuperclass())
		{
			for (Field f : c.getDeclaredFields())
			{
				if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType() == String.class)
				{
					continue;
				}
				String n = f.getType().getName();
				if (n.equals("net.runelite.api.Client") || n.equals("com.questhelper.QuestHelperPlugin")
					|| n.equals("net.runelite.client.config.ConfigManager") || n.equals("net.runelite.client.game.ItemManager")
					|| n.equals("com.questhelper.QuestHelperConfig"))
				{
					f.setAccessible(true);
					if (f.get(h) == null)
					{
						f.set(h, org.mockito.Mockito.mock(f.getType(), org.mockito.Mockito.RETURNS_DEEP_STUBS));
					}
				}
			}
		}
	}

	private static void requirements(Collection<?> reqs, List<JsonObject> out)
	{
		if (reqs == null)
		{
			return;
		}
		for (Object r : reqs)
		{
			if (r instanceof ItemRequirement)
			{
				flatten((ItemRequirement) r, out);
			}
		}
	}

	/** Items named by a step and every step under it. */
	@SuppressWarnings("unchecked")
	private static void steps(Object step, List<JsonObject> out, Map<Object, Boolean> seen) throws Exception
	{
		if (step == null || seen.put(step, true) != null)
		{
			return;
		}
		Object reqs = field(step.getClass(), step, "requirements");
		if (reqs instanceof Collection)
		{
			requirements((Collection<?>) reqs, out);
		}
		Object rec = field(step.getClass(), step, "recommended");
		if (rec instanceof Collection)
		{
			requirements((Collection<?>) rec, out);
		}
		if (step instanceof ConditionalStep)
		{
			for (QuestStep s : ((ConditionalStep) step).getSteps())
			{
				steps(s, out, seen);
			}
		}
		Object subs = field(step.getClass(), step, "steps");
		if (subs instanceof Collection)
		{
			for (Object s : (Collection<Object>) subs)
			{
				steps(s, out, seen);
			}
		}
		else if (subs instanceof Map)
		{
			for (Object s : ((Map<Object, Object>) subs).values())
			{
				steps(s, out, seen);
			}
		}
	}

	private static JsonArray array(List<JsonObject> list)
	{
		JsonArray a = new JsonArray();
		list.forEach(a::add);
		return a;
	}

	public static void main(String[] args) throws Exception
	{
		JsonArray tasks = new JsonArray();
		Map<String, Integer> fails = new TreeMap<>();
		for (QuestHelperQuest q : QuestHelperQuest.values())
		{
			String kind;
			switch (q.getQuestType())
			{
				case P2P:
				case F2P:
					kind = "quest";
					break;
				case MINIQUEST:
					kind = "miniquest";
					break;
				case ACHIEVEMENT_DIARY:
					kind = "diary";
					break;
				default:
					continue;
			}
			JsonObject task = new JsonObject();
			task.addProperty("name", q.getName());
			task.addProperty("kind", kind);
			Quest match = null;
			if (!kind.equals("diary"))
			{
				for (Quest x : Quest.values())
				{
					if (x.getId() == q.getId())
					{
						match = x;
					}
				}
			}
			if (match != null)
			{
				task.addProperty("quest", match.name());
			}
			else
			{
				// Diaries, and miniquests RuneLite has no Quest for, are read from their varbit.
				Object vb = field(QuestHelperQuest.class, q, "varbit");
				int value = (Integer) field(QuestHelperQuest.class, q, "completeValue");
				if (vb == null || value <= 0)
				{
					fails.merge("noProgress:" + q.getName(), 1, Integer::sum);
					continue;
				}
				task.addProperty("varbit", (Integer) field(vb.getClass(), vb, "id"));
				task.addProperty("value", value);
			}
			List<JsonObject> needs = new ArrayList<>();
			List<JsonObject> soft = new ArrayList<>();
			try
			{
				QuestHelper h = q.getQuestHelper();
				inject(h);
				h.initializeRequirements();
				requirements(h.getItemRequirements(), needs);
				try
				{
					h.init();
					if (needs.isEmpty())
					{
						// Some helpers only build their list while loading their steps.
						requirements(h.getItemRequirements(), needs);
					}
					List<PanelDetails> panels = h.getPanels();
					Map<Object, Boolean> seen = new IdentityHashMap<>();
					if (panels != null)
					{
						for (PanelDetails p : panels)
						{
							requirements(p.getRequirements(), soft);
							requirements(p.getRecommended(), soft);
							for (QuestStep s : p.getSteps() == null ? Collections.<QuestStep>emptyList() : p.getSteps())
							{
								steps(s, soft, seen);
							}
						}
					}
				}
				catch (Throwable e)
				{
					fails.merge("steps:" + e.getClass().getSimpleName() + ":" + q.getName(), 1, Integer::sum);
				}
				requirements(h.getItemRecommended(), soft);
			}
			catch (Throwable e)
			{
				fails.merge(e.getClass().getSimpleName() + ":" + q.getName(), 1, Integer::sum);
			}
			task.add("needs", array(needs));
			task.add("soft", array(soft));
			tasks.add(task);
		}
		System.err.println("failures: " + fails);
		System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(tasks));
	}
}
