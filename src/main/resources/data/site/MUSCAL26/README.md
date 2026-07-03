# SCEC Multi-Scale California (MUSCAL) 2026 Z files

SCEC Multi-Scale California (MUSCAL) 2026 Z files, based off of the MUSCAL model with the nearest neighbor background 1D. Extracted by Mei-Hui Su on 06/30/2026. Generated with this command:

```
./basin_query_mpi_complete TEST=basin_query_mpi_complete_cvmsi_z1.0
srun -N 20 -n 40  -t 2:00:00 -p development -o ${TEST}.srun.out ${BIN_DIR}/basin_query_mpi_complete
-b ${TEST}.first,${TEST}.firstOrSecond,${TEST}.last,${TEST}.secondOnly,${TEST}.threeLast
-o ${TEST}.result,${TEST}.meta.json -f ${CONF_DIR}/ucvm.conf -m muscal
-i 20 -v 1000 -l 32.2,-124.4 -s 0.005 -x 2061 -y  1981
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
minLat = 32.2
minLon = -124.4
nx (spacing in Lon) = 2061
ny (spacing in Lat) = 1981
gridSpacing = 0.005
and
maxLat = 42.1 and maxLon = -114.1
```
