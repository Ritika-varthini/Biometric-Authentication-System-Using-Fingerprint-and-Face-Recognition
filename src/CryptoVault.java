import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM template encryption. The key lives in a file OUTSIDE the database. */
public class CryptoVault {
    private static final int NONCE_BYTES = 12, TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();
    private final SecretKey key;

    public CryptoVault(Path keyFile) throws Exception {
        byte[] raw;
        if (Files.exists(keyFile)) {
            raw = Base64.getDecoder().decode(Files.readString(keyFile).trim());
        } else {
            raw = new byte[32];                       // 256-bit key
            RNG.nextBytes(raw);
            Files.writeString(keyFile, Base64.getEncoder().encodeToString(raw));
            try { Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------")); }
            catch (Exception ignored) { /* non-POSIX file system (Windows) */ }
        }
        if (raw.length != 32) throw new IllegalStateException("Invalid key file");
        key = new SecretKeySpec(raw, "AES");
    }

    /** @return {nonce, ciphertext||tag}. aad binds the template to its owner. */
    public byte[][] encrypt(byte[] plain, byte[] aad) throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);                         // fresh nonce for every template
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        c.updateAAD(aad);
        return new byte[][]{nonce, c.doFinal(plain)};
    }

    /** Throws AEADBadTagException if the stored data (or its owner) was modified. */
    public byte[] decrypt(byte[] nonce, byte[] ct, byte[] aad) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        c.updateAAD(aad);
        return c.doFinal(ct);
    }
}
