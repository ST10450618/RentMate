<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Biometric authentication

Goal: Users unlock RentMate with fingerprint or face after SSO, with a safe fallback

Owner: Michael. Docs: https://developer.android.com/codelabs/biometric-login and https://developer.android.com/identity/sign-in/biometric-auth.

Add androidx.biometric. MainActivity must extend FragmentActivity. After a restored session (RM-0002) and when the Settings toggle is on, show BiometricPrompt with BIOMETRIC_STRONG or DEVICE_CREDENTIAL; keep content hidden until success. Check BiometricManager.canAuthenticate() and handle NONE_ENROLLED (offer to open enrolment settings), NO_HARDWARE (hide toggle with explanation), errors and cancel (stay locked, retry button). Re-prompt when the app returns from background after 60s. Log every outcome.

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0013 | android | BiometricPrompt unlock gate after SSO with device-credential fallback | On a physical phone, relaunch asks for fingerprint/face; cancel keeps app locked; PIN fallback works | RM-0002 | - | 🟥 Pending |
| RM-0014 | android | Biometric toggle in Settings with enrolment checks and unsupported-device handling | Toggle off skips prompt; device without biometrics shows explanation instead of crashing | RM-0013 | - | 🟥 Pending |
