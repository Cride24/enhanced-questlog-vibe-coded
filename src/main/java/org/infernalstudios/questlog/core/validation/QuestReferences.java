package org.infernalstudios.questlog.core.validation;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.infernalstudios.questlog.core.DefinitionUtil;
import org.infernalstudios.questlog.core.quests.EditorMetadata;

import java.util.function.Predicate;

public record QuestReferences(RegistryAccess registries, Predicate<ResourceLocation> advancements, Predicate<ResourceLocation> lootTables)
        implements QuestValidation.References {
    public QuestReferences(RegistryAccess registries, Predicate<ResourceLocation> advancements) { this(registries, advancements, null); }
    public static QuestReferences forServer(net.minecraft.server.level.ServerPlayer player) {
        var server = java.util.Objects.requireNonNull(player.getServer());
        var tables = new java.util.HashSet<>(server.reloadableRegistries().getKeys(Registries.LOOT_TABLE));
        return new QuestReferences(player.level().registryAccess(), id -> server.getAdvancements().get(id) != null, tables::contains);
    }
    @Override
    public Boolean exists(EditorMetadata.SuggestionType kind, ResourceLocation id, boolean tag) {
        return switch (kind) {
            case QUEST -> DefinitionUtil.hasCachedQuest(id);
            case CHAPTER -> DefinitionUtil.getCachedChapter(id) != null;
            case ADVANCEMENT -> advancements == null ? null : advancements.test(id);
            case ITEM -> contains(BuiltInRegistries.ITEM, id, tag);
            case BLOCK -> contains(BuiltInRegistries.BLOCK, id, tag);
            case ENTITY_TYPE -> contains(BuiltInRegistries.ENTITY_TYPE, id, tag);
            case CUSTOM_STAT -> contains(BuiltInRegistries.CUSTOM_STAT, id, tag);
            case MOB_EFFECT -> contains(BuiltInRegistries.MOB_EFFECT, id, tag);
            case ENCHANTMENT -> dynamic(Registries.ENCHANTMENT, id, tag);
            case BIOME -> dynamic(Registries.BIOME, id, tag);
            case DIMENSION -> dynamic(Registries.DIMENSION, id, tag);
            case STRUCTURE -> dynamic(Registries.STRUCTURE, id, tag);
            // Loot tables and Origins need their respective server resources/integration.
            case LOOT_TABLE -> lootTables == null ? null : lootTables.test(id);
            case ORIGIN, NONE -> null;
        };
    }
    private <T> Boolean dynamic(ResourceKey<? extends Registry<T>> key, ResourceLocation id, boolean tag) {
        return registries == null ? null : registries.registry(key).map(r -> contains(r, id, tag)).orElse(null);
    }
    private static <T> boolean contains(Registry<T> registry, ResourceLocation id, boolean tag) {
        return tag ? registry.getTag(TagKey.create(registry.key(), id)).isPresent() : registry.containsKey(id);
    }
}
