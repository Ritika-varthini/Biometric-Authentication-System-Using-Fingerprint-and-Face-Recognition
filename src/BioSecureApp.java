import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;

/** BioSecure - Swing GUI: Enroll / Verify / Admin tabs. */
public class BioSecureApp extends JFrame {
    private final Db db; private final AuthService auth;
    private final DefaultTableModel usersModel = new DefaultTableModel(new String[]{"User", "Created", "Fails", "State"}, 0);
    private final DefaultTableModel logModel = new DefaultTableModel(new String[]{"Time", "User", "FP", "Face", "Fused", "Result"}, 0);

    public BioSecureApp() throws Exception {
        super("BioSecure - Fingerprint + Face Authentication");
        db = new Db("biosecure.db");
        auth = new AuthService(db, new CryptoVault(Paths.get("biosecure.key")));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Enroll", buildPanel(true));
        tabs.addTab("Verify", buildPanel(false));
        tabs.addTab("Admin", buildAdmin());
        tabs.addChangeListener(e -> refreshAdmin());
        add(tabs);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(760, 560);
        setLocationRelativeTo(null);
    }

    /** Shared layout for enrollment and verification. */
    private JPanel buildPanel(boolean enroll) {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JTextField user = new JTextField(20);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Username:")); top.add(user);
        p.add(top, BorderLayout.NORTH);

        BufferedImage[] img = new BufferedImage[2];            // [0]=fingerprint, [1]=face
        JLabel[] preview = {previewLabel("Fingerprint"), previewLabel("Face")};
        JPanel mid = new JPanel(new GridLayout(1, 2, 10, 10));
        for (int i = 0; i < 2; i++) {
            final int k = i;
            JButton b = new JButton(i == 0 ? "Select fingerprint image..." : "Select face image...");
            b.addActionListener(e -> {
                BufferedImage im = chooseImage();
                if (im != null) { img[k] = im; preview[k].setIcon(new ImageIcon(im.getScaledInstance(200, 200, Image.SCALE_SMOOTH))); preview[k].setText(""); }
            });
            JPanel col = new JPanel(new BorderLayout(4, 4));
            col.add(preview[i], BorderLayout.CENTER); col.add(b, BorderLayout.SOUTH);
            mid.add(col);
        }
        p.add(mid, BorderLayout.CENTER);

        JTextArea out = new JTextArea(5, 40);
        out.setEditable(false); out.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JButton go = new JButton(enroll ? "Enroll user" : "Verify identity");
        go.addActionListener(e -> {
            try {
                if (img[0] == null || img[1] == null) throw new IllegalArgumentException("Select both a fingerprint and a face image");
                if (enroll) {
                    auth.enroll(user.getText().trim(), img[0], img[1]);
                    out.setText("Enrolled '" + user.getText().trim() + "'. Templates encrypted (AES-256-GCM); raw images discarded.");
                } else {
                    AuthService.Result r = auth.verify(user.getText().trim(), img[0], img[1]);
                    out.setText(String.format("%s%nFingerprint score : %.3f%nFace score        : %.3f%nFused score       : %.3f  (threshold %.2f)",
                            r.message, r.fp, r.face, r.fused, AuthService.THRESHOLD));
                    out.setForeground(r.granted ? new Color(0, 120, 0) : Color.RED);
                }
            } catch (Exception ex) {
                out.setForeground(Color.RED);
                out.setText("Error: " + ex.getMessage());
            }
        });
        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.add(go, BorderLayout.NORTH); bottom.add(new JScrollPane(out), BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildAdmin() {
        JPanel p = new JPanel(new GridLayout(2, 1, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JTable ut = new JTable(usersModel);
        JButton del = new JButton("Delete selected user");
        del.addActionListener(e -> {
            int row = ut.getSelectedRow();
            if (row < 0) return;
            try { db.deleteUser((String) usersModel.getValueAt(row, 0)); refreshAdmin(); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
        });
        JPanel up = new JPanel(new BorderLayout());
        up.add(new JLabel("Enrolled users"), BorderLayout.NORTH);
        up.add(new JScrollPane(ut), BorderLayout.CENTER); up.add(del, BorderLayout.SOUTH);
        JPanel lp = new JPanel(new BorderLayout());
        lp.add(new JLabel("Audit log (latest 200)"), BorderLayout.NORTH);
        lp.add(new JScrollPane(new JTable(logModel)), BorderLayout.CENTER);
        p.add(up); p.add(lp);
        return p;
    }

    private void refreshAdmin() {
        try {
            usersModel.setRowCount(0); logModel.setRowCount(0);
            for (String[] r : db.users()) usersModel.addRow(r);
            for (String[] r : db.auditLog()) logModel.addRow(r);
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage()); }
    }

    private static JLabel previewLabel(String t) {
        JLabel l = new JLabel(t + " preview", SwingConstants.CENTER);
        l.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        l.setPreferredSize(new Dimension(220, 220));
        return l;
    }

    private BufferedImage chooseImage() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        try {
            BufferedImage im = ImageIO.read(fc.getSelectedFile());
            if (im == null) throw new IllegalArgumentException("Not a valid image file");
            return im;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Cannot read image: " + ex.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { new BioSecureApp().setVisible(true); }
            catch (Exception e) { JOptionPane.showMessageDialog(null, "Startup failed: " + e); }
        });
    }
}
