package org.infernalstudios.questlog.client.gui.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.QuestlogClient;
import org.infernalstudios.questlog.client.gui.components.ScrollableComponent;
import org.infernalstudios.questlog.client.gui.components.scrollable.Scrollable;

/** Informational author report. Uses the same scroll container as the existing editor. */
public final class QuestValidationScreen extends Screen {
    private final Screen previous;
    private final JsonArray rows;
    public QuestValidationScreen(Screen previous, JsonArray rows) {
        super(Component.translatable("questlog.validation.title"));
        this.previous = previous; this.rows = rows;
    }
    @Override protected void init() {
        addRenderableWidget(new ScrollableComponent(20, 38, Math.max(80, width - 40), Math.max(24, height - 80), new Rows()));
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose()).bounds(width / 2 - 50, height - 30, 100, 20).build());
    }
    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        if (rows.isEmpty()) graphics.drawCenteredString(font, Component.translatable("questlog.validation.clear"), width / 2, 45, 0x88DD88);
        super.render(graphics, mouseX, mouseY, delta);
    }
    private final class Rows implements Scrollable, GuiEventListener {
        private ScrollableComponent scroller;
        public int getHeight() { return rows.size() * 36; }
        public void setScrollableComponent(ScrollableComponent scroller) { this.scroller = scroller; }
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int first = Math.max(0,(int)scroller.getScrollAmount()/36);
            int last = Math.min(rows.size(),first+scroller.height/36+2);
            for (int i = first; i < last; i++) {
                JsonObject row = rows.get(i).getAsJsonObject();
                int y = (int) scroller.getYOffset() + i * 36;
                int x = (int) scroller.getXOffset() + 4;
                int color = row.get("error").getAsBoolean() ? 0xFF9999 : 0xFFDD88;
                graphics.drawString(font, font.plainSubstrByWidth(row.get("id").getAsString() + " · " + row.get("path").getAsString(), width - 80), x, y + 2, color);
                graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(row.get("message").getAsString()).getString(), width - 80), x, y + 14, 0xFFFFFF);
            }
        }
        public boolean mouseClicked(double x, double y, int button) {
            if (button != 0 || x < 0 || x >= scroller.width - 16) return false;
            int index = (int) y / 36;
            if (y < 0 || index >= rows.size()) return false;
            ResourceLocation id = ResourceLocation.tryParse(rows.get(index).getAsJsonObject().get("id").getAsString());
            var quest = id == null ? null : QuestlogClient.getLocal().getQuest(id);
            if (quest != null) minecraft.setScreen(new QuestDetails(QuestValidationScreen.this, quest));
            return quest != null;
        }
        public void setFocused(boolean focused) {}
        public boolean isFocused() { return false; }
    }
}
