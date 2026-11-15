package org.infernalstudios.questlog.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.infernalstudios.questlog.client.gui.QuestlogGuiSet;
import org.infernalstudios.questlog.client.gui.components.QuestlogButton;
import org.infernalstudios.questlog.client.gui.components.ScrollableComponent;
import org.infernalstudios.questlog.client.gui.components.scrollable.Scrollable;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.network.ClientPacketHandler;
import org.infernalstudios.questlog.network.packet.QuestRewardActionPacket;
import org.infernalstudios.questlog.platform.Services;
import org.infernalstudios.questlog.util.texture.NineSliceTexture;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The group member picker, limited to the reward's completion snapshot. */
public final class RewardRecipientScreen extends Screen {
    private static final int PANEL_WIDTH = 220, ROW_HEIGHT = 22;
    private final Screen previous;
    private final Quest quest;
    private final int index;
    private final String allocationId;
    private final net.minecraft.nbt.ListTag beneficiaries;
    private final List<Choice> choices = new ArrayList<>();
    private UUID selected;
    private NineSliceTexture panel;
    private ScrollableComponent list;
    private QuestlogButton confirm;
    private int left, top, listHeight;

    private record Choice(UUID id, String name) {}

    public RewardRecipientScreen(Screen previous, Quest quest, int index, String allocationId,
                                 net.minecraft.nbt.ListTag beneficiaries) {
        super(Component.translatable("questlog.reward.assign"));
        this.previous = previous;
        this.quest = quest;
        this.index = index;
        this.allocationId = allocationId;
        this.beneficiaries = beneficiaries;
    }

    @Override protected void init() {
        choices.clear();
        CompoundTag state = ClientPacketHandler.groupState();
        String own = state.getString("own");
        for (Tag item : state.getList("groups", Tag.TAG_COMPOUND)) {
            CompoundTag group = (CompoundTag) item;
            if (!own.equals(group.getString("id"))) continue;
            for (Tag member : group.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) member;
                try {
                    UUID id = UUID.fromString(row.getString("id"));
                    boolean eligible = false;
                    for (Tag beneficiary : beneficiaries) if (((CompoundTag) beneficiary).hasUUID("id")
                            && ((CompoundTag) beneficiary).getUUID("id").equals(id)) eligible = true;
                    if (eligible)
                        choices.add(new Choice(id, row.getString("name")));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        choices.sort(Comparator.comparing(Choice::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(choice -> choice.id.toString()));
        if (choices.stream().noneMatch(choice -> choice.id.equals(selected))) selected = null;
        int visibleRows = Math.max(1, Math.min(Math.min(choices.size(), 5), Math.max(1, (height - 96) / ROW_HEIGHT)));
        listHeight = visibleRows * ROW_HEIGHT;
        panel = new NineSliceTexture(QuestlogGuiSet.DEFAULT.backgroundLoc,
                PANEL_WIDTH, 82 + listHeight, 375, 174, 275, 166, 1024, 512, 16, 16);
        left = (width - PANEL_WIDTH) / 2;
        top = (height - panel.height()) / 2;
        list = addRenderableWidget(new ScrollableComponent(left + 15, top + 42, PANEL_WIDTH - 30,
                listHeight, new Rows()));
        int footer = top + 52 + listHeight;
        confirm = addRenderableWidget(new QuestlogButton(0, footer,
                0x4B3422, 0x7C4C22, Component.translatable("questlog.reward.confirm"), () -> {
            if (selected == null) return;
            Services.PLATFORM.sendPacketToServer(new QuestRewardActionPacket(quest.getId(), index,
                    allocationId, QuestRewardActionPacket.DESIGNATE, selected.toString()));
            onClose();
        }, QuestlogGuiSet.DEFAULT));
        confirm.active = selected != null;
        QuestlogButton cancel = addRenderableWidget(new QuestlogButton(0, footer, 0x4B3422, 0x7C4C22,
                Component.translatable("questlog.editor.cancel"), this::onClose, QuestlogGuiSet.DEFAULT));
        confirm.setX(left + (PANEL_WIDTH - confirm.getExpectedWidth() - cancel.getExpectedWidth() - 6) / 2);
        cancel.setX(confirm.getX() + confirm.getExpectedWidth() + 6);
    }

    private class Rows extends AbstractContainerEventHandler implements Scrollable {
        private final List<RecipientRow> rows = new ArrayList<>();
        private ScrollableComponent scroller;

        private Rows() {
            for (int i = 0; i < choices.size(); i++) rows.add(new RecipientRow(choices.get(i), i));
        }

        @Override public int getHeight() { return rows.size() * ROW_HEIGHT; }
        @Override public void setScrollableComponent(ScrollableComponent component) { scroller = component; }
        @Override public List<? extends GuiEventListener> children() { return rows; }

        @Override public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (scroller == null) return;
            graphics.pose().pushPose();
            graphics.pose().translate(scroller.getXOffset(), scroller.getYOffset(), 0);
            if (rows.isEmpty()) graphics.drawString(font, Component.translatable("questlog.groups.no_players"),
                    5, 6, 0xFF4B3422, false);
            for (RecipientRow row : rows) row.render(graphics, mouseX, mouseY, delta);
            graphics.pose().popPose();
        }
    }

    private class RecipientRow extends AbstractButton {
        private final Choice choice;

        private RecipientRow(Choice choice, int index) {
            super(5, index * ROW_HEIGHT + 1, PANEL_WIDTH - 50, ROW_HEIGHT - 2, Component.literal(choice.name));
            this.choice = choice;
        }

        @Override protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            boolean picked = choice.id.equals(selected);
            if (picked || isHoveredOrFocused()) graphics.fill(getX(), getY(), getX() + width, getY() + height,
                    picked ? 0x66A36B38 : 0x33A36B38);
            String name = font.plainSubstrByWidth(choice.name, width - 26);
            graphics.drawString(font, name, getX() + (width - font.width(name)) / 2,
                    getY() + 6, 0xFF4B3422, false);
            if (picked) graphics.drawString(font, "✓", getX() + 6, getY() + 6, 0xFF4B3422, false);
        }

        @Override public void onPress() { selected = choice.id; confirm.active = true; }
        @Override public void updateWidgetNarration(@NotNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
        @Override public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (focused && list != null) list.scrollToReveal(getY(), height);
        }
    }

    @Override public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (previous != null) previous.render(graphics, -1, -1, delta);
        super.renderBackground(graphics, mouseX, mouseY, delta);
        panel.blit(graphics, left, top);
        graphics.drawString(font, title, (width - font.width(title)) / 2, top + 12, 0x4B3422, false);
        QuestlogGuiSet.DEFAULT.panelHR.blit(graphics, width / 2 - 70, top + 26);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) { }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(previous); }
    @Override public boolean isPauseScreen() { return false; }
}
