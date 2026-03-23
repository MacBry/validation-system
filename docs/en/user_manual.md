# User Manual: Validation Cold Control (VCC)

## 1. Introduction
The **Validation Cold Control (VCC)** system is designed for monitoring, managing, and validating cooling devices and temperature data loggers. This document describes the basic operations a user can perform within the system.

---

## 2. Getting Started

### 2.1 Logging In
1. Navigate to the login page.
2. Enter your **username** (or email) and **password**.
3. Click the **Login** button.

> [!NOTE]
> If you have forgotten your password, use the "Forgot Password" option on the login screen.

### 2.2 Dashboard
Upon logging in, you will see the Dashboard, which contains:
- **Summary Tiles**: Number of devices, loggers, and active validations.
- **Notifications (🔔)**: The bell icon in the top right corner informs you of upcoming validation deadlines, calibrations, or password expiration.
- **Search**: Allows you to quickly find a device by name or inventory number.

---

## 3. Resource Management

### 3.1 Cooling Devices
This section allows for the inventory of fridges, freezers, and other cooling chambers.
- **Adding**: Click "Add Device", fill in the name, model, inventory number, and select the chamber type.
- **Details**: By clicking on a device's name, you can view its full history, including assigned validations.

### 3.2 Temperature Loggers
- **Adding a Logger**: Enter the serial number and model.
- **Calibration**: Each logger must have a current calibration certificate. The system reminds you of the upcoming calibration expiration date 60 days before the deadline.

---

## 4. Validation Process
This is the most critical process in the application, divided into stages:

### Step 1: Create Validation
Select the device you wish to validate and choose "New Validation".

### Step 2: Measurement Configuration
Identify the loggers used during the measurement (e.g., in the corners of the chamber and the center). The system automatically verifies if the loggers have valid calibrations.

### Step 3: Data Import
Upload measurement data files (`.vi2` format). The system automatically processes the data and prepares statistics.

### Step 4: Statistical Analysis
The system calculates:
- Mean, minimum, and maximum temperature.
- Standard deviation.
- Stability and homogeneity of temperature distribution.

### Step 5: Approval
After verifying the data's accuracy, the validation is submitted for approval. Upon approval, a PDF report is generated.

---

## 5. Notification System
An alert system is located in the top bar:
- 🔴 **Red (Danger)**: Deadline has passed or less than 7 days remain.
- 🟡 **Yellow (Warning)**: Deadline is approaching (e.g., 30 days until validation).

Click the bell icon to see details and navigate directly to the item requiring attention.

---

## 6. Profile Settings
Users can at any time:
- Change their password.
- Update contact information.
- Check their login history.
