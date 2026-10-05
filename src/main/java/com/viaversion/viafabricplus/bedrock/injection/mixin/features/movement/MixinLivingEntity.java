/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.building.BedrockSprint;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Shadow
    public abstract @Nullable MobEffectInstance getEffect(final Holder<MobEffect> effect);

    @Shadow
    @Final
    private static Identifier SPRINTING_MODIFIER_ID;

    @Shadow
    public abstract @Nullable AttributeInstance getAttribute(final Holder<Attribute> attribute);

    /**
     * Bedrock computes the speed from its base value and modifiers again when its sprint modifier is added, or removed
     * while there, which forgets a speed the server set beyond what its modifiers give.
     */
    @Inject(method = "setSprinting", at = @At("HEAD"))
    private void computeSpeedAgain(final boolean sprinting, final CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player || !ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return;
        }
        final AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && (sprinting || speed.getModifier(SPRINTING_MODIFIER_ID) != null)) {
            speed.removeModifier(BedrockSprint.SERVER_VALUE_MODIFIER);
        }
    }

    @Redirect(method = "getFluidFallingAdjustedMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSprinting()Z"))
    private boolean changeFluidGravityCondition(final LivingEntity instance) {
        return ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) ? instance.isSwimming() : instance.isSprinting();
    }

    @Inject(method = "getFluidFallingAdjustedMovement", at = @At("HEAD"), cancellable = true)
    private void applyLevitationVelocity(final double baseGravity, final boolean isFalling, final Vec3 movement, final CallbackInfoReturnable<Vec3> cir) {
        final MobEffectInstance effect = this.getEffect(MobEffects.LEVITATION);
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) && effect != null) {
            cir.setReturnValue(new Vec3(movement.x, movement.y + (((effect.getAmplifier() + 1) * 0.05) - movement.y) * 0.2, movement.z));
        }
    }


}
