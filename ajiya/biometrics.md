<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# Biometric authentication

Goal: Users unlock RentMate with fingerprint or face after SSO, with a safe fallback

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0013 | android | BiometricPrompt unlock gate after SSO with device-credential fallback | On a physical phone, relaunch asks for fingerprint/face; cancel keeps app locked; PIN fallback works | RM-0002 | - | 🟥 Pending |
| RM-0014 | android | Biometric toggle in Settings with enrolment checks and unsupported-device handling | Toggle off skips prompt; device without biometrics shows explanation instead of crashing | RM-0013 | - | 🟥 Pending |
