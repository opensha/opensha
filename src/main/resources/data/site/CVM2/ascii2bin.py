#!/usr/bin/env python

import struct

fp = open("basindepth_OpenSHA.txt", "r")

map = {}

def keyGen(lat, lon):
	return str(lat) + "_" + str(lon)

minLat = 999.0
minLon = 999.0
maxLat = -999.0
maxLon = -999.0

inc = 0.01

print "loading ascii"

num = 0

for line in fp:
	line = line.strip()
	split = line.split()
	
	lat = float(split[0])
	lon = float(split[1])
	dep = float(split[2])
	
	key = keyGen(lat, lon)
	map[key] = dep

	num += 1
	
	if lat < minLat:
		minLat = lat
	if lon < minLon:
		minLon = lon
	if lat > maxLat:
		maxLat = lat
	if lon > maxLon:
		maxLon = lon

fp.close()

fp = open("depth_2.5.bin", "wb")

nx = int((maxLon - minLon) / inc + 0.5) + 1
ny = int((maxLat - minLat) / inc + 0.5) + 1

print "minLat: " + str(minLat)
print "minLon: " + str(minLon)
print "nx: " + str(nx)
print "ny: " + str(ny)

print "writing bin"

bin = 0

for y in range(ny):
	for x in range(nx):
		lat = minLat + (y * inc)
		lon = minLon + (x * inc)
		key = keyGen(lat, lon)
		if map.has_key(key):
			bin += 1
			binData = struct.pack("f", map[key])
			fp.write(binData)
		else:
			print "nothing..."

fp.close()
print "done! " + str(num) + "/" + str(bin)
