import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.Random;

/** Headless smoke test with synthetic images: enroll -> verify -> reject -> lockout -> tamper. */
public class SelfTest {
    static BufferedImage ridges(long seed, double noise) {          // swirl-like ridge pattern
        Random r = new Random(seed); double a = r.nextDouble() * 6, b = r.nextDouble() * 0.08 + 0.05, c = r.nextDouble() * 3;
        Random n = new Random(seed * 31 + (long) (noise * 1000) + 7);
        BufferedImage im = new BufferedImage(160, 160, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 160; y++) for (int x = 0; x < 160; x++) {
            double ang = a + c * Math.atan2(y - 80, x - 80);
            double v = 128 + 100 * Math.sin((x * Math.cos(ang) + y * Math.sin(ang)) * 0.9 + b * (x + y)) + noise * n.nextGaussian() * 30;
            im.getRaster().setSample(x, y, 0, (int) Math.max(0, Math.min(255, v)));
        }
        return im;
    }
    static BufferedImage face(long seed, double noise) {            // textured blob "face"
        Random r = new Random(seed); Random n = new Random(seed * 17 + (long) (noise * 1000));
        double[] p = new double[12]; for (int i = 0; i < 12; i++) p[i] = r.nextDouble() * 6;
        BufferedImage im = new BufferedImage(160, 160, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 160; y++) for (int x = 0; x < 160; x++) {
            double v = 120 + 50 * Math.sin(x * 0.05 * (1 + p[0] % 2) + p[1]) * Math.cos(y * 0.07 * (1 + p[2] % 2) + p[3])
                    + 30 * Math.sin((x + y) * 0.2 + p[4]) * Math.sin((x - y) * 0.15 + p[5]) + noise * n.nextGaussian() * 15;
            im.getRaster().setSample(x, y, 0, (int) Math.max(0, Math.min(255, v)));
        }
        return im;
    }
    public static void main(String[] a) throws Exception {
        Files.deleteIfExists(Path.of("selftest.db")); Files.deleteIfExists(Path.of("selftest.key"));
        Db db = new Db("selftest.db"); AuthService auth = new AuthService(db, new CryptoVault(Path.of("selftest.key")));
        auth.enroll("alice", ridges(1, 0), face(1, 0));
        AuthService.Result r = auth.verify("alice", ridges(1, 1), face(1, 1));
        System.out.printf("genuine (noisy re-capture): fp=%.3f face=%.3f fused=%.3f -> %s%n", r.fp, r.face, r.fused, r.message);
        for (int i = 0; i < 3; i++) {
            r = auth.verify("alice", ridges(50 + i, 0), face(50 + i, 0));
            System.out.printf("impostor #%d: fp=%.3f face=%.3f fused=%.3f -> %s%n", i + 1, r.fp, r.face, r.fused, r.message);
        }
        r = auth.verify("alice", ridges(1, 1), face(1, 1));
        System.out.println("genuine while locked -> " + r.message);
        db.close();
    }
}
