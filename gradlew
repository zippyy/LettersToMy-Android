#!/bin/sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=~/Library/Android/sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# Download gradle if needed
GRADLE_VERSION=8.9
GRADLE_HOME=~/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}
if [ ! -d "$GRADLE_HOME" ]; then
    mkdir -p ~/.gradle/wrapper/dists
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    curl -sL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}.zip" -o /tmp/gradle.zip
    unzip -qo /tmp/gradle.zip -d /tmp/ && mv /tmp/gradle-${GRADLE_VERSION} ~/.gradle/wrapper/dists/
    rm -f /tmp/gradle.zip
fi
GRADLE_EXE="$GRADLE_HOME/bin/gradle"
exec "$GRADLE_EXE" "$@"
