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

package com.viaversion.viafabricplus.bedrock.feature;

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.api.BedrockValue;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public final class ValueImpl<T> implements BedrockValue<T> {

    private final String id;
    private final String description;
    private final Class<T> type;
    private final T defaultValue;
    private final @Nullable T min;
    private final @Nullable T max;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    ValueImpl(final String id, final String description, final Class<T> type, final T defaultValue, final @Nullable T min, final @Nullable T max) {
        this.id = id;
        this.description = description;
        this.type = type;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.value = defaultValue;
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public String description() {
        return this.description;
    }

    @Override
    public Class<T> type() {
        return this.type;
    }

    @Override
    public T get() {
        return this.value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void set(final T value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof final Comparable<?> comparable && this.min != null && this.max != null
            && (((Comparable<T>) comparable).compareTo(this.min) < 0 || ((Comparable<T>) comparable).compareTo(this.max) > 0)) {
            throw new IllegalArgumentException("The value " + this.id + " has to be from " + this.min + " to " + this.max + ", not " + value);
        }
        if (value.equals(this.value)) {
            return;
        }
        this.value = value;
        for (final Consumer<T> listener : this.listeners) {
            try {
                listener.accept(value);
            } catch (final RuntimeException e) {
                ViaFabricPlusBedrock.impl().logger().error("A listener of the value {} failed", this.id, e);
            }
        }
    }

    @Override
    public T defaultValue() {
        return this.defaultValue;
    }

    @Override
    public void reset() {
        this.set(this.defaultValue);
    }

    @Override
    public @Nullable T min() {
        return this.min;
    }

    @Override
    public @Nullable T max() {
        return this.max;
    }

    @Override
    public void addListener(final Consumer<T> listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(final Consumer<T> listener) {
        this.listeners.remove(listener);
    }

}
