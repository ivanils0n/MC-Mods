#!/bin/bash
# Sonda temporária: de onde vem o som de bigorna ao abrir a tela da bigorna.
set +e
MJ=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-merged*.jar' | head -1)
CJ=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-client.jar' | head -1)
for c in net.minecraft.client.gui.screens.inventory.AnvilScreen net.minecraft.client.gui.screens.inventory.ItemCombinerScreen \
  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen net.minecraft.client.gui.screens.MenuScreens \
  net.minecraft.world.inventory.AnvilMenu net.minecraft.world.inventory.ItemCombinerMenu net.minecraft.world.inventory.MenuType; do
  echo "=== $c"
  javap -cp "$MJ" -c -p "$c" 2>&1 | grep -nE "SoundEvent|playSound|levelEvent|ANVIL|Sound" | head -30
done
echo "=== MenuType fields/ctor"; javap -cp "$MJ" -p net.minecraft.world.inventory.MenuType 2>&1 | head -60
echo "=== quem usa sons de bigorna"; for s in ANVIL_USE ANVIL_PLACE ANVIL_LAND; do
  for cls in $(unzip -l "$MJ" | awk '{print $4}' | grep -E '^net/minecraft/(client/gui|client/multiplayer|world/inventory|client/sounds).*\.class$' | sed 's/\.class$//; s#/#.#g'); do
    javap -cp "$MJ" -c -p "$cls" 2>/dev/null | grep -q "SoundEvents.$s" && echo "$s usado em $cls"
  done
done
echo "=== sounds.json anvil/ui"; unzip -p "$CJ" assets/minecraft/sounds.json 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); [print(k) for k in d if 'anvil' in k or k.startswith('ui.')]"
