package dev.eugheist.thaumicenergistics.me;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;

/**
 * One aspect of essentia as an ME resource. The original 1.7.10 addon had to disguise essentia as a gaseous fluid
 * (GaseousEssentia) because AE2 rv3 only knew items and fluids; modern AE2 takes custom key types directly.
 */
public final class EssentiaKey extends AEKey {
    private static final Map<Aspect, EssentiaKey> CACHE = new ConcurrentHashMap<>();
    private final Aspect aspect;

    private EssentiaKey(Aspect aspect) { this.aspect = aspect; }

    public static EssentiaKey of(Aspect aspect) { return CACHE.computeIfAbsent(aspect, EssentiaKey::new); }
    @Nullable public static EssentiaKey of(String tag) { var aspect = Aspect.getAspect(tag); return aspect == null ? null : of(aspect); }

    public Aspect getAspect() { return aspect; }
    @Override public AEKeyType getType() { return EssentiaKeyType.TYPE; }
    @Override public AEKey dropSecondary() { return this; }
    @Override public CompoundTag toTag(HolderLookup.Provider registries) { var tag = new CompoundTag(); tag.putString("aspect", aspect.getTag()); return tag; }
    @Override public Object getPrimaryKey() { return aspect; }
    @Override public ResourceLocation getId() { return ThaumicEnergistics.id(aspect.getTag()); }
    @Override public void writeToPacket(RegistryFriendlyByteBuf data) { data.writeUtf(aspect.getTag()); }
    @Override protected Component computeDisplayName() { return Component.literal(aspect.getName()); }
    /** Essentia has no item form; like the original gaseous essentia it is lost when its holder breaks. */
    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    @Override public boolean hasComponents() { return false; }
    @Override public boolean equals(Object o) { return o instanceof EssentiaKey k && k.aspect == aspect; }
    @Override public int hashCode() { return aspect.hashCode(); }
    @Override public String toString() { return "EssentiaKey[" + aspect.getTag() + "]"; }
}
