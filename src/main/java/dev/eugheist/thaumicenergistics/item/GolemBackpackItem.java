package dev.eugheist.thaumicenergistics.item;

import java.util.List;
import appeng.api.features.IGridLinkableHandler;
import appeng.api.ids.AEComponents;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/** ItemGolemWirelessBackpack: stack size 1, linked to a network in a wireless access point (the old encryption key). */
public class GolemBackpackItem extends Item {
    public static final IGridLinkableHandler LINKABLE_HANDLER = new IGridLinkableHandler() {
        @Override public boolean canLink(ItemStack stack) { return stack.getItem() instanceof GolemBackpackItem; }
        @Override public void link(ItemStack stack, GlobalPos pos) { stack.set(AEComponents.WIRELESS_LINK_TARGET, pos); }
        @Override public void unlink(ItemStack stack) { stack.remove(AEComponents.WIRELESS_LINK_TARGET); }
    };

    public GolemBackpackItem(Properties props) { super(props.stacksTo(1)); }

    @Nullable public static GlobalPos link(ItemStack stack) { return stack.get(AEComponents.WIRELESS_LINK_TARGET); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(link(stack) == null ? Tooltips.of(GuiText.Unlinked, Tooltips.RED) : Tooltips.of(GuiText.Linked, Tooltips.GREEN));
    }
}
