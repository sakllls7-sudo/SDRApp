cmake_minimum_required(VERSION 3.18.1)
project(sdrapp_core LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# RTLSDR sources (included directly)
set(RTLSDR_SOURCES
    rtlsdr/librtlsdr.c
    rtlsdr/tuner_r82xx.c
)

add_library(sdrapp_core SHARED
    jni_bridge.cpp
    signal_path/signal_path.cpp
    signal_path/source.cpp
    signal_path/demod.cpp
    signal_path/sink.cpp
    ${RTLSDR_SOURCES}
)

target_include_directories(sdrapp_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/signal_path
    ${CMAKE_CURRENT_SOURCE_DIR}/buffer
    ${CMAKE_CURRENT_SOURCE_DIR}/rtlsdr
)

target_link_libraries(sdrapp_core
    log
    aaudio
)

# RTLSDR needs some defines
target_compile_definitions(sdrapp_core PRIVATE
    -DRTLSDR_HAS_GETDEVICECOUNT=1
    -DRTLSDR_HAS_GETDEVICEUSBSTRINGS=1
)

# Compiler flags
target_compile_options(sdrapp_core PRIVATE
    -Wall -Wextra -Wno-unused-parameter
    -O2 -DNDEBUG
)