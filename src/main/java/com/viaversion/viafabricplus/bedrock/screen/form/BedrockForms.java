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
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.libs.fastutil.ints.IntObjectPair;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.lenni0451.mcstructs_bedrock.forms.Form;
import net.lenni0451.mcstructs_bedrock.forms.elements.ButtonFormElement;
import net.lenni0451.mcstructs_bedrock.forms.elements.FormElement;
import net.lenni0451.mcstructs_bedrock.forms.elements.FormImage;
import net.lenni0451.mcstructs_bedrock.forms.types.ActionForm;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.action.CustomAll;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Helpers for showing ViaBedrock's form dialogs closer to how the Bedrock client shows forms.
 * <p>
 * Servers can replace Bedrock's form layout with their own (ui/server_form.json in their resource pack). These
 * layouts pick a design based on markers in the form title and commonly show buttons as a grid of tiles: the button
 * image above the first two lines of the button text, with the full text shown when hovering the tile.
 */
public final class BedrockForms {

    /** Width of form buttons and body text, matching ViaBedrock's widest form element. */
    public static final int WIDTH = 300;

    private static final int GRID_WIDTH = 400;
    private static final int MAX_COLUMNS = 4;
    private static final int MIN_TILE_WIDTH = 96;
    public static final int TILE_SPACING = 2; // Spacing used by Java's dialog button grid

    // Routing key of custom layouts which the layouts hide, like "@mineville/gamemodes_menu:"
    private static final Pattern TITLE_ROUTING_PREFIX = Pattern.compile("^@[A-Za-z0-9_./-]+:");
    // Count of category buttons which grid layouts read from the first three characters and hide, like ";04"
    private static final Pattern TITLE_CATEGORY_COUNT = Pattern.compile("^;\\d{2}");

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

    /**
     * @return whether the server's resource packs replace Bedrock's form layout
     */
    public static boolean hasCustomFormLayout() {
        final UserConnection user = ViaFabricPlus.api().userConnection();
        final ResourcePackStorage storage = user != null ? user.get(ResourcePackStorage.class) : null;
        if (storage == null) {
            return false;
        }
        for (final ResourcePack pack : storage.getPackStackTopToBottom()) {
            if (pack.content().contains("ui/server_form.json")) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether the current form should be shown as a grid of tiles
     */
    public static boolean useTiles() {
        if (!isActive() || !hasCustomFormLayout()) {
            return false;
        }
        final ActionForm form = currentActionForm();
        if (form == null || form.getElements().length == 0) {
            return false;
        }
        for (final FormElement element : form.getElements()) {
            if (!(element instanceof ButtonFormElement)) { // Labels and headers need the full width
                return false;
            }
        }
        return true;
    }

    public static int tileColumns(final int screenWidth) {
        final int gridWidth = Math.min(GRID_WIDTH, screenWidth - 40);
        return Math.clamp((gridWidth + TILE_SPACING) / (MIN_TILE_WIDTH + TILE_SPACING), 1, MAX_COLUMNS);
    }

    public static int tileWidth(final int screenWidth) {
        final int columns = tileColumns(screenWidth);
        final int gridWidth = Math.max(MIN_TILE_WIDTH, Math.min(GRID_WIDTH, screenWidth - 40));
        return Math.max(MIN_TILE_WIDTH, (gridWidth - TILE_SPACING * (columns - 1)) / columns);
    }

    /**
     * @return the index ViaBedrock sends back when the button is pressed, empty for the exit button
     */
    public static OptionalInt buttonId(final ActionButton actionButton) {
        if (actionButton.action().orElse(null) instanceof final CustomAll customAll) {
            final Optional<Integer> buttonId = customAll.additions().flatMap(additions -> additions.getInt("button_id"));
            if (buttonId.isPresent()) {
                return OptionalInt.of(buttonId.get());
            }
        }
        return OptionalInt.empty();
    }

    public static @Nullable FormImage buttonImage(final int buttonId) {
        final ActionForm form = currentActionForm();
        if (form == null) {
            return null;
        }
        int index = 0;
        for (final FormElement element : form.getElements()) {
            if (element instanceof final ButtonFormElement button && index++ == buttonId) {
                return button.getImage();
            }
        }
        return null;
    }

    private static @Nullable ActionForm currentActionForm() {
        final UserConnection user = ViaFabricPlus.api().userConnection();
        final InventoryTracker tracker = user != null ? user.get(InventoryTracker.class) : null;
        final IntObjectPair<Form> currentForm = tracker != null ? tracker.getCurrentForm() : null;
        return currentForm != null && currentForm.right() instanceof final ActionForm actionForm ? actionForm : null;
    }

    public static Component stripTitlePrefix(final Component title) {
        Component result = strip(title, TITLE_ROUTING_PREFIX);
        if (hasCustomFormLayout()) {
            result = strip(result, TITLE_CATEGORY_COUNT);
        }
        return result;
    }

    private static Component strip(final Component text, final Pattern prefix) {
        final Matcher matcher = prefix.matcher(text.getString());
        if (!matcher.find()) {
            return text;
        }

        final int[] skip = {matcher.end()};
        final MutableComponent result = Component.empty();
        text.visit((style, part) -> {
            final int skipped = Math.min(skip[0], part.length());
            skip[0] -= skipped;
            if (skipped < part.length()) {
                result.append(Component.literal(part.substring(skipped)).withStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

}
