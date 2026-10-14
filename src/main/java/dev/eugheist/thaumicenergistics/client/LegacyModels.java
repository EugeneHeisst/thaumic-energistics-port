package dev.eugheist.thaumicenergistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.eugheist.thaumicenergistics.ThaumicEnergistics;
import dev.eugheist.thaumicenergistics.block.ArcaneAssemblerEntity;
import dev.eugheist.thaumicenergistics.block.GearBoxEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** ModelGearbox/RenderTileGearbox and ModelArcaneAssembler/RenderTileArcaneAssembler, plus TileAsItemRenderer for their items. */
public final class LegacyModels {
    private LegacyModels() {}
    static final float HALF_TURN = 1.5707964f, QUARTER_TURN = .7853982f, EDGE = 3.12f;
    static final ResourceLocation IRON = ThaumicEnergistics.id("textures/models/gearbox_iron.png"), THAUMIUM = ThaumicEnergistics.id("textures/models/gearbox_thaumium.png"), ASSEMBLER = ThaumicEnergistics.id("textures/models/arcane_assembler.png");
    public static int gearboxes, assemblers;

    static ModelPart box(int texWidth, int texHeight, int u, int v, float x, float y, float z, int w, int h, int d, float px, float py, float pz) {
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("box", CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d), PartPose.offset(px, py, pz));
        return LayerDefinition.create(mesh, texWidth, texHeight).bakeRoot().getChild("box");
    }
    static void rot(ModelPart p, float x, float y, float z) { p.xRot = x;p.yRot = y;p.zRot = z; }
    /** ModelRenderer.offsetX/Y/Z: a plain translation before the pivot, in the (already scaled) model units. */
    static void render(ModelPart p, PoseStack pose, VertexConsumer vc, int light, int overlay, float ox, float oy, float oz) {
        pose.pushPose();pose.translate(ox, oy, oz);p.render(pose, vc, light, overlay);pose.popPose();
    }

    // ModelGearbox (boxes built at texture size 32 before setTextureSize(64, 64), so 32 is what the UVs use)
    static final ModelPart TRANSMISSION = box(32, 32, 1, 0, 0, 0, 0, 7, 7, 7, -3.5f, -3.5f, -3.5f), SHAFT = box(32, 32, 1, 15, -1.5f, -.5f, -.5f, 3, 1, 1, 0, 0, 0), SPOKE = box(32, 32, 1, 18, -.5f, -3.5f, -.5f, 1, 7, 1, 0, 0, 0);
    /** SideTransformation per Direction: rotateAngleY, rotateAngleZ and the shaft offset. */
    static float[] side(Direction d) {
        return switch (d) {
            case EAST -> new float[]{0, 0, EDGE, 0, 0}; case WEST -> new float[]{0, 0, -EDGE, 0, 0};
            case UP -> new float[]{0, HALF_TURN, 0, EDGE, 0}; case DOWN -> new float[]{0, HALF_TURN, 0, -EDGE, 0};
            case SOUTH -> new float[]{HALF_TURN, 0, 0, 0, EDGE}; case NORTH -> new float[]{HALF_TURN, 0, 0, 0, -EDGE};
        };
    }
    /** RenderTileGearbox: centre, scale 0.12, model at f5 0.625; gears only on the sides without a crankable. Old y-up model space equals block space here. */
    static void gearbox(PoseStack pose, MultiBufferSource buffers, int light, boolean thaumium, float rotation, boolean[] crankable) {
        gearboxes++;
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(thaumium ? THAUMIUM : IRON));int overlay = OverlayTexture.NO_OVERLAY;
        pose.pushPose();pose.translate(.5, .5, .5);pose.scale(.12f, .12f, .12f);
        // f5 = 0.625 instead of 0.0625: the parts' own 1/16 is undone by a x10 scale, offsets stay in the 0.12 space.
        pose.pushPose();pose.scale(10, 10, 10);TRANSMISSION.render(pose, vc, light, overlay);pose.popPose();
        for (var d : Direction.values()) {
            var t = side(d);
            pose.pushPose();pose.translate(t[2], t[3], t[4]);pose.scale(10, 10, 10);rot(SHAFT, rotation, t[0], t[1]);SHAFT.render(pose, vc, light, overlay);pose.popPose();
            if (!crankable[d.ordinal()]) for (int k = 1; k <= 4; k++) {
                pose.pushPose();pose.translate(t[2] * 1.2f, t[3] * 1.2f, t[4] * 1.2f);pose.scale(10, 10, 10);rot(SPOKE, rotation + k * QUARTER_TURN, t[0], t[1]);SPOKE.render(pose, vc, light, overlay);pose.popPose();
            }
        }
        pose.popPose();
    }

    // ModelArcaneAssembler
    static final ModelPart OUTER = box(64, 64, 0, 0, -17, -17, -15, 34, 2, 2, 0, 0, 0), OUTER_BOTTOM = box(64, 64, 0, 0, -17, 15, -15, 34, 2, 2, 0, 0, 0),
        GLASS = box(64, 64, 0, 37, -13, -13, -14, 26, 26, 1, 0, 0, 0), INNER = box(64, 64, 0, 4, -15, -15, -15, 30, 2, 2, 0, 0, 0), INNER_BOTTOM = box(64, 64, 0, 4, -15, 13, -15, 30, 2, 2, 0, 0, 0);
    /** RenderTileArcaneAssembler: centre, scale 0.047, f5 0.625. Like the TESR, no y flip: the old model space is block space. */
    static void assembler(PoseStack pose, MultiBufferSource buffers, int light) {
        assemblers++;
        int overlay = OverlayTexture.NO_OVERLAY;
        pose.pushPose();pose.translate(.5, .5, .5);pose.scale(.047f * 10, .047f * 10, .047f * 10);
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ASSEMBLER));
        for (float r : new float[]{0, HALF_TURN, -HALF_TURN, (float)Math.PI}) {
            rot(OUTER, 0, r, 0);OUTER.render(pose, vc, light, overlay);
            rot(OUTER_BOTTOM, 0, r, 0);OUTER_BOTTOM.render(pose, vc, light, overlay);
            rot(OUTER, -r, 0, -1.570796f);OUTER.render(pose, vc, light, overlay);
            rot(OUTER, r, 0, 1.570796f);OUTER.render(pose, vc, light, overlay);
        }
        float[][] inner = {{0, 0}, {HALF_TURN, 0}, {-HALF_TURN, 0}, {(float)Math.PI, 0}, {0, HALF_TURN}, {0, -HALF_TURN}};
        for (var a : inner) {
            rot(INNER, a[1], a[0], 0);INNER.render(pose, vc, light, overlay);
            rot(INNER_BOTTOM, a[1], a[0], 0);INNER_BOTTOM.render(pose, vc, light, overlay);
            rot(INNER, a[1], a[0], -1.570796f);INNER.render(pose, vc, light, overlay);
            rot(INNER, a[1], a[0], 1.570796f);INNER.render(pose, vc, light, overlay);
        }
        var glass = buffers.getBuffer(RenderType.entityTranslucent(ASSEMBLER));
        for (var a : inner) { rot(GLASS, a[1], a[0], 0);GLASS.render(pose, glass, light, overlay); }
        pose.popPose();
    }

    public static final class GearboxRenderer implements BlockEntityRenderer<GearBoxEntity> {
        @Override public void render(GearBoxEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            var crankable = new boolean[6];
            for (var d : Direction.values()) crankable[d.ordinal()] = appeng.api.implementations.blockentities.ICrankable.get(be.getLevel(), be.getBlockPos().relative(d), d.getOpposite()) != null;
            float rotation = be.shaftRotation == 0 ? 0 : Math.max(0, be.shaftRotation - .2f * partial);
            gearbox(pose, buffers, light, be.thaumium(), rotation, crankable);
        }
    }
    public static final class AssemblerRenderer implements BlockEntityRenderer<ArcaneAssemblerEntity> {
        @Override public void render(ArcaneAssemblerEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) { assembler(pose, buffers, light); }
    }
    public static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
        public static final ItemRenderer INSTANCE = new ItemRenderer();
        ItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
        @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (stack.is(ThaumicEnergistics.ARCANE_ASSEMBLER_ITEM.get())) assembler(pose, buffers, light);
            else gearbox(pose, buffers, light, stack.is(ThaumicEnergistics.THAUMIUM_GEARBOX_ITEM.get()), 0, new boolean[6]);
        }
    }
}
