cmake_minimum_required(VERSION 3.22)
project(latinime CXX)

file(GLOB_RECURSE sources CONFIGURE_DEPENDS latinime/*.cpp)
add_library(jni_latinime SHARED ${sources})
target_include_directories(jni_latinime PRIVATE latinime/src)
target_compile_options(jni_latinime PRIVATE -Wall -Wno-unused-parameter -Wno-unused-function)
target_link_options(jni_latinime PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(jni_latinime log)
