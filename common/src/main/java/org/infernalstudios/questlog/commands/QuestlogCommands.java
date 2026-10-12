package org.infernalstudios.questlog.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.core.DefinitionUtil;
import org.infernalstudios.questlog.core.QuestManager;
import org.infernalstudios.questlog.core.ServerPlayerManager;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.network.packet.QuestOpenPacket;
import org.infernalstudios.questlog.core.QuestSharing;
import org.infernalstudios.questlog.platform.Services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class QuestlogCommands {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_QUEST_OR_CATEGORY = (ctx, builder) -> {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return Suggestions.empty();
        }

        QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
        if (manager == null) {
            return Suggestions.empty();
        }

        List<String> suggestions = new ArrayList<>();
        for (Quest quest : manager.getAllQuests()) {
            suggestions.add(quest.getId().toString());
        }
        for (ResourceLocation chapterId : DefinitionUtil.getCachedChapterKeys()) {
            suggestions.add(chapterId.toString());
        }
        if (!suggestions.contains(Questlog.MODID + ":main") && !suggestions.contains("main")) {
            suggestions.add(Questlog.MODID + ":main");
        }

        return SharedSuggestionProvider.suggest(suggestions, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("questlog_view")
                .then(Commands.argument("quest", ResourceLocationArgument.id())
                        .executes(ctx -> QuestSharing.open(ctx.getSource().getPlayerOrException(),
                                ResourceLocationArgument.getId(ctx, "quest"), false) ? 1 : 0)));
        dispatcher.register(Commands.literal("questlog_proposal")
                .then(Commands.argument("quest", ResourceLocationArgument.id())
                        .executes(ctx -> QuestSharing.open(ctx.getSource().getPlayerOrException(),
                                ResourceLocationArgument.getId(ctx, "quest"), true) ? 1 : 0)));
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(
                Commands.literal("questlog")
                        .requires(stack -> stack.hasPermission(2))

                        .then(Commands.literal("reload")
                                .executes(QuestlogCommands::reloadQuests)
                        )

                        .then(Commands.literal("reset_all_progress_and_reload")
                                .executes(ctx -> resetAllProgressAndReload(ctx, Collections.singletonList(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> resetAllProgressAndReload(ctx, EntityArgument.getPlayers(ctx, "players")))
                                )
                        )

                        .then(Commands.literal("open")
                                .executes(ctx -> open(ctx, null, Collections.singletonList(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("target", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_QUEST_OR_CATEGORY)
                                        .executes(ctx -> open(ctx, ResourceLocationArgument.getId(ctx, "target").toString(), Collections.singletonList(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(ctx -> open(ctx, ResourceLocationArgument.getId(ctx, "target").toString(), EntityArgument.getPlayers(ctx, "players")))
                                        )
                                )
                        )

                        .then(Commands.literal("progress")
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("target", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_QUEST_OR_CATEGORY)
                                                .executes(ctx -> modifyProgress(ctx, ResourceLocationArgument.getId(ctx, "target").toString(), false, Collections.singletonList(ctx.getSource().getPlayerOrException())))
                                                .then(Commands.argument("players", EntityArgument.players())
                                                        .executes(ctx -> modifyProgress(ctx, ResourceLocationArgument.getId(ctx, "target").toString(), false, EntityArgument.getPlayers(ctx, "players")))
                                                )
                                                .then(Commands.literal("uuid")
                                                        .then(Commands.argument("player_uuid", StringArgumentType.word())
                                                                .executes(ctx -> resetOfflineProgress(ctx, ResourceLocationArgument.getId(ctx, "target"), StringArgumentType.getString(ctx, "player_uuid")))))
                                                .then(Commands.literal("all")
                                                        .executes(ctx -> resetTargetForAll(ctx, ResourceLocationArgument.getId(ctx, "target"))))
                                        )
                                        .then(Commands.literal("all")
                                                .executes(ctx -> resetAllProgress(ctx, Collections.singletonList(ctx.getSource().getPlayerOrException())))
                                                .then(Commands.argument("players", EntityArgument.players())
                                                        .executes(ctx -> resetAllProgress(ctx, EntityArgument.getPlayers(ctx, "players")))
                                                )
                                        )
                                )
                                .then(Commands.literal("complete")
                                        .then(Commands.argument("target", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_QUEST_OR_CATEGORY)
                                                .executes(ctx -> modifyProgress(ctx, ResourceLocationArgument.getId(ctx, "target").toString(), true, Collections.singletonList(ctx.getSource().getPlayerOrException())))
                                                .then(Commands.argument("players", EntityArgument.players())
                                                        .executes(ctx -> modifyProgress(ctx, ResourceLocationArgument.getId(ctx, "target").toString(), true, EntityArgument.getPlayers(ctx, "players")))
                                                )
                                        )
                                        .then(Commands.literal("all")
                                                .executes(ctx -> completeAllProgress(ctx, Collections.singletonList(ctx.getSource().getPlayerOrException())))
                                                .then(Commands.argument("players", EntityArgument.players())
                                                        .executes(ctx -> completeAllProgress(ctx, EntityArgument.getPlayers(ctx, "players")))
                                                )
                                        )
                                )
                        )

                        .then(Commands.literal("trigger")
                                .then(Commands.literal("all")
                                        .executes(ctx -> trigger(ctx, "all", Collections.singletonList(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(ctx -> trigger(ctx, "all", EntityArgument.getPlayers(ctx, "players")))
                                        )
                                )
                                .then(Commands.argument("target", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_QUEST_OR_CATEGORY)
                                        .executes(ctx -> trigger(ctx, ResourceLocationArgument.getId(ctx, "target").toString(), Collections.singletonList(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(ctx -> trigger(ctx, ResourceLocationArgument.getId(ctx, "target").toString(), EntityArgument.getPlayers(ctx, "players")))
                                        )
                                )
                        )
                        .then(Commands.literal("edit_mode")
                                .executes(ctx -> toggleEditMode(ctx, null))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> setEditMode(ctx, BoolArgumentType.getBool(ctx, "enabled"), null))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> setEditMode(ctx, BoolArgumentType.getBool(ctx, "enabled"), EntityArgument.getPlayer(ctx, "player")))
                                        )
                                )
                        )
        );

        dispatcher.register(Commands.literal("ql").requires(stack -> stack.hasPermission(2)).redirect(root));
    }

    private static int reloadQuests(CommandContext<CommandSourceStack> ctx) {
        DefinitionUtil.loadFromConfig();
        int questCount = DefinitionUtil.getCachedQuestKeys().size();
        int chapterCount = DefinitionUtil.getCachedChapterKeys().size();

        if (ServerPlayerManager.INSTANCE != null) {
            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
                manager.reload();
                ServerPlayerManager.INSTANCE.syncPlayer(manager);
            }
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Reloaded " + questCount + " quests and " + chapterCount + " chapters from config."), true);
        return questCount;
    }

    private static int resetAllProgressAndReload(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        DefinitionUtil.loadFromConfig();
        int questCount = DefinitionUtil.getCachedQuestKeys().size();
        int chapterCount = DefinitionUtil.getCachedChapterKeys().size();

        for (ServerPlayer player : players) {
            QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
            manager.clearQuests();
            manager.createAllQuests();

            for (Quest quest : manager.getAllQuests()) {
                quest.resetProgress(quest.isGlobal());
            }

            ServerPlayerManager.INSTANCE.save(manager);
            ServerPlayerManager.INSTANCE.syncPlayer(manager);
        }

        if (ServerPlayerManager.INSTANCE != null) {
            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                if (!players.contains(player)) {
                    QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
                    manager.reload();
                    ServerPlayerManager.INSTANCE.syncPlayer(manager);
                }
            }
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Successfully reloaded " + questCount + " quests / " + chapterCount + " chapters, and reset progress for " + players.size() + " player(s)."), true);
        return players.size();
    }

    private static int open(CommandContext<CommandSourceStack> ctx, String target, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Services.PLATFORM.sendPacketToClient(player, new QuestOpenPacket(target == null ? "" : target));
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Opened Questlog UI for " + players.size() + " player(s)" + (target != null ? " targeting " + target : "")), false);
        return players.size();
    }

    private static int setEditMode(CommandContext<CommandSourceStack> ctx, boolean enabled, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = target != null ? target : ctx.getSource().getPlayerOrException();
        QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
        manager.setEditMode(enabled);
        ServerPlayerManager.INSTANCE.save(manager);
        ServerPlayerManager.INSTANCE.syncPlayer(manager);
        ctx.getSource().sendSuccess(() -> Component.literal("Edit mode for " + player.getName().getString() + " set to " + enabled), true);
        return 1;
    }

    private static int toggleEditMode(CommandContext<CommandSourceStack> ctx, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = target != null ? target : ctx.getSource().getPlayerOrException();
        QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
        return setEditMode(ctx, !manager.isEditMode(), player);
    }

    private static int trigger(CommandContext<CommandSourceStack> ctx, String target, Collection<ServerPlayer> players) {
        int count = 0;
        for (ServerPlayer player : players) {
            QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
            List<Quest> affectedQuests = "all".equalsIgnoreCase(target) ? manager.getAllQuests() : getTargetQuests(manager, target);

            for (Quest quest : affectedQuests) {
                if (!quest.isTriggered()) {
                    quest.prerequisites.forEach(trigger -> trigger.forceSetUnits(trigger.getRequiredAmount()));
                    count++;
                }
            }
            ServerPlayerManager.INSTANCE.save(manager);
            ServerPlayerManager.INSTANCE.syncPlayer(manager);
        }

        if (count == 0) {
            ctx.getSource().sendFailure(Component.literal("No quests found or able to be triggered for target: " + target));
            return 0;
        }

        final int finalCount = count;
        ctx.getSource().sendSuccess(() -> Component.literal("Successfully triggered " + finalCount + " quests across " + players.size() + " player(s)."), true);
        return finalCount;
    }

    private static int modifyProgress(CommandContext<CommandSourceStack> ctx, String target, boolean complete, Collection<ServerPlayer> players) {
        int modified = 0;

        for (ServerPlayer player : players) {
            QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
            List<Quest> affectedQuests = getTargetQuests(manager, target);

            for (Quest quest : affectedQuests) {
                if (complete) {
                    quest.objectives.forEach(obj -> obj.forceSetUnits(obj.getRequiredAmount()));
                    quest.failureConditions.forEach(obj -> obj.forceSetUnits(0)); // clear fails if force completed
                } else {
                    quest.resetProgress(quest.isGlobal());
                }
            }
            ServerPlayerManager.INSTANCE.save(manager);
            ServerPlayerManager.INSTANCE.syncPlayer(manager);
            modified += affectedQuests.size();
        }

        if (modified == 0) {
            ctx.getSource().sendFailure(Component.literal("No quests found for target: " + target));
            return 0;
        }

        String action = complete ? "Completed" : "Reset";
        final int fModified = modified;
        ctx.getSource().sendSuccess(() -> Component.literal(action + " progress for " + fModified + " quests across " + players.size() + " player(s)."), true);
        return modified;
    }

    private static int resetOfflineProgress(CommandContext<CommandSourceStack> ctx, ResourceLocation questId, String value) {
        if (!DefinitionUtil.hasCachedQuest(questId)) {
            ctx.getSource().sendFailure(Component.literal("Quest not found: " + questId));
            return 0;
        }
        UUID playerId;
        try {
            playerId = UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("Invalid player UUID: " + value));
            return 0;
        }
        if (!ServerPlayerManager.INSTANCE.resetQuestForPlayer(playerId, questId)) {
            ctx.getSource().sendFailure(Component.literal("Could not reset quest data for " + playerId));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Reset " + questId + " for " + playerId), true);
        return 1;
    }

    private static int resetTargetForAll(CommandContext<CommandSourceStack> ctx, ResourceLocation questId) {
        if (!DefinitionUtil.hasCachedQuest(questId)) {
            ctx.getSource().sendFailure(Component.literal("Quest not found: " + questId));
            return 0;
        }
        int count = ServerPlayerManager.INSTANCE.resetQuestForAll(questId);
        if (count < 0) {
            ctx.getSource().sendFailure(Component.literal("Could not reset all saved players for " + questId));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Reset " + questId + " for " + count + " saved player(s)"), true);
        return count;
    }

    private static int resetAllProgress(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
            for (Quest quest : manager.getAllQuests()) {
                quest.resetProgress(quest.isGlobal());
            }
            ServerPlayerManager.INSTANCE.save(manager);
            ServerPlayerManager.INSTANCE.syncPlayer(manager);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Successfully reset all quest progress for " + players.size() + " player(s)."), true);
        return players.size();
    }

    private static int completeAllProgress(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            QuestManager manager = ServerPlayerManager.INSTANCE.getManagerByPlayer(player);
            for (Quest quest : manager.getAllQuests()) {
                quest.objectives.forEach(obj -> obj.forceSetUnits(obj.getRequiredAmount()));
                quest.failureConditions.forEach(obj -> obj.forceSetUnits(0));
            }
            ServerPlayerManager.INSTANCE.save(manager);
            ServerPlayerManager.INSTANCE.syncPlayer(manager);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("Successfully completed all quests for " + players.size() + " player(s)."), true);
        return players.size();
    }

    private static List<Quest> getTargetQuests(QuestManager manager, String target) {
        List<Quest> quests = new ArrayList<>();
        ResourceLocation id = ResourceLocation.tryParse(target);

        if (id != null) {
            Quest q = manager.getQuest(id);
            if (q != null) {
                quests.add(q);
                return quests;
            }
        }

        for (Quest quest : manager.getAllQuests()) {
            if (target.equalsIgnoreCase(quest.getDisplay().getChapter())) {
                quests.add(quest);
            }
        }

        return quests;
    }
}
