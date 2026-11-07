package org.infernalstudios.questlog.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.objectives.Objective;
import org.infernalstudios.questlog.core.validation.QuestValidation;

import java.util.List;
import java.util.UUID;

public final class GroupProgressVerification {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("questlog", "group_check");

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        JsonObject group = definition(true, true);
        check(QuestValidation.inspect(group, QuestValidation.UNAVAILABLE).functional(), "Group quota definition is valid");
        JsonObject personal = definition(false, true);
        check(!QuestValidation.inspect(personal, QuestValidation.UNAVAILABLE).functional(), "Personal quest rejects per-participant quota");
        personal.remove("per_participant");
        check(QuestValidation.inspect(personal, QuestValidation.UNAVAILABLE).functional(), "Personal fixed quota is valid");

        Quest quest = quest(group);
        check(quest.isGroupQuest() && !quest.isShareable(), "Group mode is independent of sharing");
        for (String type : List.of("visit_biome", "visit_structure")) {
            String field = type.equals("visit_biome") ? "biome" : "structure";
            JsonObject taggedVisit = JsonParser.parseString("{\"title\":\"Tagged group\",\"description\":\"Test\","
                    + "\"group_quest\":true,\"objectives\":[{\"type\":\"" + type + "\",\"" + field
                    + "\":\"#minecraft:test\",\"required_amount\":2}]}").getAsJsonObject();
            Objective visit = quest(taggedVisit).objectives.getFirst();
            visit.setUnits(2);
            check(visit.canRecordMore(), "Tagged group visit can continue after its personal quota: " + type);
        }
        Objective objective = quest.objectives.getFirst();
        objective.setUnits(40);
        check(objective.getPersonalUnits() == 40, "Personal contribution is recorded");
        objective.setCollectiveProgress(60, 128);
        check(objective.getUnits() == 60 && objective.getRequiredAmount() == 128 && !objective.isCompleted(),
                "Two eligible members contribute 40 and 20 toward 128");
        objective.setCollectiveProgress(150, 192);
        check(!objective.isCompleted(), "Three participants still need 192");
        objective.setCollectiveProgress(150, 128);
        check(objective.isCompleted(), "A reduced quota can complete without another action");
        check(quest.serialize().getList("objectives", 10).getCompound(0).getInt("units") == 40,
                "Collective view does not overwrite personal contribution");
        objective.setCollectiveProgress((long) Integer.MAX_VALUE + 1, (long) Integer.MAX_VALUE + 2);
        check(!objective.isCompleted(), "Large totals do not complete through integer overflow");

        objective.setCollectiveProgress(128, 128);
        check(objective.getDisplay().isCompleted(), "Completed group objective is shown as complete");
        quest.resetProgress();
        check(quest.isGroupResetPending() && !quest.isCompleted(), "Targeted reset stays pending");
        check(!objective.getDisplay().isCompleted() && objective.getDisplayedUnits() == 0,
                "Reset clears the collective completion shown in quest details");
        objective.setUnits(1);
        check(!quest.isGroupResetPending(), "A new personal action releases the reset hold");
        Quest saved = quest(group);
        saved.deserialize(quest.serialize());
        check(saved.objectives.getFirst().getPersonalUnits() == 1, "Personal counter survives reload");

        JsonObject nested = JsonParser.parseString("{\"title\":\"Nested group\",\"description\":\"Test\","
                + "\"group_quest\":true,\"objectives\":[{\"type\":\"and\",\"objectives\":["
                + "{\"type\":\"read\"},{\"type\":\"read\"}]}]}").getAsJsonObject();
        Quest nestedQuest = quest(nested);
        Objective combined = nestedQuest.objectives.getFirst();
        combined.getChildren().forEach(child -> child.setCollectiveProgress(1, 1));
        check(combined.isCompleted(), "Nested collective objectives can complete");
        nestedQuest.resetProgress();
        check(combined.getChildren().stream().noneMatch(Objective::isCompleted),
                "Reset clears collective completion from nested objectives");

        Quest waiting = quest(group);
        waiting.resetProgress();
        waiting.objectives.getFirst().forceSetUnits(64);
        Quest contributing = quest(group);
        contributing.objectives.getFirst().setUnits(80);
        List<GroupProgress.Participant> members = List.of(
                new GroupProgress.Participant(new UUID(0, 1), waiting, null, true),
                new GroupProgress.Participant(new UUID(0, 2), contributing, null, false));
        GroupProgress.applyObjective(waiting.objectives.getFirst(), 0, new int[0], members, true, null);
        check(waiting.objectives.getFirst().getDisplayedUnits() == 80
                        && waiting.objectives.getFirst().getDisplayedRequiredAmount() == 128,
                "Reset member counts toward the quota but contributes zero until a new action");
        check(!waiting.objectives.getFirst().getDisplay().isCompleted(),
                "Waiting member's objective is not displayed as complete");

        Quest solo = quest(personal);
        solo.objectives.getFirst().setUnits(100);
        check(solo.objectives.getFirst().getPersonalUnits() == 64 && !solo.isGroupQuest(),
                "A personal quest keeps its own cap even in a group world");

        DefinitionUtil.putCachedQuest(ID, group);
        CompoundTag offline = new CompoundTag();
        check(GroupProgress.eligibleOffline(quest, offline, false), "An unlocked offline member participates");
        offline.putBoolean("completed", true);
        check(!GroupProgress.eligibleOffline(quest, offline, false), "Completed member is excluded");
        offline.putBoolean("completed", false);
        offline.putBoolean("group_reset_pending", true);
        check(GroupProgress.eligibleOffline(quest, offline, false),
                "Offline targeted reset still counts toward the participant quota");
        offline.putBoolean("group_reset_pending", false);
        check(!GroupProgress.eligibleOffline(quest, offline, true), "Inherited global completion excludes an offline member");
        offline.putBoolean("ignore_global_completion", true);
        check(GroupProgress.eligibleOffline(quest, offline, true), "A personal global reset stays eligible to redo");
        ListTag failures = new ListTag();
        CompoundTag failed = new CompoundTag();
        failed.putInt("units", 1);
        failures.add(failed);
        offline.put("failures", failures);
        check(!GroupProgress.eligibleOffline(quest, offline, false), "Failed offline member is excluded");
        offline.remove("failures");
        group.addProperty("hidden", true);
        DefinitionUtil.putCachedQuest(ID, group);
        check(!GroupProgress.eligibleOffline(quest, offline, false), "Hidden quest does not admit an unshared offline member");
        offline.putBoolean("shared_access", true);
        check(GroupProgress.eligibleOffline(quest, offline, false), "Accepted access admits the offline member");
        CompoundTag playerData = new CompoundTag();
        playerData.put(ID.toString(), quest.serialize());
        ServerPlayerManager.resetSavedQuest(playerData, ID, false, true);
        check(playerData.getCompound(ID.toString()).getBoolean("group_reset_pending"),
                "Offline reset records its group completion hold");
        System.out.println("Group progress: headless checks passed; no Minecraft session was run.");
    }

    private static JsonObject definition(boolean group, boolean perParticipant) {
        return JsonParser.parseString("{\"title\":\"Group check\",\"description\":\"Test\","
                + "\"group_quest\":" + group + ",\"per_participant\":" + perParticipant + ","
                + "\"objectives\":[{\"type\":\"read\",\"required_amount\":64}],"
                + "\"failures\":[{\"type\":\"read\"}]}").getAsJsonObject();
    }

    private static Quest quest(JsonObject definition) {
        QuestManager manager = new QuestManager(null) {
            @Override public boolean isClient() { return true; }
            @Override public void sync(ResourceLocation id) { }
        };
        return Quest.create(definition, ID, manager);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
