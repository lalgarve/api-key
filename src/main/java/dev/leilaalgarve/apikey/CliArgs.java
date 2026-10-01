package dev.leilaalgarve.apikey;

/** Shared `--flag value` parsing helpers for every {@link CliCommand}. args[0] is always the command word, never a flag. */
public final class CliArgs {

    private CliArgs() {
    }

    public static String extractOption(String[] args, String flag) {
        for (int i = 1; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    public static Integer parsePositiveInt(String raw) {
        Integer value = parseInt(raw);
        return value != null && value > 0 ? value : null;
    }

    public static Integer parseNonNegativeInt(String raw) {
        Integer value = parseInt(raw);
        return value != null && value >= 0 ? value : null;
    }

    public static Long parseLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
