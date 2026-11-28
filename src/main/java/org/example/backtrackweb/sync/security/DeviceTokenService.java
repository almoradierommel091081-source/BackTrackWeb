package org.example.backtrackweb.sync.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Creates device tokens and hashes them.
 *
 * <p>The raw token is returned to the device once, at registration. Only its
 * SHA-256 hash is stored, so a copy of the database cannot be used to call the
 * API, and the token is never written to a log or included in a response body
 * after that.
 */
@Component
public class DeviceTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    /** The visible part of the token kept in the database for recognition only. */
    public static final int PREFIX_LENGTH = 12;

    public String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return "btk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but not available.", ex);
        }
    }

    public String prefixOf(String token) {
        return token.length() <= PREFIX_LENGTH ? token : token.substring(0, PREFIX_LENGTH);
    }
}
