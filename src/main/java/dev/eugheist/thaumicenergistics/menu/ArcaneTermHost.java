package dev.eugheist.thaumicenergistics.menu;

import appeng.api.inventories.InternalInventory;
import appeng.api.storage.ITerminalHost;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** What the arcane crafting terminal menu needs from its host: the cabled part or the wireless terminal item. */
public interface ArcaneTermHost extends ITerminalHost {
    InternalInventory grid();
    InternalInventory wand();
    InternalInventory armor();
    void saveChanges();

    /** swapStoredArmor: stored slot i holds the head..feet piece, exchanged with the worn one. */
    default void swapArmor(Player player) {
        var slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            var stored = armor().getStackInSlot(i);var worn = player.getItemBySlot(slots[i]);
            player.setItemSlot(slots[i], stored.copy());
            armor().setItemDirect(i, worn.copy());
        }
        saveChanges();
    }
}
