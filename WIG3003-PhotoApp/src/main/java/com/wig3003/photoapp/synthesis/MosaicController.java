package com.wig3003.photoapp.synthesis;

import com.wig3003.photoapp.util.FavouritesManager;
import com.wig3003.photoapp.util.ImageUtils;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.opencv.core.Mat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MosaicController {

    // =========================================================
    // FXML — TOOLBAR
    // =========================================================

    @FXML private Label  mosaicTitle;
    @FXML private Label  progressLabel;
    @FXML private Button cancelBtn;
    @FXML private Button exportBtn;

    // =========================================================
    // FXML — CANVAS AREA
    // =========================================================

    @FXML private StackPane  mosaicCheckerPane;
    @FXML private ScrollPane mosaicScrollPane;
    @FXML private Canvas     checkerCanvas;
    @FXML private ImageView  mosaicView;
    @FXML private Label      placeholderLabel;
    @FXML private Label      gridInfoLabel;

    // =========================================================
    // FXML — RIGHT PANEL: TARGET
    // =========================================================

    @FXML private ImageView targetThumb;
    @FXML private Label     targetFilename;

    // =========================================================
    // FXML — RIGHT PANEL: TILE POOL
    // =========================================================

    @FXML private Label    tileCountLabel;
    @FXML private Label    tileSourceLabel;
    @FXML private TilePane tilePreviewPane;

    // =========================================================
    // FXML — RIGHT PANEL: GRID
    // =========================================================

    @FXML private Slider columnsSlider;
    @FXML private Label  columnsLabel;
    @FXML private Slider rowsSlider;
    @FXML private Label  rowsLabel;
    @FXML private Slider tileGapSlider;
    @FXML private Label  tileGapLabel;

    // =========================================================
    // FXML — RIGHT PANEL: GENERATE
    // =========================================================

    @FXML private Button generateBtn;

    // =========================================================
    // STATE
    // =========================================================

    private String       targetImagePath = null;
    private List<String> tilePaths       = new ArrayList<>();
    private List<String> libraryPaths    = new ArrayList<>();
    private String       lastMosaicPath  = null;
    private Thread       generationThread = null;

    /** Background executor for loading thumbnails — daemon so JVM exits cleanly. */
    private final ExecutorService thumbnailExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "mosaic-thumb");
                t.setDaemon(true);
                return t;
            });

    // =========================================================
    // INITIALIZE
    // =========================================================

    @FXML
    public void initialize() {
        // Slider listeners
        columnsSlider.valueProperty().addListener((obs, ov, nv) ->
                columnsLabel.setText(String.valueOf(nv.intValue())));

        rowsSlider.valueProperty().addListener((obs, ov, nv) ->
                rowsLabel.setText(String.valueOf(nv.intValue())));

        tileGapSlider.valueProperty().addListener((obs, ov, nv) ->
                tileGapLabel.setText(nv.intValue() + " px"));

        // Checkerboard canvas — bind size to the checker StackPane
        checkerCanvas.widthProperty().bind(mosaicCheckerPane.widthProperty());
        checkerCanvas.heightProperty().bind(mosaicCheckerPane.heightProperty());
        checkerCanvas.widthProperty().addListener( (o, ov, nv) -> drawChecker());
        checkerCanvas.heightProperty().addListener((o, ov, nv) -> drawChecker());

        // Pre-populate tile pool from favourites
        loadTilePoolFromFavourites();
    }

    // =========================================================
    // PUBLIC API
    // =========================================================

    /** Called by MainController when navigating to the Mosaic page. */
    public void setLibraryPaths(List<String> paths) {
        this.libraryPaths = paths != null ? new ArrayList<>(paths) : new ArrayList<>();
    }

    // =========================================================
    // TILE POOL LOADING
    // =========================================================

    private void loadTilePoolFromFavourites() {
        List<String> paths;
        try {
            paths = FavouritesManager.getFavourites();
        } catch (IOException e) {
            paths = new ArrayList<>();
        }
        applyTilePool(paths, "Favorites");
    }

    private void loadTilePoolFromLibrary() {
        applyTilePool(new ArrayList<>(libraryPaths), "All Library");
    }

    private void loadTilePoolFromDirectory(File dir) {
        List<String> paths = new ArrayList<>();
        File[] files = dir.listFiles(f -> f.isFile() && isImageFile(f.getName()));
        if (files != null) {
            for (File f : files) paths.add(f.getAbsolutePath());
        }
        applyTilePool(paths, dir.getName());
    }

    private void applyTilePool(List<String> paths, String sourceName) {
        tilePaths = paths;
        tileCountLabel.setText(paths.size() + " photo" + (paths.size() == 1 ? "" : "s"));
        tileSourceLabel.setText(sourceName);
        populateTilePreview(paths);
    }

    private void populateTilePreview(List<String> paths) {
        tilePreviewPane.getChildren().clear();
        int max = Math.min(21, paths.size());
        for (int i = 0; i < max; i++) {
            final String path = paths.get(i);
            StackPane cell = makeThumbnailCell();
            tilePreviewPane.getChildren().add(cell);

            thumbnailExecutor.submit(() -> {
                String uri = new File(path).toURI().toString();
                Image img = new Image(uri, 28, 28, false, true);
                Platform.runLater(() -> {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(28);
                    iv.setFitHeight(28);
                    iv.setPreserveRatio(false);
                    iv.setSmooth(true);
                    cell.getChildren().add(iv);
                });
            });
        }
    }

    private StackPane makeThumbnailCell() {
        StackPane cell = new StackPane();
        cell.setMinSize(28, 28);
        cell.setMaxSize(28, 28);
        cell.setPrefSize(28, 28);
        cell.setStyle("-fx-background-color:#ECE4D3; -fx-background-radius:4;");
        return cell;
    }

    // =========================================================
    // SELECT TARGET IMAGE
    // =========================================================

    @FXML
    private void handleSelectTarget() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select target image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp"));

        Stage stage = getStage();
        if (stage == null) return;

        File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        targetImagePath = file.getAbsolutePath();
        String filename = file.getName();
        targetFilename.setText(filename);
        mosaicTitle.setText("Mosaic - " + filename);
        updateGridInfoLabel();

        // Load thumbnail asynchronously
        thumbnailExecutor.submit(() -> {
            String uri = file.toURI().toString();
            Image img = new Image(uri, 40, 40, true, true);
            Platform.runLater(() -> targetThumb.setImage(img));
        });
    }

    // =========================================================
    // CHANGE TILE SOURCE
    // =========================================================

    @FXML
    private void handleChangeSource() {
        ButtonType favBtn    = new ButtonType("Favorites");
        ButtonType libBtn    = new ButtonType("All Library");
        ButtonType folderBtn = new ButtonType("Browse folder…");
        ButtonType cancelBt  = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert dialog = new Alert(Alert.AlertType.NONE,
                "Choose the source for tile images:",
                favBtn, libBtn, folderBtn, cancelBt);
        dialog.setTitle("Change tile source");
        dialog.setHeaderText(null);

        dialog.showAndWait().ifPresent(result -> {
            if (result == favBtn) {
                loadTilePoolFromFavourites();
            } else if (result == libBtn) {
                loadTilePoolFromLibrary();
            } else if (result == folderBtn) {
                DirectoryChooser dc = new DirectoryChooser();
                dc.setTitle("Select tile folder");
                Stage stage = getStage();
                if (stage == null) return;
                File dir = dc.showDialog(stage);
                if (dir != null) loadTilePoolFromDirectory(dir);
            }
        });
    }

    // =========================================================
    // GENERATE
    // =========================================================

    @FXML
    private void handleGenerate() {
        if (targetImagePath == null) {
            showError("No target image selected.", "Please select a target image first.");
            return;
        }
        if (tilePaths.isEmpty()) {
            showError("No tile images.", "Add images to your Favorites or choose a tile source.");
            return;
        }

        // Capture slider values on FX thread before going to background
        final int    columns       = Math.max(1, (int) columnsSlider.getValue());
        final int    rows          = Math.max(1, (int) rowsSlider.getValue());
        final String capturedTarget = targetImagePath;
        final List<String> capturedTiles = new ArrayList<>(tilePaths);

        // Update UI
        progressLabel.setText("Generating…");
        progressLabel.setVisible(true);
        progressLabel.setManaged(true);
        cancelBtn.setVisible(true);
        cancelBtn.setManaged(true);
        generateBtn.setDisable(true);

        generationThread = new Thread(() -> {
            try {
                // Load target briefly to get its dimensions for tileSize
                Mat target = ImageUtils.loadMatFromPath(capturedTarget);
                int targetWidth = target.cols();
                target.release();

                int tileSize = Math.max(10, targetWidth / columns);

                String resultPath = new MosaicGenerator()
                        .generateMosaic(capturedTiles, capturedTarget, tileSize);

                if (Thread.interrupted()) return;

                // Load result image for display
                Mat resultMat = ImageUtils.loadMatFromPath(resultPath);
                WritableImage wi = ImageUtils.matToWritableImage(resultMat);
                resultMat.release();

                final String finalPath    = resultPath;
                final int    totalTiles   = columns * rows;
                final String targetName   = new File(capturedTarget).getName();
                final String infoText     = columns + " × " + rows
                        + " grid · " + totalTiles + " tiles · target " + targetName;

                Platform.runLater(() -> {
                    lastMosaicPath = finalPath;
                    mosaicView.setImage(wi);
                    placeholderLabel.setVisible(false);
                    gridInfoLabel.setText(infoText);
                    resetGenerationUI();
                });

            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()
                        || e instanceof java.io.InterruptedIOException) {
                    Platform.runLater(this::resetGenerationUI);
                } else {
                    Platform.runLater(() -> {
                        showError("Generation failed.", e.getMessage() != null
                                ? e.getMessage() : e.getClass().getSimpleName());
                        resetGenerationUI();
                    });
                }
            }
        });
        generationThread.setDaemon(true);
        generationThread.setName("mosaic-generate");
        generationThread.start();
    }

    private void resetGenerationUI() {
        progressLabel.setVisible(false);
        progressLabel.setManaged(false);
        cancelBtn.setVisible(false);
        cancelBtn.setManaged(false);
        generateBtn.setDisable(false);
    }

    // =========================================================
    // CANCEL
    // =========================================================

    @FXML
    private void handleCancel() {
        if (generationThread != null && generationThread.isAlive()) {
            generationThread.interrupt();
        }
        resetGenerationUI();
        progressLabel.setText("");
    }

    // =========================================================
    // EXPORT
    // =========================================================

    @FXML
    private void handleExport() {
        if (lastMosaicPath == null) {
            showWarning("No mosaic to export.", "Generate a mosaic first.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export mosaic");
        chooser.setInitialFileName("mosaic_export.png");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG Image", "*.png"));

        Stage stage = getStage();
        if (stage == null) return;

        File dest = chooser.showSaveDialog(stage);
        if (dest == null) return;

        try {
            Files.copy(Paths.get(lastMosaicPath), dest.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            showInfo("Exported successfully.", "Saved to: " + dest.getAbsolutePath());
        } catch (IOException e) {
            showError("Export failed.", e.getMessage());
        }
    }

    // =========================================================
    // CHECKERBOARD
    // =========================================================

    private void drawChecker() {
        double w = checkerCanvas.getWidth();
        double h = checkerCanvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = checkerCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        int   tile  = 20;
        Color dark  = Color.web("#1a1a1a");
        Color light = Color.web("#2e2e2e");

        for (int r = 0; r * tile < h; r++) {
            for (int c = 0; c * tile < w; c++) {
                gc.setFill((r + c) % 2 == 0 ? dark : light);
                gc.fillRect(c * tile, r * tile, tile, tile);
            }
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private void updateGridInfoLabel() {
        if (targetImagePath != null) {
            String name = new File(targetImagePath).getName();
            int cols = (int) columnsSlider.getValue();
            int rows = (int) rowsSlider.getValue();
            gridInfoLabel.setText(cols + " × " + rows + " grid · target " + name);
        }
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg")  || lower.endsWith(".jpeg")
            || lower.endsWith(".png")  || lower.endsWith(".bmp")
            || lower.endsWith(".webp") || lower.endsWith(".tiff")
            || lower.endsWith(".tif");
    }

    private Stage getStage() {
        if (generateBtn == null || generateBtn.getScene() == null) return null;
        return (Stage) generateBtn.getScene().getWindow();
    }

    private void showError(String header, String body) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(header);
        a.setContentText(body);
        a.showAndWait();
    }

    private void showWarning(String header, String body) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setHeaderText(header);
        a.setContentText(body);
        a.showAndWait();
    }

    private void showInfo(String header, String body) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(header);
        a.setContentText(body);
        a.showAndWait();
    }
}
