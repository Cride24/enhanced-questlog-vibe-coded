package org.infernalstudios.questlog.core.quests.rewards;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.core.quests.Quest;
import org.infernalstudios.questlog.core.quests.display.RewardDisplayData;
import org.infernalstudios.questlog.core.quests.display.WithDisplayData;
import org.infernalstudios.questlog.util.JsonUtils;
import org.infernalstudios.questlog.util.NbtSaveable;
import org.jetbrains.annotations.Nullable;

public abstract class Reward implements NbtSaveable, WithDisplayData<RewardDisplayData> {

    private final RewardDisplayData display;
    private final boolean autoClaim;
    private final int beneficiaryDistance;
    private final boolean onePerGroup;
    private final int referenceObjective;
    private String allocationId = "";
    private int allocationState = -1;
    private long lotteryDeadline;
    private boolean lotteryCandidate;
    private boolean lotteryAnswered;
    private int allocationPolicy;
    private boolean lotteryTie;
    private java.util.List<java.util.UUID> allocationBeneficiaries = java.util.List.of();
    private java.util.UUID allocationWinner;
    private Quest parent;
    private boolean rewarded = false;
    private boolean eligible = true;
    @Nullable
    private ChoiceReward container;

    public Reward(JsonObject definition) {
        if (definition.has("auto_claim")) {
            if (!definition.get("auto_claim").isJsonPrimitive()) {
                throw new IllegalStateException("Reward auto_claim must be a boolean");
            }
            this.autoClaim = JsonUtils.getBoolean(definition, "auto_claim");
        } else {
            this.autoClaim = false;
        }
        this.beneficiaryDistance = Math.max(0, JsonUtils.getOrDefault(definition, "beneficiary_distance", 0));
        this.onePerGroup = JsonUtils.getOrDefault(definition, "one_per_group", false);
        this.referenceObjective = JsonUtils.getOrDefault(definition, "reference_objective", -1);

        this.display = new RewardDisplayData(definition);
        this.display.setReward(this);
    }

    @Nullable
    public Quest getParent() {
        return this.parent;
    }

    public final void setParent(Quest parent) {
        this.parent = parent;
    }

    @Override
    @Nullable
    public RewardDisplayData getDisplay() {
        return this.display;
    }

    public boolean isAutoClaim() {
        return this.autoClaim;
    }

    public int beneficiaryDistance() { return this.beneficiaryDistance; }
    public boolean onePerGroup() { return this.onePerGroup; }
    public int referenceObjective() { return this.referenceObjective; }
    public String allocationId() { return this.allocationId; }
    public void setAllocationId(String id) { this.allocationId = id; }
    public int allocationState() { return this.allocationState; }
    public long lotteryDeadline() { return this.lotteryDeadline; }
    public boolean lotteryCandidate() { return this.lotteryCandidate; }
    public boolean lotteryAnswered() { return this.lotteryAnswered; }
    public int allocationPolicy() { return this.allocationPolicy; }
    public boolean lotteryTie() { return this.lotteryTie; }
    public java.util.List<java.util.UUID> allocationBeneficiaries() { return this.allocationBeneficiaries; }
    public java.util.UUID allocationWinner() { return this.allocationWinner; }
    public void setAllocationState(int state, long deadline, boolean candidate, boolean answered,
                                   int policy, java.util.Set<java.util.UUID> beneficiaries, java.util.UUID winner, boolean tie) {
        this.allocationState = state;
        this.lotteryDeadline = deadline;
        this.lotteryCandidate = candidate;
        this.lotteryAnswered = answered;
        this.allocationPolicy = policy;
        this.lotteryTie = tie;
        this.allocationBeneficiaries = java.util.List.copyOf(beneficiaries);
        this.allocationWinner = winner;
    }

    public void applyReward(ServerPlayer player) {
        if (this.getParent() != null && !this.getParent().isActive()) return;
        this.rewarded = true;
        if (this.getParent() != null) {
            this.getParent().markForUpdate();
        }
    }

    public boolean canDeliver(ServerPlayer player) { return true; }

    public void revokeReward() {
        this.rewarded = false;
        this.eligible = true;
        this.allocationId = "";
        this.setAllocationState(-1, 0, false, false, 0, java.util.Set.of(), null, false);
        if (this.getParent() != null) {
            this.getParent().markForUpdate();
        }
    }

    public boolean hasRewarded() {
        return this.rewarded;
    }

    public boolean isEligible() { return this.eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }

    @Override
    public void writeInitialData(CompoundTag data) {
        data.putBoolean("rewarded", false);
        data.putBoolean("eligible", true);
        data.putString("allocation_id", "");
        data.putInt("allocation_state", -1);
    }

    @Override
    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("rewarded", this.rewarded);
        tag.putBoolean("eligible", this.eligible);
        tag.putString("allocation_id", this.allocationId);
        tag.putInt("allocation_state", this.allocationState);
        tag.putLong("lottery_deadline", this.lotteryDeadline);
        tag.putBoolean("lottery_candidate", this.lotteryCandidate);
        tag.putBoolean("lottery_answered", this.lotteryAnswered);
        tag.putInt("allocation_policy", this.allocationPolicy);
        tag.putBoolean("lottery_tie", this.lotteryTie);
        if (this.allocationWinner != null) tag.putUUID("allocation_winner", this.allocationWinner);
        net.minecraft.nbt.ListTag beneficiaries = new net.minecraft.nbt.ListTag();
        for (java.util.UUID id : this.allocationBeneficiaries) {
            CompoundTag row = new CompoundTag(); row.putUUID("id", id); beneficiaries.add(row);
        }
        tag.put("allocation_beneficiaries", beneficiaries);
        return tag;
    }

    @Override
    public void deserialize(CompoundTag data) {
        this.rewarded = data.getBoolean("rewarded");
        this.eligible = !data.contains("eligible") || data.getBoolean("eligible");
        this.allocationId = data.getString("allocation_id");
        this.allocationState = data.contains("allocation_state") ? data.getInt("allocation_state") : -1;
        this.lotteryDeadline = data.getLong("lottery_deadline");
        this.lotteryCandidate = data.getBoolean("lottery_candidate");
        this.lotteryAnswered = data.getBoolean("lottery_answered");
        this.allocationPolicy = data.getInt("allocation_policy");
        this.lotteryTie = data.getBoolean("lottery_tie");
        this.allocationWinner = data.hasUUID("allocation_winner") ? data.getUUID("allocation_winner") : null;
        java.util.List<java.util.UUID> beneficiaries = new java.util.ArrayList<>();
        for (net.minecraft.nbt.Tag item : data.getList("allocation_beneficiaries", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) item;
            if (row.hasUUID("id")) beneficiaries.add(row.getUUID("id"));
        }
        this.allocationBeneficiaries = beneficiaries;
    }

    public void setContainer(@Nullable ChoiceReward container) {
        this.container = container;
    }

    @Nullable
    public ChoiceReward getContainer() {
        return this.container;
    }

    public boolean isSelected() {
        if (this.container != null) {
            return this.container.isChoiceSelected(this);
        }
        return false;
    }

    public void setRewarded(boolean rewarded) {
        this.rewarded = rewarded;
    }
}
