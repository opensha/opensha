# USGS Bay Area Velocity Model Release 8.3.0 Z files

Extracted by Kevin Milner on 3/4/09 from version 8.3.0 of the USGS Bay Area Velocity Model (used in the SF06 simulation project).

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
minLat = 35;
minLon = -127;
nx = 851;
ny = 651;
gridSpacing = 0.01;
```
