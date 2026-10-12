package org.infernalstudios.questlog;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public class QuestlogNeoForgeEventForwarder {
    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        QuestlogEvents.onServerStart(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerSave(PlayerEvent.SaveToFile event) {
        QuestlogEvents.onPlayerSave((ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent event) {
        QuestlogEvents.onServerStop();
    }

    @SubscribeEvent
    public static void onServerPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        QuestlogEvents.onServerPlayerLogin((ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public static void onServerPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        QuestlogEvents.onServerPlayerLogout((ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        QuestlogEvents.registerCommands(event.getDispatcher());
    }

    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void renderHud(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
            if (net.minecraft.client.Minecraft.getInstance().screen == null)
                org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.render(event.getGuiGraphics(), -1, -1);
        }
        @SubscribeEvent
        public static void renderChat(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
            if (event.getScreen() instanceof net.minecraft.client.gui.screens.ChatScreen)
                org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.render(event.getGuiGraphics(),event.getMouseX(),event.getMouseY());
        }
        @SubscribeEvent
        public static void clickChat(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
            if (event.getScreen() instanceof net.minecraft.client.gui.screens.ChatScreen
                    && org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.click(event.getMouseX(),event.getMouseY(),event.getButton())) event.setCanceled(true);
        }
        @SubscribeEvent
        public static void releaseChat(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonReleased.Pre event) {
            if (event.getScreen() instanceof net.minecraft.client.gui.screens.ChatScreen
                    && org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.release(event.getMouseX(),event.getMouseY(),event.getButton())) event.setCanceled(true);
        }
        @SubscribeEvent
        public static void scrollChat(net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre event) {
            if (event.getScreen() instanceof net.minecraft.client.gui.screens.ChatScreen
                    && org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.scroll(event.getMouseX(),event.getMouseY(),event.getScrollDeltaY())) event.setCanceled(true);
        }
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            QuestlogClientEvents.onClientTick();
        }

        @SubscribeEvent
        public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            QuestlogClientEvents.onClientPlayerLogin();
        }

        @SubscribeEvent
        public static void onClientPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            QuestlogClientEvents.onClientPlayerLogout();
        }
    }
}
