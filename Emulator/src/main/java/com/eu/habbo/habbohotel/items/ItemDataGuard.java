package com.eu.habbo.habbohotel.items;

final class ItemDataGuard {
    static final int MAX_EXTRA_DATA_LENGTH = 1000;

    private ItemDataGuard() {
    }

    static String safeString(String value) {
        return value == null ? "" : value;
    }

    static String normalizeExtraData(String value) {
        String safe = safeString(value);
        return safe.length() > MAX_EXTRA_DATA_LENGTH ? safe.substring(0, MAX_EXTRA_DATA_LENGTH) : safe;
    }

    static int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(safeString(value).trim());
            return parsed > 0 ? parsed : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static int[] parsePositiveIntList(String value) {
        String safe = safeString(value).replace(";", ",").replace(".", ",");
        if (safe.isBlank()) {
            return new int[0];
        }

        String[] parts = safe.split(",");
        int[] parsed = new int[parts.length];
        int count = 0;

        for (String part : parts) {
            int id = parsePositiveInt(part);
            if (id > 0) {
                parsed[count++] = id;
            }
        }

        if (count == parsed.length) {
            return parsed;
        }

        int[] compact = new int[count];
        System.arraycopy(parsed, 0, compact, 0, count);
        return compact;
    }

    static double[] parseHeights(String value) {
        String safe = safeString(value);
        if (safe.isBlank() || !safe.contains(";")) {
            return new double[0];
        }

        String[] parts = safe.split(";");
        double[] parsed = new double[parts.length];
        int count = 0;

        for (String part : parts) {
            try {
                double height = Double.parseDouble(part.trim());
                if (Double.isFinite(height)) {
                    parsed[count++] = height;
                }
            } catch (NumberFormatException e) {
                // Ignore malformed DB values and keep the remaining heights usable.
            }
        }

        if (count == parsed.length) {
            return parsed;
        }

        double[] compact = new double[count];
        System.arraycopy(parsed, 0, compact, 0, count);
        return compact;
    }
}
