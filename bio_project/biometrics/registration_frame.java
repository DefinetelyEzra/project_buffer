package biometrics_exam;

import java.awt.Image;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.swing.JOptionPane;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.digitalpersona.onetouch.*;
import com.digitalpersona.onetouch.capture.*;
import com.digitalpersona.onetouch.capture.event.*;
import com.digitalpersona.onetouch.processing.*;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import java.security.SecureRandom;
import javax.imageio.ImageIO;
import javax.swing.Timer;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.Videoio; 
import org.opencv.videoio.VideoCapture;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;

public class registration_frame extends javax.swing.JFrame {

    // Variables to store user data temporarily
    private String tempFullname;
    private String tempEmail;
    private String tempPhoneNumber;
    private String tempDOB;
    private String tempGender;
    private String tempUnit;
    private String tempRole;
    private String selectedImagePath;
    private byte[] imageData;
    private boolean fingerprintCaptured = false;
    private byte[] fingerprintTemplate = null;
    private boolean rightThumbCaptured = false;
    private boolean leftThumbCaptured = false;
    private byte[] rightThumbTemplate = null;
    private byte[] leftThumbTemplate = null;
    private int rightThumbQuality = 0;
    private int leftThumbQuality = 0;
    private String currentFingerType = "Right_Thumb"; 
    private int currentSampleCount = 0;
    private static final int REQUIRED_SAMPLES = 4;
    private javax.swing.DefaultComboBoxModel<String> defaultRoleModel;
    private javax.swing.DefaultComboBoxModel<String> noneUnitRoleModel;
    private int currentUserId = -1;
    private String currentUserRole = null;
    private String cuttentUserUnit = null;
    private DPFPCapture capturer;
    private DPFPTemplate template;
    private DPFPFeatureSet featureSet;
    private DPFPEnrollment enrollment;
    private BufferedImage fingerprintImage;
    private boolean isDeviceConnected = false;
    private VideoCapture webcam;
    private Mat currentFrame;
    private Timer webcamTimer;
    private javax.swing.JFrame webcamFrame;
    private javax.swing.JLabel webcamDisplay;
    private javax.swing.JButton captureImageButton;
    private javax.swing.JButton closeWebcamButton;
    private BufferedImage capturedWebcamImage;
    private boolean webcamActive = false;
    private static final String SENDER_EMAIL = "ezraagun@gmail.com";
    private static final String SENDER_PASSWORD = "nwhotxkkqbsbwvzy"; 

    static {
        File file = new File("C:\\Users\\ezraa\\OneDrive\\Documents\\300 Lvl\\semester 2\\OOP\\open_cv\\opencv_java4110.dll");
        System.load(file.getAbsolutePath());
        //System.loadLibrary("C:\\Program Files\\DigitalPersona\\Bin\\Java");
    }

    public registration_frame() {
        initComponents();
        setupComboBoxes();
        initializeFingerprint();
    }

    private void initializeWebcamComponents() {
        // Create webcam frame
        webcamFrame = new javax.swing.JFrame("Webcam Capture");
        webcamFrame.setDefaultCloseOperation(javax.swing.JFrame.DO_NOTHING_ON_CLOSE);
        webcamFrame.setSize(800, 650);
        webcamFrame.setLocationRelativeTo(this);
        webcamFrame.setResizable(false);

        // Create main panel
        javax.swing.JPanel mainPanel = new javax.swing.JPanel();
        mainPanel.setLayout(new java.awt.BorderLayout());
        mainPanel.setBackground(java.awt.Color.WHITE);

        // Create webcam display label
        webcamDisplay = new javax.swing.JLabel();
        webcamDisplay.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        webcamDisplay.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
        webcamDisplay.setPreferredSize(new java.awt.Dimension(640, 480));
        webcamDisplay.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.BLACK, 2));
        webcamDisplay.setText("Initializing webcam...");

        // Create button panel
        javax.swing.JPanel buttonPanel = new javax.swing.JPanel();
        buttonPanel.setLayout(new java.awt.FlowLayout());
        buttonPanel.setBackground(java.awt.Color.WHITE);

        // Create capture button
        captureImageButton = new javax.swing.JButton("Capture Image");
        captureImageButton.setFont(new java.awt.Font("Segoe UI", 1, 14));
        captureImageButton.setBackground(new java.awt.Color(0, 123, 255));
        captureImageButton.setForeground(java.awt.Color.WHITE);
        captureImageButton.setPreferredSize(new java.awt.Dimension(150, 40));
        captureImageButton.setEnabled(false);

        // Create close button
        closeWebcamButton = new javax.swing.JButton("Close Webcam");
        closeWebcamButton.setFont(new java.awt.Font("Segoe UI", 1, 14));
        closeWebcamButton.setBackground(new java.awt.Color(220, 53, 69));
        closeWebcamButton.setForeground(java.awt.Color.WHITE);
        closeWebcamButton.setPreferredSize(new java.awt.Dimension(150, 40));

        // Add action listeners
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

        // Add window closing listener
        webcamFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeWebcam();
            }
        });

        // Add components to panels
        buttonPanel.add(captureImageButton);
        buttonPanel.add(closeWebcamButton);

        // Add title label
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

            // Initialize webcam
            webcam = new VideoCapture(0); 

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

            // Create timer to update webcam feed
            webcamTimer = new Timer(33, new ActionListener() { // ~30 FPS
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
                    // Convert Mat to BufferedImage
                    BufferedImage image = matToBufferedImage(currentFrame);
                    if (image != null) {
                        // Scale image to fit display
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
            // Convert Mat to byte array
            MatOfByte matOfByte = new MatOfByte();
            Imgcodecs.imencode(".jpg", mat, matOfByte);
            byte[] byteArray = matOfByte.toArray();

            // Convert byte array to BufferedImage
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
                // Convert current frame to BufferedImage
                capturedWebcamImage = matToBufferedImage(currentFrame);

                if (capturedWebcamImage != null) {
                    // Pause webcam feed
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
        // Create dialog
        javax.swing.JDialog imageDialog = new javax.swing.JDialog(webcamFrame, "Captured Image", true);
        imageDialog.setSize(500, 400);
        imageDialog.setLocationRelativeTo(webcamFrame);
        imageDialog.setDefaultCloseOperation(javax.swing.JDialog.DO_NOTHING_ON_CLOSE);

        // Create main panel
        javax.swing.JPanel dialogPanel = new javax.swing.JPanel(new java.awt.BorderLayout());
        dialogPanel.setBackground(java.awt.Color.WHITE);

        // Create image label
        javax.swing.JLabel imageLabel = new javax.swing.JLabel();
        imageLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        // Scale captured image for preview
        Image scaledPreview = capturedWebcamImage.getScaledInstance(300, 240, Image.SCALE_SMOOTH);
        imageLabel.setIcon(new ImageIcon(scaledPreview));
        imageLabel.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.BLACK, 1));

        // Create button panel
        javax.swing.JPanel buttonPanel = new javax.swing.JPanel(new java.awt.FlowLayout());
        buttonPanel.setBackground(java.awt.Color.WHITE);

        // Create buttons
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
                // Resume webcam feed
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

        // Add window closing listener
        imageDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeWebcam();
                imageDialog.dispose();
            }
        });

        // Add components
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
                // Scale image to fit the image_label
                int labelWidth = Math.max(image_label.getWidth(), 150);
                int labelHeight = Math.max(image_label.getHeight(), 150);

                Image scaledImage = capturedWebcamImage.getScaledInstance(labelWidth, labelHeight, Image.SCALE_SMOOTH);
                ImageIcon icon = new ImageIcon(scaledImage);

                // Set the image on the label
                image_label.setText("");
                image_label.setIcon(icon);
                image_label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                image_label.setVerticalAlignment(javax.swing.SwingConstants.CENTER);

                // Convert image to byte array for database storage
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

            // Stop timer
            if (webcamTimer != null && webcamTimer.isRunning()) {
                webcamTimer.stop();
            }

            // Release webcam
            if (webcam != null && webcam.isOpened()) {
                webcam.release();
            }

            // Hide webcam frame
            if (webcamFrame != null) {
                webcamFrame.setVisible(false);
            }

            // Clear references
            currentFrame = null;
            capturedWebcamImage = null;

        } catch (Exception e) {
            System.err.println("Error closing webcam: " + e.getMessage());
        }
    }

    private void initializeFingerprint() {
        try {
            capturer = DPFPGlobal.getCaptureFactory().createCapture();

            enrollment = DPFPGlobal.getEnrollmentFactory().createEnrollment();

            capturer.addDataListener(new DPFPDataAdapter() {
                @Override
                public void dataAcquired(DPFPDataEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            fingerprintDataAcquired(event);
                        } catch (Exception e) {
                            updateStatus("Error processing fingerprint data: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                }
            });

            capturer.addReaderStatusListener(new DPFPReaderStatusAdapter() {
                @Override
                public void readerConnected(DPFPReaderStatusEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        updateDeviceStatus(true);
                        updateStatus("Fingerprint scanner connected successfully.");
                    });
                }

                @Override
                public void readerDisconnected(DPFPReaderStatusEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        updateDeviceStatus(false);
                        updateStatus("Fingerprint scanner disconnected.");
                    });
                }
            });

            capturer.addSensorListener(new DPFPSensorAdapter() {
                @Override
                public void fingerTouched(DPFPSensorEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("Finger detected. Hold still while scanning...");
                    });
                }

                @Override
                public void fingerGone(DPFPSensorEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("Finger removed. Processing scan...");
                    });
                }
            });

            capturer.addErrorListener(new DPFPErrorAdapter() {
                public void errorOccurred(DPFPErrorEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        String errorMsg = "Scanner error: " + event.getError();
                        updateStatus(errorMsg);
                        updateDeviceStatus(false);
                    });
                }
            });

            updateDeviceStatus(false);
            updateStatus("Checking for fingerprint scanner...");

            SwingUtilities.invokeLater(() -> {
                checkDeviceConnectionProperly();
            });

        } catch (Exception e) {
            String errorMsg = "Failed to initialize fingerprint scanner: " + e.getMessage();
            System.err.println(errorMsg);
            updateDeviceStatus(false);
            updateStatus("Fingerprint system initialization failed.");
        }
    }

    private void checkDeviceConnectionProperly() {
        try {
            if (capturer != null) {
                capturer.startCapture();
                Thread.sleep(100);
                capturer.stopCapture();

                if (!isDeviceConnected) {
                    updateDeviceStatus(false);
                    updateStatus("No fingerprint scanner detected. Please connect your USB device.");
                }
            } else {
                updateDeviceStatus(false);
                updateStatus("Capture system not initialized.");
            }
        } catch (RuntimeException e) {
            updateDeviceStatus(false);
            updateStatus("No fingerprint scanner detected. Please connect your USB device.");
            System.err.println("Device connection check failed: " + e.getMessage());
        } catch (Exception e) {
            updateDeviceStatus(false);
            updateStatus("Error checking device connection: " + e.getMessage());
            System.err.println("Device connection check failed: " + e.getMessage());
        }
    }

    // Method to validate fingerprint data before saving
    private boolean validateFingerprintData() {
        if (!rightThumbCaptured || !leftThumbCaptured) {
            String missing = "";
            if (!rightThumbCaptured && !leftThumbCaptured) {
                missing = "both Right Thumb and Left Thumb";
            } else if (!rightThumbCaptured) {
                missing = "Right Thumb";
            } else {
                missing = "Left Thumb";
            }

            JOptionPane.showMessageDialog(this,
                    "Please capture and confirm " + missing + " before proceeding.",
                    "Fingerprints Required", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        if (rightThumbTemplate == null || rightThumbTemplate.length == 0
                || leftThumbTemplate == null || leftThumbTemplate.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "Invalid fingerprint data. Please capture fingerprints again.",
                    "Invalid Fingerprints", JOptionPane.ERROR_MESSAGE);
            resetFingerprintCapture();
            return false;
        }

        if (rightThumbQuality < 80 || leftThumbQuality < 80) {
            JOptionPane.showMessageDialog(this,
                    "Fingerprint quality is below minimum (80%). Please recapture fingerprints.",
                    "Poor Quality", JOptionPane.ERROR_MESSAGE);
            resetFingerprintCapture();
            return false;
        }

        return true;
    }

    // Method to update device status display
    private void updateDeviceStatus(boolean connected) {
        isDeviceConnected = connected;
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (device_status_label != null) {
                if (connected) {
                    device_status_label.setText("Device: Connected ✓");
                    device_status_label.setForeground(java.awt.Color.GREEN);
                } else {
                    device_status_label.setText("Device: Disconnected ✗");
                    device_status_label.setForeground(java.awt.Color.RED);
                }
            }

            // Enable/disable fingerprint buttons based on connection status
            if (confirm_fingerprint != null) {
                confirm_fingerprint.setEnabled(connected && template != null);
            }
        });
    }

    // Method to update status messages
    private void updateStatus(String message) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            System.out.println("Fingerprint Status: " + message);
        });
    }

    // Method called when fingerprint data is acquired
    private void fingerprintDataAcquired(DPFPDataEvent event) {
        try {
            // Get the fingerprint sample
            DPFPSample sample = event.getSample();

            // Convert to image for display
            fingerprintImage = convertSampleToImage(sample);
            displayFingerprintImage(fingerprintImage);

            // Process the sample to create features
            DPFPFeatureExtraction featureExtractor = DPFPGlobal.getFeatureExtractionFactory().createFeatureExtraction();

            try {
                featureSet = featureExtractor.createFeatureSet(sample, DPFPDataPurpose.DATA_PURPOSE_ENROLLMENT);

                if (featureSet != null) {
                    // Automatically add features to enrollment on each sample
                    enrollment.addFeatures(featureSet);
                    currentSampleCount++;

                    // Calculate quality score
                    int qualityScore = calculateQualityScore(sample);

                    // Store the quality score for later use
                    if ("Right_Thumb".equals(currentFingerType)) {
                        rightThumbQuality = qualityScore;
                    } else {
                        leftThumbQuality = qualityScore;
                    }

                    // Update status with sample count
                    updateStatus(currentFingerType + " sample " + currentSampleCount + "/" + REQUIRED_SAMPLES
                            + " captured. Quality: " + qualityScore + "%");

                    // Enable confirm button only when we have enough samples
                    if (currentSampleCount >= REQUIRED_SAMPLES) {
                        confirm_fingerprint.setEnabled(true);
                        updateStatus(currentFingerType + " ready for confirmation (" + currentSampleCount
                                + " samples collected). Click 'Confirm' to proceed.");
                    } else {
                        confirm_fingerprint.setEnabled(false);
                        int remaining = REQUIRED_SAMPLES - currentSampleCount;
                        updateStatus("Need " + remaining + " more sample(s) for " + currentFingerType
                                + ". Place finger on scanner again.");
                    }
                }

            } catch (DPFPImageQualityException ex) {
                featureSet = null;
                updateStatus("Poor image quality for " + currentFingerType + ": " + ex.getMessage()
                        + ". Sample not counted. Try again.");
                confirm_fingerprint.setEnabled(false);
            }

        } catch (Exception e) {
            updateStatus("Error processing " + currentFingerType + ": " + e.getMessage());
            e.printStackTrace();
            confirm_fingerprint.setEnabled(false);
        }
    }
    
    private int calculateQualityScore(DPFPSample sample) {
        try {
            if (sample == null) {
                return 0;
            }

            byte[] sampleData = sample.serialize();
            int dataSize = sampleData.length;

            if (dataSize > 50000) {
                return 95;
            } else if (dataSize > 40000) {
                return 90;
            } else if (dataSize > 30000) {
                return 85;
            } else if (dataSize > 20000) {
                return 80;
            } else if (dataSize > 15000) {
                return 75;
            } else if (dataSize > 10000) {
                return 70;
            } else {
                return 65;
            }

        } catch (Exception e) {
            System.err.println("Error calculating quality score: " + e.getMessage());
            return 70; 
        }
    }

    private BufferedImage convertSampleToImage(DPFPSample sample) {
        if (sample == null) {
            return createDefaultPlaceholder();
        }

        try {
            Image rawImage = DPFPGlobal.getSampleConversionFactory().createImage(sample);

            if (rawImage == null) {
                return createDefaultPlaceholder();
            }

            int targetSize = 200;
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
            return createDefaultPlaceholder();
        }
    }

    // Create a default placeholder image
    private BufferedImage createDefaultPlaceholder() {
        try {
            BufferedImage placeholder = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = placeholder.createGraphics();

            g2d.setColor(java.awt.Color.LIGHT_GRAY);
            g2d.fillRect(0, 0, 200, 200);

            g2d.setColor(java.awt.Color.DARK_GRAY);
            g2d.drawRect(0, 0, 199, 199);

            g2d.setColor(java.awt.Color.BLACK);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
            g2d.drawString("Place finger", 60, 90);
            g2d.drawString("on scanner", 65, 110);

            g2d.dispose();
            return placeholder;

        } catch (Exception e) {
            System.err.println("Failed to create placeholder image: " + e.getMessage());
            return null;
        }
    }

    private BufferedImage createSuccessPlaceholder() {
        try {
            BufferedImage placeholder = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = placeholder.createGraphics();

            g2d.setColor(new java.awt.Color(230, 255, 230));
            g2d.fillRect(0, 0, 200, 200);

            g2d.setColor(java.awt.Color.GREEN);
            g2d.setStroke(new java.awt.BasicStroke(2));
            g2d.drawRect(2, 2, 196, 196);

            g2d.setColor(java.awt.Color.DARK_GRAY);

            for (int i = 0; i < 5; i++) {
                int offset = i * 15;
                g2d.drawOval(50 + offset, 50 + offset, 100 - (offset * 2), 100 - (offset * 2));
            }

            g2d.setColor(java.awt.Color.BLACK);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            g2d.drawString("Fingerprint", 65, 170);
            g2d.drawString("Captured ✓", 68, 185);

            g2d.dispose();
            return placeholder;

        } catch (Exception e) {
            System.err.println("Failed to create success placeholder: " + e.getMessage());
            return createDefaultPlaceholder();
        }
    }

    private void displayFingerprintImage(BufferedImage image) {
        if (image != null && fingerprint_scanner != null) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    // Scale image to fit the label
                    int labelWidth = Math.max(fingerprint_scanner.getWidth(), 150);
                    int labelHeight = Math.max(fingerprint_scanner.getHeight(), 150);

                    java.awt.Image scaledImage = image.getScaledInstance(
                            labelWidth, labelHeight, java.awt.Image.SCALE_SMOOTH);

                    javax.swing.ImageIcon icon = new javax.swing.ImageIcon(scaledImage);
                    fingerprint_scanner.setIcon(icon);
                    fingerprint_scanner.setText("");

                } catch (Exception e) {
                    System.err.println("Error displaying fingerprint: " + e.getMessage());
                }
            });
        }
    }

    private void startFingerprintCapture() {
        if (!validateDeviceConnectionWithTest()) {
            return;
        }

        try {
            featureSet = null;
            currentSampleCount = 0; // Reset sample count
            confirm_fingerprint.setEnabled(false);
            confirm_fingerprint.setText("Confirm " + currentFingerType);

            // Start capture
            capturer.startCapture();
            updateStatus("Ready to capture " + currentFingerType + ". Need " + REQUIRED_SAMPLES
                    + " samples. Place finger on scanner...");
            updateFingerInstructions();

            // Show default placeholder
            if (fingerprint_scanner != null) {
                displayFingerprintImage(createDefaultPlaceholder());
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Failed to start capture: " + e.getMessage(),
                    "Capture Error", JOptionPane.ERROR_MESSAGE);
            updateDeviceStatus(false);
        }
    }
    
    private boolean validateDeviceConnection() {
        if (!isDeviceConnected) {
            JOptionPane.showMessageDialog(this,
                    "Fingerprint scanner not connected! Please connect your USB device and restart the application.",
                    "Device Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    private boolean validateDeviceConnectionWithTest() {
        if (!isDeviceConnected) {
            JOptionPane.showMessageDialog(this,
                    "Fingerprint scanner not connected! Please connect your USB device and restart the application.",
                    "Device Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        try {
            capturer.startCapture();
            capturer.stopCapture();
            return true;
        } catch (RuntimeException e) {
            updateDeviceStatus(false);
            JOptionPane.showMessageDialog(this,
                    "USB fingerprint scanner disconnected! Please reconnect your device.",
                    "Device Disconnected", JOptionPane.ERROR_MESSAGE);
            return false;
        } catch (Exception e) {
            updateDeviceStatus(false);
            JOptionPane.showMessageDialog(this,
                    "Error checking device connection: " + e.getMessage(),
                    "Device Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void stopFingerprintCapture() {
        try {
            if (capturer != null) {
                capturer.stopCapture();
            }
            updateStatus("Capture stopped.");
        } catch (Exception e) {
            System.err.println("Error stopping capture: " + e.getMessage());
        }
    }

    // Method to reset current finger capture
    private void resetCurrentFingerCapture() {
        try {
            stopFingerprintCapture();

            // Reset current finger data
            template = null;
            featureSet = null;
            fingerprintImage = null;
            currentSampleCount = 0; 

            // Clear enrollment for current finger
            if (enrollment != null) {
                enrollment.clear();
                enrollment = DPFPGlobal.getEnrollmentFactory().createEnrollment();
            }

            if (fingerprint_scanner != null) {
                displayFingerprintImage(createDefaultPlaceholder());
            }

            if (confirm_fingerprint != null) {
                confirm_fingerprint.setText("Confirm " + currentFingerType);
                confirm_fingerprint.setEnabled(false);
            }

            updateStatus("Ready to capture " + currentFingerType + ". Need " + REQUIRED_SAMPLES
                    + " samples. Place finger on scanner...");

        } catch (Exception e) {
            System.err.println("Error resetting current finger capture: " + e.getMessage());
            updateStatus("Error resetting fingerprint system.");
        }
    }
    
    private void resetFingerprintCapture() {
        try {
            stopFingerprintCapture();

            // Reset all fingerprint data
            fingerprintCaptured = false;
            rightThumbCaptured = false;
            leftThumbCaptured = false;
            rightThumbTemplate = null;
            leftThumbTemplate = null;
            rightThumbQuality = 0;
            leftThumbQuality = 0;
            currentFingerType = "Right_Thumb";
            currentSampleCount = 0; // Reset sample count

            template = null;
            featureSet = null;
            fingerprintImage = null;

            // Reset enrollment
            if (enrollment != null) {
                enrollment.clear();
                enrollment = DPFPGlobal.getEnrollmentFactory().createEnrollment();
            }

            // Reset UI components
            if (fingerprint_scanner != null) {
                displayFingerprintImage(createDefaultPlaceholder());
            }

            if (confirm_fingerprint != null) {
                confirm_fingerprint.setText("Confirm Right_Thumb");
                confirm_fingerprint.setEnabled(false);
            }

            if (capture_button != null) {
                capture_button.setEnabled(true);
            }

            updateFingerInstructions();
            updateStatus("Fingerprint data cleared. Ready to capture Right Thumb. Need " + REQUIRED_SAMPLES + " samples.");

        } catch (Exception e) {
            System.err.println("Error resetting fingerprint capture: " + e.getMessage());
            updateStatus("Error resetting fingerprint system.");
        }
    }
    
    private void updateFingerInstructions() {
        if (finger_instruction_label != null) {
            finger_instruction_label.setText("Please place your " + currentFingerType + " on the scanner");
        }

        // Update button text
        if (confirm_fingerprint != null) {
            confirm_fingerprint.setText("Confirm " + currentFingerType);
        }

        updateStatus("Ready to capture " + currentFingerType);
    }

    private void periodicDeviceCheck() {
        try {
            capturer.startCapture();
            capturer.stopCapture();

            if (!isDeviceConnected) {
                updateDeviceStatus(true);
                updateStatus("USB fingerprint scanner reconnected.");
            }
        } catch (RuntimeException e) {
            if (isDeviceConnected) {
                updateDeviceStatus(false);
                updateStatus("USB fingerprint scanner disconnected.");
            }
        } catch (Exception e) {
            if (isDeviceConnected) {
                updateDeviceStatus(false);
                updateStatus("Device connection lost: " + e.getMessage());
            }
        }
    }

    private void cleanupFingerprint() {
        try {
            if (capturer != null) {
                capturer.stopCapture();
            }
        } catch (Exception e) {
            System.err.println("Error during fingerprint cleanup: " + e.getMessage());
        }
    }

    @Override
    protected void processWindowEvent(java.awt.event.WindowEvent e) {
        if (e.getID() == java.awt.event.WindowEvent.WINDOW_CLOSING) {
            cleanupFingerprint();
            closeWebcam();
        }
        super.processWindowEvent(e);
    }

    private void setupComboBoxes() {
        gender_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            "Select Gender", "Male", "Female"
        }));

        unit_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{
            "Select Unit", "Security", "Horticulture", "Facility", "Cafeteria", "Maintenance", "None"
        }));

        defaultRoleModel = new javax.swing.DefaultComboBoxModel<>(new String[]{
            "Select Role", "Supervisor", "Staff"
        });

        noneUnitRoleModel = new javax.swing.DefaultComboBoxModel<>(new String[]{
            "Select Role", "IT_Admin", "IT_Head", "Director"
        });

        role_combo.setModel(defaultRoleModel);

        unit_combo.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unitComboActionPerformed(evt);
            }
        });
    }

    private void unitComboActionPerformed(java.awt.event.ActionEvent evt) {
        String selectedUnit = (String) unit_combo.getSelectedItem();
        if ("None".equals(selectedUnit)) {
            role_combo.setModel(noneUnitRoleModel);
        } else {
            role_combo.setModel(defaultRoleModel);
        }
        role_combo.setSelectedIndex(0); // Reset to placeholder
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

    private String generateRandomPassword() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            int index = random.nextInt(characters.length());
            password.append(characters.charAt(index));
        }

        return password.toString();
    }
    
    private void updateGenerateButtonState() {
        if (generate_button != null) {
            generate_button.setEnabled(fingerprintCaptured && rightThumbCaptured && leftThumbCaptured);
        }
    }

    private boolean isValidEmail(String email) {
        String regex = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$";
        return email.matches(regex);
    }

    private boolean validateForm() {
        if (username_field.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            username_field.requestFocus();
            return false;
        }

        if (dob_chooser.getDate() == null) {
            JOptionPane.showMessageDialog(this, "Please select a date of birth!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            dob_chooser.requestFocus();
            return false;
        }

        if (email_field.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Email cannot be empty!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            email_field.requestFocus();
            return false;
        } else if (!isValidEmail(email_field.getText().trim())) {
            JOptionPane.showMessageDialog(this, "Please enter a valid email address!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            email_field.requestFocus();
            return false;
        }

        if (phone_field.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Phone number cannot be empty!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            phone_field.requestFocus();
            return false;
        } else if (phone_field.getText().trim().length() < 10) {
            JOptionPane.showMessageDialog(this, "Phone number must be at least 10 digits!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            phone_field.requestFocus();
            return false;
        }

        if (gender_combo.getSelectedIndex() == 0) {
            JOptionPane.showMessageDialog(this, "Please select a gender!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            gender_combo.requestFocus();
            return false;
        }

        if (unit_combo.getSelectedIndex() == 0) {
            JOptionPane.showMessageDialog(this, "Please select a unit!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            unit_combo.requestFocus();
            return false;
        }

        if (role_combo.getSelectedIndex() == 0) {
            JOptionPane.showMessageDialog(this, "Please select a role!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            role_combo.requestFocus();
            return false;
        }

        if (imageData == null) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "No profile image selected. Continue without image?",
                    "Image Missing", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        return true;
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

    private boolean sendCredentialsEmail(String fullName, String recipientEmail, String password) {
        boolean emailSent = false;

        try {
            String subject = "PAMS Registration - Your Login Credentials";
            String messageBody = createCredentialsEmailBody(fullName, tempEmail, password);
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
            message.setText(messageBody);

            Transport.send(message);
            emailSent = true;
            System.out.println("Credentials email sent successfully via STARTTLS (port 587) to: " + recipientEmail);

        } catch (MessagingException e) {
            System.err.println("Failed to send credentials email via STARTTLS (port 587): " + e.getMessage());

            // Fallback to SSL (port 465)
            try {
                String subject = "PAMS Registration - Your Login Credentials";
                String messageBody = createCredentialsEmailBody(fullName, tempEmail, password);

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
                sslMessage.setText(messageBody);

                Transport.send(sslMessage);
                emailSent = true;
                System.out.println("Credentials email sent successfully via SSL (port 465) to: " + recipientEmail);

            } catch (MessagingException sslException) {
                System.err.println("Failed to send credentials email via SSL (port 465): " + sslException.getMessage());
                System.err.println("Email delivery failed for user: " + fullName + " (" + recipientEmail + ")");
            }
        } catch (Exception e) {
            System.err.println("Unexpected error sending credentials email: " + e.getMessage());
            e.printStackTrace();
        }

        return emailSent;
    }

    private String createCredentialsEmailBody(String fullName, String email, String password) {
        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(fullName).append(",\n\n");
        body.append("Welcome to the PAU Attendance Management System (PAMS)!\n\n");
        body.append("Your registration has been completed successfully. Below are your login credentials:\n\n");
        body.append("Username/Email: ").append(email).append("\n");
        body.append("Password: ").append(password).append("\n\n");
        body.append("IMPORTANT SECURITY NOTES:\n");
        body.append("- Please change your password after your first login\n");
        body.append("- Do not share your credentials with anyone\n");
        body.append("- Keep this information secure and confidential\n\n");
        body.append("You can now log in to the system using these credentials.\n\n");
        body.append("If you have any questions or issues, please contact the IT department.\n\n");
        body.append("Best regards,\n");
        body.append("PAMS Administration Team");

        return body.toString();
    }
    
    private void saveUserData(String password) {
        Connection con = null;
        PreparedStatement userPs = null;
        PreparedStatement biometricPs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");
            con.setAutoCommit(false); 

            userPs = con.prepareStatement(
                    "INSERT INTO users (full_name, password_hash, date_of_birth, phone_number, email, gender, unit, role, photo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS);

            userPs.setString(1, tempFullname);
            userPs.setString(2, password);
            userPs.setString(3, tempDOB);
            userPs.setString(4, tempPhoneNumber);
            userPs.setString(5, tempEmail);
            userPs.setString(6, tempGender);
            userPs.setString(7, tempUnit);
            userPs.setString(8, tempRole.replace(" ", "_"));

            if (imageData != null) {
                userPs.setBytes(9, imageData);
            } else {
                userPs.setNull(9, java.sql.Types.BLOB);
            }

            int userResult = userPs.executeUpdate();

            if (userResult > 0) {
                var generatedKeys = userPs.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);

                    biometricPs = con.prepareStatement(
                            "INSERT INTO biometric_data (user_id, fingerprint_template, finger_position, quality_score) VALUES (?, ?, ?, ?)");

                    biometricPs.setInt(1, userId);
                    biometricPs.setBytes(2, rightThumbTemplate);
                    biometricPs.setString(3, "Right_Thumb");
                    biometricPs.setInt(4, rightThumbQuality);
                    int rightThumbResult = biometricPs.executeUpdate();

                    biometricPs.setInt(1, userId);
                    biometricPs.setBytes(2, leftThumbTemplate);
                    biometricPs.setString(3, "Left_Thumb");
                    biometricPs.setInt(4, leftThumbQuality);
                    int leftThumbResult = biometricPs.executeUpdate();

                    if (rightThumbResult > 0 && leftThumbResult > 0) {
                        con.commit(); 

                        boolean emailSent = sendCredentialsEmail(tempFullname, tempEmail, new String(password_1.getPassword()));

                        if (emailSent) {
                            JOptionPane.showMessageDialog(password_frame,
                                    "Registration completed successfully! Login credentials sent to " + tempEmail,
                                    "Success", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(password_frame,
                                    "Registration completed successfully, but failed to send email notification.",
                                    "Partial Success", JOptionPane.WARNING_MESSAGE);
                        }

                        password_frame.setVisible(false);
                        clearForm();
                        resetFingerprintCapture();
                    } else {
                        con.rollback();
                        JOptionPane.showMessageDialog(password_frame, "Failed to save biometric data.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                con.rollback();
                JOptionPane.showMessageDialog(password_frame, "Failed to register user.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            try {
                if (con != null) {
                    con.rollback();
                }
            } catch (Exception rollbackEx) {
                System.err.println("Rollback failed: " + rollbackEx.getMessage());
            }
            JOptionPane.showMessageDialog(password_frame, "Database Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            try {
                if (biometricPs != null) {
                    biometricPs.close();
                }
                if (userPs != null) {
                    userPs.close();
                }
                if (con != null) {
                    con.setAutoCommit(true);
                    con.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }

    private void clearForm() {
        username_field.setText("");
        email_field.setText("");
        phone_field.setText("");
        dob_chooser.setDate(null);
        gender_combo.setSelectedIndex(0);
        unit_combo.setSelectedIndex(0);
        role_combo.setSelectedIndex(0);
        selectedImagePath = null;
        imageData = null;
        resetFingerprintCapture();
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        password_frame = new javax.swing.JFrame();
        jPanel4 = new javax.swing.JPanel();
        jLabel13 = new javax.swing.JLabel();
        finish_button2 = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        password_1 = new javax.swing.JPasswordField();
        show_password1 = new javax.swing.JCheckBox();
        show_password2 = new javax.swing.JCheckBox();
        password_2 = new javax.swing.JPasswordField();
        jLabel17 = new javax.swing.JLabel();
        jLabel18 = new javax.swing.JLabel();
        device_status_label = new javax.swing.JLabel();
        finger_instruction_label = new javax.swing.JLabel();
        generate_button = new javax.swing.JButton();
        jLabel20 = new javax.swing.JLabel();
        jPanel6 = new javax.swing.JPanel();
        fingerprint_scanner = new javax.swing.JLabel();
        jLabel19 = new javax.swing.JLabel();
        confirm_fingerprint = new javax.swing.JButton();
        reset_fingerprint = new javax.swing.JButton();
        capture_button = new javax.swing.JButton();
        jFileChooser1 = new javax.swing.JFileChooser();
        jPanel1 = new javax.swing.JPanel();
        username_field = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        finish_button = new javax.swing.JButton();
        login_button = new javax.swing.JButton();
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
        jLabel12 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        image_label = new javax.swing.JLabel();
        browse_button = new javax.swing.JButton();
        webcam_button = new javax.swing.JButton();

        jPanel4.setBackground(new java.awt.Color(255, 255, 255));
        jPanel4.setForeground(new java.awt.Color(255, 255, 255));

        jLabel13.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel13.setForeground(new java.awt.Color(45, 59, 111));
        jLabel13.setText("Type Password");

        finish_button2.setBackground(new java.awt.Color(51, 204, 0));
        finish_button2.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        finish_button2.setForeground(new java.awt.Color(255, 255, 255));
        finish_button2.setText("Confirm Registration");
        finish_button2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                finish_button2ActionPerformed(evt);
            }
        });

        jPanel5.setBackground(new java.awt.Color(45, 59, 111));

        jLabel14.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel14.setForeground(new java.awt.Color(255, 255, 255));
        jLabel14.setText("CREATE ACCOUNT");

        jLabel15.setIcon(new javax.swing.ImageIcon(getClass().getResource("/biometrics_exam/pau_icon-25x25.png"))); // NOI18N

        jLabel16.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel16.setForeground(new java.awt.Color(255, 255, 255));
        jLabel16.setText("Attendance Management System");

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel15)
                .addGap(230, 230, 230)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel16)
                    .addComponent(jLabel14))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel15, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel14)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel16)))
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

        jLabel17.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel17.setForeground(new java.awt.Color(45, 59, 111));
        jLabel17.setText("Retype Password");

        jLabel18.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel18.setForeground(new java.awt.Color(153, 153, 153));
        jLabel18.setText("Confirm your password and register biometric");

        device_status_label.setBackground(new java.awt.Color(45, 59, 111));
        device_status_label.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        device_status_label.setForeground(new java.awt.Color(45, 59, 111));
        device_status_label.setText("Device Connected?");

        finger_instruction_label.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        finger_instruction_label.setForeground(new java.awt.Color(153, 153, 153));

        generate_button.setBackground(new java.awt.Color(255, 0, 51));
        generate_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        generate_button.setForeground(new java.awt.Color(255, 255, 255));
        generate_button.setText("Generate Password");
        generate_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generate_buttonActionPerformed(evt);
            }
        });

        jLabel20.setBackground(new java.awt.Color(45, 59, 111));
        jLabel20.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel20.setForeground(new java.awt.Color(45, 59, 111));
        jLabel20.setText("or");

        jPanel6.setBackground(new java.awt.Color(255, 255, 255));

        fingerprint_scanner.setForeground(new java.awt.Color(153, 153, 153));
        fingerprint_scanner.setText("      place finger here");
        fingerprint_scanner.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel19.setBackground(new java.awt.Color(45, 59, 111));
        jLabel19.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel19.setForeground(new java.awt.Color(45, 59, 111));
        jLabel19.setText("Please place your finger on the scanner");

        confirm_fingerprint.setBackground(new java.awt.Color(51, 204, 0));
        confirm_fingerprint.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        confirm_fingerprint.setForeground(new java.awt.Color(255, 255, 255));
        confirm_fingerprint.setText("Confirm");
        confirm_fingerprint.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                confirm_fingerprintActionPerformed(evt);
            }
        });

        reset_fingerprint.setBackground(new java.awt.Color(45, 59, 111));
        reset_fingerprint.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        reset_fingerprint.setForeground(new java.awt.Color(255, 255, 255));
        reset_fingerprint.setText("Reset");
        reset_fingerprint.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reset_fingerprintActionPerformed(evt);
            }
        });

        capture_button.setBackground(new java.awt.Color(255, 0, 51));
        capture_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        capture_button.setForeground(new java.awt.Color(255, 255, 255));
        capture_button.setText("Capture Fingerprint");
        capture_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                capture_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGap(41, 41, 41)
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel6Layout.createSequentialGroup()
                                .addComponent(confirm_fingerprint)
                                .addGap(18, 18, 18)
                                .addComponent(reset_fingerprint, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel6Layout.createSequentialGroup()
                                .addGap(14, 14, 14)
                                .addComponent(capture_button))))
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGap(20, 20, 20)
                        .addComponent(jLabel19))
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addGap(63, 63, 63)
                        .addComponent(fingerprint_scanner, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(313, Short.MAX_VALUE))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(12, 12, 12)
                .addComponent(fingerprint_scanner, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel19)
                .addGap(12, 12, 12)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(confirm_fingerprint)
                    .addComponent(reset_fingerprint))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(capture_button)
                .addContainerGap(21, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(50, 50, 50)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(finger_instruction_label))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel17)
                            .addComponent(password_2, javax.swing.GroupLayout.PREFERRED_SIZE, 368, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(show_password2)
                            .addComponent(jLabel13)
                            .addComponent(show_password1)
                            .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(jLabel18)
                                .addComponent(password_1, javax.swing.GroupLayout.PREFERRED_SIZE, 360, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(finish_button2, javax.swing.GroupLayout.PREFERRED_SIZE, 214, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(generate_button, javax.swing.GroupLayout.PREFERRED_SIZE, 214, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addGap(95, 95, 95)
                                .addComponent(jLabel20)))
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addGap(82, 82, 82)
                                .addComponent(device_status_label)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(62, 62, 62)))))
                .addGap(88, 88, 88))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGap(20, 20, 20)
                        .addComponent(device_status_label)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(finger_instruction_label)
                                .addContainerGap(59, Short.MAX_VALUE))
                            .addGroup(jPanel4Layout.createSequentialGroup()
                                .addComponent(jLabel13)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(password_1, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(show_password1)
                                .addGap(18, 18, 18)
                                .addComponent(jLabel17)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(password_2, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(58, 58, 58)
                                .addComponent(finish_button2, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel20)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(generate_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(35, Short.MAX_VALUE))))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jLabel18)
                        .addGap(202, 202, 202)
                        .addComponent(show_password2)
                        .addGap(101, 101, 101))))
        );

        javax.swing.GroupLayout password_frameLayout = new javax.swing.GroupLayout(password_frame.getContentPane());
        password_frame.getContentPane().setLayout(password_frameLayout);
        password_frameLayout.setHorizontalGroup(
            password_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(password_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, 724, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        password_frameLayout.setVerticalGroup(
            password_frameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(password_frameLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));
        jPanel1.setForeground(new java.awt.Color(255, 255, 255));

        username_field.setFont(new java.awt.Font("Segoe UI", 0, 16)); // NOI18N
        username_field.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        jLabel4.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel4.setForeground(new java.awt.Color(45, 59, 111));
        jLabel4.setText("Full Name");

        finish_button.setBackground(new java.awt.Color(51, 204, 0));
        finish_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        finish_button.setForeground(new java.awt.Color(255, 255, 255));
        finish_button.setText("Finish Registration");
        finish_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                finish_buttonActionPerformed(evt);
            }
        });

        login_button.setBackground(new java.awt.Color(0, 102, 153));
        login_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        login_button.setForeground(new java.awt.Color(255, 255, 255));
        login_button.setText("Login");
        login_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                login_buttonActionPerformed(evt);
            }
        });

        jPanel2.setBackground(new java.awt.Color(45, 59, 111));

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel2.setForeground(new java.awt.Color(255, 255, 255));
        jLabel2.setText("CREATE ACCOUNT");

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
                .addGap(207, 207, 207)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addComponent(jLabel2))
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

        jLabel12.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel12.setForeground(new java.awt.Color(45, 59, 111));
        jLabel12.setText("already have account?");

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

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(105, 105, 105)
                .addComponent(jLabel12)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(64, 64, 64)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
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
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(login_button, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(finish_button, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 214, Short.MAX_VALUE)
                            .addComponent(unit_combo, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel10, javax.swing.GroupLayout.Alignment.LEADING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel11)
                            .addComponent(role_combo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 56, Short.MAX_VALUE)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(39, 39, 39))
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
                    .addComponent(unit_combo, javax.swing.GroupLayout.DEFAULT_SIZE, 33, Short.MAX_VALUE)
                    .addComponent(role_combo, javax.swing.GroupLayout.DEFAULT_SIZE, 33, Short.MAX_VALUE))
                .addGap(31, 31, 31)
                .addComponent(finish_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel12)
                .addGap(9, 9, 9)
                .addComponent(login_button, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(22, 22, 22))
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

    private void login_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_login_buttonActionPerformed
        new login_frame().setVisible(true); 
        this.setVisible(false);
    }//GEN-LAST:event_login_buttonActionPerformed

    private void finish_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_finish_buttonActionPerformed
        if (validateForm()) {
            tempFullname = username_field.getText().trim();
            tempEmail = email_field.getText().trim();
            tempPhoneNumber = phone_field.getText().trim();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            tempDOB = sdf.format(dob_chooser.getDate());

            tempGender = gender_combo.getSelectedItem().toString();

            tempUnit = unit_combo.getSelectedItem().toString();

            tempRole = role_combo.getSelectedItem().toString();

            password_frame.setLocationRelativeTo(this);
            password_frame.setVisible(true);
            password_frame.pack();
            password_frame.toFront();
            password_1.setText("");
            password_2.setText("");
        }
    }//GEN-LAST:event_finish_buttonActionPerformed

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

    private void finish_button2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_finish_button2ActionPerformed
        if (!validateFingerprintData()) {
            return;
        }

        String password = new String(password_1.getPassword());
        String confirmPassword = new String(password_2.getPassword());

        // Validate passwords
        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(password_frame, "Password cannot be empty!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            password_1.requestFocus();
            return;
        }

        if (password.length() < 8) {
            JOptionPane.showMessageDialog(password_frame, "Password must be at least 8 characters long!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            password_1.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(password_frame, "Passwords do not match!", "Validation Error", JOptionPane.ERROR_MESSAGE);
            password_2.requestFocus();
            return;
        }

        // Hash the password
        String hashedPassword = hashPassword(password);
        if (hashedPassword != null) {
            saveUserData(hashedPassword);
        }
    }//GEN-LAST:event_finish_button2ActionPerformed

    
    private void show_password2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_show_password2ActionPerformed
        if (show_password2.isSelected()) {
            password_2.setEchoChar((char) 0);
        } else {
            password_2.setEchoChar('*');
        }
    }//GEN-LAST:event_show_password2ActionPerformed

    private void show_password1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_show_password1ActionPerformed
        if (show_password1.isSelected()) {
            password_1.setEchoChar((char) 0);
        } else {
            password_1.setEchoChar('*');
        }    
    }//GEN-LAST:event_show_password1ActionPerformed

    private void unit_comboActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unit_comboActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_unit_comboActionPerformed

    private void generate_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generate_buttonActionPerformed
        if (!validateFingerprintData()) {
            JOptionPane.showMessageDialog(password_frame,
                    "Please capture and confirm both fingerprints before generating password!",
                    "Fingerprints Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Generate random password
        String generatedPassword = generateRandomPassword();

        password_1.setText(generatedPassword);
        password_2.setText(generatedPassword);
        
        password_1.setEnabled(false);
        password_2.setEnabled(false);
        show_password1.setEnabled(false);
        show_password2.setEnabled(false);

        int choice = JOptionPane.showConfirmDialog(password_frame,
                "\nThe generated password will be sent to your email address.\nProceed with registration?",
                "Password Generated",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            // Hash the password and save user data
            String hashedPassword = hashPassword(generatedPassword);
            if (hashedPassword != null) {
                saveUserData(hashedPassword);
            }
        } else {
            password_1.setText("");
            password_2.setText("");
        }
    }//GEN-LAST:event_generate_buttonActionPerformed

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

    private void reset_fingerprintActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reset_fingerprintActionPerformed
        try {
            stopFingerprintCapture();

            // Reset all fingerprint data
            template = null;
            featureSet = null;
            fingerprintTemplate = null;
            fingerprintCaptured = false;
            fingerprintImage = null;

            // Clear the display
            if (fingerprint_scanner != null) {
                fingerprint_scanner.setIcon(null);
                fingerprint_scanner.setText("Place finger here");
            }

            confirm_fingerprint.setText("Confirm");
            confirm_fingerprint.setEnabled(false);

            updateStatus("Fingerprint data cleared. Ready to capture new fingerprint.");

        } catch (Exception e) {
            System.err.println("Error canceling fingerprint: " + e.getMessage());
        }
    }//GEN-LAST:event_reset_fingerprintActionPerformed

    private void confirm_fingerprintActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_confirm_fingerprintActionPerformed
        if (!validateDeviceConnection()) {
            return;
        }

        if (currentSampleCount < REQUIRED_SAMPLES) {
            JOptionPane.showMessageDialog(password_frame,
                "Need exactly " + REQUIRED_SAMPLES + " samples for " + currentFingerType
                + ". Currently have " + currentSampleCount + " samples. Please capture more samples.",
                "Insufficient Samples", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            switch (enrollment.getTemplateStatus()) {
                case TEMPLATE_STATUS_READY:
                template = enrollment.getTemplate();

                if (template != null) {
                    byte[] templateData = template.serialize();
                    int qualityScore = ("Right_Thumb".equals(currentFingerType)) ? rightThumbQuality : leftThumbQuality;

                    if (qualityScore < 80) {
                        JOptionPane.showMessageDialog(this,
                            currentFingerType + " quality score (" + qualityScore + "%) is below minimum (80%). Please try again.",
                            "Poor Quality", JOptionPane.WARNING_MESSAGE);
                        resetCurrentFingerCapture();
                        return;
                    }

                    if ("Right_Thumb".equals(currentFingerType)) {
                        rightThumbTemplate = templateData;
                        rightThumbCaptured = true;
                        updateStatus("Right Thumb confirmed with " + currentSampleCount + " samples (Quality: " + qualityScore + "%)");

                        currentFingerType = "Left_Thumb";
                        currentSampleCount = 0; 
                        updateFingerInstructions();

                        if (!leftThumbCaptured) {
                            JOptionPane.showMessageDialog(password_frame,
                                "Right Thumb captured successfully! Now please capture your Left Thumb ("
                                + REQUIRED_SAMPLES + " samples required).",
                                "Next Finger", JOptionPane.INFORMATION_MESSAGE);
                            resetCurrentFingerCapture();
                        }
                    } else {
                        leftThumbTemplate = templateData;
                        leftThumbCaptured = true;
                        updateStatus("Left Thumb confirmed with " + currentSampleCount + " samples (Quality: " + qualityScore + "%)");

                        JOptionPane.showMessageDialog(this,
                            "Both fingerprints captured successfully!",
                            "Success", JOptionPane.INFORMATION_MESSAGE);

                        stopFingerprintCapture();
                        confirm_fingerprint.setText("Both Confirmed");
                        confirm_fingerprint.setEnabled(false);
                        capture_button.setEnabled(false);

                        displayFingerprintImage(createSuccessPlaceholder());
                        fingerprintCaptured = true; 
                    }
                } else {
                    throw new Exception("Failed to create template from enrollment");
                }
                break;

                case TEMPLATE_STATUS_FAILED:
                enrollment.clear();
                featureSet = null;
                currentSampleCount = 0; 
                JOptionPane.showMessageDialog(this,
                    currentFingerType + " enrollment failed. Please try again.",
                    "Enrollment Failed", JOptionPane.WARNING_MESSAGE);
                resetCurrentFingerCapture();
                break;

                default:
                JOptionPane.showMessageDialog(password_frame,
                    "Template not ready. Please ensure you have captured " + REQUIRED_SAMPLES + " samples.",
                    "Template Not Ready", JOptionPane.WARNING_MESSAGE);
                break;
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error confirming " + currentFingerType + ": " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            resetCurrentFingerCapture();
        }
        if (rightThumbCaptured && leftThumbCaptured) {
            updateGenerateButtonState();
        }
    }//GEN-LAST:event_confirm_fingerprintActionPerformed

    private void capture_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_capture_buttonActionPerformed
        checkDeviceConnectionProperly();

        if (validateDeviceConnection()) {
            startFingerprintCapture();
        }
    }//GEN-LAST:event_capture_buttonActionPerformed

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new registration_frame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browse_button;
    private javax.swing.JButton capture_button;
    private javax.swing.JButton confirm_fingerprint;
    private javax.swing.JLabel device_status_label;
    private com.toedter.calendar.JDateChooser dob_chooser;
    private javax.swing.JTextField email_field;
    private javax.swing.JLabel finger_instruction_label;
    private javax.swing.JLabel fingerprint_scanner;
    private javax.swing.JButton finish_button;
    private javax.swing.JButton finish_button2;
    private javax.swing.JComboBox<String> gender_combo;
    private javax.swing.JButton generate_button;
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
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JButton login_button;
    private javax.swing.JPasswordField password_1;
    private javax.swing.JPasswordField password_2;
    private javax.swing.JFrame password_frame;
    private javax.swing.JTextField phone_field;
    private javax.swing.JButton reset_fingerprint;
    private javax.swing.JComboBox<String> role_combo;
    private javax.swing.JCheckBox show_password1;
    private javax.swing.JCheckBox show_password2;
    private javax.swing.JComboBox<String> unit_combo;
    private javax.swing.JTextField username_field;
    private javax.swing.JButton webcam_button;
    // End of variables declaration//GEN-END:variables
}

