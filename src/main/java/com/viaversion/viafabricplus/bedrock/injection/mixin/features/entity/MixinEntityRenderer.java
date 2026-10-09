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

import com.viaversion.viafabricplus.bedrock.feature.Features;
import com.mojang.blaze3d.vertex.PoseStack;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.injection.access.IEntityRenderState;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockNameTags;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the name tags of Bedrock entities like Bedrock, see {@link BedrockNameTags}.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity, S extends EntityRenderState> {

    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V", at = @At("TAIL"))
    private void extractBedrockNameTag(final T entity, final S state, final float partialTicks, final double nameTagDistance, final double belowNameDistance, final CallbackInfo ci) {
        final IEntityRenderState bedrockState = (IEntityRenderState) state;
        bedrockState.viaFabricPlusBedrock$setNameLines(null);
        bedrockState.viaFabricPlusBedrock$setBodyHidden(false);
        if (!Features.NAME_TAGS.isActive()) {
            return;
        }
        final BedrockNameTags.NameTag nameTag = BedrockNameTags.get(entity.getId());
        if (nameTag == null) {
            return;
        }
        bedrockState.viaFabricPlusBedrock$setBodyHidden(nameTag.hidesBody());
        final boolean shown = !nameTag.lines().isEmpty() && state.distanceToCameraSq < Mth.square(nameTagDistance)
            && (nameTag.alwaysShow() || nameTag.canShow() && entity == this.entityRenderDispatcher.crosshairPickEntity);
        if (!shown) {
            state.nameTag = null;
            return;
        }
        state.nameTag = nameTag.lines().getFirst();
        // Bedrock scales the bounding box with the entity, like The Hive's costume NPCs, whose box height is the unscaled one
        final Vec3 attachment = nameTag.height() >= 0F ? new Vec3(0, nameTag.height(), 0)
            : entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
        state.nameTagAttachment = attachment != null ? attachment.scale(nameTag.scale()) : null;
        bedrockState.viaFabricPlusBedrock$setNameLines(nameTag.lines());
    }

    /**
     * Java draws one line of a name, Bedrock all of them with the first on top.
     */
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V", at = @At("HEAD"), cancellable = true)
    private void submitBedrockNameLines(final EntityRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera, final int offset,
                                        final CallbackInfo ci) {
        final List<Component> lines = ((IEntityRenderState) state).viaFabricPlusBedrock$getNameLines();
        if (lines == null || lines.size() < 2 || state.nameTag == null) {
            return;
        }
        poseStack.pushPose();
        if (state.scoreText != null) {
            submitNodeCollector.submitNameTag(poseStack, state.nameTagAttachment, offset, state.scoreText, !state.isDiscrete, state.lightCoords, camera);
            poseStack.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
        }
        for (int i = lines.size() - 1; i >= 0; i--) {
            submitNodeCollector.submitNameTag(poseStack, state.nameTagAttachment, offset, lines.get(i), !state.isDiscrete, state.lightCoords, camera);
            poseStack.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
        }
        poseStack.popPose();
        ci.cancel();
    }

    @Inject(method = "finalizeRenderState", at = @At("TAIL"))
    private void hideShadowOfHiddenBody(final T entity, final S state, final CallbackInfo ci) {
        if (((IEntityRenderState) state).viaFabricPlusBedrock$isBodyHidden()) {
            state.shadowRadius = 0F;
            state.shadowPieces.clear();
        }
    }

}
