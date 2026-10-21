# Instalar o Free My Tablet

O jeito mais curto, para quem não quer compilar nada.

## 1. Baixar o APK

Baixe o arquivo `FreeMyTablet.apk` da página de
[versões](https://github.com/T3lo5/free-my-tablet/releases) (na release mais recente).

## 2. Instalar no tablet

**Opção A — pelo cabo USB (mais fácil)**

1. Conecte o tablet pelo cabo e abra as **Opções do desenvolvedor** no computador
   (o botão "Transferir arquivos" do gerenciador de arquivos; no Windows, o driver da Samsung).
2. Copie o `FreeMyTablet.apk` para o tablet (a pasta "Download" serve).
3. No tablet, abra o **Meus Arquivos** → **Download** → toque no APK → **Instalar**.
   (O Android vai pedir para permitir a instalação desse app: autorize.)

**Opção B — direto no tablet**

1. Abra o link da release no navegador do tablet e baixe o APK.
2. Toque no arquivo baixado → **Instalar**.

## 3. Ligar o Free My Tablet

1. Abra o app **Free My Tablet** (o ícone dele).
2. Toque em **Tela de Início** → **Free My Tablet**.
3. O Android pergunta qual app é o Início: escolha o **Free My Tablet**.
4. Se aparecer o aviso **"Permitir depuração USB?"**, aceite e marque **"Sempre permitir"**
   (é o app falando com o próprio tablet, para desligar os apps de fábrica).
5. Pronto: a tela inicial é a nossa.

## 4. Escolher o que fica na tela

No app **Free My Tablet** (toque e segure a parte vazia da tela inicial, ou o ícone na lista de apps):

| Onde | O quê |
|---|---|
| **Apps na tela** | Os apps da fileira (Obsidian, Termux, YouTube, o leitor, os webapps) |
| **Pasta do Obsidian** | Onde a nota rápida é salva — escolha a pasta do seu vault |
| **Card do tempo** | A temperatura no canto da tela (liga e desliga) |
| **Widget** | O widget (o do sistema: *Últimas notas* mostra as suas últimas notas) |
| **Sincronizar** | A pasta do git no Termux e o comando do pull |
| **Espaço** | O que ocupa o tablet e os botões para liberar espaço |
| **Quem fala com você** | Os apps que mais mandam notificação (um toque abre a tela do sistema) |
| **Atualizações** | Verifica e instala a versão nova |

## Atualizar

**Não precisa baixar nada.** O app procura sozinho (de seis em seis horas) se há versão nova e avisa na tela
inicial. Para atualizar agora: **Free My Tablet → Atualizações → Atualizar** → o app baixa o APK e abre o
instalador do sistema (é só confirmar).

Duas coisas acontecem na primeira atualização:

1. O Android pede a permissão de **"instalar apps desconhecidos"** para o Free My Tablet: aceite (é o app
   baixando o próprio update).
2. O **Google Play Protect** pode querer conferir o app antes de instalar, e nesse tablet a conferência
   trava. Para não passar por isso: **Play Store → seu avatar → Configurações → Segurança → Verificação de
   apps → "Não verificar"**.

## Se algo der errado

- **O Início não mudou?** Configurações → Apps → Free My Tablet → **Ativar** (de novo).
- **Aviso de depuração toda vez?** Marque "Sempre permitir" no aviso.
- **Quer voltar ao Início da Samsung?** Free My Tablet → **Tela de Início** → *Voltar*.
- **A tela está travando?** Veja o log: `adb logcat -s FreeMyTablet`.
