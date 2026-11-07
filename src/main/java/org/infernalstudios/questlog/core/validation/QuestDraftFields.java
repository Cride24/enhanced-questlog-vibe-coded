package org.infernalstudios.questlog.core.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Keep unfinished numeric fields unfinished, so validation can explain them to the author. */
public final class QuestDraftFields {
    private QuestDraftFields() {}
    public static void putInteger(JsonObject object, String key, String text) {
        if (text == null || text.isBlank()) { object.remove(key); return; }
        try { object.addProperty(key, Integer.parseInt(text.trim())); }
        catch (NumberFormatException malformed) { object.addProperty(key, text); }
    }
    public static String text(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }
    public static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
}
