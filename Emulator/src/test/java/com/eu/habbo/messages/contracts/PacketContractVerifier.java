package com.eu.habbo.messages.contracts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record PacketContractContext(String name, String direction, String sourcePath) {
}

final class PacketContractVerifier {
    private PacketContractVerifier() {
    }

    static void verify(
            List<WireSchema> expected,
            List<WireSchema> observed,
            PacketContractContext context) {
        List<String> errors = new ArrayList<>();
        compareList(expected, observed, "fields", errors);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Packet contract " + context.name() + " (" + context.direction() + ") in "
                            + context.sourcePath() + " mismatched: " + errors.getFirst());
        }
    }

    private static void compareList(
            List<WireSchema> expected,
            List<WireSchema> observed,
            String path,
            List<String> errors) {
        if (observed.size() > expected.size()
                || (observed.size() < expected.size()
                && expected.subList(observed.size(), expected.size()).stream()
                .anyMatch(field -> !(field instanceof OptionalSchema)))) {
            errors.add(path + " expected " + expected.size() + " fields but observed " + observed.size());
            return;
        }
        for (int index = 0; index < observed.size(); index++) {
            compare(expected.get(index), observed.get(index), path + "[" + index + "]", errors);
            if (!errors.isEmpty()) return;
        }
    }

    private static void compare(
            WireSchema expected,
            WireSchema observed,
            String path,
            List<String> errors) {
        if (expected instanceof ScalarSchema expectedScalar) {
            if (!(observed instanceof ScalarSchema observedScalar)) {
                errors.add(path + " expected scalar " + expectedScalar.type()
                        + " but observed " + kind(observed));
            } else if (!expectedScalar.type().equals(observedScalar.type())) {
                errors.add(path + " expected " + expectedScalar.type()
                        + " but observed " + observedScalar.type());
            }
            return;
        }
        if (expected instanceof ListSchema expectedList) {
            if (!(observed instanceof ListSchema observedList)) {
                errors.add(path + " expected list but observed " + kind(observed));
            } else if (!expectedList.countType().equals(observedList.countType())) {
                errors.add(path + ".list.count expected " + expectedList.countType()
                        + " but observed " + observedList.countType());
            } else {
                compareList(expectedList.item(), observedList.item(), path + ".list.item", errors);
            }
            return;
        }
        if (expected instanceof OptionalSchema expectedOptional) {
            if (!(observed instanceof OptionalSchema observedOptional)) {
                errors.add(path + " expected optional but observed " + kind(observed));
            } else if (!expectedOptional.controller().equals(observedOptional.controller())) {
                errors.add(path + ".optional.controller expected " + expectedOptional.controller()
                        + " but observed " + observedOptional.controller());
            } else {
                compareList(expectedOptional.fields(), observedOptional.fields(), path + ".optional.fields", errors);
            }
            return;
        }
        VariantSchema expectedVariant = (VariantSchema) expected;
        if (!(observed instanceof VariantSchema observedVariant)) {
            errors.add(path + " expected variant but observed " + kind(observed));
            return;
        }
        if (!expectedVariant.discriminator().equals(observedVariant.discriminator())) {
            errors.add(path + ".variant.discriminator expected " + expectedVariant.discriminator()
                    + " but observed " + observedVariant.discriminator());
            return;
        }
        if (!expectedVariant.branches().keySet().equals(observedVariant.branches().keySet())) {
            errors.add(path + ".variant branches expected " + expectedVariant.branches().keySet()
                    + " but observed " + observedVariant.branches().keySet());
            return;
        }
        for (Map.Entry<String, List<WireSchema>> branch : expectedVariant.branches().entrySet()) {
            compareList(
                    branch.getValue(),
                    observedVariant.branches().get(branch.getKey()),
                    path + ".variant." + branch.getKey(),
                    errors);
            if (!errors.isEmpty()) return;
        }
    }

    private static String kind(WireSchema schema) {
        return switch (schema) {
            case ScalarSchema ignored -> "scalar";
            case ListSchema ignored -> "list";
            case OptionalSchema ignored -> "optional";
            case VariantSchema ignored -> "variant";
        };
    }
}
