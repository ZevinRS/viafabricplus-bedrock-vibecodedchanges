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

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.model.container.player.InventoryContainer;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ActorEvent;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerAuthInputData;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.jetbrains.annotations.Nullable;

/**
 * The parts of using items that only the client knows, recorded for {@link BedrockPlacementTranslator}:
 * <ul>
 *     <li>Buckets: Bedrock uses a bucket on the liquid or block it looks at, while Java targets the block behind a
 *     liquid. The target is found like Java's bucket does before the packet is sent.</li>
 *     <li>Using items over time (eating, drinking, bows, ...): Bedrock marks the tick the use starts, and while eating
 *     or drinking sends the eating event every 4 ticks, starting 8 ticks after the use started. When the item's use
 *     duration is reached, Bedrock uses the item again, which makes the server consume it.</li>
 * </ul>
 */
public final class BedrockItemUse {

    private static final Queue<BucketUse> BUCKET_USES = new ConcurrentLinkedQueue<>();
    private static final long BUCKET_USE_TIMEOUT_NANOS = 1_000_000_000L;
    private static final int FIRST_EATING_EVENT_TICK = 8;
    private static final int EATING_EVENT_INTERVAL = 4;

    // Ticks since using an item started, counted on the render thread
    private static int ticksUsing = -1;
    private static boolean tracking;
    // Whether the client repeats using the item because use is held, which Bedrock sends as simulation_tick
    private static boolean repeating;

    private BedrockItemUse() {
    }

    public static void setRepeating(final boolean repeating) {
        BedrockItemUse.repeating = repeating;
    }

    /**
     * Called before the client sends a packet using its main hand item.
     *
     * @param onBlock     whether the packet is the one using the item on a block (the client sends using the item after it)
     * @param interaction whether using the item on the block interacted with it instead, like filling a cauldron
     */
    public static void recordBucketUse(final LocalPlayer player, final boolean onBlock, final boolean interaction) {
        if (interaction) {
            BUCKET_USES.add(new BucketUse(onBlock, true, repeating, null, 0, Position3f.ZERO, null, System.nanoTime()));
            return;
        }
        final ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof final BucketItem bucket)) {
            return;
        }
        final boolean empty = bucket.getContent() == Fluids.EMPTY;
        final Vec3 eye = player.getEyePosition();
        final Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        final BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, empty ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            BUCKET_USES.add(new BucketUse(onBlock, false, repeating, null, 0, Position3f.ZERO, null, System.nanoTime()));
            return;
        }

        final BlockPos pos = hit.getBlockPos();
        String predictedItem = null;
        if (empty) {
            final FluidState fluid = player.level().getFluidState(pos);
            if (fluid.isSource() && fluid.is(FluidTags.WATER)) {
                predictedItem = "minecraft:water_bucket";
            } else if (fluid.isSource() && fluid.is(FluidTags.LAVA)) {
                predictedItem = "minecraft:lava_bucket";
            }
        } else {
            predictedItem = "minecraft:bucket";
        }
        final Vec3 location = hit.getLocation();
        final Position3f clickPosition = new Position3f((float) (location.x - pos.getX()), (float) (location.y - pos.getY()), (float) (location.z - pos.getZ()));
        BUCKET_USES.add(new BucketUse(onBlock, false, repeating, new BlockPosition(pos.getX(), pos.getY(), pos.getZ()), hit.getDirection().get3DDataValue(), clickPosition, predictedItem, System.nanoTime()));
    }

    /**
     * @return the oldest recorded bucket use of this kind, or null if the packet isn't from using a bucket
     */
    public static @Nullable BucketUse pollBucketUse(final boolean onBlock) {
        final long now = System.nanoTime();
        final Iterator<BucketUse> iterator = BUCKET_USES.iterator();
        while (iterator.hasNext()) {
            final BucketUse use = iterator.next();
            if (now - use.time() > BUCKET_USE_TIMEOUT_NANOS) {
                iterator.remove();
            } else if (use.onBlock() == onBlock) {
                iterator.remove();
                return use;
            }
        }
        return null;
    }

    /**
     * Called after the client used its main hand item, while it is still handling the use button.
     */
    public static void onItemUsed(final LocalPlayer player) {
        if (!player.isUsingItem() || !BedrockBuilding.isActive()) {
            return;
        }
        // Counted up at the end of this tick, so the tick using started is tick 0
        ticksUsing = -1;
        tracking = true;
        runOnConnection(user -> user.get(EntityTracker.class).getClientPlayer().addAuthInputData(PlayerAuthInputData.StartUsingItem));
    }

    /**
     * Called at the end of every client tick.
     */
    public static void tick(final Minecraft minecraft) {
        final LocalPlayer player = minecraft.player;
        if (!tracking || player == null || !BedrockBuilding.isActive()) {
            return;
        }
        if (!player.isUsingItem()) {
            tracking = false;
            return;
        }
        ticksUsing++;
        final ItemStack useItem = player.getUseItem();
        final ItemUseAnimation animation = useItem.getUseAnimation();
        if (animation != ItemUseAnimation.EAT && animation != ItemUseAnimation.DRINK) {
            return;
        }
        if (ticksUsing >= FIRST_EATING_EVENT_TICK && ticksUsing % EATING_EVENT_INTERVAL == 0) {
            runOnConnection(BedrockItemUse::sendEatingEvent);
        }
        if (ticksUsing == useItem.getUseDuration(player)) {
            tracking = false;
            runOnConnection(BedrockPlacementTranslator::finishUsingItem);
        }
    }

    private static void sendEatingEvent(final UserConnection user) {
        final ClientPlayerEntity clientPlayer = user.get(EntityTracker.class).getClientPlayer();
        final InventoryContainer inventory = user.get(InventoryTracker.class).getInventoryContainer();
        final BedrockItem item = inventory.getSelectedHotbarItem();
        if (item.isEmpty()) {
            return;
        }
        final PacketWrapper entityEvent = PacketWrapper.create(ServerboundBedrockPackets.ENTITY_EVENT, user);
        entityEvent.write(BedrockTypes.UNSIGNED_VAR_LONG, clientPlayer.runtimeId()); // entity runtime id
        entityEvent.write(Types.UNSIGNED_BYTE, (short) ActorEvent.FEED.getValue()); // event
        entityEvent.write(BedrockTypes.VAR_INT, item.identifier() << 16 | (item.data() & 0xFFFF)); // data
        entityEvent.write(BedrockTypes.OPTIONAL_POSITION_3F, null); // fire at position
        entityEvent.sendToServer(BedrockProtocol.class);
    }

    /**
     * Runs on the connection's event loop, which handles the client's packets in the order they were sent.
     */
    private static void runOnConnection(final Consumer<UserConnection> action) {
        final UserConnection user = ViaFabricPlus.api().userConnection();
        if (user == null || user.getChannel() == null) {
            return;
        }
        user.getChannel().eventLoop().execute(() -> {
            try {
                action.accept(user);
            } catch (final Exception e) {
                ViaFabricPlusBedrock.impl().logger().error("Failed to send item use", e);
            }
        });
    }

    /**
     * @param onBlock       whether the client used the item on a block or in the air
     * @param interaction   whether using the bucket on the block interacted with it, which is translated like other block interactions
     * @param repeated      whether the client repeated the use because use is held
     * @param target        the liquid or block Bedrock uses the bucket on, or null if there is none in reach
     * @param predictedItem the item the bucket turns into, or null if the use fails
     */
    public record BucketUse(boolean onBlock, boolean interaction, boolean repeated, @Nullable BlockPosition target, int face, Position3f clickPosition, @Nullable String predictedItem, long time) {
    }

}
