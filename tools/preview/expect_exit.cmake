# Runs PROGRAM with ARGS (separated by |) and checks that it exits with EXPECTED, and that
# what it prints holds OUTPUT (a regular expression) when given. When the program exits with 77
# and that is not what is expected (no Vulkan to render with), it prints "raven_preview SKIPPED",
# which the test's SKIP_REGULAR_EXPRESSION reports as skipped.
#
#   cmake -DPROGRAM=raven_preview "-DARGS=a.xml|--null" -DEXPECTED=77 [-DOUTPUT=text] -P expect_exit.cmake
string(REPLACE "|" ";" _args "${ARGS}")
execute_process(COMMAND "${PROGRAM}" ${_args} RESULT_VARIABLE _code OUTPUT_VARIABLE _out ERROR_VARIABLE _err)
if(_code EQUAL 77 AND NOT EXPECTED EQUAL 77)
	message(FATAL_ERROR "raven_preview SKIPPED: ${_err}")
endif()
if(NOT _code STREQUAL "${EXPECTED}")
	message(FATAL_ERROR "${PROGRAM} exited with ${_code}, not ${EXPECTED}\n${_out}${_err}")
endif()
if(DEFINED OUTPUT AND NOT "${_out}${_err}" MATCHES "${OUTPUT}")
	message(FATAL_ERROR "${PROGRAM} did not say \"${OUTPUT}\":\n${_out}${_err}")
endif()
message("${PROGRAM} exited with ${_code} as expected")
