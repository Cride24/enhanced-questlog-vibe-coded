package org.infernalstudios.questlog.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.QuestlogClient;
import org.infernalstudios.questlog.client.gui.QuestlogGuiSet;
import org.infernalstudios.questlog.client.gui.components.NoShadowEditBox;
import org.infernalstudios.questlog.client.gui.components.BookSideTab;
import org.infernalstudios.questlog.client.gui.components.QuestlogButton;
import org.infernalstudios.questlog.client.gui.components.ScrollableComponent;
import org.infernalstudios.questlog.client.gui.components.scrollable.Scrollable;
import org.infernalstudios.questlog.network.ClientPacketHandler;
import org.infernalstudios.questlog.network.packet.GroupActionPacket;
import org.infernalstudios.questlog.network.packet.QuestShareActionPacket;
import org.infernalstudios.questlog.platform.Services;
import org.infernalstudios.questlog.util.texture.Texture;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The group page uses the book background, Questlog buttons and scroll component. */
public class GroupScreen extends Screen {
    private static final Texture BACKGROUND = new Texture(ResourceLocation.fromNamespaceAndPath(Questlog.MODID, "textures/gui/questlog.png"), 1024, 512, 0, 0, 1024, 512);
    private static final int PAGE_WIDTH = 245;
    private static final int ROW_HEIGHT = 18;
    private static final int[] COLUMNS = {0, 104, 165, PAGE_WIDTH};
    private final Screen journal;
    private final ResourceLocation shareQuest;
    private String selected = "";
    private String selectedPlayer = "";
    private boolean inspecting;
    private boolean inviting;
    private boolean naming;
    private String name = "";
    private String search = "";
    private int sort;
    private boolean descending;
    private NoShadowEditBox input;
    private int left, top;

    public GroupScreen(Screen journal) {
        this(journal, null);
    }

    public GroupScreen(Screen journal, ResourceLocation shareQuest) {
        super(Component.translatable("questlog.groups.tab"));
        this.journal = journal;
        this.shareQuest = shareQuest;
    }

    private CompoundTag state() { return ClientPacketHandler.groupState(); }
    private CompoundTag candidates() {
        if (shareQuest == null) return state();
        CompoundTag data = ClientPacketHandler.shareCandidates();
        return shareQuest.toString().equals(data.getString("quest")) ? data : new CompoundTag();
    }
    private String own() { return state().getString("own"); }
    private String self() { return minecraft != null && minecraft.player != null ? minecraft.player.getUUID().toString() : ""; }
    private CompoundTag group(String id) {
        for (Tag item : state().getList("groups", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) item;
            if (row.getString("id").equals(id)) return row;
        }
        return null;
    }
    private CompoundTag current() { return group(own().isEmpty() ? selected : own()); }
    private boolean leader(CompoundTag group) { return group != null && self().equals(group.getString("leader")); }
    private static String id(CompoundTag row) { return row.getString("id"); }
    private static String label(String key) { return Component.translatable("questlog.groups." + key).getString(); }
    private void send(int action, String group, String target, String value) {
        Services.PLATFORM.sendPacketToServer(new GroupActionPacket(action, group, target, value));
    }

    @Override protected void init() {
        super.init();
        left = (width - 245) / 2 + 1 + Questlog.getConfig().gui.mainPanelX;
        top = (height - 136) / 2 + 1 + Questlog.getConfig().gui.mainPanelY;
        rebuild();
        if (!initialized) {
            initialized = true;
            if (shareQuest == null) send(GroupActionPacket.REFRESH, "", "", "");
            else share(QuestShareActionPacket.SEARCH, "");
        }
    }
    private boolean initialized;

    public void refreshFromServer() {
        if (shareQuest != null) { rebuild(); return; }
        if (!own().isEmpty()) { selected = own(); inspecting = false; }
        else if (!selected.isEmpty() && group(selected) == null) { selected = ""; inspecting = false; }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        panelTitle = "";
        input = null;
        if (shareQuest != null) { invitePopup(); return; }
        addRenderableWidget(new BookSideTab(left - 32, top + 4, Items.WRITABLE_BOOK.getDefaultInstance(), false,
                Component.translatable("questlog.groups.journal"), () -> minecraft.setScreen(journal)));
        addRenderableWidget(new BookSideTab(left - 32, top + 65, Items.PLAYER_HEAD.getDefaultInstance(), true,
                Component.translatable("questlog.groups.tab"), () -> {}));
        if (naming) { namePopup(); return; }
        if (inviting) { invitePopup(); return; }
        if (own().isEmpty()) buildDirectory(); else buildMembership();
    }

    private QuestlogButton button(int x, int y, String key, Runnable action) {
        return addRenderableWidget(new QuestlogButton(x, y, 0x4B3422, 0x7C4C22, Component.translatable("questlog.groups." + key), action::run, QuestlogGuiSet.DEFAULT));
    }

    private void buildDirectory() {
        int right = left + 264;
        panelTitle = inspecting && current() != null ? current().getString("name") : label("list_title");
        if (!inspecting) {
            for (int column = 0; column < 3; column++) {
                int selectedColumn = column;
                String key = column == 0 ? "name" : column == 1 ? "count" : "open";
                String heading = label("sort." + key) + (sort == column ? (descending ? " ↓" : " ↑") : "");
                addRenderableWidget(new SortHeader(left + COLUMNS[column], top + 33,
                        COLUMNS[column + 1] - COLUMNS[column], heading, () -> {
                    if (sort == selectedColumn) descending = !descending;
                    else { sort = selectedColumn; descending = false; }
                    rebuild();
                }));
            }
        }
        List<Row> rows = new ArrayList<>();
        if (inspecting && current() != null) {
            addMembers(rows, current(), false);
        } else {
            for (Tag item : state().getList("groups", Tag.TAG_COMPOUND)) {
                CompoundTag group = (CompoundTag) item;
                rows.add(new Row(id(group), group.getString("name") + (group.getBoolean("invited") ? " ✉" : ""), false, true));
            }
            Comparator<Row> comparator = switch (sort) {
                case 1 -> Comparator.comparingInt(row -> group(row.id).getInt("count"));
                case 2 -> Comparator.comparing(row -> group(row.id).getBoolean("open"));
                default -> Comparator.comparing(row -> group(row.id).getString("name"), String.CASE_INSENSITIVE_ORDER);
            };
            comparator = comparator.thenComparing(row -> row.id);
            rows.sort(descending ? comparator.reversed() : comparator);
        }
        list(rows, inspecting ? top + 36 : top + 54, inspecting ? 100 : 82);
        CompoundTag group = current();
        if (group == null) {
            button(right, top + 8, "create", () -> { naming = true; rebuild(); });
        } else {
            if (inspecting) button(right, top + 115, "back", () -> { inspecting = false; rebuild(); });
            else button(right, top + 36, "inspect", () -> { inspecting = true; rebuild(); });
            if (group.getBoolean("invited")) {
                button(right, top + 58, "accept", () -> send(6, id(group), "", ""));
                button(right + 88, top + 58, "refuse", () -> send(7, id(group), "", ""));
            } else if (group.getBoolean("applied")) {
                button(right, top + 58, "cancel_apply", () -> send(2, id(group), "", ""));
            } else if (group.getBoolean("open")) {
                button(right, top + 58, "apply", () -> send(1, id(group), "", ""));
            }
            button(right, top + 88, "create", () -> { naming = true; rebuild(); });
        }
    }

    private String panelTitle = "";

    private void buildMembership() {
        CompoundTag group = current();
        if (group == null) return;
        panelTitle = group.getString("name");
        List<Row> rows = new ArrayList<>();
        addMembers(rows, group, true);
        list(rows, top + 36, 100);
        int right = left + 264;
        boolean chief = leader(group);
        boolean targetSelected = chief && !selectedPlayer.isEmpty() && !selectedPlayer.equals(self());
        boolean applicant = false;
        if (targetSelected) for (Tag item : group.getList("applicants", Tag.TAG_COMPOUND))
            if (id((CompoundTag) item).equals(selectedPlayer)) applicant = true;
        int actionRows = chief ? 4 + (targetSelected ? applicant ? 1 : 2 : 0) : 2;
        int actionY = top + (136 - actionRows * 18 - (actionRows - 1) * 4) / 2;
        QuestlogButton loot = button(right, actionY, "loot." + group.getInt("loot"), () -> {
            if (chief) send(12, id(group), "", Integer.toString((group.getInt("loot") + 1) % 3));
        });
        loot.active = chief;
        loot.setTooltip(Tooltip.create(Component.translatable("questlog.groups.loot.pending")));
        actionY += 22;
        if (chief) {
            if (targetSelected) {
                if (applicant) {
                    button(right, actionY, "accept", () -> { send(3, id(group), selectedPlayer, ""); selectedPlayer = ""; });
                    button(right + 88, actionY, "refuse", () -> { send(4, id(group), selectedPlayer, ""); selectedPlayer = ""; });
                    actionY += 22;
                } else {
                    button(right, actionY, "transfer", () -> { send(10, id(group), selectedPlayer, ""); selectedPlayer = ""; });
                    actionY += 22;
                    button(right, actionY, "kick", () -> confirm("kick", () -> send(9, id(group), selectedPlayer, "")));
                    actionY += 22;
                }
            }
            button(right, actionY, group.getBoolean("open") ? "close" : "reopen", () -> send(11, id(group), "", ""));
            actionY += 22;
            button(right, actionY, "invite", () -> { inviting = true; search = ""; rebuild(); send(GroupActionPacket.REFRESH, "", "", ""); });
            actionY += 22;
        }
        button(right, actionY, "leave", () -> confirm("leave", () -> send(8, id(group), "", "")));
    }

    private void addMembers(List<Row> rows, CompoundTag group, boolean includeApplicants) {
        for (Tag item : group.getList("members", Tag.TAG_COMPOUND)) {
            CompoundTag person = (CompoundTag) item;
            boolean online = false;
            for (Tag marker : group.getList("online", Tag.TAG_COMPOUND)) if (id((CompoundTag) marker).equals(id(person))) online = true;
            String text = person.getString("name") + (id(person).equals(group.getString("leader")) ? " ★" : "") + (online ? "" : "  (" + label("offline") + ")");
            rows.add(new Row(id(person), text, !online, false));
        }
        if (includeApplicants) for (Tag item : group.getList("applicants", Tag.TAG_COMPOUND)) {
            CompoundTag person = (CompoundTag) item;
            rows.add(new Row(id(person), person.getString("name") + "  (" + label("applicant") + ")", false, false));
        }
    }

    private void list(List<Row> rows, int y, int height) {
        addRenderableWidget(new ScrollableComponent(left, y, PAGE_WIDTH, height, new Rows(rows)));
    }

    private record Row(String id, String text, boolean muted, boolean group) {}
    private class SortHeader extends AbstractButton {
        private final Runnable action;
        private SortHeader(int x, int y, int width, String text, Runnable action) {
            super(x, y, width, 18, Component.literal(text));
            this.action = action;
        }
        @Override protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (isHovered) graphics.fill(getX() + 2, getY(), getX() + width - 2, getY() + height, 0x33A36B38);
            String text = font.plainSubstrByWidth(getMessage().getString(), width - 5);
            graphics.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + 5, 0xFF4B3422, false);
        }
        @Override public void onPress() { action.run(); }
        @Override public void updateWidgetNarration(@NotNull NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
    private class Rows extends AbstractContainerEventHandler implements Scrollable {
        private final List<Row> rows;
        private ScrollableComponent scroller;
        private Rows(List<Row> rows) { this.rows = rows; }
        @Override public int getHeight() { return rows.size() * ROW_HEIGHT; }
        @Override public void setScrollableComponent(ScrollableComponent component) { scroller = component; }
        @Override public List<? extends GuiEventListener> children() { return List.of(); }
        @Override public boolean mouseClicked(double x, double y, int button) {
            int index = (int) y / ROW_HEIGHT;
            if (button != 0 || index < 0 || index >= rows.size()) return false;
            Row row = rows.get(index);
            if (row.group) { selected = row.id; inspecting = false; }
            else selectedPlayer = row.id;
            rebuild();
            return true;
        }
        @Override public void render(@NotNull GuiGraphics graphics, int x, int y, float delta) {
            if (scroller == null) return;
            graphics.pose().pushPose();
            graphics.pose().translate(scroller.getXOffset(), scroller.getYOffset(), 0);
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int yy = i * ROW_HEIGHT;
                boolean selectedRow = row.group ? row.id.equals(selected) : row.id.equals(selectedPlayer);
                if (row.group) graphics.fill(0, yy, PAGE_WIDTH - 5, yy + ROW_HEIGHT, i % 2 == 0 ? 0x44A36B38 : 0x22FFFFFF);
                if (selectedRow) graphics.fill(0, yy, PAGE_WIDTH - 5, yy + ROW_HEIGHT, 0x66A36B38);
                if (row.group) {
                    CompoundTag group = group(row.id);
                    graphics.drawString(font, font.plainSubstrByWidth(row.text, COLUMNS[1] - 8), 4, yy + 5, 0xFF4B3422, false);
                    if (group != null) {
                        String count = Integer.toString(group.getInt("count"));
                        graphics.drawString(font, count, COLUMNS[1] + (COLUMNS[2] - COLUMNS[1] - font.width(count)) / 2, yy + 5, 0xFF4B3422, false);
                        String status = label(group.getBoolean("open") ? "open" : "closed");
                        graphics.drawString(font, status, COLUMNS[2] + (COLUMNS[3] - COLUMNS[2] - font.width(status)) / 2, yy + 5, 0xFF4B3422, false);
                    }
                } else graphics.drawString(font, font.plainSubstrByWidth(row.text, 215), 4, yy + 5, row.muted ? 0xFF777777 : 0xFF4B3422, false);
            }
            graphics.pose().popPose();
        }
    }

    private void namePopup() {
        panelTitle = label("create");
        input = new NoShadowEditBox(font, left + 32, top + 58, 180, 20, Component.translatable("questlog.groups.name"));
        input.setMaxLength(32); input.setValue(name); input.setResponder(value -> name = value);
        addRenderableWidget(input);
        setInitialFocus(input);
        button(left + 43, top + 94, "create_short", () -> {
            if (!name.trim().isEmpty()) { send(0, "", "", name.trim()); naming = false; name = ""; rebuild(); }
        });
        button(left + 143, top + 94, "cancel", () -> { naming = false; rebuild(); });
    }

    private void invitePopup() {
        panelTitle = shareQuest == null ? label("invite") : Component.translatable("questlog.share.title").getString();
        input = new NoShadowEditBox(font, left + 8, top + 38, 215, 20, Component.translatable("questlog.groups.search"));
        input.setMaxLength(32); input.setValue(search);
        input.setResponder(value -> {
            search = value;
            if (shareQuest == null) send(GroupActionPacket.REFRESH, "", "", search);
            else share(QuestShareActionPacket.SEARCH, search);
        });
        addRenderableWidget(input);
        setInitialFocus(input);
        List<Row> rows = new ArrayList<>();
        for (Tag item : candidates().getList("candidates", Tag.TAG_COMPOUND)) {
            CompoundTag person = (CompoundTag) item;
            rows.add(new Row(id(person), person.getString("name"), false, false));
        }
        list(rows, top + 65, 71);
        button(left + 264, top + 50, shareQuest == null ? "invite_short" : "share", () -> {
            if (!selectedPlayer.isEmpty()) {
                if (shareQuest == null) { send(5, own(), selectedPlayer, ""); inviting = false; selectedPlayer = ""; rebuild(); }
                else { share(QuestShareActionPacket.OFFER, selectedPlayer); minecraft.setScreen(journal); }
            }
        });
        button(left + 264, top + 76, shareQuest == null ? "back" : "cancel", () -> {
            if (shareQuest == null) { inviting = false; selectedPlayer = ""; rebuild(); }
            else minecraft.setScreen(journal);
        });
    }

    private void share(int action, String value) {
        Services.PLATFORM.sendPacketToServer(new QuestShareActionPacket(action, shareQuest, value));
    }

    private void confirm(String kind, Runnable action) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            minecraft.setScreen(this);
            if (yes) action.run();
        }, Component.translatable("questlog.groups." + kind),
                Component.translatable("questlog.groups.confirm.personal")));
    }

    @Override public void renderBackground(@NotNull GuiGraphics graphics, int x, int y, float delta) {
        super.renderBackground(graphics, x, y, delta);
        BACKGROUND.blit(graphics, (width - BACKGROUND.width()) / 2, (height - BACKGROUND.height()) / 2);
    }
    @Override public void render(@NotNull GuiGraphics graphics, int x, int y, float delta) {
        super.render(graphics, x, y, delta);
        if (!panelTitle.isEmpty()) {
            String title = font.plainSubstrByWidth(panelTitle, 224);
            graphics.drawString(font, title, left + (PAGE_WIDTH - font.width(title)) / 2, top + 7, 0xFF4B3422, false);
            QuestlogGuiSet.DEFAULT.smallHR.blit(graphics, left - 3, top + 18);
        }
        if (shareQuest == null && own().isEmpty() && !inspecting && !naming && !inviting) {
            graphics.fill(left + 3, top + 52, left + PAGE_WIDTH - 5, top + 53, 0xFFA8874A);
            for (int column = 1; column < 3; column++) {
                int ruleX = left + COLUMNS[column];
                graphics.fill(ruleX, top + 33, ruleX + 1, top + 136, 0xFFA8874A);
            }
        }
        if (naming) graphics.drawString(font, Component.translatable("questlog.groups.enter_name"), left + 32, top + 43, 0xFF4B3422, false);
        if (shareQuest == null && own().isEmpty() && selected.isEmpty() && !naming && state().getList("groups", Tag.TAG_COMPOUND).isEmpty()) {
            int lineY = top + 62;
            for (var line : font.split(Component.translatable("questlog.groups.none"), 224)) {
                graphics.drawString(font, line, left + 8, lineY, 0xFF4B3422, false);
                lineY += font.lineHeight + 2;
            }
        }
        if ((inviting || shareQuest != null) && candidates().getList("candidates", Tag.TAG_COMPOUND).isEmpty())
            graphics.drawString(font, Component.translatable("questlog.groups.no_players"), left + 8, top + 72, 0xFF4B3422, false);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (shareQuest != null && (key == GLFW.GLFW_KEY_ESCAPE ||
                ((key == GLFW.GLFW_KEY_E || QuestlogClient.OPEN_SCREEN_KEY.matches(key, scan)) && (input == null || !input.isFocused())))) {
            minecraft.setScreen(journal);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE ||
                ((key == GLFW.GLFW_KEY_E || QuestlogClient.OPEN_SCREEN_KEY.matches(key, scan)) && (input == null || !input.isFocused()))) {
            if (naming || inviting) { naming = false; inviting = false; rebuild(); }
            else if (inspecting) { inspecting = false; rebuild(); }
            else minecraft.setScreen(journal);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}
