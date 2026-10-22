# A limpeza do tablet (22/10/2026)

O Galaxy Tab A 8.0 (SM-T290) foi limpo à mão, por ADB, com o tablet ligado na rede. Tudo o que
saiu é de fábrica: **nenhum app seu foi tocado** (Obsidian, ReadEra, Termux, Bitwarden, YouTube,
YouCine, CDisplay, os web apps e o Z-Lib continuam lá).

**Resultado: 5,5 GB → 9,3 GB livres (76% → 59% usado).** 3,8 GB a mais.

## O que saiu (69 apps de fábrica)

| app | ocupava | por que saiu |
|---|---|---|
| `com.sec.android.diagmonagent` | 1,7 GB | o diagnóstico da Samsung (esconde um monte de coisa) |
| `com.microsoft.skydrive` | 628 MB | OneDrive (a sincronização é o git da pessoa) |
| `com.google.android.tts` | 560 MB | o Google TTS (o Android 11 não tem outro aqui; ninguém usa) |
| `com.samsung.android.app.notes` | 428 MB | o Samsung Notes (o Obsidian é o que a pessoa usa) |
| `com.samsung.android.scloud` | 217 MB | o Samsung Cloud (não há conta Samsung) |
| `com.sec.android.app.samsungapps` | 137 MB | a Galaxy Store |
| `com.google.android.apps.maps` | — | o Maps (só desliga; o Android não deixa desinstalar) |
| `com.google.android.apps.tachyon` | — | o Google Meet |
| `com.google.android.videos` | — | o Google Videos |
| `com.samsung.android.game.gos` | — | o Game Optimizing Service |
| `com.samsung.android.app.dressroom` | — | os papéis de parede da Samsung |
| `com.samsung.android.themecenter` | — | a loja de temas |
| `com.samsung.android.app.omcagent`, `com.samsung.android.mobileservice` | — | as push da Samsung |
| `com.samsung.android.app.sharelive` | — | o Quick Share |
| `com.samsung.android.app.rubin.app` | — | o assistente da Samsung (só desliga) |
| `com.osp.app.signin` | — | o login da conta Samsung |
| `com.sec.android.app.soundalive` | — | o SoundAlive |
| `com.sec.android.app.billing` | — | a cobrança da Samsung |
| `com.samsung.android.dialer`, `incallui`, `app.telephonyui` | — | o telefone (o tablet é só Wi-Fi, não tem chip) |
| + 49 outros de fábrica | — | Bixby, Edge panels, Galaxy Kids, AR, AR Emoji, Quick Share, Smart Switch, serviço, AllShare, Theme Center, stickers, Game Manager, leitor de voz, Knox, conserto do sistema… |

A lista completa (69 linhas) está no script de restauração, no vault e aqui no repositório.

## O que ficou (e por quê)

Play Store, Play Services, Gmail, Google Docs, o app do YouTube, o Chrome, a câmera, o Meus
Arquivos, o teclado Samsung, o SwiftKey (que é o teclado ativo), Calendário, Contatos, Gmail —
e todos os seus apps.

## O cache

`pm trim-caches 3G` limpou o cache de todos os apps (YouTube tinha 1 GB, o Chrome 462 MB, o
YouCine 332 MB, a busca do Google 290 MB). Nada seu é apagado: cache recria sozinho.

## Como voltar atrás

O script `restaurar-bloatware.sh` (no vault: `pos-coders/restaurar-bloatware.sh`) religa tudo:

```sh
sh ~/path/para/restaurar-bloatware.sh
# ou, pelo computador:
adb shell sh /sdcard/Documents/Obsidian/pos-coders/restaurar-bloatware.sh
```

Cada linha é um `pm install-existing`: o Android reinstala o app de fábrica para o seu usuário.
Nada é apagado de novo — os seus dados continuam onde estavam.

## Como o app se comporta

O Free My Tablet só *desliga* apps (nunca religa), então uma atualização do tablet ou do app
não traz o bloatware de volta. Se a pessoa voltar ao Início original pelo app, os apps que
*ele* desligou voltam — os que saíram por esta limpeza, não (para eles, o script).

O `com.google.android.apps.docs` (Google Docs) saiu da lista de opcionais do app, para que o
modo Free My Tablet não o desligue.
