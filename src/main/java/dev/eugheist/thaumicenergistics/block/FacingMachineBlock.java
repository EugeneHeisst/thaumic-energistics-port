package dev.eugheist.thaumicenergistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** BlockDistillationEncoder: the encoder face turns towards the player who placed it. */
public class FacingMachineBlock<T extends net.minecraft.world.level.block.entity.BlockEntity> extends MachineBlock<T> {
    public FacingMachineBlock(Properties properties, java.util.function.BiFunction<BlockPos, BlockState, T> factory, String title) {
        super(properties, factory, title);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(HorizontalDirectionalBlock.FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) { return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, ctx.getHorizontalDirection().getOpposite()); }
}
