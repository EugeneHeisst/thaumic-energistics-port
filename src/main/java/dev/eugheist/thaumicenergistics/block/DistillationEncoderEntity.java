package dev.eugheist.thaumicenergistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.DistillationEncoderMenu;

/** TileDistillationPatternEncoder: ghost source item (0), blank patterns (1), encoded pattern (2); only the patterns drop. */
public class DistillationEncoderEntity extends BlockEntity implements MachineBlock.Host {
    public static final int SOURCE = 0, BLANK = 1, ENCODED = 2;
    public final SimpleContainer inventory = new SimpleContainer(3) {
        @Override public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == BLANK ? stack.is(appeng.core.definitions.AEItems.BLANK_PATTERN.asItem()) : slot != ENCODED || appeng.api.crafting.PatternDetailsHelper.isEncodedPattern(stack);
        }
    };
    final SimpleContainer drops = new SimpleContainer(2);
    public DistillationEncoderEntity(BlockPos pos, BlockState state) { super(ThaumicEnergistics.DISTILLATION_ENCODER_ENTITY.get(), pos, state); inventory.addListener(c -> setChanged()); }
    @Override public Container drops() { drops.setItem(0, inventory.getItem(BLANK)); drops.setItem(1, inventory.getItem(ENCODED)); return drops; }
    @Override public AbstractContainerMenu menu(int id, Inventory inv) { return new DistillationEncoderMenu(id, inv, this); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) { super.saveAdditional(tag, r); var list = net.minecraft.core.NonNullList.withSize(3, ItemStack.EMPTY); for (int i = 0; i < 3; i++) list.set(i, inventory.getItem(i)); var inv = new CompoundTag(); ContainerHelper.saveAllItems(inv, list, r); tag.put("inventory", inv); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) { super.loadAdditional(tag, r); var list = net.minecraft.core.NonNullList.withSize(3, ItemStack.EMPTY); ContainerHelper.loadAllItems(tag.getCompound("inventory"), list, r); for (int i = 0; i < 3; i++) inventory.setItem(i, list.get(i)); }
}
