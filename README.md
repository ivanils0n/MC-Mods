# MC-Mods

Mods Fabric para Minecraft. Cada pasta `<mod>-<versão>/` é um mod independente.

| Mod | Pasta | Minecraft |
|-----|-------|-----------|
| Mob Health Bar | `mobhealthbar-1.2.1/` | 26.2 e 26.3+ (Fabric, só cliente) |
| Item Despawn Timer | `itemtimer-1.0.0/` | 26.2 e 26.3+ (Fabric, só cliente) |
| Altar de Teleporte | `tpaltar-1.0.0/` | 26.2 e 26.3+ (Fabric, servidor ou singleplayer, requer Fabric API) |
| Baú Central | `smartchest-1.1.0/` | 26.2 (Fabric, servidor ou singleplayer, requer Fabric API) |

## Altar de Teleporte

Vai na pasta `mods/` do servidor, junto com o Fabric API; os jogadores entram sem instalar nada. Em singleplayer, coloque o mesmo .jar na pasta `mods/` do jogo.

- **Receita** (bancada): pedra lisa ×3 em cima; pedra lisa, ender pearl, pedra lisa no meio; obsidiana ×3 embaixo. Aparece no livro de receitas quando o jogador pega uma ender pearl.
- **Colocar**: clique no chão com o altar; abre uma tela de bigorna para digitar o nome (até 24 letras, único por dimensão). Clique na ender pearl do resultado para confirmar ou feche a tela para desistir. Precisa de 3 blocos livres de altura.
- **Abastecer**: abra o menu do altar e coloque ender pearls nos 4 espaços da última fileira (até 64); dá para tirar de volta do mesmo jeito. Cada viagem **até** este altar gasta 2 (32 viagens). O estoque é compartilhado por todos.
- **Viajar**: clique no altar → lista dos outros altares da mesma dimensão → clique no destino. Não há limite de altares: acima de 45 destinos a lista ganha páginas (setas nas pontas da última fileira). Sem 2 pérolas no destino, a viagem é recusada. Saída e chegada soltam partículas roxas.
- **Em uso**: o olho gira para encarar o jogador mais próximo (até 16 blocos) e muda de cor enquanto alguém está com o menu aberto; nesse tempo ninguém mais abre o menu daquele altar.
- **Remover**: quebre a base de obsidiana. Com picareta de diamante ou netherite o altar e as pérolas guardadas voltam; com qualquer outra coisa ele se despedaça sem dropar nada.
- **Visual (obelisco rúnico)**: obelisco com runas que acendem em onda, quatro cristais girando em volta e um olho 3D flutuante (verde parado, roxo em uso). Vem dentro do .jar (singleplayer) e, no servidor dedicado, o próprio mod envia o resource pack ao jogador quando ele entra (só pergunta na primeira vez; atualiza sozinho quando a textura muda). O item do altar vira um obelisco 3D em miniatura e os destinos da lista viram olhos rúnicos (aberto: com carga; fechado: sem ender pearls) e as telas do altar e de nome ganham fundo próprio (os vidros da fileira de pérolas ficam invisíveis). Quem recusar vê o visual simples (pedra lisa, pérola, olho do fim e telas vanilla). As texturas são geradas por `arte/gerar.py`.
- **Desempenho**: altares sem jogador a 48 blocos ficam parados (sem giro, animação ou partículas); as animações são interpoladas pelo cliente (1 atualização a cada 2 s por altar) e as mudanças de pérolas são gravadas no máximo 1 vez por segundo.
- Cada dimensão tem sua própria lista. Os dados ficam em `<mundo>/tpaltar.json`, fora do save do Minecraft. A decoração (coluna, tampo, olho) nunca é salva no mapa: sem o mod, sobra só uma obsidiana comum; com o mod, ela é recriada sozinha. Sobras de versões antigas são apagadas automaticamente.

## Baú Inteligente

Vai na pasta `mods/` do servidor, junto com o Fabric API; os jogadores entram sem instalar nada. Em singleplayer, coloque o mesmo .jar na pasta `mods/` do jogo.

- **Receitas** (bancada): **Baú Inteligente** = 8 obsidianas em volta de um baú. **Vinculador** = 8 ender pearls em volta de uma vara de blaze. Aparecem no livro de receitas ao pegar obsidiana / vara de blaze.
- **Colocar**: clique no chão com o Baú Inteligente. Ele fica virado para você e nunca se junta com baús vizinhos.
- **Vincular**: clique no Baú Inteligente com o Vinculador (ele passa a "apontar" para esse baú) e depois clique nos baús e barris que quer ligar; clicar de novo num baú ligado o desliga. Baú duplo entra inteiro com um clique. Limites: mesma dimensão, até 64 baús e distância:
  - **1.1.0** (26.2): sem limite de blocos; o baú precisa estar dentro da distância de visão (a menor entre a do servidor e a do jogador, em chunks) contada a partir do Baú Inteligente, senão aparece "Baú muito longe". Se a distância de visão diminuir depois, os baús que ficarem fora continuam ligados, mas não aparecem: ao abrir o Baú Inteligente aparece quantos estão fora, e eles voltam quando a área estiver carregada de novo. Desligar sempre funciona. Segurando o Vinculador você vê partículas nos baús ligados.
- **Usar**: clique no Baú Inteligente. A tela junta os itens de todos os baús ligados (itens iguais somados; o número no espaço vai até 99 e o total exato aparece ao passar o mouse).
  - Clique: pega um pack · botão direito: metade · Shift+clique: direto para o inventário.
  - Guardar: solte um item em qualquer espaço da grade, Shift+clique num item do seu inventário ou use o botão **Guardar inventário** (tudo menos a hotbar). Primeiro completa pilhas iguais, depois usa espaços vazios. Se os baús ligados estiverem cheios, nada é guardado e o item fica com você.
  - Barra de baixo: ◀ página ▶ · **pesquisar** (abre uma caixa de texto; o resultado mostra ao vivo o que foi achado; botão direito limpa) · **ordenar** (quantidade, nome, mod/id) · **guardar inventário** · **baús ligados** (fecha a tela e marca os baús com partículas).
- **Pesquisa no idioma do jogo**: acha pelo nome no idioma de cada jogador, pelo nome em inglês e pelo id do item, sem diferenciar acentos ("pa" acha "Pá"). Em singleplayer usa o idioma do próprio jogo. No servidor dedicado, o arquivo de idioma do jogador é baixado uma vez da Mojang (conferido pelo SHA-1) e guardado em `<mundo>/smartchest-lang/`; sem internet, a pesquisa usa inglês e id.
- **Quebrar**: como a obsidiana, só volta com picareta de diamante ou netherite. Os vínculos são apagados: ao colocar de novo, é preciso vincular os baús outra vez. Nenhum item se perde (eles sempre ficam nos baús normais).
- **Visual (Obsidiana do End)**: o baú e o Vinculador ("Chave do End") têm textura própria, e a tela ganha fundo escuro com botões. Vem dentro do .jar (singleplayer) e, no servidor dedicado, o próprio mod envia o resource pack ao jogador. Quem recusar vê obsidiana, vara de blaze e a tela de baú comum (com flechas, livro, comparador, funil e laço como botões). As texturas são geradas por `arte/gerar.py`.
  - **1.1.0** (26.2): os itens do mod usam modelos próprios (`smartchest:*`), então a textura do mod vale mesmo com outros resource packs ativos, e os itens vanilla (livro, flecha, funil...) continuam com o pack do jogador. O topo da tela do baú mostra só "filtro: ..." quando há uma pesquisa ativa. Quem recusar o pack vê o Baú Inteligente e o Vinculador sem textura (roxo e preto); os botões da tela continuam como itens vanilla.
  - **1.1.0** (26.2): a primeira fileira da tela tem abas: um encaixe de **filtro pelo item** (clique nele com um item na mão para ver só itens desse tipo; sem item, tira o filtro) e 8 categorias, baseadas nas abas do modo criativo do próprio jogo: Todos, Construção (inclui madeira, troncos e blocos funcionais como bancada, fornalha e baús), Armaduras (de jogador, cavalo, lobo...), Minérios (minério, minério bruto, lingotes, gemas e seus blocos), Redstone (o que dá ou recebe sinal: pistão, comparador, observador, ejetor, funil...), Ferramentas e combate, Comida e poções (inclui sementes, mudas e plantas) e Ingredientes e outros. A lista mostra 36 itens por página. Aba e filtro voltam para "Todos" ao abrir o baú de novo. Passando o mouse num item aparecem o total e em quantos baús ele está (o número no item vai até 99). A rodinha do **Mouse Tweaks** tira itens (precisa de um espaço vazio no inventário; o item junta com o monte que já existe).
  - **1.1.0** (26.2): **pesquisa** com fundo "Céu do End". O inventário da tela vira parte do baú: os 27 espaços mostram os itens que batem com o que você digita (atualiza a cada letra) e a hotbar vira a fileira de **separados**. Pegue o item com o mouse e solte na fileira, como num baú comum (botão direito pega metade ou solta 1; Shift+clique manda o pacote direto para a fileira). O botão verde, Esc ou E levam os separados para a sua hotbar/inventário e voltam ao baú; a **seta** volta ao baú sem a pesquisa e sem os separados e o **X** limpa o texto (os separados continuam). Nada sai dos baús até confirmar, e o seu inventário de verdade nunca é tocado enquanto pesquisa.
  - **1.1.0** (26.2): o mod passa a se chamar **Baú Central** (itens antigos mudam de nome ao serem segurados), aceita até **512 baús ligados** (antes 64), a **tampa abre** enquanto alguém usa o baú, o texto de cada item mostra total, baús e **categoria**, e clicar no baú com comida, escudo ou pérola na mão não usa mais o item.
- **Desempenho**: os baús só são lidos enquanto alguém está com a tela aberta (1 vez por segundo) e nunca carregam chunks; baús ligados longe demais para estarem carregados aparecem como "longe demais agora".
- Os dados ficam em `<mundo>/smartchest.json`, fora do save do Minecraft. Sem o mod, sobra só uma obsidiana comum e os itens continuam nos baús.

## Item Despawn Timer

Mostra acima dos itens no chão quanto falta para sumirem (5 min). Itens dropados juntos no mesmo lugar (ex.: ao morrer) mostram um só cronômetro, com o tempo do primeiro a sumir. Amarelo no último minuto, vermelho nos últimos 10 s.

- Singleplayer/LAN (host): tempo exato, mesmo para itens que você deixou para trás e voltou para buscar.
- Servidor multiplayer: o servidor não informa a idade do item, então o cronômetro conta a partir de quando o item apareceu para você.

## Compilar

O GitHub Actions compila a cada push. Os `.jar` ficam em **Actions → último build → Artifacts**, um download por mod e versão do Minecraft (ex.: `mc26.2-tpaltar-1.0.0`; o de `mc26.1.2` serve para 26.1, 26.1.1 e 26.1.2). As versões ficam na matriz do `build.yml`; cada mod escolhe as suas com `ci_minecraft_versions` no `gradle.properties` (sem a linha: 26.3 e 26.2).

Localmente (Java 25): `./gradlew build` → `<pasta>/build/libs/`.

## Novo mod

Crie uma pasta `<mod>-<versão>/` com `build.gradle` e `gradle.properties` (copie do mobhealthbar). Ela entra no build automaticamente.
