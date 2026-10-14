import java.sql.*;
import java.util.*;

/** SQLite storage. Parameterised queries only (no SQL injection). */
public class Db implements AutoCloseable {
    private final Connection con;

    public static class UserRec {
        public String username; public byte[] fpNonce, fpCt, faceNonce, faceCt;
        public int failed; public long lockedUntil;
    }

    public Db(String file) throws SQLException {
        con = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement s = con.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS users(username TEXT PRIMARY KEY, fp_nonce BLOB, fp_ct BLOB,"
                    + " face_nonce BLOB, face_ct BLOB, created_at TEXT, failed_count INTEGER DEFAULT 0,"
                    + " locked_until INTEGER DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS audit_log(id INTEGER PRIMARY KEY AUTOINCREMENT, ts TEXT,"
                    + " username TEXT, fp_score REAL, face_score REAL, fused REAL, result TEXT)");
        }
    }

    public boolean exists(String u) throws SQLException {
        try (PreparedStatement p = con.prepareStatement("SELECT 1 FROM users WHERE username=?")) {
            p.setString(1, u);
            return p.executeQuery().next();
        }
    }

    public void insertUser(String u, byte[][] fp, byte[][] face) throws SQLException {
        try (PreparedStatement p = con.prepareStatement(
                "INSERT INTO users(username,fp_nonce,fp_ct,face_nonce,face_ct,created_at) VALUES(?,?,?,?,?,datetime('now'))")) {
            p.setString(1, u); p.setBytes(2, fp[0]); p.setBytes(3, fp[1]);
            p.setBytes(4, face[0]); p.setBytes(5, face[1]);
            p.executeUpdate();
        }
    }

    public UserRec getUser(String u) throws SQLException {
        try (PreparedStatement p = con.prepareStatement("SELECT * FROM users WHERE username=?")) {
            p.setString(1, u);
            ResultSet r = p.executeQuery();
            if (!r.next()) return null;
            UserRec x = new UserRec();
            x.username = u; x.fpNonce = r.getBytes("fp_nonce"); x.fpCt = r.getBytes("fp_ct");
            x.faceNonce = r.getBytes("face_nonce"); x.faceCt = r.getBytes("face_ct");
            x.failed = r.getInt("failed_count"); x.lockedUntil = r.getLong("locked_until");
            return x;
        }
    }

    public void setFailState(String u, int failed, long lockedUntil) throws SQLException {
        try (PreparedStatement p = con.prepareStatement("UPDATE users SET failed_count=?, locked_until=? WHERE username=?")) {
            p.setInt(1, failed); p.setLong(2, lockedUntil); p.setString(3, u);
            p.executeUpdate();
        }
    }

    public void deleteUser(String u) throws SQLException {
        try (PreparedStatement p = con.prepareStatement("DELETE FROM users WHERE username=?")) {
            p.setString(1, u); p.executeUpdate();
        }
    }

    public void log(String u, double fp, double face, double fused, String result) throws SQLException {
        try (PreparedStatement p = con.prepareStatement(
                "INSERT INTO audit_log(ts,username,fp_score,face_score,fused,result) VALUES(datetime('now'),?,?,?,?,?)")) {
            p.setString(1, u); p.setDouble(2, fp); p.setDouble(3, face); p.setDouble(4, fused); p.setString(5, result);
            p.executeUpdate();
        }
    }

    public List<String[]> users() throws SQLException {
        List<String[]> out = new ArrayList<>();
        try (Statement s = con.createStatement();
             ResultSet r = s.executeQuery("SELECT username, created_at, failed_count, locked_until FROM users ORDER BY username")) {
            while (r.next()) out.add(new String[]{r.getString(1), r.getString(2), "" + r.getInt(3),
                    r.getLong(4) > System.currentTimeMillis() ? "LOCKED" : "active"});
        }
        return out;
    }

    public List<String[]> auditLog() throws SQLException {
        List<String[]> out = new ArrayList<>();
        try (Statement s = con.createStatement();
             ResultSet r = s.executeQuery("SELECT ts,username,fp_score,face_score,fused,result FROM audit_log ORDER BY id DESC LIMIT 200")) {
            while (r.next()) out.add(new String[]{r.getString(1), r.getString(2),
                    String.format("%.3f", r.getDouble(3)), String.format("%.3f", r.getDouble(4)),
                    String.format("%.3f", r.getDouble(5)), r.getString(6)});
        }
        return out;
    }

    @Override public void close() throws SQLException { con.close(); }
}
