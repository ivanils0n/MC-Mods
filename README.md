# MC-Mods

Mods Fabric para Minecraft. Cada pasta `<mod>-<versão>/` é um mod independente.

| Mod | Pasta | Minecraft |
|-----|-------|-----------|
| Mob Health Bar | `mobhealthbar-1.2.1/` | 26.2 e 26.3+ (Fabric, só cliente) |
| Item Despawn Timer | `itemtimer-1.0.0/` | 26.2 e 26.3+ (Fabric, só cliente) |

## Item Despawn Timer

Mostra acima dos itens no chão quanto falta para sumirem (5 min). Itens dropados juntos no mesmo lugar (ex.: ao morrer) mostram um só cronômetro, com o tempo do primeiro a sumir. Amarelo no último minuto, vermelho nos últimos 10 s.

- Singleplayer/LAN (host): tempo exato, mesmo para itens que você deixou para trás e voltou para buscar.
- Servidor multiplayer: o servidor não informa a idade do item, então o cronômetro conta a partir de quando o item apareceu para você.

## Compilar

O GitHub Actions compila a cada push. Os `.jar` ficam em **Actions → último build → Artifacts** (um `mods-mc<versão>` por versão do Minecraft, definidas na matriz do `build.yml`).

Localmente (Java 25): `./gradlew build` → `<pasta>/build/libs/`.

## Novo mod

Crie uma pasta `<mod>-<versão>/` com `build.gradle` e `gradle.properties` (copie do mobhealthbar). Ela entra no build automaticamente.
