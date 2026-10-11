# NilaasS security notes

- API keys, vault entries, saved preferences, skills, and conversation history are encrypted at rest with AES-GCM using an Android Keystore key.
- Full backups use a separate passphrase-derived key (PBKDF2-HMAC-SHA256, 180,000 iterations) and AES-GCM authenticated encryption. The passphrase is never persisted by the backup utility.
- The vault is a starter implementation, not a certified password manager. App compromise, rooted devices, accessibility malware, memory disclosure, or unsafe backups may expose secrets.
- API credentials stored on a phone remain less protected than server-side credentials. A production service should use a backend proxy, secret rotation, rate limits, and abuse monitoring.
- Online messages and media prompts are sent only to the configured provider when the corresponding feature is used. Provider retention and content policies still apply.
- The app never promises anonymous or untraceable internet activity. The security lab is a non-invasive HTTPS header/TLS check for authorized targets only.
- Do not use the app to access systems without permission, evade incident response, steal credentials, or create non-consensual intimate media.
