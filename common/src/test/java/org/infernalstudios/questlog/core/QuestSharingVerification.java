package org.infernalstudios.questlog.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.validation.QuestValidation;

import java.util.List;

/** Headless invariants for offers, accepted access, visibility and chat references. */
public final class QuestSharingVerification {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("questlog", "locked");
    private static final ResourceLocation OTHER = ResourceLocation.fromNamespaceAndPath("questlog", "other");

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        Quest locked = quest(ID, "Hidden Valley", true, false, false, false);
        check(!locked.isTriggered() && !locked.isVisibleToPlayer(), "Prerequisites hide a quest");
        check(!QuestSharing.maySendDefinition(locked, false) && QuestSharing.maySendDefinition(locked, true),
                "Locked definition is private except for an authorized author");
        check(locked.offer("sender"), "A shareable quest can be offered");
        check(QuestSharing.maySendDefinition(locked, false), "Only the offer recipient gets its preview definition");
        check(locked.isOffered() && !locked.isTriggered() && !locked.isVisibleToPlayer(), "An offer is preview only");
        check(locked.objectives.getFirst().getUnits() == 0 && locked.prerequisites.getFirst().getUnits() == 0,
                "An offer changes no progress");
        check(!locked.offer("other"), "Duplicate offers are rejected");
        Quest synced = quest(ID, "Hidden Valley", true, false, false, false);
        synced.deserialize(locked.serialize());
        check(synced.isOffered(), "An offer survives serialization before acceptance");
        check(locked.acceptOffer(), "The recipient can accept once");
        check(locked.isTriggered() && locked.isVisibleToPlayer() && !locked.isCompleted(), "Acceptance opens only the quest");
        check(locked.objectives.getFirst().getUnits() == 0 && locked.prerequisites.getFirst().getUnits() == 0,
                "Acceptance does not complete objectives or prerequisites");
        check(!locked.acceptOffer(), "Acceptance cannot be replayed");

        CompoundTag saved = locked.serialize();
        Quest reloaded = quest(ID, "Hidden Valley", false, true, false, false);
        reloaded.deserialize(saved);
        check(reloaded.hasSharedAccess() && reloaded.isVisibleToPlayer(), "Access persists after reload and author settings change");
        CompoundTag offlineSave = new CompoundTag();
        offlineSave.put(ID.toString(), saved);
        ServerPlayerManager.resetSavedQuest(offlineSave, ID, false);
        Quest resetOffline = quest(ID, "Hidden Valley", true, false, false, false);
        resetOffline.deserialize(offlineSave.getCompound(ID.toString()));
        check(resetOffline.hasSharedAccess() && !resetOffline.isCompleted(), "Offline progress reset keeps accepted access");
        Quest neighbour = quest(OTHER, "Other Quest", false, false, false, false);
        check(!neighbour.isVisibleToPlayer(), "The neighbouring quest remains locked");

        Quest stale = quest(ID, "Old title", true, false, false, false);
        check(stale.offer("sender"), "Offer before author edit");
        CompoundTag pendingSave = new CompoundTag();
        pendingSave.put(ID.toString(), stale.serialize());
        ServerPlayerManager.resetSavedQuest(pendingSave, ID, false);
        Quest pendingAfterReset = quest(ID, "Old title", true, false, false, false);
        pendingAfterReset.deserialize(pendingSave.getCompound(ID.toString()));
        check(pendingAfterReset.isOffered(), "Offline progress reset keeps an unrelated pending offer");
        Quest edited = quest(ID, "New title", true, false, false, false);
        edited.deserialize(stale.serialize());
        check(!edited.isOffered() && !edited.acceptOffer(), "An edited offer expires");
        Quest disabled = quest(ID, "Hidden Valley", false, false, false, false);
        check(!disabled.offer("sender"), "Sharing is off by default");
        Quest hidden = quest(ID, "Hidden Valley", true, true, true, false);
        check(!hidden.isVisibleToPlayer(), "Author-hidden quest is not ordinarily visible");
        check(hidden.offer("sender") && hidden.acceptOffer() && hidden.isVisibleToPlayer(), "An accepted offer reveals its quest");
        DefinitionUtil.putCachedChapter(ResourceLocation.fromNamespaceAndPath("questlog", "sealed"),
                JsonParser.parseString("{\"hidden\":true}").getAsJsonObject());
        Quest sealed = quest(ID, "Sealed", true, true, false, false, "sealed");
        check(!sealed.isVisibleToPlayer(), "A hidden chapter blocks ordinary opening");
        check(sealed.offer("sender") && sealed.acceptOffer() && sealed.isVisibleToPlayer(),
                "An accepted quest can reveal its hidden chapter");

        Quest completed = quest(ID, "Completed", true, false, false, false);
        completed.hasSentCompletion = true;
        check(completed.offer("sender") && !completed.acceptOffer() && completed.isCompleted(),
                "Completed nonrepeatable quest cannot be accepted again");
        Quest repeatable = quest(ID, "Repeatable", true, false, false, true);
        repeatable.hasSentCompletion = true;
        check(repeatable.offer("sender") && repeatable.acceptOffer() && repeatable.isCompleted(),
                "A repeatable quest retains its completion until normal reset");

        Quest visible = quest(ID, "La cité oubliée", true, true, false, false);
        Component linked = QuestChatReferences.decorate(Component.literal("Voir quest:\"La cité oubliée\" !"), List.of(visible));
        check(linked.getSiblings().stream().anyMatch(part -> part.getStyle().getClickEvent() != null
                        && part.getStyle().getClickEvent().getValue().equals("/questlog_view " + ID)),
                "A spaced title links to the stable ID");
        check(QuestChatReferences.decorate(Component.literal("quest:La cité oubliée"), List.of(visible))
                        .getSiblings().stream().anyMatch(part -> part.getStyle().getClickEvent() != null),
                "An unquoted spaced title links when unambiguous");
        Quest duplicate = quest(OTHER, "La cité oubliée", true, true, false, false);
        check(QuestChatReferences.decorate(Component.literal("quest:\"La cité oubliée\""), List.of(visible, duplicate))
                        .getSiblings().isEmpty(), "Ambiguous names stay plain text");
        check(QuestChatReferences.decorate(Component.literal("quest:Hidden Valley"), List.of(quest(ID, "Hidden Valley", true, false, false, false)))
                        .getSiblings().isEmpty(), "Locked quests do not become chat links");
        Quest offeredOnly = quest(ID, "Offered Only", true, false, false, false);
        offeredOnly.offer("sender");
        check(QuestChatReferences.decorate(Component.literal("quest:Offered Only"), List.of(offeredOnly))
                        .getSiblings().isEmpty(), "A private offer does not make an ordinary chat reference accessible");

        JsonObject invalid = JsonParser.parseString("{\"title\":\"Test\",\"description\":\"Test\",\"shareable\":\"yes\"}").getAsJsonObject();
        check(!QuestValidation.inspect(invalid, QuestValidation.UNAVAILABLE).functional(), "Malformed shareable setting is rejected");
        System.out.println("Quest sharing: headless checks passed; no in-game test was run.");
    }

    private static Quest quest(ResourceLocation id, String title, boolean shareable, boolean unlocked, boolean hidden, boolean repeatable) {
        return quest(id, title, shareable, unlocked, hidden, repeatable, "main");
    }

    private static Quest quest(ResourceLocation id, String title, boolean shareable, boolean unlocked, boolean hidden, boolean repeatable, String chapter) {
        JsonObject definition = JsonParser.parseString("{\"title\":\"" + title + "\",\"description\":\"Test\","
                + "\"shareable\":" + shareable + ",\"hidden\":" + hidden + ",\"repeatable\":" + repeatable + ",\"chapter\":\"" + chapter + "\","
                + "\"prerequisites\":[{\"type\":\"read\"}],\"objectives\":[{\"type\":\"read\"}]}").getAsJsonObject();
        QuestManager manager = new QuestManager(null) {
            @Override public boolean isClient() { return true; }
            @Override public void sync(ResourceLocation questId) { }
        };
        Quest quest = Quest.create(definition, id, manager);
        if (unlocked) quest.prerequisites.getFirst().forceSetUnits(1);
        return quest;
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
