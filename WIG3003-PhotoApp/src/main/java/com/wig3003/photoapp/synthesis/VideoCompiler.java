package com.wig3003.photoapp.synthesis;

import com.wig3003.photoapp.util.ImageUtils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoWriter;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Winnie — Multimedia Synthesis
 * Implementation of the Video Compiler as per Contract §6.
 */
public class VideoCompiler {

    /**
     * Compiles a list of images into an AVI video with text overlays.
     */
    public String compileVideo(List<String> imagePaths, int durationPerPhoto, String overlayText, String outputDir) {
        // 1. Requirement check: Validate inputs
        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new IllegalArgumentException("imagePaths must not be empty"); // 
        }
        if (durationPerPhoto <= 0) {
            throw new IllegalArgumentException("durationPerPhoto must be > 0"); // 
        }
        
        // Note: imagePaths is used exactly in the order received. Do NOT re-sort. [cite: 69, 104]

        // 2. Set up VideoWriter
        String filename = "video_" + System.currentTimeMillis() + ".avi"; // [cite: 67, 130]
        String outPath = outputDir + File.separator + filename; // [cite: 67]
        
        VideoWriter writer = new VideoWriter(); // [cite: 68]
        int fourcc = VideoWriter.fourcc('X', 'V', 'I', 'D'); // 
        Size frameSize = new Size(1280, 720); // [cite: 68]
        
        writer.open(outPath, fourcc, 25.0, frameSize, true); // [cite: 68]

        // 3. For each image in ordered list
        for (String imgPath : imagePaths) { // [cite: 68]
            // a. Load via ImageUtils and resize to 1280x720 uniformly
            Mat frame = ImageUtils.loadMatFromPath(imgPath); // [cite: 68, 80]
            Imgproc.resize(frame, frame, frameSize); // 

            // b. Convert Mat to BufferedImage for Graphics2D processing
            BufferedImage bi = ImageUtils.matToBufferedImage(frame); // [cite: 68, 81]

            // c. Draw overlay text via Graphics2D
            Graphics2D g2d = bi.createGraphics(); // [cite: 68]
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // [cite: 68]
            g2d.setFont(new Font("Arial", Font.BOLD, 32)); // [cite: 68]
            g2d.setColor(Color.WHITE); // 
            
            FontMetrics fm = g2d.getFontMetrics(); // [cite: 68]
            int textX = (1280 - fm.stringWidth(overlayText)) / 2; // 
            int textY = 720 - 40; // bottom-center 
            
            g2d.drawString(overlayText, textX, textY); // 
            g2d.dispose(); // [cite: 68]

            // d. Convert back to Mat and write frames
            Mat overlaidFrame = ImageUtils.bufferedImageToMat(bi); // [cite: 68, 82, 86]
            
            int totalFrames = durationPerPhoto * 25; // 
            for (int f = 0; f < totalFrames; f++) { // [cite: 68]
                writer.write(overlaidFrame); // [cite: 68]
            }
        }

        writer.release(); // [cite: 69]
        return outPath; // [cite: 69]
    }
}