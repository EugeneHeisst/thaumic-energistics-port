package dev.eugheist.thaumicenergistics.block;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.tc4modern.EssentiaItems;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.item.EssentiaCellItem;

/**
 * ContainerEssentiaCellWorkbench: cell slot at (152,8), player inventory at y169/227. The 63 partition widgets are menu buttons
 * 0..62 (carried aspect item adds/replaces, empty hand removes), 100 = partition to contents, 101 = clear, as the original packets.
 */
public final class CellWorkbenchMenu extends AbstractContainerMenu {
    public static final int CELL_X = 152, CELL_Y = 8, BUTTON_TO_CONTENTS = 100, BUTTON_CLEAR = 101;
    @Nullable public final CellWorkbenchEntity workbench;
    final Container cellContainer;

    public CellWorkbenchMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) { this(id, inventory, inventory.player.level().getBlockEntity(buf.readBlockPos()) instanceof CellWorkbenchEntity w ? w : null); }
    public CellWorkbenchMenu(int id, Inventory inventory, @Nullable CellWorkbenchEntity workbench) {
        super(ThaumicEnergistics.CELL_WORKBENCH_MENU.get(), id);
        this.workbench = workbench;
        cellContainer = workbench != null ? workbench.inventory : new SimpleContainer(1);
        addSlot(new Slot(cellContainer, 0, CELL_X, CELL_Y) { @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof EssentiaCellItem; } @Override public int getMaxStackSize() { return 1; } });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 169 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 227));
    }

    /** EssentiaItemContainerHelper.getFilterAspectFromItem: jar labels, crystallised essentia, phials/jars, wisp essence. */
    @Nullable public static Aspect filterAspect(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var aspects = EssentiaItems.getAspects(stack);
        return aspects.size() > 0 ? aspects.getAspects()[0] : null;
    }

    public java.util.List<Aspect> partition() { var cell = slots.get(0).getItem(); var inv = dev.eugheist.thaumicenergistics.me.EssentiaCellInventory.of(cell, null); return inv == null ? java.util.List.of() : inv.getPartition(); }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (workbench == null) return false;
        if (button == BUTTON_CLEAR) { workbench.clear(); return true; }
        if (button == BUTTON_TO_CONTENTS) { workbench.toContents(); return true; }
        if (button < 0 || button >= 63) return false;
        var list = workbench.partition();
        Aspect widget = button < list.size() ? list.get(button) : null, held = filterAspect(getCarried());
        if (widget == null && held != null) workbench.add(held);
        else if (widget != null && held == null) workbench.remove(widget);
        else if (widget != null) workbench.replace(widget, held);
        broadcastChanges();
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        if (index == 0) { if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY; }
        else if (stack.getItem() instanceof EssentiaCellItem && !slots.get(0).hasItem()) { if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY; }
        else {
            var aspect = filterAspect(stack);
            if (aspect != null && workbench != null && workbench.add(aspect)) { broadcastChanges(); return ItemStack.EMPTY; }
            if (index < 28 ? !moveItemStackTo(stack, 28, 37, false) : !moveItemStackTo(stack, 1, 28, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return ItemStack.EMPTY;
    }

    @Override public boolean stillValid(Player player) { return workbench != null && !workbench.isRemoved() && player.distanceToSqr(workbench.getBlockPos().getCenter()) <= 64; }
}
