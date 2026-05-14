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
 * Implementation of the Mosaic Generator as per Contract §6[cite: 3, 7].
 */
public class MosaicGenerator {

    /**
     * Generates a mosaic from a target image and a pool of tiles[cite: 8].
     */
    public String generateMosaic(List<String> tilePaths, String targetPath, int tileSize) throws IOException {
        // 1. Requirement check: Validate inputs 
        if (tilePaths == null || tilePaths.isEmpty()) {
            throw new IllegalArgumentException("tilePaths must not be empty");
        }
        if (tileSize < 10) {
            throw new IllegalArgumentException("tileSize must be >= 10");
        }

        // 2. Load target image via ImageUtils [cite: 8, 80]
        Mat target = ImageUtils.loadMatFromPath(targetPath);
        
        // 3. Compute grid dimensions [cite: 8]
        int cols = target.cols() / tileSize;
        int rows = target.rows() / tileSize;

        // 4. Load and resize all tile images to tileSize x tileSize [cite: 8]
        List<Mat> tiles = new ArrayList<>();
        for (String p : tilePaths) {
            Mat t = ImageUtils.loadMatFromPath(p);
            Mat resized = new Mat();
            Imgproc.resize(t, resized, new Size(tileSize, tileSize));
            tiles.add(resized);
        }

        // 5. Build mosaic Mat [cite: 8]
        Mat mosaic = Mat.zeros(rows * tileSize, cols * tileSize, CvType.CV_8UC3);

        // 6. Build the grid and copy tiles [cite: 17, 18, 28]
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Rect roi = new Rect(c * tileSize, r * tileSize, tileSize, tileSize);
                Mat region = target.submat(roi);
                
                // Use helper to find the best-matching tile [cite: 26]
                Mat tile = bestMatchingTile(region, tiles);
                
                // Copy tile into the correct submat ROI [cite: 28]
                tile.copyTo(mosaic.submat(roi));
            }
        }

        // 7. Save mosaic to data/output/ [cite: 31-39, 63]
        Files.createDirectories(Paths.get("data/output")); // Ensure dir exists 
        String filename = "mosaic_" + System.currentTimeMillis() + ".png";
        String outPath = "data/output/" + filename;
        
        Imgcodecs.imwrite(outPath, mosaic);
        return outPath;
    }

    /**
     * Finds the best matching tile using BGR Euclidean distance [cite: 9, 49-51].
     */
    private Mat bestMatchingTile(Mat region, List<Mat> tiles) {
        // Compute mean BGR of target region [cite: 40-43]
        Scalar regionMean = Core.mean(region);
        
        Mat bestTile = tiles.get(0);
        double bestDist = Double.MAX_VALUE;

        for (Mat tile : tiles) {
            Scalar tileMean = Core.mean(tile); // [cite: 48]    
            
            // Euclidean distance in BGR space [cite: 51-57]
            double dist = Math.sqrt(
                Math.pow(regionMean.val[0] - tileMean.val[0], 2) +
                Math.pow(regionMean.val[1] - tileMean.val[1], 2) +
                Math.pow(regionMean.val[2] - tileMean.val[2], 2)
            );

            if (dist < bestDist) {
                bestDist = dist;
                bestTile = tile;
            }
        }
        return bestTile; // [cite: 63]
    }
}