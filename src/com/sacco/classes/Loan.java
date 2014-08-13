package com.sacco.classes;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.security.auth.login.AccountException;
import javax.swing.JTextArea;

public class Loan {

    // define loan table datatypes as potrayed by the Database
    private long id;
    private long paymentID;
    private double LoanAmount;
    private double TotalAmount; // loanAmount + interest
    // value is entered as months but displayed as years internally
    private double PaybackPeriod;
    private String LoanPurpose;
    private String LoanType;
    private Timestamp DateSubmitted;
    private List<Loan> loanInfo = new ArrayList();
    // amounts
    private double AmountToPay; // from the user
    private double AmountPaid; // from db
    // loan constants
    private static double LOAN_INTEREST;
    private static final short LOAN_CLEARED = 1;
    private static final short LOAN_NOT_CLEARED = 0;
    private static double MIN_LOAN = 5000;
    private static double MAX_LOAN = 1000000;
    private static final double MAX_ALLOWED_PAYMENT = 1000000;
    private static final double MIN_ALLOWED_PAYMENT = 1000;

    Contribution _contribution;
    PreparedStatement stmt = null;
    Connection conn;
    ResultSet result = null;
    Member m;
    LoanPayment p;

    public Loan() {
        this._contribution = new Contribution();
        this.conn = Database.getDBConnection();
        this.m = new Member();
        this.p = new LoanPayment();
        try {
            Loan.LOAN_INTEREST = getLoanInterest();
        } catch (SQLException ex) {
            // if we get an error, default to 5
            Loan.LOAN_INTEREST = 5;
        }
    }

    // allow the user to know their pending loan amount to pay, after making a payment to their loan
    public double getPendingAmount() {
        return getTotalAmount() - getAmountPaid();
    }

    public double getLoanAmount() {
        return LoanAmount;
    }

    public void setLoanAmount(double LoanAmount) {
        this.LoanAmount = LoanAmount;
    }

    public double getTotalAmount() {
        return TotalAmount;
    }

    public final double getLoanInterest() throws SQLException {
        try {
            String sql = "SELECT value FROM settings WHERE name = 'interest'";
            stmt = conn.prepareStatement(sql);
            result = stmt.executeQuery();
            if (result.next()) {
                return result.getDouble("value");
            }
        } finally {
            close();
        }
        // default to 5 if an error occurs
        return 5;
    }

    public double getPaybackPeriod() {
        return PaybackPeriod;
    }

    public void setPaybackPeriod(double PaybackDate) {
        this.PaybackPeriod = PaybackDate / 12; // convert to years
    }

    public String getLoanPurpose() {
        return LoanPurpose;
    }

    public void setLoanPurpose(String LoanPurpose) {
        this.LoanPurpose = LoanPurpose;
    }

    public String getLoanType() {
        return LoanType;
    }

    public void setLoanType(String LoanType) {
        this.LoanType = LoanType;
    }

    public long getId() {
        return id;
    }

    private void setId(long id) {
        this.id = id;
    }

    public double getAmountToPay() {
        return AmountToPay;
    }

    public void setAmountToPay(double AmountToPay) {
        this.AmountToPay = AmountToPay;
    }

    public double getAmountPaid() {
        return AmountPaid;
    }

    private void setAmountPaid(double AmountPaid) {
        this.AmountPaid = AmountPaid;
    }

    public long getPaymentID() {
        return paymentID;
    }

    private void setPaymentID(long paymentID) {
        this.paymentID = paymentID;
    }

    public static double getMAX_ALLOWED_PAYMENT() {
        return MAX_ALLOWED_PAYMENT;
    }

    public static double getMIN_ALLOWED_PAYMENT() {
        return MIN_ALLOWED_PAYMENT;
    }

    public static double getMIN_LOAN() {
        return MIN_LOAN;
    }

    public static void setMinLoanAmount(double min) {
        MIN_LOAN = min;
    }

    public static double getMaxLoanAmount() {
        return MAX_LOAN;
    }

    public static void setMaxLoanAmount(double max) {
        MAX_LOAN = max;
    }

    public Timestamp getDateSubmitted() {
        return DateSubmitted;
    }

    private void setTotalAmount(double TotalAmount) {
        double arate = LOAN_INTEREST * 12 / 100;
        double payment = TotalAmount * Math.pow((1 + arate), getPaybackPeriod());
        this.TotalAmount = payment + TotalAmount;
    }

    public double GetAllowedLoan() throws AccountException, SQLException {
        // we determine their loan amnt based on average contributions and how many times they have validly contributed
        double AvgContribution = _contribution.getAvgContributions(Member.getId(), 2);
        int contributionCount = _contribution.getMemberContributions(1);
        // a member should only apply for a loan if they've at least contributed once. 
        // so we count their approved contributions, and if they are 0 we alert them
        if (contributionCount == 0) {
            throw new AccountException("You are not elligible to apply for a loan since youve not yet contributed to the sacco");
        }
        // a user can only apply for a loan if they do't have any pending loans. so we get the count of their uncleared loans
        // in the db, a loan is uncleared if it's cleared status is 0. implies it hasn't been fully paid
        if (GetMemberLoanCount(LOAN_NOT_CLEARED) == 1) {
            throw new AccountException("You have pending loans to pay. Please clear them first to be able to continue");
        }
        double multiplier;
        // at least 3x
        if (AvgContribution >= 5000 && contributionCount <= 5) {
            multiplier = 1.5;
            return multiplier * AvgContribution;
        } else {
            // max loan should be 3x their contributions
            multiplier = 3.0;
            return multiplier * AvgContribution;
        }
    }

    // allow members to request loans
    public long RequestLoan(double amount) throws SQLException, AccountException {
        setTotalAmount(amount);
        try {
            String sql = "INSERT INTO `loans` "
                    + "(`member_id`, `LoanType`, `LoanAmount`, "
                    + "`TotalAmount`, `PaybackDate`, `LoanPurpose`) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setLong(1, Member.getId());
            stmt.setString(2, getLoanType());
            stmt.setDouble(3, getLoanAmount());
            stmt.setDouble(4, getTotalAmount());
            stmt.setDouble(5, getPaybackPeriod());
            stmt.setString(6, getLoanPurpose());
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("The loan couldn't be saved");
            }
            // get the returned inserted id
            result = stmt.getGeneratedKeys();
            if (result.next()) {
                setId(result.getLong(1));
                return getId();
            } else {
                throw new SQLException("The loan couldn't be saved. an ID wasn't obtained");
            }
        } finally {
            close();
        }
    }

    // loan payback function
    public boolean PayBackLoan() throws SQLException, AccountException {
        // only an uncleared loan should be the one a member should pay for
        getLoanInfo(LOAN_NOT_CLEARED);
        // implies a loan id couldn't be found
        if (getId() <= 0) {
            throw new SQLException("A loan id wasn't obtained");
        }
        // a member shouldn't be allowed to pay up above their total. so we notify them if that happens as they keep paying
        if (getAmountPaid() >= getTotalAmount()) {
            // get their excess payment
            double excess = getAmountPaid() - getTotalAmount();
            // clear the loan, since now the user has either overpaid or has equally paid the loan fully
            clearLoan(excess);
            // not really an indication of an error, but since the function return T/F this is the best way to do so
            throw new AccountException("You loan is now fully paid. \nYour overpayment of ksh " + Application.df.format(excess) + " will be added to your contributions");
        } else {
            // implies that the user hasn't paid enough, so we allow them to pay up
            try {
                String sql = "UPDATE `loans` SET `paidAmount`= `paidAmount` + ? WHERE  `id`=?";
                stmt = conn.prepareStatement(sql);
                stmt.setDouble(1, getAmountToPay());
                stmt.setLong(2, getId());
                // record payment
                int rows = stmt.executeUpdate();
                setPaymentID(p.recordLoanPayment(getId(), getAmountToPay()));
                return rows == 1;

            } finally {
                close();
            }
        }
    }

    // this shud be calld only after payment >= Loan+interest
    protected boolean clearLoan(double excess) throws SQLException {
        try {
            String sql = "UPDATE `loans` SET cleared = ? WHERE `id`=?";
            stmt = conn.prepareStatement(sql);
            stmt.setDouble(1, LOAN_CLEARED);
            stmt.setLong(2, getId());
            int rows = stmt.executeUpdate();
            return addExcessToContributions(excess) == rows;

        } finally {
            close();
        }
    }

    private int addExcessToContributions(double excess) throws SQLException {
        String sql = "INSERT INTO `contributions` (`member_id`, `Amount`, `paymentMethod`, `Approved`) VALUES (?, ?, ?, ?)";
        stmt = conn.prepareStatement(sql);
        stmt.setLong(1, Member.getId());
        stmt.setDouble(2, excess);
        stmt.setString(3, "EXCESS");
        stmt.setBoolean(4, true);
        return stmt.executeUpdate();
    }

    public double GetLoanTotal(int cleared) throws SQLException {
        String sql;
        if (cleared == 0) {
            sql = "SELECT SUM(LoanAmount) FROM loans WHERE cleared = 0";
        } else if (cleared == 1) {
            sql = "SELECT SUM(LoanAmount) FROM loans WHERE cleared = 1";
        } else {
            sql = "SELECT SUM(LoanAmount) FROM loans WHERE cleared = 1 OR cleared = 0";
        }
        try {
            stmt = conn.prepareStatement(sql);
            result = stmt.executeQuery();
            if (result.next()) {
                return result.getDouble(1);
            } else {
                throw new SQLException("a sum could not be made");
            }
        } finally {
            close();
        }
    }

    public double getPaymentsTotal() throws SQLException {
        String sql = "SELECT SUM(paidAmount) FROM loans";
        try {
            stmt = conn.prepareStatement(sql);
            result = stmt.executeQuery();
            if (result.next()) {
                return result.getDouble(1);
            } else {
                throw new SQLException("a sum could not be made");
            }
        } finally {
            close();
        }
    }

    public double getInterestTotal() throws SQLException {
        String sql = "SELECT SUM(TotalAmount) FROM loans WHERE cleared = 1 OR cleared = 0";
        try {
            stmt = conn.prepareStatement(sql);
            result = stmt.executeQuery();
            if (result.next()) {
                return result.getDouble(1);
            } else {
                throw new SQLException("a sum could not be made");
            }
        } finally {
            close();
        }
    }

    public int GetOverallLoanCount(int cleared) throws SQLException {
        String sql;
        if (cleared == 0) {
            sql = "SELECT COUNT(id) FROM loans WHERE cleared = 0";
        } else if (cleared == 1) {
            sql = "SELECT COUNT(id) FROM loans WHERE cleared = 1";
        } else {
            sql = "SELECT COUNT(id) FROM loans WHERE cleared = 1 OR cleared = 0";
        }
        try {
            stmt = conn.prepareStatement(sql);
            result = stmt.executeQuery();
            int rows;
            if (result.next()) {
                rows = result.getInt(1);
                return rows;
            } else {
                throw new SQLException("a count could not be made");
            }
        } finally {
            close();
        }
    }

    public int GetMemberLoanCount(int cleared) throws SQLException {
        String sql;
        if (cleared == 0) {
            sql = "SELECT COUNT(member_id) FROM loans WHERE member_id = ? AND cleared = 0";
        } else if (cleared == 1) {
            sql = "SELECT COUNT(member_id) FROM loans WHERE member_id = ? AND cleared = 1";
        } else {
            sql = "SELECT COUNT(member_id) FROM loans WHERE member_id = ? AND cleared = 1 OR cleared = 0";
        }
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, Member.getId());
            result = stmt.executeQuery();
            int rows;
            if (result.next()) {
                rows = result.getInt(1);
                return rows;
            } else {
                throw new SQLException("a count could not be made");
            }
        } finally {
            close();
        }
    }

    public void getLoanInfo(int cleared) throws SQLException {
        String sql;
        if (cleared == 0) {
            sql = "SELECT * FROM `loans` WHERE member_id = ? AND cleared = 0";
        } else if (cleared == 1) {
            sql = "SELECT * FROM `loans` WHERE member_id = ? AND cleared = 1";
        } else {
            sql = "SELECT * FROM `loans` WHERE member_id = ? AND cleared = 1 OR cleared = 0";
        }
        try {
            stmt = conn.prepareStatement(sql);
            stmt.setLong(1, Member.getId());
            result = stmt.executeQuery();
            while (result.next()) {
                Loan l = new Loan();
                l.id = (result.getLong("id"));
                l.LoanType = result.getString("LoanType");
                l.LoanAmount = result.getDouble("LoanAmount");
                l.TotalAmount = result.getDouble("TotalAmount");
                l.AmountPaid = result.getDouble("paidAmount");
                l.DateSubmitted = result.getTimestamp("DateSubmitted");
                l.PaybackPeriod = result.getDouble("paybackDate");
                l.LoanType = result.getString("loanType");
                loanInfo.add(l);
            }
        } finally {
            close();
        }
    }

    public void PrintLoanStatus(JTextArea jt, int cleared) throws SQLException {
        jt.setText("");
        if (loanInfo.isEmpty()) {
            getLoanInfo(cleared);
        }
        double la, pa = 0, ta = 0;
        Date d = Date.valueOf(LocalDate.now());
        jt.append("LOAN_AMOUNT\t\tTOTAL_AMOUNT\t\tLOAN_PERIOD\t\tLOAN_TYPE\t\tPAID_AMOUNT\n \n");
        for (Loan loan : loanInfo) {
            la = Double.parseDouble(Application.df.format(loan.getLoanAmount()));
            pa = Double.parseDouble(Application.df.format(loan.getAmountPaid()));
            ta = Double.parseDouble(Application.df.format(loan.getTotalAmount()));
            jt.append(la + "");
            jt.append("\t\t");
            jt.append(ta + "");
            jt.append("\t\t");
            jt.append(Application.df.format(loan.getPaybackPeriod()) + "");
            jt.append("\t\t");
            jt.append(loan.getLoanType());
            jt.append("\t\t");
            jt.append(pa + "\n");
            d = new Date(loan.getDateSubmitted().getTime());
        }
        jt.append("\n");
        jt.append("==============================================================================\n\n");
        jt.append("For your selected option, You currently have " + GetMemberLoanCount(cleared) + " loans\n");
        jt.append("Regarding your current loan, you've paid ksh " + pa + " since " + d + "\n");
        jt.append("You owe the sacco ksh " + (ta - pa) + ". \t Note: A negative value indicates an overpayment\n");
        jt.append("==============================================================================\n\n");
        jt.append("LOAN_AMOUNT  ==> represents the amount(s) you took as a loan\n");
        jt.append("TOTAL_AMOUNT ==> calculated as; loan x interestRate x time\n");
        jt.append("PAID_AMOUNT  ==> represents the amount you paid for each loan\n");
        jt.append("LOAN_PERIOD  ==> Time (Years) you chose to fulfil your loan payment");
    }

    private void close() {
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
    }
}
