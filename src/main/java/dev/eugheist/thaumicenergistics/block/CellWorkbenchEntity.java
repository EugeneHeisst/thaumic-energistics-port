package dev.eugheist.thaumicenergistics.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.item.EssentiaCellItem;
import dev.eugheist.thaumicenergistics.me.EssentiaCellInventory;

/** TileEssentiaCellWorkbench: one essentia cell; partition edits go straight into the cell item. */
public final class CellWorkbenchEntity extends BlockEntity {
    public final SimpleContainer inventory = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return stack.getItem() instanceof EssentiaCellItem; }
        @Override public int getMaxStackSize() { return 1; }
    };
    public CellWorkbenchEntity(BlockPos pos, BlockState state) { super(ThaumicEnergistics.CELL_WORKBENCH_ENTITY.get(), pos, state); inventory.addListener(c -> setChanged()); }

    public ItemStack cell() { return inventory.getItem(0); }
    public List<Aspect> partition() { var inv = EssentiaCellInventory.of(cell(), null); return inv == null ? List.of() : inv.getPartition(); }
    private void edit(java.util.function.UnaryOperator<List<Aspect>> change) {
        var inv = EssentiaCellInventory.of(cell(), null);
        if (inv == null) return;
        inv.setPartition(change.apply(new ArrayList<>(inv.getPartition())));
        inventory.setChanged();
    }
    /** addAspectToPartitionList: appended when absent. */
    public boolean add(Aspect aspect) { if (cell().isEmpty() || partition().contains(aspect) || partition().size() >= 63) return false; edit(list -> { list.add(aspect); return list; }); return true; }
    public void remove(Aspect aspect) { edit(list -> { list.remove(aspect); return list; }); }
    /** replaceAspectInPartitionList: the new aspect takes the old one's place unless it is already partitioned. */
    public void replace(Aspect from, Aspect to) { edit(list -> { int i = list.indexOf(from); if (i >= 0 && !list.contains(to)) list.set(i, to); return list; }); }
    public void clear() { edit(list -> new ArrayList<>()); }
    /** partitionToCellContents: partition = aspects currently stored, in slot order. */
    public void toContents() { var inv = EssentiaCellInventory.of(cell(), null); if (inv == null) return; var stored = new ArrayList<Aspect>(); for (var entry : inv.getStored()) stored.add((Aspect) entry[0]); edit(list -> stored); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag, registries); if (!cell().isEmpty()) tag.put("EssentiaCell", cell().save(registries)); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag, registries); inventory.setItem(0, tag.contains("EssentiaCell") ? ItemStack.parseOptional(registries, tag.getCompound("EssentiaCell")) : ItemStack.EMPTY); }
}
