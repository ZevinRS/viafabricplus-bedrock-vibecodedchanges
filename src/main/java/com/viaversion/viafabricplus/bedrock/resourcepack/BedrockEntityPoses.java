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

package com.viaversion.viafabricplus.bedrock.resourcepack;

import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.raphimc.viabedrock.api.util.MoLangEngine;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.cube.converter.model.element.Cube;
import org.cube.converter.model.element.Parent;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.util.element.Position3V;
import org.jetbrains.annotations.Nullable;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.standard.MochaMath;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;

/**
 * Custom entities are drawn as one item model, which ViaBedrock converts from the entity's geometry without its bones'
 * rotations, and without the animations Bedrock plays on them. The Hive's NPCs are posed by both: their geometry bends
 * arms and heads, and the animations they play when idle pose them further. Both are put into the cubes here, as the
 * pose the animations start with: each cube gets the rotation and offset of its bones, with the rest rotation of every
 * bone and the rotation and position the default animations give it.
 * <p>
 * Positions are worked out in Java's model space, where x is mirrored: there a Bedrock rotation (x, y, z) is the Euler
 * rotation (-x, -y, z) applied x first, as the converter maps cube rotations.
 */
public final class BedrockEntityPoses {

    private static final Scope SCOPE = Scope.create();

    static {
        SCOPE.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
        for (final String name : new String[]{"query", "q", "variable", "v", "context", "c"}) {
            final MutableObjectBinding binding = new MutableObjectBinding();
            binding.block();
            SCOPE.set(name, binding);
        }
        SCOPE.readOnly(true);
    }

    private BedrockEntityPoses() {
    }

    /**
     * @return the geometry with its bones' rotations and the default pose in its cubes, or the geometry itself if no
     * bone is rotated or posed
     */
    public static BedrockGeometryModel pose(final ResourcePackStorage storage, final String entityIdentifier, final BedrockGeometryModel geometry) {
        final Map<String, BonePose> pose = defaultPose(storage, entityIdentifier);
        final Map<String, Parent> bones = new HashMap<>();
        boolean posed = false;
        for (final Parent bone : geometry.getParents()) {
            bones.put(bone.getName().toLowerCase(Locale.ROOT), bone);
            posed |= !bone.getRotation().isZero() || pose.containsKey(bone.getName().toLowerCase(Locale.ROOT));
        }
        if (!posed) {
            return geometry;
        }

        final BedrockGeometryModel posedGeometry = new BedrockGeometryModel(geometry.getIdentifier(), geometry.getTextureSize());
        for (final Parent bone : geometry.getParents()) {
            final Parent posedBone = bone.clone();
            final Affine boneTransform = boneChain(bones, pose, bone.getName().toLowerCase(Locale.ROOT), new HashSet<>());
            for (final Cube cube : posedBone.getCubes().values()) {
                bake(cube, boneTransform);
            }
            posedBone.getRotation().set(0F, 0F, 0F);
            posedGeometry.getParents().add(posedBone);
        }
        return posedGeometry;
    }

    /**
     * @return the transform a bone and its parents give the cubes in it
     */
    private static Affine boneChain(final Map<String, Parent> bones, final Map<String, BonePose> pose, final String name, final Set<String> visited) {
        final Parent bone = bones.get(name);
        if (bone == null || !visited.add(name)) {
            return Affine.IDENTITY;
        }
        final BonePose bonePose = pose.getOrDefault(name, BonePose.NONE);
        final Position3V pivot = bone.getPivot();
        final Position3V rotation = bone.getRotation();
        final Affine own = Affine.rotationAround(mirrored(pivot), javaRotation(rotation.getX() + bonePose.rotation[0], rotation.getY() + bonePose.rotation[1],
                rotation.getZ() + bonePose.rotation[2]))
            .then(Affine.translation(-bonePose.position[0], bonePose.position[1], bonePose.position[2]));
        final Affine parent = bone.getParent() != null ? boneChain(bones, pose, bone.getParent().toLowerCase(Locale.ROOT), visited) : Affine.IDENTITY;
        return own.then(parent);
    }

    private static void bake(final Cube cube, final Affine bones) {
        final Position3V size = cube.getSize();
        final Position3V origin = cube.getPosition();
        // The box in mirrored space before any rotation
        final double[] from = {-(origin.getX() + size.getX()), origin.getY(), origin.getZ()};
        final double[] center = {from[0] + size.getX() / 2.0, from[1] + size.getY() / 2.0, from[2] + size.getZ() / 2.0};
        final Position3V cubeRotation = cube.getRotation();
        final Affine own = cubeRotation.isZero() ? Affine.IDENTITY
            : Affine.rotationAround(mirrored(cube.getPivot()), javaRotation(cubeRotation.getX(), cubeRotation.getY(), cubeRotation.getZ()));
        final Affine total = own.then(bones);

        // Rotating the moved box around its center: R (p + d - (c + d)) + c + d = R p + t when d = t + R c - c
        final double[] rotatedCenter = total.applyRotation(center);
        final double[] offset = {total.t[0] + rotatedCenter[0] - center[0], total.t[1] + rotatedCenter[1] - center[1], total.t[2] + rotatedCenter[2] - center[2]};
        final double[] euler = total.eulerZyx();
        origin.set((float) (-(from[0] + offset[0]) - size.getX()), (float) (from[1] + offset[1]), (float) (from[2] + offset[2]));
        cube.getPivot().set((float) -(center[0] + offset[0]), (float) (center[1] + offset[1]), (float) (center[2] + offset[2]));
        cubeRotation.set((float) -euler[0], (float) -euler[1], (float) euler[2]);
    }

    private static double[] mirrored(final Position3V position) {
        return new double[]{-position.getX(), position.getY(), position.getZ()};
    }

    /**
     * @return the rotation matrix of a Bedrock rotation in mirrored space
     */
    private static double[][] javaRotation(final double x, final double y, final double z) {
        final double rx = Math.toRadians(-x);
        final double ry = Math.toRadians(-y);
        final double rz = Math.toRadians(z);
        final double cx = Math.cos(rx), sx = Math.sin(rx), cy = Math.cos(ry), sy = Math.sin(ry), cz = Math.cos(rz), sz = Math.sin(rz);
        // Rz * Ry * Rx
        return new double[][]{
            {cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx},
            {sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx},
            {-sy, cy * sx, cy * cx}
        };
    }

    /**
     * The pose of the bones at the start of the animations an entity plays by default: the ones its scripts animate
     * without a condition or with one that holds at rest, and the ones the first state of its animation controllers
     * plays, like the idle pose of The Hive's hub NPCs.
     */
    private static Map<String, BonePose> defaultPose(final ResourcePackStorage storage, final String entityIdentifier) {
        final BedrockPackIndex index = BedrockPackIndex.of(storage);
        final JsonObject description = index.entityDescriptions().get(entityIdentifier);
        final Map<String, BonePose> pose = new HashMap<>();
        if (description == null) {
            return pose;
        }
        final JsonObject animations = BedrockPackIndex.object(description, "animations");
        final JsonObject scripts = BedrockPackIndex.object(description, "scripts");
        final List<String> animated = new ArrayList<>();
        if (scripts != null && scripts.get("animate") instanceof final JsonArray animate) {
            collectEntries(animate, animated);
        }
        // The older list of controllers, each a name and the controller's identifier
        if (description.get("animation_controllers") instanceof final JsonArray controllers) {
            for (final JsonElement controller : controllers) {
                if (controller.isJsonPrimitive()) {
                    animated.add(controller.getAsString());
                } else if (controller instanceof final JsonObject named) {
                    for (final JsonElement identifier : named.asMap().values()) {
                        if (identifier.isJsonPrimitive()) {
                            animated.add(identifier.getAsString());
                        }
                    }
                }
            }
        }

        final Set<String> played = new HashSet<>();
        for (final String key : animated) {
            resolve(index, animations, key, played, 0);
        }
        for (final String animation : played) {
            final JsonObject bones = BedrockPackIndex.object(index.animation(animation), "bones");
            if (bones == null) {
                continue;
            }
            for (final Map.Entry<String, JsonElement> bone : bones.entrySet()) {
                if (!(bone.getValue() instanceof final JsonObject channels)) {
                    continue;
                }
                final BonePose bonePose = pose.computeIfAbsent(bone.getKey().toLowerCase(Locale.ROOT), k -> new BonePose());
                final double[] rotation = startValue(channels.get("rotation"));
                if (rotation != null && !facesCamera(channels.get("rotation"))) {
                    bonePose.add(bonePose.rotation, rotation);
                }
                final double[] position = startValue(channels.get("position"));
                if (position != null) {
                    bonePose.add(bonePose.position, position);
                }
            }
        }
        return pose;
    }

    /**
     * Collects animation keys of an animate list, where entries are a key or a key with a condition.
     */
    private static void collectEntries(final JsonArray entries, final List<String> keys) {
        for (final JsonElement entry : entries) {
            if (entry.isJsonPrimitive()) {
                keys.add(entry.getAsString());
            } else if (entry instanceof final JsonObject conditional) {
                for (final Map.Entry<String, JsonElement> condition : conditional.entrySet()) {
                    if (!condition.getValue().isJsonPrimitive() || evaluate(condition.getValue().getAsString()) != 0) {
                        keys.add(condition.getKey());
                    }
                }
            }
        }
    }

    private static void resolve(final BedrockPackIndex index, final @Nullable JsonObject animations, final String key, final Set<String> played, final int depth) {
        if (depth > 4) {
            return;
        }
        final String identifier = animations != null && animations.get(key) instanceof final JsonElement value && value.isJsonPrimitive() ? value.getAsString() : key;
        final JsonObject controller = index.animationController(identifier);
        if (controller != null) {
            final JsonObject states = BedrockPackIndex.object(controller, "states");
            final String initialState = controller.has("initial_state") ? controller.get("initial_state").getAsString() : "default";
            final JsonObject state = BedrockPackIndex.object(states, initialState);
            if (state != null && state.get("animations") instanceof final JsonArray stateAnimations) {
                final List<String> keys = new ArrayList<>();
                collectEntries(stateAnimations, keys);
                for (final String stateKey : keys) {
                    resolve(index, animations, stateKey, played, depth + 1);
                }
            }
        } else if (index.animation(identifier) != null) {
            played.add(identifier);
        }
    }

    /**
     * @return the value a channel starts with, from a fixed value or its first keyframe
     */
    private static double @Nullable [] startValue(final @Nullable JsonElement channel) {
        if (channel == null) {
            return null;
        } else if (channel instanceof final JsonArray values) {
            return vector(values);
        } else if (channel.isJsonPrimitive()) { // One value for all axes
            final double value = channel.getAsJsonPrimitive().isNumber() ? channel.getAsDouble() : evaluate(channel.getAsString());
            return new double[]{value, value, value};
        } else if (channel instanceof final JsonObject keyframes) {
            String first = null;
            double firstTime = Double.MAX_VALUE;
            for (final String time : keyframes.keySet()) {
                try {
                    final double parsed = Double.parseDouble(time);
                    if (parsed < firstTime) {
                        firstTime = parsed;
                        first = time;
                    }
                } catch (final NumberFormatException ignored) {
                }
            }
            if (first == null) {
                return null;
            }
            final JsonElement keyframe = keyframes.get(first);
            if (keyframe instanceof final JsonObject object) {
                final JsonElement value = object.has("post") ? object.get("post") : object.has("pre") ? object.get("pre") : object.get("vector");
                return value instanceof final JsonArray values ? vector(values) : null;
            }
            return startValue(keyframe);
        }
        return null;
    }

    private static double @Nullable [] vector(final JsonArray values) {
        if (values.size() < 3) {
            return null;
        }
        final double[] vector = new double[3];
        for (int i = 0; i < 3; i++) {
            final JsonElement value = values.get(i);
            vector[i] = value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : value.isJsonPrimitive() ? evaluate(value.getAsString()) : 0;
        }
        return vector;
    }

    private static boolean facesCamera(final JsonElement rotation) {
        return rotation instanceof final JsonArray values && values.size() >= 2
            && (BedrockCameraFacing.facesCamera(values.get(0), 0) || BedrockCameraFacing.facesCamera(values.get(1), 1));
    }

    /**
     * @return the value of a MoLang expression at rest, with every query and variable 0
     */
    private static double evaluate(final String expression) {
        try {
            return MoLangEngine.eval(SCOPE, expression).getAsNumber();
        } catch (final Throwable e) {
            return 0;
        }
    }

    private static final class BonePose {

        static final BonePose NONE = new BonePose();

        final double[] rotation = new double[3];
        final double[] position = new double[3];

        void add(final double[] target, final double[] value) {
            for (int i = 0; i < 3; i++) {
                target[i] += value[i];
            }
        }

    }

    /**
     * p -> r p + t in mirrored model space.
     */
    private record Affine(double[][] r, double[] t) {

        static final Affine IDENTITY = new Affine(new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, new double[3]);

        static Affine translation(final double x, final double y, final double z) {
            return new Affine(IDENTITY.r, new double[]{x, y, z});
        }

        /**
         * p -> r (p - pivot) + pivot
         */
        static Affine rotationAround(final double[] pivot, final double[][] r) {
            final double[] rotatedPivot = multiply(r, pivot);
            return new Affine(r, new double[]{pivot[0] - rotatedPivot[0], pivot[1] - rotatedPivot[1], pivot[2] - rotatedPivot[2]});
        }

        /**
         * @return this transform followed by the other
         */
        Affine then(final Affine other) {
            final double[][] r = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    r[i][j] = other.r[i][0] * this.r[0][j] + other.r[i][1] * this.r[1][j] + other.r[i][2] * this.r[2][j];
                }
            }
            final double[] rotated = multiply(other.r, this.t);
            return new Affine(r, new double[]{rotated[0] + other.t[0], rotated[1] + other.t[1], rotated[2] + other.t[2]});
        }

        double[] applyRotation(final double[] vector) {
            return multiply(this.r, vector);
        }

        /**
         * @return the angles in degrees of r = Rz * Ry * Rx
         */
        double[] eulerZyx() {
            final double sy = Math.max(-1, Math.min(1, -this.r[2][0]));
            final double y = Math.asin(sy);
            final double x;
            final double z;
            if (Math.abs(sy) < 0.99999) {
                x = Math.atan2(this.r[2][1], this.r[2][2]);
                z = Math.atan2(this.r[1][0], this.r[0][0]);
            } else { // Gimbal lock, the x and z rotations are around the same axis
                x = Math.atan2(-this.r[1][2], this.r[1][1]);
                z = 0;
            }
            return new double[]{Math.toDegrees(x), Math.toDegrees(y), Math.toDegrees(z)};
        }

        private static double[] multiply(final double[][] r, final double[] v) {
            return new double[]{
                r[0][0] * v[0] + r[0][1] * v[1] + r[0][2] * v[2],
                r[1][0] * v[0] + r[1][1] * v[1] + r[1][2] * v[2],
                r[2][0] * v[0] + r[2][1] * v[1] + r[2][2] * v[2]
            };
        }

    }

}
