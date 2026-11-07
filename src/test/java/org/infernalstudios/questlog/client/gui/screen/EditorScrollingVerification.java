package org.infernalstudios.questlog.client.gui.screen;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.server.Bootstrap;
import org.infernalstudios.questlog.client.gui.components.EditorScrollableComponent;
import org.infernalstudios.questlog.client.gui.components.QuestTextEditBox;

/** Exercise nested input without a running game or rendered fonts. */
public final class EditorScrollingVerification {
    private static int checks;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Font font = new Font(id -> null, false);
        verifyOpeningLanguages();
        verifyLanguageSwitch(font);
        verifyChapterLanguages(font);
        TestBox description = new TestBox(font, 100, 100);
        TestBox details = new TestBox(font, 100, 170);
        QuestEditorScreen screen = new QuestEditorScreen(null);
        try {
            var title = QuestEditorScreen.class.getDeclaredField("tempTitle"); title.setAccessible(true);
            var body = QuestEditorScreen.class.getDeclaredField("tempDescription"); body.setAccessible(true);
            check(title.get(screen).equals("") && body.get(screen).equals(""), "New title and required description start empty");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        screen.descriptionBox = description;
        screen.detailsBox = details;
        screen.leftFields.add(description);
        screen.leftFields.add(details);
        EditorScrollableComponent panel = new EditorScrollableComponent(90, 90, 220, 170, new LeftPanelScrollable(screen));

        check(panel.mouseScrolled(110, 110, 0, -1), "Wheel over description is consumed");
        check(description.offset() > 0, "Description scrolls");
        check(panel.getScrollAmount() == 0, "Text wheel leaves outer panel still");
        panel.mouseScrolled(110, 180, 0, -1);
        check(details.offset() > 0, "Details scrolls independently");
        check(description.offset() == 4.5, "Details wheel leaves description still");
        panel.mouseScrolled(95, 150, 0, -1);
        check(panel.getScrollAmount() > 0, "Wheel in panel margin scrolls the panel");
        panel.setScrollAmount(0);

        check(panel.mouseClicked(298, 190, 0), "Click on details scrollbar is accepted");
        check(details.isFocused(), "Scrollbar click focuses its field");
        check(panel.mouseDragged(298, 224, 0, 0, 34), "Details scrollbar can be dragged");
        check(details.offset() == details.maximum(), "Dragging to bottom reaches all text");
        panel.mouseDragged(110, 110, 0, -188, -114);
        check(details.offset() == 0, "Drag across description remains with details and clamps at top");
        check(description.offset() == 4.5, "Other field never steals a drag");
        panel.mouseReleased(298, 224, 0);
        check(!details.mouseDragged(298, 224, 0, 0, 1), "Release through outer panel ends the inner drag");

        panel.mouseClicked(298, 120, 0);
        panel.mouseDragged(298, 500, 0, 0, 380);
        check(description.offset() == description.maximum(), "Drag outside panel clamps at bottom");
        panel.mouseReleased(298, 500, 0);
        panel.mouseScrolled(298, 120, 0, 1);
        check(description.offset() < description.maximum(), "Wheel works on the visible scrollbar too");
        double previous = description.offset();
        check(!description.mouseScrolled(95, 95, 0, -1), "Wheel outside a text box is ignored");
        check(description.offset() == previous, "Outside wheel leaves text unchanged");
        description.contentHeight = 9;
        check(!description.mouseScrolled(110, 110, 0, -1), "Short text yields the wheel to its parent");

        check(QuestEditorScreen.acceptsTitleChange("x".repeat(49), "x".repeat(50)),
                "The fiftieth title character is accepted");
        check(!QuestEditorScreen.acceptsTitleChange("x".repeat(50), "x".repeat(51)),
                "The fifty-first title character is rejected");
        check(!QuestEditorScreen.acceptsTitleChange("x".repeat(60), "x".repeat(61)),
                "Existing long titles cannot grow");
        check(QuestEditorScreen.acceptsTitleChange("x".repeat(60), "x".repeat(59)),
                "Existing long titles may be shortened");
        System.out.println("Editor scrolling and title input: " + checks
                + " checks passed (headless; visual appearance needs in-game testing).");
    }

    private static void verifyOpeningLanguages() {
        org.infernalstudios.questlog.core.QuestText.setLanguage("de_de");
        check(new QuestEditorScreen(null).editingLanguage().equals("de_de"),"New quest editor uses the player language");
        var data=com.google.gson.JsonParser.parseString("{\"title-fr_fr\":\"Titre\",\"description-fr_fr\":\"Description\"}").getAsJsonObject();
        check(new QuestEditorScreen(null,null,data).editingLanguage().equals("fr_fr"),"Existing quest editor opens its sole authored language");
        data.addProperty("title-en_us","Title"); data.addProperty("description-de_de","Beschreibung");
        check(new QuestEditorScreen(null,null,data).editingLanguage().equals("de_de"),"Existing quest editor prefers available player language");
        org.infernalstudios.questlog.core.QuestText.setLanguage("ja_jp");
        check(new QuestEditorScreen(null,null,data).editingLanguage().equals("en_us"),"Existing quest editor falls back to US English");
        data.remove("title-en_us");
        check(new QuestEditorScreen(null,null,data).editingLanguage().equals("fr_fr"),"Existing quest editor finally follows JSON language order");
        org.infernalstudios.questlog.core.QuestText.setLanguage("en_us");
    }

    private static void verifyLanguageSwitch(Font font) {
        var definition = com.google.gson.JsonParser.parseString("{\"title-fr_fr\":\"Titre initial\",\"description-fr_fr\":\"Description initiale\",\"chapter\":\"main\"}").getAsJsonObject();
        QuestEditorScreen editor = new QuestEditorScreen(null,null,definition);
        try {
            var savedId=QuestEditorScreen.class.getDeclaredField("savedId"); savedId.setAccessible(true);
            savedId.set(editor,net.minecraft.resources.ResourceLocation.parse("questlog:stable_id"));
            editor.titleBox = new ValueBox(font,"Titre modifié"); editor.descriptionBox = new ValueDescription(font,"Description modifiée");
            editor.saveTemporaryState(); editor.switchLanguage("en_us");
            var title=QuestEditorScreen.class.getDeclaredField("tempTitle"); title.setAccessible(true);
            var body=QuestEditorScreen.class.getDeclaredField("tempDescription"); body.setAccessible(true);
            check(title.get(editor).equals("") && body.get(editor).equals(""),"Selecting a missing language exposes blank editor fields");
            editor.titleBox = new ValueBox(font,"English title"); editor.descriptionBox = new ValueDescription(font,"English description");
            editor.saveTemporaryState(); editor.switchLanguage("fr_fr");
            check(title.get(editor).equals("Titre modifié") && body.get(editor).equals("Description modifiée"),"Switching back retains unsaved edits to an existing quest");
            var build=QuestEditorScreen.class.getDeclaredMethod("buildDefinition"); build.setAccessible(true);
            var result=(com.google.gson.JsonObject)build.invoke(editor);
            check(result.get("title-fr_fr").getAsString().equals("Titre modifié") && result.get("title-en_us").getAsString().equals("English title"),"Editor save includes both languages without a legacy title");
            check(!result.has("title") && !result.has("description") && savedId.get(editor).toString().equals("questlog:stable_id"),"Language editing neither creates legacy text nor changes identity");
            editor.switchLanguage(""); title.set(editor,"Legacy title"); body.set(editor,"Legacy description");
            result=(com.google.gson.JsonObject)build.invoke(editor);
            check(result.has("title") && result.has("title-fr_fr") && result.has("title-en_us"),"None mode coexists with translations in the same definition");
        } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static final class ValueBox extends org.infernalstudios.questlog.client.gui.components.NoShadowEditBox {
        private final String value;
        ValueBox(Font font,String value) { super(font,0,0,195,16,net.minecraft.network.chat.Component.empty()); this.value=value; }
        @Override public String getValue() { return value; }
    }
    private static void verifyChapterLanguages(Font font) {
        var id=net.minecraft.resources.ResourceLocation.parse("questlog:language_chapter");
        var definition=com.google.gson.JsonParser.parseString("{\"name\":\"Old chapter\",\"name-fr_fr\":\"Chapitre\",\"icon\":{\"texture\":\"questlog:textures/gui/editor_plus.png\"},\"translatable\":true,\"custom_field\":\"keep\"}").getAsJsonObject();
        org.infernalstudios.questlog.core.DefinitionUtil.putCachedChapter(id,definition);
        ChapterEditorScreen editor=new ChapterEditorScreen(null,id);
        try {
            var box=ChapterEditorScreen.class.getDeclaredField("titleBox"); box.setAccessible(true);
            box.set(editor,new ValueBox(font,"Old chapter edited")); editor.saveTemporaryState(); editor.switchLanguage("fr_fr");
            box.set(editor,new ValueBox(font,"Chapitre modifié")); editor.saveTemporaryState(); editor.switchLanguage("en_us");
            box.set(editor,new ValueBox(font,"English chapter")); editor.saveTemporaryState(); editor.switchLanguage("fr_fr");
            var title=ChapterEditorScreen.class.getDeclaredField("tempTitle"); title.setAccessible(true);
            check(title.get(editor).equals("Chapitre modifié"),"Chapter edits survive switching between three languages before saving");
            var build=ChapterEditorScreen.class.getDeclaredMethod("buildDefinition"); build.setAccessible(true);
            var result=(com.google.gson.JsonObject)build.invoke(editor);
            check(result.get("name").getAsString().equals("Old chapter edited") && result.get("name-en_us").getAsString().equals("English chapter"),"Chapter JSON preserves None and translated names together");
            check(result.get("custom_field").getAsString().equals("keep") && result.get("translatable").getAsBoolean() && result.getAsJsonObject("icon").has("texture"),"Translating a chapter preserves its legacy flag, custom fields and texture icon");
        } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
        org.infernalstudios.questlog.core.DefinitionUtil.clearClientCaches();
    }
    private static final class ValueDescription extends QuestTextEditBox {
        private final String value;
        ValueDescription(Font font,String value) { super(font,0,0,195,54); this.value=value; }
        @Override public String getValue() { return value; }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static final class TestBox extends QuestTextEditBox {
        int contentHeight = 540;
        TestBox(Font font, int x, int y) { super(font, x, y, 195, 54); }
        @Override public int getInnerHeight() { return contentHeight; }
        @Override protected boolean scrollbarVisible() { return contentHeight > getHeight() - 8; }
        double offset() { return scrollAmount(); }
        int maximum() { return getMaxScrollAmount(); }
    }
}
