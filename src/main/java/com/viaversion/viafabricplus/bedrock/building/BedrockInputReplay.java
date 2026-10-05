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

package com.viaversion.viafabricplus.bedrock.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;

/**
 * Debug tool for comparing movement with the Bedrock client: plays back the per tick input of a recorded Bedrock run
 * (keys and rotation), starting from the same position. Enabled with -Dviafabricplus.bedrock.inputReplay=path/to/replay.json,
 * started with F7. The file holds the start position and one frame per tick: yaw, pitch and the held keys as letters
 * (F forward, B backward, L left, R right, J jump, S sneak, P sprint). Servers without a tp command can teleport
 * through chat with -Dviafabricplus.bedrock.inputReplayTeleport=chat, which sends "!tp x y z".
 */
public final class BedrockInputReplay {

    private static final String FILE = System.getProperty("viafabricplus.bedrock.inputReplay");
    private static final boolean TELEPORT_THROUGH_CHAT = "chat".equals(System.getProperty("viafabricplus.bedrock.inputReplayTeleport"));
    private static final int TELEPORT_WAIT_TICKS = 40;

    private static State state = State.IDLE;
    private static boolean keyWasDown;
    private static int waitTicks;
    private static int frameIndex;
    private static double[] start;
    private static final List<Frame> FRAMES = new ArrayList<>();

    private BedrockInputReplay() {
    }

    public static boolean isPlaying() {
        return state == State.PLAYING;
    }

    public static int frameIndex() {
        return frameIndex;
    }

    /**
     * Called at the end of every client tick.
     */
    public static void tick(final Minecraft minecraft) {
        if (FILE == null || minecraft.player == null) {
            return;
        }
        final boolean keyDown = InputConstants.isKeyDown(InputConstants.KEY_F7);
        if (keyDown && !keyWasDown && minecraft.gui.screen() == null) {
            if (state == State.IDLE || state == State.DONE) {
                begin(minecraft.player);
            } else {
                ViaFabricPlusBedrock.impl().logger().info("[replay] stopped");
                state = State.IDLE;
            }
        }
        keyWasDown = keyDown;

        if (state == State.TELEPORTING && --waitTicks <= 0) {
            frameIndex = 0;
            state = State.PLAYING;
            ViaFabricPlusBedrock.impl().logger().info("[replay] playing {} frames, first tick {}", FRAMES.size(), lastSentTick() + 1);
        }
    }

    private static void begin(final LocalPlayer player) {
        try {
            final JsonObject json = JsonParser.parseString(Files.readString(Path.of(FILE))).getAsJsonObject();
            final JsonObject startJson = json.getAsJsonObject("start");
            start = new double[]{startJson.get("x").getAsDouble(), startJson.get("y").getAsDouble(), startJson.get("z").getAsDouble(),
                startJson.get("yaw").getAsDouble(), startJson.get("pitch").getAsDouble()};
            FRAMES.clear();
            for (final JsonElement element : json.getAsJsonArray("frames")) {
                final JsonArray frame = element.getAsJsonArray();
                final String keys = frame.get(2).getAsString();
                FRAMES.add(new Frame(frame.get(0).getAsFloat(), frame.get(1).getAsFloat(), new Input(keys.indexOf('F') >= 0, keys.indexOf('B') >= 0,
                    keys.indexOf('L') >= 0, keys.indexOf('R') >= 0, keys.indexOf('J') >= 0, keys.indexOf('S') >= 0, keys.indexOf('P') >= 0)));
            }
        } catch (final Exception e) {
            ViaFabricPlusBedrock.impl().logger().error("[replay] failed to read {}", FILE, e);
            return;
        }
        if (TELEPORT_THROUGH_CHAT) {
            player.connection.sendChat(String.format(Locale.ROOT, "!tp %.5f %.5f %.5f", start[0], start[1], start[2]));
        } else {
            player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.5f %.5f %.5f %.3f %.3f", start[0], start[1], start[2], start[3], start[4]));
        }
        waitTicks = TELEPORT_WAIT_TICKS;
        state = State.TELEPORTING;
        ViaFabricPlusBedrock.impl().logger().info("[replay] teleporting to the start of {} frames", FRAMES.size());
    }

    /**
     * Called after the client read its keys for the tick, before it moves.
     *
     * @return the move vector to use, or null when not playing
     */
    public static Vec2 applyFrame(final LocalPlayer player, final net.minecraft.client.player.ClientInput input) {
        if (state != State.PLAYING) {
            return null;
        }
        if (frameIndex >= FRAMES.size()) {
            state = State.DONE;
            ViaFabricPlusBedrock.impl().logger().info("[replay] done, last tick {}", lastSentTick());
            return null;
        }
        final Frame frame = FRAMES.get(frameIndex++);
        player.setYRot(frame.yaw);
        player.setXRot(frame.pitch);
        player.setYHeadRot(frame.yaw);
        input.keyPresses = frame.keys;
        return new Vec2(impulse(frame.keys.left(), frame.keys.right()), impulse(frame.keys.forward(), frame.keys.backward())).normalized();
    }

    private static float impulse(final boolean positive, final boolean negative) {
        return positive == negative ? 0F : positive ? 1F : -1F;
    }

    private static long lastSentTick() {
        final UserConnection user = ViaFabricPlus.api().userConnection();
        return user != null ? user.get(EntityTracker.class).getClientPlayer().age() : -1;
    }

    private enum State {
        IDLE, TELEPORTING, PLAYING, DONE
    }

    private record Frame(float yaw, float pitch, Input keys) {
    }

}
