package biometrics_exam;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import com.digitalpersona.onetouch.*;
import com.digitalpersona.onetouch.capture.*;
import com.digitalpersona.onetouch.capture.event.*;
import com.digitalpersona.onetouch.processing.*;
import com.digitalpersona.onetouch.verification.DPFPVerification;
import com.digitalpersona.onetouch.verification.DPFPVerificationResult;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import javax.imageio.ImageIO;
import javax.swing.filechooser.FileNameExtensionFilter;

public class staff_frame extends javax.swing.JFrame {
  
    private int currentUserId;
    private String currentUserUnit;
    private javax.swing.Timer refreshTimer;
    private DPFPCapture capturer;
    private DPFPTemplate template;
    private DPFPFeatureSet featureSet;
    private BufferedImage fingerprintImage;
    private boolean isDeviceConnected = false;
    private boolean isCapturing = false;
    private boolean isSigningIn = true;
    private boolean isEditing = false;
    private String originalFullName, originalEmail, originalPhone, originalUnit, originalRole, originalGender;
    private java.util.Date originalDob;
    private byte[] originalPhoto;
    private byte[] currentPhoto;
    private File selectedImageFile;
    private static final String SENDER_EMAIL = "ezraagun@gmail.com";
    private static final String SENDER_PASSWORD = "nwhotxkkqbsbwvzy";

    public staff_frame(int userId) {
        initComponents();
        this.currentUserId = userId;
        loadUserData();
        setupEventListeners();
        loadDashboardData();
        loadShiftsData();
        loadStaffCombo();
        startAutoRefresh();
        initializeFingerprintSystem();
        initializeProfile();
    }
    
    public staff_frame() {
        this(-1);
        loadUserData();
    }  
    
    private void initializeFingerprintSystem() {
        try {
            capturer = DPFPGlobal.getCaptureFactory().createCapture();

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
            displayFingerprintOnSignFrame(fingerprintImage);

            DPFPFeatureExtraction featureExtractor = DPFPGlobal.getFeatureExtractionFactory().createFeatureExtraction();

            try {
                featureSet = featureExtractor.createFeatureSet(sample, DPFPDataPurpose.DATA_PURPOSE_VERIFICATION);

                if (featureSet != null) {
                    updateBiometricStatus("Fingerprint captured. Verifying...");
                    verifyFingerprintForAttendance();
                }

            } catch (DPFPImageQualityException ex) {
                updateBiometricStatus("Poor fingerprint quality. Please try again.");
            }

        } catch (Exception e) {
            updateBiometricStatus("Error processing fingerprint: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void verifyFingerprintForAttendance() {
        if (featureSet == null) {
            updateBiometricStatus("No fingerprint data to verify.");
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

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

                        // Deserialize template from database
                        DPFPTemplate dbTemplate = DPFPGlobal.getTemplateFactory().createTemplate();
                        dbTemplate.deserialize(templateBytes);

                        // Verify against current fingerprint
                        DPFPVerificationResult result = verifier.verify(featureSet, dbTemplate);

                        if (result.isVerified()) {
                            int userId = rs.getInt("user_id");
                            String userRole = rs.getString("role");
                            boolean isActive = rs.getBoolean("is_active");

                            if (isActive) {
                                SwingUtilities.invokeLater(() -> {
                                    stopBiometricCapture();
                                    updateBiometricStatus("Fingerprint verified! Processing attendance...");

                                    // Process attendance based on sign-in or sign-out
                                    if (isSigningIn) {
                                        processSignIn(userId);
                                    } else {
                                        processSignOut(userId);
                                    }
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
                    continue; // Continue checking other templates
                }
            }

            // No match found
            updateBiometricStatus("Fingerprint not recognized. Please try again.");

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

    private void processSignIn(int userId) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            LocalTime currentTime = LocalTime.now();
            LocalDate currentDate = LocalDate.now();

            ps = con.prepareStatement(
                    "SELECT ss.shift_id, s.start_time, s.end_time, s.shift_name, u.full_name, u.unit "
                    + "FROM staff_shifts ss "
                    + "JOIN shifts s ON ss.shift_id = s.shift_id "
                    + "JOIN users u ON ss.user_id = u.user_id "
                    + "WHERE ss.user_id = ? AND ss.shift_date = ? AND ss.is_active = TRUE"
            );
            ps.setInt(1, userId);
            ps.setDate(2, java.sql.Date.valueOf(currentDate));
            rs = ps.executeQuery();

            if (!rs.next()) {
                PreparedStatement debugPs = con.prepareStatement(
                        "SELECT ss.shift_date, s.shift_name, u.full_name, ss.is_active "
                        + "FROM staff_shifts ss "
                        + "JOIN shifts s ON ss.shift_id = s.shift_id "
                        + "JOIN users u ON ss.user_id = u.user_id "
                        + "WHERE ss.user_id = ? "
                        + "ORDER BY ss.shift_date DESC LIMIT 5"
                );
                debugPs.setInt(1, userId);
                ResultSet debugRs = debugPs.executeQuery();

                System.out.println("Debug - Recent shifts for user " + userId + ":");
                while (debugRs.next()) {
                    System.out.println("  Date: " + debugRs.getDate("shift_date")
                            + ", Shift: " + debugRs.getString("shift_name")
                            + ", User: " + debugRs.getString("full_name")
                            + ", Active: " + debugRs.getBoolean("is_active"));
                }
                debugRs.close();
                debugPs.close();

                JOptionPane.showMessageDialog(this,
                        "You have no shifts assigned for today (" + currentDate + ").\n"
                        + "Please contact your supervisor if this is incorrect.",
                        "Sign-in Error", JOptionPane.WARNING_MESSAGE);
                sign_frame.setVisible(false);
                return;
            }

            int shiftId = rs.getInt("shift_id");
            LocalTime shiftStartTime = rs.getTime("start_time").toLocalTime();
            LocalTime shiftEndTime = rs.getTime("end_time").toLocalTime();
            String shiftName = rs.getString("shift_name");
            String userName = rs.getString("full_name");
            String userUnit = rs.getString("unit");

            System.out.println("Debug - Found shift: " + shiftName + " for " + userName
                    + " (" + userUnit + ") from " + shiftStartTime + " to " + shiftEndTime);

            rs.close();
            ps.close();

            // Check if shift has already ended
            if (currentTime.isAfter(shiftEndTime)) {
                JOptionPane.showMessageDialog(this,
                        "Your " + shiftName + " shift has already ended at " + shiftEndTime
                        + ". You cannot sign in after shift end time.",
                        "Sign-in Error", JOptionPane.WARNING_MESSAGE);
                sign_frame.setVisible(false);
                return;
            }

            // Check if user has already signed in today
            ps = con.prepareStatement(
                    "SELECT attendance_id, sign_in_time FROM attendance "
                    + "WHERE user_id = ? AND attendance_date = ? AND shift_id = ?"
            );
            ps.setInt(1, userId);
            ps.setDate(2, java.sql.Date.valueOf(currentDate));
            ps.setInt(3, shiftId);
            rs = ps.executeQuery();

            if (rs.next() && rs.getTimestamp("sign_in_time") != null) {
                Timestamp existingSignIn = rs.getTimestamp("sign_in_time");
                JOptionPane.showMessageDialog(this,
                        "You have already signed in today at "
                        + existingSignIn.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + ".",
                        "Sign-in Error", JOptionPane.WARNING_MESSAGE);
                sign_frame.setVisible(false);
                return;
            }

            rs.close();
            ps.close();

            // Determine if user is late )
            LocalTime lateThreshold = shiftStartTime.plusMinutes(30);
            boolean isLate = currentTime.isAfter(lateThreshold);

            // Insert or update attendance record
            ps = con.prepareStatement(
                    "INSERT INTO attendance (user_id, shift_id, sign_in_time, attendance_date, is_late, is_absent) "
                    + "VALUES (?, ?, ?, ?, ?, FALSE) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "sign_in_time = VALUES(sign_in_time), "
                    + "is_late = VALUES(is_late), "
                    + "is_absent = FALSE, "
                    + "updated_at = CURRENT_TIMESTAMP"
            );
            ps.setInt(1, userId);
            ps.setInt(2, shiftId);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setDate(4, java.sql.Date.valueOf(currentDate));
            ps.setBoolean(5, isLate);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                logSystemAction(userId, "SIGN_IN",
                        "User " + userName + " signed in for " + shiftName + " shift" + (isLate ? " (LATE)" : ""));

                String message = "Sign-in successful!"
                        + "\nUser: " + userName
                        + "\nUnit: " + userUnit
                        + "\nShift: " + shiftName + " (" + shiftStartTime + " - " + shiftEndTime + ")"
                        + "\nSign-in Time: " + currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        + (isLate ? "\n\n⚠️ NOTE: You are marked as LATE (more than 30 minutes after shift start)." : "");

                JOptionPane.showMessageDialog(this, message, "Sign-in Success",
                        isLate ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to record sign-in. Please try again.",
                        "Sign-in Error", JOptionPane.ERROR_MESSAGE);
            }

            sign_frame.setVisible(false);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Database error during sign-in: " + e.getMessage(),
                    "Sign-in Error", JOptionPane.ERROR_MESSAGE);
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

    private void processSignOut(int userId) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            LocalDate today = LocalDate.now();
            LocalTime currentTime = LocalTime.now();

            // Check if user has signed in today and hasn't signed out yet
            ps = con.prepareStatement(
                    "SELECT a.attendance_id, a.shift_id, a.sign_in_time, s.end_time, s.shift_name "
                    + "FROM attendance a "
                    + "JOIN shifts s ON a.shift_id = s.shift_id "
                    + "WHERE a.user_id = ? AND a.attendance_date = ? AND a.sign_in_time IS NOT NULL AND a.sign_out_time IS NULL"
            );
            ps.setInt(1, userId);
            ps.setDate(2, java.sql.Date.valueOf(today));

            rs = ps.executeQuery();

            if (!rs.next()) {
                JOptionPane.showMessageDialog(this,
                        "You haven't signed in today or have already signed out.",
                        "Sign-out Error", JOptionPane.WARNING_MESSAGE);
                sign_frame.setVisible(false);
                return;
            }

            int attendanceId = rs.getInt("attendance_id");
            int shiftId = rs.getInt("shift_id");
            Timestamp signInTime = rs.getTimestamp("sign_in_time");
            LocalTime shiftEndTime = rs.getTime("end_time").toLocalTime();
            String shiftName = rs.getString("shift_name");

            rs.close();
            ps.close();

            // Determine if user is signing out early
            boolean isEarlyOut = currentTime.isBefore(shiftEndTime);

            // Update attendance record with sign-out time
            ps = con.prepareStatement(
                    "UPDATE attendance SET "
                    + "sign_out_time = ?, "
                    + "is_early_out = ?, "
                    + "updated_at = CURRENT_TIMESTAMP "
                    + "WHERE attendance_id = ?"
            );
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setBoolean(2, isEarlyOut);
            ps.setInt(3, attendanceId);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                // Log the sign-out action
                logSystemAction(userId, "SIGN_OUT",
                        "User signed out from " + shiftName + " shift" + (isEarlyOut ? " (EARLY)" : ""));

                // Calculate work duration
                LocalDateTime signInDateTime = signInTime.toLocalDateTime();
                LocalDateTime signOutDateTime = LocalDateTime.now();
                long minutes = java.time.Duration.between(signInDateTime, signOutDateTime).toMinutes();
                long hours = minutes / 60;
                minutes = minutes % 60;

                String message = "Sign-out successful!"
                        + (isEarlyOut ? "\nNote: You are signing out before your shift ends." : "")
                        + "\nShift: " + shiftName
                        + "\nWork Duration: " + hours + " hours, " + minutes + " minutes"
                        + "\nSign-out Time: " + currentTime.toString();

                JOptionPane.showMessageDialog(this, message, "Sign-out Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to record sign-out. Please try again.",
                        "Sign-out Error", JOptionPane.ERROR_MESSAGE);
            }

            sign_frame.setVisible(false);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Database error during sign-out: " + e.getMessage(),
                    "Sign-out Error", JOptionPane.ERROR_MESSAGE);
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

    private void logSystemAction(int userId, String actionType, String description) {
        Connection con = null;
        PreparedStatement ps = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement(
                    "INSERT INTO system_logs (user_id, action_type, description) VALUES (?, ?, ?)"
            );
            ps.setInt(1, userId);
            ps.setString(2, actionType);
            ps.setString(3, description);

            ps.executeUpdate();

        } catch (Exception e) {
            System.err.println("Error logging system action: " + e.getMessage());
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing log connection: " + e.getMessage());
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

    private void displayFingerprintOnSignFrame(BufferedImage image) {
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
            if (jLabel21 != null) {
                jLabel21.setText(message);

                if (message.toLowerCase().contains("ready for fingerprint")) {
                    jLabel21.setForeground(Color.GREEN);
                } else {
                    jLabel21.setForeground(Color.RED);
                }
            }
        });
    }

    private void startBiometricCapture() {
        try {
            if (capturer != null && isDeviceConnected) {
                isCapturing = true;
                capturer.startCapture();
                updateBiometricStatus("Ready for fingerprint. Place finger on scanner...");
                displayFingerprintOnSignFrame(createPlaceholderImage());
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
    
    private void loadUserData() {
        if (currentUserId == -1) {
            jLabel13.setText("Welcome back, Guest");
            jLabel15.setText("No Unit");
            jLabel16.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement("SELECT full_name, unit FROM users WHERE user_id = ? AND is_active = TRUE");
            ps.setInt(1, currentUserId);
            rs = ps.executeQuery();

            if (rs.next()) {
                String fullName = rs.getString("full_name");
                String unit = rs.getString("unit");
                this.currentUserUnit = unit;

                String firstName = fullName.split(" ")[0];
                jLabel13.setText("Welcome back, " + firstName);
                jLabel15.setText(unit);
            } else {
                jLabel13.setText("Welcome back, User");
                jLabel15.setText("No Unit");
                this.currentUserUnit = "None";
            }

            jLabel16.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading user data: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            jLabel13.setText("Welcome back, User");
            jLabel15.setText("No Unit");
            jLabel16.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
            this.currentUserUnit = "None";
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void loadDashboardData() {
        loadStatusAnalytics();
    }

    private void loadStatusAnalytics() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DefaultListModel<String> statusModel = new DefaultListModel<>();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            // Get total shifts this month
            ps = con.prepareStatement(
                    "SELECT COUNT(*) as total_shifts FROM staff_shifts ss "
                    + "WHERE ss.user_id = ? AND MONTH(ss.shift_date) = MONTH(CURDATE()) AND YEAR(ss.shift_date) = YEAR(CURDATE())"
            );
            ps.setInt(1, currentUserId);
            rs = ps.executeQuery();

            if (rs.next()) {
                statusModel.addElement("📊 Total Shifts This Month: " + rs.getInt("total_shifts"));
            }
            rs.close();
            ps.close();

            // Get pending swap requests
            ps = con.prepareStatement(
                    "SELECT COUNT(*) as pending_swaps FROM shift_swap_requests "
                    + "WHERE (requester_id = ? OR target_user_id = ?) AND status = 'Pending'"
            );
            ps.setInt(1, currentUserId);
            ps.setInt(2, currentUserId);
            rs = ps.executeQuery();

            if (rs.next()) {
                statusModel.addElement("🔄 Pending Swap Requests: " + rs.getInt("pending_swaps"));
            }
            rs.close();
            ps.close();

            // Get upcoming shifts this week
            ps = con.prepareStatement(
                    "SELECT COUNT(*) as upcoming_shifts FROM staff_shifts ss "
                    + "WHERE ss.user_id = ? AND ss.shift_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)"
            );
            ps.setInt(1, currentUserId);
            rs = ps.executeQuery();

            if (rs.next()) {
                statusModel.addElement("📅 Upcoming Shifts This Week: " + rs.getInt("upcoming_shifts"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            statusModel.addElement("❌ Error loading analytics");
        } finally {
            closeResources(con, ps, rs);
        }

        status_list.setModel(statusModel);
    }

    // Load shifts data for the shifts tab
    private void loadShiftsData() {
        loadUpcomingShifts();
        loadSwapRequests();
    }

    // Load upcoming shifts
    private void loadUpcomingShifts() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DefaultListModel<String> upcomingModel = new DefaultListModel<>();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement(
                    "SELECT ss.shift_date, s.shift_name, s.start_time, s.end_time "
                    + "FROM staff_shifts ss "
                    + "JOIN shifts s ON ss.shift_id = s.shift_id "
                    + "WHERE ss.user_id = ? AND ss.shift_date >= CURDATE() AND ss.is_active = TRUE "
                    + "ORDER BY ss.shift_date, s.start_time LIMIT 10"
            );
            ps.setInt(1, currentUserId);
            rs = ps.executeQuery();

            while (rs.next()) {
                String date = rs.getDate("shift_date").toString();
                String shiftName = rs.getString("shift_name");
                String startTime = rs.getTime("start_time").toString();
                String endTime = rs.getTime("end_time").toString();

                upcomingModel.addElement(String.format("📅 %s - %s (%s - %s)",
                        date, shiftName, startTime.substring(0, 5), endTime.substring(0, 5)));
            }

            if (upcomingModel.isEmpty()) {
                upcomingModel.addElement("No upcoming shifts scheduled");
            }

        } catch (Exception e) {
            e.printStackTrace();
            upcomingModel.addElement("❌ Error loading upcoming shifts");
        } finally {
            closeResources(con, ps, rs);
        }

        upcoming_list.setModel(upcomingModel);
    }

    private void loadSwapRequests() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DefaultListModel<String> requestModel = new DefaultListModel<>();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement(
                    "SELECT ssr.*, u.full_name as target_name, s1.shift_name as req_shift, s2.shift_name as target_shift "
                    + "FROM shift_swap_requests ssr "
                    + "JOIN users u ON ssr.target_user_id = u.user_id "
                    + "JOIN shifts s1 ON ssr.requester_shift_id = s1.shift_id "
                    + "JOIN shifts s2 ON ssr.target_shift_id = s2.shift_id "
                    + "WHERE ssr.requester_id = ? "
                    + "ORDER BY ssr.requested_at DESC LIMIT 5"
            );
            ps.setInt(1, currentUserId);
            rs = ps.executeQuery();

            while (rs.next()) {
                String targetName = rs.getString("target_name");
                String status = rs.getString("status");
                String swapDate = rs.getDate("swap_date").toString();
                String reqShift = rs.getString("req_shift");
                String targetShift = rs.getString("target_shift");

                String statusIcon = status.equals("Pending") ? "🟡"
                        : status.equals("Approved") ? "✅" : "❌";

                requestModel.addElement(String.format("%s %s - Swap %s with %s on %s [%s]",
                        statusIcon, status, reqShift, targetName, swapDate, targetShift));
            }

            if (requestModel.isEmpty()) {
                requestModel.addElement("No swaps made");
            }

        } catch (Exception e) {
            e.printStackTrace();
            requestModel.addElement("❌ Error loading swap requests");
        } finally {
            closeResources(con, ps, rs);
        }

        request_list.setModel(requestModel);
    }

    private void loadStaffCombo() {
        if ("None".equals(currentUserUnit)) {
            staff_combo.removeAllItems();
            staff_combo.addItem("No unit assigned");
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement(
                    "SELECT user_id, full_name FROM users "
                    + "WHERE unit = ? AND user_id != ? AND is_active = TRUE AND unit != 'None' "
                    + "ORDER BY full_name"
            );
            ps.setString(1, currentUserUnit);
            ps.setInt(2, currentUserId);
            rs = ps.executeQuery();

            staff_combo.removeAllItems();
            staff_combo.addItem("Select Staff Member");

            while (rs.next()) {
                int userId = rs.getInt("user_id");
                String fullName = rs.getString("full_name");
                staff_combo.addItem(new StaffMember(userId, fullName));
            }

            if (staff_combo.getItemCount() == 1) {
                staff_combo.addItem("No other staff in your unit");
            }

        } catch (Exception e) {
            e.printStackTrace();
            staff_combo.removeAllItems();
            staff_combo.addItem("Error loading staff");
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void setupEventListeners() {
        current_date.addPropertyChangeListener("date", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getNewValue() != null) {
                    loadShiftTimesForDate((Date) evt.getNewValue());
                }
            }
        });

        reason_area.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (reason_area.getText().equals("Enter reason for shift swap...")) {
                    reason_area.setText("");
                    reason_area.setForeground(java.awt.Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (reason_area.getText().isEmpty()) {
                    reason_area.setText("Enter reason for shift swap...");
                    reason_area.setForeground(java.awt.Color.LIGHT_GRAY);
                }
            }
        });
    }

    private void loadShiftTimesForDate(Date selectedDate) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            java.sql.Date sqlDate = new java.sql.Date(selectedDate.getTime());

            ps = con.prepareStatement(
                    "SELECT s.shift_id, s.shift_name, s.start_time, s.end_time "
                    + "FROM staff_shifts ss "
                    + "JOIN shifts s ON ss.shift_id = s.shift_id "
                    + "WHERE ss.user_id = ? AND ss.shift_date = ? AND ss.is_active = TRUE"
            );
            ps.setInt(1, currentUserId);
            ps.setDate(2, sqlDate);
            rs = ps.executeQuery();

            current_time.removeAllItems();
            current_time.addItem("Select shift time..."); 

            boolean hasShifts = false;
            while (rs.next()) { 
                hasShifts = true;
                int shiftId = rs.getInt("shift_id");
                String shiftName = rs.getString("shift_name");

                // Get time values as Time objects first, then convert to string
                java.sql.Time startTime = rs.getTime("start_time");
                java.sql.Time endTime = rs.getTime("end_time");

                String startTimeStr = startTime.toString();
                String endTimeStr = endTime.toString();

                current_time.addItem(new ShiftTime(shiftId, shiftName, startTimeStr, endTimeStr));
            }

            if (!hasShifts) {
                current_time.addItem("No shift assigned for this date");
            }

        } catch (Exception e) {
            e.printStackTrace();
            current_time.removeAllItems();
            current_time.addItem("Error loading shift times");
        } finally {
            closeResources(con, ps, rs);
        }
    }
    
    private void showAvailableSpots() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement(
                    "SELECT DISTINCT ss.shift_date, s.shift_name, s.start_time, s.end_time, u.full_name "
                    + "FROM staff_shifts ss "
                    + "JOIN shifts s ON ss.shift_id = s.shift_id "
                    + "JOIN users u ON ss.user_id = u.user_id "
                    + "WHERE u.unit = ? AND ss.user_id != ? AND ss.shift_date >= CURDATE() "
                    + "AND ss.is_active = TRUE "
                    + "ORDER BY ss.shift_date, s.start_time"
            );
            ps.setString(1, currentUserUnit);
            ps.setInt(2, currentUserId);
            rs = ps.executeQuery();

            StringBuilder availableSpots = new StringBuilder();
            availableSpots.append("Available Spots for Swapping:\n\n");

            while (rs.next()) {
                String date = rs.getDate("shift_date").toString();
                String shiftName = rs.getString("shift_name");
                String startTime = rs.getTime("start_time").toString();
                String endTime = rs.getTime("end_time").toString();
                String staffName = rs.getString("full_name");

                availableSpots.append(String.format("📅 %s - %s (%s - %s) - %s\n",
                        date, shiftName, startTime.substring(0, 5), endTime.substring(0, 5), staffName));
            }

            if (availableSpots.length() == 0) {
                availableSpots.append("No available spots for swapping at this time.");
            }

            JTextArea textArea = new JTextArea(availableSpots.toString());
            textArea.setEditable(false);
            textArea.setRows(15);
            textArea.setColumns(50);

            JScrollPane scrollPane = new JScrollPane(textArea);
            JOptionPane.showMessageDialog(this, scrollPane, "Available Spots", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading available spots: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }
    
    private void submitSwapRequest() {
        // Validate inputs
        if (current_date.getDate() == null) {
            JOptionPane.showMessageDialog(swap_frame, "Please select a date.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Fixed validation - check for valid selection
        if (current_time.getSelectedIndex() <= 0 || current_time.getSelectedItem() == null
                || !(current_time.getSelectedItem() instanceof ShiftTime)) {
            JOptionPane.showMessageDialog(swap_frame, "Please select a valid shift time.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (staff_combo.getSelectedIndex() <= 0
                || !(staff_combo.getSelectedItem() instanceof StaffMember)) {
            JOptionPane.showMessageDialog(swap_frame, "Please select a staff member to swap with.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String reason = reason_area.getText().trim();
        if (reason.isEmpty() || reason.equals("Enter reason for shift swap...")) {
            JOptionPane.showMessageDialog(swap_frame, "Please enter a reason for the swap.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");
            con.setAutoCommit(false);

            // Get selected data
            ShiftTime selectedShift = (ShiftTime) current_time.getSelectedItem();
            StaffMember selectedStaff = (StaffMember) staff_combo.getSelectedItem();
            java.sql.Date swapDate = new java.sql.Date(current_date.getDate().getTime());

            // Get target user's shift for the same date
            ps = con.prepareStatement(
                    "SELECT ss.shift_id FROM staff_shifts ss "
                    + "WHERE ss.user_id = ? AND ss.shift_date = ? AND ss.is_active = TRUE"
            );
            ps.setInt(1, selectedStaff.getUserId());
            ps.setDate(2, swapDate);
            rs = ps.executeQuery();

            if (!rs.next()) {
                JOptionPane.showMessageDialog(swap_frame, "Selected staff member has no shift on the selected date.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                con.rollback();
                return;
            }

            int targetShiftId = rs.getInt("shift_id");
            rs.close();
            ps.close();

            // Insert swap request
            ps = con.prepareStatement(
                    "INSERT INTO shift_swap_requests (requester_id, target_user_id, requester_shift_id, "
                    + "target_shift_id, swap_date, request_reason, status) VALUES (?, ?, ?, ?, ?, ?, 'Pending')"
            );
            ps.setInt(1, currentUserId);
            ps.setInt(2, selectedStaff.getUserId());
            ps.setInt(3, selectedShift.getShiftId());
            ps.setInt(4, targetShiftId);
            ps.setDate(5, swapDate);
            ps.setString(6, reason);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                sendSwapRequestEmail(selectedStaff.getFullName(), swapDate.toString(), reason);

                con.commit();
                JOptionPane.showMessageDialog(swap_frame, "Swap request submitted successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);

                // Reset form
                current_date.setDate(null);
                current_time.removeAllItems();
                staff_combo.setSelectedIndex(0);
                reason_area.setText("Enter reason for shift swap...");
                reason_area.setForeground(java.awt.Color.LIGHT_GRAY);

                // Refresh data
                loadShiftsData();
                swap_frame.setVisible(false);
            }

        } catch (Exception e) {
            try {
                if (con != null) {
                    con.rollback();
                }
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            e.printStackTrace();
            JOptionPane.showMessageDialog(swap_frame, "Error submitting swap request: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            try {
                if (con != null) {
                    con.setAutoCommit(true);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            closeResources(con, ps, rs);
        }
    }
    
    private static class ShiftTime {

        private final int shiftId;
        private final String displayText;
        private final String startTime; 
        private final String endTime;

        public ShiftTime(int shiftId, String shiftName, String startTime, String endTime) {
            this.shiftId = shiftId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.displayText = shiftName + " (" + startTime.substring(0, 5) + " - " + endTime.substring(0, 5) + ")";
        }

        public int getShiftId() {
            return shiftId;
        }

        public String getStartTime() {
            return startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }

    private void sendSwapRequestEmail(String targetStaffName, String swapDate, String reason) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            // Get supervisor email
            ps = con.prepareStatement(
                    "SELECT email, full_name FROM users WHERE unit = ? AND role IN ('Supervisor', 'IT_Head', 'Director') "
                    + "AND is_active = TRUE LIMIT 1"
            );
            ps.setString(1, currentUserUnit);
            rs = ps.executeQuery();

            if (rs.next()) {
                String supervisorEmail = rs.getString("email");
                String supervisorName = rs.getString("full_name");

                // Get current user's name
                ps.close();
                rs.close();
                ps = con.prepareStatement("SELECT full_name FROM users WHERE user_id = ?");
                ps.setInt(1, currentUserId);
                rs = ps.executeQuery();

                String requesterName = "";
                if (rs.next()) {
                    requesterName = rs.getString("full_name");
                }

                String subject = "Shift Swap Request - " + requesterName;
                String body = String.format(
                        "Dear %s,\n\n"
                        + "A new shift swap request has been submitted:\n\n"
                        + "Requester: %s\n"
                        + "Target Staff: %s\n"
                        + "Swap Date: %s\n"
                        + "Reason: %s\n\n"
                        + "Please review and approve/decline this request in the system.\n\n"
                        + "Best regards,\n"
                        + "PAMS System",
                        supervisorName, requesterName, targetStaffName, swapDate, reason
                );

                boolean emailSent = sendEmailWithFallback(supervisorEmail, subject, body);
                if (emailSent) {
                    // Record successful email in database
                    ps.close();
                    rs.close();
                    ps = con.prepareStatement(
                            "INSERT INTO email_notifications (user_id, email_type, subject, body, sent_status) "
                            + "VALUES ((SELECT user_id FROM users WHERE email = ?), 'Shift_Swap', ?, ?, 'Sent')"
                    );
                    ps.setString(1, supervisorEmail);
                    ps.setString(2, subject);
                    ps.setString(3, body);
                    ps.executeUpdate();

                    System.out.println("Email notification sent to: " + supervisorEmail);
                } else {
                    System.err.println("Failed to send swap request email to: " + supervisorEmail);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error sending email notification: " + e.getMessage());
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private boolean sendEmailWithFallback(String recipientEmail, String subject, String body) {
        boolean emailSent = false;

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", "smtp.gmail.com");

            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
            emailSent = true;
            System.out.println("Email sent successfully via STARTTLS to: " + recipientEmail);

        } catch (MessagingException e) {
            System.err.println("STARTTLS failed: " + e.getMessage());

            try {
                Properties sslProps = new Properties();
                sslProps.put("mail.smtp.host", "smtp.gmail.com");
                sslProps.put("mail.smtp.port", "465");
                sslProps.put("mail.smtp.auth", "true");
                sslProps.put("mail.smtp.ssl.enable", "true");
                sslProps.put("mail.smtp.ssl.trust", "smtp.gmail.com");

                Session sslSession = Session.getInstance(sslProps, new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                    }
                });

                Message sslMessage = new MimeMessage(sslSession);
                sslMessage.setFrom(new InternetAddress(SENDER_EMAIL));
                sslMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
                sslMessage.setSubject(subject);
                sslMessage.setText(body);

                Transport.send(sslMessage);
                emailSent = true;
                System.out.println("Email sent successfully via SSL to: " + recipientEmail);

            } catch (MessagingException sslEx) {
                System.err.println("SSL fallback also failed: " + sslEx.getMessage());
            }
        }

        return emailSent;
    }

    private void startAutoRefresh() {
        refreshTimer = new javax.swing.Timer(300000, e -> { // Refresh every 5 minutes
            loadDashboardData();
            loadShiftsData();
        });
        refreshTimer.start();
    }
    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        super.dispose();
    }

    private void closeResources(Connection con, PreparedStatement ps, ResultSet rs) {
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static class StaffMember {

        private final int userId;
        private final String fullName;

        public StaffMember(int userId, String fullName) {
            this.userId = userId;
            this.fullName = fullName;
        }

        public int getUserId() {
            return userId;
        }

        public String getFullName() {
            return fullName;
        }

        @Override
        public String toString() {
            return fullName;
        }
    }
    
     private void initializeProfile() {
        loadProfileData();
        setFieldsEditable(false);
    }

    private void loadProfileData() {
        if (currentUserId == -1) {
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement("SELECT full_name, date_of_birth, gender, email, phone_number, unit, role, photo FROM users WHERE user_id = ? AND is_active = TRUE");
            ps.setInt(1, currentUserId);
            rs = ps.executeQuery();

            if (rs.next()) {
                // Store original values
                originalFullName = rs.getString("full_name");
                originalDob = rs.getDate("date_of_birth");
                originalGender = rs.getString("gender");
                originalEmail = rs.getString("email");
                originalPhone = rs.getString("phone_number");
                originalUnit = rs.getString("unit");
                originalRole = rs.getString("role");
                originalPhoto = rs.getBytes("photo");
                currentPhoto = originalPhoto;

                // Set field values
                username_field.setText(originalFullName);
                dob_chooser.setDate(originalDob);
                gender_combo.setSelectedItem(originalGender);
                email_field.setText(originalEmail);
                phone_field.setText(originalPhone);
                unit_combo.setSelectedItem(originalUnit);
                role_combo.setSelectedItem(originalRole);

                // Load profile image
                loadProfileImage(originalPhoto);
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading profile data: " + e.getMessage(),
                    "Profile Error", JOptionPane.ERROR_MESSAGE);
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
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadProfileImage(byte[] imageData) {
        if (imageData != null && imageData.length > 0) {
            try {
                ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
                BufferedImage image = ImageIO.read(bis);
                if (image != null) {
                    Image scaledImage = image.getScaledInstance(150, 150, Image.SCALE_SMOOTH);
                    image_label.setIcon(new ImageIcon(scaledImage));
                } else {
                    setDefaultProfileImage();
                }
            } catch (Exception e) {
                setDefaultProfileImage();
                System.err.println("Error loading profile image: " + e.getMessage());
            }
        } else {
            setDefaultProfileImage();
        }
    }

    private void setDefaultProfileImage() {
        try {
            BufferedImage defaultImage = new BufferedImage(150, 150, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = defaultImage.createGraphics();
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.fillRect(0, 0, 150, 150);
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawOval(25, 25, 100, 100);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.drawString("No Photo", 55, 80);
            g2d.dispose();
            image_label.setIcon(new ImageIcon(defaultImage));
        } catch (Exception e) {
            System.err.println("Error creating default image: " + e.getMessage());
        }
    }

    private void setFieldsEditable(boolean editable) {
        username_field.setEditable(editable);
        dob_chooser.setEnabled(editable);
        gender_combo.setEnabled(editable);
        email_field.setEditable(editable);
        phone_field.setEditable(editable);
        unit_combo.setEnabled(editable);
        role_combo.setEnabled(editable);
        browse_image_button.setEnabled(editable);
    }

    private boolean hasChanges() {
        boolean nameChanged = !originalFullName.equals(username_field.getText().trim());
        boolean dobChanged = !originalDob.equals(dob_chooser.getDate());
        boolean genderChanged = !originalGender.equals(gender_combo.getSelectedItem().toString());
        boolean emailChanged = !originalEmail.equals(email_field.getText().trim());
        boolean phoneChanged = !originalPhone.equals(phone_field.getText().trim());
        boolean unitChanged = !originalUnit.equals(unit_combo.getSelectedItem().toString());
        boolean roleChanged = !originalRole.equals(role_combo.getSelectedItem().toString());
        boolean photoChanged = !Arrays.equals(originalPhoto, currentPhoto);

        return nameChanged || dobChanged || genderChanged || emailChanged || phoneChanged || unitChanged || roleChanged || photoChanged;
    }

    private void saveProfileChanges() {
        Connection con = null;
        PreparedStatement ps = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String sql = "UPDATE users SET full_name = ?, date_of_birth = ?, gender = ?, email = ?, phone_number = ?, unit = ?, role = ?, photo = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
            ps = con.prepareStatement(sql);

            ps.setString(1, username_field.getText().trim());
            ps.setDate(2, new java.sql.Date(dob_chooser.getDate().getTime()));
            ps.setString(3, gender_combo.getSelectedItem().toString());
            ps.setString(4, email_field.getText().trim());
            ps.setString(5, phone_field.getText().trim());
            ps.setString(6, unit_combo.getSelectedItem().toString());
            ps.setString(7, role_combo.getSelectedItem().toString());

            if (currentPhoto != null) {
                ps.setBytes(8, currentPhoto);
            } else {
                ps.setNull(8, java.sql.Types.LONGVARBINARY);
            }

            ps.setInt(9, currentUserId);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                JOptionPane.showMessageDialog(this, "Profile updated successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);

                originalFullName = username_field.getText().trim();
                originalDob = dob_chooser.getDate();
                originalGender = gender_combo.getSelectedItem().toString();
                originalEmail = email_field.getText().trim();
                originalPhone = phone_field.getText().trim();
                originalUnit = unit_combo.getSelectedItem().toString();
                originalRole = role_combo.getSelectedItem().toString();
                originalPhoto = currentPhoto;

                loadUserData();

            } else {
                JOptionPane.showMessageDialog(this, "Failed to update profile!",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error updating profile: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
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
            e.printStackTrace();
            return null;
        }
    }

    private boolean verifyCurrentPassword(String inputPassword) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement("SELECT password_hash FROM users WHERE user_id = ?");
            ps.setInt(1, currentUserId);
            rs = ps.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                String inputHash = hashPassword(inputPassword);
                return storedHash.equals(inputHash);
            }

        } catch (Exception e) {
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
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private boolean changePassword(String newPassword) {
        Connection con = null;
        PreparedStatement ps = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String hashedPassword = hashPassword(newPassword);
            ps = con.prepareStatement("UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?");
            ps.setString(1, hashedPassword);
            ps.setInt(2, currentUserId);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private void showAllShiftsDialog() {
        if (currentUserId == -1) {
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement(
                    "SELECT ss.shift_date, s.shift_name, s.start_time, s.end_time, s.unit, "
                    + "CASE WHEN ss.shift_date < CURDATE() THEN 'Past' "
                    + "     WHEN ss.shift_date = CURDATE() THEN 'Today' "
                    + "     ELSE 'Upcoming' END as status "
                    + "FROM staff_shifts ss "
                    + "JOIN shifts s ON ss.shift_id = s.shift_id "
                    + "WHERE ss.user_id = ? AND ss.is_active = TRUE "
                    + "ORDER BY ss.shift_date DESC, s.start_time ASC"
            );
            ps.setInt(1, currentUserId);
            rs = ps.executeQuery();

            DefaultListModel<String> model = new DefaultListModel<>();

            while (rs.next()) {
                java.sql.Date shiftDate = rs.getDate("shift_date");
                String shiftName = rs.getString("shift_name");
                Time startTime = rs.getTime("start_time");
                Time endTime = rs.getTime("end_time");
                String unit = rs.getString("unit");
                String status = rs.getString("status");

                String shiftInfo = String.format("[%s] %s - %s (%s) %s-%s",
                        status, shiftDate.toString(), shiftName, unit,
                        startTime.toString(), endTime.toString());
                model.addElement(shiftInfo);
            }

            if (model.isEmpty()) {
                model.addElement("No shifts assigned");
            }

            JList<String> shiftsList = new JList<>(model);
            JScrollPane scrollPane = new JScrollPane(shiftsList);
            scrollPane.setPreferredSize(new java.awt.Dimension(500, 300));

            JOptionPane.showMessageDialog(this, scrollPane, "All My Shifts", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading shifts: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
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
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        swap_frame = new javax.swing.JFrame();
        jPanel11 = new javax.swing.JPanel();
        jLabel19 = new javax.swing.JLabel();
        jLabel20 = new javax.swing.JLabel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel4 = new javax.swing.JPanel();
        current_date = new com.toedter.calendar.JDateChooser();
        current_time = new javax.swing.JComboBox<>();
        jLabel35 = new javax.swing.JLabel();
        jLabel36 = new javax.swing.JLabel();
        staff_combo = new javax.swing.JComboBox<>();
        jLabel37 = new javax.swing.JLabel();
        jLabel38 = new javax.swing.JLabel();
        jScrollPane4 = new javax.swing.JScrollPane();
        reason_area = new javax.swing.JTextArea();
        submit_button = new javax.swing.JButton();
        sign_frame = new javax.swing.JFrame();
        sign_panel = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        fingerprint_label = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        cancel_button = new javax.swing.JButton();
        jLabel21 = new javax.swing.JLabel();
        password_frame = new javax.swing.JFrame();
        jPanel12 = new javax.swing.JPanel();
        jLabel22 = new javax.swing.JLabel();
        change_password_button = new javax.swing.JButton();
        jPanel13 = new javax.swing.JPanel();
        jLabel23 = new javax.swing.JLabel();
        jLabel24 = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        password_1 = new javax.swing.JPasswordField();
        show_password1 = new javax.swing.JCheckBox();
        show_password2 = new javax.swing.JCheckBox();
        password_2 = new javax.swing.JPasswordField();
        jLabel26 = new javax.swing.JLabel();
        device_status_label = new javax.swing.JLabel();
        finger_instruction_label = new javax.swing.JLabel();
        cancel_change_button = new javax.swing.JButton();
        jFileChooser1 = new javax.swing.JFileChooser();
        jPanel9 = new javax.swing.JPanel();
        jPanel10 = new javax.swing.JPanel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jSeparator4 = new javax.swing.JSeparator();
        jLabel16 = new javax.swing.JLabel();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        jPanel6 = new javax.swing.JPanel();
        jScrollPane11 = new javax.swing.JScrollPane();
        status_list = new javax.swing.JList<>();
        jScrollPane14 = new javax.swing.JScrollPane();
        upcoming_list = new javax.swing.JList<>();
        title_label = new javax.swing.JLabel();
        jScrollPane15 = new javax.swing.JScrollPane();
        request_list = new javax.swing.JList<>();
        jLabel18 = new javax.swing.JLabel();
        swap_button = new javax.swing.JButton();
        spots_button = new javax.swing.JButton();
        sign_button = new javax.swing.JButton();
        signout_button = new javax.swing.JButton();
        logout_button = new javax.swing.JButton();
        all_shifts_button = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        change_button = new javax.swing.JButton();
        username_field = new javax.swing.JTextField();
        dob_chooser = new com.toedter.calendar.JDateChooser();
        gender_combo = new javax.swing.JComboBox<>();
        email_field = new javax.swing.JTextField();
        phone_field = new javax.swing.JTextField();
        unit_combo = new javax.swing.JComboBox<>();
        role_combo = new javax.swing.JComboBox<>();
        jLabel6 = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        image_label = new javax.swing.JLabel();
        browse_image_button = new javax.swing.JButton();
        edit_button = new javax.swing.JButton();
        logout_button2 = new javax.swing.JButton();

        jPanel11.setBackground(new java.awt.Color(45, 59, 111));

        jLabel19.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel19.setForeground(new java.awt.Color(255, 255, 255));
        jLabel19.setText("SHIFT SWAP");

        jLabel20.setIcon(new javax.swing.ImageIcon(getClass().getResource("/biometrics_exam/pau_icon-25x25.png"))); // NOI18N

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel20)
                .addGap(176, 176, 176)
                .addComponent(jLabel19)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel20, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel19))
                .addContainerGap(35, Short.MAX_VALUE))
        );

        jTabbedPane1.setBackground(new java.awt.Color(255, 255, 255));
        jTabbedPane1.setTabPlacement(javax.swing.JTabbedPane.LEFT);

        jPanel4.setBackground(new java.awt.Color(255, 255, 255));

        current_date.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        current_time.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        current_time.setForeground(new java.awt.Color(45, 59, 111));
        current_time.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));
        current_time.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                current_timeActionPerformed(evt);
            }
        });

        jLabel35.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel35.setForeground(new java.awt.Color(45, 59, 111));
        jLabel35.setText("Current Shift Date");

        jLabel36.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel36.setForeground(new java.awt.Color(45, 59, 111));
        jLabel36.setText("Current Shift Time");

        staff_combo.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        staff_combo.setForeground(new java.awt.Color(45, 59, 111));
        staff_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Select Staff Member" }));
        staff_combo.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        jLabel37.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel37.setForeground(new java.awt.Color(45, 59, 111));
        jLabel37.setText("Swap With Staff Member");

        jLabel38.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel38.setForeground(new java.awt.Color(45, 59, 111));
        jLabel38.setText("Reason For Swap");

        reason_area.setColumns(20);
        reason_area.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        reason_area.setForeground(new java.awt.Color(204, 204, 204));
        reason_area.setLineWrap(true);
        reason_area.setRows(5);
        reason_area.setText("Enter reason for shift swap...");
        reason_area.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));
        jScrollPane4.setViewportView(reason_area);

        submit_button.setBackground(new java.awt.Color(45, 59, 111));
        submit_button.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        submit_button.setForeground(new java.awt.Color(255, 255, 255));
        submit_button.setText("Submit Swap Request");
        submit_button.setBorder(javax.swing.BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.RAISED));
        submit_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                submit_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap(36, Short.MAX_VALUE)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel37)
                    .addComponent(current_date, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel35)
                    .addComponent(staff_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabel38)
                    .addComponent(jLabel36)
                    .addComponent(current_time, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 213, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(23, 23, 23))
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(155, 155, 155)
                .addComponent(submit_button, javax.swing.GroupLayout.PREFERRED_SIZE, 173, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(21, 21, 21)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel35)
                    .addComponent(jLabel36))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(current_date, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(current_time, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel38)
                    .addComponent(jLabel37))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(staff_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(submit_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(36, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("Swap Shifts", jPanel4);

        javax.swing.GroupLayout swap_frameLayout = new javax.swing.GroupLayout(swap_frame.getContentPane());
        swap_frame.getContentPane().setLayout(swap_frameLayout);
        swap_frameLayout.setHorizontalGroup(
            swap_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(swap_frameLayout.createSequentialGroup()
                .addGroup(swap_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jTabbedPane1)
                    .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        swap_frameLayout.setVerticalGroup(
            swap_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, swap_frameLayout.createSequentialGroup()
                .addComponent(jPanel11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 271, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        sign_panel.setBackground(new java.awt.Color(255, 255, 255));
        sign_panel.setForeground(new java.awt.Color(255, 255, 255));

        jPanel7.setBackground(new java.awt.Color(45, 59, 111));

        jLabel10.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel10.setForeground(new java.awt.Color(255, 255, 255));
        jLabel10.setText("PAN-ANTLANTIC UNIVERSITY");

        jLabel11.setIcon(new javax.swing.ImageIcon(getClass().getResource("/biometrics_exam/pau_icon-25x25.png"))); // NOI18N

        jLabel12.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel12.setForeground(new java.awt.Color(255, 255, 255));
        jLabel12.setText("Attendance Management System");

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel11)
                .addGap(72, 72, 72)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel10)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addGap(55, 55, 55)
                        .addComponent(jLabel12)))
                .addContainerGap(112, Short.MAX_VALUE))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel11, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(jLabel10)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel12)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        fingerprint_label.setForeground(new java.awt.Color(153, 153, 153));
        fingerprint_label.setText("      place finger here");
        fingerprint_label.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel9.setBackground(new java.awt.Color(45, 59, 111));
        jLabel9.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel9.setForeground(new java.awt.Color(45, 59, 111));
        jLabel9.setText("Please place your finger on the scanner");

        cancel_button.setBackground(new java.awt.Color(153, 153, 153));
        cancel_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        cancel_button.setForeground(new java.awt.Color(255, 255, 255));
        cancel_button.setText("Cancel");
        cancel_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancel_buttonActionPerformed(evt);
            }
        });

        jLabel21.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        jLabel21.setForeground(new java.awt.Color(255, 51, 0));
        jLabel21.setText("\"\"");

        javax.swing.GroupLayout sign_panelLayout = new javax.swing.GroupLayout(sign_panel);
        sign_panel.setLayout(sign_panelLayout);
        sign_panelLayout.setHorizontalGroup(
            sign_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(sign_panelLayout.createSequentialGroup()
                .addGroup(sign_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(sign_panelLayout.createSequentialGroup()
                        .addGap(215, 215, 215)
                        .addComponent(cancel_button, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(sign_panelLayout.createSequentialGroup()
                        .addGap(158, 158, 158)
                        .addComponent(jLabel9))
                    .addGroup(sign_panelLayout.createSequentialGroup()
                        .addGap(205, 205, 205)
                        .addComponent(fingerprint_label, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(sign_panelLayout.createSequentialGroup()
                        .addGap(176, 176, 176)
                        .addComponent(jLabel21)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        sign_panelLayout.setVerticalGroup(
            sign_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sign_panelLayout.createSequentialGroup()
                .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jLabel21)
                .addGap(19, 19, 19)
                .addComponent(fingerprint_label, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(46, 46, 46)
                .addComponent(jLabel9)
                .addGap(18, 18, 18)
                .addComponent(cancel_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(51, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout sign_frameLayout = new javax.swing.GroupLayout(sign_frame.getContentPane());
        sign_frame.getContentPane().setLayout(sign_frameLayout);
        sign_frameLayout.setHorizontalGroup(
            sign_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sign_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(sign_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        sign_frameLayout.setVerticalGroup(
            sign_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sign_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(sign_panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jPanel12.setBackground(new java.awt.Color(255, 255, 255));
        jPanel12.setForeground(new java.awt.Color(255, 255, 255));

        jLabel22.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel22.setForeground(new java.awt.Color(45, 59, 111));
        jLabel22.setText("Type Password");

        change_password_button.setBackground(new java.awt.Color(51, 204, 0));
        change_password_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        change_password_button.setForeground(new java.awt.Color(255, 255, 255));
        change_password_button.setText("Confirm Password");
        change_password_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                change_password_buttonActionPerformed(evt);
            }
        });

        jPanel13.setBackground(new java.awt.Color(45, 59, 111));

        jLabel23.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel23.setForeground(new java.awt.Color(255, 255, 255));
        jLabel23.setText("CHANGE PASSWORD");

        jLabel24.setIcon(new javax.swing.ImageIcon(getClass().getResource("/biometrics_exam/pau_icon-25x25.png"))); // NOI18N

        jLabel25.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel25.setForeground(new java.awt.Color(255, 255, 255));
        jLabel25.setText("Attendance Management System");

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel24)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel13Layout.createSequentialGroup()
                        .addGap(76, 76, 76)
                        .addComponent(jLabel25))
                    .addGroup(jPanel13Layout.createSequentialGroup()
                        .addGap(59, 59, 59)
                        .addComponent(jLabel23)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel24, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel13Layout.createSequentialGroup()
                        .addComponent(jLabel23)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel25)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        password_1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        password_1.setForeground(new java.awt.Color(45, 59, 111));
        password_1.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        password_1.setText("password");
        password_1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        show_password1.setBackground(new java.awt.Color(255, 255, 255));
        show_password1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        show_password1.setForeground(new java.awt.Color(45, 59, 111));
        show_password1.setText("show password");
        show_password1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                show_password1ActionPerformed(evt);
            }
        });

        show_password2.setBackground(new java.awt.Color(255, 255, 255));
        show_password2.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        show_password2.setForeground(new java.awt.Color(45, 59, 111));
        show_password2.setText("show password");
        show_password2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                show_password2ActionPerformed(evt);
            }
        });

        password_2.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        password_2.setForeground(new java.awt.Color(45, 59, 111));
        password_2.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        password_2.setText("password");
        password_2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        jLabel26.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel26.setForeground(new java.awt.Color(45, 59, 111));
        jLabel26.setText("Retype Password");

        device_status_label.setBackground(new java.awt.Color(45, 59, 111));
        device_status_label.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        device_status_label.setForeground(new java.awt.Color(45, 59, 111));

        finger_instruction_label.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        finger_instruction_label.setForeground(new java.awt.Color(153, 153, 153));

        cancel_change_button.setBackground(new java.awt.Color(204, 204, 204));
        cancel_change_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        cancel_change_button.setForeground(new java.awt.Color(255, 255, 255));
        cancel_change_button.setText("Cancel");
        cancel_change_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancel_change_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addGap(50, 50, 50)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel12Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(finger_instruction_label))
                    .addGroup(jPanel12Layout.createSequentialGroup()
                        .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel26)
                            .addComponent(password_2, javax.swing.GroupLayout.PREFERRED_SIZE, 368, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(show_password2)
                            .addComponent(jLabel22)
                            .addComponent(show_password1)
                            .addComponent(password_1, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(device_status_label)
                        .addGap(430, 430, 430))
                    .addGroup(jPanel12Layout.createSequentialGroup()
                        .addComponent(change_password_button, javax.swing.GroupLayout.PREFERRED_SIZE, 214, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(cancel_change_button, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addComponent(jPanel13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel12Layout.createSequentialGroup()
                        .addGap(46, 46, 46)
                        .addComponent(jLabel22)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(password_1, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(show_password1)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel26)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(password_2, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(44, 44, 44)
                        .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(change_password_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cancel_change_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 52, Short.MAX_VALUE)
                        .addComponent(finger_instruction_label)
                        .addContainerGap(12, Short.MAX_VALUE))
                    .addGroup(jPanel12Layout.createSequentialGroup()
                        .addGap(40, 40, 40)
                        .addComponent(device_status_label)
                        .addGap(174, 174, 174)
                        .addComponent(show_password2)
                        .addGap(101, 101, 101))))
        );

        javax.swing.GroupLayout password_frameLayout = new javax.swing.GroupLayout(password_frame.getContentPane());
        password_frame.getContentPane().setLayout(password_frameLayout);
        password_frameLayout.setHorizontalGroup(
            password_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(password_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel12, javax.swing.GroupLayout.PREFERRED_SIZE, 494, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        password_frameLayout.setVerticalGroup(
            password_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(password_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel9.setBackground(new java.awt.Color(255, 255, 255));
        jPanel9.setForeground(new java.awt.Color(255, 255, 255));

        jPanel10.setBackground(new java.awt.Color(45, 59, 111));

        jLabel13.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel13.setForeground(new java.awt.Color(255, 255, 255));
        jLabel13.setText("Welcome back, Username");

        jLabel14.setIcon(new javax.swing.ImageIcon(getClass().getResource("/biometrics_exam/pau_icon-25x25.png"))); // NOI18N

        jLabel15.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel15.setForeground(new java.awt.Color(255, 255, 255));
        jLabel15.setText("Unit Label");

        jSeparator4.setOrientation(javax.swing.SwingConstants.VERTICAL);

        jLabel16.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel16.setForeground(new java.awt.Color(255, 255, 255));
        jLabel16.setText("Current Date");

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel14)
                .addGap(129, 129, 129)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel13)
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addGap(64, 64, 64)
                        .addComponent(jLabel15)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jSeparator4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel16)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel14, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addComponent(jLabel13)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel15)
                            .addComponent(jSeparator4, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel16))))
                .addContainerGap(17, Short.MAX_VALUE))
        );

        jTabbedPane2.setBackground(new java.awt.Color(255, 255, 255));
        jTabbedPane2.setForeground(new java.awt.Color(45, 59, 111));
        jTabbedPane2.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N

        jPanel6.setBackground(new java.awt.Color(255, 255, 255));

        jScrollPane11.setViewportView(status_list);

        jScrollPane14.setViewportView(upcoming_list);

        title_label.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        title_label.setForeground(new java.awt.Color(45, 59, 111));
        title_label.setText("Upcoming Shifts");

        jScrollPane15.setViewportView(request_list);

        jLabel18.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        jLabel18.setForeground(new java.awt.Color(45, 59, 111));
        jLabel18.setText("Swap Requests");

        swap_button.setBackground(new java.awt.Color(0, 102, 153));
        swap_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        swap_button.setForeground(new java.awt.Color(255, 255, 255));
        swap_button.setText("Request Swap");
        swap_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                swap_buttonActionPerformed(evt);
            }
        });

        spots_button.setBackground(new java.awt.Color(47, 184, 2));
        spots_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        spots_button.setForeground(new java.awt.Color(255, 255, 255));
        spots_button.setText("View Available Spots");
        spots_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                spots_buttonActionPerformed(evt);
            }
        });

        sign_button.setBackground(new java.awt.Color(45, 59, 111));
        sign_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        sign_button.setForeground(new java.awt.Color(255, 255, 255));
        sign_button.setText("Sign In");
        sign_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sign_buttonActionPerformed(evt);
            }
        });

        signout_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        signout_button.setForeground(new java.awt.Color(45, 59, 111));
        signout_button.setText("Sign Out");
        signout_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                signout_buttonActionPerformed(evt);
            }
        });

        logout_button.setBackground(new java.awt.Color(255, 0, 0));
        logout_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        logout_button.setForeground(new java.awt.Color(255, 255, 255));
        logout_button.setText("Log Out");
        logout_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logout_buttonActionPerformed(evt);
            }
        });

        all_shifts_button.setBackground(new java.awt.Color(45, 59, 111));
        all_shifts_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        all_shifts_button.setForeground(new java.awt.Color(255, 255, 255));
        all_shifts_button.setText("All Shifts");
        all_shifts_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                all_shifts_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(17, 17, 17)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(sign_button)
                        .addGap(18, 18, 18)
                        .addComponent(signout_button)
                        .addGap(18, 18, 18)
                        .addComponent(logout_button))
                    .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jScrollPane11)
                        .addComponent(jLabel18)
                        .addGroup(jPanel6Layout.createSequentialGroup()
                            .addComponent(swap_button)
                            .addGap(26, 26, 26)
                            .addComponent(spots_button)
                            .addGap(18, 18, 18)
                            .addComponent(all_shifts_button))
                        .addComponent(title_label)
                        .addComponent(jScrollPane15, javax.swing.GroupLayout.DEFAULT_SIZE, 623, Short.MAX_VALUE)
                        .addComponent(jScrollPane14)))
                .addContainerGap(15, Short.MAX_VALUE))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(title_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(31, 31, 31)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(swap_button)
                    .addComponent(spots_button)
                    .addComponent(all_shifts_button))
                .addGap(18, 18, 18)
                .addComponent(jLabel18)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane15, javax.swing.GroupLayout.PREFERRED_SIZE, 87, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jScrollPane11, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sign_button)
                    .addComponent(logout_button)
                    .addComponent(signout_button))
                .addContainerGap(79, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("Dashboard", jPanel6);

        jPanel5.setBackground(new java.awt.Color(255, 255, 255));

        change_button.setBackground(new java.awt.Color(45, 59, 111));
        change_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        change_button.setForeground(new java.awt.Color(255, 255, 255));
        change_button.setText("Change Password");
        change_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                change_buttonActionPerformed(evt);
            }
        });

        username_field.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        username_field.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        dob_chooser.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));
        dob_chooser.setForeground(new java.awt.Color(45, 59, 111));

        gender_combo.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        gender_combo.setForeground(new java.awt.Color(45, 59, 111));
        gender_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Male", "Female" }));
        gender_combo.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 59, 111)));

        email_field.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        email_field.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        phone_field.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        phone_field.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        unit_combo.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        unit_combo.setForeground(new java.awt.Color(45, 59, 111));
        unit_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Security", "Maintenance", "Cafeteria", "Facility", "Horticulture", "None" }));
        unit_combo.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));
        unit_combo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unit_comboActionPerformed(evt);
            }
        });

        role_combo.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        role_combo.setForeground(new java.awt.Color(45, 59, 111));
        role_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Staff", "Supervisor" }));
        role_combo.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        jLabel6.setFont(new java.awt.Font("Segoe UI", 1, 22)); // NOI18N
        jLabel6.setForeground(new java.awt.Color(45, 59, 111));
        jLabel6.setText("User Data");

        jPanel8.setBackground(new java.awt.Color(255, 255, 255));

        image_label.setBorder(javax.swing.BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.RAISED));

        browse_image_button.setBackground(new java.awt.Color(153, 153, 153));
        browse_image_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        browse_image_button.setForeground(new java.awt.Color(255, 255, 255));
        browse_image_button.setText("Browse");
        browse_image_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browse_image_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(image_label, javax.swing.GroupLayout.DEFAULT_SIZE, 161, Short.MAX_VALUE)
                    .addComponent(browse_image_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addComponent(image_label, javax.swing.GroupLayout.PREFERRED_SIZE, 137, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(24, 24, 24)
                .addComponent(browse_image_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        edit_button.setBackground(new java.awt.Color(204, 0, 0));
        edit_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        edit_button.setForeground(new java.awt.Color(255, 255, 255));
        edit_button.setText("Edit Data");
        edit_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                edit_buttonActionPerformed(evt);
            }
        });

        logout_button2.setBackground(new java.awt.Color(255, 0, 0));
        logout_button2.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        logout_button2.setForeground(new java.awt.Color(255, 255, 255));
        logout_button2.setText("Log Out");
        logout_button2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logout_button2ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGap(23, 23, 23)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel6)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(username_field, javax.swing.GroupLayout.PREFERRED_SIZE, 426, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addComponent(dob_chooser, javax.swing.GroupLayout.PREFERRED_SIZE, 217, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(gender_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(email_field, javax.swing.GroupLayout.PREFERRED_SIZE, 426, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(phone_field, javax.swing.GroupLayout.PREFERRED_SIZE, 426, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addComponent(unit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(role_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 204, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(18, 18, 18)
                        .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(edit_button)
                            .addComponent(logout_button2))
                        .addGap(18, 18, 18)
                        .addComponent(change_button)))
                .addContainerGap(15, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGap(23, 23, 23)
                .addComponent(jLabel6)
                .addGap(18, 18, 18)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(username_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(dob_chooser, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(gender_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(email_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(phone_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(role_combo)
                            .addComponent(unit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(47, 47, 47)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(edit_button)
                    .addComponent(change_button))
                .addGap(39, 39, 39)
                .addComponent(logout_button2)
                .addGap(175, 175, 175))
        );

        jTabbedPane2.addTab("Profile", jPanel5);

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane2)
                .addContainerGap())
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTabbedPane2)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void swap_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_swap_buttonActionPerformed
        swap_frame.setVisible(true);
        swap_frame.pack();
    }//GEN-LAST:event_swap_buttonActionPerformed

    private void spots_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_spots_buttonActionPerformed
       showAvailableSpots(); 
    }//GEN-LAST:event_spots_buttonActionPerformed

    private void submit_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_submit_buttonActionPerformed
        submitSwapRequest();        
    }//GEN-LAST:event_submit_buttonActionPerformed

    private void current_timeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_current_timeActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_current_timeActionPerformed

    private void sign_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sign_buttonActionPerformed
        isSigningIn = true;
        sign_frame.setVisible(true);
        sign_frame.pack();
        startBiometricCapture();
    }//GEN-LAST:event_sign_buttonActionPerformed

    private void signout_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_signout_buttonActionPerformed
        isSigningIn = false;
        sign_frame.setVisible(true);
        sign_frame.pack();
        startBiometricCapture();
    }//GEN-LAST:event_signout_buttonActionPerformed

    private void logout_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logout_buttonActionPerformed
        new login_frame().setVisible(true);
        this.setVisible(false);
    }//GEN-LAST:event_logout_buttonActionPerformed

    private void cancel_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancel_buttonActionPerformed
        stopBiometricCapture();
        sign_frame.setVisible(false);
    }//GEN-LAST:event_cancel_buttonActionPerformed

    private void all_shifts_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_all_shifts_buttonActionPerformed
        showAllShiftsDialog();
    }//GEN-LAST:event_all_shifts_buttonActionPerformed

    private void change_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_change_buttonActionPerformed
        int option = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to change your password?",
            "Change Password", JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            // Ask for current password with 3 attempts
            int attempts = 0;
            boolean verified = false;

            while (attempts < 3 && !verified) {
                String currentPassword = JOptionPane.showInputDialog(this,
                    "Enter your current password (Attempt " + (attempts + 1) + " of 3):",
                    "Current Password", JOptionPane.QUESTION_MESSAGE);

                if (currentPassword == null) {
                    // User cancelled
                    return;
                }

                if (verifyCurrentPassword(currentPassword)) {
                    verified = true;
                    password_frame.setVisible(true);
                    password_frame.pack();
                    password_1.setText("");
                    password_2.setText("");
                } else {
                    attempts++;
                    if (attempts < 3) {
                        JOptionPane.showMessageDialog(this,
                            "Incorrect password. Please try again.",
                            "Wrong Password", JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this,
                            "Too many incorrect attempts. Password change cancelled.",
                            "Access Denied", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
    }//GEN-LAST:event_change_buttonActionPerformed

    private void unit_comboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unit_comboActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_unit_comboActionPerformed

    private void browse_image_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browse_image_buttonActionPerformed
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
            "Image Files", "jpg", "jpeg", "png", "gif", "bmp");
        jFileChooser1.setFileFilter(filter);

        int result = jFileChooser1.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedImageFile = jFileChooser1.getSelectedFile();

            try {
                if (selectedImageFile.length() > 5 * 1024 * 1024) {
                    JOptionPane.showMessageDialog(this,
                        "Image file too large. Please select an image smaller than 5MB.",
                        "File Too Large", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                BufferedImage image = ImageIO.read(selectedImageFile);
                if (image != null) {
                    Image scaledImage = image.getScaledInstance(150, 150, Image.SCALE_SMOOTH);
                    image_label.setIcon(new ImageIcon(scaledImage));

                    int option = JOptionPane.showConfirmDialog(this,
                        "Do you want to use this image as your profile picture?",
                        "Confirm Image Change", JOptionPane.YES_NO_OPTION);

                    if (option == JOptionPane.YES_OPTION) {
                        // Convert image to byte array
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(image, "jpg", baos);
                        currentPhoto = baos.toByteArray();
                    } else {
                        // Restore previous image
                        loadProfileImage(currentPhoto);
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Invalid image file. Please select a valid image.",
                        "Invalid Image", JOptionPane.ERROR_MESSAGE);
                }

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Error loading image: " + e.getMessage(),
                    "Image Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }//GEN-LAST:event_browse_image_buttonActionPerformed

    private void edit_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_edit_buttonActionPerformed
        if (!isEditing) {
            // Start editing
            isEditing = true;
            edit_button.setText("Confirm");
            setFieldsEditable(true);
        } else {
            if (hasChanges()) {
                int option = JOptionPane.showConfirmDialog(this,
                    "You have made changes to your profile. Do you want to save these changes?",
                    "Confirm Changes", JOptionPane.YES_NO_OPTION);

                if (option == JOptionPane.YES_OPTION) {
                    saveProfileChanges();
                    isEditing = false;
                    edit_button.setText("Edit");
                    setFieldsEditable(false);
                }
            } else {
                int option = JOptionPane.showConfirmDialog(this,
                    "No changes were made. Do you want to stop editing?",
                    "No Changes", JOptionPane.YES_NO_OPTION);

                if (option == JOptionPane.YES_OPTION) {
                    isEditing = false;
                    edit_button.setText("Edit");
                    setFieldsEditable(false);
                    // Reload original data
                    loadProfileData();
                }
            }
        }
    }//GEN-LAST:event_edit_buttonActionPerformed

    private void logout_button2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logout_button2ActionPerformed
        new login_frame().setVisible(true);
        this.setVisible(false);     
    }//GEN-LAST:event_logout_button2ActionPerformed

    private void change_password_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_change_password_buttonActionPerformed
        String password1 = new String(password_1.getPassword());
        String password2 = new String(password_2.getPassword());

        if (password1.isEmpty() || password2.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please fill in both password fields.",
                "Empty Fields", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!password1.equals(password2)) {
            JOptionPane.showMessageDialog(this,
                "Passwords do not match. Please try again.",
                "Password Mismatch", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (password1.length() < 6) {
            JOptionPane.showMessageDialog(this,
                "Password must be at least 6 characters long.",
                "Password Too Short", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (changePassword(password1)) {
            JOptionPane.showMessageDialog(this,
                "Password changed successfully!",
                "Success", JOptionPane.INFORMATION_MESSAGE);
            password_frame.setVisible(false);
            password_1.setText("");
            password_2.setText("");
        } else {
            JOptionPane.showMessageDialog(this,
                "Failed to change password. Please try again.",
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_change_password_buttonActionPerformed

    private void show_password1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_show_password1ActionPerformed
        if (show_password1.isSelected()) {
            password_1.setEchoChar((char) 0);
        } else {
            password_1.setEchoChar('*');
        }
    }//GEN-LAST:event_show_password1ActionPerformed

    private void show_password2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_show_password2ActionPerformed
        if (show_password2.isSelected()) {
            password_2.setEchoChar((char) 0);
        } else {
            password_2.setEchoChar('*');
        }
    }//GEN-LAST:event_show_password2ActionPerformed

    private void cancel_change_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancel_change_buttonActionPerformed
        password_frame.setVisible(false);
        password_1.setText("");
        password_2.setText("");
    }//GEN-LAST:event_cancel_change_buttonActionPerformed

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new staff_frame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton all_shifts_button;
    private javax.swing.JButton browse_image_button;
    private javax.swing.JButton cancel_button;
    private javax.swing.JButton cancel_change_button;
    private javax.swing.JButton change_button;
    private javax.swing.JButton change_password_button;
    private com.toedter.calendar.JDateChooser current_date;
    private javax.swing.JComboBox<Object> current_time;
    private javax.swing.JLabel device_status_label;
    private com.toedter.calendar.JDateChooser dob_chooser;
    private javax.swing.JButton edit_button;
    private javax.swing.JTextField email_field;
    private javax.swing.JLabel finger_instruction_label;
    private javax.swing.JLabel fingerprint_label;
    private javax.swing.JComboBox<String> gender_combo;
    private javax.swing.JLabel image_label;
    private javax.swing.JFileChooser jFileChooser1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel35;
    private javax.swing.JLabel jLabel36;
    private javax.swing.JLabel jLabel37;
    private javax.swing.JLabel jLabel38;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane11;
    private javax.swing.JScrollPane jScrollPane14;
    private javax.swing.JScrollPane jScrollPane15;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JButton logout_button;
    private javax.swing.JButton logout_button2;
    private javax.swing.JPasswordField password_1;
    private javax.swing.JPasswordField password_2;
    private javax.swing.JFrame password_frame;
    private javax.swing.JTextField phone_field;
    private javax.swing.JTextArea reason_area;
    private javax.swing.JList<String> request_list;
    private javax.swing.JComboBox<String> role_combo;
    private javax.swing.JCheckBox show_password1;
    private javax.swing.JCheckBox show_password2;
    private javax.swing.JButton sign_button;
    private javax.swing.JFrame sign_frame;
    private javax.swing.JPanel sign_panel;
    private javax.swing.JButton signout_button;
    private javax.swing.JButton spots_button;
    private javax.swing.JComboBox<Object> staff_combo;
    private javax.swing.JList<String> status_list;
    private javax.swing.JButton submit_button;
    private javax.swing.JButton swap_button;
    private javax.swing.JFrame swap_frame;
    private javax.swing.JLabel title_label;
    private javax.swing.JComboBox<String> unit_combo;
    private javax.swing.JList<String> upcoming_list;
    private javax.swing.JTextField username_field;
    // End of variables declaration//GEN-END:variables
}

