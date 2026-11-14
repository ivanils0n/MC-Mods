# MC-Mods

Mods Fabric para Minecraft. Cada pasta `<mod>-<versão>/` é um mod independente.

| Mod | Pasta | Minecraft |
|-----|-------|-----------|
| Mob Health Bar | `mobhealthbar-1.2.1/` | 26.2 e 26.3+ (Fabric, só cliente) |
| Item Despawn Timer | `itemtimer-1.0.0/` | 26.2 e 26.3+ (Fabric, só cliente) |
| Altar de Teleporte | `tpaltar-1.0.0/` | 26.2 e 26.3+ (Fabric, servidor ou singleplayer, requer Fabric API) |
| Baú Central | `smartchest-1.1.0/` | 26.2 (Fabric, servidor ou singleplayer, requer Fabric API) |
| Golem de Cobre Inteligente | `coppergolem-1.0.0/` | 26.2 (Fabric, servidor ou singleplayer, requer Fabric API) |
| Autômato | `automato-1.0.0/` | 26.2 (Fabric, servidor ou singleplayer, requer Fabric API) |
| Mochilas | `mochilas-1.0.0/` | 26.2 (Fabric, servidor ou singleplayer, requer Fabric API; Polymer e SGUI já vêm dentro) |

## Releases

Cada mod e versão tem um release `<mod_id>-<versão>` (ex.: `tpaltar-1.0.0`) com o .jar de cada versão do Minecraft e, se o mod tem resource pack, o `<mod_id>-pack-<sha1>.zip`. O CI cria tudo sozinho a cada push na `main`.

Mod novo: pasta `<mod>-<versão>/` com `build.gradle` e `gradle.properties` (`mod_id`, `mod_version`). Com resource pack (`src/pack/`), o link no código é `releases/download/<mod_id>-` + versão do mod (sem o `+mc...`) + `/<mod_id>-pack-<sha1>.zip`, como em `TpAltar.modVersion()`.

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
  - **1.1.0** (26.2): a primeira fileira da tela tem abas: um encaixe de **filtro pelo item** (clique nele com um item na mão para ver só itens desse tipo; sem item, tira o filtro) e 8 categorias, baseadas nas abas do modo criativo do próprio jogo: Todos, Construção (inclui madeira, troncos e blocos funcionais como bancada, fornalha e baús), Armaduras (de jogador, cavalo, lobo...), Minérios (minério, minério bruto, lingotes, gemas e seus blocos), Redstone (o que dá ou recebe sinal: pistão, comparador, observador, ejetor, funil...), Ferramentas e combate, Comidas, sementes e poções (inclui mudas e plantas) e Ingredientes e outros. A lista mostra 36 itens por página. Aba e filtro voltam para "Todos" ao abrir o baú de novo. Passando o mouse num item aparecem o total e em quantos baús ele está (o número no item vai até 99). Os ícones das abas são os itens do próprio jogo (seguem o resource pack do jogador) e a aba ativa brilha. A ordem padrão é por nome. A rodinha do **Mouse Tweaks** tira itens (precisa de um espaço vazio no inventário; o item junta com o monte que já existe). No encaixe do filtro, as teclas 1-9 filtram pelo item daquele espaço da hotbar.
  - **1.1.0** (26.2): **pesquisa** com fundo "Céu do End". O inventário da tela vira parte do baú: os 27 espaços mostram os itens que batem com o que você digita (atualiza a cada letra) e a hotbar vira a fileira de **separados**. Pegue o item com o mouse e solte na fileira, como num baú comum (botão direito pega metade ou solta 1; Shift+clique manda o pacote direto para a fileira). O botão verde, Esc ou E levam os separados para a sua hotbar/inventário e voltam ao baú; a **seta** volta ao baú sem a pesquisa e sem os separados e o **X** limpa o texto (os separados continuam). Nada sai dos baús até confirmar, e o seu inventário de verdade nunca é tocado enquanto pesquisa.
  - **1.1.0** (26.2): **tipo de baú**: com o Vinculador, o **botão esquerdo** num baú abre uma fileira para escolher o tipo (liga o baú, se ainda não estiver ligado, ou troca o tipo): **qualquer item** (o normal, igual ao botão direito), **uma categoria** (os itens dela vão primeiro para esses baús, e nenhum outro item entra neles; se encherem, o resto vai para os baús de qualquer item) ou **só retirar** (você põe itens nele à mão; o Baú Central mostra, pesquisa e tira dele, mas nunca guarda nem organiza nada ali). O botão direito continua ligando para qualquer item e desligando.
  - **1.1.0** (26.2): botão **Organizar baús** (na barra de baixo): junta as pilhas e separa os itens por categoria, na ordem das abas, cada categoria começando num baú próprio; se não houver baús para todas, ficam em sequência.
  - **1.1.0** (26.2): **mesa de trabalho ligada**: com o Vinculador, clique numa mesa de trabalho a até 33 blocos do Baú Central (clique de novo para desligar). Ela ganha textura própria. Ao abrir, aparece uma pesquisa (a mesma tela de digitar do Baú Central) com tudo o que dá para craftar com os itens dos baús ligados e do seu inventário (passe o mouse para ver os ingredientes). Clicar num item abre a mesa, com tela tematizada, já com a receita montada com o máximo que os itens permitem (primeiro os dos baús, faltando, os do inventário); quando a grade acaba, ela é montada de novo sozinha, então dá para craftar quantos quiser. A grade é da mesa: não dá para pôr nem tirar itens dela, só pegar o que foi craftado, e ao fechar o que sobrou volta direto para os baús. O livro de receitas fica fechado: no lugar do botão dele há uma seta que volta para a pesquisa (o livro das outras mesas continua como o jogador deixou). Baús fora da distância de visão não entram, e aparece um aviso ao abrir a mesa.
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

## Golem de Cobre Inteligente

O golem de cobre do jogo passa a trabalhar sozinho entre baús que você liga com o **Cajado do Golem** (receita: 3 barras de cobre, 1 bloco de cobre e 1 palito).

- Botão direito no golem com o cajado: liga o cajado e abre a tela de vínculos. Depois clique em baús ou barris (abre uma tela para escolher **origem**, **guardar** ou desligar).
- **Baú de cobre** = recarga: guarde barras de cobre; quando o golem enferruja, ele vai lá e usa 1 barra por nível de ferrugem (como uma passada de machado). Também dá para clicar no golem com uma barra de cobre na mão. As barras também raspam a ferrugem do próprio baú de cobre. O baú abre a tampa quando o golem mexe nele.
- **Shift + botão direito** no golem com o cajado: guarda o golem no inventário (com ferrugem, nome e baús vinculados). Botão direito no chão com ele: solta o mesmo golem.
- Ligue primeiro o baú de cobre: origem e guardar só até **33 blocos** dele (sem limite de quantidade).
- **Origem**: de onde ele pega itens. **Guardar**: para onde ele leva. Moldura com um item num baú de guardar: só entra esse item; sem moldura, qualquer item, agrupando por categoria do criativo.
- Textura por resource pack (enviado a servidores dedicados), mesmo esquema dos outros mods. Dados em `<mundo>/coppergolem.json`.

## Autômato

Um bloco que usa as ferramentas guardadas nele como um jogador parado na frente dele. Vai na pasta `mods/` do servidor, junto com o Fabric API; os jogadores entram sem instalar nada. Em singleplayer, coloque o mesmo .jar na pasta `mods/` do jogo.

- **Receita** (bancada): barra de ferro, observador, barra de ferro em cima; barra de ferro, ejetor, barra de ferro no meio; barra de ferro, bloco de redstone, barra de ferro embaixo. Aparece no livro de receitas ao pegar um ejetor.
- **Colocar**: a frente fica virada para onde você olha (também para cima ou para baixo).
- **Tela** (clique no bloco): a fileira de cima tem 9 espaços que só aceitam ferramentas e armas (à mão ou por funil). A fileira de baixo escolhe o **alcance da quebra**, de 1 a 5 blocos à frente (começa em 1); com uma arma em uso, os mesmos botões escolhem a **espera do golpe**, de 1 a 5 segundos (começa em 1).
- **Ligar**: redstone ou alavanca direto nele. Ligado, ele usa a primeira ferramenta dos 9 espaços; quando ela quebra, passa para a próxima.
  - **Picareta, pá, enxada, tesoura**: quebram o primeiro bloco à frente dentro do alcance, na velocidade de um jogador com essa ferramenta (eficiência conta), com a rachadura aparecendo e 5 ticks de pausa entre blocos. Fortuna e toque suave valem; os drops caem no chão.
  - **Espada, maça, tridente, lança**: golpeiam o mob à frente (até 3 blocos, como o jogador): com um mob à frente, esperam o tempo escolhido (1 a 5 s) e, se ele ainda estiver lá, golpeiam; a espera recomeça a cada golpe (e nunca é menor que a recarga da arma). Golpe com a barra cheia, com encantamentos.
  - **Machado**: golpeia se houver mob à frente (com a mesma espera da arma); se não, corta o bloco dentro do alcance.
  - **Encantamentos** da ferramenta valem como num jogador: Eficiência, Fortuna, Toque Suave, Inquebrável, Afiação, Aspecto Flamejante, Repulsão, Corte Arrebatador e Pilhagem. Com Remendo, o XP coletado conserta a ferramenta antes de ir para o pote.
  - Gasta a durabilidade como um jogador. Não bate em jogadores nem em suportes de armadura. O alcance do golpe não passa de paredes.
- **XP**: as bolinhas de XP que aparecem à frente dele (dos mobs que ele mata e dos blocos que ele quebra) vão para o pote da tela, até o equivalente a 100 níveis (cheio, ficam no chão). O pote mostra quantos níveis tem (os pontos exatos aparecem ao passar o mouse) e o botão **Recolher** passa todo o XP para você.
- **Quebrar**: as ferramentas guardadas caem no chão e, com picareta, o Autômato volta como item.
- **Visual (Pedra e Carvalho)**: bloco de tijolos de pedra com relevo e boca em arco; ligado, a brasa no fundo tremula. A ferramenta em uso aparece de verdade saindo do arco (com o material e o brilho de encantamento dela) e dá uma batida a cada golpe ou enquanto cava. A tela ganha fundo próprio e as placas de alcance numeradas. Vem dentro do .jar (singleplayer) e, no servidor dedicado, o próprio mod envia o resource pack ao jogador. Quem recusar vê um ejetor comum, a tela de baú comum e flechas no lugar das placas (o número é a quantidade). As texturas são geradas por `arte/final.py`.
- Por baixo é um ejetor de verdade: os dados ficam em `<mundo>/automato.json`, fora do save do Minecraft, e o visual nunca é salvo no mapa. Sem o mod, sobra um ejetor comum com as ferramentas dentro.

## Mochilas

Mochilas de couro (9 espaços), ferro (18), ouro (27) e diamante (54), com inventário próprio, que aparecem em 3D nas costas do jogador para todo mundo. Só no servidor: os jogadores entram com o Minecraft sem mods.

- **Instalar (servidor)**: coloque o `.jar` em `mods/` junto com o Fabric API (o Polymer e o SGUI já vêm dentro do .jar). Na primeira vez o mod liga o envio automático do resource pack do Polymer (`config/polymer/auto-host.json`, `"enabled": true`): o pack é montado ao iniciar o servidor e enviado pela mesma porta do jogo a quem entra. Atrás de proxy/Docker com outro endereço, preencha `"forced_address"` nesse arquivo (ex.: `"http://seu.servidor:25565"`).
- **Instalar (singleplayer)**: o mesmo `.jar` + Fabric API em `mods/` do jogo. As texturas carregam direto, sem resource pack.
- **Resource pack**: é gerado sozinho a cada início do servidor. Para atualizar depois de trocar texturas, reinicie o servidor. O arquivo fica em `polymer/resource_pack.zip`. As texturas e modelos são gerados por `mochilas-1.0.0/arte/gerar.py` (`python3 arte/gerar.py`).
- **Receitas** (bancada):
  - Couro: couro, linha, couro / couro, baú, couro / couro, couro, couro.
  - Melhorias (mochila + 1 bloco em qualquer lugar da grade): couro + bloco de ferro = ferro; ferro + bloco de ouro = ouro; ouro + bloco de diamante = diamante. Os itens guardados e o nome dado na bigorna continuam.
- **Usar**: segure a mochila e clique com o botão direito para colocá-la nas costas (não ocupa o peitoral; se já houver uma, as duas trocam). `/backpack` (ou `/mochila`) abre, `/backpack unequip` tira e devolve ao inventário com os itens dentro, `/backpack info` mostra nível, espaços usados e ID.
- **Admin** (op nível 2): `/backpack give <jogador> <couro|ferro|ouro|diamante>`, `/backpack reload` (relê `config/mochilas.json`).
- **Tela**: igual à de um baú, com exatamente a capacidade do nível. Shift-clique, arrastar e teclas numéricas funcionam; tudo é validado no servidor. Mochilas não entram em mochilas. Uma mochila só pode estar aberta por uma pessoa de cada vez.
- **Config** (`config/mochilas.json`): `capacidades` (múltiplos de 9, de 9 a 54; se diminuir, o que passar do limite volta para o jogador ao abrir), `soltar_ao_morrer` (padrão `true`: a mochila equipada cai no chão ao morrer, com os itens; com keepInventory ela fica) e `ativar_autohost`.
- **Onde fica salvo**: cada mochila tem um ID próprio (no item) e o inventário fica em `<mundo>/mochilas/<id>.dat`, gravado só quando muda. A mochila equipada fica salva nos dados do jogador. Cópias de um mesmo item (criativo) abrem o mesmo inventário, então não duplicam itens.
- **Visual**: um `item_display` virtual (Polymer Virtual Entity) montado no jogador; o cliente o carrega junto, sem pacotes de posição. O servidor só manda a rotação quando o corpo vira ou a pose muda (agachar, nadar, planar), e some com invisibilidade, espectador ou dormindo. O próprio dono recebe uma cópia marcada com uma cor quase branca; os shaders `item` e `entity` do resource pack (`assets/minecraft/shaders/core`, cópias dos do 26.2 com um trecho a mais) escondem essa cópia quando a câmera está a menos de 1,6 bloco: na 1ª pessoa a mochila some da tela e, ao apertar F5 (3ª pessoa), aparece de novo. Os outros jogadores sempre a veem. Com shaders do Iris a cópia do dono aparece também na 1ª pessoa. Na montagem do resource pack, os arquivos das mochilas (texturas, modelos e os shaders `item`/`entity`) são gravados por último, por cima dos de qualquer outro mod Polymer; o log avisa quando outro mod tentou trocar algum deles.

