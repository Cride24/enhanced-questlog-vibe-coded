package org.infernalstudios.questlog.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.infernalstudios.questlog.core.QuestText;

import java.util.*;
import java.util.function.Consumer;

/** Search the language inventory supplied by Minecraft, including resource-pack languages. */
public final class LanguageSelectionScreen extends Screen {
    private final Screen previous;
    private final String selected;
    private final Consumer<String> choose;
    private LanguageList list;
    private String query = "";
    public LanguageSelectionScreen(Screen previous, String selected, Consumer<String> choose) {
        super(Component.translatable("questlog.editor.language")); this.previous=previous; this.selected=selected; this.choose=choose;
    }
    public static Component label(String code) {
        if (code.isEmpty()) return Component.translatable("questlog.editor.language.none");
        Minecraft client = Minecraft.getInstance();
        var info = client == null ? null : client.getLanguageManager().getLanguage(code);
        return info == null ? Component.literal(code) : info.toComponent();
    }
    public static String playerLanguage() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? QuestText.language() : client.getLanguageManager().getSelected();
    }
    @Override protected void init() {
        EditBox search = new EditBox(font,width/2-150,32,300,20,Component.translatable("questlog.editor.language.search"));
        search.setHint(Component.translatable("questlog.editor.language.search")); search.setValue(query);
        addRenderableWidget(search);
        list = new LanguageList(); addRenderableWidget(list); populate();
        search.setResponder(value -> { query=value; populate(); });
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),button -> onClose()).bounds(width/2-75,height-28,150,20).build());
        setInitialFocus(search);
    }
    private void populate() {
        list.clear();
        String filter=query.toLowerCase(Locale.ROOT);
        list.add("",Component.translatable("questlog.editor.language.none"),filter);
        minecraft.getLanguageManager().getLanguages().forEach((code,info) -> list.add(code,info.toComponent(),filter));
        // Preserve codes found in imported definitions even if the current resource packs do not advertise them.
        if (!selected.isEmpty() && minecraft.getLanguageManager().getLanguage(selected)==null) list.add(selected,Component.literal(selected),filter);
    }
    @Override public void onClose() { minecraft.setScreen(previous); }
    @Override public void render(GuiGraphics graphics,int x,int y,float delta) {
        super.render(graphics,x,y,delta); graphics.drawCenteredString(font,title,width/2,14,0xFFFFFF);
    }
    private final class LanguageList extends ObjectSelectionList<LanguageEntry> {
        LanguageList() { super(LanguageSelectionScreen.this.minecraft,LanguageSelectionScreen.this.width,Math.max(24,LanguageSelectionScreen.this.height-94),58,24); }
        void clear() { clearEntries(); }
        void add(String code,Component name,String filter) {
            Component text=name.copy().append(code.isEmpty()?"":" — "+code);
            if (text.getString().toLowerCase(Locale.ROOT).contains(filter)) {
                LanguageEntry entry=new LanguageEntry(code,text); addEntry(entry); if(code.equals(selected)) setSelected(entry);
            }
        }
    }
    private final class LanguageEntry extends ObjectSelectionList.Entry<LanguageEntry> {
        private final String code; private final Component text;
        LanguageEntry(String code,Component text) { this.code=code; this.text=text; }
        @Override public Component getNarration() { return text; }
        @Override public void render(GuiGraphics graphics,int index,int y,int x,int width,int height,int mouseX,int mouseY,boolean hovered,float delta) {
            graphics.drawString(font,font.plainSubstrByWidth(text.getString(),width-12),x+6,y+6,0xFFFFFF);
        }
        @Override public boolean mouseClicked(double x,double y,int button) {
            if(button!=0) return false; choose.accept(code); minecraft.setScreen(previous); return true;
        }
        @Override public boolean keyPressed(int key,int scan,int modifiers) {
            if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key==org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) { choose.accept(code); minecraft.setScreen(previous); return true; }
            return false;
        }
    }
}
