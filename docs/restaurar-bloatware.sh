#!/system/bin/sh
#
# Religa o bloatware que foi removido do Galaxy Tab A 8.0 (SM-T290) em 22/10/2026.
# Roda no Termux (ou por "adb shell sh este-arquivo.sh").
# Cada linha religa um app de fábrica: o Android reinstala o app para o seu usuário.
# Nada é apagado de novo: os seus dados continuam onde estavam.

pm install-existing com.google.android.apps.maps
pm install-existing com.google.android.apps.tachyon
pm install-existing com.google.android.videos
pm install-existing com.google.android.tts
pm install-existing com.google.android.apps.turbo
pm install-existing com.google.android.apps.carrier.carrierwifi
pm install-existing com.google.android.gms.location.history
pm install-existing com.microsoft.skydrive
pm install-existing com.sec.android.diagmonagent
pm install-existing com.samsung.android.app.notes
pm install-existing com.samsung.android.scloud
pm install-existing com.sec.android.app.samsungapps
pm install-existing com.sec.android.widgetapp.samsungapps
pm install-existing com.sec.android.app.billing
pm install-existing com.samsung.android.app.omcagent
pm install-existing com.samsung.android.mobileservice
pm install-existing com.samsung.android.app.sharelive
pm install-existing com.samsung.android.app.dressroom
pm install-existing com.samsung.android.themecenter
pm install-existing com.samsung.android.game.gos
pm install-existing com.samsung.android.kidsinstaller
pm install-existing com.samsung.android.app.rubin.app
pm install-existing com.samsung.android.app.appsedge
pm install-existing com.samsung.android.app.clipboardedge
pm install-existing com.samsung.android.app.taskedge
pm install-existing com.samsung.android.app.smartcapture
pm install-existing com.samsung.android.app.updatecenter
pm install-existing com.samsung.android.stickercenter
pm install-existing com.sec.android.app.camera.sticker
pm install-existing com.samsung.android.easysetup
pm install-existing com.samsung.android.mdm
pm install-existing com.samsung.android.mdm.kit
pm install-existing com.samsung.android.app.settings.bixby
pm install-existing com.sec.android.easyMover.Agent
pm install-existing com.sec.android.app.servicemodeapp
pm install-existing com.sec.android.app.wlantest
pm install-existing com.sec.android.app.bluetoothtest
pm install-existing com.sec.android.app.ringtoneBR
pm install-existing com.sec.android.app.ve.vebgm
pm install-existing com.sec.android.app.chromecustomizations
pm install-existing com.samsung.android.app.cocktailbarservice
pm install-existing com.samsung.android.aware.service
pm install-existing com.samsung.android.beaconmanager
pm install-existing com.samsung.android.securitylogagent
pm install-existing com.samsung.android.app.watchmanagerstub
pm install-existing com.samsung.android.smartcall
pm install-existing com.samsung.android.smartcallprovider
pm install-existing com.samsung.android.smartswitchassistant
pm install-existing com.samsung.android.shortcutbackupservice
pm install-existing com.samsung.android.sdk.handwriting
pm install-existing com.samsung.android.allshare.service.fileshare
pm install-existing com.samsung.android.allshare.service.mediashare
pm install-existing com.samsung.android.dqagent
pm install-existing com.samsung.android.networkdiagnostic
pm install-existing com.osp.app.signin
pm install-existing com.sec.android.sdhms
pm install-existing com.samsung.android.video
pm install-existing com.sec.android.mimage.photoretouching
pm install-existing com.samsung.android.dialer
pm install-existing com.samsung.android.incallui
pm install-existing com.samsung.android.app.telephonyui
pm install-existing com.sec.android.app.DataCreate
pm install-existing com.sec.android.app.parser
pm install-existing com.sec.android.app.factorykeystring
pm install-existing com.sec.android.app.setupwizardlegalprovider
pm install-existing com.sec.android.preloadinstaller
pm install-existing com.sec.android.app.quicktool
pm install-existing com.sec.android.app.soundalive
pm install-existing com.sec.android.usermanual

# Os que o Android não deixa desinstalar (só desliga):
pm enable com.google.android.apps.maps
pm enable com.samsung.android.app.rubin.app
pm enable com.samsung.android.mdm.kit
pm enable com.samsung.android.smartcall
