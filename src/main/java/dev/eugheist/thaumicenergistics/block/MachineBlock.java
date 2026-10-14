package dev.eugheist.thaumicenergistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** A plain (non-network) Thaumic Energistics machine: opens its menu, drops the listed inventory slots when broken. */
public class MachineBlock<T extends BlockEntity> extends BaseEntityBlock {
    public interface Host { Container drops(); AbstractContainerMenu menu(int id, Inventory inventory); }
    final java.util.function.BiFunction<BlockPos, BlockState, T> factory;
    final String title;
    public MachineBlock(Properties properties, java.util.function.BiFunction<BlockPos, BlockState, T> factory, String title) { super(properties); this.factory = factory; this.title = title; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override @Nullable public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return factory.apply(pos, state); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof Host host)
            server.openMenu(new SimpleMenuProvider((id, inv, p) -> host.menu(id, inv), Component.translatable(title)), buf -> buf.writeBlockPos(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof Host host) Containers.dropContents(level, pos, host.drops());
        super.onRemove(state, level, pos, next, moved);
    }
}
