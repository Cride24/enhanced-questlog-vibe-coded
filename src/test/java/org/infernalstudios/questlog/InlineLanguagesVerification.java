package org.infernalstudios.questlog;

import com.google.gson.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.core.*;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.display.*;
import org.infernalstudios.questlog.core.validation.QuestValidation;
import java.util.Set;

/** Definition, display and progress round trips; no game or renderer launched. */
public final class InlineLanguagesVerification {
    private static int checks;
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        verifyOpeningLanguage();
        JsonObject data = json("{\"title-fr_fr\":\"Français\",\"title-de_de\":\"Deutsch\",\"title-en_us\":\"English\",\"description-fr_fr\":\"Description française\"}");
        QuestText.setLanguage("fr_fr"); check(QuestText.text(data,"title","").equals("Français"),"Requested player language wins");
        QuestText.setLanguage("de_de"); check(QuestText.text(data,"title","").equals("Deutsch"),"Another available language is selected");
        QuestText.setLanguage("ja_jp"); check(QuestText.text(data,"title","").equals("English"),"Missing player language prefers US English");
        data.remove("title-en_us"); check(QuestText.text(data,"title","").equals("Français"),"Without US English the first authored value wins");
        data.addProperty("title-en_us","  "); check(QuestText.text(data,"title","").equals("Français"),"Blank US English does not mask a real translation");
        data.addProperty("title-ja_jp",""); check(QuestText.text(data,"title","").equals("Français"),"Blank requested translation falls back too");
        check(QuestValidation.inspect(data,QuestValidation.UNAVAILABLE).functional(),"Localized-only required texts validate without legacy title/description");
        check(QuestText.text(data,"description","").equals("Description française"),"Fallback is independent for each field");
        JsonObject wrongTitle=data.deepCopy(); wrongTitle.add("title-it_it",json("{\"text\":\"Unexpected title component\"}"));
        check(!QuestValidation.inspect(wrongTitle,QuestValidation.UNAVAILABLE).functional(),"A malformed unused title translation is reported before display construction");
        JsonObject wrongDescription=data.deepCopy(); wrongDescription.addProperty("description-it_it",42);
        check(!QuestValidation.inspect(wrongDescription,QuestValidation.UNAVAILABLE).functional(),"Numeric translated descriptions are rejected even when another language is valid");
        JsonObject legacy=json("{\"title\":\"Old title\",\"description\":\"Old description\"}");
        check(QuestText.text(legacy,"title","").equals("Old title") && QuestValidation.inspect(legacy,QuestValidation.UNAVAILABLE).functional(),"Unsuffixed definitions remain functional");
        QuestText.Draft draft=new QuestText.Draft(data,QuestText.QUEST_FIELDS);
        check(draft.initialLanguage("en_us").equals("fr_fr"),"Opening an inline definition chooses its first authored language");
        check(new QuestText.Draft(legacy,QuestText.QUEST_FIELDS).initialLanguage("fr_fr").isEmpty(),"Legacy definitions open in None mode");
        check(draft.read("title","it_it").isEmpty(),"A missing editing language starts empty instead of copying a fallback");
        draft.put("title","it_it","Italiano"); draft.put("description","it_it","Descrizione");
        check(draft.read("title","fr_fr").equals("Français"),"Writing another language retains previous text before saving");
        JsonObject saved=data.deepCopy(); draft.apply(saved);
        JsonObject roundTrip=JsonParser.parseString(saved.toString()).getAsJsonObject();
        check(roundTrip.get("title-it_it").getAsString().equals("Italiano") && roundTrip.get("title-fr_fr").getAsString().equals("Français"),"One JSON persists all translations");
        check(QuestText.selectKey(roundTrip,"title","ru_ru").equals("title-fr_fr"),"Save/reopen retains authored fallback order");
        draft.put("title","it_it",""); draft.apply(saved);
        check(!saved.has("title-it_it") && saved.has("title-fr_fr"),"Clearing one translation leaves other languages intact");
        JsonObject rich=json("{\"details-fr_fr\":{\"text\":\"Détails\",\"bold\":true}}");
        QuestText.Draft richDraft=new QuestText.Draft(rich,QuestText.QUEST_FIELDS); richDraft.put("details","fr_fr",richDraft.read("details","fr_fr"));
        richDraft.apply(rich); check(rich.get("details-fr_fr").isJsonObject(),"Unchanged formatted components keep their structure");
        QuestText.Draft chapter=new QuestText.Draft(json("{\"name\":\"Legacy chapter\",\"icon\":{\"item\":\"minecraft:book\"}}"),Set.of("name"));
        chapter.put("name","fr_fr","Chapitre"); chapter.put("name","en_us","Chapter");
        JsonObject chapterJson=json("{\"name\":\"Legacy chapter\",\"hidden\":true}"); chapter.apply(chapterJson);
        QuestText.setLanguage("fr_fr"); check(QuestText.name(chapterJson,"name",null).getString().equals("Chapitre"),"Chapter names use the same language resolver");
        check(chapterJson.get("hidden").getAsBoolean() && chapter.read("name","").equals("Legacy chapter"),"Chapter language editing retains shared fields and None text");
        verifyDisplayAndProgress();
        QuestText.setLanguage("en_us");
        System.out.println("Inline languages: "+checks+" checks passed (headless; language picker and layout need in-game testing).");
    }
    private static void verifyOpeningLanguage() {
        check(new QuestText.Draft(json("{}"),QuestText.QUEST_FIELDS).initialLanguage("de_de").equals("de_de"),"An empty definition starts in the player's configured language");
        QuestText.Draft single=new QuestText.Draft(json("{\"title-fr_fr\":\"Titre\",\"description-fr_fr\":\"Description\",\"title-en_us\":\"  \"}"),QuestText.QUEST_FIELDS);
        check(single.initialLanguage("en_us").equals("fr_fr"),"The only filled language wins over the player's language and blank variants");
        JsonObject multilingual=json("{\"title-fr_fr\":\"Titre\",\"description-de_de\":\"Beschreibung\",\"title-en_us\":\"Title\"}");
        QuestText.Draft multiple=new QuestText.Draft(multilingual,QuestText.QUEST_FIELDS);
        check(multiple.initialLanguage("de_de").equals("de_de"),"Player language wins even when present only in the description");
        check(multiple.initialLanguage("ja_jp").equals("en_us"),"Unavailable player language opens US English");
        multilingual.remove("title-en_us");
        check(new QuestText.Draft(multilingual,QuestText.QUEST_FIELDS).initialLanguage("ja_jp").equals("fr_fr"),"Without player language or US English, JSON authored order wins");
        QuestText.Draft mixed=new QuestText.Draft(json("{\"title\":\"Old title\",\"title-fr_fr\":\"Titre\"}"),QuestText.QUEST_FIELDS);
        check(mixed.initialLanguage("fr_fr").equals("fr_fr"),"Player language wins over earlier legacy fields in mixed definitions");
        check(mixed.initialLanguage("ja_jp").isEmpty(),"Legacy None mode participates in authored-order fallback");
        QuestText.Draft chapter=new QuestText.Draft(json("{\"name-fr_fr\":\"Chapitre\",\"name-en_us\":\"Chapter\"}"),Set.of("name"));
        check(chapter.initialLanguage("fr_fr").equals("fr_fr") && chapter.initialLanguage("ja_jp").equals("en_us"),"Chapters use the same opening-language priorities");
        QuestText.Draft entries=new QuestText.Draft(json("{\"title-fr_fr\":\"Titre\",\"rewards\":[{\"type\":\"choice\",\"choices\":[{\"type\":\"experience\",\"name-en_us\":\"Experience\"}]}]}"),QuestText.QUEST_FIELDS);
        check(entries.initialLanguage("en_us").equals("en_us"),"Nested reward names count as filled quest language content");
        QuestText.Draft predicate=new QuestText.Draft(json("{\"title-fr_fr\":\"Titre\",\"objectives\":[{\"type\":\"entity_kill\",\"entity\":{\"name\":\"Boss\"}}]}"),QuestText.QUEST_FIELDS);
        check(predicate.initialLanguage("en_us").equals("fr_fr"),"Semantic entity names do not add an unrelated legacy editing language");
        QuestText.Draft entryFirst=new QuestText.Draft(json("{\"objectives\":[{\"type\":\"read\",\"name-de_de\":\"Lesen\"}],\"title-fr_fr\":\"Titre\"}"),QuestText.QUEST_FIELDS);
        check(entryFirst.initialLanguage("ja_jp").equals("de_de"),"Authored order is preserved even when entry names precede root text");
    }
    private static void verifyDisplayAndProgress() {
        JsonObject definition=json("{\"title-fr_fr\":\"Première quête\",\"title-en_us\":\"First quest\",\"description-fr_fr\":\"[Bois](item:minecraft:oak_log)\",\"description-en_us\":\"[Wood](item:minecraft:oak_log)\",\"details-fr_fr\":\"Détails\",\"details-en_us\":\"Details\",\"description_completed-fr_fr\":\"Bravo\",\"description_failed-fr_fr\":\"Échec\",\"back_button_text-fr_fr\":\"Retour perso\",\"collect_button_text-fr_fr\":\"Prendre\",\"collected_text-fr_fr\":\"Reçu\",\"uncollected_text-fr_fr\":\"À prendre\",\"objectives\":[{\"type\":\"read\",\"required_amount\":3,\"name-fr_fr\":\"Lire\",\"name-en_us\":\"Read\"}],\"rewards\":[{\"type\":\"experience\",\"experience\":1,\"name-fr_fr\":\"Expérience\",\"name-en_us\":\"Experience\"}]}");
        QuestManager manager=new QuestManager(null) { @Override public boolean isClient() { return true; } };
        ResourceLocation id=ResourceLocation.parse("questlog:multilingual");
        QuestText.setLanguage("fr_fr"); Quest quest=Quest.create(definition,id,manager); manager.addQuest(quest);
        QuestDisplayData display=quest.getDisplay();
        check(display.getTitle().getString().equals("Première quête"),"Localized-only quest constructs normally");
        check(display.getDescription().getSiblings().stream().anyMatch(part -> part.getStyle().getHoverEvent()!=null && part.getStyle().getClickEvent()!=null),"Localized descriptions retain item tooltips and recipe links");
        check(display.getDetails().getString().equals("Détails") && display.hasDetails(),"Localized details are available");
        check(display.getBackButtonText().getString().equals("Retour perso") && display.getCollectButtonText().getString().equals("Prendre"),"Custom buttons are localized");
        check(display.getCollectedText().getString().equals("Reçu") && display.getUncollectedText().getString().equals("À prendre"),"Reward status labels are localized");
        check(quest.objectives.getFirst().getDisplay().getName().getString().equals("Lire"),"Objective names are localized");
        check(quest.rewards.getFirst().getDisplay().getName().getString().equals("Expérience"),"Reward names are localized");
        QuestText.setLanguage("en_us");
        check(display.getTitle().getString().equals("First quest") && display.getDescription().getString().equals("Wood"),"The same quest instance updates after a language change");
        check(display.getDetails().getString().equals("Details"),"Details update after a language change");
        check(display.matchesSearch("first quest") && !display.matchesSearch("première"),"Journal search uses the displayed language");
        check(quest.objectives.getFirst().getDisplay().getName().getString().equals("Read") && quest.rewards.getFirst().getDisplay().getName().getString().equals("Experience"),"Entry name caches update too");
        QuestText.setLanguage("fr_fr"); check(display.getTitle().getString().equals("Première quête"),"Switching back restores the original text");
        JsonObject mixed=definition.deepCopy(); mixed.addProperty("translatable",true); mixed.addProperty("back_button_text","gui.back"); mixed.remove("back_button_text-fr_fr");
        QuestDisplayData mixedDisplay=new QuestDisplayData(mixed);
        check(mixedDisplay.getTitle().getString().equals("Première quête"),"Inline literals coexist with legacy translatable flags");
        check(mixedDisplay.getBackButtonText().getString().equals(net.minecraft.network.chat.Component.translatable("gui.back").getString()),"Legacy resource-pack keys keep their meaning");
        quest.objectives.getFirst().setUnits(2); quest.rewards.getFirst().setRewarded(true);
        var progress=quest.serialize();
        JsonObject updated=definition.deepCopy(); updated.getAsJsonArray("objectives").get(0).getAsJsonObject().addProperty("name-de_de","Lesen");
        updated.getAsJsonArray("rewards").get(0).getAsJsonObject().addProperty("name-en_us","Changed wording");
        Quest restored=Quest.create(updated,id,manager); restored.deserialize(progress);
        check(restored.objectives.getFirst().getUnits()==2 && restored.rewards.getFirst().hasRewarded(),"Adding/editing translations retains objective counts and claimed rewards");
        JsonObject oldStructure=json("{}"); oldStructure.add("objectives",definition.get("objectives")); oldStructure.add("rewards",definition.get("rewards"));
        progress.putString("_structure",oldStructure.toString()); restored.deserialize(progress);
        check(restored.objectives.getFirst().getUnits()==2 && restored.rewards.getFirst().hasRewarded(),"Older progress signatures with display text are migrated safely");
        JsonObject entity=json("{\"type\":\"entity_kill\",\"name-fr_fr\":\"Chasser\",\"entity\":{\"name\":\"Boss\"}}");
        JsonObject shape=QuestText.gameplay(entity).getAsJsonObject();
        check(!shape.has("name-fr_fr") && shape.getAsJsonObject("entity").get("name").getAsString().equals("Boss"),"Display names are stripped from signatures while entity predicates are preserved");
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean condition,String text) { checks++; if(!condition) throw new AssertionError(text); }
}
