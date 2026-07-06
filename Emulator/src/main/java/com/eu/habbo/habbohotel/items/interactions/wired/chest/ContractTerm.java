package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One PAY/RECEIVE term on a wired contract extra.
 * <p>
 * Wire encoding (v2, 5 ints/term): {@code [dir, kind, a, b, amount]} where
 * {@code kind=0} currency ({@code a=currencyType}, {@code b=0}),
 * {@code kind=1} furni ({@code a=wall?1:0}, {@code b=baseItemId}).
 * Legacy v1 (3 ints/term): {@code [dir, currencyType, amount]}.
 * Wall poster ids ride in the contract {@code stringParam} as {@code index=poster} pairs.
 * </p>
 */
public class ContractTerm {
    public static final int DIR_PAY = 0;
    public static final int DIR_RECEIVE = 1;

    public static final int KIND_CURRENCY = 0;
    public static final int KIND_FURNI = 1;

    public static final int STRIDE_V2 = 5;
    public static final int STRIDE_V1 = 3;

    public final int direction;
    public final int kind;
    public final int currencyType;
    public final boolean wallItem;
    public final int baseItemId;
    public final String legacyPosterId;
    public final int amount;

    public ContractTerm(int direction, int kind, int currencyType, boolean wallItem, int baseItemId, String legacyPosterId, int amount) {
        this.direction = (direction == DIR_RECEIVE) ? DIR_RECEIVE : DIR_PAY;
        this.kind = (kind == KIND_FURNI) ? KIND_FURNI : KIND_CURRENCY;
        this.currencyType = currencyType;
        this.wallItem = wallItem;
        this.baseItemId = baseItemId;
        this.legacyPosterId = legacyPosterId == null ? "" : legacyPosterId;
        this.amount = Math.max(0, amount);
    }

    public static ContractTerm currency(int direction, int currencyType, int amount) {
        return new ContractTerm(direction, KIND_CURRENCY, currencyType, false, 0, "", amount);
    }

    public static ContractTerm furni(int direction, boolean wallItem, int baseItemId, String legacyPosterId, int amount) {
        return new ContractTerm(direction, KIND_FURNI, 0, wallItem, baseItemId, legacyPosterId, amount);
    }

    public boolean isCurrency() {
        return this.kind == KIND_CURRENCY;
    }

    public boolean isFurni() {
        return this.kind == KIND_FURNI;
    }

    public static List<ContractTerm> parse(int[] params, String stringParam) {
        if (params == null || params.length < 1) {
            return Collections.emptyList();
        }

        int count = Math.max(0, params[0]);
        if (count == 0) {
            return Collections.emptyList();
        }

        int stride = detectStride(params, count);
        List<String> posters = parsePosters(stringParam);

        List<ContractTerm> terms = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int base = 1 + i * stride;
            if (base + stride - 1 >= params.length) {
                break;
            }

            int dir = params[base];
            if (stride == STRIDE_V1) {
                int amount = Math.max(0, params[base + 2]);
                if (amount > 0) {
                    terms.add(currency(dir, params[base + 1], amount));
                }
                continue;
            }

            int kind = params[base + 1];
            int amount = Math.max(0, params[base + 4]);
            if (amount <= 0) {
                continue;
            }

            if (kind == KIND_FURNI) {
                terms.add(furni(dir, params[base + 2] != 0, params[base + 3], posterAt(posters, i), amount));
            } else {
                terms.add(currency(dir, params[base + 2], amount));
            }
        }

        return terms;
    }

    public static int[] serialize(List<ContractTerm> terms) {
        List<ContractTerm> valid = new ArrayList<>();
        for (ContractTerm term : terms) {
            if (term != null && term.amount > 0) {
                valid.add(term);
            }
        }

        int[] params = new int[1 + valid.size() * STRIDE_V2];
        params[0] = valid.size();
        for (int i = 0; i < valid.size(); i++) {
            ContractTerm term = valid.get(i);
            int base = 1 + i * STRIDE_V2;
            params[base] = term.direction;
            params[base + 1] = term.kind;
            if (term.isFurni()) {
                params[base + 2] = term.wallItem ? 1 : 0;
                params[base + 3] = term.baseItemId;
            } else {
                params[base + 2] = term.currencyType;
                params[base + 3] = 0;
            }
            params[base + 4] = term.amount;
        }
        return params;
    }

    public static String serializePosters(List<ContractTerm> terms) {
        if (terms == null || terms.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            ContractTerm term = terms.get(i);
            if (term != null && term.isFurni() && term.wallItem && !term.legacyPosterId.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(i).append('=').append(term.legacyPosterId);
            }
        }
        return sb.toString();
    }

    private static int detectStride(int[] params, int count) {
        if (count <= 0) {
            return STRIDE_V1;
        }
        if (params.length >= 1 + count * STRIDE_V2) {
            return STRIDE_V2;
        }
        return STRIDE_V1;
    }

    private static List<String> parsePosters(String stringParam) {
        List<String> posters = new ArrayList<>();
        if (stringParam == null || stringParam.isEmpty()) {
            return posters;
        }
        for (String part : stringParam.split(",")) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                int index = Integer.parseInt(part.substring(0, eq).trim());
                String poster = part.substring(eq + 1);
                while (posters.size() <= index) {
                    posters.add("");
                }
                posters.set(index, poster);
            } catch (NumberFormatException ignored) {
            }
        }
        return posters;
    }

    private static String posterAt(List<String> posters, int index) {
        return (index >= 0 && index < posters.size()) ? posters.get(index) : "";
    }
}
