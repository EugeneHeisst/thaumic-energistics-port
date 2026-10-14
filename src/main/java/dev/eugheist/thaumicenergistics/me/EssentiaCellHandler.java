package dev.eugheist.thaumicenergistics.me;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import dev.eugheist.thaumicenergistics.item.EssentiaCellItem;

/** AEEssentiaCellHandler: drives and ME chests recognise essentia cells. */
public final class EssentiaCellHandler implements ICellHandler {
    public static final EssentiaCellHandler INSTANCE = new EssentiaCellHandler();
    private EssentiaCellHandler() {}
    @Override public boolean isCell(ItemStack stack) { return stack != null && stack.getItem() instanceof EssentiaCellItem; }
    @Override @Nullable public EssentiaCellInventory getCellInventory(ItemStack stack, @Nullable ISaveProvider container) { return EssentiaCellInventory.of(stack, container); }
}
