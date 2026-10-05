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

package com.viaversion.viafabricplus.bedrock.protocoltranslator.netty;

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;

/**
 * Records every Bedrock packet sent and received, decrypted and decompressed, to compare the translation against the
 * Bedrock client. Each line of the gzipped file is a JSON object with the time in milliseconds, the direction and the
 * packet with its header in base64.
 */
public final class BedrockPacketRecorder extends ChannelDuplexHandler {

    public static final String NAME = "viafabricplus-bedrock-packet-recorder";

    // Large world and registry data that isn't needed for comparisons
    private static final Set<Integer> SKIPPED_PACKETS = Set.of(
        ClientboundBedrockPackets.LEVEL_CHUNK.getId(),
        ClientboundBedrockPackets.SUB_CHUNK.getId(),
        ClientboundBedrockPackets.RESOURCE_PACK_CHUNK_DATA.getId(),
        ClientboundBedrockPackets.CRAFTING_DATA.getId(),
        ClientboundBedrockPackets.CREATIVE_CONTENT.getId(),
        ClientboundBedrockPackets.BIOME_DEFINITION_LIST.getId(),
        ClientboundBedrockPackets.AVAILABLE_COMMANDS.getId()
    );
    private static final long FLUSH_INTERVAL_MILLIS = 1000;

    private Writer writer;
    private boolean failed;
    private long lastFlush;

    public static boolean isEnabled() {
        return ViaFabricPlusBedrock.impl().settings().recordPackets().isActive();
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof final ByteBuf buf) {
            this.record(ctx, "s2c", buf);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
        if (msg instanceof final ByteBuf buf) {
            this.record(ctx, "c2s", buf);
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) {
        this.close();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        this.close();
        super.channelInactive(ctx);
    }

    private void record(final ChannelHandlerContext ctx, final String direction, final ByteBuf buf) {
        if (this.failed || !buf.isReadable()) {
            return;
        }
        final int packetId = readVarInt(buf, buf.readerIndex()) & 1023;
        if (SKIPPED_PACKETS.contains(packetId)) {
            return;
        }
        try {
            if (this.writer == null) {
                this.open(ctx);
            }
            final long now = System.currentTimeMillis();
            this.writer.write("{\"t\":" + now + ",\"dir\":\"" + direction + "\",\"id\":" + packetId + ",\"data\":\""
                + Base64.getEncoder().encodeToString(ByteBufUtil.getBytes(buf)) + "\"}\n");
            if (now - this.lastFlush > FLUSH_INTERVAL_MILLIS) {
                this.writer.flush();
                this.lastFlush = now;
            }
        } catch (final IOException e) {
            this.failed = true;
            ViaFabricPlusBedrock.impl().logger().error("Failed to record Bedrock packets", e);
            this.close();
        }
    }

    private void open(final ChannelHandlerContext ctx) throws IOException {
        final Path directory = FabricLoader.getInstance().getGameDir().resolve("bedrock-captures");
        Files.createDirectories(directory);
        final Path file = directory.resolve("capture-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".jsonl.gz");
        final OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file)), true);
        this.writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        this.writer.write("{\"t\":" + System.currentTimeMillis() + ",\"dir\":\"info\",\"server\":\"" + String.valueOf(ctx.channel().remoteAddress()).replace("\\", "\\\\").replace("\"", "'")
            + "\",\"protocol\":" + ProtocolConstants.BEDROCK_PROTOCOL_VERSION + ",\"version\":\"" + ProtocolConstants.BEDROCK_VERSION_NAME + "\"}\n");
        ViaFabricPlusBedrock.impl().logger().info("Recording Bedrock packets to {}", file);
    }

    private void close() {
        if (this.writer != null) {
            try {
                this.writer.close();
            } catch (final IOException ignored) {
            }
            this.writer = null;
        }
    }

    private static int readVarInt(final ByteBuf buf, int index) {
        int value = 0;
        for (int shift = 0; shift < 35 && index < buf.writerIndex(); shift += 7) {
            final byte b = buf.getByte(index++);
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
        }
        return value;
    }

}
