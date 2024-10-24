#!/bin/bash

set -o errexit

if [[ $# -ne 1 ]];then
	echo "USAGE: <source-dir>"
	exit 1
fi

SRC=$1

declare -a in_prefixes=("rateunc-CAR Interface" "rateunc-CAR Intraslab" "rateunc-Crustal" "rateunc-MUE Interface" "rateunc-MUE Intraslab")
declare -a out_prefixes=("CAR_INTERFACE" "CAR_INTRASLAB" "CRUSTAL" "MUE_INTERFACE" "MUE_INTRASLAB")

UNIFORM_INTERFACE=0

arraylength=${#in_prefixes[@]}

for (( i=0; i<${arraylength}; i++ ));
do
	in_prefix=${in_prefixes[$i]}
	out_file="${out_prefixes[$i]}.csv"
	count=`find /tmp/ -type f 2> /dev/null | grep -c "$in_prefix"`
	if [[ $count -ne 1 ]];then
		echo "Found $count matches for prefix $in_prefix"
		exit 1
	fi
	in_file=`find /tmp/ -type f 2> /dev/null | grep "$in_prefix"`
	
	cp -v "$in_file" $out_file
done
