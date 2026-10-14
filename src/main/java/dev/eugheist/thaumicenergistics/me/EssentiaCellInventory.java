package dev.eugheist.thaumicenergistics.me;

import java.util.ArrayList;
import java.util.List;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.item.EssentiaCellItem;

/**
 * HandlerItemEssentiaCell / HandlerItemEssentiaCellCreative: fixed type slots (first free slot or the aspect's own slot),
 * no per-type byte overhead, two essentia per byte, optional partition list; the creative cell offers 2,000,000,000 of each
 * partitioned aspect, never runs out and accepts nothing.
 */
public final class EssentiaCellInventory implements StorageCell {
    static final String TAG = "thaumicenergistics_cell", ESSENTIA = "Essentia#", PARTITION = "Partition";
    public static final long CREATIVE_AMOUNT = 2_000_000_000L;
    private final ItemStack stack;
    private final EssentiaStorageType type;
    @Nullable private final ISaveProvider container;
    private final Aspect[] aspects;
    private final long[] amounts;
    private final List<Aspect> partition = new ArrayList<>();
    private long used;
    private boolean persisted = true;

    public EssentiaCellInventory(ItemStack stack, EssentiaStorageType type, @Nullable ISaveProvider container) {
        this.stack = stack; this.type = type; this.container = container;
        aspects = new Aspect[type.maxStoredTypes]; amounts = new long[type.maxStoredTypes];
        var data = data(stack);
        for (int i = 0; i < aspects.length; i++) {
            if (!data.contains(ESSENTIA + i)) continue;
            var entry = data.getCompound(ESSENTIA + i);var aspect = Aspect.getAspect(entry.getString("AspectTag"));long amount = entry.getLong("Amount");
            if (aspect == null || amount <= 0) continue;
            aspects[i] = aspect; amounts[i] = amount; used += amount;
        }
        for (var tag : data.getList(PARTITION, 8)) { var aspect = Aspect.getAspect(tag.getAsString()); if (aspect != null && !partition.contains(aspect)) partition.add(aspect); }
    }

    static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(TAG); }

    public EssentiaStorageType type() { return type; }
    public long getTotalBytes() { return type.capacity; }
    public long getTotalEssentia() { return getTotalBytes() * EssentiaKeyType.ESSENTIA_PER_BYTE; }
    public long getUsedBytes() { return type.creative() ? 0 : (long) Math.ceil(used / (double) EssentiaKeyType.ESSENTIA_PER_BYTE); }
    public long getFreeBytes() { return getTotalBytes() - getUsedBytes(); }
    public int getTotalTypes() { return type.maxStoredTypes; }
    public int getUsedTypes() { if (type.creative()) return partition.size(); int n = 0; for (var a : aspects) if (a != null) n++; return n; }
    public List<Aspect> getPartition() { return List.copyOf(partition); }
    public boolean isPartitioned() { return !partition.isEmpty(); }
    /** Stored aspects in slot order, as getStoredEssentia. */
    public List<Object[]> getStored() {
        var out = new ArrayList<Object[]>();
        if (type.creative()) { for (var a : partition) out.add(new Object[]{a, 1L}); return out; }
        for (int i = 0; i < aspects.length; i++) if (aspects[i] != null) out.add(new Object[]{aspects[i], amounts[i]});
        return out;
    }

    /** ItemEssentiaCell.getStatusForCell: creative and types-full orange, full red, otherwise green (modern drives show empty/used). */
    @Override public CellState getStatus() {
        if (type.creative()) return CellState.TYPES_FULL;
        if (getUsedBytes() == getTotalBytes()) return CellState.FULL;
        if (getUsedTypes() == getTotalTypes()) return CellState.TYPES_FULL;
        return used == 0 ? CellState.EMPTY : CellState.NOT_EMPTY;
    }
    @Override public double getIdleDrain() { return type.idleAEPowerDrain; }
    @Override public boolean isPreferredStorageFor(AEKey what, IActionSource source) { return what instanceof EssentiaKey key && partition.contains(key.getAspect()); }

    private int slotFor(Aspect aspect) {
        int slot = -1;
        for (int i = 0; i < aspects.length; i++) {
            if (aspects[i] == null) { if (slot == -1) slot = i; continue; }
            if (aspects[i] == aspect) return i;
        }
        return slot;
    }

    @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (type.creative() || amount <= 0 || !(what instanceof EssentiaKey key)) return 0;
        var aspect = key.getAspect();
        if (isPartitioned() && !partition.contains(aspect)) return 0;
        long toStore = Math.min(amount, getTotalEssentia() - used);
        if (toStore <= 0) return 0;
        int slot = slotFor(aspect);
        if (slot == -1) return 0;
        if (mode == Actionable.MODULATE) { aspects[slot] = aspect; amounts[slot] += toStore; used += toStore; saveChanges(); }
        return toStore;
    }

    @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(what instanceof EssentiaKey key)) return 0;
        var aspect = key.getAspect();
        if (type.creative()) return partition.contains(aspect) ? amount : 0;
        int slot = slotFor(aspect);
        if (slot == -1 || aspects[slot] != aspect) return 0;
        long taken = Math.min(amounts[slot], amount);
        if (mode == Actionable.MODULATE) { amounts[slot] -= taken; used -= taken; if (amounts[slot] <= 0) { amounts[slot] = 0; aspects[slot] = null; } saveChanges(); }
        return taken;
    }

    @Override public void getAvailableStacks(KeyCounter out) {
        if (type.creative()) { for (var a : partition) out.add(EssentiaKey.of(a), CREATIVE_AMOUNT); return; }
        for (int i = 0; i < aspects.length; i++) if (aspects[i] != null) out.add(EssentiaKey.of(aspects[i]), amounts[i]);
    }

    /** Cell workbench partition list (addAspectToPartitionList / clearPartitioning). */
    public void setPartition(List<Aspect> list) { partition.clear(); for (var a : list) if (a != null && !partition.contains(a)) partition.add(a); saveChanges(); }

    private void saveChanges() { persisted = false; if (container != null) container.saveChanges(); else persist(); }

    @Override public void persist() {
        if (persisted) return;
        var data = new CompoundTag();
        for (int i = 0; i < aspects.length; i++) if (aspects[i] != null && amounts[i] > 0) { var entry = new CompoundTag(); entry.putString("AspectTag", aspects[i].getTag()); entry.putLong("Amount", amounts[i]); data.put(ESSENTIA + i, entry); }
        if (!partition.isEmpty()) { var list = new ListTag(); for (var a : partition) list.add(StringTag.valueOf(a.getTag())); data.put(PARTITION, list); }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> { if (data.isEmpty()) tag.remove(TAG); else tag.put(TAG, data); });
        persisted = true;
    }

    @Override public Component getDescription() { return stack.getHoverName(); }
    @Override public boolean canFitInsideCell() { return used == 0 && partition.isEmpty(); }

    @Nullable public static EssentiaCellInventory of(ItemStack stack, @Nullable ISaveProvider container) {
        return stack.getItem() instanceof EssentiaCellItem cell ? new EssentiaCellInventory(stack, cell.type, container) : null;
    }
}
