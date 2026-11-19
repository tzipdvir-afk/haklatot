cmake_minimum_required(VERSION 3.22.1)
project(haklatot_mp3 C)

# Official LAME 3.100 encoder sources (unmodified), see lame/SOURCE.txt
file(GLOB LAME_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame/*.c)

add_library(mp3encoder SHARED ${LAME_SOURCES} mp3_encoder_jni.c)

target_include_directories(mp3encoder PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/lame/include
        ${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame)

# Replaces what LAME's ./configure would normally generate in config.h
target_compile_definitions(mp3encoder PRIVATE
        STDC_HEADERS=1
        HAVE_STDINT_H=1
        HAVE_INTTYPES_H=1
        HAVE_LIMITS_H=1
        HAVE_ERRNO_H=1
        HAVE_FCNTL_H=1
        ieee754_float32_t=float)

target_compile_options(mp3encoder PRIVATE -O2 -w -fno-strict-aliasing)
target_link_libraries(mp3encoder m)
