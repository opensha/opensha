# CyberShake Study 18.8 Z files

Multiple velocity models, stitched for use in the CyberShake Study 18.8 (Northern California). Extracted by Mei-Hui Su, Z2.5 on 7/9/18 and Z1.0 on ??/??/??. Second crossing, model order is:

1. CCA-06 with GTL (cca)
2. USGS Bay Area Model (cencal)
3. CVM-S4.26.M01 with GTL (cvm5)

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
minLat = 30
minLon = -130
nx = 3400
ny = 2400
gridSpacing = 0.005
```
