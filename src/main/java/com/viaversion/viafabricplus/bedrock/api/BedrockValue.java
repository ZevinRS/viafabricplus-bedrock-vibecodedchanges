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

import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * A value a feature uses, like a reach distance. Changes take effect the next time the feature uses the value.
 *
 * @param <T> Integer, Double or String
 */
public interface BedrockValue<T> {

    String id();

    /**
     * @return what the value is, in English, with its unit
     */
    String description();

    Class<T> type();

    T get();

    /**
     * @throws IllegalArgumentException if a number is outside {@link #min()} and {@link #max()}
     */
    void set(T value);

    T defaultValue();

    void reset();

    /**
     * @return the lowest number allowed, or null for strings
     */
    @Nullable T min();

    /**
     * @return the highest number allowed, or null for strings
     */
    @Nullable T max();

    /**
     * @param listener called with the new value whenever it changes, on the thread that changed it
     */
    void addListener(Consumer<T> listener);

    void removeListener(Consumer<T> listener);

}
