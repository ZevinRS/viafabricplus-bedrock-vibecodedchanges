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

import java.util.Map;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A block state a server defined, with the components that apply to it.
 *
 * @param name          the Bedrock block identifier, like hive:anchor
 * @param geometry      the identifier of its model in the packs, or null for a full block
 * @param textures      the texture short names by face: *, up, down, north, south, east, west, side, or a material name
 * @param rotation      the rotation of the model around x, y and z in degrees
 * @param collision     the shape players collide with
 * @param outline       the shape that's targeted
 * @param friction      Java's friction, 0.6 by default
 * @param destroyTime   the time to break it, like Java's destroy time
 * @param lightEmission the light it gives off
 * @param lightFilter   how much light it takes away when passing through, from 0 to 15
 */
public record BedrockCustomBlockDefinition(String name, @Nullable String geometry, Map<String, String> textures, float[] rotation, VoxelShape collision,
                                           VoxelShape outline, float friction, float destroyTime, int lightEmission, int lightFilter) {
}
