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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.block;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.injection.access.IBlockStatePredictionHandler;
import com.viaversion.viafabricplus.bedrock.injection.mixin.core.access.MixinBlockStatePredictionHandler_ServerVerifiedState;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Java reverts predicted blocks to the last state received from the server when the server acknowledges the
 * prediction. Bedrock has no acknowledgements, so ViaBedrock acknowledges right away, before the Bedrock server sent
 * the result. Placed blocks then disappear until the server's block update arrives, letting the player fall into them.
 * <p>
 * On Bedrock, acknowledged predictions are only ended once the server sent the block, or after a timeout in case the
 * server doesn't send it (e.g. because it didn't change).
 */
@Mixin(BlockStatePredictionHandler.class)
public abstract class MixinBlockStatePredictionHandler implements IBlockStatePredictionHandler {

    @Unique
    private static final long viaFabricPlusBedrock$SERVER_UPDATE_TIMEOUT = 1500;

    @Shadow
    @Final
    private Long2ObjectOpenHashMap<?> serverVerifiedStates;

    @Shadow
    private int lastTeleportSequence;

    @Unique
    private final LongSet viaFabricPlusBedrock$serverUpdated = new LongOpenHashSet();

    @Unique
    private final Long2LongMap viaFabricPlusBedrock$acknowledgedAt = new Long2LongOpenHashMap();

    @Unique
    private int viaFabricPlusBedrock$acknowledgedSequence = -1;

    @Inject(method = "retainKnownServerState", at = @At("HEAD"))
    private void resetServerUpdate(final BlockPos pos, final BlockState state, final LocalPlayer player, final CallbackInfo ci) {
        // The position is predicted again and needs a new server update
        this.viaFabricPlusBedrock$serverUpdated.remove(pos.asLong());
        this.viaFabricPlusBedrock$acknowledgedAt.remove(pos.asLong());
    }

    @Inject(method = "updateKnownServerState", at = @At("RETURN"))
    private void trackServerUpdate(final BlockPos pos, final BlockState blockState, final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            this.viaFabricPlusBedrock$serverUpdated.add(pos.asLong());
        }
    }

    @Inject(method = "endPredictionsUpTo", at = @At("HEAD"), cancellable = true)
    private void waitForServerUpdates(final int sequence, final ClientLevel clientLevel, final CallbackInfo ci) {
        if (!BedrockProtocolVersion.BEDROCK_LATEST.equals(ViaFabricPlus.api().targetVersion())) {
            return;
        }
        ci.cancel();

        final long now = Util.getMillis();
        for (final Long2ObjectOpenHashMap.Entry<?> entry : this.serverVerifiedStates.long2ObjectEntrySet()) {
            if (((MixinBlockStatePredictionHandler_ServerVerifiedState) entry.getValue()).viaFabricPlusBedrock$getSequence() <= sequence) {
                this.viaFabricPlusBedrock$acknowledgedAt.putIfAbsent(entry.getLongKey(), now);
            }
        }
        this.viaFabricPlusBedrock$acknowledgedSequence = Math.max(this.viaFabricPlusBedrock$acknowledgedSequence, sequence);
        this.viaFabricPlusBedrock$endAcknowledgedPredictions(clientLevel);
    }

    @Override
    public void viaFabricPlusBedrock$endAcknowledgedPredictions(final ClientLevel level) {
        if (this.viaFabricPlusBedrock$acknowledgedAt.isEmpty()) {
            return;
        }

        final long now = Util.getMillis();
        final ObjectIterator<? extends Long2ObjectOpenHashMap.Entry<?>> iterator = this.serverVerifiedStates.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            final Long2ObjectOpenHashMap.Entry<?> entry = iterator.next();
            final long key = entry.getLongKey();
            if (!this.viaFabricPlusBedrock$acknowledgedAt.containsKey(key)) {
                continue;
            }
            if (!this.viaFabricPlusBedrock$serverUpdated.contains(key) && now - this.viaFabricPlusBedrock$acknowledgedAt.get(key) < viaFabricPlusBedrock$SERVER_UPDATE_TIMEOUT) {
                continue;
            }

            final MixinBlockStatePredictionHandler_ServerVerifiedState state = (MixinBlockStatePredictionHandler_ServerVerifiedState) entry.getValue();
            iterator.remove();
            this.viaFabricPlusBedrock$serverUpdated.remove(key);
            this.viaFabricPlusBedrock$acknowledgedAt.remove(key);
            level.syncBlockState(BlockPos.of(key), state.viaFabricPlusBedrock$getBlockState(), this.lastTeleportSequence < this.viaFabricPlusBedrock$acknowledgedSequence ? state.viaFabricPlusBedrock$getPlayerPos() : null);
        }
    }

}
