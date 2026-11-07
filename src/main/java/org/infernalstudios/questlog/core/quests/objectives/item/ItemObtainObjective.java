package org.infernalstudios.questlog.core.quests.objectives.item;

import com.evandev.triggers.event.events.TriggerPlayerEvent;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public class ItemObtainObjective extends AbstractItemObjective {

    private int ticksUntilCheck = 0;

    public ItemObtainObjective(JsonObject definition) {
        super(definition);
    }

    @Override
    public void registerEventListeners() {
        super.registerEventListeners();
        this.addListener(TriggerPlayerEvent.Tick.class, this::onPlayerTick);
    }

    private void onPlayerTick(TriggerPlayerEvent.Tick event) {
        if (this.getParent() == null || !this.canRecordMore()) return;

        if (event.player instanceof ServerPlayer player && this.getParent().manager.player.equals(player) && --ticksUntilCheck <= 0) {

            int currentCount = countInventory(player);
            if (this.getParent().isGroupQuest() ? currentCount != this.getPersonalUnits()
                    : currentCount > this.getPersonalUnits()) this.setUnits(currentCount);

            this.ticksUntilCheck = 10;
        }
    }

    public int countInventory(ServerPlayer player) {
        long count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (this.test(stack)) count += stack.getCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    public int countOfflineInventory(MinecraftServer server, UUID player) {
        Path path = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player + ".dat");
        try {
            var data = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            if (data == null) return 0;
            long count = 0;
            for (Tag item : data.getList("Inventory", Tag.TAG_COMPOUND)) {
                var stackData = (net.minecraft.nbt.CompoundTag) item;
                int slot = stackData.getByte("Slot") & 255;
                if (slot >= 36) continue;
                ItemStack stack = ItemStack.parseOptional(server.registryAccess(), stackData);
                if (this.test(stack)) count += stack.getCount();
            }
            return (int) Math.min(Integer.MAX_VALUE, count);
        } catch (IOException | RuntimeException failure) {
            return 0;
        }
    }
}
