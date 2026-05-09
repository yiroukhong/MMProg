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
import java.awt.image.DataBufferByte;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class VideoCompiler {

    public String compileVideo(
            List<String> imagePaths,
            int durationPerPhoto,
            String overlayText,
            String outputDir) throws IOException {

        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new IllegalArgumentException("imagePaths must not be empty");
        }

        if (durationPerPhoto <= 0) {
            throw new IllegalArgumentException("durationPerPhoto must be > 0");
        }

        String filename = "video_" + System.currentTimeMillis() + ".avi";

        String outPath = outputDir + File.separator + filename;

        VideoWriter writer = new VideoWriter();

        int fourcc = VideoWriter.fourcc('X', 'V', 'I', 'D');

        Size frameSize = new Size(1280, 720);

        writer.open(outPath, fourcc, 25.0, frameSize, true);

        for (String imgPath : imagePaths) {

            Mat frame = ImageUtils.loadMatFromPath(imgPath);

            Imgproc.resize(frame, frame, frameSize);

            // CUSTOM BufferedImage from teammate's utility
            com.wig3003.photoapp.util.BufferedImage customBi =
                    ImageUtils.matToBufferedImage(frame);

            // Convert to STANDARD Java BufferedImage
            java.awt.image.BufferedImage javaBi =
                    new java.awt.image.BufferedImage(
                            customBi.getWidth(),
                            customBi.getHeight(),
                            java.awt.image.BufferedImage.TYPE_3BYTE_BGR
                    );

            // Copy raw bytes
            byte[] customData = customBi.getData();

            byte[] javaData =
                    ((DataBufferByte) javaBi.getRaster()
                            .getDataBuffer()).getData();

            System.arraycopy(
                    customData,
                    0,
                    javaData,
                    0,
                    customData.length
            );

            // Draw overlay text
            Graphics2D g2d = javaBi.createGraphics();

            g2d.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2d.setFont(new Font("Arial", Font.BOLD, 32));

            g2d.setColor(Color.WHITE);

            FontMetrics fm = g2d.getFontMetrics();

            int textX =
                    (1280 - fm.stringWidth(overlayText)) / 2;

            int textY = 720 - 40;

            g2d.drawString(
                    overlayText,
                    textX,
                    textY
            );

            g2d.dispose();

            // Convert BACK to custom BufferedImage
            com.wig3003.photoapp.util.BufferedImage modifiedCustomBi =
                    new com.wig3003.photoapp.util.BufferedImage(
                            javaBi.getWidth(),
                            javaBi.getHeight(),
                            3,
                            javaData
                    );

            // Convert to OpenCV Mat
            Mat overlaidFrame =
                    ImageUtils.bufferedImageToMat(modifiedCustomBi);

            int totalFrames = durationPerPhoto * 25;

            for (int f = 0; f < totalFrames; f++) {
                writer.write(overlaidFrame);
            }
        }

        writer.release();

        return outPath;
    }
}