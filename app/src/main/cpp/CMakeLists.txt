cmake_minimum_required(VERSION 3.18.1)
project(sdrapp_audio)

add_library(sdrapp_audio SHARED aaudio_wrapper.cpp)

find_library(log-lib log)
find_library(aaudio-lib aa)

target_link_libraries(sdrapp_audio ${log-lib} ${aaudio-lib})
