#!/usr/bin/env sh
# Minimal gradlew shim — CI uses gradle/actions/setup-gradle, this file satisfies wrapper check
exec gradle "$@"
