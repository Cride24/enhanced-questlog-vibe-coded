package org.infernalstudios.questlog.core.quests.objectives;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.display.ObjectiveDisplayData;
import org.infernalstudios.questlog.core.quests.display.WithDisplayData;
import org.infernalstudios.questlog.util.JsonUtils;
import org.infernalstudios.questlog.util.NbtSaveable;
import org.jetbrains.annotations.Nullable;

public abstract class Objective implements NbtSaveable, WithDisplayData<ObjectiveDisplayData> {

    private final ObjectiveDisplayData display;
    private final int requiredAmount;
    private Quest parent;
    private int units;
    private long collectiveUnits = -1;
    private long collectiveRequired = -1;
    private boolean isPartOfPrerequisites = false;

    public Objective(JsonObject definition) {
        this.requiredAmount = JsonUtils.getOrDefault(definition, "required_amount", 1);
        this.units = 0;

        this.display = new ObjectiveDisplayData(definition);
        this.display.setObjective(this);
    }

    public void registerEventListeners() {
    }

    public void markAsPrerequisite() {
        this.isPartOfPrerequisites = true;
    }

    @Nullable
    public final Quest getParent() {
        return this.parent;
    }

    public void setParent(@Nullable Quest parent) {
        this.parent = parent;
    }

    public int getUnits() {
        return this.collectiveUnits >= 0 ? (int) Math.min(Integer.MAX_VALUE, this.collectiveUnits) : this.units;
    }

    public int getPersonalUnits() { return this.units; }
    public boolean hasCollectiveProgress() { return this.collectiveUnits >= 0; }

    public void addUnits(int amount) {
        if (amount <= 0) return;
        setUnits((int) Math.min(Integer.MAX_VALUE, (long) this.units + amount));
    }

    public void setCollectiveProgress(long units, long required) {
        this.collectiveUnits = Math.max(0, units);
        this.collectiveRequired = Math.max(1, required);
    }

    public void clearCollectiveProgress() {
        this.collectiveUnits = -1;
        this.collectiveRequired = -1;
    }

    public boolean canRecordMore() {
        return this.parent != null && !this.parent.hasSentCompletion
                && (this.parent.isGroupQuest() && this.parent.isProgressObjective(this) || !this.isCompleted());
    }

    public void setUnits(int units) {
        if (this.getParent() != null && !this.getParent().isActive()) return;
        if (this.getParent() != null && !this.getParent().isTriggered() && !this.isPartOfPrerequisites) {
            return;
        }

        int previous = this.units;
        this.units = Math.max(0, this.parent != null && this.parent.isGroupQuest()
                && this.parent.isProgressObjective(this) ? units : Math.min(units, this.requiredAmount));
        if (this.units > previous && this.parent != null && this.parent.isProgressObjective(this))
            this.parent.onNewContribution();
        if (this.getParent() != null) {
            this.getParent().markForUpdate();
        }
    }

    public int getRequiredAmount() {
        return this.collectiveRequired >= 0 ? (int) Math.min(Integer.MAX_VALUE, this.collectiveRequired) : this.requiredAmount;
    }
    public long getDisplayedUnits() { return this.collectiveUnits >= 0 ? this.collectiveUnits : this.units; }
    public long getDisplayedRequiredAmount() { return this.collectiveRequired >= 0 ? this.collectiveRequired : this.requiredAmount; }
    public int getBaseRequiredAmount() { return this.requiredAmount; }

    public boolean isCompleted() {
        return this.collectiveRequired >= 0 ? this.collectiveUnits >= this.collectiveRequired
                : this.getUnits() >= this.getRequiredAmount();
    }

    public boolean isHidden() {
        return false;
    }

    @Override
    public ObjectiveDisplayData getDisplay() {
        return this.display;
    }

    @Override
    public void writeInitialData(CompoundTag data) {
        data.putInt("units", this.units);
    }

    @Override
    public void deserialize(CompoundTag data) {
        this.units = data.getInt("units");
        boolean client = this.parent != null && this.parent.manager.isClient();
        this.collectiveUnits = client && data.contains("collective_units") ? data.getLong("collective_units") : -1;
        this.collectiveRequired = client && data.contains("collective_required") ? data.getLong("collective_required") : -1;
    }

    @Override
    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("units", this.units);
        if (this.collectiveUnits >= 0) tag.putLong("collective_units", this.collectiveUnits);
        if (this.collectiveRequired >= 0) tag.putLong("collective_required", this.collectiveRequired);
        return tag;
    }

    public boolean isReadObjective() {
        return false;
    }

    public void forceSetUnits(int units) {
        this.units = Math.max(0, Math.min(units, this.requiredAmount));
        if (this.getParent() != null) {
            this.getParent().markForUpdate();
        }
    }

    public java.util.List<Objective> getChildren() {
        return java.util.List.of();
    }
}
