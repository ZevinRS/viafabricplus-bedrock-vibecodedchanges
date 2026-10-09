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

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.api.BedrockFeature;
import com.viaversion.viafabricplus.bedrock.api.BedrockValue;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.jetbrains.annotations.Nullable;

public class FeatureImpl implements BedrockFeature {

    private final String id;
    private final String description;
    private final Timing timing;
    private final Map<String, ValueImpl<?>> values = new LinkedHashMap<>();
    private final List<Consumer<Boolean>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean enabled;

    public FeatureImpl(final String id, final String description, final Timing timing, final boolean enabled) {
        this.id = id;
        this.description = description;
        this.timing = timing;
        this.enabled = enabled;
    }

    <T> ValueImpl<T> add(final ValueImpl<T> value) {
        this.values.put(value.id(), value);
        return value;
    }

    /**
     * @return whether the feature is on and the client plays on Bedrock, which the mod checks before doing anything
     */
    public boolean isActive() {
        return this.isEnabled() && ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST);
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
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        if (this.isEnabled() == enabled) {
            return;
        }
        this.store(enabled);
        for (final Consumer<Boolean> listener : this.listeners) {
            try {
                listener.accept(enabled);
            } catch (final RuntimeException e) {
                ViaFabricPlusBedrock.impl().logger().error("A listener of the feature {} failed", this.id, e);
            }
        }
    }

    protected void store(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Timing timing() {
        return this.timing;
    }

    @Override
    public Collection<BedrockValue<?>> values() {
        return Collections.unmodifiableCollection(this.values.values());
    }

    @Override
    public @Nullable BedrockValue<?> value(final String id) {
        return this.values.get(id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> BedrockValue<T> value(final String id, final Class<T> type) {
        final ValueImpl<?> value = this.values.get(id);
        if (value == null) {
            throw new IllegalArgumentException("The feature " + this.id + " has no value " + id);
        }
        if (value.type() != type) {
            throw new IllegalArgumentException("The value " + this.id + "." + id + " is a " + value.type().getSimpleName() + ", not a " + type.getSimpleName());
        }
        return (BedrockValue<T>) value;
    }

    @Override
    public void addListener(final Consumer<Boolean> listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(final Consumer<Boolean> listener) {
        this.listeners.remove(listener);
    }

}
