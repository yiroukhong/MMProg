package com.wig3003.photoapp.util;

/**
 * Immutable, lightweight pixel buffer used as the internal bridge between
 * OpenCV Mat and JavaFX WritableImage.
 *
 * Pixel data is stored as a flat, row-major byte array in the same channel order
 * as the originating Mat: BGR for colour images, a single byte for grayscale,
 * or BGRA when an alpha channel is present. No colour-space conversion is applied.
 */
public class BufferedImage {

    private final int width;
    private final int height;

    /** Number of bytes per pixel: 1 = grayscale, 3 = BGR, 4 = BGRA. */
    private final int channels;

    /** Raw pixel data in row-major order, matching the source Mat's byte layout. */
    private final byte[] data;

    public BufferedImage(int width, int height, int channels, byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Pixel data must not be null");
        }
        this.width    = width;
        this.height   = height;
        this.channels = channels;
        // Defensive copy: prevents the caller from mutating our internal state
        // after construction, preserving the immutability contract.
        this.data = data.clone();
    }

    public int getWidth()    { return width; }
    public int getHeight()   { return height; }
    public int getChannels() { return channels; }

    /**
     * Returns a copy of the raw pixel bytes.
     * A copy is returned rather than the backing array so that callers cannot
     * accidentally corrupt the buffer — consistent with the "never modify the
     * original" rule for all image utilities in this package.
     */
    public byte[] getData() { return data.clone(); }
}
