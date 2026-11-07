package org.infernalstudios.questlog.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.infernalstudios.questlog.client.gui.screen.QuestDetails;
import org.infernalstudios.questlog.core.quests.display.ObjectiveDisplayData;
import org.infernalstudios.questlog.core.quests.display.QuestDisplayData;
import org.infernalstudios.questlog.core.quests.display.RewardDisplayData;
import org.infernalstudios.questlog.util.texture.Blittable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InfoEntry implements Renderable, GuiEventListener {
    public static final int INFO_ENTRY_HEIGHT = 28;
    private final QuestDetails questDetails;
    private final RewardDisplayData rewardData;
    private final ObjectiveDisplayData objectiveData;
    public int x, y;
    @Nullable
    private QuestDisplayData display;

    public InfoEntry(QuestDetails questDetails, @Nullable RewardDisplayData reward, int x, int y, @Nullable QuestDisplayData display) {
        this.questDetails = questDetails;
        this.rewardData = reward;
        this.objectiveData = null;
        this.x = x;
        this.y = y;
        this.display = display;
    }

    public InfoEntry(QuestDetails questDetails, @Nullable ObjectiveDisplayData objective, int x, int y) {
        this.questDetails = questDetails;
        this.rewardData = null;
        this.objectiveData = objective;
        this.x = x;
        this.y = y;
    }

    @Override
    public void render(@NotNull GuiGraphics ps, int mouseX, int mouseY, float partialTicks) {
        boolean isReward = rewardData != null;
        Blittable icon = isReward ? rewardData.getIcon() : objectiveData.getIcon();
        Component name = isReward ? rewardData.getName() : objectiveData.getName();

        int indent = isReward ? rewardData.getIndentLevel() * 12 : objectiveData.getIndentLevel() * 12;
        int currentX = this.x + indent;

        Font font = Minecraft.getInstance().font;
        if (icon != null) {
            int iconY = isReward
                    ? this.y + 1 + (font.lineHeight - 1) / 2 - icon.height() / 2
                    : this.y + 4;
            icon.blit(ps, currentX, iconY);
        }

        boolean hasIconColumn = icon != null || isReward && rewardData.getIndentLevel() > 0;
        int textX = currentX + (hasIconColumn ? 20 : 0);
        int maxWidth = questDetails.getDisplay().getRightPanelWidth() - 36 - 15 - (hasIconColumn ? 20 : 0) - indent;
        int nameY = this.y + 2;
        for (net.minecraft.util.FormattedCharSequence line : font.split(name, Math.max(1, maxWidth))) {
            ps.drawString(font, line, textX, nameY, questDetails.getPalette().textColor(), false);
            nameY += font.lineHeight;
        }

        if (this.objectiveData != null && this.objectiveData.getItemId() != null
                && (org.infernalstudios.questlog.Questlog.getConfig().itemLinks == null
                    || org.infernalstudios.questlog.Questlog.getConfig().itemLinks.showTooltips)
                && mouseX >= textX && mouseX < textX + maxWidth
                && mouseY >= this.y + 2 && mouseY < nameY) {
            this.questDetails.pendingItemTooltip = new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(this.objectiveData.getItemId()));
        }

        if (isReward) {
            this.drawRewardStatus(ps, textX);
        } else {
            this.drawObjectiveStatus(ps, textX);
        }
    }

    public int getNameHeight() {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().font == null) return 9;
        Font font = Minecraft.getInstance().font;
        boolean reward = this.rewardData != null;
        Blittable icon = reward ? this.rewardData.getIcon() : this.objectiveData.getIcon();
        int indent = (reward ? this.rewardData.getIndentLevel() : this.objectiveData.getIndentLevel()) * 12;
        boolean hasIconColumn = icon != null || reward && this.rewardData.getIndentLevel() > 0;
        int width = this.questDetails.getDisplay().getRightPanelWidth() - 51 - indent - (hasIconColumn ? 20 : 0);
        return font.split(reward ? this.rewardData.getName() : this.objectiveData.getName(), Math.max(1, width)).size() * font.lineHeight;
    }

    private int getStatusY() {
        return this.y + 4 + this.getNameHeight();
    }

    public int getHeight() {
        return Math.max(INFO_ENTRY_HEIGHT, this.getNameHeight() + 17);
    }

    private void drawRewardStatus(GuiGraphics ps, int textX) {
        if (!this.questDetails.quest.isCompleted()
                || rewardData.getReward() != null && rewardData.getReward().getContainer() != null) {
            return;
        }
        Component status = !this.questDetails.quest.canClaimRewards() && !rewardData.hasRewarded() ? Component.translatable("questlog.reward.inherited") : rewardData.hasRewarded() ?
                (display != null ? display.getCollectedText() : Component.translatable("questlog.reward.collected")) :
                (display != null ? display.getUncollectedText() : Component.translatable("questlog.reward.uncollected"));

        ps.drawString(Minecraft.getInstance().font, status, textX, this.getStatusY(),
                !this.questDetails.quest.hasPendingRewards() ? questDetails.getPalette().completedTextColor() : questDetails.getPalette().progressTextColor(), false);
    }

    private void drawObjectiveStatus(GuiGraphics ps, int textX) {
        ps.drawString(Minecraft.getInstance().font, objectiveData.getProgress(), textX, this.getStatusY(),
                objectiveData.isCompleted() ? questDetails.getPalette().completedTextColor() : questDetails.getPalette().progressTextColor(), false);
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    public void setFocused(boolean var1) {
    }

    public boolean handleObjectiveItemClick() {
        if (this.objectiveData == null || this.objectiveData.getItemId() == null) return false;
        if (org.infernalstudios.questlog.Questlog.getConfig().itemLinks != null
                && !org.infernalstudios.questlog.Questlog.getConfig().itemLinks.openRecipes) return false;
        return org.infernalstudios.questlog.client.integration.RecipeViewerIntegration.openRecipes(this.objectiveData.getItemId());
    }

    public boolean handleChoiceClick() {
        if (this.rewardData != null) {
            org.infernalstudios.questlog.core.quests.rewards.Reward reward = this.rewardData.getReward();
            if (reward != null && reward.getContainer() != null) {
                org.infernalstudios.questlog.core.quests.rewards.ChoiceReward choiceReward = reward.getContainer();
                if (!choiceReward.hasRewarded()) {
                    choiceReward.toggleChoice(reward);
                    net.minecraft.client.resources.sounds.SimpleSoundInstance sound =
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F
                            );
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(sound);
                    return true;
                }
            }
        }
        return false;
    }
}
