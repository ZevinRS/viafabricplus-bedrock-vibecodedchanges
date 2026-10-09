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

import com.viaversion.viafabricplus.api.settings.base.BooleanSetting;
import java.util.function.Supplier;

/**
 * A feature that is one of the mod's settings, which the player can change in ViaFabricPlus' settings too.
 */
final class SettingFeatureImpl extends FeatureImpl {

    private final Supplier<BooleanSetting> setting;

    SettingFeatureImpl(final String id, final String description, final Timing timing, final Supplier<BooleanSetting> setting) {
        super(id, description, timing, false);
        this.setting = setting;
    }

    @Override
    public boolean isEnabled() {
        return this.setting.get().isActive();
    }

    @Override
    protected void store(final boolean enabled) {
        this.setting.get().setActive(enabled);
    }

}
