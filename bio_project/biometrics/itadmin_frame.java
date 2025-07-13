package biometrics_exam;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Random;
import javax.mail.*;
import javax.mail.internet.*;
import javax.swing.table.DefaultTableModel;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;

public class itadmin_frame extends javax.swing.JFrame {
    
    private Connection con;
    private VideoCapture webcam;
    private Mat currentFrame;
    private Timer webcamTimer;
    private javax.swing.JFrame webcamFrame;
    private javax.swing.JLabel webcamDisplay;
    private javax.swing.JButton captureImageButton;
    private javax.swing.JButton closeWebcamButton;
    private BufferedImage capturedWebcamImage;
    private boolean webcamActive = false;
    private String selectedImagePath;
    private byte[] imageData;
    private int currentUserId;
    private DefaultTableModel tableModel;
    private int selectedUserId = -1;
    private String currentResetToken = "";
    private int resetUserId = -1;
    private static final String SENDER_EMAIL = "ezraagun@gmail.com";
    private static final String SENDER_PASSWORD = "nwhotxkkqbsbwvzy";

    public itadmin_frame(int userId) {
        initComponents();
        this.currentUserId = userId;
        initializeDatabase();
        initializeTable();
    }
    
    public itadmin_frame() {
        this(-1);
    }

    static {
        File file = new File("C:\\Users\\ezraa\\OneDrive\\Documents\\300 Lvl\\semester 2\\OOP\\open_cv\\opencv_java4110.dll");
        System.load(file.getAbsolutePath());
    }

    private void initializeDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Database connection failed: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    private void initializeTable() {
        String[] columnNames = {"Staff Id", "Name", "Unit", "Role", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        users_table.setModel(tableModel);

        users_table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = users_table.getSelectedRow();
                if (selectedRow >= 0) {
                    selectedUserId = (int) tableModel.getValueAt(selectedRow, 0);
                } else {
                    selectedUserId = -1;
                }
            }
        });

        loadUsersData();
    }

    private void loadUsersData() {
        loadUsersData(""); // Load all users by default
    }

    private void loadUsersData(String searchTerm) {
        if (con == null) {
            initializeDatabase();
        }

        try {
            tableModel.setRowCount(0); 

            String sql = "SELECT user_id, full_name, unit, role, is_active FROM users WHERE is_active = TRUE";

            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                sql += " AND (full_name LIKE ? OR email LIKE ? OR user_id = ?)";
            }

            // Add unit filter
            String selectedUnit = (String) unit_filter.getSelectedItem();
            if (selectedUnit != null && !selectedUnit.equals("All Units")) {
                sql += " AND unit = ?";
            }

            sql += " ORDER BY full_name";

            PreparedStatement pst = con.prepareStatement(sql);
            int paramIndex = 1;

            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                pst.setString(paramIndex++, "%" + searchTerm + "%");
                pst.setString(paramIndex++, "%" + searchTerm + "%");
                try {
                    int userId = Integer.parseInt(searchTerm.trim());
                    pst.setInt(paramIndex++, userId);
                } catch (NumberFormatException e) {
                    pst.setInt(paramIndex++, -1); // Invalid user ID
                }
            }

            if (selectedUnit != null && !selectedUnit.equals("All Units")) {
                pst.setString(paramIndex, selectedUnit);
            }

            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                Object[] row = {
                    rs.getInt("user_id"),
                    rs.getString("full_name"),
                    rs.getString("unit"),
                    rs.getString("role"),
                    rs.getBoolean("is_active") ? "Active" : "Inactive"
                };
                tableModel.addRow(row);
            }

            rs.close();
            pst.close();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading users data: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    private void initiatePasswordReset(int userId, String email, String userName) {
        try {
            // Generate reset token
            String resetToken = generateResetToken();

            // Store reset token in database
            String sql = "INSERT INTO password_resets (user_id, reset_token, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 15 MINUTE))";
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setInt(1, userId);
            pst.setString(2, hashToken(resetToken));
            pst.executeUpdate();
            pst.close();

            // Send email with reset code
            boolean emailSent = sendPasswordResetEmail(email, userName, resetToken);

            if (emailSent) {
                currentResetToken = resetToken;
                resetUserId = userId;
                showResetCodeDialog(userName);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to send reset email. Please try again.",
                        "Email Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error initiating password reset: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private String generateResetToken() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(999999));
    }

    private String hashToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            BigInteger number = new BigInteger(1, hash);
            return number.toString(16);
        } catch (Exception e) {
            return token; // Fallback to plain token
        }
    }
    
    private int getUserIdByEmail(String email) {
        int userId = -1;
        String sql = "SELECT user_id FROM users WHERE email = ?";
        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL"); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                userId = rs.getInt("user_id");
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving user ID: " + e.getMessage());
        }
        return userId;
    }

    private boolean sendPasswordResetEmail(String email, String userName, String resetCode) {
        int userId = getUserIdByEmail(email); 
        if (userId == -1) {
            System.err.println("No user found with email: " + email);
            return false;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", "smtp.gmail.com");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("Password Reset Code - PAMS");

            String emailBody = "Dear " + userName + ",\n\n"
                    + "A password reset has been initiated for your account.\n"
                    + "Your reset code is: " + resetCode + "\n\n"
                    + "This code will expire in 15 minutes.\n"
                    + "If you did not request this reset, please contact IT support.\n\n"
                    + "Best regards,\nPAMS";

            message.setText(emailBody);
            Transport.send(message);

            // Log email notification
            logEmailNotification(userId, "Password_Reset", "Password Reset Code", emailBody, "Sent");

            return true;

        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void showResetCodeDialog(String userName) {
        javax.swing.JDialog resetDialog = new javax.swing.JDialog(this, "Enter Reset Code", true);
        resetDialog.setSize(400, 200);
        resetDialog.setLocationRelativeTo(this);
        resetDialog.setDefaultCloseOperation(javax.swing.JDialog.DISPOSE_ON_CLOSE);

        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();

        javax.swing.JLabel messageLabel = new javax.swing.JLabel("Reset code sent to " + userName + "'s email");
        javax.swing.JLabel codeLabel = new javax.swing.JLabel("Enter Reset Code:");
        javax.swing.JTextField codeField = new javax.swing.JTextField(15);
        javax.swing.JButton verifyButton = new javax.swing.JButton("Verify Code");
        javax.swing.JButton cancelButton = new javax.swing.JButton("Cancel");

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new java.awt.Insets(10, 10, 10, 10);
        panel.add(messageLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(codeLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(codeField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(verifyButton, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        panel.add(cancelButton, gbc);

        verifyButton.addActionListener(e -> {
            String enteredCode = codeField.getText().trim();
            if (verifyResetCode(enteredCode)) {
                resetDialog.dispose();
                completePasswordReset();
            } else {
                JOptionPane.showMessageDialog(resetDialog, "Invalid or expired reset code.",
                        "Invalid Code", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> resetDialog.dispose());

        resetDialog.add(panel);
        resetDialog.setVisible(true);
    }

    private boolean verifyResetCode(String enteredCode) {
        try {
            String sql = "SELECT reset_id FROM password_resets WHERE user_id = ? AND reset_token = ? AND expires_at > NOW() AND used = FALSE";
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setInt(1, resetUserId);
            pst.setString(2, hashToken(enteredCode));
            ResultSet rs = pst.executeQuery();

            boolean valid = rs.next();

            rs.close();
            pst.close();

            return valid;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void completePasswordReset() {
        try {
            // Generate new password
            String newPassword = generateRandomPassword();
            String hashedPassword = hashPassword(newPassword);

            // Update user password
            String updateSql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
            PreparedStatement updatePst = con.prepareStatement(updateSql);
            updatePst.setString(1, hashedPassword);
            updatePst.setInt(2, resetUserId);
            updatePst.executeUpdate();
            updatePst.close();

            // Mark reset token as used
            String markUsedSql = "UPDATE password_resets SET used = TRUE WHERE user_id = ? AND reset_token = ?";
            PreparedStatement markPst = con.prepareStatement(markUsedSql);
            markPst.setInt(1, resetUserId);
            markPst.setString(2, hashToken(currentResetToken));
            markPst.executeUpdate();
            markPst.close();

            // Get user email for sending new password
            String emailSql = "SELECT email, full_name FROM users WHERE user_id = ?";
            PreparedStatement emailPst = con.prepareStatement(emailSql);
            emailPst.setInt(1, resetUserId);
            ResultSet rs = emailPst.executeQuery();

            if (rs.next()) {
                String email = rs.getString("email");
                String fullName = rs.getString("full_name");
                sendNewPasswordEmail(email, fullName, newPassword);
            }

            rs.close();
            emailPst.close();

            JOptionPane.showMessageDialog(this, "Password reset successful! New password sent to user's email.",
                    "Reset Complete", JOptionPane.INFORMATION_MESSAGE);

            // Use valid enum value
            logActivity("PASSWORD_RESET", "Password reset completed for user ID: " + resetUserId);

            // Clear reset variables
            currentResetToken = "";
            resetUserId = -1;

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error completing password reset: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        Random random = new Random();
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        return password.toString();
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            BigInteger number = new BigInteger(1, hash);
            return number.toString(16);
        } catch (Exception e) {
            return password; // Fallback
        }
    }

    private boolean sendNewPasswordEmail(String email, String userName, String newPassword) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", "smtp.gmail.com");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("New Password - PAMS");

            String emailBody = "Dear " + userName + ",\n\n"
                    + "Your password has been reset successfully.\n"
                    + "Your new password is: " + newPassword + "\n\n"
                    + "Please log in with this password and change it immediately.\n\n"
                    + "Best regards,\nPAMS";

            message.setText(emailBody);
            Transport.send(message);

            logEmailNotification(resetUserId, "Password_Reset", "New Password", emailBody, "Sent");

            return true;

        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private void loadUserDataForEditing(int userId) {
        try {
            String sql = "SELECT * FROM users WHERE user_id = ?";
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setInt(1, userId);
            ResultSet rs = pst.executeQuery();

            if (rs.next()) {
                // Populate fields with user data
                username_field.setText(rs.getString("full_name"));
                email_field.setText(rs.getString("email"));
                phone_field.setText(rs.getString("phone_number"));

                // Set date of birth
                java.sql.Date dob = rs.getDate("date_of_birth");
                if (dob != null) {
                    dob_chooser.setDate(dob);
                }

                // Set combo boxes
                gender_combo.setSelectedItem(rs.getString("gender"));
                unit_combo.setSelectedItem(rs.getString("unit"));
                role_combo.setSelectedItem(rs.getString("role"));

                // Load photo if available
                byte[] photoData = rs.getBytes("photo");
                if (photoData != null) {
                    try {
                        ByteArrayInputStream bis = new ByteArrayInputStream(photoData);
                        BufferedImage image = ImageIO.read(bis);
                        if (image != null) {
                            int labelWidth = Math.max(image_label.getWidth(), 150);
                            int labelHeight = Math.max(image_label.getHeight(), 150);
                            Image scaledImage = image.getScaledInstance(labelWidth, labelHeight, Image.SCALE_SMOOTH);
                            image_label.setIcon(new ImageIcon(scaledImage));
                            image_label.setText("");
                        }
                    } catch (IOException e) {
                        image_label.setText("No Image");
                        image_label.setIcon(null);
                    }
                } else {
                    image_label.setText("No Image");
                    image_label.setIcon(null);
                }
            }

            rs.close();
            pst.close();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading user data: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    private byte[] imageToByteArray(String imagePath) {
        ByteArrayOutputStream baos = null;
        try {
            InputStream is = new FileInputStream(imagePath);
            baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read = -1;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            is.close();
            return baos.toByteArray();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error converting image: " + ex.getMessage(),
                    "Image Processing Error", JOptionPane.ERROR_MESSAGE);
            return null;
        } finally {
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException ex) {
                    // Ignore close error
                }
            }
        }
    }
    
    private void initializeWebcamComponents() {
        webcamFrame = new javax.swing.JFrame("Webcam Capture");
        webcamFrame.setDefaultCloseOperation(javax.swing.JFrame.DO_NOTHING_ON_CLOSE);
        webcamFrame.setSize(800, 650);
        webcamFrame.setLocationRelativeTo(this);
        webcamFrame.setResizable(false);

        javax.swing.JPanel mainPanel = new javax.swing.JPanel();
        mainPanel.setLayout(new java.awt.BorderLayout());
        mainPanel.setBackground(java.awt.Color.WHITE);

        webcamDisplay = new javax.swing.JLabel();
        webcamDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        webcamDisplay.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
        webcamDisplay.setPreferredSize(new java.awt.Dimension(640, 480));
        webcamDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.BLACK, 2));
        webcamDisplay.setText("Initializing webcam...");

        javax.swing.JPanel buttonPanel = new javax.swing.JPanel();
        buttonPanel.setLayout(new java.awt.FlowLayout());
        buttonPanel.setBackground(java.awt.Color.WHITE);

        captureImageButton = new javax.swing.JButton("Capture Image");
        captureImageButton.setFont(new java.awt.Font("Segoe UI", 1, 14));
        captureImageButton.setBackground(new java.awt.Color(0, 123, 255));
        captureImageButton.setForeground(java.awt.Color.WHITE);
        captureImageButton.setPreferredSize(new java.awt.Dimension(150, 40));
        captureImageButton.setEnabled(false);

        closeWebcamButton = new javax.swing.JButton("Close Webcam");
        closeWebcamButton.setFont(new java.awt.Font("Segoe UI", 1, 14));
        closeWebcamButton.setBackground(new java.awt.Color(220, 53, 69));
        closeWebcamButton.setForeground(java.awt.Color.WHITE);
        closeWebcamButton.setPreferredSize(new java.awt.Dimension(150, 40));

        captureImageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                captureWebcamImage();
            }
        });

        closeWebcamButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeWebcam();
            }
        });

        webcamFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeWebcam();
            }
        });

        buttonPanel.add(captureImageButton);
        buttonPanel.add(closeWebcamButton);

        javax.swing.JLabel titleLabel = new javax.swing.JLabel("Position yourself in the frame and click 'Capture Image'");
        titleLabel.setFont(new java.awt.Font("Segoe UI", 1, 16));
        titleLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        titleLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));

        mainPanel.add(titleLabel, java.awt.BorderLayout.NORTH);
        mainPanel.add(webcamDisplay, java.awt.BorderLayout.CENTER);
        mainPanel.add(buttonPanel, java.awt.BorderLayout.SOUTH);

        webcamFrame.add(mainPanel);
    }

    private void startWebcam() {
        try {
            if (webcamFrame == null) {
                initializeWebcamComponents();
            }

            webcam = new VideoCapture(0); // Use default camera (index 0)

            if (!webcam.isOpened()) {
                JOptionPane.showMessageDialog(this,
                        "Could not open webcam. Please check if a camera is connected and not being used by another application.",
                        "Webcam Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            webcam.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);
            webcam.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);
            webcam.set(Videoio.CAP_PROP_FPS, 30);

            currentFrame = new Mat();
            webcamActive = true;

            webcamTimer = new Timer(33, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    updateWebcamFeed();
                }
            });

            webcamFrame.setVisible(true);
            webcamTimer.start();
            captureImageButton.setEnabled(true);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error starting webcam: " + e.getMessage(),
                    "Webcam Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void updateWebcamFeed() {
        if (webcam != null && webcam.isOpened() && webcamActive) {
            try {
                if (webcam.read(currentFrame) && !currentFrame.empty()) {
                    BufferedImage image = matToBufferedImage(currentFrame);
                    if (image != null) {
                        Image scaledImage = image.getScaledInstance(
                                webcamDisplay.getWidth(),
                                webcamDisplay.getHeight(),
                                Image.SCALE_SMOOTH);
                        webcamDisplay.setIcon(new ImageIcon(scaledImage));
                        webcamDisplay.setText("");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error updating webcam feed: " + e.getMessage());
            }
        }
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        try {
            MatOfByte matOfByte = new MatOfByte();
            Imgcodecs.imencode(".jpg", mat, matOfByte);
            byte[] byteArray = matOfByte.toArray();

            ByteArrayInputStream inputStream = new ByteArrayInputStream(byteArray);
            return ImageIO.read(inputStream);

        } catch (Exception e) {
            System.err.println("Error converting Mat to BufferedImage: " + e.getMessage());
            return null;
        }
    }

    private void captureWebcamImage() {
        if (currentFrame != null && !currentFrame.empty()) {
            try {
                capturedWebcamImage = matToBufferedImage(currentFrame);

                if (capturedWebcamImage != null) {
                    webcamTimer.stop();

                    showCapturedImageDialog();
                } else {
                    JOptionPane.showMessageDialog(webcamFrame,
                            "Failed to capture image. Please try again.",
                            "Capture Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(webcamFrame,
                        "Error capturing image: " + e.getMessage(),
                        "Capture Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showCapturedImageDialog() {
        javax.swing.JDialog imageDialog = new javax.swing.JDialog(webcamFrame, "Captured Image", true);
        imageDialog.setSize(500, 400);
        imageDialog.setLocationRelativeTo(webcamFrame);
        imageDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);

        javax.swing.JPanel dialogPanel = new javax.swing.JPanel(new java.awt.BorderLayout());
        dialogPanel.setBackground(java.awt.Color.WHITE);

        javax.swing.JLabel imageLabel = new javax.swing.JLabel();
        imageLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        Image scaledPreview = capturedWebcamImage.getScaledInstance(300, 240, Image.SCALE_SMOOTH);
        imageLabel.setIcon(new ImageIcon(scaledPreview));
        imageLabel.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.BLACK, 1));

        javax.swing.JPanel buttonPanel = new javax.swing.JPanel(new java.awt.FlowLayout());
        buttonPanel.setBackground(java.awt.Color.WHITE);

        javax.swing.JButton useImageButton = new javax.swing.JButton("Use This Image");
        useImageButton.setFont(new java.awt.Font("Segoe UI", 1, 12));
        useImageButton.setBackground(new java.awt.Color(40, 167, 69));
        useImageButton.setForeground(java.awt.Color.WHITE);
        useImageButton.setPreferredSize(new java.awt.Dimension(130, 35));

        javax.swing.JButton recaptureButton = new javax.swing.JButton("Recapture");
        recaptureButton.setFont(new java.awt.Font("Segoe UI", 1, 12));
        recaptureButton.setBackground(new java.awt.Color(255, 193, 7));
        recaptureButton.setForeground(java.awt.Color.BLACK);
        recaptureButton.setPreferredSize(new java.awt.Dimension(130, 35));

        javax.swing.JButton cancelButton = new javax.swing.JButton("Cancel");
        cancelButton.setFont(new java.awt.Font("Segoe UI", 1, 12));
        cancelButton.setBackground(new java.awt.Color(220, 53, 69));
        cancelButton.setForeground(java.awt.Color.WHITE);
        cancelButton.setPreferredSize(new java.awt.Dimension(130, 35));

        useImageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                useWebcamImage();
                imageDialog.dispose();
                closeWebcam();
            }
        });

        recaptureButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                imageDialog.dispose();
                if (webcamActive) {
                    webcamTimer.start();
                }
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                imageDialog.dispose();
                closeWebcam();
            }
        });

        imageDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeWebcam();
                imageDialog.dispose();
            }
        });

        buttonPanel.add(useImageButton);
        buttonPanel.add(recaptureButton);
        buttonPanel.add(cancelButton);

        javax.swing.JLabel titleLabel = new javax.swing.JLabel("Do you want to use this image?");
        titleLabel.setFont(new java.awt.Font("Segoe UI", 1, 14));
        titleLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        titleLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));

        dialogPanel.add(titleLabel, java.awt.BorderLayout.NORTH);
        dialogPanel.add(imageLabel, java.awt.BorderLayout.CENTER);
        dialogPanel.add(buttonPanel, java.awt.BorderLayout.SOUTH);

        imageDialog.add(dialogPanel);
        imageDialog.setVisible(true);
    }

    private void useWebcamImage() {
        try {
            if (capturedWebcamImage != null) {
                int labelWidth = Math.max(image_label.getWidth(), 150);
                int labelHeight = Math.max(image_label.getHeight(), 150);

                Image scaledImage = capturedWebcamImage.getScaledInstance(labelWidth, labelHeight, Image.SCALE_SMOOTH);
                ImageIcon icon = new ImageIcon(scaledImage);

                image_label.setText("");
                image_label.setIcon(icon);
                image_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                image_label.setVerticalAlignment(javax.swing.SwingConstants.CENTER);

                imageData = bufferedImageToByteArray(capturedWebcamImage);

                JOptionPane.showMessageDialog(this,
                        "Webcam image set successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error using webcam image: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private byte[] bufferedImageToByteArray(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("Error converting BufferedImage to byte array: " + e.getMessage());
            return null;
        }
    }

    private void closeWebcam() {
        try {
            webcamActive = false;

            if (webcamTimer != null && webcamTimer.isRunning()) {
                webcamTimer.stop();
            }

            if (webcam != null && webcam.isOpened()) {
                webcam.release();
            }

            if (webcamFrame != null) {
                webcamFrame.setVisible(false);
            }

            currentFrame = null;
            capturedWebcamImage = null;

        } catch (Exception e) {
            System.err.println("Error closing webcam: " + e.getMessage());
        }
    }
    
    private void logActivity(String actionType, String description) {
        try {
            String sql = "INSERT INTO system_logs (user_id, action_type, description, ip_address) VALUES (?, ?, ?, ?)";
            PreparedStatement pst = con.prepareStatement(sql);

            if (currentUserId > 0) {
                pst.setInt(1, currentUserId);
            } else {
                pst.setNull(1, java.sql.Types.INTEGER);
            }

            pst.setString(2, actionType);
            pst.setString(3, description);
            pst.setString(4, "127.0.0.1"); 
            pst.executeUpdate();
            pst.close();
        } catch (SQLException e) {
            System.err.println("Error logging activity: " + e.getMessage());
            e.printStackTrace(); 
        }
    }

    private void logEmailNotification(int userId, String emailType, String subject, String body, String status) {
        try {
            String sql = "INSERT INTO email_notifications (user_id, email_type, subject, body, sent_status, sent_at) VALUES (?, ?, ?, ?, ?, NOW())";
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setInt(1, userId);
            pst.setString(2, emailType); 
            pst.setString(3, subject);
            pst.setString(4, body);
            pst.setString(5, status); 
            pst.executeUpdate();
            pst.close();
        } catch (SQLException e) {
            System.err.println("Error logging email notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jFileChooser1 = new javax.swing.JFileChooser();
        edit_frame = new javax.swing.JFrame();
        jPanel1 = new javax.swing.JPanel();
        username_field = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        dob_chooser = new com.toedter.calendar.JDateChooser();
        gender_combo = new javax.swing.JComboBox<>();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        email_field = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        phone_field = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        unit_combo = new javax.swing.JComboBox<>();
        role_combo = new javax.swing.JComboBox<>();
        jLabel11 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        image_label = new javax.swing.JLabel();
        browse_button = new javax.swing.JButton();
        webcam_button = new javax.swing.JButton();
        save_button = new javax.swing.JButton();
        cancel_button = new javax.swing.JButton();
        jPanel10 = new javax.swing.JPanel();
        jPanel9 = new javax.swing.JPanel();
        jLabel17 = new javax.swing.JLabel();
        jLabel18 = new javax.swing.JLabel();
        jLabel19 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        unit_filter = new javax.swing.JComboBox<>();
        search_button = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        users_table = new javax.swing.JTable();
        edit_button = new javax.swing.JButton();
        reset_button = new javax.swing.JButton();

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));
        jPanel1.setForeground(new java.awt.Color(255, 255, 255));

        username_field.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        username_field.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        jLabel4.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(45, 59, 111));
        jLabel4.setText("Full Name");

        jPanel2.setBackground(new java.awt.Color(45, 59, 111));

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(255, 255, 255));
        jLabel2.setText("EDIT ACCOUNT");

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/biometrics_exam/pau_icon-25x25.png"))); // NOI18N

        jLabel3.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(255, 255, 255));
        jLabel3.setText("Attendance Management System");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel1)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(225, 225, 225)
                        .addComponent(jLabel2))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(211, 211, 211)
                        .addComponent(jLabel3)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel3)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel5.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(45, 59, 111));
        jLabel5.setText("Date of Birth");

        dob_chooser.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));
        dob_chooser.setForeground(new java.awt.Color(45, 59, 111));

        gender_combo.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        gender_combo.setForeground(new java.awt.Color(45, 59, 111));
        gender_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "select gender", "Male", "Female" }));
        gender_combo.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 59, 111)));

        jLabel7.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel7.setForeground(new java.awt.Color(45, 59, 111));
        jLabel7.setText("Gender");

        jLabel8.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel8.setForeground(new java.awt.Color(45, 59, 111));
        jLabel8.setText("Email");

        email_field.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        email_field.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        jLabel9.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(45, 59, 111));
        jLabel9.setText("Phone Number");

        phone_field.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        phone_field.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        jLabel10.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(45, 59, 111));
        jLabel10.setText("Unit");

        unit_combo.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        unit_combo.setForeground(new java.awt.Color(45, 59, 111));
        unit_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "select unit", "Security", "Maintenance", "Cafeteria", "Facility", "Horticulture", "None" }));
        unit_combo.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));
        unit_combo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unit_comboActionPerformed(evt);
            }
        });

        role_combo.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        role_combo.setForeground(new java.awt.Color(45, 59, 111));
        role_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "select role", "Staff", "Supervisor" }));
        role_combo.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        jLabel11.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel11.setForeground(new java.awt.Color(45, 59, 111));
        jLabel11.setText("Role");

        jPanel3.setBackground(new java.awt.Color(255, 255, 255));

        image_label.setBorder(javax.swing.BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.RAISED));

        browse_button.setBackground(new java.awt.Color(153, 153, 153));
        browse_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        browse_button.setForeground(new java.awt.Color(255, 255, 255));
        browse_button.setText("Browse");
        browse_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browse_buttonActionPerformed(evt);
            }
        });

        webcam_button.setBackground(new java.awt.Color(0, 102, 153));
        webcam_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        webcam_button.setForeground(new java.awt.Color(255, 255, 255));
        webcam_button.setText("Webcam");
        webcam_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                webcam_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(image_label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(browse_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(webcam_button, javax.swing.GroupLayout.DEFAULT_SIZE, 161, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(image_label, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(browse_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(webcam_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(10, Short.MAX_VALUE))
        );

        save_button.setBackground(new java.awt.Color(102, 204, 0));
        save_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        save_button.setForeground(new java.awt.Color(255, 255, 255));
        save_button.setText("Save Changes");
        save_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                save_buttonActionPerformed(evt);
            }
        });

        cancel_button.setBackground(new java.awt.Color(153, 153, 153));
        cancel_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        cancel_button.setForeground(new java.awt.Color(255, 255, 255));
        cancel_button.setText("Cancel");
        cancel_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancel_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(64, 64, 64)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(save_button, javax.swing.GroupLayout.PREFERRED_SIZE, 167, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(cancel_button, javax.swing.GroupLayout.PREFERRED_SIZE, 167, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(phone_field, javax.swing.GroupLayout.PREFERRED_SIZE, 426, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel9)
                            .addComponent(email_field, javax.swing.GroupLayout.PREFERRED_SIZE, 426, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel8)
                            .addComponent(jLabel4)
                            .addComponent(username_field, javax.swing.GroupLayout.PREFERRED_SIZE, 426, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(dob_chooser, javax.swing.GroupLayout.PREFERRED_SIZE, 217, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jLabel5))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel7)
                                    .addComponent(gender_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(unit_combo, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jLabel10, javax.swing.GroupLayout.Alignment.LEADING))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel11)
                                    .addComponent(role_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 124, javax.swing.GroupLayout.PREFERRED_SIZE))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 63, Short.MAX_VALUE)
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(39, 39, 39))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(37, 37, 37)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(username_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel5)
                            .addComponent(jLabel7))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(gender_combo, javax.swing.GroupLayout.DEFAULT_SIZE, 33, Short.MAX_VALUE)
                            .addComponent(dob_chooser, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(18, 18, 18)
                        .addComponent(jLabel8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(email_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel9))
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(phone_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(jLabel11))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(unit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(role_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 44, Short.MAX_VALUE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(save_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cancel_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(35, 35, 35))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {role_combo, unit_combo});

        javax.swing.GroupLayout edit_frameLayout = new javax.swing.GroupLayout(edit_frame.getContentPane());
        edit_frame.getContentPane().setLayout(edit_frameLayout);
        edit_frameLayout.setHorizontalGroup(
            edit_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(edit_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        edit_frameLayout.setVerticalGroup(
            edit_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(edit_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jPanel10.setBackground(new java.awt.Color(255, 255, 255));

        jPanel9.setBackground(new java.awt.Color(45, 59, 111));

        jLabel17.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel17.setForeground(new java.awt.Color(255, 255, 255));
        jLabel17.setText("PAN-ANTLANTIC UNIVERSITY");

        jLabel18.setIcon(new javax.swing.ImageIcon(getClass().getResource("/biometrics_exam/pau_icon-25x25.png"))); // NOI18N

        jLabel19.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel19.setForeground(new java.awt.Color(255, 255, 255));
        jLabel19.setText("Attendance Management System");

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel18)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addGap(54, 54, 54)
                        .addComponent(jLabel17))
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addGap(114, 114, 114)
                        .addComponent(jLabel19)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel18, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addComponent(jLabel17)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel19)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTextField1.setForeground(new java.awt.Color(204, 204, 204));
        jTextField1.setText(" search by name, email, or staff ID...");
        jTextField1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        unit_filter.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        unit_filter.setForeground(new java.awt.Color(45, 59, 111));
        unit_filter.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All Units", "Security", "Maintenance ", "Cafeteria", "Horticulture", "Facility" }));
        unit_filter.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));
        unit_filter.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unit_filterActionPerformed(evt);
            }
        });

        search_button.setBackground(new java.awt.Color(45, 59, 111));
        search_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        search_button.setForeground(new java.awt.Color(255, 255, 255));
        search_button.setText("Search");
        search_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                search_buttonActionPerformed(evt);
            }
        });

        users_table.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 59, 111)));
        users_table.setForeground(new java.awt.Color(45, 59, 111));
        users_table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Staff ID", "Name", "Unit", "Role", "Status"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane1.setViewportView(users_table);
        if (users_table.getColumnModel().getColumnCount() > 0) {
            users_table.getColumnModel().getColumn(0).setResizable(false);
            users_table.getColumnModel().getColumn(1).setResizable(false);
            users_table.getColumnModel().getColumn(2).setResizable(false);
            users_table.getColumnModel().getColumn(3).setResizable(false);
            users_table.getColumnModel().getColumn(4).setResizable(false);
        }

        edit_button.setBackground(new java.awt.Color(45, 59, 111));
        edit_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        edit_button.setForeground(new java.awt.Color(255, 255, 255));
        edit_button.setText("Edit User");
        edit_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                edit_buttonActionPerformed(evt);
            }
        });

        reset_button.setBackground(new java.awt.Color(204, 0, 0));
        reset_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        reset_button.setForeground(new java.awt.Color(255, 255, 255));
        reset_button.setText("Reset Password");
        reset_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reset_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel9, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addGap(31, 31, 31)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addComponent(edit_button, javax.swing.GroupLayout.PREFERRED_SIZE, 109, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(26, 26, 26)
                        .addComponent(reset_button))
                    .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jScrollPane1)
                        .addGroup(jPanel10Layout.createSequentialGroup()
                            .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 265, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGap(18, 18, 18)
                            .addComponent(unit_filter, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(search_button, javax.swing.GroupLayout.PREFERRED_SIZE, 109, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap(31, Short.MAX_VALUE))
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(unit_filter, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTextField1, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(search_button, javax.swing.GroupLayout.DEFAULT_SIZE, 30, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 324, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(26, 26, 26)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(edit_button, javax.swing.GroupLayout.DEFAULT_SIZE, 38, Short.MAX_VALUE)
                    .addComponent(reset_button, javax.swing.GroupLayout.DEFAULT_SIZE, 38, Short.MAX_VALUE))
                .addGap(28, 28, 28))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void unit_comboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unit_comboActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_unit_comboActionPerformed

    private void browse_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browse_buttonActionPerformed
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Image",
            "jpg", "jpeg", "png", "gif", "bmp");
        jFileChooser1.setAcceptAllFileFilterUsed(false);
        jFileChooser1.addChoosableFileFilter(filter);
        int result = jFileChooser1.showOpenDialog(null);
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            try {
                File f = jFileChooser1.getSelectedFile();
                selectedImagePath = f.getAbsolutePath();

                int labelWidth = image_label.getWidth();
                int labelHeight = image_label.getHeight();

                if (labelWidth <= 0 || labelHeight <= 0) {
                    image_label.revalidate();
                    image_label.repaint();
                    labelWidth = image_label.getPreferredSize().width;
                    labelHeight = image_label.getPreferredSize().height;
                }

                BufferedImage originalImage = ImageIO.read(f);
                Image scaledImage = originalImage.getScaledInstance(labelWidth, labelHeight, Image.SCALE_SMOOTH);
                ImageIcon ic = new ImageIcon(scaledImage);

                image_label.setText("");
                image_label.setIcon(ic);
                image_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                image_label.setVerticalAlignment(javax.swing.SwingConstants.CENTER);

                imageData = imageToByteArray(selectedImagePath);

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error loading image: " + ex.getMessage(),
                    "Image Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_browse_buttonActionPerformed

    private void webcam_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_webcam_buttonActionPerformed
        try {
            startWebcam();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error starting webcam: " + e.getMessage(),
                "Webcam Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }//GEN-LAST:event_webcam_buttonActionPerformed

    private void save_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_save_buttonActionPerformed
        if (selectedUserId == -1) {
            JOptionPane.showMessageDialog(edit_frame, "No user selected for editing.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            if (username_field.getText().trim().isEmpty()
                    || email_field.getText().trim().isEmpty()
                    || phone_field.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(edit_frame, "Please fill in all required fields.",
                        "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String sql = "UPDATE users SET full_name = ?, email = ?, phone_number = ?, "
                    + "date_of_birth = ?, gender = ?, unit = ?, role = ?, photo = ? WHERE user_id = ?";

            PreparedStatement pst = con.prepareStatement(sql);
            pst.setString(1, username_field.getText().trim());
            pst.setString(2, email_field.getText().trim());
            pst.setString(3, phone_field.getText().trim());

            if (dob_chooser.getDate() != null) {
                pst.setDate(4, new java.sql.Date(dob_chooser.getDate().getTime()));
            } else {
                pst.setNull(4, java.sql.Types.DATE);
            }

            pst.setString(5, (String) gender_combo.getSelectedItem());
            pst.setString(6, (String) unit_combo.getSelectedItem());
            pst.setString(7, (String) role_combo.getSelectedItem());

            if (imageData != null) {
                pst.setBytes(8, imageData);
            } else {
                pst.setNull(8, java.sql.Types.LONGVARBINARY);
            }

            pst.setInt(9, selectedUserId);

            int rowsAffected = pst.executeUpdate();
            pst.close();

            if (rowsAffected > 0) {
                JOptionPane.showMessageDialog(edit_frame, "User information updated successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);

                logActivity("USER_UPDATE", "Updated user information for user ID: " + selectedUserId);

                loadUsersData();

                edit_frame.setVisible(false);
            } else {
                JOptionPane.showMessageDialog(edit_frame, "Failed to update user information.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(edit_frame, "Error updating user: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }//GEN-LAST:event_save_buttonActionPerformed

    private void cancel_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancel_buttonActionPerformed
        int confirm = JOptionPane.showConfirmDialog(edit_frame,
                "Are you sure you want to cancel? Any unsaved changes will be lost.",
                "Confirm Cancel", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            imageData = null;
            edit_frame.setVisible(false);
        }
    }//GEN-LAST:event_cancel_buttonActionPerformed

    private void reset_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reset_buttonActionPerformed
        if (selectedUserId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a user from the table first.",
                    "No User Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String sql = "SELECT full_name, email FROM users WHERE user_id = ?";
            PreparedStatement pst = con.prepareStatement(sql);
            pst.setInt(1, selectedUserId);
            ResultSet rs = pst.executeQuery();

            if (rs.next()) {
                String userName = rs.getString("full_name");
                String userEmail = rs.getString("email");

                int confirm = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to reset the password for:\n" + userName + " (" + userEmail + ")?",
                        "Confirm Password Reset", JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    initiatePasswordReset(selectedUserId, userEmail, userName);
                }
            } else {
                JOptionPane.showMessageDialog(this, "User not found.", "Error", JOptionPane.ERROR_MESSAGE);
            }

            rs.close();
            pst.close();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error retrieving user information: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }//GEN-LAST:event_reset_buttonActionPerformed

    private void edit_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_edit_buttonActionPerformed
        if (selectedUserId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a user from the table first.",
                    "No User Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        loadUserDataForEditing(selectedUserId);
        edit_frame.setVisible(true);
        edit_frame.pack();
    }//GEN-LAST:event_edit_buttonActionPerformed

    private void search_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_search_buttonActionPerformed
        String searchTerm = jTextField1.getText().trim();
        loadUsersData(searchTerm);
        logActivity("REPORT_GENERATION", "Searched for users with term: " + searchTerm);
    }//GEN-LAST:event_search_buttonActionPerformed

    private void unit_filterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unit_filterActionPerformed
        loadUsersData(jTextField1.getText().trim());
    }//GEN-LAST:event_unit_filterActionPerformed

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new itadmin_frame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browse_button;
    private javax.swing.JButton cancel_button;
    private com.toedter.calendar.JDateChooser dob_chooser;
    private javax.swing.JButton edit_button;
    private javax.swing.JFrame edit_frame;
    private javax.swing.JTextField email_field;
    private javax.swing.JComboBox<String> gender_combo;
    private javax.swing.JLabel image_label;
    private javax.swing.JFileChooser jFileChooser1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField phone_field;
    private javax.swing.JButton reset_button;
    private javax.swing.JComboBox<String> role_combo;
    private javax.swing.JButton save_button;
    private javax.swing.JButton search_button;
    private javax.swing.JComboBox<String> unit_combo;
    private javax.swing.JComboBox<String> unit_filter;
    private javax.swing.JTextField username_field;
    private javax.swing.JTable users_table;
    private javax.swing.JButton webcam_button;
    // End of variables declaration//GEN-END:variables
}
