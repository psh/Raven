#!/bin/sh
# Builds Raven (Release, static) if needed, then shows dot.json rendered by ThorVG (Homebrew's
# thorvg: brew install thorvg) over a custom-drawn widget in a real window on macOS, capturing the
# window at the first and last frames to thorvg-<frame>.bmp beside libraven.a.
set -e
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../../.." && pwd)
build="$repo/compose/build/raven/spike"
thorvg=$(brew --prefix thorvg)
[ -f "$build/libraven.a" ] || {
	cmake -S "$repo" -B "$build" -G Ninja -DCMAKE_BUILD_TYPE=Release -DRAVEN_BUILD_EXAMPLES=OFF \
		-DRAVEN_BUILD_TESTS=OFF -DRAVEN_BUILD_TOOLS=OFF -DRAVEN_BUILD_WEB=OFF
	cmake --build "$build"
}
cc -std=c11 -O1 -I"$repo/include" -I"$thorvg/include/thorvg-1" "$here/window.c" "$build/libraven.a" \
	-L"$thorvg/lib" -lthorvg-1 -Wl,-rpath,"$thorvg/lib" \
	-framework AppKit -framework QuartzCore -framework Metal -framework CoreFoundation -lobjc \
	-o "$build/thorvg-window"
"$build/thorvg-window" "$here/dot.json" "$build/thorvg"
