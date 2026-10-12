package org.infernalstudios.questlog.core;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.network.packet.QuestSyncPacket;
import org.infernalstudios.questlog.network.packet.QuestEditModePacket;
import org.infernalstudios.questlog.platform.Services;
import net.minecraft.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.advancements.AdvancementHolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

public class ServerPlayerManager {

    public static ServerPlayerManager INSTANCE = null;

    private final Map<UUID, QuestManager> questManagers = new HashMap<>();
    private final MinecraftServer server;

    public ServerPlayerManager(MinecraftServer server) {
        this.server = server;
        for (Player player : this.server.getPlayerList().getPlayers()) {
            this.addPlayer(player);
        }
    }

    public void addPlayer(Player player) {
        QuestManager questManager = new QuestManager(player);
        this.questManagers.put(player.getUUID(), questManager);
    }

    public QuestManager getManagerIfPresent(Player player) {
        if (!this.questManagers.containsKey(player.getUUID())) {
            return null;
        } else if (this.questManagers.get(player.getUUID()).player != player) {
            this.questManagers.get(player.getUUID()).player = player;
        }

        return this.questManagers.get(player.getUUID());
    }

    public QuestManager getManagerByPlayer(Player player) {
        if (!this.questManagers.containsKey(player.getUUID())) {
            this.addPlayer(player);
        } else if (this.questManagers.get(player.getUUID()).player != player) {
            this.questManagers.get(player.getUUID()).player = player;
        }

        return this.questManagers.get(player.getUUID());
    }

    /**
     * This method is used to save the data of all quest managers.
     * It iterates over all quest managers and calls the save method for each one.
     */
    public void save() {
        for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
            QuestManager manager = getManagerIfPresent(player);
            if (manager != null) this.save(manager);
        }
    }

    /**
     * This method is used to save the data of a specific quest manager.
     * It creates a new CompoundTag, serializes the data of all quests in the quest manager, and writes the data to a file.
     *
     * @param questManager The quest manager whose data will be saved.
     */
    public boolean save(QuestManager questManager) {
        if (!questManager.isLoaded()) {
            Questlog.LOGGER.warn("Skipping quest save for player {} because quest data has not been loaded yet", questManager.player.getGameProfile().getName());
            return false;
        }

        Questlog.LOGGER.debug("Saving player data for {}", questManager.player.getGameProfile().getName());
        CompoundTag data = new CompoundTag();
        for (Quest quest : questManager.getAllQuests()) {
            data.put(quest.getId().toString(), quest.serialize());
        }

        data.putBoolean("edit_mode", questManager.isEditMode());
        questManager.tracking.save(data);

        Path tempFile = null;
        try {
            Path playerDataDir = this.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            Files.createDirectories(playerDataDir);
            String uuid = questManager.player.getStringUUID();
            tempFile = Files.createTempFile(playerDataDir, uuid + "-", ".questlog.dat");
            NbtIo.writeCompressed(data, tempFile);
            Path targetFile = playerDataDir.resolve(uuid + ".questlog.dat");
            Path backupFile = playerDataDir.resolve(uuid + ".questlog.dat_old");
            Util.safeReplaceFile(targetFile, tempFile, backupFile);
            return true;
        } catch (IOException e) {
            Questlog.LOGGER.error("Failed to save player data for {}", questManager.player.getGameProfile().getName(), e);
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
            return false;
        }
    }

    /**
     * This method is used to load the data of all quest managers.
     * It iterates over all players and calls the load method for each player's quest manager.
     */
    public void load() {
        Questlog.LOGGER.trace("Loading all player data");
        for (Player player : this.server.getPlayerList().getPlayers()) {
            this.load(this.getManagerByPlayer(player));
        }
    }

    /**
     * This method is used to load the data of a specific quest manager.
     * It reads the data from a file, deserializes the data, and updates the quests in the quest manager.
     * If a quest does not exist in the data, it will be created.
     * After loading the data, it checks if the data should be saved (if a quest was created).
     *
     * @param questManager The quest manager whose data will be loaded.
     */
    public void load(QuestManager questManager) {
        Questlog.LOGGER.debug("Loading player data for {}", questManager.player.getGameProfile().getName());
        File playerDataFile = this.getPlayerDataFile(questManager.player);
        File backupDataFile = this.getBackupPlayerDataFile(questManager.player);

        CompoundTag data = null;
        if (playerDataFile.exists()) {
            try {
                data = NbtIo.readCompressed(playerDataFile.toPath(), NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                Questlog.LOGGER.error("Failed to load player data for {}, attempting backup", questManager.player.getGameProfile().getName(), e);
            }
        }

        if (data == null && backupDataFile.exists()) {
            try {
                data = NbtIo.readCompressed(backupDataFile.toPath(), NbtAccounter.unlimitedHeap());
                Questlog.LOGGER.info("Successfully loaded backup quest data for {}", questManager.player.getGameProfile().getName());
            } catch (IOException e) {
                Questlog.LOGGER.error("Failed to load backup player data for {}", questManager.player.getGameProfile().getName(), e);
            }
        }

        if (data == null) {
            data = new CompoundTag();
        }

        boolean shouldSave = false;
        // The saved file is authoritative on login, including offline resets.
        questManager.clearQuests();
        questManager.createAllQuests();

        if (data.contains("edit_mode")) {
            questManager.setEditMode(data.getBoolean("edit_mode"));
        } else {
            questManager.setEditMode(false);
        }

        CompoundTag globalData = this.loadGlobalData();
        for (Quest quest : questManager.getAllQuests()) {
            if (data.contains(quest.getId().toString())) {
                CompoundTag questData = data.getCompound(quest.getId().toString());
                quest.deserialize(questData);
            } else {
                shouldSave = true;
            }

            if (mergeGlobalCompletion(quest, globalData)) {
                shouldSave = true;
            }
        }

        questManager.tracking.load(data);
        questManager.tracking.prune(questManager);
        questManager.setLoaded(true);

        if (shouldSave) {
            this.save(questManager);
        }

        this.syncPlayer(questManager);
    }

    private boolean isSyncingGlobal = false;

    private File getGlobalDataFile() {
        Path playerDataPath = this.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        return new File(playerDataPath.toFile(), "global_quests.questlog.dat");
    }

    private CompoundTag loadGlobalData() {
        File file = getGlobalDataFile();
        File backupFile = new File(file.getParentFile(), "global_quests.questlog.dat_old");
        if (file.exists()) {
            try {
                return NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                Questlog.LOGGER.error("Failed to load global quest data, attempting backup", e);
            }
        }
        if (backupFile.exists()) {
            try {
                return NbtIo.readCompressed(backupFile.toPath(), NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                Questlog.LOGGER.error("Failed to load backup global quest data", e);
            }
        }
        return new CompoundTag();
    }

    private boolean saveGlobalData(CompoundTag tag) {
        Path tempFile = null;
        try {
            Path playerDataDir = this.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            Files.createDirectories(playerDataDir);
            tempFile = Files.createTempFile(playerDataDir, "global_quests-", ".questlog.dat");
            NbtIo.writeCompressed(tag, tempFile);
            Path targetFile = playerDataDir.resolve("global_quests.questlog.dat");
            Path backupFile = playerDataDir.resolve("global_quests.questlog.dat_old");
            Util.safeReplaceFile(targetFile, tempFile, backupFile);
            return true;
        } catch (IOException e) {
            Questlog.LOGGER.error("Failed to save global quest data", e);
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
            return false;
        }
    }

    public void onGlobalQuestUpdated(Quest sourceQuest) {
        if (isSyncingGlobal) return;
        isSyncingGlobal = true;
        try {
            ResourceLocation id = sourceQuest.getId();
            CompoundTag globalTag = loadGlobalData();
            CompoundTag completion = new CompoundTag();
            completion.putBoolean("completed", true);
            globalTag.put(id.toString(), completion);
            if (!saveGlobalData(globalTag)) return;

            for (ServerPlayer player : this.server.getPlayerList().getPlayers()) {
                QuestManager manager = getManagerIfPresent(player);
                if (manager == null) continue;
                Quest q = manager.getQuest(id);
                if (q != null && manager.isLoaded() && !q.ignoresGlobalCompletion()) {
                    if (q != sourceQuest && !q.hasSentCompletion) q.grantGlobalCompletion();
                    manager.sync(id);
                    this.save(manager);
                }
            }
        } finally {
            isSyncingGlobal = false;
        }
    }

    public void resetGlobalQuest(ResourceLocation id) {
        resetQuestForAll(id);
    }

    public boolean resetQuestForPlayer(UUID playerId, ResourceLocation questId) {
        return resetQuestForPlayer(playerId, questId, true);
    }

    private boolean resetQuestForPlayer(UUID playerId, ResourceLocation questId, boolean suppressGlobal) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(playerId);
        QuestManager manager = player == null ? null : getManagerByPlayer(player);
        if (manager != null && manager.isLoaded()) {
            Quest quest = manager.getQuest(questId);
            if (quest == null) return false;
            quest.resetProgress(suppressGlobal && quest.isGlobal());
            if (!this.save(manager)) return false;
            this.syncPlayer(manager);
            return true;
        }

        Path dir = this.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        CompoundTag data = readPlayerData(dir, playerId);
        if (data == null) return false;
        JsonObject definition = DefinitionUtil.hasCachedQuest(questId) ? DefinitionUtil.getCachedQuest(questId) : null;
        boolean global = definition != null && definition.has("global") && definition.get("global").getAsBoolean();
        resetSavedQuest(data, questId, suppressGlobal && global);
        return writePlayerData(dir, playerId, data);
    }

    static void resetSavedQuest(CompoundTag data, ResourceLocation questId, boolean ignoreGlobalCompletion) {
        CompoundTag previous = data.getCompound(questId.toString());
        CompoundTag questData = new CompoundTag();
        questData.putBoolean("ignore_global_completion", ignoreGlobalCompletion);
        questData.putBoolean("shared_access", previous.getBoolean("shared_access"));
        questData.putString("offered_by", previous.getString("offered_by"));
        questData.putString("offer_version", previous.getString("offer_version"));
        data.put(questId.toString(), questData);
    }

    static boolean mergeGlobalCompletion(Quest quest, CompoundTag globalData) {
        if (!quest.isGlobal() || quest.ignoresGlobalCompletion() || quest.hasSentCompletion
                || !globalData.getCompound(quest.getId().toString()).getBoolean("completed")) return false;
        quest.grantGlobalCompletion();
        return true;
    }

    public int resetQuestForAll(ResourceLocation questId) {
        Path playerDataDir = this.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        int count = 0;
        try {
            Files.createDirectories(playerDataDir);
            Set<UUID> playerIds = new HashSet<>();
            try (Stream<Path> files = Files.list(playerDataDir)) {
                for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".questlog.dat")
                        || p.getFileName().toString().endsWith(".questlog.dat_old")).toList()) {
                    String name = path.getFileName().toString();
                    try {
                        String suffix = name.endsWith(".questlog.dat_old") ? ".questlog.dat_old" : ".questlog.dat";
                        playerIds.add(UUID.fromString(name.substring(0, name.length() - suffix.length())));
                    } catch (IllegalArgumentException ignored) {
                        // The global file and temporary files are not player saves.
                    }
                }
            }
            for (ServerPlayer player : this.server.getPlayerList().getPlayers()) playerIds.add(player.getUUID());
            for (UUID playerId : playerIds) {
                if (!resetQuestForPlayer(playerId, questId, false)) return -1;
                count++;
            }
        } catch (IOException e) {
            Questlog.LOGGER.error("Failed to reset quest {} for offline players", questId, e);
            return -1;
        }
        CompoundTag globalTag = loadGlobalData();
        if (globalTag.contains(questId.toString())) {
            globalTag.remove(questId.toString());
            if (!saveGlobalData(globalTag)) return -1;
        }
        return count;
    }

    static CompoundTag readPlayerData(Path playerDataDir, UUID playerId) {
        boolean found = false;
        for (String suffix : new String[]{".questlog.dat", ".questlog.dat_old"}) {
            Path path = playerDataDir.resolve(playerId + suffix);
            if (!Files.exists(path)) continue;
            found = true;
            try {
                return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                Questlog.LOGGER.error("Failed to read quest data for {} from {}", playerId, path, e);
                if (suffix.equals(".questlog.dat")) return null;
            }
        }
        return found ? null : new CompoundTag();
    }

    static boolean writePlayerData(Path dir, UUID playerId, CompoundTag data) {
        Path tempFile = null;
        try {
            Files.createDirectories(dir);
            tempFile = Files.createTempFile(dir, playerId + "-", ".questlog.dat");
            NbtIo.writeCompressed(data, tempFile);
            Util.safeReplaceFile(dir.resolve(playerId + ".questlog.dat"), tempFile,
                    dir.resolve(playerId + ".questlog.dat_old"));
            return true;
        } catch (IOException e) {
            Questlog.LOGGER.error("Failed to save quest data for {}", playerId, e);
            if (tempFile != null) try { Files.deleteIfExists(tempFile); } catch (IOException ignored) { }
            return false;
        }
    }

    public void syncPlayer(QuestManager questManager) {
        if (questManager.player instanceof ServerPlayer serverPlayer) {
            Map<ResourceLocation, String> definitions = new HashMap<>();
            Map<ResourceLocation, String> chapterDefinitions = new HashMap<>();
            Map<ResourceLocation, CompoundTag> data = new HashMap<>();

            for (Quest quest : questManager.getAllQuests()) {
                if (!QuestSharing.maySendDefinition(quest, QuestSharing.canSeeAllDefinitions(serverPlayer, questManager))) continue;
                definitions.put(quest.getId(), DefinitionUtil.getCachedQuest(quest.getId()).toString());
                data.put(quest.getId(), quest.serialize());
            }

            for (ResourceLocation chapterId : DefinitionUtil.getCachedChapterKeys()) {
                var chapterJson = DefinitionUtil.getCachedChapter(chapterId);
                if (chapterJson != null) {
                    chapterDefinitions.put(chapterId, chapterJson.toString());
                } else {
                    Questlog.LOGGER.warn("Attempted to sync missing chapter definition: {}", chapterId);
                }
            }

            List<ResourceLocation> advancements = new ArrayList<>();
            for (AdvancementHolder holder : serverPlayer.getServer().getAdvancements().getAllAdvancements()) {
                advancements.add(holder.id());
            }

            Services.PLATFORM.sendPacketToClient(serverPlayer, new QuestSyncPacket(definitions, chapterDefinitions, data, advancements));
            questManager.syncedQuests.clear();
            questManager.syncedQuests.addAll(definitions.keySet());
            Services.PLATFORM.sendPacketToClient(serverPlayer, new QuestEditModePacket(serverPlayer.hasPermissions(2) && questManager.isEditMode()));
            questManager.tracking.prune(questManager);
            Services.PLATFORM.sendPacketToClient(serverPlayer, new org.infernalstudios.questlog.network.packet.QuestTrackingPacket(questManager.tracking.ids()));
        }
    }

    private File getPlayerDataFile(Player player) {
        Path playerDataPath = this.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        return new File(playerDataPath.toFile(), player.getUUID() + ".questlog.dat");
    }

    private File getBackupPlayerDataFile(Player player) {
        Path playerDataPath = this.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        return new File(playerDataPath.toFile(), player.getUUID() + ".questlog.dat_old");
    }
}
