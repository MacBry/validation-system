# Precise .vi2 File Decoder (Version 2)

This document describes the flexible decoding algorithm for binary files from TESTO loggers (OLE2/CFB format), decoupled from hardcoded point limits and fixed intervals.

## Formatting Basics
A `.vi2` file is an OLE2-type container (similar to legacy MS Office files). It consists of hidden streams (files within a file). Main device data is collected in a folder named based on the type (e.g., `19788/` or `52762/`).

To create a flawless, universal parser, we must read data from **two coupled streams**:

---

## 1. `summary` Stream
**Purpose:** Replaces "guessing" session lengths and manually entering intervals.

**Structure (Offset Analysis):**
The stream typically has a length of `36 bytes`. All variables are read as **4-byte unsigned integers (Little-Endian Uint32)**.

- **Offset `12-15`**: *Measurement Count*. 
  - This value uniquely defines how many readings are in the file. This eliminates the need to search for "padding" (null bytes) at the end of the `values` stream. The software knows exactly where to end the loop and does not "cut" data.
- **Offset `28-31`**: *Measurement Interval*.
  - Stored in milliseconds (ms). 
  - Example: A value of `10800000` = `3 hours` / `300000` = `5 minutes`. Instead of hardcoding `+3h`, the application reads this field, converts it to seconds, and iteratively adds it to the initial time.

---

## 2. `t17b` Configuration Stream
**Purpose:** Reliable identification of hardware (Serial Number) without relying on the uploaded filename.

Previously, logic extracted the serial number from the filename mask (e.g., `_58980778_...`). This was a critical vulnerability – renaming the file to e.g., `data.vi2` caused save errors and prevented linking data to the correct chamber in the database.

**Structure (Offset Analysis):**
- **Offset `13`**: *Logger Serial Number (ASCII String)*
  - From this byte, the serial number assigned to the device during production/configuration is stored in plain text (e.g., `58980778`). Simply loading the truncated byte array and casting directly to text (UTF-8 / ASCII) is sufficient.

---

## 3. `data/values` Stream
**Purpose:** Recovering real temperatures and the absolute startup time of the logger.

**Structure (Offset Analysis):**
- The system skips the **first 4 bytes** (Section Header).
- It then reads 8-byte packets in a loop (`BYTES_PER_MEASUREMENT = 8`).
  - **Bytes `0-3`:** Temperature (Float32 / IEEE 754).
  - **Bytes `4-7`:** Time Metadata (Logger Tick Clock).

> **Key Detail:** TESTO loggers can produce "shifted" or erroneous intervals for points `2` to `N` in a series. However, the metadata **for measurement #1 is always correct and synchronized with UTC time at the moment the START button is pressed**.

**Startup Time Recovery Algorithm (Epoch 1961):**
Uses the "TESTO Epoch," i.e., the beginning of time measurement for this system: `1961-07-09 01:30:00`.
1. Read 4 bytes of metadata T from the first measurement.
2. Days since epoch: `T / 131072` (Where 131072 is the daily quartz Tick count).
3. Add seconds representing the "remainder" from fractional day division to the obtained calendar days.
4. Freeze this result as the base **Start Time (T0)** for the entire series.

---

## Final (New) Decoding Logic

Instead of hardcoded frames in the code, the universal method calculates final data for tables based on this principle:

```text
STEP 1: (Read Summary)
- Assign READINGS = bytes[12..15]
- Assign INTERVAL_SECONDS = bytes[28..31] / 1000

STEP 2: (Read T0 from data/values)
- Go to bytes [8..11] in the values stream and decode the base Tick
- Calculate START_DATE (T0) using the "1961 Epoch" method

STEP 3: (Measurement Loop)
- Run a loop from i=0 to READINGS
- Temperature for loop [i] is 4 bytes directly from the stream (at the shifted offset)
- Time for loop [i] is: T0 + (i * INTERVAL_SECONDS)
```

## What else can make the code more flexible?
To make the parser 100% robust, one can examine the third most important stream in the OLE2 format:

1. **`data/timezone` (188 bytes):**
  - A common problem with registrations for international servers is time zones (Timezone / DST). Analysis of this stream proved that it stores the full hardware local time configuration in Windows standard.
  - **Offset 16 (Int32)**: Value `-60` (Bias in minutes. -60 min = UTC+1).
  - **Around Offset 20+**: Plain text in `UTF-16LE` format (e.g., `"Central Europe Standard Time"` and `"Central Europe Summer Time"`).
  - This allows the VI2 system to flawlessly convert raw hardware ticks from UTC to the user's exact local time regardless of which continent the logger was started on. This should be retrieved and this `Bias` dynamically added to the time calculated from the first point on the time grid.
