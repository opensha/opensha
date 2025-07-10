package org.opensha.sha.faultSurface.utils.ptSrcCorr;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RectangularSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.PointSurfaceBuilder;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.DistanceDistributionCorrection.FractileBin;
import org.opensha.sha.faultSurface.utils.ptSrcCorr.DistanceDistributionCorrection.PrecomputedComparableDistances;

public class DistanceDistributionCorrectionTest {
	
	private static final double[] zTops = {0d, 1d, 5d};
	private static final double[] zBots = {2d, 7d, 15d};
	private static final double[] dippingDips = {10d, 30d, 50d, 70d};
	private static final double[] lengths = {1d, 10d, 50d};
	private static final double[] rEpis = {0d, 5d, 15d, 25d, 50d, 100d};
	private static final double[] fractDASs = {0d, 0.1, 0.25, 0.5, 0.75, 0.9, 1d};
	private static final double[] fractDDs = fractDASs;
	
//	private static final double[] zTops = {0d};
//	private static final double[] zBots = {15d};
//	private static final double[] dippingDips = {10d};
//	private static final double[] lengths = {50d};
//	private static final double[] rEpis = {0d, 5d, 15d, 25d, 50d, 100d};
//	private static final double[] fractDASs = {0d};
//	private static final double[] fractDDs = {0.5};
	
	private static final double[] alphas = DistanceDistributionCorrection.buildSpacedSamples(0d, 360d, 361, true);
	
	private static double[][] rEpiSiteXs;
	private static double[][] rEpiSiteYs;
	
	private static final Location origin = new Location(0d, 0d);
	private static Location[][] rEpiSiteLocs;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		rEpiSiteXs = new double[rEpis.length][alphas.length];
		rEpiSiteYs = new double[rEpis.length][alphas.length];
		rEpiSiteLocs = new Location[rEpis.length][alphas.length];
		
		for (int a=0; a<alphas.length; a++) {
			double alphaRad = Math.toRadians(alphas[a]);
			double sinAlpha = Math.sin(alphaRad);
			double cosAlpha = Math.cos(alphaRad);
			for (int d=0; d<rEpis.length; d++) {
				rEpiSiteXs[d][a] = rEpis[d]*sinAlpha;
				rEpiSiteYs[d][a] = rEpis[d]*cosAlpha;
				rEpiSiteLocs[d][a] = LocationUtils.location(origin, alphaRad, rEpis[d]);
				for (int n=0; n<5; n++) {
					double corrDist = LocationUtils.horzDistanceFast(origin, rEpiSiteLocs[d][a]);
					rEpiSiteLocs[d][a] = LocationUtils.location(rEpiSiteLocs[d][a], alphaRad, rEpis[d] - corrDist);
				}
			}
		}
	}

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testVerticalCentered() {
		for (double zTop : zTops) {
			for (double zBot : zBots) {
				if (zBot <= zTop)
					continue;
				for (double length : lengths) {
					doDistDest(zTop, zBot, length, 90d, 0.5d, 0.5d);
				}
			}
		}
	}

	@Test
	public void testVerticalAlong() {
		for (double zTop : zTops) {
			for (double zBot : zBots) {
				if (zBot <= zTop)
					continue;
				for (double length : lengths) {
					for (double fractDAS : fractDASs) {
						doDistDest(zTop, zBot, length, 90d, fractDAS, 0.5d);
					}
				}
			}
		}
	}

	@Test
	public void testDippingCentered() {
		for (double zTop : zTops) {
			for (double zBot : zBots) {
				if (zBot <= zTop)
					continue;
				for (double length : lengths) {
					for (double dip : dippingDips)
						doDistDest(zTop, zBot, length, dip, 0.5d, 0.5d);
				}
			}
		}
	}

	@Test
	public void testDippingAlong() {
		for (double zTop : zTops) {
			for (double zBot : zBots) {
				if (zBot <= zTop)
					continue;
				for (double length : lengths) {
					for (double dip : dippingDips) {
						for (double fractDAS : fractDASs) {
							for (double fractDD : fractDDs) {
								doDistDest(zTop, zBot, length, dip, fractDAS, fractDD);
							}
						}
					}
				}
			}
		}
	}
	
	private static void doDistDest(double zTop, double zBot, double length, double dip, double fractDAS, double fractDD) {
		PointSurfaceBuilder builder = new PointSurfaceBuilder(origin);
		builder.upperDepth(zTop).lowerDepth(zBot).length(length).dip(dip)
				.fractionalDAS(fractDAS).fractionalHypocentralDepth(fractDD);
		
		PointSurface ptSurf = builder.buildPointSurface();
		
		RectangularSurface rectSurf = builder.buildRectSurface(0d);
		
		String surfStr = "zTop=" + zTop + ", zBot=" + zBot + ", length=" + length + ", dip=" + dip + ","
				+ "\n\tfractDAS="	+ fractDAS + ", fractDD=" + fractDD+", calcHorzWidth="+ptSurf.getAveHorizontalWidth();
		
		DistanceDistributionCorrection.RuptureKey rupKey = new DistanceDistributionCorrection.RuptureKey(ptSurf, false);
		
		for (int d=0; d<rEpis.length; d++) {
			PrecomputedComparableDistances[] dists = new DistanceDistributionCorrection.DistCalcSupplier(
					rupKey, fractDAS, fractDD, rEpiSiteXs[d], rEpiSiteYs[d]).get();
			assertEquals(dists.length, alphas.length);
			
			double tol = Math.max(length*1e-4, rEpis[d]*1e-2);
			for (int a=0; a<alphas.length; a++) {
				SurfaceDistances rectDists = rectSurf.calcDistances(rEpiSiteLocs[d][a]);
				PrecomputedComparableDistances ptDists = dists[a];
//				PrecomputedComparableDistances ptDists = new DistanceDistributionCorrection.DistCalcSupplier(
//						rupKey, fractDAS, fractDD, new double[] {rEpiSiteXs[d][a]}, new double[] {rEpiSiteYs[d][a]}).get()[0];
				
				double calcAzimuth = LocationUtils.azimuth(origin, rEpiSiteLocs[d][a]);
				
				String testStr = "suf=["+surfStr+"]"
						+ "\nrEpi="+rEpis[d]+"; alpha="+alphas[a]+"; site=("+rEpiSiteXs[d][a]+", "+rEpiSiteYs[d][a]+")"
						+ "\ntestLoc (rel 0,0): "+rEpiSiteLocs[d][a]+"; calcAlpha="+calcAzimuth;
				
				assertEquals("Rjb fail for:\n"+testStr+"\n", rectDists.getDistanceJB(), ptDists.getDistanceJB(), tol);
				assertEquals("Rrup fail for:\n"+testStr+"\n", rectDists.getDistanceRup(), ptDists.getDistanceRup(), tol);
				// TODO, rect is actually wrong here
//				assertEquals("Rseis fail for:\n"+testStr+"\n", rectDists.getDistanceSeis(), ptDists.getDistanceSeis(), tol);
				double rectRx = rectDists.getDistanceX();
				double ptRx = ptDists.getDistanceX();
				if (dip == 90d) {
					// vertical, ignore the sign because we might always return footwall for efficiency
					rectRx = Math.abs(rectRx);
					ptRx = Math.abs(ptRx);
				}
				assertEquals("Rx fail for:\n"+testStr+"\n", rectRx, ptRx, tol);
			}
		}
	}
	
	@Test
	public void testFractileAveraging() {
		int nFract = 5;
		WeightedList<FractileBin> fractiles = DistanceDistributionCorrection.getEvenlySpacedFractiles(nFract);
		
		int[] numsPerBin = {1, 2, 3, 5, 10, 11};
		
		double tol = 1e-4;
		
		for (int numPerBin : numsPerBin) {
			List<PrecomputedComparableDistances> dists = new ArrayList<>(numPerBin);
			PrecomputedComparableDistances[] expected = new PrecomputedComparableDistances[nFract];
			int index = 0;
			for (int f=0; f<nFract; f++) {
				double sumJB = 0d;
				double sumRup = 0d;
				double sumSeis = 0d;
				double sumX = 0d;
				for (int i=0; i<numPerBin; i++) {
					double jb = index;
					double rup = index + 1000;
					double seis = index + 2000;
					double x = -jb;
					dists.add(new PrecomputedComparableDistances(rup, jb, seis, x));
					sumJB += jb;
					sumRup += rup;
					sumSeis += seis;
					sumX += x;
				}
				expected[f] = new PrecomputedComparableDistances(sumRup/numPerBin, sumJB/numPerBin, sumSeis/numPerBin, sumX/numPerBin);
			}

			PrecomputedComparableDistances[] binAvg = DistanceDistributionCorrection.calcBinAverageDistances(dists, fractiles, 0d);
			for (int f=0; f<nFract; f++) {
				PrecomputedComparableDistances test = binAvg[f];
				PrecomputedComparableDistances ref = expected[f];
				String testStr = "nFract="+nFract+", f="+f+", numPerBin="+numsPerBin;
				assertEquals("rJB mistmatch: "+testStr+"\n", ref.getDistanceJB(), test.getDistanceJB(), tol);
				assertEquals("rRup mistmatch: "+testStr+"\n", ref.getDistanceRup(), test.getDistanceRup(), tol);
				assertEquals("rSeis mistmatch: "+testStr+"\n", ref.getDistanceSeis(), test.getDistanceSeis(), tol);
				assertEquals("rX mistmatch: "+testStr+"\n", ref.getDistanceX(), test.getDistanceX(), tol);
			}
		}
	}

	/** builds a PrecomputedComparableDistances with every distance = v */
	private static PrecomputedComparableDistances pcd(double v) {
		return new PrecomputedComparableDistances(v, v, v, v);
	}

	/** ascending list 1 … n (all fields set to that integer) */
	private static List<PrecomputedComparableDistances> ascending(int n) {
		List<PrecomputedComparableDistances> list = new ArrayList<>(n);
		for (int i = 1; i <= n; i++) list.add(pcd(i));
		return list;
	}

	/* ------------------------------------------------------------------ *
	 *  tests                                                             *
	 * ------------------------------------------------------------------ */

	/** empty input → null */
	@Test
	public void testFractileAveragingEmptyInputReturnsNull() {
		WeightedList<FractileBin> fr =
				DistanceDistributionCorrection.getEvenlySpacedFractiles(3);
		assertNull(DistanceDistributionCorrection.calcBinAverageDistances(new ArrayList<>(), fr, 1.0));
	}

	/** nDists == 1 → replicate same value to every bin */
	@Test
	public void testFractileAveragingSingleObservationReplicated() {
		WeightedList<FractileBin> fr =
				DistanceDistributionCorrection.getEvenlySpacedFractiles(5);
		List<PrecomputedComparableDistances> data = ascending(1);

		PrecomputedComparableDistances[] out = DistanceDistributionCorrection.calcBinAverageDistances(data, fr, 1.0);

		assertEquals(5, out.length);
		for (PrecomputedComparableDistances d : out) {
			assertEquals(1.0, d.getDistanceJB(), 1e-12);
			assertEquals(1.0, d.getDistanceRup(), 1e-12);
		}
	}

	/** nDists &lt; nBins → interpolation branch */
	@Test
	public void testFractileAveragingInterpolationBranch() {
		final int nBins = 5, nVals = 4;
		WeightedList<FractileBin> fr =
				DistanceDistributionCorrection.getEvenlySpacedFractiles(nBins);
		List<PrecomputedComparableDistances> data = ascending(nVals);

		/* ground-truth with the same partial-area logic */
		double[] expected = new double[nBins];
		double[] wSum     = new double[nBins];

		for (int k = 0; k < nVals; k++) {
			double segA = k, segB = k + 1;
			double value = k + 1;                 // pcd(k+1)

			for (int b = 0; b < nBins; b++) {
				FractileBin bin = fr.getValue(b);
				double binA = bin.minimum * nVals;
				double binB = bin.maximum * nVals;

				double overlap = Math.min(segB, binB) - Math.max(segA, binA);
				if (overlap <= 0) continue;

				wSum[b]     += overlap;
				expected[b] += overlap * value;
			}
		}
		for (int b = 0; b < nBins; b++) expected[b] /= wSum[b];

		/* choose a *large* threshold so smallestCount &lt; maxBinSizeForInterp */
		PrecomputedComparableDistances[] out = DistanceDistributionCorrection.calcBinAverageDistances(data, fr, 10.0);

		assertEquals(nBins, out.length);
		for (int b = 0; b < nBins; b++) {
			assertEquals(expected[b], out[b].getDistanceJB(), 1e-10);
		}
	}

	/** nDists ≫ nBins → direct-average branch */
	@Test
	public void testFractileAveragingDirectAverageBranch() {
		final int nBins = 5, nVals = 50;
		WeightedList<FractileBin> fr =
				DistanceDistributionCorrection.getEvenlySpacedFractiles(nBins);
		List<PrecomputedComparableDistances> data = ascending(nVals);

		double smallestCount = (double) nVals / nBins; // ≈10
		double maxBinSizeForInterp = smallestCount / 2.0; // < smallestCount, triggers else-branch

		/* integer slicing ground truth */
		double[] expected = new double[nBins];
		for (int b = 0; b < nBins; b++) {
			int start = (int) (fr.getValue(b).minimum * nVals);
			int end   = (int) Math.ceil(fr.getValue(b).maximum * nVals);
			double sum = 0;
			for (int k = start; k < end; k++) sum += (k + 1);
			expected[b] = sum / (end - start);
		}

		PrecomputedComparableDistances[] out =
				DistanceDistributionCorrection.calcBinAverageDistances(data, fr, maxBinSizeForInterp);

		assertEquals(nBins, out.length);
		for (int b = 0; b < nBins; b++) {
			assertEquals(expected[b], out[b].getDistanceJB(), 1e-12);
		}
	}

}
