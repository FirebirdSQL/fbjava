cmake_minimum_required(VERSION 3.8)

project(libfbjava CXX)

set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_SHARED_LIBRARY_PREFIX "")
set(CMAKE_VERBOSE_MAKEFILE ON)
set (CMAKE_EXPORT_COMPILE_COMMANDS ON)

set(JAVA_AWT_LIBRARY NotNeeded)
set(JAVA_JVM_LIBRARY NotNeeded)
set(JAVA_AWT_INCLUDE_PATH NotNeeded)
find_package(JNI REQUIRED)

include_directories(${JNI_INCLUDE_DIRS} "include")

add_library(${PROJECT_NAME} SHARED
	"fbjava.cpp"
)

if (MSVC)
	add_definitions(-D_CRT_SECURE_NO_WARNINGS)

	target_compile_options(${PROJECT_NAME} PRIVATE
		"$<$<CONFIG:Release>:/MT>"
		"$<$<CONFIG:Debug>:/MTd>"
	)
else()
	target_compile_options(${PROJECT_NAME} PRIVATE
		-fvisibility=hidden
	)

	target_link_libraries(${PROJECT_NAME}
		-static-libgcc
		-static-libstdc++
		-Wl,--exclude-libs,ALL
	)
endif()

set_property(TARGET ${PROJECT_NAME} PROPERTY CXX_STANDARD 17)
