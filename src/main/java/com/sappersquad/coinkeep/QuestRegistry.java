package com.sappersquad.coinkeep;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-side view over the {@code coinkeep:quest} / {@code quest_line}
 * datapack registries. Content itself lives in JSON now (see ModRegistries),
 * so this class holds no definitions - only lookups and a cache.
 *
 * Caching is keyed on the Registry instance: a datapack reload or a world
 * join hands out a fresh Registry object, so an identity check is a cheap
 * and exact invalidation signal. Without it the quest book would rebuild
 * per-line lists several times a frame.
 */
public class QuestRegistry {

    private static Registry<Quest> cachedQuestRegistry;
    private static Map<String, Quest> byId = Map.of();
    private static Map<String, List<Quest>> byLine = Map.of();
    private static List<QuestLine> sortedLines = List.of();

    /**
     * Quests indexed by what fires them: trigger type, then target id.
     *
     * Every block break, mob kill, craft and advancement used to scan the ENTIRE
     * quest list looking for matches. That is fine for the ~180 quests Coinkeep
     * ships, and quietly awful for a modpack that adds a thousand: a thousand
     * string comparisons per broken block, per player. This turns it into one
     * hash lookup, without changing the "just play, nothing to accept" design a
     * manual turn-in step would have cost.
     */
    private static Map<TriggerType, Map<String, List<Quest>>> byTrigger = Map.of();

    private static void ensure(RegistryAccess access) {
        Registry<Quest> registry = access.lookupOrThrow(ModRegistries.QUEST);
        if (registry == cachedQuestRegistry) {
            return;
        }

        Map<String, Quest> ids = new LinkedHashMap<>();
        Map<String, List<Quest>> lines = new LinkedHashMap<>();
        Map<TriggerType, Map<String, List<Quest>>> triggers = new java.util.EnumMap<>(TriggerType.class);

        List<QuestLine> orderedLines = access.lookupOrThrow(ModRegistries.QUEST_LINE).stream()
                .sorted(Comparator.comparingInt(QuestLine::sortOrder).thenComparing(QuestLine::id))
                .toList();
        for (QuestLine line : orderedLines) {
            lines.put(line.id(), new ArrayList<>());
        }

        // Registry iteration order is not stable across loads, so sort by id
        // to keep the book's within-chapter order deterministic.
        registry.stream().sorted(Comparator.comparing(Quest::id)).forEach(quest -> {
            ids.put(quest.id(), quest);
            lines.computeIfAbsent(quest.lineId(), key -> new ArrayList<>()).add(quest);
            triggers.computeIfAbsent(quest.triggerType(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(quest.triggerTarget(), key -> new ArrayList<>())
                    .add(quest);
        });

        cachedQuestRegistry = registry;
        byId = ids;
        byLine = lines;
        byTrigger = triggers;
        sortedLines = orderedLines;
    }

    public static List<QuestLine> lines(RegistryAccess access) {
        ensure(access);
        return sortedLines;
    }

    public static Collection<Quest> all(RegistryAccess access) {
        ensure(access);
        return byId.values();
    }

    /**
     * Every quest fired by this trigger and target, or an empty list. One hash
     * lookup instead of a scan over the whole book - see {@code byTrigger}.
     */
    public static List<Quest> triggeredBy(RegistryAccess access, TriggerType type, String target) {
        ensure(access);
        Map<String, List<Quest>> forType = byTrigger.get(type);
        if (forType == null) {
            return List.of();
        }
        return forType.getOrDefault(target, List.of());
    }

    public static Quest byId(RegistryAccess access, String id) {
        ensure(access);
        return byId.get(id);
    }

    public static QuestLine lineById(RegistryAccess access, String id) {
        ensure(access);
        for (QuestLine line : sortedLines) {
            if (line.id().equals(id)) {
                return line;
            }
        }
        return null;
    }

    public static List<Quest> questsIn(RegistryAccess access, String lineId) {
        ensure(access);
        return byLine.getOrDefault(lineId, List.of());
    }

    /**
     * Content sanity check, run on server start and after every /reload.
     * A typo'd dependency id would otherwise leave a quest permanently
     * locked with no error anywhere - which matters far more now that
     * datapacks can author content by hand.
     *
     * @return human-readable problems; empty when the content is sound.
     */
    public static List<String> validate(RegistryAccess access) {
        ensure(access);
        List<String> problems = new ArrayList<>();
        Set<String> lineIds = new HashSet<>();
        for (QuestLine line : sortedLines) {
            lineIds.add(line.id());
        }

        for (Quest quest : byId.values()) {
            if (!lineIds.contains(quest.lineId())) {
                problems.add("Quest '" + quest.id() + "' references unknown line '" + quest.lineId() + "'");
            }
            for (String dependency : quest.dependencies()) {
                if (!byId.containsKey(dependency)) {
                    problems.add("Quest '" + quest.id() + "' depends on unknown quest '" + dependency
                            + "' - it can never unlock");
                }
                if (dependency.equals(quest.id())) {
                    problems.add("Quest '" + quest.id() + "' depends on itself - it can never unlock");
                }
            }
            if (quest.requiredCount() < 1) {
                problems.add("Quest '" + quest.id() + "' has a required count below 1");
            }
        }

        for (String id : byId.keySet()) {
            if (hasCycle(id, new HashSet<>(), new HashSet<>())) {
                problems.add("Quest '" + id + "' is part of a dependency cycle - it can never unlock");
            }
        }

        // Shop categories are a datapack registry as of 1.1.0, so an entry
        // can name one nothing defines. That no longer deletes the entry (it
        // lands in a placeholder tab at the end of the sidebar), which means
        // the typo would otherwise be invisible - hence the report.
        for (String category : ShopRegistry.danglingCategories(access)) {
            List<String> orphans = new ArrayList<>();
            for (ShopEntry entry : ShopRegistry.inCategory(access, category)) {
                orphans.add(entry.id());
            }
            problems.add("Shop category '" + category + "' is not defined by any datapack"
                    + " (expected data/<namespace>/coinkeep/shop_category/" + category + ".json)"
                    + " - " + orphans.size() + " entr" + (orphans.size() == 1 ? "y" : "ies")
                    + " fell back to a placeholder tab: " + orphans);
        }
        return problems;
    }

    private static boolean hasCycle(String id, Set<String> visiting, Set<String> done) {
        if (done.contains(id)) {
            return false;
        }
        if (!visiting.add(id)) {
            return true;
        }
        Quest quest = byId.get(id);
        if (quest != null) {
            for (String dependency : quest.dependencies()) {
                if (hasCycle(dependency, visiting, done)) {
                    return true;
                }
            }
        }
        visiting.remove(id);
        done.add(id);
        return false;
    }
}
