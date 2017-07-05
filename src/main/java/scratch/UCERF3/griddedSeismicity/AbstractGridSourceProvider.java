package scratch.UCERF3.griddedSeismicity;

import java.awt.geom.Point2D;
import java.util.Map;

import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.geo.Location;
import org.opensha.nshmp2.erf.source.PointSource13b;
import org.opensha.nshmp2.util.FocalMech;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.griddedSeis.Point2Vert_FaultPoisSource;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import scratch.UCERF3.utils.GardnerKnopoffAftershockFilter;

/**
 * Add comments here
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public abstract class AbstractGridSourceProvider implements GridSourceProvider {

	private final WC1994_MagLengthRelationship magLenRel = new WC1994_MagLengthRelationship();
	private double ptSrcCutoff = 6.0;
	
	public static double SOURCE_MIN_MAG_CUTOFF = 5.05;

	protected AbstractGridSourceProvider() {
	}
	
	@Override
	public int size() {
		return getGriddedRegion().getNodeCount();
	}

	private static final double[] DEPTHS = new double[] {5.0, 1.0};
	
	@Override
	public ProbEqkSource getSource(int idx, double duration,
			boolean filterAftershocks, BackgroundRupType bgRupType) {
		Location loc = getGriddedRegion().locationForIndex(idx);
		IncrementalMagFreqDist mfd = getNodeMFD(idx, SOURCE_MIN_MAG_CUTOFF);
		if (filterAftershocks) scaleMFD(mfd);
		
		double fracStrikeSlip = getFracStrikeSlip(idx);
		double fracNormal = getFracNormal(idx);
		double fracReverse = getFracReverse(idx);

		switch (bgRupType) {
		case CROSSHAIR:
			return new Point2Vert_FaultPoisSource(loc, mfd, magLenRel, duration,
					ptSrcCutoff, fracStrikeSlip, fracNormal,
					fracReverse, true);
		case FINITE:
			return new Point2Vert_FaultPoisSource(loc, mfd, magLenRel, duration,
					ptSrcCutoff, fracStrikeSlip, fracNormal,
					fracReverse, false);
		case POINT:
			Map<FocalMech, Double> mechMap = Maps.newHashMap();
			mechMap.put(FocalMech.STRIKE_SLIP, fracStrikeSlip);
			mechMap.put(FocalMech.REVERSE, fracReverse);
			mechMap.put(FocalMech.NORMAL, fracNormal);
			return new PointSource13b(loc, mfd, duration, DEPTHS, mechMap);

		default:
			throw new IllegalStateException("Unknown Background Rup Type: "+bgRupType);
		}
		
	}
	
	
	
	
	public ProbEqkSource getSourceSubseismoOnly(int idx, double duration,
			boolean filterAftershocks, BackgroundRupType bgRupType) {
		Location loc = getGriddedRegion().locationForIndex(idx);
		IncrementalMagFreqDist origMFD = getNodeSubSeisMFD(idx);
		if(origMFD == null)
			return null;
		IncrementalMagFreqDist mfd = trimMFD(origMFD, SOURCE_MIN_MAG_CUTOFF);
		if (filterAftershocks) scaleMFD(mfd);
		
		double fracStrikeSlip = getFracStrikeSlip(idx);
		double fracNormal = getFracNormal(idx);
		double fracReverse = getFracReverse(idx);

		switch (bgRupType) {
		case CROSSHAIR:
			return new Point2Vert_FaultPoisSource(loc, mfd, magLenRel, duration,
					ptSrcCutoff, fracStrikeSlip, fracNormal,
					fracReverse, true);
		case FINITE:
			return new Point2Vert_FaultPoisSource(loc, mfd, magLenRel, duration,
					ptSrcCutoff, fracStrikeSlip, fracNormal,
					fracReverse, false);
		case POINT:
			Map<FocalMech, Double> mechMap = Maps.newHashMap();
			mechMap.put(FocalMech.STRIKE_SLIP, fracStrikeSlip);
			mechMap.put(FocalMech.REVERSE, fracReverse);
			mechMap.put(FocalMech.NORMAL, fracNormal);
			return new PointSource13b(loc, mfd, duration, DEPTHS, mechMap);

		default:
			throw new IllegalStateException("Unknown Background Rup Type: "+bgRupType);
		}
		
	}

	public ProbEqkSource getSourceTrulyOffOnly(int idx, double duration,
			boolean filterAftershocks, BackgroundRupType bgRupType) {
		Location loc = getGriddedRegion().locationForIndex(idx);
		IncrementalMagFreqDist origMFD = getNodeUnassociatedMFD(idx);
		if(origMFD == null)
			return null;
		IncrementalMagFreqDist mfd = trimMFD(origMFD, SOURCE_MIN_MAG_CUTOFF);
		if (filterAftershocks) scaleMFD(mfd);
		
		double fracStrikeSlip = getFracStrikeSlip(idx);
		double fracNormal = getFracNormal(idx);
		double fracReverse = getFracReverse(idx);

		switch (bgRupType) {
		case CROSSHAIR:
			return new Point2Vert_FaultPoisSource(loc, mfd, magLenRel, duration,
					ptSrcCutoff, fracStrikeSlip, fracNormal,
					fracReverse, true);
		case FINITE:
			return new Point2Vert_FaultPoisSource(loc, mfd, magLenRel, duration,
					ptSrcCutoff, fracStrikeSlip, fracNormal,
					fracReverse, false);
		case POINT:
			Map<FocalMech, Double> mechMap = Maps.newHashMap();
			mechMap.put(FocalMech.STRIKE_SLIP, fracStrikeSlip);
			mechMap.put(FocalMech.REVERSE, fracReverse);
			mechMap.put(FocalMech.NORMAL, fracNormal);
			return new PointSource13b(loc, mfd, duration, DEPTHS, mechMap);

		default:
			throw new IllegalStateException("Unknown Background Rup Type: "+bgRupType);
		}
		
	}

	
//	@Override
//	public void setAsPointSources(boolean usePoints) {
//		ptSrcCutoff = (usePoints) ? 10.0 : 6.0;
//	}


	@Override
	public IncrementalMagFreqDist getNodeMFD(int idx, double minMag) {
		return trimMFD(getNodeMFD(idx), minMag);
		
		// NOTE trimMFD clones the MFD returned by getNodeMFD so its safe for
		// subsequent modification; if this changes, then we need to review if
		// MFD is safe from alteration.
	}
	
	@Override
	public IncrementalMagFreqDist getNodeMFD(int idx) {
		
		IncrementalMagFreqDist nodeIndMFD = getNodeUnassociatedMFD(idx);
		IncrementalMagFreqDist nodeSubMFD = getNodeSubSeisMFD(idx);
		if (nodeIndMFD == null) return nodeSubMFD;
		if (nodeSubMFD == null) return nodeIndMFD;
		
		SummedMagFreqDist sumMFD = initSummedMFD(nodeIndMFD);
		sumMFD.addIncrementalMagFreqDist(nodeSubMFD);
		sumMFD.addIncrementalMagFreqDist(nodeIndMFD);
		return sumMFD;
	}
	
	private static SummedMagFreqDist initSummedMFD(IncrementalMagFreqDist model) {
		return new SummedMagFreqDist(model.getMinX(), model.getMaxX(),
			model.size());
	}


	/*
	 * Applies gardner Knopoff aftershock filter scaling to MFD in place.
	 */
	private static void scaleMFD(IncrementalMagFreqDist mfd) {
		double scale;
		for (int i=0; i<mfd.size(); i++) {
			scale = GardnerKnopoffAftershockFilter.scaleForMagnitude(mfd.getX(i));
			mfd.set(i, mfd.getY(i) * scale);
		}
	}

	/*
	 * Utility to trim the supplied MFD to the supplied min mag and the maximum
	 * non-zero mag. This method makes the assumtions that the min mag of the 
	 * supplied mfd is lower then the mMin, and that mag bins are centered on
	 * 0.05.
	 */
	private static IncrementalMagFreqDist trimMFD(IncrementalMagFreqDist mfdIn, double mMin) {
		if (mfdIn == null)
			return new IncrementalMagFreqDist(mMin,mMin,1);
		// in GR nofix branches there are mfds with all zero rates
		double mMax = mfdIn.getMaxMagWithNonZeroRate();
		if (Double.isNaN(mMax)) {
			IncrementalMagFreqDist mfdOut = new IncrementalMagFreqDist(mMin,mMin,1);
			return mfdOut;
		}
		double delta = mfdIn.getDelta();
		// if delta get's slightly off, the inner part of this cast can be something like 0.99999999, which
		// will mess up the num calculation. pad by 0.1 to be safe before casting
		int num = (int) ((mMax - mMin) / delta + 0.1) + 1;
//		IncrementalMagFreqDist mfdOut = new IncrementalMagFreqDist(mMin, mMax, num);
		IncrementalMagFreqDist mfdOut = new IncrementalMagFreqDist(mMin, num, delta);
		for (int i=0; i<mfdOut.size(); i++) {
			double mag = mfdOut.getX(i);
			double rate = mfdIn.getY(mag);
			mfdOut.set(mag, rate);
		}
//		if ((float)mfdOut.getMaxX() != (float)mMax) {
//			System.out.println("MFD IN");
//			System.out.println(mfdIn);
//			System.out.println("MFD OUT");
//			System.out.println(mfdOut);
//		}
		Preconditions.checkState((float)mfdOut.getMaxX() == (float)mMax,
				"Bad trim! mMin=%s, mMax=%s, delta=%s, num=%s, outputMMax=%s",
				mMin, mMax, delta, num, mfdOut.getMaxX());
		return mfdOut;
//		try {
//			double mMax = mfdIn.getMaxMagWithNonZeroRate();
//			double delta = mfdIn.getDelta();
//			int num = (int) ((mMax - mMin) / delta) + 1;
////			IncrementalMagFreqDist mfdOut = new IncrementalMagFreqDist(mMin, mMax, num);
//			IncrementalMagFreqDist mfdOut = new IncrementalMagFreqDist(mMin, num, delta);
//			for (int i=0; i<mfdOut.size(); i++) {
//				double mag = mfdOut.getX(i);
//				double rate = mfdIn.getY(mag);
//				mfdOut.set(mag, rate);
//			}
//			return mfdOut;
//		} catch (Exception e) {
////			e.printStackTrace();
////			System.out.println("empty MFD");
//			IncrementalMagFreqDist mfdOut = new IncrementalMagFreqDist(mMin,mMin,1);
////			mfdOut.scaleToCumRate(mMin, 0.0);
//			return mfdOut;
//		}
	}



}
