package com.wig3003.photoapp.util;

import java.io.IOException;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

public class ImageUtils {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Loads an image from disk into an OpenCV Mat.
     *
     * Imgcodecs.imread always decodes to BGR on Windows regardless of the
     * on-disk format (JPEG, PNG, BMP, etc.), so the returned Mat is always BGR.
     *
     * imread does NOT throw when a file is missing — it silently returns an empty
     * Mat. We detect that and surface a meaningful IOException so callers do not
     * have to inspect the Mat themselves.
     *
     * @param absolutePath full path to the image file
     * @throws IllegalArgumentException if the path is null or blank
     * @throws IOException              if the file does not exist or cannot be decoded
     */
    public static Mat loadMatFromPath(String absolutePath) throws IOException {
        if (absolutePath == null || absolutePath.isEmpty()) {
            throw new IllegalArgumentException("Path must not be null or empty");
        }

        Mat mat = Imgcodecs.imread(absolutePath);

        if (mat.empty()) {
            throw new IOException("Image not found or could not be decoded: " + absolutePath);
        }

        return mat;
    }

    /**
     * Converts an OpenCV Mat to a JavaFX WritableImage for display in an ImageView.
     *
     * JavaFX's PixelWriter consumes pixels in BGRA byte order, which is a natural
     * extension of OpenCV's BGR format. The conversion only needs to insert a
     * full-opacity (255) alpha byte for each pixel — no colour values are altered.
     *
     * The method routes through matToBufferedImage so that the byte-extraction
     * logic lives in one place and can be tested independently.
     *
     * @throws IllegalArgumentException if mat is null or empty
     */
    public static WritableImage matToWritableImage(Mat mat) throws IllegalArgumentException {
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("Input Mat must not be null or empty");
        }

        // Use the internal bridge rather than duplicating byte-extraction code.
        BufferedImage bi = matToBufferedImage(mat);
        return bridgeToWritableImage(bi);
    }

    /**
     * Converts a JavaFX WritableImage back into an OpenCV Mat for processing.
     *
     * PixelReader exposes pixels in BGRA byte order, which matches OpenCV's BGR
     * layout once the alpha channel is stripped. A new Mat is always returned —
     * the source WritableImage is never modified.
     *
     * NOTE: This method reads pixel data via PixelReader, which must be called on
     * or scheduled from the JavaFX Application Thread. Callers are responsible for
     * thread safety.
     *
     * @throws IllegalArgumentException if image is null or has zero dimensions
     */
    public static Mat writableImageToMat(WritableImage image) throws IllegalArgumentException {
        if (image == null) {
            throw new IllegalArgumentException("Input WritableImage must not be null");
        }

        int width  = (int) image.getWidth();
        int height = (int) image.getHeight();

        if (width == 0 || height == 0) {
            throw new IllegalArgumentException("Input WritableImage must not be empty (zero dimensions)");
        }

        // Read all pixels in one call. BGRA is chosen because it matches OpenCV's
        // byte order exactly — we only need to drop the alpha byte, not reorder channels.
        byte[] bgraData = new byte[width * height * 4];
        image.getPixelReader().getPixels(
                0, 0, width, height,
                PixelFormat.getByteBgraInstance(),
                bgraData, 0, width * 4);

        // Strip the alpha byte to produce BGR. OpenCV does not use an alpha channel
        // in its standard 3-channel format, and retaining it would mis-index pixels.
        byte[] bgrData = new byte[width * height * 3];
        for (int src = 0, dst = 0; src < bgraData.length; src += 4, dst += 3) {
            bgrData[dst]     = bgraData[src];     // B
            bgrData[dst + 1] = bgraData[src + 1]; // G
            bgrData[dst + 2] = bgraData[src + 2]; // R
            // bgraData[src + 3] = alpha — intentionally discarded
        }

        BufferedImage bi = new BufferedImage(width, height, 3, bgrData);
        return bufferedImageToMat(bi);
    }

    /**
     * Extracts the raw pixel bytes from a Mat into an immutable BufferedImage.
     *
     * The byte layout mirrors the Mat exactly — BGR for colour images, a single
     * byte for grayscale — so no colour-space conversion is performed here.
     * This keeps the bridge type format-agnostic and lets callers inspect the
     * raw data without going through JavaFX.
     *
     * @throws IllegalArgumentException if mat is null or empty
     */
    public static BufferedImage matToBufferedImage(Mat mat) throws IllegalArgumentException {
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("Input Mat must not be null or empty");
        }

        int width    = mat.cols();
        int height   = mat.rows();
        int channels = mat.channels();

        byte[] data = new byte[width * height * channels];
        mat.get(0, 0, data); // bulk copy: faster than per-pixel access

        return new BufferedImage(width, height, channels, data);
    }

    /**
     * Rebuilds an OpenCV Mat from the raw bytes stored in a BufferedImage.
     *
     * The channel count in the BufferedImage determines the OpenCV Mat type:
     *   1 channel → CV_8UC1 (grayscale)
     *   3 channels → CV_8UC3 (BGR)
     *   4 channels → CV_8UC4 (BGRA)
     *
     * @throws IllegalArgumentException if bi is null, carries no data,
     *                                  or has an unsupported channel count
     */
    public static Mat bufferedImageToMat(BufferedImage bi) throws IllegalArgumentException {
        if (bi == null) {
            throw new IllegalArgumentException("Input BufferedImage must not be null");
        }

        byte[] data = bi.getData();

        if (data.length == 0) {
            throw new IllegalArgumentException("Input BufferedImage must not be empty");
        }

        // Map channel count to the corresponding OpenCV Mat type constant.
        int cvType;
        switch (bi.getChannels()) {
            case 1: cvType = CvType.CV_8UC1; break;
            case 3: cvType = CvType.CV_8UC3; break;
            case 4: cvType = CvType.CV_8UC4; break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported channel count: " + bi.getChannels() + " (expected 1, 3, or 4)");
        }

        Mat mat = new Mat(bi.getHeight(), bi.getWidth(), cvType);
        mat.put(0, 0, data);
        return mat;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a BufferedImage (BGR / grayscale bytes) to a JavaFX WritableImage.
     *
     * JavaFX's PixelFormat.getByteBgraInstance() expects bytes in B-G-R-A order,
     * which maps directly to OpenCV's B-G-R order. We only need to inject a
     * full-opacity alpha byte — the colour values are copied verbatim, so the
     * round-trip Mat → WritableImage → Mat is lossless for 8-bit images.
     */
    private static WritableImage bridgeToWritableImage(BufferedImage bi) {
        int width    = bi.getWidth();
        int height   = bi.getHeight();
        int channels = bi.getChannels();
        byte[] src   = bi.getData();

        byte[] bgraData;

        if (channels == 3) {
            // BGR → BGRA: copy each pixel and append a fully-opaque alpha byte.
            bgraData = new byte[width * height * 4];
            for (int s = 0, d = 0; s < src.length; s += 3, d += 4) {
                bgraData[d]     = src[s];     // B
                bgraData[d + 1] = src[s + 1]; // G
                bgraData[d + 2] = src[s + 2]; // R
                bgraData[d + 3] = (byte) 255; // A — fully opaque
            }
        } else if (channels == 1) {
            // Grayscale → BGRA: replicate the luminance value into all three
            // colour channels so the image displays correctly in colour space.
            bgraData = new byte[width * height * 4];
            for (int s = 0, d = 0; s < src.length; s++, d += 4) {
                bgraData[d]     = src[s]; // B = grey
                bgraData[d + 1] = src[s]; // G = grey
                bgraData[d + 2] = src[s]; // R = grey
                bgraData[d + 3] = (byte) 255; // A — fully opaque
            }
        } else if (channels == 4) {
            // Already BGRA — hand the buffer directly to the PixelWriter.
            bgraData = src;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported channel count: " + channels + " (expected 1, 3, or 4)");
        }

        WritableImage writableImage = new WritableImage(width, height);
        PixelWriter writer = writableImage.getPixelWriter();
        writer.setPixels(0, 0, width, height,
                PixelFormat.getByteBgraInstance(),
                bgraData, 0, width * 4);

        return writableImage;
    }
}
