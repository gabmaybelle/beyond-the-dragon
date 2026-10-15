package com.gabbymaybelle.beyondthedragon.mixin;

import com.gabbymaybelle.beyondthedragon.aphelite.AphelitePerks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Aphelite's perks: shorter levitation from shulker bullets, and safe landings in the End. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
	private MobEffectInstance beyondTheDragon$shorterLevitation(MobEffectInstance effect, MobEffectInstance original, Entity source) {
		return AphelitePerks.adjustIncoming((LivingEntity) (Object) this, effect, source);
	}

	@Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
	private void beyondTheDragon$safeLanding(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (AphelitePerks.cancelsLanding((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}
}
