# Family Tree for Android

Source code for Family Tree for Android 1.2, made by Victor.

An offline family tree viewer: people list and search, person details with sources, ancestor (pedigree) and descendant charts, stats, and GEDCOM import and export.

## Download

Download and install the app for free from [kodiakking.com](https://kodiakking.com).

## Build

Requirements: JDK 17 or 21, Android SDK (compileSdk 35), or Android Studio.

```
./gradlew assembleDebug        # debug APK: app/build/outputs/apk/debug/
./gradlew assembleRelease      # release APK: app/build/outputs/apk/release/
```

Windows: use `gradlew.bat` instead of `./gradlew`. Create a `local.properties` with `sdk.dir=...` if `ANDROID_HOME` is not set (Android Studio does this for you).

### Signing (optional)

Release signing reads `keystore/keystore.properties`, which is not in the repository. Copy `keystore/keystore.properties.example` to `keystore/keystore.properties`, put your own keystore in `keystore/`, and fill in the values. Without it the release build is unsigned.

## Notes

**Family data is not included.** The app bundles its data at build time. By default it uses the small fictional dataset in `sample-data/` (people.json, families.json, events.json, sources.json, places.json). To build with your own data, put files with the same layout in a folder and run `./gradlew assembleRelease -PfamilyData=/path/to/your/data`.

## License

No license has been chosen for this project yet.
