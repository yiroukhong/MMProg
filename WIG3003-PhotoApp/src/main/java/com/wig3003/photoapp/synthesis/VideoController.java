package com.wig3003.photoapp.synthesis;

import com.wig3003.photoapp.util.FavouritesManager;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VideoController {

    // =========================================================
    // FXML — TOOLBAR
    // =========================================================

    @FXML private Label  videoTitle;
    @FXML private Label  timecodeLabel;
    @FXML private Button saveDraftBtn;
    @FXML private Button exportBtn;

    // =========================================================
    // FXML — CANVAS AREA
    // =========================================================

    @FXML private StackPane canvasArea;
    @FXML private ImageView previewView;
    @FXML private Label     placeholderLabel;
    @FXML private HBox      playbackBar;

    // =========================================================
    // FXML — PLAYBACK CONTROLS
    // =========================================================

    @FXML private Button prevBtn;
    @FXML private Button playPauseBtn;
    @FXML private Button nextBtn;
    @FXML private Label  playbackTimeLabel;
    @FXML private Slider seekSlider;

    // =========================================================
    // FXML — FILMSTRIP
    // =========================================================

    @FXML private Label stripInfoLabel;
    @FXML private HBox  filmstripBox;

    // =========================================================
    // FXML — RIGHT PANEL
    // =========================================================

    @FXML private Label        clipIndexLabel;
    @FXML private Label        durationValueLabel;
    @FXML private Slider       durationSlider;
    @FXML private ToggleButton transNone;
    @FXML private ToggleButton transFade;
    @FXML private ToggleButton transCross;
    @FXML private TextArea     overlayTextArea;
    @FXML private ToggleButton posTop;
    @FXML private ToggleButton posCenter;
    @FXML private ToggleButton posBottom;
    @FXML private Slider       textSizeSlider;
    @FXML private Label        textSizeLabel;
    @FXML private ToggleButton fps24;
    @FXML private ToggleButton fps30;
    @FXML private ToggleButton fps60;
    @FXML private Button       compileBtn;

    // =========================================================
    // STATE
    // =========================================================

    private List<String>             clipPaths         = new ArrayList<>();
    private int                      selectedClipIndex = -1;
    private String                   lastVideoPath     = null;
    private Thread                   compileThread     = null;
    private Timeline                 previewTimeline   = null;
    private int                      previewIndex      = 0;
    private final Map<Integer,String> clipOverlays     = new HashMap<>();

    // =========================================================
    // INITIALIZE
    // =========================================================

    @FXML
    public void initialize() {
        durationSlider.valueProperty().addListener((obs, ov, nv) -> {
            durationValueLabel.setText(String.format("%.1f s", nv.doubleValue()));
            updateStripInfo();
        });

        textSizeSlider.valueProperty().addListener((obs, ov, nv) ->
                textSizeLabel.setText(nv.intValue() + " px"));

        loadClipsFromFavourites();
    }

    // =========================================================
    // LOAD CLIPS
    // =========================================================

    private void loadClipsFromFavourites() {
        List<String> paths;
        try {
            paths = FavouritesManager.getFavourites();
        } catch (IOException e) {
            paths = new ArrayList<>();
        }
        clipPaths = new ArrayList<>(paths);
        rebuildFilmstrip();
        updateStripInfo();
        if (!clipPaths.isEmpty()) {
            handleClipSelected(0);
        }
    }

    private void rebuildFilmstrip() {
        filmstripBox.getChildren().clear();
        for (int i = 0; i < clipPaths.size(); i++) {
            filmstripBox.getChildren().add(buildClipCard(i, clipPaths.get(i)));
        }
        filmstripBox.getChildren().add(buildAddCard());
    }

    private StackPane buildClipCard(int index, String path) {
        StackPane card = new StackPane();
        card.setMinSize(120, 80);
        card.setMaxSize(120, 80);
        card.setPrefSize(120, 80);

        boolean selected = (index == selectedClipIndex);
        card.setStyle(
                "-fx-background-color:#ECE4D3;"
                + "-fx-background-radius:6;"
                + "-fx-cursor:hand;"
                + (selected ? "-fx-border-color:#B0432B;-fx-border-width:2;-fx-border-radius:6;" : "")
        );

        // Thumbnail loaded in background
        Thread t = new Thread(() -> {
            String uri = new File(path).toURI().toString();
            Image img = new Image(uri, 120, 80, false, true);
            Platform.runLater(() -> {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(120);
                iv.setFitHeight(80);
                iv.setPreserveRatio(false);
                iv.setSmooth(true);
                card.getChildren().add(0, iv);
            });
        });
        t.setDaemon(true);
        t.start();

        // Orange dot — visible when this clip has overlay text
        StackPane dot = new StackPane();
        dot.setMinSize(8, 8);
        dot.setMaxSize(8, 8);
        dot.setPrefSize(8, 8);
        dot.setStyle("-fx-background-color:#C8A93F; -fx-background-radius:999;");
        StackPane.setAlignment(dot, Pos.TOP_RIGHT);
        StackPane.setMargin(dot, new Insets(4, 4, 0, 0));
        boolean hasOverlay = !clipOverlays.getOrDefault(index, "").isEmpty();
        dot.setVisible(hasOverlay);
        dot.setManaged(hasOverlay);
        card.getChildren().add(dot);

        card.setOnMouseClicked(e -> handleClipSelected(index));
        return card;
    }

    private StackPane buildAddCard() {
        StackPane card = new StackPane();
        card.setMinSize(120, 80);
        card.setMaxSize(120, 80);
        card.setPrefSize(120, 80);
        card.setStyle(
                "-fx-background-color:transparent;"
                + "-fx-border-color:#DDD2BC;"
                + "-fx-border-width:1.5;"
                + "-fx-border-style:dashed;"
                + "-fx-border-radius:6;"
                + "-fx-cursor:hand;"
        );

        Label plus = new Label("+");
        plus.setStyle("-fx-font-size:24; -fx-text-fill:#9C907D;");
        card.getChildren().add(plus);
        card.setOnMouseClicked(e -> handleAddFromFavorites());
        return card;
    }

    private void updateStripInfo() {
        int n         = clipPaths.size();
        int totalSecs = (int) (n * durationSlider.getValue());
        int m         = totalSecs / 60;
        int s         = totalSecs % 60;
        stripInfoLabel.setText(String.format(
                "Strip · %d Clip%s · %d:%02d", n, n == 1 ? "" : "s", m, s));
    }

    // =========================================================
    // CLIP SELECTION
    // =========================================================

    private void handleClipSelected(int index) {
        // Persist overlay text for the previously selected clip
        if (selectedClipIndex >= 0 && overlayTextArea != null) {
            clipOverlays.put(selectedClipIndex, overlayTextArea.getText());
        }

        selectedClipIndex = index;
        clipIndexLabel.setText(String.format("CLIP · #%02d", index + 1));
        overlayTextArea.setText(clipOverlays.getOrDefault(index, ""));

        // Load preview in background
        if (index >= 0 && index < clipPaths.size()) {
            final String path = clipPaths.get(index);
            Thread t = new Thread(() -> {
                String uri = new File(path).toURI().toString();
                Image img  = new Image(uri, 0, 0, true, true);
                Platform.runLater(() -> {
                    previewView.setImage(img);
                    placeholderLabel.setVisible(false);
                    placeholderLabel.setManaged(false);
                });
            });
            t.setDaemon(true);
            t.start();
        }

        rebuildFilmstrip();
    }

    // =========================================================
    // ADD FROM FAVORITES
    // =========================================================

    private void handleAddFromFavorites() {
        List<String> favourites;
        try {
            favourites = FavouritesManager.getFavourites();
        } catch (IOException e) {
            showError("Could not load Favorites.", e.getMessage());
            return;
        }

        List<String> available = favourites.stream()
                .filter(p -> !clipPaths.contains(p))
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            showInfo("No new photos.", "All Favorites are already in the strip.");
            return;
        }

        List<String> names = available.stream()
                .map(p -> new File(p).getName())
                .collect(Collectors.toList());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Add from Favorites");
        dialog.setHeaderText("Choose a photo to add:");
        dialog.setContentText("Photo:");

        dialog.showAndWait().ifPresent(chosen -> {
            int idx = names.indexOf(chosen);
            if (idx >= 0) {
                clipPaths.add(available.get(idx));
                rebuildFilmstrip();
                updateStripInfo();
                handleClipSelected(clipPaths.size() - 1);
            }
        });
    }

    // =========================================================
    // TOOLBAR / STRIP ACTIONS
    // =========================================================

    @FXML
    private void handleAddText() {
        overlayTextArea.requestFocus();
    }

    // =========================================================
    // PLAYBACK
    // =========================================================

    @FXML
    private void handlePrev() {
        if (clipPaths.isEmpty()) return;
        handleClipSelected(Math.max(0, selectedClipIndex - 1));
    }

    @FXML
    private void handleNext() {
        if (clipPaths.isEmpty()) return;
        handleClipSelected(Math.min(clipPaths.size() - 1, selectedClipIndex + 1));
    }

    @FXML
    private void handlePlayPause() {
        if (clipPaths.isEmpty()) return;

        if (previewTimeline != null
                && previewTimeline.getStatus() == Timeline.Status.RUNNING) {
            previewTimeline.pause();
            playPauseBtn.setText("▶");
            return;
        }

        previewIndex = selectedClipIndex < 0 ? 0 : selectedClipIndex;
        double secPerClip = durationSlider.getValue();

        previewTimeline = new Timeline(new KeyFrame(Duration.seconds(secPerClip), e -> {
            previewIndex = (previewIndex + 1) % clipPaths.size();
            String uri = new File(clipPaths.get(previewIndex)).toURI().toString();
            previewView.setImage(new Image(uri, true));
            placeholderLabel.setVisible(false);
            placeholderLabel.setManaged(false);
            clipIndexLabel.setText(String.format("CLIP · #%02d", previewIndex + 1));

            int totalSecs  = (int) (clipPaths.size() * secPerClip);
            int elapsedSec = (int) (previewIndex * secPerClip);
            playbackTimeLabel.setText(formatTime(elapsedSec) + " / " + formatTime(totalSecs));
            seekSlider.setValue((double) elapsedSec / Math.max(1, totalSecs));
        }));
        previewTimeline.setCycleCount(Timeline.INDEFINITE);
        previewTimeline.play();
        playPauseBtn.setText("⏸");
    }

    // =========================================================
    // COMPILE
    // =========================================================

    @FXML
    private void handleCompile() {
        if (clipPaths.isEmpty()) {
            showError("No clips.", "Add photos from Favorites to build your video.");
            return;
        }

        int    durationPerPhoto = (int) durationSlider.getValue();
        String overlayText      = overlayTextArea.getText();
        String outputDir        = "data/output";

        new File(outputDir).mkdirs();

        compileBtn.setDisable(true);
        saveDraftBtn.setText("Compiling…");
        saveDraftBtn.setVisible(true);
        saveDraftBtn.setManaged(true);

        List<String> paths = new ArrayList<>(clipPaths);

        compileThread = new Thread(() -> {
            try {
                String resultPath = new VideoCompiler()
                        .compileVideo(paths, durationPerPhoto, overlayText, outputDir);

                Platform.runLater(() -> {
                    lastVideoPath = resultPath;
                    timecodeLabel.setText(formatTime(paths.size() * durationPerPhoto));
                    resetCompileUI();
                    showInfo("Video compiled.", "Click Export to save the file.");
                });

            } catch (IllegalArgumentException | IOException e) {
                Platform.runLater(() -> {
                    showError("Compile failed.",
                            e.getMessage() != null ? e.getMessage()
                                                   : e.getClass().getSimpleName());
                    resetCompileUI();
                });
            }
        });
        compileThread.setDaemon(true);
        compileThread.setName("video-compile");
        compileThread.start();
    }

    private void resetCompileUI() {
        compileBtn.setDisable(false);
        saveDraftBtn.setText("Save draft");
        saveDraftBtn.setVisible(false);
        saveDraftBtn.setManaged(false);
    }

    // =========================================================
    // SAVE DRAFT / EXPORT
    // =========================================================

    @FXML
    private void handleSaveDraft() {
        showInfo("Draft saved.", "Your clip list has been noted.");
    }

    @FXML
    private void handleExport() {
        if (lastVideoPath == null) {
            showWarning("Nothing to export.", "Compile the video first.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export video");
        chooser.setInitialFileName("video_export.avi");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("AVI Video", "*.avi"));

        Stage stage = getStage();
        if (stage == null) return;

        File dest = chooser.showSaveDialog(stage);
        if (dest == null) return;

        try {
            Files.copy(Paths.get(lastVideoPath), dest.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            showInfo("Exported.", "Saved to: " + dest.getAbsolutePath());
            new MediaPlayerController().launchPlayer(dest.getAbsolutePath());
        } catch (IOException e) {
            showError("Export failed.", e.getMessage());
        }
    }

    // =========================================================
    // PUBLIC API
    // =========================================================

    public void setClipPaths(List<String> paths) {
        clipPaths = paths != null ? new ArrayList<>(paths) : new ArrayList<>();
        clipOverlays.clear();
        selectedClipIndex = -1;
        rebuildFilmstrip();
        updateStripInfo();
        if (!clipPaths.isEmpty()) {
            handleClipSelected(0);
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String formatTime(int totalSeconds) {
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private Stage getStage() {
        if (compileBtn == null || compileBtn.getScene() == null) return null;
        return (Stage) compileBtn.getScene().getWindow();
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
