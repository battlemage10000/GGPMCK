#!/bin/bash

# $1 = OWD (working directory)
function run
{
	OWD="$1"
	shift

	echo $PWD
	echo $OWD
	echo "arg1: "$1
	
	# Runtime env state
	echo "Start time: "$(date)
	echo "System Info: "$(uname -mprs)
	echo
	mck "$@"
}

# $1 = log file path
function redirectStdErrOut
{
	if [ "$1" != "" ]; then
		logFile="$1"
	else
		logFile="runmck.log"
	fi
	exec 1<&-
	exec 2<&-
	exec 1<>"$logFile"
	exec 2>&1
}

# redirect stdout to log file and run in background
function runAsDaemon
{

	if [ "$1" == "--log-file" ]; then
		shift
		logFile="$1"
		shift
		redirectStdErrOut "$logFile"
	fi
	
	OWD=$PWD
	cd /
	run "$OWD" "$@" &
	disown -h
}

# process arguments and run "run" function
# arg1 = --daemon
# arg2 = --log-file path/to/log
function main
{
	echo $0 $@
	daemonArg="F"

	if [ "$1" == "--daemon" ]; then 
		daemonArg="T"
		shift
	fi

	if [ "$1" == "--log-file" ]; then
		shift
		logFile="$1"
		shift
		redirectStdErrOut "$logFile"
	fi

	if [ "$daemonArg" == "T" ]; then 
		runAsDaemon "$@"
	else
		run $PWD "$@"
	fi
}

# Run main bash function with all arguments
# Note: no other line should be outside of a function
main "$@"
