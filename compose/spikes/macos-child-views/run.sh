#!/bin/sh
# Builds Raven (Release, static) if needed, then runs the GL and Metal child-view spike in a real
# window on macOS (Vulkan through MoltenVK) and captures the window to child-views.bmp.
set -e
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../../.." && pwd)
build="$repo/compose/build/raven/spike"
[ -f "$build/libraven.a" ] || {
	cmake -S "$repo" -B "$build" -G Ninja -DCMAKE_BUILD_TYPE=Release -DRAVEN_BUILD_EXAMPLES=OFF \
		-DRAVEN_BUILD_TESTS=OFF -DRAVEN_BUILD_TOOLS=OFF -DRAVEN_BUILD_WEB=OFF
	cmake --build "$build"
}
cc -fobjc-arc -O1 -I"$repo/include" "$here/main.m" "$build/libraven.a" \
	-framework AppKit -framework QuartzCore -framework Metal -framework OpenGL -framework CoreFoundation \
	-o "$build/child-views"
"$build/child-views" "$build/child-views.bmp"
