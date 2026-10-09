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

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerActionType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ServerboundLoadingScreenPacketType;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.Nullable;

/**
 * The loading screen of a dimension change, as the Bedrock client goes through it, recorded on Dragonfly with a change
 * to the Nether and back like The Hive does when joining a game:
 * <ul>
 * <li>it starts the loading screen when it handles the change, once for changes following each other quickly,</li>
 * <li>ends it when the area around the player loaded, 50 to 160 ms later,</li>
 * <li>acknowledges the change about 7 ticks after that (320 to 370 ms),</li>
 * <li>doesn't move the player in between, ignores the movement keys and keeps the sprinting, still sending it while
 * the sprint key is ignored,</li>
 * <li>and ignores keys still held from before until they are pressed again.</li>
 * </ul>
 * ViaBedrock started and ended the loading screen and acknowledged the change as soon as the packets arrived, and the
 * player kept moving. Only used on the render thread, except for the calls marked as on the network thread.
 */
public final class BedrockDimensionChange {

    // The Bedrock client starts the loading screen 110 to 160 ms after the change arrived
    private static final int START_DELAY_TICKS = 2;
    // and ends it at the earliest 60 ms later
    private static final int MIN_LOADING_TICKS = 2;
    private static final int ACK_DELAY_TICKS = 7;
    // Gives up waiting for the server's acknowledgement or the area to load
    private static final int TIMEOUT_TICKS = 200;

    // Counted on the network thread, since packets and tasks reach the render thread in separate queues
    private static final AtomicInteger CHANGES = new AtomicInteger();
    private static final AtomicInteger SERVER_ACKS = new AtomicInteger();
    private static volatile @Nullable UserConnection user;
    private static volatile @Nullable Long loadingScreenId;
    private static int handledChanges;
    // From handling the change until acknowledging it
    private static boolean loading;
    private static boolean started;
    private static boolean ended;
    private static int ticks;
    private static int startTick;
    private static int ackTick;
    // Movement keys held while loading, ignored until released
    private static Input heldKeys = Input.EMPTY;
    // Read on the network thread when building the input packet
    private static volatile boolean sendsSprinting;

    private BedrockDimensionChange() {
    }

    public static boolean isLoading() {
        return loading;
    }

    /**
     * @return whether the input packet says the player sprints although the sprint key is ignored
     */
    public static boolean sendsSprinting() {
        return sendsSprinting;
    }

    /**
     * Called on the network thread when ViaBedrock got a dimension change, instead of starting the loading screen.
     */
    public static void onChangeDimension(final UserConnection connection, final @Nullable Long screenId) {
        user = connection;
        loadingScreenId = screenId;
        CHANGES.incrementAndGet();
    }

    /**
     * Called on the network thread when the server acknowledged a change, instead of ViaBedrock answering right away.
     */
    public static void onServerAcknowledged() {
        SERVER_ACKS.incrementAndGet();
    }

    /**
     * Called when the client handled the respawn ViaBedrock turned a dimension change into.
     */
    public static void onRespawnHandled() {
        if (handledChanges >= CHANGES.get()) {
            return;
        }
        handledChanges++;
        final Minecraft minecraft = Minecraft.getInstance();
        sendsSprinting = minecraft.player != null && minecraft.player.isSprinting();
        if (!loading) {
            loading = true;
            started = false;
            ended = false;
            startTick = ticks + START_DELAY_TICKS;
        } else if (ended) {
            // Another change after the loading screen ended waits for its own loading
            ended = false;
        }
    }

    public static void tick(final Minecraft minecraft) {
        ticks++;
        if (minecraft.player == null || minecraft.getConnection() == null) {
            reset();
            return;
        }
        sendsSprinting = loading && minecraft.player.isSprinting();
        if (!loading) {
            return;
        }
        if (!started) {
            if (ticks < startTick) {
                return;
            }
            started = true;
            send(() -> PacketFactory.sendBedrockLoadingScreen(user, ServerboundLoadingScreenPacketType.StartLoadingScreen, loadingScreenId));
        }
        final int changes = CHANGES.get();
        final boolean acknowledged = handledChanges >= changes && SERVER_ACKS.get() >= changes;
        final boolean timedOut = ticks - startTick > TIMEOUT_TICKS;
        if (!ended && (acknowledged && minecraft.getConnection().hasClientLoaded() && ticks >= startTick + MIN_LOADING_TICKS || timedOut)) {
            ended = true;
            ackTick = ticks + ACK_DELAY_TICKS;
            send(() -> PacketFactory.sendBedrockLoadingScreen(user, ServerboundLoadingScreenPacketType.EndLoadingScreen, loadingScreenId));
        }
        if (ended && ticks >= ackTick) {
            loading = false;
            send(() -> user.get(EntityTracker.class).getClientPlayer().sendPlayerActionPacketToServer(PlayerActionType.ChangeDimensionAck));
        }
    }

    /**
     * Called after the keys were read for the tick: while loading the keys do nothing, and keys held while loading
     * only count again once they were released.
     *
     * @return the move vector of the keys that count, or null when all of them count
     */
    public static @Nullable Vec2 filterInput(final ClientInput input) {
        final Input keys = input.keyPresses;
        if (loading) {
            heldKeys = or(heldKeys, keys);
        } else {
            // Toggled sprinting and sneaking stay on without the key being held, so they're never released
            final Options options = Minecraft.getInstance().options;
            heldKeys = new Input(heldKeys.forward() && keys.forward(), heldKeys.backward() && keys.backward(), heldKeys.left() && keys.left(),
                heldKeys.right() && keys.right(), heldKeys.jump() && keys.jump(), heldKeys.shift() && keys.shift() && !options.toggleCrouch().get(),
                heldKeys.sprint() && keys.sprint() && !options.toggleSprint().get());
        }
        if (heldKeys.equals(Input.EMPTY)) {
            return null;
        }
        input.keyPresses = new Input(keys.forward() && !heldKeys.forward(), keys.backward() && !heldKeys.backward(), keys.left() && !heldKeys.left(),
            keys.right() && !heldKeys.right(), keys.jump() && !heldKeys.jump(), keys.shift() && !heldKeys.shift(), keys.sprint() && !heldKeys.sprint());
        final float forward = impulse(input.keyPresses.forward(), input.keyPresses.backward());
        final float left = impulse(input.keyPresses.left(), input.keyPresses.right());
        return new Vec2(left, forward).normalized();
    }

    private static Input or(final Input a, final Input b) {
        return new Input(a.forward() || b.forward(), a.backward() || b.backward(), a.left() || b.left(), a.right() || b.right(),
            a.jump() || b.jump(), a.shift() || b.shift(), a.sprint() || b.sprint());
    }

    private static float impulse(final boolean positive, final boolean negative) {
        return positive == negative ? 0F : positive ? 1F : -1F;
    }

    private static void send(final Runnable action) {
        if (user == null) {
            return;
        }
        try {
            action.run();
        } catch (final Exception e) {
            ViaFabricPlusBedrock.impl().logger().error("Failed to send the dimension change progress", e);
        }
    }

    private static void reset() {
        CHANGES.set(0);
        SERVER_ACKS.set(0);
        handledChanges = 0;
        user = null;
        loadingScreenId = null;
        loading = false;
        sendsSprinting = false;
        heldKeys = Input.EMPTY;
    }

}
