# DevTools Browser

An Android browser (Kotlin + WebView) that gives you **developer tools on the phone itself** to inspect any website — console, DOM, network, and sources, right on the device.

## Features

- **On-device devtools (Eruda)** — tap the 🐞 bug icon to inject [Eruda](https://eruda.liriliri.io/) into the current page. Panels: Console, Elements, Network, Resources, Sources, Info. Bundled at `app/src/main/assets/eruda.min.js`, so it works offline and isn't blocked by page CSP.
- **DevTools state persists** and is re-injected automatically on every page load.
- **Remote debugging** — `WebView.setWebContentsDebuggingEnabled(true)`, so you can also inspect from desktop Chrome via `chrome://inspect`.
- **Pop-up / touch-redirect blocker** — the shield button (default **ON**) blocks `window.open` pop-ups that some sites trigger on tap.
- **Status-bar insets** — content is padded below the notification bar and above the gesture nav bar (edge-to-edge safe on Android 15 / targetSdk 35).
- URL bar with smart input: `https://` is added automatically; anything non-URL falls back to a Google search.
- Back / forward / reload-stop, progress bar, error view, `target=_blank` handling, external-scheme links (`tel:`, `intent:`, …), last-URL persistence.
- Handles `VIEW` intents — set it as the handler for `http`/`https` links.

## Requirements

| Tool   | Version  |
|--------|----------|
| Gradle | 9.x      |
| AGP    | 9.2.1    |
| JDK    | 17+ (21 recommended) |
| SDK    | `android-35`, build-tools `36.0.0` |

## Build

```sh
cd devtools-browser
sh build.sh          # writes local.properties, runs :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On-device (Termux), the pre-made script is at `/storage/emulated/0/apps/gradle-9.7.1/bin/gradle`.

## Push to GitHub

```sh
bash push_to_github.sh [repo-name] [public|private] [remote-url]
```

If git reports `dubious ownership` on `/sdcard`, the script already adds the `safe.directory` exception. If your token can't create repos, create an empty repo manually and pass its URL as the third argument.

## Project structure

```
app/src/main/
├── assets/eruda.min.js               # bundled devtools
├── java/com/devtools/browser/
│   └── MainActivity.kt               # WebView + devtools + blocker + insets
├── res/
│   ├── layout/activity_main.xml      # toolbar, progress bar, WebView
│   ├── drawable/                     # nav icons, launcher foreground
│   ├── mipmap-anydpi-v26/            # adaptive icon
│   └── values/                       # colors, strings, Material3 theme
└── AndroidManifest.xml
```

## How devtools injection works

Eruda is read from assets and passed to `WebView.evaluateJavascript(...)` wrapped in an `if (window.eruda)` guard, so each page gets exactly one instance (`MainActivity.injectEruda`). Toggling off calls `eruda.destroy()`. The remote-debug flag means the same WebView is also reachable from `chrome://inspect` on a desktop.
