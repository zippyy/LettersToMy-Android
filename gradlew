#!/bin/sh
# Local dev defaults only — never override an environment that already
# provides JAVA_HOME / ANDROID_HOME (e.g. GitHub Actions runners).
if [ -z "$JAVA_HOME" ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@17
fi
if [ -z "$ANDROID_HOME" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
fi
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# Download gradle if needed
GRADLE_VERSION=8.9
GRADLE_HOME=~/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}
if [ ! -d "$GRADLE_HOME" ]; then
    mkdir -p ~/.gradle/wrapper/dists
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    # Retry-aware download+verify: GitHub-hosted runners can truncate
    # large downloads from services.gradle.org; a single silent curl made
    # CI fail nondeterministically with a corrupt zip.
    ok=0
    for attempt in 1 2 3; do
        if curl -fsSL --retry 5 --retry-all-errors --connect-timeout 20 \
            "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}.zip" -o /tmp/gradle.zip \
            && unzip -qo /tmp/gradle.zip -d /tmp/; then
            ok=1
            break
        fi
        echo "Gradle download attempt ${attempt} failed/corrupt; retrying..." >&2
        rm -f /tmp/gradle.zip
        sleep 3
    done
    if [ "$ok" -ne 1 ]; then
        echo "Failed to download Gradle ${GRADLE_VERSION} after retries" >&2
        exit 1
    fi
    mv /tmp/gradle-${GRADLE_VERSION} "$GRADLE_HOME"
    rm -f /tmp/gradle.zip
fi
GRADLE_EXE="$GRADLE_HOME/bin/gradle"
exec "$GRADLE_EXE" "$@"