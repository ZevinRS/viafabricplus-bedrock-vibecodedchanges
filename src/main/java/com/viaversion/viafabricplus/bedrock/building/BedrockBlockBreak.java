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

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viafabricplus.bedrock.inventory.BedrockInventoryTranslator;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.raphimc.viabedrock.api.model.container.player.InventoryContainer;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ComplexInventoryTransaction_Type;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerID;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.HandSlot;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.InventorySourceFlags;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.InventorySourceType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemUseActionType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemUseClientCooldownState;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemUsePredictedResult;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemUseTriggerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerAuthInputData;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.model.inventory.BedrockInventoryTransaction;
import net.raphimc.viabedrock.protocol.model.inventory.InventoryActionData;
import net.raphimc.viabedrock.protocol.model.inventory.InventorySource;
import net.raphimc.viabedrock.protocol.model.inventory.InventoryTransactionData;
import net.raphimc.viabedrock.protocol.model.inventory.LegacySetItemSlotData;
import net.raphimc.viabedrock.protocol.rewriter.InventoryTransactionRewriter;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import net.raphimc.viabedrock.protocol.types.InventoryTypes;
import org.jetbrains.annotations.Nullable;

/**
 * With client authoritative block breaking, like on The Hive, the Bedrock client reports every broken block with a
 * break_block item use in the input of the tick it broke in, as recorded there. It lists how the held tool wore down,
 * with the next legacy request id. Without a change to the tool it still takes a request id, but sends 0 and no
 * actions. ViaBedrock only sent the block actions, so the server never broke the block.
 */
public final class BedrockBlockBreak {

    // Set on the render thread when Java breaks a block, taken on the network thread with the block's packet
    private static volatile int toolDamage;

    private BedrockBlockBreak() {
    }

    /**
     * Called when the client breaks a block: tools wear down like on Java, by their damage per block for blocks that
     * aren't broken instantly.
     */
    public static void onDestroyBlock(final LocalPlayer player, final Level level, final BlockPos pos) {
        final ItemStack stack = player.getMainHandItem();
        final Tool tool = stack.get(DataComponents.TOOL);
        toolDamage = tool != null && stack.isDamageableItem() && !player.getAbilities().instabuild && level.getBlockState(pos).getDestroySpeed(level, pos) != 0F
            ? tool.damagePerBlock() : 0;
    }

    /**
     * Called when the broken block is sent.
     */
    public static void onBlockBroken(final UserConnection user, final BlockPosition position, final int face) {
        final int damage = toolDamage;
        toolDamage = 0;
        BedrockPlacementTranslator.state(user).setBrokenBlock(new BedrockPlacementState.BrokenBlock(position, face, damage));
    }

    /**
     * Called before the input of a tick is written. Wears the held tool down, which sends it again like the Bedrock
     * client does before the input.
     *
     * @return the item use of the block broken in the tick
     */
    public static @Nullable BedrockInventoryTransaction prepareInput(final UserConnection user) {
        final BedrockPlacementState state = BedrockPlacementTranslator.state(user);
        final BedrockPlacementState.BrokenBlock broken = state.takeBrokenBlock();
        if (broken == null) {
            return null;
        }
        final ClientPlayerEntity clientPlayer = user.get(EntityTracker.class).getClientPlayer();
        final InventoryContainer inventory = user.get(InventoryTracker.class).getInventoryContainer();
        final byte slot = inventory.getSelectedHotbarSlot();
        final BedrockItem heldItem = inventory.getSelectedHotbarItem().copy();

        int legacyRequestId = state.nextLegacyRequestId();
        List<LegacySetItemSlotData> legacySlots = null;
        List<InventoryActionData> actions = List.of();
        if (broken.toolDamage() > 0 && !heldItem.isEmpty()) {
            final BedrockItem wornItem = heldItem.copy();
            final CompoundTag tag = heldItem.tag() != null ? heldItem.tag().copy() : new CompoundTag();
            tag.putInt("Damage", tag.getInt("Damage") + broken.toolDamage());
            wornItem.setTag(tag);
            legacySlots = List.of(new LegacySetItemSlotData(ContainerEnumName.InventoryContainer, new byte[]{slot}));
            actions = List.of(new InventoryActionData(new InventorySource(InventorySourceType.Container_Inventory, ContainerID.CONTAINER_ID_INVENTORY.getValue(), InventorySourceFlags.No_Flag),
                slot, heldItem, wornItem));
            BedrockInventoryTranslator.setItem(user, inventory, slot, wornItem);
        } else {
            legacyRequestId = 0;
        }

        clientPlayer.addAuthInputData(PlayerAuthInputData.PerformItemInteraction);
        return new BedrockInventoryTransaction(legacyRequestId, legacySlots, actions, ComplexInventoryTransaction_Type.ItemUseTransaction,
            new InventoryTransactionData.UseItemTransactionData(ItemUseActionType.Destroy, ItemUseTriggerType.Unknown, broken.position(), broken.face(), slot, HandSlot.Mainhand,
                heldItem, clientPlayer.position(), Position3f.ZERO, 0, ItemUsePredictedResult.Failure, ItemUseClientCooldownState.Off));
    }

    /**
     * Writes the item use in place of the empty optional of the input, which is the inventory transaction without its
     * type.
     */
    public static void writeInput(final PacketWrapper wrapper, final BedrockInventoryTransaction transaction) {
        final InventoryTransactionData.UseItemTransactionData data = (InventoryTransactionData.UseItemTransactionData) transaction.transactionData();
        wrapper.write(Types.BOOLEAN, true); // has transaction
        wrapper.write(BedrockTypes.VAR_INT, transaction.legacyRequestId()); // legacy request id
        wrapper.write(Types.BOOLEAN, transaction.legacyRequestId() != 0); // has legacy slots
        if (transaction.legacyRequestId() != 0) {
            wrapper.write(InventoryTypes.LEGACY_SET_ITEM_SLOT_DATA, transaction.legacySlots().toArray(new LegacySetItemSlotData[0])); // legacy slots
        }
        wrapper.write(wrapper.user().get(InventoryTransactionRewriter.class).getInventoryActionDataType(), transaction.actions().toArray(new InventoryActionData[0])); // actions
        wrapper.write(BedrockTypes.VAR_INT, data.actionType().getValue()); // action type
        wrapper.write(Types.BYTE, (byte) data.triggerType().getValue()); // trigger type
        wrapper.write(BedrockTypes.BLOCK_POSITION, data.blockPosition()); // block position
        wrapper.write(Types.BYTE, (byte) data.face()); // face
        wrapper.write(BedrockTypes.VAR_INT, data.hotbarSlot()); // hotbar slot
        wrapper.write(Types.BYTE, (byte) data.handSlot().getValue()); // hand
        wrapper.write(wrapper.user().get(ItemRewriter.class).newItemType(), data.itemInHand()); // held item
        wrapper.write(BedrockTypes.POSITION_3F, data.playerPosition()); // player position
        wrapper.write(BedrockTypes.POSITION_3F, data.clickPosition()); // click position
        wrapper.write(BedrockTypes.UNSIGNED_VAR_INT, data.blockRuntimeId()); // block runtime id
        wrapper.write(Types.BYTE, (byte) data.predictedResult().getValue()); // client prediction
        wrapper.write(Types.BYTE, (byte) data.clientCooldownState().getValue()); // client cooldown state
    }

}
