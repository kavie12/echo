# Echo

Sync **messages** and **clipboard** between your own Android phones over a local network (Wi-Fi or hotspot).
One phone is the **Host**; any number of **Clients** pair with it using a 6-digit code.

## Build & run
1. Open this folder in Android Studio (Koala or newer, JDK 17). Let Gradle sync (wrapper = Gradle 8.9).
2. Run on two or more devices (Android 8.0+ / API 26+) on the same Wi-Fi, or one phone's hotspot.
3. Host: pick **Host**, grant SMS + notification permission, read the code on screen.
4. Client: pick **Client**, choose the discovered host (or type its IP), enter the code.

## Architecture (event driven, layered)

```
 SmsSource ─┐                                   ┌─> MessageRepository ─┐
            ├─ publish ─> EventBus (SharedFlow) ┤                      ├─> ViewModels ─> Compose UI
 Clipboard ─┘             ▲        │            └─> ClipboardRepository┘
 Access                   │        └─> HostEngine ─> HostServer ══ WebSocket ══> ClientConnection
                          └────────────────────────────────────────────────── publishes received data
```

| Package | Responsibility |
|---|---|
| `core` | `EventBus` + `AppEvent` (typed pub/sub on `SharedFlow`) |
| `domain` | Models and the wire protocol (`Packet`, JSON) – no Android deps |
| `data` | `SmsSource`, `ClipboardAccess`, `HostServer` (Ktor CIO), `ClientConnection` (Ktor client), NSD discovery, DataStore settings, in-memory repositories |
| `engine` | Use-cases: `HostEngine`, `ClientEngine`, `AppResetter` |
| `service` | Foreground `SyncService`, invisible `ClipboardShareActivity` |
| `ui` | Compose screens + ViewModels (onboarding, host, client) |
| `di` | Hand-written `AppContainer` |

Sources only *publish* events; the host engine, repositories and UI *subscribe*. Nobody calls anybody directly across layers.

### Protocol
JSON over WebSocket at `ws://<host>:8765/sync`.
`hello → welcome | rejected`, then `prefs` (client→host), `ping/pong` (heartbeat + latency),
`messages`, `clips` (host→client), `kicked`.

### Security
* Pairing needs the host's random 6-digit code (regenerate any time with **New code**).
* After pairing the host issues a 256-bit device token; reconnects use the token, not the code.
* 5 wrong codes lock an address out for 60 s. Codes/tokens are compared in constant time.
* Removing a device revokes its token immediately.
* Traffic is plain `ws://` on your LAN. Use a network you trust (your own hotspot is ideal).

## Known limits (Android platform rules)
* **Clipboard**: Android 10+ only lets an app *read* the clipboard while it has focus. Echo captures
  copies while it is open; for background sharing use the notification's **Send clipboard** button.
  Writing on the client works in the background.
* **SMS**: needs `READ_SMS`. Google Play restricts this permission, so distribute by APK/sideload.
* Clients show the last 50 messages / 50 clips the host had when they connected, then live updates.
* Data is one-way: host → clients.

## Ideas for next steps
TLS with a pinned certificate derived from pairing, contact-name lookup, MMS/notification mirroring,
Room persistence on clients, splitting `data`/`engine` into Gradle modules.
