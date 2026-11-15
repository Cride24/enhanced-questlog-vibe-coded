package org.infernalstudios.questlog.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.infernalstudios.questlog.QuestlogClient;
import org.infernalstudios.questlog.client.gui.QuestlogGuiSet;
import org.infernalstudios.questlog.client.gui.components.QuestlogButton;
import org.infernalstudios.questlog.core.GroupRewards;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.rewards.ItemReward;
import org.infernalstudios.questlog.core.quests.rewards.Reward;
import org.infernalstudios.questlog.network.packet.QuestRewardActionPacket;
import org.infernalstudios.questlog.platform.Services;
import org.infernalstudios.questlog.util.texture.NineSliceTexture;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/** Small non-pausing prompt shown while an eligible vote is open. */
public final class LotteryScreen extends Screen {
    private static final NineSliceTexture PANEL = new NineSliceTexture(QuestlogGuiSet.DEFAULT.backgroundLoc,
            210, 130, 375, 174, 275, 166, 1024, 512, 16, 16);
    private static final Set<String> opened = new HashSet<>();
    private final Screen previous;
    private final Quest quest;
    private final int index;
    private final String allocation;
    private boolean responded;

    public static void clearSession() { opened.clear(); }

    public static void offer(Screen previous) {
        if (previous instanceof LotteryScreen || QuestlogClient.getLocal() == null) return;
        for (Quest quest : QuestlogClient.getLocal().getAllQuests()) {
            for (int i = 0; i < quest.rewards.size(); i++) {
                Reward reward = quest.rewards.get(i);
                if (reward.allocationState() == GroupRewards.VOTING && reward.lotteryCandidate()
                        && !reward.lotteryAnswered() && reward.lotteryDeadline() > System.currentTimeMillis()
                        && opened.add(reward.allocationId())) {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new LotteryScreen(previous, quest, i));
                    return;
                }
            }
        }
    }

    private LotteryScreen(Screen previous, Quest quest, int index) {
        super(Component.translatable("questlog.reward.lottery"));
        this.previous = previous;
        this.quest = quest;
        this.index = index;
        this.allocation = quest.rewards.get(index).allocationId();
    }

    @Override protected void init() {
        int y = (height - PANEL.height()) / 2 + 98;
        QuestlogButton yes = addRenderableWidget(new QuestlogButton(0, y, 0x4B3422, 0x7C4C22,
                Component.translatable("gui.yes"), () -> respond(true), QuestlogGuiSet.DEFAULT));
        QuestlogButton no = addRenderableWidget(new QuestlogButton(0, y, 0x4B3422, 0x7C4C22,
                Component.translatable("gui.no"), () -> respond(false), QuestlogGuiSet.DEFAULT));
        yes.setX((width - yes.getExpectedWidth() - no.getExpectedWidth() - 4) / 2);
        no.setX(yes.getX() + yes.getExpectedWidth() + 4);
    }

    private void respond(boolean yes) {
        if (responded) return;
        responded = true;
        Services.PLATFORM.sendPacketToServer(new QuestRewardActionPacket(quest.getId(), index,
                allocation, yes ? QuestRewardActionPacket.YES : QuestRewardActionPacket.NO, ""));
        if (minecraft != null) minecraft.setScreen(previous);
    }

    @Override public void tick() {
        Quest current = QuestlogClient.getLocal().getQuest(quest.getId());
        if (current == null || index >= current.rewards.size()
                || !allocation.equals(current.rewards.get(index).allocationId())
                || current.rewards.get(index).allocationState() != GroupRewards.VOTING
                || current.rewards.get(index).lotteryAnswered()
                || System.currentTimeMillis() >= current.rewards.get(index).lotteryDeadline()) {
            if (minecraft != null) minecraft.setScreen(previous);
        }
    }

    @Override public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (previous != null) previous.render(graphics, -1, -1, delta);
        super.renderBackground(graphics, mouseX, mouseY, delta);
        int left = (width - PANEL.width()) / 2, top = (height - PANEL.height()) / 2;
        PANEL.blit(graphics, left, top);
        Reward reward = quest.rewards.get(index);
        graphics.drawString(font, title, (width - font.width(title)) / 2, top + 12, 0x4B3422, false);
        QuestlogGuiSet.DEFAULT.panelHR.blit(graphics, width / 2 - 70, top + 26);
        int iconX = -1, iconY = top + 38;
        if (reward.getDisplay() != null) {
            Component name = reward.getDisplay().getName();
            var icon = reward.getDisplay().getIcon();
            int iconSpace = icon == null ? 0 : icon.width() + 4;
            String label = font.plainSubstrByWidth(name.getString(), PANEL.width() - 40 - iconSpace);
            int textX = (width - font.width(label) - iconSpace) / 2;
            if (icon != null) {
                iconX = textX;
                icon.blit(graphics, iconX, iconY);
                textX += iconSpace;
            }
            graphics.drawString(font, label, textX, top + 41, 0x4B3422, false);
        }
        Component question = Component.translatable(reward.lotteryTie()
                ? "questlog.reward.lottery_tie_question" : "questlog.reward.lottery_question");
        graphics.drawString(font, question, (width - font.width(question)) / 2, top + 64, 0x4B3422, false);
        long seconds = Math.max(0, (reward.lotteryDeadline() - System.currentTimeMillis() + 999) / 1000);
        Component timer = Component.literal(seconds + " s");
        graphics.drawString(font, timer, (width - font.width(timer)) / 2, top + 79, 0x4B3422, false);
        super.render(graphics, mouseX, mouseY, delta);
        if (reward instanceof ItemReward item && iconX >= 0 && mouseX >= iconX && mouseX < iconX + 16
                && mouseY >= iconY && mouseY < iconY + 16)
            graphics.renderTooltip(font, item.getStack(), mouseX, mouseY);
    }

    @Override public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) { }

    @Override public void onClose() { respond(false); }
    @Override public boolean isPauseScreen() { return false; }
}
