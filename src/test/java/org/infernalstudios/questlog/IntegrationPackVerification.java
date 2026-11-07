package org.infernalstudios.questlog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.infernalstudios.questlog.core.DefinitionUtil;
import org.infernalstudios.questlog.core.QuestManager;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.validation.QuestValidation;

import java.nio.file.Files;
import java.nio.file.Path;

/** Checks that the installable integration pack parses and activates. */
public final class IntegrationPackVerification {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path root = Path.of(System.getProperty("questlog.integrationPack"));
        QuestManager manager = new QuestManager(null) {
            @Override public boolean isClient() { return true; }
            @Override public void sync(ResourceLocation id) { }
        };
        DefinitionUtil.clearClientCaches();
        try {
            for (Path file : Files.list(root.resolve("chapters")).filter(p -> p.toString().endsWith(".json")).toList()) {
                DefinitionUtil.putCachedChapter(id(file), JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            }
            var quests = Files.list(root.resolve("quests")).filter(p -> p.toString().endsWith(".json")).toList();
            for (Path file : quests) {
                DefinitionUtil.putCachedQuest(id(file), JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            }
            for (Path file : quests) {
                JsonObject definition = DefinitionUtil.getCachedQuest(id(file));
                var report = QuestValidation.inspect(definition, new org.infernalstudios.questlog.core.validation.QuestReferences(null, null));
                if (!report.functional()) throw new AssertionError(file + ": " + report.issues());
                if (Quest.create(definition, id(file), manager) == null) throw new AssertionError(file);
            }
            if (quests.size() != 17) throw new AssertionError("Expected 17 test quests, found " + quests.size());
            System.out.println("Integration pack: " + quests.size() + " quests parse, validate and construct.");
        } finally {
            DefinitionUtil.clearClientCaches();
        }
    }

    private static ResourceLocation id(Path file) {
        return ResourceLocation.fromNamespaceAndPath("questlog", file.getFileName().toString().replace(".json", ""));
    }
}
