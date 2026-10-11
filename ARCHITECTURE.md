# NilaasS architecture

- **Compose UI:** Chat, Storage, Skills, Web, Media, Security Lab, Vault, AI Setup, and Privacy screens.
- **Local core:** rule-based intents and exact-trigger user-authored skills.
- **LearningStore:** explicit preferences and simple skills; encrypted at rest. No automatic model training.
- **SecureVault:** AES-GCM encrypted values using Android Keystore key material.
- **LocalStore:** encrypted chat history plus persisted Android Storage Access Framework folder URIs.
- **EncryptedBackup:** passphrase-derived key and AES-GCM authenticated full backup/restore.
- **OpenAiCompatibleClient:** optional Chat Completions request over HTTPS.
- **Media adapters:** OpenAI-compatible image generation and a minimal Replicate prediction adapter. Model schemas differ; some models require additional parameters.
- **Speech:** Android SpeechRecognizer and TextToSpeech; installed engines determine language and offline support.
- **Security Lab:** user-confirmed, non-invasive HTTPS HEAD request and response-header review.
- **Future Nila OS integration:** intentionally absent; this project remains a standalone Android app.
