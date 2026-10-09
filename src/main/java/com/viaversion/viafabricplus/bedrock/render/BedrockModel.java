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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * A Bedrock geometry as Java model parts, one per bone and one more per rotated cube, posed by {@link Pose}.
 * <p>
 * Java's model space is Bedrock's with y pointing down from 24 units up: a Bedrock point (x, y, z) is at (x, 24 - y, z).
 * Turning y around keeps Bedrock's rotations as Java's: (x, y, z) degrees are the same Euler angles, applied z, y, then
 * x like Java's parts. Faces keep Bedrock's texture layout too, which is Java's: Bedrock's east is Java's west, its up
 * Java's down, since both are named in their own space.
 */
public final class BedrockModel extends Model<BedrockModel.Pose> {

    private static final float ORIGIN_Y = 24F;
    // Bedrock's face names by the face Java gives their area in its own space
    private static final Map<Direction, String> FACES = Map.of(Direction.DOWN, "up", Direction.UP, "down", Direction.WEST, "east", Direction.EAST, "west",
        Direction.NORTH, "north", Direction.SOUTH, "south");

    private final Map<String, ModelPart> bones;

    private BedrockModel(final ModelPart root, final Map<String, ModelPart> bones) {
        super(root, RenderTypes::entityCutout);
        this.bones = bones;
    }

    public static BedrockModel of(final BedrockGeometry geometry) {
        final Map<String, ModelPart> parts = new LinkedHashMap<>();
        final Map<String, Map<String, ModelPart>> children = new HashMap<>();
        final Map<String, BedrockGeometry.Bone> byName = new HashMap<>();
        for (final BedrockGeometry.Bone bone : geometry.bones()) {
            byName.put(bone.name().toLowerCase(Locale.ROOT), bone);
        }

        for (final BedrockGeometry.Bone bone : geometry.bones()) {
            final Map<String, ModelPart> boneChildren = new LinkedHashMap<>();
            final List<ModelPart.Cube> cubes = new ArrayList<>();
            final float[] pivot = javaPoint(bone.pivot());
            int rotatedCubes = 0;
            for (final BedrockGeometry.Cube cube : bone.cubes()) {
                if (cube.rotation() == null || isZero(cube.rotation())) {
                    cubes.add(cube(cube, pivot, geometry));
                    continue;
                }
                // Java rotates parts, not cubes: a rotated cube is a part of its own around its pivot
                final float[] cubePivot = cube.pivot() != null ? javaPoint(cube.pivot()) : pivot;
                final ModelPart cubePart = new ModelPart(List.of(cube(cube, cubePivot, geometry)), Map.of());
                cubePart.setInitialPose(PartPose.offsetAndRotation(cubePivot[0] - pivot[0], cubePivot[1] - pivot[1], cubePivot[2] - pivot[2],
                    cube.rotation()[0] * Mth.DEG_TO_RAD, cube.rotation()[1] * Mth.DEG_TO_RAD, cube.rotation()[2] * Mth.DEG_TO_RAD));
                cubePart.resetPose();
                boneChildren.put("cube_" + rotatedCubes++, cubePart);
            }
            final ModelPart part = new ModelPart(cubes, boneChildren);
            final String name = bone.name().toLowerCase(Locale.ROOT);
            parts.put(name, part);
            children.put(name, boneChildren);
        }

        final Map<String, ModelPart> rootChildren = new LinkedHashMap<>();
        for (final BedrockGeometry.Bone bone : geometry.bones()) {
            final String name = bone.name().toLowerCase(Locale.ROOT);
            final ModelPart part = parts.get(name);
            final String parentName = bone.parent() != null ? bone.parent().toLowerCase(Locale.ROOT) : null;
            final BedrockGeometry.Bone parent = parentName != null && !parentName.equals(name) ? byName.get(parentName) : null;
            final float[] pivot = javaPoint(bone.pivot());
            final float[] parentPivot = parent != null ? javaPoint(parent.pivot()) : new float[3];
            part.setInitialPose(PartPose.offsetAndRotation(pivot[0] - parentPivot[0], pivot[1] - parentPivot[1], pivot[2] - parentPivot[2],
                bone.rotation()[0] * Mth.DEG_TO_RAD, bone.rotation()[1] * Mth.DEG_TO_RAD, bone.rotation()[2] * Mth.DEG_TO_RAD));
            part.resetPose();
            (parent != null ? children.get(parentName) : rootChildren).put("bone_" + name, part);
        }
        return new BedrockModel(new ModelPart(List.of(), rootChildren), Collections.unmodifiableMap(parts));
    }

    /**
     * Resets the bones and moves them by the pose.
     */
    @Override
    public void setupAnim(final Pose pose) {
        this.resetPose();
        for (final Map.Entry<String, float[]> bone : pose.bones().entrySet()) {
            final ModelPart part = this.bones.get(bone.getKey());
            if (part == null) {
                continue;
            }
            final float[] t = bone.getValue();
            part.xRot += t[0] * Mth.DEG_TO_RAD;
            part.yRot += t[1] * Mth.DEG_TO_RAD;
            part.zRot += t[2] * Mth.DEG_TO_RAD;
            part.x += t[3];
            part.y -= t[4];
            part.z += t[5];
            part.xScale *= t[6];
            part.yScale *= t[7];
            part.zScale *= t[8];
        }
    }

    public boolean hasBone(final String name) {
        return this.bones.containsKey(name);
    }

    private static float[] javaPoint(final float[] bedrock) {
        return new float[]{bedrock[0], ORIGIN_Y - bedrock[1], bedrock[2]};
    }

    private static boolean isZero(final float[] vector) {
        return vector[0] == 0F && vector[1] == 0F && vector[2] == 0F;
    }

    /**
     * @param partOrigin where the part the cube is in is, in Java's model space
     */
    private static ModelPart.Cube cube(final BedrockGeometry.Cube cube, final float[] partOrigin, final BedrockGeometry geometry) {
        final float[] size = cube.size();
        final float minX = cube.origin()[0] - partOrigin[0];
        final float minY = ORIGIN_Y - cube.origin()[1] - size[1] - partOrigin[1];
        final float minZ = cube.origin()[2] - partOrigin[2];

        // Each face's area as Java's polygons take it: the corners u0, v0 and u1, v1
        final Map<Direction, float[]> areas = new HashMap<>();
        if (cube.uv() != null) {
            // The box layout, with the sizes rounded down like Bedrock
            final float u = cube.uv()[0];
            final float v = cube.uv()[1];
            final float w = (float) Math.floor(size[0]);
            final float h = (float) Math.floor(size[1]);
            final float d = (float) Math.floor(size[2]);
            areas.put(Direction.DOWN, new float[]{u + d, v, u + d + w, v + d});
            areas.put(Direction.UP, new float[]{u + d + w, v + d, u + d + w + w, v});
            areas.put(Direction.WEST, new float[]{u, v + d, u + d, v + d + h});
            areas.put(Direction.NORTH, new float[]{u + d, v + d, u + d + w, v + d + h});
            areas.put(Direction.EAST, new float[]{u + d + w, v + d, u + d + w + d, v + d + h});
            areas.put(Direction.SOUTH, new float[]{u + d + w + d, v + d, u + d + w + d + w, v + d + h});
        } else {
            for (final Map.Entry<Direction, String> face : FACES.entrySet()) {
                final float[] area = cube.faceUvs().get(face.getValue());
                if (area != null) {
                    areas.put(face.getKey(), new float[]{area[0], area[1], area[0] + area[2], area[1] + area[3]});
                }
            }
        }

        final Set<Direction> faces = areas.isEmpty() ? EnumSet.noneOf(Direction.class) : EnumSet.copyOf(areas.keySet());
        final float inflate = cube.inflate();
        final boolean mirror = cube.mirror() && cube.uv() != null;
        final ModelPart.Cube javaCube = new ModelPart.Cube(0, 0, minX, minY, minZ, size[0], size[1], size[2], inflate, inflate, inflate, mirror,
            geometry.textureWidth(), geometry.textureHeight(), faces);

        // The same corners Java's cube made its polygons of, given the areas above
        float x0 = minX - inflate, y0 = minY - inflate, z0 = minZ - inflate;
        float x1 = minX + size[0] + inflate, y1 = minY + size[1] + inflate, z1 = minZ + size[2] + inflate;
        if (mirror) {
            final float swap = x1;
            x1 = x0;
            x0 = swap;
        }
        final ModelPart.Vertex t0 = new ModelPart.Vertex(x0, y0, z0, 0F, 0F);
        final ModelPart.Vertex t1 = new ModelPart.Vertex(x1, y0, z0, 0F, 0F);
        final ModelPart.Vertex t2 = new ModelPart.Vertex(x1, y1, z0, 0F, 0F);
        final ModelPart.Vertex t3 = new ModelPart.Vertex(x0, y1, z0, 0F, 0F);
        final ModelPart.Vertex l0 = new ModelPart.Vertex(x0, y0, z1, 0F, 0F);
        final ModelPart.Vertex l1 = new ModelPart.Vertex(x1, y0, z1, 0F, 0F);
        final ModelPart.Vertex l2 = new ModelPart.Vertex(x1, y1, z1, 0F, 0F);
        final ModelPart.Vertex l3 = new ModelPart.Vertex(x0, y1, z1, 0F, 0F);
        final Map<Direction, ModelPart.Vertex[]> corners = Map.of(
            Direction.DOWN, new ModelPart.Vertex[]{l1, l0, t0, t1},
            Direction.UP, new ModelPart.Vertex[]{t2, t3, l3, l2},
            Direction.WEST, new ModelPart.Vertex[]{t0, l0, l3, t3},
            Direction.NORTH, new ModelPart.Vertex[]{t1, t0, t3, t2},
            Direction.EAST, new ModelPart.Vertex[]{l1, t1, t2, l2},
            Direction.SOUTH, new ModelPart.Vertex[]{l0, l1, l2, l3});
        int polygon = 0;
        for (final Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH}) {
            final float[] area = areas.get(direction);
            if (area != null) {
                javaCube.polygons[polygon++] = new ModelPart.Polygon(corners.get(direction).clone(), area[0], area[1], area[2], area[3],
                    geometry.textureWidth(), geometry.textureHeight(), mirror, direction);
            }
        }
        return javaCube;
    }

    /**
     * How the animations move the bones from their rest pose, by lowercase bone name: Bedrock's rotation in degrees,
     * position offset and scale, x, y and z each.
     */
    public record Pose(Map<String, float[]> bones) {

        public static final Pose NONE = new Pose(Map.of());

    }

}
