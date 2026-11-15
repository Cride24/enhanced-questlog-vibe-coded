package org.infernalstudios.questlog.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.function.Function;

/** Server-thread combat totals live only as long as the damaged entity. */
public final class CombatContributions {
    private static final Map<LivingEntity, Fight> fights = new WeakHashMap<>();
    private CombatContributions() {}

    private static final class Fight {
        final Map<String, Map<UUID, Float>> damage = new HashMap<>();
        final Set<String> credited = new HashSet<>();
        Set<String> fatalKeys = Set.of();
        UUID fatalPlayer;
        float fatalDamage;
        long fatalTick;
    }

    private static ServerPlayer attacker(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) return player;
        if (source.getEntity() instanceof net.minecraft.world.entity.TamableAnimal tame
                && tame.getOwner() instanceof ServerPlayer player) return player;
        if (source.getEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player)
            return player;
        if (source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player)
            return player;
        return null;
    }

    private static boolean matches(net.minecraft.world.entity.Entity entity,
                                   org.infernalstudios.questlog.core.quests.objectives.Objective objective) {
        if (objective instanceof org.infernalstudios.questlog.core.quests.objectives.entity.EntityKillObjective kill
                && kill.matchesCombatTarget(entity)) return true;
        for (var child : objective.getChildren()) if (matches(entity, child)) return true;
        return false;
    }

    private static Set<String> eligibleQuests(ServerPlayer player, LivingEntity entity) {
        ServerPlayerManager players = ServerPlayerManager.INSTANCE;
        QuestManager manager = players == null ? null : players.getManagerIfPresent(player);
        if (manager == null || !manager.isLoaded()) return Set.of();
        AdventureGroups.Group group = AdventureGroups.INSTANCE == null ? null : AdventureGroups.INSTANCE.membership(player.getUUID());
        UUID groupId = group == null ? player.getUUID() : group.id;
        Set<String> keys = new HashSet<>();
        for (var quest : manager.getAllQuests()) if (quest.isGroupQuest() && quest.isActive()
                && quest.isVisibleToPlayer() && !quest.hasSentCompletion && !quest.isFailed()
                && quest.objectives.stream().anyMatch(objective -> matches(entity, objective))) {
            keys.add(quest.getId() + "/" + groupId + "/" + quest.rewardCycle());
        }
        return keys;
    }

    public static void damage(LivingEntity entity, DamageSource source, float amount) {
        ServerPlayer player = attacker(source);
        if (player == null || !Float.isFinite(amount) || amount <= 0) return;
        Fight fight = fights.computeIfAbsent(entity, ignored -> new Fight());
        for (String key : eligibleQuests(player, entity))
            fight.damage.computeIfAbsent(key, ignored -> new HashMap<>()).merge(player.getUUID(), amount, Float::sum);
    }

    /** Fabric's nonfatal damage event omits the lethal hit; count it only if death really follows. */
    public static void fatalCandidate(LivingEntity entity, DamageSource source, float amount) {
        ServerPlayer player = attacker(source);
        Fight fight = fights.computeIfAbsent(entity, ignored -> new Fight());
        fight.fatalKeys = player == null || !Float.isFinite(amount) || amount <= 0
                ? Set.of() : eligibleQuests(player, entity);
        fight.fatalPlayer = player == null ? null : player.getUUID();
        fight.fatalDamage = amount;
        fight.fatalTick = entity.level().getGameTime();
    }

    static Map<UUID, Float> withFatal(Map<UUID, Float> previous, UUID player, float amount, boolean valid) {
        Map<UUID, Float> result = new HashMap<>(previous);
        if (valid && player != null && amount > 0 && Float.isFinite(amount)) result.merge(player, amount, Float::sum);
        return result;
    }

    public static List<UUID> bestEligible(Map<UUID, Float> damage, Predicate<UUID> eligible) {
        float best = 0;
        for (var entry : damage.entrySet()) if (eligible.test(entry.getKey())) best = Math.max(best, entry.getValue());
        float highest = best;
        return damage.entrySet().stream().filter(entry -> highest > 0 && entry.getValue() == highest
                        && eligible.test(entry.getKey()))
                .map(Map.Entry::getKey).sorted(Comparator.comparing(UUID::toString)).toList();
    }

    public static List<UUID> bestEligible(LivingEntity entity, ResourceLocation quest, UUID group,
                                           Function<UUID, String> currentCycle) {
        Fight fight = fights.get(entity);
        if (fight == null) return List.of();
        String prefix = quest + "/" + group + "/";
        Map<UUID, Float> qualified = new HashMap<>();
        for (var entry : fight.damage.entrySet()) if (entry.getKey().startsWith(prefix))
            for (var player : entry.getValue().entrySet()) {
                String cycle = currentCycle.apply(player.getKey());
                if (cycle != null && entry.getKey().equals(prefix + cycle))
                    qualified.put(player.getKey(), player.getValue());
            }
        if (fight.fatalPlayer != null && entity.level().getGameTime() - fight.fatalTick <= 1) {
            String cycle = currentCycle.apply(fight.fatalPlayer);
            if (cycle != null && fight.fatalKeys.contains(prefix + cycle))
                qualified = withFatal(qualified, fight.fatalPlayer, fight.fatalDamage, true);
        }
        return bestEligible(qualified, id -> true);
    }

    public static boolean creditOnce(LivingEntity entity, ResourceLocation quest, UUID group, String objective) {
        Fight fight = fights.computeIfAbsent(entity, ignored -> new Fight());
        return fight.credited.add(quest + "/" + group + "/" + objective);
    }

    public static void forget(LivingEntity entity) { fights.remove(entity); }
    public static void clear() { fights.clear(); }
}
