package com.eu.habbo.plugin;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class NumericConfigurationParser {

    private NumericConfigurationParser() {
    }

    static int[] parseIntArray(String value, String delimiter) {
        return Arrays.stream(value.split(delimiter))
                .mapToInt(Integer::parseInt)
                .toArray();
    }

    static int[] parseIntArray(String value, String delimiter, int[] currentValue, String configurationKey) {
        return parseIntArray(value, delimiter);
    }

    static List<Integer> parseIntList(String value, String delimiter) {
        return Arrays.stream(value.split(delimiter))
                .mapToInt(Integer::parseInt)
                .boxed()
                .collect(Collectors.toList());
    }

    static List<Integer> parseIntList(
            String value,
            String delimiter,
            List<Integer> currentValue,
            String configurationKey
    ) {
        return parseIntList(value, delimiter);
    }
}
