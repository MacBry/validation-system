
import re
from datetime import datetime, timedelta

log_path = r"c:\Users\macie\Desktop\Day zero\validation-system-v2.11.0-COMPLETE\logs\validation-system-dev.log"
now = datetime.now()
target_time = now - timedelta(minutes=15)

# Format: 2026-03-08 10:45:08
log_pattern = re.compile(r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})")

errors = []
with open(log_path, 'r', encoding='utf-8', errors='ignore') as f:
    for line in f:
        m = log_pattern.match(line)
        if m:
            log_time = datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S")
            if log_time > target_time:
                # This line is recent. Look for SpEL error in following lines (simplified)
                pass 

# Actually let's just grep the last 5000 lines of the log
with open(log_path, 'r', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()[-5000:]

content = "".join(lines)
matches = re.findall(r"EL1008E: Property or field '(.*?)' cannot be found", content)
if matches:
    unique_matches = list(dict.fromkeys(matches))
    for m in unique_matches:
        print(f"RECENT Error Property: {m}")
else:
    print("No RECENT SpEL errors found.")
