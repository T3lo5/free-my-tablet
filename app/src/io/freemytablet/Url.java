package io.freemytablet;

/**
 * The percent-encoding of a piece of an address.
 *
 * The same rules the system's Uri.encode uses, but without the Android: the tests can check it on any
 * computer (the encoding of the addresses of the Obsidian is easy to get wrong — a slash escaped and the
 * note of a subfolder never opens).
 */
final class Url {
    private Url() {}

    /** The characters that stay as they are. */
    private static final String SAFE =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()";

    /** The text encoded for an address (the space becomes %20, the accents become %C3%A1). */
    static String encode(String text) {
        if (text == null) return "";
        byte[] bytes;
        try {
            bytes = text.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (c < 0x80 && SAFE.indexOf((char) c) >= 0) {
                out.append((char) c);
            } else {
                out.append('%');
                out.append(hex(c >> 4));
                out.append(hex(c & 0xF));
            }
        }
        return out.toString();
    }

    /** One digit of the hexadecimal. */
    private static char hex(int value) {
        return (char) (value < 10 ? '0' + value : 'A' + value - 10);
    }
}
