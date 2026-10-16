package com.gabbymaybelle.beyondthedragon.client;

import com.gabbymaybelle.beyondthedragon.jelly.VoidJelly;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.ghast.HappyGhastHarnessModel;
import net.minecraft.client.model.animal.ghast.HappyGhastModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.RopesLayer;
import net.minecraft.client.renderer.entity.state.HappyGhastRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Draws a Void Jelly in three passes, the way a slime is drawn: the dark core with the face
 * (solid), the eyes glowing on it (like a phantom's), then the see-through jelly shell over both,
 * with the happy ghast's body and tentacles as its shape. The harness is the happy ghast's
 * model with the jelly's own sheets ({@link #harnessTexture}); the ropes are the happy ghast's.
 */
public class VoidJellyRenderer extends AgeableMobRenderer<VoidJelly, HappyGhastRenderState, HappyGhastModel> {
	public static final Identifier CORE_TEXTURE = JellyClient.id("textures/entity/void_jelly/void_jelly_core.png");
	public static final Identifier SHELL_TEXTURE = JellyClient.id("textures/entity/void_jelly/void_jelly_shell.png");
	public static final Identifier EYES_TEXTURE = JellyClient.id("textures/entity/void_jelly/void_jelly_eyes.png");
	private static final Identifier ROPES_TEXTURE = Identifier.withDefaultNamespace("textures/entity/ghast/happy_ghast_ropes.png");

	public VoidJellyRenderer(EntityRendererProvider.Context context) {
		super(context,
				new HappyGhastModel(context.bakeLayer(JellyClient.CORE)),
				new HappyGhastModel(context.bakeLayer(JellyClient.CORE_BABY)),
				2.0F);
		this.addLayer(new Eyes(this));
		this.addLayer(new Shell(this, context.getModelSet()));
		this.addLayer(new Harness(this, context.getModelSet()));
		this.addLayer(new RopesLayer<>(this, context.getModelSet(), ROPES_TEXTURE));
	}

	@Override
	public Identifier getTextureLocation(HappyGhastRenderState state) {
		return CORE_TEXTURE;
	}

	@Override
	public HappyGhastRenderState createRenderState() {
		return new HappyGhastRenderState();
	}

	@Override
	protected AABB getBoundingBoxForCulling(VoidJelly entity, float partialTicks) {
		AABB aabb = super.getBoundingBoxForCulling(entity, partialTicks);
		return aabb.setMinY(aabb.minY - entity.getBbHeight() / 2.0F);
	}

	@Override
	public void extractRenderState(VoidJelly entity, HappyGhastRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.bodyItem = entity.getItemBySlot(EquipmentSlot.BODY).copy();
		state.isRidden = entity.isVehicle();
		state.isLeashHolder = entity.isLeashHolder();
	}

	/** The face's eyes, full bright whatever the light, seen through the shell. */
	private static final class Eyes extends EyesLayer<HappyGhastRenderState, HappyGhastModel> {
		private static final RenderType EYES = RenderTypes.eyes(EYES_TEXTURE);

		Eyes(RenderLayerParent<HappyGhastRenderState, HappyGhastModel> renderer) {
			super(renderer);
		}

		@Override
		public RenderType renderType() {
			return EYES;
		}
	}

	/**
	 * Where a harness's jelly sheet is: the happy ghast's own covers the top half of the body in
	 * cloth, which hid the shell and made a ridden jelly look like a happy ghast, so the jelly
	 * has its own sheets (leather straps and goggles only; `tools/JellyTextureGen.java`), one per colour,
	 * named after the harness's equipment asset.
	 */
	public static @Nullable Identifier harnessTexture(ItemStack harness) {
		Equippable equippable = harness.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.assetId().isEmpty()) {
			return null;
		}
		return JellyClient.id("textures/entity/void_jelly/harness/" + equippable.assetId().get().identifier().getPath() + ".png");
	}

	/** The harness, drawn from the jelly's own sheets on the happy ghast's harness model. */
	private static final class Harness extends RenderLayer<HappyGhastRenderState, HappyGhastModel> {
		private final HappyGhastHarnessModel adult;
		private final HappyGhastHarnessModel baby;

		Harness(RenderLayerParent<HappyGhastRenderState, HappyGhastModel> renderer, EntityModelSet models) {
			super(renderer);
			this.adult = new HappyGhastHarnessModel(models.bakeLayer(ModelLayers.HAPPY_GHAST_HARNESS));
			this.baby = new HappyGhastHarnessModel(models.bakeLayer(ModelLayers.HAPPY_GHAST_BABY_HARNESS));
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, HappyGhastRenderState state, float yRot, float xRot) {
			if (!state.bodyItem.is(ItemTags.HARNESSES)) {
				return;
			}
			Identifier texture = harnessTexture(state.bodyItem);
			if (texture == null) {
				return;
			}
			HappyGhastHarnessModel model = state.isBaby ? baby : adult;
			collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(texture), light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		}
	}

	/**
	 * How brightly the shell glows on its own, as block light out of 15: the End is so dark that
	 * a lighter teal barely showed, so the shell is lit as if a lamp were near (Gabby chose the
	 * soft glow from screenshots, 2026-10-16, and 12 to match the preview she picked). Brighter
	 * surroundings still light it more.
	 */
	static final int SHELL_GLOW = 12;

	/** The see-through jelly: the happy ghast's full body and tentacles, drawn last. */
	private static final class Shell extends RenderLayer<HappyGhastRenderState, HappyGhastModel> {
		private final HappyGhastModel adult;
		private final HappyGhastModel baby;

		Shell(RenderLayerParent<HappyGhastRenderState, HappyGhastModel> renderer, EntityModelSet models) {
			super(renderer);
			this.adult = new HappyGhastModel(models.bakeLayer(ModelLayers.HAPPY_GHAST));
			this.baby = new HappyGhastModel(models.bakeLayer(ModelLayers.HAPPY_GHAST_BABY));
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, HappyGhastRenderState state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			HappyGhastModel model = state.isBaby ? baby : adult;
			int glow = LightCoordsUtil.withBlock(light, Math.max(LightCoordsUtil.block(light), SHELL_GLOW));
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(SHELL_TEXTURE), glow,
					LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
		}
	}
}
