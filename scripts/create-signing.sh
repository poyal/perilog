#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -e signing.properties ] || [ -e .signing/perilog-release.p12 ]; then
  echo 'A signing key already exists. Keep it for future updates.'
  exit 1
fi
umask 077
mkdir -p .signing
python3 - <<'PY'
from pathlib import Path
import secrets
password = secrets.token_urlsafe(36)
Path('.signing/password').write_text(password)
Path('signing.properties').write_text('storeFile=.signing/perilog-release.p12\nstorePassword='+password+'\nkeyAlias=perilog\nkeyPassword='+password+'\n')
PY
if [ -d "$PWD/.tools/jdk" ]; then export JAVA_HOME="$PWD/.tools/jdk"; fi
"${JAVA_HOME:?Set JAVA_HOME to JDK 17}/bin/keytool" -genkeypair -keystore .signing/perilog-release.p12 -storetype PKCS12 -storepass:file .signing/password -keypass:file .signing/password -alias perilog -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Perilog Personal, OU=Personal Android App'
echo 'Private signing files created in .signing/ and signing.properties. Back up both securely. Never commit them.'
