package org.infernalstudios.questlog.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.QuestlogClient;
import org.infernalstudios.questlog.client.gui.screen.QuestDetails;
import org.infernalstudios.questlog.core.QuestTracking;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.network.packet.QuestTrackingUpdatePacket;
import org.infernalstudios.questlog.platform.Services;

import java.util.*;

/** Shared renderer/controller. Loader adapters supply native HUD and screen callbacks. */
public final class TrackedQuestsOverlay {
    private static final QuestTracking TRACKING = new QuestTracking();
    private static List<Quest> rows = List.of();
    private static boolean dirty = true;
    private static TrackingWindow window;
    private static int scroll;
    private static final TrackingReorder reorder = new TrackingReorder();
    private static final int HEADER=TrackingWindow.HEADER, BORDER=TrackingWindow.BORDER, ROW=28;
    private static org.infernalstudios.questlog.util.texture.NineSliceTexture panel;
    private static boolean authorMode;
    private static final org.infernalstudios.questlog.core.quests.display.Palette PALETTE = new org.infernalstudios.questlog.core.quests.display.Palette(null,null,null,null,null);
    private TrackedQuestsOverlay() {}
    public static void accept(List<ResourceLocation> ids) {
        if (!TRACKING.ids().equals(ids)) reorder.end();
        TRACKING.replace(ids, id -> true); invalidate();
    }
    public static void invalidate() { dirty=true; }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (window == null || !window.dragging() && !reorder.active()) return;
        if (!(mc.screen instanceof ChatScreen) || !mc.isWindowActive()) {
            reorder.end(); invalidate();
            if (window.dragging()) {
                var config = Questlog.getConfig().tracking;
                config.x=window.x; config.y=window.y; config.width=window.width; config.height=window.height;
                window.end(); Questlog.saveConfig();
            }
        // MouseHandler.isLeftPressed is only maintained during gameplay, never in a screen.
        } else if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == org.lwjgl.glfw.GLFW.GLFW_RELEASE) {
            double x=mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/mc.getWindow().getScreenWidth();
            double y=mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/mc.getWindow().getScreenHeight();
            release(x,y,0);
        }
    }
    public static void clear() { TRACKING.replace(List.of(), id -> true); rows=List.of(); reorder.end(); window=null; panel=null; scroll=0; dirty=true; }
    public static boolean follows(ResourceLocation id) { return TRACKING.contains(id); }
    public static void toggle(ResourceLocation id) {
        List<ResourceLocation> ids=new ArrayList<>(TRACKING.ids());
        if (!ids.remove(id)) ids.add(id);
        submit(ids);
    }
    private static void submit(List<ResourceLocation> ids) {
        accept(ids); Services.PLATFORM.sendPacketToServer(new QuestTrackingUpdatePacket(ids));
    }
    private static void refresh() {
        if (dirty || authorMode != QuestlogClient.isEditModeActive) {
            if (authorMode != QuestlogClient.isEditModeActive) reorder.end();
            authorMode = QuestlogClient.isEditModeActive;
            rows=reorder.active() ? reorder.display(QuestlogClient.getLocal(), authorMode)
                    : TRACKING.display(QuestlogClient.getLocal(), authorMode);
            if (reorder.active() && rows.stream().noneMatch(quest -> quest.getId().equals(reorder.source()))) {
                reorder.end(); rows=TRACKING.display(QuestlogClient.getLocal(),authorMode);
            }
            dirty=false;
        }
    }
    private static boolean available() {
        Minecraft mc=Minecraft.getInstance();
        return mc.player != null && mc.level != null && Questlog.getConfig().tracking.enabled
                && !mc.options.hideGui && (mc.screen == null || mc.screen instanceof ChatScreen) && !TRACKING.ids().isEmpty();
    }
    private static void geometry() {
        Minecraft mc=Minecraft.getInstance();
        if (window==null) {
            var config=Questlog.getConfig().tracking;
            window=new TrackingWindow(config.x,config.y,config.width,config.height);
        }
        window.clamp(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight() - (mc.screen instanceof ChatScreen ? 30 : 0));
        scroll=Math.max(0, Math.min(scroll, Math.max(0,rows.size()-capacity())));
    }
    private static int capacity() { return Math.max(1,(window.height-HEADER-BORDER)/ROW); }
    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!available()) { tick(); return; }
        refresh(); if (rows.isEmpty()) return;
        geometry();
        boolean interactive=Minecraft.getInstance().screen instanceof ChatScreen;
        if (interactive && window.dragging()) window.drag(mouseX,mouseY,Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight()-30);
        geometry();
        if (interactive && reorder.active()) updateReorder(mouseY);
        var font=Minecraft.getInstance().font;
        if (panel == null || panel.width() != window.width || panel.height() != window.height)
            panel = new org.infernalstudios.questlog.util.texture.NineSliceTexture(QuestlogGuiSet.DEFAULT.backgroundLoc,
                    window.width, window.height, 375, 174, 275, 166, 1024, 512, 16, 16);
        graphics.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,Questlog.getConfig().tracking.backgroundAlpha());
        try {
            panel.blit(graphics,window.x,window.y);
            graphics.pose().pushPose();
            graphics.pose().translate(window.x+16,window.y+20,0);
            graphics.pose().scale((window.width-32)/(float)QuestlogGuiSet.DEFAULT.panelHR.width(),1,1);
            QuestlogGuiSet.DEFAULT.panelHR.blit(graphics,0,0);
            graphics.pose().popPose();
            graphics.flush();
        }
        finally { com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1); }
        var palette = PALETTE;
        String count = rows.size()>capacity() ? (scroll+1)+"–"+Math.min(rows.size(),scroll+capacity())+"/"+rows.size() : "";
        int countWidth = font.width(count);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("questlog.tracking.title").getString(), Math.max(0,window.width-24-(count.isEmpty()?0:countWidth+6))),window.x+12,window.y+10,palette.titleColor(),false);
        if (!count.isEmpty()) graphics.drawString(font,count,window.x+window.width-12-countWidth,window.y+10,palette.textColor(),false);
        graphics.enableScissor(window.x+BORDER,window.y+HEADER,window.x+window.width-BORDER,window.y+window.height-BORDER);
        for (int index=scroll; index<Math.min(rows.size(),scroll+capacity());index++) {
            Quest quest=rows.get(index);
            int y=window.y+HEADER+(index-scroll)*ROW;
            if (reorder.active() && quest.getId().equals(reorder.source())) {
                graphics.fill(window.x+BORDER,y,window.x+window.width-BORDER,y+ROW,0x226D4C27);
                continue;
            }
            boolean hovered=interactive && !reorder.active() && mouseX>=window.x+BORDER && mouseX<window.x+window.width-BORDER && mouseY>=y && mouseY<y+ROW;
            renderRow(graphics,quest,y,hovered);
        }
        if (interactive && reorder.active()) {
            Quest quest=QuestlogClient.getLocal().getQuest(reorder.source());
            if (quest != null) {
                int y=(int)floatingTop(mouseY);
                graphics.fill(window.x+BORDER,y,window.x+window.width-BORDER,y+ROW,0xDDE9D5B6);
                renderRow(graphics,quest,y,false);
            }
        }
        graphics.disableScissor();
        if (interactive) graphics.drawString(font,"◢",window.x+window.width-12,window.y+window.height-12,palette.textColor(),false);
    }
    private static void renderRow(GuiGraphics graphics, Quest quest, int y, boolean hovered) {
        var font=Minecraft.getInstance().font;
        var palette=PALETTE;
        if (hovered) graphics.fill(window.x+BORDER,y,window.x+window.width-BORDER,y+ROW,0x226D4C27);
        if (quest.getDisplay().getIcon()!=null) {
            graphics.pose().pushPose();
            var icon=quest.getDisplay().getIcon();
            graphics.pose().translate(window.x+10,y+5,0);
            float scale=16f/Math.max(1,Math.max(icon.width(),icon.height()));
            graphics.pose().scale(scale,scale,1);
            icon.blit(graphics,0,0); graphics.pose().popPose();
        } else graphics.drawString(font,"≡",window.x+12,y+8,palette.textColor(),false);
        int color=quest.isActive() ? palette.textColor() : 0x777777;
        graphics.drawString(font,font.plainSubstrByWidth(quest.getDisplay().getTitle().getString(),window.width-48),window.x+32,y+3,color,false);
        String status=!quest.isActive() ? "disabled" : quest.isCompleted() ? (quest.hasPendingRewards() ? "reward_pending" : "completed") : quest.isFailed() ? "failed" : !quest.isTriggered() ? "locked" : "in_progress";
        graphics.drawString(font,font.plainSubstrByWidth(Component.translatable("questlog.tracking."+status).getString(),window.width-48),window.x+32,y+14,quest.isActive()?palette.progressTextColor():0x777777,false);
    }
    private static double floatingTop(double pointerY) {
        return Math.max(window.y+HEADER,Math.min(reorder.top(pointerY),window.y+HEADER+(Math.min(rows.size(),capacity())-1)*ROW));
    }
    private static void updateReorder(double pointerY) {
        double clampedPointer=pointerY+floatingTop(pointerY)-reorder.top(pointerY);
        if (reorder.move(rows,scroll,window.y+HEADER,ROW,clampedPointer)) { invalidate(); refresh(); }
    }
    public static boolean click(double x, double y, int button) {
        if (button!=0 || !(Minecraft.getInstance().screen instanceof ChatScreen) || !available()) return false;
        refresh(); if (rows.isEmpty()) return false; geometry();
        if (!window.contains(x,y)) return false;
        if (window.begin(x,y)) return true;
        int index=rowAt(y);
        if (index>=0 && index<rows.size()) {
            Quest quest=rows.get(index);
            if (x<window.x+32) {
                reorder.begin(TRACKING.ids(),quest.getId(),y,window.y+HEADER+(index-scroll)*ROW);
                invalidate();
            }
            else Minecraft.getInstance().setScreen(QuestDetails.overview(Minecraft.getInstance().screen,quest));
        }
        return true;
    }
    public static boolean release(double x,double y,int button) {
        if (button!=0 || window==null || !window.dragging() && !reorder.active()) return false;
        if (window.dragging()) {
            window.drag(x,y,Minecraft.getInstance().getWindow().getGuiScaledWidth(),Minecraft.getInstance().getWindow().getGuiScaledHeight()-30);
            var config=Questlog.getConfig().tracking;
            config.x=window.x; config.y=window.y; config.width=window.width; config.height=window.height;
            window.end(); Questlog.saveConfig();
        }
        if (reorder.active()) {
            refresh();
            if (!reorder.active()) return true;
            updateReorder(y);
            List<ResourceLocation> ids=reorder.ids();
            reorder.end(); invalidate();
            if (!TRACKING.ids().equals(ids)) submit(ids);
        }
        return true;
    }
    public static boolean scroll(double x,double y,double amount) {
        if (!(Minecraft.getInstance().screen instanceof ChatScreen) || !available()) return false;
        refresh(); if (rows.isEmpty()) return false; geometry(); if (!window.contains(x,y)) return false;
        scroll=Math.max(0,Math.min(Math.max(0,rows.size()-capacity()),scroll-(int)Math.signum(amount)));
        return true;
    }
    private static int rowAt(double y) {
        return y<window.y+HEADER || y>=window.y+HEADER+capacity()*ROW || y>=window.y+window.height-BORDER
                ? -1 : scroll+(int)((y-window.y-HEADER)/ROW);
    }
}
