package org.infernalstudios.questlog.core;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.QuestlogEvents;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.event.events.QuestEvent;
import org.infernalstudios.questlog.network.packet.QuestDataPacket;
import org.infernalstudios.questlog.network.packet.QuestRemovePacket;
import org.infernalstudios.questlog.platform.Services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class QuestManager {
    private final Map<ResourceLocation, Quest> quests = new LinkedHashMap<>();
    final Set<ResourceLocation> syncedQuests = new HashSet<>();
    public Player player;
    public final QuestTracking tracking = new QuestTracking();
    private boolean editMode = false;
    private boolean loaded = false;

    public QuestManager(Player player) {
        this.player = player;
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public boolean isEditMode() {
        return this.editMode;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    public void addQuest(Quest quest) {
        this.quests.put(quest.getId(), quest);
    }

    public void clearQuests() {
        this.quests.values().forEach(quest -> quest.setActive(false));
        this.quests.clear();
        this.syncedQuests.clear();
    }

    public void reload() {
        CompoundTag savedData = new CompoundTag();
        for (Quest quest : this.quests.values()) {
            savedData.put(quest.getId().toString(), quest.serialize());
        }

        this.clearQuests();
        this.createAllQuests();

        for (Quest quest : this.quests.values()) {
            if (savedData.contains(quest.getId().toString())) {
                quest.deserialize(savedData.getCompound(quest.getId().toString()));
            }
        }
    }

    /**
     * Removes a quest from the player's tracked quest list.
     */
    public void removeQuest(ResourceLocation id) {
        Quest removed = this.quests.remove(id);
        if (removed != null) removed.setActive(false);
    }

    public Quest getQuest(ResourceLocation id) {
        return this.quests.get(id);
    }

    public boolean isSyncedToClient(ResourceLocation id) { return this.syncedQuests.contains(id); }

    public boolean isClient() {
        return this.player.level().isClientSide();
    }

    /**
     * Returns all quests in insertion order (definition order).
     */
    public List<Quest> getAllQuests() {
        return new ArrayList<>(this.quests.values());
    }

    /**
     * This method is used to create all quests for the player.
     * It fetches all quest IDs from the cached keys and checks if the quest is already present in the player's quest list.
     * If not, it fetches the quest definition from the cache, creates a new quest instance and adds it to the player's quest list.
     */
    public void createAllQuests() {
        List<ResourceLocation> ids = DefinitionUtil.getCachedQuestKeys();
        var references = this.player instanceof ServerPlayer serverPlayer
                ? org.infernalstudios.questlog.core.validation.QuestReferences.forServer(serverPlayer)
                : new org.infernalstudios.questlog.core.validation.QuestReferences(this.player.level().registryAccess(), null);

        for (ResourceLocation id : ids) {
            if (!this.quests.containsKey(id)) {
                JsonObject definition = DefinitionUtil.getCachedQuest(id);
                Quest quest;
                try {
                    quest = Quest.create(definition, id, this);
                    boolean functional = org.infernalstudios.questlog.core.validation.QuestValidation.inspect(definition, references).functional();
                    quest.setFunctional(functional);
                    definition.addProperty("_functional", functional);
                    if (!functional) {
                        quest.setActive(false);
                        definition.addProperty("active", false);
                    }
                    CompoundTag data = new CompoundTag();
                    quest.writeInitialData(data);
                    quest.deserialize(data);
                    this.addQuest(quest);
                } catch (Exception e) {
                    Questlog.LOGGER.error("=====================================================");
                    Questlog.LOGGER.error(" QUESTLOG ERROR: Failed to load quest '{}'", id);
                    Questlog.LOGGER.error(" The JSON file has a syntax error, typo, or missing field.");
                    Questlog.LOGGER.error(" Exception Details: ", e);
                    Questlog.LOGGER.error("=====================================================");
                    if (definition != null) {
                        definition.addProperty("active", false);
                        definition.addProperty("_functional", false);
                        definition.addProperty("_validation_error", e.getMessage() == null ? e.toString() : e.getMessage());
                    }
                    try {
                        JsonObject fallbackDef = new JsonObject();
                        fallbackDef.addProperty("title", "Broken Quest (" + id.getPath() + ")");
                        fallbackDef.addProperty("active", false);
                        fallbackDef.addProperty("_functional", false);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                        if (e.getCause() != null) {
                            errorMsg += "\nCaused by: " + e.getCause().getMessage();
                        }
                        fallbackDef.addProperty("description", "This quest failed to load properly. Edit it to fix errors.\n\nError details:\n" + errorMsg);
                        fallbackDef.addProperty("chapter", definition != null && definition.has("chapter") && definition.get("chapter").isJsonPrimitive() && definition.get("chapter").getAsJsonPrimitive().isString() ? definition.get("chapter").getAsString() : "main");
                        quest = Quest.create(fallbackDef, id, this);
                        CompoundTag data = new CompoundTag();
                        quest.writeInitialData(data);
                        quest.deserialize(data);
                        this.addQuest(quest);
                    } catch (Exception fallbackEx) {
                        Questlog.LOGGER.error("Fallback load failed for quest '{}'", id, fallbackEx);
                    }
                }
            }
        }
    }

    /**
     * This method is used to sync all quests data to the client.
     * It iterates over all quests in the player's quest list and calls the sync method for each quest.
     */
    public void sync() {
        for (ResourceLocation id : this.quests.keySet()) {
            this.sync(id);
        }
    }

    /**
     * This method is used to sync specific quest data to the client.
     * If the quest does not exist, it sends a QuestRemovePacket to the client.
     * If the quest exists, it serializes the quest data and sends a QuestDataPacket to the client.
     * It also checks if the quest has been triggered or completed and sends the corresponding event to the client.
     *
     * @param id The ID of the quest to be synced.
     */
    public void sync(ResourceLocation id) {
        if (!this.isClient() && this.player instanceof ServerPlayer) {
            Questlog.LOGGER.trace("Syncing quest data for {} to client", id);
            if (this.tracking.prune(this)) {
                Services.PLATFORM.sendPacketToClient((ServerPlayer) this.player,
                        new org.infernalstudios.questlog.network.packet.QuestTrackingPacket(this.tracking.ids()));
                ServerPlayerManager.INSTANCE.save(this);
            }
            Quest quest = this.quests.get(id);
            if (quest == null) {
                this.syncedQuests.remove(id);
                // This will only be called if a definition has been deleted while reloading the server
                Services.PLATFORM.sendPacketToClient((ServerPlayer) this.player, new QuestRemovePacket(id));
                Questlog.LOGGER.warn("Quest {} not found in manager, removing from client", id);
            } else {
                ServerPlayer serverPlayer = (ServerPlayer) this.player;
                boolean available = QuestSharing.maySendDefinition(quest, QuestSharing.canSeeAllDefinitions(serverPlayer, this));
                if (available != this.syncedQuests.contains(id)) {
                    ServerPlayerManager.INSTANCE.syncPlayer(this);
                } else if (available) {
                    CompoundTag data = quest.serialize();
                    Services.PLATFORM.sendPacketToClient(serverPlayer, new QuestDataPacket(id, data));
                }
                if (!quest.isActive()) return;
                Questlog.LOGGER.trace("Sent quest data for {} to client", id);
                if (!quest.hasSentTrigger && quest.isTriggered()) {
                    quest.hasSentTrigger = true;
                    QuestlogEvents.onQuestTriggered(new QuestEvent.Triggered(this.player, quest, true)); // This handles sending of packet
                    Questlog.LOGGER.trace("Sent quest triggered event for {}", id);
                }

                if (!quest.hasSentCompletion && quest.isCompleted()) {
                    quest.hasSentCompletion = true;
                    QuestlogEvents.onQuestCompleted(new QuestEvent.Completed(this.player, quest, true));
                    Questlog.LOGGER.trace("Sent quest completed event for {}", id);

                    if (quest.isGlobal() && ServerPlayerManager.INSTANCE != null) {
                        ServerPlayerManager.INSTANCE.onGlobalQuestUpdated(quest);
                    }
                }
            }
        }
    }
}
