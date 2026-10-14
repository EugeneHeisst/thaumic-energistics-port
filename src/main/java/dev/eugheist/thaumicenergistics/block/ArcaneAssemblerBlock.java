package dev.eugheist.thaumicenergistics.block;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.block.AEBaseBlock;
import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;

/** BlockArcaneAssembler: a memory card holding a vis relay interface links it; a blank card unlinks it. */
public class ArcaneAssemblerBlock extends AEBaseEntityBlock<ArcaneAssemblerEntity> {
    public ArcaneAssemblerBlock() { super(AEBaseBlock.defaultProps(MapColor.METAL, SoundType.METAL).strength(1f).noOcclusion()); }

    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (held.getItem() instanceof IMemoryCard card && level.getBlockEntity(pos) instanceof ArcaneAssemblerEntity be && !appeng.util.InteractionUtil.isInAlternateUseMode(player)) {
            if (!level.isClientSide) {
                var source = held.get(ThaumicEnergistics.VIS_SOURCE.get());
                if (source != null) { be.setVisSource(source); card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED); }
                else if (held.get(AEComponents.EXPORTED_SETTINGS_SOURCE) == null) { be.setVisSource(null); card.notifyUser(player, MemoryCardMessages.SETTINGS_CLEARED); }
                else card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(held, state, level, pos, player, hand, hit);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof ArcaneAssemblerEntity be)
            server.openMenu(new SimpleMenuProvider((id, inv, p) -> be.menu(id, inv), Component.translatable("thaumicenergistics.gui.arcane.assembler.title")), buf -> { buf.writeBlockPos(pos); buf.writeBoolean(appeng.items.tools.NetworkToolItem.findNetworkToolInv(player) != null); });
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
