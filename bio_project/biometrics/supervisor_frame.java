package biometrics_exam;

import java.awt.Color;
import java.sql.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.DefaultListModel;
import java.io.*;
import java.util.*;
import java.util.List;
import java.text.SimpleDateFormat;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import java.awt.Image;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import javax.imageio.ImageIO;

public class supervisor_frame extends javax.swing.JFrame {

    private int currentuserId;
    private Connection con;
    private PreparedStatement ps;
    private ResultSet rs;
    private String supervisorUnit;
    private File selectedRosterFile;
    private DefaultListModel<String> recentActivityModel;
    private DefaultListModel<String> notificationModel;
    private static final String SENDER_EMAIL = "ezraagun@gmail.com";
    private static final String SENDER_PASSWORD = "nwhotxkkqbsbwvzy";
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
    private DefaultTableModel swapTableModel;
    private int selectedRequestId = -1;

    public supervisor_frame(int userId) {
        initComponents();
        this.currentuserId = userId;

        recentActivityModel = new DefaultListModel<>();
        notificationModel = new DefaultListModel<>();
        recent_activity_list.setModel(recentActivityModel);
        notification_list.setModel(notificationModel);

        initializeFingerprintSystem();
        loadUserData();
        initializeProfile();
        initializeSwapTable();

        if (userId != -1) {
            initializeSupervisorData();
            setupEventHandlers();
        }
    }
 
    private int getCurrentLoggedInUserId() {
        return this.currentuserId;
    }

    public supervisor_frame() {
        this(-1);
    }

    private void loadUserData() {
        if (currentuserId == -1) {
            // Default values for when no user is logged in
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
            ps.setInt(1, currentuserId);
            rs = ps.executeQuery();

            if (rs.next()) {
                String fullName = rs.getString("full_name");
                String unit = rs.getString("unit");

                // Extract first name from full name
                String firstName = fullName.split(" ")[0];

                jLabel13.setText("Welcome back, " + firstName);
                jLabel15.setText(unit);
            } else {
                jLabel13.setText("Welcome back, User");
                jLabel15.setText("No Unit");
            }

            jLabel16.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));

        } catch (Exception e) {
            e.printStackTrace();
            jLabel13.setText("Welcome back, User");
            jLabel15.setText("No Unit");
            jLabel16.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
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

    private void setupEventHandlers() {
        filter_combo.addActionListener(e -> {
            String selectedFilter = (String) filter_combo.getSelectedItem();
            if ("Filter By".equals(selectedFilter)) {
                loadStaffAttendanceData();
            } else if ("Date".equals(selectedFilter)) {
                String dateInput = JOptionPane.showInputDialog(this,
                        "Enter date (YYYY-MM-DD):", "Filter by Date", JOptionPane.QUESTION_MESSAGE);
                if (dateInput != null && !dateInput.trim().isEmpty()) {
                    filterStaffTable("Date", dateInput.trim());
                }
            } else if ("Staff Id".equals(selectedFilter)) {
                String staffIdInput = JOptionPane.showInputDialog(this,
                        "Enter Staff ID:", "Filter by Staff ID", JOptionPane.QUESTION_MESSAGE);
                if (staffIdInput != null && !staffIdInput.trim().isEmpty()) {
                    filterStaffTable("Staff Id", staffIdInput.trim());
                }
            }
        });

        filter_combo1.addActionListener(e -> {
            String selectedFilter = (String) filter_combo1.getSelectedItem();
            if ("Filter By".equals(selectedFilter)) {
                loadSystemLogs(null, null);
            } else if ("Date".equals(selectedFilter)) {
                String dateInput = JOptionPane.showInputDialog(this,
                        "Enter date (YYYY-MM-DD):", "Filter Logs by Date", JOptionPane.QUESTION_MESSAGE);
                if (dateInput != null && !dateInput.trim().isEmpty()) {
                    loadSystemLogs("Date", dateInput.trim());
                }
            } else if ("Staff Id".equals(selectedFilter)) {
                String staffIdInput = JOptionPane.showInputDialog(this,
                        "Enter Staff ID:", "Filter Logs by Staff ID", JOptionPane.QUESTION_MESSAGE);
                if (staffIdInput != null && !staffIdInput.trim().isEmpty()) {
                    loadSystemLogs("Staff Id", staffIdInput.trim());
                }
            }
        });

        reminder_button.addActionListener(e -> sendReminderToSelectedStaff());

        logs_button.addActionListener(e -> loadSystemLogs(null, null));

        export_button.addActionListener(e -> exportAttendanceToCSV());

        jTabbedPane2.addChangeListener(e -> {
            if (jTabbedPane2.getSelectedIndex() == 1) { 
                loadStaffAttendanceData();
                updatePresentStaffCount();
            }
        });
    }

    private void refreshDashboardData() {
        loadStaffAttendanceData();
        updatePresentStaffCount();
        loadSystemLogs(null, null);
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
                    updateBiometricStatus("Fingerprint captured. Verifying against database...");
                    verifyFingerprintForAttendance();
                } else {
                    updateBiometricStatus("Failed to extract fingerprint features. Please try again.");
                }

            } catch (DPFPImageQualityException ex) {
                updateBiometricStatus("Poor fingerprint quality. Please clean your finger and try again.");
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

        int loggedInUserId = getCurrentLoggedInUserId();

        if (loggedInUserId == -1) {
            updateBiometricStatus("No user logged in. Please log in first.");
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            updateBiometricStatus("Verifying fingerprint for logged-in user...");

            ps = con.prepareStatement(
                    "SELECT bd.user_id, bd.fingerprint_template, bd.finger_position, bd.quality_score, "
                    + "u.role, u.is_active, u.full_name, u.unit "
                    + "FROM biometric_data bd "
                    + "JOIN users u ON bd.user_id = u.user_id "
                    + "WHERE bd.user_id = ? AND u.is_active = TRUE "
                    + "ORDER BY bd.quality_score DESC"
            );
            ps.setInt(1, loggedInUserId);

            rs = ps.executeQuery();

            if (!rs.isBeforeFirst()) {
                updateBiometricStatus("No fingerprint enrolled for this user. Please enroll fingerprint first.");
                JOptionPane.showMessageDialog(this,
                        "No fingerprint found for your account. Please contact administrator to enroll your fingerprint.",
                        "Fingerprint Not Found", JOptionPane.WARNING_MESSAGE);
                return;
            }

            DPFPVerification verifier = DPFPGlobal.getVerificationFactory().createVerification();
            boolean matchFound = false;
            int templatesChecked = 0;
            String userName = "";
            String userUnit = "";

            while (rs.next() && !matchFound) {
                templatesChecked++;

                try {
                    byte[] templateBytes = rs.getBytes("fingerprint_template");
                    userName = rs.getString("full_name");
                    userUnit = rs.getString("unit");

                    if (templateBytes != null && templateBytes.length > 0) {

                        DPFPTemplate dbTemplate = DPFPGlobal.getTemplateFactory().createTemplate();
                        dbTemplate.deserialize(templateBytes);

                        DPFPVerificationResult result = verifier.verify(featureSet, dbTemplate);

                        if (result.isVerified()) {
                            matchFound = true;
                            String fingerPosition = rs.getString("finger_position");

                            System.out.println("Fingerprint match confirmed for logged-in user: " + userName
                                    + " (ID: " + loggedInUserId + ", " + fingerPosition + ")");

                            SwingUtilities.invokeLater(() -> {
                                stopBiometricCapture();
                                updateBiometricStatus("Fingerprint verified! Processing attendance...");

                                if (isSigningIn) {
                                    processSignIn(loggedInUserId);
                                } else {
                                    processSignOut(loggedInUserId);
                                }
                            });
                            return;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing template for user ID " + loggedInUserId + ": " + e.getMessage());
                    continue; 
                }
            }

            if (!matchFound) {
                updateBiometricStatus("Fingerprint does not match " + userName + ". Access denied.");
                JOptionPane.showMessageDialog(this,
                        "Fingerprint verification failed. The scanned fingerprint does not match " + userName + "'s enrolled fingerprints.\n"
                        + "Please try again or contact administrator if this is incorrect.",
                        "Fingerprint Mismatch", JOptionPane.ERROR_MESSAGE);

                logSystemAction(loggedInUserId, "BIOMETRIC_VERIFICATION_FAILED",
                        "Fingerprint verification failed for user " + userName + ". " + templatesChecked + " templates checked.");
            }

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

            ps = con.prepareStatement("SELECT full_name, unit, is_active, email FROM users WHERE user_id = ?");
            ps.setInt(1, userId);
            rs = ps.executeQuery();

            if (!rs.next()) {
                updateBiometricStatus("User not found in system.");
                sign_frame.setVisible(false);
                return;
            }

            if (!rs.getBoolean("is_active")) {
                updateBiometricStatus("User account is inactive.");
                sign_frame.setVisible(false);
                return;
            }

            String userName = rs.getString("full_name");
            String userUnit = rs.getString("unit");
            String userEmail = rs.getString("email");
            rs.close();
            ps.close();

            // Get user's shift information for today
            ps = con.prepareStatement(
                    "SELECT ss.shift_id, s.start_time, s.end_time, s.shift_name, s.unit as shift_unit "
                    + "FROM staff_shifts ss "
                    + "JOIN shifts s ON ss.shift_id = s.shift_id "
                    + "WHERE ss.user_id = ? AND ss.shift_date = ? AND ss.is_active = TRUE"
            );
            ps.setInt(1, userId);
            ps.setDate(2, java.sql.Date.valueOf(currentDate));
            rs = ps.executeQuery();

            if (!rs.next()) {
                PreparedStatement debugPs = con.prepareStatement(
                        "SELECT ss.shift_date, s.shift_name, ss.is_active "
                        + "FROM staff_shifts ss "
                        + "JOIN shifts s ON ss.shift_id = s.shift_id "
                        + "WHERE ss.user_id = ? "
                        + "ORDER BY ss.shift_date DESC LIMIT 5"
                );
                debugPs.setInt(1, userId);
                ResultSet debugRs = debugPs.executeQuery();

                System.out.println("Debug - Recent shifts for user " + userId + " (" + userName + "):");
                while (debugRs.next()) {
                    System.out.println("  Date: " + debugRs.getDate("shift_date")
                            + ", Shift: " + debugRs.getString("shift_name")
                            + ", Active: " + debugRs.getBoolean("is_active"));
                }
                debugRs.close();
                debugPs.close();

                updateBiometricStatus("No shift assigned for today.");
                JOptionPane.showMessageDialog(this,
                        userName + ", you have no shifts assigned for today (" + currentDate + ").\n"
                        + "Please contact your supervisor if this is incorrect.",
                        "Sign-in Error", JOptionPane.WARNING_MESSAGE);
                sign_frame.setVisible(false);
                return;
            }

            int shiftId = rs.getInt("shift_id");
            LocalTime shiftStartTime = rs.getTime("start_time").toLocalTime();
            LocalTime shiftEndTime = rs.getTime("end_time").toLocalTime();
            String shiftName = rs.getString("shift_name");

            System.out.println("Debug - Found shift: " + shiftName + " for " + userName
                    + " (" + userUnit + ") from " + shiftStartTime + " to " + shiftEndTime);

            rs.close();
            ps.close();

            // Check if shift has already ended
            if (currentTime.isAfter(shiftEndTime)) {
                updateBiometricStatus("Shift has ended. Cannot sign in.");
                JOptionPane.showMessageDialog(this,
                        userName + ", your " + shiftName + " shift has already ended at " + shiftEndTime
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
                updateBiometricStatus("Already signed in today.");
                JOptionPane.showMessageDialog(this,
                        userName + ", you have already signed in today at "
                        + existingSignIn.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + ".",
                        "Sign-in Error", JOptionPane.WARNING_MESSAGE);
                sign_frame.setVisible(false);
                return;
            }

            rs.close();
            ps.close();

            LocalTime lateThreshold = shiftStartTime.plusMinutes(30);
            boolean isLate = currentTime.isAfter(lateThreshold);

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
                // Log the sign-in action
                logSystemAction(userId, "SIGN_IN",
                        "User " + userName + " signed in for " + shiftName + " shift" + (isLate ? " (LATE)" : ""));

                String message = "Sign-in successful!"
                        + "\nUser: " + userName
                        + "\nUnit: " + userUnit
                        + "\nShift: " + shiftName + " (" + shiftStartTime + " - " + shiftEndTime + ")"
                        + "\nSign-in Time: " + currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        + (isLate ? "\n\n⚠️ NOTE: You are marked as LATE (more than 30 minutes after shift start)." : "");

                updateBiometricStatus("Sign-in successful for " + userName + "!");
                JOptionPane.showMessageDialog(this, message, "Sign-in Success",
                        isLate ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);

                if (userEmail != null && !userEmail.trim().isEmpty()) {
                    String emailBody = "You have successfully signed in to PAMS.\n\n"
                            + "Details:\n"
                            + "- Date: " + currentDate.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")) + "\n"
                            + "- Time: " + currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n"
                            + "- Shift: " + shiftName + " (" + shiftStartTime + " - " + shiftEndTime + ")\n"
                            + "- Unit: " + userUnit + "\n"
                            + (isLate ? "- Status: LATE (more than 30 minutes after shift start)\n" : "- Status: On Time\n")
                            + "\nThank you for your attendance.";

                    sendAttendanceNotificationEmail(userName, userEmail, "Sign-in Confirmation", emailBody);
                }
            } else {
                updateBiometricStatus("Failed to record sign-in.");
                JOptionPane.showMessageDialog(this,
                        "Failed to record sign-in. Please try again.",
                        "Sign-in Error", JOptionPane.ERROR_MESSAGE);
            }

            sign_frame.setVisible(false);

        } catch (Exception e) {
            updateBiometricStatus("Database error during sign-in: " + e.getMessage());
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

            ps = con.prepareStatement("SELECT full_name, unit, is_active, email FROM users WHERE user_id = ?");
            ps.setInt(1, userId);
            rs = ps.executeQuery();

            if (!rs.next()) {
                updateBiometricStatus("User not found in system.");
                sign_frame.setVisible(false);
                return;
            }

            if (!rs.getBoolean("is_active")) {
                updateBiometricStatus("User account is inactive.");
                sign_frame.setVisible(false);
                return;
            }

            String userName = rs.getString("full_name");
            String userUnit = rs.getString("unit");
            String userEmail = rs.getString("email");
            rs.close();
            ps.close();

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
                updateBiometricStatus("No active sign-in found for today.");
                JOptionPane.showMessageDialog(this,
                        userName + ", you haven't signed in today or have already signed out.",
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
                        "User " + userName + " signed out from " + shiftName + " shift" + (isEarlyOut ? " (EARLY)" : ""));

                // Calculate work duration
                LocalDateTime signInDateTime = signInTime.toLocalDateTime();
                LocalDateTime signOutDateTime = LocalDateTime.now();
                long minutes = java.time.Duration.between(signInDateTime, signOutDateTime).toMinutes();
                long hours = minutes / 60;
                minutes = minutes % 60;

                String message = "Sign-out successful!"
                        + "\nUser: " + userName
                        + (isEarlyOut ? "\n⚠️ Note: You are signing out before your shift ends." : "")
                        + "\nShift: " + shiftName
                        + "\nWork Duration: " + hours + " hours, " + minutes + " minutes"
                        + "\nSign-out Time: " + currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                updateBiometricStatus("Sign-out successful for " + userName + "!");
                JOptionPane.showMessageDialog(this, message, "Sign-out Success",
                        JOptionPane.INFORMATION_MESSAGE);

                // Send email notification for sign-out
                if (userEmail != null && !userEmail.trim().isEmpty()) {
                    String emailBody = "You have successfully signed out from PAMS.\n\n"
                            + "Details:\n"
                            + "- Date: " + today.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")) + "\n"
                            + "- Sign-in Time: " + signInDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n"
                            + "- Sign-out Time: " + currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n"
                            + "- Shift: " + shiftName + "\n"
                            + "- Unit: " + userUnit + "\n"
                            + "- Work Duration: " + hours + " hours, " + minutes + " minutes\n"
                            + (isEarlyOut ? "- Status: Early sign-out (before shift end time)\n" : "- Status: Complete shift\n")
                            + "\nThank you for your service today.";

                    sendAttendanceNotificationEmail(userName, userEmail, "Sign-out Confirmation", emailBody);
                }
            } else {
                updateBiometricStatus("Failed to record sign-out.");
                JOptionPane.showMessageDialog(this,
                        "Failed to record sign-out. Please try again.",
                        "Sign-out Error", JOptionPane.ERROR_MESSAGE);
            }

            sign_frame.setVisible(false);

        } catch (Exception e) {
            updateBiometricStatus("Database error during sign-out: " + e.getMessage());
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

    private void sendAttendanceNotificationEmail(String fullName, String recipientEmail, String subject, String messageBody) {
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
            message.setSubject("PAMS Attendance - " + subject);
            message.setText(formatAttendanceNotificationBody(fullName, messageBody));

            Transport.send(message);
            emailSent = true;
            System.out.println("Attendance notification sent to: " + recipientEmail + " via STARTTLS.");

        } catch (MessagingException e) {
            System.err.println("STARTTLS failed for " + recipientEmail + ": " + e.getMessage());

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
                sslMessage.setSubject("PAMS Attendance - " + subject);
                sslMessage.setText(formatAttendanceNotificationBody(fullName, messageBody));

                Transport.send(sslMessage);
                emailSent = true;
                System.out.println("Attendance notification sent to: " + recipientEmail + " via SSL fallback.");

            } catch (MessagingException sslException) {
                System.err.println("SSL fallback failed for " + recipientEmail + ": " + sslException.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("Unexpected error sending attendance notification to " + recipientEmail + ": " + ex.getMessage());
        }

        if (!emailSent) {
            System.err.println("Failed to send attendance notification to: " + recipientEmail);
        }
    }

    private String formatAttendanceNotificationBody(String fullName, String messageBody) {
        return "Dear " + fullName + ",\n\n"
                + messageBody + "\n\n"
                + "This is an automated notification from the PAU Attendance Management System (PAMS).\n"
                + "If you have any questions or concerns, please contact your supervisor or the HR department.\n\n"
                + "Best regards,\n"
                + "PAMS Team\n"
                + "Generated at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
    
    private void logSystemAction(String actionType, String description) {
        try {
            String insertLog = "INSERT INTO system_logs (user_id, action_type, description, ip_address) "
                    + "VALUES (?, ?, ?, 'localhost')";

            PreparedStatement logPs = con.prepareStatement(insertLog);
            logPs.setInt(1, currentuserId);
            logPs.setString(2, actionType);
            logPs.setString(3, description);
            logPs.executeUpdate();
            logPs.close();

        } catch (SQLException e) {
            System.err.println("Error logging system action: " + e.getMessage());
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
            if (jLabel17 != null) {
                jLabel17.setText(message);

                if (message.toLowerCase().contains("ready for fingerprint")) {
                    jLabel17.setForeground(new Color(0, 128, 0)); 
                } else {
                    jLabel17.setForeground(Color.RED); 
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
    
    private void initializeSupervisorData() {
        try {
            try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL"); PreparedStatement ps = con.prepareStatement("SELECT unit FROM users WHERE user_id = ?")) {

                ps.setInt(1, currentuserId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        supervisorUnit = rs.getString("unit");
                    }
                }
            }

            loadStaffAttendanceData();
            updatePresentStaffCount();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Database connection error: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }

        loadSupervisorUnit();
        loadPresentStaffCount();
        loadSwapRequestsCount();
        loadRecentActivity();
        loadNotifications();

        notification_list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    deleteNotification();
                }
            }
        });
    }
    
    private void loadStaffAttendanceData() {
        PreparedStatement localPs = null;
        ResultSet localRs = null;

        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL")) {
            DefaultTableModel model = (DefaultTableModel) staff_table.getModel();
            model.setRowCount(0); 

            String query = "SELECT DISTINCT u.user_id, u.full_name, "
                    + "DATE(a.sign_in_time) as attendance_date, "
                    + "TIME(a.sign_in_time) as sign_in, "
                    + "TIME(a.sign_out_time) as sign_out, "
                    + "CASE "
                    + "    WHEN a.sign_in_time IS NULL THEN 'Absent' "
                    + "    WHEN a.sign_out_time IS NULL THEN 'Present' "
                    + "    ELSE 'Completed' "
                    + "END as status "
                    + "FROM users u "
                    + "LEFT JOIN attendance a ON u.user_id = a.user_id AND DATE(a.attendance_date) = CURDATE() "
                    + "WHERE u.unit = ? AND u.role = 'Staff' AND u.is_active = TRUE "
                    + "ORDER BY u.full_name";

            localPs = con.prepareStatement(query);
            localPs.setString(1, supervisorUnit);
            localRs = localPs.executeQuery();

            while (localRs.next()) {
                Object[] row = {
                    localRs.getInt("user_id"),
                    localRs.getString("full_name"),
                    localRs.getString("sign_in") != null ? localRs.getString("sign_in") : "Not Signed In",
                    localRs.getString("sign_out") != null ? localRs.getString("sign_out") : "Not Signed Out",
                    localRs.getString("status")
                };
                model.addRow(row);
            }
        } catch (SQLException e) {
        }
    }

    private void filterStaffTable(String filterType, String filterValue) {
        try {
            DefaultTableModel model = (DefaultTableModel) staff_table.getModel();
            model.setRowCount(0);

            String query;
            if ("Date".equals(filterType)) {
                query = "SELECT DISTINCT u.user_id, u.full_name, "
                        + "TIME(a.sign_in_time) as sign_in, "
                        + "TIME(a.sign_out_time) as sign_out, "
                        + "CASE "
                        + "    WHEN a.sign_in_time IS NULL THEN 'Absent' "
                        + "    WHEN a.sign_out_time IS NULL THEN 'Present' "
                        + "    ELSE 'Completed' "
                        + "END as status "
                        + "FROM users u "
                        + "LEFT JOIN attendance a ON u.user_id = a.user_id AND DATE(a.attendance_date) = ? "
                        + "WHERE u.unit = ? AND u.role = 'Staff' AND u.is_active = TRUE "
                        + "ORDER BY u.full_name";
            } else if ("Staff Id".equals(filterType)) {
                query = "SELECT DISTINCT u.user_id, u.full_name, "
                        + "TIME(a.sign_in_time) as sign_in, "
                        + "TIME(a.sign_out_time) as sign_out, "
                        + "CASE "
                        + "    WHEN a.sign_in_time IS NULL THEN 'Absent' "
                        + "    WHEN a.sign_out_time IS NULL THEN 'Present' "
                        + "    ELSE 'Completed' "
                        + "END as status "
                        + "FROM users u "
                        + "LEFT JOIN attendance a ON u.user_id = a.user_id AND DATE(a.attendance_date) = CURDATE() "
                        + "WHERE u.unit = ? AND u.role = 'Staff' AND u.is_active = TRUE AND u.user_id = ? "
                        + "ORDER BY u.full_name";
            } else {
                loadStaffAttendanceData();
                return;
            }

            ps = con.prepareStatement(query);
            if ("Date".equals(filterType)) {
                ps.setString(1, filterValue);
                ps.setString(2, supervisorUnit);
            } else {
                ps.setString(1, supervisorUnit);
                ps.setInt(2, Integer.parseInt(filterValue));
            }

            rs = ps.executeQuery();

            while (rs.next()) {
                Object[] row = {
                    rs.getInt("user_id"),
                    rs.getString("full_name"),
                    rs.getString("sign_in") != null ? rs.getString("sign_in") : "Not Signed In",
                    rs.getString("sign_out") != null ? rs.getString("sign_out") : "Not Signed Out",
                    rs.getString("status")
                };
                model.addRow(row);
            }

        } catch (SQLException | NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Error filtering data: " + e.getMessage(),
                    "Filter Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void updatePresentStaffCount() {
        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL")) {
            String query = "SELECT COUNT(*) as present_count FROM attendance a "
                    + "JOIN users u ON a.user_id = u.user_id "
                    + "WHERE u.unit = ? AND u.role = 'Staff' AND u.is_active = TRUE "
                    + "AND DATE(a.attendance_date) = CURDATE() "
                    + "AND a.sign_in_time IS NOT NULL AND a.sign_out_time IS NULL";

            ps = con.prepareStatement(query);
            ps.setString(1, supervisorUnit);
            rs = ps.executeQuery();

            if (rs.next()) {
                present_staff_label.setText(String.valueOf(rs.getInt("present_count")));
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error updating staff count: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void sendReminderToSelectedStaff() {
        int selectedRow = staff_table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a staff member to send reminder to.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int staffId = (Integer) staff_table.getValueAt(selectedRow, 0);
            String staffName = (String) staff_table.getValueAt(selectedRow, 1);
            String status = (String) staff_table.getValueAt(selectedRow, 4);

            if ("Present".equals(status) || "Completed".equals(status)) {
                JOptionPane.showMessageDialog(this, staffName + " is already present.",
                        "Already Present", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String insertQuery = "INSERT INTO email_notifications (user_id, email_type, subject, body, sent_status) "
                    + "VALUES (?, 'Sign_In', ?, ?, 'Pending')";

            String subject = "Attendance Reminder - Please Sign In";
            String body = "Dear " + staffName + ",\n\n"
                    + "This is a reminder to sign in for your shift today.\n"
                    + "Please report to your designated location and sign in using the biometric system.\n\n"
                    + "Best regards,\nAttendance Management System";

            ps = con.prepareStatement(insertQuery);
            ps.setInt(1, staffId);
            ps.setString(2, subject);
            ps.setString(3, body);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                // Log the action
                logSystemAction("EMAIL_SENT", "Reminder sent to staff ID: " + staffId + " (" + staffName + ")");

                JOptionPane.showMessageDialog(this, "Reminder sent successfully to " + staffName,
                        "Reminder Sent", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error sending reminder: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void loadSystemLogs(String filterType, String filterValue) {
        try {
            DefaultListModel<String> listModel = new DefaultListModel<>();
            logs_list.setModel(listModel);

            String query = "SELECT sl.*, u.full_name FROM system_logs sl "
                    + "LEFT JOIN users u ON sl.user_id = u.user_id "
                    + "WHERE 1=1 ";

            if ("Date".equals(filterType) && filterValue != null && !filterValue.isEmpty()) {
                query += "AND DATE(sl.created_at) = ? ";
            } else if ("Staff Id".equals(filterType) && filterValue != null && !filterValue.isEmpty()) {
                query += "AND sl.user_id = ? ";
            }

            // Only show logs related to supervisor's unit staff
            query += "AND (sl.user_id IS NULL OR sl.user_id IN "
                    + "(SELECT user_id FROM users WHERE unit = ? OR role IN ('IT_Admin', 'Director', 'IT_Head', 'Supervisor'))) "
                    + "ORDER BY sl.created_at DESC LIMIT 100";

            ps = con.prepareStatement(query);
            int paramIndex = 1;

            if ("Date".equals(filterType) && filterValue != null && !filterValue.isEmpty()) {
                ps.setString(paramIndex++, filterValue);
            } else if ("Staff Id".equals(filterType) && filterValue != null && !filterValue.isEmpty()) {
                ps.setInt(paramIndex++, Integer.parseInt(filterValue));
            }

            ps.setString(paramIndex, supervisorUnit);
            rs = ps.executeQuery();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            while (rs.next()) {
                String logEntry = String.format("[%s] %s - %s (%s)",
                        sdf.format(rs.getTimestamp("created_at")),
                        rs.getString("action_type"),
                        rs.getString("description"),
                        rs.getString("full_name") != null ? rs.getString("full_name") : "System"
                );
                listModel.addElement(logEntry);
            }

            if (listModel.isEmpty()) {
                listModel.addElement("No logs found for the selected filter.");
            }

        } catch (SQLException | NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Error loading logs: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void exportAttendanceToCSV() {
        try {
            javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser();
            fileChooser.setDialogTitle("Save Attendance Report");
            fileChooser.setSelectedFile(new java.io.File("attendance_report_"
                    + new SimpleDateFormat("yyyyMMdd").format(new java.util.Date()) + ".csv"));

            if (fileChooser.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
                java.io.File file = fileChooser.getSelectedFile();

                try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
                    // Write CSV header
                    writer.println("Staff ID,Name,Sign In Time,Sign Out Time,Status,Date");

                    DefaultTableModel model = (DefaultTableModel) staff_table.getModel();
                    for (int i = 0; i < model.getRowCount(); i++) {
                        writer.printf("%s,%s,%s,%s,%s,%s%n",
                                model.getValueAt(i, 0),
                                model.getValueAt(i, 1),
                                model.getValueAt(i, 2),
                                model.getValueAt(i, 3),
                                model.getValueAt(i, 4),
                                new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date())
                        );
                    }

                    logSystemAction("REPORT_GENERATION", "Attendance CSV report exported");
                    JOptionPane.showMessageDialog(this, "Report exported successfully to: " + file.getAbsolutePath(),
                            "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                }
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error exporting report: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void loadSupervisorUnit() {
        try (Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL"); PreparedStatement ps = con.prepareStatement("SELECT unit FROM users WHERE user_id = ?")) {

            ps.setInt(1, currentuserId);
            rs = ps.executeQuery();

            if (rs.next()) {
                supervisorUnit = rs.getString("unit");
            }

            con.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading supervisor unit: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadPresentStaffCount() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement(
                    "SELECT COUNT(DISTINCT a.user_id) as present_count "
                    + "FROM attendance a "
                    + "JOIN users u ON a.user_id = u.user_id "
                    + "WHERE u.unit = ? AND DATE(a.sign_in_time) = CURDATE() "
                    + "AND a.sign_out_time IS NULL"
            );
            ps.setString(1, supervisorUnit);
            rs = ps.executeQuery();

            if (rs.next()) {
                present_staff_label.setText(String.valueOf(rs.getInt("present_count")));
            }

            con.close();
        } catch (Exception e) {
            present_staff_label.setText("0");
            System.err.println("Error loading present staff count: " + e.getMessage());
        }
    }

    private void loadRecentActivity() {
        Connection localCon = null;
        PreparedStatement localPs = null;
        ResultSet localRs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            localCon = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            localPs = localCon.prepareStatement(
                    "SELECT u.full_name, a.sign_in_time, a.sign_out_time "
                    + "FROM attendance a "
                    + "JOIN users u ON a.user_id = u.user_id "
                    + "WHERE u.unit = ? AND DATE(a.sign_in_time) = CURDATE() "
                    + "ORDER BY a.sign_in_time DESC LIMIT 10"
            );
            localPs.setString(1, supervisorUnit);
            localRs = localPs.executeQuery();

            if (recentActivityModel != null) {
                recentActivityModel.clear();
                while (localRs.next()) {
                    String activity = localRs.getString("full_name") + " - ";
                    if (localRs.getTimestamp("sign_out_time") != null) {
                        activity += "Signed Out at " + localRs.getTimestamp("sign_out_time").toString();
                    } else {
                        activity += "Signed In at " + localRs.getTimestamp("sign_in_time").toString();
                    }
                    recentActivityModel.addElement(activity);
                }
            }

        } catch (Exception e) {
            System.err.println("Error loading recent activity: " + e.getMessage());
        } finally {
            try {
                if (localRs != null) {
                    localRs.close();
                }
                if (localPs != null) {
                    localPs.close();
                }
                if (localCon != null) {
                    localCon.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    private void loadNotifications() {
        Connection localCon = null;
        PreparedStatement localPs = null;
        ResultSet localRs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            localCon = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            localPs = localCon.prepareStatement(
                    "SELECT notification_id, subject, created_at "
                    + "FROM email_notifications "
                    + "WHERE user_id = ? "
                    + "ORDER BY created_at DESC LIMIT 20"
            );
            localPs.setInt(1, currentuserId);
            localRs = localPs.executeQuery();

            if (notificationModel != null) {
                notificationModel.clear();
                while (localRs.next()) {
                    String notification = localRs.getString("subject") + " - "
                            + localRs.getTimestamp("created_at").toString();
                    notificationModel.addElement(notification);
                }
            }

        } catch (Exception e) {
            System.err.println("Error loading notifications: " + e.getMessage());
        } finally {
            try {
                if (localRs != null) {
                    localRs.close();
                }
                if (localPs != null) {
                    localPs.close();
                }
                if (localCon != null) {
                    localCon.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }

    private void deleteNotification() {
        int selectedIndex = notification_list.getSelectedIndex();
        if (selectedIndex >= 0) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete this notification?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION);

            if (result == JOptionPane.YES_OPTION) {
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

                    // Get notification ID based on index
                    ps = con.prepareStatement(
                            "SELECT notification_id FROM email_notifications "
                            + "WHERE user_id = ? ORDER BY created_at DESC LIMIT 1 OFFSET ?"
                    );
                    ps.setInt(1, currentuserId);
                    ps.setInt(2, selectedIndex);
                    rs = ps.executeQuery();

                    if (rs.next()) {
                        int notificationId = rs.getInt("notification_id");

                        ps = con.prepareStatement("DELETE FROM email_notifications WHERE notification_id = ?");
                        ps.setInt(1, notificationId);
                        ps.executeUpdate();

                        loadNotifications();

                        JOptionPane.showMessageDialog(this, "Notification deleted successfully!");
                    }

                    con.close();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error deleting notification: " + e.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void uploadRoster() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

        ps = con.prepareStatement(
                "INSERT INTO rosters (unit, roster_name, start_date, end_date, uploaded_by, file_path) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
        );
        ps.setString(1, supervisorUnit);
        ps.setString(2, selectedRosterFile.getName());
        ps.setDate(3, new java.sql.Date(roster_start.getDate().getTime()));
        ps.setDate(4, new java.sql.Date(roster_end.getDate().getTime()));
        ps.setInt(5, currentuserId);
        ps.setString(6, selectedRosterFile.getAbsolutePath());
        ps.executeUpdate();

        parseAndAssignShifts();

        // Log the successful upload
        logSystemAction("ROSTER_UPLOAD", "Roster uploaded: " + selectedRosterFile.getName()
                + " for period " + roster_start.getDate() + " to " + roster_end.getDate());

        con.close();
    }

    private void downloadRoster() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            // Get the most recent active roster for supervisor's unit
            ps = con.prepareStatement(
                    "SELECT r.roster_id, r.roster_name, r.start_date, r.end_date, r.uploaded_at "
                    + "FROM rosters r "
                    + "WHERE r.unit = ? AND r.is_active = TRUE "
                    + "ORDER BY r.uploaded_at DESC LIMIT 1"
            );
            ps.setString(1, supervisorUnit);
            rs = ps.executeQuery();

            if (!rs.next()) {
                JOptionPane.showMessageDialog(this,
                        "No active roster found for " + supervisorUnit + " unit.",
                        "No Roster", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            int rosterId = rs.getInt("roster_id");
            String rosterName = rs.getString("roster_name");
            java.sql.Date startDate = rs.getDate("start_date");
            java.sql.Date endDate = rs.getDate("end_date");
            Timestamp uploadedAt = rs.getTimestamp("uploaded_at");

            rs.close();
            ps.close();

            // Choose save location
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Roster As");
            fileChooser.setSelectedFile(new File("roster_" + supervisorUnit + "_"
                    + new SimpleDateFormat("yyyy-MM-dd").format(startDate) + "_to_"
                    + new SimpleDateFormat("yyyy-MM-dd").format(endDate) + ".csv"));
            FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files", "csv");
            fileChooser.setFileFilter(filter);

            int userSelection = fileChooser.showSaveDialog(this);
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File saveFile = fileChooser.getSelectedFile();
            if (!saveFile.getName().toLowerCase().endsWith(".csv")) {
                saveFile = new File(saveFile.getAbsolutePath() + ".csv");
            }

            // Generate CSV content
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("Full Name,Date,Unit,Shift Type,Start Time,End Time\n");

            // Get all shifts for the roster period
            ps = con.prepareStatement(
                    "SELECT u.full_name, ss.shift_date, u.unit, s.shift_name, s.start_time, s.end_time "
                    + "FROM staff_shifts ss "
                    + "JOIN users u ON ss.user_id = u.user_id "
                    + "JOIN shifts s ON ss.shift_id = s.shift_id "
                    + "WHERE u.unit = ? AND ss.shift_date BETWEEN ? AND ? AND ss.is_active = TRUE "
                    + "ORDER BY ss.shift_date, u.full_name, s.start_time"
            );
            ps.setString(1, supervisorUnit);
            ps.setDate(2, new java.sql.Date(startDate.getTime()));
            ps.setDate(3, new java.sql.Date(endDate.getTime()));

            rs = ps.executeQuery();

            SimpleDateFormat dateFormat = new SimpleDateFormat("M/d/yyyy");
            SimpleDateFormat timeFormat = new SimpleDateFormat("H:mm");

            while (rs.next()) {
                String fullName = rs.getString("full_name");
                java.sql.Date shiftDate = rs.getDate("shift_date");
                String unit = rs.getString("unit");
                String shiftName = rs.getString("shift_name");
                Time startTime = rs.getTime("start_time");
                Time endTime = rs.getTime("end_time");

                csvContent.append(fullName).append(",")
                        .append(dateFormat.format(shiftDate)).append(",")
                        .append(unit).append(",")
                        .append(shiftName).append(",")
                        .append(timeFormat.format(startTime)).append(",")
                        .append(timeFormat.format(endTime)).append("\n");
            }

            // Write to file
            try (PrintWriter writer = new PrintWriter(saveFile)) {
                writer.write(csvContent.toString());
            }

            // Log the download
            logSystemAction("ROSTER_DOWNLOAD",
                    "Downloaded roster for " + supervisorUnit + " unit ("
                    + dateFormat.format(startDate) + " to " + dateFormat.format(endDate) + ")");

            JOptionPane.showMessageDialog(this,
                    "Roster downloaded successfully!\n"
                    + "File: " + saveFile.getName() + "\n"
                    + "Period: " + dateFormat.format(startDate) + " to " + dateFormat.format(endDate) + "\n"
                    + "Uploaded: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(uploadedAt),
                    "Download Complete", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Error downloading roster: " + e.getMessage(),
                    "Download Error", JOptionPane.ERROR_MESSAGE);
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
    
    private void parseAndAssignShifts() throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(selectedRosterFile));
        String line;
        boolean isFirstLine = true;
        int successCount = 0;
        int errorCount = 0;
        int skippedCount = 0; 
        StringBuilder errorLog = new StringBuilder();

        while ((line = reader.readLine()) != null) {
            if (isFirstLine) {
                isFirstLine = false;
                continue;
            }

            String[] parts = line.split(",");
            if (parts.length >= 6) {
                String fullName = parts[0].trim();
                String dateStr = parts[1].trim();
                String unit = parts[2].trim();
                String shiftType = parts[3].trim();
                String startTime = parts[4].trim();
                String endTime = parts[5].trim();

                try {
                    // Convert date format from M/d/yyyy to yyyy-MM-dd
                    SimpleDateFormat inputFormat = new SimpleDateFormat("M/d/yyyy");
                    inputFormat.setLenient(false); // Strict parsing
                    java.util.Date parsedDate = inputFormat.parse(dateStr);

                    // Convert to SQL date
                    java.sql.Date sqlDate = new java.sql.Date(parsedDate.getTime());

                    System.out.println("Debug - Processing: " + fullName + " on " + sqlDate + " (" + dateStr + ")");

                    ps = con.prepareStatement("SELECT user_id, unit FROM users WHERE full_name = ? AND is_active = TRUE");
                    ps.setString(1, fullName);
                    rs = ps.executeQuery();

                    if (rs.next()) {
                        int userId = rs.getInt("user_id");
                        String userUnit = rs.getString("unit");

                        // Check if user belongs to the supervisor's unit
                        if (!userUnit.equals(supervisorUnit)) {
                            System.out.println("Skipping user " + fullName + " - not in supervisor's unit ("
                                    + userUnit + " vs " + supervisorUnit + ")");
                            skippedCount++;
                            continue; 
                        }

                        if (!userUnit.equals(unit)) {
                            System.err.println("Unit mismatch in CSV for user " + fullName
                                    + ": Database has " + userUnit + ", CSV has " + unit);
                        }

                        // Ensure time format is HH:mm:ss
                        String formattedStartTime = formatTime(startTime);
                        String formattedEndTime = formatTime(endTime);

                        if (formattedStartTime == null || formattedEndTime == null) {
                            errorLog.append("Invalid time format for ").append(fullName).append(": ")
                                    .append(startTime).append(" - ").append(endTime).append("\n");
                            errorCount++;
                            continue;
                        }

                        // Find or create shift for the supervisor's unit
                        ps = con.prepareStatement(
                                "SELECT shift_id FROM shifts WHERE unit = ? AND shift_name = ? AND start_time = ? AND end_time = ?"
                        );
                        ps.setString(1, supervisorUnit); // Use supervisor's unit consistently
                        ps.setString(2, shiftType);
                        ps.setTime(3, Time.valueOf(formattedStartTime));
                        ps.setTime(4, Time.valueOf(formattedEndTime));
                        rs = ps.executeQuery();

                        int shiftId;
                        if (rs.next()) {
                            shiftId = rs.getInt("shift_id");
                        } else {
                            // Create new shift for the supervisor's unit
                            ps = con.prepareStatement(
                                    "INSERT INTO shifts (unit, shift_name, start_time, end_time) VALUES (?, ?, ?, ?)",
                                    Statement.RETURN_GENERATED_KEYS
                            );
                            ps.setString(1, supervisorUnit); // Use supervisor's unit consistently
                            ps.setString(2, shiftType);
                            ps.setTime(3, Time.valueOf(formattedStartTime));
                            ps.setTime(4, Time.valueOf(formattedEndTime));
                            ps.executeUpdate();

                            rs = ps.getGeneratedKeys();
                            rs.next();
                            shiftId = rs.getInt(1);
                            System.out.println("Created new shift: " + shiftType + " for " + supervisorUnit);
                        }

                        // Assign shift to staff
                        ps = con.prepareStatement(
                                "INSERT INTO staff_shifts (user_id, shift_id, shift_date, is_active) "
                                + "VALUES (?, ?, ?, TRUE) "
                                + "ON DUPLICATE KEY UPDATE shift_id = VALUES(shift_id), is_active = TRUE"
                        );
                        ps.setInt(1, userId);
                        ps.setInt(2, shiftId);
                        ps.setDate(3, sqlDate);
                        ps.executeUpdate();

                        successCount++;
                        System.out.println("Successfully assigned shift for: " + fullName + " on " + sqlDate);
                    } else {
                        errorLog.append("User not found or not active: ").append(fullName).append("\n");
                        errorCount++;
                    }
                } catch (Exception e) {
                    errorLog.append("Error processing ").append(fullName).append(": ").append(e.getMessage()).append("\n");
                    errorCount++;
                    System.err.println("Error processing " + fullName + ": " + e.getMessage());
                }
            } else {
                errorLog.append("Invalid CSV line format: ").append(line).append("\n");
                errorCount++;
            }
        }
        reader.close();

        // Show summary including skipped users
        String summary = "Roster upload completed:\n"
                + "✓ Successfully processed: " + successCount + " entries\n"
                + "⚠ Skipped (different unit): " + skippedCount + " entries\n"
                + "✗ Errors: " + errorCount + " entries";

        if (skippedCount > 0) {
            summary += "\n\nNote: " + skippedCount + " users were skipped because they don't belong to your unit (" + supervisorUnit + ").";
        }

        if (errorCount > 0) {
            summary += "\n\nErrors:\n" + errorLog.toString();
        }

        JOptionPane.showMessageDialog(this, summary, "Upload Summary",
                errorCount > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatTime(String timeStr) {
        try {
            // Handle different time formats
            if (timeStr.matches("\\d{1,2}:\\d{2}")) {
                // Format like "7:00" or "15:00"
                return timeStr + ":00";
            } else if (timeStr.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
                // Already in correct format
                return timeStr;
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void sendNotificationToUnit(String message) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

        ps = con.prepareStatement("SELECT user_id, full_name, email FROM users WHERE unit = ? AND role = 'Staff'");
        ps.setString(1, supervisorUnit);
        rs = ps.executeQuery();

        List<Integer> userIds = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        List<String> fullNames = new ArrayList<>();

        while (rs.next()) {
            userIds.add(rs.getInt("user_id"));
            emails.add(rs.getString("email"));
            fullNames.add(rs.getString("full_name"));
        }

        // Insert notifications into the DB
        for (int userId : userIds) {
            ps = con.prepareStatement(
                    "INSERT INTO email_notifications (user_id, email_type, subject, body) VALUES (?, ?, ?, ?)"
            );
            ps.setInt(1, userId);
            ps.setString(2, "Shift_Assignment");
            ps.setString(3, "New Roster Assignment");
            ps.setString(4, message);
            ps.executeUpdate();
        }

        // Send emails
        for (int i = 0; i < emails.size(); i++) {
            String email = emails.get(i);
            String fullName = fullNames.get(i);
            sendNotificationEmail(fullName, email, message);
        }

        con.close();
    }

    private void sendNotificationEmail(String fullName, String recipientEmail, String messageBody) {
        String subject = "PAMS Notification - New Roster Assignment";
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
            message.setText(formatNotificationBody(fullName, messageBody));

            Transport.send(message);
            emailSent = true;
            System.out.println("Email sent to: " + recipientEmail + " via STARTTLS.");

        } catch (MessagingException e) {
            System.err.println("STARTTLS failed for " + recipientEmail + ": " + e.getMessage());

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
                sslMessage.setText(formatNotificationBody(fullName, messageBody));

                Transport.send(sslMessage);
                emailSent = true;
                System.out.println("Email sent to: " + recipientEmail + " via SSL fallback.");
            } catch (MessagingException sslException) {
                System.err.println("SSL fallback failed for " + recipientEmail + ": " + sslException.getMessage());
            }
        } catch (Exception ex) {
            System.err.println("Unexpected error sending email to " + recipientEmail + ": " + ex.getMessage());
        }

        if (!emailSent) {
            System.err.println("Failed to send notification to: " + recipientEmail);
        }
    }

    private String formatNotificationBody(String fullName, String message) {
        return "Dear " + fullName + ",\n\n"
                + "You have a new roster assignment in PAMS.\n\n"
                + "Details:\n"
                + message + "\n\n"
                + "Please log in to your dashboard for more information.\n\n"
                + "Best regards,\n"
                + "PAMS Admin Team";
    }
    
    private void sendReminderToUnit(String message) {
        try {
            String insertQuery = "INSERT INTO email_notifications (user_id, email_type, subject, body, sent_status) "
                    + "SELECT user_id, 'Registration', 'Unit Notification', ?, 'Pending' "
                    + "FROM users WHERE unit = ? AND role = 'Staff' AND is_active = TRUE";

            ps = con.prepareStatement(insertQuery);
            ps.setString(1, message);
            ps.setString(2, supervisorUnit);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                logSystemAction("EMAIL_SENT", "Unit notification sent to " + rowsAffected + " staff members");
                JOptionPane.showMessageDialog(this, "Notification sent to " + rowsAffected + " staff members",
                        "Notification Sent", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "No staff members found to send notification to",
                        "No Recipients", JOptionPane.WARNING_MESSAGE);
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error sending notification: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    private void initializeSwapTable() {
        String[] columns = {"Request ID", "Requester", "Target User", "Date", "Reason", "Requested At"};
        swapTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table non-editable
            }
        };
        swap_table.setModel(swapTableModel);

        // Hide the Request ID column but keep it for reference
        swap_table.getColumnModel().getColumn(0).setMinWidth(0);
        swap_table.getColumnModel().getColumn(0).setMaxWidth(0);
        swap_table.getColumnModel().getColumn(0).setWidth(0);

        swap_table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = swap_table.getSelectedRow();
                boolean hasSelection = selectedRow >= 0;

                approve_button.setEnabled(hasSelection);
                decline_button.setEnabled(hasSelection);

                if (hasSelection) {
                    selectedRequestId = (Integer) swapTableModel.getValueAt(selectedRow, 0);
                } else {
                    selectedRequestId = -1;
                }
            }
        });

        approve_button.setEnabled(false);
        decline_button.setEnabled(false);
    }
    
    private void loadSwapRequestsCount() {
        Connection localCon = null;
        PreparedStatement localPs = null;
        ResultSet localRs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            localCon = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            localPs = localCon.prepareStatement(
                    "SELECT COUNT(*) as request_count "
                    + "FROM shift_swap_requests ssr "
                    + "JOIN users u ON ssr.requester_id = u.user_id "
                    + "WHERE u.unit = ? AND ssr.status = 'Pending'"
            );
            localPs.setString(1, supervisorUnit);
            localRs = localPs.executeQuery();

            if (localRs.next()) {
                swap_requests_label.setText(String.valueOf(localRs.getInt("request_count")));
            }

        } catch (ClassNotFoundException | SQLException e) {
            swap_requests_label.setText("0");
            System.err.println("Error loading swap requests count: " + e.getMessage());
        } finally {
            try {
                if (localRs != null) {
                    localRs.close();
                }
                if (localPs != null) {
                    localPs.close();
                }
                if (localCon != null) {
                    localCon.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing database resources: " + e.getMessage());
            }
        }
    }

    private void loadSwapRequests() {
        Connection localCon = null;
        PreparedStatement localPs = null;
        ResultSet localRs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            localCon = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = "SELECT ssr.request_id, u1.full_name as requester_name, u2.full_name as target_name, "
                    + "ssr.swap_date, ssr.request_reason, ssr.requested_at "
                    + "FROM shift_swap_requests ssr "
                    + "JOIN users u1 ON ssr.requester_id = u1.user_id "
                    + "JOIN users u2 ON ssr.target_user_id = u2.user_id "
                    + "WHERE u1.unit = ? AND ssr.status = 'Pending' "
                    + "ORDER BY ssr.requested_at DESC";

            localPs = localCon.prepareStatement(query);
            localPs.setString(1, supervisorUnit);
            localRs = localPs.executeQuery();

            swapTableModel.setRowCount(0);

            while (localRs.next()) {
                Object[] row = {
                    localRs.getInt("request_id"),
                    localRs.getString("requester_name"), 
                    localRs.getString("target_name"), 
                    localRs.getDate("swap_date"), 
                    localRs.getString("request_reason"), 
                    localRs.getTimestamp("requested_at") 
                };
                swapTableModel.addRow(row);
            }

            // Reset selection
            selectedRequestId = -1;
            approve_button.setEnabled(false);
            decline_button.setEnabled(false);

        } catch (ClassNotFoundException | SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading swap requests: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        } finally {
            try {
                if (localRs != null) {
                    localRs.close();
                }
                if (localPs != null) {
                    localPs.close();
                }
                if (localCon != null) {
                    localCon.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing database resources: " + e.getMessage());
            }
        }
    }

    private void updateSwapRequestsCount() {
        Connection localCon = null;
        PreparedStatement localPs = null;
        ResultSet localRs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            localCon = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = "SELECT COUNT(*) as swap_count FROM shift_swap_requests ssr "
                    + "JOIN users u1 ON ssr.requester_id = u1.user_id "
                    + "WHERE u1.unit = ? AND ssr.status = 'Pending'";

            localPs = localCon.prepareStatement(query);
            localPs.setString(1, supervisorUnit);
            localRs = localPs.executeQuery();

            if (localRs.next()) {
                swap_requests_label.setText(String.valueOf(localRs.getInt("swap_count")));
            }

        } catch (ClassNotFoundException | SQLException e) {
            System.err.println("Error updating swap requests count: " + e.getMessage());
        } finally {
            try {
                if (localRs != null) {
                    localRs.close();
                }
                if (localPs != null) {
                    localPs.close();
                }
                if (localCon != null) {
                    localCon.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing database resources: " + e.getMessage());
            }
        }
    }

    private void processSwapRequest(boolean approve) {
        if (selectedRequestId == -1) {
            JOptionPane.showMessageDialog(this, "Please select a request to process.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String status = approve ? "Approved" : "Declined";
        Connection localCon = null;
        PreparedStatement localPs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            localCon = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            localPs = localCon.prepareStatement(
                    "UPDATE shift_swap_requests SET status = ?, supervisor_id = ?, processed_at = NOW() WHERE request_id = ?"
            );
            localPs.setString(1, status);
            localPs.setInt(2, currentuserId);
            localPs.setInt(3, selectedRequestId);

            int rowsAffected = localPs.executeUpdate();

            if (rowsAffected > 0) {
                JOptionPane.showMessageDialog(this, "Request " + status.toLowerCase() + " successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);

                // Refresh the data
                loadSwapRequests();
                loadSwapRequestsCount();
            } else {
                JOptionPane.showMessageDialog(this, "No request was updated. Please try again.",
                        "Update Failed", JOptionPane.WARNING_MESSAGE);
            }

        } catch (ClassNotFoundException | SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error processing request: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        } finally {
            try {
                if (localPs != null) {
                    localPs.close();
                }
                if (localCon != null) {
                    localCon.close();
                }
            } catch (SQLException e) {
                System.err.println("Error closing database resources: " + e.getMessage());
            }
        }
    }
    
    private void initializeProfile() {
        loadProfileData();
        loadUpcomingShifts();
        setFieldsEditable(false);
    }

    private void loadProfileData() {
        if (currentuserId == -1) {
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement("SELECT full_name, date_of_birth, gender, email, phone_number, unit, role, photo FROM users WHERE user_id = ? AND is_active = TRUE");
            ps.setInt(1, currentuserId);
            rs = ps.executeQuery();

            if (rs.next()) {
                originalFullName = rs.getString("full_name");
                originalDob = rs.getDate("date_of_birth");
                originalGender = rs.getString("gender");
                originalEmail = rs.getString("email");
                originalPhone = rs.getString("phone_number");
                originalUnit = rs.getString("unit");
                originalRole = rs.getString("role");
                originalPhoto = rs.getBytes("photo");
                currentPhoto = originalPhoto;

                username_field.setText(originalFullName);
                dob_chooser.setDate(originalDob);
                gender_combo.setSelectedItem(originalGender);
                email_field.setText(originalEmail);
                phone_field.setText(originalPhone);
                unit_combo.setSelectedItem(originalUnit);
                role_combo.setSelectedItem(originalRole);

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

            ps.setInt(9, currentuserId);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                logSystemAction("USER_UPDATE", "User profile updated successfully");
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

    private void loadUpcomingShifts() {
        if (currentuserId == -1) {
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        DefaultListModel<String> model = new DefaultListModel<>();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            ps = con.prepareStatement(
                    "SELECT ss.shift_date, s.shift_name, s.start_time, s.end_time, s.unit "
                    + "FROM staff_shifts ss "
                    + "JOIN shifts s ON ss.shift_id = s.shift_id "
                    + "WHERE ss.user_id = ? AND ss.shift_date >= CURDATE() AND ss.is_active = TRUE "
                    + "ORDER BY ss.shift_date ASC, s.start_time ASC "
                    + "LIMIT 10"
            );
            ps.setInt(1, currentuserId);
            rs = ps.executeQuery();

            while (rs.next()) {
                java.sql.Date shiftDate = rs.getDate("shift_date");
                String shiftName = rs.getString("shift_name");
                Time startTime = rs.getTime("start_time");
                Time endTime = rs.getTime("end_time");
                String unit = rs.getString("unit");

                String shiftInfo = String.format("%s - %s (%s) %s-%s",
                        shiftDate.toString(), shiftName, unit,
                        startTime.toString(), endTime.toString());
                model.addElement(shiftInfo);
            }

            if (model.isEmpty()) {
                model.addElement("No upcoming shifts");
            }

            upcoming_list.setModel(model);

        } catch (Exception e) {
            model.addElement("Error loading shifts");
            upcoming_list.setModel(model);
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
            ps.setInt(1, currentuserId);
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
            ps.setInt(2, currentuserId);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                logSystemAction("PASSWORD_CHANGE", "User password changed successfully");
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
            if (currentuserId == -1) {
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
                ps.setInt(1, currentuserId);
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

    @Override
    public void dispose() {
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
            System.err.println("Error closing database connection: " + e.getMessage());
        }
        super.dispose();
    }
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        sign_frame = new javax.swing.JFrame();
        sign_panel = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        fingerprint_label = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        cancel_button = new javax.swing.JButton();
        jLabel17 = new javax.swing.JLabel();
        jFileChooser1 = new javax.swing.JFileChooser();
        password_frame = new javax.swing.JFrame();
        jPanel11 = new javax.swing.JPanel();
        jLabel18 = new javax.swing.JLabel();
        change_password_button = new javax.swing.JButton();
        jPanel12 = new javax.swing.JPanel();
        jLabel19 = new javax.swing.JLabel();
        jLabel20 = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        password_1 = new javax.swing.JPasswordField();
        show_password1 = new javax.swing.JCheckBox();
        show_password2 = new javax.swing.JCheckBox();
        password_2 = new javax.swing.JPasswordField();
        jLabel22 = new javax.swing.JLabel();
        device_status_label = new javax.swing.JLabel();
        finger_instruction_label = new javax.swing.JLabel();
        cancel_change_button = new javax.swing.JButton();
        swap_frame = new javax.swing.JFrame();
        sign_panel1 = new javax.swing.JPanel();
        jPanel13 = new javax.swing.JPanel();
        jLabel23 = new javax.swing.JLabel();
        jLabel24 = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        swap_table = new javax.swing.JTable();
        decline_button = new javax.swing.JButton();
        approve_button = new javax.swing.JButton();
        jPanel9 = new javax.swing.JPanel();
        jPanel10 = new javax.swing.JPanel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jSeparator4 = new javax.swing.JSeparator();
        jLabel16 = new javax.swing.JLabel();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane12 = new javax.swing.JScrollPane();
        recent_activity_list = new javax.swing.JList<>();
        jScrollPane13 = new javax.swing.JScrollPane();
        notification_list = new javax.swing.JList<>();
        jPanel1 = new javax.swing.JPanel();
        present_staff_label = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        swap_requests_label = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        roster_start = new com.toedter.calendar.JDateChooser();
        roster_end = new com.toedter.calendar.JDateChooser();
        jLabel4 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        browse_button = new javax.swing.JButton();
        jLabel8 = new javax.swing.JLabel();
        roster_button = new javax.swing.JButton();
        notification_button = new javax.swing.JButton();
        swap_button = new javax.swing.JButton();
        sign_button = new javax.swing.JButton();
        logout_button = new javax.swing.JButton();
        signout_button = new javax.swing.JButton();
        dowload_roster_button = new javax.swing.JButton();
        jPanel6 = new javax.swing.JPanel();
        filter_combo = new javax.swing.JComboBox<>();
        jScrollPane1 = new javax.swing.JScrollPane();
        staff_table = new javax.swing.JTable();
        reminder_button = new javax.swing.JButton();
        jScrollPane14 = new javax.swing.JScrollPane();
        logs_list = new javax.swing.JList<>();
        logs_button = new javax.swing.JButton();
        filter_combo1 = new javax.swing.JComboBox<>();
        export_button = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        all_shifts_button = new javax.swing.JButton();
        change_button = new javax.swing.JButton();
        jScrollPane15 = new javax.swing.JScrollPane();
        upcoming_list = new javax.swing.JList<>();
        jLabel5 = new javax.swing.JLabel();
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

        jLabel17.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        jLabel17.setForeground(new java.awt.Color(255, 51, 0));
        jLabel17.setText("\"\"");

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
                        .addComponent(jLabel17)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        sign_panelLayout.setVerticalGroup(
            sign_panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sign_panelLayout.createSequentialGroup()
                .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jLabel17)
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

        jPanel11.setBackground(new java.awt.Color(255, 255, 255));
        jPanel11.setForeground(new java.awt.Color(255, 255, 255));

        jLabel18.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel18.setForeground(new java.awt.Color(45, 59, 111));
        jLabel18.setText("Type Password");

        change_password_button.setBackground(new java.awt.Color(51, 204, 0));
        change_password_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        change_password_button.setForeground(new java.awt.Color(255, 255, 255));
        change_password_button.setText("Confirm Password");
        change_password_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                change_password_buttonActionPerformed(evt);
            }
        });

        jPanel12.setBackground(new java.awt.Color(45, 59, 111));

        jLabel19.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel19.setForeground(new java.awt.Color(255, 255, 255));
        jLabel19.setText("CHANGE PASSWORD");

        jLabel20.setIcon(new javax.swing.ImageIcon(getClass().getResource("/biometrics_exam/pau_icon-25x25.png"))); // NOI18N

        jLabel21.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel21.setForeground(new java.awt.Color(255, 255, 255));
        jLabel21.setText("Attendance Management System");

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel20)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel12Layout.createSequentialGroup()
                        .addGap(76, 76, 76)
                        .addComponent(jLabel21))
                    .addGroup(jPanel12Layout.createSequentialGroup()
                        .addGap(59, 59, 59)
                        .addComponent(jLabel19)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel20, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel12Layout.createSequentialGroup()
                        .addComponent(jLabel19)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel21)))
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

        jLabel22.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel22.setForeground(new java.awt.Color(45, 59, 111));
        jLabel22.setText("Retype Password");

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

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addGap(50, 50, 50)
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(finger_instruction_label))
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel22)
                            .addComponent(password_2, javax.swing.GroupLayout.PREFERRED_SIZE, 368, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(show_password2)
                            .addComponent(jLabel18)
                            .addComponent(show_password1)
                            .addComponent(password_1, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(device_status_label)
                        .addGap(430, 430, 430))
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addComponent(change_password_button, javax.swing.GroupLayout.PREFERRED_SIZE, 214, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(cancel_change_button, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addComponent(jPanel12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addGap(46, 46, 46)
                        .addComponent(jLabel18)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(password_1, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(show_password1)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel22)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(password_2, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(44, 44, 44)
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(change_password_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cancel_change_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 52, Short.MAX_VALUE)
                        .addComponent(finger_instruction_label)
                        .addContainerGap(12, Short.MAX_VALUE))
                    .addGroup(jPanel11Layout.createSequentialGroup()
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
                .addComponent(jPanel11, javax.swing.GroupLayout.PREFERRED_SIZE, 494, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        password_frameLayout.setVerticalGroup(
            password_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(password_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        sign_panel1.setBackground(new java.awt.Color(255, 255, 255));
        sign_panel1.setForeground(new java.awt.Color(255, 255, 255));

        jPanel13.setBackground(new java.awt.Color(45, 59, 111));

        jLabel23.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel23.setForeground(new java.awt.Color(255, 255, 255));
        jLabel23.setText("SWAP REQUESTS");

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
                .addGap(149, 149, 149)
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel23)
                    .addComponent(jLabel25))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addGroup(jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel13Layout.createSequentialGroup()
                        .addGap(25, 25, 25)
                        .addComponent(jLabel24, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel13Layout.createSequentialGroup()
                        .addGap(19, 19, 19)
                        .addComponent(jLabel23)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel25)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        swap_table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Requester", "Target User", "Date", "Reason", "Requested At"
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
        jScrollPane2.setViewportView(swap_table);
        if (swap_table.getColumnModel().getColumnCount() > 0) {
            swap_table.getColumnModel().getColumn(0).setResizable(false);
            swap_table.getColumnModel().getColumn(1).setResizable(false);
            swap_table.getColumnModel().getColumn(2).setResizable(false);
            swap_table.getColumnModel().getColumn(4).setResizable(false);
        }

        decline_button.setBackground(new java.awt.Color(204, 51, 0));
        decline_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        decline_button.setForeground(new java.awt.Color(255, 255, 255));
        decline_button.setText("Decine");
        decline_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decline_buttonActionPerformed(evt);
            }
        });

        approve_button.setBackground(new java.awt.Color(51, 204, 0));
        approve_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        approve_button.setForeground(new java.awt.Color(255, 255, 255));
        approve_button.setText("Approve");
        approve_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                approve_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout sign_panel1Layout = new javax.swing.GroupLayout(sign_panel1);
        sign_panel1.setLayout(sign_panel1Layout);
        sign_panel1Layout.setHorizontalGroup(
            sign_panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(sign_panel1Layout.createSequentialGroup()
                .addGroup(sign_panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(sign_panel1Layout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 594, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(sign_panel1Layout.createSequentialGroup()
                        .addGap(82, 82, 82)
                        .addComponent(approve_button, javax.swing.GroupLayout.PREFERRED_SIZE, 214, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(35, 35, 35)
                        .addComponent(decline_button, javax.swing.GroupLayout.PREFERRED_SIZE, 214, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(25, Short.MAX_VALUE))
        );
        sign_panel1Layout.setVerticalGroup(
            sign_panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sign_panel1Layout.createSequentialGroup()
                .addComponent(jPanel13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 272, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(31, 31, 31)
                .addGroup(sign_panel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(approve_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(decline_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(30, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout swap_frameLayout = new javax.swing.GroupLayout(swap_frame.getContentPane());
        swap_frame.getContentPane().setLayout(swap_frameLayout);
        swap_frameLayout.setHorizontalGroup(
            swap_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(swap_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(sign_panel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        swap_frameLayout.setVerticalGroup(
            swap_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(swap_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(sign_panel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                .addGap(132, 132, 132)
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

        jPanel5.setBackground(new java.awt.Color(255, 255, 255));

        recent_activity_list.setBackground(new java.awt.Color(229, 229, 229));
        recent_activity_list.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        jScrollPane12.setViewportView(recent_activity_list);

        notification_list.setBackground(new java.awt.Color(215, 234, 240));
        notification_list.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        jScrollPane13.setViewportView(notification_list);

        jPanel1.setBackground(new java.awt.Color(215, 237, 252));
        jPanel1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(255, 255, 255), 1, true));

        present_staff_label.setFont(new java.awt.Font("Segoe UI", 1, 50)); // NOI18N
        present_staff_label.setForeground(new java.awt.Color(74, 118, 165));
        present_staff_label.setText("0");

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(74, 118, 165));
        jLabel2.setText("Staff Present");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(24, Short.MAX_VALUE)
                .addComponent(jLabel2)
                .addGap(17, 17, 17))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(59, 59, 59)
                .addComponent(present_staff_label)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(present_staff_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addContainerGap(15, Short.MAX_VALUE))
        );

        jPanel3.setBackground(new java.awt.Color(215, 237, 252));
        jPanel3.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(255, 255, 255), 1, true));

        swap_requests_label.setFont(new java.awt.Font("Segoe UI", 1, 50)); // NOI18N
        swap_requests_label.setForeground(new java.awt.Color(74, 118, 165));
        swap_requests_label.setText("0");

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel3.setForeground(new java.awt.Color(74, 118, 165));
        jLabel3.setText("Swap Requests");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                .addContainerGap(15, Short.MAX_VALUE)
                .addComponent(jLabel3)
                .addGap(14, 14, 14))
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(64, 64, 64)
                .addComponent(swap_requests_label)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(swap_requests_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel3)
                .addContainerGap(29, Short.MAX_VALUE))
        );

        jLabel1.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel1.setForeground(new java.awt.Color(45, 59, 111));
        jLabel1.setText("Upload Roster");

        jSeparator1.setBackground(new java.awt.Color(45, 59, 111));
        jSeparator1.setForeground(new java.awt.Color(45, 59, 111));

        roster_start.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 59, 111)));

        roster_end.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 59, 111)));

        jLabel4.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(45, 59, 111));
        jLabel4.setText("Roster Period");

        jPanel2.setBackground(new java.awt.Color(255, 255, 255));
        jPanel2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 2, true));

        jLabel7.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        jLabel7.setForeground(new java.awt.Color(45, 59, 111));
        jLabel7.setText("CSV Format: Name, Date, Unit, Shift_Type, Start_Time, End_Time  ");

        browse_button.setBackground(new java.awt.Color(45, 59, 111));
        browse_button.setFont(new java.awt.Font("Segoe UI", 1, 16)); // NOI18N
        browse_button.setForeground(new java.awt.Color(255, 255, 255));
        browse_button.setText("Browse");
        browse_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browse_buttonActionPerformed(evt);
            }
        });

        jLabel8.setForeground(new java.awt.Color(153, 153, 153));
        jLabel8.setText("     file here");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(171, 171, 171)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(browse_button, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(84, 84, 84)
                        .addComponent(jLabel7)))
                .addContainerGap(111, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap(16, Short.MAX_VALUE)
                .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(browse_button)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel7)
                .addGap(15, 15, 15))
        );

        roster_button.setBackground(new java.awt.Color(47, 184, 2));
        roster_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        roster_button.setForeground(new java.awt.Color(255, 255, 255));
        roster_button.setText("Upload Roster");
        roster_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                roster_buttonActionPerformed(evt);
            }
        });

        notification_button.setBackground(new java.awt.Color(255, 153, 0));
        notification_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        notification_button.setForeground(new java.awt.Color(255, 255, 255));
        notification_button.setText("Send Notification");
        notification_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                notification_buttonActionPerformed(evt);
            }
        });

        swap_button.setBackground(new java.awt.Color(45, 59, 111));
        swap_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        swap_button.setForeground(new java.awt.Color(255, 255, 255));
        swap_button.setText("Swap Requests");
        swap_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                swap_buttonActionPerformed(evt);
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

        logout_button.setBackground(new java.awt.Color(255, 0, 0));
        logout_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        logout_button.setForeground(new java.awt.Color(255, 255, 255));
        logout_button.setText("Log Out");
        logout_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logout_buttonActionPerformed(evt);
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

        dowload_roster_button.setBackground(new java.awt.Color(204, 0, 0));
        dowload_roster_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        dowload_roster_button.setForeground(new java.awt.Color(255, 255, 255));
        dowload_roster_button.setText("Download Roster");
        dowload_roster_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dowload_roster_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel5Layout.createSequentialGroup()
                                        .addGap(18, 18, 18)
                                        .addComponent(jLabel1)
                                        .addGap(0, 0, Short.MAX_VALUE))
                                    .addGroup(jPanel5Layout.createSequentialGroup()
                                        .addGap(18, 18, 18)
                                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(jSeparator1)
                                            .addGroup(jPanel5Layout.createSequentialGroup()
                                                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                    .addComponent(jLabel4)
                                                    .addGroup(jPanel5Layout.createSequentialGroup()
                                                        .addComponent(roster_start, javax.swing.GroupLayout.PREFERRED_SIZE, 170, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addGap(18, 18, 18)
                                                        .addComponent(roster_end, javax.swing.GroupLayout.PREFERRED_SIZE, 170, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                    .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                .addGap(0, 26, Short.MAX_VALUE))))))
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(jScrollPane13, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 661, Short.MAX_VALUE)
                                    .addComponent(jScrollPane12, javax.swing.GroupLayout.Alignment.LEADING))
                                .addGap(0, 0, Short.MAX_VALUE))))
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addGap(23, 23, 23)
                                .addComponent(swap_button)
                                .addGap(56, 56, 56)
                                .addComponent(roster_button)
                                .addGap(18, 18, 18)
                                .addComponent(dowload_roster_button)
                                .addGap(18, 18, 18)
                                .addComponent(notification_button))
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(sign_button)
                                .addGap(18, 18, 18)
                                .addComponent(signout_button)
                                .addGap(18, 18, 18)
                                .addComponent(logout_button)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(roster_start, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(roster_end, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(18, 18, 18)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(roster_button)
                    .addComponent(swap_button)
                    .addComponent(dowload_roster_button)
                    .addComponent(notification_button))
                .addGap(18, 18, 18)
                .addComponent(jScrollPane12, javax.swing.GroupLayout.PREFERRED_SIZE, 145, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jScrollPane13, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sign_button)
                    .addComponent(logout_button)
                    .addComponent(signout_button))
                .addContainerGap(39, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("Dashboard", jPanel5);

        jPanel6.setBackground(new java.awt.Color(255, 255, 255));

        filter_combo.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        filter_combo.setForeground(new java.awt.Color(45, 59, 111));
        filter_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Filter By", "Date", "Staff Id" }));

        staff_table.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        staff_table.setForeground(new java.awt.Color(45, 59, 111));
        staff_table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Staff ID", "Name", "Sign In", "Sign Out", "Status"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Integer.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
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
        jScrollPane1.setViewportView(staff_table);
        if (staff_table.getColumnModel().getColumnCount() > 0) {
            staff_table.getColumnModel().getColumn(0).setResizable(false);
            staff_table.getColumnModel().getColumn(1).setResizable(false);
            staff_table.getColumnModel().getColumn(2).setResizable(false);
            staff_table.getColumnModel().getColumn(3).setResizable(false);
            staff_table.getColumnModel().getColumn(4).setResizable(false);
        }

        reminder_button.setBackground(new java.awt.Color(255, 153, 0));
        reminder_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        reminder_button.setForeground(new java.awt.Color(255, 255, 255));
        reminder_button.setText("Send Reminder");
        reminder_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reminder_buttonActionPerformed(evt);
            }
        });

        logs_list.setBackground(new java.awt.Color(229, 229, 229));
        logs_list.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255)));
        jScrollPane14.setViewportView(logs_list);

        logs_button.setBackground(new java.awt.Color(255, 0, 0));
        logs_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        logs_button.setForeground(new java.awt.Color(255, 255, 255));
        logs_button.setText("Logs");

        filter_combo1.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        filter_combo1.setForeground(new java.awt.Color(45, 59, 111));
        filter_combo1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Filter By", "Date", "Staff Id" }));

        export_button.setBackground(new java.awt.Color(45, 59, 111));
        export_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        export_button.setForeground(new java.awt.Color(255, 255, 255));
        export_button.setText("Export");

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 687, Short.MAX_VALUE)
                    .addComponent(jScrollPane14, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel6Layout.createSequentialGroup()
                                .addComponent(logs_button)
                                .addGap(18, 18, 18)
                                .addComponent(filter_combo1, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(filter_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 180, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel6Layout.createSequentialGroup()
                                .addComponent(export_button)
                                .addGap(18, 18, 18)
                                .addComponent(reminder_button)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel6Layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 287, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(filter_combo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(reminder_button)
                    .addComponent(export_button))
                .addGap(27, 27, 27)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(logs_button)
                    .addComponent(filter_combo1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(jScrollPane14, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(48, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("Attendance Monitor", jPanel6);

        jPanel4.setBackground(new java.awt.Color(255, 255, 255));

        all_shifts_button.setBackground(new java.awt.Color(45, 59, 111));
        all_shifts_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        all_shifts_button.setForeground(new java.awt.Color(255, 255, 255));
        all_shifts_button.setText("All Shifts");
        all_shifts_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                all_shifts_buttonActionPerformed(evt);
            }
        });

        change_button.setBackground(new java.awt.Color(45, 59, 111));
        change_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        change_button.setForeground(new java.awt.Color(255, 255, 255));
        change_button.setText("Change Password");
        change_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                change_buttonActionPerformed(evt);
            }
        });

        jScrollPane15.setViewportView(upcoming_list);

        jLabel5.setFont(new java.awt.Font("Segoe UI", 1, 22)); // NOI18N
        jLabel5.setForeground(new java.awt.Color(45, 59, 111));
        jLabel5.setText("Upcoming Shifts");

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

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(23, 23, 23)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(edit_button)
                        .addGap(18, 18, 18)
                        .addComponent(change_button))
                    .addComponent(jLabel6)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(username_field, javax.swing.GroupLayout.PREFERRED_SIZE, 426, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(dob_chooser, javax.swing.GroupLayout.PREFERRED_SIZE, 217, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(gender_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 203, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(email_field, javax.swing.GroupLayout.PREFERRED_SIZE, 426, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(phone_field, javax.swing.GroupLayout.PREFERRED_SIZE, 426, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(unit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 216, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(role_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 204, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(18, 18, 18)
                        .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addGroup(jPanel4Layout.createSequentialGroup()
                            .addComponent(jLabel5)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(all_shifts_button))
                        .addComponent(jScrollPane15, javax.swing.GroupLayout.PREFERRED_SIZE, 626, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(50, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(23, 23, 23)
                .addComponent(jLabel6)
                .addGap(18, 18, 18)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(username_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(dob_chooser, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(gender_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(email_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(phone_field, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(role_combo)
                            .addComponent(unit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 14, Short.MAX_VALUE))
                    .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(15, 15, 15)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(edit_button)
                    .addComponent(change_button))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 30, Short.MAX_VALUE)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(all_shifts_button))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane15, javax.swing.GroupLayout.PREFERRED_SIZE, 170, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(53, 53, 53))
        );

        jTabbedPane2.addTab("Profile", jPanel4);

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
                .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancel_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancel_buttonActionPerformed
        stopBiometricCapture();
        sign_frame.setVisible(false);
    }//GEN-LAST:event_cancel_buttonActionPerformed

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

    private void swap_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_swap_buttonActionPerformed
        if (swapTableModel == null) {
            initializeSwapTable();
        }

    
        loadSwapRequests();

        swap_frame.setVisible(true);
        swap_frame.pack();
    }//GEN-LAST:event_swap_buttonActionPerformed
    
    private void browse_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browse_buttonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files", "csv");
        fileChooser.setFileFilter(filter);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedRosterFile = fileChooser.getSelectedFile();
            jLabel8.setText("📄 " + selectedRosterFile.getName());
            jLabel8.setToolTipText(selectedRosterFile.getAbsolutePath());
        }
    }//GEN-LAST:event_browse_buttonActionPerformed

    private void roster_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roster_buttonActionPerformed
        if (selectedRosterFile == null) {
            JOptionPane.showMessageDialog(this, "Please select a roster file first!",
                    "No File Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (roster_start.getDate() == null || roster_end.getDate() == null) {
            JOptionPane.showMessageDialog(this, "Please select both start and end dates!",
                    "Date Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!selectedRosterFile.exists() || !selectedRosterFile.canRead()) {
            JOptionPane.showMessageDialog(this, "Selected file does not exist or cannot be read!",
                    "File Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            uploadRoster();
            JOptionPane.showMessageDialog(this, "Roster uploaded successfully!",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            loadSwapRequestsCount(); 
            loadRecentActivity();
        } catch (Exception e) {
            e.printStackTrace(); 
            JOptionPane.showMessageDialog(this, "Error uploading roster: " + e.getMessage(),
                    "Upload Error", JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_roster_buttonActionPerformed

    private void notification_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_notification_buttonActionPerformed
        String message = JOptionPane.showInputDialog(this, "Enter notification message:");
        if (message != null && !message.trim().isEmpty()) {
            try {
                sendNotificationToUnit(message);
                JOptionPane.showMessageDialog(this, "Notification sent successfully!",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error sending notification: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        } 
    }//GEN-LAST:event_notification_buttonActionPerformed

    private void reminder_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reminder_buttonActionPerformed
        String message = JOptionPane.showInputDialog(this,
                "Enter notification message:", "Send Notification", JOptionPane.QUESTION_MESSAGE);
        if (message != null && !message.trim().isEmpty()) {
            sendReminderToUnit(message.trim());
        }
    }//GEN-LAST:event_reminder_buttonActionPerformed

    private void all_shifts_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_all_shifts_buttonActionPerformed
        showAllShiftsDialog();
    }//GEN-LAST:event_all_shifts_buttonActionPerformed

    private void change_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_change_buttonActionPerformed
        int option = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to change your password?",
                "Change Password", JOptionPane.YES_NO_OPTION);

        if (option == JOptionPane.YES_OPTION) {
            int attempts = 0;
            boolean verified = false;

            while (attempts < 3 && !verified) {
                String currentPassword = JOptionPane.showInputDialog(this,
                        "Enter your current password (Attempt " + (attempts + 1) + " of 3):",
                        "Current Password", JOptionPane.QUESTION_MESSAGE);

                if (currentPassword == null) {
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

                // Load and display preview
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
                    loadProfileData();
                }
            }
        }
    }//GEN-LAST:event_edit_buttonActionPerformed

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

    private void dowload_roster_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dowload_roster_buttonActionPerformed
        downloadRoster();
    }//GEN-LAST:event_dowload_roster_buttonActionPerformed

    private void decline_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decline_buttonActionPerformed
        processSwapRequest(false);
    }//GEN-LAST:event_decline_buttonActionPerformed

    private void approve_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_approve_buttonActionPerformed
        processSwapRequest(true);
    }//GEN-LAST:event_approve_buttonActionPerformed

    private void logout_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logout_buttonActionPerformed
        new login_frame().setVisible(true);
        this.setVisible(false);
    }//GEN-LAST:event_logout_buttonActionPerformed
      
    
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new supervisor_frame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton all_shifts_button;
    private javax.swing.JButton approve_button;
    private javax.swing.JButton browse_button;
    private javax.swing.JButton browse_image_button;
    private javax.swing.JButton cancel_button;
    private javax.swing.JButton cancel_change_button;
    private javax.swing.JButton change_button;
    private javax.swing.JButton change_password_button;
    private javax.swing.JButton decline_button;
    private javax.swing.JLabel device_status_label;
    private com.toedter.calendar.JDateChooser dob_chooser;
    private javax.swing.JButton dowload_roster_button;
    private javax.swing.JButton edit_button;
    private javax.swing.JTextField email_field;
    private javax.swing.JButton export_button;
    private javax.swing.JComboBox<String> filter_combo;
    private javax.swing.JComboBox<String> filter_combo1;
    private javax.swing.JLabel finger_instruction_label;
    private javax.swing.JLabel fingerprint_label;
    private javax.swing.JComboBox<String> gender_combo;
    private javax.swing.JLabel image_label;
    private javax.swing.JFileChooser jFileChooser1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane12;
    private javax.swing.JScrollPane jScrollPane13;
    private javax.swing.JScrollPane jScrollPane14;
    private javax.swing.JScrollPane jScrollPane15;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JButton logout_button;
    private javax.swing.JButton logs_button;
    private javax.swing.JList<String> logs_list;
    private javax.swing.JButton notification_button;
    private javax.swing.JList<String> notification_list;
    private javax.swing.JPasswordField password_1;
    private javax.swing.JPasswordField password_2;
    private javax.swing.JFrame password_frame;
    private javax.swing.JTextField phone_field;
    private javax.swing.JLabel present_staff_label;
    private javax.swing.JList<String> recent_activity_list;
    private javax.swing.JButton reminder_button;
    private javax.swing.JComboBox<String> role_combo;
    private javax.swing.JButton roster_button;
    private com.toedter.calendar.JDateChooser roster_end;
    private com.toedter.calendar.JDateChooser roster_start;
    private javax.swing.JCheckBox show_password1;
    private javax.swing.JCheckBox show_password2;
    private javax.swing.JButton sign_button;
    private javax.swing.JFrame sign_frame;
    private javax.swing.JPanel sign_panel;
    private javax.swing.JPanel sign_panel1;
    private javax.swing.JButton signout_button;
    private javax.swing.JTable staff_table;
    private javax.swing.JButton swap_button;
    private javax.swing.JFrame swap_frame;
    private javax.swing.JLabel swap_requests_label;
    private javax.swing.JTable swap_table;
    private javax.swing.JComboBox<String> unit_combo;
    private javax.swing.JList<String> upcoming_list;
    private javax.swing.JTextField username_field;
    // End of variables declaration//GEN-END:variables
}


