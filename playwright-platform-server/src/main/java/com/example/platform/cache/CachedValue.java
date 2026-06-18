package com.example.platform.cache;

public record CachedValue(boolean empty, Object value) {
    public static CachedValue hit(Object value) {
        return new CachedValue(false, value);
    }

    public static CachedValue nullValue() {
        return new CachedValue(true, null);
    }
}
