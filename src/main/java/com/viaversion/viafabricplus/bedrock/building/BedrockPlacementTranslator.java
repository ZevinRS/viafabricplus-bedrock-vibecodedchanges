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
import com.viaversion.viaversion.api.minecraft.BlockFace;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ServerboundPackets26_3;
import java.util.List;
import java.util.Locale;
import net.raphimc.viabedrock.api.model.container.player.InventoryContainer;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.PlayerActionPacketFactory;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.Direction;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ComplexInventoryTransaction_Type;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ActorSwingSource;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.AnimatePacketPayload_Action;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerID;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.HandSlot;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.InventorySourceFlags;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.InventorySourceType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemUseActionType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemUseClientCooldownState;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemUsePredictedResult;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemUseTriggerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerActionType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerAuthInputData;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.GameMode;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.InteractionHand;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.model.inventory.BedrockInventoryTransaction;
import net.raphimc.viabedrock.protocol.model.inventory.InventoryActionData;
import net.raphimc.viabedrock.protocol.model.inventory.InventorySource;
import net.raphimc.viabedrock.protocol.model.inventory.InventoryTransactionData;
import net.raphimc.viabedrock.protocol.model.inventory.LegacySetItemSlotData;
import net.raphimc.viabedrock.protocol.rewriter.InventoryTransactionRewriter;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.storage.ChunkTracker;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.storage.GameSessionStorage;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

/**
 * Sends block placements like the Bedrock client instead of ViaBedrock's simplified translation, as recorded from the
 * Bedrock client:
 * <ul>
 *     <li>start_item_use_on once when a click places a block, stop_item_use_on with the last placed block on release</li>
 *     <li>a "build" swing before each placement, instead of an "attack" swing marked as a missed swing after it</li>
 *     <li>placements with a legacy request id and the changed hotbar slot, followed by a use on air after clicks</li>
 *     <li>failed placements without request id, item change or swing</li>
 *     <li>simulation_tick as trigger for placements of held use (see {@link BedrockBuilding})</li>
 *     <li>the held item with its predicted count after each placement</li>
 * </ul>
 * Every item use transaction takes a legacy request id, even the ones sent without it.
 */
public final class BedrockPlacementTranslator {

    private BedrockPlacementTranslator() {
    }

    public static void register(final BedrockProtocol protocol) {
        protocol.replaceServerbound(ServerboundPackets26_3.USE_ITEM_ON, BedrockPlacementTranslator::useItemOn);
        protocol.replaceServerbound(ServerboundPackets26_3.USE_ITEM, BedrockPlacementTranslator::useItem);
        protocol.replaceServerbound(ServerboundPackets26_3.PUNCH, BedrockPlacementTranslator::punch);
    }

    public static BedrockPlacementState state(final UserConnection user) {
        BedrockPlacementState state = user.get(BedrockPlacementState.class);
        if (state == null) {
            state = new BedrockPlacementState();
            user.put(state);
        }
        return state;
    }

    private static void useItemOn(final PacketWrapper wrapper) {
        wrapper.cancel();
        final UserConnection user = wrapper.user();
        final ClientPlayerEntity clientPlayer = user.get(EntityTracker.class).getClientPlayer();
        final InventoryContainer inventory = user.get(InventoryTracker.class).getInventoryContainer();
        final ChunkTracker chunkTracker = user.get(ChunkTracker.class);
        final BedrockPlacementState state = state(user);

        final InteractionHand hand = InteractionHand.values()[wrapper.read(Types.VAR_INT)]; // hand
        final BlockPosition position = wrapper.read(Types.BLOCK_POSITION1_14); // block position
        final int faceId = wrapper.read(Types.UNSIGNED_BYTE); // face
        final Direction direction = Direction.getFromVerticalId(faceId);
        final Position3f javaClickPosition = new Position3f(wrapper.read(Types.FLOAT), wrapper.read(Types.FLOAT), wrapper.read(Types.FLOAT)); // click position
        final boolean insideBlock = wrapper.read(Types.BOOLEAN); // inside block
        wrapper.read(Types.BOOLEAN); // world border hit, doesn't exist on Bedrock
        PacketFactory.sendJavaBlockChangedAck(user, wrapper.read(Types.VAR_INT)); // sequence, Bedrock has no acknowledgements
        if (direction == null || hand != InteractionHand.MAIN_HAND) { // Bedrock can only use the main hand
            return;
        }

        final BlockFace face = direction.blockFace();
        final BlockPosition placePosition = insideBlock ? position : position.getRelative(face);
        final BedrockBuilding.Placement placement = BedrockBuilding.pollPlacement(position.x(), position.y(), position.z(), faceId);
        if (placement == null) { // Not a block placement, for example opening a door
            useItemOnLikeViaBedrock(user, clientPlayer, inventory, chunkTracker, position, placePosition, faceId, javaClickPosition);
            return;
        }

        final boolean simulated = placement.simulated();
        final byte slot = inventory.getSelectedHotbarSlot();
        final BedrockItem heldItem = state.heldItem(slot, inventory.getSelectedHotbarItem());
        final int legacyRequestId = state.nextLegacyRequestId();
        final ItemUseTriggerType trigger = simulated ? ItemUseTriggerType.Simulation_Tick : ItemUseTriggerType.Player_Input;
        final Position3f clickPosition = simulated ? placement.simulatedClickPosition() : javaClickPosition;
        final int blockRuntimeId = state.blockState(position, chunkTracker.getBlockState(position));

        if (placement.result() != BedrockBuilding.Placement.Result.SUCCESS) {
            sendTransaction(user, new BedrockInventoryTransaction(0, null, null, ComplexInventoryTransaction_Type.ItemUseTransaction,
                new InventoryTransactionData.UseItemTransactionData(ItemUseActionType.Place, trigger, position, faceId, slot, HandSlot.Mainhand,
                    heldItem, clientPlayer.position(), clickPosition, blockRuntimeId, ItemUsePredictedResult.Failure, ItemUseClientCooldownState.Off)
            ));
            // When the placement passed, the client goes on to use the item, which sends the use on air
            if (!simulated && placement.result() == BedrockBuilding.Placement.Result.FAIL) {
                sendUseOnAir(user, clientPlayer, slot, heldItem);
                state.nextLegacyRequestId();
            }
            return;
        }

        if (!simulated) { // A click starts using the item on blocks
            PlayerActionPacketFactory.sendBedrockPlayerAction(user, clientPlayer.runtimeId(), PlayerActionType.StartItemUseOn, position, placePosition, faceId);
            state.setUsingItemOn(true);
        }

        sendSwing(user, clientPlayer, ActorSwingSource.Build);
        state.expectPlacementSwing();

        BedrockItem predictedItem = heldItem.copy();
        if (predictedItem.blockRuntimeId() != 0 && clientPlayer.javaGameMode() != GameMode.CREATIVE) {
            predictedItem.setAmount(predictedItem.amount() - 1);
        }
        if (predictedItem.amount() <= 0) {
            predictedItem = BedrockItem.empty();
        }

        sendTransaction(user, new BedrockInventoryTransaction(
            legacyRequestId,
            List.of(new LegacySetItemSlotData(ContainerEnumName.InventoryContainer, new byte[]{slot})),
            List.of(new InventoryActionData(new InventorySource(InventorySourceType.Container_Inventory, ContainerID.CONTAINER_ID_INVENTORY.getValue(), InventorySourceFlags.No_Flag), slot, heldItem, predictedItem)),
            ComplexInventoryTransaction_Type.ItemUseTransaction,
            new InventoryTransactionData.UseItemTransactionData(ItemUseActionType.Place, trigger, position, faceId, slot, HandSlot.Mainhand,
                heldItem, clientPlayer.position(), clickPosition, blockRuntimeId, ItemUsePredictedResult.Success, ItemUseClientCooldownState.Off)
        ));

        if (!simulated) { // A click is followed by using the item on air
            sendUseOnAir(user, clientPlayer, slot, predictedItem);
            state.nextLegacyRequestId();
        }

        if (state.equip(slot, predictedItem)) {
            final PacketWrapper mobEquipment = PacketWrapper.create(ServerboundBedrockPackets.MOB_EQUIPMENT, user);
            mobEquipment.write(BedrockTypes.UNSIGNED_VAR_LONG, clientPlayer.runtimeId()); // entity runtime id
            mobEquipment.write(user.get(ItemRewriter.class).newItemType(), predictedItem); // item
            mobEquipment.write(Types.BYTE, slot); // slot
            mobEquipment.write(Types.BYTE, slot); // selected slot
            mobEquipment.write(Types.BYTE, (byte) ContainerID.CONTAINER_ID_INVENTORY.getValue()); // container id
            mobEquipment.sendToServer(BedrockProtocol.class);
        }
        state.setLastPlacedPosition(placePosition);
        if (heldItem.blockRuntimeId() != 0) {
            state.predictBlock(placePosition, heldItem.blockRuntimeId());
        }
    }

    /**
     * ViaBedrock's translation, for using items on blocks without placing a block.
     */
    private static void useItemOnLikeViaBedrock(final UserConnection user, final ClientPlayerEntity clientPlayer, final InventoryContainer inventory, final ChunkTracker chunkTracker,
                                                final BlockPosition position, final BlockPosition placePosition, final int faceId, final Position3f clickPosition) {
        PlayerActionPacketFactory.sendBedrockPlayerAction(user, clientPlayer.runtimeId(), PlayerActionType.StartItemUseOn, position, placePosition, faceId);

        BedrockItem predictedItem = inventory.getSelectedHotbarItem().copy();
        if (predictedItem.blockRuntimeId() != 0 && clientPlayer.javaGameMode() != GameMode.CREATIVE) {
            predictedItem.setAmount(predictedItem.amount() - 1);
        }
        if (predictedItem.amount() <= 0) {
            predictedItem = BedrockItem.empty();
        }
        sendTransaction(user, new BedrockInventoryTransaction(
            0,
            null,
            List.of(new InventoryActionData(new InventorySource(InventorySourceType.Container_Inventory, ContainerID.CONTAINER_ID_INVENTORY.getValue(), InventorySourceFlags.No_Flag),
                inventory.getSelectedHotbarSlot(), inventory.getSelectedHotbarItem(), predictedItem)),
            ComplexInventoryTransaction_Type.ItemUseTransaction,
            new InventoryTransactionData.UseItemTransactionData(ItemUseActionType.Place, ItemUseTriggerType.Player_Input, position, faceId, inventory.getSelectedHotbarSlot(),
                HandSlot.Mainhand, inventory.getSelectedHotbarItem(), clientPlayer.position(), clickPosition, chunkTracker.getBlockState(position),
                ItemUsePredictedResult.Success, ItemUseClientCooldownState.Off)
        ));

        PlayerActionPacketFactory.sendBedrockPlayerAction(user, clientPlayer.runtimeId(), PlayerActionType.StopItemUseOn, position, new BlockPosition(0, 0, 0), 0);
    }

    /**
     * ViaBedrock's translation of using an item, which also takes a legacy request id like on Bedrock.
     */
    private static void useItem(final PacketWrapper wrapper) {
        wrapper.cancel();
        final int hand = wrapper.read(Types.VAR_INT); // hand
        wrapper.read(Types.VAR_INT); // sequence
        wrapper.read(Types.FLOAT); // yaw
        wrapper.read(Types.FLOAT); // pitch
        if (hand != InteractionHand.MAIN_HAND.ordinal()) { // Bedrock can't use items in the offhand
            return;
        }

        final UserConnection user = wrapper.user();
        final InventoryContainer inventory = user.get(InventoryTracker.class).getInventoryContainer();
        final BedrockPlacementState state = state(user);
        final byte slot = inventory.getSelectedHotbarSlot();
        sendUseOnAir(user, user.get(EntityTracker.class).getClientPlayer(), slot, state.heldItem(slot, inventory.getSelectedHotbarItem()));
        state.nextLegacyRequestId();
    }

    /**
     * Ends using the item on blocks when the use button is released.
     */
    public static void stopUsingItemOn(final UserConnection user) {
        user.getChannel().eventLoop().execute(() -> {
            final BedrockPlacementState state = user.get(BedrockPlacementState.class);
            if (state == null || !state.usingItemOn()) {
                return;
            }
            state.setUsingItemOn(false);
            try {
                final ClientPlayerEntity clientPlayer = user.get(EntityTracker.class).getClientPlayer();
                PlayerActionPacketFactory.sendBedrockPlayerAction(user, clientPlayer.runtimeId(), PlayerActionType.StopItemUseOn, state.lastPlacedPosition(), new BlockPosition(0, 0, 0), 0);
            } catch (final Exception e) {
                ViaFabricPlusBedrock.impl().logger().error("Failed to send stop_item_use_on", e);
            }
        });
    }

    /**
     * ViaBedrock's swing translation, except that swings of placements are already sent before the placement.
     */
    private static void punch(final PacketWrapper wrapper) {
        final ClientPlayerEntity clientPlayer = wrapper.user().get(EntityTracker.class).getClientPlayer();
        final boolean placementSwing = state(wrapper.user()).consumePlacementSwing();
        if (clientPlayer.checkCancelSwingPacket() || placementSwing) {
            wrapper.cancel();
            return;
        }

        wrapper.setPacketType(ServerboundBedrockPackets.ANIMATE);
        wrapper.write(Types.UNSIGNED_BYTE, (short) AnimatePacketPayload_Action.Swing.getValue()); // action
        wrapper.write(BedrockTypes.UNSIGNED_VAR_LONG, clientPlayer.runtimeId()); // entity runtime id
        wrapper.write(BedrockTypes.FLOAT_LE, 0F); // data
        wrapper.write(BedrockTypes.OPTIONAL_STRING, ActorSwingSource.Attack.name().toLowerCase(Locale.ROOT)); // swing source

        if (clientPlayer.blockBreakingInfo() != null) {
            if (!wrapper.user().get(GameSessionStorage.class).isBlockBreakingServerAuthoritative()) {
                final ClientPlayerEntity.BlockBreakingInfo blockBreakingInfo = clientPlayer.blockBreakingInfo();
                clientPlayer.addAuthInputBlockAction(new ClientPlayerEntity.AuthInputBlockAction(PlayerActionType.CrackBlock, blockBreakingInfo.position(), blockBreakingInfo.direction().ordinal()));
            }
        } else {
            clientPlayer.addAuthInputData(PlayerAuthInputData.MissedSwing);
        }
    }

    private static void sendSwing(final UserConnection user, final ClientPlayerEntity clientPlayer, final ActorSwingSource source) {
        final PacketWrapper animate = PacketWrapper.create(ServerboundBedrockPackets.ANIMATE, user);
        animate.write(Types.UNSIGNED_BYTE, (short) AnimatePacketPayload_Action.Swing.getValue()); // action
        animate.write(BedrockTypes.UNSIGNED_VAR_LONG, clientPlayer.runtimeId()); // entity runtime id
        animate.write(BedrockTypes.FLOAT_LE, 0F); // data
        animate.write(BedrockTypes.OPTIONAL_STRING, source.name().toLowerCase(Locale.ROOT)); // swing source
        animate.sendToServer(BedrockProtocol.class);
    }

    private static void sendUseOnAir(final UserConnection user, final ClientPlayerEntity clientPlayer, final byte slot, final BedrockItem item) {
        sendTransaction(user, new BedrockInventoryTransaction(0, null, null, ComplexInventoryTransaction_Type.ItemUseTransaction,
            new InventoryTransactionData.UseItemTransactionData(ItemUseActionType.Use, ItemUseTriggerType.Unknown, new BlockPosition(0, 0, 0), 255, slot, HandSlot.Mainhand,
                item, clientPlayer.position(), Position3f.ZERO, 0, ItemUsePredictedResult.Failure, ItemUseClientCooldownState.Off)
        ));
    }

    private static void sendTransaction(final UserConnection user, final BedrockInventoryTransaction transaction) {
        final PacketWrapper packet = PacketWrapper.create(ServerboundBedrockPackets.INVENTORY_TRANSACTION, user);
        packet.write(user.get(InventoryTransactionRewriter.class).getInventoryTransactionType(), transaction);
        packet.sendToServer(BedrockProtocol.class);
    }

}
