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

import net.minecraft.world.entity.Entity;

/**
 * A query of the MoLang expressions in the animations of custom entities, see
 * {@link ViaFabricPlusBedrockApi#registerMoLangQuery(String, MoLangQuery)}.
 */
@FunctionalInterface
public interface MoLangQuery {

    /**
     * Called on the render thread every frame the entity's animations use the query.
     *
     * @param entity       the Java entity the custom entity is drawn for
     * @param identifier   the custom entity's Bedrock identifier, like {@code hivehub:npc_store}
     * @param partialTicks how far the frame is into the tick
     * @param arguments    the arguments the expression passed, none for {@code query.name} without parentheses
     * @return the query's value
     */
    double evaluate(Entity entity, String identifier, float partialTicks, double[] arguments);

}
