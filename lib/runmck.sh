#!/bin/bash

# Function declarations
function timer
{
	echo "Runtime: "$(($(date +"%s") - startTime))" seconds"
}

function usage
{
	echo "Usage: runmck.sh mck-file [log-file]"
}

# doesnt work
function daemonize
{
	{
		cd /
		#umask 0
	} &
	disown -h
}

# $1 = logFile
function redirectStdout
{
	logFile="$1"
	if [ "$logFile" != "" ]; then
		exec 1<&- # close out
		exec 2<&- # close err
		exec 1<>"$logFile" # out to log file
		exec 2>&1 # err to out
	fi
}

function main 
{
	# Configure
	startTime=$(date +%s)
	
	if [ "$1" == "-d" ]; then
		daemon="true"
		shift
	else
		daemon="false"
	fi

	if [ "$1" != "" ]; then
		mckFile="$1"
		shift
	else
		echo "Invalid argument: not a file"
		usage
		exit 1
	fi


	if [ "$1" != "" ]; then
		logFile="$1"
	else
		logFile="$mckFile"$(date +".%m.%d_%H.%M.%S")".log"
	fi
	redirectStdout "$logFile"
	
	if [ $daemon = "true" ]; then
		daemonize
	fi

	# Print System info
	echo "Start time: "$(date)
	echo "System Info: "$(uname -mprs)

	 Run
	if [ -f $mckFile ]; then
		echo "Input File: "$mckFile
		log=$(mck $mckFile)
	else
		usage
		exit
	fi

	# Timer (don't do computations after this line)
	echo "Runtime: "$(($(date +"%s") - $startTime))" seconds"

	# Output
	echo "$log" >> "$logFile"
}

main "$@"