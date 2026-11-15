package com.rentequip.backend.mappers;

import java.util.function.Consumer;

public final class MappingUtils {

    private MappingUtils() {
    }

    public static <T> void applyIfPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /** An empty string from a form means "remove the value". */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
