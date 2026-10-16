package com.gabbymaybelle.beyondthedragon.neoforge;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.gabbymaybelle.beyondthedragon.client.JellyClient;
import com.gabbymaybelle.beyondthedragon.client.VoidJellyRenderer;
import com.gabbymaybelle.beyondthedragon.jelly.Jellies;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The NeoForge client entrypoint (clients only): the Void Jelly's model layers and renderer. */
@Mod(value = BeyondTheDragon.MOD_ID, dist = Dist.CLIENT)
public class BeyondTheDragonNeoForgeClient {
	public BeyondTheDragonNeoForgeClient(IEventBus modBus) {
		modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class,
				event -> JellyClient.layers().forEach(event::registerLayerDefinition));
		modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
				event -> event.registerEntityRenderer(Jellies.VOID_JELLY.get(), VoidJellyRenderer::new));
	}
}
