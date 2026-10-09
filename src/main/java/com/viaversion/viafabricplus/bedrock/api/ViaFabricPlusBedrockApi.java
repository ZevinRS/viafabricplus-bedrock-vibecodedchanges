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

import com.viaversion.viafabricplus.bedrock.feature.BedrockApiImpl;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The API of ViaFabricPlus Bedrock, for other mods to turn its features on and off, change the values they use, and
 * read what it knows about the Bedrock server.
 * <p>
 * Get it with {@link #get()} any time after the game started, or implement {@link ViaFabricPlusBedrockApiEntrypoint}
 * to set things up as soon as it's ready. The ids of the built-in features and values are in {@link BedrockFeatures}.
 * <p>
 * Changes made through the API last until the game closes, mods set them up again on every start. Everything here can
 * be used from any thread.
 */
public interface ViaFabricPlusBedrockApi {

    static ViaFabricPlusBedrockApi get() {
        return BedrockApiImpl.INSTANCE;
    }

    /**
     * @return every feature, in the order they're listed in {@link BedrockFeatures}
     */
    Collection<BedrockFeature> features();

    /**
     * @param id a feature id, like {@link BedrockFeatures#REACH}
     * @return the feature, or null if there's none with the id
     */
    @Nullable BedrockFeature feature(String id);

    /**
     * Shortcut for {@code feature(id).value(valueId)}.
     *
     * @throws IllegalArgumentException if there's no such feature or value
     */
    <T> BedrockValue<T> value(String featureId, String valueId, Class<T> type);

    /**
     * @return whether the client is connected to a Bedrock server, which the features only change anything on
     */
    boolean isBedrockConnection();

    /**
     * @return the Bedrock skin a player wears, if the server sent one
     */
    Optional<BedrockSkin> skin(UUID player);

    /**
     * Adds a query for the MoLang expressions of the animations of custom entities, or replaces a built-in one, like
     * {@code query.my_query} or {@code q.my_query(1, 2)}.
     *
     * @param name the name after {@code query.}, lowercase
     */
    void registerMoLangQuery(String name, MoLangQuery query);

    void unregisterMoLangQuery(String name);

}
