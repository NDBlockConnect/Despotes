package dev.despotes.fabric;

import dev.despotes.common.platform.ShotHandle;

/** Fabric screenshot handle holding encoded PNG bytes. */
public final class FabricShotHandle implements ShotHandle {

    private final int width;
    private final int height;
    private final String format;
    private final byte[] encoded;

    public FabricShotHandle(int width, int height, String format, byte[] encoded) {
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
