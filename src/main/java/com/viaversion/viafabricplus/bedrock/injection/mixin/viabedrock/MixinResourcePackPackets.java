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

import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.exception.InformativeException;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.packet.ResourcePackPackets;
import net.raphimc.viabedrock.protocol.storage.ResourcePackDownloadTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Bedrock client downloads a resource pack one chunk at a time, requesting the next chunk once the previous one
 * arrived. ViaBedrock requested all chunks of a pack at once, and the server sending them all together broke the
 * connection on The Hive ("Invalid encrypted packet") while downloading its packs.
 */
@Mixin(value = ResourcePackPackets.class, remap = false)
public abstract class MixinResourcePackPackets {

    @Unique
    private static final boolean[] FIRST_CHUNK = new boolean[1];

    /**
     * The pack info handler requests every chunk below the chunk count, so only the first one is requested.
     */
    @Redirect(method = "lambda$register$2", at = @At(value = "INVOKE", target = "Lnet/raphimc/viabedrock/protocol/storage/ResourcePackDownloadTracker$Download;receivedChunks()[Z"))
    private static boolean[] requestFirstChunk(final ResourcePackDownloadTracker.Download download) {
        return download.receivedChunks().length == 0 ? download.receivedChunks() : FIRST_CHUNK;
    }

    @Inject(method = "lambda$register$3", at = @At(value = "INVOKE", target = "Lnet/raphimc/viabedrock/protocol/storage/ResourcePackDownloadTracker$Download;processDataChunk(J[B)Lnet/raphimc/viabedrock/api/resourcepack/ResourcePack;", shift = At.Shift.AFTER))
    private static void requestNextChunk(final PacketWrapper wrapper, final CallbackInfo ci, @Local final String key, @Local final long chunk,
                                         @Local final ResourcePackDownloadTracker.Download download) throws InformativeException {
        final long next = chunk + 1;
        if (next < download.receivedChunks().length && !download.receivedChunks()[(int) next]) {
            final PacketWrapper request = wrapper.create(ServerboundBedrockPackets.RESOURCE_PACK_CHUNK_REQUEST);
            request.write(BedrockTypes.STRING, key);
            request.write(BedrockTypes.UNSIGNED_INT_LE, next);
            request.sendToServer(BedrockProtocol.class);
        }
    }

}
