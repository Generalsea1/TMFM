# TMFM M0 — Redmi 10 evidence gate

## Device information supplied by the owner

- Manufacturer: Xiaomi
- Model: Redmi 10
- SoC: MediaTek Helio G88
- Android: 13
- MIUI: 14
- 3.5 mm headset jack: reported present
- FM radio capability: reported/documented, but third-party TMFM access is NOT VERIFIED.
- Wired headset as antenna: reported/expected for OEM FM implementation, NOT VERIFIED by ADB evidence.

## Required physical evidence

Run with the target device connected through ADB:
- adb devices -l
- adb shell getprop ro.product.manufacturer
- adb shell getprop ro.product.model
- adb shell getprop ro.build.version.release
- adb shell getprop ro.build.version.sdk
- adb shell getprop ro.board.platform
- adb shell getprop ro.hardware
- adb shell uname -a
- adb shell pm list features | grep -i -E "radio|fm"
- adb shell dumpsys broadcastradio
- adb shell service list | grep -i -E "radio|fm"
- adb shell pm list packages -s | grep -i -E "fm|radio"
- adb shell pm list permissions -f | grep -A4 ACCESS_BROADCAST_RADIO
- adb shell dumpsys media.audio_policy | grep -i -E "fm|radio"
- adb shell getprop | grep -i -E "fm|radio"

Also record whether the OEM FM application can tune a real station with a wired headset.

## Gate
NOT VERIFIED. PATH A/B/C cannot be declared without the physical evidence and a real tuner callback.
