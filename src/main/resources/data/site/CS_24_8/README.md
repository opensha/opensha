# CyberShake Study 14.8 Z files

CyberShake Study 24.8 Z files, based off of the USGS Bay Area model with some custom taper parameters. Extracted by Mei-Hui Su on 10/30/2024. Second crossing, generated with this command:

```
./basin_query_mpi_complete -b RESULT/sfcvm_z2.5.first,RESULT/sfcvm_z2.5.firstOrSecond,RESULT/sfcvm_z2.5.last,RESULT/sfcvm_z2.5.secondOnly,RESULT/sfcvm_z2.5.threeLast -o sfcvm_z2.5.result,sfcvm_z2.5.meta.json -f ./ucvm.conf -m sfcvm,elygtl:taper -L 400,1000,1000 -i 10 -v 2500 -l 36.9,-123.4 -s 0.005 -x 421 -y 401
```

## File Format

The binary files are 4 byte float, little endian, fast XY, no rotation. Here are the formulas for lat/lon at a given x/y index:

```
lon = xIndex * gridSpacing + minLon
lat = yIndex * gridSpacing + minLat
```

and for calculating the file position for a given xIndex/yIndex:

```
filePos = 4 * (nx * yIndex + xIndex)
```

The first data point is bottom left (at minX, minY). 

## Parameters
```
minLat = 36.9
minLon = -123.4
nx = 421
ny = 401
gridSpacing = 0.005
```
