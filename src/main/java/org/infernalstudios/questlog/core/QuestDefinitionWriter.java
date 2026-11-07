package org.infernalstudios.questlog.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.platform.Services;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared author save/activation path. Player progress stays in the existing managers. */
public final class QuestDefinitionWriter {
    private QuestDefinitionWriter() {}
    public static boolean write(ServerPlayer player, ResourceLocation id, JsonObject definition) {
        if (!player.hasPermissions(2) || !id.getNamespace().equals(Questlog.MODID)) return false;
        Path directory = Services.PLATFORM.getConfigDirectory().resolve("questlog/quests").toAbsolutePath().normalize();
        Path file = directory.resolve(id.getPath() + ".json").normalize();
        if (!file.startsWith(directory)) return false;
        definition = definition.deepCopy();
        definition.remove("_functional");
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "questlog-", ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(definition), StandardCharsets.UTF_8);
            if (Files.exists(file)) Files.copy(file, file.resolveSibling(file.getFileName() + ".old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            DefinitionUtil.loadFromConfig();
            if (ServerPlayerManager.INSTANCE != null) {
                for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
                    QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(online);
                    manager.reload();
                    ServerPlayerManager.INSTANCE.syncPlayer(manager);
                }
            }
            return true;
        } catch (Exception e) {
            Questlog.LOGGER.error("Unable to save quest {}", id, e);
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) {}
        }
    }
}
