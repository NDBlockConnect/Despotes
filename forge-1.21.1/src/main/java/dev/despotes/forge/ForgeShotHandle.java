package dev.despotes.forge;

import dev.despotes.common.platform.ShotHandle;

/** Forge screenshot handle holding encoded PNG bytes. */
public final class ForgeShotHandle implements ShotHandle {

    private final int width;
    private final int height;
    private final String format;
    private final byte[] encoded;

    public ForgeShotHandle(int width, int height, String format, byte[] encoded) {
        this.width = width;
        this.height = height;
        this.format = format;
        this.encoded = encoded;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public byte[] encoded() {
        return encoded;
    }

    @Override
    public String format() {
        return format;
    }
}
