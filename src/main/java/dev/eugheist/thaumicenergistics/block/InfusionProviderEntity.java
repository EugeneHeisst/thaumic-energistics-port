package dev.eugheist.thaumicenergistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.tc4modern.api.AspectSource;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;

/** TileInfusionProvider: an essentia source for the infusion matrix answering from the network (all or nothing), 5 AE/t. */
public class InfusionProviderEntity extends ProviderEntity implements AspectSource {
    public InfusionProviderEntity(BlockPos pos, BlockState state) { super(ThaumicEnergistics.INFUSION_PROVIDER_ENTITY.get(), pos, state, 5); }
    @Override public boolean takeFromContainer(Aspect aspect, int amount) {
        if (extract(aspect, amount, true) != amount) return false;
        // doParticalFX: rune particles in the aspect colour around the provider.
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            int c = aspect.getColor();
            server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f), 1f),
                worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, 10, .4, .4, .4, 0);
        }
        return true;
    }
}
