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


package com.viaversion.viafabricplus.bedrock.screen.form;

import com.viaversion.viafabricplus.ViaFabricPlus;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

/** Helpers for showing ViaBedrock's form dialogs closer to how the Bedrock client shows forms. */
public final class BedrockForms {

    /** Width of form buttons and body text, matching ViaBedrock's widest form element. */
    public static final int WIDTH = 300;

    // Servers with custom JSON UI prefix form titles with a routing key like "@mineville/gamemodes_menu:",
    // which the Bedrock client never displays
    private static final Pattern TITLE_ROUTING_PREFIX = Pattern.compile("^@[A-Za-z0-9_./-]+:");

    // ViaBedrock shows form labels and headers as fake buttons with this tooltip
    private static final String FAKE_BUTTON_TOOLTIP = "This is not actually a button";

    private BedrockForms() {
    }

    public static boolean isActive() {
        return BedrockProtocolVersion.BEDROCK_LATEST.equals(ViaFabricPlus.api().targetVersion());
    }

    public static int width(final int screenWidth) {
        return Math.max(150, Math.min(WIDTH, screenWidth - 40));
    }

    public static boolean isFakeButton(final Component tooltip) {
        return tooltip.getString().startsWith(FAKE_BUTTON_TOOLTIP);
    }

    public static Component stripTitlePrefix(final Component title) {
        final Matcher matcher = TITLE_ROUTING_PREFIX.matcher(title.getString());
        if (!matcher.find()) {
            return title;
        }

        final int[] skip = {matcher.end()};
        final MutableComponent result = Component.empty();
        title.visit((style, text) -> {
            final int skipped = Math.min(skip[0], text.length());
            skip[0] -= skipped;
            if (skipped < text.length()) {
                result.append(Component.literal(text.substring(skipped)).withStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

}
