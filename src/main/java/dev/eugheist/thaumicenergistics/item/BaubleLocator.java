package dev.eugheist.thaumicenergistics.item;

import appeng.menu.locator.ItemMenuHostLocator;
import dev.eugheist.tc4modern.BaublesInventory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** AE2 menu host locator for a wireless terminal worn in a tc4modern Baubles slot (like AE2's Curios locator). */
public record BaubleLocator(int slot) implements ItemMenuHostLocator {
    @Override public ItemStack locateItem(Player player) { return BaublesInventory.of(player).getItem(slot); }
    @Override @Nullable public BlockHitResult hitResult() { return null; }
    public void writeToPacket(FriendlyByteBuf buf) { buf.writeVarInt(slot); }
    public static BaubleLocator readFromPacket(FriendlyByteBuf buf) { return new BaubleLocator(buf.readVarInt()); }

    /** First worn bauble of the given item class, as a locator; null if none is worn. */
    @Nullable public static BaubleLocator find(Player player, Class<?> itemClass) {
        var baubles = BaublesInventory.of(player);
        for (int i = 0; i < baubles.getContainerSize(); i++) if (itemClass.isInstance(baubles.getItem(i).getItem())) return new BaubleLocator(i);
        return null;
    }
}
