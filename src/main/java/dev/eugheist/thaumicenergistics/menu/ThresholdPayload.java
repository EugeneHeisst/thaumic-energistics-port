package dev.eugheist.thaumicenergistics.menu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;

/** Packet_S_EssentiaEmitter.sendWantedAmount: the typed threshold of the open level emitter screen. */
public record ThresholdPayload(long value) implements CustomPacketPayload {
    public static final Type<ThresholdPayload> TYPE = new Type<>(ThaumicEnergistics.id("emitter_threshold"));
    public static final StreamCodec<ByteBuf, ThresholdPayload> CODEC = ByteBufCodecs.VAR_LONG.map(ThresholdPayload::new, ThresholdPayload::value);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(ThresholdPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player().containerMenu instanceof EssentiaLevelEmitterMenu menu) menu.setThreshold(payload.value()); });
    }
}
