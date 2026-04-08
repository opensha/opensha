#!/bin/bash

set -o errexit

if [[ $# -ne 1 ]];then
	echo "USAGE: <source-dir>"
	exit 1
fi

SRC=$1

declare -a in_prefixes=("rateunc-CAR Interface" "rateunc-CAR Intraslab" "rateunc-Crustal" "rateunc-MUE Interface" "rateunc-MUE Intraslab")
declare -a out_prefixes=("CAR_INTERFACE" "CAR_INTRASLAB" "CRUSTAL" "MUE_INTERFACE" "MUE_INTRASLAB")

arraylength=${#in_prefixes[@]}

for (( i=0; i<${arraylength}; i++ ));
do
	in_prefix=${in_prefixes[$i]}
	out_file="${out_prefixes[$i]}.csv"
	count=`find $SRC -type f 2> /dev/null | grep -v __MAC | grep -c "$in_prefix"`
	if [[ $count -ne 1 ]];then
		echo "Found $count matches for prefix $in_prefix:"
		find $SRC -type f 2> /dev/null | grep -v __MA | grep "$in_prefix"
		exit 1
	fi
	in_file=`find $SRC -type f 2> /dev/null | grep -v __MA | grep "$in_prefix"`
#	echo "$in_file $out_file"
	cp -v "$in_file" $out_file
done
