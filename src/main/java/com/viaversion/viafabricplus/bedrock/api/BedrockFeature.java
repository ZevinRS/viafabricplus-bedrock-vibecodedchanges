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

import java.util.Collection;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Something ViaFabricPlus Bedrock does, which can be turned off and has values it uses. All features are on by
 * default, except the ones meant for development, like {@link BedrockFeatures#PACKET_RECORDING}.
 * <p>
 * Features that change what the client sends make it look less like a Bedrock client when they're off, which some
 * servers' anticheats notice.
 */
public interface BedrockFeature {

    String id();

    /**
     * @return what the feature does, in English
     */
    String description();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    /**
     * @return when turning the feature on or off takes effect
     */
    Timing timing();

    /**
     * @return the values the feature uses
     */
    Collection<BedrockValue<?>> values();

    /**
     * @return the value with the id, or null if the feature has none
     */
    @Nullable BedrockValue<?> value(String id);

    /**
     * @throws IllegalArgumentException if the feature has no such value, or it's of another type
     */
    <T> BedrockValue<T> value(String id, Class<T> type);

    /**
     * @param listener called with the new state whenever it changes, on the thread that changed it
     */
    void addListener(Consumer<Boolean> listener);

    void removeListener(Consumer<Boolean> listener);

    enum Timing {
        /**
         * Right away, even while connected.
         */
        IMMEDIATELY,
        /**
         * For things that are set up when they appear, like entities spawning.
         */
        NEXT_SPAWN,
        /**
         * On the next join or server transfer.
         */
        NEXT_JOIN
    }

}
