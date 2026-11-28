package org.example.backtrackweb.sync.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Identifies the exact content of one sync command.
 *
 * <p>An operation id is only a claim. To tell a genuine retry from a reused id,
 * the server stores this fingerprint next to the ledger row and compares it when
 * the id is seen again: same business, same device, same command type and same
 * payload means a retry and the original answer is returned; anything else means
 * the id was reused for a different command and is refused.
 *
 * <p>JSON object property order is <em>not</em> stable: two clients (or two
 * serialisations by the same client) can send the same command with the keys in
 * a different order. The payload is therefore converted to plain Java values and
 * written back out with object keys sorted, so the hash depends on the content
 * and not on the byte order. Numbers are normalised too ({@code 100} and
 * {@code 100.00} are the same value), and the digest is SHA-256 hex so it fits
 * the {@code payload_hash} column and cannot be reversed.
 */
@Component
public class OperationFingerprint {

    private final ObjectMapper objectMapper;

    public OperationFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * The fingerprint of one command, as stored in {@code sync_operations}.
     *
     * @param businessId the business the command belongs to
     * @param deviceId   the device that sent it
     * @param type       the command type, compared case-insensitively
     * @param payload    the payload exactly as it arrived
     */
    public String of(Long businessId, Long deviceId, String type, JsonNode payload) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("business=").append(businessId).append('\n');
        canonical.append("device=").append(deviceId).append('\n');
        canonical.append("type=")
                .append(type == null ? "" : type.trim().toUpperCase(Locale.ROOT))
                .append('\n');
        canonical.append("payload=");
        write(canonical, plain(payload));
        return sha256(canonical.toString());
    }

    /** The payload as plain maps, lists and scalars, so it can be reordered. */
    private Object plain(JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            return null;
        }
        try {
            return objectMapper.convertValue(payload, Object.class);
        } catch (RuntimeException ex) {
            // Not a structure we understand: fall back to its own text, which is
            // still stable for a given incoming byte sequence.
            return payload.toString();
        }
    }

    private void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, entryValue) -> sorted.put(String.valueOf(key), entryValue));
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(out, entry.getKey());
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
            return;
        }
        if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(out, list.get(i));
            }
            out.append(']');
            return;
        }
        if (value instanceof Number number) {
            out.append(numberText(number));
            return;
        }
        if (value instanceof Boolean bool) {
            out.append(bool);
            return;
        }
        quote(out, String.valueOf(value));
    }

    /** One textual form per value, whatever numeric type the parser chose. */
    private String numberText(Number number) {
        try {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return number.toString();
        }
    }

    private void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but not available.", ex);
        }
    }
}
