package biometrics_exam;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import javax.swing.JOptionPane;
import com.digitalpersona.onetouch.*;
import com.digitalpersona.onetouch.capture.*;
import com.digitalpersona.onetouch.capture.event.*;
import com.digitalpersona.onetouch.processing.*;
import com.digitalpersona.onetouch.verification.DPFPVerification;
import com.digitalpersona.onetouch.verification.DPFPVerificationResult;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import java.awt.Image;
import java.sql.ResultSet;

public class login_frame extends javax.swing.JFrame {

    private int currentUserId = -1;
    private String currentUserRole = null;
    private DPFPCapture capturer;
    private DPFPTemplate template;
    private DPFPFeatureSet featureSet;
    private BufferedImage fingerprintImage;
    private boolean isDeviceConnected = false;
    private boolean isCapturing = false;

    public login_frame() {
        initComponents();
        initializeFingerprintSystem();
    }
    
    private void initializeFingerprintSystem() {
        try {
            // Create capture operation
            capturer = DPFPGlobal.getCaptureFactory().createCapture();

            // Add event handlers
            capturer.addDataListener(new DPFPDataAdapter() {
                @Override
                public void dataAcquired(DPFPDataEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            processFingerprintData(event);
                        } catch (Exception e) {
                            updateBiometricStatus("Error processing fingerprint: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                }
            });

            capturer.addReaderStatusListener(new DPFPReaderStatusAdapter() {
                @Override
                public void readerConnected(DPFPReaderStatusEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        updateDeviceConnectionStatus(true);
                        updateBiometricStatus("Scanner connected. Place finger on scanner.");
                    });
                }

                @Override
                public void readerDisconnected(DPFPReaderStatusEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        updateDeviceConnectionStatus(false);
                        updateBiometricStatus("Scanner disconnected. Please reconnect device.");
                    });
                }
            });

            capturer.addSensorListener(new DPFPSensorAdapter() {
                @Override
                public void fingerTouched(DPFPSensorEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        updateBiometricStatus("Finger detected. Processing...");
                    });
                }

                @Override
                public void fingerGone(DPFPSensorEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        updateBiometricStatus("Finger removed. Please place finger again if needed.");
                    });
                }
            });

            capturer.addErrorListener(new DPFPErrorAdapter() {
                public void errorOccurred(DPFPErrorEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        String errorMsg = "Scanner error: " + event.getError();
                        updateBiometricStatus(errorMsg);
                        updateDeviceConnectionStatus(false);
                    });
                }
            });

            // Initial device check
            checkDeviceConnection();

        } catch (Exception e) {
            System.err.println("Failed to initialize fingerprint system: " + e.getMessage());
            updateDeviceConnectionStatus(false);
            updateBiometricStatus("Fingerprint system initialization failed.");
        }
    }

    private void checkDeviceConnection() {
        try {
            capturer.startCapture();
            Thread.sleep(100);
            capturer.stopCapture();
            updateDeviceConnectionStatus(true);
            updateBiometricStatus("Scanner ready. Place finger on scanner.");
        } catch (RuntimeException e) {
            updateDeviceConnectionStatus(false);
            updateBiometricStatus("No scanner detected. Please connect USB device.");
        } catch (Exception e) {
            updateDeviceConnectionStatus(false);
            updateBiometricStatus("Error checking scanner connection.");
            System.err.println("Device check error: " + e.getMessage());
        }
    }

    private void processFingerprintData(DPFPDataEvent event) {
        try {
            DPFPSample sample = event.getSample();

            fingerprintImage = convertSampleToImage(sample);
            displayFingerprintOnBiometricFrame(fingerprintImage);

            DPFPFeatureExtraction featureExtractor = DPFPGlobal.getFeatureExtractionFactory().createFeatureExtraction();

            try {
                featureSet = featureExtractor.createFeatureSet(sample, DPFPDataPurpose.DATA_PURPOSE_VERIFICATION);

                if (featureSet != null) {
                    updateBiometricStatus("Fingerprint captured. Verifying...");
                    // Perform verification against database
                    verifyFingerprintAgainstDatabase();
                }

            } catch (DPFPImageQualityException ex) {
                updateBiometricStatus("Poor fingerprint quality. Please try again.");
            }

        } catch (Exception e) {
            updateBiometricStatus("Error processing fingerprint: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void verifyFingerprintAgainstDatabase() {
        if (featureSet == null) {
            updateBiometricStatus("No fingerprint data to verify.");
            return;
        }

        Connection con = null;
        java.sql.PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            // Get all fingerprint templates from database
            ps = con.prepareStatement(
                    "SELECT bd.user_id, bd.fingerprint_template, u.role, u.is_active "
                    + "FROM biometric_data bd "
                    + "JOIN users u ON bd.user_id = u.user_id "
                    + "WHERE u.is_active = TRUE"
            );

            rs = ps.executeQuery();

            DPFPVerification verifier = DPFPGlobal.getVerificationFactory().createVerification();

            while (rs.next()) {
                try {
                    byte[] templateBytes = rs.getBytes("fingerprint_template");
                    if (templateBytes != null && templateBytes.length > 0) {

                        DPFPTemplate dbTemplate = DPFPGlobal.getTemplateFactory().createTemplate();
                        dbTemplate.deserialize(templateBytes);

                        DPFPVerificationResult result = verifier.verify(featureSet, dbTemplate);

                        if (result.isVerified()) {
                            int userId = rs.getInt("user_id");
                            String userRole = rs.getString("role");
                            boolean isActive = rs.getBoolean("is_active");

                            if (isActive) {
                                this.currentUserId = userId;
                                this.currentUserRole = userRole;

                                SwingUtilities.invokeLater(() -> {
                                    stopBiometricCapture();
                                    updateBiometricStatus("Login successful! Redirecting...");

                                    JOptionPane.showMessageDialog(this,
                                            "Fingerprint verified successfully! Welcome back.",
                                            "Login Success", JOptionPane.INFORMATION_MESSAGE);

                                    navigateToDashboard(userRole, userId);
                                });
                                return;
                            } else {
                                updateBiometricStatus("Account is inactive. Please contact administrator.");
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing template: " + e.getMessage());
                    continue; 
                }
            }

            updateBiometricStatus("Fingerprint not recognized. Please try again or use manual login.");

        } catch (Exception e) {
            updateBiometricStatus("Database error during verification: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Database error during fingerprint verification: " + e.getMessage(),
                    "Verification Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();

        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }

    private BufferedImage convertSampleToImage(DPFPSample sample) {
        if (sample == null) {
            return createPlaceholderImage();
        }

        try {
            Image rawImage = DPFPGlobal.getSampleConversionFactory().createImage(sample);
            if (rawImage == null) {
                return createPlaceholderImage();
            }

            int targetSize = 150;
            BufferedImage bufferedImage = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = bufferedImage.createGraphics();

            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            g2d.drawImage(rawImage, 0, 0, targetSize, targetSize, null);
            g2d.dispose();

            return bufferedImage;

        } catch (Exception e) {
            System.err.println("Failed to convert sample to image: " + e.getMessage());
            return createPlaceholderImage();
        }
    }

    private BufferedImage createPlaceholderImage() {
        try {
            BufferedImage placeholder = new BufferedImage(150, 150, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = placeholder.createGraphics();

            g2d.setColor(java.awt.Color.LIGHT_GRAY);
            g2d.fillRect(0, 0, 150, 150);

            g2d.setColor(java.awt.Color.DARK_GRAY);
            g2d.drawRect(0, 0, 149, 149);

            g2d.setColor(java.awt.Color.BLACK);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            g2d.drawString("Place finger", 40, 70);
            g2d.drawString("on scanner", 45, 85);

            g2d.dispose();
            return placeholder;

        } catch (Exception e) {
            System.err.println("Failed to create placeholder: " + e.getMessage());
            return null;
        }
    }

    private void displayFingerprintOnBiometricFrame(BufferedImage image) {
        if (image != null && fingerprint_label != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    javax.swing.ImageIcon icon = new javax.swing.ImageIcon(image);
                    fingerprint_label.setIcon(icon);
                    fingerprint_label.setText("");
                } catch (Exception e) {
                    System.err.println("Error displaying fingerprint: " + e.getMessage());
                }
            });
        }
    }

    private void updateDeviceConnectionStatus(boolean connected) {
        isDeviceConnected = connected;
        SwingUtilities.invokeLater(() -> {
            if (connected) {
                System.out.println("Device: Connected ✓");
            } else {
                System.out.println("Device: Disconnected ✗");
            }
        });
    }

    private void updateBiometricStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("Biometric Status: " + message);
            if (jLabel8 != null) {
                jLabel8.setText(message);
            }
        });
    }

    private void startBiometricCapture() {
        try {
            if (capturer != null && isDeviceConnected) {
                isCapturing = true;
                capturer.startCapture();
                updateBiometricStatus("Ready for fingerprint. Place finger on scanner...");
                displayFingerprintOnBiometricFrame(createPlaceholderImage());
            } else {
                updateBiometricStatus("Scanner not available. Please check connection.");
            }
        } catch (Exception e) {
            updateBiometricStatus("Failed to start capture: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Failed to start fingerprint capture: " + e.getMessage(),
                    "Capture Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopBiometricCapture() {
        try {
            if (capturer != null && isCapturing) {
                capturer.stopCapture();
                isCapturing = false;
                updateBiometricStatus("Capture stopped.");
            }
        } catch (Exception e) {
            System.err.println("Error stopping capture: " + e.getMessage());
        }
    }

    private void cleanupFingerprintSystem() {
        try {
            stopBiometricCapture();
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    @Override
    protected void processWindowEvent(java.awt.event.WindowEvent e) {
        if (e.getID() == java.awt.event.WindowEvent.WINDOW_CLOSING) {
            cleanupFingerprintSystem();
        }
        super.processWindowEvent(e);
    }

    private boolean isValidEmail(String email) {
        String regex = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$";
        return email.matches(regex);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            JOptionPane.showMessageDialog(this, "Error hashing password: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
    
    private int authenticateUser(String email, String password) {
        Connection con = null;
        PreparedStatement ps = null;
        java.sql.ResultSet rs = null;
        try {
            String hashedPassword = hashPassword(password);
            if (hashedPassword == null) {
                return -1;
            }
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");
            ps = con.prepareStatement("SELECT user_id, role FROM users WHERE email = ? AND password_hash = ? AND is_active = TRUE");
            ps.setString(1, email.trim());
            ps.setString(2, hashedPassword);
            rs = ps.executeQuery();
            if (rs.next()) {
                this.currentUserId = rs.getInt("user_id");
                this.currentUserRole = rs.getString("role");
                return rs.getInt("user_id");
            } else {
                return -1; 
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Database Error: " + e.getMessage(),
                    "Authentication Error", JOptionPane.ERROR_MESSAGE);
            return -1;
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
            }
        }
    }

    private void navigateToDashboard(String role, int userId) {
        try {
            switch (role) {
                case "Staff":
                    new staff_frame(userId).setVisible(true);
                    break;
                case "Supervisor":
                    new supervisor_frame(userId).setVisible(true);
                    break;
                case "IT_Admin":
                    new itadmin_frame(userId).setVisible(true);
                    break;
                case "Director":
                    new dos_ithead_frame(userId).setVisible(true);
                    break;
                default:
                    JOptionPane.showMessageDialog(this, "Unknown user role: " + role,
                            "Access Error", JOptionPane.ERROR_MESSAGE);
                    return;
            }
            this.setVisible(false);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error opening dashboard: " + e.getMessage(),
                    "Navigation Error", JOptionPane.ERROR_MESSAGE);
        }
    }
   
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        biometric_frame = new javax.swing.JFrame();
        jPanel3 = new javax.swing.JPanel();
        manual_sign_button = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        fingerprint_label = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        cancel_button = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        email_field = new javax.swing.JTextField();
        password_field = new javax.swing.JPasswordField();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        login_button = new javax.swing.JButton();
        register_button = new javax.swing.JButton();
        jLabel6 = new javax.swing.JLabel();
        show_password = new javax.swing.JCheckBox();
        forgot_password = new javax.swing.JLabel();
        bio_button = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();

        jPanel3.setBackground(new java.awt.Color(255, 255, 255));
        jPanel3.setForeground(new java.awt.Color(255, 255, 255));

        manual_sign_button.setBackground(new java.awt.Color(45, 59, 111));
        manual_sign_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        manual_sign_button.setForeground(new java.awt.Color(255, 255, 255));
        manual_sign_button.setText("Manual Sign In");
        manual_sign_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manual_sign_buttonActionPerformed(evt);
            }
        });

        jPanel4.setBackground(new java.awt.Color(45, 59, 111));

        jLabel10.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(255, 255, 255));
        jLabel10.setText("PAN-ANTLANTIC UNIVERSITY");

        jLabel11.setIcon(new javax.swing.ImageIcon(getClass().getResource("/biometrics_exam/pau_icon-25x25.png"))); // NOI18N

        jLabel12.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel12.setForeground(new java.awt.Color(255, 255, 255));
        jLabel12.setText("Attendance Management System");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel11)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGap(36, 36, 36)
                        .addComponent(jLabel10))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGap(91, 91, 91)
                        .addComponent(jLabel12)))
                .addContainerGap(91, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel11, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jLabel10)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel12)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        fingerprint_label.setForeground(new java.awt.Color(153, 153, 153));
        fingerprint_label.setText("      place finger here");
        fingerprint_label.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel8.setBackground(new java.awt.Color(45, 59, 111));
        jLabel8.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel8.setForeground(new java.awt.Color(45, 59, 111));
        jLabel8.setText("Please place your finger on the scanner");

        cancel_button.setBackground(new java.awt.Color(153, 153, 153));
        cancel_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        cancel_button.setForeground(new java.awt.Color(255, 255, 255));
        cancel_button.setText("Cancel");
        cancel_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancel_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(fingerprint_label, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(187, 187, 187))
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGap(113, 113, 113)
                        .addComponent(manual_sign_button, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(cancel_button, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGap(135, 135, 135)
                        .addComponent(jLabel8)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(52, 52, 52)
                .addComponent(fingerprint_label, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jLabel8)
                .addGap(41, 41, 41)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(manual_sign_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cancel_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(46, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout biometric_frameLayout = new javax.swing.GroupLayout(biometric_frame.getContentPane());
        biometric_frame.getContentPane().setLayout(biometric_frameLayout);
        biometric_frameLayout.setHorizontalGroup(
            biometric_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(biometric_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        biometric_frameLayout.setVerticalGroup(
            biometric_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(biometric_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setBackground(new java.awt.Color(255, 255, 255));

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));
        jPanel1.setForeground(new java.awt.Color(255, 255, 255));

        email_field.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        email_field.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        password_field.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        password_field.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        jLabel4.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(45, 59, 111));
        jLabel4.setText("Email");

        jLabel5.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(45, 59, 111));
        jLabel5.setText("Password");

        login_button.setBackground(new java.awt.Color(51, 204, 0));
        login_button.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        login_button.setForeground(new java.awt.Color(255, 255, 255));
        login_button.setText("LOGIN WITH PASSWORD");
        login_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                login_buttonActionPerformed(evt);
            }
        });

        register_button.setBackground(new java.awt.Color(0, 102, 153));
        register_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        register_button.setForeground(new java.awt.Color(255, 255, 255));
        register_button.setText("REGISTER");
        register_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                register_buttonActionPerformed(evt);
            }
        });

        jLabel6.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel6.setForeground(new java.awt.Color(45, 59, 111));
        jLabel6.setText("or");

        show_password.setBackground(new java.awt.Color(255, 255, 255));
        show_password.setForeground(new java.awt.Color(45, 59, 111));
        show_password.setText("show password");
        show_password.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                show_passwordActionPerformed(evt);
            }
        });

        forgot_password.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        forgot_password.setForeground(new java.awt.Color(255, 0, 0));
        forgot_password.setText("forgot password");

        bio_button.setBackground(new java.awt.Color(0, 102, 153));
        bio_button.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        bio_button.setForeground(new java.awt.Color(255, 255, 255));
        bio_button.setText("LOGIN WITH BIOMETRIC");
        bio_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bio_buttonActionPerformed(evt);
            }
        });

        jPanel2.setBackground(new java.awt.Color(45, 59, 111));

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(255, 255, 255));
        jLabel2.setText("PAN-ANTLANTIC UNIVERSITY");

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
                        .addGap(36, 36, 36)
                        .addComponent(jLabel2))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(91, 91, 91)
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

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(64, 64, 64)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(login_button, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addGap(189, 189, 189)
                                .addComponent(jLabel6)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bio_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(password_field, javax.swing.GroupLayout.DEFAULT_SIZE, 396, Short.MAX_VALUE)
                    .addComponent(email_field)
                    .addComponent(jLabel4)
                    .addComponent(jLabel5)
                    .addComponent(register_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(show_password)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(forgot_password)))
                .addContainerGap(79, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 43, Short.MAX_VALUE)
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(email_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(36, 36, 36)
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(password_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(show_password)
                    .addComponent(forgot_password))
                .addGap(24, 24, 24)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(login_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bio_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel6)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(register_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(32, 32, 32))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void register_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_register_buttonActionPerformed
        new registration_frame().setVisible(true);
        this.setVisible(false);
    }//GEN-LAST:event_register_buttonActionPerformed

    private void manual_sign_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manual_sign_buttonActionPerformed
        stopBiometricCapture();
        this.setVisible(true);
        biometric_frame.setVisible(false);
    }//GEN-LAST:event_manual_sign_buttonActionPerformed

    private void cancel_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancel_buttonActionPerformed
        stopBiometricCapture();
        this.setVisible(true);
        biometric_frame.setVisible(false);
    }//GEN-LAST:event_cancel_buttonActionPerformed

    private void bio_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bio_buttonActionPerformed
        if (!isDeviceConnected) {
            JOptionPane.showMessageDialog(this,
                    "Fingerprint scanner not connected! Please connect your USB device.",
                    "Device Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        biometric_frame.setVisible(true);
        biometric_frame.pack();
        biometric_frame.toFront();
        this.setVisible(false);

        startBiometricCapture();
    }//GEN-LAST:event_bio_buttonActionPerformed

    private void login_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_login_buttonActionPerformed
        String loginEmail = email_field.getText().trim();
        String loginPassword = new String(password_field.getPassword());

        if (loginEmail.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your email!",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            email_field.requestFocus();
            return;
        }

        if (loginPassword.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter your password!",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            email_field.requestFocus();
            return;
        }

        // Validate email format
        if (!isValidEmail(loginEmail)) {
            JOptionPane.showMessageDialog(this, "Please enter a valid email address!",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            email_field.requestFocus();
            return;
        }

        int userId = authenticateUser(loginEmail, loginPassword);
        if (userId != -1 && currentUserRole != null) {
            JOptionPane.showMessageDialog(this, "Login successful! Welcome back.",
                    "Login Success", JOptionPane.INFORMATION_MESSAGE);
            navigateToDashboard(currentUserRole, userId);
            email_field.setText("");
            password_field.setText("");

        } else {
            JOptionPane.showMessageDialog(this,
                    "Invalid email or password. Please try again.",
                    "Login Failed", JOptionPane.ERROR_MESSAGE);

            email_field.setText("");
            password_field.requestFocus();
        }
    }//GEN-LAST:event_login_buttonActionPerformed

    private void show_passwordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_show_passwordActionPerformed
        if (show_password.isSelected()) {
            password_field.setEchoChar((char) 0);
        } else {
            password_field.setEchoChar('*');
        }     
    }//GEN-LAST:event_show_passwordActionPerformed

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new login_frame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bio_button;
    private javax.swing.JFrame biometric_frame;
    private javax.swing.JButton cancel_button;
    private javax.swing.JTextField email_field;
    private javax.swing.JLabel fingerprint_label;
    private javax.swing.JLabel forgot_password;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JButton login_button;
    private javax.swing.JButton manual_sign_button;
    private javax.swing.JPasswordField password_field;
    private javax.swing.JButton register_button;
    private javax.swing.JCheckBox show_password;
    // End of variables declaration//GEN-END:variables
}