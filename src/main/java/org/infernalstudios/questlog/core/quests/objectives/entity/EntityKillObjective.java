package org.infernalstudios.questlog.core.quests.objectives.entity;

import com.evandev.triggers.event.events.TriggerEntityEvent;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.core.AdventureGroups;
import org.infernalstudios.questlog.core.CombatContributions;
import org.infernalstudios.questlog.core.GroupProgress;

import java.util.UUID;

public class EntityKillObjective extends AbstractEntityObjective {

    public EntityKillObjective(JsonObject definition) {
        super(definition);
    }

    public boolean matchesCombatTarget(net.minecraft.world.entity.Entity entity) { return this.test(entity); }

    @Override
    public void registerEventListeners() {
        super.registerEventListeners();
        this.addListener(TriggerEntityEvent.Death.class, this::onEntityDeath);
    }

    private void onEntityDeath(TriggerEntityEvent.Death event) {
        if (!this.canRecordMore()) return;
        if (this.getParent().isGroupQuest() && this.test(event.entity)) {
            var source = this.getParent();
            String path = GroupProgress.objectivePath(source, this);
            if (path == null) return;
            UUID group = AdventureGroups.INSTANCE.membership(source.manager.player.getUUID()) == null
                    ? source.manager.player.getUUID()
                    : AdventureGroups.INSTANCE.membership(source.manager.player.getUUID()).id;
            java.util.List<UUID> best = CombatContributions.bestEligible(event.entity, source.getId(), group,
                    id -> GroupProgress.combatCycle(source, id));
            UUID winner = best.isEmpty() ? null : best.getFirst();
            if (winner == null) return;
            if (winner.equals(source.manager.player.getUUID())) {
                if (CombatContributions.creditOnce(event.entity, source.getId(), group, path)) {
                    GroupProgress.noteCombatReference(source, path, best);
                    this.addUnits(1);
                }
            } else if (source.manager.player.level().getServer().getPlayerList().getPlayer(winner) == null
                    && CombatContributions.creditOnce(event.entity, source.getId(), group, path)) {
                GroupProgress.noteCombatReference(source, path, best);
                if (GroupProgress.addOfflineKill(source, winner, path)) GroupProgress.refresh(source);
            }
            return;
        }
        if (
                event.damageSource.getEntity() instanceof ServerPlayer player &&
                        this.getParent().manager.player.equals(player) &&
                        this.test(event.entity)
        ) {
            this.addUnits(1);
        }
    }
}
