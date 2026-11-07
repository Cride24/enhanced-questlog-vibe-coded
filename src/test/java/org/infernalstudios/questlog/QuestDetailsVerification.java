package org.infernalstudios.questlog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.client.gui.screen.QuestDetails;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.display.QuestDisplayData;
import java.util.ArrayList;

/** Headless checks; visual layout and navigation still require in-game testing. */
public final class QuestDetailsVerification {
    private static int checks;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (String value : new String[] {null, "null", "\"\"", "\" \\n \\t\"", "[]", "{\"text\":\"\"}"}) {
            QuestDisplayData display = display(value);
            check(!display.hasDetails(), "Absent/empty details must hide the button: " + value);
            check(display.getDetails().getStyle().isItalic(), "Missing-details fallback must be italic");
            check(display.getDescription().getString().equals("Overview"), "Do not replace the overview");
        }
        QuestDisplayData plain = display("\"Extra instructions\\nSecond line\"");
        check(plain.hasDetails(), "Plain details are available");
        check(plain.getDetails().getString().equals("Extra instructions\nSecond line"), "Preserve line breaks");
        check(plain.matchesSearch("EXTRA INSTRUCTIONS"), "Search includes details");
        QuestDisplayData object = display("{\"text\":\"Formatted details\",\"italic\":true}");
        check(object.getDetails().getString().equals("Formatted details"), "Parse a JSON text object");
        check(object.getDetails().getStyle().isItalic(), "Preserve JSON formatting");
        QuestDisplayData array = display("[{\"text\":\"First\"},{\"text\":\" second\"}]");
        check(array.getDetails().getString().equals("First second"), "Parse a JSON text array");
        QuestDisplayData linked = display("\"See [Next quest](quest:questlog:next).\"");
        check(linked.getDetails().getString().equals("See Next quest."), "Parse existing inline quest links");
        Component questLink = linked.getDetails().getSiblings().get(1);
        check(questLink.getStyle().isUnderlined(), "Keep existing inline link formatting");
        check(questLink.getStyle().getClickEvent().getValue().equals("questlog:next"), "Keep existing quest navigation");
        Component imageLink = display("\"[Picture](image:questlog:test.png)\"").getDetails().getSiblings().get(1);
        check(imageLink.getStyle().getHoverEvent().getAction() == HoverEvent.Action.SHOW_TEXT,
                "Keep existing image links");
        JsonObject translated = definition("\"questlog.test.details\"");
        translated.addProperty("translatable", true);
        check(new QuestDisplayData(translated).getDetails().getString().equals("Translated extra instructions"),
                "Resolve details through the player's language");
        verifyLanguageChange();
        JsonObject disabled = definition("\"Additional text\"");
        disabled.addProperty("disable_details_button", true);
        disabled.addProperty("details_open_by_default", true);
        QuestDisplayData flags = new QuestDisplayData(disabled);
        check(flags.hasDetails() && flags.isDetailsButtonDisabled() && flags.isDetailsOpenByDefault(),
                "Keep both explicit display flags");
        QuestDetails overview = new QuestDetails(null, quest(plain));
        check(overview.getPreviousScreen() == null, "Overview returns to the caller");
        QuestDetails defaultDetails = new QuestDetails(null, quest(flags));
        check(defaultDetails.getPreviousScreen() instanceof QuestDetails, "Default details returns to an overview");
        Quest updated = quest(flags);
        QuestDetails refreshed = defaultDetails.refreshed(updated);
        check(((QuestDetails) refreshed.getPreviousScreen()).quest == updated, "Refresh updates the hidden overview too");
        check(refreshed.quest == updated, "Refresh updates the additional page");
        JsonObject absentDefault = definition(null);
        absentDefault.addProperty("details_open_by_default", true);
        check(new QuestDetails(null, quest(new QuestDisplayData(absentDefault))).getPreviousScreen() == null,
                "Do not open an empty additional page by default");
        System.out.println("Quest details: " + checks + " checks passed (headless, no game launched).");
    }

    private static void verifyLanguageChange() {
        Language original = Language.getInstance();
        try {
            Language.inject(testLanguage(original, "Find [Emerald](item:minecraft:emerald).",
                    "Open [Book](item:minecraft:book)."));
            JsonObject data = definition("\"questlog.test.dynamic.details\"");
            data.addProperty("description", "questlog.test.dynamic.description");
            data.addProperty("translatable", true);
            QuestDisplayData display = new QuestDisplayData(data);
            check(display.getDescription().getString().equals("Find Emerald."),
                    "Initial translated description resolves");
            Language.inject(testLanguage(original, "Trouve [Émeraude](item:minecraft:emerald).",
                    "Ouvre [Livre](item:minecraft:book)."));
            check(display.getDescription().getString().equals("Trouve Émeraude."),
                    "Description follows a language change after quest construction");
            check(display.getDetails().getString().equals("Ouvre Livre."),
                    "Details follow a language change after quest construction");
            check(display.matchesSearch("ÉMERAUDE"), "Search follows the current translation");
            check(display.getDetails().getSiblings().get(1).getStyle().getHoverEvent().getAction()
                            == HoverEvent.Action.SHOW_ITEM,
                    "Translated item links keep their tooltip action");
            Language.inject(testLanguage(original, "Empty description", ""));
            QuestDisplayData emptyInThisLanguage = new QuestDisplayData(data);
            check(!emptyInThisLanguage.hasDetails(), "Blank translated details hide the button");
            Language.inject(testLanguage(original, "Description", "Now available"));
            check(emptyInThisLanguage.hasDetails(), "Details can appear after a language change");
        } finally {
            Language.inject(original);
        }
    }

    private static Language testLanguage(Language original, String description, String details) {
        return new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return switch (key) {
                    case "questlog.test.dynamic.description" -> description;
                    case "questlog.test.dynamic.details" -> details;
                    default -> original.getOrDefault(key, fallback);
                };
            }

            @Override
            public boolean has(String key) {
                return key.startsWith("questlog.test.dynamic.") || original.has(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return original.isDefaultRightToLeft();
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return original.getVisualOrder(text);
            }
        };
    }

    private static JsonObject definition(String detailsJson) {
        JsonObject json = new JsonObject();
        json.addProperty("title", "Test quest");
        json.addProperty("description", "Overview");
        if (detailsJson != null) json.add("details", JsonParser.parseString(detailsJson));
        return json;
    }

    private static QuestDisplayData display(String detailsJson) {
        return new QuestDisplayData(definition(detailsJson));
    }

    private static Quest quest(QuestDisplayData display) {
        return new Quest(display, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                ResourceLocation.fromNamespaceAndPath("questlog", "test"), null, false, false);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
