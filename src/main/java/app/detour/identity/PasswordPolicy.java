package app.detour.identity;

public final class PasswordPolicy {
    public static final int MINIMUM_CODE_POINTS = 12;
    public static final int MAXIMUM_CODE_POINTS = 128;

    private PasswordPolicy() {
    }

    public static boolean isAccepted(String password) {
        if (password == null) {
            return false;
        }
        int length = password.codePointCount(0, password.length());
        return length >= MINIMUM_CODE_POINTS && length <= MAXIMUM_CODE_POINTS;
    }
}
