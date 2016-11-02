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

function main 
{
	# Configure
	startTime=$(date +%s)

	if [ "$1" != "" ]; then
		mckFile="$1"
		shift
	else
		echo "Invalid argument: not a file"
		usage
		exit
	fi

	if [ "$1" != "" ]; then
		logFile="$1"
	else
		logFile="$mckFile"$(date +".%m.%d_%H.%M.%S")".log"
	fi

	# Print System info
	header="Start time: "$(date)"\n"
	header="$header""System Info: "$(uname -mprs)"\n"

	# Run
	if [ -f $mckFile ]; then
		header="$header""Input File: "$mckFile"\n"
		log=$(mckAlt $mckFile)
	else 
		usage
		exit
	fi

	# Timer (don't do computations after this line)
	header="$header""Runtime: "$(($(date +"%s") - $startTime))" seconds\n"

	# Output
	echo "$header" > "$logFile"
	echo "$log" >> "$logFile"
}

main "$@"