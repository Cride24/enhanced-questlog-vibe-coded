package org.infernalstudios.questlog.client.gui.components.scrollable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.infernalstudios.questlog.client.gui.components.InfoEntry;
import org.infernalstudios.questlog.client.gui.components.ScrollableComponent;
import org.infernalstudios.questlog.client.gui.screen.QuestDetails;
import org.infernalstudios.questlog.core.quests.display.ObjectiveDisplayData;
import org.infernalstudios.questlog.core.quests.display.QuestDisplayData;
import org.infernalstudios.questlog.core.quests.display.RewardDisplayData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ScrollableInfo implements Scrollable, GuiEventListener {
    private static final int TOP_HEADING_HEIGHT = 23;
    private static final int REWARD_HEADING_HEIGHT = 34;
    private static final int REWARD_HEADING_TITLE_Y = 8;
    private static final int TITLE_HEIGHT = 16;
    private static final int HR_Y_OFFSET = -2;
    private final QuestDetails questDetails;
    private final QuestDisplayData display;
    private final boolean showRewardPreviews;
    private List<InfoEntry> entries;
    private boolean entriesCompleted;
    @Nullable
    private ScrollableComponent parent = null;

    public ScrollableInfo(QuestDetails questDetails, QuestDisplayData display, boolean showRewardPreviews) {
        this.questDetails = questDetails;
        this.display = display;
        this.showRewardPreviews = showRewardPreviews;
    }

    private boolean isPreviewing() {
        return this.showRewardPreviews && !this.questDetails.quest.isCompleted();
    }

    private List<InfoEntry> getEntries() {
        boolean completed = this.questDetails.quest.isCompleted();
        if (this.entries == null || this.entriesCompleted != completed) {
            this.entries = new ArrayList<>();
            if (!completed) {
                for (ObjectiveDisplayData datum : this.display.getObjectiveDisplayData()) {
                    this.entries.add(new InfoEntry(this.questDetails, datum, 0, 0));
                }
            }
            if (completed || this.showRewardPreviews) {
                for (RewardDisplayData datum : this.display.getRewardDisplayData()) {
                    this.entries.add(new InfoEntry(this.questDetails, datum, 0, 0, display));
                }
            }
            this.entriesCompleted = completed;
        }
        return this.entries;
    }

    private boolean hasRewardHeading() {
        return this.isPreviewing() && !this.display.getObjectiveDisplayData().isEmpty()
                && !this.display.getRewardDisplayData().isEmpty();
    }

    private int getEntryY(int index) {
        int y = TOP_HEADING_HEIGHT;
        if (index >= this.display.getObjectiveDisplayData().size() && this.hasRewardHeading())
            y += REWARD_HEADING_HEIGHT;
        List<InfoEntry> entries = this.getEntries();
        for (int i = 0; i < index; i++) y += entries.get(i).getHeight();
        return y;
    }

    @Override
    public int getHeight() {
        if (this.getEntries().isEmpty()) return 0;
        return TOP_HEADING_HEIGHT + this.getEntries().stream().mapToInt(InfoEntry::getHeight).sum()
                + (this.hasRewardHeading() ? REWARD_HEADING_HEIGHT : 0);
    }

    @Override
    public void render(@NotNull GuiGraphics ps, int mouseX, int mouseY, float partialTicks) {
        List<InfoEntry> entries = this.getEntries();
        if (entries.isEmpty()) return;
        int x = this.parent != null ? (int) this.parent.getXOffset() : 0;
        int y = this.parent != null ? (int) this.parent.getYOffset() : 0;
        int width = this.parent != null ? this.parent.width : this.display.getRightPanelWidth() - 36;
        Font font = Minecraft.getInstance().font;
        Component topTitle = this.questDetails.quest.isCompleted()
                || (this.showRewardPreviews && this.display.getObjectiveDisplayData().isEmpty())
                ? Component.translatable("questlog.info.rewards")
                : Component.translatable("questlog.info.objectives");
        int titleX = x + (width - font.width(topTitle)) / 2;
        int titleY = y + (TITLE_HEIGHT - font.lineHeight + 2) / 2;
        int hrX = x + (width - this.questDetails.getGuiSet().panelHR.width()) / 2;
        ps.drawString(font, topTitle, titleX, titleY, this.questDetails.getPalette().titleColor(), false);
        this.questDetails.getGuiSet().panelHR.blit(ps, hrX, y + TITLE_HEIGHT + HR_Y_OFFSET);
        if (this.hasRewardHeading()) {
            int rewardY = y + TOP_HEADING_HEIGHT;
            for (int i = 0; i < this.display.getObjectiveDisplayData().size(); i++) {
                rewardY += entries.get(i).getHeight();
            }
            Component title = Component.translatable("questlog.info.rewards");
            int rewardTitleX = x + (width - font.width(title)) / 2;
            int rewardTitleY = rewardY + REWARD_HEADING_TITLE_Y + (TITLE_HEIGHT - font.lineHeight + 2) / 2;
            this.questDetails.getGuiSet().panelHR.blit(ps, hrX, rewardY);
            ps.drawString(font, title, rewardTitleX, rewardTitleY, this.questDetails.getPalette().titleColor(), false);
            this.questDetails.getGuiSet().panelHR.blit(ps,
                    hrX, rewardY + REWARD_HEADING_TITLE_Y + TITLE_HEIGHT + HR_Y_OFFSET);
        }
        for (int i = 0; i < entries.size(); i++) {
            InfoEntry entry = entries.get(i);
            entry.x = this.parent != null ? (int) this.parent.getXOffset() : 0;
            entry.y = (this.parent != null ? (int) this.parent.getYOffset() : 0) + this.getEntryY(i);

            int absMouseX = this.parent != null ? mouseX + (int) this.parent.getXOffset() : mouseX;
            int absMouseY = this.parent != null ? mouseY + (int) this.parent.getYOffset() : mouseY;

            entry.render(ps, absMouseX, absMouseY, partialTicks);
        }
    }

    @Override
    public void setScrollableComponent(ScrollableComponent parent) {
        this.parent = parent;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_1 || mouseY < 0) return false;
        List<InfoEntry> entries = this.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            double entryY = this.getEntryY(i);
            InfoEntry entry = entries.get(i);
            if (mouseY >= entryY && mouseY < entryY + entry.getHeight()) {
                if (!this.questDetails.quest.isCompleted()) {
                    int width = (this.parent != null ? this.parent.width : this.display.getRightPanelWidth() - 36) - 15;
                    return i < this.display.getObjectiveDisplayData().size()
                            && mouseX >= 0 && mouseX < width
                            && mouseY - entryY >= 2 && mouseY - entryY < 2 + entry.getNameHeight()
                            && entry.handleObjectiveItemClick();
                }
                return this.questDetails.quest.hasPendingRewards() && entry.handleChoiceClick();
            }
        }
        return false;
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
    }
}
