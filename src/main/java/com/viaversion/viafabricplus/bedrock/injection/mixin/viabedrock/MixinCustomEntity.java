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

package com.viaversion.viafabricplus.bedrock.injection.mixin.viabedrock;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.bedrock.render.BedrockEntityModels;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockCameraFacing;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes26_3;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.minecraft.entitydata.types.EntityDataTypes26_3;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.types.version.VersionedTypes;
import java.util.ArrayList;
import java.util.List;
import net.raphimc.viabedrock.api.model.entity.CustomEntity;
import net.raphimc.viabedrock.api.model.entity.Entity;
import net.raphimc.viabedrock.api.resourcepack.definition.EntityDefinitions;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorDataIds;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom entities are drawn with item display entities, which ViaBedrock sized by their model only. Servers scale
 * them with the entity's scale, like The Hive's SkyWars border around the starting islands (9.7 times its 3 block
 * model) and the NPCs in its hub. Models that turn toward the camera get the matching billboard mode, see
 * {@link BedrockCameraFacing}.
 */
@Mixin(value = CustomEntity.class, remap = false)
public abstract class MixinCustomEntity extends Entity {

    @Shadow
    @Final
    private EntityDefinitions.EntityDefinition entityDefinition;

    @Shadow
    private boolean spawned;

    @Shadow
    @Final
    private List<CustomEntity.EvaluatedModel> models;

    @Shadow
    private void spawn() {
    }

    @Shadow
    private void despawn() {
    }

    @Unique
    private float viaFabricPlusBedrock$spawnedScale = 1F;

    private MixinCustomEntity(final UserConnection user) {
        super(user, 0L, 0L, null, 0, null, null);
    }

    @Unique
    private float viaFabricPlusBedrock$scale() {
        final EntityData scale = this.entityData().get(ActorDataIds.RESERVED_038); // scale
        return scale != null && scale.value() instanceof final Float value && value > 0F ? value : 1F;
    }

    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
    private void drawBedrockModel(final CallbackInfo ci) {
        this.viaFabricPlusBedrock$spawnedScale = this.viaFabricPlusBedrock$scale();
        final ResourcePackStorage packs = this.user.get(ResourcePackStorage.class);
        if (packs != null && BedrockEntityModels.spawn(packs, (CustomEntity) (Object) this, this.entityDefinition.identifier(), this.models)) {
            this.spawned = true;
            ci.cancel();
        }
    }

    @Inject(method = "despawn", at = @At("HEAD"))
    private void forgetBedrockModel(final CallbackInfo ci) {
        BedrockEntityModels.remove(this.javaId());
    }

    @ModifyVariable(method = "spawn", at = @At("STORE"), ordinal = 0)
    private float applyEntityScale(final float modelScale) {
        return modelScale * this.viaFabricPlusBedrock$spawnedScale;
    }

    @WrapOperation(method = "spawn", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;write(Lcom/viaversion/viaversion/api/type/Type;Ljava/lang/Object;)V"))
    private void faceCamera(final PacketWrapper wrapper, final Type<?> type, final Object value, final Operation<Void> original) {
        if (value instanceof final List<?> entityData) {
            final byte billboard = BedrockCameraFacing.billboard(this.user.get(ResourcePackStorage.class), this.entityDefinition.identifier());
            if (billboard != BedrockCameraFacing.FIXED) {
                final List<Object> withBillboard = new ArrayList<>(entityData);
                withBillboard.add(new EntityData(BedrockProtocol.MAPPINGS.getJavaEntityDataFields().get(EntityTypes26_3.ITEM_DISPLAY).indexOf("BILLBOARD_RENDER_CONSTRAINTS"),
                    ((EntityDataTypes26_3) VersionedTypes.V26_3.entityDataTypes).byteType, billboard));
                original.call(wrapper, type, withBillboard);
                return;
            }
        }
        original.call(wrapper, type, value);
    }

    @Inject(method = "onEntityDataChanged", at = @At("TAIL"))
    private void respawnWhenScaled(final CallbackInfo ci) {
        if (this.spawned && this.viaFabricPlusBedrock$scale() != this.viaFabricPlusBedrock$spawnedScale) {
            this.despawn();
            this.spawn();
        }
    }

}
