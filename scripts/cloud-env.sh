# Carregar com: source scripts/cloud-env.sh (somente neste ambiente Codex).
export ANDROID_HOME=/workspace/toolchains/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME=/workspace/toolchains/android-user
export GRADLE_USER_HOME=/workspace/toolchains/gradle-cache
for agendapp_jdk in /workspace/toolchains/jdk-*; do
    if [ -x "$agendapp_jdk/bin/javac" ]; then export JAVA_HOME="$agendapp_jdk"; fi
done
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
if [ -f /etc/ssl/certs/java/cacerts ]; then
    export SDKMANAGER_OPTS="${SDKMANAGER_OPTS:+$SDKMANAGER_OPTS }-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
    export GRADLE_OPTS="${GRADLE_OPTS:+$GRADLE_OPTS }-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
