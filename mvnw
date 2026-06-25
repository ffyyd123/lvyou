#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup script
# ----------------------------------------------------------------------------

MVNW_PROJECTBASEDIR="$(cd "$(dirname "$0")" && pwd)"
MVNW_USERHOME="${HOME}/.m2/wrapper"
MAVEN_HOME="${MAVEN_HOME:-$MVNW_USERHOME/dists/apache-maven-3.9.9}"
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1024m -XX:MaxMetaspaceSize=256m}"

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="$(command -v java)"
fi

if [ ! -x "$JAVACMD" ]; then
    echo "ERROR: Java not found. Please install JDK 17+ or set JAVA_HOME."
    exit 1
fi

# Download & install Maven if not present
if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
    echo "Downloading Apache Maven 3.9.9..."
    mkdir -p "$MVNW_USERHOME/dists"

    if command -v curl > /dev/null 2>&1; then
        curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip" -o "$MVNW_USERHOME/dists/maven-3.9.9-bin.zip"
    elif command -v wget > /dev/null 2>&1; then
        wget -q "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip" -O "$MVNW_USERHOME/dists/maven-3.9.9-bin.zip"
    else
        echo "ERROR: Neither curl nor wget found. Cannot download Maven."
        exit 1
    fi

    echo "Extracting Maven..."
    unzip -qo "$MVNW_USERHOME/dists/maven-3.9.9-bin.zip" -d "$MVNW_USERHOME/dists"
    rm -f "$MVNW_USERHOME/dists/maven-3.9.9-bin.zip"
    echo "Maven installed successfully."
fi

# Run Maven
MVN_CMD="$MAVEN_HOME/bin/mvn"
chmod +x "$MVN_CMD" 2>/dev/null
exec "$MVN_CMD" "$@"
