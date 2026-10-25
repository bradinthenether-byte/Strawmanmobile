# RoboJimmy9000

RoboJimmy9000 is a native Android chat app that runs an AI model on the phone.
It does not send chat messages to a cloud AI service. The first launch downloads
the 475 MiB Qwen3 0.6B model; after that, inference works offline.

## Build the APK

Requirements: JDK 17 and Android SDK Platform 36. Gradle downloads any
additional Android build tools it needs.

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it
with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Model and network use

The quantized model is downloaded from
[Hugging Face](https://huggingface.co/litert-community/Qwen3-0.6B) on first use
and stored in the app's private storage. The 497,516,544-byte model is
distributed under Apache-2.0. Network access is only needed to fetch the model;
chat inference runs locally using Google's LiteRT-LM Android runtime. Allow at
least 575 MiB of free storage before downloading. The model has a 2,048-token
context and needs more working memory than its download size; performance varies
by phone.

The app targets Android 15 and supports Android 8.0 and later on ARM64 or
x86_64 devices. Local model performance depends on the phone's available memory
and processor.
