package dev.eugheist.thaumicenergistics.part;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/** Opens the original-style part screens: the menu buffer carries the host position and the part side. */
public final class PartMenus {
    private PartMenus() {}
    public interface Host extends IPart {
        AbstractContainerMenu createMenu(int id, Inventory inventory);
        default boolean stillValid(Player player) {
            var be = ((appeng.parts.AEBasePart) this).getBlockEntity();
            return be != null && !be.isRemoved() && be instanceof IPartHost host && host.getPart(((appeng.parts.AEBasePart) this).getSide()) == this
                && player.distanceToSqr(be.getBlockPos().getCenter()) <= 64;
        }
    }

    public static boolean open(Host part, Player player) {
        var base = (appeng.parts.AEBasePart) part;
        if (base.getBlockEntity().getLevel().isClientSide) return true;
        var side = ((appeng.parts.AEBasePart) part).getSide();
        ((ServerPlayer) player).openMenu(new SimpleMenuProvider((id, inv, p) -> part.createMenu(id, inv), ((appeng.parts.AEBasePart) part).getPartItem().asItem().getDescription()),
            buf -> { buf.writeBlockPos(base.getBlockEntity().getBlockPos()); buf.writeEnum(side); buf.writeBoolean(appeng.items.tools.NetworkToolItem.findNetworkToolInv(player) != null); });
        return true;
    }

    /** Whether the server found a network tool when it opened the menu (client inventories can lag one tick behind). */
    public static final ThreadLocal<Boolean> TOOL = ThreadLocal.withInitial(() -> false);
    @Nullable @SuppressWarnings("unchecked")
    public static <T> T read(Inventory inventory, RegistryFriendlyByteBuf buf, Class<T> type) {
        var pos = buf.readBlockPos();var side = buf.readEnum(Direction.class);TOOL.set(buf.readBoolean());
        return inventory.player.level().getBlockEntity(pos) instanceof IPartHost host && type.isInstance(host.getPart(side)) ? (T) host.getPart(side) : null;
    }
}
