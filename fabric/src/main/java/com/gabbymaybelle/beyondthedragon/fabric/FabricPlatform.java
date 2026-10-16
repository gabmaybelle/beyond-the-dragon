package com.gabbymaybelle.beyondthedragon.fabric;

import com.gabbymaybelle.beyondthedragon.platform.Platform;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class FabricPlatform implements Platform {
	@Override
	public String name() {
		return "Fabric";
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Supplier<T> register(ResourceKey<? extends Registry<T>> registry, Identifier id, Supplier<T> factory) {
		Registry<T> target = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(registry.identifier());
		T value = Registry.register(target, id, factory.get());
		return () -> value;
	}

	@Override
	public void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(item.get()));
	}

	@Override
	public void addOreToEndBiomes(ResourceKey<PlacedFeature> feature) {
		BiomeModifications.addFeature(BiomeSelectors.foundInTheEnd(), GenerationStep.Decoration.UNDERGROUND_ORES, feature);
	}

	@Override
	public void addCreatureToEndBiomes(Supplier<? extends EntityType<?>> type) {
		BiomeModifications.addSpawn(BiomeSelectors.foundInTheEnd(), MobCategory.CREATURE, type.get(), 10, 1, 2);
	}

	@Override
	public <T extends LivingEntity> void registerAttributes(Supplier<EntityType<T>> type, Supplier<AttributeSupplier.Builder> attributes) {
		FabricDefaultAttributeRegistry.register(type.get(), attributes.get());
	}
}
