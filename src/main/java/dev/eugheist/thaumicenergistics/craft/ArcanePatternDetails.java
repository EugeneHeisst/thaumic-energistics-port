package dev.eugheist.thaumicenergistics.craft;

import java.util.ArrayList;
import java.util.List;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;

/**
 * An arcane pattern as an AE2 crafting pattern. Its definition is an arcane pattern item carrying the pattern, so crafting
 * jobs survive saving through the registered decoder. Only the arcane assembler accepts it.
 */
public final class ArcanePatternDetails implements IPatternDetails {
    final AEItemKey definition;
    public final ArcanePattern pattern;
    final IInput[] inputs;

    public ArcanePatternDetails(AEItemKey definition, ArcanePattern pattern) {
        this.definition = definition;this.pattern = pattern;
        var list = new ArrayList<IInput>();
        for (int i = 0; i < ArcanePattern.SIZE; i++) {
            var options = pattern.slots().get(i);
            if (options.isEmpty()) continue;
            var stacks = options.stream().map(s -> new GenericStack(AEItemKey.of(s), 1)).toArray(GenericStack[]::new);
            list.add(new IInput() {
                public GenericStack[] getPossibleInputs() { return stacks; }
                public long getMultiplier() { return 1; }
                public boolean isValid(AEKey key, Level level) { for (var s : stacks) if (s.what().equals(key)) return true; return false; }
                @Nullable public AEKey getRemainingKey(AEKey key) { return null; }
            });
        }
        inputs = list.toArray(IInput[]::new);
    }

    public static AEItemKey definitionOf(ArcanePattern pattern, net.minecraft.core.HolderLookup.Provider registries) {
        var stack = new ItemStack(ThaumicEnergistics.ARCANE_PATTERN.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(pattern.save(registries)));
        return AEItemKey.of(stack);
    }

    @Override public AEItemKey getDefinition() { return definition; }
    @Override public IInput[] getInputs() { return inputs; }
    @Override public List<GenericStack> getOutputs() { return List.of(GenericStack.fromItemStack(pattern.output())); }
    @Override public boolean supportsPushInputsToExternalInventory() { return false; }
    @Override public boolean equals(Object o) { return o instanceof ArcanePatternDetails d && d.definition.equals(definition); }
    @Override public int hashCode() { return definition.hashCode(); }

    public static final IPatternDetailsDecoder DECODER = new IPatternDetailsDecoder() {
        public boolean isEncodedPattern(ItemStack stack) { return stack.is(ThaumicEnergistics.ARCANE_PATTERN.get()); }
        @Nullable public IPatternDetails decodePattern(AEItemKey what, Level level) {
            if (level == null || what.getItem() != ThaumicEnergistics.ARCANE_PATTERN.get()) return null;
            var data = what.get(DataComponents.CUSTOM_DATA);
            if (data == null) return null;
            var pattern = ArcanePattern.load(data.copyTag(), level.registryAccess());
            return pattern == null ? null : new ArcanePatternDetails(what, pattern);
        }
    };
}
