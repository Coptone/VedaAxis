package dev.vedaaxis.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class SecureTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] USER_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private SecureTokens() {
    }

    public static String opaqueToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String userCode() {
        char[] result = new char[9];
        for (int index = 0; index < result.length; index++) {
            if (index == 4) {
                result[index] = '-';
            } else {
                result[index] = USER_CODE_ALPHABET[RANDOM.nextInt(USER_CODE_ALPHABET.length)];
            }
        }
        return new String(result);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
