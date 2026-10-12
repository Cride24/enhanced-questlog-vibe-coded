package org.infernalstudios.questlog;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;

public class QuestlogFabricEventForwarder {
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(QuestlogEvents::onServerStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> QuestlogEvents.onServerStop());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> QuestlogEvents.onServerPlayerLogin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> QuestlogEvents.onServerPlayerLogout(handler.player));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> QuestlogEvents.registerCommands(dispatcher));
        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.CONTENT_PHASE,
                org.infernalstudios.questlog.core.QuestChatReferences::decorate);
    }

    public static void initClient() {
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, delta) -> {
            if (net.minecraft.client.Minecraft.getInstance().screen == null)
                org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.render(graphics, -1, -1);
        });
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((current, graphics, x, y, delta) ->
                    org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.render(graphics,x,y));
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register((current,x,y,button) ->
                    !org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.click(x,y,button));
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseRelease(screen).register((current,x,y,button) ->
                    !org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.release(x,y,button));
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen).register((current,x,y,horizontal,vertical) ->
                    !org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.scroll(x,y,vertical));
        });
        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> QuestlogClientEvents.onClientTick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> QuestlogClientEvents.onClientPlayerLogin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> QuestlogClientEvents.onClientPlayerLogout());
    }
}
