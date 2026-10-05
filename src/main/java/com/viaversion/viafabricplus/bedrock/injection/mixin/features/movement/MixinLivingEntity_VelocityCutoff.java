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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.LivingEntity;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Applied before ViaFabricPlus, which changes the same constant for 1.8 and older, so its value is kept for those.
 */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class MixinLivingEntity_VelocityCutoff {

    /**
     * Java zeroes velocity components below 0.003 every tick, Bedrock only lets them decay to about 1e-7, as recorded from
     * the Bedrock client. For example, a small sideways speed while running against a wall carries into a jump on Bedrock.
     */
    @ModifyConstant(method = "aiStep", constant = @Constant(doubleValue = 0.003))
    private double bedrockVelocityCutoff(final double cutoff) {
        final ProtocolVersion version = ViaFabricPlus.api().targetVersion();
        if (version.equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return 1.0E-7;
        }
        return version.olderThanOrEqualTo(ProtocolVersion.v1_8) ? 0.005 : cutoff;
    }

}
