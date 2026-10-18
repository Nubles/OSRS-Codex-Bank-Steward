import com.questhelper.questhelpers.QuestDetails;
import com.questhelper.questinfo.QuestHelperQuest;
import com.questhelper.questhelpers.QuestHelper;
import com.questhelper.requirements.item.ItemRequirement;
import com.questhelper.requirements.item.ItemRequirements;
import com.questhelper.requirements.util.LogicType;
import com.google.gson.*;
import java.lang.reflect.Field;
import java.util.*;
import net.runelite.api.Quest;

public class ExtractQuestHelper {
  static Object field(Object o, String name) throws Exception {
    Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
  }
  static void flatten(ItemRequirement r, List<JsonObject> out, List<String> notes) {
    if (r instanceof ItemRequirements) {
      ItemRequirements rs = (ItemRequirements) r;
      if (rs.getLogicType() == LogicType.OR) {
        // one of the children: merge every id into a single group
        Set<Integer> ids = new LinkedHashSet<>(); int qty = 1;
        for (ItemRequirement c : rs.getItemRequirements()) { ids.addAll(c.getAllIds()); qty = Math.max(qty, c.getQuantity()); }
        add(rs.getName(), new ArrayList<>(ids), qty, false, out);
      } else {
        for (ItemRequirement c : rs.getItemRequirements()) flatten(c, out, notes);
      }
      return;
    }
    add(r.getName(), r.getAllIds(), r.getQuantity(), false, out);
  }
  static void add(String name, List<Integer> ids, int qty, boolean kept, List<JsonObject> out) {
    List<Integer> real = new ArrayList<>();
    for (Integer id : ids) if (id != null && id > 0 && !real.contains(id)) real.add(id);
    if (real.isEmpty()) return;
    JsonObject o = new JsonObject(); o.addProperty("name", name);
    JsonArray a = new JsonArray(); real.forEach(a::add); o.add("ids", a); o.addProperty("qty", Math.max(1, qty));
    out.add(o);
  }
  static void inject(Object h) throws Exception {
    for (Class<?> c = h.getClass(); c != null; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType() == String.class) continue;
        String n = f.getType().getName();
        if (n.equals("net.runelite.api.Client") || n.equals("com.questhelper.QuestHelperPlugin") || n.equals("net.runelite.client.config.ConfigManager") || n.equals("net.runelite.client.game.ItemManager")) {
          f.setAccessible(true); if (f.get(h) == null) f.set(h, org.mockito.Mockito.mock(f.getType(), org.mockito.Mockito.RETURNS_DEEP_STUBS));
        }
      }
    }
  }
  public static void main(String[] args) throws Exception {
    JsonArray tasks = new JsonArray();
    Map<String, Integer> fails = new TreeMap<>();
    for (QuestHelperQuest q : QuestHelperQuest.values()) {
      QuestDetails.Type t = q.getQuestType();
      String kind;
      switch (t) { case P2P: case F2P: kind = "quest"; break; case MINIQUEST: kind = "miniquest"; break; case ACHIEVEMENT_DIARY: kind = "diary"; break; default: continue; }
      JsonObject task = new JsonObject(); task.addProperty("name", q.getName()); task.addProperty("kind", kind);
      if (kind.equals("diary")) {
        Object vb = field(q, "varbit"); task.addProperty("varbit", (Integer) field(vb, "id")); task.addProperty("value", (Integer) field(q, "completeValue"));
      } else {
        Quest match = null; for (Quest x : Quest.values()) if (x.getId() == q.getId()) match = x;
        if (match == null) { fails.merge("noQuest:" + q.getName(), 1, Integer::sum); continue; }
        task.addProperty("quest", match.name());
      }
      List<JsonObject> needs = new ArrayList<>(); List<String> notes = new ArrayList<>();
      try {
        QuestHelper h = q.getQuestHelper(); inject(h); h.initializeRequirements();
        List<ItemRequirement> reqs = h.getItemRequirements();
        if (reqs != null) for (ItemRequirement r : reqs) if (r != null) flatten(r, needs, notes);
      } catch (Throwable e) { fails.merge(e.getClass().getSimpleName() + ":" + q.getName(), 1, Integer::sum); }
      JsonArray n = new JsonArray(); needs.forEach(n::add); task.add("needs", n);
      tasks.add(task);
    }
    System.err.println("failures: " + fails);
    System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(tasks));
  }
}
