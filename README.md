# Rabbah Device Enrollment - source code

Two projects:

1. hmdm-launcher-rabbah/  (the main app - Headwind MDM launcher with Rabbah auto-enroll built in)
   - New: app/src/main/java/com/hmdm/launcher/util/RabbahEnroll.java   (serial -> Rabbah API -> device code -> MDM check -> device ID)
   - New: app/src/main/java/com/hmdm/launcher/ui/RabbahEnrollActivity.java  (branded progress screen, 4 static check marks)
   - Changed: ui/MainActivity.java (Rabbah enrollment runs first when the device ID is empty), app/build.gradle (config), AndroidManifest.xml
   Config (app/build.gradle):
     BASE_URL / SECONDARY_BASE_URL  https://mdm.rabbah.sa
     DEVICE_ID_CHOICE               "rabbah"
     RABBAH_API_URL                 https://dev-api.rabbah.sa/api/v1   (UAT / production when /devices/enroll is deployed there)
     RABBAH_ENROLL_KEY              "" (set the x-device-enrollment-key)
     RABBAH_TEST_SERIAL             empty unless built with -PrabbahTestSerial=<serial> (test phones only)
   Build (JDK 17 required; JDK 25 breaks Gradle 8.13):
     set JAVA_HOME=<JDK 17>
     gradlew.bat assembleDebug
     output: app/build/outputs/apk/opensource/debug/app-opensource-debug.apk

2. rabbah-enroll-agent/  (standalone enrollment app, optional - same flow without the MDM launcher)
   Build: powershell -File build.ps1   (needs Android SDK build-tools 35.0.0, platform android-34, JDK)

Notes
- Android 10+ only gives the device serial to system apps / device owners. On a normal phone the app stops at "Serial number unavailable".
- Release builds must be signed with the release key (builds here use the debug key).
- /devices/enroll is deployed on dev only (UAT and production return 404 Cannot POST).
