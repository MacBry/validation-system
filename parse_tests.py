import os
import xml.etree.ElementTree as ET

with open("test_failures.txt", "w", encoding="utf-8") as out:
    report_dir = "target/surefire-reports"
    for filename in os.listdir(report_dir):
        if filename.endswith(".xml"):
            filepath = os.path.join(report_dir, filename)
            try:
                tree = ET.parse(filepath)
                testsuite = tree.getroot()
                failures = int(testsuite.attrib.get('failures', 0))
                errors = int(testsuite.attrib.get('errors', 0))
                if failures > 0 or errors > 0:
                    out.write(f"[{testsuite.attrib['name']}] Failures: {failures}, Errors: {errors}\n")
                    for testcase in testsuite.findall('testcase'):
                        failure = testcase.find('failure')
                        error = testcase.find('error')
                        if failure is not None:
                            out.write(f"  FAILURE: {testcase.attrib['name']} - {failure.attrib.get('message', 'No message')}\n")
                        if error is not None:
                            out.write(f"  ERROR: {testcase.attrib['name']} - {error.attrib.get('message', 'No message')}\n")
            except Exception as e:
                pass
