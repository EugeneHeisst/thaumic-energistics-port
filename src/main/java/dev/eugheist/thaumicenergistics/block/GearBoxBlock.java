package dev.eugheist.thaumicenergistics.block;

import appeng.api.implementations.blockentities.ICrankable;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * BlockGearBox / BlockGolemGearBox: turning the box turns every attached crankable (AE2 charger, ...) on all six sides; each
 * turn carries 6 power split evenly between the devices able to turn, and a device turns once it has gathered 18. Several
 * players cranking add up. Only the thaumium box can be worked by golems (use core / any fake player).
 */
public class GearBoxBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    public static final MapCodec<GearBoxBlock> CODEC = simpleCodec(p -> new GearBoxBlock(p, false));
    final boolean thaumium;
    private final java.util.Map<BlockPos, int[]> shafts = new java.util.WeakHashMap<>();
    public GearBoxBlock(Properties properties, boolean thaumium) { super(properties); this.thaumium = thaumium; }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GearBoxEntity(pos, state); }
    @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide && type == dev.eugheist.thaumicenergistics.ThaumicEnergistics.GEARBOX_ENTITY.get() ? (l, p, s, be) -> GearBoxEntity.clientTick(l, p, s, (GearBoxEntity)be) : null;
    }
    /** The shaft animation travels as a block event, like the original's description packet after a crank. */
    @Override protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        if (id == 1 && level.getBlockEntity(pos) instanceof GearBoxEntity be) be.cranked();
        return id == 1;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof FakePlayer && !thaumium) return InteractionResult.PASS;
        return crank(level, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** crankGearbox. */
    public boolean crank(Level level, BlockPos pos) {
        var turnable = new ICrankable[6];int count = 0;
        for (var side : Direction.values()) {
            var c = ICrankable.get(level, pos.relative(side), side.getOpposite());
            if (c != null && c.canTurn()) { turnable[side.ordinal()] = c; count++; }
        }
        if (count == 0) return false;
        int power = 6 / count;
        var shaft = shafts.computeIfAbsent(pos.immutable(), p -> new int[6]);
        for (int i = 0; i < 6; i++) {
            if (turnable[i] == null) { shaft[i] = 0; continue; }
            shaft[i] += power;
            if (shaft[i] >= 18) { shaft[i] = 0; turnable[i].applyTurn(); }
        }
        level.blockEvent(pos, this, 1, 0);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOODEN_BUTTON_CLICK_ON, net.minecraft.sounds.SoundSource.BLOCKS, .3f, .6f);
        return true;
    }
}
