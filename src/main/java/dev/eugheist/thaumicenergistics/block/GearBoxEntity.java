package dev.eugheist.thaumicenergistics.block;

import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** TileGearBox client half: each crank sent by the server spins the shafts half a turn that winds down by 0.2 rad per tick. */
public class GearBoxEntity extends BlockEntity {
    public float shaftRotation;
    public GearBoxEntity(BlockPos pos, BlockState state) { super(ThaumicEnergistics.GEARBOX_ENTITY.get(), pos, state); }
    public boolean thaumium() { return getBlockState().getBlock() instanceof GearBoxBlock g && g.thaumium; }
    /** readSyncData: a new crank adds a full turn only once the previous one has nearly run out. */
    void cranked() { if (shaftRotation < 1) shaftRotation += (float)Math.PI * 2; }
    static void clientTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, GearBoxEntity be) {
        if (be.shaftRotation != 0) { be.shaftRotation -= .2f; if (be.shaftRotation < .001f) be.shaftRotation = 0; }
    }
}
