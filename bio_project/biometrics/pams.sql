CREATE TABLE users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    date_of_birth DATE NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    gender ENUM('Male', 'Female') NOT NULL,
    unit ENUM('Security', 'Horticulture', 'Facility', 'Cafeteria', 'Maintenance', 'None') NOT NULL,
    role ENUM('IT_Admin', 'Director', 'IT_Head', 'Supervisor', 'Staff') NOT NULL,
    photo LONGBLOB,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE biometric_data (
    biometric_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    fingerprint_template LONGBLOB NOT NULL,
    finger_position ENUM('Right_Thumb', 'Right_Index', 'Left_Thumb', 'Left_Index') NOT NULL,
    quality_score INT,
    enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE shifts (
    shift_id INT PRIMARY KEY AUTO_INCREMENT,
    unit ENUM('Security', 'Horticulture', 'Facility', 'Cafeteria', 'Maintenance') NOT NULL,
    shift_name ENUM('Morning', 'Afternoon', 'Evening') NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE staff_shifts (
    assignment_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    shift_id INT NOT NULL,
    shift_date DATE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (shift_id) REFERENCES shifts(shift_id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_date (user_id, shift_date)
);

CREATE TABLE attendance (
    attendance_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    shift_id INT NOT NULL,
    sign_in_time TIMESTAMP NULL,
    sign_out_time TIMESTAMP NULL,
    attendance_date DATE NOT NULL,
    is_late BOOLEAN DEFAULT FALSE,
    is_early_out BOOLEAN DEFAULT FALSE,
    is_absent BOOLEAN DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (shift_id) REFERENCES shifts(shift_id) ON DELETE CASCADE
);

CREATE TABLE shift_swap_requests (
    request_id INT PRIMARY KEY AUTO_INCREMENT,
    requester_id INT NOT NULL,
    target_user_id INT NOT NULL,
    requester_shift_id INT NOT NULL,
    target_shift_id INT NOT NULL,
    swap_date DATE NOT NULL,
    request_reason TEXT,
    status ENUM('Pending', 'Approved', 'Declined') DEFAULT 'Pending',
    supervisor_id INT,
    supervisor_comments TEXT,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    FOREIGN KEY (requester_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (target_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (requester_shift_id) REFERENCES shifts(shift_id) ON DELETE CASCADE,
    FOREIGN KEY (target_shift_id) REFERENCES shifts(shift_id) ON DELETE CASCADE,
    FOREIGN KEY (supervisor_id) REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE TABLE rosters (
    roster_id INT PRIMARY KEY AUTO_INCREMENT,
    unit ENUM('Security', 'Horticulture', 'Facility', 'Cafeteria', 'Maintenance') NOT NULL,
    roster_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    uploaded_by INT NOT NULL,
    file_path VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (uploaded_by) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE password_resets (
    reset_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    reset_token VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE email_notifications (
    notification_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    email_type ENUM('Registration', 'Password_Reset', 'Shift_Assignment', 'Shift_Swap', 'Sign_In', 'Sign_Out') NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    sent_status ENUM('Pending', 'Sent', 'Failed') DEFAULT 'Pending',
    sent_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE system_logs (
    log_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NULL,
    action_type ENUM(
        'LOGIN', 'LOGOUT', 'SIGN_IN', 'SIGN_OUT', 
        'USER_REGISTRATION', 'PASSWORD_RESET', 'PASSWORD_CHANGE',
        'SHIFT_ASSIGNMENT', 'SHIFT_SWAP_REQUEST', 'SHIFT_SWAP_APPROVAL',
        'ROSTER_UPLOAD', 'ROSTER_DOWNLOAD', 'REPORT_GENERATION',
        'USER_UPDATE', 'USER_DELETE', 'BIOMETRIC_ENROLLMENT',
        'EMAIL_SENT', 'SYSTEM_ERROR'
    ) NOT NULL,
    description TEXT NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    additional_data JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
);