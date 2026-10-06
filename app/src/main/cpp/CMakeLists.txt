cmake_minimum_required(VERSION 3.22.1)
project(cirro_jni C)

# Cirro JNI glue + libnetease, built WITHOUT libcurl. At runtime the app
# installs an OkHttp-backed transport over JNI (see CirroTransport),
# so libnetease never hard-depends on a network/TLS stack.

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# libnetease submodule lives at the repo root. externalNativeBuild runs cmake
# from app/.cxx, so CMAKE_CURRENT_LIST_DIR == <root>/app/src/main/cpp; step
# up four levels to the repository root.
get_filename_component(CIRRO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
set(CIRRO_NETEASE_SRC "${CIRRO_ROOT}/libnetease")

# Snapshot before add_subdirectory so libnetease's `option()` reads them.
set(NE_USE_CURL OFF CACHE BOOL "Build without the libcurl transport (OkHttp via JNI)" FORCE)

# Pull in the `netease` static library. EXCLUDE_FROM_ALL keeps netease-cli and
# the test executables out of the Android build; only netease is needed.
add_subdirectory("${CIRRO_NETEASE_SRC}" libnetease-build EXCLUDE_FROM_ALL)

add_library(cirro_jni SHARED libnetease_jni.c)
target_include_directories(cirro_jni PRIVATE "${CIRRO_NETEASE_SRC}/include")
target_link_libraries(cirro_jni PRIVATE netease)