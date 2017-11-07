= CVM-S4.26-M01 Z files =

Extracted by Scott Callaghan on 7/27/17, now using the second crossing

== File Format ==

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

== Parameters ==
```
minLat = 31;
minLon = -121;
nx = 1701;
ny = 1101;
gridSpacing = 0.005; 
```
