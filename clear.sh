#!/bin/bash

echo "Cleaning Gradle project..."
bash ./gradlew clean

echo "Removing build and cache directories..."
# Remove root build and .gradle cache
rm -rf build/
rm -rf .gradle/

# Remove module build directories
rm -rf androidApp/build/
rm -rf composeApp/build/
rm -rf shared/build/

# Remove iOS specific build directories (if applicable)
rm -rf iosApp/iosApp.xcworkspace/xcuserdata/
rm -rf iosApp/Pods/
rm -rf iosApp/build/

# Remove Kotlin multiplatform caches
rm -rf .kotlin/

# Remove macOS generated files
find . -name ".DS_Store" -type f -delete

echo "Project cleaned successfully! Only necessary source files remain."
