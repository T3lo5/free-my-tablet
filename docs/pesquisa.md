# Quem reclama de tablet — e o que a gente faz a respeito

Levantamento em **21/10/2026**: o que a internet reclama (verificado nas fontes), cruzado com o que foi medido
no Galaxy Tab A 8.0 (SM-T290, Android 11, 2 GB de RAM, Exynos 7904) — `dumpsys meminfo`, `dumpsys cpuinfo`,
`dumpsys diskstats`, `dumpsys notification` e `batterystats`.

---

## 1. O que a internet reclama (verificado nas fontes)

### As 8 reclamações canônicas (janeiro de 2026)

A lista que aparece em todo resumo de "o que mais irrita no Android" — e **todas as oito existem no seu
tablet**:

| # | A reclamação | No seu tablet |
|---|---|---|
| 1 | Atualizações lentas (dependem da marca) | Android 11 de 2023 numa máquina de 2019: a última atualização, e nenhuma depois |
| 2 | **Bloatware pré-instalado** | 302 apps de fábrica; 1,7 GB só do diagnóstico da Samsung |
| 3 | **Bateria acabando rápido** | `cnss-daemon` (driver de Wi-Fi) consumindo 64% da CPU, sozinho |
| 4 | **"Armazenamento cheio"** | **só 14% livres** (3,3 GB de 23,5 GB) — a dica geral é liberar 10 a 15% |
| 5 | **Propaganda em apps do sistema** | Play Store, Google, Galaxy Store, Samsung Weather: 24 notificações de fábrica |
| 6 | Interfaces diferentes demais | One UI Home e o nosso Início, com settings diferentes |
| 7 | **Esquenta** | CPU queimando o dia inteiro (surfaceflinger + apps de fundo) |
| 8 | **Notificação demais** | 70 notificações no histórico, a maioria de apps que você não pediu |

### O que a_população tenta para consertar (10 dicas do Asurion, Lifewire, Answered.io)

1. Fechar o que está aberto (memória).
2. **Liberar 10–15% do armazenamento.**
3. Modo de economia de bateria (só que ele *atrasa* o aparelho).
4. Reiniciar.
5. Conferir o Wi-Fi.
6. Atualizar o sistema.
7. Atualizar os apps.
8. Revisar os apps que rodam em segundo plano.
9. Baixar a resolução do vídeo.
10. Reinstalar / trocar de tablet (o último recurso).

Duas dessas o **launcher** pode fazer por você, e nenhuma delas é "reinstalar":

- **Liberar 10–15% do espaço** → o app mede o que ocupa o tablet e limpa o cache de todos os apps.
- **Menos apps de fundo** → desligando os de fábrica (o que já fazemos).

### A onda em que a gente está

Em outubro de 2026 o **TechCrunch** publicou *"People are using AI to make their smart TVs dumb again"*: um
montão de pessoas apontando o Claude Code para a TV, tirando propaganda, telemetria e o launcher pesado
(`dumbtv`, `cleanyourtv`, `tv-vibehack`...). O lance da conversa: o **launcher leve** e o "botão que desfaz
tudo" viraram o que a galera procura. O Free My TV é dessa onda; **o Free My Tablet é a versão para quem
trabalha, lê e escreve** — e é a primeira que vai para tablet.

---

## 2. O que o seu tablet mostra (medido)

| | |
|---|---|
| Processos vivos | **525** |
| RAM (1,89 GB) | 479 MB disponíveis, **1,17 GB em swap** |
| `kswapd0` (o trocador de página) | 9,3% da CPU, o tempo todo |
| `cnss-daemon` (driver de Wi-Fi) | **64% da CPU** |
| Dados dos apps | 10,6 GB |
| **Cache dos apps** | **2,6 GB** |
| Armazenamento livre | 3,3 GB de 23,5 GB (**14%**) |
| Apps de fábrica | 302 |

**Os que mais ocupam espaço:**

| App | MB | O que é |
|---|---|---|
| YouCine | 3.473 | o app de IPTV (do projeto da TV) |
| YouTube | 2.247 | 1 GB de cache de vídeo |
| **com.sec.android.diagmonagent** | **1.692** | **o diagnóstico da Samsung: registra tudo o que você faz** |
| Chrome | 1.032 | 462 MB de cache |
| Play Services | 772 | |
| **Google (quicksearchbox)** | **687** | o app do Google, com o feed de notícias |
| **OneDrive** | **629** | pré-instalado, nunca usado |
| **Google TTS** | **560** | o motor de texto para voz, que ninguém pediu |
| Samsung Notes | 428 | |
| Z-Lib | 292 | |
| **Samsung Cloud** | **217** | desligado por nós, mas os dados continuam lá |
| Termux | 199 | o seu |
| **Google Maps** | **185** | pré-instalado, nunca usado |

**Quem manda notificação:** Gmail (14), YouTube (10), Samsung Calendar (9), **Samsung Weather (8)**, **Google (8)**,
**Play Store (7)**, OneDrive (4), Galaxy Store (1).

---

## 3. O que mudou por causa disso (e o que é seu)

### Melhorar

- **Tela de "Espaço"** (novo): mostra quanto está livre, os 8 apps que mais ocupam o tablet, e dois botões:
  **limpar o cache de todos** (2,6 GB, sem perder nada seu) e **limpar os apps desligados** (o OneDrive, o
  Samsung Cloud, o TTS...).
- **Tela de "Quem fala com você"** (novo): mede as notificações do tablet e lista quem mais fala — um toque
  leva direto à tela do sistema para desligar aquele app. (O Android não deixa um app calar outro: só você
  pode — por isso a app te leva lá em vez de fingir que resolve.)
- **O diagnóstico da Samsung** entrou na lista de fábrica desligada: 1,7 GB e é ele que reporta o uso do
  tablet.
- **TTS, Maps, Drive e Samsung Weather** entraram na lista de opcionais.

### Tirar

- **O Início não é mais um grid.** É o relógio, o tempo, os seus apps e a nota. O resto fica a um botão.
- **Nada de 60 fps.** A tela de Início descansa (0,0% de CPU parado, contra 37% quando redesenhava sem parar).
- **Nada de notificação, permissão ou conta.** O app não pede nada disso.

### Adicionar

- **A nota rápida** (a sua vai pro vault do Obsidian) e o **widget** das últimas notas.
- **O sync** (o `git pull` e a cópia, num botão).
- **A tela de "Apps para desligar"** mostra *todos* os opcionais do tablet (inclusive os que não têm ícone) e
  **aplica na hora**.
- **O tema** (automático/claro/escuro) e o **card do tempo** (que dá para desligar).

### O que continua aberto

| | |
|---|---|
| **O driver de Wi-Fi** (`cnss-daemon`, 64% da CPU) | Não é nosso: é o firmware do Exynos 7900. Dá para reduzir o varrer do ambiente nas Configurações do sistema, mas o app não mexe nisso. |
| **O swap de 1,17 GB** | É consequência da pressão de memória: some quando o *bloat* é desligado e o tablet é reiniciado. |
| **YouCine com 3,4 GB** | É seu app; o app não mexe no que você usa. |
| **Notificações do sistema** | Android não deixa um app calar outro: a app te leva à tela do sistema (1 toque). |
| **Atualizar o Android** | A Samsung parou no Android 11 para esse modelo. Não há o que fazer — nem pelo launcher. |

---

## 4. A receita (para qualquer tablet barato)

1. **Desligue o que é de fábrica e você não usa** — não desinstale, *desligue* (`pm disable-user`): o app
   some da tela, a RAM e a CPU voltam, e um botão traz tudo de volta.
2. **Um Início que não desenha o tempo todo**: *se nada mudou, não redesenhe*. Relógio uma vez por minuto,
   animação só quando o dedo mexe. Um launcher a 60 fps consome mais que o navegador aberto.
3. **Um app pequeno, sem bibliotecas**: 1,9 MB de APK, Java puro, Android SDK pura. Nada de AndroidX, nada de
   WebView, nada de fonte remota. O custo de memória de um launcher é quase todo do framework que ele carrega
   (o de fábrica: 25–46 MB; o nosso: 7 MB).
4. **Nada de conta, nada de rastreio, nada de propaganda.**
5. **Um botão para voltar.** Nada de desinstalar: o watchdog devolve o Início original se a tela nova cair 3
   vezes, e a conferência depois de reiniciar religa o que o sistema ligou.
6. **Meça, não acredite.** `dumpsys meminfo`, `dumpsys cpuinfo`, `dumpsys diskstats`,
   `dumpsys notification`, `batterystats`, `df`. Cada número deste documento tem um comando por trás.

---

## 5. As fontes

- [TechCrunch — People are using AI to make their smart TVs dumb again (19/10/2026)](https://techcrunch.com/2026/10/19/people-are-using-ai-to-make-their-smart-tvs-dumb-again/)
- [What Annoys Android Users the Most? (Top Complaints in 2026)](https://www.janamana.in/2026/03/what-annoys-android-users-most-top.html)
- [Asurion — 10 ways to fix an Android tablet that's running slow or freezing](https://www.asurion.com/connect/tech-tips/fix-slow-android-tablet/)
- [Answered.io — 16 Ways to Speed Up Your Slow Android Tablet](https://unanswered.io/guide/speed-up-slow-android-tablet)
- [Lifewire — How to Fix It When an Android Tablet Is Running Slow or Freezing](https://www.lifewire.com/fix-android-tablet-slow-or-freezing-5224958)
- [Android Authority — Fed up with ads and bloatware on your smart TV](https://www.androidauthority.com/android-tv-vibe-hacking)
- [dumbtv](https://github.com/MuzzammilYasin/dumbtv) e [cleanyourtv](https://github.com/Gathin23/cleanyourtv) — a mesma onda, para TVs
