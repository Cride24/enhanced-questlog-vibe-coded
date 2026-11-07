package org.infernalstudios.questlog.core;

import com.google.gson.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Inline translations. No client classes: definitions remain readable on a dedicated server. */
public final class QuestText {
    public static final Set<String> QUEST_FIELDS = Set.of("title", "description", "details", "description_completed",
            "description_failed", "back_button_text", "collect_button_text", "collected_text", "uncollected_text");
    public static final Set<String> ALL_FIELDS;
    private static final String LITERAL_FIELDS = "_questlog_literal_fields";
    private static volatile String language = "en_us";
    static { var fields = new HashSet<>(QUEST_FIELDS); fields.add("name"); ALL_FIELDS = Set.copyOf(fields); }
    private QuestText() {}
    public static String language() { return language; }
    public static void setLanguage(String code) { language = code == null ? "en_us" : code; }
    public static String key(String field, String code) { return code == null || code.isEmpty() ? field : field + "-" + code; }
    public static boolean isKey(String key, Set<String> fields) {
        return fields.stream().anyMatch(field -> key.equals(field) || key.startsWith(field + "-")
                && key.substring(field.length()+1).matches("[a-z0-9]+(?:_[a-z0-9]+)*"));
    }
    public static boolean filled(JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        if (value.isJsonPrimitive()) return value.getAsJsonPrimitive().isString() && !value.getAsString().isBlank();
        try { Component text = Component.Serializer.fromJson(value, RegistryAccess.EMPTY); return text != null && !text.getString().isBlank(); }
        catch (RuntimeException malformed) { return false; }
    }
    public static String selectKey(JsonObject data, String field, String code) {
        String requested = key(field, code);
        if (filled(data.get(requested))) return requested;
        String english = key(field, "en_us");
        if (filled(data.get(english))) return english;
        for (var entry : data.entrySet())
            if (isKey(entry.getKey(), Set.of(field)) && filled(entry.getValue())) return entry.getKey();
        return field;
    }
    public static JsonObject project(JsonObject source) {
        JsonObject view = source.deepCopy();
        JsonArray literal = new JsonArray();
        for (String field : ALL_FIELDS) {
            String selected = selectKey(source, field, language);
            if (!selected.equals(field)) { view.add(field, source.get(selected).deepCopy()); literal.add(field); }
        }
        view.add(LITERAL_FIELDS, literal);
        return view;
    }
    public static boolean translatable(JsonObject projected, String field, boolean legacy) {
        return legacy && (!projected.has(LITERAL_FIELDS) || !projected.getAsJsonArray(LITERAL_FIELDS).contains(new JsonPrimitive(field)));
    }
    public static String text(JsonObject data, String field, String fallback) { return text(data,field,language,fallback); }
    public static String text(JsonObject data, String field, String code, String fallback) {
        JsonElement value = data.get(selectKey(data,field,code));
        return value == null || value.isJsonNull() ? fallback : value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }
    public static Component name(JsonObject source, String field, Component fallback) {
        JsonObject view = project(source);
        String value = text(view,field,null);
        return value == null ? fallback : translatable(view,field,source.has("translatable") && source.get("translatable").getAsBoolean())
                ? Component.translatable(value) : Component.literal(value);
    }
    public static JsonElement gameplay(JsonElement source) {
        if (source.isJsonArray()) {
            JsonArray array = new JsonArray(); source.getAsJsonArray().forEach(entry -> array.add(gameplay(entry))); return array;
        }
        if (!source.isJsonObject()) return source.deepCopy();
        JsonObject object = new JsonObject();
        source.getAsJsonObject().entrySet().forEach(entry -> {
            boolean presentation = source.getAsJsonObject().has("type") && (isKey(entry.getKey(),ALL_FIELDS) || entry.getKey().equals("translatable"));
            if (!presentation) object.add(entry.getKey(), Set.of("prerequisites","requirements","objectives","objective","failures","rewards","choices","entries").contains(entry.getKey())
                    ? gameplay(entry.getValue()) : entry.getValue().deepCopy());
        });
        return object;
    }

    /** Only text fields, in authored order; selecting a missing language exposes empty fields. */
    public static final class Draft {
        private final JsonObject texts = new JsonObject();
        private final Set<String> fields;
        private final Set<String> initialLanguages = new LinkedHashSet<>();
        public Draft(JsonObject definition, Set<String> fields) {
            this.fields = fields;
            definition.entrySet().forEach(entry -> { if (isKey(entry.getKey(),fields)) texts.add(entry.getKey(),entry.getValue().deepCopy()); });
            collectLanguages(definition,fields,initialLanguages);
        }
        /** Pick the opening language from filled texts, preserving the source JSON's authored order. */
        public String initialLanguage(String playerLanguage) {
            if (initialLanguages.isEmpty()) return playerLanguage;
            if (initialLanguages.size()==1) return initialLanguages.iterator().next();
            if (initialLanguages.contains(playerLanguage)) return playerLanguage;
            if (initialLanguages.contains("en_us")) return "en_us";
            return initialLanguages.iterator().next();
        }
        private static void collectLanguages(JsonElement source,Set<String> fields,Set<String> languages) {
            if (source.isJsonArray()) {
                source.getAsJsonArray().forEach(entry -> collectLanguages(entry,fields,languages));
                return;
            }
            if (!source.isJsonObject()) return;
            for (var entry : source.getAsJsonObject().entrySet()) {
                if (isKey(entry.getKey(),fields) && filled(entry.getValue())) {
                    for (String field : fields) {
                        if (entry.getKey().equals(field)) languages.add("");
                        else if (entry.getKey().startsWith(field+"-")) languages.add(entry.getKey().substring(field.length()+1));
                    }
                }
                // Only visit quest entries, never item data or entity predicates with unrelated name fields.
                if (Set.of("prerequisites","requirements","objectives","objective","failures","rewards","choices","entries").contains(entry.getKey()))
                    collectLanguages(entry.getValue(),Set.of("name"),languages);
            }
        }
        public String read(String field, String code) {
            JsonElement value = texts.get(key(field,code));
            return value == null || value.isJsonNull() ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString();
        }
        public String firstText(String field) {
            for (var entry : texts.entrySet()) if (isKey(entry.getKey(),Set.of(field)) && filled(entry.getValue()))
                return entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : entry.getValue().toString();
            return "";
        }
        public void put(String field, String code, String value) {
            String target = key(field,code); value = value == null ? "" : value;
            if (read(field,code).equals(value)) return; // Preserve existing formatted components and insertion order.
            if (value.isBlank()) { texts.remove(target); return; }
            if (field.startsWith("description") || field.equals("details")) {
                try {
                    JsonElement component = JsonParser.parseString(value);
                    if (component.isJsonObject() || component.isJsonArray()) { texts.add(target,component); return; }
                } catch (RuntimeException ordinaryText) {}
            }
            texts.addProperty(target,value);
        }
        public void apply(JsonObject definition) {
            for (String field : new ArrayList<>(definition.keySet())) if (isKey(field,fields)) definition.remove(field);
            texts.entrySet().forEach(entry -> definition.add(entry.getKey(),entry.getValue().deepCopy()));
        }
    }
}
