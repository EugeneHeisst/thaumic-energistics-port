package dev.eugheist.thaumicenergistics.part;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartItem;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManagerBuilder;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import appeng.parts.automation.UpgradeablePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.tc4modern.api.AspectContainers;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;
import dev.eugheist.thaumicenergistics.menu.EssentiaIOBusMenu;

/**
 * ThEPartEssentiaIOBus_Base: a 3x3 aspect filter whose centre is always open, the cross unlocked by the first capacity card
 * and the corners by the second; moves 4 essentia per second plus 8 per speed card (at most 64 per operation) every 10-40
 * ticks; a redstone card enables the redstone mode, and pulse mode works on every change of the incoming signal.
 */
public abstract class EssentiaIOBusPart extends UpgradeablePart implements IGridTickable, PartMenus.Host {
    public static final int FILTER_SIZE = 9, CENTER = 4;
    public static final int[] TIER1 = {1, 3, 5, 7}, TIER2 = {0, 2, 6, 8};
    /** GuiEssentiaIO WIDGET_CONFIG_BYTES: capacity cards needed for each filter slot. */
    public static final byte[] SLOT_TIER = {2, 1, 2, 1, 0, 1, 2, 1, 2};
    protected final Aspect[] filter = new Aspect[FILTER_SIZE];
    protected final IActionSource source = new MachineSource(this);
    private boolean lastRedstone;

    protected EssentiaIOBusPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.7).addService(IGridTickable.class, this);
    }

    @Override protected void registerSettings(IConfigManagerBuilder builder) { super.registerSettings(builder); builder.registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE); }
    @Override public RedstoneMode getRSMode() { return getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED); }
    public void setRSMode(RedstoneMode mode) { getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, mode); }
    @Override protected int getUpgradeSlots() { return 4; }
    public int filterSize() { return Math.min(2, getInstalledUpgrades(AEItems.CAPACITY_CARD)); }
    public boolean redstoneControlled() { return isUpgradedWith(AEItems.REDSTONE_CARD); }
    public int speedCards() { return getInstalledUpgrades(AEItems.SPEED_CARD); }
    public boolean slotOpen(int index) { return SLOT_TIER[index] <= filterSize(); }
    @Nullable public Aspect getAspect(int index) { return filter[index]; }
    public boolean isVoidAllowed() { return false; }
    public void toggleVoid() {}

    /** resizeAvailableArray: removing capacity cards empties the slots they unlocked. */
    @Override public void upgradesChanged() {
        for (int i = 0; i < FILTER_SIZE; i++) if (!slotOpen(i)) filter[i] = null;
        getHost().markForSave();
    }

    public void setAspect(int index, @Nullable Aspect aspect) { if (index >= 0 && index < FILTER_SIZE && slotOpen(index)) { filter[index] = aspect; getHost().markForSave(); } }
    /** addFilteredAspectFromItemstack: first free open slot, centre first; true when the aspect is (now) filtered. */
    public boolean addAspect(@Nullable Aspect aspect) {
        if (aspect == null) return false;
        for (var a : filter) if (a == aspect) return true;
        int[] order = {CENTER, 1, 3, 5, 7, 0, 2, 6, 8};
        for (int i : order) if (slotOpen(i) && filter[i] == null) { setAspect(i, aspect); return true; }
        return false;
    }

    @Nullable protected AspectContainers.Container facing() {
        var level = getLevel();
        if (level == null) return null;
        BlockPos pos = getBlockEntity().getBlockPos().relative(getSide());
        return level.isLoaded(pos) ? AspectContainers.find(level, pos) : null;
    }

    int transferPerSecond() { return 4 + speedCards() * 8; }

    /** canDoWork: with a redstone card, high/low need/forbid a signal and pulse mode never works on its own. */
    boolean canDoWork() {
        if (!redstoneControlled()) return true;
        return switch (getRSMode()) {
            case HIGH_SIGNAL -> getHost().hasRedstone();
            case LOW_SIGNAL -> !getHost().hasRedstone();
            case SIGNAL_PULSE -> false;
            default -> true;
        };
    }

    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(10, 40, false); }
    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!getMainNode().isActive() || !canDoWork()) return TickRateModulation.IDLE;
        int amount = (int) (transferPerSecond() * (ticksSinceLastCall / 20f));
        amount = Math.max(1, Math.min(64, amount));
        return doWork(node.getGrid(), amount) ? TickRateModulation.URGENT : TickRateModulation.IDLE;
    }

    @Override public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        if (isClientSide() || !redstoneControlled() || getRSMode() != RedstoneMode.SIGNAL_PULSE) return;
        boolean power = getHost().hasRedstone();
        if (power != lastRedstone) {
            lastRedstone = power;
            getMainNode().ifPresent((grid, node) -> doWork(grid, transferPerSecond()));
        }
    }
    @Override public void addToWorld() { super.addToWorld(); lastRedstone = getHost().hasRedstone(); }

    protected abstract boolean doWork(IGrid grid, int amount);

    @Override public float getCableConnectionLength(AECableType cable) { return 5; }
    @Override public boolean onUseWithoutItem(Player player, Vec3 pos) { return PartMenus.open(this, player); }
    @Override public boolean onUseItemOn(ItemStack held, Player player, InteractionHand hand, Vec3 pos) { return super.onUseItemOn(held, player, hand, pos) || PartMenus.open(this, player); }
    @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv) { return new EssentiaIOBusMenu(id, inv, this); }

    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        for (int i = 0; i < FILTER_SIZE; i++) filter[i] = data.contains("AspectFilter#" + i) ? Aspect.getAspect(data.getString("AspectFilter#" + i)) : null;
    }
    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        for (int i = 0; i < FILTER_SIZE; i++) if (filter[i] != null) data.putString("AspectFilter#" + i, filter[i].getTag());
    }

    static boolean noFilters(Aspect[] filter) { for (var a : filter) if (a != null) return false; return true; }
    static boolean contains(Aspect[] filter, Aspect aspect) { for (var a : filter) if (a == aspect) return true; return false; }
    static long extract(AspectContainers.Container c, int n, Aspect a, boolean sim) { return EssentiaTiles.extract(c, n, a, sim); }
}
