package scratch.UCERF3.erf.ETAS.launcher;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.comcat.EdgeRuptureSurface;
import org.opensha.commons.data.comcat.ShakeMapFiniteFaultAccessor;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Params.ETAS_ParameterList;

public abstract class TriggerRupture {
	
	final Long customOccurrenceTime;
	private String comcatEventID;
	private Double etas_log10_k;
	private Double etas_p;
	private Double etas_c;

	protected TriggerRupture(Long customOccurrenceTime) {
		this.customOccurrenceTime = customOccurrenceTime;
	}
	
	public long getOccurrenceTime(long simulationStartTime) {
		if (customOccurrenceTime != null && customOccurrenceTime > Long.MIN_VALUE) {
			Preconditions.checkState(customOccurrenceTime <= simulationStartTime,
					"Trigger rupture custom occurrence time (%s) cannot be after simulation start time (%s)",
					customOccurrenceTime, simulationStartTime);
			return customOccurrenceTime;
		}
		return simulationStartTime;
	}
	
	public String getComcatEventID() {
		return comcatEventID;
	}

	public void setComcatEventID(String comcatEventID) {
		this.comcatEventID = comcatEventID;
	}
	
	public void setETAS_Params(Double log10_k, Double p, Double c) {
		this.etas_log10_k = log10_k;
		this.etas_p = p;
		this.etas_c = c;
	}
	
	public Double getETAS_log10_k() {
		return etas_log10_k;
	}
	
	public Double getETAS_p() {
		return etas_p;
	}
	
	public Double getETAS_c() {
		return etas_c;
	}

	public ETAS_EqkRupture buildRupture(FaultSystemRupSet rupSet, long simulationStartTime, ETAS_ParameterList etasParams) {
		ETAS_EqkRupture rupture = new ETAS_EqkRupture();
		long ot = getOccurrenceTime(simulationStartTime);
		rupture.setOriginTime(ot);
		if (comcatEventID != null)
			rupture.setEventId(comcatEventID);
		Double k = null;
		if (etas_log10_k != null) {
			// convert k to UCERF3-ETAS units
			k = Math.pow(10, etas_log10_k);
		}
		rupture.setCustomETAS_Params(k, etas_p, etas_c);
		populateRupture(rupSet, rupture);
		return rupture;
	}
	
	protected abstract void populateRupture(FaultSystemRupSet rupSet, ETAS_EqkRupture rupture);
	
	/**
	 * @param rupSet
	 * @return magnitude of this rupture, or null if dependent on rupture set and rupSet not supplied
	 */
	public abstract Double getMag(FaultSystemRupSet rupSet);
	
	public abstract int[] getSectionsRuptured(FaultSystemRupSet rupSet);
	
	public static class FSS extends TriggerRupture {
		
		final int fssIndex;
		final Double overrideMag;
		
		public FSS(int fssIndex) {
			this(fssIndex, null, null);
		}

		public FSS(int fssIndex, Long customOccurrenceTime, Double overrideMag) {
			super(customOccurrenceTime);
			this.fssIndex = fssIndex;
			this.overrideMag = overrideMag;
		}

		@Override
		public void populateRupture(FaultSystemRupSet rupSet, ETAS_EqkRupture rupture) {
			double origMag = rupSet.getMagForRup(fssIndex);
			
			System.out.println("Building FSS rupture with index="+fssIndex+", OT="+rupture.getOriginTime()+", original M="+(float)origMag);
			
			rupture.setAveRake(rupSet.getAveRakeForRup(fssIndex));
			rupture.setMag(origMag);
			rupture.setRuptureSurface(rupSet.getSurfaceForRupture(fssIndex, 1d));
			rupture.setFSSIndex(fssIndex);
			
			if (overrideMag != null && overrideMag > 0) {
				System.out.println("\tOverriding magnitude with specified mag: "+overrideMag);
				rupture.setMag(overrideMag);
			}
		}

		@Override
		public int[] getSectionsRuptured(FaultSystemRupSet rupSet) {
			return Ints.toArray(rupSet.getSectionsIndicesForRup(fssIndex));
		}

		@Override
		public Double getMag(FaultSystemRupSet rupSet) {
			if (overrideMag != null && overrideMag > 0)
				return overrideMag;
			if (rupSet != null)
				rupSet.getMagForRup(fssIndex);
			return null;
		}
		
	}
	
	public static class SectionBased extends TriggerRupture {
		
		public final int[] subSects;
		final double mag;

		public SectionBased(int[] subSects, Long customOccurrenceTime, double mag) {
			super(customOccurrenceTime);
			Preconditions.checkState(subSects.length > 0, "Must supply at least 1 subsection index!");
			this.subSects = subSects;
			this.mag = mag;
		}

		@Override
		public void populateRupture(FaultSystemRupSet rupSet, ETAS_EqkRupture rupture) {
			System.out.println("Building rupture from "+subSects.length+" specified subsections with OT="+rupture.getOriginTime()+" and M="+(float)mag);
			
			rupture.setMag(mag);
			
			List<RuptureSurface> rupSurfs = new ArrayList<>();
			List<Double> rakes = new ArrayList<>();
			List<Double> areas = new ArrayList<>();
			double gridSpacing = 1;
			for (int sectIndex : subSects) {
				Preconditions.checkState(sectIndex >= 0 && sectIndex < rupSet.getNumSections(),
						"Bad subsection index. %s is outside of bounts [0, %s]", sectIndex, rupSet.getNumSections()-1);
				FaultSection fltData = rupSet.getFaultSectionData(sectIndex);
				rakes.add(fltData.getAveRake());
				areas.add(fltData.getReducedDownDipWidth()*fltData.getTraceLength());
				rupSurfs.add(fltData.getFaultSurface(gridSpacing, false, true));
			}
			if (rupSurfs.size() == 1) {
				rupture.setAveRake(rakes.get(0));
				rupture.setRuptureSurface(rupSurfs.get(0));
			} else {
				rupture.setAveRake(FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(areas, rakes)));
				rupture.setRuptureSurface(new CompoundSurface(rupSurfs));
			}
		}

		@Override
		public int[] getSectionsRuptured(FaultSystemRupSet rupSet) {
			return subSects;
		}

		@Override
		public Double getMag(FaultSystemRupSet rupSet) {
			return mag;
		}
		
	}
	
	public static class Point extends TriggerRupture {
		
		final Location hypocenter;
		final double mag;
		public final int[] sectsReset;

		public Point(Location hypocenter, Long customOccurrenceTime, double mag) {
			this(hypocenter, customOccurrenceTime, mag, null);
		}

		public Point(Location hypocenter, Long customOccurrenceTime, double mag, int[] sectsReset) {
			super(customOccurrenceTime);
			this.hypocenter = hypocenter;
			this.mag = mag;
			this.sectsReset = sectsReset;
		}

		@Override
		public void populateRupture(FaultSystemRupSet rupSet, ETAS_EqkRupture rupture) {
//			System.out.println("Building point rupture hypo="+hypocenter+", OT="+ot+", M="+(float)mag);
			
			rupture.setAveRake(0.0); // not used
			rupture.setMag(mag);
			rupture.setPointSurface(hypocenter);
			rupture.setHypocenterLocation(hypocenter);
		}

		@Override
		public int[] getSectionsRuptured(FaultSystemRupSet rupSet) {
			return sectsReset;
		}

		@Override
		public Double getMag(FaultSystemRupSet rupSet) {
			return mag;
		}
		
	}
	
	public static class SimpleFault extends TriggerRupture {
		
		public final SimpleFaultData[] sfds;
		public final Location hypo;
		public final double mag;
		public final int[] sectsReset;

		public SimpleFault(Long customOccurrenceTime, Location hypo, double mag, SimpleFaultData... sfds) {
			this(customOccurrenceTime, hypo, mag, null, sfds);
		}

		public SimpleFault(Long customOccurrenceTime, Location hypo, double mag, int[] sectsReset, SimpleFaultData... sfds) {
			super(customOccurrenceTime);
			Preconditions.checkState(sfds.length > 0, "Must supply at least 1 simple fault description!");
			this.sfds = sfds;
			this.hypo = hypo;
			this.mag = mag;
			this.sectsReset = sectsReset;
		}

		@Override
		public void populateRupture(FaultSystemRupSet rupSet, ETAS_EqkRupture rupture) {
			System.out.println("Building rupture from "+sfds.length+" simple faults OT="+rupture.getOriginTime()+" and M="+(float)mag);
			
			rupture.setMag(mag);
			rupture.setHypocenterLocation(hypo);
			
			List<RuptureSurface> rupSurfs = new ArrayList<>();
			double gridSpacing = 1;
			for (SimpleFaultData sfd : sfds)
				rupSurfs.add(new StirlingGriddedSurface(sfd, gridSpacing, gridSpacing));
			if (rupSurfs.size() == 1)
				rupture.setRuptureSurface(rupSurfs.get(0));
			else
				rupture.setRuptureSurface(new CompoundSurface(rupSurfs));
		}

		@Override
		public int[] getSectionsRuptured(FaultSystemRupSet rupSet) {
			return sectsReset;
		}

		@Override
		public Double getMag(FaultSystemRupSet rupSet) {
			return mag;
		}
		
	}
	
	public static class EdgeFault extends TriggerRupture {
		
		public final LocationList[] outlines;
		public final Location hypo;
		public final double mag;
		public final int[] sectsReset;

		public EdgeFault(Long customOccurrenceTime, Location hypo, double mag, LocationList... outlines) {
			this(customOccurrenceTime, hypo, mag, null, outlines);
		}

		public EdgeFault(Long customOccurrenceTime, Location hypo, double mag, int[] sectsReset, LocationList... outlines) {
			super(customOccurrenceTime);
			Preconditions.checkState(outlines.length > 0, "Must supply at least 1 rupture outline!");
			this.outlines = outlines;
			this.hypo = hypo;
			this.mag = mag;
			this.sectsReset = sectsReset;
		}

		@Override
		public void populateRupture(FaultSystemRupSet rupSet, ETAS_EqkRupture rupture) {
			System.out.println("Building rupture from "+outlines.length+" fault outlines, OT="+rupture.getOriginTime()+" and M="+(float)mag);
			
			rupture.setMag(mag);
			rupture.setHypocenterLocation(hypo);
			
			List<RuptureSurface> rupSurfs = new ArrayList<>();
			double gridSpacing = 1;
			for (LocationList outline : outlines)
				rupSurfs.add(EdgeRuptureSurface.build(outline, gridSpacing));
			if (rupSurfs.size() == 1)
				rupture.setRuptureSurface(rupSurfs.get(0));
			else
				rupture.setRuptureSurface(new CompoundSurface(rupSurfs));
		}

		@Override
		public int[] getSectionsRuptured(FaultSystemRupSet rupSet) {
			return sectsReset;
		}

		@Override
		public Double getMag(FaultSystemRupSet rupSet) {
			return mag;
		}
		
	}

}
