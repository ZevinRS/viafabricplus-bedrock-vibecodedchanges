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
import com.viaversion.viafabricplus.bedrock.api.BedrockSkin;
import com.viaversion.viafabricplus.bedrock.api.BedrockValue;
import com.viaversion.viafabricplus.bedrock.api.MoLangQuery;
import com.viaversion.viafabricplus.bedrock.api.ViaFabricPlusBedrockApi;
import com.viaversion.viafabricplus.bedrock.api.ViaFabricPlusBedrockApiEntrypoint;
import com.viaversion.viafabricplus.bedrock.render.BedrockSkins;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.jetbrains.annotations.Nullable;

public final class BedrockApiImpl implements ViaFabricPlusBedrockApi {

    public static final BedrockApiImpl INSTANCE = new BedrockApiImpl();

    private final Map<String, MoLangQuery> queries = new ConcurrentHashMap<>();

    private BedrockApiImpl() {
    }

    /**
     * Lets the mods that use the API set it up.
     */
    public void callEntrypoints() {
        for (final ViaFabricPlusBedrockApiEntrypoint entrypoint : FabricLoader.getInstance().getEntrypoints(ViaFabricPlusBedrockApiEntrypoint.KEY, ViaFabricPlusBedrockApiEntrypoint.class)) {
            try {
                entrypoint.onApiReady(this);
            } catch (final RuntimeException e) {
                ViaFabricPlusBedrock.impl().logger().error("A mod failed to set up ViaFabricPlus Bedrock's API: {}", entrypoint.getClass().getName(), e);
            }
        }
    }

    public Map<String, MoLangQuery> queries() {
        return this.queries;
    }

    @Override
    public Collection<BedrockFeature> features() {
        return Collections.unmodifiableList((List<? extends BedrockFeature>) Features.all());
    }

    @Override
    public @Nullable BedrockFeature feature(final String id) {
        for (final FeatureImpl feature : Features.all()) {
            if (feature.id().equals(id)) {
                return feature;
            }
        }
        return null;
    }

    @Override
    public <T> BedrockValue<T> value(final String featureId, final String valueId, final Class<T> type) {
        final BedrockFeature feature = this.feature(featureId);
        if (feature == null) {
            throw new IllegalArgumentException("There's no feature " + featureId);
        }
        return feature.value(valueId, type);
    }

    @Override
    public boolean isBedrockConnection() {
        return Minecraft.getInstance().getConnection() != null && ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST);
    }

    @Override
    public Optional<BedrockSkin> skin(final UUID player) {
        final BedrockSkins.Skin skin = BedrockSkins.get(player);
        return skin != null ? Optional.of(skin.api()) : Optional.empty();
    }

    @Override
    public void registerMoLangQuery(final String name, final MoLangQuery query) {
        this.queries.put(name.toLowerCase(Locale.ROOT), query);
    }

    @Override
    public void unregisterMoLangQuery(final String name) {
        this.queries.remove(name.toLowerCase(Locale.ROOT));
    }

}
