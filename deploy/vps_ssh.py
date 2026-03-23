#!/usr/bin/env python3
"""
VPS SSH helper - executes commands on remote server via password auth.
Usage: python vps_ssh.py "command to run"
"""
import sys
import paramiko
import time

VPS_HOST = "${TSA_IP}"
VPS_PORT = 22
VPS_USER = "root"
VPS_PASS = "${DB_PASSWORD}"

def run_command(cmd, timeout=120):
    import io, sys
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
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
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
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
