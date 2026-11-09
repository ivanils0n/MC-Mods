#!/bin/bash
# Sonda temporária: imprime as assinaturas da API usadas pelo Baú Inteligente.
set +e
MJ=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-merged*.jar' | head -1)
[ -z "$MJ" ] && MJ=$(find ~/.gradle/caches/fabric-loom -name '*.jar' -path '*minecraft*' | grep -v sources | head -5 | tr '\n' ':')
CJ=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-client.jar' | head -1)
echo "MJ=$MJ"; echo "CJ=$CJ"
find ~/.gradle/caches/fabric-loom -name '*.jar' | grep -i minecraft | head -20
FAPI=$(find ~/.gradle/caches/modules-2 -name 'fabric-*.jar' | grep -v sources | tr '\n' ':')
FP=$(for j in $(find ~/.gradle/caches/modules-2 -name 'fabric-*.jar' | grep -v sources); do unzip -l "$j" 2>/dev/null | grep -q 'net/fabricmc/fabric/api/entity/FakePlayer.class' && echo "$j"; done | head -1)
echo "FP=$FP"

p() { echo "=== $1 ${2:+[$2]}"; javap -cp "$MJ:$FAPI" ${3:--public} "$1" 2>&1 | grep -E "${2:-.}" | head -${4:-80}; }

p net.minecraft.world.inventory.AbstractContainerMenu "clicked|Carried|sendAllDataToRemote|broadcastChanges|removed|stillValid|quickMoveStack|moveItemStackTo|slots|setData|addSlot|getSlot|initMenu|resumeRemoteUpdates|suppressRemoteUpdates|incrementStateId|containerId" "-p" 60
p net.minecraft.world.inventory.ContainerInput "" "-public" 30
p net.minecraft.world.inventory.ClickType "" "-public" 30
p net.minecraft.world.inventory.ChestMenu "ChestMenu\(|getContainer|getRowCount" "-public" 20
p net.minecraft.world.inventory.AnvilMenu "" "-p" 60
p net.minecraft.world.inventory.ItemCombinerMenu "" "-p" 60
p net.minecraft.world.inventory.Slot "index|mayPlace|mayPickup|set\(|getItem|Slot\(" "-public" 30
p net.minecraft.world.item.ItemStack "isSameItem|hash|copy|MaxStack|HoverName|ItemName|getCount|setCount|grow|shrink|split|isEmpty| set\(| get\(|has\(|getItem\(|getStyledHoverName|getDisplayName|isDamageableItem|isStackable|EMPTY|ItemStack\(" "-public" 80
p net.minecraft.world.Container "" "-public" 60
p net.minecraft.world.SimpleContainer "SimpleContainer\(|setChanged|getItem|setItem" "-public" 20
p net.minecraft.world.level.block.entity.ChestBlockEntity "class|ChestBlockEntity\(" "-public" 10
p net.minecraft.world.level.block.entity.BarrelBlockEntity "class" "-public" 5
echo "=== copper/chest block entities"; unzip -l "$MJ" | grep -iE 'block/entity/.*(Chest|Barrel).*BlockEntity\.class' | awk '{print $4}'
p net.minecraft.world.level.block.ChestBlock "TYPE|FACING|getConnectedDirection|getBlockType|getContainer|static" "-public" 30
p net.minecraft.world.level.block.state.properties.ChestType "" "-public" 20
p net.minecraft.locale.Language "" "-public" 30
p net.minecraft.network.chat.contents.TranslatableContents "getKey|getArgs|getFallback|class" "-public" 20
p net.minecraft.network.chat.ComponentContents "" "-public" 20
p net.minecraft.network.chat.contents.PlainTextContents "" "-public" 20
p net.minecraft.network.chat.Component "getContents|getSiblings|getString|visit|translatable\(|literal\(" "-public" 30
p net.minecraft.network.chat.FormattedText "" "-public" 30
p net.minecraft.server.level.ClientInformation "" "-public" 30
p net.minecraft.server.level.ServerPlayer "clientInformation|playNotifySound|openMenu|initMenu|nameAndId|closeContainer|sendSystemMessage|displayClientMessage|getInventory|hasDisconnected|isSpectator|level\(" "-public" 30
p net.minecraft.world.entity.player.Player "hasCorrectToolForDrops|getInventory|containerMenu|displayClientMessage|isCreative|hasInfiniteMaterials|getMainHandItem|isShiftKeyDown|isSecondaryUseActive|playSound|drop\(" "-public" 30
p net.minecraft.server.level.ServerLevel "sendParticles|getBlockEntity|isLoaded|isPositionEntityTicking|getServer|playSound" "-public" 20
p net.minecraft.world.level.Level "getBlockEntity|isLoaded|playSound\(|dimension\(|destroyBlock" "-public" 20
p net.minecraft.world.entity.player.Inventory "add\(|getItem|getContainerSize|getNonEquipmentItems|placeItemBackInInventory|setItem|INVENTORY_SIZE|getFreeSlot|getSlotWithRemainingSpace|setChanged|items" "-public" 30
p net.minecraft.SharedConstants "getCurrentVersion" "-public" 5
p net.minecraft.WorldVersion "" "-public" 20
p net.minecraft.core.registries.BuiltInRegistries "ITEM" "-public" 5
p net.minecraft.core.DefaultedRegistry "getKey" "-public" 5
p net.minecraft.core.Registry "getKey|getValue|get\(" "-public" 10
p net.minecraft.world.item.component.ItemLore "ItemLore\(|lines|EMPTY" "-public" 10
p net.minecraft.world.item.component.CustomModelData "CustomModelData\(" "-public" 5
p net.minecraft.world.item.component.CustomData "of\(|copyTag|contains|EMPTY|matchedBy|getUnsafe" "-public" 10
p net.minecraft.nbt.CompoundTag "getInt|putInt|getString|putString|getBoolean|putBoolean|contains|getCompound|put\(" "-public" 30
p net.minecraft.core.component.DataComponents "CUSTOM_NAME|LORE|CUSTOM_DATA|CUSTOM_MODEL_DATA|MAX_STACK_SIZE|ENCHANTMENT_GLINT_OVERRIDE|ITEM_NAME|ITEM_MODEL|TOOLTIP_DISPLAY|HIDE" "-public" 20
p net.minecraft.world.item.Items " (OBSIDIAN|BLAZE_ROD|ARROW|WRITABLE_BOOK|COMPARATOR|HOPPER|LEAD|BOOK|CHEST|ENDER_PEARL|BARRIER|SPECTRAL_ARROW);" "-public" 20
p net.minecraft.core.Direction "getOpposite|toYRot|getStepX|Plane|get2DDataValue|from2DDataValue|getName|byName" "-public" 20
p net.minecraft.world.level.block.Block "popResource\(" "-public" 5
p net.minecraft.sounds.SoundEvents " (UI_BUTTON_CLICK|ENDER_CHEST_OPEN|ENDER_CHEST_CLOSE|BARREL_OPEN|CHAIN_PLACE|ITEM_PICKUP|NOTE_BLOCK_BASS|VILLAGER_NO|LODESTONE_COMPASS_LOCK|AMETHYST_BLOCK_CHIME|BUNDLE_INSERT|BUNDLE_INSERT_FAIL);" "-public" 20
p net.minecraft.world.MenuProvider "" "-public" 10
p net.minecraft.world.SimpleMenuProvider "" "-public" 10
p net.minecraft.world.inventory.MenuType "GENERIC_9x6|ANVIL" "-public" 5
p net.minecraft.world.entity.EquipmentSlot "MAINHAND" "-public" 3
p net.minecraft.world.level.storage.LevelResource "ROOT" "-public" 3
p net.minecraft.server.MinecraftServer "getServerDirectory|isDedicatedServer|isSingleplayerOwner|getWorldPath|getPlayerList|execute\(|getLevel\(|overworld|getRecipeManager|halt" "-public" 20
p net.minecraft.world.level.Level "NETHER|OVERWORLD|END" "-public" 5
p net.minecraft.core.particles.ParticleTypes " (PORTAL|END_ROD|REVERSE_PORTAL|WAX_ON|HAPPY_VILLAGER|ENCHANT|WITCH|DRAGON_BREATH);" "-public" 10
p net.minecraft.world.InteractionResult "SUCCESS|PASS|FAIL|CONSUME" "-public" 10
echo "=== FakePlayer"; javap -cp "$FP:$MJ" -public net.fabricmc.fabric.api.entity.FakePlayer 2>&1 | head -30
echo "=== sendAllDataToRemote bytecode"; javap -cp "$MJ" -c -p net.minecraft.world.inventory.AbstractContainerMenu 2>&1 | grep -A14 'public void sendAllDataToRemote()' | head -18
echo "=== AnvilMenu.mayPickup/createResult bytecode"; javap -cp "$MJ" -c -p net.minecraft.world.inventory.AnvilMenu 2>&1 | grep -A10 'protected boolean mayPickup' | head -14
echo "=== client items"; for i in arrow writable_book comparator hopper lead book obsidian blaze_rod; do echo "-- $i"; unzip -p "$CJ" assets/minecraft/items/$i.json; echo; done
echo "=== version.json"; unzip -p "$CJ" version.json
echo "=== lang files in client jar"; unzip -l "$CJ" | grep -E 'lang/' | head -5
echo "=== container screen labels"; javap -cp "$MJ" -c -p net.minecraft.client.gui.screens.inventory.ContainerScreen 2>&1 | grep -E 'inventoryLabelY|imageHeight|bipush|sipush' | head -20
echo "=== font ascii widths sample"; unzip -l "$CJ" | grep -E 'font/(ascii|accented|nonlatin)' | head
