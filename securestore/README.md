# securestore

Tiny key-value store for secrets (tokens, passwords) behind one interface:

| Platform | Backing |
|---|---|
| Android | AES-GCM with a non-exportable Android Keystore key, values kept in private SharedPreferences |
| iOS | Keychain generic passwords, readable after first unlock, this device only |
| Desktop, Web | in memory only (nothing persisted) |

```kotlin
// Koin: includes(secureStoreModule), then
val store: SecureStore = get()
store.put("refresh_token", token)   // null removes the key
store.get("refresh_token")
```

Android needs `androidContext(...)` in `startKoin`.
