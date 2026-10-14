package dev.eugheist.thaumicenergistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.KnowledgeInscriberMenu;

/** TileKnowledgeInscriber: holds one knowledge core. */
public class KnowledgeInscriberEntity extends BlockEntity implements MachineBlock.Host {
    public final SimpleContainer inventory = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return stack.is(ThaumicEnergistics.KNOWLEDGE_CORE.get()); }
        @Override public int getMaxStackSize() { return 1; }
    };
    public KnowledgeInscriberEntity(BlockPos pos, BlockState state) { super(ThaumicEnergistics.KNOWLEDGE_INSCRIBER_ENTITY.get(), pos, state); inventory.addListener(c -> setChanged()); }
    @Override public Container drops() { return inventory; }
    @Override public AbstractContainerMenu menu(int id, Inventory inv) { return new KnowledgeInscriberMenu(id, inv, this); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) { super.saveAdditional(tag, r); if (!inventory.getItem(0).isEmpty()) tag.put("kcore", inventory.getItem(0).save(r)); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) { super.loadAdditional(tag, r); inventory.setItem(0, tag.contains("kcore") ? ItemStack.parseOptional(r, tag.getCompound("kcore")) : ItemStack.EMPTY); }
}
