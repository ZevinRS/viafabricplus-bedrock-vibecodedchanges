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

import com.viaversion.viaversion.libs.gson.JsonObject;
import net.raphimc.viabedrock.protocol.rewriter.resourcepack.GlyphSheetResourceRewriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GlyphSheetResourceRewriter.class, remap = false)
public abstract class MixinGlyphSheetResourceRewriter {

    @Unique
    private static final int viaFabricPlusBedrock$GLYPH_HEIGHT = 8;

    @Unique
    private static final int viaFabricPlusBedrock$GLYPH_ASCENT = 7;

    /**
     * ViaBedrock sizes glyphs by the pixel size of the sheet cells, so high resolution sheets render huge and sheets
     * with cells smaller than 5 pixels fail to load with the whole font file. Bedrock draws every glyph cell at the
     * same size regardless of the sheet resolution, so use the size and baseline of normal text instead.
     */
    @Redirect(method = "handleGlyphSheets", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/libs/gson/JsonObject;addProperty(Ljava/lang/String;Ljava/lang/Number;)V"))
    private void useTextGlyphSize(final JsonObject glyphPage, final String property, final Number value) {
        switch (property) {
            case "height" -> glyphPage.addProperty(property, viaFabricPlusBedrock$GLYPH_HEIGHT);
            case "ascent" -> glyphPage.addProperty(property, viaFabricPlusBedrock$GLYPH_ASCENT);
            default -> glyphPage.addProperty(property, value);
        }
    }

}
