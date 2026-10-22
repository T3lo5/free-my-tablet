package io.freemytablet;

import java.util.ArrayList;
import java.util.List;

/**
 * The tests of the app: the logic that is pure (the reading of the system's numbers, the encoding of the
 * addresses, the dates). No Gradle, no JUnit: a plain program that says what broke and stops with an error.
 *
 * The tests that need the Android (the screen, the vault) are not here: those are checked on the tablet.
 */
public final class Tests {
    private Tests() {}

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        // The numbers the system writes: the marks of thousands are the trap ("1,892,156K").
        equal("o total da memória", 1892156L, Panel.number("Total RAM: 1,892,156K", "Total RAM: "));
        equal("a memória livre", 729216L, Panel.number("Free RAM: 729,216K", "Free RAM: "));
        equal("a memória usada", 0L, Panel.number("não tem isto aqui", "Used RAM: "));
        equal("o swap", 1572860L, Panel.number("SwapTotal:       1572860 kB", "SwapTotal:"));
        equal("o swap livre", 844772L, Panel.number("SwapFree:        844772 kB", "SwapFree:"));
        equal("o nível da bateria", 42L, Panel.value("  level: 42", "level:"));
        equal("a temperatura", 302L, Panel.value("  temperature: 302", "temperature:"));

        // The names dos apps na linha do meminfo.
        equal("o nome do app", "system", Panel.appName("system (pid 32393)"));
        equal("o nome com dois-pontos", "com.google.android.webview", Panel.appName("com.google.android.webview:zygote (pid 1234)"));
        equal("o nome comprido", "abcdefghijklmnopqrstuvwxyzabc…", Panel.appName("abcdefghijklmnopqrstuvwxyzabcdefghijklmnop"));

        // A lista do dumpsys.
        String[] names = Space.array("Package Names: [\"com.a\", \"com.b\"]", "Package Names: ");
        equal("o tamanho da lista", 2, names.length);
        equal("o primeiro nome", "com.a", names[0]);
        equal("o segundo nome", "com.b", names[1]);
        equal("a lista que não existe", 0, Space.array("nada aqui", "Package Names: ").length);

        // O espaço: a linha do "df".
        long[] storage = Gain.storage("/dev/block/mmcblk0p69 23591320 13711528   9748720  59% /data/user/0");
        equal("o espaço total", 23591320L * 1024, storage[0]);
        equal("o espaço livre", 9748720L * 1024, storage[1]);
        equal("a linha do df que não é do df", 0L, Gain.storage("nada aqui")[0]);

        // O endereço do Obsidian: as barras da pasta têm que ficar.
        equal("o endereço de uma nota", "obsidian://open?vault=pos-coders&file=00%20-%20Painel/Home.md",
                Vault.obsidianUri("pos-coders", "00 - Painel/Home.md"));
        equal("o endereço com acento", "obsidian://open?vault=pos-coders&file=01%20-%20Di%C3%A1rio/Nota.md",
                Vault.obsidianUri("pos-coders", "01 - Diário/Nota.md"));
        equal("o endereço com &", "obsidian://open?vault=pos-coders&file=a%26b.md",
                Vault.obsidianUri("pos-coders", "a&b.md"));
        equal("o espaço codificado", "a%20b", Url.encode("a b"));
        equal("o acento codificado", "Di%C3%A1rio", Url.encode("Diário"));
        equal("a barra codificada", "%2F", Url.encode("/"));
        equal("o til e o asterisco", "a~!*()b", Url.encode("a~!*()b"));

        // Os dias: "hoje", "ontem" e o resto.
        long now = 1758000000000L;
        equal("hoje", 0, NotesWidget.daysAgo(now, now));
        equal("ontem", 1, NotesWidget.daysAgo(now, now - 86400000L));
        equal("anteontem", -1, NotesWidget.daysAgo(now, now - 2 * 86400000L));
        equal("sem data", -1, NotesWidget.daysAgo(now, 0));

        // A busca no vault: pelo nome e pelo texto de dentro da nota.
        Vault.Note peloNome = new Vault.Note("Calendário da Turma", "00 - Painel/Calendário da Turma.md", "pos-coders", 0);
        Vault.Note peloTexto = new Vault.Note("Home", "00 - Painel/Home.md", "pos-coders", 0);
        peloTexto.text = "Aqui a gente escreve sobre o harness e o LLm.";
        peloTexto.lower = peloTexto.text.toLowerCase(java.util.Locale.getDefault());
        List<Vault.Note> notas = new ArrayList<>();
        notas.add(peloNome);
        notas.add(peloTexto);
        equal("a busca pelo nome", 1, VaultSearch.filter(notas, "Calendário").size());
        equal("a busca pelo nome (minúscula)", 1, VaultSearch.filter(notas, "calendário").size());
        equal("a busca por dentro do texto", 1, VaultSearch.filter(notas, "harness").size());
        equal("a busca por dentro do texto (maiúscula)", 1, VaultSearch.filter(notas, "HARNESS").size());
        equal("a busca que não acha", 0, VaultSearch.filter(notas, "que nada").size());
        equal("a busca vazia mostra tudo", 2, VaultSearch.filter(notas, "").size());

        // O fim.
        System.out.println();
        System.out.println(passed + " checks passed, " + failures.size() + " failed.");
        for (String failure : failures) System.out.println("  " + failure);
        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }

    /** Checks that the two are the same. */
    private static void equal(String what, Object expected, Object got) {
        if (expected == null ? got == null : expected.equals(got)) {
            passed++;
            return;
        }
        failures.add(what + ": expected " + expected + ", got " + got);
    }
}
