cmake_minimum_required(VERSION 3.18.1)
project(sdrapp_core LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(sdrapp_core SHARED
    jni_bridge.cpp
    signal_path/signal_path.cpp
    signal_path/source.cpp
    signal_path/demod.cpp
    signal_path/sink.cpp
)

target_include_directories(sdrapp_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/signal_path
    ${CMAKE_CURRENT_SOURCE_DIR}/buffer
)

target_link_libraries(sdrapp_core
    log
    aaudio
)

target_compile_options(sdrapp_core PRIVATE
    -Wall -Wextra -Wno-unused-parameter
    -O2 -DNDEBUG
)