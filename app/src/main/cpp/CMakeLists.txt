cmake_minimum_required(VERSION 3.22.1)
project("chatcore")

add_library(chatcore SHARED native-lib.cpp)

find_library(log-lib log)
target_link_libraries(chatcore ${log-lib})
