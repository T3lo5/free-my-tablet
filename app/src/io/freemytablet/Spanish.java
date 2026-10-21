package io.freemytablet;

/** Spanish copy for text drawn directly by the custom UI. Source keys are the existing English copy. */
final class Spanish {
    private Spanish() {}

    static String text(String english) {
        switch (english) {
            case "All apps": return "Todas las apps";
            case "Home screen": return "Pantalla de inicio";
            case "Apps to turn off": return "Apps para desactivar";
            case "Dock": return "Barra de apps";
            case "Language": return "Idioma";
            case "Theme": return "Tema";
            case "About": return "Acerca de";
            case "Original": return "Original";
            case "Apps you don't use": return "Apps que no usas";
            case "Choose": return "Elegir";
            case "Automatic": return "Automático";
            case "Light": return "Claro";
            case "Dark": return "Oscuro";
            case "Cancel": return "Cancelar";
            case "Turn on": return "Activar";
            case "Turn on Free My Tablet?": return "¿Activar Free My Tablet?";
            case "Go back": return "Volver";
            case "Go back to the original?": return "¿Volver al original?";
            case "Português": return "Portugués";
            case "Español": return "Español";
            case "Connecting…": return "Conectando…";
            case "Measuring the original home screen…": return "Midiendo la pantalla de inicio original…";
            case "Switching the home screen…": return "Cambiando la pantalla de inicio…";
            case "Android didn't accept the new home screen": return "Android no aceptó la nueva pantalla de inicio";
            case "Turning off apps (": return "Desactivando apps (";
            case "Turning apps back on (": return "Reactivando apps (";
            case "Measuring Free My Tablet…": return "Midiendo Free My Tablet…";
            case "Turning the apps back on…": return "Reactivando las apps…";
            case "Restoring the original home screen…": return "Restaurando la pantalla de inicio original…";
            case "Couldn't turn on Free My Tablet: ": return "No se pudo activar Free My Tablet: ";
            case "Couldn't go back to the original: ": return "No se pudo volver al modo original: ";
            case "Couldn't check Free My Tablet: ": return "No se pudo comprobar Free My Tablet: ";
            case "the home screen": return "la pantalla de inicio";
            case "the original home screen": return "la pantalla de inicio original";
            case "Original mode: the factory home screen and apps are back.":
                return "Modo original: se restauraron la pantalla de inicio y las apps de fábrica.";
            case "Original mode, but I couldn't turn back on: ": return "Modo original, pero no se pudo reactivar: ";
            case "Free My Tablet's home screen crashed 3 times in a row; I switched back to the original one.":
                return "La pantalla de inicio de Free My Tablet falló 3 veces seguidas; volví a la original.";
            case "Free My Tablet's home screen ran into a problem.\nGoing back to the tablet's original home screen…":
                return "La pantalla de inicio de Free My Tablet tuvo un problema.\nVolviendo a la pantalla original del tablet…";
            case "The tablet update turned back on ": return "La actualización del tablet reactivó ";
            case "Turned off what was missing: ": return "Desactivé lo que faltaba: ";
            case "The tablet was updated; Free My Tablet is still on.":
                return "El tablet se actualizó; Free My Tablet sigue activo.";
            case "Accept “Allow USB debugging?” on the tablet (tick “Always allow”).":
                return "Acepta “¿Permitir depuración USB?” en el tablet (marca “Permitir siempre”).";
            case "We go back to the tablet's original home screen and turn the apps back on. Nothing is uninstalled.":
                return "Volvemos a la pantalla de inicio original del tablet y reactivamos las apps. Nada se desinstala.";
            case "The home screen becomes Free My Tablet: a light home screen, with no ads, and the apps you don't use are turned off (never uninstalled). The tablet will ask to allow debugging: accept it once.":
                return "La pantalla de inicio se vuelve Free My Tablet: una pantalla de inicio leve, sin anuncios, y las apps que no usas se desactivan (nunca se desinstalan). El tablet pedirá permiso de depuración: acéptalo una vez.";
            case "Version ": return "Versión ";
            case "A light home screen, with no ads, for your tablet.":
                return "Una pantalla de inicio leve, sin anuncios, para tu tablet.";
            case "Nothing is uninstalled: one button brings everything back.":
                return "Nada se desinstala: un botón lo devuelve todo.";
            default:
                if (english.startsWith("Free My Tablet is on: new home screen, ")) {
                    return "Free My Tablet está activo: nueva pantalla de inicio, "
                            + english.substring("Free My Tablet is on: new home screen, ".length()).replace(" apps turned off.", " apps desactivadas.");
                }
                if (english.startsWith("Version ")) {
                    return "Versión " + english.substring("Version ".length());
                }
                if (english.startsWith("A light home screen")) {
                    return "Una pantalla de inicio leve, sin anuncios, para tu tablet.\n\n"
                            + "Nada se desinstala: un botón lo devuelve todo.";
                }
                return english;
        }
    }
}
