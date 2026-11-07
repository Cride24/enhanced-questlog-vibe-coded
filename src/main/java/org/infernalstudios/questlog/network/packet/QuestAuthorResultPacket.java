package org.infernalstudios.questlog.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.Questlog;

public record QuestAuthorResultPacket(ResourceLocation id, int action, boolean success, String report) implements CustomPacketPayload {
    public static final Type<QuestAuthorResultPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Questlog.MODID, "author_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestAuthorResultPacket> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, QuestAuthorResultPacket::id,
            ByteBufCodecs.INT, QuestAuthorResultPacket::action,
            ByteBufCodecs.BOOL, QuestAuthorResultPacket::success,
            ByteBufCodecs.stringUtf8(262_144), QuestAuthorResultPacket::report, QuestAuthorResultPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
