package org.opensha.sha.earthquake.rupForecastImpl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList.GriddedRuptureProperties;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm.DistanceCorrection2013;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_SingleRegionGridSourceProvider.NSHM23GridRupDimensions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded.NSHM23_SingleRegionGridSourceProvider.NSHM23_WUS_FiniteRuptureConverter;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.util.TectonicRegionType;

public class PointSourceNshmDistanceCorrectionTest {

	private static final Location SOURCE_LOCATION = new Location(0d, 0d, 50d);
	private static final Location SITE_LOCATION = new Location(0d, 0.1d);
	private static final double HORIZONTAL_DISTANCE = 10.5d;
	private static final double MAGNITUDE = 6.05d;

	private static SurfaceDistances correctedSlabDistances(double length, double magnitude) {
		PointSurface surface = new PointSurface(SOURCE_LOCATION, 90d, 50d, 58d, length);
		DistanceCorrection2013 correction = new DistanceCorrection2013();
		WeightedList<SurfaceDistances> distances = correction.getCorrectedDistances(SITE_LOCATION, surface,
				TectonicRegionType.SUBDUCTION_SLAB, magnitude, HORIZONTAL_DISTANCE);
		assertEquals(1, distances.size());
		return distances.getValue(0);
	}

	@Test
	public void testTruePointSlabIsUncorrected() {
		SurfaceDistances distances = correctedSlabDistances(0d, MAGNITUDE);
		assertEquals(HORIZONTAL_DISTANCE, distances.getDistanceJB(), 0d);
		assertEquals(Math.hypot(HORIZONTAL_DISTANCE, 50d), distances.getDistanceRup(), 0d);
		assertEquals(-HORIZONTAL_DISTANCE, distances.getDistanceX(), 0d);
	}

	@Test
	public void testFiniteSlabUsesGeomatrixCorrection() {
		SurfaceDistances distances = correctedSlabDistances(20d, MAGNITUDE);
		double expectedRJB = 8.25d;
		assertEquals(expectedRJB, distances.getDistanceJB(), 0d);
		assertEquals(Math.hypot(expectedRJB, 50d), distances.getDistanceRup(), 0d);
		assertEquals(-expectedRJB, distances.getDistanceX(), 0d);
	}

	@Test
	public void testGeomatrixCorrectionStartsAtMagnitudeSix() {
		SurfaceDistances distances = correctedSlabDistances(20d, 5.95d);
		assertEquals(HORIZONTAL_DISTANCE, distances.getDistanceJB(), 0d);
		assertEquals(Math.hypot(HORIZONTAL_DISTANCE, 50d), distances.getDistanceRup(), 0d);
	}

	@Test
	public void testNSHM23SlabProperties() {
		double magnitude = 7d;
		double upperDepth = 42d;
		double expectedLength = Math.pow(10d, (magnitude - 4.94d) / 1.39d);

		NSHM23GridRupDimensions dims = NSHM23_WUS_FiniteRuptureConverter.getSlabDimensions(
				magnitude, upperDepth);
		assertEquals(upperDepth, dims.upperDepth(), 0d);
		assertEquals(50d, dims.lowerDepth(), 0d);
		assertEquals(expectedLength, dims.length(), 0d);
		assertEquals(8d, dims.width(), 0d);
		assertEquals(90d, dims.dip(), 0d);

		GriddedRuptureProperties props = NSHM23_WUS_FiniteRuptureConverter.getSlabRuptureProperties(
				magnitude, upperDepth, null);
		assertEquals(TectonicRegionType.SUBDUCTION_SLAB, props.tectonicRegionType);
		assertEquals(0d, props.rake, 0d);
		assertEquals(90d, props.dip, 0d);
		assertEquals(upperDepth, props.upperDepth, 0d);
		assertEquals(50d, props.lowerDepth, 0d);
		assertEquals(expectedLength, props.length, 0d);
		assertEquals(46d, props.getHypocentralDepth(), 0d);
		assertEquals(0.5d * expectedLength, props.getHypocentralDAS(), 0d);
	}
}
