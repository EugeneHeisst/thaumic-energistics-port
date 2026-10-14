package dev.eugheist.thaumicenergistics.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.block.VibrationChamberEntity;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;

/** ContainerEssentiaVibrationChamber: no slots; stored essentia, processing aspect, power and progress as data slots. */
public final class VibrationChamberMenu extends AbstractContainerMenu {
    @Nullable final VibrationChamberEntity chamber;
    final DataSlot stored = DataSlot.standalone(), amount = DataSlot.standalone(), processing = DataSlot.standalone(), power = DataSlot.standalone(), maxPower = DataSlot.standalone(), remaining = DataSlot.standalone(), total = DataSlot.standalone();
    public VibrationChamberMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) { this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()) instanceof VibrationChamberEntity e ? e : null); }
    public VibrationChamberMenu(int id, Inventory inv, @Nullable VibrationChamberEntity chamber) {
        super(ThaumicEnergistics.VIBRATION_CHAMBER_MENU.get(), id);
        this.chamber = chamber;
        for (var d : new DataSlot[]{stored, amount, processing, power, maxPower, remaining, total}) addDataSlot(d);
        sync();
    }
    void sync() {
        if (chamber == null) return;
        stored.set(EssentiaTiles.id(chamber.stored));amount.set(chamber.storedAmount);processing.set(EssentiaTiles.id(chamber.processing));
        power.set((int) (chamber.powerPerTick() * 10));maxPower.set((int) (chamber.maxPowerPerTick() * 10));
        double dilation = Math.max(20, Math.min(200, chamber.speed)) / 100.0;
        remaining.set((int) (chamber.ticksRemaining / dilation));total.set((int) (chamber.totalTicks / dilation));
    }
    @Override public void broadcastChanges() { sync(); super.broadcastChanges(); }
    @Nullable public Aspect stored() { return EssentiaTiles.byId(stored.get()); }
    public int storedAmount() { return amount.get(); }
    @Nullable public Aspect processing() { return EssentiaTiles.byId(processing.get()); }
    public float powerPerTick() { return power.get() / 10f; }
    public float powerPercent() { return 1f - (maxPower.get() > 0 ? power.get() / (float) maxPower.get() : 0f); }
    public int ticksRemaining() { return remaining.get(); }
    public float ticksPercent() { return 1f - (total.get() > 0 ? Math.min(1f, remaining.get() / (float) total.get()) : 0f); }
    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p) { return chamber != null && !chamber.isRemoved() && p.distanceToSqr(chamber.getBlockPos().getCenter()) <= 64; }
}
