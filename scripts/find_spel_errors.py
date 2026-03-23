
import re

log_path = r"c:\Users\macie\Desktop\Day zero\validation-system-v2.11.0-COMPLETE\logs\validation-system-dev.log"
with open(log_path, 'r', encoding='utf-8', errors='ignore') as f:
    content = f.read()

# Find all EL1008E errors
matches = re.findall(r"EL1008E: Property or field '(.*?)' cannot be found", content)
if matches:
    print(f"Found {len(matches)} SpEL errors.")
    # Show last 10 unique ones
    unique_matches = list(dict.fromkeys(matches))
    for m in unique_matches[-10:]:
        print(f"Error Property: {m}")
else:
    print("No SpEL errors found in the log.")
