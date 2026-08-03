package com.eu.habbo.networking.gameserver.cms;

import java.util.Set;

/**
 * A single CMS API credential: an identifier, its shared HMAC secret and the set
 * of scopes it is authorized for. Secrets never leave the server; they are only
 * used to verify request signatures.
 *
 * @param keyId  public identifier sent in the {@code X-Cms-Key} header
 * @param secret shared secret bytes used as the HMAC-SHA256 key
 * @param scopes authorized scope tokens (see {@link CmsCommandScopes})
 */
public record CmsApiKey(String keyId, byte[] secret, Set<String> scopes) {}
