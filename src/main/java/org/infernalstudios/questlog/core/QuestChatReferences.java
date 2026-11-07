package org.infernalstudios.questlog.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.core.quests.Quest;

import java.util.HashSet;
import java.util.Set;

/** Converts visible quest names in ordinary chat into links to stable IDs. */
public final class QuestChatReferences {
    private QuestChatReferences() {}

    public static Component decorate(ServerPlayer sender, Component message) {
        if (sender == null || ServerPlayerManager.INSTANCE == null) return message;
        QuestManager manager = ServerPlayerManager.INSTANCE.getManagerIfPresent(sender);
        if (manager == null || !manager.isLoaded()) return message;
        return decorate(message, manager.getAllQuests());
    }

    static Component decorate(Component message, Iterable<Quest> quests) {
        String source = message.getString();
        MutableComponent result = Component.empty().withStyle(message.getStyle());
        int cursor = 0, plainFrom = 0;
        boolean replaced = false;
        while ((cursor = source.indexOf("quest:", cursor)) >= 0) {
            int nameStart = cursor + 6;
            boolean quoted = nameStart < source.length() && source.charAt(nameStart) == '"';
            int tokenStart = quoted ? nameStart + 1 : nameStart;
            int tokenEnd = quoted ? source.indexOf('"', tokenStart) : -1;
            Match best = null;
            boolean ambiguous = false;
            if (!quoted || tokenEnd >= 0) for (Quest quest : quests) {
                if (!quest.isVisibleToPlayer()) continue;
                Set<String> names = new HashSet<>();
                names.add(quest.getId().toString());
                names.add(quest.getDisplay().getTitle().getString());
                JsonObject definition = DefinitionUtil.hasCachedQuest(quest.getId())
                        ? DefinitionUtil.getCachedQuest(quest.getId()) : null;
                if (definition != null) for (var entry : definition.entrySet()) {
                    if (!entry.getKey().equals("title") && !entry.getKey().startsWith("title-")) continue;
                    JsonElement value = entry.getValue();
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) names.add(value.getAsString());
                }
                for (String name : names) {
                    if (name.isBlank() || tokenStart + name.length() > source.length()
                            || quoted && tokenStart + name.length() != tokenEnd
                            || !source.regionMatches(true, tokenStart, name, 0, name.length())) continue;
                    int end = tokenStart + name.length();
                    if (!quoted && end < source.length() && (Character.isLetterOrDigit(source.charAt(end))
                            || source.charAt(end) == '_' || source.charAt(end) == ':')) continue;
                    int length = name.length();
                    if (best == null || length > best.length) {
                        best = new Match(quest.getId(), length);
                        ambiguous = false;
                    } else if (length == best.length && !quest.getId().equals(best.id)) ambiguous = true;
                }
            }
            if (best == null || ambiguous) { cursor = nameStart; continue; }
            int end = quoted ? tokenEnd + 1 : tokenStart + best.length;
            result.append(Component.literal(source.substring(plainFrom, cursor)));
            ResourceLocation id = best.id;
            result.append(Component.literal(source.substring(cursor, end)).withStyle(style -> style
                    .withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/questlog_view " + id))));
            replaced = true;
            cursor = end;
            plainFrom = end;
        }
        if (!replaced) return message;
        return result.append(Component.literal(source.substring(plainFrom)));
    }

    private record Match(ResourceLocation id, int length) {}
}
