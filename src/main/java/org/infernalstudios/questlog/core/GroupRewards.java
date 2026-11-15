package org.infernalstudios.questlog.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.rewards.Reward;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/** One server-owned decision per group reward and quest completion cycle. */
public final class GroupRewards {
    public static GroupRewards INSTANCE;
    public static final int READY = 0, VOTING = 1, WON = 2, OPEN = 3, CONSUMED = 4;
    private final MinecraftServer server;
    private final Path file;
    private final Map<String, Allocation> allocations = new LinkedHashMap<>();
    private boolean loadFailed;

    private static final class Allocation {
        String id, quest, cycle;
        UUID group, winner;
        int index, policy, state;
        int score = -1;
        boolean tie;
        long deadline;
        final Set<UUID> beneficiaries = new HashSet<>(), candidates = new HashSet<>(), yes = new HashSet<>(), answered = new HashSet<>();
        final List<Map<UUID, Integer>> rounds = new ArrayList<>();
    }

    public GroupRewards(MinecraftServer server) {
        this.server = server;
        this.file = server.getWorldPath(LevelResource.ROOT).resolve("data/questlog_rewards.dat");
        load();
    }

    GroupRewards(Path file) {
        this.server = null;
        this.file = file;
        load();
    }

    private static ListTag ids(Set<UUID> values) {
        ListTag list = new ListTag();
        for (UUID id : values) { CompoundTag row = new CompoundTag(); row.putUUID("id", id); list.add(row); }
        return list;
    }

    private static void readIds(CompoundTag row, String key, Set<UUID> values) {
        for (Tag item : row.getList(key, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) item;
            if (entry.hasUUID("id")) values.add(entry.getUUID("id"));
        }
    }

    private CompoundTag write() {
        CompoundTag root = new CompoundTag();
        ListTag rows = new ListTag();
        for (Allocation a : allocations.values()) {
            CompoundTag row = new CompoundTag();
            row.putString("id", a.id); row.putString("quest", a.quest); row.putString("cycle", a.cycle);
            row.putUUID("group", a.group); row.putInt("index", a.index);
            row.putInt("policy", a.policy); row.putInt("state", a.state); row.putLong("deadline", a.deadline);
            row.putInt("score", a.score); row.putBoolean("tie", a.tie);
            if (a.winner != null) row.putUUID("winner", a.winner);
            row.put("beneficiaries", ids(a.beneficiaries)); row.put("candidates", ids(a.candidates));
            row.put("yes", ids(a.yes)); row.put("answered", ids(a.answered));
            ListTag rounds = new ListTag();
            for (Map<UUID, Integer> round : a.rounds) {
                CompoundTag roundTag = new CompoundTag();
                ListTag rolls = new ListTag();
                for (var roll : round.entrySet()) {
                    CompoundTag result = new CompoundTag();
                    result.putUUID("id", roll.getKey()); result.putInt("score", roll.getValue()); rolls.add(result);
                }
                roundTag.put("rolls", rolls); rounds.add(roundTag);
            }
            row.put("rounds", rounds);
            rows.add(row);
        }
        root.put("allocations", rows);
        return root;
    }

    private void read(Path source) throws IOException {
        CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.unlimitedHeap());
        if (!root.contains("allocations", Tag.TAG_LIST)) throw new IOException("Missing allocations");
        Map<String, Allocation> loaded = new LinkedHashMap<>();
        for (Tag item : root.getList("allocations", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) item;
            Allocation a = new Allocation();
            a.id = row.getString("id"); a.quest = row.getString("quest"); a.cycle = row.getString("cycle");
            if (a.id.isBlank() || ResourceLocation.tryParse(a.quest) == null || !row.hasUUID("group"))
                throw new IOException("Invalid allocation");
            a.group = row.getUUID("group"); a.index = row.getInt("index");
            a.policy = row.getInt("policy"); a.state = row.getInt("state"); a.deadline = row.getLong("deadline");
            a.score = row.contains("score") ? row.getInt("score") : -1; a.tie = row.getBoolean("tie");
            if (row.hasUUID("winner")) a.winner = row.getUUID("winner");
            readIds(row, "beneficiaries", a.beneficiaries); readIds(row, "candidates", a.candidates);
            readIds(row, "yes", a.yes); readIds(row, "answered", a.answered);
            for (Tag roundItem : row.getList("rounds", Tag.TAG_COMPOUND)) {
                Map<UUID, Integer> round = new LinkedHashMap<>();
                for (Tag rollItem : ((CompoundTag) roundItem).getList("rolls", Tag.TAG_COMPOUND)) {
                    CompoundTag roll = (CompoundTag) rollItem;
                    if (!roll.hasUUID("id") || roll.getInt("score") < 0 || roll.getInt("score") > 100)
                        throw new IOException("Invalid lottery roll");
                    round.put(roll.getUUID("id"), roll.getInt("score"));
                }
                a.rounds.add(round);
            }
            if (a.state < READY || a.state > CONSUMED || loaded.putIfAbsent(a.id, a) != null)
                throw new IOException("Duplicate or invalid allocation state");
        }
        allocations.clear(); allocations.putAll(loaded);
    }

    private void load() {
        if (!Files.exists(file)) return;
        try { read(file); }
        catch (Exception primary) {
            Questlog.LOGGER.error("Cannot load group rewards; trying backup", primary);
            try {
                Path backup = file.resolveSibling(file.getFileName() + "_old");
                read(backup);
                Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (Exception secondary) {
                allocations.clear(); loadFailed = true;
                Questlog.LOGGER.error("Cannot load group reward backup; claims disabled", secondary);
            }
        }
    }

    private boolean save() {
        if (loadFailed) return false;
        Path temp = null;
        try {
            Files.createDirectories(file.getParent());
            temp = Files.createTempFile(file.getParent(), "questlog-rewards-", ".dat");
            NbtIo.writeCompressed(write(), temp);
            net.minecraft.Util.safeReplaceFile(file, temp,
                    file.resolveSibling(file.getFileName() + "_old"));
            return true;
        } catch (IOException e) {
            Questlog.LOGGER.error("Cannot save group rewards", e);
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            return false;
        }
    }

    public String create(Quest quest, int index, UUID group, int policy, Set<UUID> beneficiaries,
                         UUID reference, Set<UUID> tied) {
        if (loadFailed || beneficiaries.isEmpty()) return null;
        Allocation a = new Allocation();
        a.id = UUID.randomUUID().toString(); a.quest = quest.getId().toString(); a.cycle = quest.rewardCycle();
        a.index = index; a.group = group; a.policy = policy;
        a.beneficiaries.addAll(beneficiaries);
        if (policy == 0 && reference != null && beneficiaries.contains(reference)) {
            if (tied.size() > 1) {
                a.policy = 1;
                a.tie = true;
                a.candidates.addAll(tied); a.candidates.retainAll(beneficiaries);
            } else { a.winner = reference; a.state = WON; }
        }
        if (a.policy == 1 && a.candidates.isEmpty()) a.candidates.addAll(beneficiaries);
        allocations.put(a.id, a);
        if (!save()) { allocations.remove(a.id); return null; }
        return a.id;
    }

    private Allocation lookup(Quest quest, int index) {
        if (loadFailed || index < 0 || index >= quest.rewards.size()) return null;
        Reward reward = quest.rewards.get(index);
        Allocation a = allocations.get(reward.allocationId());
        return a != null && a.index == index && a.quest.equals(quest.getId().toString())
                && a.cycle.equals(quest.rewardCycle()) && quest.hasSentCompletion ? a : null;
    }

    private Allocation checked(Quest quest, int index, UUID actor) {
        Allocation a = lookup(quest, index);
        return a != null && a.beneficiaries.contains(actor) && quest.rewards.get(index).isEligible() ? a : null;
    }

    public boolean claim(Quest quest, int index, UUID actor) {
        Allocation a = checked(quest, index, actor);
        if (!claimable(a, actor)) return false;
        int previous = a.state;
        UUID previousWinner = a.winner;
        if (a.state == READY) {
            a.state = OPEN;
        }
        a.winner = actor; a.state = CONSUMED;
        if (!save()) { a.state = previous; a.winner = previousWinner; return false; }
        refreshViews(a);
        return true;
    }

    public boolean canClaim(Quest quest, int index, UUID actor) {
        return claimable(checked(quest, index, actor), actor);
    }

    private static boolean claimable(Allocation a, UUID actor) {
        if (a == null || a.state == CONSUMED || a.state == VOTING
                || a.winner != null && !a.winner.equals(actor)) return false;
        AdventureGroups.Group group = AdventureGroups.INSTANCE == null ? null : AdventureGroups.INSTANCE.group(a.group);
        return a.state != READY || group == null || a.policy != 1 && a.policy != 2;
    }

    private boolean leader(Allocation a, UUID actor) {
        AdventureGroups.Group group = AdventureGroups.INSTANCE == null ? null : AdventureGroups.INSTANCE.group(a.group);
        return group != null && group.leader.equals(actor);
    }

    static boolean mayStartLottery(AdventureGroups.Group group, Set<UUID> beneficiaries,
                                   UUID actor, boolean leaderOnline) {
        return group != null && (group.leader.equals(actor)
                || !leaderOnline && group.members.containsKey(actor) && beneficiaries.contains(actor));
    }

    public ListTag pendingForLeader(UUID groupId) {
        ListTag rows = new ListTag();
        for (Allocation a : allocations.values()) if (a.group.equals(groupId)
                && (a.state == READY || a.state == VOTING) && (a.policy == 1 || a.policy == 2)) {
            CompoundTag row = new CompoundTag();
            row.putString("quest", a.quest); row.putInt("index", a.index);
            row.putString("allocation", a.id); row.putInt("policy", a.policy); row.putInt("state", a.state);
            row.put("beneficiaries", ids(a.beneficiaries));
            rows.add(row);
        }
        return rows;
    }

    /** A reset for every saved player discards decisions from the previous quest cycles. */
    public boolean cancelPendingForQuest(ResourceLocation quest) {
        if (loadFailed) return false;
        Map<String, Allocation> removed = new LinkedHashMap<>();
        for (var entry : allocations.entrySet())
            if (entry.getValue().quest.equals(quest.toString()) && entry.getValue().state != CONSUMED)
                removed.put(entry.getKey(), entry.getValue());
        if (removed.isEmpty()) return true;
        removed.keySet().forEach(allocations::remove);
        if (!save()) { allocations.putAll(removed); return false; }
        if (server != null && AdventureGroups.INSTANCE != null) for (Allocation allocation : removed.values()) {
            AdventureGroups.Group group = AdventureGroups.INSTANCE.group(allocation.group);
            ServerPlayer leader = group == null ? null : server.getPlayerList().getPlayer(group.leader);
            if (leader != null) AdventureGroups.INSTANCE.sync(leader, "");
        }
        return true;
    }

    private Allocation action(String allocationId, ResourceLocation quest, int index) {
        Allocation a = allocations.get(allocationId);
        return !loadFailed && a != null && a.quest.equals(quest.toString()) && a.index == index ? a : null;
    }

    public boolean startById(String allocationId, ResourceLocation quest, int index, UUID actor) {
        Allocation a = action(allocationId, quest, index);
        if (a == null || a.state != READY || a.policy != 1) return false;
        AdventureGroups.Group group = AdventureGroups.INSTANCE == null ? null : AdventureGroups.INSTANCE.group(a.group);
        boolean leaderOnline = server == null || group != null && AdventureGroups.INSTANCE.isOnline(group.leader);
        if (!mayStartLottery(group, a.beneficiaries, actor, leaderOnline)) return false;
        if (!group.leader.equals(actor)) {
            ServerPlayer player = server.getPlayerList().getPlayer(actor);
            QuestManager manager = player == null ? null : ServerPlayerManager.INSTANCE.getManagerIfPresent(player);
            Quest current = manager == null ? null : manager.getQuest(quest);
            if (current == null || checked(current, index, actor) != a) return false;
        }
        a.state = VOTING; a.deadline = System.currentTimeMillis() + 120_000;
        if (!save()) { a.state = READY; a.deadline = 0; return false; }
        refreshViews(a);
        return true;
    }

    public boolean start(Quest quest, int index, UUID actor) {
        Allocation a = lookup(quest, index);
        return a != null && startById(a.id, quest.getId(), index, actor);
    }

    public boolean respond(Quest quest, int index, UUID actor, boolean yes) {
        Allocation a = checked(quest, index, actor);
        if (a == null || a.state != VOTING || System.currentTimeMillis() >= a.deadline
                || !a.candidates.contains(actor) || a.answered.contains(actor)) return false;
        a.answered.add(actor);
        if (yes) a.yes.add(actor);
        if (!save()) { a.answered.remove(actor); a.yes.remove(actor); return false; }
        if (a.answered.containsAll(a.candidates)) finish(a);
        else refreshViews(a);
        return true;
    }

    public boolean designate(Quest quest, int index, UUID actor, UUID target) {
        Allocation a = lookup(quest, index);
        return a != null && designateById(a.id, quest.getId(), index, actor, target);
    }

    public boolean designateById(String allocationId, ResourceLocation quest, int index, UUID actor, UUID target) {
        Allocation a = action(allocationId, quest, index);
        if (a == null || a.state != READY || a.policy != 2 || !leader(a, actor)
                || !a.beneficiaries.contains(target)
                || AdventureGroups.INSTANCE.group(a.group) == null
                || !AdventureGroups.INSTANCE.group(a.group).members.containsKey(target)) return false;
        a.winner = target; a.state = WON;
        if (!save()) { a.winner = null; a.state = READY; return false; }
        notifyOnlineMembers(a);
        refreshViews(a);
        deliverAuto(a);
        return true;
    }

    public void tick() {
        tick(System.currentTimeMillis());
    }

    void tick(long now) {
        for (Allocation a : allocations.values()) if (a.state == VOTING && now >= a.deadline) finish(a);
    }

    record Draw(UUID winner, int score, List<Map<UUID, Integer>> rounds) {}

    static Draw draw(Set<UUID> volunteers, IntSupplier roll) {
        List<UUID> tied = new ArrayList<>(volunteers);
        List<Map<UUID, Integer>> rounds = new ArrayList<>();
        if (tied.isEmpty()) return new Draw(null, -1, rounds);
        int winningScore = -1;
        while (tied.size() > 1) {
            int best = -1;
            List<UUID> next = new ArrayList<>();
            Map<UUID, Integer> round = new LinkedHashMap<>();
            for (UUID id : tied) {
                int score = roll.getAsInt();
                if (score < 0 || score > 100) throw new IllegalArgumentException("Lottery score outside 0..100");
                round.put(id, score);
                if (score > best) { best = score; next.clear(); next.add(id); }
                else if (score == best) next.add(id);
            }
            rounds.add(round);
            winningScore = best;
            tied = next;
        }
        if (winningScore < 0) {
            winningScore = roll.getAsInt();
            if (winningScore < 0 || winningScore > 100) throw new IllegalArgumentException("Lottery score outside 0..100");
            rounds.add(Map.of(tied.getFirst(), winningScore));
        }
        return new Draw(tied.getFirst(), winningScore, rounds);
    }

    private void finish(Allocation a) {
        int previousState = a.state;
        UUID previousWinner = a.winner;
        int previousScore = a.score;
        Draw draw = draw(a.yes, () -> ThreadLocalRandom.current().nextInt(101));
        a.score = draw.score;
        a.rounds.clear(); a.rounds.addAll(draw.rounds);
        if (draw.winner == null) a.state = OPEN;
        else { a.winner = draw.winner; a.state = WON; }
        if (!save()) { a.state = previousState; a.winner = previousWinner; a.score = previousScore;
            a.rounds.clear(); return; }
        notifyOnlineMembers(a);
        refreshViews(a);
        deliverAuto(a);
    }

    private void notifyOnlineMembers(Allocation a) {
        AdventureGroups.Group group = AdventureGroups.INSTANCE == null ? null : AdventureGroups.INSTANCE.group(a.group);
        if (server != null && group != null) for (UUID member : group.members.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) notifyResult(a, player);
        }
    }

    private void notifyResult(Allocation a, ServerPlayer player) {
        AdventureGroups.Group group = AdventureGroups.INSTANCE == null ? null : AdventureGroups.INSTANCE.group(a.group);
        if (a.policy == 1) {
            for (int i = 0; i < a.rounds.size(); i++) {
                java.util.StringJoiner rolls = new java.util.StringJoiner(", ");
                for (var roll : a.rounds.get(i).entrySet())
                    rolls.add((group == null ? roll.getKey().toString()
                            : group.members.getOrDefault(roll.getKey(), roll.getKey().toString())) + ": " + roll.getValue());
                player.sendSystemMessage(Component.translatable("questlog.reward.lottery_round", i + 1, rolls.toString()));
            }
        }
        String winner = a.winner == null ? "" : group == null ? a.winner.toString()
                : group.members.getOrDefault(a.winner, a.winner.toString());
        if (a.policy == 2) player.sendSystemMessage(Component.translatable("questlog.reward.assignment_result", winner));
        else player.sendSystemMessage(Component.translatable("questlog.reward.lottery_result",
                    a.winner == null ? Component.translatable("questlog.reward.no_volunteer") : Component.literal(winner),
                    a.score < 0 ? "—" : Integer.toString(a.score)));
    }

    private void deliverAuto(Allocation a) {
        if (server == null || a.state != WON || a.winner == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(a.winner);
        QuestManager manager = player == null ? null : ServerPlayerManager.INSTANCE.getManagerIfPresent(player);
        Quest quest = manager == null ? null : manager.getQuest(ResourceLocation.tryParse(a.quest));
        if (quest == null || a.index >= quest.rewards.size()) return;
        Reward reward = quest.rewards.get(a.index);
        if (!reward.isAutoClaim() || reward.hasRewarded() || !reward.canDeliver(player)
                || reward instanceof org.infernalstudios.questlog.core.quests.rewards.ChoiceReward choice
                && !choice.canClaim()) return;
        if (claim(quest, a.index, a.winner)) {
            reward.applyReward(player);
            ServerPlayerManager.INSTANCE.save(manager);
        }
    }

    public void reconcile(Quest quest) {
        if (quest == null || !quest.hasSentCompletion) return;
        UUID player = quest.manager.player.getUUID();
        for (Reward reward : quest.rewards) if (!reward.allocationId().isEmpty()) {
            Allocation a = allocations.get(reward.allocationId());
            if (a == null || !a.cycle.equals(quest.rewardCycle())) { reward.setEligible(false); continue; }
            reward.setAllocationState(a.state, a.deadline, a.candidates.contains(player), a.answered.contains(player),
                    a.policy, a.beneficiaries, a.winner, a.tie);
            if (a.state == CONSUMED && !player.equals(a.winner)) reward.setEligible(false);
        }
    }

    private void refreshViews(Allocation a) {
        if (server == null) return;
        ResourceLocation id = ResourceLocation.tryParse(a.quest);
        if (id == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestManager manager = ServerPlayerManager.INSTANCE.getManagerIfPresent(player);
            Quest quest = manager == null ? null : manager.getQuest(id);
            if (quest == null || a.index >= quest.rewards.size()
                    || !a.id.equals(quest.rewards.get(a.index).allocationId())) continue;
            reconcile(quest);
            ServerPlayerManager.INSTANCE.save(manager);
            manager.syncIndividual(id);
        }
        AdventureGroups.Group group = AdventureGroups.INSTANCE == null ? null : AdventureGroups.INSTANCE.group(a.group);
        if (group != null) {
            ServerPlayer leader = server.getPlayerList().getPlayer(group.leader);
            if (leader != null) AdventureGroups.INSTANCE.sync(leader, "");
        }
    }
}
