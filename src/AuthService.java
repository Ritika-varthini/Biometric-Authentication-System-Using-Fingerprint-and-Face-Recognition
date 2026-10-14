import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/** Enrollment, verification, score fusion, lockout. */
public class AuthService {
    public static final double W_FP = 0.6, W_FACE = 0.4;      // w1 + w2 = 1
    public static final double THRESHOLD = 0.60;               // choose from EER (EvalTool)
    public static final int MAX_FAILS = 3;
    public static final long LOCK_MS = 60_000;

    public static class Result {
        public boolean granted; public double fp, face, fused; public String message;
    }

    private final Db db; private final CryptoVault vault;
    public AuthService(Db db, CryptoVault vault) { this.db = db; this.vault = vault; }

    private static byte[] aad(String user, String kind) { return (user + ":" + kind).getBytes(StandardCharsets.UTF_8); }

    static byte[] toBytes(float[] f) {
        ByteBuffer b = ByteBuffer.allocate(f.length * 4);
        for (float v : f) b.putFloat(v);
        return b.array();
    }
    static float[] toFloats(byte[] raw) {
        ByteBuffer b = ByteBuffer.wrap(raw);
        float[] f = new float[raw.length / 4];
        for (int i = 0; i < f.length; i++) f[i] = b.getFloat();
        return f;
    }

    public void enroll(String user, BufferedImage fpImg, BufferedImage faceImg) throws Exception {
        if (user == null || !user.matches("[A-Za-z0-9_.-]{3,32}"))
            throw new IllegalArgumentException("Username must be 3-32 chars: letters, digits, _ . -");
        if (db.exists(user)) throw new IllegalArgumentException("User already enrolled");
        float[] fp = FingerprintMatcher.extract(fpImg);        // raw images are discarded after this
        float[] face = FaceMatcher.extract(faceImg);
        db.insertUser(user, vault.encrypt(toBytes(fp), aad(user, "fp")),
                            vault.encrypt(toBytes(face), aad(user, "face")));
        db.log(user, 0, 0, 0, "ENROLLED");
    }

    public Result verify(String user, BufferedImage fpImg, BufferedImage faceImg) throws Exception {
        Result r = new Result();
        Db.UserRec u = db.getUser(user);
        if (u == null) { r.message = "Unknown user"; db.log(user, 0, 0, 0, "UNKNOWN_USER"); return r; }

        long now = System.currentTimeMillis();
        if (u.lockedUntil > now) {
            r.message = "Account locked. Try again in " + (u.lockedUntil - now) / 1000 + " s";
            db.log(user, 0, 0, 0, "LOCKED_OUT");
            return r;
        }
        float[] fpT, faceT;
        try {
            fpT = toFloats(vault.decrypt(u.fpNonce, u.fpCt, aad(user, "fp")));
            faceT = toFloats(vault.decrypt(u.faceNonce, u.faceCt, aad(user, "face")));
        } catch (GeneralSecurityException e) {                 // GCM tag failed -> tampering
            r.message = "Stored template failed integrity check (tampered). ACCESS DENIED";
            db.log(user, 0, 0, 0, "TAMPER_DETECTED");
            return r;
        }
        r.fp = FingerprintMatcher.match(fpT, FingerprintMatcher.extract(fpImg));
        r.face = FaceMatcher.match(faceT, FaceMatcher.extract(faceImg));
        r.fused = W_FP * r.fp + W_FACE * r.face;               // S = w1*S(fp) + w2*S(face)
        r.granted = r.fused >= THRESHOLD;

        if (r.granted) {
            db.setFailState(user, 0, 0);
            r.message = "ACCESS GRANTED";
            db.log(user, r.fp, r.face, r.fused, "GRANTED");
        } else {
            int fails = u.failed + 1;
            if (fails >= MAX_FAILS) {
                db.setFailState(user, 0, now + LOCK_MS);
                r.message = "ACCESS DENIED - account locked for " + LOCK_MS / 1000 + " s";
            } else {
                db.setFailState(user, fails, 0);
                r.message = "ACCESS DENIED (" + (MAX_FAILS - fails) + " attempt(s) left)";
            }
            db.log(user, r.fp, r.face, r.fused, "REJECTED");
        }
        return r;
    }
}
