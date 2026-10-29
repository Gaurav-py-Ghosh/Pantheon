# Pantheon — handoff for Stages 4 and 5

Pantheon is a terminal-styled Android home launcher. Stages 1–3 are done and committed on `main`.
Build Stage 4, then Stage 5, one commit per stage. Read this whole file first.

## Ground rules

- Kotlin + Jetpack Compose (foundation only — **no Material3**). `minSdk 31`, `compileSdk/targetSdk 37`.
- Match the existing code: same naming, comment density, file layout. Every piece of text uses
  `TText` (JetBrains Mono) from `ui/components/Text.kt`; every panel uses `Modifier.terminalBox(colors)`.
  Colors only from `Pantheon.colors` (`background, surface, surfaceHigh, border, text, textDim, accent, warn, error`).
  Accent text gets `glow = true`.
- Look: terminal / hacker. Prompts like `$ cmd`, paths like `~/apps`, tags like `[sh]`, toggles `[x]` / `[ ]`, buttons `[ok]`.
- Smoothness matters: never read fast-changing state (drawer progress, drag finger) in composition — read it in
  `graphicsLayer {}` or through `derivedStateOf`. Heavy work on `Dispatchers.IO/Default`.
- Pure layout logic goes in `data/LayoutOps.kt` with unit tests in `app/src/test/.../LayoutOpsTest.kt`.
- Don't add dependencies unless needed; if you do, add them to `gradle/libs.versions.toml`.

## Build, test, run

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable("JAVA_HOME","User")
$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable("ANDROID_HOME","User")
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

- Release APK: `app\build\outputs\apk\release\app-release.apk` (signed with the debug key; installs directly).
- Emulator AVD: `Pantheon_API37` (`%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Pantheon_API37`).
- Install and make it home:
  `adb install -r <apk>` then `adb shell cmd role add-role-holder android.app.role.HOME dev.pantheon.launcher 0`
- Screenshots: use Git Bash (`adb exec-out screencap -p > file.png`); PowerShell corrupts binary output.
  In Git Bash set `MSYS_NO_PATHCONV=1` before adb commands with `/sdcard` paths.
- Verify every stage on the emulator with screenshots before committing. Both builds must have zero warnings.

## Architecture (where things are)

| Area | File |
|---|---|
| App singletons (manual DI) | `PantheonApp.kt` (`AppContainer`, `context.container`) |
| Activity, home-press handling, widget host lifecycle | `MainActivity.kt` |
| App list (LauncherApps, disk cache) | `data/AppRepository.kt` (`AppEntry`, key = `pkg/.Activity#userSerial`) |
| Icons (monochrome layer or tinted fallback) | `data/IconCache.kt`, `ui/components/AppIcon.kt` |
| Settings (DataStore) | `data/SettingsRepository.kt` (`Settings` data class) |
| Home layout model + persistence | `data/LayoutRepository.kt` (`HomeLayout`, `HomePage`, `HomeItem`, `ItemContent`) |
| Pure layout edits | `data/LayoutOps.kt` |
| Tags (`[sh]`) | `data/Tags.kt` |
| Role defaults (dialer, camera…) | `data/DefaultApps.kt` |
| Stats, torch | `data/SystemStats.kt` |
| Media, calendar, notes, widget host | `data/MediaRepository.kt`, `data/WidgetData.kt` |
| Search + commands + calculator | `search/Search.kt` |
| Theme, accents, fonts | `ui/theme/Theme.kt` |
| Root composable, overlays | `ui/LauncherRoot.kt` |
| All home behavior (menus, drag/drop, pages, widgets, back/home) | `ui/LauncherController.kt` (implements `HomeActions`) |
| Drawer gesture + controller | `ui/DrawerController.kt` |
| Pickers / permissions / system dialogs from the controller | `ui/ActivityBridge.kt` |
| Home screen, grid, dock, app cell, drag ghost | `ui/home/HomeScreen.kt`, `ui/home/StatusLine.kt` |
| Drawer | `ui/drawer/DrawerScreen.kt` |
| Drag controller, geometry, item drag gesture | `ui/edit/Drag.kt` |
| Menus, rename prompt, toasts | `ui/edit/Overlays.kt` |
| Widgets | `ui/widgets/*.kt` |

Menus are `MenuSpec(anchorRect, title, listOf(MenuItem(label, enabled, hint) { action }))` shown with `launcher.menu.show(...)`.
Disabled placeholders currently exist for `change icon` (Stage 4) and `settings` (Stage 5) in `LauncherController`.

## Stage 4 — Icons and notification counts

Icon resolution order for every app (home, dock, drawer, ghost):

1. **Per-app override** chosen by the user (stored in `Settings`, e.g. `iconOverrides: Map<appKey, String>`).
2. **Installed icon pack** if the user selected one (`Settings.iconPack: String?`).
   Support the standard ADW/Nova format: query activities with intent actions
   `org.adw.launcher.THEMES` and `com.novalauncher.THEME`; read `res/xml/appfilter.xml` (or `assets/appfilter.xml`)
   from the pack via `packageManager.getResourcesForApplication(pack)`; map `ComponentInfo{pkg/cls}` → drawable name.
3. **Bundled line icons**: ship ~150 thin-outline vector drawables (Tabler Icons or Lucide, both MIT — keep their
   LICENSE file under `app/src/main/assets/licenses/`). Map common packages (Chrome, WhatsApp, YouTube, Spotify,
   GitHub, Gmail, Maps, Photos, Camera, Phone, Messages, Settings, Files, Calendar, Clock, Play Store, Telegram,
   Discord, Instagram, X, Reddit, Netflix, Termux, Drive, Keep, Calculator, ChatGPT, Claude, Gemini…) in a
   Kotlin map `package → R.drawable.ic_line_*`. Render tinted with `Pantheon.colors.text`.
4. The app's own **monochrome** adaptive layer (already implemented in `IconCache`).
5. **Category** line icon from `ApplicationInfo.category` (game, audio, video, image, social, news, maps, productivity).
6. Last resort: a boxed tag (`[ab]`) drawn with `TText` in a thin border.

Also:
- Enable the `change icon` menu item: opens a terminal-style picker overlay (grid of bundled line icons plus
  icons from the active pack, with a search field) and a `[reset]` option.
- **Icon pack picker** (temporary entry in the long-press empty-space menu, `icon pack ›`; Stage 5 moves it into settings).
- **Notification counts**: a `NotificationListenerService` (declare in manifest with
  `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`) that publishes a `StateFlow<Map<packageName, Int>>`.
  Show as `[msg:3]` inside the tag on home, dock and drawer. If access isn't granted, show nothing (Stage 5's setup
  screen asks for it; for now add a `notification counts` item in the home menu that opens
  `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`).
- Invalidate `IconCache` when the pack or an override changes.
- Commit message: `Stage 4: icons and notification counts`.

## Stage 5 — Settings, setup, gestures, effects, backup

- **Settings screen** styled as a config file `~/.pantheonrc`: full-screen overlay, sections `[theme]`, `[grid]`,
  `[dock]`, `[drawer]`, `[search]`, `[icons]`, `[widgets]`, `[gestures]`, `[effects]`, `[backup]`, `[about]`.
  Rows like `accent = green   ‹ ›`, toggles `[x] glow`. Must cover every field of `Settings`:
  theme mode, accent (presets + custom hex input), columns 4/5/6, label style (icon+tag+name / icon+tag / icon / tag),
  dock slots 4–6, dock labels, drawer mode, search engine (default/google/ddg/bing/brave), image fill default,
  icon pack, hidden apps list (unhide), background (solid / wallpaper with dark-or-light overlay — use
  `window.addFlags(FLAG_SHOW_WALLPAPER)` and a translucent background), effects toggles.
  Open from the home menu `settings` item (currently disabled) and from long-press empty space.
- **First-run setup** ("boot screen"): typed-out terminal lines, then step by step: set as default home
  (`RoleManager.createRequestRoleIntent(ROLE_HOME)`), photos, notifications access, calendar, accessibility
  (for double-tap lock), usage stats (optional). Each step explains why and has `[grant]` / `[skip]`.
  Store `Settings.setupDone`.
- **Double-tap to lock**: an `AccessibilityService` (`res/xml/accessibility_service.xml`, no event types needed)
  calling `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`. Off by default; toggle in `[gestures]`; double-tap on
  empty grid space (add `onDoubleTap` to the existing `detectTapGestures` in `HomeGrid`). If the service isn't
  enabled, open `Settings.ACTION_ACCESSIBILITY_SETTINGS` with a toast explaining.
  Also use the service as a fallback for swipe-down notifications (`GLOBAL_ACTION_NOTIFICATIONS`).
- **Effects** (`[effects]`): status line types itself in when returning home (typewriter over ~400ms), soft glow
  (exists: `Settings.glow`), optional CRT scanlines overlay (cheap `drawWithContent` lines at low alpha) and
  optional glitch transition on drawer open. Scanlines/glitch off by default.
- **sysinfo panel**: tapping the status line opens a full-screen `$ sysinfo` panel — device model, Android version,
  build, uptime, storage, battery health/temperature (`ACTION_BATTERY_CHANGED` sticky intent), IP address
  (`ConnectivityManager`/`LinkProperties`), VPN on/off, screen size/density.
- **Backup**: export/import `pantheon-backup.json` (settings + layout; not images) via
  `ActivityResultContracts.CreateDocument("application/json")` / `OpenDocument`.
- Remove the temporary `theme ›`, `accent ›`, `icon pack ›` items from the home menu once settings covers them
  (keep `edit layout`, `add widget`, `add image`, `pages ›`, `settings`).
- Commit message: `Stage 5: settings, setup, gestures, effects, backup`.

## Known small issues you may fix along the way

- Status line is slightly wider than a phone screen, so it scrolls (marquee). Consider shortening labels.
- Photo widget thumbnails can look soft; consider `loadThumbnail` with the widget's real pixel size.
- `HomeLayout.dock` holds nullable app keys per slot; keep that.
