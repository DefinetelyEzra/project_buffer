package biometrics_exam;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.swing.JOptionPane;
import java.io.FileWriter;
import java.io.IOException;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.util.ArrayList;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.RowFilter;
import javax.swing.table.TableRowSorter;

public class dos_ithead_frame extends javax.swing.JFrame {

    private int currentUserId;
    private String currentUserRole;
    private TableRowSorter<DefaultTableModel> tableSorter;
    private DefaultTableModel rosterTableModel;

    public dos_ithead_frame(int userId) {
        initComponents();
        this.currentUserId = userId;
        loadUserData();
        setupSearchField();
        loadStaffData();
        setupRosterTable();  
        loadRosterData();   
        loadAttendanceData();
        loadUnitSummaryData();
        setupSearchField();
    }
    
    public dos_ithead_frame() {
        this(-1);
    }

    private void loadUserData() {
        if (currentUserId == -1) {
            jLabel15.setText("Guest User");
            jLabel16.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
            return;
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = "SELECT full_name, role FROM users WHERE user_id = ? AND is_active = TRUE";
            ps = con.prepareStatement(query);
            ps.setInt(1, currentUserId);
            rs = ps.executeQuery();

            if (rs.next()) {
                String fullName = rs.getString("full_name");
                String role = rs.getString("role");

                // Store unit for application use
                this.currentUserRole = role;

                // Extract first name from full name
                String firstName = fullName.split("\\s+")[0];

                jLabel13.setText("Welcome back, " + firstName);
                jLabel15.setText(" - " + role);
            } else {
                jLabel13.setText("Welcome back, User");
                jLabel15.setText("No Unit");
                this.currentUserRole = "Guest User";
            }

            // Always update the date
            jLabel16.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));

            loadDashboardData();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error loading user data:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
            jLabel13.setText("Welcome back, User");
            jLabel15.setText("No Unit");
            jLabel16.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")));
            this.currentUserRole = "Guest User";
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void loadDashboardData() {
        loadTotalStaffCount();
        loadPendingSwapsCount();
        loadRecentSignIns();
        loadTodayPresentCount();
    }

    private void loadTotalStaffCount() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = "SELECT COUNT(*) as total_staff FROM users WHERE is_active = TRUE AND role IN ('Staff', 'Supervisor')";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            if (rs.next()) {
                int totalStaff = rs.getInt("total_staff");
                staff_label.setText(String.valueOf(totalStaff));
            }

        } catch (Exception e) {
            e.printStackTrace();
            staff_label.setText("Error");
            JOptionPane.showMessageDialog(this,
                    "Error loading staff count:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void loadPendingSwapsCount() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = "SELECT COUNT(*) as pending_swaps FROM shift_swap_requests WHERE status = 'Pending'";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            if (rs.next()) {
                int pendingSwaps = rs.getInt("pending_swaps");
                swaps_label.setText(String.valueOf(pendingSwaps));
            }

        } catch (Exception e) {
            e.printStackTrace();
            swaps_label.setText("Error");
            JOptionPane.showMessageDialog(this,
                    "Error loading pending swaps count:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void loadTodayPresentCount() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = "SELECT COUNT(DISTINCT a.user_id) as present_today "
                    + "FROM attendance a "
                    + "WHERE DATE(a.attendance_date) = CURDATE() "
                    + "AND a.sign_in_time IS NOT NULL "
                    + "AND a.is_absent = FALSE";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            if (rs.next()) {
                int presentToday = rs.getInt("present_today");
                present_label.setText(String.valueOf(presentToday));
            }

        } catch (Exception e) {
            e.printStackTrace();
            present_label.setText("Error");
            JOptionPane.showMessageDialog(this,
                    "Error loading present count:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void loadRecentSignIns() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = "SELECT u.full_name, u.unit, a.sign_in_time, s.shift_name, "
                    + "CASE "
                    + "  WHEN a.is_late = TRUE THEN 'Late' "
                    + "  WHEN a.sign_in_time IS NOT NULL THEN 'On Time' "
                    + "  ELSE 'Not Signed In' "
                    + "END as status "
                    + "FROM attendance a "
                    + "JOIN users u ON a.user_id = u.user_id "
                    + "JOIN shifts s ON a.shift_id = s.shift_id "
                    + "WHERE DATE(a.attendance_date) = CURDATE() "
                    + "AND a.sign_in_time IS NOT NULL "
                    + "ORDER BY a.sign_in_time DESC "
                    + "LIMIT 10";

            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) sign_table.getModel();
            model.setRowCount(0);

            while (rs.next()) {
                String fullName = rs.getString("full_name");
                String unit = rs.getString("unit");
                java.sql.Timestamp signInTime = rs.getTimestamp("sign_in_time");
                String shiftName = rs.getString("shift_name");
                String status = rs.getString("status");

                String formattedTime = signInTime != null
                        ? signInTime.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        : "Not Signed In";

                model.addRow(new Object[]{fullName, unit, formattedTime, shiftName, status});
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error loading recent sign-ins:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void showAllSignInsDialog() {
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "All Recent Sign-Ins", true);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);

        javax.swing.JTable allSignInsTable = new javax.swing.JTable();
        allSignInsTable.setModel(new javax.swing.table.DefaultTableModel(
                new Object[][]{},
                new String[]{"Staff Name", "Unit", "Sign In Time", "Shift", "Status", "Date"}
        ) {
            boolean[] canEdit = new boolean[]{false, false, false, false, false, false};

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit[columnIndex];
            }
        });

        loadAllRecentSignIns(allSignInsTable);

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(allSignInsTable);

        javax.swing.JButton closeButton = new javax.swing.JButton("Close");
        closeButton.addActionListener(e -> dialog.dispose());

        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout());
        panel.add(scrollPane, java.awt.BorderLayout.CENTER);

        javax.swing.JPanel buttonPanel = new javax.swing.JPanel();
        buttonPanel.add(closeButton);
        panel.add(buttonPanel, java.awt.BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void loadAllRecentSignIns(javax.swing.JTable table) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = "SELECT u.full_name, u.unit, a.sign_in_time, s.shift_name, "
                    + "CASE "
                    + "  WHEN a.is_late = TRUE THEN 'Late' "
                    + "  WHEN a.sign_in_time IS NOT NULL THEN 'On Time' "
                    + "  ELSE 'Not Signed In' "
                    + "END as status, "
                    + "a.attendance_date "
                    + "FROM attendance a "
                    + "JOIN users u ON a.user_id = u.user_id "
                    + "JOIN shifts s ON a.shift_id = s.shift_id "
                    + "WHERE a.sign_in_time IS NOT NULL "
                    + "AND a.attendance_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) "
                    + "ORDER BY a.sign_in_time DESC "
                    + "LIMIT 100";

            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            javax.swing.table.DefaultTableModel model = (javax.swing.table.DefaultTableModel) table.getModel();
            model.setRowCount(0);

            while (rs.next()) {
                String fullName = rs.getString("full_name");
                String unit = rs.getString("unit");
                java.sql.Timestamp signInTime = rs.getTimestamp("sign_in_time");
                String shiftName = rs.getString("shift_name");
                String status = rs.getString("status");
                java.sql.Date attendanceDate = rs.getDate("attendance_date");

                // Format the timestamp
                String formattedTime = signInTime != null
                        ? signInTime.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                        : "Not Signed In";

                // Format the date
                String formattedDate = attendanceDate != null
                        ? attendanceDate.toLocalDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                        : "";

                model.addRow(new Object[]{fullName, unit, formattedTime, shiftName, status, formattedDate});
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error loading all recent sign-ins:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void loadStaffData() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = "SELECT user_id, full_name, email, unit, role, "
                    + "CASE WHEN is_active = TRUE THEN 'Active' ELSE 'Inactive' END as status "
                    + "FROM users ORDER BY full_name";
            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            DefaultTableModel model = (DefaultTableModel) staff_table.getModel();
            model.setRowCount(0); 

            while (rs.next()) {
                Object[] row = {
                    rs.getInt("user_id"),
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("unit"),
                    rs.getString("role"),
                    rs.getString("status")
                };
                model.addRow(row);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error loading staff data:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void searchStaff() {
        String searchText = jTextField1.getText().trim();
        String selectedUnit = (String) unit_combo.getSelectedItem();
        String selectedRole = (String) role_combo.getSelectedItem();

        if (searchText.equals("search by name, email, or staff ID...")) {
            searchText = "";
        }

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            StringBuilder query = new StringBuilder();
            query.append("SELECT user_id, full_name, email, unit, role, ");
            query.append("CASE WHEN is_active = TRUE THEN 'Active' ELSE 'Inactive' END as status ");
            query.append("FROM users WHERE 1=1 ");

            List<Object> parameters = new ArrayList<>();

            if (!searchText.isEmpty()) {
                query.append("AND (full_name LIKE ? OR email LIKE ? OR user_id = ?) ");
                parameters.add("%" + searchText + "%");
                parameters.add("%" + searchText + "%");
                try {
                    int userId = Integer.parseInt(searchText);
                    parameters.add(userId);
                } catch (NumberFormatException e) {
                    parameters.add(-1); // Invalid ID that won't match
                }
            }

            // Add unit filter
            if (selectedUnit != null && !selectedUnit.equals("All Units")) {
                query.append("AND unit = ? ");
                parameters.add(selectedUnit);
            }

            // Add role filter
            if (selectedRole != null && !selectedRole.equals("All Roles")) {
                if (selectedRole.equals("None Supervisor")) {
                    query.append("AND role != 'Supervisor' ");
                } else if (selectedRole.equals("None")) {
                    query.append("AND role = 'Staff' ");
                } else {
                    query.append("AND role = ? ");
                    parameters.add(selectedRole);
                }
            }

            query.append("ORDER BY full_name");

            ps = con.prepareStatement(query.toString());

            // Set parameters
            for (int i = 0; i < parameters.size(); i++) {
                ps.setObject(i + 1, parameters.get(i));
            }

            rs = ps.executeQuery();

            DefaultTableModel model = (DefaultTableModel) staff_table.getModel();
            model.setRowCount(0);

            int rowCount = 0;
            while (rs.next()) {
                Object[] row = {
                    rs.getInt("user_id"),
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("unit"),
                    rs.getString("role"),
                    rs.getString("status")
                };
                model.addRow(row);
                rowCount++;
            }

            if (rowCount == 0) {
                JOptionPane.showMessageDialog(this,
                        "No staff members found matching your search criteria.",
                        "Search Results",
                        JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error searching staff:\n" + e.getMessage(),
                    "Search Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void exportStaffToCSV() {
        try {
            java.io.File directory = new java.io.File("C:\\Users\\ezraa\\OneDrive\\Documents\\NetBeansProjects\\biometrics_exam\\src\\rosters");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String filename = "staff_list_" + timestamp + ".csv";
            String fullPath = directory.getAbsolutePath() + "\\" + filename;

            FileWriter writer = new FileWriter(fullPath);

            writer.append("Staff ID,Name,Email,Unit,Role,Status\n");

            DefaultTableModel model = (DefaultTableModel) staff_table.getModel();
            for (int i = 0; i < model.getRowCount(); i++) {
                for (int j = 0; j < model.getColumnCount(); j++) {
                    Object value = model.getValueAt(i, j);
                    String cellValue = (value != null) ? value.toString() : "";

                    if (cellValue.contains(",") || cellValue.contains("\"")) {
                        cellValue = "\"" + cellValue.replace("\"", "\"\"") + "\"";
                    }

                    writer.append(cellValue);
                    if (j < model.getColumnCount() - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");
            }

            writer.flush();
            writer.close();

            JOptionPane.showMessageDialog(this,
                    "Staff list exported successfully!\nFile saved to: " + fullPath,
                    "Export Successful",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error exporting staff list:\n" + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setupSearchField() {
        jTextField1.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (jTextField1.getText().equals(" search by name, email, or staff ID...")) {
                    jTextField1.setText("");
                    jTextField1.setForeground(new java.awt.Color(0, 0, 0));
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (jTextField1.getText().isEmpty()) {
                    jTextField1.setText(" search by name, email, or staff ID...");
                    jTextField1.setForeground(new java.awt.Color(204, 204, 204));
                }
            }
        });
        staff_search.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (staff_search.getText().equals(" enter staff name, email, or staff ID...")) {
                    staff_search.setText("");
                    staff_search.setForeground(new java.awt.Color(0, 0, 0));
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (staff_search.getText().isEmpty()) {
                    staff_search.setText(" enter staff name, email, or staff ID...");
                    staff_search.setForeground(new java.awt.Color(204, 204, 204));
                }
            }
        });
        search_staff2.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (search_staff2.getText().equals(" enter staff name, email, or staff ID...")) {
                    search_staff2.setText("");
                    search_staff2.setForeground(new java.awt.Color(45, 59, 111));
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (search_staff2.getText().isEmpty()) {
                    search_staff2.setText(" enter staff name, email, or staff ID...");
                    search_staff2.setForeground(new java.awt.Color(204, 204, 204));
                }
            }
        });     
    }
    
    private void setupRosterTable() {
        rosterTableModel = (DefaultTableModel) roster_table.getModel();
        tableSorter = new TableRowSorter<>(rosterTableModel);
        roster_table.setRowSorter(tableSorter);
    }
    
    private void loadRosterData() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = """
                SELECT 
                    u.full_name,
                    u.unit,
                    u.user_id,
                    u.email,
                    ss.shift_date,
                    s.shift_name,
                    s.start_time,
                    s.end_time,
                    DAYNAME(ss.shift_date) as day_name
                FROM users u
                INNER JOIN staff_shifts ss ON u.user_id = ss.user_id
                INNER JOIN shifts s ON ss.shift_id = s.shift_id
                WHERE u.is_active = TRUE AND ss.is_active = TRUE
                ORDER BY u.full_name, ss.shift_date
            """;

            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            rosterTableModel.setRowCount(0);

            java.util.Map<String, java.util.Map<String, String>> userWeeklySchedule = new java.util.HashMap<>();

            while (rs.next()) {
                String staffName = rs.getString("full_name");
                String unit = rs.getString("unit");
                String userKey = staffName + "|" + unit; // Unique key for each staff member
                String dayName = rs.getString("day_name");
                String shiftInfo = rs.getString("shift_name") + " ("
                        + rs.getTime("start_time") + "-"
                        + rs.getTime("end_time") + ")";

                userWeeklySchedule.putIfAbsent(userKey, new java.util.HashMap<>());
                userWeeklySchedule.get(userKey).put(dayName, shiftInfo);
            }

            // Populate table with weekly schedule
            for (java.util.Map.Entry<String, java.util.Map<String, String>> entry : userWeeklySchedule.entrySet()) {
                String[] userInfo = entry.getKey().split("\\|");
                String staffName = userInfo[0];
                String unit = userInfo[1];
                java.util.Map<String, String> weekSchedule = entry.getValue();

                Object[] row = new Object[9];
                row[0] = staffName;
                row[1] = unit;
                row[2] = weekSchedule.getOrDefault("Monday", "Off");
                row[3] = weekSchedule.getOrDefault("Tuesday", "Off");
                row[4] = weekSchedule.getOrDefault("Wednesday", "Off");
                row[5] = weekSchedule.getOrDefault("Thursday", "Off");
                row[6] = weekSchedule.getOrDefault("Friday", "Off");
                row[7] = weekSchedule.getOrDefault("Saturday", "Off");
                row[8] = weekSchedule.getOrDefault("Sunday", "Off");

                rosterTableModel.addRow(row);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error loading roster data:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }
    
    public void refreshRosterData() {
    loadRosterData();
    }
    
    private void loadAttendanceData() {
        DefaultTableModel model = (DefaultTableModel) attendance_table.getModel();
        model.setRowCount(0); // Clear existing data

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            String query = """
            SELECT 
                u.full_name,
                u.unit,
                s.shift_name,
                CONCAT(s.start_time, ' - ', s.end_time) as scheduled_time,
                a.sign_in_time,
                a.sign_out_time,
                CASE 
                    WHEN a.is_absent = TRUE THEN 'Absent'
                    WHEN a.is_late = TRUE THEN 'Late'
                    WHEN a.is_early_out = TRUE THEN 'Early Out'
                    WHEN a.sign_in_time IS NOT NULL AND a.sign_out_time IS NOT NULL THEN 'Complete'
                    WHEN a.sign_in_time IS NOT NULL AND a.sign_out_time IS NULL THEN 'Signed In'
                    ELSE 'No Record'
                END as status,
                a.attendance_date,
                u.user_id,
                u.email
            FROM attendance a
            JOIN users u ON a.user_id = u.user_id
            JOIN shifts s ON a.shift_id = s.shift_id
            WHERE u.role NOT IN ('IT_Head', 'Director') AND u.is_active = TRUE
            ORDER BY a.attendance_date DESC, u.full_name
        """;

            ps = con.prepareStatement(query);
            rs = ps.executeQuery();

            while (rs.next()) {
                String fullName = rs.getString("full_name");
                String unit = rs.getString("unit");
                String shiftName = rs.getString("shift_name");
                String scheduledTime = rs.getString("scheduled_time");

                // Format sign in time
                String signInTime = "Not Signed In";
                if (rs.getTimestamp("sign_in_time") != null) {
                    signInTime = new SimpleDateFormat("HH:mm:ss").format(rs.getTimestamp("sign_in_time"));
                }

                // Format sign out time
                String signOutTime = "Not Signed Out";
                if (rs.getTimestamp("sign_out_time") != null) {
                    signOutTime = new SimpleDateFormat("HH:mm:ss").format(rs.getTimestamp("sign_out_time"));
                }

                String status = rs.getString("status");

                model.addRow(new Object[]{fullName, unit, shiftName, scheduledTime, signInTime, signOutTime, status});
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error loading attendance data:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }
    
    private void filterAttendanceTable() {
        DefaultTableModel model = (DefaultTableModel) attendance_table.getModel();
        model.setRowCount(0); 

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            // Build dynamic query based on filters
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("""
            SELECT 
                u.full_name,
                u.unit,
                s.shift_name,
                CONCAT(s.start_time, ' - ', s.end_time) as scheduled_time,
                a.sign_in_time,
                a.sign_out_time,
                CASE 
                    WHEN a.is_absent = TRUE THEN 'Absent'
                    WHEN a.is_late = TRUE THEN 'Late'
                    WHEN a.is_early_out = TRUE THEN 'Early Out'
                    WHEN a.sign_in_time IS NOT NULL AND a.sign_out_time IS NOT NULL THEN 'Complete'
                    WHEN a.sign_in_time IS NOT NULL AND a.sign_out_time IS NULL THEN 'Signed In'
                    ELSE 'No Record'
                END as status,
                a.attendance_date,
                u.user_id,
                u.email
            FROM attendance a
            JOIN users u ON a.user_id = u.user_id
            JOIN shifts s ON a.shift_id = s.shift_id
            WHERE u.role NOT IN ('IT_Head', 'Director') AND u.is_active = TRUE
        """);

            // Add date filters
            boolean hasStartDate = start_date.getDate() != null;
            boolean hasEndDate = end_date.getDate() != null;

            if (hasStartDate) {
                queryBuilder.append(" AND a.attendance_date >= ?");
            }
            if (hasEndDate) {
                queryBuilder.append(" AND a.attendance_date <= ?");
            }

            // Add unit filter
            String selectedUnit = (String) unit_combo3.getSelectedItem();
            boolean hasUnitFilter = !selectedUnit.equals("All Units");
            if (hasUnitFilter) {
                queryBuilder.append(" AND u.unit = ?");
            }

            // Add search filter
            String searchText = search_staff2.getText().trim();
            boolean hasSearchFilter = !searchText.isEmpty()
                    && !searchText.equals(" enter staff name, email, or staff ID...");
            if (hasSearchFilter) {
                queryBuilder.append(" AND (u.full_name LIKE ? OR u.email LIKE ? OR u.user_id LIKE ?)");
            }

            queryBuilder.append(" ORDER BY a.attendance_date DESC, u.full_name");

            ps = con.prepareStatement(queryBuilder.toString());

            int paramIndex = 1;

            // Set date parameters
            if (hasStartDate) {
                ps.setDate(paramIndex++, new java.sql.Date(start_date.getDate().getTime()));
            }
            if (hasEndDate) {
                ps.setDate(paramIndex++, new java.sql.Date(end_date.getDate().getTime()));
            }

            // Set unit parameter
            if (hasUnitFilter) {
                ps.setString(paramIndex++, selectedUnit);
            }

            // Set search parameters
            if (hasSearchFilter) {
                String searchPattern = "%" + searchText + "%";
                ps.setString(paramIndex++, searchPattern);
                ps.setString(paramIndex++, searchPattern);
                ps.setString(paramIndex++, searchPattern);
            }

            rs = ps.executeQuery();

            int recordCount = 0;
            while (rs.next()) {
                String fullName = rs.getString("full_name");
                String unit = rs.getString("unit");
                String shiftName = rs.getString("shift_name");
                String scheduledTime = rs.getString("scheduled_time");

                // Format sign in time
                String signInTime = "Not Signed In";
                if (rs.getTimestamp("sign_in_time") != null) {
                    signInTime = new SimpleDateFormat("HH:mm:ss").format(rs.getTimestamp("sign_in_time"));
                }

                // Format sign out time
                String signOutTime = "Not Signed Out";
                if (rs.getTimestamp("sign_out_time") != null) {
                    signOutTime = new SimpleDateFormat("HH:mm:ss").format(rs.getTimestamp("sign_out_time"));
                }

                String status = rs.getString("status");

                model.addRow(new Object[]{fullName, unit, shiftName, scheduledTime, signInTime, signOutTime, status});
                recordCount++;
            }

            if (recordCount == 0) {
                JOptionPane.showMessageDialog(this,
                        "No records found matching the selected criteria.",
                        "No Results",
                        JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error filtering attendance data:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void generateAttendanceReport() {
        String reportType = (String) type_combo.getSelectedItem();

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        FileWriter csvWriter = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            // Build query based on report type
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("""
            SELECT 
                u.full_name as 'Staff Name',
                u.email as 'Email',
                u.user_id as 'Staff ID',
                u.unit as 'Unit',
                s.shift_name as 'Shift',
                CONCAT(s.start_time, ' - ', s.end_time) as 'Scheduled Time',
                DATE(a.attendance_date) as 'Date',
                TIME(a.sign_in_time) as 'Sign In Time',
                TIME(a.sign_out_time) as 'Sign Out Time',
                CASE 
                    WHEN a.is_absent = TRUE THEN 'Absent'
                    WHEN a.is_late = TRUE THEN 'Late'
                    WHEN a.is_early_out = TRUE THEN 'Early Out'
                    WHEN a.sign_in_time IS NOT NULL AND a.sign_out_time IS NOT NULL THEN 'Complete'
                    WHEN a.sign_in_time IS NOT NULL AND a.sign_out_time IS NULL THEN 'Signed In'
                    ELSE 'No Record'
                END as 'Status',
                CASE 
                    WHEN a.sign_in_time IS NOT NULL AND a.sign_out_time IS NOT NULL THEN
                        TIMESTAMPDIFF(MINUTE, a.sign_in_time, a.sign_out_time)
                    ELSE 0
                END as 'Hours Worked (Minutes)'
            FROM attendance a
            JOIN users u ON a.user_id = u.user_id
            JOIN shifts s ON a.shift_id = s.shift_id
            WHERE u.role NOT IN ('IT_Head', 'Director') AND u.is_active = TRUE
        """);

            // Add date filters based on report type
            Date currentDate = new Date();
            java.sql.Date startDate = null;
            java.sql.Date endDate = new java.sql.Date(currentDate.getTime());

            switch (reportType) {
                case "Daily":
                    startDate = new java.sql.Date(currentDate.getTime());
                    queryBuilder.append(" AND DATE(a.attendance_date) = CURDATE()");
                    break;
                case "Weekly":
                    queryBuilder.append(" AND a.attendance_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)");
                    break;
                case "Monthly":
                    queryBuilder.append(" AND a.attendance_date >= DATE_SUB(CURDATE(), INTERVAL 1 MONTH)");
                    break;
                case "Yearly":
                    queryBuilder.append(" AND a.attendance_date >= DATE_SUB(CURDATE(), INTERVAL 1 YEAR)");
                    break;
            }

            queryBuilder.append(" ORDER BY a.attendance_date DESC, u.full_name");

            ps = con.prepareStatement(queryBuilder.toString());
            rs = ps.executeQuery();

            String directoryPath = "C:\\Users\\ezraa\\OneDrive\\Documents\\NetBeansProjects\\biometrics_exam\\src\\rosters";
            File directory = new File(directoryPath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Generate filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String filename = String.format("Attendance_Report_%s_%s.csv", reportType, timestamp);
            String filepath = directoryPath + File.separator + filename;

            csvWriter = new FileWriter(filepath);

            // Write CSV header
            csvWriter.append("Staff Name,Email,Staff ID,Unit,Shift,Scheduled Time,Date,Sign In Time,Sign Out Time,Status,Hours Worked (Minutes)\n");

            int recordCount = 0;
            while (rs.next()) {
                // Escape and format data for CSV
                String staffName = escapeCSV(rs.getString("Staff Name"));
                String email = escapeCSV(rs.getString("Email"));
                String staffId = escapeCSV(String.valueOf(rs.getInt("Staff ID")));
                String unit = escapeCSV(rs.getString("Unit"));
                String shift = escapeCSV(rs.getString("Shift"));
                String scheduledTime = escapeCSV(rs.getString("Scheduled Time"));
                String date = escapeCSV(rs.getString("Date"));
                String signInTime = rs.getString("Sign In Time") != null
                        ? escapeCSV(rs.getString("Sign In Time")) : "Not Signed In";
                String signOutTime = rs.getString("Sign Out Time") != null
                        ? escapeCSV(rs.getString("Sign Out Time")) : "Not Signed Out";
                String status = escapeCSV(rs.getString("Status"));
                String hoursWorked = String.valueOf(rs.getInt("Hours Worked (Minutes)"));

                csvWriter.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                        staffName, email, staffId, unit, shift, scheduledTime,
                        date, signInTime, signOutTime, status, hoursWorked));
                recordCount++;
            }

            csvWriter.flush();

            JOptionPane.showMessageDialog(this,
                    String.format("Attendance report generated successfully!\n"
                            + "File: %s\n"
                            + "Records: %d\n"
                            + "Report Type: %s",
                            filename, recordCount, reportType),
                    "Report Generated",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error generating attendance report:\n" + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            if (csvWriter != null) {
                try {
                    csvWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            closeResources(con, ps, rs);
        }
    }

    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }

        // If the value contains comma, quote, or newline, wrap it in quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            // Escape existing quotes by doubling them
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }

        return value;
    }

    private void loadUnitSummaryData() {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            // Clear existing data
            DefaultTableModel model = (DefaultTableModel) unit_table.getModel();
            model.setRowCount(0);

            // Get today's date for attendance calculation
            String today = LocalDate.now().toString();

            // Query to get unit statistics
            String query = """
            SELECT 
                u.unit,
                COUNT(DISTINCT u.user_id) as total_staff,
                COUNT(DISTINCT CASE 
                    WHEN a.attendance_date = ? AND a.sign_in_time IS NOT NULL AND a.is_absent = FALSE 
                    THEN a.user_id 
                END) as present,
                COUNT(DISTINCT CASE 
                    WHEN a.attendance_date = ? AND (a.is_absent = TRUE OR (a.sign_in_time IS NULL AND a.sign_out_time IS NULL))
                    THEN a.user_id 
                END) as absent,
                COUNT(DISTINCT CASE 
                    WHEN a.attendance_date = ? AND a.is_late = TRUE AND a.is_absent = FALSE
                    THEN a.user_id 
                END) as late
            FROM users u
            LEFT JOIN attendance a ON u.user_id = a.user_id
            WHERE u.is_active = TRUE AND u.unit != 'None'
            GROUP BY u.unit
            ORDER BY u.unit
            """;

            ps = con.prepareStatement(query);
            ps.setString(1, today);
            ps.setString(2, today);
            ps.setString(3, today);
            rs = ps.executeQuery();

            while (rs.next()) {
                String unit = rs.getString("unit");
                int totalStaff = rs.getInt("total_staff");
                int present = rs.getInt("present");
                int absent = rs.getInt("absent");
                int late = rs.getInt("late");

                // Calculate attendance rate
                double attendanceRate = 0.0;
                if (totalStaff > 0) {
                    attendanceRate = ((double) present / totalStaff) * 100;
                }

                // Add row to table
                Object[] rowData = {
                    unit,
                    String.valueOf(totalStaff),
                    String.valueOf(present),
                    String.valueOf(absent),
                    String.valueOf(late),
                    Math.round(attendanceRate * 100.0) / 100.0 // Rounds to 2 decimal places
                };

                model.addRow(rowData);
            }

            // If no data found, show units with zero counts
            if (model.getRowCount() == 0) {
                loadEmptyUnitData(model);
            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Database driver not found:\n" + e.getMessage(),
                    "Driver Error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Error loading unit summary data:\n" + e.getMessage(),
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Unexpected error loading unit data:\n" + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            closeResources(con, ps, rs);
        }
    }

    private void loadEmptyUnitData(DefaultTableModel model) {
        String[] units = {"Security", "Horticulture", "Facility", "Cafeteria", "Maintenance"};

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection("jdbc:mysql://localhost:3306/pams", "root", "Mayfair314@11SQL");

            for (String unit : units) {
                String query = "SELECT COUNT(*) as total_staff FROM users WHERE unit = ? AND is_active = TRUE";
                ps = con.prepareStatement(query);
                ps.setString(1, unit);
                rs = ps.executeQuery();

                int totalStaff = 0;
                if (rs.next()) {
                    totalStaff = rs.getInt("total_staff");
                }

                Object[] rowData = {
                    unit,
                    String.valueOf(totalStaff),
                    "0", // present
                    "0", // absent
                    "0", // late
                    0.0 // attendance rate
                };

                model.addRow(rowData);

                if (rs != null) {
                    rs.close();
                    rs = null;
                }
                if (ps != null) {
                    ps.close();
                    ps = null;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            for (String unit : units) {
                Object[] rowData = {unit, "0", "0", "0", "0", 0.0};
                model.addRow(rowData);
            }
        } finally {
            closeResources(con, ps, rs);
        }
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

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel9 = new javax.swing.JPanel();
        jPanel10 = new javax.swing.JPanel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        Dash = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        swaps_label = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        a_title = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        staff_label = new javax.swing.JLabel();
        jLabel19 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        present_label = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        sign_table = new javax.swing.JTable();
        a_title1 = new javax.swing.JLabel();
        all_sign_button = new javax.swing.JButton();
        staff_tab = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        staff_table = new javax.swing.JTable();
        a_title3 = new javax.swing.JLabel();
        export_button = new javax.swing.JButton();
        jTextField1 = new javax.swing.JTextField();
        unit_combo = new javax.swing.JComboBox<>();
        search_staff_button = new javax.swing.JButton();
        role_combo = new javax.swing.JComboBox<>();
        roster_tab = new javax.swing.JPanel();
        a_title10 = new javax.swing.JLabel();
        jScrollPane5 = new javax.swing.JScrollPane();
        roster_table = new javax.swing.JTable();
        a_title11 = new javax.swing.JLabel();
        starting_week = new com.toedter.calendar.JDateChooser();
        unit_combo2 = new javax.swing.JComboBox<>();
        staff_search = new javax.swing.JTextField();
        filter_button = new javax.swing.JButton();
        download_button = new javax.swing.JButton();
        attendance_tab = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        attendance_table = new javax.swing.JTable();
        a_title6 = new javax.swing.JLabel();
        a_title7 = new javax.swing.JLabel();
        start_date = new com.toedter.calendar.JDateChooser();
        a_title8 = new javax.swing.JLabel();
        end_date = new com.toedter.calendar.JDateChooser();
        unit_combo3 = new javax.swing.JComboBox<>();
        search_staff2 = new javax.swing.JTextField();
        filter_table2 = new javax.swing.JButton();
        type_combo = new javax.swing.JComboBox<>();
        a_title9 = new javax.swing.JLabel();
        download_attendance_button = new javax.swing.JButton();
        unit_tab = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        unit_table = new javax.swing.JTable();
        a_title2 = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel9.setBackground(new java.awt.Color(45, 59, 111));
        jPanel9.setForeground(new java.awt.Color(255, 255, 255));

        jPanel10.setBackground(new java.awt.Color(45, 59, 111));

        jLabel13.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel13.setForeground(new java.awt.Color(255, 255, 255));
        jLabel13.setText("ATTENDANCE MANAGEMENT SYSTEM-");

        jLabel14.setIcon(new javax.swing.ImageIcon(getClass().getResource("/biometrics_exam/pau_icon-25x25.png"))); // NOI18N

        jLabel15.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
        jLabel15.setForeground(new java.awt.Color(255, 255, 255));
        jLabel15.setText("Director of Services");

        jLabel16.setFont(new java.awt.Font("Segoe UI", 0, 14)); // NOI18N
        jLabel16.setForeground(new java.awt.Color(255, 255, 255));
        jLabel16.setText("current date");

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel14)
                .addGap(18, 18, 18)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addComponent(jLabel13)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel15))
                    .addComponent(jLabel16))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel14, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel13)
                            .addComponent(jLabel15))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel16)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane2.setBackground(new java.awt.Color(45, 59, 111));
        jTabbedPane2.setForeground(new java.awt.Color(255, 255, 255));
        jTabbedPane2.setTabPlacement(javax.swing.JTabbedPane.LEFT);
        jTabbedPane2.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N

        Dash.setBackground(new java.awt.Color(255, 255, 255));

        jPanel7.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED, null, new java.awt.Color(204, 204, 204), null, new java.awt.Color(204, 204, 204)));

        swaps_label.setFont(new java.awt.Font("Segoe UI", 1, 36)); // NOI18N
        swaps_label.setForeground(new java.awt.Color(45, 59, 111));
        swaps_label.setText("0");

        jLabel25.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        jLabel25.setForeground(new java.awt.Color(45, 59, 111));
        jLabel25.setText("Pending Swaps");

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel25))
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addGap(40, 40, 40)
                        .addComponent(swaps_label)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(swaps_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel25)
                .addContainerGap(8, Short.MAX_VALUE))
        );

        a_title.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        a_title.setForeground(new java.awt.Color(45, 59, 111));
        a_title.setText("Recent Sign Ins");

        jPanel2.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED, null, new java.awt.Color(204, 204, 204), null, new java.awt.Color(204, 204, 204)));

        staff_label.setFont(new java.awt.Font("Segoe UI", 1, 36)); // NOI18N
        staff_label.setForeground(new java.awt.Color(45, 59, 111));
        staff_label.setText("0");

        jLabel19.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel19.setForeground(new java.awt.Color(45, 59, 111));
        jLabel19.setText("Total Staff");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(47, 47, 47)
                        .addComponent(staff_label))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(27, 27, 27)
                        .addComponent(jLabel19)))
                .addContainerGap(26, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(staff_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel19)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel3.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED, null, new java.awt.Color(204, 204, 204), null, new java.awt.Color(204, 204, 204)));

        present_label.setFont(new java.awt.Font("Segoe UI", 1, 36)); // NOI18N
        present_label.setForeground(new java.awt.Color(45, 59, 111));
        present_label.setText("0");

        jLabel21.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        jLabel21.setForeground(new java.awt.Color(45, 59, 111));
        jLabel21.setText("Present Today");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGap(49, 49, 49)
                        .addComponent(present_label))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGap(16, 16, 16)
                        .addComponent(jLabel21)))
                .addContainerGap(13, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(present_label)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel21)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        sign_table.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 59, 111)));
        sign_table.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        sign_table.setForeground(new java.awt.Color(45, 59, 111));
        sign_table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Staff Name", "Unit", "Sign In Time", "Shift", "Status"
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
        jScrollPane2.setViewportView(sign_table);
        if (sign_table.getColumnModel().getColumnCount() > 0) {
            sign_table.getColumnModel().getColumn(0).setResizable(false);
            sign_table.getColumnModel().getColumn(1).setResizable(false);
            sign_table.getColumnModel().getColumn(2).setResizable(false);
            sign_table.getColumnModel().getColumn(3).setResizable(false);
            sign_table.getColumnModel().getColumn(4).setResizable(false);
        }

        a_title1.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        a_title1.setForeground(new java.awt.Color(45, 59, 111));
        a_title1.setText("SYSTEM OVERVIEW");

        all_sign_button.setBackground(new java.awt.Color(204, 0, 51));
        all_sign_button.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        all_sign_button.setForeground(new java.awt.Color(255, 255, 255));
        all_sign_button.setText("View All");
        all_sign_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                all_sign_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout DashLayout = new javax.swing.GroupLayout(Dash);
        Dash.setLayout(DashLayout);
        DashLayout.setHorizontalGroup(
            DashLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(DashLayout.createSequentialGroup()
                .addGap(31, 31, 31)
                .addGroup(DashLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(DashLayout.createSequentialGroup()
                        .addComponent(a_title1)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, DashLayout.createSequentialGroup()
                        .addGroup(DashLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(DashLayout.createSequentialGroup()
                                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(62, 62, 62)
                                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(60, 60, 60)
                                .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(DashLayout.createSequentialGroup()
                                .addComponent(a_title)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(all_sign_button))
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 672, Short.MAX_VALUE))
                        .addGap(37, 37, 37))))
        );
        DashLayout.setVerticalGroup(
            DashLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(DashLayout.createSequentialGroup()
                .addGap(15, 15, 15)
                .addComponent(a_title1)
                .addGap(18, 18, 18)
                .addGroup(DashLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel7, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(34, 34, 34)
                .addGroup(DashLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(a_title)
                    .addComponent(all_sign_button))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 336, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane2.addTab("Dashboard", Dash);

        staff_tab.setBackground(new java.awt.Color(255, 255, 255));

        staff_table.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        staff_table.setForeground(new java.awt.Color(45, 59, 111));
        staff_table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Staff ID", "Name", "Email", "Unit", "Role", "Status"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Integer.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane1.setViewportView(staff_table);

        a_title3.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        a_title3.setForeground(new java.awt.Color(45, 59, 111));
        a_title3.setText("STAFF DIRECTORY");

        export_button.setBackground(new java.awt.Color(204, 0, 51));
        export_button.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        export_button.setForeground(new java.awt.Color(255, 255, 255));
        export_button.setText("Export Staff List");
        export_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                export_buttonActionPerformed(evt);
            }
        });

        jTextField1.setForeground(new java.awt.Color(204, 204, 204));
        jTextField1.setText(" search by name, email, or staff ID...");
        jTextField1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        unit_combo.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        unit_combo.setForeground(new java.awt.Color(45, 59, 111));
        unit_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All Units", "Security", "Maintenance ", "Cafeteria", "Horticulture", "Facility" }));
        unit_combo.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        search_staff_button.setBackground(new java.awt.Color(45, 59, 111));
        search_staff_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        search_staff_button.setForeground(new java.awt.Color(255, 255, 255));
        search_staff_button.setText("Search");
        search_staff_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                search_staff_buttonActionPerformed(evt);
            }
        });

        role_combo.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        role_combo.setForeground(new java.awt.Color(45, 59, 111));
        role_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All Roles", "Supervisor", "None Supervisor", "None" }));
        role_combo.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        javax.swing.GroupLayout staff_tabLayout = new javax.swing.GroupLayout(staff_tab);
        staff_tab.setLayout(staff_tabLayout);
        staff_tabLayout.setHorizontalGroup(
            staff_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, staff_tabLayout.createSequentialGroup()
                .addContainerGap(38, Short.MAX_VALUE)
                .addGroup(staff_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(staff_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(jScrollPane1)
                        .addGroup(staff_tabLayout.createSequentialGroup()
                            .addComponent(a_title3)
                            .addGap(380, 380, 380)
                            .addComponent(export_button)))
                    .addGroup(staff_tabLayout.createSequentialGroup()
                        .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 265, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(unit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(role_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(search_staff_button, javax.swing.GroupLayout.PREFERRED_SIZE, 109, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(15, 15, 15))
        );
        staff_tabLayout.setVerticalGroup(
            staff_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, staff_tabLayout.createSequentialGroup()
                .addGap(17, 17, 17)
                .addGroup(staff_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(a_title3)
                    .addComponent(export_button))
                .addGap(18, 18, 18)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 366, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addGroup(staff_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(unit_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(search_staff_button, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(role_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(75, 75, 75))
        );

        jTabbedPane2.addTab("Staff Management", staff_tab);

        roster_tab.setBackground(new java.awt.Color(255, 255, 255));

        a_title10.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        a_title10.setForeground(new java.awt.Color(45, 59, 111));
        a_title10.setText("WEEKLY ROSTER");

        roster_table.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        roster_table.setForeground(new java.awt.Color(45, 59, 111));
        roster_table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Staff Name", "Unit", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane5.setViewportView(roster_table);
        if (roster_table.getColumnModel().getColumnCount() > 0) {
            roster_table.getColumnModel().getColumn(0).setResizable(false);
            roster_table.getColumnModel().getColumn(1).setResizable(false);
            roster_table.getColumnModel().getColumn(2).setResizable(false);
            roster_table.getColumnModel().getColumn(3).setResizable(false);
            roster_table.getColumnModel().getColumn(4).setResizable(false);
            roster_table.getColumnModel().getColumn(5).setResizable(false);
            roster_table.getColumnModel().getColumn(6).setResizable(false);
            roster_table.getColumnModel().getColumn(7).setResizable(false);
            roster_table.getColumnModel().getColumn(8).setResizable(false);
        }

        a_title11.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        a_title11.setForeground(new java.awt.Color(45, 59, 111));
        a_title11.setText("Week Starting");

        starting_week.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 59, 111)));

        unit_combo2.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        unit_combo2.setForeground(new java.awt.Color(45, 59, 111));
        unit_combo2.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All Units", "Security", "Maintenance ", "Cafeteria", "Horticulture", "Facility" }));
        unit_combo2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        staff_search.setForeground(new java.awt.Color(204, 204, 204));
        staff_search.setText(" enter staff name, email, or staff ID...");
        staff_search.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));
        staff_search.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                staff_searchActionPerformed(evt);
            }
        });

        filter_button.setBackground(new java.awt.Color(45, 59, 111));
        filter_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        filter_button.setForeground(new java.awt.Color(255, 255, 255));
        filter_button.setText("Filter Table");
        filter_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filter_buttonActionPerformed(evt);
            }
        });

        download_button.setBackground(new java.awt.Color(51, 204, 0));
        download_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        download_button.setForeground(new java.awt.Color(255, 255, 255));
        download_button.setText("Download Roster (CSV)");
        download_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                download_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout roster_tabLayout = new javax.swing.GroupLayout(roster_tab);
        roster_tab.setLayout(roster_tabLayout);
        roster_tabLayout.setHorizontalGroup(
            roster_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(roster_tabLayout.createSequentialGroup()
                .addGap(29, 29, 29)
                .addGroup(roster_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(download_button)
                    .addComponent(a_title11)
                    .addComponent(a_title10)
                    .addGroup(roster_tabLayout.createSequentialGroup()
                        .addComponent(starting_week, javax.swing.GroupLayout.PREFERRED_SIZE, 161, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(unit_combo2, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(staff_search, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(filter_button, javax.swing.GroupLayout.PREFERRED_SIZE, 109, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 681, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(30, Short.MAX_VALUE))
        );

        roster_tabLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {starting_week, unit_combo2});

        roster_tabLayout.setVerticalGroup(
            roster_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, roster_tabLayout.createSequentialGroup()
                .addGap(23, 23, 23)
                .addComponent(a_title10)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 238, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(a_title11)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(roster_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(roster_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(unit_combo2, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(staff_search, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(filter_button, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(starting_week, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(32, 32, 32)
                .addComponent(download_button, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(110, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("Roster Management", roster_tab);

        attendance_tab.setBackground(new java.awt.Color(255, 255, 255));

        attendance_table.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        attendance_table.setForeground(new java.awt.Color(45, 59, 111));
        attendance_table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Staff Name", "Unit", "Shift", "Scheduled Time", "Sign In Time", "Sign Out", "Status"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane4.setViewportView(attendance_table);
        if (attendance_table.getColumnModel().getColumnCount() > 0) {
            attendance_table.getColumnModel().getColumn(0).setResizable(false);
            attendance_table.getColumnModel().getColumn(1).setResizable(false);
            attendance_table.getColumnModel().getColumn(2).setResizable(false);
            attendance_table.getColumnModel().getColumn(3).setResizable(false);
            attendance_table.getColumnModel().getColumn(4).setResizable(false);
            attendance_table.getColumnModel().getColumn(5).setResizable(false);
            attendance_table.getColumnModel().getColumn(6).setResizable(false);
        }

        a_title6.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        a_title6.setForeground(new java.awt.Color(45, 59, 111));
        a_title6.setText("ATTENDANCE RECORDS");

        a_title7.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        a_title7.setForeground(new java.awt.Color(45, 59, 111));
        a_title7.setText("From Date");

        start_date.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 59, 111)));

        a_title8.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        a_title8.setForeground(new java.awt.Color(45, 59, 111));
        a_title8.setText("To Date");

        end_date.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(45, 59, 111)));

        unit_combo3.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        unit_combo3.setForeground(new java.awt.Color(45, 59, 111));
        unit_combo3.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All Units", "Security", "Maintenance ", "Cafeteria", "Horticulture", "Facility" }));
        unit_combo3.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        search_staff2.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        search_staff2.setForeground(new java.awt.Color(204, 204, 204));
        search_staff2.setText(" enter staff name, email, or staff ID...");
        search_staff2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        filter_table2.setBackground(new java.awt.Color(45, 59, 111));
        filter_table2.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        filter_table2.setForeground(new java.awt.Color(255, 255, 255));
        filter_table2.setText("Filter Table");
        filter_table2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filter_table2ActionPerformed(evt);
            }
        });

        type_combo.setFont(new java.awt.Font("Segoe UI", 1, 13)); // NOI18N
        type_combo.setForeground(new java.awt.Color(45, 59, 111));
        type_combo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Daily", "Weekly", "Monthly", "Yearly" }));
        type_combo.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(45, 59, 111), 1, true));

        a_title9.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        a_title9.setForeground(new java.awt.Color(45, 59, 111));
        a_title9.setText("Report Type");

        download_attendance_button.setBackground(new java.awt.Color(51, 204, 0));
        download_attendance_button.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        download_attendance_button.setForeground(new java.awt.Color(255, 255, 255));
        download_attendance_button.setText("Download CSV");
        download_attendance_button.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                download_attendance_buttonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout attendance_tabLayout = new javax.swing.GroupLayout(attendance_tab);
        attendance_tab.setLayout(attendance_tabLayout);
        attendance_tabLayout.setHorizontalGroup(
            attendance_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(attendance_tabLayout.createSequentialGroup()
                .addGap(29, 29, 29)
                .addGroup(attendance_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(a_title9)
                    .addGroup(attendance_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, attendance_tabLayout.createSequentialGroup()
                            .addGroup(attendance_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(start_date, javax.swing.GroupLayout.PREFERRED_SIZE, 161, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(a_title7))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addGroup(attendance_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(a_title8)
                                .addGroup(attendance_tabLayout.createSequentialGroup()
                                    .addComponent(end_date, javax.swing.GroupLayout.PREFERRED_SIZE, 161, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGap(12, 12, 12)
                                    .addComponent(unit_combo3, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGap(12, 12, 12)
                                    .addComponent(search_staff2, javax.swing.GroupLayout.PREFERRED_SIZE, 211, javax.swing.GroupLayout.PREFERRED_SIZE))))
                        .addComponent(filter_table2, javax.swing.GroupLayout.PREFERRED_SIZE, 109, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 681, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(a_title6))
                    .addGroup(attendance_tabLayout.createSequentialGroup()
                        .addComponent(type_combo, javax.swing.GroupLayout.PREFERRED_SIZE, 161, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(download_attendance_button, javax.swing.GroupLayout.PREFERRED_SIZE, 166, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(30, Short.MAX_VALUE))
        );
        attendance_tabLayout.setVerticalGroup(
            attendance_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, attendance_tabLayout.createSequentialGroup()
                .addGap(23, 23, 23)
                .addComponent(a_title6)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 238, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(attendance_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(a_title7)
                    .addComponent(a_title8))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(attendance_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(start_date, javax.swing.GroupLayout.DEFAULT_SIZE, 33, Short.MAX_VALUE)
                    .addComponent(end_date, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(unit_combo3)
                    .addComponent(search_staff2))
                .addGap(18, 18, 18)
                .addComponent(filter_table2, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(a_title9)
                .addGap(12, 12, 12)
                .addGroup(attendance_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(download_attendance_button, javax.swing.GroupLayout.DEFAULT_SIZE, 35, Short.MAX_VALUE)
                    .addComponent(type_combo))
                .addGap(55, 55, 55))
        );

        jTabbedPane2.addTab("Attendance Logs", attendance_tab);

        unit_tab.setBackground(new java.awt.Color(255, 255, 255));

        unit_table.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        unit_table.setForeground(new java.awt.Color(45, 59, 111));
        unit_table.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Unit", "Total Staff", "Present", "Absent", "Late", "Attendance Rate"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.Double.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane3.setViewportView(unit_table);
        if (unit_table.getColumnModel().getColumnCount() > 0) {
            unit_table.getColumnModel().getColumn(0).setResizable(false);
            unit_table.getColumnModel().getColumn(1).setResizable(false);
            unit_table.getColumnModel().getColumn(2).setResizable(false);
            unit_table.getColumnModel().getColumn(3).setResizable(false);
            unit_table.getColumnModel().getColumn(4).setResizable(false);
            unit_table.getColumnModel().getColumn(5).setResizable(false);
        }

        a_title2.setFont(new java.awt.Font("Segoe UI", 1, 20)); // NOI18N
        a_title2.setForeground(new java.awt.Color(45, 59, 111));
        a_title2.setText("UNIT PERFORMANCE SUMMARY");

        javax.swing.GroupLayout unit_tabLayout = new javax.swing.GroupLayout(unit_tab);
        unit_tab.setLayout(unit_tabLayout);
        unit_tabLayout.setHorizontalGroup(
            unit_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(unit_tabLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(unit_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(a_title2)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 694, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(30, Short.MAX_VALUE))
        );
        unit_tabLayout.setVerticalGroup(
            unit_tabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(unit_tabLayout.createSequentialGroup()
                .addGap(19, 19, 19)
                .addComponent(a_title2, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(284, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("Unit Summary", unit_tab);

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
                .addComponent(jTabbedPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 562, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 6, Short.MAX_VALUE)
                .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
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

    private void download_attendance_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_download_attendance_buttonActionPerformed
        generateAttendanceReport();
    }//GEN-LAST:event_download_attendance_buttonActionPerformed

    private void filter_table2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filter_table2ActionPerformed
        filterAttendanceTable();
    }//GEN-LAST:event_filter_table2ActionPerformed

    private void download_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_download_buttonActionPerformed
        try {
            String directoryPath = "C:\\Users\\ezraa\\OneDrive\\Documents\\NetBeansProjects\\biometrics_exam\\src\\rosters";
            File directory = new File(directoryPath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Generate filename with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String timestamp = sdf.format(new Date());
            String fileName = "roster_" + timestamp + ".csv";
            String filePath = directoryPath + File.separator + fileName;

            // Create and write CSV file
            try (FileWriter writer = new FileWriter(filePath)) {
                // Write header
                writer.append("Staff Name,Unit,Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday\n");

                // Write data from visible rows (considering filters)
                for (int i = 0; i < roster_table.getRowCount(); i++) {
                    for (int j = 0; j < roster_table.getColumnCount(); j++) {
                        Object value = roster_table.getValueAt(i, j);
                        String cellValue = (value != null) ? value.toString() : "";

                        // Escape quotes and wrap in quotes if contains comma
                        if (cellValue.contains(",") || cellValue.contains("\"")) {
                            cellValue = "\"" + cellValue.replace("\"", "\"\"") + "\"";
                        }

                        writer.append(cellValue);
                        if (j < roster_table.getColumnCount() - 1) {
                            writer.append(",");
                        }
                    }
                    writer.append("\n");
                }

                writer.flush();

                JOptionPane.showMessageDialog(this,
                    "Roster exported successfully!\nFile saved as: " + fileName
                    + "\nLocation: " + directoryPath,
                    "Export Successful",
                    JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException e) {
                throw new Exception("Error writing CSV file: " + e.getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error exporting roster:\n" + e.getMessage(),
                "Export Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_download_buttonActionPerformed

    private void filter_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filter_buttonActionPerformed
        try {
            String searchText = staff_search.getText().trim();
            String selectedUnit = (String) unit_combo2.getSelectedItem();

            if (searchText.equals(" enter staff name, email, or staff ID...")) {
                searchText = "";
            }

            java.util.List<RowFilter<Object, Object>> filters = new java.util.ArrayList<>();

            // Add search filter if search text is not empty
            if (!searchText.isEmpty()) {
                // Create a filter that searches in staff name (column 0)
                RowFilter<Object, Object> searchFilter = RowFilter.regexFilter("(?i)" + searchText, 0);
                filters.add(searchFilter);
            }

            // Add unit filter if not "All Units"
            if (selectedUnit != null && !selectedUnit.equals("All Units")) {
                RowFilter<Object, Object> unitFilter = RowFilter.regexFilter("^" + selectedUnit + "$", 1);
                filters.add(unitFilter);
            }

            // Apply filters
            if (filters.isEmpty()) {
                tableSorter.setRowFilter(null); // Show all rows
            } else {
                RowFilter<Object, Object> combinedFilter = RowFilter.andFilter(filters);
                tableSorter.setRowFilter(combinedFilter);
            }

            // Show message if no results found
            if (roster_table.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this,
                    "No matching records found.",
                    "Search Results",
                    JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Error applying filters:\n" + e.getMessage(),
                "Filter Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_filter_buttonActionPerformed

    private void staff_searchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_staff_searchActionPerformed
        filter_buttonActionPerformed(evt);
    }//GEN-LAST:event_staff_searchActionPerformed

    private void search_staff_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_search_staff_buttonActionPerformed
        searchStaff();
    }//GEN-LAST:event_search_staff_buttonActionPerformed

    private void export_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_export_buttonActionPerformed
        exportStaffToCSV();
    }//GEN-LAST:event_export_buttonActionPerformed

    private void all_sign_buttonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_all_sign_buttonActionPerformed
        showAllSignInsDialog();
    }//GEN-LAST:event_all_sign_buttonActionPerformed

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new dos_ithead_frame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel Dash;
    private javax.swing.JLabel a_title;
    private javax.swing.JLabel a_title1;
    private javax.swing.JLabel a_title10;
    private javax.swing.JLabel a_title11;
    private javax.swing.JLabel a_title2;
    private javax.swing.JLabel a_title3;
    private javax.swing.JLabel a_title6;
    private javax.swing.JLabel a_title7;
    private javax.swing.JLabel a_title8;
    private javax.swing.JLabel a_title9;
    private javax.swing.JButton all_sign_button;
    private javax.swing.JPanel attendance_tab;
    private javax.swing.JTable attendance_table;
    private javax.swing.JButton download_attendance_button;
    private javax.swing.JButton download_button;
    private com.toedter.calendar.JDateChooser end_date;
    private javax.swing.JButton export_button;
    private javax.swing.JButton filter_button;
    private javax.swing.JButton filter_table2;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JLabel present_label;
    private javax.swing.JComboBox<String> role_combo;
    private javax.swing.JPanel roster_tab;
    private javax.swing.JTable roster_table;
    private javax.swing.JTextField search_staff2;
    private javax.swing.JButton search_staff_button;
    private javax.swing.JTable sign_table;
    private javax.swing.JLabel staff_label;
    private javax.swing.JTextField staff_search;
    private javax.swing.JPanel staff_tab;
    private javax.swing.JTable staff_table;
    private com.toedter.calendar.JDateChooser start_date;
    private com.toedter.calendar.JDateChooser starting_week;
    private javax.swing.JLabel swaps_label;
    private javax.swing.JComboBox<String> type_combo;
    private javax.swing.JComboBox<String> unit_combo;
    private javax.swing.JComboBox<String> unit_combo2;
    private javax.swing.JComboBox<String> unit_combo3;
    private javax.swing.JPanel unit_tab;
    private javax.swing.JTable unit_table;
    // End of variables declaration//GEN-END:variables
}

