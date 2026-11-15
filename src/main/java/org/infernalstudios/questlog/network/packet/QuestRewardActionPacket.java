package org.infernalstudios.questlog.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.core.GroupRewards;
import org.infernalstudios.questlog.core.QuestManager;
import org.infernalstudios.questlog.core.ServerPlayerManager;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.network.IPacketContext;

import java.util.UUID;

public record QuestRewardActionPacket(ResourceLocation questId, int rewardIndex, String allocationId,
                                      int action, String target) implements CustomPacketPayload {
    public static final int START = 0, YES = 1, NO = 2, DESIGNATE = 3;
    public static final Type<QuestRewardActionPacket> TYPE = new Type<>(Questlog.id("reward_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestRewardActionPacket> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, QuestRewardActionPacket::questId,
            ByteBufCodecs.VAR_INT, QuestRewardActionPacket::rewardIndex,
            ByteBufCodecs.stringUtf8(36), QuestRewardActionPacket::allocationId,
            ByteBufCodecs.VAR_INT, QuestRewardActionPacket::action,
            ByteBufCodecs.stringUtf8(36), QuestRewardActionPacket::target,
            QuestRewardActionPacket::new);

    public static void handle(QuestRewardActionPacket packet, IPacketContext context) {
        if (!(context.getSender() instanceof ServerPlayer player) || GroupRewards.INSTANCE == null) return;
        QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
        Quest quest = manager.getQuest(packet.questId);
        if (quest == null || !quest.isActive() || !quest.isGroupQuest()
                || packet.rewardIndex < 0
                || packet.rewardIndex >= quest.rewards.size()) return;
        switch (packet.action) {
            case START -> GroupRewards.INSTANCE.startById(packet.allocationId, packet.questId,
                    packet.rewardIndex, player.getUUID());
            case YES, NO -> {
                if (quest.rewards.get(packet.rewardIndex).allocationId().equals(packet.allocationId))
                    GroupRewards.INSTANCE.respond(quest, packet.rewardIndex, player.getUUID(), packet.action == YES);
            }
            case DESIGNATE -> {
                try { GroupRewards.INSTANCE.designateById(packet.allocationId, packet.questId,
                        packet.rewardIndex, player.getUUID(), UUID.fromString(packet.target)); }
                catch (IllegalArgumentException ignored) {}
            }
            default -> {}
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
