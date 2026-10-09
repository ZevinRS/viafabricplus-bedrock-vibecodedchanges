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
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockEntityPoses;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockPackImages;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockDoubleSidedPlanes;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.util.Map;
import net.raphimc.viabedrock.api.resourcepack.definition.EntityDefinitions;
import net.raphimc.viabedrock.protocol.rewriter.resourcepack.CustomEntityResourceRewriter;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.cube.converter.converter.enums.RotationType;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.model.impl.java.JavaItemModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CustomEntityResourceRewriter.class, remap = false)
public abstract class MixinCustomEntityResourceRewriter {

    /**
     * See {@link BedrockEntityPoses}.
     */
    @WrapOperation(method = "handleEntityDefinition", at = @At(value = "INVOKE", target = "Lorg/cube/converter/model/impl/bedrock/BedrockGeometryModel;toJavaItemModel(Ljava/lang/String;Lorg/cube/converter/converter/enums/RotationType;)Lorg/cube/converter/model/impl/java/JavaItemModel;"))
    private JavaItemModel poseModel(final BedrockGeometryModel geometry, final String texture, final RotationType rotationType, final Operation<JavaItemModel> original,
                                    @Local(argsOnly = true) final ResourcePackStorage resourcePackStorage, @Local(argsOnly = true) final EntityDefinitions.EntityDefinition entityDefinition) {
        BedrockGeometryModel posed = geometry;
        try {
            posed = BedrockEntityPoses.pose(resourcePackStorage, entityDefinition.identifier(), geometry);
        } catch (final Exception e) {
            ViaFabricPlusBedrock.impl().logger().error("Failed to pose the model of {}", entityDefinition.identifier(), e);
        }
        return original.call(posed, texture, rotationType);
    }

    /**
     * See {@link BedrockDoubleSidedPlanes}.
     */
    @WrapOperation(method = "handleEntityDefinition", at = @At(value = "INVOKE", target = "Lorg/cube/converter/model/impl/java/JavaItemModel;compile()Lcom/viaversion/viaversion/libs/gson/JsonObject;"))
    private JsonObject showFlatPartsFromBothSides(final JavaItemModel itemModel, final Operation<JsonObject> original,
                                                  @Local(argsOnly = true) final ResourcePackStorage resourcePackStorage, @Local(ordinal = 1) final Map.Entry<String, String> textureEntry) {
        final JsonObject model = original.call(itemModel);
        BedrockDoubleSidedPlanes.apply(model, () -> BedrockPackImages.get(resourcePackStorage, textureEntry.getValue()));
        return model;
    }

}
