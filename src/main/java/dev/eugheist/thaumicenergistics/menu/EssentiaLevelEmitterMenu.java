package dev.eugheist.thaumicenergistics.menu;

import appeng.api.config.RedstoneMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.me.EssentiaTiles;
import dev.eugheist.thaumicenergistics.part.EssentiaLevelEmitterPart;
import dev.eugheist.thaumicenergistics.part.PartMenus;

/**
 * ContainerPartEssentiaLevelEmitter: button 0 is the aspect widget, 1..6 add +1 +10 +100 -1 -10 -100 (11..16 the shifted
 * x100 set), 20 toggles high/low; a typed amount arrives as {@link ThresholdPayload}. The threshold travels in three 16-bit
 * data slots because container data packets carry shorts.
 */
public final class EssentiaLevelEmitterMenu extends PartMenu<EssentiaLevelEmitterPart> {
    public static final int BUTTON_REDSTONE = 20;
    public static final int[] DELTAS = {1, 10, 100, -1, -10, -100};
    public EssentiaLevelEmitterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) { this(id, inventory, PartMenus.read(inventory, buf, EssentiaLevelEmitterPart.class)); }
    public EssentiaLevelEmitterMenu(int id, Inventory inventory, @Nullable EssentiaLevelEmitterPart part) {
        super(ThaumicEnergistics.LEVEL_EMITTER_MENU.get(), id, inventory, part);
        bindPlayer(inventory, 102, 160);
        track(5, () -> EssentiaTiles.id(part.getTracked()), () -> part.getMode().ordinal(),
            () -> (int) (part.getThreshold() & 0xFFFF), () -> (int) (part.getThreshold() >>> 16 & 0xFFFF), () -> (int) (part.getThreshold() >>> 32 & 0xFFFF));
    }
    @Nullable public Aspect tracked() { return EssentiaTiles.byId(data[0]); }
    public RedstoneMode mode() { return RedstoneMode.values()[Math.max(0, Math.min(RedstoneMode.values().length - 1, data[1]))]; }
    public long threshold() { return (data[2] & 0xFFFFL) | (data[3] & 0xFFFFL) << 16 | (data[4] & 0xFFFFL) << 32; }

    public void setThreshold(long value) { if (part != null) part.setThreshold(value); }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (part == null) return false;
        if (button == 0) { part.setTracked(EssentiaTiles.filterAspect(getCarried())); return true; }
        if (button >= 1 && button <= 6) { part.setThreshold(part.getThreshold() + DELTAS[button - 1]); return true; }
        if (button >= 11 && button <= 16) { part.setThreshold(part.getThreshold() + DELTAS[button - 11] * 100L); return true; }
        if (button == BUTTON_REDSTONE) { part.toggleMode(); return true; }
        return false;
    }
    @Override protected boolean quickMoveAspect(ItemStack stack) { var a = EssentiaTiles.filterAspect(stack); if (a == null || part == null) return false; part.setTracked(a); return true; }
}
