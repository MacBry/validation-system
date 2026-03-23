# Functional Specification (FS): Validation Cold Control (VCC)

## 1. Introduction
This Functional Specification (FS) document details the functionalities of the VCC system and their implementation, serving as a response to the requirements outlined in the User Requirements Specification (URS).

---

## 2. Device & Logger Management Functions

### 2.1 Device Registry (FS-FUNC-01)
- **Implementation (UR-FUNC-01)**: User interface at `/devices` allows adding, editing, and deleting devices.
- **Data**: Storage of inventory number, name, chamber type, and operational temperature range.
- **Associations**: Each device is assigned to a specific `Department`.

### 2.2 Calibration Verification (FS-FUNC-02)
- **Implementation (UR-FUNC-02)**: When assigning loggers to a validation, the system invokes the `CalibrationService`, which checks the `validUntil` field for each logger.
- **Block**: If `validUntil` is earlier than the planned validation date, the system displays an error message and prevents saving.

---

## 3. Data Processing & Calculation Engine

### 3.1 File Import & Decoding (FS-FUNC-03)
- **Implementation (UR-FUNC-03)**: The system utilizes a dedicated `Vi2Decoder` (supporting the OLE2 standard), which extracts raw measurement data from sidecar files.
- **Filtering**: Data is cleansed of erroneous readings (e.g., sensor errors) before calculations begin.

### 3.2 Statistical Engine (FS-FUNC-04)
- **MKT (Mean Kinetic Temperature)**: Calculated according to the Arrhenius equation (default activation energy 83.144 kJ/mol).
- **Stability/Homogeneity**: Determined as differences between channels and deviations from the setpoint temperature over time.

---

## 4. Reporting & Approval

### 4.1 PDF Report Generation (FS-FUNC-05)
- **Implementation (UR-FUNC-04)**: The `iText 7` library assembles the final report, pulling data from the `Validation` entity and associated `MeasurementSeries`.
- **Charts**: `JFreeChart` generates temperature profiles, which are injected into the PDF document as SVG/PNG images.

### 4.2 Approval Workflow (FS-FUNC-06)
- **Implementation (UR-COMP-02)**: The `APPROVED` status can only be granted by a user with `ROLE_APPROVER` permissions via a dedicated approval form. After this action, the `sealed` field is set to `true`, preventing further editing.

---

## 5. Security & Integrity

### 5.1 Audit Trail (FS-SEC-01)
- **Implementation (UR-COMP-01)**: Utilization of Hibernate Envers. The system creates an entry in the `REVINFO` table for each transaction, and old data versions are copied to `*_AUD` tables.
- **Interface**: Audit logs are accessible at `/admin/audit-log`.

### 5.2 Multi-tenancy (FS-SEC-02)
- **Implementation (UR-SEC-01)**: Utilization of `Filter` in Spring Security and JPA mechanisms to automatically inject the `WHERE company_id = ?` condition into SQL queries.
