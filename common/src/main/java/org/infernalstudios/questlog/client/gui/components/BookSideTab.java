package org.infernalstudios.questlog.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.infernalstudios.questlog.client.gui.QuestlogGuiSet;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/** The existing chapter tab texture turned toward the book's left edge. */
public final class BookSideTab extends AbstractButton {
    private final ItemStack icon;
    private final boolean selected;
    private final Runnable action;

    public BookSideTab(int x, int y, ItemStack icon, boolean selected, Component label, Runnable action) {
        super(x, y, 32, 54, label);
        this.icon = icon;
        this.selected = selected;
        this.action = action;
        setTooltip(Tooltip.create(label));
    }

    @Override protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(getX() + 38, getY(), 0);
        pose.mulPose(new Quaternionf().rotationZ((float) Math.PI / 2));
        (selected || isHoveredOrFocused() ? QuestlogGuiSet.DEFAULT.tabMainActive : QuestlogGuiSet.DEFAULT.tabMain).blit(graphics, 0, 0);
        pose.popPose();
        graphics.renderItem(icon, getX() + 14, getY() + 19);
    }

    @Override public void onPress() { action.run(); }
    @Override protected void updateWidgetNarration(@NotNull NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
