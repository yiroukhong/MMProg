# Inter-Module Interface Contracts
**Project:** WIG3003-PhotoApp  
**Version:** 1.0 | April 2026  
**Maintained by:** Sam  
**Status:** APPROVED — binding for all team members

---

## How to Use This Document

- These contracts define **what each module exposes** and **what other modules may call**.
- Do **not** change a method signature without notifying the team and updating this document.
- If your module depends on another, code against the signature here — do not wait for the implementation.
- All exceptions must be handled gracefully in the UI layer (show error dialog, never crash).

---

## Contract 1: `ImageUtils.java`

**Owner:** Yirou  
**Location:** `src/main/java/com/wig3003/photoapp/util/ImageUtils.java`  
**Depends on:** Nothing — must be stable before all other modules begin.

### Method Signatures

```java
public class ImageUtils {

    // Load image from absolute file path into OpenCV Mat
    public static Mat loadMatFromPath(String absolutePath) throws IOException;

    // Convert OpenCV Mat to JavaFX WritableImage (for display in ImageView)
    public static WritableImage matToWritableImage(Mat mat) throws IllegalArgumentException;

    // Convert JavaFX WritableImage to OpenCV Mat (for processing UI-selected image)
    public static Mat writableImageToMat(WritableImage image) throws IllegalArgumentException;

    // Convert OpenCV Mat to Java BufferedImage (internal bridge step)
    public static BufferedImage matToBufferedImage(Mat mat) throws IllegalArgumentException;

    // Convert Java BufferedImage to OpenCV Mat (internal bridge step)
    public static Mat bufferedImageToMat(BufferedImage bi) throws IllegalArgumentException;
}
```

### Rules

| Rule | Detail |
|------|--------|
| Mat color format | Always **BGR** — OpenCV default on Windows |
| Null input | All methods throw `IllegalArgumentException` if input is null or empty Mat |
| File not found | `loadMatFromPath` throws `IOException` |
| Thread safety | Caller is responsible for running off the JavaFX thread |
| Original | Never modify the input — always return a new object |

### Who Uses What

| Member | Methods Called |
|--------|---------------|
| Emily | `matToWritableImage`, `writableImageToMat` |
| Chyntia | `matToWritableImage`, `writableImageToMat` |
| Winnie | `matToBufferedImage`, `bufferedImageToMat`, `loadMatFromPath` |
| Sam | `loadMatFromPath` |

---

## Contract 2: `MetadataStore.java`

**Owner:** Sam  
**Location:** `src/main/java/com/wig3003/photoapp/util/MetadataStore.java`  
**Depends on:** Nothing.

### Method Signatures

```java
public class MetadataStore {

    // Save or update annotation for an image (keyed by absolute file path)
    public static void saveAnnotation(String absolutePath, String annotation) throws IOException;

    // Retrieve annotation for an image. Returns null if no annotation exists.
    public static String getAnnotation(String absolutePath) throws IOException;

    // Delete annotation for an image
    public static void deleteAnnotation(String absolutePath) throws IOException;

    // Check if an image has an annotation (used by Yirou for red heart overlay trigger)
    public static boolean hasAnnotation(String absolutePath) throws IOException;
}
```

### Storage Detail

| Detail | Decision |
|--------|----------|
| File location | `data/annotations/annotations.json` |
| Format | `{ "C:\\Users\\...\\sunset.jpg": "My annotation text" }` |
| Encoding | UTF-8 |
| Missing file | Auto-create on first `saveAnnotation` call |
| Key | Absolute file path (unique per machine) |

### Rules

| Rule | Detail |
|------|--------|
| `hasAnnotation` | Must return `false` (not throw) when annotation file does not yet exist |
| One annotation per image | Calling `saveAnnotation` again overwrites the previous annotation |
| Null annotation | Throw `IllegalArgumentException` if annotation string is null |

### Who Uses What

| Member | Methods Called |
|--------|---------------|
| Yirou | `getAnnotation`, `hasAnnotation`, `deleteAnnotation` |
| Sam | Implements all, calls all during testing |

---

## Contract 3: `FavouritesManager.java`

**Owner:** Yirou  
**Location:** `src/main/java/com/wig3003/photoapp/util/FavouritesManager.java`  
**Depends on:** Nothing.

### Method Signatures

```java
public class FavouritesManager {

    // Add image to favourites list
    public static void addFavourite(String absolutePath) throws IOException;

    // Remove image from favourites list
    public static void removeFavourite(String absolutePath) throws IOException;

    // Check if image is currently a favourite (used for button toggle state in UI)
    public static boolean isFavourite(String absolutePath) throws IOException;

    // Get full ordered favourites list, sorted by file last-modified date (oldest first)
    public static List<String> getFavourites() throws IOException;
}
```

### Storage Detail

| Detail | Decision |
|--------|----------|
| File location | `data/annotations/favourites.json` |
| Format | `["C:\\Users\\...\\photo1.jpg", "C:\\Users\\...\\photo2.jpg"]` |
| Sort order | Sorted by file last-modified date at **call time** — oldest first |
| Missing file | Auto-create on first `addFavourite` call |

### Rules

| Rule | Detail |
|------|--------|
| Sort responsibility | `getFavourites` sorts internally — **Winnie must not sort the result herself** |
| Duplicate paths | Calling `addFavourite` on an already-favourite image is a no-op (no duplicate added) |
| Missing file | `isFavourite` returns `false` (not throw) when favourites file does not yet exist |

### Who Uses What

| Member | Methods Called |
|--------|---------------|
| Yirou | `isFavourite`, `addFavourite`, `removeFavourite` |
| Winnie | `getFavourites` |

---

## Contract 4: DIP Radiometric & Aesthetic

**Owner:** Emily  
**Location:** `src/main/java/com/wig3003/photoapp/dip/radiometric/RadiometricProcessor.java`, `dip/aesthetic/AestheticProcessor.java`  
**Depends on:** `ImageUtils.java`

### Method Signatures

```java
public class RadiometricProcessor {

    // Adjust brightness and contrast of an image
    // alpha: contrast multiplier — range 0.0 to 3.0, where 1.0 = no change
    // beta:  brightness offset  — range -100 to 100, where 0 = no change
    public static Mat adjustBrightnessContrast(Mat source, double alpha, double beta)
        throws IllegalArgumentException;

    // Convert image to grayscale
    public static Mat toGrayscale(Mat source)
        throws IllegalArgumentException;
}

public class AestheticProcessor {

    // Apply a border to an image
    // borderWidth: pixel width of border (must be > 0)
    // borderType:  "CONSTANT", "REFLECT", or "REPLICATE"
    // color:       hex color string e.g. "#FF0000" — used only when borderType is "CONSTANT"
    public static Mat applyBorder(Mat source, int borderWidth, String borderType, String color)
        throws IllegalArgumentException;
}
```

### Rules

| Rule | Detail |
|------|--------|
| Input Mat | Always BGR format, never null |
| Return Mat | Always BGR format — same as input |
| Grayscale | Convert back to BGR before returning so the ImageUtils bridge works uniformly |
| Original | Never modified — always operate on a copy of the source Mat |
| Invalid borderType | Throw `IllegalArgumentException` for unrecognised border type strings |
| Out-of-range alpha/beta | Clamp to valid range internally — do not throw |

### Who Triggers These

| Caller | Action |
|--------|--------|
| Yirou | Passes current `WritableImage` → `ImageUtils.writableImageToMat()` → hands Mat to Emily's methods |
| Sam | Wires slider values and button actions in FXML controllers to these method calls |

---

## Contract 5: DIP Geometric & Object Extraction

**Owner:** Chyntia  
**Location:** `src/main/java/com/wig3003/photoapp/dip/geometric/GeometricProcessor.java`, `dip/geometric/ObjectExtractor.java`  
**Depends on:** `ImageUtils.java`

### Method Signatures

```java
public class GeometricProcessor {

    // Resize image by a percentage scale factor
    // scalePercent: e.g. 50.0 = half size, 200.0 = double size
    public static Mat resize(Mat source, double scalePercent)
        throws IllegalArgumentException;

    // Translate (reposition) image within fixed canvas
    // dx: horizontal shift in pixels (negative = left)
    // dy: vertical shift in pixels (negative = up)
    // Pixels shifted out of frame are clipped — canvas size stays fixed
    public static Mat translate(Mat source, double dx, double dy)
        throws IllegalArgumentException;

    // Rotate image around its center
    // angle: 0.0 to 360.0 degrees clockwise
    public static Mat rotate(Mat source, double angle)
        throws IllegalArgumentException;
}

public class ObjectExtractor {

    // Select an object by color similarity from a user-clicked pixel
    // clickX, clickY: pixel coordinates of user click on the displayed image
    // tolerance: HSV color similarity threshold — range 0 to 255
    // Returns: binary mask Mat of the selected region
    public static Mat selectObject(Mat source, int clickX, int clickY, int tolerance)
        throws IllegalArgumentException;

    // Extract selected object using mask and save to output directory
    // mask: binary mask Mat returned by selectObject()
    // outputDir: absolute path of output directory (use data/output/)
    // Returns: absolute path of the saved extracted image file
    public static String extractAndSave(Mat source, Mat mask, String outputDir)
        throws IOException;
}
```

### Rules

| Rule | Detail |
|------|--------|
| Input Mat | Always BGR format, never null |
| Return Mat | Same size as input — except `resize` which changes dimensions |
| Canvas on translate | Fixed size — pixels shifted out of frame are clipped |
| Output filename | Auto-generated: `extracted_<timestamp>.png` |
| Original | Never modified — always operate on a copy |
| Invalid scalePercent | Throw `IllegalArgumentException` if <= 0 |
| Invalid tolerance | Throw `IllegalArgumentException` if outside 0–255 |

### Who Triggers These

| Caller | Action |
|--------|--------|
| Yirou | Passes current `WritableImage` → `ImageUtils.writableImageToMat()` → hands Mat to Chyntia's methods |
| Sam | Wires slider values, text inputs, and click coordinates in FXML controllers to these method calls |

---

## Contract 6: Multimedia Synthesis

**Owner:** Winnie  
**Location:** `src/main/java/com/wig3003/photoapp/synthesis/`  
**Depends on:** `ImageUtils.java`, `FavouritesManager.java`

### Method Signatures

```java
public class MosaicGenerator {

    // Generate a mosaic image from a collection of tile images
    // tilePaths:  list of absolute paths to tile images selected by user (must not be empty)
    // targetPath: absolute path of the target image to recreate as a mosaic
    // tileSize:   pixel size of each square tile — minimum 10px
    // Returns:    absolute path of the saved mosaic file
    public static String generateMosaic(List<String> tilePaths, String targetPath, int tileSize)
        throws IOException, IllegalArgumentException;
}

public class VideoCompiler {

    // Compile an AVI video from the user's ordered favourites list
    // imagePaths:       ordered list from FavouritesManager.getFavourites()
    // durationPerPhoto: seconds each photo is displayed — must be > 0
    // overlayText:      poem or text typed by the user — drawn on every frame
    // outputDir:        absolute path of output directory (use data/output/)
    // Returns:          absolute path of the saved AVI file
    public static String compileVideo(List<String> imagePaths, int durationPerPhoto,
        String overlayText, String outputDir)
        throws IOException, IllegalArgumentException;
}

public class MediaPlayerController {

    // Launch video playback in a separate popup window
    // videoPath: absolute path of AVI file to play
    public static void launchPlayer(String videoPath)
        throws IllegalArgumentException;
}
```

### Output & Format Rules

| Rule | Detail |
|------|--------|
| Mosaic output filename | `data/output/mosaic_<timestamp>.png` |
| Video output filename | `data/output/video_<timestamp>.avi` |
| Video codec | XVID fourcc, AVI container |
| Video frame size | All images uniformly resized to `1280x720` before writing |
| Overlay text position | Bottom-center of frame, white color, readable font size |
| Overlay text rendering | Drawn via `Graphics2D` on `BufferedImage` before VideoWriter — not via OpenCV text API |
| Tile size minimum | Throw `IllegalArgumentException` if `tileSize` < 10 |
| Empty list | Both `generateMosaic` and `compileVideo` throw `IllegalArgumentException` if list is empty |
| Player controls | Play, pause, seek slider, current time label |

### Who Triggers These

| Caller | Action |
|--------|--------|
| Yirou | Passes selected tile image paths list to `generateMosaic` |
| Winnie | Calls `FavouritesManager.getFavourites()` internally inside `compileVideo` |
| Sam | Wires UI buttons and text input in FXML to `compileVideo` and `launchPlayer` |

---

## Contract 7: Email Sharing

**Owner:** Sam  
**Location:** `src/main/java/com/wig3003/photoapp/social/EmailSender.java`  
**Depends on:** Nothing.

### Method Signatures

```java
public class EmailSender {

    // Launch email compose window with pre-attached file
    // attachmentPath: absolute path of image or AVI file to attach
    // attachmentType: "IMAGE" or "VIDEO"
    public static void launchComposeWindow(String attachmentPath, String attachmentType)
        throws IllegalArgumentException;

    // Send email — called internally from the compose window Send button only
    private static void sendEmail(String recipient, String subject, String body,
        String attachmentPath)
        throws MessagingException, IOException;
}
```

### Compose Window Fields

| Field | Type | Validation |
|-------|------|------------|
| Recipient | Text input | Must contain `@` — show inline error if invalid |
| Subject | Text input | Cannot be empty |
| Body | Text area | Optional |
| Attachment | Read-only label | Pre-filled from `attachmentPath` parameter |
| Send button | Button | Disabled until recipient and subject are filled |

### SMTP Configuration

| Detail | Decision |
|--------|----------|
| Provider | Gmail SMTP |
| Host | `smtp.gmail.com` |
| Port | `587` |
| Auth | Gmail App Password |
| Credentials | Loaded from `data/email.properties` — **never hardcoded, never committed to Git** |
| Missing credentials | Show setup instructions dialog — tell user to create `data/email.properties` |

### Rules

| Rule | Detail |
|------|--------|
| Invalid attachment path | Throw `IllegalArgumentException` |
| Invalid attachment type | Throw `IllegalArgumentException` if not `"IMAGE"` or `"VIDEO"` |
| Send failure | Show error dialog — never crash the app |
| Send success | Show confirmation dialog, close compose window |

### Who Triggers These

| Caller | Context |
|--------|---------|
| Yirou | `launchComposeWindow(path, "IMAGE")` — Share button in main window |
| Winnie | `launchComposeWindow(path, "VIDEO")` — Share button in media player popup |

---

## Quick Reference: Module Dependencies

| Module | Owner | Depends On |
|--------|-------|------------|
| `ImageUtils.java` | Yirou | Nothing |
| `MetadataStore.java` | Sam | Nothing |
| `FavouritesManager.java` | Yirou | Nothing |
| `RadiometricProcessor`, `AestheticProcessor` | Emily | `ImageUtils` |
| `GeometricProcessor`, `ObjectExtractor` | Chyntia | `ImageUtils` |
| `MosaicGenerator`, `VideoCompiler`, `MediaPlayerController` | Winnie | `ImageUtils`, `FavouritesManager` |
| `EmailSender` | Sam | Nothing |

---

## Change Log

| Version | Date | Author | Change |
|---------|------|--------|--------|
| 1.0 | April 2026 | Sam | Initial contracts defined |

---

*Any deviation from these contracts requires team consensus and must be logged here.*
