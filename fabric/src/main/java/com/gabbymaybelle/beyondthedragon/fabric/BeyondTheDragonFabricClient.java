package com.gabbymaybelle.beyondthedragon.fabric;

import com.gabbymaybelle.beyondthedragon.client.JellyClient;
import com.gabbymaybelle.beyondthedragon.client.VoidJellyRenderer;
import com.gabbymaybelle.beyondthedragon.jelly.Jellies;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;

/** The Fabric client entrypoint: the Void Jelly's model layers and renderer. */
public class BeyondTheDragonFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		JellyClient.layers().forEach((location, layer) -> EntityModelLayerRegistry.registerModelLayer(location, layer::get));
		EntityRendererRegistry.register(Jellies.VOID_JELLY.get(), VoidJellyRenderer::new);
	}
}
