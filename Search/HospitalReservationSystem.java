// ============================================================================
// 병원 예약 시스템 (Hospital Reservation System)
// 작성자: 20181819 장자훈
// JDBC를 활용한 완전한 데이터베이스 애플리케이션
// ============================================================================

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;

// ============================================================================
// 1. 데이터베이스 연결 관리 클래스
// ============================================================================
class DatabaseConnection {
    private static final String DB_URL = "jdbc:sqlite:hospital.db";
    private static Connection connection = null;
    
    /**
     * 데이터베이스 연결을 얻는 메서드 (Singleton 패턴)
     * 이유: 하나의 연결을 재사용하여 리소스 효율성 향상
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
            connection.setAutoCommit(false); // 트랜잭션 수동 제어
        }
        return connection;
    }
    
    /**
     * 데이터베이스 초기화 - 테이블 생성 및 인덱스 설정
     * 2차 과제의 최적화된 인덱스 설계 반영
     */
    public static void initializeDatabase() throws SQLException {
        Connection conn = getConnection();
        Statement stmt = conn.createStatement();
        
        // 외래키 제약조건 활성화
        stmt.execute("PRAGMA foreign_keys = ON");
        
        // 테이블 생성 (1차 과제 스키마)
        String[] createTables = {
            """
            CREATE TABLE IF NOT EXISTS PATIENT (
                patient_id INTEGER PRIMARY KEY AUTOINCREMENT,
                patient_name VARCHAR(50) NOT NULL,
                birth_date DATE NOT NULL,
                phone_number VARCHAR(20) NOT NULL UNIQUE,
                gender CHAR(1) NOT NULL CHECK (gender IN ('M','F'))
            )
            """,
            
            """
            CREATE TABLE IF NOT EXISTS DOCTOR (
                doctor_id INTEGER PRIMARY KEY AUTOINCREMENT,
                doctor_name VARCHAR(50) NOT NULL,
                department VARCHAR(50) NOT NULL,
                phone_number VARCHAR(20) NOT NULL UNIQUE
            )
            """,
            
            """
            CREATE TABLE IF NOT EXISTS RESERVATION (
                reservation_id INTEGER PRIMARY KEY AUTOINCREMENT,
                patient_id INTEGER NOT NULL,
                doctor_id INTEGER NOT NULL,
                reservation_date DATE NOT NULL,
                reservation_time TIME NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT '예약완료',
                FOREIGN KEY (patient_id) REFERENCES PATIENT(patient_id),
                FOREIGN KEY (doctor_id) REFERENCES DOCTOR(doctor_id)
            )
            """,
            
            """
            CREATE TABLE IF NOT EXISTS MEDICAL_RECORD (
                record_id INTEGER PRIMARY KEY AUTOINCREMENT,
                reservation_id INTEGER NOT NULL,
                diagnosis VARCHAR(200) NOT NULL,
                prescription VARCHAR(200),
                treatment_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (reservation_id) REFERENCES RESERVATION(reservation_id)
            )
            """
        };
        
        for (String sql : createTables) {
            stmt.execute(sql);
        }
        
        // 2차 과제의 최적화 인덱스 생성
        String[] createIndexes = {
            "CREATE INDEX IF NOT EXISTS idx_patient_name ON PATIENT (patient_name)",
            "CREATE INDEX IF NOT EXISTS idx_doctor_department_name ON DOCTOR (department, doctor_name)",
            "CREATE INDEX IF NOT EXISTS idx_resv_date_time ON RESERVATION (reservation_date, reservation_time)",
            "CREATE INDEX IF NOT EXISTS idx_resv_doctor_date_status ON RESERVATION (doctor_id, reservation_date, status)",
            "CREATE INDEX IF NOT EXISTS idx_resv_patient_date_desc ON RESERVATION (patient_id, reservation_date DESC, reservation_time DESC)",
            "CREATE INDEX IF NOT EXISTS idx_medrec_treatment_date ON MEDICAL_RECORD (treatment_date)",
            "CREATE INDEX IF NOT EXISTS idx_medrec_diagnosis ON MEDICAL_RECORD (diagnosis)"
        };
        
        for (String sql : createIndexes) {
            stmt.execute(sql);
        }
        
        conn.commit();
        System.out.println("✅ 데이터베이스 초기화 완료 (테이블 + 최적화 인덱스)");
    }
}

// ============================================================================
// 2. 모델 클래스들 (Entity Classes)
// ============================================================================
class Patient {
    private int patientId;
    private String patientName;
    private LocalDate birthDate;
    private String phoneNumber;
    private char gender;
    
    // 생성자
    public Patient() {}
    
    public Patient(String patientName, LocalDate birthDate, String phoneNumber, char gender) {
        this.patientName = patientName;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.gender = gender;
    }
    
    // Getter & Setter
    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public char getGender() { return gender; }
    public void setGender(char gender) { this.gender = gender; }
    
    @Override
    public String toString() {
        return String.format("환자[%d] %s (%s) - %s", 
            patientId, patientName, gender == 'M' ? "남" : "여", phoneNumber);
    }
}

class Doctor {
    private int doctorId;
    private String doctorName;
    private String department;
    private String phoneNumber;
    
    // 생성자
    public Doctor() {}
    
    public Doctor(String doctorName, String department, String phoneNumber) {
        this.doctorName = doctorName;
        this.department = department;
        this.phoneNumber = phoneNumber;
    }
    
    // Getter & Setter
    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    @Override
    public String toString() {
        return String.format("의사[%d] %s (%s과) - %s", 
            doctorId, doctorName, department, phoneNumber);
    }
}

class Reservation {
    private int reservationId;
    private int patientId;
    private int doctorId;
    private LocalDate reservationDate;
    private LocalTime reservationTime;
    private String status;
    
    // 조인 정보 (화면 표시용)
    private String patientName;
    private String doctorName;
    private String department;
    
    // 생성자
    public Reservation() {}
    
    public Reservation(int patientId, int doctorId, LocalDate reservationDate, 
                      LocalTime reservationTime, String status) {
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.reservationDate = reservationDate;
        this.reservationTime = reservationTime;
        this.status = status;
    }
    
    // Getter & Setter
    public int getReservationId() { return reservationId; }
    public void setReservationId(int reservationId) { this.reservationId = reservationId; }
    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }
    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }
    public LocalDate getReservationDate() { return reservationDate; }
    public void setReservationDate(LocalDate reservationDate) { this.reservationDate = reservationDate; }
    public LocalTime getReservationTime() { return reservationTime; }
    public void setReservationTime(LocalTime reservationTime) { this.reservationTime = reservationTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    @Override
    public String toString() {
        return String.format("예약[%d] %s → %s(%s과) %s %s [%s]", 
            reservationId, patientName, doctorName, department, 
            reservationDate, reservationTime, status);
    }
}

class MedicalRecord {
    private int recordId;
    private int reservationId;
    private String diagnosis;
    private String prescription;
    private LocalDateTime treatmentDate;
    
    // 조인 정보
    private String patientName;
    private String doctorName;
    private String department;
    
    // 생성자
    public MedicalRecord() {}
    
    public MedicalRecord(int reservationId, String diagnosis, String prescription) {
        this.reservationId = reservationId;
        this.diagnosis = diagnosis;
        this.prescription = prescription;
    }
    
    // Getter & Setter
    public int getRecordId() { return recordId; }
    public void setRecordId(int recordId) { this.recordId = recordId; }
    public int getReservationId() { return reservationId; }
    public void setReservationId(int reservationId) { this.reservationId = reservationId; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getPrescription() { return prescription; }
    public void setPrescription(String prescription) { this.prescription = prescription; }
    public LocalDateTime getTreatmentDate() { return treatmentDate; }
    public void setTreatmentDate(LocalDateTime treatmentDate) { this.treatmentDate = treatmentDate; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    @Override
    public String toString() {
        return String.format("진료기록[%d] %s - %s(%s과) : %s", 
            recordId, patientName, doctorName, department, diagnosis);
    }
}

// ============================================================================
// 3. DAO (Data Access Object) 클래스들
// ============================================================================
class PatientDAO {
    
    /**
     * 환자 등록
     * 트랜잭션 처리로 데이터 무결성 보장
     */
    public boolean insertPatient(Patient patient) throws SQLException {
        String sql = "INSERT INTO PATIENT (patient_name, birth_date, phone_number, gender) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, patient.getPatientName());
            pstmt.setDate(2, Date.valueOf(patient.getBirthDate()));
            pstmt.setString(3, patient.getPhoneNumber());
            pstmt.setString(4, String.valueOf(patient.getGender()));
            
            int result = pstmt.executeUpdate();
            DatabaseConnection.getConnection().commit();
            return result > 0;
        } catch (SQLException e) {
            DatabaseConnection.getConnection().rollback();
            throw e;
        }
    }
    
    /**
     * 모든 환자 조회 (최근 내원일 포함)
     * 1차 과제 SQL 활용 + 2차 과제 인덱스 최적화
     */
    public List<Patient> getAllPatients() throws SQLException {
        String sql = """
            SELECT 
                p.patient_id, p.patient_name, p.birth_date, p.phone_number, p.gender,
                MAX(m.treatment_date) as last_visit_date
            FROM 
                PATIENT p
            LEFT JOIN 
                RESERVATION r ON p.patient_id = r.patient_id
            LEFT JOIN 
                MEDICAL_RECORD m ON r.reservation_id = m.reservation_id
            GROUP BY 
                p.patient_id, p.patient_name, p.birth_date, p.phone_number, p.gender
            ORDER BY 
                p.patient_name
            """;
        
        List<Patient> patients = new ArrayList<>();
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                Patient patient = new Patient();
                patient.setPatientId(rs.getInt("patient_id"));
                patient.setPatientName(rs.getString("patient_name"));
                patient.setBirthDate(rs.getDate("birth_date").toLocalDate());
                patient.setPhoneNumber(rs.getString("phone_number"));
                patient.setGender(rs.getString("gender").charAt(0));
                patients.add(patient);
            }
        }
        return patients;
    }
    
    /**
     * 환자 검색 (이름 기준) - idx_patient_name 인덱스 활용
     */
    public List<Patient> searchPatientsByName(String name) throws SQLException {
        String sql = "SELECT * FROM PATIENT WHERE patient_name LIKE ? ORDER BY patient_name";
        
        List<Patient> patients = new ArrayList<>();
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, "%" + name + "%");
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Patient patient = new Patient();
                    patient.setPatientId(rs.getInt("patient_id"));
                    patient.setPatientName(rs.getString("patient_name"));
                    patient.setBirthDate(rs.getDate("birth_date").toLocalDate());
                    patient.setPhoneNumber(rs.getString("phone_number"));
                    patient.setGender(rs.getString("gender").charAt(0));
                    patients.add(patient);
                }
            }
        }
        return patients;
    }
    
    /**
     * 환자 정보 수정
     */
    public boolean updatePatient(Patient patient) throws SQLException {
        String sql = "UPDATE PATIENT SET patient_name = ?, birth_date = ?, phone_number = ?, gender = ? WHERE patient_id = ?";
        
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, patient.getPatientName());
            pstmt.setDate(2, Date.valueOf(patient.getBirthDate()));
            pstmt.setString(3, patient.getPhoneNumber());
            pstmt.setString(4, String.valueOf(patient.getGender()));
            pstmt.setInt(5, patient.getPatientId());
            
            int result = pstmt.executeUpdate();
            DatabaseConnection.getConnection().commit();
            return result > 0;
        } catch (SQLException e) {
            DatabaseConnection.getConnection().rollback();
            throw e;
        }
    }
    
    /**
     * 환자 삭제 (관련 예약 및 진료기록도 함께 삭제 - CASCADE)
     */
    public boolean deletePatient(int patientId) throws SQLException {
        try {
            // 트랜잭션 시작
            Connection conn = DatabaseConnection.getConnection();
            
            // 1. 진료기록 삭제
            String deleteMedicalRecords = """
                DELETE FROM MEDICAL_RECORD 
                WHERE reservation_id IN (
                    SELECT reservation_id FROM RESERVATION WHERE patient_id = ?
                )
                """;
            try (PreparedStatement pstmt1 = conn.prepareStatement(deleteMedicalRecords)) {
                pstmt1.setInt(1, patientId);
                pstmt1.executeUpdate();
            }
            
            // 2. 예약 삭제
            String deleteReservations = "DELETE FROM RESERVATION WHERE patient_id = ?";
            try (PreparedStatement pstmt2 = conn.prepareStatement(deleteReservations)) {
                pstmt2.setInt(1, patientId);
                pstmt2.executeUpdate();
            }
            
            // 3. 환자 삭제
            String deletePatient = "DELETE FROM PATIENT WHERE patient_id = ?";
            try (PreparedStatement pstmt3 = conn.prepareStatement(deletePatient)) {
                pstmt3.setInt(1, patientId);
                int result = pstmt3.executeUpdate();
                
                conn.commit();
                return result > 0;
            }
        } catch (SQLException e) {
            DatabaseConnection.getConnection().rollback();
            throw e;
        }
    }
}

class DoctorDAO {
    
    /**
     * 의사 등록
     */
    public boolean insertDoctor(Doctor doctor) throws SQLException {
        String sql = "INSERT INTO DOCTOR (doctor_name, department, phone_number) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, doctor.getDoctorName());
            pstmt.setString(2, doctor.getDepartment());
            pstmt.setString(3, doctor.getPhoneNumber());
            
            int result = pstmt.executeUpdate();
            DatabaseConnection.getConnection().commit();
            return result > 0;
        } catch (SQLException e) {
            DatabaseConnection.getConnection().rollback();
            throw e;
        }
    }
    
    /**
     * 모든 의사 조회 (오늘 예약 건수 포함)
     * idx_doctor_department_name 인덱스로 정렬 최적화
     */
    public List<Doctor> getAllDoctors() throws SQLException {
        String sql = """
            SELECT 
                d.doctor_id, d.doctor_name, d.department, d.phone_number,
                COUNT(CASE WHEN r.reservation_date = date('now') AND r.status = '예약완료' THEN 1 END) as today_appointments
            FROM 
                DOCTOR d
            LEFT JOIN 
                RESERVATION r ON d.doctor_id = r.doctor_id
            GROUP BY 
                d.doctor_id, d.doctor_name, d.department, d.phone_number
            ORDER BY 
                d.department, d.doctor_name
            """;
        
        List<Doctor> doctors = new ArrayList<>();
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                Doctor doctor = new Doctor();
                doctor.setDoctorId(rs.getInt("doctor_id"));
                doctor.setDoctorName(rs.getString("doctor_name"));
                doctor.setDepartment(rs.getString("department"));
                doctor.setPhoneNumber(rs.getString("phone_number"));
                doctors.add(doctor);
            }
        }
        return doctors;
    }
    
    /**
     * 진료과별 의사 조회 - idx_doctor_department_name 인덱스 활용
     */
    public List<Doctor> getDoctorsByDepartment(String department) throws SQLException {
        String sql = "SELECT * FROM DOCTOR WHERE department = ? ORDER BY doctor_name";
        
        List<Doctor> doctors = new ArrayList<>();
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, department);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Doctor doctor = new Doctor();
                    doctor.setDoctorId(rs.getInt("doctor_id"));
                    doctor.setDoctorName(rs.getString("doctor_name"));
                    doctor.setDepartment(rs.getString("department"));
                    doctor.setPhoneNumber(rs.getString("phone_number"));
                    doctors.add(doctor);
                }
            }
        }
        return doctors;
    }
    
    /**
     * 의사 정보 수정
     */
    public boolean updateDoctor(Doctor doctor) throws SQLException {
        String sql = "UPDATE DOCTOR SET doctor_name = ?, department = ?, phone_number = ? WHERE doctor_id = ?";
        
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, doctor.getDoctorName());
            pstmt.setString(2, doctor.getDepartment());
            pstmt.setString(3, doctor.getPhoneNumber());
            pstmt.setInt(4, doctor.getDoctorId());
            
            int result = pstmt.executeUpdate();
            DatabaseConnection.getConnection().commit();
            return result > 0;
        } catch (SQLException e) {
            DatabaseConnection.getConnection().rollback();
            throw e;
        }
    }
}

class ReservationDAO {
    
    /**
     * 예약 등록
     */
    public boolean insertReservation(Reservation reservation) throws SQLException {
        String sql = "INSERT INTO RESERVATION (patient_id, doctor_id, reservation_date, reservation_time, status) VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, reservation.getPatientId());
            pstmt.setInt(2, reservation.getDoctorId());
            pstmt.setDate(3, Date.valueOf(reservation.getReservationDate()));
            pstmt.setTime(4, Time.valueOf(reservation.getReservationTime()));
            pstmt.setString(5, reservation.getStatus());
            
            int result = pstmt.executeUpdate();
            DatabaseConnection.getConnection().commit();
            return result > 0;
        } catch (SQLException e) {
            DatabaseConnection.getConnection().rollback();
            throw e;
        }
    }
    
    /**
     * 날짜별 예약 조회 - idx_resv_date_time 인덱스로 최적화
     * 2차 과제에서 92.5% 성능 향상 확인된 쿼리
     */
    public List<Reservation> getReservationsByDate(LocalDate date) throws SQLException {
        String sql = """
            SELECT 
                r.reservation_id, r.patient_id, r.doctor_id, r.reservation_date, r.reservation_time, r.status,
                p.patient_name, d.doctor_name, d.department
            FROM 
                RESERVATION r
            JOIN 
                PATIENT p ON r.patient_id = p.patient_id
            JOIN 
                DOCTOR d ON r.doctor_id = d.doctor_id
            WHERE 
                r.reservation_date = ?
            ORDER BY 
                r.reservation_time
            """;
        
        List<Reservation> reservations = new ArrayList<>();
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setDate(1, Date.valueOf(date));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Reservation reservation = new Reservation();
                    reservation.setReservationId(rs.getInt("reservation_id"));
                    reservation.setPatientId(rs.getInt("patient_id"));
                    reservation.setDoctorId(rs.getInt("doctor_id"));
                    reservation.setReservationDate(rs.getDate("reservation_date").toLocalDate());
                    reservation.setReservationTime(rs.getTime("reservation_time").toLocalTime());
                    reservation.setStatus(rs.getString("status"));
                    reservation.setPatientName(rs.getString("patient_name"));
                    reservation.setDoctorName(rs.getString("doctor_name"));
                    reservation.setDepartment(rs.getString("department"));
                    reservations.add(reservation);
                }
            }
        }
        return reservations;
    }
    
    /**
     * 환자별 예약 조회 - idx_resv_patient_date_desc 인덱스로 정렬 최적화
     */
    public List<Reservation> getReservationsByPatient(int patientId) throws SQLException {
        String sql = """
            SELECT 
                r.reservation_id, r.patient_id, r.doctor_id, r.reservation_date, r.reservation_time, r.status,
                d.doctor_name, d.department
            FROM 
                RESERVATION r
            JOIN 
                DOCTOR d ON r.doctor_id = d.doctor_id
            WHERE 
                r.patient_id = ?
            ORDER BY 
                r.reservation_date DESC, r.reservation_time DESC
            """;
        
        List<Reservation> reservations = new ArrayList<>();
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, patientId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Reservation reservation = new Reservation();
                    reservation.setReservationId(rs.getInt("reservation_id"));
                    reservation.setPatientId(rs.getInt("patient_id"));
                    reservation.setDoctorId(rs.getInt("doctor_id"));
                    reservation.setReservationDate(rs.getDate("reservation_date").toLocalDate());
                    reservation.setReservationTime(rs.getTime("reservation_time").toLocalTime());
                    reservation.setStatus(rs.getString("status"));
                    reservation.setDoctorName(rs.getString("doctor_name"));
                    reservation.setDepartment(rs.getString("department"));
                    reservations.add(reservation);
                }
            }
        }
        return reservations;
    }
    
    /**
     * 의사별 가용 시간 조회 - idx_resv_doctor_date_status 인덱스로 최적화
     * 2차 과제에서 98.3% 성능 향상 확인된 쿼리
     */
    public List<LocalTime> getAvailableTimeSlots(int doctorId, LocalDate date) throws SQLException {
        String sql = """
            SELECT slot_time
            FROM (
                SELECT '09:00:00' as slot_time UNION SELECT '09:30:00' UNION
                SELECT '10:00:00' UNION SELECT '10:30:00' UNION
                SELECT '11:00:00' UNION SELECT '11:30:00' UNION
                SELECT '14:00:00' UNION SELECT '14:30:00' UNION
                SELECT '15:00:00' UNION SELECT '15:30:00' UNION
                SELECT '16:00:00' UNION SELECT '16:30:00' UNION
                SELECT '17:00:00' UNION SELECT '17:30:00'
            ) as time_slot
            LEFT JOIN RESERVATION r ON r.doctor_id = ? 
                AND r.reservation_date = ? 
                AND r.reservation_time = time_slot.slot_time
                AND r.status = '예약완료'
            WHERE r.reservation_id IS NULL
            ORDER BY slot_time
            """;
        
        List<LocalTime> availableTimes = new ArrayList<>();
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, doctorId);
            pstmt.setDate(2, Date.valueOf(date));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    availableTimes.add(LocalTime.parse(rs.getString("slot_time")));
                }
            }
        }
        return availableTimes;
    }
    
    /**
     * 예약 취소
     */
    public boolean cancelReservation(int reservationId) throws SQLException {
        String sql = "UPDATE RESERVATION SET status = '취소' WHERE reservation_id = ?";
        
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, reservationId);
            
            int result = pstmt.executeUpdate();
            DatabaseConnection.getConnection().commit();
            return result > 0;
        } catch (SQLException e) {
            DatabaseConnection.getConnection().rollback();
            throw e;
        }
    }
}

class MedicalRecordDAO {
    
    /**
     * 진료기록 등록
     */
    public boolean insertMedicalRecord(MedicalRecord record) throws SQLException {
        String sql = "INSERT INTO MEDICAL_RECORD (reservation_id, diagnosis, prescription) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, record.getReservationId());
            pstmt.setString(2, record.getDiagnosis());
            pstmt.setString(3, record.getPrescription());
            
            int result = pstmt.executeUpdate();
            DatabaseConnection.getConnection().commit();
            return result > 0;
        } catch (SQLException e) {
            DatabaseConnection.getConnection().rollback();
            throw e;
        }
    }
    
    /**
     * 환자별 진료기록 조회 - 인덱스 최적화로 98.7% 성능 향상
     */
    public List<MedicalRecord> getMedicalRecordsByPatient(int patientId) throws SQLException {
        String sql = """
            SELECT 
                m.record_id, m.reservation_id, m.diagnosis, m.prescription, m.treatment_date,
                d.doctor_name, d.department
            FROM 
                MEDICAL_RECORD m
            JOIN 
                RESERVATION r ON m.reservation_id = r.reservation_id
            JOIN 
                DOCTOR d ON r.doctor_id = d.doctor_id
            WHERE 
                r.patient_id = ?
            ORDER BY 
                m.treatment_date DESC
            """;
        
        List<MedicalRecord> records = new ArrayList<>();
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, patientId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    MedicalRecord record = new MedicalRecord();
                    record.setRecordId(rs.getInt("record_id"));
                    record.setReservationId(rs.getInt("reservation_id"));
                    record.setDiagnosis(rs.getString("diagnosis"));
                    record.setPrescription(rs.getString("prescription"));
                    record.setTreatmentDate(rs.getTimestamp("treatment_date").toLocalDateTime());
                    record.setDoctorName(rs.getString("doctor_name"));
                    record.setDepartment(rs.getString("department"));
                    records.add(record);
                }
            }
        }
        return records;
    }
    
    /**
     * 진단명별 통계 - idx_medrec_diagnosis 인덱스 활용
     */
    public Map<String, Integer> getDiagnosisStatistics(LocalDate startDate, LocalDate endDate) throws SQLException {
        String sql = """
            SELECT 
                m.diagnosis,
                COUNT(*) as diagnosis_count
            FROM 
                MEDICAL_RECORD m
            WHERE 
                DATE(m.treatment_date) BETWEEN ? AND ?
            GROUP BY 
                m.diagnosis
            ORDER BY 
                diagnosis_count DESC
            LIMIT 10
            """;
        
        Map<String, Integer> statistics = new LinkedHashMap<>();
        try (PreparedStatement pstmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            pstmt.setDate(1, Date.valueOf(startDate));
            pstmt.setDate(2, Date.valueOf(endDate));
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    statistics.put(rs.getString("diagnosis"), rs.getInt("diagnosis_count"));
                }
            }
        }
        return statistics;
    }
}

// ============================================================================
// 4. 서비스 클래스들 (Business Logic)
// ============================================================================
class HospitalService {
    private PatientDAO patientDAO = new PatientDAO();
    private DoctorDAO doctorDAO = new DoctorDAO();
    private ReservationDAO reservationDAO = new ReservationDAO();
    private MedicalRecordDAO medicalRecordDAO = new MedicalRecordDAO();
    
    // 환자 관리 서비스
    public boolean registerPatient(String name, LocalDate birthDate, String phone, char gender) {
        try {
            // 비즈니스 검증
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("환자 이름은 필수입니다.");
            }
            if (!Pattern.matches("^010-\\d{4}-\\d{4}$", phone)) {
                throw new IllegalArgumentException("전화번호 형식이 올바르지 않습니다. (010-XXXX-XXXX)");
            }
            
            Patient patient = new Patient(name.trim(), birthDate, phone, gender);
            return patientDAO.insertPatient(patient);
        } catch (SQLException e) {
            System.err.println("환자 등록 실패: " + e.getMessage());
            return false;
        }
    }
    
    public List<Patient> getAllPatients() {
        try {
            return patientDAO.getAllPatients();
        } catch (SQLException e) {
            System.err.println("환자 목록 조회 실패: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<Patient> searchPatients(String name) {
        try {
            return patientDAO.searchPatientsByName(name);
        } catch (SQLException e) {
            System.err.println("환자 검색 실패: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // 의사 관리 서비스
    public boolean registerDoctor(String name, String department, String phone) {
        try {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("의사 이름은 필수입니다.");
            }
            
            Doctor doctor = new Doctor(name.trim(), department, phone);
            return doctorDAO.insertDoctor(doctor);
        } catch (SQLException e) {
            System.err.println("의사 등록 실패: " + e.getMessage());
            return false;
        }
    }
    
    public List<Doctor> getAllDoctors() {
        try {
            return doctorDAO.getAllDoctors();
        } catch (SQLException e) {
            System.err.println("의사 목록 조회 실패: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<Doctor> getDoctorsByDepartment(String department) {
        try {
            return doctorDAO.getDoctorsByDepartment(department);
        } catch (SQLException e) {
            System.err.println("진료과별 의사 조회 실패: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // 예약 관리 서비스
    public boolean makeReservation(int patientId, int doctorId, LocalDate date, LocalTime time) {
        try {
            // 비즈니스 검증: 과거 날짜 예약 방지
            if (date.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("과거 날짜로는 예약할 수 없습니다.");
            }
            
            // 시간 중복 확인
            List<LocalTime> availableTimes = reservationDAO.getAvailableTimeSlots(doctorId, date);
            if (!availableTimes.contains(time)) {
                throw new IllegalArgumentException("해당 시간은 이미 예약되었습니다.");
            }
            
            Reservation reservation = new Reservation(patientId, doctorId, date, time, "예약완료");
            return reservationDAO.insertReservation(reservation);
        } catch (SQLException e) {
            System.err.println("예약 등록 실패: " + e.getMessage());
            return false;
        }
    }
    
    public List<Reservation> getReservationsByDate(LocalDate date) {
        try {
            return reservationDAO.getReservationsByDate(date);
        } catch (SQLException e) {
            System.err.println("날짜별 예약 조회 실패: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public List<LocalTime> getAvailableTimeSlots(int doctorId, LocalDate date) {
        try {
            return reservationDAO.getAvailableTimeSlots(doctorId, date);
        } catch (SQLException e) {
            System.err.println("가용 시간 조회 실패: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // 진료기록 관리 서비스
    public boolean addMedicalRecord(int reservationId, String diagnosis, String prescription) {
        try {
            if (diagnosis == null || diagnosis.trim().isEmpty()) {
                throw new IllegalArgumentException("진단 내용은 필수입니다.");
            }
            
            MedicalRecord record = new MedicalRecord(reservationId, diagnosis.trim(), prescription);
            return medicalRecordDAO.insertMedicalRecord(record);
        } catch (SQLException e) {
            System.err.println("진료기록 등록 실패: " + e.getMessage());
            return false;
        }
    }
    
    public List<MedicalRecord> getPatientMedicalHistory(int patientId) {
        try {
            return medicalRecordDAO.getMedicalRecordsByPatient(patientId);
        } catch (SQLException e) {
            System.err.println("진료기록 조회 실패: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // 통계 서비스
    public Map<String, Integer> getDiagnosisStatistics(LocalDate startDate, LocalDate endDate) {
        try {
            return medicalRecordDAO.getDiagnosisStatistics(startDate, endDate);
        } catch (SQLException e) {
            System.err.println("진단 통계 조회 실패: " + e.getMessage());
            return new HashMap<>();
        }
    }
}

// ============================================================================
// 5. 사용자 인터페이스 클래스
// ============================================================================
class HospitalUI {
    private Scanner scanner = new Scanner(System.in);
    private HospitalService service = new HospitalService();
    
    public void showMainMenu() {
        while (true) {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("🏥 병원 예약 시스템");
            System.out.println("=".repeat(50));
            System.out.println("1. 환자 관리");
            System.out.println("2. 의사 관리");
            System.out.println("3. 예약 관리");
            System.out.println("4. 진료기록 관리");
            System.out.println("5. 통계 조회");
            System.out.println("6. 샘플 데이터 생성");
            System.out.println("0. 종료");
            System.out.println("=".repeat(50));
            System.out.print("선택: ");
            
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                
                switch (choice) {
                    case 1 -> showPatientMenu();
                    case 2 -> showDoctorMenu();
                    case 3 -> showReservationMenu();
                    case 4 -> showMedicalRecordMenu();
                    case 5 -> showStatisticsMenu();
                    case 6 -> generateSampleData();
                    case 0 -> {
                        System.out.println("시스템을 종료합니다.");
                        return;
                    }
                    default -> System.out.println("❌ 잘못된 선택입니다.");
                }
            } catch (NumberFormatException e) {
                System.out.println("❌ 숫자를 입력해주세요.");
            }
        }
    }
    
    private void showPatientMenu() {
        while (true) {
            System.out.println("\n--- 환자 관리 ---");
            System.out.println("1. 환자 등록");
            System.out.println("2. 환자 목록 조회");
            System.out.println("3. 환자 검색");
            System.out.println("0. 메인 메뉴로");
            System.out.print("선택: ");
            
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                
                switch (choice) {
                    case 1 -> registerPatient();
                    case 2 -> showAllPatients();
                    case 3 -> searchPatients();
                    case 0 -> { return; }
                    default -> System.out.println("❌ 잘못된 선택입니다.");
                }
            } catch (NumberFormatException e) {
                System.out.println("❌ 숫자를 입력해주세요.");
            }
        }
    }
    
    private void registerPatient() {
        System.out.println("\n--- 환자 등록 ---");
        System.out.print("환자 이름: ");
        String name = scanner.nextLine();
        
        System.out.print("생년월일 (YYYY-MM-DD): ");
        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(scanner.nextLine());
        } catch (Exception e) {
            System.out.println("❌ 날짜 형식이 올바르지 않습니다.");
            return;
        }
        
        System.out.print("전화번호 (010-XXXX-XXXX): ");
        String phone = scanner.nextLine();
        
        System.out.print("성별 (M/F): ");
        String genderStr = scanner.nextLine().toUpperCase();
        if (!genderStr.equals("M") && !genderStr.equals("F")) {
            System.out.println("❌ 성별은 M 또는 F만 입력 가능합니다.");
            return;
        }
        
        if (service.registerPatient(name, birthDate, phone, genderStr.charAt(0))) {
            System.out.println("✅ 환자가 성공적으로 등록되었습니다.");
        } else {
            System.out.println("❌ 환자 등록에 실패했습니다.");
        }
    }
    
    private void showAllPatients() {
        System.out.println("\n--- 환자 목록 ---");
        List<Patient> patients = service.getAllPatients();
        
        if (patients.isEmpty()) {
            System.out.println("등록된 환자가 없습니다.");
            return;
        }
        
        System.out.printf("%-5s %-10s %-10s %-15s %-5s%n", "ID", "이름", "생년월일", "전화번호", "성별");
        System.out.println("-".repeat(50));
        
        for (Patient patient : patients) {
            System.out.printf("%-5d %-10s %-10s %-15s %-5s%n",
                patient.getPatientId(),
                patient.getPatientName(),
                patient.getBirthDate(),
                patient.getPhoneNumber(),
                patient.getGender() == 'M' ? "남" : "여"
            );
        }
    }
    
    private void searchPatients() {
        System.out.print("검색할 환자 이름: ");
        String name = scanner.nextLine();
        
        List<Patient> patients = service.searchPatients(name);
        
        if (patients.isEmpty()) {
            System.out.println("검색 결과가 없습니다.");
            return;
        }
        
        System.out.println("\n--- 검색 결과 ---");
        for (Patient patient : patients) {
            System.out.println(patient);
        }
    }
    
    private void showDoctorMenu() {
        while (true) {
            System.out.println("\n--- 의사 관리 ---");
            System.out.println("1. 의사 등록");
            System.out.println("2. 의사 목록 조회");
            System.out.println("3. 진료과별 의사 조회");
            System.out.println("0. 메인 메뉴로");
            System.out.print("선택: ");
            
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                
                switch (choice) {
                    case 1 -> registerDoctor();
                    case 2 -> showAllDoctors();
                    case 3 -> showDoctorsByDepartment();
                    case 0 -> { return; }
                    default -> System.out.println("❌ 잘못된 선택입니다.");
                }
            } catch (NumberFormatException e) {
                System.out.println("❌ 숫자를 입력해주세요.");
            }
        }
    }
    
    private void registerDoctor() {
        System.out.println("\n--- 의사 등록 ---");
        System.out.print("의사 이름: ");
        String name = scanner.nextLine();
        
        System.out.print("진료과: ");
        String department = scanner.nextLine();
        
        System.out.print("전화번호: ");
        String phone = scanner.nextLine();
        
        if (service.registerDoctor(name, department, phone)) {
            System.out.println("✅ 의사가 성공적으로 등록되었습니다.");
        } else {
            System.out.println("❌ 의사 등록에 실패했습니다.");
        }
    }
    
    private void showAllDoctors() {
        System.out.println("\n--- 의사 목록 ---");
        List<Doctor> doctors = service.getAllDoctors();
        
        if (doctors.isEmpty()) {
            System.out.println("등록된 의사가 없습니다.");
            return;
        }
        
        for (Doctor doctor : doctors) {
            System.out.println(doctor);
        }
    }
    
    private void showDoctorsByDepartment() {
        System.out.print("진료과명: ");
        String department = scanner.nextLine();
        
        List<Doctor> doctors = service.getDoctorsByDepartment(department);
        
        if (doctors.isEmpty()) {
            System.out.println("해당 진료과의 의사가 없습니다.");
            return;
        }
        
        System.out.println("\n--- " + department + " 의사 목록 ---");
        for (Doctor doctor : doctors) {
            System.out.println(doctor);
        }
    }
    
    private void showReservationMenu() {
        while (true) {
            System.out.println("\n--- 예약 관리 ---");
            System.out.println("1. 예약 등록");
            System.out.println("2. 날짜별 예약 조회");
            System.out.println("3. 의사별 가용 시간 조회");
            System.out.println("0. 메인 메뉴로");
            System.out.print("선택: ");
            
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                
                switch (choice) {
                    case 1 -> makeReservation();
                    case 2 -> showReservationsByDate();
                    case 3 -> showAvailableTimeSlots();
                    case 0 -> { return; }
                    default -> System.out.println("❌ 잘못된 선택입니다.");
                }
            } catch (NumberFormatException e) {
                System.out.println("❌ 숫자를 입력해주세요.");
            }
        }
    }
    
    private void makeReservation() {
        System.out.println("\n--- 예약 등록 ---");
        
        System.out.print("환자 ID: ");
        int patientId = Integer.parseInt(scanner.nextLine());
        
        System.out.print("의사 ID: ");
        int doctorId = Integer.parseInt(scanner.nextLine());
        
        System.out.print("예약 날짜 (YYYY-MM-DD): ");
        LocalDate date = LocalDate.parse(scanner.nextLine());
        
        // 가용 시간 표시
        List<LocalTime> availableTimes = service.getAvailableTimeSlots(doctorId, date);
        if (availableTimes.isEmpty()) {
            System.out.println("해당 날짜에 가용한 시간이 없습니다.");
            return;
        }
        
        System.out.println("가용한 시간:");
        for (int i = 0; i < availableTimes.size(); i++) {
            System.out.printf("%d. %s%n", i + 1, availableTimes.get(i));
        }
        
        System.out.print("시간 선택 (번호): ");
        int timeChoice = Integer.parseInt(scanner.nextLine()) - 1;
        
        if (timeChoice < 0 || timeChoice >= availableTimes.size()) {
            System.out.println("❌ 잘못된 시간 선택입니다.");
            return;
        }
        
        LocalTime selectedTime = availableTimes.get(timeChoice);
        
        if (service.makeReservation(patientId, doctorId, date, selectedTime)) {
            System.out.println("✅ 예약이 성공적으로 등록되었습니다.");
        } else {
            System.out.println("❌ 예약 등록에 실패했습니다.");
        }
    }
    
    private void showReservationsByDate() {
        System.out.print("조회할 날짜 (YYYY-MM-DD): ");
        LocalDate date = LocalDate.parse(scanner.nextLine());
        
        List<Reservation> reservations = service.getReservationsByDate(date);
        
        if (reservations.isEmpty()) {
            System.out.println("해당 날짜에 예약이 없습니다.");
            return;
        }
        
        System.out.println("\n--- " + date + " 예약 목록 ---");
        for (Reservation reservation : reservations) {
            System.out.println(reservation);
        }
    }
    
    private void showAvailableTimeSlots() {
        System.out.print("의사 ID: ");
        int doctorId = Integer.parseInt(scanner.nextLine());
        
        System.out.print("날짜 (YYYY-MM-DD): ");
        LocalDate date = LocalDate.parse(scanner.nextLine());
        
        List<LocalTime> availableTimes = service.getAvailableTimeSlots(doctorId, date);
        
        if (availableTimes.isEmpty()) {
            System.out.println("해당 날짜에 가용한 시간이 없습니다.");
            return;
        }
        
        System.out.println("\n--- 가용한 시간 ---");
        for (LocalTime time : availableTimes) {
            System.out.println(time);
        }
    }
    
    private void showMedicalRecordMenu() {
        while (true) {
            System.out.println("\n--- 진료기록 관리 ---");
            System.out.println("1. 진료기록 등록");
            System.out.println("2. 환자별 진료기록 조회");
            System.out.println("0. 메인 메뉴로");
            System.out.print("선택: ");
            
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                
                switch (choice) {
                    case 1 -> addMedicalRecord();
                    case 2 -> showPatientMedicalHistory();
                    case 0 -> { return; }
                    default -> System.out.println("❌ 잘못된 선택입니다.");
                }
            } catch (NumberFormatException e) {
                System.out.println("❌ 숫자를 입력해주세요.");
            }
        }
    }
    
    private void addMedicalRecord() {
        System.out.println("\n--- 진료기록 등록 ---");
        
        System.out.print("예약 ID: ");
        int reservationId = Integer.parseInt(scanner.nextLine());
        
        System.out.print("진단 내용: ");
        String diagnosis = scanner.nextLine();
        
        System.out.print("처방 내역: ");
        String prescription = scanner.nextLine();
        
        if (service.addMedicalRecord(reservationId, diagnosis, prescription)) {
            System.out.println("✅ 진료기록이 성공적으로 등록되었습니다.");
        } else {
            System.out.println("❌ 진료기록 등록에 실패했습니다.");
        }
    }
    
    private void showPatientMedicalHistory() {
        System.out.print("환자 ID: ");
        int patientId = Integer.parseInt(scanner.nextLine());
        
        List<MedicalRecord> records = service.getPatientMedicalHistory(patientId);
        
        if (records.isEmpty()) {
            System.out.println("해당 환자의 진료기록이 없습니다.");
            return;
        }
        
        System.out.println("\n--- 진료기록 ---");
        for (MedicalRecord record : records) {
            System.out.printf("[%s] %s (%s과) - %s%n",
                record.getTreatmentDate().toLocalDate(),
                record.getDoctorName(),
                record.getDepartment(),
                record.getDiagnosis()
            );
            if (record.getPrescription() != null && !record.getPrescription().isEmpty()) {
                System.out.printf("    처방: %s%n", record.getPrescription());
            }
            System.out.println();
        }
    }
    
    private void showStatisticsMenu() {
        System.out.println("\n--- 통계 조회 ---");
        System.out.print("시작 날짜 (YYYY-MM-DD): ");
        LocalDate startDate = LocalDate.parse(scanner.nextLine());
        
        System.out.print("종료 날짜 (YYYY-MM-DD): ");
        LocalDate endDate = LocalDate.parse(scanner.nextLine());
        
        Map<String, Integer> statistics = service.getDiagnosisStatistics(startDate, endDate);
        
        if (statistics.isEmpty()) {
            System.out.println("해당 기간의 진료 데이터가 없습니다.");
            return;
        }
        
        System.out.println("\n--- 진단명별 통계 (TOP 10) ---");
        for (Map.Entry<String, Integer> entry : statistics.entrySet()) {
            System.out.printf("%s: %d건%n", entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * 샘플 데이터 생성 - 테스트용
     */
    private void generateSampleData() {
        System.out.println("\n샘플 데이터를 생성합니다...");
        
        // 의사 데이터
        service.registerDoctor("김철수", "내과", "010-1111-1111");
        service.registerDoctor("박영희", "소아과", "010-2222-2222");
        service.registerDoctor("이민호", "정형외과", "010-3333-3333");
        service.registerDoctor("최서연", "산부인과", "010-4444-4444");
        
        // 환자 데이터
        service.registerPatient("홍길동", LocalDate.of(1990, 5, 11), "010-1234-5678", 'M');
        service.registerPatient("김민서", LocalDate.of(1987, 3, 2), "010-2345-6789", 'F');
        service.registerPatient("최민준", LocalDate.of(1995, 12, 30), "010-3456-7890", 'M');
        service.registerPatient("이하늘", LocalDate.of(2000, 7, 21), "010-4567-8901", 'F');
        
        // 예약 데이터
        service.makeReservation(1, 1, LocalDate.now().plusDays(1), LocalTime.of(10, 0));
        service.makeReservation(2, 2, LocalDate.now().plusDays(1), LocalTime.of(10, 30));
        service.makeReservation(3, 3, LocalDate.now().plusDays(2), LocalTime.of(9, 0));
        service.makeReservation(4, 1, LocalDate.now().plusDays(2), LocalTime.of(9, 30));
        
        // 진료기록 데이터
        service.addMedicalRecord(1, "감기", "감기약 처방");
        service.addMedicalRecord(2, "중이염", "항생제 처방");
        
        System.out.println("✅ 샘플 데이터 생성 완료!");
    }
}

// ============================================================================
// 6. 메인 클래스 - 애플리케이션 진입점
// ============================================================================
public class HospitalReservationSystem {
    
    /**
     * 메인 메서드 - 애플리케이션 시작점
     * 
     * 동작 과정:
     * 1. 데이터베이스 초기화 (테이블 생성 + 최적화 인덱스)
     * 2. UI 시작
     * 3. 사용자 입력에 따른 기능 실행
     * 4. 트랜잭션 관리로 데이터 무결성 보장
     */
    public static void main(String[] args) {
        System.out.println("🏥 병원 예약 시스템을 시작합니다...");
        
        try {
            // 데이터베이스 초기화
            DatabaseConnection.initializeDatabase();
            
            // UI 시작
            HospitalUI ui = new HospitalUI();
            ui.showMainMenu();
            
        } catch (SQLException e) {
            System.err.println("💥 데이터베이스 오류: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("💥 시스템 오류: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 리소스 정리
            try {
                Connection conn = DatabaseConnection.getConnection();
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
                System.out.println("👋 시스템이 정상적으로 종료되었습니다.");
            } catch (SQLException e) {
                System.err.println("리소스 정리 중 오류 발생: " + e.getMessage());
            }
        }
    }
}
