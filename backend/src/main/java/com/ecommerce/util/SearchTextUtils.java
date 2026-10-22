package com.ecommerce.util;

import java.text.Normalizer;
import java.util.Locale;

public final class SearchTextUtils {
    public static final int MIN_KEYWORD_LENGTH = 2;
    public static final int MAX_KEYWORD_LENGTH = 100;
    public static final int MAX_TOKENS = 5;

    private SearchTextUtils() {}

    /** lower + bỏ dấu + đ->d + gộp khoảng trắng. null -> "" */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = Normalizer.normalize(raw.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd');
        return s.trim().replaceAll("\\s+", " ");
    }

    /** Escape ký tự đặc biệt của LIKE (dùng với escape char '\\') */
    public static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
