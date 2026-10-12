package org.infernalstudios.questlog.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationSupplier;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.QuestlogClient;
import org.infernalstudios.questlog.QuestlogClientEvents;
import org.infernalstudios.questlog.client.gui.QuestlogGuiSet;
import org.infernalstudios.questlog.client.integration.RecipeViewerIntegration;
import org.infernalstudios.questlog.client.gui.components.QuestlogButton;
import org.infernalstudios.questlog.client.gui.components.ScrollableComponent;
import org.infernalstudios.questlog.client.gui.components.scrollable.ScrollableInfo;
import org.infernalstudios.questlog.client.gui.components.scrollable.ScrollableText;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.display.Palette;
import org.infernalstudios.questlog.core.quests.display.QuestDisplayData;
import org.infernalstudios.questlog.core.quests.rewards.Reward;
import org.infernalstudios.questlog.network.packet.QuestReadPacket;
import org.infernalstudios.questlog.network.packet.QuestRewardCollectPacket;
import org.infernalstudios.questlog.platform.Services;
import org.infernalstudios.questlog.util.texture.AnimatedTexture;
import org.infernalstudios.questlog.util.texture.Blittable;
import org.infernalstudios.questlog.util.texture.Texture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class QuestDetails extends Screen implements NarrationSupplier {

    private static final int PANEL_SPACING = 6;
    private static final int BUTTON_SPACING = 6;
    private static final int TITLE_Y = 13;
    private static final int TITLE_HEIGHT = 16;
    private static final int CONTENT_X = 18;
    private static final int CONTENT_Y = 36;
    private static final int HR_Y_OFFSET = -2;
    private static final int MAX_TITLE_CHARACTERS = 50;
    private static final int MAX_TITLE_LINES = 2;

    private final boolean detailsPage;
    private boolean showInfo;

    public final Quest quest;

    @Nullable
    private final Screen previousScreen;
    public Component pendingTooltip = null;
    public net.minecraft.world.item.ItemStack pendingItemTooltip = null;
    private int panel1X;
    private int panel2X;
    private int panel1Y;
    private int panel2Y;
    @Nullable
    private QuestlogButton backButton;
    private java.util.List<net.minecraft.util.FormattedCharSequence> titleLines;
    private int titleHeight;
    @Nullable
    private QuestlogButton rewardButton;
    @Nullable
    private QuestlogButton objectivesButton;
    @Nullable
    private ScrollableComponent description;
    @Nullable
    private ScrollableComponent info;
    private long handCursor = 0L;
    private boolean changedCursor = false;

    public QuestDetails(@Nullable Screen previousScreen, Quest quest) {
        this(previousScreen, quest, quest.getDisplay().hasDetails() && quest.getDisplay().isDetailsOpenByDefault());
    }

    public static QuestDetails overview(@Nullable Screen previousScreen, Quest quest) { return new QuestDetails(previousScreen, quest, false); }

    private QuestDetails(@Nullable Screen previousScreen, Quest quest, boolean detailsPage) {
        super(quest.getDisplay().getTitle());
        this.quest = quest;
        this.detailsPage = detailsPage;
        this.previousScreen = detailsPage && !(previousScreen instanceof QuestDetails overview
                && overview.quest == quest && !overview.detailsPage)
                ? new QuestDetails(previousScreen, quest, false) : previousScreen;
    }

    public QuestDetails refreshed(Quest updatedQuest) {
        Screen previous = this.previousScreen;
        if (this.detailsPage && previous instanceof QuestDetails overview
                && overview.quest.getId().equals(updatedQuest.getId())) {
            previous = new QuestDetails(overview.getPreviousScreen(), updatedQuest, false);
        }
        return new QuestDetails(previous, updatedQuest, this.detailsPage);
    }

    @Nullable
    public Screen getPreviousScreen() {
        return this.previousScreen;
    }

    public QuestDisplayData getDisplay() {
        return this.quest.getDisplay();
    }

    public Palette getPalette() {
        return this.getDisplay().getPalette();
    }

    public QuestlogGuiSet getGuiSet() {
        return this.getDisplay().getGuiSet();
    }

    @Override
    protected void init() {
        super.init();
        QuestlogClientEvents.mostRecentNotificationQuest = null;

        this.showInfo = !this.detailsPage && (!this.getDisplay().getObjectiveDisplayData().isEmpty()
                || !this.getDisplay().getRewardDisplayData().isEmpty());

        int leftWidth = this.getDisplay().getLeftPanelWidth();
        int rightWidth = this.getDisplay().getRightPanelWidth();
        int height = this.getDisplay().getPanelHeight();

        int totalWidth = this.showInfo ? (leftWidth + rightWidth + PANEL_SPACING) : leftWidth;
        int baseX = (this.width - totalWidth) / 2;
        int baseY = (this.height - height) / 2;

        this.panel1X = baseX + this.getDisplay().getLeftPanelXOffset();
        this.panel1Y = baseY + this.getDisplay().getLeftPanelYOffset();

        this.panel2X = baseX + leftWidth + PANEL_SPACING + this.getDisplay().getRightPanelXOffset();
        this.panel2Y = baseY + this.getDisplay().getRightPanelYOffset();

        int iconWidth = this.getDisplay().getIcon() != null ? this.getDisplay().getIcon().width() + 4 : 0;
        int titleWidth = Math.max(1, leftWidth - 36 - iconWidth);
        String fullTitle = this.getDisplay().getTitle().getString();
        int codePoints = fullTitle.codePointCount(0, fullTitle.length());
        boolean shortened = codePoints > MAX_TITLE_CHARACTERS;
        String visibleTitle = shortened
                ? fullTitle.substring(0, fullTitle.offsetByCodePoints(0, MAX_TITLE_CHARACTERS))
                : fullTitle;
        this.titleLines = this.font.split(Component.literal(visibleTitle + (shortened ? "…" : "")), titleWidth);
        while (this.titleLines.size() > MAX_TITLE_LINES && !visibleTitle.isEmpty()) {
            visibleTitle = visibleTitle.substring(0, visibleTitle.offsetByCodePoints(0,
                    visibleTitle.codePointCount(0, visibleTitle.length()) - 1));
            this.titleLines = this.font.split(Component.literal(visibleTitle + "…"), titleWidth);
        }
        this.titleHeight = Math.max(TITLE_HEIGHT, this.titleLines.size() * this.font.lineHeight + 4);
        this.setupButtons();
        this.setupContent();
    }

    private QuestlogButton followButton;

    private void setupButtons() {
        int height = this.getDisplay().getPanelHeight();

        if (!this.detailsPage) {
            this.followButton = new QuestlogButton(this.panel1X, Math.max(0,this.panel1Y-22),
                    this.getPalette().textColor(), this.getPalette().hoveredTextColor(),
                    Component.translatable(org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.follows(this.quest.getId()) ? "questlog.tracking.unfollow" : "questlog.tracking.follow"),
                    () -> org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.toggle(this.quest.getId()),this.getGuiSet());
            this.followButton.active = org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.follows(this.quest.getId())
                    || this.quest.isActive() && this.quest.isTriggered() && (!this.quest.isCompleted() || this.quest.hasPendingRewards());
            this.addRenderableWidget(this.followButton);
        }
        int buttonY = this.panel1Y + height + 2;

        this.backButton = new QuestlogButton(
                0, buttonY,
                this.getPalette().textColor(),
                this.getPalette().hoveredTextColor(),
                Component.empty(),
                this::handlePrimaryAction,
                this.getGuiSet()
        );

        if (this.shouldShowRewardButton()) {
            this.rewardButton = new QuestlogButton(
                    0, this.panel2Y + height + 2,
                    this.getPalette().textColor(),
                    this.getPalette().hoveredTextColor(),
                    Component.empty(),
                    this::claimAllRewards,
                    this.getGuiSet()
            );
            this.updateRewardButton();
        } else {
            this.rewardButton = null;
        }

        if (!this.detailsPage && this.getDisplay().hasDetails() && !this.getDisplay().isDetailsButtonDisabled()) {
            this.objectivesButton = new QuestlogButton(
                    0, buttonY,
                    this.getPalette().textColor(),
                    this.getPalette().hoveredTextColor(),
                    Component.translatable("questlog.info.details"),
                    () -> {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new QuestDetails(this, this.quest, true));
                        }
                    },
                    this.getGuiSet()
            );
        } else {
            this.objectivesButton = null;
        }

        this.updateButtonLayout();

        this.addRenderableWidget(this.backButton);
        if (this.rewardButton != null) {
            this.addRenderableWidget(this.rewardButton);
        }
        if (this.objectivesButton != null) {
            this.addRenderableWidget(this.objectivesButton);
        }
    }

    private void updateButtonLayout() {
        if (this.backButton == null) return;
        Component backText = this.getDisplay().getBackButtonText();
        int leftBoundary = this.panel1X + this.getDisplay().getLeftPanelWidth() - 12;

        if (this.detailsPage) {
            this.backButton.setMessage(Component.translatable("gui.back"));
            this.backButton.setX(leftBoundary - this.backButton.getExpectedWidth());
            return;
        }

        if (this.quest.isActive() && this.quest.isCompleted() && this.quest.isRewarded() && this.quest.isRepeatable()) {
            backText = Component.translatable("questlog.reward.reset");
        } else if (this.quest.isActive() && this.needsRead()) {
            backText = Component.translatable("questlog.button.read");
        }

        this.backButton.setMessage(backText);

        int backWidth = this.backButton.getExpectedWidth();
        this.backButton.setX(leftBoundary - backWidth);

        if (this.objectivesButton != null) {
            int objWidth = this.objectivesButton.getExpectedWidth();
            this.objectivesButton.setX(this.backButton.getX() - objWidth - BUTTON_SPACING);
        }
    }

    private boolean shouldShowRewardButton() {
        return this.quest.isActive() && !this.detailsPage && this.showInfo && !this.quest.rewards.isEmpty()
                && this.quest.isCompleted() && this.quest.hasPendingRewards();
    }

    private void updateRewardButton() {
        if (this.rewardButton == null) return;

        boolean canClaim = true;
        for (Reward reward : this.quest.rewards) {
            if (!reward.hasRewarded() && reward instanceof org.infernalstudios.questlog.core.quests.rewards.ChoiceReward choiceReward && !choiceReward.canClaim()) {
                canClaim = false;
                break;
            }
        }

        this.rewardButton.active = canClaim;
        Component text = canClaim ? this.getDisplay().getCollectButtonText() : Component.translatable("questlog.reward.make_choices");
        this.rewardButton.setMessage(text);
        this.rewardButton.setX(this.panel2X + this.getDisplay().getRightPanelWidth() - 12 - this.rewardButton.getExpectedWidth());
    }

    private void handlePrimaryAction() {
        if (this.detailsPage) {
            if (this.minecraft != null) this.minecraft.setScreen(this.previousScreen);
        } else if (this.quest.isActive() && this.quest.isCompleted() && this.quest.isRewarded() && this.quest.isRepeatable()) {
            Services.PLATFORM.sendPacketToServer(new org.infernalstudios.questlog.network.packet.QuestResetPacket(this.quest.getId()));
        } else if (this.quest.isActive() && this.needsRead()) {
            Services.PLATFORM.sendPacketToServer(new QuestReadPacket(this.quest.getId()));
        } else if (this.minecraft != null) {
            this.minecraft.setScreen(this.previousScreen);
        }
    }

    private void claimAllRewards() {
        for (int i = 0; i < this.quest.rewards.size(); i++) {
            Reward reward = this.quest.rewards.get(i);
            if (!reward.hasRewarded()) {
                java.util.List<Integer> selections = java.util.Collections.emptyList();
                if (reward instanceof org.infernalstudios.questlog.core.quests.rewards.ChoiceReward choiceReward) {
                    selections = choiceReward.getSelectedIndicesList();
                }
                Services.PLATFORM.sendPacketToServer(new QuestRewardCollectPacket(this.quest.getId(), i, selections));
                SoundEvent sound = reward.getDisplay() != null ? reward.getDisplay().getClaimSound() : null;
                if (sound != null && this.minecraft != null) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1, 1));
                }
            }
        }
    }

    private boolean needsRead() {
        return !this.quest.isCompleted() && this.quest.objectives.stream()
                .anyMatch(obj -> !obj.isCompleted() && obj.isReadObjective());
    }

    private void setupContent() {
        int leftWidth = this.getDisplay().getLeftPanelWidth();
        int rightWidth = this.getDisplay().getRightPanelWidth();
        int height = this.getDisplay().getPanelHeight();
        int globalOffset = this.quest.isGlobal() ? 6 : 0;

        this.description = new ScrollableComponent(
                this.panel1X + CONTENT_X,
                this.panel1Y + TITLE_Y + globalOffset + this.titleHeight + 7,
                leftWidth - 38,
                Math.max(1, height - 68 - (this.titleHeight - TITLE_HEIGHT) - globalOffset),
                new ScrollableText(this.minecraft.font, this.detailsPage ? this.getDisplay().getDetails()
                        : this.getDisplay().getDescription(this.quest), this.getPalette().textColor())
        );

        this.addWidget(this.description);

        if (this.showInfo) {
            this.info = new ScrollableComponent(
                    this.panel2X + CONTENT_X,
                    this.panel2Y + TITLE_Y,
                    rightWidth - 36,
                    height - 68 + CONTENT_Y - TITLE_Y,
                    new ScrollableInfo(this, this.getDisplay(), this.showRewardPreviews())
            );

            this.addWidget(this.info);
        } else {
            this.info = null;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics ps, int mouseX, int mouseY, float partialTicks) {
        this.pendingTooltip = null;
        this.pendingItemTooltip = null;
        if (this.followButton != null) {
            boolean followed = org.infernalstudios.questlog.client.gui.TrackedQuestsOverlay.follows(this.quest.getId());
            this.followButton.setMessage(Component.translatable(followed ? "questlog.tracking.unfollow" : "questlog.tracking.follow"));
            this.followButton.active = followed || this.quest.isActive() && this.quest.isTriggered() && !this.quest.getDisplay().isHidden() && (!this.quest.isCompleted() || this.quest.hasPendingRewards());
        }
        super.render(ps, mouseX, mouseY, partialTicks);
        this.renderTitle(ps);
        if (this.description != null) this.description.render(ps, mouseX, mouseY, partialTicks);
        if (this.showInfo) {
            this.renderInfo(ps, mouseX, mouseY, partialTicks);
        }

        this.handleMouseOverLinks(mouseX, mouseY, ps);

        if (this.pendingItemTooltip != null && this.info != null && this.info.isMouseOver(mouseX, mouseY)) {
            ps.renderTooltip(this.font, this.pendingItemTooltip, mouseX, mouseY);
        } else if (this.pendingTooltip != null && this.info != null && this.info.isMouseOver(mouseX, mouseY)) {
            ps.renderTooltip(this.font, this.pendingTooltip, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics ps, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(ps, mouseX, mouseY, partialTicks);
        this.getGuiSet().detailBackgroundLeft.blit(ps, this.panel1X, this.panel1Y);
        if (this.showInfo) {
            this.getGuiSet().detailBackgroundRight.blit(ps, this.panel2X, this.panel2Y);
        }

        ResourceLocation overlay = this.getDisplay().getOverlayTexture();
        if (overlay != null) {
            int overlayX = this.panel1X + this.getDisplay().getOverlayXOffset();
            int overlayY = this.panel1Y + this.getDisplay().getOverlayYOffset();
            int overlayWidth = this.getDisplay().getOverlayWidth();
            int overlayHeight = this.getDisplay().getOverlayHeight();
            ps.blit(overlay, overlayX, overlayY, 0, 0, overlayWidth, overlayHeight, overlayWidth, overlayHeight);
        }
    }

    private void handleMouseOverLinks(int mouseX, int mouseY, GuiGraphics ps) {
        if (this.description == null) return;

        long window = this.minecraft.getWindow().getWindow();
        boolean isHoveringLink = false;

        if (this.description.isMouseOver(mouseX, mouseY)) {
            ScrollableText scrollableText = (ScrollableText) this.description.scrollable;
            Style style = scrollableText.getStyleAt(mouseX - this.description.getXOffset(), mouseY - this.description.getYOffset());

            if (style != null) {
                ClickEvent click = style.getClickEvent();
                if (click != null && (!click.getValue().startsWith("item:")
                        || (Questlog.getConfig().itemLinks == null || Questlog.getConfig().itemLinks.openRecipes)
                        && RecipeViewerIntegration.isAvailable())) {
                    isHoveringLink = true;
                    if (!this.changedCursor) {
                        if (this.handCursor == 0L) {
                            this.handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
                        }
                        GLFW.glfwSetCursor(window, this.handCursor);
                        this.changedCursor = true;
                    }
                }
                this.renderHoverEffect(ps, style, mouseX, mouseY);
            }
        }

        if (!isHoveringLink && this.changedCursor) {
            GLFW.glfwSetCursor(window, 0L);
            this.changedCursor = false;
        }
    }

    private void renderHoverEffect(GuiGraphics ps, Style style, int mouseX, int mouseY) {
        HoverEvent hover = style.getHoverEvent();
        if (hover == null) return;
        if (hover.getAction() == HoverEvent.Action.SHOW_ITEM
                && Questlog.getConfig().itemLinks != null
                && !Questlog.getConfig().itemLinks.showTooltips) return;
        if (hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
            Component hoverComponent = (Component) hover.getValue(hover.getAction());
            if (hoverComponent != null) {
                String text = hoverComponent.getString();
                if (text.startsWith("image:")) {
                    this.renderImageTooltip(ps, text, mouseX, mouseY);
                    return;
                }
            }
        }
        ps.renderComponentHoverEffect(this.font, style, mouseX, mouseY);
    }

    private void renderImageTooltip(GuiGraphics ps, String data, int mouseX, int mouseY) {
        org.infernalstudios.questlog.util.texture.ImageTooltipData image =
                org.infernalstudios.questlog.util.texture.ImageTooltipData.parse(data);
        if (image == null) return;
        int w = image.width();
        int h = image.height();
        ps.pose().pushPose();
        try {
            ps.pose().translate(0.0F, 0.0F, 400.0F);
            ps.fill(mouseX + 8, mouseY - 8, mouseX + 8 + w + 4, mouseY - 8 + h + 4, 0xDD000000);
            Blittable texture = image.frames() > 0
                    ? new AnimatedTexture(image.texture(), w, h, 0, 0, w, h * image.frames(), image.frames(), image.frameTime())
                    : new Texture(image.texture(), w, h, 0, 0, w, h);
            texture.blit(ps, mouseX + 10, mouseY - 6);
        } finally {
            ps.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.description != null && this.description.isMouseOver(mouseX, mouseY)) {
            ScrollableText scrollableText = (ScrollableText) this.description.scrollable;
            Style style = scrollableText.getStyleAt(mouseX - this.description.getXOffset(), mouseY - this.description.getYOffset());

            if (style != null && style.getClickEvent() != null) {
                ClickEvent click = style.getClickEvent();
                if (click.getAction() == ClickEvent.Action.CHANGE_PAGE) {
                    if (click.getValue().startsWith("item:")) {
                        if (Questlog.getConfig().itemLinks != null && !Questlog.getConfig().itemLinks.openRecipes) return false;
                        ResourceLocation itemId = ResourceLocation.tryParse(click.getValue().substring(5));
                        return itemId != null && RecipeViewerIntegration.openRecipes(itemId);
                    }
                    ResourceLocation questId = ResourceLocation.tryParse(click.getValue());
                    Quest target = questId != null ? QuestlogClient.getLocal().getQuest(questId) : null;
                    if (target != null && this.minecraft != null) {
                        this.minecraft.setScreen(new QuestDetails(this, target));
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderTitle(GuiGraphics ps) {
        QuestDisplayData display = this.getDisplay();
        int titleY = TITLE_Y + (this.quest.isGlobal() ? 6 : 0);
        if (this.quest.isGlobal()) {
            ps.drawString(this.font, Component.translatable("questlog.quest.global"),
                    this.panel1X + CONTENT_X, this.panel1Y + 10, this.getPalette().textColor(), false);
        }
        int iconWidth = display.getIcon() != null ? display.getIcon().width() + 4 : 0;
        int textWidth = this.titleLines.stream().mapToInt(this.font::width).max().orElse(0);
        int x = this.panel1X + (display.getLeftPanelWidth() - textWidth - iconWidth) / 2;
        if (display.getIcon() != null) {
            display.getIcon().blit(ps, x, this.panel1Y + titleY);
        }
        int y = this.panel1Y + titleY + 4;
        for (net.minecraft.util.FormattedCharSequence line : this.titleLines) {
            ps.drawString(this.font, line, x + iconWidth, y, this.getPalette().titleColor(), false);
            y += this.font.lineHeight;
        }
        this.getGuiSet().smallHR.blit(ps, this.panel1X + (display.getLeftPanelWidth() - 132) / 2 - 60,
                this.panel1Y + titleY + this.titleHeight + HR_Y_OFFSET);
    }

    private void renderInfo(GuiGraphics ps, int mouseX, int mouseY, float partialTicks) {
        if (this.info != null) this.info.render(ps, mouseX, mouseY, partialTicks);
    }

    private boolean showRewardPreviews() {
        return this.getDisplay().shouldShowRewardPreviews(Questlog.getConfig().preferences.showRewardPreviews);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.detailsPage) return;

        if (this.rewardButton != null) {
            if (!this.shouldShowRewardButton()) {
                this.rebuildWidgets();
                return;
            }
            this.updateRewardButton();
        } else if (this.shouldShowRewardButton()) {
            this.rebuildWidgets();
            return;
        }

        if (this.backButton != null && !this.needsRead() &&
                this.backButton.getMessage().getString().equals(Component.translatable("questlog.button.read").getString())) {
            this.rebuildWidgets();
        }

        if (this.backButton != null && !this.quest.isCompleted() &&
                this.backButton.getMessage().getString().equals(Component.translatable("questlog.reward.reset").getString())) {
            this.rebuildWidgets();
        }
    }

    @Override
    public void removed() {
        if (this.changedCursor) {
            long window = this.minecraft.getWindow().getWindow();
            GLFW.glfwSetCursor(window, 0L);
            this.changedCursor = false;
        }
        if (this.handCursor != 0L) {
            GLFW.glfwDestroyCursor(this.handCursor);
            this.handCursor = 0L;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_E || QuestlogClient.OPEN_SCREEN_KEY.matches(key, scancode)) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.previousScreen);
                return true;
            }
        }

        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public void updateNarration(@NotNull NarrationElementOutput output) {
    }
}
