package dev.eugheist.thaumicenergistics.part;

import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPartItem;
import appeng.api.storage.StorageHelper;
import appeng.core.localization.PlayerMessages;
import appeng.me.helpers.PlayerSource;
import appeng.parts.reporting.AbstractMonitorPart;
import appeng.parts.reporting.ConversionMonitorPart;
import appeng.parts.reporting.StorageMonitorPart;
import appeng.util.InteractionUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import dev.eugheist.tc4modern.EssentiaItems;
import dev.eugheist.thaumicenergistics.me.EssentiaContainerItemStrategy;
import dev.eugheist.thaumicenergistics.me.EssentiaKey;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;

/**
 * PartEssentiaStorageMonitor / PartEssentiaConversionMonitor on AE2's monitor display. Right-click with an aspect item
 * (phial, jar, label, crystal, wisp essence) tracks that aspect, an empty hand clears it, a wrench (or sneak + empty hand)
 * locks the screen. A locked conversion monitor fills right-clicked phials/jars from the network and writes its aspect onto
 * jar labels; sneak + right-click with a filled container empties it into the network, twice within 2 s empties every
 * container of that aspect in the inventory.
 */
public final class EssentiaMonitors {
    private EssentiaMonitors() {}

    static boolean check(AbstractMonitorPart part, Player player) { return !(player instanceof FakePlayer) && part.getMainNode().isActive(); }
    @Nullable static Aspect tracked(AbstractMonitorPart part) { return part.getDisplayed() instanceof EssentiaKey k ? k.getAspect() : null; }

    static boolean toggleLock(AbstractMonitorPart part, Player player) {
        part.setLocked(!part.isLocked());
        player.displayClientMessage((part.isLocked() ? PlayerMessages.isNowLocked : PlayerMessages.isNowUnlocked).text(), true);
        part.getHost().markForSave();
        return true;
    }
    static void track(AbstractMonitorPart part, @Nullable Aspect aspect) {
        part.setConfiguredItem(aspect == null ? null : EssentiaKey.of(aspect));
        ((Access) part).reconfigure();
        part.getHost().markForSave();
    }
    interface Access { void reconfigure(); }

    /** onActivatedWithEmptyHand. */
    static boolean emptyHand(AbstractMonitorPart part, Player player) {
        if (part.isClientSide()) return true;
        if (!check(part, player)) return false;
        if (InteractionUtil.isInAlternateUseMode(player)) return toggleLock(part, player);
        if (part.isLocked()) return false;
        track(part, null);
        return true;
    }
    /** onActivate with an item: wrench locks, aspect items track. */
    static boolean withItem(AbstractMonitorPart part, ItemStack held, Player player) {
        if (part.isClientSide()) return true;
        if (!check(part, player)) return false;
        if (!InteractionUtil.isInAlternateUseMode(player) && InteractionUtil.canWrenchRotate(held)) return toggleLock(part, player);
        var aspect = EssentiaTiles.filterAspect(held);
        if (aspect == null && !EssentiaContainerItemStrategy.isContainer(held)) return false;
        if (part.isLocked()) return false;
        track(part, aspect);
        return true;
    }

    public static class Storage extends StorageMonitorPart implements Access {
        public Storage(IPartItem<?> item) { super(item); }
        @Override public void reconfigure() { configureWatchers(); }
        @Override public boolean onUseWithoutItem(Player player, Vec3 pos) { return emptyHand(this, player); }
        @Override public boolean onUseItemOn(ItemStack held, Player player, InteractionHand hand, Vec3 pos) { return withItem(this, held, player); }
    }

    public static class Conversion extends ConversionMonitorPart implements Access {
        private static final long DOUBLE_CLICK_TICKS = 40;
        @Nullable private UUID depositedPlayer;
        private long depositedTick;
        @Nullable private Aspect depositedAspect;
        public Conversion(IPartItem<?> item) { super(item); }
        @Override public void reconfigure() { configureWatchers(); }
        @Override public boolean onUseWithoutItem(Player player, Vec3 pos) { return emptyHand(this, player); }
        @Override public boolean onClicked(Player player, Vec3 pos) { return false; }
        @Override public boolean onShiftClicked(Player player, Vec3 pos) { return false; }

        @Override public boolean onUseItemOn(ItemStack held, Player player, InteractionHand hand, Vec3 pos) {
            if (isClientSide()) return true;
            if (!check(this, player)) return false;
            if (InteractionUtil.isInAlternateUseMode(player)) return shiftActivate(player);
            var aspect = tracked(this);
            if (aspect == null || !isLocked()) return withItem(this, held, player);
            if (InteractionUtil.canWrenchRotate(held)) return toggleLock(this, player);
            return fill(player, held, aspect);
        }

        /** fillEssentiaContainer: labels take the aspect, phials/jars are topped up from the network. */
        boolean fill(Player player, ItemStack held, Aspect aspect) {
            if (held.getItem() == dev.eugheist.tc4modern.EssentiaDevices.LABEL.get()) { EssentiaItems.setAspects(held, new AspectList().add(aspect, 0)); return true; }
            if (!EssentiaContainerItemStrategy.isContainer(held)) return false;
            var grid = getMainNode().getGrid();
            if (grid == null) return false;
            var key = EssentiaKey.of(aspect);var src = new PlayerSource(player, (IActionHost) this);
            var ctx = EssentiaContainerItemStrategy.playerSlot(player, player.getInventory().selected);
            long room = EssentiaContainerItemStrategy.INSTANCE.insert(ctx, key, Long.MAX_VALUE / 4, Actionable.SIMULATE);
            if (room <= 0) return false;
            long available = StorageHelper.poweredExtraction(grid.getEnergyService(), grid.getStorageService().getInventory(), key, room, src, Actionable.SIMULATE);
            if (available <= 0 || EssentiaContainerItemStrategy.INSTANCE.insert(ctx, key, available, Actionable.SIMULATE) <= 0) return false;
            long put = EssentiaContainerItemStrategy.INSTANCE.insert(ctx, key, available, Actionable.MODULATE);
            StorageHelper.poweredExtraction(grid.getEnergyService(), grid.getStorageService().getInventory(), key, put, src, Actionable.MODULATE);
            player.containerMenu.broadcastChanges();
            return true;
        }

        /** onShiftActivate: drain the held container; a second sneak-click within 2 s drains the whole inventory. */
        boolean shiftActivate(Player player) {
            if (!EssentiaContainerItemStrategy.isContainer(player.getMainHandItem())) return false;
            long now = player.level().getGameTime();
            if (depositedPlayer != null && depositedPlayer.equals(player.getUUID()) && now - depositedTick <= DOUBLE_CLICK_TICKS) {
                depositedPlayer = null;
                var inv = player.getInventory();
                for (int slot = 0; slot < inv.getContainerSize(); slot++) while (!inv.getItem(slot).isEmpty() && drain(player, slot, depositedAspect)) {}
                return true;
            }
            boolean drained = drain(player, player.getInventory().selected, null);
            if (drained) { depositedPlayer = player.getUUID(); depositedTick = now; }
            return drained;
        }

        /** drainEssentiaContainer: whatever the network accepts leaves the container. */
        boolean drain(Player player, int slot, @Nullable Aspect mustMatch) {
            var grid = getMainNode().getGrid();
            var stack = player.getInventory().getItem(slot);
            var held = EssentiaContainerItemStrategy.INSTANCE.getContainedStack(stack);
            if (grid == null || held == null || !(held.what() instanceof EssentiaKey key) || mustMatch != null && key.getAspect() != mustMatch) return false;
            var ctx = EssentiaContainerItemStrategy.playerSlot(player, slot);var src = new PlayerSource(player, (IActionHost) this);
            var inv = grid.getStorageService().getInventory();var energy = grid.getEnergyService();
            long accepted = StorageHelper.poweredInsert(energy, inv, key, held.amount(), src, Actionable.SIMULATE);
            long taken = EssentiaContainerItemStrategy.INSTANCE.extract(ctx, key, accepted, Actionable.SIMULATE);
            if (taken <= 0) return false;
            taken = EssentiaContainerItemStrategy.INSTANCE.extract(ctx, key, taken, Actionable.MODULATE);
            StorageHelper.poweredInsert(energy, inv, key, taken, src, Actionable.MODULATE);
            depositedAspect = key.getAspect();
            player.containerMenu.broadcastChanges();
            return taken > 0;
        }
    }
}
