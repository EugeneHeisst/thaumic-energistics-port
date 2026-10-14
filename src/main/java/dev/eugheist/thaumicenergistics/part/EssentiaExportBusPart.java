package dev.eugheist.thaumicenergistics.part;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.storage.StorageHelper;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;

/**
 * PartEssentiaExportBus: fills the facing container with the first filtered aspect it can take. With void mode on, a full
 * void jar of the same aspect keeps accepting (the essentia is destroyed).
 */
public class EssentiaExportBusPart extends EssentiaIOBusPart {
    @PartModels public static final ResourceLocation MODEL_BASE = ThaumicEnergistics.id("part/essentia_export_bus_base");
    @PartModels public static final ResourceLocation MODEL_OFF = ThaumicEnergistics.id("part/essentia_export_bus_off");
    @PartModels public static final ResourceLocation MODEL_ON = ThaumicEnergistics.id("part/essentia_export_bus_on");
    @PartModels public static final ResourceLocation MODEL_HAS_CHANNEL = ThaumicEnergistics.id("part/essentia_export_bus_has_channel");
    static final IPartModel OFF = new PartModel(MODEL_BASE, MODEL_OFF), ON = new PartModel(MODEL_BASE, MODEL_ON), HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);
    private boolean voidAllowed;

    public EssentiaExportBusPart(IPartItem<?> partItem) { super(partItem); }

    @Override public boolean isVoidAllowed() { return voidAllowed; }
    @Override public void toggleVoid() { voidAllowed = !voidAllowed; getHost().markForSave(); }

    @Override protected boolean doWork(IGrid grid, int amount) {
        var c = facing();
        if (c == null) return false;
        var inv = grid.getStorageService().getInventory();var energy = grid.getEnergyService();
        for (var aspect : filter) {
            if (aspect == null) continue;
            if (EssentiaTiles.inject(c, 1, aspect, true) <= 0 && !(voidAllowed && EssentiaTiles.aspectIn(c) == aspect)) continue;
            var key = EssentiaKey.of(aspect);
            long available = StorageHelper.poweredExtraction(energy, inv, key, amount, source, Actionable.SIMULATE);
            if (available <= 0) continue;
            long filled = voidAllowed && c.voids() ? available : EssentiaTiles.inject(c, (int) available, aspect, true);
            if (filled <= 0) continue;
            long actual = EssentiaTiles.inject(c, (int) filled, aspect, false);
            if (!voidAllowed) filled = actual;
            StorageHelper.poweredExtraction(energy, inv, key, filled, source, Actionable.MODULATE);
            return true;
        }
        return false;
    }

    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) { super.readFromNBT(data, registries); voidAllowed = data.getBoolean("IsVoidAllowed"); }
    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) { super.writeToNBT(data, registries); if (voidAllowed) data.putBoolean("IsVoidAllowed", true); }

    @Override public void getBoxes(IPartCollisionHelper helper) { helper.addBox(4, 4, 12, 12, 12, 13.5); helper.addBox(5, 5, 13.5, 11, 11, 15); helper.addBox(6, 6, 15, 10, 10, 16); }
    @Override public IPartModel getStaticModels() { return isActive() ? HAS_CHANNEL : isPowered() ? ON : OFF; }
}
