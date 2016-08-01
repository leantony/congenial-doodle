package com.sacco.classes;

import com.sacco.Hashing.BCrypt;
import java.beans.PropertyChangeSupport;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.security.auth.login.AccountException;

public class Member implements AutoCloseable {

    protected static long id;
    public static final String PROP_FIRSTNAME = "PROP_FIRSTNAME";
    public static final String PROP_GENDER = "PROP_GENDER";
    public static final String PROP_LASTNAME = "PROP_LASTNAME";
    public static final String PROP_DOB = "PROP_DOB";
    public static final String PROP_MOBILENO = "PROP_MOBILENO";
    public static final String PROP_ADDRESS = "PROP_ADDRESS";
    public static final String PROP_EMAIL = "PROP_EMAIL";
    private String firstname;
    private String gender;
    private String lastname;
    private Date dob;
    private long mobileno;
    private String address;
    private String email;
    private String password;
    private Timestamp RegisteredDate;
    private Timestamp LastModifiedDate;
    private boolean AccountStatus;
    protected long t_id = 0;

    // for account active/ not. by default, a created account is active
    public static final short ACCOUNT_ACTIVE = 1;
    public static final short ACCOUNT_INACTIVE = 0;
    private static boolean admin = false;

    protected List<Member> allMembers = new ArrayList<>();
    protected static List<String> userEmails = new ArrayList<>();
    protected static List<Long> userMobile = new ArrayList<>();

    // for Database stuff
    PreparedStatement stmt = null;
    Connection conn = null;
    ResultSet result = null;

    protected static void setAdmin(boolean status) {
        admin = status;
    }

    public static long getId() {
        return id;
    }

    public static boolean isAdmin() {
        return admin;
    }

    protected static void setId(long userid) {
        id = userid;
    }

    private final transient PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        java.lang.String oldFirstname = this.firstname;
        this.firstname = firstname;
        propertyChangeSupport.firePropertyChange(PROP_FIRSTNAME, oldFirstname, firstname);
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        java.lang.String oldGender = this.gender;
        this.gender = gender;
        propertyChangeSupport.firePropertyChange(PROP_GENDER, oldGender, gender);
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        java.lang.String oldLastname = this.lastname;
        this.lastname = lastname;
        propertyChangeSupport.firePropertyChange(PROP_LASTNAME, oldLastname, lastname);
    }

    public Date getDob() {
        return dob;
    }

    public void setDob(Date dob) {
        java.sql.Date oldDob = this.dob;
        this.dob = dob;
        propertyChangeSupport.firePropertyChange(PROP_DOB, oldDob, dob);
    }

    public long getMobileno() {
        return mobileno;
    }

    public void setMobileno(long mobileno) {
        long oldMobileno = this.mobileno;
        this.mobileno = mobileno;
        propertyChangeSupport.firePropertyChange(PROP_MOBILENO, oldMobileno, mobileno);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        java.lang.String oldAddress = this.address;
        this.address = address;
        propertyChangeSupport.firePropertyChange(PROP_ADDRESS, oldAddress, address);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        java.lang.String oldEmail = this.email;
        this.email = email;
        propertyChangeSupport.firePropertyChange(PROP_EMAIL, oldEmail, email);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        // hash the password. never optional but in this case it is. ata bcrypt nkatoa bana
        this.password = BCrypt.Hash(password, BCrypt.generateSalt());
    }

    public Timestamp getRegisteredDate() {
        return RegisteredDate;
    }

    public Timestamp getLastModifiedDate() {
        return LastModifiedDate;
    }

    public boolean getAccountStatus() {
        return AccountStatus;
    }

    private void fillEmailList() throws SQLException {
        try {
            this.conn = new Database().getConnection();
            String sql = "SELECT email FROM members";
            stmt = conn.prepareStatement(sql);
            result = stmt.executeQuery();
            while (result.next()) {
                userEmails.add(result.getString("email"));
            }
        } finally {
            close();
        }
    }

    private void fillMobileNoList() throws SQLException {
        try {
            this.conn = new Database().getConnection();
            String sql = "SELECT mobileno FROM members";
            stmt = conn.prepareStatement(sql);
            result = stmt.executeQuery();
            while (result.next()) {
                userMobile.add(result.getLong("mobileno"));
            }
        } finally {
            close();
        }
    }

    public boolean checkMobileExists(long mno) {
        try {
            if (userMobile.isEmpty()) {
                fillMobileNoList();
            }
            return userMobile.contains(mno);
        } catch (SQLException ex) {
            return false;
        }
    }

    public boolean checkEmailExists(String email) {
        try {
            if (userEmails.isEmpty()) {
                fillEmailList();
            }
            //JOptionPane.showMessageDialog(null, userEmails + " " + email +" "+userEmails.contains(email));
            return userEmails.contains(email);
        } catch (SQLException ex) {
            return false;
        }
    }

    public long AddMember() throws SQLException, AccountException {
        this.conn = new Database().getConnection();
        try {
            String sql
                    = "INSERT INTO `members` "
                    + "(`firstname`, `lastname`, `gender`, `dob`,"
                    + " `mobileno`, `address`, `email`, `password`) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, getFirstname());
            stmt.setString(2, getLastname());
            stmt.setString(3, getGender());
            stmt.setDate(4, getDob());
            stmt.setLong(5, getMobileno());
            stmt.setString(6, getAddress());
            stmt.setString(7, getEmail());
            stmt.setString(8, getPassword());

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("The specified user wasn't created");
            }
            // get the returned inserted id
            result = stmt.getGeneratedKeys();
            if (result.next()) {
                return result.getLong(1);
            } else {
                throw new SQLException("The specified user wasn't created. an ID wasn't obtained");
            }
        } finally {
            close();
        }
    }

    public long getPositionId(String posname) throws SQLException {
        this.conn = new Database().getConnection();
        try {
            String sql = "SELECT `id` FROM positions WHERE `name` LIKE ? LIMIT 1";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, "%" + posname + "%");
            result = stmt.executeQuery();
            if (result.next()) {
                return result.getLong("id");
            }
        } finally {
            close();
        }
        return -1;
    }

    public void getMemberInfo(Member m, long memberID) throws SQLException {
        // if a user invokes this as a normal user, then we don't need them passing any memberID in. just default to theirs
        if (!isAdmin()) {
            memberID = Member.getId();
        }
        this.conn = new Database().getConnection();
        try {
            String sql = "SELECT * FROM `members` WHERE  `id`=?";
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, memberID);
            result = stmt.executeQuery();
            while (result.next()) {
                m.lastname = result.getString("lastname");
                m.address = result.getString("address");
                m.firstname = result.getString("firstname");
                m.gender = result.getString("gender");
                m.email = result.getString("email");
                m.dob = result.getDate("dob");
                m.mobileno = result.getLong("mobileno");
                m.password = result.getString("password");
                m.RegisteredDate = result.getTimestamp("DateRegistered");
                m.LastModifiedDate = result.getTimestamp("DateModified");
                m.AccountStatus = result.getBoolean("active");
            }
        } finally {
            close();
        }
    }

    public boolean EditPassword(String password, long memberID) throws AccountException, SQLException {
        if (!isAdmin()) {
            memberID = Member.getId();
        }
        this.conn = new Database().getConnection();
        try {
            password = BCrypt.Hash(password, BCrypt.generateSalt());
            String sql = "UPDATE `members` SET `password`=? WHERE `id`= ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, password);
            stmt.setLong(2, memberID);
            return stmt.executeUpdate() == 1;
        } finally {
            close();
        }
    }

    public boolean EditMemberInfo(Member m) throws AccountException, SQLException {
        this.conn = new Database().getConnection();
        try {
            String sql = "UPDATE `members` SET "
                    + "`firstname`=?, `lastname`=?, `gender`=?, `dob`=?, "
                    + "`mobileno`=?, `address`=?, `email`=? WHERE  `id`=?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, m.getFirstname());
            stmt.setString(2, m.getLastname());
            stmt.setString(3, m.getGender());
            stmt.setDate(4, m.getDob());
            stmt.setLong(5, m.getMobileno());
            stmt.setString(6, m.getAddress());
            stmt.setString(7, m.getEmail());
            stmt.setLong(8, Member.getId());
            return stmt.executeUpdate() == 1;
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        if (result != null) {
            try {
                result.close();
            } catch (SQLException e) {
            }
        }
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
            }
        }
        if (conn != null) {
            try {
                conn.close();
                conn = null;
            } catch (SQLException e) {
            }
        }
    }
}
