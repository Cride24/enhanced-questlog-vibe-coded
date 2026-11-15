package org.infernalstudios.questlog.core.quests.rewards;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.infernalstudios.questlog.util.JsonUtils;

/** The authored text is copied into player progress only when the reward is delivered. */
public class InformationReward extends Reward {
    private final String information;
    private String revealed = "";

    public InformationReward(JsonObject definition) {
        super(definition);
        this.information = JsonUtils.getOrDefault(definition, "information", "");
    }

    public String revealed() { return hasRewarded() ? revealed : ""; }

    @Override
    public void applyReward(ServerPlayer player) {
        if (getParent() == null || !getParent().isActive()) return;
        revealed = information;
        super.applyReward(player);
    }

    @Override
    public void revokeReward() {
        super.revokeReward();
        revealed = "";
    }

    @Override
    public CompoundTag serialize() {
        CompoundTag tag = super.serialize();
        if (hasRewarded()) tag.putString("revealed_information", revealed);
        return tag;
    }

    @Override
    public void deserialize(CompoundTag data) {
        super.deserialize(data);
        revealed = hasRewarded() ? data.getString("revealed_information") : "";
    }
}
