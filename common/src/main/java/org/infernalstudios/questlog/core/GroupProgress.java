package org.infernalstudios.questlog.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.infernalstudios.questlog.QuestlogEvents;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.objectives.Objective;
import org.infernalstudios.questlog.core.quests.objectives.item.ItemObtainObjective;
import org.infernalstudios.questlog.core.quests.objectives.logic.AndObjective;
import org.infernalstudios.questlog.core.quests.objectives.logic.OrObjective;
import org.infernalstudios.questlog.core.quests.objectives.logic.NotObjective;
import org.infernalstudios.questlog.event.events.QuestEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Rebuilds a group quest from each current participant's own saved progress. */
public final class GroupProgress {
    private static boolean updating;
    private static final Set<Quest> pending = new LinkedHashSet<>();
    private GroupProgress() {}

    private record Participant(UUID id, Quest online, CompoundTag saved) {}

    public static boolean refresh(Quest source) {
        ServerPlayerManager players = ServerPlayerManager.INSTANCE;
        if (source == null || !source.isGroupQuest() || source.manager.isClient() || players == null
                || AdventureGroups.INSTANCE == null || !source.manager.isLoaded()) return false;
        if (updating) { pending.add(source); return true; }
        updating = true;
        try {
            ResourceLocation id = source.getId();
            AdventureGroups.Group group = AdventureGroups.INSTANCE.membership(source.manager.player.getUUID());
            Set<UUID> members = group == null ? Set.of(source.manager.player.getUUID())
                    : new LinkedHashSet<>(group.members.keySet());
            List<Participant> participants = new ArrayList<>();
            Path dir = players.server().getWorldPath(LevelResource.PLAYER_DATA_DIR);
            boolean globalDone = source.isGlobal() && players.isGloballyCompleted(id);
            for (UUID member : members) {
                ServerPlayer player = players.server().getPlayerList().getPlayer(member);
                QuestManager manager = player == null ? null : players.getManagerIfPresent(player);
                Quest quest = manager == null || !manager.isLoaded() ? null : manager.getQuest(id);
                if (quest != null) {
                    if (quest.isActive() && quest.isVisibleToPlayer() && !quest.hasSentCompletion
                            && !quest.isGroupResetPending() && !quest.isFailed())
                        participants.add(new Participant(member, quest, null));
                } else if (player == null) {
                    CompoundTag all = ServerPlayerManager.readPlayerData(dir, member);
                    if (all == null) continue;
                    CompoundTag saved = all.getCompound(id.toString());
                    if (eligibleOffline(source, saved, globalDone)) participants.add(new Participant(member, null, all));
                }
            }
            if (!participants.isEmpty()) {
                for (int index = 0; index < source.objectives.size(); index++) {
                    Objective model = source.objectives.get(index);
                    applyObjective(model, index, new int[0], participants, source.isPerParticipant(), players);
                }
                for (Participant person : participants) if (person.online != null)
                    person.online.setParticipantCount(participants.size());
                boolean complete = source.objectives.stream().allMatch(Objective::isCompleted);
                if (complete) complete(participants, source, players, dir);
            }
            for (UUID member : members) {
                ServerPlayer player = players.server().getPlayerList().getPlayer(member);
                QuestManager manager = player == null ? null : players.getManagerIfPresent(player);
                Quest quest = manager == null ? null : manager.getQuest(id);
                if (quest != null && !quest.hasSentCompletion) quest.setParticipantCount(participants.size());
            }
            for (UUID member : members) {
                ServerPlayer player = players.server().getPlayerList().getPlayer(member);
                QuestManager manager = player == null ? null : players.getManagerIfPresent(player);
                if (manager != null && manager.isLoaded()) manager.syncIndividual(id);
            }
            return true;
        } finally {
            updating = false;
            if (!pending.isEmpty()) {
                List<Quest> queued = List.copyOf(pending);
                pending.clear();
                for (Quest quest : queued) refresh(quest);
            }
        }
    }

    static boolean eligibleOffline(Quest source, CompoundTag saved, boolean globalDone) {
        if (saved.getBoolean("completed")) return false;
        if (saved.getBoolean("group_reset_pending")) return false;
        if (globalDone && !saved.getBoolean("ignore_global_completion")) return false;
        var failures = saved.getList("failures", Tag.TAG_COMPOUND);
        for (int i = 0; i < source.failureConditions.size() && i < failures.size(); i++)
            if (savedCompleted(source.failureConditions.get(i), failures.getCompound(i))) return false;
        if (saved.getBoolean("shared_access")) return true;
        JsonObject definition = DefinitionUtil.getCachedQuest(source.getId());
        boolean triggered = saved.getBoolean("triggered");
        if (triggered && saved.contains("_structure")) {
            try {
                JsonObject previous = JsonParser.parseString(saved.getString("_structure")).getAsJsonObject();
                var current = definition != null && definition.has("prerequisites") ? definition.get("prerequisites")
                        : definition != null && definition.has("requirements") ? definition.get("requirements") : new JsonArray();
                triggered = previous.has("prerequisites")
                        && QuestText.gameplay(previous.get("prerequisites")).equals(QuestText.gameplay(current));
            } catch (RuntimeException malformed) { triggered = false; }
        }
        String chapter = source.getDisplay().getChapter();
        ResourceLocation chapterId = ResourceLocation.tryParse(chapter.contains(":") ? chapter : "questlog:" + chapter);
        JsonObject chapterDefinition = chapterId == null ? null : DefinitionUtil.getCachedChapter(chapterId);
        boolean noPrerequisites = definition != null
                && (!definition.has("prerequisites") || definition.getAsJsonArray("prerequisites").isEmpty())
                && (!definition.has("requirements") || definition.getAsJsonArray("requirements").isEmpty());
        return definition != null && (!definition.has("hidden") || !definition.get("hidden").getAsBoolean())
                && (chapterDefinition == null || !chapterDefinition.has("hidden") || !chapterDefinition.get("hidden").getAsBoolean())
                && (triggered || noPrerequisites);
    }

    private static boolean savedCompleted(Objective model, CompoundTag saved) {
        if (model instanceof NotObjective) return !savedCompleted(model.getChildren().getFirst(), saved.getCompound("child"));
        if (model instanceof AndObjective || model instanceof OrObjective) {
            var children = saved.getList("children", Tag.TAG_COMPOUND);
            boolean all = model instanceof AndObjective;
            for (int i = 0; i < model.getChildren().size(); i++) {
                boolean done = savedCompleted(model.getChildren().get(i), i < children.size() ? children.getCompound(i) : new CompoundTag());
                if (all && !done || !all && done) return !all;
            }
            return all;
        }
        return saved.getInt("units") >= model.getBaseRequiredAmount();
    }

    private static void applyObjective(Objective model, int index, int[] path, List<Participant> people,
                                       boolean perParticipant, ServerPlayerManager players) {
        List<Objective> children = model.getChildren();
        if (!children.isEmpty()) {
            for (int i = 0; i < children.size(); i++) {
                int[] childPath = java.util.Arrays.copyOf(path, path.length + 1);
                childPath[path.length] = i;
                applyObjective(children.get(i), index, childPath, people, perParticipant, players);
            }
            return;
        }
        long total = 0;
        for (Participant person : people) {
            Objective own = person.online == null ? null : ownObjective(person.online.objectives.get(index), path);
            int units;
            if (model instanceof ItemObtainObjective obtain) {
                ServerPlayer onlinePlayer = players.server().getPlayerList().getPlayer(person.id);
                units = onlinePlayer != null ? obtain.countInventory(onlinePlayer)
                        : obtain.countOfflineInventory(players.server(), person.id);
            } else if (own != null) units = own.getPersonalUnits();
            else units = savedObjective(person.saved.getCompound(model.getParent().getId().toString()), index, path).getInt("units");
            total = Math.min(Long.MAX_VALUE - Math.max(0, units), total) + Math.max(0, units);
        }
        long required = (long) model.getBaseRequiredAmount() * (perParticipant ? people.size() : 1);
        long target = Math.max(1, required);
        long count = total;
        model.setCollectiveProgress(count, target);
        for (Participant person : people) {
            if (person.online == null) continue;
            Objective own = ownObjective(person.online.objectives.get(index), path);
            own.setCollectiveProgress(count, target);
        }
    }

    private static Objective ownObjective(Objective root, int[] path) {
        for (int index : path) root = root.getChildren().get(index);
        return root;
    }

    private static CompoundTag savedObjective(CompoundTag quest, int index, int[] path) {
        var objectives = quest.getList("objectives", Tag.TAG_COMPOUND);
        if (index >= objectives.size()) return new CompoundTag();
        CompoundTag result = objectives.getCompound(index);
        for (int child : path) {
            if (result.contains("child", Tag.TAG_COMPOUND)) { result = result.getCompound("child"); continue; }
            var children = result.getList("children", Tag.TAG_COMPOUND);
            if (child >= children.size()) return new CompoundTag();
            result = children.getCompound(child);
        }
        return result;
    }

    private static void complete(List<Participant> people, Quest source, ServerPlayerManager players, Path dir) {
        for (Participant person : people) {
            if (person.online != null) continue;
            CompoundTag saved = person.saved.getCompound(source.getId().toString());
            saved.putBoolean("completed", true);
            person.saved.put(source.getId().toString(), saved);
            if (!ServerPlayerManager.writePlayerData(dir, person.id, person.saved)) return;
        }
        for (Participant person : people) {
            if (person.online != null) {
                person.online.hasSentCompletion = true;
                players.save(person.online.manager);
            }
        }
        for (Participant person : people) {
            if (person.online == null) continue;
            QuestlogEvents.onQuestCompleted(new QuestEvent.Completed(person.online.manager.player, person.online, true));
            players.save(person.online.manager);
        }
        if (source.isGlobal()) players.onGlobalQuestUpdated(source);
    }

    public static void refreshAll() {
        ServerPlayerManager players = ServerPlayerManager.INSTANCE;
        if (players == null || AdventureGroups.INSTANCE == null) return;
        for (ServerPlayer player : players.server().getPlayerList().getPlayers()) {
            QuestManager manager = players.getManagerIfPresent(player);
            if (manager == null || !manager.isLoaded()) continue;
            for (Quest quest : manager.getAllQuests()) if (quest.isGroupQuest() && quest.isActive()) refresh(quest);
        }
    }
}
