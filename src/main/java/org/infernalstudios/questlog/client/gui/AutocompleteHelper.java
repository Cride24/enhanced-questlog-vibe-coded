package org.infernalstudios.questlog.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.infernalstudios.questlog.client.gui.components.NoShadowEditBox;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AutocompleteHelper {
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_HEIGHT = 14;

    private int firstVisibleIndex = 0;
    private List<String> currentMatches = Collections.emptyList();
    private int selectedSuggestionIndex = -1;
    private String lastValue = "";
    private NoShadowEditBox lastBox = null;

    public void update(NoShadowEditBox activeBox, Supplier<List<String>> matchesSupplier) {
        if (activeBox != this.lastBox || (activeBox != null && !activeBox.getValue().equals(this.lastValue))) {
            this.selectedSuggestionIndex = -1;
            this.firstVisibleIndex = 0;
            this.lastBox = activeBox;
            this.lastValue = activeBox != null ? activeBox.getValue() : "";
            if (activeBox != null && activeBox.isFocused()) {
                this.currentMatches = matchesSupplier.get();
            } else {
                this.currentMatches = Collections.emptyList();
            }
        }
    }

    public void clear() {
        this.currentMatches = Collections.emptyList();
        this.selectedSuggestionIndex = -1;
        this.firstVisibleIndex = 0;
        this.lastBox = null;
        this.lastValue = "";
    }

    public boolean hasSuggestions() {
        return !this.currentMatches.isEmpty();
    }

    public NoShadowEditBox getLastBox() {
        return this.lastBox;
    }

    public void render(GuiGraphics ps, Font font, int mouseX, int mouseY) {
        if (this.lastBox == null || !this.lastBox.isFocused() || this.currentMatches.isEmpty()) {
            return;
        }

        int boxX = this.lastBox.getX();
        int boxY = this.lastBox.getY();
        int boxW = this.lastBox.getWidth();
        int startY = boxY + 17;
        int overlayHeight = this.visibleRowCount() * ROW_HEIGHT + 2;

        ps.pose().pushPose();
        ps.pose().translate(0, 0, 400.0F);

        ps.fill(boxX, startY, boxX + boxW, startY + overlayHeight, 0xFF202020);
        ps.fill(boxX - 1, startY, boxX, startY + overlayHeight, 0xFF505050);
        ps.fill(boxX + boxW, startY, boxX + boxW + 1, startY + overlayHeight, 0xFF505050);
        ps.fill(boxX, startY - 1, boxX + boxW, startY, 0xFF505050);
        ps.fill(boxX, startY + overlayHeight, boxX + boxW, startY + overlayHeight + 1, 0xFF505050);

        for (int row = 0; row < this.visibleRowCount(); row++) {
            int index = this.firstVisibleIndex + row;
            String match = this.currentMatches.get(index);
            int itemY = startY + 1 + row * ROW_HEIGHT;
            boolean hovered = mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= itemY && mouseY < itemY + ROW_HEIGHT;
            boolean selected = hovered || this.selectedSuggestionIndex == index;

            if (selected) {
                ps.fill(boxX, itemY, boxX + boxW, itemY + ROW_HEIGHT, 0xFF404040);
            }

            String drawText = match;
            if (font.width(drawText) > boxW - 10) {
                drawText = font.plainSubstrByWidth(drawText, boxW - 16) + "...";
            }
            ps.drawString(font, drawText, boxX + 4, itemY + 3, selected ? 0xFFFFFF00 : 0xFFFFFFFF, false);
        }

        ps.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, Consumer<String> onSelected) {
        if (this.lastBox == null || !this.lastBox.isFocused() || this.currentMatches.isEmpty()) {
            return false;
        }

        int boxX = this.lastBox.getX();
        int boxY = this.lastBox.getY();
        int boxW = this.lastBox.getWidth();
        int startY = boxY + 17;

        if (button == GLFW.GLFW_MOUSE_BUTTON_1 && mouseX >= boxX && mouseX <= boxX + boxW) {
            for (int row = 0; row < this.visibleRowCount(); row++) {
                int itemY = startY + 1 + row * ROW_HEIGHT;
                if (mouseY >= itemY && mouseY < itemY + ROW_HEIGHT) {
                    onSelected.accept(this.currentMatches.get(this.firstVisibleIndex + row));
                    this.clear();
                    return true;
                }
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (this.lastBox == null || !this.lastBox.isFocused() || this.currentMatches.isEmpty()) {
            return false;
        }

        int boxX = this.lastBox.getX();
        int startY = this.lastBox.getY() + 17;
        int overlayHeight = this.visibleRowCount() * ROW_HEIGHT + 2;
        if (mouseX < boxX || mouseX > boxX + this.lastBox.getWidth()
                || mouseY < startY || mouseY > startY + overlayHeight) {
            return false;
        }

        int delta = (int) Math.round(scrollY);
        if (delta == 0 && scrollY != 0) {
            delta = scrollY > 0 ? 1 : -1;
        }
        this.firstVisibleIndex = Math.max(0, Math.min(this.firstVisibleIndex - delta,
                this.currentMatches.size() - this.visibleRowCount()));
        this.selectedSuggestionIndex = -1;
        return true;
    }

    private int visibleRowCount() {
        return Math.min(VISIBLE_ROWS, this.currentMatches.size());
    }

    private void scrollSelectionIntoView() {
        if (this.selectedSuggestionIndex < this.firstVisibleIndex) {
            this.firstVisibleIndex = this.selectedSuggestionIndex;
        } else if (this.selectedSuggestionIndex >= this.firstVisibleIndex + this.visibleRowCount()) {
            this.firstVisibleIndex = this.selectedSuggestionIndex - this.visibleRowCount() + 1;
        }
    }

    public boolean keyPressed(int key, Consumer<String> onSelected) {
        if (this.lastBox == null || !this.lastBox.isFocused() || this.currentMatches.isEmpty()) {
            return false;
        }

        if (key == GLFW.GLFW_KEY_DOWN) {
            this.selectedSuggestionIndex = this.selectedSuggestionIndex < 0 ? this.firstVisibleIndex
                    : (this.selectedSuggestionIndex + 1) % this.currentMatches.size();
            this.scrollSelectionIntoView();
            return true;
        } else if (key == GLFW.GLFW_KEY_UP) {
            if (this.selectedSuggestionIndex < 0) {
                this.selectedSuggestionIndex = this.firstVisibleIndex + this.visibleRowCount() - 1;
            } else if (this.selectedSuggestionIndex == 0) {
                this.selectedSuggestionIndex = this.currentMatches.size() - 1;
            } else {
                this.selectedSuggestionIndex--;
            }
            this.scrollSelectionIntoView();
            return true;
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.selectedSuggestionIndex >= 0 && this.selectedSuggestionIndex < this.currentMatches.size()) {
                onSelected.accept(this.currentMatches.get(this.selectedSuggestionIndex));
                this.clear();
                return true;
            }
        } else if (key == GLFW.GLFW_KEY_TAB) {
            int idx = this.selectedSuggestionIndex >= 0 ? this.selectedSuggestionIndex : this.firstVisibleIndex;
            onSelected.accept(this.currentMatches.get(idx));
            this.clear();
            return true;
        }

        return false;
    }
}
