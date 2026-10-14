package dev.eugheist.thaumicenergistics.item;

import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.me.EssentiaCellInventory;
import dev.eugheist.thaumicenergistics.me.EssentiaStorageType;

/** ItemEssentiaCell: original tooltip lines and sneak-use disassembly into component + casing when empty. */
public final class EssentiaCellItem extends Item {
    public final EssentiaStorageType type;
    public EssentiaCellItem(EssentiaStorageType type) { super(new Properties().stacksTo(1).rarity(type.rarity)); this.type = type; }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var cell = EssentiaCellInventory.of(stack, null);
        if (cell == null) return;
        lines.add(Component.translatable("thaumicenergistics.tooltip.essentia.cell.bytes", cell.getUsedBytes(), cell.getTotalBytes()));
        lines.add(Component.translatable("thaumicenergistics.tooltip.essentia.cell.types", cell.getUsedTypes(), cell.getTotalTypes()));
        if (cell.isPartitioned()) lines.add(Component.translatable("gui.ae2.Partitioned"));
        if (cell.getUsedTypes() <= 0) return;
        if (!ThaumicEnergistics.clientShiftDown()) { lines.add(Component.translatable("thaumicenergistics.tooltip.itemstack.details").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)); return; }
        var stored = cell.getStored();
        stored.sort(Comparator.comparing(entry -> ((Aspect) entry[0]).getName()));
        for (var entry : stored) { var aspect = (Aspect) entry[0]; lines.add(Component.literal(aspect.getChatcolor() + aspect.getName() + ChatFormatting.WHITE + " x " + entry[1])); }
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || type.creative()) return InteractionResultHolder.pass(stack);
        var cell = EssentiaCellInventory.of(stack, null);
        if (cell == null || cell.getUsedBytes() != 0) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            if (!player.getInventory().add(new ItemStack(ThaumicEnergistics.CELL_CASING.get()))) return InteractionResultHolder.pass(stack);
            return InteractionResultHolder.success(new ItemStack(ThaumicEnergistics.COMPONENTS.get(type).get()));
        }
        return InteractionResultHolder.success(stack);
    }
}
