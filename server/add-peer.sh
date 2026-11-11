#!/usr/bin/env bash
# Kestrel VPN - authorise one phone on this server.
# Usage: sudo bash add-peer.sh "<device name>" "<phone public key from the app>"
# Prints a single KESTREL1|... line to paste into the app (Servers -> Add server).
set -euo pipefail

CONF="/etc/wireguard/wg0.conf"
META="/etc/wireguard/kestrel.meta"

if [[ $EUID -ne 0 ]]; then echo "Please run with sudo."; exit 1; fi
if [[ $# -ne 2 ]]; then echo "Usage: sudo bash $0 \"<device name>\" \"<device public key>\""; exit 1; fi
[[ -f "$CONF" && -f "$META" ]] || { echo "Run setup-wireguard.sh first."; exit 1; }

DEV="$(printf '%s' "$1" | tr -cd 'A-Za-z0-9 _.-')"
KEY="$2"
[[ "$KEY" =~ ^[A-Za-z0-9+/]{43}=$ ]] || { echo "That does not look like a WireGuard public key (44 characters ending in =)."; exit 1; }

# shellcheck disable=SC1090
source "$META"

if grep -qF "$KEY" "$CONF"; then echo "This key is already added."; exit 1; fi

used="$(grep -oE 'AllowedIPs = 10\.8\.0\.[0-9]+' "$CONF" | awk -F. '{print $4}' || true)"
next=""
for i in $(seq 2 254); do
  if ! grep -qx "$i" <<<"$used"; then next="$i"; break; fi
done
[[ -n "$next" ]] || { echo "No free addresses left."; exit 1; }
CLIENT_IP="10.8.0.${next}/32"

cat >> "$CONF" <<EOF

# device: ${DEV}
[Peer]
PublicKey = ${KEY}
AllowedIPs = ${CLIENT_IP}
EOF

wg set wg0 peer "$KEY" allowed-ips "$CLIENT_IP"

SERVER_PUB="$(cat /etc/wireguard/server.pub)"
echo
echo "Added \"${DEV}\". Copy the whole line below into the app (Servers -> Add server):"
echo
echo "KESTREL1|${NAME}|${CC}|${CITY}|${HOST}:${PORT}|${SERVER_PUB}|${CLIENT_IP}|${DNS}"
echo
