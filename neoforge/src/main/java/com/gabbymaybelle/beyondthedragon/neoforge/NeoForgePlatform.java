package com.gabbymaybelle.beyondthedragon.neoforge;

import com.gabbymaybelle.beyondthedragon.platform.Platform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * NeoForge only lets mods register during its registry events, so everything common code
 * registers is queued here and handed over when {@link #onRegister} fires for that registry.
 */
public class NeoForgePlatform implements Platform {
	private record Pending<T>(ResourceKey<? extends Registry<T>> registry, Identifier id, Supplier<T> factory) {
		void offer(RegisterEvent event) {
			if (event.getRegistryKey().equals(registry)) {
				event.register(registry, id, factory);
			}
		}
	}

	private record TabEntry(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
	}

	private final List<Pending<?>> pending = new ArrayList<>();
	private final List<TabEntry> tabEntries = new ArrayList<>();
	private final List<Runnable> attributes = new ArrayList<>();
	private EntityAttributeCreationEvent attributeEvent;

	@Override
	public String name() {
		return "NeoForge";
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Supplier<T> register(ResourceKey<? extends Registry<T>> registry, Identifier id, Supplier<T> factory) {
		pending.add(new Pending<>(registry, id, factory));
		ResourceKey<T> key = ResourceKey.create(registry, id);
		return new Supplier<>() {
			private T value;

			@Override
			public T get() {
				if (value == null) {
					Registry<T> target = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(registry.identifier());
					value = target.getValue(key);
					if (value == null) {
						throw new IllegalStateException(id + " isn't registered yet");
					}
				}
				return value;
			}
		};
	}

	@Override
	public void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
		tabEntries.add(new TabEntry(tab, item));
	}

	@Override
	public void addOreToEndBiomes(ResourceKey<PlacedFeature> feature) {
		// Done by data/beyond_the_dragon/neoforge/biome_modifier/aphelite_ore.json.
	}

	@Override
	public <T extends LivingEntity> void registerAttributes(Supplier<EntityType<T>> type, Supplier<AttributeSupplier.Builder> builder) {
		attributes.add(() -> attributeEvent.put(type.get(), builder.get().build()));
	}

	void onAttributes(EntityAttributeCreationEvent event) {
		attributeEvent = event;
		attributes.forEach(Runnable::run);
		attributeEvent = null;
	}

	void onRegister(RegisterEvent event) {
		for (Pending<?> entry : pending) {
			entry.offer(event);
		}
	}

	void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
		for (TabEntry entry : tabEntries) {
			if (event.getTabKey().equals(entry.tab())) {
				event.accept(entry.item().get());
			}
		}
	}
}
