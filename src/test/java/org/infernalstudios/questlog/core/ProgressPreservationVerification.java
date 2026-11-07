package org.infernalstudios.questlog.core;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.rewards.ChoiceReward;
import org.infernalstudios.questlog.core.quests.rewards.RandomReward;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

/** Headless checks for old saves, edited definitions, targeted resets and global inheritance. */
public final class ProgressPreservationVerification {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("questlog", "first");
    private static final ResourceLocation OTHER = ResourceLocation.fromNamespaceAndPath("questlog", "other");

    public static void main(String[] args) throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        Quest finished = quest(1, 5, false, false);
        finished.objectives.get(0).forceSetUnits(1);
        finished.hasSentCompletion = true;
        finished.rewards.get(0).setRewarded(true);
        CompoundTag oldSave = finished.serialize();

        Quest edited = quest(2, 10, false, false);
        edited.deserialize(oldSave);
        check(edited.isCompleted(), "An acquired completion survives an objective edit");
        check(edited.isRewarded(), "A collected reward stays collected after its definition changes");

        CompoundTag legacy = oldSave.copy();
        legacy.remove("_structure");
        Quest sameDefinition = quest(1, 5, false, false);
        sameDefinition.deserialize(legacy);
        check(sameDefinition.isCompleted() && sameDefinition.isRewarded(), "An old save remains readable");

        Quest partial = quest(2, 5, false, false);
        partial.objectives.get(0).forceSetUnits(1);
        Quest editedPartial = quest(2, 10, false, false);
        editedPartial.deserialize(partial.serialize());
        check(editedPartial.objectives.get(0).getUnits() == 1 && !editedPartial.isCompleted(),
                "Compatible progress survives a reward edit");
        Quest changedObjective = quest(3, 10, false, false);
        changedObjective.deserialize(partial.serialize());
        check(changedObjective.objectives.get(0).getUnits() == 0 && !changedObjective.isCompleted(),
                "Changed objectives of an unfinished quest use the new definition");

        CompoundTag player = new CompoundTag();
        player.put(ID.toString(), oldSave);
        CompoundTag otherProgress = new CompoundTag();
        otherProgress.putBoolean("completed", true);
        player.put(OTHER.toString(), otherProgress);
        ServerPlayerManager.resetSavedQuest(player, ID, false);
        Quest reset = quest(1, 5, false, false);
        reset.deserialize(player.getCompound(ID.toString()));
        check(!reset.isCompleted() && !reset.isRewarded(), "A targeted reset permits replay and reward collection");
        check(player.getCompound(OTHER.toString()).equals(otherProgress), "A neighbouring quest stays untouched");
        finished.deserialize(player.getCompound(ID.toString()));
        check(!finished.isCompleted() && !finished.isRewarded() && finished.objectives.get(0).getUnits() == 0,
                "An offline reset clears stale in-memory progress on load");
        ChoiceReward choice = new ChoiceReward(JsonParser.parseString("{\"choices\":[{\"type\":\"questlog:experience\",\"experience\":1}]}").getAsJsonObject());
        choice.getChoices().get(0).setRewarded(true);
        choice.deserialize(new CompoundTag());
        check(!choice.getChoices().get(0).hasRewarded(), "An empty save clears a nested choice receipt");
        RandomReward random = new RandomReward(JsonParser.parseString("{\"entries\":[{\"reward\":{\"type\":\"questlog:experience\",\"experience\":1}}]}").getAsJsonObject());
        random.getEntries().get(0).setRewarded(true);
        random.deserialize(new CompoundTag());
        check(!random.getEntries().get(0).hasRewarded(), "An empty save clears a nested random receipt");

        Path tempDir = Files.createTempDirectory("questlog-progress-");
        UUID offlineId = UUID.randomUUID();
        try {
            check(ServerPlayerManager.writePlayerData(tempDir, offlineId, player), "Write an offline player's save");
            CompoundTag offline = ServerPlayerManager.readPlayerData(tempDir, offlineId);
            ServerPlayerManager.resetSavedQuest(offline, ID, true);
            check(ServerPlayerManager.writePlayerData(tempDir, offlineId, offline), "Save a targeted offline reset");
            CompoundTag reconnected = ServerPlayerManager.readPlayerData(tempDir, offlineId);
            check(reconnected.getCompound(OTHER.toString()).equals(otherProgress), "Offline reset preserves other quests on disk");
            Quest afterLogin = quest(1, 5, false, true);
            afterLogin.deserialize(reconnected.getCompound(ID.toString()));
            CompoundTag inheritedCompletion = new CompoundTag();
            CompoundTag completedGlobal = new CompoundTag();
            completedGlobal.putBoolean("completed", true);
            inheritedCompletion.put(ID.toString(), completedGlobal);
            check(!ServerPlayerManager.mergeGlobalCompletion(afterLogin, inheritedCompletion) && !afterLogin.isCompleted(),
                    "Offline UUID reset remains effective after file reload and global inheritance");
        } finally {
            try (Stream<Path> files = Files.list(tempDir)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Files.delete(tempDir);
        }

        CompoundTag globalData = new CompoundTag();
        CompoundTag completion = new CompoundTag();
        completion.putBoolean("completed", true);
        completion.put("rewards", oldSave.get("rewards"));
        globalData.put(ID.toString(), completion);
        Quest globalSource = quest(1, 5, false, true);
        globalSource.objectives.get(0).forceSetUnits(1);
        globalSource.hasSentCompletion = true;
        check(globalSource.canClaimRewards(), "The player completing a global quest can claim its reward");
        Quest inherited = quest(1, 5, false, true);
        check(ServerPlayerManager.mergeGlobalCompletion(inherited, globalData) && inherited.isCompleted(),
                "Global completion reaches an existing or new player");
        check(!inherited.isRewarded(), "Global completion does not copy another player's reward receipt");
        check(!inherited.canClaimRewards(), "An inherited global completion does not grant a reward");
        Quest inheritedAfterLogin = quest(1, 5, false, true);
        inheritedAfterLogin.deserialize(inherited.serialize());
        check(!inheritedAfterLogin.canClaimRewards(), "Reward restriction survives reconnection");
        inheritedAfterLogin.resetProgress(true);
        inheritedAfterLogin.objectives.get(0).forceSetUnits(1);
        check(inheritedAfterLogin.canClaimRewards() && inheritedAfterLogin.isCompleted(),
                "A personally reset global quest can be completed for its reward");
        ServerPlayerManager.resetSavedQuest(player, ID, true);
        Quest personalReset = quest(1, 5, false, true);
        personalReset.deserialize(player.getCompound(ID.toString()));
        check(!ServerPlayerManager.mergeGlobalCompletion(personalReset, globalData) && !personalReset.isCompleted(),
                "A personal reset survives global completion on reconnection");
        ServerPlayerManager.resetSavedQuest(player, ID, false);
        Quest resetForAll = quest(1, 5, false, true);
        resetForAll.deserialize(player.getCompound(ID.toString()));
        globalData.remove(ID.toString());
        check(!ServerPlayerManager.mergeGlobalCompletion(resetForAll, globalData) && !resetForAll.isCompleted(),
                "Reset for all clears the inherited completion");
        check(resetForAll.canClaimRewards(), "Reset for all opens reward eligibility for the next completion");

        Quest repeatable = quest(1, 5, true, false);
        repeatable.deserialize(oldSave);
        repeatable.resetProgress();
        check(!repeatable.isCompleted() && !repeatable.isRewarded(), "Repeatable quests still restart normally");
        System.out.println("Progress preservation: headless checks passed; no in-game test was run.");
    }

    private static Quest quest(int amount, int experience, boolean repeatable, boolean global) {
        String json = "{\"title\":\"Test\",\"description\":\"Test\",\"repeatable\":" + repeatable
                + ",\"global\":" + global + ",\"objectives\":[{\"type\":\"read\",\"required_amount\":" + amount
                + "}],\"rewards\":[{\"type\":\"experience\",\"experience\":" + experience + "}]}";
        QuestManager manager = new QuestManager(null) {
            @Override public boolean isClient() { return true; }
            @Override public void sync(ResourceLocation id) { }
        };
        return Quest.create(JsonParser.parseString(json).getAsJsonObject(), ID, manager);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
