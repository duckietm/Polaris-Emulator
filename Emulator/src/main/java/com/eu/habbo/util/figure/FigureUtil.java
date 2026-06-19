package com.eu.habbo.util.figure;

import org.apache.commons.lang3.ArrayUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FigureUtil {
    public static Map<String, String> getFigureBits(String looks) {
        Map<String, String> bits = new HashMap<>();
        String[] sets = looks.split("\\.");

        for (String set : sets) {
            String[] setBits = set.split("-", 2);
            bits.put(setBits[0], setBits.length > 1 ? setBits[1] : "");
        }

        return bits;
    }


    public static String mergeFigures(String figure1, String figure2) {
        return mergeFigures(figure1, figure2, null, null);
    }

    public static String mergeFigures(String figure1, String figure2, String[] limitFigure1) {
        return mergeFigures(figure1, figure2, limitFigure1, null);
    }

    public static boolean hasBlacklistedClothing(String figure, Set<Integer> blacklist) {
        for (String set : figure.split("\\.")) {
            String[] pieces = set.split("-");

            try {
                if (pieces.length >= 2 && blacklist.contains(Integer.valueOf(pieces[1]))) {
                    return true;
                }
            } catch (NumberFormatException ignored) {

            }
        }

        return false;
    }

    public static String mergeFigures(String figure1, String figure2, String[] limitFigure1, String[] limitFigure2) {
        Map<String, String> figureBits1 = getFigureBits(figure1);
        Map<String, String> figureBits2 = getFigureBits(figure2);

        StringBuilder finalLook = new StringBuilder();

        for (Map.Entry<String, String> keys : figureBits1.entrySet()) {
            if (limitFigure1 == null || ArrayUtils.contains(limitFigure1, keys.getKey())) {
                finalLook.append(keys.getKey()).append("-").append(keys.getValue()).append(".");
            }
        }

        for (Map.Entry<String, String> keys : figureBits2.entrySet()) {
            if (limitFigure2 == null || ArrayUtils.contains(limitFigure2, keys.getKey())) {
                finalLook.append(keys.getKey()).append("-").append(keys.getValue()).append(".");
            }
        }

        if (finalLook.toString().endsWith(".")) {
            finalLook = new StringBuilder(finalLook.substring(0, finalLook.length() - 1));
        }

        return finalLook.toString();
    }
}
