package com.eu.habbo.habbohotel.wired.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hands out values in shuffled rounds, as AIR deals furni over users (Furni To User) and users over furni
 * (Teleport To): no value is used k+1 times before every value was used k times.
 */
public final class WiredFairDeal {
    private WiredFairDeal() {}

    public static <T> List<T> deal(List<T> values, int count) {
        return deal(values, count, ThreadLocalRandom.current());
    }

    public static <T> List<T> deal(List<T> values, int count, Random random) {
        List<T> dealt = new ArrayList<>(Math.max(0, count));

        if (values == null || values.isEmpty() || count <= 0) {
            return dealt;
        }

        while (dealt.size() < count) {
            List<T> round = new ArrayList<>(values);
            Collections.shuffle(round, random);

            for (T value : round) {
                if (dealt.size() == count) {
                    break;
                }

                dealt.add(value);
            }
        }

        return dealt;
    }
}
