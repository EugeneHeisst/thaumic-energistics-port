package dev.eugheist.thaumicenergistics.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import dev.eugheist.tc4modern.FociContent;

/**
 * ItemFocusAEWrench: sneak + right-click with the wand acts as a certus quartz wrench on the looked-at block (dismantling AE
 * parts and machines), for 0.1 ignis and 0.1 aer per use. Cooldown 1 s, energy-coloured focus.
 */
public class WrenchFocusItem extends FociContent.Focus {
    static final AspectList COST = new AspectList().add(Aspect.FIRE, 10).add(Aspect.AIR, 10);
    @Override public AspectList getVisCost(ItemStack focus) { return COST; }
    @Override public String getSortingHelperBase() { return "AEWRENCH"; }
    @Override public int getActivationCooldown(ItemStack focus) { return 1000; }
    @Override public int getFocusColor(ItemStack focus) { return Aspect.ENERGY.getColor(); }
    @Override public int[] getPossibleUpgradesByRank(ItemStack focus, int rank) { return new int[0]; }

    @Override public boolean onFocusRightClick(ItemStack wand, Level level, Player player) {
        if (!player.isShiftKeyDown()) return false;
        var start = player.getEyePosition();var end = start.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !level.mayInteract(player, hit.getBlockPos())) return false;
        if (!FociContent.consumeAllVis(wand, player, COST, false)) return false;
        if (level.isClientSide) return true;
        boolean handled = useAsWrench(level, player, hit);
        if (handled) FociContent.consumeAllVis(wand, player, COST, true);
        return handled;
    }

    /** handleBlockPop: the player briefly holds a certus quartz wrench and uses it on the block. */
    static boolean useAsWrench(Level level, Player player, BlockHitResult hit) {
        var hand = InteractionHand.MAIN_HAND;var held = player.getItemInHand(hand);
        var wrench = new ItemStack(appeng.core.definitions.AEItems.CERTUS_QUARTZ_WRENCH.asItem());
        boolean wasAir = level.isEmptyBlock(hit.getBlockPos());
        try {
            player.setItemInHand(hand, wrench);
            // AE2 1.21 dismantles/rotates through its wrench hook on block use; then the block itself, then the wrench item.
            var hooked = appeng.hooks.WrenchHook.onPlayerUseBlock(player, level, hand, hit);
            if (hooked.consumesAction()) return true;
            var state = level.getBlockState(hit.getBlockPos());
            var blockResult = state.useItemOn(wrench, level, player, hand, hit);
            if (blockResult.consumesAction()) return true;
            var ctx = new UseOnContext(player, hand, hit);
            var result = wrench.onItemUseFirst(ctx);
            if (!result.consumesAction()) result = wrench.useOn(ctx);
            return result.consumesAction() || !wasAir && level.isEmptyBlock(hit.getBlockPos());
        } finally {
            player.setItemInHand(hand, held);
        }
    }
}
