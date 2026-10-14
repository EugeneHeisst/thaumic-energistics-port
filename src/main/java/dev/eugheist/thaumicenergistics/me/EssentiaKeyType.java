package dev.eugheist.thaumicenergistics.me;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;

/** Essentia as an ME key type. One byte holds two essentia, as HandlerItemEssentiaCell.ESSENTIA_PER_BYTE. */
public final class EssentiaKeyType extends AEKeyType {
    public static final int ESSENTIA_PER_BYTE = 2;
    public static final EssentiaKeyType TYPE = new EssentiaKeyType();
    private static final MapCodec<EssentiaKey> MAP_CODEC = Codec.STRING.comapFlatMap(
        tag -> { var key = EssentiaKey.of(tag); return key == null ? DataResult.error(() -> "Unknown aspect " + tag) : DataResult.success(key); },
        key -> key.getAspect().getTag()).fieldOf("aspect");

    private EssentiaKeyType() { super(ThaumicEnergistics.id("essentia"), EssentiaKey.class, Component.translatable("thaumicenergistics.key.essentia")); }

    public static void register(RegisterEvent event) { if (event.getRegistryKey().equals(Registries.ITEM)) AEKeyTypes.register(TYPE); }

    @Override public MapCodec<? extends AEKey> codec() { return MAP_CODEC; }
    @Override @Nullable public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return EssentiaKey.of(input.readUtf()); }
    @Override public int getAmountPerByte() { return ESSENTIA_PER_BYTE; }
    @Override public int getAmountPerOperation() { return 1; }
}
