#!/bin/bash
set +e
SJ=$(find ~/.gradle /home/runner/work -name '*minecraft*sources*.jar' 2>/dev/null | head -1)
mkdir -p /tmp/src && cd /tmp/src && unzip -q -o "$SJ"
show() { echo "=================== $1"; cat "net/minecraft/$1.java"; }
show client/gui/screens/inventory/AbstractRecipeBookScreen
show client/gui/screens/inventory/CraftingScreen
grep -n "public void extractRenderState\|public void render\|extractContents\|renderContents\|extractLabels\|renderLabels\|super.extract\|super.render\|extractBackground\|renderBg" net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java
echo "=================== AbstractContainerScreen render body"; awk '/public void extractRenderState|public void render\(/{p=1} p{print; n++} n>60{exit}' net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java
echo "=================== Screen render"; awk '/public void extractRenderState|public void render\(/{p=1} p{print; n++} n>25{exit}' net/minecraft/client/gui/screens/Screen.java
show stats/RecipeBookSettings
grep -rn "class ClientboundRecipeBookSettingsPacket" -A12 net/minecraft/network/protocol/game/ClientboundRecipeBookSettingsPacket.java
grep -n "RecipeBookChangeSettings" -A10 net/minecraft/server/network/ServerGamePacketListenerImpl.java
grep -n "public.*BookSetting\|public RecipeBookSettings\|public void sendInitialRecipeBook\|public boolean contains" net/minecraft/stats/ServerRecipeBook.java net/minecraft/stats/RecipeBook.java
grep -n "public.*static.*of\|public record\|public static" net/minecraft/world/item/crafting/CraftingInput.java | head
grep -n "assemble\|getResultItem" net/minecraft/world/item/crafting/CraftingRecipe.java net/minecraft/world/item/crafting/Recipe.java net/minecraft/world/item/crafting/ShapedRecipe.java
grep -n "getRecipes\|public.*byType\|recipeMap\|public .*getAllRecipesFor\|public Collection" net/minecraft/world/item/crafting/RecipeManager.java net/minecraft/world/item/crafting/RecipeMap.java | head
