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

package com.viaversion.viafabricplus.bedrock.block;

import com.viaversion.nbt.tag.ByteTag;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.NumberTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.nbt.tag.Tag;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.raphimc.viabedrock.api.util.MoLangEngine;
import org.jetbrains.annotations.Nullable;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.standard.MochaMath;

/**
 * Works out what a block state a server defines is like, from its components in start_game: the components of the
 * block, replaced by those of every permutation whose condition holds for the state.
 */
public final class BedrockCustomBlockDefinitions {

    private static final Pattern STRING_LITERAL = Pattern.compile("'[^']*'");
    private static final Pattern STATE_QUERY = Pattern.compile("(?:query|q)\\.block_(?:property|state)\\(\\s*'([^']+)'\\s*\\)");
    private static final Scope SCOPE = Scope.create();
    private static final String FULL_BLOCK_GEOMETRY = "minecraft:geometry.full_block";

    static {
        SCOPE.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
        SCOPE.readOnly(true);
    }

    private BedrockCustomBlockDefinitions() {
    }

    /**
     * @param blockProperties the block's definition from start_game
     * @param states          the properties of the block state
     */
    public static BedrockCustomBlockDefinition create(final String name, final CompoundTag blockProperties, final CompoundTag states) {
        final CompoundTag components = new CompoundTag();
        if (blockProperties.get("components") instanceof final CompoundTag base) {
            for (final Map.Entry<String, Tag> component : base.entrySet()) {
                components.put(component.getKey(), component.getValue());
            }
        }
        if (blockProperties.get("permutations") instanceof final ListTag<?> permutations) {
            for (final Tag permutation : permutations) {
                if (permutation instanceof final CompoundTag compound && compound.get("components") instanceof final CompoundTag permutationComponents
                    && holds(compound.getString("condition", ""), states)) {
                    for (final Map.Entry<String, Tag> component : permutationComponents.entrySet()) {
                        components.put(component.getKey(), component.getValue());
                    }
                }
            }
        }

        String geometry = components.get("minecraft:geometry") instanceof final CompoundTag geometryTag ? geometryTag.getString("identifier", null) : null;
        if (FULL_BLOCK_GEOMETRY.equals(geometry)) {
            geometry = null;
        }

        final Map<String, String> textures = new LinkedHashMap<>();
        String renderMethod = null;
        if (components.get("minecraft:material_instances") instanceof final CompoundTag materialInstances
            && materialInstances.get("materials") instanceof final CompoundTag materials) {
            for (final Map.Entry<String, Tag> material : materials.entrySet()) {
                if (material.getValue() instanceof final CompoundTag instance && instance.getString("texture", null) != null) {
                    textures.put(material.getKey(), instance.getString("texture"));
                    if (renderMethod == null || material.getKey().equals("*")) {
                        renderMethod = instance.getString("render_method", null);
                    }
                }
            }
        }

        final float[] rotation = new float[3];
        if (components.get("minecraft:transformation") instanceof final CompoundTag transformation) {
            rotation[0] = transformation.getInt("RX", 0) * 90F;
            rotation[1] = transformation.getInt("RY", 0) * 90F;
            rotation[2] = transformation.getInt("RZ", 0) * 90F;
        }

        final VoxelShape collision = shape(components.get("minecraft:collision_box"), rotation[1]);
        final VoxelShape outline = components.get("minecraft:selection_box") != null ? shape(components.get("minecraft:selection_box"), rotation[1]) : collision;
        final float friction = components.get("minecraft:friction") instanceof final CompoundTag frictionTag ? 1F - frictionTag.getFloat("value", 0.4F) : 0.6F;
        final float destroyTime = components.get("minecraft:destructible_by_mining") instanceof final CompoundTag destructible ? destructible.getFloat("value", 0F) : 0F;
        final int lightEmission = components.get("minecraft:light_emission") instanceof final CompoundTag light ? light.getInt("emission", 0) : 0;
        final int lightFilter = components.get("minecraft:block_light_filter") instanceof final CompoundTag filter ? filter.getInt("lightLevel", 15) : 15;
        return new BedrockCustomBlockDefinition(name, geometry, textures, renderMethod, rotation, collision, outline, friction, destroyTime, lightEmission, lightFilter);
    }

    /**
     * @return whether the block is a plain full cube, which a full block of the pool can be
     */
    public static boolean isFullBlock(final BedrockCustomBlockDefinition definition) {
        return definition.geometry() == null && definition.collision() == Shapes.block() && definition.outline() == Shapes.block();
    }

    /**
     * @return the shape of a collision or selection box, in block coordinates from 0 to 16 and turned around the
     * block's center by the yaw of its transformation
     */
    private static VoxelShape shape(final @Nullable Tag box, final float yaw) {
        if (!(box instanceof final CompoundTag compound)) {
            return Shapes.block();
        }
        if (compound.get("enabled") instanceof final NumberTag enabled && enabled.asInt() == 0) {
            return Shapes.empty();
        }
        VoxelShape shape = Shapes.empty();
        if (compound.get("boxes") instanceof final ListTag<?> boxes && !boxes.isEmpty()) {
            for (final Tag boxTag : boxes) {
                if (boxTag instanceof final CompoundTag b) {
                    shape = Shapes.or(shape, box(b.getFloat("minX"), b.getFloat("minY"), b.getFloat("minZ"), b.getFloat("maxX"), b.getFloat("maxY"), b.getFloat("maxZ"), yaw));
                }
            }
        } else if (compound.get("origin") instanceof final ListTag<?> origin && compound.get("size") instanceof final ListTag<?> size && origin.size() == 3 && size.size() == 3) {
            final float x = number(origin.get(0)) + 8F;
            final float y = number(origin.get(1));
            final float z = number(origin.get(2)) + 8F;
            shape = box(x, y, z, x + number(size.get(0)), y + number(size.get(1)), z + number(size.get(2)), yaw);
        } else {
            return Shapes.block();
        }
        return shape.isEmpty() ? Shapes.empty() : shape.optimize();
    }

    private static VoxelShape box(float minX, final float minY, float minZ, float maxX, final float maxY, float maxZ, final float yaw) {
        // Turned counterclockwise seen from above, in quarter turns
        final int quarterTurns = Math.floorMod(Math.round(yaw / 90F), 4);
        for (int i = 0; i < quarterTurns; i++) {
            final float newMinX = minZ;
            final float newMaxX = maxZ;
            minZ = 16F - maxX;
            maxZ = 16F - minX;
            minX = newMinX;
            maxX = newMaxX;
        }
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return Shapes.empty();
        }
        return Shapes.box(clamp(minX) / 16.0, clamp(minY) / 16.0, clamp(minZ) / 16.0, clamp(maxX) / 16.0, clamp(maxY) / 16.0, clamp(maxZ) / 16.0);
    }

    private static double clamp(final float value) {
        return Math.max(0F, Math.min(16F, value));
    }

    private static float number(final Tag tag) {
        return tag instanceof final NumberTag number ? number.asFloat() : 0F;
    }

    /**
     * The MoLang engine compares strings as numbers, so 'top' == 'bottom' held and every permutation of a slab's halves
     * applied. Gives every distinct string a number of its own instead.
     */
    private static String numberStrings(final String expression) {
        final Map<String, Integer> numbers = new HashMap<>();
        final Matcher matcher = STRING_LITERAL.matcher(expression);
        final StringBuilder numbered = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(numbered, Integer.toString(numbers.computeIfAbsent(matcher.group(), string -> 1000000 + numbers.size())));
        }
        matcher.appendTail(numbered);
        return numbered.toString();
    }

    /**
     * @return whether a permutation's condition holds for the state, with its block properties put in
     */
    private static boolean holds(final String condition, final CompoundTag states) {
        if (condition.isBlank()) {
            return true;
        }
        final Matcher matcher = STATE_QUERY.matcher(condition);
        final StringBuilder expression = new StringBuilder();
        while (matcher.find()) {
            final Tag value = states.get(matcher.group(1));
            final String literal;
            if (value instanceof final StringTag string) {
                literal = "'" + string.getValue().replace("'", "") + "'";
            } else if (value instanceof final ByteTag byteTag) {
                literal = Integer.toString(byteTag.asByte());
            } else if (value instanceof final NumberTag number) {
                literal = Float.toString(number.asFloat());
            } else {
                literal = "0";
            }
            matcher.appendReplacement(expression, Matcher.quoteReplacement(literal));
        }
        matcher.appendTail(expression);
        try {
            return MoLangEngine.eval(SCOPE, numberStrings(expression.toString())).getAsBoolean();
        } catch (final Throwable e) {
            return false;
        }
    }

}
