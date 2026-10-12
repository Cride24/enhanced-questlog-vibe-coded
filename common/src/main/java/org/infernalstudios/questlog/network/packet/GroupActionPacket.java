package org.infernalstudios.questlog.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.core.AdventureGroups;
import org.infernalstudios.questlog.network.IPacketContext;

import java.util.UUID;

public record GroupActionPacket(int action, String groupId, String targetId, String value) implements CustomPacketPayload {
    public static final int REFRESH = 13;
    public static final Type<GroupActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Questlog.MODID, "group_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GroupActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GroupActionPacket::action,
            ByteBufCodecs.stringUtf8(36), GroupActionPacket::groupId,
            ByteBufCodecs.stringUtf8(36), GroupActionPacket::targetId,
            ByteBufCodecs.stringUtf8(64), GroupActionPacket::value,
            GroupActionPacket::new);

    private static UUID parse(String value) {
        try { return UUID.fromString(value); } catch (IllegalArgumentException e) { return null; }
    }

    public static void handle(GroupActionPacket packet, IPacketContext context) {
        if (!(context.getSender() instanceof ServerPlayer player) || AdventureGroups.INSTANCE == null) return;
        if (packet.action == REFRESH) AdventureGroups.INSTANCE.sync(player, packet.value);
        else AdventureGroups.INSTANCE.act(player, packet.action, parse(packet.groupId), parse(packet.targetId), packet.value);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
