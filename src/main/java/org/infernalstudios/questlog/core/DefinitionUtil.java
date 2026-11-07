package org.infernalstudios.questlog.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import org.infernalstudios.questlog.Questlog;
import org.infernalstudios.questlog.platform.Services;
import org.infernalstudios.questlog.util.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class DefinitionUtil {
    private static final Map<ResourceLocation, JsonObject> QUEST_DEFINITION_CACHE = new Object2ObjectOpenHashMap<>();
    private static final Map<ResourceLocation, JsonObject> CHAPTER_DEFINITION_CACHE = new Object2ObjectOpenHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static List<ResourceLocation> getCachedQuestKeys() {
        List<ResourceLocation> keys = new ArrayList<>(QUEST_DEFINITION_CACHE.keySet());
        keys.sort((a, b) -> {
            JsonObject jsonA = QUEST_DEFINITION_CACHE.get(a);
            JsonObject jsonB = QUEST_DEFINITION_CACHE.get(b);
            int orderA = definitionOrder(jsonA);
            int orderB = definitionOrder(jsonB);
            if (orderA != orderB) {
                return Integer.compare(orderA, orderB);
            }
            String titleA = definitionTitle(jsonA);
            String titleB = definitionTitle(jsonB);
            int titleCompare = titleA.compareToIgnoreCase(titleB);
            if (titleCompare != 0) {
                return titleCompare;
            }
            return a.compareTo(b);
        });
        return keys;
    }

    private static int definitionOrder(JsonObject definition) {
        try {
            return definition.has("sort_order") ? definition.get("sort_order").getAsInt()
                    : JsonUtils.getOrDefault(definition, "order", 0);
        } catch (RuntimeException malformed) {
            return 0; // Keep malformed definitions accessible to the author and validator.
        }
    }

    /** Append to this chapter's definitions, including inactive drafts, rather than the main-page aggregate. */
    public static int nextQuestOrder(String chapter) {
        ResourceLocation chapterId = chapterId(chapter);
        if (chapterId == null) return 0;
        Integer maximum = null;
        for (JsonObject definition : QUEST_DEFINITION_CACHE.values()) {
            try {
                if (!chapterId.equals(chapterId(JsonUtils.getOrDefault(definition, "chapter", "questlog:main")))) continue;
                int order = definitionOrder(definition);
                maximum = maximum == null ? order : Math.max(maximum, order);
            } catch (RuntimeException malformed) {
                // An unreadable chapter cannot influence another chapter's proposed order.
            }
        }
        return maximum == null ? 0 : maximum == Integer.MAX_VALUE ? maximum : maximum + 1;
    }

    private static ResourceLocation chapterId(String chapter) {
        return chapter == null ? null : ResourceLocation.tryParse(chapter.contains(":") ? chapter : Questlog.MODID + ":" + chapter);
    }

    private static String definitionTitle(JsonObject definition) {
        try {
            return QuestText.text(definition, "title", "");
        } catch (RuntimeException malformed) {
            return "";
        }
    }

    public static boolean hasCachedQuest(ResourceLocation path) {
        return QUEST_DEFINITION_CACHE.containsKey(path);
    }

    public static JsonObject getCachedQuest(ResourceLocation path) {
        if (!QUEST_DEFINITION_CACHE.containsKey(path)) {
            throw new NullPointerException("Quest not found: " + path);
        }
        return QUEST_DEFINITION_CACHE.get(path);
    }

    public static synchronized void putCachedQuest(ResourceLocation path, JsonObject definition) {
        QUEST_DEFINITION_CACHE.put(path, definition);
    }

    public static synchronized void clearClientCaches() {
        QUEST_DEFINITION_CACHE.clear();
        CHAPTER_DEFINITION_CACHE.clear();
    }

    public static List<ResourceLocation> getCachedChapterKeys() {
        List<ResourceLocation> keys = new ArrayList<>(CHAPTER_DEFINITION_CACHE.keySet());
        keys.sort((a, b) -> {
            boolean aMain = a.getNamespace().equals(Questlog.MODID) && a.getPath().equals("main");
            boolean bMain = b.getNamespace().equals(Questlog.MODID) && b.getPath().equals("main");
            if (aMain && !bMain) return -1;
            if (!aMain && bMain) return 1;

            JsonObject jsonA = CHAPTER_DEFINITION_CACHE.get(a);
            JsonObject jsonB = CHAPTER_DEFINITION_CACHE.get(b);
            int orderA = definitionOrder(jsonA);
            int orderB = definitionOrder(jsonB);

            if (orderA != orderB) {
                return Integer.compare(orderA, orderB);
            }
            return a.compareTo(b);
        });
        return keys;
    }

    public static synchronized JsonObject getCachedChapter(ResourceLocation path) {
        return CHAPTER_DEFINITION_CACHE.get(path);
    }

    public static void putCachedChapter(ResourceLocation path, JsonObject definition) {
        CHAPTER_DEFINITION_CACHE.put(path, definition);
    }

    public static synchronized void loadFromConfig() {
        QUEST_DEFINITION_CACHE.clear();
        CHAPTER_DEFINITION_CACHE.clear();

        Path configDir = Services.PLATFORM.getConfigDirectory().resolve("questlog");
        Path questDir = configDir.resolve("quests");
        Path chapterDir = configDir.resolve("chapters");

        createDirIfNotExists(questDir);
        createDirIfNotExists(chapterDir);

        Path defaultMainChapter = chapterDir.resolve("main.json");
        if (!Files.exists(defaultMainChapter)) {
            try {
                JsonObject mainChapter = new JsonObject();
                JsonObject iconObj = new JsonObject();
                iconObj.addProperty("item", "minecraft:knowledge_book");
                mainChapter.add("icon", iconObj);
                mainChapter.addProperty("default_chapter", true);
                mainChapter.addProperty("hidden", false);

                Files.writeString(defaultMainChapter, GSON.toJson(mainChapter));
            } catch (IOException e) {
                Questlog.LOGGER.error("Failed to create default main.json chapter", e);
            }
        }

        loadFiles(questDir, QUEST_DEFINITION_CACHE);
        loadFiles(chapterDir, CHAPTER_DEFINITION_CACHE);

        Questlog.LOGGER.info("Loaded {} quests and {} chapters from config.", QUEST_DEFINITION_CACHE.size(), CHAPTER_DEFINITION_CACHE.size());
    }

    private static void createDirIfNotExists(Path dir) {
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                Questlog.LOGGER.error("Failed to create directory: {}", dir, e);
            }
        }
    }

    private static void loadFiles(Path dir, Map<ResourceLocation, JsonObject> cache) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        Path relative = dir.relativize(path);
                        String resourcePath = relative.toString().replace(File.separatorChar, '/').replace(".json", "");
                        ResourceLocation id = Questlog.id(resourcePath);
                        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                            JsonObject json = GSON.fromJson(reader, JsonObject.class);
                            if (json == null) throw new IOException("Expected a JSON object");
                            cache.put(id, json);
                        } catch (Exception e) {
                            Questlog.LOGGER.error("Failed to parse file: {}", path, e);
                            if (cache == QUEST_DEFINITION_CACHE) {
                                String chapterVal = "main";
                                try {
                                    String content = Files.readString(path, StandardCharsets.UTF_8);
                                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"chapter\"\\s*:\\s*\"([^\"]+)\"").matcher(content);
                                    if (m.find()) {
                                        chapterVal = m.group(1);
                                    }
                                } catch (Exception ignored) {}

                                String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                                if (e.getCause() != null) {
                                    errorMsg += "\nCaused by: " + e.getCause().getMessage();
                                }
                                JsonObject fallback = new JsonObject();
                                fallback.addProperty("active", false);
                                fallback.addProperty("_validation_error", errorMsg);
                                fallback.addProperty("title", "Broken Quest (" + id.getPath() + ")");
                                fallback.addProperty("description", "This quest failed to load properly. Edit it to fix errors.\n\nError details:\n" + errorMsg);
                                fallback.addProperty("chapter", chapterVal);
                                cache.put(id, fallback);
                            }
                        }
                    });
        } catch (IOException e) {
            Questlog.LOGGER.error("Failed to read files from directory: {}", dir, e);
        }
    }
}
