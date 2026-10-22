package com.ecommerce.unit;

import com.ecommerce.util.SearchTextUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchTextUtilsTest {

    @Test
    void normalize_VietnameseDiacritics_ShouldRemoveDiacriticsAndLower() {
        assertEquals("ao thun", SearchTextUtils.normalize("Áo Thun"));
    }

    @Test
    void normalize_LetterDWithStroke_ShouldConvertToD() {
        assertEquals("dong ho", SearchTextUtils.normalize("Đồng Hồ"));
    }

    @Test
    void normalize_MultipleSpacesAndWhitespace_ShouldTrimAndCollapse() {
        assertEquals("ao thun", SearchTextUtils.normalize("  ao   thun "));
    }

    @Test
    void normalize_NullInput_ShouldReturnEmptyString() {
        assertEquals("", SearchTextUtils.normalize(null));
    }

    @Test
    void normalize_EmptyInput_ShouldReturnEmptyString() {
        assertEquals("", SearchTextUtils.normalize(""));
    }

    @Test
    void escapeLike_SpecialCharacters_ShouldEscapeBackslashPercentAndUnderscore() {
        // "100%_\\" has '%' -> '\%', '_' -> '\_', '\' -> '\\'
        // Expected: "100\%\\_\x5c\\..."
        // In java string: "100" + "\\" + "%" + "\\" + "_" + "\\" + "\\"
        assertEquals("100\\%\\_\\\\", SearchTextUtils.escapeLike("100%_\\"));
    }
}
