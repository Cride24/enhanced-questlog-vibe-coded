package org.infernalstudios.questlog.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.core.quests.Quest;

import java.util.*;
import java.util.function.Predicate;

/** Personal order, independent of shared/global quest progression. */
public final class QuestTracking {
    public static final int MAX_IDS = 4096;
    private final List<ResourceLocation> ids = new ArrayList<>();
    public List<ResourceLocation> ids() { return List.copyOf(ids); }
    public boolean contains(ResourceLocation id) { return ids.contains(id); }
    public boolean replace(Collection<ResourceLocation> requested, Predicate<ResourceLocation> eligible) {
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>();
        for (ResourceLocation id : requested) {
            if (unique.size() >= MAX_IDS) break;
            if (id != null && eligible.test(id)) unique.add(id);
        }
        List<ResourceLocation> updated = new ArrayList<>(unique);
        if (ids.equals(updated)) return false;
        ids.clear(); ids.addAll(updated); return true;
    }
    public boolean prune(QuestManager manager) {
        return ids.removeIf(id -> {
            Quest quest = manager.getQuest(id);
            return quest == null || quest.isActive() && quest.isCompleted() && quest.isRewarded();
        });
    }
    public List<Quest> display(QuestManager manager) { return display(manager, true); }
    public List<Quest> display(QuestManager manager, boolean authorMode) {
        List<Quest> result = ids.stream().map(manager::getQuest).filter(Objects::nonNull).filter(quest -> authorMode || quest.isVisibleToPlayer() && quest.isFunctional()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        // Stable sorting preserves each group's personal order.
        result.sort(Comparator.comparingInt(quest -> quest.isActive() && quest.isCompleted() ? 1 : 0));
        return result;
    }
    public boolean reorder(ResourceLocation source, ResourceLocation target, boolean after) {
        if (source.equals(target) || !ids.contains(source) || !ids.contains(target)) return false;
        List<ResourceLocation> updated = new ArrayList<>(ids);
        updated.remove(source);
        updated.add(updated.indexOf(target) + (after ? 1 : 0), source);
        return replace(updated, id -> true);
    }
    public void save(CompoundTag data) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        data.put("tracked_quests", list);
    }
    public void load(CompoundTag data) {
        ListTag list = data.getList("tracked_quests", Tag.TAG_STRING);
        List<ResourceLocation> restored = new ArrayList<>();
        for (int i = 0; i < Math.min(list.size(), MAX_IDS); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) restored.add(id);
        }
        replace(restored, id -> true);
    }
}
