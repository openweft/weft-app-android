# weft-app-android

Android client for the [Weft](https://github.com/openweft) dashboard —
Kotlin + `WebView` hosting [`weft-webui`](https://github.com/openweft/weft-webui).

It mirrors [`weft-app-core`](https://github.com/openweft/weft-app-core)'s
failover logic natively: [`failover/Supervisor.kt`](app/src/main/java/io/openweft/weftapp/failover/Supervisor.kt)
is a line-for-line port of the Go supervisor (fail over fast, fail back
slow with hysteresis), verified against the same test cases in
[`SupervisorTest.kt`](app/src/test/java/io/openweft/weftapp/failover/SupervisorTest.kt).

## Failover model

Mobile can't host a loopback TCP gateway, so it uses the **multi-origin**
model: every DC origin is injected as `window.__WEFT_ENDPOINTS__` (the
same contract as the desktop apps, see
[`WebInject.kt`](app/src/main/java/io/openweft/weftapp/failover/WebInject.kt)),
so the SPA's API client rotates across DCs itself. The native supervisor
picks the first DC to load, raises the SPA's "connection switched" banner
via `__weftFailoverNotice`, and reloads onto the active DC if the current
page becomes unreachable.

## Status — scaffold

Builds with the standard Android toolchain (`./gradlew :app:assembleDebug`,
unit tests `./gradlew :app:testDebugUnitTest`). The Gradle wrapper jar and
launcher icons are intentionally not committed — `gradle wrapper` and
Android Studio generate them.

## Transports

- **Direct** — [`Backend.kt`](app/src/main/java/io/openweft/weftapp/failover/Backend.kt) `DirectBackend`, used when the device is on the mesh.
- **SSH local-forward** — [`SshForwardBackend.kt`](app/src/main/java/io/openweft/weftapp/failover/SshForwardBackend.kt) (sshj, host-key verified via `known_hosts`), wired through `Config.parse` for `"kind":"ssh"` endpoints. **End-to-end tested** ([`SshForwardIntegrationTest`](app/src/test/java/io/openweft/weftapp/failover/SshForwardIntegrationTest.kt)) against an in-process Apache MINA SSH server forwarding to an HTTP echo. No public web listener.
- **WireGuard** — [`WireGuardTunnel.kt`](app/src/main/java/io/openweft/weftapp/failover/WireGuardTunnel.kt) skeleton over the official `com.wireguard.android:tunnel` GoBackend + `VpnService` (declared, commented, in the manifest). Once the tunnel is up, mesh endpoints are plain `DirectBackend`.

### TODO
- Finish the WireGuard `VpnService` wiring (consent flow + GoBackend).
- DNS SRV discovery; load config from a user-editable location.
- Run the supervisor in a foreground service so failover survives the
  activity being backgrounded.
- Auth window (OIDC / OpenPubkey / dev keypair) — present on the desktop
  apps (`weft-app-osx/auth_*.go`, ~2.7k LOC), absent on Android today.
  Token storage would land on the Android Keystore using the same
  `service="weft-app", account=<issuer>` (tokens) and
  `service="weft-app-keypair", account=<issuer>` (ed25519 keys)
  convention the desktop apps use.
- Cluster · DC indicator and failover banner UI — the supervisor already
  emits the `__weftFailoverNotice(from, to)` JS call, but no native
  surface (toast / status bar) yet.
