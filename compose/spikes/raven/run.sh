#!/bin/sh
# Builds Raven (Release, static) if needed and runs the group A spikes headless.
set -e
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../../.." && pwd)
build="$repo/compose/build/raven/spike"
[ -f "$build/libraven.a" ] || {
	cmake -S "$repo" -B "$build" -G Ninja -DCMAKE_BUILD_TYPE=Release -DRAVEN_BUILD_EXAMPLES=OFF \
		-DRAVEN_BUILD_TESTS=OFF -DRAVEN_BUILD_TOOLS=OFF -DRAVEN_BUILD_WEB=OFF
	cmake --build "$build"
}
case "$(uname)" in
	Darwin) libs="-framework AppKit -framework QuartzCore -framework Metal -framework CoreFoundation" ;;
	*) libs="-lX11 -ldl -lpthread -lm" ;;
esac
cc -std=c99 -O1 -I"$repo/include" "$here/spikes.c" "$build/libraven.a" $libs -o "$build/spikes"
"$build/spikes"
