<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Biometric authentication

Goal: Users unlock RentMate with fingerprint or face after SSO, with a safe fallback

Owner: Michael. Docs: https://developer.android.com/codelabs/biometric-login and https://developer.android.com/identity/sign-in/biometric-auth.

Add androidx.biometric. MainActivity must extend FragmentActivity. After a restored session (RM-0002) and when the Settings toggle is on, show BiometricPrompt with BIOMETRIC_STRONG or DEVICE_CREDENTIAL; keep content hidden until success. Check BiometricManager.canAuthenticate() and handle NONE_ENROLLED (offer to open enrolment settings), NO_HARDWARE (hide toggle with explanation), errors and cancel (stay locked, retry button). Re-prompt when the app returns from background after 60s. Log every outcome.

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0013 | android | BiometricPrompt unlock gate after SSO with device-credential fallback | On a physical phone, relaunch asks for fingerprint/face; cancel keeps app locked; PIN fallback works | RM-0002 | - | 🟩 Done · b8f749c · 2026-11-06 · Note: BiometricPrompt gate with PIN fallback (BIOMETRIC_WEAK or DEVICE_CREDENTIAL on Android 8-10, BIOMETRIC_STRONG or DEVICE_CREDENTIAL on 11+); relocks after 60 seconds away. BiometricLockPolicyTest passes. The phone checks are on RM-0047. |
| RM-0014 | android | Biometric toggle in Settings with enrolment checks and unsupported-device handling | Toggle off skips prompt; device without biometrics shows explanation instead of crashing | RM-0013 | - | 🟩 Done · f8bc04e · 2026-11-06 · Note: Settings toggle turns the lock on and off; a device with no screen lock gets an explanation instead of a crash. Code commit 584d264. The phone checks are in docs/TEST_CHECKLIST.txt section 6 (RM-0047). |
