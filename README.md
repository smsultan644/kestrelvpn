# Kestrel VPN (working name)

An Android WireGuard VPN app plus the scripts to run your own server. "Kestrel VPN" and the package name `com.sultanmahmud.kestrelvpn` are working names. Trademark and domain availability have **not** been checked.

## Status: Milestone 1 (Stage 1, personal VPN)

Source code written. **Nothing has been compiled or run yet.** The workspace that wrote this code cannot reach the Android build tools or a public server, so the APK has to be built by GitHub (see `docs/NEXT-STEPS.md`). Expect to fix small compile errors on the first build.

| Part | State |
|---|---|
| Android app (Kotlin, Compose, Material 3, WireGuard GoBackend) | Written, not compiled |
| Server scripts (`server/`) | Written, syntax-checked only, never run on a server |
| GitHub build workflow | Written, never run |
| Backend API, accounts, admin dashboard (Stage 2) | Not started |
| Payments, plans, Play Store (Stage 3) | Not started |

## What the app does now
- Connect and disconnect through Android's VPN permission and the official WireGuard library.
- The phone creates its own key pair. The private key is stored encrypted on the phone only.
- Servers are added by pasting a one-line code printed by `server/add-peer.sh`. Only real servers you add are listed.
- Shows connection time, speeds, data used, the public IP websites see, and whether it changed. Also shows an IPv6 address if one is visible.
- Dark and light theme (follows the phone). Settings page with kill switch guidance.

## Not built yet (from the original brief)
Latency and load display, favourites, recent connections, auto-connect, notifications, a connection diagnostics screen, a signed release key, a backend, and the full privacy policy and terms. Automatic reconnection after network changes is handled by WireGuard itself but is untested.

## Layout
- `app/` Android app
- `server/setup-wireguard.sh` one-time server setup
- `server/add-peer.sh` authorise a phone and print the code for the app
- `.github/workflows/build-apk.yml` builds the APK and a SHA-256 checksum
- `docs/` next steps and test status

## Honest limits
- A free server may be slow, limited or unavailable. It must have a public IPv4 address. Home connections often sit behind CGNAT and cannot host a server.
- The first APK is a debug build signed with a throwaway key. A new build cannot be installed over an old one without uninstalling first. A permanent release key comes in the next milestone.
- This is not a "no-logs" service and makes no such claim. The server scripts add no traffic logging, but the operator is responsible for the machine.
- The IP check uses api.ipify.org, a third-party site.
