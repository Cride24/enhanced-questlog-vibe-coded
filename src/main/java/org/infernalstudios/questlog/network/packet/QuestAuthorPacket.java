package org.infernalstudios.questlog.network.packet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.core.*;
import org.infernalstudios.questlog.core.validation.*;
import org.infernalstudios.questlog.network.IPacketContext;
import org.infernalstudios.questlog.platform.Services;

public record QuestAuthorPacket(ResourceLocation id, int action) implements CustomPacketPayload {
    public static final int EDIT = 0, ACTIVATE = 1, ANALYSE = 2, SAVE = 3, SAVE_DRAFT = 4;
    public static final Type<QuestAuthorPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Questlog.MODID, "author"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestAuthorPacket> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, QuestAuthorPacket::id, ByteBufCodecs.INT, QuestAuthorPacket::action, QuestAuthorPacket::new);
    public static QuestValidation.References references(ServerPlayer player) {
        return QuestReferences.forServer(player);
    }
    public static JsonArray report(ResourceLocation id, QuestValidation.Report report) {
        JsonArray rows = new JsonArray();
        for (var issue : report.issues()) {
            JsonObject row = new JsonObject();
            row.addProperty("id", id.toString()); row.addProperty("path", issue.path());
            row.addProperty("message", issue.message()); row.addProperty("error", issue.error());
            rows.add(row);
        }
        return rows;
    }
    /** Keep the S2C payload below Minecraft's limit while preserving complete JSON rows. */
    public static String encodeReport(JsonArray rows) {
        JsonArray bounded = new JsonArray();
        int length = 2;
        for (var row : rows) {
            int rowLength = row.toString().length() + 1;
            if (length + rowLength > 240_000) {
                JsonObject truncated = new JsonObject();
                truncated.addProperty("id", rows.get(0).getAsJsonObject().get("id").getAsString());
                truncated.addProperty("path", "$");
                truncated.addProperty("message", "questlog.validation.truncated");
                truncated.addProperty("error", false);
                bounded.add(truncated);
                break;
            }
            bounded.add(row);
            length += rowLength;
        }
        return bounded.toString();
    }
    public static void handle(QuestAuthorPacket packet, IPacketContext ctx) {
        if (!(ctx.getSender() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
        var refs = references(player);
        if (packet.action == ANALYSE) {
            JsonArray rows = new JsonArray();
            for (ResourceLocation id : DefinitionUtil.getCachedQuestKeys())
                rows.addAll(report(id, QuestValidation.inspect(DefinitionUtil.getCachedQuest(id), refs)));
            Services.PLATFORM.sendPacketToClient(player, new QuestAuthorResultPacket(packet.id, ANALYSE, true, encodeReport(rows)));
            return;
        }
        if (packet.action != EDIT && packet.action != ACTIVATE) return;
        if (!DefinitionUtil.hasCachedQuest(packet.id)) return;
        JsonObject original = DefinitionUtil.getCachedQuest(packet.id);
        JsonObject definition = original.deepCopy();
        var report = QuestValidation.inspect(definition, refs);
        boolean success;
        if (packet.action == ACTIVATE && !report.functional()) success = false;
        else {
            definition.addProperty("active", packet.action == ACTIVATE);
            success = packet.action == EDIT && original.has("active") && !original.get("active").getAsBoolean()
                    || QuestDefinitionWriter.write(player, packet.id, definition);
        }
        Services.PLATFORM.sendPacketToClient(player, new QuestAuthorResultPacket(packet.id, packet.action, success, encodeReport(report(packet.id, report))));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
