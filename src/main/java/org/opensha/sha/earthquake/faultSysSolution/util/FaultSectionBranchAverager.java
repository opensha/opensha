package org.opensha.sha.earthquake.faultSysSolution.util;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.FaultUtils.AngleAverager;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

public class FaultSectionBranchAverager {
	
	private List<? extends FaultSection> refSects;
	private int numSects;
	private double[] avgSectAseis;
	private double[] avgSectCoupling;
	private boolean[] sectAnyCreeps;
	private double[] avgSectCreep;
	private double[] avgSectSlipRates;
	private double[] avgSectSlipRateStdDevs;
	private double[] avgSectOrigFractUncert;
	private List<AngleAverager> avgSectRakes;
	
	private double totWeight;
	
	public FaultSectionBranchAverager(List<? extends FaultSection> refSects) {
		this.refSects = refSects;
		this.numSects = refSects.size();
		this.totWeight = 0d;
		
		avgSectAseis = new double[numSects];
		avgSectCreep = null;
		avgSectOrigFractUncert = new double[numSects];
		for (int i=0; i<avgSectOrigFractUncert.length; i++)
			avgSectOrigFractUncert[i] = Double.NaN;
		for (FaultSection sect : refSects) {
			if (sect instanceof GeoJSONFaultSection) {
				GeoJSONFaultSection geoSect = (GeoJSONFaultSection)sect;
				double creepRate = geoSect.getProperties().getDouble(GeoJSONFaultSection.CREEP_RATE, Double.NaN);
				if (Double.isFinite(creepRate)) {
					// have creep data
					avgSectCreep = new double[numSects];
					sectAnyCreeps = new boolean[numSects];
					break;
				}
				
				double origFractUncert = geoSect.getProperties().getDouble(
						NSHM23_DeformationModels.ORIG_FRACT_STD_DEV_PROPERTY_NAME, Double.NaN);
				if (Double.isFinite(origFractUncert))
					// will average this one
					avgSectOrigFractUncert[sect.getSectionId()] = 0d;
			}
		}
		avgSectSlipRates = new double[numSects];
		avgSectSlipRateStdDevs = new double[numSects];
		avgSectCoupling = new double[numSects];
		avgSectRakes = new ArrayList<>();
		for (int s=0; s<numSects; s++)
			avgSectRakes.add(new AngleAverager());
	}
	
	public void addWeighted(List<? extends FaultSection> sects, double weight) {
		Preconditions.checkState(sects.size() == numSects);
		totWeight += weight;
		
		for (int s=0; s<sects.size(); s++) {
			FaultSection sect = sects.get(s);
			avgSectAseis[s] += sect.getAseismicSlipFactor()*weight;
			avgSectSlipRates[s] += sect.getOrigAveSlipRate()*weight;
			avgSectSlipRateStdDevs[s] += sect.getOrigSlipRateStdDev()*weight;
			avgSectCoupling[s] += sect.getCouplingCoeff()*weight;
			avgSectRakes.get(s).add(sect.getAveRake(), weight);
			if (sect instanceof GeoJSONFaultSection) {
				GeoJSONFaultSection geoSect = (GeoJSONFaultSection)sect;
				
				if (avgSectCreep != null) {
					double creepRate = geoSect.getProperties().getDouble(GeoJSONFaultSection.CREEP_RATE, Double.NaN);
					if (Double.isFinite(creepRate)) {
						sectAnyCreeps[s] = true;
						avgSectCreep[s] += Math.max(0d, creepRate)*weight;
					}
				}
				
				if (Double.isFinite(avgSectOrigFractUncert[s]))
					avgSectOrigFractUncert[s] += weight*geoSect.getProperties().getDouble(
							NSHM23_DeformationModels.ORIG_FRACT_STD_DEV_PROPERTY_NAME, Double.NaN);
			}
		}
	}
	
	public List<FaultSection> buildAverageSects() {
		List<FaultSection> subSects = new ArrayList<>();
		for (int s=0; s<numSects; s++) {
			FaultSection refSect = refSects.get(s);
			
			avgSectAseis[s] /= totWeight;
			avgSectCoupling[s] /= totWeight;
			avgSectSlipRates[s] /= totWeight;
			avgSectSlipRateStdDevs[s] /= totWeight;
			double avgRake = FaultUtils.getInRakeRange(avgSectRakes.get(s).getAverage());
			
			GeoJSONFaultSection avgSect = new GeoJSONFaultSection(new AvgFaultSection(refSect, avgSectAseis[s],
					avgSectCoupling[s], avgRake, avgSectSlipRates[s], avgSectSlipRateStdDevs[s]));
			if (avgSectCreep != null && sectAnyCreeps[s]) {
				avgSectCreep[s] /= totWeight;
				avgSect.setProperty(GeoJSONFaultSection.CREEP_RATE, avgSectCreep[s]);
			}
			if (Double.isFinite(avgSectOrigFractUncert[s])) {
				avgSectOrigFractUncert[s] /= totWeight;
				avgSect.setProperty(NSHM23_DeformationModels.ORIG_FRACT_STD_DEV_PROPERTY_NAME, avgSectOrigFractUncert[s]);
			}
			subSects.add(avgSect);
		}
		// clear everything so we can't accidentally try to build twice (which won't work)
		avgSectAseis = null;
		avgSectCoupling = null;
		avgSectSlipRates = null;
		avgSectSlipRateStdDevs = null;
		refSects = null;
		numSects = -1;
		return subSects;
	}
	
	private class AvgFaultSection implements FaultSection {
		
		private FaultSection refSect;
		private double avgAseis;
		private double avgCoupling;
		private double avgRake;
		private double avgSlip;
		private double avgSlipStdDev;

		public AvgFaultSection(FaultSection refSect, double avgAseis, double avgCoupling, double avgRake, double avgSlip, double avgSlipStdDev) {
			this.refSect = refSect;
			this.avgAseis = avgAseis;
			this.avgCoupling = avgCoupling;
			this.avgRake = avgRake;
			this.avgSlip = avgSlip;
			this.avgSlipStdDev = avgSlipStdDev;
		}

		@Override
		public String getName() {
			return refSect.getName();
		}

		@Override
		public Element toXMLMetadata(Element root) {
			throw new UnsupportedOperationException();
		}

		@Override
		public long getDateOfLastEvent() {
			return refSect.getDateOfLastEvent();
		}

		@Override
		public void setDateOfLastEvent(long dateOfLastEventMillis) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setSlipInLastEvent(double slipInLastEvent) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getSlipInLastEvent() {
			return refSect.getSlipInLastEvent();
		}

		@Override
		public double getAseismicSlipFactor() {
			if ((float)avgCoupling == 0f)
				return 0d;
			return avgAseis;
		}

		@Override
		public void setAseismicSlipFactor(double aseismicSlipFactor) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getCouplingCoeff() {
			if ((float)avgCoupling == 1f)
				return 1d;
			return avgCoupling;
		}

		@Override
		public void setCouplingCoeff(double couplingCoeff) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getAveDip() {
			return refSect.getAveDip();
		}

		@Override
		public double getOrigAveSlipRate() {
			return avgSlip;
		}

		@Override
		public void setAveSlipRate(double aveLongTermSlipRate) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getAveLowerDepth() {
			return refSect.getAveLowerDepth();
		}

		@Override
		public FaultTrace getLowerFaultTrace() {
			return refSect.getLowerFaultTrace();
		}

		@Override
		public double getAveRake() {
			return avgRake;
		}

		@Override
		public void setAveRake(double aveRake) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getOrigAveUpperDepth() {
			return refSect.getOrigAveUpperDepth();
		}

		@Override
		public float getDipDirection() {
			return refSect.getDipDirection();
		}

		@Override
		public FaultTrace getFaultTrace() {
			return refSect.getFaultTrace();
		}

		@Override
		public int getSectionId() {
			return refSect.getSectionId();
		}

		@Override
		public void setSectionId(int sectID) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setSectionName(String sectName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getParentSectionId() {
			return refSect.getParentSectionId();
		}

		@Override
		public void setParentSectionId(int parentSectionId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getParentSectionName() {
			return refSect.getParentSectionName();
		}

		@Override
		public void setParentSectionName(String parentSectionName) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<? extends FaultSection> getSubSectionsList(double maxSubSectionLen, int startId,
				int minSubSections) {
			throw new UnsupportedOperationException();
		}

		@Override
		public double getOrigSlipRateStdDev() {
			return avgSlipStdDev;
		}

		@Override
		public void setSlipRateStdDev(double slipRateStdDev) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isConnector() {
			return refSect.isConnector();
		}

		@Override
		public Region getZonePolygon() {
			return refSect.getZonePolygon();
		}

		@Override
		public void setZonePolygon(Region zonePolygon) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Element toXMLMetadata(Element root, String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RuptureSurface getFaultSurface(double gridSpacing) {
			throw new UnsupportedOperationException();
		}

		@Override
		public RuptureSurface getFaultSurface(double gridSpacing, boolean preserveGridSpacingExactly,
				boolean aseisReducesArea) {
			throw new UnsupportedOperationException();
		}

		@Override
		public FaultSection clone() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isProxyFault() {
			return refSect.isProxyFault();
		}

		@Override
		public int getSubSectionIndex() {
			return refSect.getSubSectionIndex();
		}

		@Override
		public int getSubSectionIndexAlong() {
			return refSect.getSubSectionIndexAlong();
		}

		@Override
		public int getSubSectionIndexDownDip() {
			return refSect.getSubSectionIndexDownDip();
		}
		
	}

}
