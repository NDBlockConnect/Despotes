package dev.despotes.common.platform;

/** A captured frame, produced on the render thread. */
public interface ShotHandle {

    int width();

    int height();

    /** Encoded image bytes (PNG or JPEG depending on request). */
    byte[] encoded();

    /** "png" or "jpg". */
    String format();
}
