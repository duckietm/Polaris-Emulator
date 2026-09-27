package com.eu.habbo.networking.gameserver.wired;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One HTTP request as the router sees it, detached from netty.
 *
 * @param path the decoded path, without the query string
 * @param query the query parameters, in the order received
 * @param headers header values keyed by lower-case name
 */
record WiredApiRequest(
        String method,
        String path,
        Map<String, List<String>> query,
        Map<String, String> headers,
        byte[] body,
        String clientIp) {

    String header(String name) {
        return this.headers.get(name.toLowerCase(Locale.ROOT));
    }

    String queryValue(String name) {
        List<String> values = this.query.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
