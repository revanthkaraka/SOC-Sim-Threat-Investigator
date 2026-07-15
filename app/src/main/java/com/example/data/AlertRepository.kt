package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AlertRepository(private val alertDao: AlertDao) {
    val allAlerts: Flow<List<AlertEntity>> = alertDao.getAllAlerts()

    fun getAlertById(id: Int): Flow<AlertEntity?> = alertDao.getAlertById(id)

    suspend fun insertAlert(alert: AlertEntity) = alertDao.insertAlert(alert)

    suspend fun updateAlert(alert: AlertEntity) = alertDao.updateAlert(alert)

    suspend fun clearAll() = alertDao.clearAllAlerts()

    suspend fun prepopulateIfEmpty() {
        // Run check to see if database is empty, if so, populate
        val currentAlerts = allAlerts.first()
        if (currentAlerts.isEmpty()) {
            val initialAlerts = listOf(
                AlertEntity(
                    title = "Brute Force Detection on SSH",
                    severity = "CRITICAL",
                    status = "New",
                    classification = "Unassigned",
                    timestamp = "2026-07-15 06:05:00 UTC",
                    sourceIp = "192.168.1.150",
                    destinationPort = "22",
                    mitreTactic = "Credential Access",
                    mitreTechnique = "T1110 (Brute Force)",
                    blastRadiusPath = "External Attacker IP [192.168.1.150] ➔ Edge Firewall ➔ SSH Gateway (Under Attack) ⤏ Internal Database (High Risk)",
                    remediationScript = """
# Python SOAR Playbook: Block SSH Brute Force IP
import subprocess
import sys

def block_ip(ip):
    print(f"[*] Appending iptables firewall filter for brute-force source: {ip}")
    try:
        # Drop all SSH packet flows from the attacker
        cmd = ["iptables", "-A", "INPUT", "-s", ip, "-p", "tcp", "--dport", "22", "-j", "DROP"]
        print(f"[+] Commands staged: {' '.join(cmd)}")
        print(f"[+] Successfully blocked IP {ip} from port 22.")
    except Exception as e:
        print(f"[-] Execution failed: {e}")

if __name__ == '__main__':
    block_ip("192.168.1.150")
""".trimIndent(),
                    rawLog = """{
  "timestamp": "2026-07-15T06:05:00Z",
  "event_type": "ssh_login_failure",
  "src_ip": "192.168.1.150",
  "dest_ip": "10.0.0.5",
  "dest_port": 22,
  "user": "root",
  "auth_method": "password",
  "status": "failed",
  "message": "Failed password for invalid user admin from 192.168.1.150 port 49152 ssh2",
  "retry_count": 47,
  "interval_seconds": 12
}"""
                ),
                AlertEntity(
                    title = "Suspicious Internal Port Scan",
                    severity = "HIGH",
                    status = "New",
                    classification = "Unassigned",
                    timestamp = "2026-07-15 06:12:00 UTC",
                    sourceIp = "10.0.0.42",
                    destinationPort = "Multiple",
                    mitreTactic = "Discovery",
                    mitreTechnique = "T1046 (Network Service Discovery)",
                    blastRadiusPath = "Rogue Internal Host [10.0.0.42] ➔ Core Switch ➔ Subnet Segments (Scanned) ⤏ Domain Controller Active Directory",
                    remediationScript = """
# Python SOAR Playbook: Quarantine Scanning Endpoint
import urllib.request
import json

def quarantine_host(ip):
    print(f"[*] Sending isolation payload for host: {ip}")
    # Simulates dispatching VLAN relocation command to the access switch via REST API
    payload = {"host_ip": ip, "vlan": "999_QUARANTINE_VLAN", "port_action": "RESTRICTED"}
    print(f"[+] API Call: POST /api/v1/network/quarantine with body: {json.dumps(payload)}")
    print(f"[+] Successfully isolated {ip} in security containment block.")

if __name__ == '__main__':
    quarantine_host("10.0.0.42")
""".trimIndent(),
                    rawLog = """{
  "timestamp": "2026-07-15T06:12:00Z",
  "event_type": "firewall_anomaly",
  "src_ip": "10.0.0.42",
  "dest_ip": "10.0.0.254",
  "scan_type": "SYN Stealth Scan",
  "ports_probed": [21, 22, 23, 80, 443, 445, 3389, 8080],
  "action": "BLOCKED",
  "severity": "HIGH",
  "packets_count": 1240,
  "signature": "ET SCAN Host-to-Host Portscan"
}"""
                ),
                AlertEntity(
                    title = "Phishing Link Clicked",
                    severity = "HIGH",
                    status = "New",
                    classification = "Unassigned",
                    timestamp = "2026-07-15 06:18:00 UTC",
                    sourceIp = "10.0.2.15",
                    destinationPort = "80",
                    mitreTactic = "Initial Access",
                    mitreTechnique = "T1566 (Phishing)",
                    blastRadiusPath = "Phishing Email ➔ Corporate Inbox ➔ User Workstation [10.0.2.15] ➔ Malicious C2 IP [185.112.146.8]",
                    remediationScript = """
# Python SOAR Playbook: Revoke Active User Sessions
import urllib.request
import json

def revoke_sign_in(user_principal):
    print(f"[*] Disabling compromised user credential tokens: {user_principal}")
    # Simulates invoking Microsoft Graph Identity API to revoke user sessions
    print("[+] Microsoft Graph Endpoint Triggered: /users/jdoe@company.com/revokeSignInSessions")
    print("[+] User forced to re-authenticate and MFA token regenerated.")

if __name__ == '__main__':
    revoke_sign_in("jdoe@company.com")
""".trimIndent(),
                    rawLog = """{
  "timestamp": "2026-07-15T06:18:00Z",
  "event_type": "dns_and_proxy_request",
  "src_ip": "10.0.2.15",
  "hostname": "secure-update-bank-login.com",
  "uri": "/login.php?user=jdoe",
  "category": "Malicious / Phishing",
  "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0",
  "action": "ALLOWED",
  "referrer": "https://mail.company.local/inbox/message_9482.html",
  "dns_resolved_ip": "185.112.146.8"
}"""
                ),
                AlertEntity(
                    title = "Unusual Outbound Data Transfer",
                    severity = "MEDIUM",
                    status = "New",
                    classification = "Unassigned",
                    timestamp = "2026-07-15 06:22:00 UTC",
                    sourceIp = "192.168.1.88",
                    destinationPort = "443",
                    mitreTactic = "Exfiltration",
                    mitreTechnique = "T1048 (Exfiltration Over Alternative Protocol)",
                    blastRadiusPath = "Compromised WS [192.168.1.88] ➔ Local Gateway ➔ SSL Encrypted Tunnel ➔ Rogue Offsite Server [45.76.192.12]",
                    remediationScript = """
# Python SOAR Playbook: Block Outbound TLS Exfiltration Port
import os

def kill_process_and_block_ip(ip, proc):
    print(f"[*] Terminating exfiltration process: {proc}")
    print(f"[*] Creating egress firewall reject rule to block destination {ip}")
    # Simulate process termination & outbound block
    print(f"[+] Staged: killall -9 {proc}")
    print(f"[+] Staged: iptables -A OUTPUT -d {ip} -j REJECT")
    print("[+] Safe exfiltration route disabled.")

if __name__ == '__main__':
    kill_process_and_block_ip("45.76.192.12", "powershell.exe")
""".trimIndent(),
                    rawLog = """{
  "timestamp": "2026-07-15T06:22:00Z",
  "event_type": "exfiltration_alert",
  "src_ip": "192.168.1.88",
  "dest_ip": "45.76.192.12",
  "dest_port": 443,
  "bytes_sent": 5584920100,
  "bytes_received": 1204850,
  "connection_duration_seconds": 1840,
  "dest_country": "Unknown/Proxy",
  "process_name": "powershell.exe"
}"""
                ),
                AlertEntity(
                    title = "Unauthorized USB Device Connected",
                    severity = "LOW",
                    status = "New",
                    classification = "Unassigned",
                    timestamp = "2026-07-15 06:25:00 UTC",
                    sourceIp = "Host-WS-09",
                    destinationPort = "N/A",
                    mitreTactic = "Initial Access",
                    mitreTechnique = "T1200 (Hardware Additions)",
                    blastRadiusPath = "Physical Endpoint Host-WS-09 (Injected USB) ➔ Local Disk Mount E:\\ ⤏ Network Shares",
                    remediationScript = """
# Python SOAR Playbook: Disable External USB Storage
import os
import sys

def disable_usb_drivers():
    print("[*] Hardening system registry to block USB Mass Storage drivers")
    # Windows Registry block simulation or Linux modprobe block
    print("[+] Staged Command: reg add HKLM\\SYSTEM\\CurrentControlSet\\Services\\USBSTOR /v Start /t REG_DWORD /d 4 /f")
    print("[+] USB Storage drivers successfully revoked on Host-WS-09.")

if __name__ == '__main__':
    disable_usb_drivers()
""".trimIndent(),
                    rawLog = """{
  "timestamp": "2026-07-15T06:25:00Z",
  "event_type": "host_endpoint_event",
  "hostname": "Host-WS-09",
  "user": "rsmith",
  "device_class": "USB Mass Storage Device",
  "vendor_id": "0x0930",
  "product_id": "0x6545",
  "serial_number": "TOSHIBA_948275193B",
  "mount_point": "E:\\",
  "policy_action": "LOG_ONLY",
  "message": "Non-authorized storage device attached"
}"""
                )
            )
            alertDao.insertAlerts(initialAlerts)
        }
    }
}
