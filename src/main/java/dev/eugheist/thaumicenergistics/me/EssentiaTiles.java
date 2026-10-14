package dev.eugheist.thaumicenergistics.me;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import dev.eugheist.tc4modern.api.AspectContainers;

/** EssentiaTileContainerHelper: extraction and injection rules for essentia blocks (jars, reservoir, alembic...). */
public final class EssentiaTiles {
    private EssentiaTiles() {}

    /** extractFromContainer: at most what is stored, nothing when the block is not extractable. */
    public static long extract(AspectContainers.Container c, int amount, Aspect aspect, boolean simulate) {
        if (amount <= 0 || !c.canExtract()) return 0;
        int have = c.getAspects().getAmount(aspect);
        if (have == 0) return 0;
        amount = Math.min(amount, have);
        if (!simulate && !c.take(aspect, amount)) return 0;
        return amount;
    }

    /** injectEssentiaIntoContainer: limited by the permission capacity minus what is stored. */
    public static long inject(AspectContainers.Container c, int amount, Aspect aspect, boolean simulate) {
        if (!c.canInject() || !c.accepts(aspect)) return 0;
        amount = Math.min(amount, c.capacity() - c.stored());
        if (amount <= 0) return 0;
        if (!simulate) amount -= c.add(aspect, amount);
        return amount;
    }

    /** getAspectInContainer: the most plentiful aspect. */
    @Nullable public static Aspect aspectIn(AspectContainers.Container c) {
        var list = c.getAspects();
        return list.size() == 0 ? null : list.getAspectsSortedAmount()[0];
    }

    /** Stable small ids for menu data slots (both sides register the same aspects in the same order). */
    private static List<Aspect> ids;
    public static int id(@Nullable Aspect aspect) { return aspect == null ? -1 : all().indexOf(aspect); }
    @Nullable public static Aspect byId(int id) { var all = all(); return id < 0 || id >= all.size() ? null : all.get(id); }
    static List<Aspect> all() { if (ids == null || ids.size() != Aspect.aspects.size()) ids = new ArrayList<>(Aspect.aspects.values()); return ids; }

    /** EssentiaItemContainerHelper.getFilterAspectFromItem: jar labels, crystallised essentia, phials, jars, wisp essence. */
    @Nullable public static Aspect filterAspect(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return null;
        AspectList aspects = dev.eugheist.tc4modern.EssentiaItems.getAspects(stack);
        return aspects != null && aspects.size() > 0 ? aspects.getAspects()[0] : null;
    }
}
