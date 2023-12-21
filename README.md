# BluecordPatcher
BluecordPatcher is a CLI application, assisting with [Bluecord](https://github.com/bluemods/Bluecord) development.

It allows you to:
- Create a Bluecord APK from scratch
- Make changes to smali / xml code, and submit them back to the Bluecord repo as patches.
- Make changes to the Java / Kotlin source code, which this tool then builds into the APK and installs onto your Android device via ADB

## Requirements
- A Windows or Linux machine
- Java 1.8+
- ~2GB free space

## Download
- Download the latest JAR from the [releases](https://github.com/bluemods/BluecordPatcher/releases) tab.
- To build from source instead, ```git clone``` this repo, then run ```./gradlew jar```. The jar is output to the build/libs directory.

## Setup
Before running BluecordPatcher the first time, you need to do the following:
- Git clone the [Bluecord](https://github.com/bluemods/Bluecord) repository. Make sure it is updated.
- Create a config.ini file [(see example)](/examples/config.ini).

## Usage
``` java -jar BluecordPatcher.jar [arguments] [path/to/config.ini] ```

## Arguments Table
| Argument | Effect                                                                                                                                                                                                     |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| --patch  | Create patches in the Bluecord project from a modded Discord APK.<br>Use this when you make a change in the decompiled APK itself and want to commit it back to the Bluecord repo.                         |
| -q       | Quick mode. Allows the APK to be rebuilt faster if the only changes between builds are in the Java / Kotlin code in the Bluecord repository.<br>Note: the tool must be run at least once without this flag |
| -k       | Keeps debug information in the Smali files, useful for debugging stack traces from the built Bluecord app.                                                                                                 |
| -v       | Enables verbose logging to stdout                                                                                                                                                                          |

## TODOs
- Show download progress in the tool when downloading files
- Try to check if some of the required tools are in the users PATH and use them instead, which could save some bandwidth. This was skipped to reduce complexity, for now.
- Enable the patcher to change the package name on build
