package dev.eugheist.thaumicenergistics.item;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.util.KeyTypeSelection;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.me.EssentiaKeyType;
import dev.eugheist.thaumicenergistics.menu.EssentiaTermMenu;

/**
 * ItemWirelessEssentiaTerminal: the essentia terminal GUI through a linked wireless access point (link it in the access
 * point). Range, link and power come from AE2's wireless terminal host; selection and modes are stored on the item.
 */
public class WirelessEssentiaTerminalItem extends WirelessTerminalItem implements baubles.api.IBauble {
    /** Worn in any Baubles slot; the wireless terminal key opens it from there. */
    @Override public baubles.api.BaubleType getBaubleType(net.minecraft.world.item.ItemStack stack) { return baubles.api.BaubleType.TRINKET; }
    public WirelessEssentiaTerminalItem(Properties props) { super(() -> appeng.core.AEConfig.instance().getWirelessTerminalBattery().getAsDouble(), props.stacksTo(1)); }
    @Override @Nullable public WirelessTerminalMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hit) {
        return new WirelessTerminalMenuHost<WirelessEssentiaTerminalItem>(this, player, locator, (p, sub) -> openFromInventory(p, locator, true)) {
            final KeyTypeSelection essentiaOnly = new KeyTypeSelection(() -> {}, type -> type == EssentiaKeyType.TYPE);
            { essentiaOnly.setEnabled(EssentiaKeyType.TYPE, true); }
            @Override public KeyTypeSelection getKeyTypeSelection() { return essentiaOnly; }
        };
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.isClientSide && checkPreconditions(stack)) openFromInventory(player, MenuLocators.forHand(player, hand), false);
        return new InteractionResultHolder<>(net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide), stack);
    }
    @Override protected boolean openFromInventory(Player player, ItemMenuHostLocator locator, boolean returningFromSubmenu) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer sp)) return false;
        var stack = locator.locateItem(player);
        if (!checkPreconditions(stack)) return false;
        if (getLinkedGrid(stack, player.level(), sp::sendSystemMessage) == null) return false;
        var aeHost = getMenuHost(player, locator, null);
        var host = new Host(aeHost, locator);
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new EssentiaTermMenu(id, inv, host), stack.getHoverName()));
        return true;
    }

    /** HandlerWirelessEssentiaTerminal over AE2's WirelessTerminalMenuHost (range to the nearest access point, idle power). */
    record Host(WirelessTerminalMenuHost<?> ae, ItemMenuHostLocator at) implements EssentiaTermMenu.Host {
        ItemStack stack() { return ae.getItemStack(); }
        net.minecraft.nbt.CompoundTag data() { return stack().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
        void data(java.util.function.Consumer<net.minecraft.nbt.CompoundTag> edit) { CustomData.update(DataComponents.CUSTOM_DATA, stack(), edit); }
        @Override public void tick() { ae.tick(); }
        @Override @Nullable public IGrid grid() { var node = ae.getActionableNode(); return ae.getLinkStatus().connected() && node != null ? node.getGrid() : null; }
        @Override public IActionSource source(Player player) { return IActionSource.ofPlayer(player, ae); }
        @Override public boolean stillValid(Player player) { return ae.isValid() && stack().getItem() instanceof WirelessEssentiaTerminalItem; }
        @Override @Nullable public Aspect selected() { var t = data().getString("ThESelected"); return t.isEmpty() ? null : Aspect.getAspect(t); }
        @Override public void selected(@Nullable Aspect aspect) { data(t -> { if (aspect == null) t.remove("ThESelected"); else t.putString("ThESelected", aspect.getTag()); }); }
        @Override public int sortMode() { return data().getInt("ThESort"); }
        @Override public int viewMode() { return data().getInt("ThEView"); }
        @Override public void modes(int sort, int view) { data(t -> { t.putInt("ThESort", sort);t.putInt("ThEView", view); }); }
        static final java.util.WeakHashMap<WirelessTerminalMenuHost<?>, Container> SLOTS = new java.util.WeakHashMap<>();
        @Override public Container workSlots() { return SLOTS.computeIfAbsent(ae, k -> new SimpleContainer(2)); }
        @Override public boolean temporarySlots() { return true; }
        @Override public MenuHostLocator locator() { return at; }
    }
}
