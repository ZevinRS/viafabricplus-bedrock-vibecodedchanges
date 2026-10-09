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

import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockPackIndex;
import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * How a Bedrock entity material draws: whether it hides faces seen from behind and whether it blends. The Hive's logo
 * in its hub is boxed in by faces that only show from inside, which its one sided material hides from outside.
 */
public record BedrockMaterial(boolean culled, boolean blended) {

    public static final BedrockMaterial ALPHA_TEST = new BedrockMaterial(false, false);

    public RenderType renderType(final Identifier texture) {
        if (this.blended) {
            return this.culled ? RenderTypes.entityTranslucentCull(texture) : RenderTypes.entityTranslucent(texture);
        }
        return this.culled ? RenderTypes.entityCutoutCull(texture) : RenderTypes.entityCutout(texture);
    }

    /**
     * @param name a material of the packs, which is based on another, or one of Bedrock's
     */
    public static BedrockMaterial resolve(final BedrockPackIndex index, final String name) {
        return resolve(index, name, 0);
    }

    private static BedrockMaterial resolve(final BedrockPackIndex index, final String name, final int depth) {
        final Map.Entry<String, JsonObject> material = depth < 8 ? index.material(name) : null;
        if (material == null || material.getKey().isEmpty()) {
            return vanilla(name);
        }
        boolean culled = resolve(index, material.getKey(), depth + 1).culled();
        boolean blended = resolve(index, material.getKey(), depth + 1).blended();
        final JsonObject changes = material.getValue();
        if (has(changes.get("+states"), "DisableCulling")) {
            culled = false;
        }
        if (has(changes.get("-states"), "DisableCulling")) {
            culled = true;
        }
        if (has(changes.get("+states"), "Blending")) {
            blended = true;
        }
        if (has(changes.get("-states"), "Blending")) {
            blended = false;
        }
        return new BedrockMaterial(culled, blended);
    }

    /**
     * Bedrock's own entity materials by their names: the one sided ones and the opaque ones hide back faces, the ones
     * with alpha don't.
     */
    private static BedrockMaterial vanilla(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        final boolean blended = lower.contains("blend") || lower.equals("slime_outer");
        final boolean culled = lower.contains("one_sided") || !(lower.contains("alpha") || lower.contains("nocull") || blended);
        return new BedrockMaterial(culled, blended);
    }

    private static boolean has(final JsonElement states, final String state) {
        if (states instanceof final JsonArray list) {
            for (final JsonElement element : list) {
                if (element.isJsonPrimitive() && element.getAsString().equals(state)) {
                    return true;
                }
            }
        }
        return false;
    }

}
