package app.detour.identity;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailCanonicalizer {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private EmailCanonicalizer() {
    }

    public static String canonicalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isAccepted(String canonicalEmail) {
        return canonicalEmail.length() <= 320 && EMAIL.matcher(canonicalEmail).matches();
    }
}
