package com.gabbymaybelle.beyondthedragon.jelly;

import com.gabbymaybelle.beyondthedragon.BeyondTheDragon;
import com.gabbymaybelle.beyondthedragon.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * The Void Jelly and what goes with it: the entity type and the chorus flower on a stick that
 * steers it. Touching the class registers them.
 */
public final class Jellies {
	public static final ResourceKey<EntityType<?>> VOID_JELLY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("void_jelly"));
	/** The happy ghast's size, seats and tracking range: the jelly is its body under a new skin. */
	public static final Supplier<EntityType<VoidJelly>> VOID_JELLY = entityType(() -> EntityType.Builder.of(VoidJelly::new, MobCategory.CREATURE)
			.sized(4.0F, 4.0F)
			.eyeHeight(2.6F)
			.passengerAttachments(new Vec3(0.0, 4.0, 1.7), new Vec3(-1.7, 4.0, 0.0), new Vec3(0.0, 4.0, -1.7), new Vec3(1.7, 4.0, 0.0))
			.ridingOffset(0.5F)
			.clientTrackingRange(10)
			.build(VOID_JELLY_KEY));

	/** Crafted like a carrot on a stick. It only steers: no boost, so it never wears out. */
	public static final Supplier<Item> CHORUS_STICK = Services.PLATFORM.register(Registries.ITEM, id("chorus_flower_on_a_stick"),
			() -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("chorus_flower_on_a_stick"))).stacksTo(1)));

	private Jellies() {
	}

	public static void register() {
		Services.PLATFORM.registerAttributes(VOID_JELLY, VoidJelly::createAttributes);
		Services.PLATFORM.addCreatureToEndBiomes(VOID_JELLY);
		Services.PLATFORM.addToCreativeTab(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities")), CHORUS_STICK);
	}

	@SuppressWarnings("unchecked")
	private static <T extends net.minecraft.world.entity.Entity> Supplier<EntityType<T>> entityType(Supplier<EntityType<T>> factory) {
		Supplier<EntityType<?>> registered = Services.PLATFORM.register(Registries.ENTITY_TYPE, VOID_JELLY_KEY.identifier(), factory::get);
		return () -> (EntityType<T>) registered.get();
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(BeyondTheDragon.MOD_ID, path);
	}
}
