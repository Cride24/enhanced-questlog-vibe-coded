package org.infernalstudios.questlog.core;

import net.minecraft.resources.ResourceLocation;
import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** Human-readable automatic identity. The server allocates the final number at first save. */
public final class QuestIdGenerator {
    private QuestIdGenerator() {}
    public static ResourceLocation next(String chapter, String title, Predicate<ResourceLocation> exists) {
        String base = slug(chapter == null ? "" : chapter.replace("questlog:", ""), "main", 48)
                + "_" + slug(title, "sans-titre", 60) + "_";
        for (int number = 1; number < Integer.MAX_VALUE; number++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("questlog", base + number);
            if (!exists.test(id)) return id;
        }
        throw new IllegalStateException("No free quest identity");
    }
    private static String slug(String text, String fallback, int limit) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) return fallback;
        return normalized.substring(0, Math.min(limit, normalized.length())).replaceAll("-+$", "");
    }
}
