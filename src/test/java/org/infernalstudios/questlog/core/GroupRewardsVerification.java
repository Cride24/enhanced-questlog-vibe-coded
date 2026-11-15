package org.infernalstudios.questlog.core;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.validation.QuestValidation;

import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class GroupRewardsVerification {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("questlog", "lot5_check");
    private static int checks;

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID(), veteran = UUID.randomUUID();
        Map<UUID, Float> nonfatal = Map.of(alice, 8f, bob, 4f);
        Map<UUID, Float> death = CombatContributions.withFatal(nonfatal, veteran, 20f, true);
        check(CombatContributions.bestEligible(death, id -> !id.equals(veteran)).equals(java.util.List.of(alice)),
                "Veteran fatal hit does not erase an eligible participant's damage");
        check(CombatContributions.withFatal(nonfatal, bob, 4f, false).get(bob) == 4f,
                "A cancelled fatal candidate is not counted");
        check(CombatContributions.bestEligible(CombatContributions.withFatal(nonfatal, bob, 4f, true),
                id -> true).size() == 2, "Fatal damage is counted once and ties remain visible");

        var personal = JsonParser.parseString("{\"title\":\"Test\",\"description\":\"Test\","
                + "\"objectives\":[{\"type\":\"read\"}],"
                + "\"rewards\":[{\"type\":\"experience\",\"experience\":1,\"one_per_group\":true}]}").getAsJsonObject();
        check(!QuestValidation.inspect(personal, QuestValidation.UNAVAILABLE).functional(),
                "Personal quests reject group-only reward rules");
        personal.addProperty("group_quest", true);
        var report = QuestValidation.inspect(personal, QuestValidation.UNAVAILABLE);
        check(report.functional(), "Group unique reward definition is valid: " + report.issues());

        var information = JsonParser.parseString("{\"title\":\"Secret\",\"description\":\"Find it\","
                + "\"rewards\":[{\"type\":\"information\",\"information\":\"X: 4, Y: 70, Z: -8 (Nether)\"}]}")
                .getAsJsonObject();
        check(QuestValidation.inspect(information, QuestValidation.UNAVAILABLE).functional(),
                "Authored information reward is valid");
        var publicDefinition = QuestSharing.visibleDefinition(information, false);
        check(!publicDefinition.toString().contains("X: 4") && QuestSharing.visibleDefinition(information, true).toString().contains("X: 4"),
                "Secret content is excluded from player definitions and retained for authors");
        check(QuestText.gameplay(information).equals(QuestText.gameplay(publicDefinition)),
                "Concealing information does not change quest progression structure");
        var revealedReward = new org.infernalstudios.questlog.core.quests.rewards.InformationReward(
                information.getAsJsonArray("rewards").get(0).getAsJsonObject());
        var revealedData = new net.minecraft.nbt.CompoundTag();
        revealedData.putBoolean("rewarded", true);
        revealedData.putString("revealed_information", "X: 4, Y: 70, Z: -8 (Nether)");
        revealedReward.deserialize(revealedData);
        check(revealedReward.revealed().equals("X: 4, Y: 70, Z: -8 (Nether)")
                && revealedReward.serialize().getString("revealed_information").equals(revealedReward.revealed()),
                "Collected information persists across save and load");
        revealedReward.revokeReward();
        check(revealedReward.revealed().isEmpty(), "Reset hides the previous information");
        var nestedInformation = JsonParser.parseString("{\"title\":\"Test\",\"description\":\"Test\","
                + "\"rewards\":[{\"type\":\"choice\",\"choices\":[{\"type\":\"information\",\"information\":\"Secret\"}]}]}")
                .getAsJsonObject();
        check(!QuestValidation.inspect(nestedInformation, QuestValidation.UNAVAILABLE).functional(),
                "Information inside a choice is rejected because child receipts are not durable");
        var examples = java.nio.file.Path.of(System.getProperty("questlog.lot5.examples"));
        try (var files = Files.list(examples)) {
            for (var example : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var definition = JsonParser.parseString(Files.readString(example)).getAsJsonObject();
                var validation = QuestValidation.inspect(definition, QuestValidation.UNAVAILABLE);
                check(validation.functional(), "Example " + example.getFileName() + " is valid: " + validation.issues());
            }
        }

        var file = Files.createTempDirectory("questlog-lot5-").resolve("rewards.dat");
        GroupRewards rewards = new GroupRewards(file);
        Quest one = quest();
        one.setRewardCycle("first-cycle"); one.hasSentCompletion = true;
        String allocation = rewards.create(one, 0, alice, 0, Set.of(alice, bob), alice, Set.of(alice));
        check(allocation != null, "Allocation is saved before collection");
        one.rewards.getFirst().setAllocationId(allocation);
        check(!rewards.claim(one, 0, bob), "A reserved reward cannot be stolen");
        check(rewards.claim(one, 0, alice), "Reserved recipient can collect");
        check(!rewards.claim(one, 0, alice), "Double click cannot collect twice");
        check(!new GroupRewards(file).claim(one, 0, bob), "Consumed allocation stays consumed after reload");
        one.resetProgress();
        check(one.isResetCycle() && !one.isCompleted() && one.rewardCycle().length() == 36
                && one.rewards.getFirst().allocationId().isEmpty(), "Reset clears completion and old allocation");
        one.hasSentCompletion = true;
        check(!rewards.claim(one, 0, alice), "Old allocation cannot be replayed in a new cycle");

        AdventureGroups groups = new AdventureGroups(Files.createTempDirectory("questlog-lot5-groups-").resolve("groups.dat"));
        AdventureGroups.Group party = new AdventureGroups.Group(UUID.randomUUID(), "Party", alice);
        party.members.put(alice, "Alice"); party.members.put(bob, "Bob");
        groups.register(party);
        AdventureGroups.INSTANCE = groups;
        check(!GroupRewards.mayStartLottery(party, Set.of(bob), bob, true)
                && GroupRewards.mayStartLottery(party, Set.of(bob), bob, false),
                "An eligible member may start only while the leader is offline");
        check(!GroupRewards.mayStartLottery(party, Set.of(alice), bob, false)
                && !GroupRewards.mayStartLottery(party, Set.of(bob), veteran, false)
                && GroupRewards.mayStartLottery(party, Set.of(bob), alice, true),
                "Offline fallback excludes non-beneficiaries and outsiders but preserves leader control");
        Quest lottery = quest(); lottery.setRewardCycle("lottery-cycle"); lottery.hasSentCompletion = true;
        String drawId = rewards.create(lottery, 0, party.id, 1, Set.of(alice, bob), null, Set.of());
        lottery.rewards.getFirst().setAllocationId(drawId);
        check(rewards.pendingForLeader(party.id).getCompound(0).getList("beneficiaries", 10).size() == 2,
                "Leader sees both eligible members before starting the vote");
        check(!rewards.start(lottery, 0, bob) && rewards.start(lottery, 0, alice),
                "Only the leader starts the 120-second lottery");
        check(!rewards.canClaim(lottery, 0, bob), "A reward waiting for votes is not ready to collect");
        check(rewards.respond(lottery, 0, bob, true) && !rewards.respond(lottery, 0, bob, true),
                "One timely yes per beneficiary");
        check(!rewards.claim(lottery, 0, bob), "No collection while voting is active");
        GroupRewards restartedVoting = new GroupRewards(file);
        check(restartedVoting.respond(lottery, 0, alice, false), "The last response ends the vote without waiting two minutes");
        var saved = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        var savedDraw = saved.getList("allocations", 10).stream().map(row -> (net.minecraft.nbt.CompoundTag) row)
                .filter(row -> drawId.equals(row.getString("id"))).findFirst().orElseThrow();
        check(savedDraw.getList("rounds", 10).size() == 1
                && savedDraw.getList("rounds", 10).getCompound(0).getList("rolls", 10).size() == 1,
                "Every completed draw is stored for audit across restart");
        check(!savedDraw.contains("notices"), "Lottery messages are not queued for offline members");
        check(restartedVoting.claim(lottery, 0, bob) && !restartedVoting.claim(lottery, 0, alice),
                "Only the sole volunteer wins immediately");
        check(!new GroupRewards(file).claim(lottery, 0, bob), "Lottery consumption survives restart");
        rewards = new GroupRewards(file);

        Quest empty = quest(); empty.setRewardCycle("no-volunteers"); empty.hasSentCompletion = true;
        String emptyId = rewards.create(empty, 0, party.id, 1, Set.of(alice, bob), null, Set.of());
        empty.rewards.getFirst().setAllocationId(emptyId);
        check(rewards.start(empty, 0, alice), "Leader starts another cycle independently");
        check(rewards.respond(empty, 0, alice, false) && rewards.respond(empty, 0, bob, false),
                "Two no responses close the lottery immediately");
        check(rewards.claim(empty, 0, bob) && !rewards.claim(empty, 0, alice),
                "No volunteers leaves one open copy for the first eligible claimant");

        Quest timed = quest(); timed.setRewardCycle("timed"); timed.hasSentCompletion = true;
        String timedId = rewards.create(timed, 0, party.id, 1, Set.of(alice, bob), null, Set.of());
        timed.rewards.getFirst().setAllocationId(timedId);
        check(rewards.start(timed, 0, alice) && rewards.respond(timed, 0, bob, true),
                "An unanswered candidate leaves the vote open");
        rewards.tick(System.currentTimeMillis() + 120_001);
        check(rewards.claim(timed, 0, bob), "The deadline still resolves an incomplete vote");

        Quest designated = quest(); designated.setRewardCycle("designated"); designated.hasSentCompletion = true;
        String designatedId = rewards.create(designated, 0, party.id, 2, Set.of(alice, bob), null, Set.of());
        designated.rewards.getFirst().setAllocationId(designatedId);
        check(!rewards.designate(designated, 0, bob, bob) && !rewards.designate(designated, 0, alice, veteran),
                "Member cannot designate and leader cannot designate an ineligible player");
        check(!rewards.canClaim(designated, 0, bob), "Undesignated reward is absent from the login reminder");
        check(rewards.designate(designated, 0, alice, bob), "Leader designates the eligible recipient");
        check(rewards.canClaim(designated, 0, bob) && !rewards.canClaim(designated, 0, alice),
                "Only the designated recipient counts as able to collect the reward");
        var assignedRows = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap())
                .getList("allocations", 10);
        var assigned = assignedRows.stream().map(row -> (net.minecraft.nbt.CompoundTag) row)
                .filter(row -> designatedId.equals(row.getString("id"))).findFirst().orElseThrow();
        check(!assigned.contains("notices"), "Leader assignment messages are not queued for offline members");
        check(!rewards.claim(designated, 0, alice) && rewards.claim(designated, 0, bob),
                "Leader reserves the copy for the designated member");

        LinkedHashSet<UUID> tied = new LinkedHashSet<>(); tied.add(alice); tied.add(bob);
        int[] sequence = {100, 100, 0, 1}; AtomicInteger next = new AtomicInteger();
        GroupRewards.Draw result = GroupRewards.draw(tied, () -> sequence[next.getAndIncrement()]);
        check(result.winner().equals(bob) && result.score() == 1 && next.get() == 4,
                "Best-score tie rerolls only tied players without new responses");
        check(result.rounds().size() == 2 && result.rounds().get(0).get(alice) == 100
                && result.rounds().get(0).get(bob) == 100
                && result.rounds().get(1).get(alice) == 0 && result.rounds().get(1).get(bob) == 1,
                "Every roll is retained in order for a visible audit before the result");
        Quest lateLeader = quest(); lateLeader.setRewardCycle("late-leader");
        String lateId = rewards.create(lateLeader, 0, party.id, 1, Set.of(bob), null, Set.of());
        check(rewards.startById(lateId, ID, 0, alice),
                "Leader can start a group decision even without personal completion");
        Quest cancelled = quest(); cancelled.setRewardCycle("old-reset"); cancelled.hasSentCompletion = true;
        String cancelledId = rewards.create(cancelled, 0, party.id, 1, Set.of(alice, bob), null, Set.of());
        cancelled.rewards.getFirst().setAllocationId(cancelledId);
        check(rewards.cancelPendingForQuest(ID)
                && new GroupRewards(file).pendingForLeader(party.id).isEmpty(),
                "Resetting all saved players removes stale leader actions");
        AdventureGroups.INSTANCE = null;
        System.out.println("Lot 5: " + checks + " headless checks passed; no Minecraft session was run.");
    }

    private static Quest quest() {
        QuestManager manager = new QuestManager(null) {
            @Override public boolean isClient() { return true; }
            @Override public void sync(ResourceLocation id) {}
        };
        return Quest.create(JsonParser.parseString("{\"title\":\"Test\",\"description\":\"Test\","
                + "\"group_quest\":true,\"objectives\":[{\"type\":\"read\"}],"
                + "\"rewards\":[{\"type\":\"experience\",\"experience\":1,\"one_per_group\":true}]}")
                .getAsJsonObject(), ID, manager);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
