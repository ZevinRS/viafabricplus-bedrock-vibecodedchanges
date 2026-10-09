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

package com.viaversion.viafabricplus.bedrock.api;

import java.awt.image.BufferedImage;
import org.jetbrains.annotations.Nullable;

/**
 * A Bedrock player's skin as the server sent it.
 *
 * @param image        the skin texture
 * @param cape         the cape texture, or null
 * @param geometryName the identifier of the geometry the skin uses, like {@code geometry.humanoid.custom}
 * @param geometryData the skin's geometry file as JSON, or empty if it uses Bedrock's own
 * @param slim         whether the skin has slim arms
 * @param customShape  whether the geometry reshapes the player beyond Bedrock's player model, a 4D skin
 */
public record BedrockSkin(BufferedImage image, @Nullable BufferedImage cape, String geometryName, String geometryData, boolean slim, boolean customShape) {
}
