package com.gabbymaybelle.beyondthedragon.client;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.model.animal.ghast.HappyGhastModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * The Void Jelly's client side, for the loaders' client entrypoints: the model layers to register
 * and (in {@link VoidJellyRenderer}) the renderer. Only ever loaded on a client.
 */
public final class JellyClient {
	public static final ModelLayerLocation CORE = new ModelLayerLocation(id("void_jelly"), "core");
	public static final ModelLayerLocation CORE_BABY = new ModelLayerLocation(id("void_jelly_baby"), "core");

	private JellyClient() {
	}

	/** Every model layer the jelly adds; the shell reuses the happy ghast's own. */
	public static Map<ModelLayerLocation, Supplier<LayerDefinition>> layers() {
		return Map.of(
				CORE, () -> coreLayer(),
				CORE_BABY, () -> coreLayer().apply(HappyGhastModel.BABY_TRANSFORMER));
	}

	/**
	 * The dark core that holds the face: a 10-pixel cube in the middle of the happy ghast's
	 * 16-pixel body, at the same 4x scale. It keeps the ghast's part names (with empty
	 * tentacles) so the happy ghast's model class can animate it; the tentacles belong to the
	 * shell.
	 */
	static LayerDefinition coreLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body",
				CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -5.0F, -5.0F, 10.0F, 10.0F, 10.0F),
				PartPose.offset(0.0F, 16.0F, 0.0F));
		for (int i = 0; i < 9; i++) {
			body.addOrReplaceChild(PartNames.tentacle(i), CubeListBuilder.create(), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, 64).apply(MeshTransformer.scaling(4.0F));
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, path);
	}
}
