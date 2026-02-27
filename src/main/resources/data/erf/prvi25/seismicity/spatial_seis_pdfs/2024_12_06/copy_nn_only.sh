#!/bin/bash

set -o errexit

if [[ $# -ne 1 ]];then
	echo "USAGE: <source-dir>"
	exit 1
fi

SRC=$1

echo "REGULAR"
declare -a prefixes=("apdf_pmmx_car_interface" "apdf_pmmx_car_intraslab" "apdf_pmmx_crustal" "apdf_pmmx_mue_interface" "apdf_pmmx_mue_intraslab")
declare -a dirnames=("CAR_INTERFACE" "CAR_INTRASLAB" "CRUSTAL" "MUE_INTERFACE" "MUE_INTRASLAB")

UNIFORM_INTERFACE=0

arraylength=${#prefixes[@]}

for (( i=0; i<${arraylength}; i++ ));
do
	prefix=${prefixes[$i]}
	dirname=${dirnames[$i]}
	echo "$prefix -> $dirname"
	if [[ ! -e $dirname ]];then
		mkdir $dirname
	fi

#	cp -v $SRC/$prefix*_gk*fixed*.csv $dirname/GK_FIXED.csv
#	cp -v $SRC/$prefix*_gk*ad*.csv $dirname/GK_ADAPTIVE.csv
	cp -v $SRC/$prefix*_nn*fixed*.csv $dirname/NN_FIXED.csv
	cp -v $SRC/$prefix*_nn*ad*.csv $dirname/NN_ADAPTIVE.csv
#	cp -v $SRC/$prefix*_r85*fixed*.csv $dirname/REAS_FIXED.csv
#	cp -v $SRC/$prefix*_r85*ad*.csv $dirname/REAS_ADAPTIVE.csv
done
