package org.infernalstudios.questlog.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.core.*;
import org.infernalstudios.questlog.network.IPacketContext;
import org.infernalstudios.questlog.platform.Services;
import java.util.ArrayList;
import java.util.List;

public record QuestTrackingUpdatePacket(List<ResourceLocation> ids) implements CustomPacketPayload {
    public QuestTrackingUpdatePacket { ids = List.copyOf(ids); }
    public static final Type<QuestTrackingUpdatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Questlog.MODID, "tracking_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestTrackingUpdatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ResourceLocation.STREAM_CODEC, QuestTracking.MAX_IDS), QuestTrackingUpdatePacket::ids, QuestTrackingUpdatePacket::new);
    public static void handle(QuestTrackingUpdatePacket packet, IPacketContext ctx) {
        if (!(ctx.getSender() instanceof ServerPlayer player) || ServerPlayerManager.INSTANCE == null) return;
        QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
        if (!manager.isLoaded()) return;
        manager.tracking.replace(packet.ids, id -> {
            var quest = manager.getQuest(id);
            return quest != null && (manager.tracking.contains(id) || quest.isVisibleToPlayer())
                    && (!quest.isActive() || !quest.isCompleted() || !quest.isRewarded());
        });
        manager.tracking.prune(manager);
        ServerPlayerManager.INSTANCE.save(manager);
        Services.PLATFORM.sendPacketToClient(player, new QuestTrackingPacket(manager.tracking.ids()));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
