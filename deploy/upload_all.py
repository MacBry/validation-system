#!/usr/bin/env python3
"""Upload all deployment files to VPS."""
import paramiko
import os
import sys
import stat

VPS_HOST = "83.168.89.191"
VPS_PORT = 22
VPS_USER = "root"
VPS_PASS = "${DB_PASSWORD}"
REMOTE_APP_DIR = "/opt/app"

# Base directory for local files
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(BASE_DIR)

FILES_TO_UPLOAD = [
    # (local_relative_to_deploy, remote_path)
    ("docker-compose.yml", f"{REMOTE_APP_DIR}/docker-compose.yml"),
    ("Dockerfile.prod", f"{REMOTE_APP_DIR}/Dockerfile.prod"),
    ("deploy.sh", f"{REMOTE_APP_DIR}/deploy.sh"),
    ("nginx/nginx.conf", f"{REMOTE_APP_DIR}/nginx/nginx.conf"),
    ("nginx/conf.d/default.conf", f"{REMOTE_APP_DIR}/nginx/conf.d/default.conf"),
]

# JAR from target directory
JAR_LOCAL = os.path.join(PROJECT_DIR, "target", "validation-system.jar")
JAR_REMOTE = f"{REMOTE_APP_DIR}/validation-system.jar"

# Python scripts from project root (if they exist)
PYTHON_SCRIPTS = [
    ("fill_validation_template.py", f"{REMOTE_APP_DIR}/fill_validation_template.py"),
    ("generate_3d_animation.py", f"{REMOTE_APP_DIR}/generate_3d_animation.py"),
]

def upload():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(VPS_HOST, port=VPS_PORT, username=VPS_USER, password=VPS_PASS, timeout=15)
    sftp = client.open_sftp()

    # Create remote directories
    for d in [f"{REMOTE_APP_DIR}/nginx/conf.d"]:
        try:
            sftp.stat(d)
        except FileNotFoundError:
            # Create parent dirs
            parts = d.split("/")
            for i in range(2, len(parts) + 1):
                path = "/".join(parts[:i])
                try:
                    sftp.stat(path)
                except FileNotFoundError:
                    sftp.mkdir(path)
                    print(f"  Created dir: {path}")

    # Upload deployment files
    print("\n--- Uploading deployment files ---")
    for local_rel, remote in FILES_TO_UPLOAD:
        local_path = os.path.join(BASE_DIR, local_rel)
        if os.path.exists(local_path):
            sftp.put(local_path, remote)
            size = os.path.getsize(local_path)
            print(f"  OK: {local_rel} -> {remote} ({size:,} bytes)")
        else:
            print(f"  SKIP: {local_rel} (not found)")

    # Upload Python scripts from project root
    print("\n--- Uploading Python scripts ---")
    for local_name, remote in PYTHON_SCRIPTS:
        local_path = os.path.join(PROJECT_DIR, local_name)
        if os.path.exists(local_path):
            sftp.put(local_path, remote)
            size = os.path.getsize(local_path)
            print(f"  OK: {local_name} -> {remote} ({size:,} bytes)")
        else:
            print(f"  SKIP: {local_name} (not found in project root)")

    # Upload JAR (largest file)
    print("\n--- Uploading JAR (this may take a few minutes) ---")
    if os.path.exists(JAR_LOCAL):
        size_mb = os.path.getsize(JAR_LOCAL) / (1024 * 1024)
        print(f"  Uploading: {JAR_LOCAL} ({size_mb:.1f} MB)...")
        sftp.put(JAR_LOCAL, JAR_REMOTE, callback=lambda transferred, total:
            print(f"\r  Progress: {transferred/total*100:.0f}% ({transferred/(1024*1024):.0f}/{total/(1024*1024):.0f} MB)", end="", flush=True)
        )
        print(f"\n  OK: validation-system.jar uploaded ({size_mb:.1f} MB)")
    else:
        print(f"  ERROR: JAR not found at {JAR_LOCAL}")
        print("  Run 'mvn clean package -DskipTests' first!")
        sftp.close()
        client.close()
        sys.exit(1)

    # Make deploy.sh executable
    stdin, stdout, stderr = client.exec_command(f"chmod +x {REMOTE_APP_DIR}/deploy.sh")
    stdout.channel.recv_exit_status()

    sftp.close()
    client.close()
    print("\n--- Upload complete! ---")
    print(f"Run deployment: ssh root@{VPS_HOST} 'bash {REMOTE_APP_DIR}/deploy.sh'")

if __name__ == "__main__":
    upload()
