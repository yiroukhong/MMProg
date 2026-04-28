package com.wig3003.photoapp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;

import javafx.application.Platform;
import javafx.scene.image.WritableImage;

class ImageUtilsTest {

    @BeforeAll
    static void setup() {
        // Load the OpenCV native library that openpnp bundles into the JAR.
        // This must happen once before any Mat or Imgcodecs call.
        nu.pattern.OpenCV.loadShared();

        // Initialise the JavaFX toolkit so WritableImage / PixelWriter can be
        // constructed. Platform.startup() is a no-op if called a second time,
        // so the catch handles the "already started" case in IDE test runners.
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Already initialised — safe to continue
        }
        // Prevent the JVM from exiting when no FX windows are open
        Platform.setImplicitExit(false);
    }

    // =========================================================================
    // loadMatFromPath
    // =========================================================================

    @Test
    void loadMatFromPath_nullPath_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageUtils.loadMatFromPath(null));
    }

    @Test
    void loadMatFromPath_emptyPath_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageUtils.loadMatFromPath(""));
    }

    @Test
    void loadMatFromPath_nonExistentFile_throwsIOException() {
        assertThrows(IOException.class,
                () -> ImageUtils.loadMatFromPath("C:/does/not/exist/image.png"));
    }

    @Test
    void loadMatFromPath_validPngFile_returnsBGRMat(@TempDir Path tempDir) throws IOException {
        // Write a small solid-colour image to disk so we have a real file to load
        Mat source = new Mat(4, 6, CvType.CV_8UC3, new Scalar(100, 150, 200));
        String path = tempDir.resolve("test.png").toString();
        Imgcodecs.imwrite(path, source);

        Mat loaded = ImageUtils.loadMatFromPath(path);

        assertFalse(loaded.empty());
        assertEquals(4, loaded.rows());
        assertEquals(6, loaded.cols());
        assertEquals(3, loaded.channels()); // always BGR on Windows
    }

    // =========================================================================
    // matToBufferedImage
    // =========================================================================

    @Test
    void matToBufferedImage_nullMat_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageUtils.matToBufferedImage(null));
    }

    @Test
    void matToBufferedImage_emptyMat_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageUtils.matToBufferedImage(new Mat()));
    }

    @Test
    void matToBufferedImage_bgrMat_extractsCorrectDimensionsAndBytes() {
        // 2×2 BGR mat — each pixel has distinct B, G, R values so we can verify
        // that the byte order is preserved exactly as-is from OpenCV
        Mat mat = new Mat(2, 2, CvType.CV_8UC3);
        byte[] expected = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100, (byte) 110, (byte) 120};
        mat.put(0, 0, expected);

        BufferedImage bi = ImageUtils.matToBufferedImage(mat);

        assertEquals(2, bi.getWidth());
        assertEquals(2, bi.getHeight());
        assertEquals(3, bi.getChannels());
        assertArrayEquals(expected, bi.getData());
    }

    @Test
    void matToBufferedImage_grayscaleMat_extractsSingleChannelBytes() {
        Mat mat = new Mat(1, 3, CvType.CV_8UC1);
        byte[] expected = {(byte) 50, (byte) 128, (byte) 200};
        mat.put(0, 0, expected);

        BufferedImage bi = ImageUtils.matToBufferedImage(mat);

        assertEquals(1, bi.getChannels());
        assertArrayEquals(expected, bi.getData());
    }

    // =========================================================================
    // bufferedImageToMat
    // =========================================================================

    @Test
    void bufferedImageToMat_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageUtils.bufferedImageToMat(null));
    }

    @Test
    void bufferedImageToMat_emptyData_throwsIllegalArgumentException() {
        BufferedImage empty = new BufferedImage(0, 0, 3, new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> ImageUtils.bufferedImageToMat(empty));
    }

    @Test
    void bufferedImageToMat_unsupportedChannelCount_throwsIllegalArgumentException() {
        // 2-channel Mats are uncommon in OpenCV; the method must reject them clearly
        BufferedImage bi = new BufferedImage(1, 1, 2, new byte[]{10, 20});
        assertThrows(IllegalArgumentException.class,
                () -> ImageUtils.bufferedImageToMat(bi));
    }

    @Test
    void bufferedImageToMat_bgrData_rebuildsMatWithCorrectTypeAndPixels() {
        byte[] pixels = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100, (byte) 110, (byte) 120};
        BufferedImage bi = new BufferedImage(2, 2, 3, pixels);

        Mat mat = ImageUtils.bufferedImageToMat(bi);

        assertEquals(2, mat.rows());
        assertEquals(2, mat.cols());
        assertEquals(CvType.CV_8UC3, mat.type());
        byte[] readBack = new byte[pixels.length];
        mat.get(0, 0, readBack);
        assertArrayEquals(pixels, readBack);
    }

    @Test
    void bufferedImageToMat_grayscaleData_producesSingleChannelMat() {
        byte[] pixels = {(byte) 80, (byte) 160};
        BufferedImage bi = new BufferedImage(2, 1, 1, pixels);

        Mat mat = ImageUtils.bufferedImageToMat(bi);

        assertEquals(CvType.CV_8UC1, mat.type());
        byte[] readBack = new byte[2];
        mat.get(0, 0, readBack);
        assertArrayEquals(pixels, readBack);
    }

    // =========================================================================
    // matToBufferedImage ↔ bufferedImageToMat roundtrip
    // =========================================================================

    @Test
    void bufferedImageRoundtrip_allPixelValuesPreserved() {
        // Fill a 3×3 BGR mat with incrementing values so every byte is distinct
        Mat original = new Mat(3, 3, CvType.CV_8UC3);
        byte[] originalPixels = new byte[3 * 3 * 3];
        for (int i = 0; i < originalPixels.length; i++) {
            originalPixels[i] = (byte) (i * 7);
        }
        original.put(0, 0, originalPixels);

        Mat reconstructed = ImageUtils.bufferedImageToMat(
                ImageUtils.matToBufferedImage(original));

        byte[] result = new byte[originalPixels.length];
        reconstructed.get(0, 0, result);
        assertArrayEquals(originalPixels, result,
                "Every pixel must survive the Mat→BufferedImage→Mat roundtrip unchanged");
    }

    // =========================================================================
    // matToWritableImage
    // =========================================================================

    @Test
    void matToWritableImage_nullMat_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageUtils.matToWritableImage(null));
    }

    @Test
    void matToWritableImage_emptyMat_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageUtils.matToWritableImage(new Mat()));
    }

    @Test
    void matToWritableImage_bgrMat_producesCorrectWidthAndHeight() {
        // 5 rows × 8 cols → WritableImage width=8, height=5
        Mat mat = new Mat(5, 8, CvType.CV_8UC3, new Scalar(0, 128, 255));

        WritableImage result = ImageUtils.matToWritableImage(mat);

        assertEquals(8, (int) result.getWidth());
        assertEquals(5, (int) result.getHeight());
    }

    @Test
    void matToWritableImage_grayscaleMat_producesCorrectDimensions() {
        Mat mat = new Mat(3, 4, CvType.CV_8UC1, new Scalar(200));

        WritableImage result = ImageUtils.matToWritableImage(mat);

        assertEquals(4, (int) result.getWidth());
        assertEquals(3, (int) result.getHeight());
    }

    // =========================================================================
    // writableImageToMat
    // =========================================================================

    @Test
    void writableImageToMat_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ImageUtils.writableImageToMat(null));
    }

    @Test
    void writableImageToMat_validImage_returnsBGR3ChannelMat() {
        // A blank WritableImage is still a valid image with known zero pixels
        WritableImage image = new WritableImage(4, 3);

        Mat mat = ImageUtils.writableImageToMat(image);

        assertEquals(3, mat.rows());
        assertEquals(4, mat.cols());
        assertEquals(3, mat.channels()); // must be BGR, not BGRA
        assertEquals(CvType.CV_8UC3, mat.type());
    }

    // =========================================================================
    // matToWritableImage ↔ writableImageToMat roundtrip
    // =========================================================================

    @Test
    void writableImageRoundtrip_bgrPixelValuesPreserved() {
        // 2×2 mat with known pixel values — alpha is always set to 255 (fully opaque),
        // so the premultiplied internal storage in WritableImage is lossless
        Mat original = new Mat(2, 2, CvType.CV_8UC3);
        byte[] pixels = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100, (byte) 110, (byte) 120};
        original.put(0, 0, pixels);

        WritableImage wi = ImageUtils.matToWritableImage(original);
        Mat recovered    = ImageUtils.writableImageToMat(wi);

        byte[] result = new byte[pixels.length];
        recovered.get(0, 0, result);
        assertArrayEquals(pixels, result,
                "BGR values must survive the Mat→WritableImage→Mat roundtrip unchanged");
    }
}
