#!/usr/bin/env bash
# Kestrel VPN - one-time WireGuard server setup.
# For Ubuntu 22.04/24.04 or Debian 12 with a public IPv4 address. Run as root:
#   sudo bash setup-wireguard.sh
#
# What it does: installs WireGuard, turns on IPv4 forwarding, creates the server key,
# writes /etc/wireguard/wg0.conf, and starts the VPN. It does not log traffic and
# installs no analytics or monitoring.
set -euo pipefail

if [[ $EUID -ne 0 ]]; then echo "Please run with sudo: sudo bash $0"; exit 1; fi
if ! command -v apt-get >/dev/null 2>&1; then echo "This script supports Ubuntu/Debian only."; exit 1; fi

WG_IF="wg0"
WG_PORT="${WG_PORT:-51820}"
WG_NET="10.8.0.0/24"
WG_ADDR="10.8.0.1/24"
CONF="/etc/wireguard/${WG_IF}.conf"
META="/etc/wireguard/kestrel.meta"

if [[ -f "$CONF" ]]; then echo "$CONF already exists. Not overwriting. Remove it first if you really want to start over."; exit 1; fi

clean() { printf '%s' "$1" | tr -d '|\r\n'; }

guess_ip="$(curl -4 -fsS --max-time 5 https://api.ipify.org 2>/dev/null || true)"
read -rp "Server public IP or domain [${guess_ip}]: " HOST
HOST="$(clean "${HOST:-$guess_ip}")"
[[ -n "$HOST" ]] || { echo "A server address is required."; exit 1; }
read -rp "Server name shown in the app (e.g. Tokyo 1): " NAME
read -rp "Country code, 2 letters (e.g. JP): " CC
read -rp "City (e.g. Tokyo): " CITY
NAME="$(clean "$NAME")"; CITY="$(clean "$CITY")"; CC="$(clean "${CC^^}")"
[[ "$CC" =~ ^[A-Z]{2}$ ]] || { echo "Country code must be exactly 2 letters."; exit 1; }
[[ -n "$NAME" && -n "$CITY" ]] || { echo "Name and city cannot be empty."; exit 1; }

echo "Installing WireGuard..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq wireguard iptables curl

WAN_IF="$(ip -4 route show default | awk '{print $5; exit}')"
[[ -n "$WAN_IF" ]] || { echo "Could not detect the internet network interface."; exit 1; }

echo 'net.ipv4.ip_forward=1' > /etc/sysctl.d/99-kestrel.conf
sysctl -q --system

umask 077
mkdir -p /etc/wireguard
wg genkey | tee /etc/wireguard/server.key | wg pubkey > /etc/wireguard/server.pub
SERVER_KEY="$(cat /etc/wireguard/server.key)"

cat > "$CONF" <<EOF
[Interface]
Address = ${WG_ADDR}
ListenPort = ${WG_PORT}
PrivateKey = ${SERVER_KEY}

# Allow WireGuard in, keep devices from talking to each other, send their traffic to the internet (NAT).
PostUp = iptables -I INPUT -p udp --dport ${WG_PORT} -j ACCEPT
PostUp = iptables -I FORWARD -i %i -o %i -j DROP
PostUp = iptables -I FORWARD -i %i -o ${WAN_IF} -j ACCEPT
PostUp = iptables -I FORWARD -i ${WAN_IF} -o %i -m state --state RELATED,ESTABLISHED -j ACCEPT
PostUp = iptables -t nat -I POSTROUTING -s ${WG_NET} -o ${WAN_IF} -j MASQUERADE
PostDown = iptables -D INPUT -p udp --dport ${WG_PORT} -j ACCEPT
PostDown = iptables -D FORWARD -i %i -o %i -j DROP
PostDown = iptables -D FORWARD -i %i -o ${WAN_IF} -j ACCEPT
PostDown = iptables -D FORWARD -i ${WAN_IF} -o %i -m state --state RELATED,ESTABLISHED -j ACCEPT
PostDown = iptables -t nat -D POSTROUTING -s ${WG_NET} -o ${WAN_IF} -j MASQUERADE
EOF
chmod 600 "$CONF"

{
  printf 'NAME=%q\n' "$NAME"
  printf 'CC=%q\n' "$CC"
  printf 'CITY=%q\n' "$CITY"
  printf 'HOST=%q\n' "$HOST"
  printf 'PORT=%q\n' "$WG_PORT"
  printf 'DNS=%q\n' "1.1.1.1"
} > "$META"
chmod 600 "$META"

systemctl enable --now "wg-quick@${WG_IF}"

echo
echo "Done. WireGuard is running on UDP port ${WG_PORT}."
echo "Server public key: $(cat /etc/wireguard/server.pub)"
echo
echo "IMPORTANT: your hosting provider's firewall must also allow inbound UDP ${WG_PORT}."
echo "On Oracle Cloud this is a separate 'Security List' / 'Network Security Group' rule."
echo
echo "Next: in the app open Servers, copy the phone's public key, then run:"
echo "  sudo bash add-peer.sh \"My phone\" PASTE_THE_PUBLIC_KEY_HERE"
