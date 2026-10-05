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

package com.viaversion.viafabricplus.bedrock.inventory;

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.HashedItem;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ServerboundPackets26_3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.api.model.container.player.InventoryContainer;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerID;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.InteractPacketPayload_Action;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.ContainerInput;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

/**
 * Moves items in the inventory and chests like the Bedrock client, which ViaBedrock doesn't translate: every click is
 * sent as item stack requests that take, place, swap or drop items, as recorded from the Bedrock client. The moves are
 * found from the slots the client predicts to change, so they follow Java's click rules:
 * <ul>
 *     <li>take: items move into the empty cursor</li>
 *     <li>place: items move from the cursor or a slot (shift clicking) into a slot, or into a cursor with items</li>
 *     <li>swap: two stacks trade places</li>
 *     <li>drop: items leave the inventory</li>
 * </ul>
 * Each move is its own request. Until the server answers it, the changed stacks are referred to by the request id.
 */
public final class BedrockInventoryTranslator {

    private static final int TAKE = 0;
    private static final int PLACE = 1;
    private static final int SWAP = 2;
    private static final int DROP = 3;
    private static final int CURSOR_ORDER = Integer.MAX_VALUE;

    private BedrockInventoryTranslator() {
    }

    public static void register(final BedrockProtocol protocol) {
        protocol.replaceServerbound(ServerboundPackets26_3.CONTAINER_CLICK, BedrockInventoryTranslator::containerClick);
        protocol.replaceClientbound(ClientboundBedrockPackets.ITEM_STACK_RESPONSE, BedrockInventoryTranslator::itemStackResponse);
    }

    private static BedrockInventoryState state(final UserConnection user) {
        BedrockInventoryState state = user.get(BedrockInventoryState.class);
        if (state == null) {
            state = new BedrockInventoryState();
            user.put(state);
        }
        return state;
    }

    private static void containerClick(final PacketWrapper wrapper) {
        wrapper.cancel();
        final UserConnection user = wrapper.user();
        final int containerId = wrapper.read(Types.VAR_INT); // container id
        wrapper.read(Types.VAR_INT); // revision
        wrapper.read(Types.SHORT); // slot
        wrapper.read(Types.BYTE); // button
        final ContainerInput action = ContainerInput.values()[wrapper.read(Types.VAR_INT)]; // action
        final Map<Integer, HashedItem> changedSlots = new TreeMap<>();
        final int changedCount = wrapper.read(Types.VAR_INT); // changed slot count
        for (int i = 0; i < changedCount; i++) {
            final int slot = wrapper.read(Types.SHORT); // slot
            changedSlots.put(slot, wrapper.read(Types.HASHED_ITEM)); // item
        }
        final HashedItem carriedItem = wrapper.read(Types.HASHED_ITEM); // carried item

        final InventoryTracker inventoryTracker = user.get(InventoryTracker.class);
        if (inventoryTracker.getPendingCloseContainer() != null) {
            return;
        }
        final Container container = inventoryTracker.getContainerServerbound((byte) containerId);
        if (container == null) { // Like ViaBedrock: the server didn't open the inventory yet
            if (containerId == ContainerID.CONTAINER_ID_INVENTORY.getValue()) {
                final PacketWrapper interact = PacketWrapper.create(ServerboundBedrockPackets.INTERACT, user);
                interact.write(Types.UNSIGNED_BYTE, (short) InteractPacketPayload_Action.OpenInventory.getValue()); // action
                interact.write(BedrockTypes.UNSIGNED_VAR_LONG, user.get(EntityTracker.class).getClientPlayer().runtimeId()); // target entity runtime id
                interact.write(BedrockTypes.OPTIONAL_POSITION_3F, null); // position
                interact.sendToServer(BedrockProtocol.class);
                PacketFactory.sendJavaContainerSetContent(user, inventoryTracker.getInventoryContainer());
            }
            return;
        }

        boolean handled;
        try {
            handled = action != ContainerInput.CLONE && translateClick(user, inventoryTracker, container, changedSlots, carriedItem);
        } catch (final Exception e) {
            ViaFabricPlusBedrock.impl().logger().error("Failed to translate inventory click", e);
            handled = false;
        }
        if (!handled) {
            resync(user, inventoryTracker);
        }
    }

    /**
     * @return whether the click could be sent, otherwise the client is reset to the tracked items
     */
    private static boolean translateClick(final UserConnection user, final InventoryTracker inventoryTracker, final Container container,
                                          final Map<Integer, HashedItem> changedSlots, final HashedItem carriedItem) {
        final ItemRewriter itemRewriter = user.get(ItemRewriter.class);
        final List<Change> changes = new ArrayList<>();
        for (final Map.Entry<Integer, HashedItem> entry : changedSlots.entrySet()) {
            final SlotRef ref = slotRef(inventoryTracker, container, entry.getKey());
            if (ref == null) { // For example the crafting result
                return false;
            }
            changes.add(change(itemRewriter, ref, entry.getValue()));
        }
        changes.add(change(itemRewriter, cursorRef(inventoryTracker), carriedItem));
        changes.removeIf(Change::unchanged);
        if (changes.isEmpty()) {
            return true;
        }

        final List<Move> moves = new ArrayList<>();
        final List<Change> typeChanges = changes.stream().filter(Change::typeChanged).toList();
        if (!typeChanges.isEmpty()) {
            // Two stacks trading places, like clicking a slot with a different item in the cursor
            if (changes.size() != 2 || typeChanges.size() != 2) {
                return false;
            }
            final Change a = changes.get(0);
            final Change b = changes.get(1);
            if (a.oldId != b.newId || a.oldCount != b.newCount || b.oldId != a.newId || b.oldCount != a.newCount) {
                return false;
            }
            final boolean bIsCursor = b.ref.order == CURSOR_ORDER;
            moves.add(new Move(SWAP, 0, bIsCursor ? b.ref : a.ref, bIsCursor ? a.ref : b.ref));
        } else {
            final Map<Integer, List<Change>> byItem = new LinkedHashMap<>();
            for (final Change change : changes) {
                byItem.computeIfAbsent(change.oldCount > 0 ? change.oldId : change.newId, id -> new ArrayList<>()).add(change);
            }
            for (final List<Change> itemChanges : byItem.values()) {
                final List<int[]> sources = new ArrayList<>(); // [index in itemChanges, amount]
                final List<int[]> sinks = new ArrayList<>();
                for (int i = 0; i < itemChanges.size(); i++) {
                    final Change change = itemChanges.get(i);
                    if (change.newCount < change.oldCount) {
                        sources.add(new int[]{i, change.oldCount - change.newCount});
                    } else {
                        sinks.add(new int[]{i, change.newCount - change.oldCount});
                    }
                }
                // Items leave the cursor first, and go to stacks of the same item before empty slots
                sources.sort(Comparator.comparingInt(s -> itemChanges.get(s[0]).ref.order == CURSOR_ORDER ? -1 : itemChanges.get(s[0]).ref.order));
                sinks.sort(Comparator.<int[]>comparingInt(s -> itemChanges.get(s[0]).oldCount > 0 ? 0 : 1).thenComparingInt(s -> itemChanges.get(s[0]).ref.order));

                int sourceIndex = 0;
                for (final int[] sink : sinks) {
                    while (sink[1] > 0) {
                        if (sourceIndex >= sources.size()) { // Items appeared out of nowhere
                            return false;
                        }
                        final int[] source = sources.get(sourceIndex);
                        final int count = Math.min(source[1], sink[1]);
                        moves.add(new Move(PLACE, count, itemChanges.get(source[0]).ref, itemChanges.get(sink[0]).ref));
                        source[1] -= count;
                        sink[1] -= count;
                        if (source[1] == 0) {
                            sourceIndex++;
                        }
                    }
                }
                for (; sourceIndex < sources.size(); sourceIndex++) { // Thrown out of the inventory
                    final int[] source = sources.get(sourceIndex);
                    if (source[1] > 0) {
                        moves.add(new Move(DROP, source[1], itemChanges.get(source[0]).ref, null));
                    }
                }
            }
        }

        sendMoves(user, moves);
        return true;
    }

    private static void sendMoves(final UserConnection user, final List<Move> moves) {
        final BedrockInventoryState state = state(user);
        final Map<BedrockInventoryState.SlotKey, BedrockItem> work = new LinkedHashMap<>();
        final List<Request> requests = new ArrayList<>();

        for (final Move move : moves) {
            final int requestId = state.nextRequestId();
            final BedrockInventoryState.SlotKey sourceKey = move.source.key();
            final BedrockItem sourceItem = work.computeIfAbsent(sourceKey, k -> move.source.item());
            final List<BedrockInventoryState.SlotSnapshot> snapshots = new ArrayList<>();
            snapshots.add(new BedrockInventoryState.SlotSnapshot(sourceKey, sourceItem.copy()));
            final Slot source = new Slot(move.source, stackId(sourceItem));

            if (move.type == DROP) {
                requests.add(new Request(requestId, DROP, move.count, source, null));
                work.put(sourceKey, remove(sourceItem, move.count, requestId));
                state.addPendingRequest(requestId, snapshots);
                continue;
            }

            final BedrockInventoryState.SlotKey destinationKey = move.destination.key();
            final BedrockItem destinationItem = work.computeIfAbsent(destinationKey, k -> move.destination.item());
            snapshots.add(new BedrockInventoryState.SlotSnapshot(destinationKey, destinationItem.copy()));
            final Slot destination = new Slot(move.destination, destinationItem.isEmpty() ? 0 : stackId(destinationItem));

            if (move.type == SWAP) {
                requests.add(new Request(requestId, SWAP, 0, source, destination));
                work.put(sourceKey, withNetId(destinationItem, requestId));
                work.put(destinationKey, withNetId(sourceItem, requestId));
            } else {
                // Bedrock takes items into an empty cursor and places them everywhere else
                final int type = move.destination.order == CURSOR_ORDER && destinationItem.isEmpty() ? TAKE : PLACE;
                requests.add(new Request(requestId, type, move.count, source, destination));
                final BedrockItem moved;
                if (destinationItem.isEmpty()) {
                    moved = sourceItem.copy();
                    moved.setAmount(move.count);
                } else {
                    moved = destinationItem.copy();
                    moved.setAmount(destinationItem.amount() + move.count);
                }
                moved.setNetId(requestId);
                work.put(sourceKey, remove(sourceItem, move.count, requestId));
                work.put(destinationKey, moved);
            }
            state.addPendingRequest(requestId, snapshots);
        }

        final PacketWrapper itemStackRequest = PacketWrapper.create(ServerboundBedrockPackets.ITEM_STACK_REQUEST, user);
        itemStackRequest.write(BedrockTypes.UNSIGNED_VAR_INT, requests.size()); // request count
        for (final Request request : requests) {
            itemStackRequest.write(BedrockTypes.VAR_INT, request.id); // request id
            itemStackRequest.write(BedrockTypes.UNSIGNED_VAR_INT, 1); // action count
            itemStackRequest.write(BedrockTypes.UNSIGNED_VAR_INT, request.type); // action type
            itemStackRequest.write(Types.UNSIGNED_BYTE, (short) request.type); // legacy action type
            switch (request.type) {
                case TAKE, PLACE -> {
                    itemStackRequest.write(Types.UNSIGNED_BYTE, (short) request.count); // count
                    writeSlot(itemStackRequest, request.source); // source
                    writeSlot(itemStackRequest, request.destination); // destination
                }
                case SWAP -> {
                    writeSlot(itemStackRequest, request.source); // source
                    writeSlot(itemStackRequest, request.destination); // destination
                }
                case DROP -> {
                    itemStackRequest.write(Types.UNSIGNED_BYTE, (short) request.count); // count
                    writeSlot(itemStackRequest, request.source); // source
                    itemStackRequest.write(Types.BOOLEAN, false); // randomly
                }
                default -> throw new IllegalStateException("Unknown action type " + request.type);
            }
            itemStackRequest.write(BedrockTypes.UNSIGNED_VAR_INT, 0); // custom names
            itemStackRequest.write(BedrockTypes.INT_LE, -1); // filter cause
        }
        itemStackRequest.sendToServer(BedrockProtocol.class);

        // The client predicted the same result, so only the tracked items change
        for (final Map.Entry<BedrockInventoryState.SlotKey, BedrockItem> entry : work.entrySet()) {
            entry.getKey().container().setItem(entry.getKey().index(), entry.getValue());
        }
    }

    private static void itemStackResponse(final PacketWrapper wrapper) {
        wrapper.cancel();
        final UserConnection user = wrapper.user();
        final InventoryTracker inventoryTracker = user.get(InventoryTracker.class);
        final BedrockInventoryState state = state(user);
        boolean resync = false;

        final int responseCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // response count
        for (int i = 0; i < responseCount; i++) {
            final short status = wrapper.read(Types.UNSIGNED_BYTE); // status
            final int requestId = wrapper.read(BedrockTypes.VAR_INT); // request id
            final List<SlotInfo> slotInfos = new ArrayList<>();
            if (wrapper.read(Types.BOOLEAN)) { // has containers
                final int containerCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // container count
                for (int j = 0; j < containerCount; j++) {
                    final FullContainerName containerName = wrapper.read(BedrockTypes.FULL_CONTAINER_NAME); // container name
                    final int slotCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // slot count
                    for (int k = 0; k < slotCount; k++) {
                        final short slot = wrapper.read(Types.UNSIGNED_BYTE); // slot
                        wrapper.read(Types.UNSIGNED_BYTE); // hotbar slot
                        final short count = wrapper.read(Types.UNSIGNED_BYTE); // count
                        final Integer stackId = wrapper.read(Types.BOOLEAN) ? wrapper.read(BedrockTypes.VAR_INT) : null; // stack id
                        wrapper.read(BedrockTypes.STRING); // custom name
                        wrapper.read(BedrockTypes.STRING); // filtered custom name
                        wrapper.read(BedrockTypes.VAR_INT); // durability correction
                        slotInfos.add(new SlotInfo(containerName, slot, count, stackId));
                    }
                }
            }

            final List<BedrockInventoryState.SlotSnapshot> snapshots = state.removePendingRequest(requestId);
            if (snapshots == null) {
                continue;
            }
            if (status == 0) {
                for (final SlotInfo slotInfo : slotInfos) {
                    final BedrockInventoryState.SlotKey key = slotKey(inventoryTracker, slotInfo.containerName, slotInfo.slot);
                    if (key == null || !state.isLastRequest(key, requestId)) { // Changed again by a later request
                        continue;
                    }
                    BedrockItem item = key.container().getItem(key.index()).copy();
                    if (slotInfo.count == 0) {
                        if (!item.isEmpty()) {
                            item = BedrockItem.empty();
                            resync = true;
                        }
                    } else if (!item.isEmpty()) {
                        item.setNetId(slotInfo.stackId);
                        if (item.amount() != slotInfo.count) {
                            item.setAmount(slotInfo.count);
                            resync = true;
                        }
                    }
                    key.container().setItem(key.index(), item);
                }
                state.onRequestAccepted(requestId, snapshots);
            } else {
                // Rejected, so the slots go back to before the first request that changed them
                for (final Map.Entry<BedrockInventoryState.SlotKey, BedrockItem> entry : state.onRequestRejected(snapshots).entrySet()) {
                    entry.getKey().container().setItem(entry.getKey().index(), entry.getValue());
                }
                resync = true;
            }
        }
        if (resync) {
            resync(user, inventoryTracker);
        }
    }

    private static void resync(final UserConnection user, final InventoryTracker inventoryTracker) {
        final Container current = inventoryTracker.getCurrentContainer();
        if (current != null && current.type() != ContainerType.INVENTORY) {
            PacketFactory.sendJavaContainerSetContent(user, current);
        }
        PacketFactory.sendJavaContainerSetContent(user, inventoryTracker.getInventoryContainer());
    }

    private static Change change(final ItemRewriter itemRewriter, final SlotRef ref, final HashedItem newItem) {
        final BedrockItem oldItem = ref.item();
        int oldId = 0;
        int oldCount = 0;
        if (!oldItem.isEmpty()) {
            final Item javaItem = itemRewriter.javaItem(oldItem);
            if (javaItem != null && !javaItem.isEmpty()) {
                oldId = javaItem.identifier();
                oldCount = javaItem.amount();
            }
        }
        final boolean newEmpty = newItem == null || newItem.isEmpty();
        return new Change(ref, oldId, oldCount, newEmpty ? 0 : newItem.identifier(), newEmpty ? 0 : newItem.amount());
    }

    /**
     * Java's slots of the inventory or chest screen.
     */
    private static SlotRef slotRef(final InventoryTracker inventoryTracker, final Container container, final int javaSlot) {
        final InventoryContainer inventory = inventoryTracker.getInventoryContainer();
        if (container.type() == ContainerType.INVENTORY) {
            if (javaSlot >= 1 && javaSlot <= 4) { // 2x2 crafting grid
                return new SlotRef(ContainerEnumName.CraftingInputContainer, 27 + javaSlot, inventoryTracker.getHudContainer(), 27 + javaSlot, javaSlot);
            } else if (javaSlot >= 5 && javaSlot <= 8) {
                return new SlotRef(ContainerEnumName.ArmorContainer, javaSlot - 5, inventoryTracker.getArmorContainer(), javaSlot - 5, javaSlot);
            } else if (javaSlot >= 9 && javaSlot <= 35) {
                return new SlotRef(ContainerEnumName.InventoryContainer, javaSlot, inventory, javaSlot, javaSlot);
            } else if (javaSlot >= 36 && javaSlot <= 44) {
                return new SlotRef(ContainerEnumName.HotbarContainer, javaSlot - 36, inventory, javaSlot - 36, javaSlot);
            } else if (javaSlot == 45) {
                return new SlotRef(ContainerEnumName.OffhandContainer, 1, inventoryTracker.getOffhandContainer(), 0, javaSlot);
            }
        } else if (container.type() == ContainerType.CONTAINER && javaSlot >= 0) {
            final int size = container.size();
            if (javaSlot < size) {
                return new SlotRef(ContainerEnumName.LevelEntityContainer, javaSlot, container, javaSlot, javaSlot);
            }
            final int playerSlot = javaSlot - size;
            if (playerSlot < 27) {
                return new SlotRef(ContainerEnumName.InventoryContainer, 9 + playerSlot, inventory, 9 + playerSlot, javaSlot);
            } else if (playerSlot < 36) {
                return new SlotRef(ContainerEnumName.HotbarContainer, playerSlot - 27, inventory, playerSlot - 27, javaSlot);
            }
        }
        return null;
    }

    private static SlotRef cursorRef(final InventoryTracker inventoryTracker) {
        return new SlotRef(ContainerEnumName.CursorContainer, 0, inventoryTracker.getHudContainer(), 0, CURSOR_ORDER);
    }

    /**
     * The tracked slot of a container the server refers to.
     */
    private static BedrockInventoryState.SlotKey slotKey(final InventoryTracker inventoryTracker, final FullContainerName containerName, final int slot) {
        return switch (containerName.name()) {
            case HotbarContainer, InventoryContainer, CombinedHotbarAndInventoryContainer -> new BedrockInventoryState.SlotKey(inventoryTracker.getInventoryContainer(), slot);
            case ArmorContainer -> new BedrockInventoryState.SlotKey(inventoryTracker.getArmorContainer(), slot);
            case OffhandContainer -> new BedrockInventoryState.SlotKey(inventoryTracker.getOffhandContainer(), 0);
            case CursorContainer -> new BedrockInventoryState.SlotKey(inventoryTracker.getHudContainer(), 0);
            case CraftingInputContainer -> new BedrockInventoryState.SlotKey(inventoryTracker.getHudContainer(), slot);
            case LevelEntityContainer, BarrelContainer, ShulkerBoxContainer -> {
                final Container current = inventoryTracker.getCurrentContainer();
                yield current != null && current.type() != ContainerType.INVENTORY && slot < current.size() ? new BedrockInventoryState.SlotKey(current, slot) : null;
            }
            default -> null;
        };
    }

    private static void writeSlot(final PacketWrapper wrapper, final Slot slot) {
        wrapper.write(BedrockTypes.FULL_CONTAINER_NAME, new FullContainerName(slot.ref.name, null)); // container name
        wrapper.write(Types.UNSIGNED_BYTE, (short) slot.ref.bedrockSlot); // slot
        wrapper.write(BedrockTypes.INT_LE, slot.stackId); // stack id
    }

    private static int stackId(final BedrockItem item) {
        return item.netId() != null ? item.netId() : 0;
    }

    private static BedrockItem remove(final BedrockItem item, final int count, final int requestId) {
        if (item.amount() <= count) {
            return BedrockItem.empty();
        }
        final BedrockItem remaining = item.copy();
        remaining.setAmount(item.amount() - count);
        remaining.setNetId(requestId);
        return remaining;
    }

    private static BedrockItem withNetId(final BedrockItem item, final int requestId) {
        if (item.isEmpty()) {
            return item;
        }
        final BedrockItem copy = item.copy();
        copy.setNetId(requestId);
        return copy;
    }

    /**
     * @param order Java's slot number, for the order of moves
     */
    private record SlotRef(ContainerEnumName name, int bedrockSlot, Container container, int index, int order) {

        BedrockInventoryState.SlotKey key() {
            return new BedrockInventoryState.SlotKey(this.container, this.index);
        }

        BedrockItem item() {
            return this.container.getItem(this.index).copy();
        }

    }

    private record Change(SlotRef ref, int oldId, int oldCount, int newId, int newCount) {

        boolean unchanged() {
            return this.oldCount == this.newCount && (this.oldCount == 0 || this.oldId == this.newId);
        }

        boolean typeChanged() {
            return this.oldCount > 0 && this.newCount > 0 && this.oldId != this.newId;
        }

    }

    private record Move(int type, int count, SlotRef source, SlotRef destination) {
    }

    private record Slot(SlotRef ref, int stackId) {
    }

    private record Request(int id, int type, int count, Slot source, Slot destination) {
    }

    private record SlotInfo(FullContainerName containerName, short slot, short count, Integer stackId) {
    }

}
