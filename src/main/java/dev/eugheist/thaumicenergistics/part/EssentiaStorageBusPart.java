package dev.eugheist.thaumicenergistics.part;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.ISubMenuHost;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPriorityHost;
import appeng.items.parts.PartModels;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.UpgradeablePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.tc4modern.api.AspectContainers;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;
import dev.eugheist.thaumicenergistics.menu.EssentiaStorageBusMenu;

/**
 * PartEssentiaStorageBus + HandlerEssentiaStorageBusDuality: exposes the facing essentia container (jar, reservoir...) to the
 * network, or, facing an ME interface or matter condenser, that block's ME storage limited to essentia. Nine aspect filters
 * (an inverter card turns them into a blacklist), priority, void mode for void jars, 0.3 AE per essentia moved.
 */
public class EssentiaStorageBusPart extends UpgradeablePart implements IStorageProvider, IPriorityHost, PartMenus.Host {
    @PartModels public static final ResourceLocation MODEL_BASE = ThaumicEnergistics.id("part/essentia_storage_bus_base");
    @PartModels public static final ResourceLocation MODEL_OFF = ThaumicEnergistics.id("part/essentia_storage_bus_off");
    @PartModels public static final ResourceLocation MODEL_ON = ThaumicEnergistics.id("part/essentia_storage_bus_on");
    @PartModels public static final ResourceLocation MODEL_HAS_CHANNEL = ThaumicEnergistics.id("part/essentia_storage_bus_has_channel");
    static final IPartModel OFF = new PartModel(MODEL_BASE, MODEL_OFF), ON = new PartModel(MODEL_BASE, MODEL_ON), HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_HAS_CHANNEL);
    public static final int FILTER_SIZE = 9;
    final Aspect[] filter = new Aspect[FILTER_SIZE];
    int priority;
    boolean voidAllowed;
    final Handler handler = new Handler();

    public EssentiaStorageBusPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(1.0).addService(IStorageProvider.class, this);
    }

    @Override protected int getUpgradeSlots() { return 1; }
    @Override public void upgradesChanged() { remount(); }
    boolean inverted() { return isUpgradedWith(AEItems.INVERTER_CARD); }
    void remount() { if (!isClientSide()) IStorageProvider.requestUpdate(getMainNode()); }

    @Nullable public Aspect getAspect(int i) { return filter[i]; }
    public void setAspect(int i, @Nullable Aspect a) { if (i >= 0 && i < FILTER_SIZE) { filter[i] = a; getHost().markForSave(); remount(); } }
    public boolean addAspect(@Nullable Aspect a) {
        if (a == null) return false;
        for (var f : filter) if (f == a) return true;
        for (int i = 0; i < FILTER_SIZE; i++) if (filter[i] == null) { setAspect(i, a); return true; }
        return false;
    }
    public boolean isVoidAllowed() { return voidAllowed; }
    public void setVoidAllowed(boolean v) { voidAllowed = v; getHost().markForSave(); }
    @Override public int getPriority() { return priority; }
    @Override public void setPriority(int p) { priority = p; getHost().markForSave(); remount(); }

    @Override public void mountInventories(IStorageMounts mounts) { if (getMainNode().isActive()) mounts.mount(handler, priority); }
    @Override public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) { remount(); }
    @Override protected void onMainNodeStateChanged(appeng.api.networking.IGridNodeListener.State reason) { super.onMainNodeStateChanged(reason); remount(); }

    /** extractPowerForEssentiaTransfer: 0.3 AE per essentia. */
    boolean power(long essentia, Actionable mode) {
        var grid = getMainNode().getGrid();
        if (grid == null) return false;
        double drain = 0.3 * essentia;
        return grid.getEnergyService().extractAEPower(drain, mode, PowerMultiplier.CONFIG) >= drain - 0.0001;
    }

    @Nullable AspectContainers.Container container() {
        var level = getLevel();
        if (level == null) return null;
        var pos = getBlockEntity().getBlockPos().relative(getSide());
        return level.isLoaded(pos) ? AspectContainers.find(level, pos) : null;
    }
    @Nullable MEStorage meStorage() {
        var level = getLevel();
        if (level == null) return null;
        var pos = getBlockEntity().getBlockPos().relative(getSide());
        return level.isLoaded(pos) ? level.getCapability(AECapabilities.ME_STORAGE, pos, getSide().getOpposite()) : null;
    }

    final class Handler implements MEStorage {
        boolean busy;
        boolean allowAny() { for (var a : filter) if (a != null) return false; return true; }
        boolean canTransfer(Aspect a) { if (allowAny()) return true; boolean listed = false; for (var f : filter) if (f == a) listed = true; return listed != inverted(); }

        @Override public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
            if (!(what instanceof EssentiaKey key)) return false;
            if (!inverted()) for (var f : filter) if (f == key.getAspect()) return true;
            var c = container();
            return c != null && c.getAspects().getAmount(key.getAspect()) > 0;
        }

        @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (busy || !(what instanceof EssentiaKey key) || !canTransfer(key.getAspect())) return 0;
            var c = container();
            if (c != null) {
                if (!c.canInject() || !c.accepts(key.getAspect())) return 0;
                boolean canVoid = voidAllowed && c.voids();
                long filled = canVoid ? amount : EssentiaTiles.inject(c, (int) Math.min(Integer.MAX_VALUE, amount), key.getAspect(), true);
                if (filled <= 0 || !power(filled, Actionable.SIMULATE)) return 0;
                if (mode == Actionable.MODULATE) { EssentiaTiles.inject(c, (int) Math.min(Integer.MAX_VALUE, filled), key.getAspect(), false); power(filled, Actionable.MODULATE); }
                return filled;
            }
            var me = meStorage();
            if (me == null) return 0;
            busy = true;
            try { return me.insert(what, amount, mode, source); } finally { busy = false; }
        }

        @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (busy || !(what instanceof EssentiaKey key) || !canTransfer(key.getAspect())) return 0;
            var c = container();
            if (c != null) {
                long drained = EssentiaTiles.extract(c, (int) Math.min(Integer.MAX_VALUE, amount), key.getAspect(), true);
                if (drained <= 0 || !power(drained, Actionable.SIMULATE)) return 0;
                if (mode == Actionable.MODULATE) { drained = EssentiaTiles.extract(c, (int) drained, key.getAspect(), false); power(drained, Actionable.MODULATE); }
                return drained;
            }
            var me = meStorage();
            if (me == null) return 0;
            busy = true;
            try { return me.extract(what, amount, mode, source); } finally { busy = false; }
        }

        @Override public void getAvailableStacks(KeyCounter out) {
            if (busy) return;
            var c = container();
            if (c != null) {
                if (!c.canExtract()) return;
                var list = c.getAspects();
                for (var a : list.getAspects()) if (a != null && list.getAmount(a) > 0 && canTransfer(a)) out.add(EssentiaKey.of(a), list.getAmount(a));
                return;
            }
            var me = meStorage();
            if (me == null) return;
            busy = true;
            try { var all = new KeyCounter(); me.getAvailableStacks(all); for (var e : all) if (e.getKey() instanceof EssentiaKey k && canTransfer(k.getAspect())) out.add(k, e.getLongValue()); }
            finally { busy = false; }
        }
        @Override public Component getDescription() { return getPartItem().asItem().getDescription(); }
    }

    @Override public float getCableConnectionLength(AECableType cable) { return 3; }
    @Override public void getBoxes(IPartCollisionHelper helper) { helper.addBox(1, 1, 15, 15, 15, 16); helper.addBox(4, 4, 14, 12, 12, 15); helper.addBox(5, 5, 13, 11, 11, 14); }
    @Override public IPartModel getStaticModels() { return isActive() ? HAS_CHANNEL : isPowered() ? ON : OFF; }

    @Override public boolean onUseWithoutItem(Player player, Vec3 pos) { return PartMenus.open(this, player); }
    @Override public boolean onUseItemOn(ItemStack held, Player player, InteractionHand hand, Vec3 pos) { return super.onUseItemOn(held, player, hand, pos) || PartMenus.open(this, player); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv) { return new EssentiaStorageBusMenu(id, inv, this); }
    public void openPriority(Player player) { MenuOpener.open(PriorityMenu.TYPE, player, MenuLocators.forPart(this)); }
    @Override public void returnToMainMenu(Player player, ISubMenu subMenu) { PartMenus.open(this, player); }
    @Override public ItemStack getMainMenuIcon() { return new ItemStack(getPartItem()); }

    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        priority = data.getInt("Priority");voidAllowed = data.getBoolean("IsVoidAllowed");
        for (int i = 0; i < FILTER_SIZE; i++) filter[i] = data.contains("FilterAspects#" + i) ? Aspect.getAspect(data.getString("FilterAspects#" + i)) : null;
    }
    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        if (priority != 0) data.putInt("Priority", priority);
        if (voidAllowed) data.putBoolean("IsVoidAllowed", true);
        for (int i = 0; i < FILTER_SIZE; i++) if (filter[i] != null) data.putString("FilterAspects#" + i, filter[i].getTag());
    }
}
