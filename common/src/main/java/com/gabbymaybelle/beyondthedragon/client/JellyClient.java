package com.gabbymaybelle.beyondthedragon.client;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The Void Jelly's client side, for the loaders' client entrypoints: the model layers to register
 * and (in {@link VoidJellyRenderer}) the renderer. Only ever loaded on a client.
 *
 * 1.21.1 has no happy ghast, so the shape is written out here: the happy ghast's (a 16-pixel body
 * and nine tentacles, from its 26.x model), so the jelly's sheets fit it as they fit the happy
 * ghast on 26.x. Every layer names its parts the way vanilla's ghast model looks them up
 * ({@code body}, {@code tentacle0} to {@code tentacle8}) so that model class animates them.
 */
public final class JellyClient {
	public static final ModelLayerLocation SHELL = new ModelLayerLocation(id("void_jelly"), "shell");
	public static final ModelLayerLocation CORE = new ModelLayerLocation(id("void_jelly"), "core");
	public static final ModelLayerLocation SADDLE = new ModelLayerLocation(id("void_jelly"), "saddle");

	/** The happy ghast's tentacles: x and z under the body, and length, as on 26.x. */
	private static final float[][] TENTACLES = {
			{-3.75F, -5.0F, 5}, {1.25F, -5.0F, 7}, {6.25F, -5.0F, 4},
			{-6.25F, 0.0F, 5}, {-1.25F, 0.0F, 5}, {3.75F, 0.0F, 7},
			{-3.75F, 5.0F, 8}, {1.25F, 5.0F, 8}, {6.25F, 5.0F, 5}};

	private JellyClient() {
	}

	/** Every model layer the jelly adds. */
	public static Map<ModelLayerLocation, Supplier<LayerDefinition>> layers() {
		return Map.of(SHELL, JellyClient::shellLayer, CORE, JellyClient::coreLayer, SADDLE, JellyClient::saddleLayer);
	}

	/** The see-through shell: the happy ghast's body and tentacles. */
	static LayerDefinition shellLayer() {
		return ghast(16.0F, CubeDeformation.NONE, true);
	}

	/** The dark core that holds the face: a 10-pixel cube in the middle of the body. */
	static LayerDefinition coreLayer() {
		return ghast(10.0F, CubeDeformation.NONE, false);
	}

	/** The saddle's straps, drawn just outside the shell. */
	static LayerDefinition saddleLayer() {
		return ghast(16.0F, new CubeDeformation(0.25F), false);
	}

	private static LayerDefinition ghast(float size, CubeDeformation deformation, boolean tentacles) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float half = size / 2.0F;
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-half, -half, -half, size, size, size, deformation),
				PartPose.offset(0.0F, 16.0F, 0.0F));
		for (int i = 0; i < TENTACLES.length; i++) {
			float[] t = TENTACLES[i];
			CubeListBuilder cube = tentacles ? CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, t[2], 2.0F)
					: CubeListBuilder.create();
			root.addOrReplaceChild("tentacle" + i, cube, PartPose.offset(t[0], 23.0F, t[1]));
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, path);
	}
}
