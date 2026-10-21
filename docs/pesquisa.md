# Quem reclama de tablet — e o que a gente faz a respeito

Levantamento feito em **21/10/2026** no Galaxy Tab A 8.0 (SM-T290, Android 11, 2 GB de RAM, Exynos 7904),
com os números medidos no próprio aparelho (`dumpsys meminfo`, `dumpsys cpuinfo`, `batterystats`, `df`).

---

## 1. As reclamações plausíveis

São as que aparecem sempre em fórum, loja e boca a boca — e quase todas as deste tablet podem ser
**medidas**, não só acreditadas.

### 1.1. "Ficou lento depois da atualização"

A atualização não piora o aparelho: ela **acorda** serviços que dormiam. O caso clássico: o tablet que
"estava bom" começa a arrastar depois do Android 10/11 porque passaram a rodar em segundo plano
sincronização do Google, Samsung Cloud, Galaxy Store, Game Booster e os "assistentes".

Neste tablet: **525 processos** vivos ao mesmo tempo. Só o `system_server` (120 MB), o Google Play
Services (101 MB), a Play Store (109 MB com o processo de fundo dela) e o SystemUI (72 MB) somam
**400 MB** sem ninguém pedir nada.

### 1.2. "Fica travando /-sem memória"

Com 2 GB de RAM, o Android roda folgado só se o *bloat* estiver desligado. A medição aqui:

| | |
|---|---|
| RAM total | 1,89 GB |
| disponível | 479 MB |
| **swap em uso** | **1,17 GB** |
| `kswapd0` (o trocador de memória) | 9,3% da CPU, o tempo todo |

O `kswapd0` gastando 9% da CPU é o sintoma clássico: o sistema está **pagando cada página em disco**.
É por isso que "demora um segundo para abrir qualquer coisa" — a página nem está na memória.

### 1.3. "A tela de início tem um monte de coisa que eu não pedi"

O launcher de fábrica mostra relógio, clima, Samsung Members, Galaxy Store, Google Discover, um feed de
notícias e atalhos para o que a fabricante quer vender. Em cima disso, os apps de fábrica se anunciam
por notificação. O efeito: o Início é um **catálogo**, não um painel de trabalho.

### 1.4. "Chega notificação de tudo"

Cada app pré-instalado é um cliente a mais: Galaxy Store, Samsung Members, One UI Store, Google Play, Health,
Game Booster. No total, o Android permite notificações de qualquer app instalado — e o de fábrica
instala 302.

### 1.5. "A bateria acaba na metade do dia"

Causas, em ordem: tela sempre ligada com animação, serviço de fundo sincronizando, e o **driver de
Wi-Fi** (que neste tablet é o maior consumidor de CPU do aparelho inteiro: `cnss-daemon` com 64%, quase
tudo em kernel — o Wi-Fi fica varrendo o ambiente sem parar).

### 1.6. "Demora para abrir"

O launcher de fábrica em Android 11 leva centenas de milissegundos só para desenhar a primeira tela, e o processo do
launcher fica entre os mais pesados do aparelho (25 MB aqui, 46 MB em outros aparelhos) mesmo sem
nenhum app aberto.

### 1.7. "O armazenamento está cheio"

Aqui: **22 GB no total, 19 GB usados, 3,1 GB livres (86%)**. Não é o usuário: são os apps de fábrica
(o Play Store, o Samsung Internet, o Game Booster, o YouTube pré-instalado...) e o cache do sistema.

### 1.8. "Aparece propaganda na tela de bloqueio / notificação"

O One UI mostra uma tela cheia de anúncio da Samsung ao ligar a tela e notificação de "oferta". Nenhum app de
sistema que tenha esse trabalho é útil para quem usa o tablet para ler e escrever.

### 1.9. "Não acho meus apps"

A gaveta do launcher de fábrica é uma lista única e alfabética de tudo, com os apps de fábrica
misturados com os seus. Em 30 apps, achar o que você quer é rolagem.

### 1.10. "Pede permissão, conta, e começa a falar de mim"

Google e Samsung querem conta sincronizada; cada app pede notificação, localização e armazenamento.
Nada disso é preciso para ler um livro, escrever uma nota e rodar um comando.

### 1.11. "Esquenta"

CPU queimando 24h por dia (surfaceflinger + apps de fundo) em um SoC de 2019 aquece o aparelho e
encurta a vida da bateria.

### 1.12. "Depois de um tempo, a tablet fica lenta de novo"

Os apps pré-instalados **se reativam** sozinhos depois de uma atualização do sistema, porque a atualização
reabilita tudo que estava desabilitado. É por isso que o "desligar o bloat" precisa de um retrabalho
periódico — e é o que o *watchdog* do Free My TV faz na TV.

---

## 2. O que o Free My Tablet já resolve

| Reclamação | O que fazemos | Medido |
|---|---|---|
| 1.1 serviços de fábrica | 10 apps de fábrica desligados no modo Free My Tablet | 10 apps |
| 1.2 falta de memória | o Início nosso pesa 5,9 MB (o de fábrica, 25–46 MB) | **5,9 MB** |
| 1.3 Início cheio de coisa | só os apps que você escolheu, nada mais | 6 apps |
| 1.4 notificações | o app não pede permissão nenhuma, não registra nada | 0 permissões |
| 1.5 bateria | a tela **repousa**: nada de animação contínua | **0,0% de CPU** parado |
| 1.6 demora para abrir | 1,1 MB de APK, sem libs, sem WebView | **1,1 MB** |
| 1.7 armazenamento | nada é instalado, nada é oculto | 0 MB |
| 1.8 propaganda | não existe propaganda no app | — |
| 1.9 achar os apps | "Todos os apps" em uma lista limpa, e a fileira do jeito que você quer | — |
| 1.10 conta e permissão | nenhuma conta, nenhum rastreio | — |
| 1.11 esquenta | CPU a 0% quando a tela está parada | **0,0%** |
| 1.12 reativam sozinhos | o *watchdog* refaz a limpeza depois de reiniciar | — |

### A correção mais recente (e a mais importante)

O Início redesenhava a tela **60 vezes por segundo**, sem motivo: o fundo é um gradiente parado, mas o
código desenhava tudo de novo assim mesmo. Medido: **37% da CPU** com a tela da home à frente — contra
0,1% do launcher de fábrica. Num tablet de 2019, isso é calor e bateria.

Agora a tela é desenhada **quando há algo novo**: o relógio (uma vez por minuto), uma mensagem
desvanecendo, e nada mais. Parado: **0,0% de CPU**.

---

## 3. A receita: como fazer um tablet consumes menos

Vale para qualquer tablet barato, não só para este.

**1. Desligue o que é de fábrica e você não usa.** Não desinstale — *desligue*
(`pm disable-user`): o app some da tela, a RAM e a CPU voltam, e um botão traz tudo de volta.
No Galaxy Tab A 8.0, o que estava de fábrica e não serve para nada: Galaxy Store, Galaxy Themes,
Samsung Clock (widget), Sticker Center, Smart Switch, Chrome Customizations, Customization Service,
User Manual, Service Mode, factory keystring. (Nem a Play Store nem o navegador entram na lista:
esses você usa.)

**2. Um Início que não desenha o tempo todo.** A regra de ouro: *se nada mudou, não redesenhe*. Relógio
uma vez por minuto, animação só quando o dedo mexe, e o resto parado. Uma tela inicial que fica a 60 fps
consome mais que o navegador aberto.

**3. Um app pequeno, sem bibliotecas.** 1,1 MB de APK, Java puro, Android SDK pura. Nada de AndroidX,
nada de WebView, nada de fonte remota. O custo de memória de um app de Início é quase todo do
*framework* que ele carrega (e o launcher de fábrica carrega o Launcher's AppViews, o People, o S Pen...).

**4. Nada de conta, nada de rastreio, nada de propaganda.** Sem servidor para chamar, sem notificação
para pedir, sem analytics. Um app assim pode ficar dias sem falar com a rede — e a bateria dura mais.

**5. Um botão para voltar.** Desligar app é reversível (o app guarda a lista e religa). Nada de
desinstalar, nada de mexer no sistema: o *watchdog* devolve o Início original se a tela nova cair 3
vezes, e a conferência depois de reiniciar religa o que o sistema turned on.

**6. Meça, não acredite.** `dumpsys meminfo` (memória), `dumpsys cpuinfo` (processador),
`batterystats` (bateria), `df` (armazenamento). Toda afirmação deste documento tem número atrás.

---

## 4. O que dá para fazer a mais

- **Widgets** — o que falta de verdade: relógio grande, o tempo, um atalho para a nota. Um widget é
  também um jeito de a pessoa colocar no Início a informação que ela usa (a próxima aula, a última
  nota do Obsidian) sem passar por app nenhum.
- **Um modo "leitura"** — a tela escura, sem nada além do essencial, para ler de madrugada.
- **Economia de dados** — nada de tela de bloqueio com foto, nada de fundo animado.
- **Um painel de "o que meu tablet gasta"** — mostrar no próprio app onde vai a memória e a bateria.
