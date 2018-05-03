#!/usr/bin/env bash
set -xeuo pipefail

# Add the project name to the settings.gradle
echo "rootProject.name = '$MCS_MODULE_NAME'"  >> settings.gradle
