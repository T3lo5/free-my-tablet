package io.freemytablet;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;

/**
 * The three steps of the first opening: turn the app into the home screen and give it the Obsidian folder.
 * Shown once by the home screen, and again from the settings ("First steps") when the person wants to read it.
 */
final class Guide {
    private Guide() {}

    static final String MESSAGE_PT =
            "1. Segure a parte vazia da tela e abra as configurações.\n"
                    + "2. Em \"Tela de Início\", escolha o Free My Tablet (ou \"Original\" para voltar).\n"
                    + "3. Em \"Pasta do Obsidian\", escolha a pasta do Obsidian (a nota rápida vai para lá).";
    static final String MESSAGE_EN =
            "1. Hold the empty part of the screen and open the settings.\n"
                    + "2. In \"Home screen\", choose Free My Tablet (or \"Original\" to go back).\n"
                    + "3. In \"Obsidian folder\", choose the Obsidian folder (the quick note goes there).";
    static final String MESSAGE_ES =
            "1. Mantén pulsada la parte vacía de la pantalla y abre los ajustes.\n"
                    + "2. En \"Pantalla de inicio\", elige Free My Tablet (o \"Original\" para volver).\n"
                    + "3. En \"Carpeta de Obsidian\", elige la carpeta de Obsidian (la nota rápida va allí).";

    static String title() {
        return Ui.tr("Primeiros passos", "First steps", "Primeros pasos");
    }

    static String message() {
        return Ui.tr(MESSAGE_PT, MESSAGE_EN, MESSAGE_ES);
    }

    /**
     * The dialog. From the home screen it offers the settings; from the settings it only says "got it" (the
     * button would open the screen the person is already in).
     */
    static void show(Activity a, boolean withSettings) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(a)
                .setTitle(title())
                .setMessage(message());
        if (withSettings) {
            dialog.setNegativeButton(Ui.tr("Depois", "Later", "Después"), null)
                    .setPositiveButton(Ui.tr("Abrir as configurações", "Open the settings", "Abrir los ajustes"),
                            (d, w) -> a.startActivity(new Intent(a, SettingsActivity.class)));
        } else {
            dialog.setPositiveButton(Ui.tr("Entendi", "Got it", "Entendido"), null);
        }
        dialog.show();
    }
}
