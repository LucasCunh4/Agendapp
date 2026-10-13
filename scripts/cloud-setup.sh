#!/usr/bin/env bash
set -euo pipefail
cd /workspace/Agendapp
mkdir -p /workspace/toolchains
python3 - <<'PY'
import urllib.request,xml.etree.ElementTree as ET,hashlib,pathlib,zipfile,tarfile
root=pathlib.Path('/workspace/toolchains')
if not any((p/'bin/javac').exists() for p in root.glob('jdk-*')):
    base='https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz'
    checksum=urllib.request.urlopen(base+'.sha256').read().decode().strip().split()[0]
    archive=root/'jdk21.tar.gz'; urllib.request.urlretrieve(base,archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=checksum: raise RuntimeError('Checksum do JDK inválido')
    with tarfile.open(archive) as t: t.extractall(root,filter='data')
sdk=root/'android-sdk'
if not (sdk/'cmdline-tools/latest/bin/sdkmanager').exists():
    base='https://dl.google.com/android/repository/'
    xml=ET.fromstring(urllib.request.urlopen(base+'repository2-1.xml').read())
    package=next(p for p in xml.iter() if p.tag.endswith('remotePackage') and p.attrib.get('path')=='cmdline-tools;13.0')
    archive=next(a for a in package.iter('archive') if a.findtext('host-os')=='linux').find('complete')
    filename=archive.findtext('url'); target=root/filename
    urllib.request.urlretrieve(base+filename,target)
    if hashlib.sha1(target.read_bytes()).hexdigest()!=archive.findtext('checksum'): raise RuntimeError('Checksum do SDK inválido')
    with zipfile.ZipFile(target) as z: z.extractall(sdk/'cmdline-tools')
    (sdk/'cmdline-tools/cmdline-tools').rename(sdk/'cmdline-tools/latest')
    for f in (sdk/'cmdline-tools/latest/bin').iterdir(): f.chmod(0o755)
PY
source scripts/cloud-env.sh
mkdir -p "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
agendapp_sdk_proxy=()
if [ -n "${HTTPS_PROXY:-}" ]; then
    read -r agendapp_proxy_host agendapp_proxy_port < <(python3 - <<'PY'
import os,urllib.parse
p=urllib.parse.urlsplit(os.environ['HTTPS_PROXY']);print(p.hostname,p.port or 80)
PY
)
    agendapp_sdk_proxy=(--proxy=http --proxy_host="$agendapp_proxy_host" --proxy_port="$agendapp_proxy_port")
fi
python3 - <<'PY'
import os,pathlib,urllib.parse
file=pathlib.Path(os.environ['GRADLE_USER_HOME'])/'gradle.properties'
props={}
proxy=os.environ.get('HTTPS_PROXY')
if proxy:
    url=urllib.parse.urlsplit(proxy)
    for scheme in ('http','https'):
        props[f'systemProp.{scheme}.proxyHost']=url.hostname
        props[f'systemProp.{scheme}.proxyPort']=str(url.port or 80)
if pathlib.Path('/etc/ssl/certs/java/cacerts').exists():
    props['systemProp.javax.net.ssl.trustStore']='/etc/ssl/certs/java/cacerts'
lines=file.read_text().splitlines() if file.exists() else []
lines=[line for line in lines if line.split('=',1)[0] not in props]
file.write_text('\n'.join(lines+[f'{k}={v}' for k,v in props.items()])+'\n')
PY
# O yes termina com SIGPIPE quando sdkmanager fecha a entrada; preserve o status do SDK.
set +o pipefail
yes | sdkmanager "${agendapp_sdk_proxy[@]}" --licenses > /tmp/agendapp-sdk-licenses.log
set -o pipefail
sdkmanager "${agendapp_sdk_proxy[@]}" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
if [ ! -f local.properties ]; then printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties; fi
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug --console=plain
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify app/build/outputs/apk/debug/app-debug.apk
