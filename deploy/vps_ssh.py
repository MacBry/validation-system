#!/usr/bin/env python3
"""
VPS SSH helper - executes commands on remote server via SSH.
Usage: python vps_ssh.py "command to run"

SECURITY: All credentials are read from environment variables.
Set the following before use:
  VPS_HOST - Server hostname or IP
  VPS_PORT - SSH port (default: 22)
  VPS_USER - SSH username
  VPS_PASS - SSH password (prefer SSH key auth instead)
"""
import sys
import os
import paramiko
import time

VPS_HOST = os.environ.get("VPS_HOST", "")
VPS_PORT = int(os.environ.get("VPS_PORT", "22"))
VPS_USER = os.environ.get("VPS_USER", "")
VPS_PASS = os.environ.get("VPS_PASS", "")

def _validate_config():
    """Validate that required environment variables are set."""
    missing = []
    if not VPS_HOST:
        missing.append("VPS_HOST")
    if not VPS_USER:
        missing.append("VPS_USER")
    if not VPS_PASS:
        missing.append("VPS_PASS")
    if missing:
        print(f"[ERROR] Missing required environment variables: {', '.join(missing)}")
        print("Set them before running this script:")
        print("  export VPS_HOST=your-server-ip")
        print("  export VPS_USER=your-username")
        print("  export VPS_PASS=your-password")
        sys.exit(1)

def run_command(cmd, timeout=120):
    _validate_config()
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    # Load system known hosts for host key verification
    try:
        client.load_system_host_keys()
    except Exception:
        pass
    try:
        client.connect(VPS_HOST, port=VPS_PORT, username=VPS_USER, password=VPS_PASS, timeout=15)
        stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
        exit_code = stdout.channel.recv_exit_status()
        out = stdout.read().decode('utf-8', errors='replace')
        err = stderr.read().decode('utf-8', errors='replace')
        if out:
            print(out, end='')
        if err:
            print(f"[STDERR] {err}", end='')
        print(f"\n[EXIT CODE: {exit_code}]")
        return exit_code
    except Exception as e:
        print(f"[ERROR] {e}")
        return 1
    finally:
        client.close()

def upload_file(local_path, remote_path):
    _validate_config()
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    try:
        client.load_system_host_keys()
    except Exception:
        pass
    try:
        client.connect(VPS_HOST, port=VPS_PORT, username=VPS_USER, password=VPS_PASS, timeout=15)
        sftp = client.open_sftp()
        sftp.put(local_path, remote_path)
        sftp.close()
        print(f"[UPLOADED] {local_path} -> {remote_path}")
        return 0
    except Exception as e:
        print(f"[ERROR] {e}")
        return 1
    finally:
        client.close()

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python vps_ssh.py <command>")
        print("       python vps_ssh.py --upload <local_path> <remote_path>")
        sys.exit(1)

    if sys.argv[1] == "--upload":
        if len(sys.argv) != 4:
            print("Usage: python vps_ssh.py --upload <local_path> <remote_path>")
            sys.exit(1)
        sys.exit(upload_file(sys.argv[2], sys.argv[3]))
    else:
        cmd = " ".join(sys.argv[1:])
        sys.exit(run_command(cmd))
