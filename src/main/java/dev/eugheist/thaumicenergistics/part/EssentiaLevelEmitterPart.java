package dev.eugheist.thaumicenergistics.part;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.automation.AbstractLevelEmitterPart;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.menu.EssentiaLevelEmitterMenu;

/**
 * PartEssentiaLevelEmitter: watches one aspect; in high mode emits while the network holds at least the threshold, in low
 * mode while it holds less. Threshold 0..9 999 999 999, 0.3 AE/t idle.
 */
public class EssentiaLevelEmitterPart extends AbstractLevelEmitterPart implements PartMenus.Host {
    @PartModels public static final ResourceLocation MODEL_OFF = ThaumicEnergistics.id("part/essentia_level_emitter_off");
    @PartModels public static final ResourceLocation MODEL_ON = ThaumicEnergistics.id("part/essentia_level_emitter_on");
    static final IPartModel OFF = new PartModel(MODEL_OFF), ON = new PartModel(MODEL_ON);
    public static final long MAX_THRESHOLD = 9999999999L;
    @Nullable private Aspect tracked;
    @Nullable private IStackWatcher watcher;

    public EssentiaLevelEmitterPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.3).addService(IStorageWatcherNode.class, new IStorageWatcherNode() {
            @Override public void updateWatcher(IStackWatcher newWatcher) { watcher = newWatcher; configureWatchers(); }
            @Override public void onStackChange(AEKey what, long amount) { if (tracked != null && what.equals(EssentiaKey.of(tracked))) { lastReportedValue = amount; updateState(); } }
        });
    }

    @Override protected int getUpgradeSlots() { return 0; }
    @Nullable public Aspect getTracked() { return tracked; }
    public void setTracked(@Nullable Aspect aspect) { if (aspect != tracked) { tracked = aspect; getHost().markForSave(); configureWatchers(); } }
    public long getThreshold() { return getReportingValue(); }
    public void setThreshold(long value) { setReportingValue(Math.max(0, Math.min(MAX_THRESHOLD, value))); getHost().markForSave(); }
    public RedstoneMode getMode() { return getConfigManager().getSetting(Settings.REDSTONE_EMITTER); }
    public void toggleMode() { getConfigManager().putSetting(Settings.REDSTONE_EMITTER, getMode() == RedstoneMode.HIGH_SIGNAL ? RedstoneMode.LOW_SIGNAL : RedstoneMode.HIGH_SIGNAL); }

    @Override protected void configureWatchers() {
        if (watcher != null) { watcher.reset(); if (tracked != null) watcher.add(EssentiaKey.of(tracked)); }
        var grid = getMainNode().getGrid();
        lastReportedValue = tracked != null && grid != null ? grid.getStorageService().getCachedInventory().get(EssentiaKey.of(tracked)) : 0;
        updateState();
    }
    @Override protected boolean hasDirectOutput() { return false; }
    @Override protected boolean getDirectOutput() { return false; }
    @Override protected boolean isLevelEmitterOn() {
        if (isClientSide()) return super.isLevelEmitterOn();
        if (!getMainNode().isActive()) return false;
        return getMode() == RedstoneMode.LOW_SIGNAL ? lastReportedValue < getReportingValue() : lastReportedValue >= getReportingValue();
    }

    @Override public IPartModel getStaticModels() { return isLevelEmitterOn() ? ON : OFF; }
    @Override public boolean onUseWithoutItem(Player player, Vec3 pos) { return PartMenus.open(this, player); }
    @Override public boolean onUseItemOn(ItemStack held, Player player, InteractionHand hand, Vec3 pos) { return super.onUseItemOn(held, player, hand, pos) || PartMenus.open(this, player); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv) { return new EssentiaLevelEmitterMenu(id, inv, this); }

    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) { super.readFromNBT(data, registries); tracked = data.contains("aspect") ? Aspect.getAspect(data.getString("aspect")) : null; }
    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) { super.writeToNBT(data, registries); if (tracked != null) data.putString("aspect", tracked.getTag()); }
}
