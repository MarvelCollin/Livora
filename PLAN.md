# Livora Roadmap

Plan for seven new tools on top of the existing Livora app (devices, tasks, dictionary). Nothing here is built yet unless a box is ticked. Update the boxes as work lands.

Effort sizes are relative: S is small, M is medium, L is large, XL is the biggest piece of work in this plan.

## 0. Where the app is today

- [x] Home shows devices only (AC and bulb cards, scenes)
- [x] AC remote with 17 brands, checked against real remote captures, 20 degree default on power on
- [x] Cerulean and marigold theme with dark mode, no gradients, 48dp touch targets
- [x] App icon and brand files in `branding/`
- [x] People (face grouping) lives in the Gallery tab as Photos, Albums and People, see section 8
- [x] Navigation is grouped into four tabs, see section 0b
- [x] UI previews with sample data exist for QR codes, Expenses, Documents, App usage and the Storage cleaner with swipe review. None of them work yet, see section 0b

## 0b. Navigation and where every tool lives

Four bottom tabs, each a place a person can name. Each tab with parts keeps them as swipeable segments under one fixed title, and a swipe changes the segment first, then the tab at the edge.

| Tab | What is inside | Why it is grouped this way |
|-----|----------------|----------------------------|
| Home | AC, bulb and scenes | The thing used most, kept devices only |
| Daily | Tasks, Dictionary, Expenses as segments | Things you check every day and log a little at a time |
| Gallery | Photos, Albums, People as segments | Everything about your pictures, including the viewer and moving photos |
| Tools | A hub with three groups. Scan and create: QR codes, Documents. Private: Password vault. Phone care: Storage cleaner, App usage | Rarely opened helpers, each one row with a short description |

Rules for this structure:
- No more than five bottom tabs, and the current tab is marked with a filled marker and its label, not color alone
- A page title matches the tab or row that opened it, and every page below a tab has a Back button
- New tools go into an existing Tools group, they do not get a new tab
- Screens with sample data say so in a line under the title, so nothing pretends to work

Status of the UI previews (sample data only, no real function behind them):
- [x] Tools hub with grouped rows
- [x] QR codes: Scan tab with history and a safe result sheet, Create tab with a live preview
- [x] Expenses: month headline, budget, where it went, searchable list grouped by day, add sheet with keypad
- [x] Documents: searchable library with sort, document page grid
- [x] App usage: permission screen, screen time with hourly chart, most used apps, unused apps
- [x] Storage cleaner: overview with space breakdown, swipe review card stack with undo and end summary
- [ ] Replace each preview with the real tool, in the order of section 10

## 1. Decisions to confirm first

Each has a recommended default. Work follows the default unless you say otherwise.

- [x] **Navigation.** Decided: four bottom tabs, Home, Daily, Gallery and Tools, as described in section 0b. Tools is a grouped hub of plain rows (name, one line of description, a live value such as "Vault, 12 items"). Home stays devices only.
- [ ] **All files access for the cleaner.** Android 11+ hides other apps' files and the Downloads root from normal apps. Full cleaner features (old APKs, big downloads, empty folders) need the `MANAGE_EXTERNAL_STORAGE` permission. Fine for a personal sideloaded app, not accepted on Play Store. Default: ask for it as an optional "Full cleaner mode" and keep a media only mode without it.
- [ ] **Expenses storage.** Local Room database only, or sync through Supabase like tasks. Default: local only, with encrypted export and import. Money data should not leave the phone by accident.
- [x] **Vault unlock.** Decided: your fingerprint or phone lock unlocks it, with a recovery code as the way back after a phone reset.
- [ ] **Document scanner engine.** ML Kit Document Scanner (needs Google Play services, best quality, least code) or a custom camera and OpenCV pipeline. Default: ML Kit now, custom fallback only if a device lacks Play services.
- [ ] **Default currency.** IDR with `id-ID` formatting. Default: yes.
- [ ] **Package name.** Still `com.example.livora`. Change it before any public release. Default: change later, not now.

## 2. Shared foundation (do this first)

Everything below depends on it, so it goes before any single tool.

- [ ] Add Room and KSP (KSP release that matches Kotlin 2.0.21) to `libs.versions.toml` and `app/build.gradle.kts` (M)
- [ ] Add Paging 3 with `paging-compose` for long lists
- [ ] Add DataStore for settings, WorkManager for background jobs
- [ ] Add Coil 3 (`coil-compose`, `coil-video`) for images and video thumbnails
- [ ] Skip CameraX for now, both QR scanning and document scanning run through Play services screens
- [ ] One `AppDatabase` for expenses, QR history, document index, review decisions. The vault gets its own encrypted store (see section 3)
- [ ] Simple `AppContainer` object for repositories, matching the current style with no DI framework
- [x] Tools hub tab and routes in `MainScreen.kt`, `AppNavHost.kt`, `Screen.kt`
- [ ] Static app shortcuts on long press of the launcher icon: Scan QR, Scan document, Add expense
- [ ] Shared UI pieces: `PermissionGate` (explains why, shows request button, handles denied and partial access), `EmptyState`, `ListRow`, `SegmentedControl`, undo message through the existing `Toaster` action support
- [ ] `<queries>` entry for launcher apps so app lists work without the restricted all packages permission
- [ ] Backup rules: keep vault and face index out of Android backup, decide for the rest
- [x] Turn on R8 for release builds (measure size after each tool lands, see section 2b)
- [ ] Fixed release keystore kept outside the repo (a debug key mismatch already forced one reinstall)

### Permission map

| Tool | Permissions and special access |
|------|-------------------------------|
| QR scanner | none with the Google code scanner |
| Password vault | `USE_BIOMETRIC` (fingerprint or phone lock) |
| Expenses | none. Optional `POST_NOTIFICATIONS` for budget alerts |
| Document scanner | none with ML Kit (runs in Play services). `CAMERA` only for a custom scanner |
| App usage stats | `PACKAGE_USAGE_STATS`, granted by the user in system Usage access settings |
| Storage cleaner | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`. Optional `MANAGE_EXTERNAL_STORAGE`. `REQUEST_DELETE_PACKAGES` to offer uninstall |
| People | `READ_MEDIA_IMAGES`, `POST_NOTIFICATIONS` for background scan. Move, rename and delete of other apps' photos use the system consent dialog |

## 2b. Size, speed and quality budget

The app must stay small, start fast and stay smooth as tools are added. These rules apply to every tool. Numbers are filled in from real measurements on the phone.

**Rules**
- [ ] Prefer platform APIs and Google Play services over bundled libraries. ML Kit barcode, face and document features can run from Play services, which keeps their models out of the APK
- [ ] Any new dependency that adds more than 500 KB to the release APK needs a written reason and a measurement
- [ ] No dependency injection framework, no second image loader, no second JSON library
- [ ] Open each feature's database lazily on first use, never all at startup
- [ ] Do network and disk work off the main thread, and only when its screen is visible
- [ ] Long lists are paged, images are decoded at display size, memory caches have a fixed cap
- [ ] Background work only through WorkManager with constraints (idle, battery not low), and pause when the phone is hot
- [ ] Use lifecycle aware state collection so hidden screens stop working
- [ ] Every screen keeps state design: skeletons while loading, no layout jumps

**Build settings**
- [x] R8 code shrinking and resource shrinking on for release builds
- [x] Keep only English and Indonesian translations from libraries
- [ ] Keep only `arm64-v8a` native libraries, since the only target phone is 64 bit ARM (saves about 2.6 MB right now)
- [ ] Trim `material-icons-extended`, either by relying on R8 or by copying the few icons used, and check the size gain
- [ ] Baseline Profile and startup profile generated with Macrobenchmark on the phone
- [ ] Proper release signing key instead of the debug key (release builds are signed with the debug key for now so they install over the current app)
- [ ] Optional later: replace Gson with kotlinx.serialization (faster, friendlier to R8)

**Startup and runtime fixes already spotted**
- [ ] Do not create every ViewModel at app start. Today all four are created at launch and Tasks and Dictionary start network calls immediately. Create them per screen and load on first visible
- [ ] Switch `collectAsState` to lifecycle aware collection
- [ ] Stable keys in lazy lists, `remember` and `derivedStateOf` where values are derived
- [ ] Check recomposition counts on the home and list screens

**Measure and record**

| Metric | Target | Measured 2026-09-30 | After |
|--------|--------|---------------------|-------|
| Release APK size | as small as possible, alert on any jump over 500 KB | 8.77 MB. Debug build is 19.4 MB. App code is 1.4 MB compressed, the rest is the TensorFlow Lite native library added for face recognition (4.5 MB arm64 plus 2.6 MB armv7). Dropping armv7 saves about 2.6 MB | |
| Cold start to first frame | under 500 ms | 322 to 387 ms over 4 runs on the Redmi Note 14 Pro+, 507 ms on the first run after install | |
| Scroll jank on long lists | under 5 percent slow frames | 1.9 percent janky, median frame 5 ms (home screen only, long lists not yet measured) | 1.4 percent janky, median 9 ms, 99th percentile 28 ms on a release build, after motion and charts were added (tab swipes, usage chart drag, list scrolls, 1,354 frames). A debug build of the same run shows 6.2 percent, so always judge jank on release |
| Idle memory | under 150 MB | 88 MB total PSS on the home screen | |
| Background scan | no visible battery drain, paused on low battery | not built yet | |

Tools: `apkanalyzer` for size, `adb shell am start -W` for cold start, `adb shell dumpsys gfxinfo` for frames, `dumpsys meminfo` for memory, Macrobenchmark for repeatable runs.

## 3. Password Vault

Highest risk tool, so the security design came before any screen. No custom crypto: standard AES-GCM from the Java crypto APIs and the Android Keystore.

**Design as built (unlock is your fingerprint or phone lock, no master password to remember)**
- A random 256 bit data key encrypts the whole vault file with AES-GCM (12 byte random nonce per save, entry file bound to its purpose so it cannot be swapped)
- The data key is wrapped by a hardware backed Android Keystore key (StrongBox when the phone has it) that requires your fingerprint or your PIN, pattern or password for every single unlock
- The data key exists in memory only while the vault is unlocked and is wiped when it locks
- A recovery code (160 random bits, shown once at setup) wraps the data key a second time. If the phone key is lost (screen lock removed, phone reset), the recovery code restores the vault and creates a fresh phone key
- The vault files live in the app's private folder and are excluded from Android cloud backup and phone to phone transfer
- Locks the moment the app leaves the screen. Vault screens block screenshots and hide from the recent apps preview
- Copied values are marked sensitive and cleared from the clipboard after 30 seconds
- Needs Android 11 or newer, because that is where a fingerprint or phone lock can unlock a Keystore key together
- The vault code never writes to the log

**Built and tested**
- [x] Crypto layer with 25 unit tests: round trip, every flipped byte rejected, wrong key and wrong data rejected, nonces never repeat, no plaintext in the files, recovery restores after key loss
- [x] Setup flow with recovery code shown once and a "saved it" confirmation
- [x] Unlock with fingerprint or phone lock, retry, and clear messages when no screen lock is set
- [x] Auto lock on leaving the app, `FLAG_SECURE` on all vault screens
- [x] Entries: name, username, password, website, notes, favorite, optional one-time code key
- [x] List with search and filters (all, favorites, weak, reused)
- [x] Detail with tap to reveal, copy, open website
- [x] Add, edit, delete with Undo, discard changes prompt
- [x] Password generator (`SecureRandom`, length 8 to 64, character sets, avoid look-alikes) and strength meter
- [x] Weak and reused password detection
- [x] One-time codes (TOTP, RFC 6238) with live countdown, tested against all RFC vectors for SHA1, SHA256 and SHA512
- [x] Vault store excluded from Android backup
- [x] Restore with recovery code, and erase vault with a confirmation

**Still to do**
- [ ] Try it on the real phone: setup, lock, unlock, add, edit, delete and undo, generator, recovery restore (needs your fingerprint, so it must be tested by hand)
- [ ] Add a one-time code by scanning its QR (comes with the QR tool)
- [ ] Encrypted export and import file
- [ ] Optional breach check with the Pwned Passwords range API (sends only a 5 character hash prefix), off by default
- [ ] Passphrase mode in the generator (EFF wordlist is CC BY 3.0, needs attribution, adds about 60 KB)
- [ ] Optional auto lock timeout setting, and a lock on screen off
- [ ] Threat model note (what it protects against and what it does not)
- [ ] Security review pass with the security skill

## 4. QR Code Scanner, scan and generate (M)

**Approach.** Google code scanner from Play services (`play-services-code-scanner`). It has its own scanning screen, needs no camera permission, and adds almost nothing to the APK. Fall back to CameraX plus unbundled ML Kit only if a custom scan screen is wanted later. ZXing core (Apache 2.0) for generating codes.

**Scan**
- [ ] Launch the Google code scanner from the QR tool, with the result shown in the app's own screen
- [ ] Scan from a gallery image through the system photo picker
- [ ] Recognize all common formats: QR, EAN, UPC, Code 128, Data Matrix
- [ ] Result parsing by type: URL, Wi-Fi, contact card, phone, SMS, email, location, calendar event, plain text (pure functions with unit tests)
- [ ] Safety: never auto open links, show the full address with the host emphasized, warn on plain http and shortened links
- [ ] Wi-Fi result offers to join the network
- [ ] History in Room: search, favorite, swipe to delete with undo
- [ ] Empty state, and a clear message if Play services is missing

**Generate**
- [ ] Types: text, URL, Wi-Fi, contact, email, phone, SMS, location
- [ ] Options: error correction level, size, foreground and background color with a contrast check
- [ ] Preview updates as you type
- [ ] Save PNG to `Pictures/Livora` through MediaStore, share sheet, copy
- [ ] Round trip test: generate then decode returns the same text

## 5. Expenses Tracker (L)

**Approach.** Local Room database, amounts stored as `Long` in the smallest currency unit, Paging 3 for the transaction list, charts drawn with Compose Canvas.

- [ ] Schema: transactions, categories, accounts, budgets, recurring rules, with migrations from day one (M)
- [ ] Money helper: parse and format by currency, IDR without decimals, unit tests
- [ ] Quick add sheet with a numeric keypad, category, account, note, date
- [ ] Transaction list grouped by day with search and filters (category, account, type, date range, amount range) and an active filter line
- [ ] Edit and delete with undo
- [ ] Accounts and transfers between accounts
- [ ] Categories with defaults for Indonesia (food, transport, bills, groceries and so on), user editable
- [ ] Monthly summary: total in, total out, top categories, one clear headline number. No icon tile stat cards
- [ ] Charts: category breakdown and daily trend, chart colors from the dataviz skill so they read in light and dark
- [ ] Budgets per category with progress and an over budget alert notification
- [ ] Recurring transactions generated by WorkManager (unit test the recurrence rules)
- [ ] CSV export and import
- [ ] Receipt photo attach, using the document scanner once section 6 is done
- [ ] Later: fill the amount from a receipt photo with ML Kit text recognition

## 6. Document Scanner, Camera to PDF (M)

**Approach.** ML Kit Document Scanner gives automatic edge detection, crop, rotate, filters and PDF output with no camera permission. Build the library, editing and export around it.

- [ ] Scan entry point with page limit and gallery import enabled
- [ ] Save result: keep page JPEGs plus the generated PDF in app storage
- [ ] Room index: name, created date, page count, size, thumbnail
- [ ] Library screen with search, sort and rename
- [ ] Document detail: page grid, reorder, delete page, add page, rotate
- [ ] Camera to PDF from existing images: pick many images, order them, make one PDF (`PdfDocument`)
- [ ] Merge two PDFs and split pages out
- [ ] Compress option with a quality slider and a size preview
- [ ] PDF preview with `PdfRenderer`
- [ ] Share, and export to a chosen folder with the system file creator
- [ ] Later: OCR copy text with ML Kit text recognition, password protected PDF with an Apache licensed library

## 7. App Usage Stats (M)

**Approach.** `UsageStatsManager` events. Screen time is computed from foreground and background events, the way the system Digital Wellbeing does, because the daily summary values are less accurate. The system only keeps a short window, so a daily WorkManager job saves totals into Room for long term charts.

- [ ] Permission gate that opens the system Usage access screen and rechecks on return
- [ ] Today view: total screen time, hourly bar chart, ranked app list with the real app icons
- [ ] Range switch: day, week, month, with previous and next
- [ ] App detail: trend, launches, last used
- [ ] Daily snapshot job into Room for history
- [ ] Unused apps list (not opened for 30 days) with app size from `StorageStatsManager`, feeds the storage cleaner
- [ ] Optional daily limit per app with a reminder notification (polling based, so mark it approximate)
- [ ] Later: mobile and Wi-Fi data per app with `NetworkStatsManager`
- [ ] Not possible for third party apps and not planned: per app battery use

## 8. Face Recognition, gallery folders and people (XL)

**Status.** A background agent is researching the best on device approach and building it. It has also been given the folder and enrollment requirements below. Its report will fix the exact models and numbers, then this section gets updated.

**How recognition works (so expectations are right).** The app does not retrain a neural network. A ready made face model turns each face into a list of numbers (an embedding). Photos of the same person land close together. "Training" means enrolling reference photos of a person and then matching every other face against them by similarity. More and better reference photos make matching more accurate.

**Gallery and folders**
- [ ] Show the phone's current photo folders with a cover, name and count
- [ ] Open a folder as a paged photo grid
- [ ] Create a folder (kept in the app until its first photo lands, because Android cannot store an empty folder, then created under `Pictures/<Name>`)
- [ ] Rename and delete a folder (delete goes to system trash, recoverable)
- [ ] Add, remove, copy and move photos with multi select
- [ ] Copy is the default. Move, rename and delete of files owned by other apps use one batched system consent dialog on Android 11+
- [ ] Undo for reversible actions, a confirmation naming the count for irreversible ones

**Enroll a person from reference photos**
- [ ] Pick one or more photos of the target person from the in app gallery or the system photo picker
- [ ] If a photo has several faces, tap the right face crop
- [ ] Quality feedback: too small, blurry, side view, several faces
- [ ] Name the person and save several reference embeddings, not one average
- [ ] Add more reference photos later
- [ ] Confirm or reject suggested matches, confirmed faces become extra references and rejected ones tighten the threshold

**Link a person to a folder**
- [ ] Select an existing folder or create one and connect it to the person
- [ ] Review first mode (default): suggestions queue with match confidence and batch approve
- [ ] Auto add mode: new photos found by the background scan go into the folder
- [ ] Default action is copy, with matched count shown and a threshold slider with a conservative default
- [ ] A person with no linked folder still works as a virtual album

**Pipeline and quality**
- [ ] Research and pick detector, embedding model and clustering method with license, size, accuracy and speed
- [ ] Detection and embedding on downscaled photos with a quality filter (small, blurry, side faces)
- [ ] Face alignment before embedding
- [ ] Automatic clustering finds unnamed people, and a cluster can be promoted to a named person with a linked folder
- [ ] Room index of faces, incremental scan of only new or changed photos, resumable WorkManager job with progress
- [ ] People tab: real face crop for each person (no initials), name, photo count, sorted by count
- [ ] Person detail: paged photo grid, open a photo, "not this person", merge people, hide a person, all with undo
- [ ] Permission, partial access, empty and no faces states
- [ ] Unit tests for clustering and matching with synthetic embeddings
- [ ] Measured on the real phone: photos per second, peak memory, screen open time
- [ ] Everything stays on the phone, no network use for this feature
- [ ] After the agent finishes: reconcile its media scanning with the shared media index in section 9

## 9. Storage Cleaner (XL)

Includes the swipe review for photos and videos, and every cleaner feature that modern Android allows.

**Shared media index.** One Room table that mirrors MediaStore (id, size, date modified, width, height, folder, content hash, blur score). Scanning once feeds duplicates, blur detection, large files and, where possible, face grouping.

**Swipe review (the headline feature)**
- Swipe left keeps, swipe right sends to the trash queue
- [ ] Card stack with drag, rotation and fly off animation, the next two cards preloaded
- [ ] Photos are zoomable. Videos play muted with tap to unmute and a seek bar (Media3 ExoPlayer)
- [ ] Info on each card: size, date, resolution, folder
- [ ] While dragging, show "Keep" on the left edge and "Trash" on the right edge with flat tints, no gradients
- [ ] Buttons for Keep and Trash and custom accessibility actions, because swipe only is not accessible
- [ ] Undo last swipe, with haptic feedback on each decision
- [ ] Nothing is deleted while swiping. Trashed items collect in a review list
- [ ] End summary: "Trash 84 items and free 1.2 GB", one system confirmation with `MediaStore.createTrashRequest` (recoverable for about 30 days). Older Android versions fall back to the recoverable delete flow
- [ ] Remember kept items so they do not come back, with a "reset decisions" setting
- [ ] Sort and filter: oldest first, biggest first, random, by month or folder, photos only, videos only, screenshots
- [ ] Progress line such as "23 of 1,204", and a resume where you stopped
- [ ] Session recap: reviewed, kept, trashed, space freed

**Other cleaner features**
- [ ] Storage overview: used and free space, breakdown by images, videos, audio, documents, apps
- [ ] Exact duplicates by size then content hash, cached by id and date modified
- [ ] Near duplicates and burst shots with perceptual hash on small thumbnails, pick the best to keep
- [ ] Large files list with a size threshold
- [ ] Screenshots and screen recordings bucket
- [ ] Blurry, very dark and accidental photos with a Laplacian variance score
- [ ] Messaging app media (WhatsApp and similar folders visible through MediaStore)
- [ ] Full cleaner mode (needs all files access): old APKs, large downloads, empty folders, temp files
- [ ] Unused apps with sizes and an uninstall prompt (needs section 7)
- [ ] Clear this app's own cache
- [ ] For other apps' caches, deep link to system settings, since Android does not let third party apps clear them
- [ ] Background scan through WorkManager with a progress notification
- [ ] Safety rules: never delete without explicit confirmation, always show total size first, prefer system trash over permanent delete
- [ ] Permission, partial access, empty and nothing to clean states

## 10. Suggested build order

| Step | Work | Size | Why here |
|------|------|------|----------|
| 1 | Shared foundation | M | Everything needs it |
| 2 | QR scanner and generator | M | Quick win, and gives the vault its TOTP scanning |
| 3 | Storage cleaner, swipe review first | XL | Headline feature, builds the shared media index |
| 4 | Expenses tracker | L | Independent, big daily value |
| 5 | Document scanner | M | Small with ML Kit, unlocks receipts for expenses |
| 6 | Password vault | L | After QR is done, with the security design done first |
| 7 | App usage stats | M | Feeds the unused apps cleaner |
| 8 | People polish and shared index merge | M | Agent already started, finish after the media index exists |
| Always | Size and speed budget, section 2b | ongoing | Checked after every tool |

## 11. Definition of done for every tool

- [ ] Unit tests for the pure logic (parsers, money, hashing, clustering, crypto)
- [ ] Every permission state designed: not asked, granted, denied, partial
- [ ] Loading uses skeletons, empty and error states exist, long lists are paged
- [ ] UI follows the uiux rules: palette from the theme only, no gradients, no icon tiles, no pill badges, 48dp targets, screen reader labels, no em dashes or semicolons in copy
- [ ] Checked on the real phone with adb, screenshots reviewed
- [ ] `/uiux review` run on the new screens
- [ ] APK size and cold start checked against the budget in section 2b
- [ ] Motion uses the tokens in `Motion.kt` (150 to 480 ms, emphasized easing), reads animated values inside `graphicsLayer` or draw lambdas so nothing recomposes per frame, and frame timing is checked on a release build against the 5 percent target
- [ ] Charts use `ChartColors.kt` in fixed slot order (validated with the dataviz script on the light and dark surfaces), show details on touch, and list every value in a legend or row so the tooltip never gates information
- [ ] Icons are bare and inline with their text, and images are real content from the phone (photos, app icons), never decorative art
- [ ] Committed and pushed to `main` with a single line `feat:` or `fix:` message

## 12. Carry-over from earlier work

- [ ] Add `secrets.properties` so Tasks and Dictionary load data from Supabase (the app shows a toast until then)
- [ ] Test the AC remote on the real AC. Use Send test and Next model in the brand picker to find the variant your unit accepts
- [ ] Restyle the bulb, task detail and quiz screens with the new palette and run a full `/uiux review` on every screen
- [ ] Haier AC protocol, skipped because the signal layout could not be verified
- [ ] Bulb discovery on the same Wi-Fi, verify on the phone
