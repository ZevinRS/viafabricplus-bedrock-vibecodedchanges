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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.entity;

import com.viaversion.viafabricplus.bedrock.injection.access.IEntityRenderState;
import com.viaversion.viafabricplus.bedrock.render.BedrockSkinRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws players with 4D skins with the renderer of their skin, see {@link BedrockSkinRenderer}. Java looks renderers up
 * by the entity when it extracts the render state and by the state when it draws.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void useSkinRenderer(final T entity, final CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        if (entity instanceof final AbstractClientPlayer player) {
            final BedrockSkinRenderer renderer = BedrockSkinRenderer.of(player);
            if (renderer != null) {
                cir.setReturnValue((EntityRenderer) renderer);
            }
        }
    }

    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;", at = @At("HEAD"), cancellable = true)
    private void useSkinRenderer(final AvatarRenderState state, final CallbackInfoReturnable<AvatarRenderer<?>> cir) {
        if (((IEntityRenderState) state).viaFabricPlusBedrock$getSkinRenderer() instanceof final BedrockSkinRenderer renderer) {
            cir.setReturnValue(renderer);
        }
    }

}
