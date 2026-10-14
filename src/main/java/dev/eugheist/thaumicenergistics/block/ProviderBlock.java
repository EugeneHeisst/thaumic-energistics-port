package dev.eugheist.thaumicenergistics.block;

import appeng.block.AEBaseBlock;
import appeng.block.AEBaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/** BlockEssentiaProvider / BlockInfusionProvider (hardness 1). */
public class ProviderBlock<T extends ProviderEntity> extends AEBaseEntityBlock<T> {
    public ProviderBlock() { super(AEBaseBlock.defaultProps(MapColor.METAL, SoundType.METAL).strength(1f)); }
}
