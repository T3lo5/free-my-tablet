# Os problemas: resolvidos, possíveis, impossíveis e o que não faz sentido

Levantamento de 21/10/2026 no Galaxy Tab A 8.0 (SM-T290, Android 11, 2 GB de RAM, Exynos 7904), a partir
da pesquisa online (as fontes no fim) e do que foi medido no aparelho (`dumpsys meminfo`, `dumpsys cpuinfo`,
`dumpsys diskstats`, `dumpsys notification`, `batterystats`, `df`).

A ideia desta lista é não repetir a mesma briga: o que já está resolvido não entra no plano, o que é
impossível não vira promessa, e o que não faz sentido para um launcher leve fica de fora.

---

## 1. Já resolvidos

| # | O problema | Como ficou | Medido |
|---|---|---|---|
| 1 | **A tela de Início redesenhava 60 vezes por segundo** para mostrar um gradiente parado | A tela só é desenhada quando há coisa nova: o relógio (1x por minuto), uma mensagem, uma animação | **37% → 0,0% de CPU** |
| 2 | **A tela de Início era um grid com tudo** | Ficou o relógio, o tempo, os apps que a pessoa escolheu, a nota e um botão | 6 apps em vez de 30+ |
| 3 | **302 apps de fábrica**, um deles com 1,7 GB (o diagnóstico da Samsung) | O switch desliga 10 de fábrica e deixa 12 opcionais à escolha | 10 apps desligados |
| 4 | **Não tinha como escrever uma nota** sem abrir o Obsidian | A nota vai direto pro vault, em Markdown, um arquivo por dia | Salva e acumula |
| 5 | **O git pull + a cópia eram na mão, no Termux** | Um botão faz os dois | Testado: "Already up to date" + cópia |
| 6 | **Não tinha widget** | O slot do Início hospeda qualquer widget do sistema; o nosso mostra as últimas notas do vault | Funcionando |
| 7 | **O cache e os dados dos apps desligados ficavam ocupando espaço** | A tela de Espaço limpa o cache de todos e o que sobrou dos desligados | **3,2 GB → 5,8 GB livres** |
| 8 | **Notificação de propaganda** (Play Store, Google, Galaxy Store, Weather) | A tela "Quem fala com você" mede e leva direto à tela do sistema | 24 notifs de fábrica |
| 9 | **A versão do app era fixa no manifesto** | Vem da tag do build (1.7 → código 107) | O app sabe se há versão nova |
| 10 | **Atualizar era baixar o APK do repo à mão** | O app procura sozinho e baixa; o instalador do sistema confirma | Download + instalação testados |
| 11 | **O widget cobria a gaveta de apps** (não dava para clicar em cima dele) | Widget e campo de nota se afastam enquanto a gaveta está aberta | Corrigido e testado |
| 12 | **O botão Voltar tirava da tela de Início** | Uma tela de Início nunca sai: o Voltar fecha a gaveta e pronto | Corrigido |
| 13 | **O ícone era o da TV** | Ícone próprio: um tablet com o mesmo brilho e o mesmo gradiente | — |
| 14 | **A nota não salvava** (URI da pasta em vez do documento, e um modo de arquivo que não existe) | Salva e aparece no vault | Testado no teu vault |
| 15 | **A tela de configurações não aceitava toque nenhum** (todo clique virava rolagem) | O gesto foi consertado e a rolagem tem limite | Corrigido |
| 16 | **"Apps para desligar" só mostrava os que têm ícone** | Mostra todos os opcionais do tablet, inclusive serviços | 12 opcionais listados |
| 17 | **Marcar um app não desligava nada na hora** | Desliga na hora, pelo ADB interno | Testado |
| 18 | **Não tinha tema claro/escuro de verdade** (o ajuste existia mas não mudava nada) | Automático (segue o sol), claro e escuro | Testado |
| 19 | **Não tinha previsão do tempo** | Cartão com temperatura, símbolo e cidade, com interruptor | 23°, Londrina |
| 20 | **O aviso da atualização mostrava o JSON cru** | Mostra o texto, e o changelog sem as marcas do markdown | Corrigido |
| 21 | **As linhas das configurações quebravam por índice** (acrescentar uma linha deslocava os valores) | A ordem vive em um lugar só | Corrigido |
| 22 | **O botão de voltar da gaveta saía do app** | Consome o gesto | Corrigido |
| 23 | **Os caminhos das notas do vault saíam errados** (o app pedia o id da árvore a um documento) | A árvore e o id andam juntos: os caminhos são certos e as subpastas aparecem | Corrigido e testado |
| 24 | **A nota ia para a raiz do vault, ignorando a pasta escolhida** | A nota vai para a pasta (que é criada se não existir) | Testado no teu vault |
| 25 | **A busca no vault travava a tela** (a varredura das pastas é lenta) | A busca abre na hora e as notas aparecem enquanto são achadas | Testado |
| 26 | **O aviso da tela de Início voltava toda vez** | Ele é mostrado uma vez | Corrigido |
| 27 | **O tema claro era um céu azul com texto branco** | O tema claro é papel quente, com a tinta escura em cima | Testado |

---

## 2. Possíveis de resolver (e vale a pena)

| # | O problema | Como resolver | Esforço |
|---|---|---|---|
| 1 | ~~**A gaveta de apps é uma lista só** — achar um app amongst 30 é rolagem~~ | **Feito na v1.9**: a busca no topo da lista filtra enquanto se digita | — |
| 2 | ~~**Não tem "usados recentemente"** — o que você abriu ontem some~~ | **Feito na v1.9** (os abertos pelo launcher): uma fileira no topo da gaveta | — |
| 3 | **O widget não pode ser redimensionado** | O host do widget pede o resize quando a pessoa arrasta | pequeno |
| 3b | ~~**A nota vai sempre para a raiz do vault**~~ | **Feito na v1.9/v1.10**: a pasta da nota é escolhida (e criada se não existir) | — |
| 4 | ~~**A nota vai sempre para a raiz do vault**~~ | **Feito na v1.9** (a pasta da nota) | — |
| 5 | ~~**Não dá para buscar dentro do vault**~~ | **Feito na v1.9**: a busca acha a nota e abre no Obsidian | — |
| 6 | **A tela de Espaço mostra só 8 apps** | Mostrar todos, ordenados, e o que dá para liberar | pequeno |
| 7 | **O "Espaço" não mostra a memória e a CPU** | Mostrar a memória usada, a da CPU e quem está usando | médio |
| 8 | ~~**Não tem um painel do tablet** (o que está comendo a bateria)~~ | **Feito na v1.8**: a linha *Tablet* mostra memória, bateria, processos, quem usa a CPU e quem usa a memória | — |
| 9 | ~~**Não dá para exportar as suas escolhas** (apps da tela, dock, pastas)~~ | **Feito na v1.9**: salvar (e ler de volta) no vault | — |
| 10 | **Widgets de outro app que querem configuração própria não abrem** (a tela de configuração do outro app não é exportada) | Abrir a configuração pelo sistema em vez de pelo nosso app | pequeno |
| 11 | **O diretório de armazenamento quase cheio (14%)** | A tela de Espaço já limpa o cache; falta limpar cache de app em uso (o sistema não deixa) | médio |
| 12 | **Não tem um "modo leitura"** (a tela escura, só o essencial, para ler de madrugada) | Um tema noturno total, com o relógio pequeno | pequeno |
| 13 | **Não tem pastas/áreas de trabalho** (hoje é uma fileira só) | Áreas (ex.: estudo, trabalho, leitura) que trocam a fileira | médio |
| 14 | **O app não sabe o que você quer abrir** (a "nota do dia", a aula) | Atalhos para o Obsidian com um arquivo fixo ("a nota de hoje") | pequeno |
| 15 | **A busca no vault só acha pelo nome do arquivo** | Ler o conteúdo dos .md e procurar dentro (mais lento: o SAF pergunta arquivo por arquivo) | médio |

---

## 3. Impossíveis (ou fora do alcance de um app)

| # | O problema | Por que não dá |
|---|---|---|
| 1 | **O Android deste modelo parou no 11** (mar/2023) | A Samsung que decide. Não existe atualização para o SM-T290 |
| 2 | **O driver de Wi-Fi consome 64% da CPU** (`cnss-daemon`) | É firmware do Exynos 7900, não é app nenhum. Dá para reduzir o varrer do ambiente nas Configurações do sistema, mas o app não mexe nisso |
| 3 | **O Google Play Services (101 MB) e o Google tem que existir** | Desligar quebra a Play Store, o Gmail e o login. O app não pode fazer isso por você |
| 4 | **Apagar de vez os apps de fábrica** (o OneDrive, o Samsung Notes) | Desligar preserva o app; desinstalar apaga os dados. A decisão é da pessoa — e a app só desliga |
| 5 | **A verificação do Google Play Protect** | É política do Google. A pessoa pode desligar nas Configurações da Play Store (e a app avisa isso na tela de atualização) |
| 6 | **O 1,17 GB em swap** | Consequência da pressão de memória: some quando o bloat sai e o tablet reinicia. Nenhum app "limpa" swap |
| 7 | **A tela de bloqueio com propaganda da Samsung** | É do sistema; a app não pode substituí-la sem ser o "launcher" de tela de bloqueio (e aí ela vira outra coisa) |
| 8 | **O "Modo seguro" da Samsung** (a pasta protegida) | É do sistema de arquivos, não há API |
| 9 | **Desbloquear o bootloader** | O SM-T290 não tem bootloader desbloqueável. E root não é o caminho do projeto |
| 10 | **Mais RAM ou CPU** | Hardware. A app só pode deixar o aparelho com menos coisa rodando |
| 11 | **Aceleração de GPU** (o Android 12+ traz a "Game Booster" nativa) | Não existe no Android 11 deste modelo |
| 12 | **Criar conta no Google/Samsung** | A app não quer conta nenhuma — essa é a proposta |

---

## 4. Que não fazem sentido (para um launcher leve)

| # | A ideia | Por que não |
|---|---|---|
| 1 | **Uma gaveta de apps completa** igual à do sistema | Já existe na do sistema, e ela é lenta. A nossa é uma lista simples, e o botão leva à do sistema se preciso |
| 2 | **Um gerenciador de apps / instalar e desinstalar** | O sistema tem (e a Play Store). A app não deve virar loja |
| 3 | **Reproduzir o launcher do Google ou o One UI** | O ponto é ser o contrário: leve, sem feed, sem anúncio |
| 4 | **Notificação própria do app** (push, "novo conteúdo") | A app não tem servidor, conta nem rastreio. Notificação própria seria exatamente o que a gente critica |
| 5 | **Sincronização na nuvem do app** | A sincronização é o **git** da pessoa, não nosso. O app só dá o botão |
| 6 | **Conta do usuário, login, perfil** | Nenhuma conta, nenhum dado que saia do tablet |
| 7 | **Refazer os ícones de todos os apps em cache** (o Android 13 faz isso) | A app já guarda os seus; os de terceiros são do sistema |
| 8 | **Uma tela de bloqueio própria** | O sistema já tem, e a Samsung põe propaganda nela |
| 9 | **Perfil de trabalho / multiusuário** | É do sistema, e precisa de work profile |
| 10 | **Tempo de tela / bem-estar digital** | O Android 11 já tem nas Configurações |
| 11 | **Câmera, galeria, players** | O sistema tem, e o teu tablet já veio com bons |
| 12 | **Suporte a Android 8 ou menos** | Abaixo do 9 não existe o papel HOME nem a permissão de instalar; e o alvo é o Android 9+ |
| 13 | **Uma loja de temas / papel de parede** | Outro app de marketplace. A app é o Início, não uma vitrine |
| 14 | **Widgets que exijam configuração complexa** | O app não consegue abrir a tela de configuração de outro app (ela não é exportada) — limitação real, e por isso o seletor é o do sistema |

---

## 5. As fontes

- [TechCrunch — People are using AI to make their smart TVs dumb again (19/10/2026)](https://techcrunch.com/2026/10/19/people-are-using-ai-to-make-their-smart-tvs-dumb-again/)
- [What Annoys Android Users the Most? (Top Complaints in 2026)](https://www.janamana.in/2026/03/what-annoys-android-users-most-top.html)
- [Asurion — 10 ways to fix an Android tablet that's running slow or freezing](https://www.asurion.com/connect/tech-tips/fix-slow-android-tablet/)
- [Answered.io — 16 Ways to Speed Up Your Slow Android Tablet](https://unanswered.io/guide/speed-up-slow-android-tablet)
- [Lifewire — How to Fix It When an Android Tablet Is Running Slow or Freezing](https://www.lifewire.com/fix-android-tablet-slow-or-freezing-5224958)
- [Android Authority — Fed up with ads and bloatware on your smart TV](https://www.androidauthority.com/android-tv-vibe-hacking)
- [dumbtv](https://github.com/MuzzammilYasin/dumbtv) e [cleanyourtv](https://github.com/Gathin23/cleanyourtv) — a mesma onda, para TVs
