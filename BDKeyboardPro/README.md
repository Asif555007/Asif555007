# BD Keyboard Pro (Android, Kotlin)

System-wide IME keyboard: Bengali phonetic + English, voice, AI writing tools, privacy mode.
Implements the Phase 1–3 core of the supplied requirements document.

## Get an installable APK (pick one)
**A. No local setup (GitHub):** create a GitHub repo, upload this folder, open the *Actions* tab →
*Build APK* → download the `BDKeyboardPro-debug-apk` artifact. Copy `app-debug.apk` to your phone and install it
(allow "install unknown apps"). Unit tests run in the same job.

**B. Android Studio:** open this folder (Gradle wrapper included), then Run, or
`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## Turn it on
1. Open the app → **Enable keyboard** → switch on *BD Keyboard Pro* → **Select keyboard**.
2. Tap the test box, type `ami`, `tumi`, `bhalo` → Bengali suggestions appear.
3. AI/translation need the backend below; typing, voice and everything else work without it.

## Release build
Set `AI_BASE_URL` in `app/build.gradle.kts`, create a keystore, add a signing config, then
`./gradlew bundleRelease` for the Play Store. Use `PRIVACY_POLICY_TEMPLATE.md` as the policy starting point.

## Backend (AI + translation proxy)
```
cd backend && npm install
ANTHROPIC_API_KEY=... APP_TOKEN=optional-secret npm start
```
or with Docker: `docker build -t bd-ai backend && docker run -p 8080:8080 --env-file backend/.env bd-ai`
(see `backend/.env.example`). If you set `APP_TOKEN`, the app must send it as a Bearer token
(wire it into `AiClient(tokenProvider = …)`); leave it unset for a first test.
The Android app never holds a provider key. Deploy behind HTTPS (Cloud Run, Fly, Render, etc.).

## What is implemented
| Area | Status |
|---|---|
| IME service, QWERTY layout, shift/caps, symbols page, number row, enter actions | Done |
| Bengali phonetic engine (dictionary + rule-based, conjuncts), candidate strip | Done (starter quality) |
| Personal dictionary (learns only deliberate picks; editable/deletable) | Done |
| Bengali/English digits setting, । punctuation | Done |
| Emoji panel, opt-in local clipboard history, snippets | Done |
| Voice typing (SpeechRecognizer, mic tap only, permission via transparent activity) | Done |
| AI panel: consent, 9 actions, preview, Insert/Replace/Copy/Cancel, error states | Done |
| Themes (dark/light/system), one-hand left/right, haptics toggle | Done |
| Private mode; password/email/URL/number field handling | Done |
| Settings/onboarding screen, privacy controls | Done |

## Not yet implemented (needs product decisions per spec §12)
Gesture typing, handwriting, bilingual dictionary UI, Mini Notes, Send-to-device, Pro/Play Billing,
tablet split layout, TalkBack polish, Mac support (out of scope). Spec items marked "clarify" were
deliberately not guessed.

## Known limitations — please read
- **Not compiled or run by me**: my environment had no Android SDK or network, so this has not been built or tested
  on a device. If the GitHub build or Android Studio shows errors, send me the log and I will fix them.
- The phonetic engine is a starter: a small dictionary plus greedy rules. Real accuracy needs the
  100–300+ reviewed examples the spec requests, then tuning (ঙ/ঞ/ণ ambiguity, য-ফলা, ref, etc.).
- Keyboard UI is built in code with plain Views and rebuilt on every state change; fine for an MVP,
  but optimise (view reuse, key-popup previews, long-press alternates) before release.
- Clipboard history is stored in SharedPreferences (unencrypted). Move to Room + EncryptedFile
  / Keystore before release.
- Replace the placeholder launcher icon; add a privacy policy URL before Play submission.
