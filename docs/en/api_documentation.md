# API Documentation: Validation Cold Control (VCC)

## 1. General Information
The current version of the VCC system primarily utilizes the Server-Side Rendering (SSR) model with Thymeleaf. However, key dynamic features are handled by dedicated REST and AJAX endpoints.

---

## 2. Notification API

### 2.1 Fetch All Notifications
Returns a list of active alerts for the logged-in user (validations, calibrations, passwords).

- **URL**: `/api/notifications`
- **Method**: `GET`
- **Authorization**: Required (Logged-in user)
- **Response Format**: `JSON`

**Example Response:**
```json
[
  {
    "type": "VALIDATION",
    "title": "Validation deadline approaching",
    "message": "Device CH-01 requires validation in 15 days.",
    "link": "/measurements/1",
    "daysLeft": 15,
    "severity": "WARNING"
  },
  {
    "type": "PASSWORD",
    "title": "Password expiring",
    "message": "Your password will expire in 3 days.",
    "link": "/profile/change-password",
    "daysLeft": 3,
    "severity": "DANGER"
  }
]
```

---

## 3. Search AJAX API

### 3.1 Global Search
Returns a HTML fragment with search results for devices, loggers, and validations.

- **URL**: `/search`
- **Method**: `GET`
- **Parameters**: 
    - `q` (string, min. 2 characters): Search query phrase.
- **Response**: `HTML (Thymeleaf Fragment)`

**Purpose**: Used by the dynamic search field on the Dashboard to display results immediately without reloading the entire page.

---

## 4. Future API Development
The system is prepared for expansion into a full REST API for:
- Integration with external LIMS/ERP systems.
- Mobile application support.
- Automatic data import from cloud systems (Cloud-to-Cloud).
