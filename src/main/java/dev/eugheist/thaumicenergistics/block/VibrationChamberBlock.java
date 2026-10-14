package dev.eugheist.thaumicenergistics.block;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseBlock;
import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/** BlockEssentiaVibrationChamber: horizontal front showing off / ignis / potentia burning, light 12 while processing. */
public class VibrationChamberBlock extends AEBaseEntityBlock<VibrationChamberEntity> {
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);
    public VibrationChamberBlock() { super(AEBaseBlock.defaultProps(MapColor.METAL, SoundType.METAL).strength(1f).lightLevel(s -> s.getValue(MODE) > 0 ? 12 : 0)); registerDefaultState(defaultBlockState().setValue(MODE, 0)); }
    @Override public IOrientationStrategy getOrientationStrategy() { return OrientationStrategies.horizontalFacing(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { super.createBlockStateDefinition(builder); builder.add(MODE); }
    @Override protected BlockState updateBlockStateFromBlockEntity(BlockState state, VibrationChamberEntity be) { return state.setValue(MODE, be.mode()); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof VibrationChamberEntity be)
            server.openMenu(new SimpleMenuProvider((id, inv, p) -> new dev.eugheist.thaumicenergistics.menu.VibrationChamberMenu(id, inv, be), Component.translatable("block.thaumicenergistics.essentia_vibration_chamber")), buf -> buf.writeBlockPos(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
