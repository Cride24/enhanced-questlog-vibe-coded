package org.infernalstudios.questlog.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.core.QuestTracking;
import java.util.ArrayList;
import java.util.List;

public record QuestTrackingPacket(List<ResourceLocation> ids) implements CustomPacketPayload {
    public QuestTrackingPacket { ids = List.copyOf(ids); }
    public static final Type<QuestTrackingPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Questlog.MODID, "tracking"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestTrackingPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ResourceLocation.STREAM_CODEC, QuestTracking.MAX_IDS), QuestTrackingPacket::ids, QuestTrackingPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
