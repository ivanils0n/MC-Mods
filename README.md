# MC-Mods

Mods Fabric para Minecraft. Cada pasta `<mod>-<versão>/` é um mod independente.

| Mod | Pasta | Minecraft |
|-----|-------|-----------|
| Mob Health Bar | `mobhealthbar-1.2.1/` | 26.2 e 26.3+ (Fabric, só cliente) |
| Item Despawn Timer | `itemtimer-1.0.0/` | 26.2 e 26.3+ (Fabric, só cliente) |
| Altar de Teleporte | `tpaltar-1.0.0/` | 26.2 e 26.3+ (Fabric, servidor ou singleplayer, requer Fabric API) |

## Altar de Teleporte

Vai na pasta `mods/` do servidor, junto com o Fabric API; os jogadores entram sem instalar nada. Em singleplayer, coloque o mesmo .jar na pasta `mods/` do jogo.

- **Receita** (bancada): pedra lisa ×3 em cima; pedra lisa, ender pearl, pedra lisa no meio; obsidiana ×3 embaixo. Aparece no livro de receitas quando o jogador pega uma ender pearl.
- **Colocar**: clique no chão com o altar; o servidor pede o nome no chat (até 24 letras, único por dimensão, `cancelar` desiste). Precisa de 3 blocos livres de altura.
- **Abastecer**: clique no altar com ender pearls na mão. Guarda até 64; cada viagem **até** este altar gasta 4 (16 viagens).
- **Viajar**: clique no altar de mãos vazias → lista dos outros altares da mesma dimensão → clique no destino. Sem 4 pérolas no destino, a viagem é recusada.
- **Remover**: quebre a base de obsidiana. Com picareta de diamante ou netherite o altar e as pérolas guardadas voltam; com qualquer outra coisa ele se despedaça sem dropar nada.
- Cada dimensão tem sua própria lista. Os dados ficam em `<mundo>/tpaltar.json`, fora do save do Minecraft. Sem o mod, o mundo abre normalmente: sobram só a obsidiana e a decoração.

## Item Despawn Timer

Mostra acima dos itens no chão quanto falta para sumirem (5 min). Itens dropados juntos no mesmo lugar (ex.: ao morrer) mostram um só cronômetro, com o tempo do primeiro a sumir. Amarelo no último minuto, vermelho nos últimos 10 s.

- Singleplayer/LAN (host): tempo exato, mesmo para itens que você deixou para trás e voltou para buscar.
- Servidor multiplayer: o servidor não informa a idade do item, então o cronômetro conta a partir de quando o item apareceu para você.

## Compilar

O GitHub Actions compila a cada push. Os `.jar` ficam em **Actions → último build → Artifacts**, um download por mod e versão do Minecraft (ex.: `mc26.2-tpaltar-1.0.0`). As versões ficam na matriz do `build.yml`.

Localmente (Java 25): `./gradlew build` → `<pasta>/build/libs/`.

## Novo mod

Crie uma pasta `<mod>-<versão>/` com `build.gradle` e `gradle.properties` (copie do mobhealthbar). Ela entra no build automaticamente.
