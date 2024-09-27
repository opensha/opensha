package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.uncertainty.Uncertainty;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_DeformationModels;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.GsonBuilder;

import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.U3PaleoRateConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoRateConstraintFetcher;

/**
 * Data container module for paleoseismic data, including paleo rate constraints, and paleo average slip constraints
 * (from which rates can be inferred when combined with slip rates and scaling relationships)
 * 
 * @author kevin
 *
 */
public class PaleoseismicConstraintData implements SubModule<FaultSystemRupSet>,
JSON_TypeAdapterBackedModule<PaleoseismicConstraintData>, BranchAverageableModule<PaleoseismicConstraintData>,
SplittableRuptureModule<PaleoseismicConstraintData> {
	
	private transient FaultSystemRupSet rupSet;
	private List<? extends SectMappedUncertainDataConstraint> paleoRateConstraints;
	private PaleoProbabilityModel paleoProbModel;
	private List<? extends SectMappedUncertainDataConstraint> paleoSlipConstraints;
	private PaleoSlipProbabilityModel paleoSlipProbModel;
	
	public static PaleoseismicConstraintData loadUCERF3(FaultSystemRupSet rupSet) throws IOException {
		// paleo event rate
		List<U3PaleoRateConstraint> paleoRateConstraints =
				UCERF3_PaleoRateConstraintFetcher.getConstraints(rupSet.getFaultSectionDataList());
		UCERF3_PaleoProbabilityModel paleoProbModel = UCERF3_PaleoProbabilityModel.load();

		// paleo slip
		List<U3AveSlipConstraint> aveSlipConstraints = U3AveSlipConstraint.load(rupSet.getFaultSectionDataList());
		PaleoSlipProbabilityModel paleoSlipProbModel = U3AveSlipConstraint.slip_prob_model;
		
		return new PaleoseismicConstraintData(rupSet, paleoRateConstraints, paleoProbModel,
				aveSlipConstraints, paleoSlipProbModel);
	}
	
	@SuppressWarnings("unused") // used for deserialization
	private PaleoseismicConstraintData() {}

	public PaleoseismicConstraintData(FaultSystemRupSet rupSet,
			List<? extends SectMappedUncertainDataConstraint> paleoRateConstraints,
			PaleoProbabilityModel paleoProbModel,
			List<? extends SectMappedUncertainDataConstraint> paleoSlipConstraints,
			PaleoSlipProbabilityModel paleoSlipProbModel) {
		this.rupSet = rupSet;
		this.paleoRateConstraints = paleoRateConstraints;
		this.paleoProbModel = paleoProbModel;
		this.paleoSlipConstraints = paleoSlipConstraints;
		this.paleoSlipProbModel = paleoSlipProbModel;
	}

	@Override
	public String getName() {
		return "Paleoseismic Constraint Data";
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		if (this.rupSet != null && parent != null)
			Preconditions.checkState(rupSet.areSectionsEquivalentTo(parent));
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}

	@Override
	public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
		if (this.rupSet != null && newParent != null)
			Preconditions.checkState(rupSet.areSectionsEquivalentTo(newParent));
		return new PaleoseismicConstraintData(newParent, paleoRateConstraints, paleoProbModel,
				paleoSlipConstraints, paleoSlipProbModel);
	}

	@Override
	public String getFileName() {
		return "paleo_constraint_data.json";
	}

	@Override
	public Type getType() {
		return PaleoseismicConstraintData.class;
	}

	@Override
	public PaleoseismicConstraintData get() {
		return this;
	}

	@Override
	public void set(PaleoseismicConstraintData value) {
		rupSet = value.rupSet;
		paleoRateConstraints = value.paleoRateConstraints;
		paleoProbModel = value.paleoProbModel;
		paleoSlipConstraints = value.paleoSlipConstraints;
		paleoSlipProbModel = value.paleoSlipProbModel;
	}

	@Override
	public void registerTypeAdapters(GsonBuilder builder) {}
	
	public boolean hasPaleoRateConstraints() {
		return paleoRateConstraints != null && !paleoRateConstraints.isEmpty();
	}

	public List<? extends SectMappedUncertainDataConstraint> getPaleoRateConstraints() {
		return paleoRateConstraints;
	}

	public PaleoProbabilityModel getPaleoProbModel() {
		return paleoProbModel;
	}
	
	public boolean hasPaleoSlipConstraints() {
		return paleoSlipConstraints != null && !paleoSlipConstraints.isEmpty();
	}

	public List<? extends SectMappedUncertainDataConstraint> getPaleoSlipConstraints() {
		return paleoSlipConstraints;
	}

	public PaleoSlipProbabilityModel getPaleoSlipProbModel() {
		return paleoSlipProbModel;
	}
	
	private boolean prevAppliedRateUncertainty;
	List<SectMappedUncertainDataConstraint> prevInferred = null;
	
	/**
	 * Converts paleo slip constraints to rate constraints using the rupture set target slip rates
	 * 
	 * @param applySlipRateUncertainty if true, uncertainties are expanded to consider slip-rate standard deviations
	 * @return list of rate constraints inferred from average slip constraints
	 */
	public synchronized List<SectMappedUncertainDataConstraint> inferRatesFromSlipConstraints(boolean applySlipRateUncertainty) {
		if (prevInferred != null && prevAppliedRateUncertainty == applySlipRateUncertainty)
			return new ArrayList<>(prevInferred);
		prevInferred = inferRatesFromSlipConstraints(rupSet, paleoSlipConstraints, applySlipRateUncertainty);
		prevAppliedRateUncertainty = applySlipRateUncertainty;
		return new ArrayList<>(prevInferred);
	}
	
	/**
	 * Converts paleo slip constraints to rate constraints using the rupture set target slip rates, propagating uncertainties
	 * 
	 * @param targetSlipRates target slip rate data
	 * @param paleoSlipConstraints paleo-slip constraints, to be converted to rate constraints
	 * @param applySlipRateUncertainty if true, uncertainties are expanded to consider slip-rate standard deviations
	 * @return list of rate constraints inferred from average slip constraints
	 */
	public static List<SectMappedUncertainDataConstraint> inferRatesFromSlipConstraints(
			FaultSystemRupSet rupSet, List<? extends SectMappedUncertainDataConstraint> paleoSlipConstraints,
			boolean applySlipRateUncertainty) {
		SectSlipRates targetSlipRates = rupSet.requireModule(SectSlipRates.class);
		double[] slipRateStdDevs = null;
		if (applySlipRateUncertainty) {
			slipRateStdDevs = targetSlipRates.getSlipRateStdDevs();
			for (int s=0; s<slipRateStdDevs.length; s++) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				double slip = targetSlipRates.getSlipRate(s);
				if (slip > 0 && sect instanceof GeoJSONFaultSection) {
					double origFractSlip = ((GeoJSONFaultSection)sect).getProperties().getDouble(
							NSHM23_DeformationModels.ORIG_FRACT_STD_DEV_PROPERTY_NAME, Double.NaN);
					if (origFractSlip > 0d)
						slipRateStdDevs[s] = Math.max(slipRateStdDevs[s], slip*origFractSlip);
				}
			}
		}
		
		List<SectMappedUncertainDataConstraint> inferred = new ArrayList<>();
		
		for (SectMappedUncertainDataConstraint constraint : paleoSlipConstraints) {
			// this is a constraint on average slip, but we need to convert it to a constraint on rates
			
			// slip rate, in m/yr
			double targetSlipRate = targetSlipRates.getSlipRate(constraint.sectionIndex);
			// slip rate std dev, in m/yr
			double targetSlipRateStdDev = applySlipRateUncertainty ? slipRateStdDevs[constraint.sectionIndex] : 0d;
			
			// average slip, in m
			double aveSlip = constraint.bestEstimate;
			// average slip std dev, in m
			double aveSlipStdDev = constraint.getPreferredStdDev();

			
			System.out.println("Inferring rate constraint from paleo slip constraint on "+constraint.sectionName);
			System.out.println("\tslip="+(float)aveSlip+" +/- "+(float)aveSlipStdDev);
			System.out.println("\tslip rate="+(float)targetSlipRate+" +/- "+(float)targetSlipRateStdDev);
			
			// rate estimate: r = s / d
			double meanRate = targetSlipRate / aveSlip;
			/*
			 * uncertainty propagation:
			 * 		r +/- deltaR = (s +/- deltaS)/(d +/- deltaD)
			 * simplifies to (see https://www.geol.lsu.edu/jlorenzo/geophysics/uncertainties/Uncertaintiespart2.html):
			 * 		deltaR/r = sqrt((deltaS/s)^2 + (deltaD/d)^2)
			 * 		deltaR = r*sqrt((deltaS/s)^2 + (deltaD/d)^2)
			 * 
			 * Note: UCERF3 used a different version:
			 * 		r_high = s / d_low
			 * 		r_low = s / d_high
			 * 		deltaR = = (r_high - r_low)/4
			 * This worked fine for and is pretty similar to the above values for small-ish uncertainties with
			 * applySlipRateUncertainty == false, but goes to infinity as d_low approaches 0. These issues were never
			 * encountered as the UCERF3 slip uncertainties were small, but is not a good general solution. 
			 */
			double rateSD;
			if (applySlipRateUncertainty) {
				if (targetSlipRate == 0d)
					// slip rate is zero, so mean rate is zero, set the uncertainty to the upper bound implied by
					// taking both high options (high slip rate, 0+sd, and low slip, slip-sd)
					rateSD = targetSlipRateStdDev / (aveSlip-aveSlipStdDev);
				else 
					rateSD = meanRate * Math.sqrt(
							Math.pow(targetSlipRateStdDev/targetSlipRate, 2) + Math.pow(aveSlipStdDev/aveSlip, 2));
			} else {
				// even simpler: deltaR = r*sqrt((deltaS/s)^2) = r*deltaD/d
				rateSD = meanRate * aveSlipStdDev/aveSlip;
			}
			
			System.out.println("\trate="+(float)meanRate+" +/- "+(float)rateSD);
			
			inferred.add(new SectMappedUncertainDataConstraint(constraint.name, constraint.sectionIndex,
					constraint.sectionName, constraint.dataLocation, meanRate, new Uncertainty(rateSD)));
		}
		
		return inferred;
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(
				new File("/home/kevin/markdown/inversions/fm3_1_u3ref_uniform_coulomb.zip"));
		
		PaleoseismicConstraintData data = loadUCERF3(rupSet);
		data.inferRatesFromSlipConstraints(true);
	}

	@Override
	public AveragingAccumulator<PaleoseismicConstraintData> averagingAccumulator() {
		// TODO Auto-generated method stub
		return new AveragingAccumulator<PaleoseismicConstraintData>() {
			
			PaleoseismicConstraintData paleoData;

			@Override
			public void process(PaleoseismicConstraintData module, double relWeight) {
				if (paleoData == null) {
					paleoData = module;
				} else {
					// make sure it's the same
					boolean same = paleoConstraintsSame(paleoData.getPaleoRateConstraints(),
							module.getPaleoRateConstraints());
					same = same && paleoConstraintsSame(paleoData.getPaleoSlipConstraints(),
							module.getPaleoSlipConstraints());
					if (same && paleoData.getPaleoProbModel() != null)
						same = paleoData.getPaleoProbModel().getClass().equals(module.getPaleoProbModel().getClass());
					if (same && paleoData.getPaleoSlipProbModel() != null)
						same = paleoData.getPaleoSlipProbModel().getClass().equals(module.getPaleoSlipProbModel().getClass());
					if (!same)
						throw new IllegalStateException("Paleo-seismic data varies by branch, averaging not (yet) supported");
				}
			}

			@Override
			public PaleoseismicConstraintData getAverage() {
				return paleoData;
			}

			@Override
			public Class<PaleoseismicConstraintData> getType() {
				return PaleoseismicConstraintData.class;
			}
		};
	}
	
	private static boolean paleoConstraintsSame(List<? extends SectMappedUncertainDataConstraint> constr1,
			List<? extends SectMappedUncertainDataConstraint> constr2) {
		if ((constr1 == null) != (constr2 == null))
			return false;
		if (constr1 == null && constr2 == null)
			return true;
		if (constr1.size() != constr2.size())
			return false;
		for (int i=0; i<constr1.size(); i++) {
			SectMappedUncertainDataConstraint c1 = constr1.get(i);
			SectMappedUncertainDataConstraint c2 = constr2.get(i);
			if (c1.sectionIndex != c2.sectionIndex)
				return false;
			if ((float)c1.bestEstimate != (float)c2.bestEstimate)
				return false;
		}
		return true;
	}

	@Override
	public PaleoseismicConstraintData getForRuptureSubSet(FaultSystemRupSet rupSubSet, RuptureSubSetMappings mappings) {
		List<SectMappedUncertainDataConstraint> filteredRateConstraints = getRemapped(paleoRateConstraints, mappings);
		List<SectMappedUncertainDataConstraint> filteredSlipConstraints = getRemapped(paleoSlipConstraints, mappings);
		if (filteredRateConstraints == null && filteredSlipConstraints == null)
			return null;
		return new PaleoseismicConstraintData(rupSubSet, filteredRateConstraints, paleoProbModel,
				filteredSlipConstraints, paleoSlipProbModel);
	}
	
	private static List<SectMappedUncertainDataConstraint> getRemapped(
			List<? extends SectMappedUncertainDataConstraint> constraints, RuptureSubSetMappings mappings) {
		if (constraints == null || constraints.isEmpty())
			return null;
		List<SectMappedUncertainDataConstraint> remapped = new ArrayList<>();
		for (SectMappedUncertainDataConstraint constraint : constraints)
			if (mappings.isSectRetained(constraint.sectionIndex))
				remapped.add(constraint.forRemappedSectionIndex(mappings.getNewSectID(constraint.sectionIndex)));
		if (remapped.isEmpty())
			return null;
		return remapped;
	}

	@Override
	public PaleoseismicConstraintData getForSplitRuptureSet(FaultSystemRupSet splitRupSet,
			RuptureSetSplitMappings mappings) {
		List<SectMappedUncertainDataConstraint> filteredRateConstraints = getRemapped(paleoRateConstraints, mappings);
		List<SectMappedUncertainDataConstraint> filteredSlipConstraints = getRemapped(paleoSlipConstraints, mappings);
		if (filteredRateConstraints == null && filteredSlipConstraints == null)
			return null;
		return new PaleoseismicConstraintData(splitRupSet, filteredRateConstraints, paleoProbModel,
				filteredSlipConstraints, paleoSlipProbModel);
	}
	
	private static List<SectMappedUncertainDataConstraint> getRemapped(
			List<? extends SectMappedUncertainDataConstraint> constraints, RuptureSetSplitMappings mappings) {
		if (constraints == null || constraints.isEmpty())
			return null;
		List<SectMappedUncertainDataConstraint> remapped = new ArrayList<>();
		for (SectMappedUncertainDataConstraint constraint : constraints) {
			List<Integer> newIDs = mappings.getNewSectIDs(constraint.sectionIndex);
			Preconditions.checkState(newIDs.size() == 1,
					"Can't split paleo constraints");
			remapped.add(constraint.forRemappedSectionIndex(newIDs.get(0)));
		}
		if (remapped.isEmpty())
			return null;
		return remapped;
	}
	
	public static PaleoseismicConstraintData fromSimpleCSV(FaultSystemRupSet rupSet, CSVFile<String> csv,
			PaleoProbabilityModel probModel) {
		List<SectMappedUncertainDataConstraint> constraints = new ArrayList<>();
		
		double maxDist = 100d;
		
		Preconditions.checkState(csv.getNumCols() == 6, "Expected 6 columns, have %s", csv.getNumCols());
		for (int row=1; row<csv.getNumRows(); row++) {
			String name = csv.get(row, 0);
			String sectStr = csv.get(row, 1);
			int subsectionIndex = sectStr == null || sectStr.isBlank() ? -1 : Integer.parseInt(sectStr);
			double lat = csv.getDouble(row, 2);
			double lon = csv.getDouble(row, 3);
			double rate = csv.getDouble(row, 4);
			double rateStdDev = csv.getDouble(row, 5);
			
			Location loc = new Location(lat, lon);
			
			FaultSection sect;
			
			if (subsectionIndex < 0) {
				// map it
				System.out.println("Looking for nearest subsection to paleoseidmic site: "+(float)lat+", "+(float)lon);
				sect = findMatchingSect(loc, rupSet.getFaultSectionDataList(), maxDist, maxDist, maxDist);
				Preconditions.checkNotNull(sect, "No fault sections found within %s km of site '%s' at location %s",
						(float)maxDist, name, loc);
				double dist = sect.getFaultTrace().minDistToLine(loc);
				System.out.println("\tClosest match is "+sect.getSectionId()+". "+sect.getSectionName()
						+" ("+(float)dist+" km away)");
				subsectionIndex = sect.getSectionId();
			} else {
				sect = rupSet.getFaultSectionData(subsectionIndex);
				double dist = sect.getFaultTrace().minDistToLine(loc);
				System.out.println("Supplied section for "+name+" is "+sect.getSectionId()+". "+sect.getSectionName()
						+" ("+(float)dist+" km away)");
			}
			
			Preconditions.checkState(rate > 0d, "Bad rate for paleo site '%s': %s", name, rate);
			Preconditions.checkState(rateStdDev > 0d, "Bad rate std. dev. for paleo site '%s': %s", name, rateStdDev);
			constraints.add(new SectMappedUncertainDataConstraint(name, sect.getSectionId(), sect.getName(),
					loc, rate, new Uncertainty(rateStdDev)));
		}
		
		return new PaleoseismicConstraintData(rupSet, constraints, probModel, null, null);
	}
	
	// filter sections to actually check with cartesian distances
	private static final double LOC_CHECK_DEGREE_TOLERANCE = 3d;
	private static final double LOC_CHECK_DEGREE_TOLERANCE_SQ = LOC_CHECK_DEGREE_TOLERANCE*LOC_CHECK_DEGREE_TOLERANCE;
	
	/**
	 * 
	 * @param loc
	 * @param subSects
	 * @param maxDistNoneContained maximum distance to search if site not contained in the surface project of any fault
	 * @param maxDistOtherContained maximum distance to search if site is contained by a fault, but another trace is closer
	 * @param maxDistContained maximum distance to search to the trace of a fault whose surface projection contains this site
	 * @return
	 */
	public static FaultSection findMatchingSect(Location loc, List<? extends FaultSection> subSects,
			double maxDistNoneContained, double maxDistOtherContained, double maxDistContained) {
		List<FaultSection> candidates = new ArrayList<>();
		List<FaultSection> containsCandidates = new ArrayList<>();
		
		Map<FaultSection, Double> candidateDists = new HashMap<>();
		
		for (FaultSection sect : subSects) {
			// first check cartesian distance
			boolean candidate = false;
			for (Location traceLoc : sect.getFaultTrace()) {
				double latDiff = loc.getLatitude() - traceLoc.getLatitude();
				double lonDiff = loc.getLongitude() - traceLoc.getLongitude();
				if (latDiff*latDiff + lonDiff*lonDiff < LOC_CHECK_DEGREE_TOLERANCE_SQ) {
					candidate = true;
					break;
				}
			}
			if (candidate) {
				candidates.add(sect);
				double dist = sect.getFaultTrace().minDistToLine(loc);
				candidateDists.put(sect, dist);
				// see if this fault contains it
				if (sect.getAveDip() < 89d) {
					LocationList perim = new LocationList();
//					perim.addAll(sect.getFaultSurface(1d).getPerimeter());
					perim.addAll(sect.getFaultSurface(1d).getEvenlyDiscritizedPerimeter());
					if (!perim.last().equals(perim.first()))
						perim.add(perim.first());
					Region region = null;
					try {
						region = new Region(perim, BorderType.GREAT_CIRCLE);
					} catch (Exception e) {
						// try regular perim
						perim = new LocationList();
						perim.addAll(sect.getFaultSurface(1d).getPerimeter());
						if (!perim.last().equals(perim.first()))
							perim.add(perim.first());
						try {
							region = new Region(perim, BorderType.GREAT_CIRCLE);
						} catch (Exception e1) {
							System.err.println("WARNING: failed to create polygon for section "+sect.getSectionId()
								+". "+sect.getSectionName()+" when searching for paleo mappings for site at "+loc);
						}
					}
					if (region != null && region.contains(loc))
						containsCandidates.add(sect);
				}
			}
		}
		
		// find the closest of any candidate section
		FaultSection closest = null;
		double closestDist = Double.POSITIVE_INFINITY;
		for (FaultSection sect : candidates) {
			double dist = candidateDists.get(sect);
			if (dist < closestDist) {
				closestDist = dist;
				closest = sect;
			}
		}
		
		if (containsCandidates.isEmpty()) {
			// this site is not in the surface projection of any faults, return if less than the none-contained threshold
			// this allows the offshore noyo site to match
			if (closestDist < maxDistNoneContained)
				return closest;
			// no match
			return null;
		}
		
		// if we're here, then this site is contained in the surface projection of at least 1 fault
		
		// first see if it's within the inner threshold of any fault, regardless of if it is contained
		if (closestDist < maxDistOtherContained)
			return closest;
		
		// see if any of the faults containing it are close enough
		FaultSection closestContaining = null;
		double closestContainingDist = Double.POSITIVE_INFINITY;
		for (FaultSection sect : containsCandidates) {
			double dist = candidateDists.get(sect);
			if (dist < closestContainingDist) {
				closestContainingDist = dist;
				closestContaining = sect;
			}
		}
		
		if (closestContainingDist < maxDistContained)
			return closestContaining;
		// no match
		return null;
	}

}
