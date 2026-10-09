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

import com.google.common.collect.BiMap;
import com.viaversion.viafabricplus.bedrock.injection.access.IBlockStateRewriter;
import com.viaversion.viaversion.api.connection.StoredObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import com.viaversion.viaversion.libs.fastutil.ints.Int2ObjectMap;
import com.viaversion.viaversion.libs.fastutil.ints.IntSortedSet;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.raphimc.viabedrock.api.util.TextUtil;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.ItemEntry;
import net.raphimc.viabedrock.protocol.rewriter.BlockStateRewriter;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The items of the blocks a server defines, like The Hive's bridging blocks and boom boxes, aren't in its item list:
 * the Bedrock client numbers custom blocks from 10000 in the order they are defined, and their items count down from
 * 255 by that number. ViaBedrock didn't know them and showed empty slots. They are shown as the item of the Java block
 * the custom block is drawn as, with the block's name.
 */
@Mixin(value = ItemRewriter.class, remap = false)
public abstract class MixinItemRewriter extends StoredObject {

    @Unique
    private static final int FIRST_CUSTOM_BLOCK = 10000;

    @Shadow
    @Final
    private BiMap<String, Integer> items;

    @Shadow
    @Final
    private Int2ObjectMap<IntSortedSet> blockItemValidBlockStates;

    private MixinItemRewriter(final UserConnection user) {
        super(user);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void addCustomBlockItems(final UserConnection user, final ItemEntry[] itemEntries, final CallbackInfo ci) {
        // start_game makes an empty item list, which ViaBedrock only replaces with the server's while it stays empty
        if (itemEntries.length == 0) {
            return;
        }
        final BlockStateRewriter blockStateRewriter = user.get(BlockStateRewriter.class);
        final List<String> customBlocks = ((IBlockStateRewriter) blockStateRewriter).viaFabricPlusBedrock$getCustomBlocks();
        for (int i = 0; i < customBlocks.size(); i++) {
            final String name = customBlocks.get(i);
            final int id = 255 - (FIRST_CUSTOM_BLOCK + i);
            final IntSortedSet validBlockStates = blockStateRewriter.validBlockStates(name);
            if (validBlockStates != null && !this.items.containsKey(name) && !this.items.containsValue(id)) {
                this.items.put(name, id);
                this.blockItemValidBlockStates.put(id, validBlockStates);
            }
        }
    }

    @Inject(method = "javaItem(Lnet/raphimc/viabedrock/protocol/model/BedrockItem;)Lcom/viaversion/viaversion/api/minecraft/item/Item;", at = @At("HEAD"), cancellable = true)
    private void customBlockItem(final BedrockItem bedrockItem, final CallbackInfoReturnable<Item> cir) {
        if (bedrockItem.isEmpty() || bedrockItem.identifier() > 255 - FIRST_CUSTOM_BLOCK) {
            return;
        }
        final String name = this.items.inverse().get(bedrockItem.identifier());
        final IntSortedSet validBlockStates = this.blockItemValidBlockStates.get(bedrockItem.identifier());
        if (name == null || validBlockStates == null || validBlockStates.isEmpty()) {
            return;
        }
        final BlockStateRewriter blockStateRewriter = this.user().get(BlockStateRewriter.class);
        final int blockState = bedrockItem.blockRuntimeId() != 0 ? bedrockItem.blockRuntimeId() : validBlockStates.firstInt();
        final int javaBlockState = blockStateRewriter.javaId(blockState);
        net.minecraft.world.item.Item javaItem = javaBlockState > 0 ? Block.stateById(javaBlockState).getBlock().asItem() : Items.AIR;
        if (javaItem == Items.AIR) {
            javaItem = Items.PAPER;
        }

        final StructuredDataContainer data = ProtocolConstants.createStructuredDataContainer();
        final ResourcePackStorage resourcePackStorage = this.user().get(ResourcePackStorage.class);
        final String nameKey = "tile." + name + ".name";
        String displayName = resourcePackStorage != null ? resourcePackStorage.getTexts().get(nameKey) : nameKey;
        if (displayName.equals(nameKey)) { // Like hive:boombox_standard -> Boombox Standard
            displayName = Arrays.stream(name.substring(name.indexOf(':') + 1).split("_"))
                .filter(word -> !word.isEmpty()).map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1)).collect(Collectors.joining(" "));
        }
        data.set(StructuredDataKey.ITEM_NAME, TextUtil.stringToNbt(displayName));
        cir.setReturnValue(new StructuredItem(BuiltInRegistries.ITEM.getId(javaItem), bedrockItem.amount(), data));
    }

}
