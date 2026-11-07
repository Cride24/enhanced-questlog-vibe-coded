package org.infernalstudios.questlog.core;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.network.packet.QuestOpenPacket;
import org.infernalstudios.questlog.network.packet.QuestShareCandidatesPacket;
import org.infernalstudios.questlog.platform.Services;

import java.util.UUID;

/** All share offers and quest openings are checked against the recipient's server-side quest. */
public final class QuestSharing {
    private QuestSharing() {}

    public static boolean maySendDefinition(Quest quest, boolean authorMode) {
        return authorMode || quest.isVisibleToPlayer() || quest.isOffered();
    }

    public static boolean canSeeAllDefinitions(ServerPlayer player, QuestManager manager) {
        // The integrated server and its owner share the same static definition cache.
        return player.getServer().isSingleplayerOwner(player.getGameProfile())
                || player.hasPermissions(2) && manager.isEditMode();
    }

    private static QuestManager manager(ServerPlayer player) {
        if (ServerPlayerManager.INSTANCE == null) return null;
        QuestManager manager = ServerPlayerManager.INSTANCE.getManagerIfPresent(player);
        return manager != null && manager.isLoaded() ? manager : null;
    }

    public static void search(ServerPlayer sender, ResourceLocation id, String query) {
        QuestManager manager = manager(sender);
        Quest quest = manager == null ? null : manager.getQuest(id);
        if (quest == null || !quest.isShareable() || !quest.isVisibleToPlayer() || AdventureGroups.INSTANCE == null) return;
        CompoundTag data = new CompoundTag();
        data.putString("quest", id.toString());
        ListTag candidates = new ListTag();
        for (ServerPlayer player : AdventureGroups.INSTANCE.playerCandidates(sender, query, false)) {
            CompoundTag row = new CompoundTag();
            row.putString("id", player.getStringUUID());
            row.putString("name", player.getGameProfile().getName());
            candidates.add(row);
        }
        data.put("candidates", candidates);
        Services.PLATFORM.sendPacketToClient(sender, new QuestShareCandidatesPacket(data));
    }

    public static void offer(ServerPlayer sender, ResourceLocation id, String targetId) {
        QuestManager source = manager(sender);
        Quest quest = source == null ? null : source.getQuest(id);
        UUID uuid;
        try { uuid = UUID.fromString(targetId); } catch (IllegalArgumentException error) { return; }
        ServerPlayer target = sender.getServer().getPlayerList().getPlayer(uuid);
        QuestManager destination = target == null ? null : manager(target);
        Quest offered = destination == null ? null : destination.getQuest(id);
        if (quest == null || !quest.isShareable() || !quest.isVisibleToPlayer() || target == null
                || target == sender || offered == null) {
            sender.sendSystemMessage(Component.translatable("questlog.share.unavailable"));
            return;
        }
        CompoundTag before = offered.serialize();
        if (!offered.offer(sender.getStringUUID())) {
            sender.sendSystemMessage(Component.translatable("questlog.share.unavailable"));
            return;
        }
        if (!ServerPlayerManager.INSTANCE.save(destination)) {
            offered.deserialize(before);
            sender.sendSystemMessage(Component.translatable("questlog.share.unavailable"));
            return;
        }
        ServerPlayerManager.INSTANCE.syncPlayer(destination);
        Component link = quest.getDisplay().getTitle().copy().withStyle(style -> style
                .withColor(ChatFormatting.GOLD).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/questlog_proposal " + id)));
        target.sendSystemMessage(Component.translatable("questlog.share.offered", sender.getName(), link));
        sender.sendSystemMessage(Component.translatable("questlog.share.sent", target.getName()));
    }

    public static void accept(ServerPlayer player, ResourceLocation id) {
        QuestManager manager = manager(player);
        Quest quest = manager == null ? null : manager.getQuest(id);
        if (quest == null) {
            player.sendSystemMessage(Component.translatable("questlog.share.unavailable"));
            return;
        }
        CompoundTag before = quest.serialize();
        if (!quest.acceptOffer() || !ServerPlayerManager.INSTANCE.save(manager)) {
            quest.deserialize(before);
            player.sendSystemMessage(Component.translatable("questlog.share.unavailable"));
            return;
        }
        manager.sync(id);
        ServerPlayerManager.INSTANCE.save(manager);
    }

    public static boolean open(ServerPlayer player, ResourceLocation id, boolean proposal) {
        QuestManager manager = manager(player);
        Quest quest = manager == null ? null : manager.getQuest(id);
        if (quest == null || (proposal ? !quest.isOffered() : !quest.isVisibleToPlayer())) return false;
        if (!manager.isSyncedToClient(id)) ServerPlayerManager.INSTANCE.syncPlayer(manager);
        Services.PLATFORM.sendPacketToClient(player, new QuestOpenPacket(id.toString(), proposal));
        return true;
    }
}
