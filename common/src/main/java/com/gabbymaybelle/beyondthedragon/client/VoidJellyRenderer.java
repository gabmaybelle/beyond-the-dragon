package com.gabbymaybelle.beyondthedragon.client;

import com.gabbymaybelle.beyondthedragon.jelly.VoidJelly;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.GhastModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SaddleLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a Void Jelly the way a slime is drawn: the dark core with the face (solid), the eyes
 * glowing on it (like a phantom's), then the see-through jelly shell over both, glowing softly
 * on its own; a saddle on top when it wears one. 1.21.1 has no happy ghast, so the shape is the
 * happy ghast's written out in {@link JellyClient}, animated by vanilla's ghast model class, at
 * the happy ghast's size.
 */
public class VoidJellyRenderer extends MobRenderer<VoidJelly, GhastModel<VoidJelly>> {
	public static final ResourceLocation CORE_TEXTURE = JellyClient.id("textures/entity/void_jelly/void_jelly_core.png");
	public static final ResourceLocation SHELL_TEXTURE = JellyClient.id("textures/entity/void_jelly/void_jelly_shell.png");
	public static final ResourceLocation EYES_TEXTURE = JellyClient.id("textures/entity/void_jelly/void_jelly_eyes.png");
	public static final ResourceLocation SADDLE_TEXTURE = JellyClient.id("textures/entity/void_jelly/void_jelly_saddle.png");

	/** The happy ghast's model is drawn four times life size; a baby a quarter of that. */
	private static final float ADULT_SCALE = 4.0F;
	private static final float BABY_SCALE = 1.0F;

	/**
	 * How brightly the shell glows on its own, as block light out of 15: the End is so dark that
	 * a lighter teal barely showed, so the shell is lit as if a lamp were near (Gabby chose the
	 * soft glow from screenshots, 2026-10-16, and 12 to match the preview she picked). Brighter
	 * surroundings still light it more.
	 */
	static final int SHELL_GLOW = 12;

	public VoidJellyRenderer(EntityRendererProvider.Context context) {
		super(context, new GhastModel<>(context.bakeLayer(JellyClient.CORE)), 2.0F);
		this.addLayer(new Eyes(this));
		this.addLayer(new Shell(this, new GhastModel<>(context.bakeLayer(JellyClient.SHELL))));
		this.addLayer(new SaddleLayer<>(this, new GhastModel<>(context.bakeLayer(JellyClient.SADDLE)), SADDLE_TEXTURE));
	}

	@Override
	public ResourceLocation getTextureLocation(VoidJelly jelly) {
		return CORE_TEXTURE;
	}

	@Override
	protected void scale(VoidJelly jelly, PoseStack poseStack, float partialTicks) {
		float scale = jelly.isBaby() ? BABY_SCALE : ADULT_SCALE;
		poseStack.scale(scale, scale, scale);
	}

	/** The face's eyes, full bright whatever the light, seen through the shell. */
	private static final class Eyes extends EyesLayer<VoidJelly, GhastModel<VoidJelly>> {
		private static final RenderType EYES = RenderType.eyes(EYES_TEXTURE);

		Eyes(RenderLayerParent<VoidJelly, GhastModel<VoidJelly>> renderer) {
			super(renderer);
		}

		@Override
		public RenderType renderType() {
			return EYES;
		}
	}

	/** The see-through jelly: the happy ghast's body and tentacles, drawn last, glowing softly. */
	private static final class Shell extends RenderLayer<VoidJelly, GhastModel<VoidJelly>> {
		private final GhastModel<VoidJelly> model;

		Shell(RenderLayerParent<VoidJelly, GhastModel<VoidJelly>> renderer, GhastModel<VoidJelly> model) {
			super(renderer);
			this.model = model;
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffers, int light, VoidJelly jelly, float limbSwing, float limbSwingAmount,
				float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
			if (jelly.isInvisible()) {
				return;
			}
			getParentModel().copyPropertiesTo(model);
			model.prepareMobModel(jelly, limbSwing, limbSwingAmount, partialTicks);
			model.setupAnim(jelly, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
			int glow = LightTexture.pack(Math.max(LightTexture.block(light), SHELL_GLOW), LightTexture.sky(light));
			model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityTranslucent(SHELL_TEXTURE)), glow,
					LivingEntityRenderer.getOverlayCoords(jelly, 0.0F));
		}
	}
}
