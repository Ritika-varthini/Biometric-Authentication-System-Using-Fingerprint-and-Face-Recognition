import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;

/**
 * Computes FAR, FRR and EER.
 * Dataset layout:  dataset/fingerprint/<person>/*.png   and   dataset/face/<person>/*.png
 * (sort order of images must correspond between the two folders for the fused evaluation).
 * Run: java -cp "out:lib/*" EvalTool dataset
 */
public class EvalTool {
    record Sample(String id, float[] fp, float[] face) {}

    public static void main(String[] a) throws Exception {
        File root = new File(a.length > 0 ? a[0] : "dataset");
        List<Sample> s = new ArrayList<>();
        File[] people = new File(root, "fingerprint").listFiles(File::isDirectory);
        if (people == null) { System.out.println("Dataset not found: " + root); return; }
        Arrays.sort(people);
        for (File p : people) {
            File[] fps = p.listFiles(), faces = new File(new File(root, "face"), p.getName()).listFiles();
            if (fps == null || faces == null) continue;
            Arrays.sort(fps); Arrays.sort(faces);
            for (int i = 0; i < Math.min(fps.length, faces.length); i++)
                s.add(new Sample(p.getName(),
                        FingerprintMatcher.extract(ImageIO.read(fps[i])),
                        FaceMatcher.extract(ImageIO.read(faces[i]))));
        }
        List<double[]> fp = new ArrayList<>(), face = new ArrayList<>(), fused = new ArrayList<>();
        // each entry: {score, isGenuine(1/0)}
        for (int i = 0; i < s.size(); i++)
            for (int j = i + 1; j < s.size(); j++) {
                double g = s.get(i).id().equals(s.get(j).id()) ? 1 : 0;
                double sf = FingerprintMatcher.match(s.get(i).fp(), s.get(j).fp());
                double sc = FaceMatcher.match(s.get(i).face(), s.get(j).face());
                fp.add(new double[]{sf, g}); face.add(new double[]{sc, g});
                fused.add(new double[]{AuthService.W_FP * sf + AuthService.W_FACE * sc, g});
            }
        report("Fingerprint only", fp);
        report("Face only", face);
        report("Fused (w_fp=" + AuthService.W_FP + ", w_face=" + AuthService.W_FACE + ")", fused);
    }

    static void report(String name, List<double[]> sc) {
        long gen = sc.stream().filter(x -> x[1] == 1).count(), imp = sc.size() - gen;
        if (gen == 0 || imp == 0) { System.out.println(name + ": need genuine AND impostor pairs"); return; }
        double bestGap = 9, eer = 0, eerT = 0;
        System.out.println("\n== " + name + " (genuine=" + gen + ", impostor=" + imp + ") ==");
        System.out.println("Thr    FAR     FRR");
        for (int t = 0; t <= 100; t++) {
            double thr = t / 100.0, fa = 0, fr = 0;
            for (double[] x : sc) {
                if (x[1] == 0 && x[0] >= thr) fa++;
                if (x[1] == 1 && x[0] < thr) fr++;
            }
            double far = fa / imp, frr = fr / gen;
            if (t % 10 == 0) System.out.printf("%.2f  %.3f   %.3f%n", thr, far, frr);
            if (Math.abs(far - frr) < bestGap) { bestGap = Math.abs(far - frr); eer = (far + frr) / 2; eerT = thr; }
        }
        System.out.printf("EER = %.3f at threshold %.2f%n", eer, eerT);
    }
}
