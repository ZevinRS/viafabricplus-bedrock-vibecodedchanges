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

package com.viaversion.viafabricplus.bedrock.injection.mixin.viabedrock;

import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.api.model.entity.LivingEntity;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.AttributeModifierOperation;
import net.raphimc.viabedrock.protocol.data.generated.java.Attributes;
import net.raphimc.viabedrock.protocol.model.EntityAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class MixinLivingEntity {

    private static final String BEDROCK_SPRINT_MODIFIER = "Sprinting speed boost";
    // Java's sprint modifier, which the client adds and removes itself when it starts and stops sprinting
    private static final String JAVA_SPRINT_MODIFIER = "minecraft:sprinting";
    private static final double JAVA_SPRINT_AMOUNT = 0.3;
    private static final int JAVA_ADD_MULTIPLIED_TOTAL = 2;

    /**
     * ViaBedrock sends the client player's speed including Bedrock's sprint boost as base value, without modifiers. The
     * client then drops its own sprint modifier, and adds it on top of the boosted speed when it starts sprinting again,
     * which makes it sprint 30% too fast until the server sends the speed again. So the base value is sent without
     * the sprint boost, and the sprint boost as Java's sprint modifier, which the client replaces when it sprints.
     */
    @Inject(method = "translateAttribute", at = @At("HEAD"), cancellable = true)
    private void sendSprintBoostAsJavaModifier(final EntityAttribute attribute, final PacketWrapper javaAttributes, final AtomicInteger attributeCount,
                                               final List<EntityData> javaEntityData, final CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ClientPlayerEntity) || !attribute.name().equals("minecraft:movement")) {
            return;
        }
        float sprintBoost = 0F;
        for (final EntityAttribute.Modifier modifier : attribute.modifiers()) {
            if (modifier.name().equals(BEDROCK_SPRINT_MODIFIER) && modifier.operation() == AttributeModifierOperation.OPERATION_MULTIPLY_TOTAL) {
                sprintBoost = modifier.amount();
            }
        }
        if (sprintBoost == 0F) {
            return;
        }

        javaAttributes.write(Types.VAR_INT, BedrockProtocol.MAPPINGS.getJavaEntityAttributes().get(Attributes.MOVEMENT_SPEED)); // attribute id
        javaAttributes.write(Types.DOUBLE, (double) (attribute.computeClampedValue() / (1F + sprintBoost))); // base value
        javaAttributes.write(Types.VAR_INT, 1); // modifier count
        javaAttributes.write(Types.STRING, JAVA_SPRINT_MODIFIER); // id
        javaAttributes.write(Types.DOUBLE, JAVA_SPRINT_AMOUNT); // amount
        javaAttributes.write(Types.VAR_INT, JAVA_ADD_MULTIPLIED_TOTAL); // operation
        attributeCount.incrementAndGet();
        cir.setReturnValue(true);
    }

}
