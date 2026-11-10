# Injected into Raven's configure with CMAKE_PROJECT_INCLUDE. Once CMakeLists.txt has been read to
# the end, it writes RAVEN_LIBRARIES (one entry per line) to COMPOSE_RAVEN_LIBRARIES_FILE, so the
# Gradle build can check raven.def's linkerOpts against what CMake links. Raven's sources stay
# untouched.
function(compose_raven_write_link_libraries)
	set(entries "")
	foreach(library IN LISTS RAVEN_LIBRARIES)
		if(library)
			string(APPEND entries "${library}\n")
		endif()
	endforeach()
	file(WRITE "${COMPOSE_RAVEN_LIBRARIES_FILE}" "${entries}")
endfunction()

cmake_language(DEFER DIRECTORY "${CMAKE_SOURCE_DIR}" CALL compose_raven_write_link_libraries)
