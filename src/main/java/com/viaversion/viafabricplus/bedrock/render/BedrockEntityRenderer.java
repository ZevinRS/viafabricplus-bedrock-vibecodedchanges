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

package com.viaversion.viafabricplus.bedrock.render;

import com.viaversion.viafabricplus.bedrock.feature.Features;
import com.viaversion.viafabricplus.bedrock.feature.BedrockApiImpl;
import com.viaversion.viafabricplus.bedrock.api.MoLangQuery;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockNameTags;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorDataIds;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorFlags;
import team.unnamed.mocha.runtime.value.Function;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

/**
 * Draws custom entities with their Bedrock models, posed by their animations every frame. ViaBedrock spawns an
 * interaction entity for each, which Java draws nothing for, see {@link BedrockEntityModels}.
 * <p>
 * Models are turned by the entity's yaw and flipped into Java's model space like Java's living entities, so a Bedrock
 * model faces the way Bedrock draws it.
 */
public final class BedrockEntityRenderer extends EntityRenderer<Interaction, BedrockEntityRenderer.State> {

    private static final double MOVE_SPEED = 4.3; // Blocks a second a walking player moves, Bedrock's walk animations are made for

    public BedrockEntityRenderer(final EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected boolean affectedByCulling(final Interaction entity) {
        // Models reach outside the entity's box
        return BedrockEntityModels.get(entity.getId()) == null && super.affectedByCulling(entity);
    }

    @Override
    public void extractRenderState(final Interaction entity, final State state, final float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.parts.clear();
        final BedrockEntityModels.Instance instance = BedrockEntityModels.get(entity.getId());
        if (instance == null) {
            return;
        }
        final BedrockNameTags.NameTag nameTag = BedrockNameTags.get(entity.getId());
        state.scale = nameTag != null ? nameTag.scale() : 1F;
        if (state.scale <= 0F) {
            return;
        }
        state.yaw = entity.getYRot(partialTicks);
        try {
            final BedrockModel.Pose pose = Features.ENTITY_ANIMATIONS.isEnabled()
                ? instance.animator().update(System.nanoTime() / 1.0E9, query -> this.queries(entity, instance, partialTicks, state.yaw, query)) : BedrockModel.Pose.NONE;
            for (final BedrockEntityModels.Part part : instance.parts()) {
                final RenderType renderType = BedrockEntityModels.renderType(instance.packs(), part);
                if (renderType != null) {
                    state.parts.add(new State.Part(part.model(), renderType, pose, part.fullBright() ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords));
                }
            }
        } catch (final RuntimeException e) {
            // A broken pack shouldn't take the game down: the entity isn't drawn anymore
            ViaFabricPlusBedrock.impl().logger().error("Failed to animate a custom entity, it won't be drawn", e);
            BedrockEntityModels.remove(entity.getId());
            state.parts.clear();
        }
    }

    @Override
    public void submit(final State state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
        if (!state.parts.isEmpty()) {
            poseStack.pushPose();
            poseStack.rotate(Axis.YP.rotationDegrees(180F - state.yaw));
            poseStack.scale(-state.scale, -state.scale, state.scale);
            poseStack.translate(0F, -1.501F, 0F);
            for (final State.Part part : state.parts) {
                submitNodeCollector.submitModel(part.model(), part.pose(), poseStack, part.renderType(), part.lightCoords(), OverlayTexture.NO_OVERLAY, -1, null,
                    state.outlineColor);
            }
            poseStack.popPose();
        }
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    /**
     * The queries Bedrock's animations of custom entities use most.
     */
    private void queries(final Interaction entity, final BedrockEntityModels.Instance instance, final float partialTicks, final float yaw, final MutableObjectBinding query) {
        final Map<ActorDataIds, EntityData> data = instance.entity().entityData();
        final Set<ActorFlags> flags = instance.entity().entityFlags();
        for (final Map.Entry<ActorFlags, String> flag : BedrockProtocol.MAPPINGS.getBedrockEntityFlagMoLangQueries().entrySet()) {
            if (flags.contains(flag.getKey())) {
                query.set(flag.getValue(), Value.of(true));
            }
        }
        if (data.get(ActorDataIds.VARIANT) != null && data.get(ActorDataIds.VARIANT).value() instanceof final Number variant) {
            query.set("variant", Value.of(variant.doubleValue()));
        }
        if (data.get(ActorDataIds.MARK_VARIANT) != null && data.get(ActorDataIds.MARK_VARIANT).value() instanceof final Number markVariant) {
            query.set("mark_variant", Value.of(markVariant.doubleValue()));
        }

        final double lifeTime = (entity.tickCount + partialTicks) / 20.0;
        final Vec3 movement = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        final double groundSpeed = movement.horizontalDistance() * 20.0;
        final Vec3 position = entity.getPosition(partialTicks);
        if (instance.lastPosition != null) {
            instance.distanceMoved += position.subtract(instance.lastPosition).horizontalDistance();
        }
        instance.lastPosition = position;
        final double yawSpeed = Double.isNaN(instance.lastYaw) ? 0 : Mth.wrapDegrees(yaw - instance.lastYaw) * 20.0;
        instance.lastYaw = yaw;
        query.set("life_time", Value.of(lifeTime));
        query.set("is_alive", Value.of(true));
        query.set("is_on_ground", Value.of(entity.onGround()));
        query.set("is_in_water", Value.of(entity.isInWater()));
        query.set("ground_speed", Value.of(groundSpeed));
        query.set("modified_move_speed", Value.of(Math.min(1, groundSpeed / MOVE_SPEED)));
        query.set("modified_distance_moved", Value.of(instance.distanceMoved));
        query.set("yaw_speed", Value.of(yawSpeed));
        query.set("body_y_rotation", Value.of(yaw));
        query.set("body_x_rotation", Value.of(entity.getXRot()));
        query.set("frame_alpha", Value.of(partialTicks));
        query.set("time_of_day", Value.of(entity.level().getOverworldClockTime() % 24000L / 24000.0));

        final Camera camera = this.entityRenderDispatcher.camera;
        final Vec3 cameraPosition = camera != null ? camera.position() : entity.position();
        final Vec3 toCamera = cameraPosition.subtract(entity.getPosition(partialTicks));
        query.set("distance_from_camera", Value.of(toCamera.length()));
        // Yaw and pitch that face the camera, as Bedrock and Java measure them
        final double yawToCamera = Math.toDegrees(Math.atan2(-toCamera.x, toCamera.z));
        final double pitchToCamera = -Math.toDegrees(Math.atan2(toCamera.y, toCamera.horizontalDistance()));
        query.set("rotation_to_camera", (Function<Object>) (context, arguments) -> {
            final int axis = arguments.length() > 0 ? (int) arguments.next().eval().getAsNumber() : 0;
            return Value.of(axis == 0 ? pitchToCamera : yawToCamera);
        });
        final float cameraYaw = camera != null ? camera.yRot() : 0F;
        final float cameraPitch = camera != null ? camera.xRot() : 0F;
        query.set("camera_rotation", (Function<Object>) (context, arguments) -> {
            final int axis = arguments.length() > 0 ? (int) arguments.next().eval().getAsNumber() : 0;
            return Value.of(axis == 0 ? cameraPitch : cameraYaw);
        });
        query.set("property", (Function<Object>) (context, arguments) -> Value.of(0));

        // Queries other mods add through the API, which replace built-in ones of the same name
        for (final Map.Entry<String, MoLangQuery> added : BedrockApiImpl.INSTANCE.queries().entrySet()) {
            final MoLangQuery apiQuery = added.getValue();
            query.set(added.getKey(), (Function<Object>) (context, arguments) -> {
                final double[] values = new double[arguments.length()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = arguments.next().eval().getAsNumber();
                }
                return Value.of(apiQuery.evaluate(entity, instance.identifier(), partialTicks, values));
            });
        }
    }

    public static final class State extends EntityRenderState {

        final List<Part> parts = new ArrayList<>();
        float yaw;
        float scale = 1F;

        record Part(BedrockModel model, RenderType renderType, BedrockModel.Pose pose, int lightCoords) {
        }

    }

}
