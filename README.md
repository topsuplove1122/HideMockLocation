# Hide Mock Location
Main Repo   

[![Release](https://img.shields.io/github/v/release/emotionbug/HideMockLocation?label=Release)](https://github.com/emotionbug/HideMockLocation/releases/latest)
[![Download](https://img.shields.io/github/downloads/emotionbug/HideMockLocation/total)](https://github.com/emotionbug/HideMockLocation/releases/latest)

## Summary
![Logo](app/src/main/res/mipmap-xhdpi/ic_launcher.png)

Hide Mock Location is an Xposed Module (now LSPosed on Android 11), which hides information 
about the 'Allow mock locations' setting on A12+ Devices.

## Usage
* Install module to your device.
* Enable module in LSPosed and reboot device.
  * System Framework
  * Target App
* That's it! You can open Hide Mock Location and view the "Test Location Data" page to view the status of the mock location setting.

## Tips
* You can view the "Test Location Data" page without enabling the module in LSPosed.
* Try enabling a mock location application before and after enabling the LSPosed module to view different output settings.
