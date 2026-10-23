# Maya - Android app

Maya ka Android app: graphite + emerald UI, voice orb, chat, memory, tijori (vault),
aur phone-control services. Backend: https://maya-backend-z6e8.onrender.com

## Stack

- Kotlin 2.0.20 + Jetpack Compose (Material 3), single module `:app`
- AGP 8.5.2, Gradle 8.9 (wrapper included), JDK 17
- minSdk 26, compile/targetSdk 34
- Retrofit + OkHttp (backend API), EncryptedSharedPreferences + Keystore (vault),
  BiometricPrompt, Navigation-Compose

Native Android isliye: Accessibility Service, Notification Listener, foreground
service, floating overlay aur Keystore vault ko cross-platform frameworks achhe
se support nahi karte.

## Build

```
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

CI (`.github/workflows/android.yml`) har push pe APK banata hai aur
`Maya-preview.apk` artifact upload karta hai.

## Configuration (secrets nahi, public config)

| Key | Kahan | Kya hai |
|---|---|---|
| `MAYA_BACKEND_URL` | `gradle.properties` | Backend URL (public) |
| `MAYA_SUPABASE_URL` | `gradle.properties` | Supabase project URL (public) |
| `MAYA_SUPABASE_ANON_KEY` | `local.properties` ya CI variable | Supabase **anon/public** key. Service-role key KABHI nahi. |
| `MAYA_KEYSTORE_PATH` + passwords | `local.properties` / CI secrets | App signing keystore (repo ke bahar) |

`local.properties` gitignored hai. CI me `MAYA_SUPABASE_ANON_KEY` repo Actions
variable se aata hai.

## Kya implement hai (approved designs ke hisaab se)

- Welcome, 12-group permission onboarding (2 pages, reasons + Allow/Skip)
- Sign in: email + password (Supabase Auth REST), Google OAuth deep link, skip
- Home: animated voice orb (idle/listening/speaking), status chips, quick actions
- Chat: backend `/v1/chat` se, voice input + TTS output (Hinglish, hi-IN)
- Memory: list/search/filter/delete via backend `/v1/memories`
- Tijori (Vault): biometric unlock, Keystore-encrypted secrets, add/list/delete,
  on-device audit log - secrets kabhi cloud/backend/log me nahi
- Settings: dark/light/system theme, background service toggle, floating orb,
  permission manager, clear memories (confirm ke saath)
- Cart-confirm dialog component: har payment/booking pe stop + read-back
- Error states (offline / server waking / quota) aur empty states
- Floating orb overlay service, foreground service (pause/stop), boot receiver
- Accessibility Service + Notification Listener: structured seams

## Jaan-boojh ke chhoda hua (seams, on-device testing chahiye)

- Always-on wake-word engine (low-power hotword model) - service seam ready
- Accessibility actions ka actual gesture execution (tap/type/scroll)
- Errand automation (Blinkit/ride booking flows) - cart-confirm UI ready
- Gemini function calling backend-side tool loop
