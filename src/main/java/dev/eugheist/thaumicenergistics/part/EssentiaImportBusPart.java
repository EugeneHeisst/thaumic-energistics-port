package dev.eugheist.thaumicenergistics.part;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.storage.StorageHelper;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;

/** PartEssentiaImportBus: pulls the most plentiful aspect of the facing container into the network, honouring the filter. */
public class EssentiaImportBusPart extends EssentiaIOBusPart {
    @PartModels public static final ResourceLocation MODEL_BASE = ThaumicEnergistics.id("part/essentia_import_bus_base");
    @PartModels public static final ResourceLocation MODEL_OFF = ThaumicEnergistics.id("part/essentia_import_bus_off");
    @PartModels public static final ResourceLocation MODEL_ON = ThaumicEnergistics.id("part/essentia_import_bus_on");
    @PartModels public static final ResourceLocation MODEL_HAS_CHANNEL = ThaumicEnergistics.id("part/essentia_import_bus_has_channel");
    static final IPartModel OFF = new PartModel(MODEL_BASE, MODEL_OFF), ON = new PartModel(MODEL_BASE, MODEL_ON), HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);

    public EssentiaImportBusPart(IPartItem<?> partItem) { super(partItem); }

    /** aspectTransferAllowed: anything when no filter is set. */
    public boolean allowed(Aspect aspect) { return aspect != null && (noFilters(filter) || contains(filter, aspect)); }

    @Override protected boolean doWork(IGrid grid, int amount) {
        var c = facing();
        if (c == null) return false;
        var aspect = EssentiaTiles.aspectIn(c);
        if (aspect == null || !allowed(aspect)) return false;
        long drained = extract(c, amount, aspect, true);
        if (drained <= 0) return false;
        var inv = grid.getStorageService().getInventory();var energy = grid.getEnergyService();var key = EssentiaKey.of(aspect);
        long accepted = StorageHelper.poweredInsert(energy, inv, key, drained, source, Actionable.SIMULATE);
        if (accepted <= 0) return false;
        // The original drained the full request even when the network took less; only what was stored leaves the container here.
        long taken = extract(c, (int) accepted, aspect, false);
        if (taken > 0) StorageHelper.poweredInsert(energy, inv, key, taken, source, Actionable.MODULATE);
        return taken > 0;
    }

    @Override public void getBoxes(IPartCollisionHelper helper) { helper.addBox(4, 4, 14, 12, 12, 16); helper.addBox(5, 5, 12, 11, 11, 14); helper.addBox(6, 6, 11, 10, 10, 12); }
    @Override public IPartModel getStaticModels() { return isActive() ? HAS_CHANNEL : isPowered() ? ON : OFF; }
}
