# USGS SF Bay Area Velocity Model Release 21.1 Z files

Extracted by Mei-Hui Su on 3/22/24 from UCVM (model name 'sfcvm'), representing version 21.1 of the USGS SF Bay Area Velocity Model from https://www.sciencebase.gov/catalog/item/61817394d34e9f2789e3c36c.

According to Scott Callaghan, it is modified "to include corrections to the gabbro regions."

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
minLat = 36.9000
minLon = -123.4000
nx = 421
ny = 401
gridSpacing = 0.0050
```
