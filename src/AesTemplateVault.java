import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/** Stand-alone crypto demo from the report (output for Fig 2). */
public class AesTemplateVault {
    private static final int KEY_BITS = 256, NONCE_BYTES = 12, TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    static SecretKey generateKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(KEY_BITS);
        return kg.generateKey();
    }
    static byte[] encrypt(byte[] template, SecretKey key) throws Exception {
        byte[] nonce = new byte[NONCE_BYTES];
        RNG.nextBytes(nonce);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        byte[] ct = c.doFinal(template);
        byte[] out = Arrays.copyOf(nonce, NONCE_BYTES + ct.length);
        System.arraycopy(ct, 0, out, NONCE_BYTES, ct.length);
        return out;
    }
    static byte[] decrypt(byte[] stored, SecretKey key) throws Exception {
        byte[] nonce = Arrays.copyOfRange(stored, 0, NONCE_BYTES);
        byte[] ct = Arrays.copyOfRange(stored, NONCE_BYTES, stored.length);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
        return c.doFinal(ct);
    }
    public static void main(String[] args) throws Exception {
        HexFormat hex = HexFormat.of();
        SecretKey key = generateKey();
        byte[] template = "FP:0.81,0.12,0.67|FACE:0.44,0.93,0.05".getBytes(StandardCharsets.UTF_8);
        byte[] stored = encrypt(template, key);
        System.out.println("Algorithm  : AES-GCM, key size " + key.getEncoded().length * 8 + " bits");
        System.out.println("Plain      : " + new String(template, StandardCharsets.UTF_8));
        System.out.println("Nonce      : " + hex.formatHex(stored, 0, NONCE_BYTES));
        System.out.println("Ciphertext : " + hex.formatHex(stored, NONCE_BYTES, 36) + "...");
        byte[] back = decrypt(stored, key);
        System.out.println("Decrypted  : " + new String(back, StandardCharsets.UTF_8));
        System.out.println("Match      : " + Arrays.equals(template, back));
        stored[NONCE_BYTES + 3] ^= 1;
        try { decrypt(stored, key); }
        catch (AEADBadTagException e) { System.out.println("Tampered   : tag check FAILED -> ACCESS DENIED"); }
    }
}
