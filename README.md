# Tantal Keyboard

The keyboard for Tantal (`app.tantal.keyboard`). Our own Compose interface
over the word engine of AOSP LatinIME; not a fork of the LatinIME app.

Done: layouts, symbols, a number pad, emoji with skin tones and recent ones.
Suggestions, next-word predictions and auto-correction from the LatinIME
dictionaries; No network access; nothing typed is stored except the recent
emoji.

## Building

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Needs JDK 21, NDK 30.0.16248370, and `local.properties` with `sdk.dir`
pointing to an Android SDK that has the `android-37.2` platform.

## Contributing

See the [contribution rules](https://github.com/tantal-os/.github/blob/main/CONTRIBUTING.md)
and the [security policy](https://github.com/tantal-os/.github/blob/main/SECURITY.md).
Report vulnerabilities privately, not in an issue.

## License

Apache-2.0, see `LICENSE`. The LatinIME engine and dictionaries are
Apache-2.0, see `app/src/main/cpp/latinime/NOTICE`. The bundled Roboto Flex
font is under the SIL Open Font License, see `app/RobotoFlex-OFL.txt`.
