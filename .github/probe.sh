#!/bin/bash
set +e
SJ=$(find ~/.gradle /home/runner/work -name '*minecraft*sources*.jar' 2>/dev/null | head -1)
echo "SJ=$SJ"
mkdir -p /tmp/src && cd /tmp/src && unzip -q -o "$SJ"
show() { echo "=================== $1"; cat "net/minecraft/$1.java"; }
show recipebook/ServerPlaceRecipe
show recipebook/PlaceRecipeHelper
show world/inventory/AbstractCraftingMenu
show world/inventory/CraftingMenu
show world/inventory/RecipeBookMenu
show world/entity/player/StackedItemContents
show world/level/block/CraftingTableBlock
show client/gui/screens/recipebook/RecipeBookComponent
show client/gui/screens/recipebook/RecipeCollection
echo "=================== handlePlaceRecipe"; grep -n -A40 "public void handlePlaceRecipe" net/minecraft/server/network/ServerGamePacketListenerImpl.java
echo "=================== Inventory"; grep -n -B2 -A25 "fillStackedContents\|findSlotMatchingCraftingIngredient\|isUsableForCrafting" net/minecraft/world/entity/player/Inventory.java
echo "=================== StackedContents"; grep -n "public\|interface" net/minecraft/world/entity/player/StackedContents.java | head -40
