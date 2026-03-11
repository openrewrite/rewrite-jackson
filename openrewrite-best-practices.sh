#!/bin/bash
set -e

INIT_SCRIPT="rewrite-best-practices-init.gradle"
GRADLE_PROPS="gradle.properties"
GRADLE_PROPS_BACKUP=""
RECIPES_CSV="src/main/resources/META-INF/rewrite/recipes.csv"
SKIP_CSV=false
SUBMODULE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -nocsv|--nocsv) SKIP_CSV=true; shift ;;
        -p|--project)
            [[ -z "$2" || "$2" == -* ]] && echo "Error: -p requires a submodule name" && exit 1
            SUBMODULE="$2"; shift 2 ;;
        *) echo "Usage: $0 [-nocsv] [-p <submodule>]"; exit 1 ;;
    esac
done

if [[ -x "./gradlew" ]]; then GRADLE_CMD="./gradlew"
elif command -v gradle &> /dev/null; then GRADLE_CMD="gradle"
else echo "Error: No ./gradlew or gradle found"; exit 1; fi

CSV_TASK_PREFIX=""
if [[ -n "$SUBMODULE" ]]; then
    [[ "$SUBMODULE" != :* ]] && SUBMODULE=":$SUBMODULE"
    CSV_TASK_PREFIX="$SUBMODULE:"
    SUBMODULE_PATH="${SUBMODULE#:}"; SUBMODULE_PATH="${SUBMODULE_PATH//://}"
    RECIPES_CSV="$SUBMODULE_PATH/src/main/resources/META-INF/rewrite/recipes.csv"
fi

[ "$SKIP_CSV" = false ] && git checkout origin/main -- "$RECIPES_CSV" 2>/dev/null || true

if [[ -f "$GRADLE_PROPS" ]]; then
    GRADLE_PROPS_BACKUP="${GRADLE_PROPS}.backup.$$"
    cp "$GRADLE_PROPS" "$GRADLE_PROPS_BACKUP"
fi

cat >> "$GRADLE_PROPS" << 'EOF'

# Temporary settings added by openrewrite-best-practices.sh
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g
EOF

cat > "$INIT_SCRIPT" << 'EOF'
initscript {
    repositories { maven { url 'https://plugins.gradle.org/m2' } }
    dependencies { classpath('org.openrewrite:plugin:latest.release') }
}
allprojects {
    plugins.apply(org.openrewrite.gradle.RewritePlugin)
    afterEvaluate {
        if (configurations.findByName('rewrite') != null) {
            dependencies { rewrite('org.openrewrite.recipe:rewrite-rewrite:latest.release') }
        }
        if (repositories.isEmpty()) { repositories { mavenCentral() } }
    }
}
EOF

cleanup() {
    rm -f "$INIT_SCRIPT"
    if [[ -n "$GRADLE_PROPS_BACKUP" ]]; then mv "$GRADLE_PROPS_BACKUP" "$GRADLE_PROPS"
    else rm -f "$GRADLE_PROPS"; fi
}
trap cleanup EXIT

$GRADLE_CMD --init-script "$INIT_SCRIPT" rewriteRun \
  -Drewrite.activeRecipe=org.openrewrite.recipes.rewrite.OpenRewriteRecipeBestPractices

[ "$SKIP_CSV" = false ] && $GRADLE_CMD ${CSV_TASK_PREFIX}recipeCsvGenerate

rm -f openrewrite-best-practices.sh
