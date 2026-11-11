# Test status

Every result below is real. Nothing has been run on a phone or server yet.

Legend: **Not tested** = no test run. **Needs device** = requires a physical Android phone. **Needs server** = requires a deployed server.

| # | Test | Status |
|---|---|---|
| 1 | APK installation | Not tested (APK not built yet) |
| 2 | Application startup | Not tested |
| 3 | VPN permission prompt | Needs device |
| 4 | Successful connection | Needs device + server |
| 5 | Successful disconnection | Needs device |
| 6 | Public IP actually changes | Needs device + server |
| 7 | DNS leak protection | Needs device + server |
| 8 | IPv6 leak protection | Needs device + server |
| 9 | Internet access through VPN | Needs device + server |
| 10 | Kill switch behaviour | Needs device |
| 11 | Wi-Fi switching | Needs device |
| 12 | Mobile network switching | Needs device |
| 13 | Automatic reconnection | Needs device |
| 14 | Server switching | Needs device + 2 servers |
| 15 | Invalid credentials | Not tested |
| 16 | Backend failures | Not applicable yet (no backend) |
| 17 | VPN server failures | Needs device + server |
| 18 | Battery usage | Needs device |
| 19 | Application stability | Not tested |
| 20 | Security configuration review | Not tested |

## Checks that were actually run
- Shell scripts: syntax check only (`bash -n`). Not executed against a real server.
- XML resource files: well-formedness check only.
- Kotlin and Gradle files: not compiled.

## Completion criteria (brief section 14)
Not met. No APK exists, no server is running, no tunnel has been established.
