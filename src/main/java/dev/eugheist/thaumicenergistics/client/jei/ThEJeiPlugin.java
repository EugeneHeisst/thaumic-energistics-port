package dev.eugheist.thaumicenergistics.client.jei;

import java.util.ArrayList;
import java.util.List;
import appeng.api.stacks.AEItemKey;
import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import dev.eugheist.tc4modern.ArcaneGridTransfer;
import dev.eugheist.tc4modern.TC4JeiPlugin;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.menu.ArcaneTermMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** NEI overlay handler of the arcane crafting terminal, now JEI: crafting and arcane recipes fill its grid from the network. */
@JeiPlugin
public final class ThEJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() { return ThaumicEnergistics.id("jei"); }

    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration r) {
        var h = r.getTransferHelper();
        r.addRecipeTransferHandler(new Terminal<>(TC4JeiPlugin.ARCANE, h), TC4JeiPlugin.ARCANE);
        r.addRecipeTransferHandler(new Terminal<>(RecipeTypes.CRAFTING, h), RecipeTypes.CRAFTING);
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration r) {
        r.addRecipeCatalyst(new ItemStack(ThaumicEnergistics.ARCANE_CRAFTING_TERMINAL.get()), TC4JeiPlugin.ARCANE, RecipeTypes.CRAFTING);
    }

    static final class Terminal<R> extends ArcaneGridTransfer<ArcaneTermMenu, R> {
        Terminal(RecipeType<R> type, IRecipeTransferHandlerHelper helper) { super(ArcaneTermMenu.class, type, helper); }
        @Override protected List<ItemStack> available(ArcaneTermMenu menu, Player player) {
            var list = new ArrayList<ItemStack>();
            var repo = menu.getClientRepo();
            if (repo != null) for (var e : repo.getAllEntries()) if (e.getWhat() instanceof AEItemKey key && e.getStoredAmount() > 0) list.add(key.toStack((int) Math.min(e.getStoredAmount(), 576)));
            for (var s : player.getInventory().items) if (!s.isEmpty()) list.add(s.copy());
            for (var s : menu.getCraftingMatrix()) if (!s.isEmpty()) list.add(s.copy());
            return list;
        }
        @Override protected void send(ArcaneTermMenu menu, List<ItemStack> templates) {
            PacketDistributor.sendToServer(new FillCraftingGridFromRecipePacket(null, NonNullList.of(ItemStack.EMPTY, templates.toArray(ItemStack[]::new)), false));
        }
    }
}
