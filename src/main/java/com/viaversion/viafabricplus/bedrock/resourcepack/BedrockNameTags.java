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

import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.api.model.entity.Entity;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorDataIds;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorFlags;
import org.jetbrains.annotations.Nullable;

/**
 * The name tags of Bedrock entities, which ViaBedrock doesn't translate: servers name entities to label them, like
 * The Hive's NPCs, the countdown of its boom boxes and the text it floats on invisible sheep. Bedrock draws every line
 * of the name, above the entity's bounding box height, and hides the body of entities scaled to 0. Players kept their
 * profile name instead, which for The Hive's costume NPCs is a translation request.
 * <p>
 * Filled on the network thread from ViaBedrock's entity data and read on the render thread by Java entity id.
 */
public final class BedrockNameTags {

    private static final Map<Integer, NameTag> NAME_TAGS = new ConcurrentHashMap<>();

    private BedrockNameTags() {
    }

    public static void update(final Entity entity) {
        if (entity instanceof ClientPlayerEntity) {
            return;
        }
        final Map<ActorDataIds, EntityData> data = entity.entityData();
        final Set<ActorFlags> flags = entity.entityFlags();
        final String name = data.get(ActorDataIds.NAME) != null && data.get(ActorDataIds.NAME).value() instanceof final String value ? value : "";
        final boolean alwaysShow = data.get(ActorDataIds.NAMETAG_ALWAYS_SHOW) != null && data.get(ActorDataIds.NAMETAG_ALWAYS_SHOW).value() instanceof final Number value
            && value.intValue() != 0 || flags.contains(ActorFlags.ALWAYS_SHOW_NAME);
        final float scale = data.get(ActorDataIds.RESERVED_038) != null && data.get(ActorDataIds.RESERVED_038).value() instanceof final Float value ? value : 1F; // scale
        final float height = data.get(ActorDataIds.RESERVED_054) != null && data.get(ActorDataIds.RESERVED_054).value() instanceof final Float value ? value : -1F; // bounding box height
        NAME_TAGS.put(entity.javaId(), new NameTag(name.isEmpty() ? List.of() : name.lines().map(line -> (Component) Component.literal(line)).toList(),
            alwaysShow, flags.contains(ActorFlags.CAN_SHOW_NAME), scale, height));
    }

    public static void remove(final int javaId) {
        NAME_TAGS.remove(javaId);
    }

    public static void clear() {
        NAME_TAGS.clear();
    }

    public static @Nullable NameTag get(final int javaId) {
        return NAME_TAGS.get(javaId);
    }

    /**
     * @param lines      the lines of the name, top to bottom, with their formatting codes
     * @param alwaysShow whether the name shows without looking at the entity
     * @param canShow    whether the name shows when looking at the entity
     * @param height     the bounding box height the name is drawn above, or -1 for the Java entity's
     */
    public record NameTag(List<Component> lines, boolean alwaysShow, boolean canShow, float scale, float height) {

        public boolean hidesBody() {
            return this.scale <= 0F;
        }

    }

}
