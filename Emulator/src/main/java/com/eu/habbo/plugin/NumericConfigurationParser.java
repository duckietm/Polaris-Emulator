package com.eu.habbo.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class NumericConfigurationParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(NumericConfigurationParser.class);

    private NumericConfigurationParser() {
    }

    static int[] parseIntArray(String value, String delimiter) {
        return Arrays.stream(value.split(delimiter))
                .mapToInt(Integer::parseInt)
                .toArray();
    }

    static int[] parseIntArray(String value, String delimiter, int[] currentValue, String configurationKey) {
        try {
            return parseIntArray(value, delimiter);
        } catch (NumberFormatException exception) {
            LOGGER.error(
                    "Invalid integer list for configuration key '{}'; keeping the previous value",
                    configurationKey
            );
            return currentValue;
        }
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
        try {
            return parseIntList(value, delimiter);
        } catch (NumberFormatException exception) {
            LOGGER.error(
                    "Invalid integer list for configuration key '{}'; keeping the previous value",
                    configurationKey
            );
            return currentValue;
        }
    }
}
