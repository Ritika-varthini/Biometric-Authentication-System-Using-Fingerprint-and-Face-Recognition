import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Shared pre-processing helpers (grayscale, resize, normalise, blur, equalise). */
public class ImageUtil {

    /** Convert to grayscale and resize to size x size. Values 0..255. */
    public static float[][] toGray(BufferedImage src, int size) {
        BufferedImage g = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D gr = g.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gr.drawImage(src, 0, 0, size, size, null);
        gr.dispose();
        float[][] out = new float[size][size];
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                out[y][x] = g.getRaster().getSample(x, y, 0);
        return out;
    }

    /** Crop the central square (used as a simple face region). ratio = fraction of the shorter side. */
    public static BufferedImage centerCrop(BufferedImage img, double ratio) {
        int side = (int) (Math.min(img.getWidth(), img.getHeight()) * ratio);
        int x = (img.getWidth() - side) / 2, y = (img.getHeight() - side) / 2;
        return img.getSubimage(x, y, side, side);
    }

    /** Zero-mean / unit-variance normalisation (contrast enhancement). */
    public static float[][] normalize(float[][] a) {
        int h = a.length, w = a[0].length;
        double sum = 0, sq = 0;
        for (float[] r : a) for (float v : r) { sum += v; sq += v * v; }
        double n = (double) h * w, mean = sum / n, sd = Math.sqrt(Math.max(1e-6, sq / n - mean * mean));
        float[][] o = new float[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) o[y][x] = (float) ((a[y][x] - mean) / sd);
        return o;
    }

    public static double stdDev(float[][] a) {
        double sum = 0, sq = 0, n = 0;
        for (float[] r : a) for (float v : r) { sum += v; sq += v * v; n++; }
        double m = sum / n;
        return Math.sqrt(Math.max(0, sq / n - m * m));
    }

    /** 3x3 box blur (noise removal). */
    public static float[][] blur(float[][] a) {
        int h = a.length, w = a[0].length;
        float[][] o = new float[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float s = 0; int c = 0;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int yy = y + dy, xx = x + dx;
                        if (yy >= 0 && yy < h && xx >= 0 && xx < w) { s += a[yy][xx]; c++; }
                    }
                o[y][x] = s / c;
            }
        return o;
    }

    /** Histogram equalisation on 0..255 values (reduces lighting variation). */
    public static float[][] equalize(float[][] a) {
        int h = a.length, w = a[0].length;
        int[] hist = new int[256];
        for (float[] r : a) for (float v : r) hist[Math.min(255, Math.max(0, (int) v))]++;
        int[] cdf = new int[256];
        int run = 0;
        for (int i = 0; i < 256; i++) { run += hist[i]; cdf[i] = run; }
        int cdfMin = 0;
        for (int i = 0; i < 256; i++) if (cdf[i] > 0) { cdfMin = cdf[i]; break; }
        float[][] o = new float[h][w];
        double total = (double) h * w - cdfMin;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = Math.min(255, Math.max(0, (int) a[y][x]));
                o[y][x] = total <= 0 ? v : (float) Math.round((cdf[v] - cdfMin) / total * 255.0);
            }
        return o;
    }
}
