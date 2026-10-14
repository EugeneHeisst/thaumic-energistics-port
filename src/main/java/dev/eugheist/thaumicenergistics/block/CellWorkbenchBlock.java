package dev.eugheist.thaumicenergistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** BlockEssentiaCellWorkbench: metal block (hardness 1), opens the partition GUI, drops its cell when broken. */
public final class CellWorkbenchBlock extends BaseEntityBlock {
    public static final MapCodec<CellWorkbenchBlock> CODEC = simpleCodec(CellWorkbenchBlock::new);
    public CellWorkbenchBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override @Nullable public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CellWorkbenchEntity(pos, state); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CellWorkbenchEntity workbench)
            server.openMenu(new SimpleMenuProvider((id, inv, p) -> new CellWorkbenchMenu(id, inv, workbench), Component.translatable("thaumicenergistics.gui.essentia.cell.workbench.title")), buf -> buf.writeBlockPos(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof CellWorkbenchEntity workbench) Containers.dropContents(level, pos, workbench.inventory);
        super.onRemove(state, level, pos, next, moved);
    }
}
