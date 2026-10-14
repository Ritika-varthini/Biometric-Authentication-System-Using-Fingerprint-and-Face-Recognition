import java.awt.image.BufferedImage;

/**
 * Fingerprint module: pre-processing + ridge orientation-field features.
 * Feature vector = for every 16x16 block, the coherence-weighted doubled-angle
 * orientation (cos2t, sin2t). Matching = cosine similarity.
 */
public class FingerprintMatcher {
    static final int SIZE = 128, BLOCK = 16, N = SIZE / BLOCK;

    public static void validate(BufferedImage img) {
        if (img == null) throw new IllegalArgumentException("Fingerprint image could not be read");
        if (img.getWidth() < 64 || img.getHeight() < 64)
            throw new IllegalArgumentException("Fingerprint image too small (min 64x64)");
        float[][] g = ImageUtil.toGray(img, SIZE);
        if (ImageUtil.stdDev(g) < 8)
            throw new IllegalArgumentException("Fingerprint image has too little contrast / ridge detail");
    }

    public static float[] extract(BufferedImage img) {
        validate(img);
        float[][] g = ImageUtil.toGray(img, SIZE);     // grayscale + resize
        g = ImageUtil.blur(g);                          // noise removal
        g = ImageUtil.normalize(g);                     // contrast normalisation

        float[] feat = new float[N * N * 2];
        for (int by = 0; by < N; by++)
            for (int bx = 0; bx < N; bx++) {
                double gxx = 0, gyy = 0, gxy = 0;
                for (int y = by * BLOCK + 1; y < (by + 1) * BLOCK - 1; y++)
                    for (int x = bx * BLOCK + 1; x < (bx + 1) * BLOCK - 1; x++) {
                        double gx = (g[y][x + 1] - g[y][x - 1]) / 2.0;   // central differences
                        double gy = (g[y + 1][x] - g[y - 1][x]) / 2.0;
                        gxx += gx * gx; gyy += gy * gy; gxy += gx * gy;
                    }
                double energy = gxx + gyy + 1e-6;
                int i = (by * N + bx) * 2;
                feat[i]     = (float) ((gxx - gyy) / energy);   // coherence * cos(2*theta)
                feat[i + 1] = (float) (2 * gxy / energy);       // coherence * sin(2*theta)
            }
        return feat;
    }

    /** Normalised similarity in [0,1]. */
    public static double match(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0 || nb == 0) return 0;
        return Math.max(0, Math.min(1, dot / Math.sqrt(na * nb)));
    }
}
