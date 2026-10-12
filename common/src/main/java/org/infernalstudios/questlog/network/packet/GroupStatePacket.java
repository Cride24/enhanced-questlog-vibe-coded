package org.infernalstudios.questlog.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.Questlog;

public record GroupStatePacket(CompoundTag data) implements CustomPacketPayload {
    public static final Type<GroupStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Questlog.MODID, "group_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GroupStatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, GroupStatePacket::data, GroupStatePacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
