package com.wig3003.photoapp.synthesis;

import com.wig3003.photoapp.util.ImageUtils;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Winnie — Multimedia Synthesis
 * Lays every tile image in a grid. No target image; every supplied photo
 * appears exactly once.
 */
public class MosaicGenerator {

    /**
     * Generates a mosaic that contains every image in {@code tilePaths}.
     *
     * @param tilePaths  paths to the tile images (all will be used)
     * @param columns    number of columns in the grid (≥ 1)
     * @param tileSize   pixel width and height of each cell (≥ 10)
     * @param tileGap    gap in pixels between cells (≥ 0)
     * @return           absolute path to the saved PNG
     */
    public String generateMosaic(List<String> tilePaths,
                                 int columns,
                                 int tileSize,
                                 int tileGap) throws IOException {
        if (tilePaths == null || tilePaths.isEmpty()) {
            throw new IllegalArgumentException("tilePaths must not be empty");
        }
        columns  = Math.max(1, columns);
        tileSize = Math.max(10, tileSize);
        tileGap  = Math.max(0, tileGap);

        int n    = tilePaths.size();
        int rows = (int) Math.ceil((double) n / columns);

        int stride   = tileSize + tileGap;
        int canvasW  = columns * tileSize + Math.max(0, columns - 1) * tileGap;
        int canvasH  = rows    * tileSize + Math.max(0, rows    - 1) * tileGap;

        // Load and resize all tile images
        List<Mat> tiles = new ArrayList<>(n);
        for (String p : tilePaths) {
            Mat raw     = ImageUtils.loadMatFromPath(p);
            Mat resized = new Mat();
            Imgproc.resize(raw, resized, new Size(tileSize, tileSize));
            raw.release();
            tiles.add(resized);
        }

        // Dark background (#1F1B16 → BGR 22, 27, 31)
        Mat mosaic = new Mat(canvasH, canvasW, CvType.CV_8UC3, new Scalar(22, 27, 31));

        for (int i = 0; i < tiles.size(); i++) {
            int c = i % columns;
            int r = i / columns;
            int x = c * stride;
            int y = r * stride;
            tiles.get(i).copyTo(mosaic.submat(new Rect(x, y, tileSize, tileSize)));
        }

        for (Mat t : tiles) t.release();

        Files.createDirectories(Paths.get("data/output"));
        String filename = "mosaic_" + System.currentTimeMillis() + ".png";
        String outPath  = "data/output/" + filename;
        Imgcodecs.imwrite(outPath, mosaic);
        mosaic.release();
        return outPath;
    }
}
