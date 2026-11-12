/*
 * Linux: libraven is built with the system's C compiler, against its glibc, but Kotlin/Native links
 * against its own glibc 2.19 sysroot. These are the newer glibc symbols libraven refers to, defined
 * with what 2.19 has. A newer Raven that needs another one fails to link, naming it.
 *
 * Built as its own static library (raven-native/build.gradle.kts): code in raven.def would be
 * internalized by Kotlin/Native and never seen by libraven.a. Compiled as strict C11 with only
 * _DEFAULT_SOURCE, so glibc's headers don't turn these calls into the C23 versions again.
 */
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/syscall.h>
#include <unistd.h>

/* glibc 2.38's C23 versions, which differ only in reading "0b" prefixes: Raven writes none. */
long __isoc23_strtol(const char* text, char** end, int base) { return strtol(text, end, base); }
long long __isoc23_strtoll(const char* text, char** end, int base) { return strtoll(text, end, base); }
unsigned long long __isoc23_strtoull(const char* text, char** end, int base) { return strtoull(text, end, base); }

int __isoc23_sscanf(const char* text, const char* format, ...) {
    va_list args;
    int read;
    va_start(args, format);
    read = vsscanf(text, format, args);
    va_end(args);
    return read;
}

/* glibc 2.27: Raven's Wayland backend makes its shared memory with it. */
int memfd_create(const char* name, unsigned int flags) { return (int)syscall(__NR_memfd_create, name, flags); }
