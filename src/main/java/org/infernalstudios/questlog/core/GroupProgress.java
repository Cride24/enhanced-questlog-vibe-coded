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

    record Participant(UUID id, Quest online, CompoundTag saved, boolean resetPending) {}

    public static boolean eligibleCombatParticipant(Quest source, UUID id) {
        ServerPlayerManager players = ServerPlayerManager.INSTANCE;
        if (players == null || AdventureGroups.INSTANCE == null || source == null) return false;
        AdventureGroups.Group group = AdventureGroups.INSTANCE.membership(source.manager.player.getUUID());
        if (group == null ? !id.equals(source.manager.player.getUUID()) : !group.members.containsKey(id)) return false;
        ServerPlayer player = players.server().getPlayerList().getPlayer(id);
        if (player != null) {
            QuestManager manager = players.getManagerIfPresent(player);
            Quest quest = manager == null || !manager.isLoaded() ? null : manager.getQuest(source.getId());
            return quest != null && quest.isActive() && quest.isVisibleToPlayer()
                    && !quest.hasSentCompletion && !quest.isFailed();
        }
        CompoundTag all = ServerPlayerManager.readPlayerData(players.server().getWorldPath(LevelResource.PLAYER_DATA_DIR), id);
        return all != null && eligibleOffline(source, all.getCompound(source.getId().toString()),
                source.isGlobal() && players.isGloballyCompleted(source.getId()));
    }

    public static String combatCycle(Quest source, UUID id) {
        if (!eligibleCombatParticipant(source, id)) return null;
        ServerPlayerManager players = ServerPlayerManager.INSTANCE;
        ServerPlayer player = players.server().getPlayerList().getPlayer(id);
        if (player != null) {
            QuestManager manager = players.getManagerIfPresent(player);
            Quest quest = manager == null ? null : manager.getQuest(source.getId());
            return quest == null ? null : quest.rewardCycle();
        }
        CompoundTag all = ServerPlayerManager.readPlayerData(players.server().getWorldPath(LevelResource.PLAYER_DATA_DIR), id);
        return all == null ? null : all.getCompound(source.getId().toString()).getString("reward_cycle");
    }

    public static String objectivePath(Quest source, Objective target) {
        for (int i = 0; i < source.objectives.size(); i++) {
            String path = objectivePath(source.objectives.get(i), target, Integer.toString(i));
            if (path != null) return path;
        }
        return null;
    }

    private static String objectivePath(Objective root, Objective target, String path) {
        if (root == target) return path;
        for (int i = 0; i < root.getChildren().size(); i++) {
            String found = objectivePath(root.getChildren().get(i), target, path + "." + i);
            if (found != null) return found;
        }
        return null;
    }

    public static boolean addOfflineKill(Quest source, UUID id, String path) {
        ServerPlayerManager players = ServerPlayerManager.INSTANCE;
        if (players == null || path == null || !eligibleCombatParticipant(source, id)) return false;
        Path dir = players.server().getWorldPath(LevelResource.PLAYER_DATA_DIR);
        CompoundTag all = ServerPlayerManager.readPlayerData(dir, id);
        if (all == null) return false;
        CompoundTag quest = all.getCompound(source.getId().toString());
        String[] steps = path.split("\\.");
        int index = Integer.parseInt(steps[0]);
        var objectives = quest.getList("objectives", Tag.TAG_COMPOUND);
        if (index >= objectives.size()) return false;
        CompoundTag node = objectives.getCompound(index);
        for (int i = 1; i < steps.length; i++) {
            int child = Integer.parseInt(steps[i]);
            if (node.contains("child", Tag.TAG_COMPOUND)) node = node.getCompound("child");
            else {
                var children = node.getList("children", Tag.TAG_COMPOUND);
                if (child >= children.size()) return false;
                node = children.getCompound(child);
            }
        }
        node.putInt("units", Math.min(Integer.MAX_VALUE, node.getInt("units") + 1));
        quest.putBoolean("group_reset_pending", false);
        return ServerPlayerManager.writePlayerData(dir, id, all);
    }

    public static void noteCombatReference(Quest source, String path, List<UUID> best) {
        ServerPlayerManager players = ServerPlayerManager.INSTANCE;
        if (players == null || AdventureGroups.INSTANCE == null || path == null) return;
        AdventureGroups.Group group = AdventureGroups.INSTANCE.membership(source.manager.player.getUUID());
        Set<UUID> members = group == null ? Set.of(source.manager.player.getUUID()) : group.members.keySet();
        Path dir = players.server().getWorldPath(LevelResource.PLAYER_DATA_DIR);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (UUID id : best) { CompoundTag row = new CompoundTag(); row.putUUID("id", id); list.add(row); }
        for (UUID member : members) {
            if (!eligibleCombatParticipant(source, member)) continue;
            ServerPlayer player = players.server().getPlayerList().getPlayer(member);
            QuestManager manager = player == null ? null : players.getManagerIfPresent(player);
            Quest quest = manager == null ? null : manager.getQuest(source.getId());
            if (quest != null) quest.setCombatReference(path, best);
            else if (player == null) {
                CompoundTag all = ServerPlayerManager.readPlayerData(dir, member);
                if (all == null) continue;
                CompoundTag saved = all.getCompound(source.getId().toString());
                saved.putString("combat_reference_path", path);
                saved.put("combat_reference", list.copy());
                ServerPlayerManager.writePlayerData(dir, member, all);
            }
        }
    }

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
                    if (quest.isActive() && quest.isVisibleToPlayer() && !quest.hasSentCompletion && !quest.isFailed())
                        participants.add(new Participant(member, quest, null, quest.isGroupResetPending()));
                } else if (player == null) {
                    CompoundTag all = ServerPlayerManager.readPlayerData(dir, member);
                    if (all == null) continue;
                    CompoundTag saved = all.getCompound(id.toString());
                    if (eligibleOffline(source, saved, globalDone))
                        participants.add(new Participant(member, null, all, saved.getBoolean("group_reset_pending")));
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

    static void applyObjective(Objective model, int index, int[] path, List<Participant> people,
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
            if (person.resetPending) continue;
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
        if (people.stream().allMatch(Participant::resetPending)) return;
        UUID sourceId = source.manager.player.getUUID();
        ServerPlayer validator = people.stream().anyMatch(person -> person.id.equals(sourceId) && !person.resetPending)
                && source.manager.player instanceof ServerPlayer player ? player
                : people.stream().filter(person -> !person.resetPending && person.online != null)
                .map(person -> players.server().getPlayerList().getPlayer(person.id)).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        String cycle = UUID.randomUUID().toString();
        AdventureGroups.Group group = AdventureGroups.INSTANCE.membership(source.manager.player.getUUID());
        UUID groupId = group == null ? source.manager.player.getUUID() : group.id;
        int policy = group == null ? 0 : group.lootPolicy;
        String[] allocations = new String[source.rewards.size()];
        if (GroupRewards.INSTANCE == null) return;
        for (int i = 0; i < source.rewards.size(); i++) {
            var reward = source.rewards.get(i);
            int distance = reward.beneficiaryDistance();
            Set<UUID> beneficiaries = eligibleBeneficiaries(people, distance, validator,
                    id -> players.server().getPlayerList().getPlayer(id));
            for (Participant person : people) {
                boolean eligible = beneficiaries.contains(person.id);
                if (person.online != null) person.online.rewards.get(i).setEligible(eligible);
                else if (person.saved != null) {
                    CompoundTag saved = person.saved.getCompound(source.getId().toString());
                    var rewards = saved.getList("rewards", Tag.TAG_COMPOUND);
                    if (i < rewards.size()) rewards.getCompound(i).putBoolean("eligible", eligible);
                }
            }
            if (reward.onePerGroup() && !beneficiaries.isEmpty()) {
                source.setRewardCycle(cycle);
                List<UUID> best = referencePlayers(source, reward.referenceObjective(), people, beneficiaries, players);
                UUID reference = best.isEmpty() ? validator == null ? null : validator.getUUID() : best.getFirst();
                allocations[i] = GroupRewards.INSTANCE.create(source, i, groupId, policy, beneficiaries,
                        reference, new LinkedHashSet<>(best));
                if (allocations[i] == null) return;
            }
        }
        for (Participant person : people) {
            if (person.online != null) {
                person.online.setRewardCycle(cycle);
                for (int i = 0; i < allocations.length; i++)
                    if (allocations[i] != null) person.online.rewards.get(i).setAllocationId(allocations[i]);
            } else if (person.saved != null) {
                CompoundTag saved = person.saved.getCompound(source.getId().toString());
                saved.putString("reward_cycle", cycle);
                var rewards = saved.getList("rewards", Tag.TAG_COMPOUND);
                for (int i = 0; i < allocations.length && i < rewards.size(); i++)
                    if (allocations[i] != null) rewards.getCompound(i).putString("allocation_id", allocations[i]);
            }
        }
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
                GroupRewards.INSTANCE.reconcile(person.online);
                players.save(person.online.manager);
            }
        }
        for (Participant person : people) {
            if (person.online == null) continue;
            QuestlogEvents.onQuestCompleted(new QuestEvent.Completed(person.online.manager.player, person.online, true));
            players.save(person.online.manager);
        }
        if (group != null) {
            ServerPlayer leader = players.server().getPlayerList().getPlayer(group.leader);
            if (leader != null) AdventureGroups.INSTANCE.sync(leader, "");
        }
        if (source.isGlobal()) players.onGlobalQuestUpdated(source);
    }

    static Set<UUID> eligibleBeneficiaries(List<Participant> people, int distance, ServerPlayer validator,
                                            java.util.function.Function<UUID, ServerPlayer> online) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Participant person : people) {
            ServerPlayer recipient = distance == 0 ? null : online.apply(person.id);
            if (distance == 0 || validator != null && recipient != null
                    && recipient.level() == validator.level()
                    && recipient.distanceToSqr(validator) <= (double) distance * distance) ids.add(person.id);
        }
        return ids;
    }

    private static List<UUID> referencePlayers(Quest source, int index, List<Participant> people,
                                                Set<UUID> beneficiaries, ServerPlayerManager players) {
        if (index < 0 || index >= source.objectives.size()) return List.of();
        Objective objective = source.objectives.get(index);
        if (objective instanceof org.infernalstudios.questlog.core.quests.objectives.entity.EntityKillObjective)
            return source.combatReference(Integer.toString(index)).stream().filter(beneficiaries::contains).toList();
        if (!(objective instanceof org.infernalstudios.questlog.core.quests.objectives.item.ItemCraftObjective)
                && !(objective instanceof ItemObtainObjective)) return List.of();
        long best = 0;
        List<UUID> tied = new ArrayList<>();
        for (Participant person : people) {
            if (!beneficiaries.contains(person.id)) continue;
            long amount;
            if (objective instanceof ItemObtainObjective obtain) {
                ServerPlayer player = players.server().getPlayerList().getPlayer(person.id);
                amount = player == null ? obtain.countOfflineInventory(players.server(), person.id) : obtain.countInventory(player);
            } else {
                amount = person.online == null
                        ? savedObjective(person.saved.getCompound(source.getId().toString()), index, new int[0]).getInt("units")
                        : person.online.objectives.get(index).getPersonalUnits();
            }
            if (amount > best) { best = amount; tied.clear(); tied.add(person.id); }
            else if (amount == best && amount > 0) tied.add(person.id);
        }
        return tied;
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
