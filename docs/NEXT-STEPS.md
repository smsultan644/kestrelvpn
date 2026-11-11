# Next steps (plain language)

## A. Build the app with GitHub (free)
1. Create a free account at github.com.
2. Create a new **private** repository called `kestrelvpn`.
3. Put the files from this project into it. Easiest: on a computer, open the repository page, choose "Add file" then "Upload files", and drag in the contents of the unzipped folder (including the hidden `.github` folder).
4. Open the "Actions" tab, choose "Build APK", press "Run workflow".
5. When it finishes (a few minutes), open the run and download the `kestrel-vpn-apk` file. Inside is the APK and `SHA256SUMS.txt`.
6. If the build fails, copy the red error text to Claude and it will be fixed.

## B. Get a server
A server is a computer on the internet running WireGuard. Options to check before choosing anything:
- Oracle Cloud "Always Free" (needs sign-up and identity verification, availability varies by country).
- Any Ubuntu/Debian server with a public IPv4 address.

Nothing may be bought without your approval. Then, on the server:
1. `sudo bash setup-wireguard.sh` and answer the questions.
2. Open UDP port 51820 in the provider's firewall.
3. In the app, Servers tab: copy the phone's public key.
4. On the server: `sudo bash add-peer.sh "My phone" PASTE_KEY_HERE`.
5. Copy the line it prints (starts with `KESTREL1`) into the app: Servers, Add server.
6. Go to Home and tap the big button. Allow the VPN permission.

## C. Check it really works
- Home screen should show a different public IP than before connecting.
- Visit a DNS leak test site (for example dnsleaktest.com) and a WebRTC/IPv6 test while connected.
- Report back what you see and these results will be recorded in `TESTING.md`.

## D. Coming in the next milestones
Permanent signing key (you keep it safe), polish and missing app features, privacy policy and terms drafts, and the Stage 2 backend design.
