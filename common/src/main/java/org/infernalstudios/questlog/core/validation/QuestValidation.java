package org.infernalstudios.questlog.core.validation;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.core.quests.*;

import java.util.ArrayList;
import java.util.List;

/** Inspects definitions without constructing quests or registering gameplay listeners. */
public final class QuestValidation {
    public record Issue(String path, String message, boolean error) {}
    public record Report(List<Issue> issues) {
        public Report { issues = List.copyOf(issues); }
        public boolean functional() { return issues.stream().noneMatch(Issue::error); }
        public boolean showAfterSave(boolean activationRequested) { return activationRequested && !functional(); }
    }
    @FunctionalInterface
    public interface References {
        /** null means this registry is unavailable, not that the reference is missing. */
        Boolean exists(EditorMetadata.SuggestionType kind, ResourceLocation id, boolean tag);
    }
    public static final References UNAVAILABLE = (kind, id, tag) -> null;
    private QuestValidation() {}

    public static Report inspect(JsonObject definition, References references) {
        List<Issue> issues = new ArrayList<>();
        if (definition == null) {
            issues.add(new Issue("$", "questlog.validation.object", true));
            return new Report(issues);
        }
        translatedTypes(definition,"",issues);
        String title = string(definition.get(org.infernalstudios.questlog.core.QuestText.selectKey(definition,"title","en_us")));
        if (title == null || title.isBlank()) issues.add(new Issue("title", "questlog.validation.required", true));
        if (!hasDescription(definition.get(org.infernalstudios.questlog.core.QuestText.selectKey(definition,"description","en_us")))) issues.add(new Issue("description", "questlog.validation.required", true));
        number(definition,"sort_order","sort_order",false,Integer.MIN_VALUE,issues);
        number(definition,"order","order",false,Integer.MIN_VALUE,issues);
        if (definition.has("_validation_error")) issues.add(new Issue("$", "questlog.validation.json", true));
        for (String key : List.of("active", "repeatable", "global", "shareable", "hidden", "hide_when_completed")) {
            bool(definition, key, key, issues);
        }
        reference(definition.get("chapter"), "chapter", EditorMetadata.SuggestionType.CHAPTER, false, references, issues);
        entries(definition, "objectives", false, "objectives", references, issues, 0);
        entries(definition, definition.has("prerequisites") ? "prerequisites" : "requirements", false,
                "prerequisites", references, issues, 0);
        entries(definition, "failures", false, "failures", references, issues, 0);
        entries(definition, "rewards", true, "rewards", references, issues, 0);
        return new Report(issues);
    }

    private static boolean hasDescription(JsonElement description) {
        if (description == null || description.isJsonNull()) return false;
        String text = string(description);
        if (text != null) return !text.isBlank();
        try {
            var component = net.minecraft.network.chat.Component.Serializer.fromJson(description, net.minecraft.core.RegistryAccess.EMPTY);
            return component != null && !component.getString().isBlank();
        } catch (RuntimeException invalid) { return false; }
    }
    private static void translatedTypes(JsonObject object, String path, List<Issue> issues) {
        for (var entry : object.entrySet()) {
            String key = entry.getKey(); JsonElement value = entry.getValue();
            if (!key.contains("-") || !org.infernalstudios.questlog.core.QuestText.isKey(key,org.infernalstudios.questlog.core.QuestText.ALL_FIELDS)
                    || value == null || value.isJsonNull()) continue;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) continue;
            boolean componentField = key.startsWith("description-") || key.startsWith("description_completed-")
                    || key.startsWith("description_failed-") || key.startsWith("details-");
            if (componentField && (value.isJsonObject() || value.isJsonArray())) {
                if (value.isJsonArray() && value.getAsJsonArray().isEmpty()) continue;
                try {
                    if (net.minecraft.network.chat.Component.Serializer.fromJson(value,net.minecraft.core.RegistryAccess.EMPTY) != null) continue;
                } catch (RuntimeException invalid) {}
            }
            issues.add(new Issue(field(path,key),"questlog.validation.string",true));
        }
    }
    public static Report inspectEntry(JsonObject entry, boolean reward, References references) {
        List<Issue> issues = new ArrayList<>();
        entry(entry, reward, "", references, issues, 0);
        return new Report(issues);
    }

    private static void entries(JsonObject parent, String key, boolean reward, String path,
                                References refs, List<Issue> issues, int depth) {
        if (!parent.has(key)) return;
        JsonElement value = parent.get(key);
        if (!value.isJsonArray()) { issues.add(new Issue(path, "questlog.validation.array", true)); return; }
        int index = 0;
        for (JsonElement element : value.getAsJsonArray()) {
            String child = path + "[" + index++ + "]";
            if (!element.isJsonObject()) issues.add(new Issue(child, "questlog.validation.object", true));
            else entry(element.getAsJsonObject(), reward, child, refs, issues, depth + 1);
        }
    }

    private static void entry(JsonObject value, boolean reward, String path, References refs, List<Issue> issues, int depth) {
        translatedTypes(value,path,issues);
        // Bound JSON nesting only; this is not an analysis of quest dependency cycles.
        if (depth > 64) { issues.add(new Issue(path, "questlog.validation.depth", true)); return; }
        String type = string(value.get("type"));
        ResourceLocation id = type == null ? null : ResourceLocation.tryParse(type.contains(":") ? type : "questlog:" + type);
        if (id == null || !(reward ? QuestRewardRegistry.getRegisteredTypes() : QuestObjectiveRegistry.getRegisteredTypes()).contains(id)) {
            issues.add(new Issue(field(path, "type"), type == null || type.isBlank() ? "questlog.validation.required" : "questlog.validation.type", true));
            return;
        }
        EditorMetadata meta = reward ? QuestRewardRegistry.getMetadata(id) : QuestObjectiveRegistry.getMetadata(id);
        if (meta != null) {
            String target = meta.targetFieldKey();
            if (target != null) {
                boolean required = meta.targetRequired(reward);
                JsonElement element = value.get(target);
                if (required && (element == null || element.isJsonNull() || "".equals(string(element)))) {
                    issues.add(new Issue(field(path, target), "questlog.validation.required", true));
                } else if (element != null) {
                    reference(element, field(path, target), meta.suggestionType(), !reward, refs, issues);
                    if (meta.suggestionType() == EditorMetadata.SuggestionType.NONE && !target.equals("bounds") && string(element) == null)
                        issues.add(new Issue(field(path, target), "questlog.validation.string", true));
                }
            }
            if (meta.amountFieldKey() != null) {
                boolean required = reward && "experience".equals(meta.amountFieldKey());
                number(value, meta.amountFieldKey(), field(path, meta.amountFieldKey()), required,
                        "experience".equals(meta.amountFieldKey()) ? 0 : 1, issues);
            }
        }
        bool(value, "auto_claim", field(path, "auto_claim"), issues);
        bool(value, "levels", field(path, "levels"), issues);
        bool(value, "retroactive", field(path, "retroactive"), issues);
        if (!reward && List.of("and", "or").contains(id.getPath())) entries(value, "objectives", false, field(path, "objectives"), refs, issues, depth);
        if (!reward && id.getPath().equals("not") && value.has("objective")) {
            if (!value.get("objective").isJsonObject()) issues.add(new Issue(field(path, "objective"), "questlog.validation.object", true));
            else entry(value.getAsJsonObject("objective"), false, field(path, "objective"), refs, issues, depth + 1);
        }
        if (!reward && id.getPath().equals("visit_position")) {
            JsonElement bounds = value.get("bounds");
            if (bounds == null) { /* metadata already reports the missing field */ }
            else {
                try { validateBounds(bounds); }
                catch (RuntimeException e) { issues.add(new Issue(field(path, "bounds"), "questlog.validation.bounds", true)); }
            }
        }
        if (reward && id.getPath().equals("choice")) {
            entries(value, "choices", true, field(path, "choices"), refs, issues, depth);
            number(value, "pick_count", field(path, "pick_count"), false, 1, issues);
            if (!value.has("choices") || (value.get("choices").isJsonArray() && value.getAsJsonArray("choices").isEmpty()))
                issues.add(new Issue(field(path, "choices"), "questlog.validation.required", true));
            else if (value.get("choices").isJsonArray() && value.has("pick_count")) {
                try { if (value.get("pick_count").getAsInt() > value.getAsJsonArray("choices").size()) issues.add(new Issue(field(path, "pick_count"), "questlog.validation.choice", true)); }
                catch (RuntimeException ignored) { /* already reported by number */ }
            }
        }
        if (reward && id.getPath().equals("random") && value.has("entries")) {
            if (!value.get("entries").isJsonArray()) issues.add(new Issue(field(path, "entries"), "questlog.validation.array", true));
            else {
                int index = 0;
                for (JsonElement element : value.getAsJsonArray("entries")) {
                    String child = field(path, "entries") + "[" + index++ + "]";
                    if (!element.isJsonObject() || !element.getAsJsonObject().has("reward") || !element.getAsJsonObject().get("reward").isJsonObject())
                        issues.add(new Issue(child, "questlog.validation.object", true));
                    else {
                        entry(element.getAsJsonObject().getAsJsonObject("reward"), true, child + ".reward", refs, issues, depth + 1);
                        decimal(element.getAsJsonObject(), "weight", child + ".weight", issues);
                    }
                }
            }
            decimal(value, "empty_weight", field(path, "empty_weight"), issues);
        }
    }

    private static void reference(JsonElement element, String path, EditorMetadata.SuggestionType kind,
                                  boolean allowTag, References refs, List<Issue> issues) {
        if (element == null || kind == EditorMetadata.SuggestionType.NONE) return;
        if (element.isJsonObject()) {
            if (kind != EditorMetadata.SuggestionType.ITEM && kind != EditorMetadata.SuggestionType.ENTITY_TYPE) {
                issues.add(new Issue(path, "questlog.validation.string", true)); return;
            }
            JsonObject object = element.getAsJsonObject();
            element = object.has("id") ? object.get("id") : object.has("item") ? object.get("item") : object.get("type");
            // Item objective objects may match only components, without an item ID.
            if (element == null && allowTag && kind == EditorMetadata.SuggestionType.ITEM) return;
        }
        String value = string(element);
        if (value == null || value.isBlank()) { issues.add(new Issue(path, "questlog.validation.string", true)); return; }
        boolean tag = value.startsWith("#");
        if (tag && (!allowTag || !java.util.Set.of(EditorMetadata.SuggestionType.ITEM, EditorMetadata.SuggestionType.BLOCK, EditorMetadata.SuggestionType.ENTITY_TYPE).contains(kind))) { issues.add(new Issue(path, "questlog.validation.id", true)); return; }
        if (kind == EditorMetadata.SuggestionType.CHAPTER && !value.contains(":")) value = "questlog:" + value;
        ResourceLocation id = ResourceLocation.tryParse(tag ? value.substring(1) : value);
        if (id == null) { issues.add(new Issue(path, "questlog.validation.id", true)); return; }
        Boolean exists = refs.exists(kind, id, tag);
        if (Boolean.FALSE.equals(exists)) issues.add(new Issue(path, "questlog.validation.reference", true));
        else if (exists == null) issues.add(new Issue(path, "questlog.validation.unchecked", false));
    }
    private static void validateBounds(JsonElement bounds) {
        if (bounds.isJsonPrimitive()) {
            String text = bounds.getAsString().trim();
            if (text.startsWith("{") || text.startsWith("[")) { validateBounds(JsonParser.parseString(text)); return; }
            String[] coordinates = text.split("[,\\s]+");
            if (coordinates.length != 3 && coordinates.length != 6) throw new IllegalArgumentException();
            for (String coordinate : coordinates) Integer.parseInt(coordinate);
        } else if (bounds.isJsonArray()) {
            JsonArray coordinates = bounds.getAsJsonArray();
            if (coordinates.size() != 3 && coordinates.size() != 6) throw new IllegalArgumentException();
            for (JsonElement coordinate : coordinates) coordinate.getAsBigDecimal().intValueExact();
        } else if (bounds.isJsonObject()) {
            JsonObject object=bounds.getAsJsonObject();
            for (String axis : java.util.List.of("x", "y", "z")) {
                boolean found=false;
                for (String key : java.util.List.of(axis,axis+"1","min"+axis.toUpperCase(),"min_"+axis)) {
                    if (object.has(key)) { object.get(key).getAsBigDecimal().intValueExact(); found=true; }
                }
                if (!found) throw new IllegalArgumentException();
                for (String key : java.util.List.of(axis+"2","max"+axis.toUpperCase(),"max_"+axis))
                    if (object.has(key)) object.get(key).getAsBigDecimal().intValueExact();
            }
        } else throw new IllegalArgumentException();
    }
    private static String field(String path, String key) { return path.isEmpty() ? key : path + "." + key; }
    private static String string(JsonElement e) { return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null; }
    private static void bool(JsonObject object, String key, String path, List<Issue> issues) {
        if (object.has(key) && !(object.get(key).isJsonPrimitive() && object.getAsJsonPrimitive(key).isBoolean())) issues.add(new Issue(path, "questlog.validation.boolean", true));
    }
    private static void number(JsonObject object, String key, String path, boolean required, int minimum, List<Issue> issues) {
        if (!object.has(key)) { if (required) issues.add(new Issue(path, "questlog.validation.required", true)); return; }
        JsonElement e = object.get(key);
        try {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || e.getAsBigDecimal().intValueExact() < minimum) throw new ArithmeticException();
        } catch (RuntimeException ex) { issues.add(new Issue(path, "questlog.validation.number", true)); }
    }
    private static void decimal(JsonObject object, String key, String path, List<Issue> issues) {
        if (!object.has(key)) return;
        JsonElement e = object.get(key);
        try { if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || !Double.isFinite(e.getAsDouble()) || e.getAsDouble() < 0) throw new ArithmeticException(); }
        catch (RuntimeException ex) { issues.add(new Issue(path, "questlog.validation.number", true)); }
    }
}
