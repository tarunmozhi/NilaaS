# NilaasS — standalone Android assistant

NilaasS is a separate Kotlin/Jetpack Compose Android app. It does not modify or depend on the existing `NilaOs` repository.

## Implemented in this source version

- Local chat UI and up to 300 encrypted conversation messages.
- Local commands for help, time, and offline status, including Tamil aliases.
- Tamil speech recognition via Android's installed speech service and optional TextToSpeech replies (availability and network behavior depend on the device's speech/TTS providers).
- Optional OpenAI-compatible Chat Completions integration. Online use is opt-in in AI Setup; API key and provider settings are encrypted at rest with AES-GCM and an Android Keystore key.
- Browser-based web search that opens the selected query in the browser.
- User-saved preferences and exact-phrase response skills, encrypted at rest. This is explicit personalization, not model training or autonomous code execution.
- A basic encrypted local secret vault for passwords/notes and provider credentials. It is not a professionally audited password manager.
- Storage Access Framework folder choices for generated media and encrypted backups.
- Image generation through an OpenAI-compatible Images API when the configured provider/model supports the requested response shape (hosted URL or `b64_json`).
- Video generation through Replicate Predictions API using a configured model version and `input.prompt`; models requiring additional input parameters need a model-specific adapter.
- Saving generated media to the chosen media folder or app-private storage.
- Passphrase-encrypted full backup and restore, including chat, learned preferences/skills, vault entries, and provider settings (online AI resets to disabled after restore). Use a strong passphrase and keep it separately; it cannot be recovered if forgotten.
- An authorized, non-invasive HTTPS header/TLS check for a URL the user owns or is allowed to assess.
- Unit tests for local intent routing and basic policy decisions.
- GitHub Actions workflow to build/test a debug APK in the cloud.

## Required setup before online features work

1. Open **AI Setup** inside the app.
2. Configure an HTTPS OpenAI-compatible endpoint, a supported chat model, and its API key. Enable online AI only when you consent to sending messages to that provider.
3. For images, configure an image model supported by that provider's Images API and credentials with image-generation access.
4. For videos, configure a Replicate API token and an exact video model version ID. The model must accept `input.prompt`; other model schemas may need a dedicated adapter.
5. Review provider content policies, privacy terms, region availability, and account limits. The Adult-content preference does not override provider rules.

API keys are encrypted at rest on the phone, but a mobile app cannot make a provider key as secure as a server-side secret. For production distribution, a backend proxy with server-side secrets and rate limits is safer. Never share keys in chat or commit them to source control.

## Build and install

- Requires JDK 17, Android SDK Platform 35, Gradle 8.9, and dependency access.
- Open this folder in Android Studio and build `app` → `assembleDebug`, or push it to a new, separate GitHub repository and run **Actions → NilaasS Android build**.
- The workflow runs unit tests and builds `app-debug.apk` as an artifact.
- The source was not compiled in this environment because no Android SDK/Gradle installation is available and outbound network resolution is unavailable. The workflow is not yet verified; its first run is the actual compile gate. Do not treat this ZIP as a verified APK.

## Important limitations

- The app is not a general autonomous agent and does not self-train an AI model. It saves preferences and simple user-authored text skills.
- The video adapter only sends a `prompt` field. Some models need additional parameters, different output formats, or more polling time.
- Web research opens the browser; it does not yet fetch, cite, and synthesize pages inside the chat.
- The security lab checks HTTPS response headers and certificate acceptance; it is not a full penetration-testing suite and does not perform port scanning, exploitation, stealth, or guaranteed trace removal.
- No privileged Nila OS integration is present.
- A release-signed APK, compatibility test on the vivo Y11, and full end-to-end tests of paid provider integrations remain unverified.

## Storage and privacy

- Chat, saved preferences, skills, provider credentials, and vault entries are encrypted at rest using AES-GCM and an Android Keystore key.
- Media is stored in the selected media folder when configured; otherwise it goes to app-private storage.
- Full backups are encrypted with a user-provided passphrase using PBKDF2-HMAC-SHA256 and AES-GCM. Restore requires the same passphrase.
- The passphrase is not stored. If it is lost, the backup cannot be restored.
- Voice recognition may send audio/text to the device's configured speech service. Check that service's privacy behavior.
- Online AI, image generation, and video generation send the relevant prompt to the configured provider. Do not submit secrets or private people's images without permission.
