package org.infernalstudios.questlog.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.core.QuestSharing;
import org.infernalstudios.questlog.network.IPacketContext;

public record QuestShareActionPacket(int action, ResourceLocation questId, String value) implements CustomPacketPayload {
    public static final int SEARCH = 0, OFFER = 1, ACCEPT = 2;
    public static final Type<QuestShareActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Questlog.MODID, "share_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestShareActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestShareActionPacket::action,
            ResourceLocation.STREAM_CODEC, QuestShareActionPacket::questId,
            ByteBufCodecs.stringUtf8(64), QuestShareActionPacket::value,
            QuestShareActionPacket::new);

    public static void handle(QuestShareActionPacket packet, IPacketContext context) {
        if (!(context.getSender() instanceof ServerPlayer player)) return;
        switch (packet.action) {
            case SEARCH -> QuestSharing.search(player, packet.questId, packet.value);
            case OFFER -> QuestSharing.offer(player, packet.questId, packet.value);
            case ACCEPT -> QuestSharing.accept(player, packet.questId);
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
