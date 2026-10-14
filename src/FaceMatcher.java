import java.awt.image.BufferedImage;

/**
 * Face module: central face-region crop, grayscale, resize, histogram equalisation,
 * Local Binary Pattern (LBP) histograms over a 4x4 grid. Matching = histogram intersection.
 * (To use a real cascade detector, replace cropFace() with an OpenCV Haar-cascade call.)
 */
public class FaceMatcher {
    static final int SIZE = 96, GRID = 4, CELL = SIZE / GRID;
    /** Calibration: raw intersection below FLOOR maps to score 0. Tune with EvalTool. */
    static final double FLOOR = 0.50;

    public static BufferedImage cropFace(BufferedImage img) {
        return ImageUtil.centerCrop(img, 0.85);
    }

    public static void validate(BufferedImage img) {
        if (img == null) throw new IllegalArgumentException("Face image could not be read");
        if (img.getWidth() < 64 || img.getHeight() < 64)
            throw new IllegalArgumentException("Face image too small (min 64x64)");
        if (ImageUtil.stdDev(ImageUtil.toGray(img, SIZE)) < 8)
            throw new IllegalArgumentException("No face detected (image too flat / dark)");
    }

    public static float[] extract(BufferedImage img) {
        validate(img);
        float[][] g = ImageUtil.toGray(cropFace(img), SIZE);
        g = ImageUtil.blur(ImageUtil.blur(g));            // suppress sensor noise before LBP
        g = ImageUtil.equalize(g);
        float[] feat = new float[GRID * GRID * 256];
        for (int y = 1; y < SIZE - 1; y++)
            for (int x = 1; x < SIZE - 1; x++) {
                float c = g[y][x];
                int code = 0;
                code |= (g[y - 1][x - 1] >= c ? 1 : 0) << 7;
                code |= (g[y - 1][x]     >= c ? 1 : 0) << 6;
                code |= (g[y - 1][x + 1] >= c ? 1 : 0) << 5;
                code |= (g[y][x + 1]     >= c ? 1 : 0) << 4;
                code |= (g[y + 1][x + 1] >= c ? 1 : 0) << 3;
                code |= (g[y + 1][x]     >= c ? 1 : 0) << 2;
                code |= (g[y + 1][x - 1] >= c ? 1 : 0) << 1;
                code |= (g[y][x - 1]     >= c ? 1 : 0);
                int cell = Math.min(GRID - 1, y / CELL) * GRID + Math.min(GRID - 1, x / CELL);
                feat[cell * 256 + code]++;
            }
        for (int cell = 0; cell < GRID * GRID; cell++) {          // normalise each cell histogram
            float s = 0;
            for (int i = 0; i < 256; i++) s += feat[cell * 256 + i];
            if (s > 0) for (int i = 0; i < 256; i++) feat[cell * 256 + i] /= s;
        }
        return feat;
    }

    /** Normalised similarity in [0,1]. */
    public static double match(float[] a, float[] b) {
        double inter = 0;
        for (int i = 0; i < a.length; i++) inter += Math.min(a[i], b[i]);
        inter /= (GRID * GRID);                                    // 0..1
        return Math.max(0, Math.min(1, (inter - FLOOR) / (1 - FLOOR)));
    }
}
