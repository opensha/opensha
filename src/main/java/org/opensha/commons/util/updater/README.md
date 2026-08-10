# OpenSHA Application Auto-Updater

This subsystem checks a running OpenSHA application for updates against GitHub
releases and, with the user's consent, downloads the latest JAR, verifies its
integrity, launches the new JAR, and exits the old JVM. It skips the download
entirely when the latest version's JAR is already installed beside the running
one. On the new version's first startup it also offers (once) to delete the
older JAR left beside it.

All classes live in two packages:

- `org.opensha.commons.util.http` &mdash; the GitHub REST API transport + DTOs
  (`GitHubClient`, `GitHubRelease`, `GitHubAsset`)
- `org.opensha.commons.util.updater` &mdash; the update workflow + UI
  (`ApplicationUpdater`, `AssetDownloader`, `AssetLauncher`, `UpdatePrompt`,
  `SwingUpdatePrompt`)

The global disable-updates setting is read from `org.opensha.commons.util.OpenSHAConfig`
(`~/.opensha/config.json`). There is no GUI to edit it &mdash it is set by hand (see
[Disabling update prompts globally](#disabling-update-prompts-globally)).

## Architecture

```
                                           ┌────────────────────┐
GitHub API ───▶                            │    GitHubClient    │ thin HTTP transport (java.net.http.HttpClient)
                                           │     (no state)     │ parses JSON → DTOs (GSON, internal only)
                                           └────────────────────┘
                                                      │ GitHubRelease / GitHubAsset
                                       ┌────────────────────────────┐
                                       │     ApplicationUpdater     │ version logic + old-version cleanup
                                       │ download · verify · launch │
                                       └────────────────────────────┘
                                                      │
                      ┌───────────────────────────────┬───────────────────────────────┐
                      │ injects                       │ injects                       │ injects
        ┌─────────────▼────────────┐    ┌─────────────▼────────────┐    ┌─────────────▼────────────┐
        │     AssetDownloader      │    │      AssetLauncher       │    │       UpdatePrompt       │
        │HTTP: downloadAssetToTemp │    │   launchJar: detached    │    │    SwingUpdatePrompt     │
        │  in ApplicationUpdater   │    │  process + System.exit   │    │UPDATE_NOW / REMIND_LATER │
        └──────────────────────────┘    └──────────────────────────┘    │   / SKIP_THIS_VERSION    │
                                                                        └──────────────────────────┘

The strategy interfaces + UpdatePrompt are the test seams; their production impls are
method references on ApplicationUpdater (downloadAssetToTemp, launchJar), injected via the
test constructor.
```

The transport (`GitHubClient`) is a stateless, uncached adapter over the GitHub
releases API &mdash; it issues requests and returns `GitHubRelease`/
`GitHubAsset` DTOs and nothing more. There is no service layer on top: the
updater checks for updates once at launch, so a cache would add no value, and
`GitHubClient` already returns clean DTOs. `ApplicationUpdater` calls it
directly. Three seams make the workflow unit-testable without Swing, a network,
or a `System.exit`: the UI is behind the `UpdatePrompt` interface, and the
download and launch steps are behind the top-level `AssetDownloader` and
`AssetLauncher` interfaces (their production implementations are method
references on `ApplicationUpdater` &mdash; `downloadAssetToTemp` and
`launchJar` &mdash; injected via the test constructor). `GitHubClient` is
mocked in tests.

## Update flow

`ApplicationUpdater.checkForUpdates(appName, appShortName, assetPrefix)` spawns
a daemon thread that first runs `cleanupOldVersions` and then `runUpdateCheck`:

1. **Cleans up old versions** (see [Old-version cleanup](#old-version-cleanup)):
   silently removes any stale `.<prefix>-*.part` leftovers, and if older
   versioned JARs sit beside the running JAR and the user has not yet been asked
   about them for this version, prompts once whether to delete them.
2. **Skips the update check early** if headless, not running from a JAR (e.g. an
   IDE/dev build), the running version is unknown (`build.version` could not
   be loaded), or update prompts have been **disabled globally** by the user
   (see [Disabling update prompts globally](#disabling-update-prompts-globally)).
3. **Finds the latest stable release.** Calls `GitHubClient.getLatestRelease()`
   and parses the tag with `parseStableVersion`. If the latest release is a
   beta/alpha (tag contains `beta`/`alpha` or non-numeric characters), it falls
   back to the most recent page of releases and takes the first stable one.
   It is cheaper to fetch a single release, so the page is only fetched when the
   latest is not a stable release.
4. **Compares versions** with `ApplicationVersion.isGreaterThan` (reusing the
   existing `Comparable` implementation). If not newer, it stops &mdash; nothing
   is downloaded.
5. **Skips if the latest is already installed.** If a sibling JAR named
   `<prefix>-<latest>.jar` (version equal to the latest) already sits beside the
   running JAR, the update is not attempted &mdash; no prompt, no download, no
   overwrite. The user can launch that JAR directly. (An on-disk versioned JAR
   is trusted: `downloadAssetToTemp` only renames a `.part` to its final name
   after SHA-256 verification.)
6. **Honors user preferences**: skips if the user previously chose "skip this
   version" or is within the "remind me later" defer window (default 7 days).
7. **Prompts** the user via `UpdatePrompt.prompt(...)`, showing the version and
   release notes. If the body contains a "Release Notes" section, only that
   section and everything below it is shown (the generic preamble above it is
   trimmed). The notes are rendered as Markdown (via `MarkdownUtils`/commonmark)
   into an HTML view; links open in the system browser.
   - **Remind me later** &rarr; stores the defer timestamp and stops.
   - **Skip this version** &rarr; records the version (`setSkipVersion`) so the
     updater will not prompt for it again until a newer release becomes latest;
     stops without downloading.
   - **Update now** &rarr; continues.

   The shipped prompt offers all three choices: "Update now", "Remind me later",
   and "Skip this version". The "Skip this version" button is wired to
   `Choice.SKIP_THIS_VERSION` and gated by a static flag
   (`SKIP_VERSION_BUTTON_ENABLED`, currently `true`) in `SwingUpdatePrompt`; the
   skip plumbing is also reachable because `UpdatePrompt.prompt` may return
   `SKIP_THIS_VERSION` (and `setSkipVersion` is public, so a caller can set the
   preference programmatically). The dialog is sized wide enough that all three
   buttons fit in a single row above the opt-out footer (see
   [Disabling update prompts globally](#disabling-update-prompts-globally)).
8. **Selects the asset** on the stable release whose name starts with
   `assetPrefix + "-"`. Release assets are named `<prefix>-<version>.jar`, e.g.
   `HazardCurveGUI-26.1.1.jar`. If no asset matches, the user is told a new
   version of OpenSHA is available but the updated application could not be
   found in that release, and is referred to the release page
   (`GitHubRelease.getHtmlUrl()`) for more information; nothing is downloaded.
   If the release can no longer be fetched at that point &mdash; a transient
   failure after the version was already detected &mdash; the URL is omitted
   and the user is asked to update manually.
9. **Downloads** the asset to a `.<name>.part` temp file beside the running JAR,
   streaming with progress updates to the prompt.
10. **Verifies** the SHA-256 of the downloaded bytes against the GitHub-provided
    `digest` (`asset.digest`, formatted `sha256:<hex>`). On mismatch or IO
    error, the whole download is retried up to 3 times.
11. **Launches**: atomically renames the verified `.part` to its versioned name
    beside the running JAR (e.g. `HazardCurveGUI-26.1.2.jar`), launches it with
    a detached `ProcessBuilder("java", "-jar", <new path>)`, and exits this JVM.
    The old JAR is left in place; it is offered for deletion on the new
    version's first startup (step 1, above).

Nothing is downloaded when the application is already up to date.

## Version parsing rules

`ApplicationUpdater.parseStableVersion(tag)`: - `null` or blank &rarr; not stable.
- Tag containing `beta` or `alpha` (case-insensitive) &rarr; not stable.
- An optional leading `v`/`V` is stripped.
- The portion before a `-` suffix is taken.
- Any non-digit, non-`.` character &rarr; not stable.
- The remainder must parse as `M.m` or `M.m.b` via
  `ApplicationVersion.fromString`.

Examples: `v26.1.1` &rarr; `26.1.1`; `26.1` &rarr; `26.1.0`;
`v27.0.0-beta` &rarr; not stable; `abc` &rarr; not stable.

## Download and integrity

- Downloads use `java.net.http.HttpClient` with redirect-following (GitHub
  release assets 302 to a CDN), a `User-Agent` header, and a 10-minute timeout.
- The asset is streamed to `<jarDir>/.<assetName>.part`.
- SHA-256 is computed over the downloaded file and compared (case-insensitively)
  to `asset.getSha256Hex()` (parsed from the API `digest` field).
- On mismatch, the `.part` file is deleted and the download restarts from
  scratch (up to 3 attempts). There is **no resumable/partial download** by
  design &mdash; fat jars are ~89 MB and a fresh atomic download is simpler and
  robust. The temp file lives next to the running JAR (not the OS temp dir) so
  the final move is on the same filesystem and atomic.
- On success the verified `.part` is atomically renamed to its versioned name
  (`<jarDir>/<assetName>`, e.g. `HazardCurveGUI-26.1.2.jar`), overwriting any
  prior file of that name. That path is what gets launched.

## Launch and relaunch

The new JAR is launched directly. Because the downloaded JAR is written to its
**versioned name** beside the running JAR (a fresh file that is never locked),
the JVM can launch it itself:

- `ApplicationUpdater.launchJar(downloadedJar)` resolves the `java` executable
  (`ProcessHandle.current().info().command()`, falling back to
  `java.home/bin/java`), then runs a detached
  `ProcessBuilder("java", "-jar", downloadedJar)` with its working directory set
  to the running JAR's folder. Output and error are discarded.
- The JVM then `System.exit(0)`. The new process is already running detached, so
  it survives the exit.

The running JAR is **never overwritten or renamed** at launch time. The new
version lands under its own name (e.g. `HazardCurveGUI-26.1.2.jar`) and the old
one (e.g. `HazardCurveGUI-26.1.1.jar`) is left in place as a fallback. This
avoids the misleading filename the previous in-place strategy produced (an
`AppName-1.0.0.jar` holding version-2 bytes) and removes the Windows-file-lock
problem entirely. Deleting the old JAR is deferred to the new app's startup,
by which point the old JVM has exited and the file is no longer locked (see
[Old-version cleanup](#old-version-cleanup)).

> **Shortcuts/launchers:** because the filename changes with the version, any
> hardcoded launcher, `.bat`/`.sh` wrapper, or desktop shortcut pointing at the
> old versioned name will break after an update and must be repointed at the
> new name. This is the accepted trade-off for honest filenames.

> JVM and application arguments from the original launch are **not** preserved on
> relaunch (the app is relaunched as `java -jar <path>` with no extra args). This
> is acceptable for the GUI applications, which take no required CLI arguments.

## Old-version cleanup

On every launch, before the update check, `cleanupOldVersions` tidies the
folder the running JAR lives in:

- **Stale `.part` files** (`.<prefix>-*.part`, leftovers from a failed/interrupted
  download) are deleted silently; they are always garbage.
- **Older versioned JARs** (`<prefix>-<version>.jar` whose version is less than
  the running version) are collected. The running JAR (equal version, or a name
  that does not match the `<prefix>-<version>.jar` pattern) is never a
  candidate.
- If any older JARs are found **and** the user has not yet been asked about them
  for the running version, a single YES/NO dialog (`confirmDeleteOldVersions`)
  lists them and asks whether to delete them. **Yes** deletes them (best-effort);
  **No** leaves them. Either way a per-app, per-version preference
  (`cleanupAsked_<appShortName>_<runningVersion>`) is set so the prompt **never
  recurs on subsequent startups of the same version**; a future update to a newer
  version gets its own one prompt.

The old JAR is deliberately kept until the new version has proven it can start
**and** the user confirms: if the new version cannot launch (e.g. the wrong Java
is installed), its `cleanupOldVersions` never runs, so the old JAR remains
usable as a fallback. Deletion is never automatic.

## Disabling update prompts globally

A user who never wants to see the update prompt can disable the updater
**globally, across all OpenSHA applications**, from a single per-user file:

`~/.opensha/config.json`

```json
{
  "disableUpdatePrompts": true
}
```

When `disableUpdatePrompts` is `true`, every OpenSHA application skips its
launch-time update check entirely &mdash; no background thread, no network call,
and no update dialog. The check happens in `ApplicationUpdater` before the
update-checker thread is even spawned, so a disabled updater is a complete no-op
at launch. The setting is read once per launch; there is no in-session caching.

This is a **cross-application** setting: it lives in one file under the user's
home (`~/.opensha/`, the same directory the OpenSHA data downloaders use) and is
honored by every application that calls `checkForUpdatesDefault`, so the user
does not have to disable the updater separately for each app. It sits above the
per-app `java.util.prefs` preferences ("remind me later", "skip this version"):
those still apply per app when prompts are enabled, but are irrelevant while the
global disable is on.

**How to set it:** there is no GUI for this setting (not all OpenSHA
applications have a Help menu), so it is set by hand. Edit
`~/.opensha/config.json` and set `disableUpdatePrompts` to `true` (disable) or
`false` (re-enable). Delete the file (or remove the key) to restore defaults
(prompts enabled). The file is created lazily &mdash; it only appears after the
first change, so a normal launch with prompts enabled writes nothing. Because the
file lives in the user's home and is read on every launch, a user can disable the
updater without ever launching an application (and thus without ever seeing an
update prompt) by creating the file ahead of time.

The update-available prompt itself (`SwingUpdatePrompt`) shows a footer reminding
the user that prompts can be disabled for this and all future OpenSHA versions by
setting `disableUpdatePrompts: true` in `~/.opensha/config.json`.

Parsing is tolerant: a missing, empty, or malformed `config.json` is treated as
defaults (prompts enabled) with a logged warning, so a bad hand-edit can never
break application startup.

## Caching (none)

There is deliberately no caching layer. The updater checks for updates exactly
once, at launch, and never re-fetches during a session, so a cache would add a
dependency and complexity for no benefit. `GitHubClient` is stateless.

GitHub's unauthenticated rate limit is 60 requests/hour/IP, which is ample for a
single check on launch.

## Per-app wiring

Each GUI application calls the one-liner from its `main`, after its disclaimer
dialog and GUI launch. The asset prefix must match the release asset name for
that app:

| App | `assetPrefix` |
|-----|---------------|
| HazardCurveApplication | `HazardCurveGUI` |
| HazardSpectrumApplication | `HazardSpectrumGUI` |
| AttenuationRelationshipApplet | `AttenuationRelationshipGUI` |
| ScenarioShakeMapApp | `ShakeMapGUI` |
| SiteDataCombinedApp | `SiteDataGUI` |
| GMT_MapGeneratorApplet | `GMTMapApp` |
| IMEventSetCalculatorGUI | `IMEventSetCalculatorGUI` |

The CLI tool `IMEventSetCalculatorCLT` (`appJarIM-CLT`, distributed as a zip)
is intentionally **not** wired &mdash; it has no GUI to prompt and is not run as
an interactive `java -jar` desktop app.

All of the GUI applications above are already wired; each `main` calls
`checkForUpdatesDefault` after its disclaimer dialog and GUI launch. Example
(from `HazardCurveApplication.main`):

```java
new DisclaimerDialog(APP_NAME, APP_SHORT_NAME, getAppVersion());
// ... exception handler, launch ...
ApplicationUpdater.checkForUpdatesDefault(APP_NAME, APP_SHORT_NAME, "HazardCurveGUI");
```

Preferences ("remind me later" defer timestamp, the "skip this version" gate,
and the per-version "old-version cleanup already asked" gate) are stored per
app via `java.util.prefs`, keyed by `appShortName` &mdash; the same keying
convention `DisclaimerDialog` uses. The **global** disable-updates setting is
separate: it lives in `~/.opensha/config.json` and applies across all apps (see
[Disabling update prompts globally](#disabling-update-prompts-globally)).

## Testing

- **Offline unit tests** (run by default via `./gradlew test` because they are
  aggregated into `*Suite*` classes):
  - `HttpSuite`: `GitHubClientTest` (parses a checked-in `releases/latest` JSON
    fixture in `src/test/resources/...`, deterministically catching "GitHub
    renamed a field" regressions).
  - `UpdaterSuite`: `ApplicationUpdaterTest` (stable-version parsing/fallback,
    `isOutdated`, asset-prefix selection, SHA-256, the prompt decision flow,
    including the skip-version choice (`SKIP_THIS_VERSION` &rarr; `setSkipVersion`,
    which suppresses the prompt for that exact version until a newer release
    becomes latest), the already-installed detection (`findInstalledJar`), and the
    old-version cleanup logic (`findOldJars` selection, delete-on-confirm /
    keep-on-decline, and the once-per-version prompt gate), with `GitHubClient`
    mocked and the download/launch steps injected as fakes &mdash; no network, no
    `System.exit`). The `SKIP_THIS_VERSION` flow is exercised by mocking
    `UpdatePrompt.prompt` to return that choice; the `SwingUpdatePrompt` button
    itself (gated by `SKIP_VERSION_BUTTON_ENABLED`, currently `true`) is not driven
    by the unit tests, which treat the prompt as a mock.
- **Live smoke test** (run only via `./gradlew testOperational`, named
  `*Operational*`): `GitHubClientOperationalTest` hits the real GitHub API,
  bounded with `@Test(timeout = 30000)`.

The download (`AssetDownloader`) and launch (`AssetLauncher`) steps are
injectable on `ApplicationUpdater`'s test constructor precisely so the full
flow can be exercised without network or process exit.

## Configuration

| Constant | Location | Default |
|----------|----------|---------|
| Repo owner/name | `GitHubClient` | `opensha/opensha` |
| API version header | `GitHubClient.API_VERSION` | `2026-03-10` |
| Download retry attempts | `ApplicationUpdater.MAX_DOWNLOAD_ATTEMPTS` | 3 |
| "Remind me later" defer | `ApplicationUpdater.REMIND_LATER_DELAY_DAYS` | 7 days |
| Disable update prompts (global) | `~/.opensha/config.json` `disableUpdatePrompts` | `false` |

To point the updater at a different repository (e.g. a fork that publishes its
own releases), construct a `GitHubClient` with an explicit owner/repo
(`new GitHubClient(owner, repo)`) and pass a custom `ApplicationUpdater`
rather than `checkForUpdatesDefault`.

## Safety and limitations

- The updater **never blocks application startup** (daemon thread) and swallows
  its own exceptions so it cannot crash the host app.
- It is a **no-op in headless environments** and when **not running from a JAR**
  (IDE/dev builds), so it does not interfere with development or CI.
- Integrity is guaranteed by the SHA-256 `digest` published with each GitHub
  asset; a mismatch is rejected and re-downloaded. There is no code-signing
  verification beyond HTTPS + the digest.
- The relaunch loses original JVM/application arguments (see above).
- The old JAR is **retained** until the user confirms deletion on the new
  version's first startup, so a new version that cannot start (e.g. the wrong
  Java installed) never leaves the user without a working application. Deletion
  is never automatic; the prompt occurs at most once per running version.
- Because the filename changes with the version, hardcoded launchers/shortcuts
  to the old versioned name break after an update (see Launch and relaunch).
- The launch-and-exit is **identical across Windows, macOS, and Linux** (a
  detached `ProcessBuilder`, then `System.exit(0)`); there is no per-OS script.
  No automated test executes the real launch/exit against a live process &mdash;
  `ApplicationUpdaterTest` injects a no-op launcher and exercises the cleanup
  logic against temp directories &mdash; so the end-to-end update must be
  validated manually, with Windows as the priority (the path most likely to
  surface a JVM/launch issue).
