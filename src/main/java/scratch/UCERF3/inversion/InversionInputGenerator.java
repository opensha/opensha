package scratch.UCERF3.inversion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import cern.colt.function.tdouble.IntIntDoubleFunction;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseRCDoubleMatrix2D;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.MatrixIO;
import scratch.UCERF3.utils.SectionMFD_constraint;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

/**
 * This class is used to generate inversion inputs (A/A_ineq matrices, d/d_ineq vectors) for a given
 * rupture set, inversion configuration, paleo rate constraints, improbability constraint, and paleo
 * probability model. It can also save these inputs to a zip file to be run on high performance
 * computing.
 * 
 * @author Kevin, Morgan, Ned
 *
 */
public class InversionInputGenerator {
	
	private static final boolean D = false;
	/**
	 * this enables use of the getQuick and setQuick methods on the sparse matrices.
	 * this comes with a performance boost, but disables range checks and is more prone
	 * to errors. this will always be true if debugging is disabled above. when debugging
	 * is enabled, the 2nd value of the following line is used to enable/disable.
	 */
	private static final boolean QUICK_GETS_SETS = true;
	private boolean aPrioriConstraintForZeroRates = true;  // If true, a Priori rup-rate constraint is applied to zero rates (eg, rups not in UCERF2)
	private double aPrioriConstraintForZeroRatesWtFactor = 0.1; // Amount to multiply standard a-priori rup rate weight by when applying to zero rates (minimization constraint for rups not in UCERF2)
	private boolean excludeParkfieldRupsFromMfdEqualityConstraints = true; // If true, rates of Parkfield M~6 ruptures do not count toward MFD Equality Constraint misfit
	
	// inputs
	private InversionFaultSystemRupSet rupSet;
	private InversionConfiguration config;
	private List<PaleoRateConstraint> paleoRateConstraints;
	private List<AveSlipConstraint> aveSlipConstraints;
	private double[] improbabilityConstraint;
	private PaleoProbabilityModel paleoProbabilityModel;
	
	// outputs
	private DoubleMatrix2D A;
	private DoubleMatrix2D A_ineq;
	private double[] d;
	private double[] d_ineq;
	private double[] initial;
	private double[] minimumRuptureRates;
	
	private ArrayList<Integer> rangeEndRows;
	private ArrayList<String> rangeNames;
	
	public InversionInputGenerator(
			InversionFaultSystemRupSet rupSet,
			InversionConfiguration config,
			List<PaleoRateConstraint> paleoRateConstraints,
			List<AveSlipConstraint> aveSlipConstraints,
			double[] improbabilityConstraint, // may become an object in the future
			PaleoProbabilityModel paleoProbabilityModel) {
		this.rupSet = rupSet;
		this.config = config;
		this.paleoRateConstraints = paleoRateConstraints;
		this.improbabilityConstraint = improbabilityConstraint;
		this.aveSlipConstraints = aveSlipConstraints;
		this.paleoProbabilityModel = paleoProbabilityModel;
	}
	
	private static PaleoProbabilityModel defaultProbModel = null;
	
	/**
	 * Loads the default paleo probability model for UCERF3 (Glenn's file). Can be turned into
	 * an enum if we get alternatives
	 * @return
	 * @throws IOException 
	 */
	public static PaleoProbabilityModel loadDefaultPaleoProbabilityModel() throws IOException {
		if (defaultProbModel == null)
			defaultProbModel = UCERF3_PaleoProbabilityModel.load();
		return defaultProbModel;
	}
	
	public void generateInputs() {
		generateInputs(null);
	}
	
	public void generateInputs(Class<? extends DoubleMatrix2D> clazz) {
		/*
		 * This is a very important part of our code. There are a few key rules we should abide by here
		 * to make sure it continues to operate correctly.
		 * 
		 * * ABSOLUTELY NO try/catch blocks that just print the stack trace and continue on.
		 * * No data file loading in here - data files should be loaded externally and passed in
		 */
		
		int numSections = rupSet.getNumSections();
		int numRuptures = rupSet.getNumRuptures();
		
		initial = config.getInitialRupModel();
		minimumRuptureRates = null;
		if (initial == null) {
			// all zeros
			initial = new double[numRuptures];
		}
		
		// now lets do a little input validation
		Preconditions.checkState(initial.length == numRuptures);
		Preconditions.checkState(config.getA_PrioriRupConstraint() == null
				|| config.getA_PrioriRupConstraint().length == numRuptures);
		Preconditions.checkState(config.getMinimumRuptureRateBasis() == null
				|| config.getMinimumRuptureRateBasis().length == numRuptures);
		
		double minimumRuptureRateFraction = config.getMinimumRuptureRateFraction();
		if (minimumRuptureRateFraction > 0) {
			// set up minimum rupture rates (water level)
			double[] minimumRuptureRateBasis = config.getMinimumRuptureRateBasis();
			Preconditions.checkNotNull(minimumRuptureRateBasis,
					"minimum rate fraction specified but no minimum rate basis given!");
			
			// first check to make sure that they're not all zeros
			boolean allZeros = true;
			for (int i=0; i<numRuptures; i++) {
				if (minimumRuptureRateBasis[i] > 0) {
					allZeros = false;
					break;
				}
			}
			Preconditions.checkState(!allZeros, "cannot set water level when water level rates are all zero!");
			
			minimumRuptureRates = new double[initial.length];
			for (int i=0; i < numRuptures; i++)
				minimumRuptureRates[i] = minimumRuptureRateBasis[i]*minimumRuptureRateFraction;
		}
		
		// now configure the minimum rupture rates
		
		double[] sectSlipRateReduced = rupSet.getSlipRateForAllSections();
//		double[] sectSlipRateStdDevReduced = getSlipRateStdDevForAllSections();  // CURRENTLY NOT USED
		double[] rupMeanMag = rupSet.getMagForAllRups();
		
		rangeEndRows = new ArrayList<Integer>();
		rangeNames = new ArrayList<String>();
		
		// CURRENTLY, EVERY SUBSECTION IS INCLUDED IN SLIP-RATE CONSTRAINT - CONSTRAINT CANNOT BE DISABLED
		// NORMALIZED (minimize ratio of model to target), UNNORMALIZED (minimize difference), 
		// or BOTH (NORMALIZED & UNNORMALIZED both included - twice as many constraints) can be specified in SlipRateConstraintWeightingType
		// NaN slip rates are treated as 0 slip rates: We minimize the model slip rates on these sections
		int numSlipRateConstraints = numSections;
		if (config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.BOTH) 
			numSlipRateConstraints+=numSections;
		
		// Find number of rows in A matrix (equals the total number of constraints)
		if(D) System.out.println("\nNumber of slip-rate constraints:    " + numSlipRateConstraints);
		int numRows = numSlipRateConstraints;
		if (numRows > 0) {
			rangeEndRows.add(numRows-1);
			rangeNames.add("Slip Rate");
		}
		
		int numPaleoRows = (int)Math.signum(config.getPaleoRateConstraintWt())*paleoRateConstraints.size();
		if(D) System.out.println("Number of paleo section-rate constraints: "+numPaleoRows);
		if (numPaleoRows > 0) {
			numRows += numPaleoRows;
			rangeEndRows.add(numRows-1);
			rangeNames.add("Paleo Event Rates");
		}
		
		if (config.getPaleoSlipConstraintWt() > 0.0) {
			int numPaleoSlipRows = aveSlipConstraints.size();
			if(D) System.out.println("Number of paleo average slip constraints: "+numPaleoSlipRows);
			numRows += numPaleoSlipRows;
			rangeEndRows.add(numRows-1);
			rangeNames.add("Paleo Slips");
		}
		
		if (config.getRupRateConstraintWt() > 0.0) {
			double[] aPrioriRupRates = config.getA_PrioriRupConstraint();
			int numRupRateRows = 0;
			for (int i=0; i<numRuptures; i++) 
					if ( aPrioriRupRates[i]>0) 	numRupRateRows++;	
			if (aPrioriConstraintForZeroRates) numRupRateRows++;
			if(D) System.out.println("Number of rupture-rate constraints: "+numRupRateRows);
			numRows += numRupRateRows;
			rangeEndRows.add(numRows-1);
			rangeNames.add("Rupture Rates");
		}
		
		List<IDPairing> smoothingConstraintRupPairings = Lists.newArrayList();
		if (config.getRupRateSmoothingConstraintWt() > 0) { 
			smoothingConstraintRupPairings = getRupSmoothingPairings(rupSet);
			int numRupRateSmoothingRows = smoothingConstraintRupPairings.size();
			if(D) System.out.println("Number of rupture-rate constraints: "+numRupRateSmoothingRows);
			numRows += numRupRateSmoothingRows;
			rangeEndRows.add(numRows-1);
			rangeNames.add("Rupture Rate Smoothing");
		}
		
		
		
//		int numMinimizationRows = (int)Math.signum(config.getMinimizationConstraintWt())*numRuptures;	// For Coulomb Improbability Constraint (not currently used)
		int numMinimizationRows = 0;
		if (config.getMinimizationConstraintWt() > 0.0) {
			for(int rup=0; rup<numRuptures; rup++) 
				if (rupSet.isRuptureBelowSectMinMag(rup) == true) numMinimizationRows++;
			if(D) System.out.println("Number of minimization constraints: "+numMinimizationRows);
			numRows += numMinimizationRows;
			rangeEndRows.add(numRows-1);
			rangeNames.add("Minimization");
		}
		
		IncrementalMagFreqDist targetMagFreqDist=null;
		if (config.getMagnitudeEqualityConstraintWt() > 0.0) {
			int totalNumMagFreqConstraints = 0;
			for (MFD_InversionConstraint constr : config.getMfdEqualityConstraints()) {
				targetMagFreqDist=constr.getMagFreqDist();
				// Find number of rows used for MFD equality constraint - only include mag bins between minimum and maximum magnitudes in rupture set
				totalNumMagFreqConstraints += targetMagFreqDist.getClosestXIndex(rupSet.getMaxMag())-targetMagFreqDist.getClosestXIndex(rupSet.getMinMag())+1;
			}
			if(D) System.out.println("Number of magnitude-distribution equality constraints: "
					+totalNumMagFreqConstraints);
			// add number of rows used for magnitude distribution constraint
			numRows += totalNumMagFreqConstraints;
			rangeEndRows.add(numRows-1);
			rangeNames.add("MFD Equality");
		}
		if (config.getParticipationSmoothnessConstraintWt() > 0.0) {
			int totalNumMagParticipationConstraints = 0;
			for (int sect=0; sect<numSections; sect++) { 
				List<Integer> rupturesForSection = rupSet.getRupturesForSection(sect);
				// Find minimum and maximum rupture-magnitudes for that subsection
				double minMag = 10.0; double maxMag = 0.0;
				for (int rupIndex=0; rupIndex<rupturesForSection.size(); rupIndex++) {
					if (rupMeanMag[rupturesForSection.get(rupIndex)] < minMag)
							minMag = rupMeanMag[rupturesForSection.get(rupIndex)];
					if (rupMeanMag[rupturesForSection.get(rupIndex)] > maxMag)
						maxMag = rupMeanMag[rupturesForSection.get(rupIndex)];
				}
				// Find total number of section magnitude-bins
				for (double m=minMag; m<maxMag; m=m+config.getParticipationConstraintMagBinSize()) { 
					for (int rupIndex=0; rupIndex<rupturesForSection.size(); rupIndex++) {
						if (rupMeanMag[rupturesForSection.get(rupIndex)]>=m
								&& rupMeanMag[rupturesForSection.get(rupIndex)]
								              < m+config.getParticipationConstraintMagBinSize()) {
							totalNumMagParticipationConstraints++; 
							numRows++;
							break;
						}				
					}
				}
			}
			if(D) System.out.println("Number of MFD participation constraints: "
					+ totalNumMagParticipationConstraints);
			rangeEndRows.add(numRows-1);
			rangeNames.add("MFD Participation");
		}
		ArrayList<SectionMFD_constraint> MFDConstraints = null;
		if (config.getNucleationMFDConstraintWt() > 0.0) {
			int totalNumNucleationMFDConstraints = 0;
			MFDConstraints = FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
			for (int sect=0; sect<numSections; sect++) { 
				SectionMFD_constraint sectMFDConstraint = MFDConstraints.get(sect);
				if (sectMFDConstraint == null) continue; // Parent sections with Mmax<6 have no MFD constraint; skip these
				for (int i=0; i<sectMFDConstraint.getNumMags(); i++) {
					if (sectMFDConstraint.getRate(i) > 0) {
						totalNumNucleationMFDConstraints++;
						numRows++;
					}
				}
			}
			if(D) System.out.println("Number of Nucleation MFD constraints: "+totalNumNucleationMFDConstraints);
			rangeEndRows.add(numRows-1);
			rangeNames.add("MFD Nucleation");
		}
		if (config.getMFDSmoothnessConstraintWt() > 0.0 || config.getMFDSmoothnessConstraintWtForPaleoParents() > 0.0) {
			int totalNumMFDSmoothnessConstraints = 0;
			MFDConstraints = FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
			
			// Get list of parent sections with paleo constraints
			ArrayList<Integer> paleoParents = new ArrayList<Integer>();
			if (config.getPaleoRateConstraintWt() > 0.0) {
				for (int i=0; i<paleoRateConstraints.size(); i++) {
					int paleoParentID = rupSet.getFaultSectionDataList().get(paleoRateConstraints.get(i).getSectionIndex()).getParentSectionId();
					paleoParents.add(paleoParentID);
				}
			}
			if (config.getPaleoSlipConstraintWt() > 0.0) {
				for (int i=0; i<aveSlipConstraints.size(); i++) {
					int paleoParentID = rupSet.getFaultSectionDataList().get(aveSlipConstraints.get(i).getSubSectionIndex()).getParentSectionId();
					paleoParents.add(paleoParentID);
				}
			}
			// Get list of parent sections
			ArrayList<Integer> parentIDs = new ArrayList<Integer>();
			for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
				int parentID = sect.getParentSectionId();
				if (!parentIDs.contains(parentID))
					parentIDs.add(parentID);
			}
			for (int parentID: parentIDs) {
				// Get list of subsections for parent 
				ArrayList<Integer> sectsForParent = new ArrayList<Integer>();
				for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
					int sectParentID = sect.getParentSectionId();
					if (sectParentID == parentID)
						sectsForParent.add(sect.getSectionId());
				}
				// For each beginning section of subsection-pair, there will be numMagBins # of constraints
				for (int j=1; j<sectsForParent.size()-2; j++) {
					int sect2 = sectsForParent.get(j);
					SectionMFD_constraint sectMFDConstraint = MFDConstraints.get(sect2);
					if (sectMFDConstraint == null) continue; // Parent sections with Mmax<6 have no MFD constraint; skip these
					int numMagBins = sectMFDConstraint.getNumMags();
					// Only add rows if this parent section will be included; it won't if it's not a paleo parent sect & MFDSmoothnessConstraintWt = 0
					// CASE WHERE MFDSmoothnessConstraintWt != 0 & MFDSmoothnessConstraintWtForPaleoParents 0 IS NOT SUPPORTED
					if (config.getMFDSmoothnessConstraintWt()>0.0 || paleoParents.indexOf(parentID) != -1) {
						totalNumMFDSmoothnessConstraints+=numMagBins;
						numRows+=numMagBins;
					}
				}
			}
			if(D) System.out.println("Number of MFD Smoothness constraints: "+totalNumMFDSmoothnessConstraints);
			rangeEndRows.add(numRows-1);
			rangeNames.add("MFD Smoothness");
		}
		if (config.getMomentConstraintWt() > 0.0) {
			numRows++;
			if(D) System.out.println("Number of Moment constraints: 1");
			rangeEndRows.add(numRows-1);
			rangeNames.add("Moment");
		}
		if (config.getParkfieldConstraintWt() > 0.0) {
			numRows++;
			if(D) System.out.println("Number of Parkfield constraints: 1");
			rangeEndRows.add(numRows-1);
			rangeNames.add("Parkfield");
		}
		if (config.getEventRateSmoothnessWt() > 0.0) {
			ArrayList<Integer> parentIDs = new ArrayList<Integer>();
			for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
				int parentID = sect.getParentSectionId();
				if (!parentIDs.contains(parentID))
					parentIDs.add(parentID);
			}
			int numParentSections=parentIDs.size();
			int numNewRows = rupSet.getNumSections()-numParentSections;
			numRows+=numNewRows;
			if(D) System.out.println("Number of Event-Rate Smoothness constraints: "+numNewRows);
			rangeEndRows.add(numRows-1);
			rangeNames.add("Event-Rate Smoothness");	
		}
		
		
		
		// Components of matrix equation to invert (Ax=d)
		A = buildMatrix(clazz, numRows, numRuptures); // A matrix
		d = new double[numRows];	// data vector d
		
		// MFD inequality constraint matrix and data vector (A_MFD * x <= d_MFD)
		// to be passed to SA algorithm
		int numMFDRows=0;
		if (config.getMagnitudeInequalityConstraintWt() > 0.0) {
			for (MFD_InversionConstraint constr : config.getMfdInequalityConstraints()) {
				targetMagFreqDist=constr.getMagFreqDist();
				// Add number of rows used for magnitude distribution constraint - only include mag bins between minimum and maximum magnitudes in rupture set				
				numMFDRows += targetMagFreqDist.getClosestXIndex(rupSet.getMaxMag())-targetMagFreqDist.getClosestXIndex(rupSet.getMinMag())+1;
			}
			A_ineq = buildMatrix(clazz, numMFDRows, numRuptures); // (A_MFD * x <= d_MFD)
			d_ineq = new double[numMFDRows];							
			if(D) System.out.println("Number of magnitude-distribution inequality constraints (not in A matrix): "
					+numMFDRows);
		}
		
		
		if(D) System.out.println("Total number of constraints (rows): " + numRows);
		if(D) System.out.println("\nNumber of fault sections: "
				+ numSections + ". Number of ruptures (columns): " + numRuptures + ".");
		
		
		
		
		// Put together "A" Matrix and data vector "d"
		Stopwatch watch_total = null;
		Stopwatch watch = null;
		if (D) {
			watch_total = Stopwatch.createStarted();
			watch = Stopwatch.createStarted();
		}
		
		// Make sparse matrix of slip in each rupture & data vector of section slip rates
		int numNonZeroElements = 0;  
		if(D) System.out.println("\nAdding slip per rup to A matrix ...");
		double slipRateConstraintWt_normalized = config.getSlipRateConstraintWt_normalized();
		double slipRateConstraintWt_unnormalized = config.getSlipRateConstraintWt_unnormalized();
		// A matrix component of slip-rate constraint 
		for (int rup=0; rup<numRuptures; rup++) {
			double[] slips = rupSet.getSlipOnSectionsForRup(rup);
			List<Integer> sects = rupSet.getSectionsIndicesForRup(rup);
			for (int i=0; i < slips.length; i++) {
				int row = sects.get(i);
				int col = rup;
				double val;
				if (config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.UNNORMALIZED || config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.BOTH) {
					if (QUICK_GETS_SETS)
						A.setQuick(row, col, slipRateConstraintWt_unnormalized * slips[i]);
					else
						A.set(row, col, slipRateConstraintWt_unnormalized * slips[i]);
					if(D) numNonZeroElements++;		
				}
				if (config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.NORMALIZED_BY_SLIP_RATE || config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.BOTH) {  
					if (config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.BOTH)
						row += numSections;
					// Note that constraints for sections w/ slip rate < 0.1 mm/yr is not normalized by slip rate -- otherwise misfit will be huge (GEOBOUND model has 10e-13 slip rates that will dominate misfit otherwise)
					if (sectSlipRateReduced[sects.get(i)] < 1E-4 || Double.isNaN(sectSlipRateReduced[sects.get(i)]))  
						val = slips[i]/0.0001;  
					else {
						val = slips[i]/sectSlipRateReduced[sects.get(i)]; 
					}
					if (QUICK_GETS_SETS)
						A.setQuick(row, col, slipRateConstraintWt_normalized * val);
					else
						A.set(row, col, slipRateConstraintWt_normalized * val);
					if(D) numNonZeroElements++;
				}
			}
		}  
		// d vector component of slip-rate constraint
		for (int sect=0; sect<numSections; sect++) {
			int row = sect;
			if (config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.UNNORMALIZED || config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.BOTH) 
				d[row] = slipRateConstraintWt_unnormalized * sectSlipRateReduced[sect];			
			if (config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.NORMALIZED_BY_SLIP_RATE || config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.BOTH) {
				if (config.getSlipRateWeightingType() == InversionConfiguration.SlipRateConstraintWeightingType.BOTH)
					row += numSections;
				if (sectSlipRateReduced[sect]<1E-4)  // For very small slip rates, do not normalize by slip rate (normalize by 0.0001 instead) so they don't dominate misfit
					d[row] = slipRateConstraintWt_normalized * sectSlipRateReduced[sect]/0.0001;
				else  // Normalize by slip rate
					d[row] = slipRateConstraintWt_normalized;
			}
			if (Double.isNaN(sectSlipRateReduced[sect]))  // Treat NaN slip rates as 0 (minimize)
				d[sect] = 0;
			if (Double.isNaN(d[sect]) || d[sect]<0)
				throw new IllegalStateException("d["+sect+"] is NaN or 0!  sectSlipRateReduced["+sect+"] = "+sectSlipRateReduced[sect]);
		}
		if (D) {
			System.out.println("Adding Slip-Rate Constraints took "+getTimeStr(watch)+".");
			watch.reset();
			watch.start();
			System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
		}
		
		
		// Make sparse matrix of paleo event probs for each rupture & data vector of mean event rates
		if (config.getPaleoRateConstraintWt() > 0.0) {
			double paleoRateConstraintWt = config.getPaleoRateConstraintWt();
			numNonZeroElements = 0;
			if(D) System.out.println("\nAdding event rates to A matrix ...");
			for (int i=numSlipRateConstraints; i<numSlipRateConstraints+paleoRateConstraints.size(); i++) {
				PaleoRateConstraint constraint = paleoRateConstraints.get(i-numSlipRateConstraints);
				d[i]=paleoRateConstraintWt * constraint.getMeanRate() / constraint.getStdDevOfMeanRate();
				List<Integer> rupsForSect = rupSet.getRupturesForSection(constraint.getSectionIndex());
				for (int rupIndex=0; rupIndex<rupsForSect.size(); rupIndex++) {
					int rup = rupsForSect.get(rupIndex);
					double probPaleoVisible = paleoProbabilityModel.getProbPaleoVisible(rupSet, rup, constraint.getSectionIndex());	
					double setVal = (paleoRateConstraintWt * probPaleoVisible / constraint.getStdDevOfMeanRate());
					if (QUICK_GETS_SETS)
						A.setQuick(i, rup, setVal);
					else
						A.set(i, rup, setVal);
					if(D) numNonZeroElements++;			
				}
			}
			if (D) {
				System.out.println("Adding Paleo-Rate Constraints took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
				System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
			}
		}

		
		// Mean paleo slip at a point
		int rowIndex = numSlipRateConstraints + numPaleoRows;  // current A matrix row index - number of rows used for slip-rate and paleo-rate constraints (previous 2 constraints)
		if (config.getPaleoSlipConstraintWt() > 0.0) {
			double paleoSlipConstraintWt = config.getPaleoSlipConstraintWt();
			numNonZeroElements = 0;
			if(D) System.out.println("\nAdding paleo mean slip constraints to A matrix ...");
			for (int i=0; i<aveSlipConstraints.size(); i++) {
				AveSlipConstraint constraint = aveSlipConstraints.get(i);
				int subsectionIndex = constraint.getSubSectionIndex();
				double meanRate = sectSlipRateReduced[subsectionIndex] / constraint.getWeightedMean();
				double lowRateBound = sectSlipRateReduced[subsectionIndex] / constraint.getUpperUncertaintyBound();
				double highRateBound = sectSlipRateReduced[subsectionIndex] / constraint.getLowerUncertaintyBound();
				double constraintError = highRateBound - lowRateBound;
				d[rowIndex]=paleoSlipConstraintWt * meanRate / constraintError;
				List<Integer> rupsForSect = rupSet.getRupturesForSection(subsectionIndex);
				for (int rupIndex=0; rupIndex<rupsForSect.size(); rupIndex++) {
					int rup = rupsForSect.get(rupIndex);
					int sectIndexInRup = rupSet.getSectionsIndicesForRup(rup).indexOf(subsectionIndex);
					double slipOnSect = rupSet.getSlipOnSectionsForRup(rup)[sectIndexInRup]; 
					double probVisible = AveSlipConstraint.getProbabilityOfObservedSlip(slipOnSect);
					double setVal = (paleoSlipConstraintWt * probVisible / constraintError);
					if (QUICK_GETS_SETS)
						A.setQuick(rowIndex, rup, setVal);
					else
						A.set(rowIndex, rup, setVal);
					numNonZeroElements++;
				}
				rowIndex++;
			}
			if (D) {
				System.out.println("Adding Paleo Mean-Slip Constraints took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
				System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
			}
		}
		
		
		// Rupture-Rate Constraint - close to UCERF2 rates
		if (config.getRupRateConstraintWt() > 0.0) {
			double rupRateConstraintWt = config.getRupRateConstraintWt();
			double zeroRupRateConstraintWt = config.getRupRateConstraintWt()*aPrioriConstraintForZeroRatesWtFactor;  // This is the RupRateConstraintWt for ruptures not in UCERF2 
			if(D) System.out.println("\nAdding rupture-rate constraint to A matrix ...");
			double[] aPrioriRupConstraint = config.getA_PrioriRupConstraint();
			numNonZeroElements = 0;
			for(int rup=0; rup<numRuptures; rup++) {
				// If aPrioriConstrintforZeroRates=false, Only apply if rupture-rate is greater than 0, this will keep ruptures on faults not in UCERF2 from being minimized
				if (aPrioriRupConstraint[rup]>0) { 
					if (QUICK_GETS_SETS)
						A.setQuick(rowIndex,rup,rupRateConstraintWt);
					else
						A.set(rowIndex,rup,rupRateConstraintWt);
					d[rowIndex]=aPrioriRupConstraint[rup]*rupRateConstraintWt;
					numNonZeroElements++; rowIndex++;
				}
			}
			// If aPrioriConstrintforZeroRates=true, constrain sum of all these rupture rates to be zero (minimize - adding only one row to A matrix)
			if (aPrioriConstraintForZeroRates) {
				for(int rup=0; rup<numRuptures; rup++) {
					if (aPrioriRupConstraint[rup]==0) { 
						if (QUICK_GETS_SETS) 
							A.setQuick(rowIndex,rup,zeroRupRateConstraintWt);
						else
							A.set(rowIndex,rup,zeroRupRateConstraintWt);
						numNonZeroElements++; 
					}
				}	
				d[rowIndex]=0;
				rowIndex++;
			}
			if (D) {
				System.out.println("Adding rupture-rate Constraints took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
				System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
			}
		}
		
		
		// Rupture rate smoothness constraint
		// This constrains rates of ruptures that differ by only 1 subsection
		if (config.getRupRateSmoothingConstraintWt() > 0) {
			double rupRateSmoothingConstraintWt = config.getRupRateSmoothingConstraintWt();
			if(D) System.out.println("\nAdding rupture rate smoothing constraints to A matrix ...");
			numNonZeroElements = 0;
			for (int i=0; i<smoothingConstraintRupPairings.size(); i++) {
				IDPairing rupPairings = smoothingConstraintRupPairings.get(i);
				if (QUICK_GETS_SETS) {
					A.setQuick(rowIndex,rupPairings.getID1(),rupRateSmoothingConstraintWt); 
					A.setQuick(rowIndex,rupPairings.getID2(),-rupRateSmoothingConstraintWt); 
				}
				else {
					A.set(rowIndex,rupPairings.getID1(),rupRateSmoothingConstraintWt);
					A.set(rowIndex,rupPairings.getID2(),-rupRateSmoothingConstraintWt);
				}
				d[rowIndex]=0;
				rowIndex++;
				numNonZeroElements++;
			}		
			if (D) {
				System.out.println("Adding rupture rate smoothness constraint took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
				System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
			}
		}
		
		
		// Rupture rate minimization constraint
		// Minimize the rates of ruptures below SectMinMag (strongly so that they have zero rates)
		if (config.getMinimizationConstraintWt() > 0.0) {
			double minimizationConstraintWt = config.getMinimizationConstraintWt();
			if(D) System.out.println("\nAdding minimization constraints to A matrix ...");
			numNonZeroElements = 0;
			for(int rup=0; rup<numRuptures; rup++) {
				if (rupSet.isRuptureBelowSectMinMag(rup) == true) { 
					if (QUICK_GETS_SETS)
						A.setQuick(rowIndex,rup,minimizationConstraintWt);
					else
						A.set(rowIndex,rup,minimizationConstraintWt);
					d[rowIndex] = 0;
					numNonZeroElements++; rowIndex++;
				}
			}
			if (D) {
				System.out.println("Adding rupture-rate minimization took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
				System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
			}
		}
		
/*		// Rupture rate minimization constraint
		// Penalize Ruptures with small Coulomb weights (Improbability constraint)
		if (config.getMinimizationConstraintWt() > 0.0) {
			double minimizationConstraintWt = config.getMinimizationConstraintWt();
			if(D) System.out.println("\nAdding minimization constraints to A matrix ...");
			numNonZeroElements = 0;
			for(int rup=0; rup<numRuptures; rup++) {
				if (QUICK_GETS_SETS)
					A.setQuick(rowIndex,rup,minimizationConstraintWt*improbabilityConstraint[rup]);
				else
					A.set(rowIndex,rup,minimizationConstraintWt*improbabilityConstraint[rup]);
				d[rowIndex]=0;
				numNonZeroElements++; rowIndex++;
			}
			if (D) {
				System.out.println("Adding Minimization Constraints took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
				System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
			}
		}	*/
		
		
		// Constrain Solution MFD to equal the Target MFD 
		// This is for equality constraints only -- inequality constraints must be
		// encoded into the A_ineq matrix instead since they are nonlinear
		if (config.getMagnitudeEqualityConstraintWt() > 0.0) {
			double magnitudeEqualityConstraintWt = config.getMagnitudeEqualityConstraintWt();
			List<MFD_InversionConstraint> mfdEqualityConstraints = config.getMfdEqualityConstraints();
			numNonZeroElements = 0;
			if(D) System.out.println("\nAdding " + mfdEqualityConstraints.size()
					+ " magnitude distribution equality constraints to A matrix ...");	
			
			// Find Parkfield M~6 ruptures (if we're excluding them)
			List<Integer> parkfieldRups = new ArrayList<Integer>();
			if (config.getParkfieldConstraintWt() > 0.0) {
				int parkfieldParentSectID = 32;
				List<Integer> potentialRups = rupSet.getRupturesForParentSection(parkfieldParentSectID);
				rupLoop:
					for (int i=0; i<potentialRups.size(); i++) {
						List<Integer> sects = rupSet.getSectionsIndicesForRup(potentialRups.get(i));
						// Make sure there are 6-8 subsections
						if (sects.size()<6 || sects.size()>8)
							continue rupLoop;
						// Make sure each section in rup is in Parkfield parent section
						for (int s=0; s<sects.size(); s++) {
							int parent = rupSet.getFaultSectionData(sects.get(s)).getParentSectionId();
							if (parent != parkfieldParentSectID)
								continue rupLoop;
						}
						parkfieldRups.add(potentialRups.get(i));
					}
			}
			
			// Loop over all MFD constraints in different regions
			for (int i=0; i < mfdEqualityConstraints.size(); i++) {
				double[] fractRupsInside = rupSet.getFractRupsInsideRegion(mfdEqualityConstraints.get(i).getRegion(), false);
				targetMagFreqDist=mfdEqualityConstraints.get(i).getMagFreqDist();	
				for(int rup=0; rup<numRuptures; rup++) {
					double mag = rupMeanMag[rup];
					double fractRupInside = fractRupsInside[rup];
					if (fractRupInside > 0 && mag>targetMagFreqDist.getMinX()-targetMagFreqDist.getDelta()/2.0 && mag<targetMagFreqDist.getMaxX()+targetMagFreqDist.getDelta()/2.0) {
						if (excludeParkfieldRupsFromMfdEqualityConstraints==false || !parkfieldRups.contains(rup)) {		
//							A.setQuick(rowIndex+targetMagFreqDist.getClosestXIndex(mag),rup,magnitudeEqualityConstraintWt * fractRupInside);
							if (QUICK_GETS_SETS){
								A.setQuick(rowIndex+targetMagFreqDist.getClosestXIndex(mag)-targetMagFreqDist.getClosestXIndex(rupSet.getMinMag()),rup,magnitudeEqualityConstraintWt * fractRupInside / targetMagFreqDist.getClosestYtoX(mag));
								if (targetMagFreqDist.getClosestYtoX(mag)==0) 
									A.setQuick(rowIndex+targetMagFreqDist.getClosestXIndex(mag)-targetMagFreqDist.getClosestXIndex(rupSet.getMinMag()),rup,0);
							}
							else
								A.set(rowIndex+targetMagFreqDist.getClosestXIndex(mag),rup,magnitudeEqualityConstraintWt * fractRupInside / targetMagFreqDist.getClosestYtoX(mag));
							numNonZeroElements++;
						}
					}
				}		
				for (int xIndex=targetMagFreqDist.getClosestXIndex(rupSet.getMinMag()); xIndex<=targetMagFreqDist.getClosestXIndex(rupSet.getMaxMag()); xIndex++) {
//					d[rowIndex]=targetMagFreqDist.getY(xIndex)*magnitudeEqualityConstraintWt;
					d[rowIndex]=magnitudeEqualityConstraintWt;
					if (targetMagFreqDist.getY(xIndex)==0) d[rowIndex]=0;
					rowIndex++; 
				}	
			}
			if (D) {
				System.out.println("Adding MFD Equality Constraints took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
				System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
			}
		}
		
		
		// Prepare MFD Inequality Constraint (not added to A matrix directly since it's nonlinear)
		if (config.getMagnitudeInequalityConstraintWt() > 0.0) {	
			double magnitudeInequalityConstraintWt = config.getMagnitudeInequalityConstraintWt();
			List<MFD_InversionConstraint> mfdInequalityConstraints = config.getMfdInequalityConstraints();
			int rowIndex_ineq = 0; 
			if(D) System.out.println("\nPreparing " + mfdInequalityConstraints.size()
					+ " magnitude inequality constraints ...");	
			
			// Loop over all MFD constraints in different regions
			for (int i=0; i < mfdInequalityConstraints.size(); i++) {
				double[] fractRupsInside = rupSet.getFractRupsInsideRegion(mfdInequalityConstraints.get(i).getRegion(), false);
				targetMagFreqDist=mfdInequalityConstraints.get(i).getMagFreqDist();	
				for(int rup=0; rup<numRuptures; rup++) {
					double mag = rupMeanMag[rup];
					double fractRupInside = fractRupsInside[rup];
					if (fractRupInside > 0 && mag>targetMagFreqDist.getMinX()-targetMagFreqDist.getDelta()/2.0 && mag<targetMagFreqDist.getMaxX()+targetMagFreqDist.getDelta()/2.0) {
//						A_ineq.setQuick(rowIndex_MFD+targetMagFreqDist.getClosestXIndex(mag),rup,magnitudeInequalityConstraintWt * fractRupInside);
						if (QUICK_GETS_SETS) {
							A_ineq.setQuick(rowIndex_ineq+targetMagFreqDist.getClosestXIndex(mag),rup,magnitudeInequalityConstraintWt * fractRupInside / targetMagFreqDist.getClosestYtoX(mag));
							if (targetMagFreqDist.getClosestYtoX(mag)==0) 
								A.setQuick(rowIndex_ineq+targetMagFreqDist.getClosestXIndex(mag),rup,0);
						}
						else
							A_ineq.set(rowIndex_ineq+targetMagFreqDist.getClosestXIndex(mag),rup,magnitudeInequalityConstraintWt * fractRupInside / targetMagFreqDist.getClosestYtoX(mag));
					}
				}		
				for (int xIndex=targetMagFreqDist.getClosestXIndex(rupSet.getMinMag()); xIndex<=targetMagFreqDist.getClosestXIndex(rupSet.getMaxMag()); xIndex++) {
//					d_ineq[rowIndex_ineq]=targetMagFreqDist.getY(xIndex)*magnitudeInequalityConstraintWt;
					d_ineq[rowIndex_ineq]=magnitudeInequalityConstraintWt;
					if (targetMagFreqDist.getY(xIndex)==0) d_ineq[rowIndex_ineq]=0;
					rowIndex_ineq++; 
				}	
			}	
			if (D) {
				System.out.println("Preparing MFD Inequality Constraints took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
			}
		}
		
		
		// MFD Smoothness Constraint - Constrain participation MFD to be uniform for each fault subsection
		if (config.getParticipationSmoothnessConstraintWt() > 0.0) {
			double participationSmoothnessConstraintWt = config.getParticipationSmoothnessConstraintWt();
			if(D) System.out.println("\nAdding MFD participation smoothness constraints to A matrix ...");
			numNonZeroElements = 0;
			ArrayList<Integer> numRupsForMagBin = new ArrayList<Integer>();
			for (int sect=0; sect<numSections; sect++) {
				List<Integer> rupturesForSection = rupSet.getRupturesForSection(sect);
				
				// Find minimum and maximum rupture-magnitudes for that subsection
				double minMag = 10.0; double maxMag = 0.0;
				for (int rupIndex=0; rupIndex<rupturesForSection.size(); rupIndex++) {
					if (rupMeanMag[rupturesForSection.get(rupIndex)] < minMag)
						minMag = rupMeanMag[rupturesForSection.get(rupIndex)];
					if (rupMeanMag[rupturesForSection.get(rupIndex)] > maxMag)
						maxMag = rupMeanMag[rupturesForSection.get(rupIndex)];
				}
				if (minMag == 10.0 || minMag == 0.0) {
					System.out.println("NO RUPTURES FOR SECTION #"+sect);  
					continue;  // Skip this section, go on to next section constraint
				}
				
				// Find number of ruptures for this section for each magnitude bin & total number
				// of magnitude-bins with ruptures
				numRupsForMagBin.clear();
				double participationConstraintMagBinSize = config.getParticipationConstraintMagBinSize();
				int numNonzeroMagBins = 0;
				for (double m=minMag; m<maxMag; m=m+participationConstraintMagBinSize) {
					numRupsForMagBin.add(0);
					for (int rupIndex=0; rupIndex<rupturesForSection.size(); rupIndex++) {
						if (rupMeanMag[rupturesForSection.get(rupIndex)]>=m
								&& rupMeanMag[rupturesForSection.get(rupIndex)]<m+participationConstraintMagBinSize) 
							numRupsForMagBin.set(numRupsForMagBin.size()-1,
									numRupsForMagBin.get(numRupsForMagBin.size()-1)+1); // numRupsForMagBin(end)++
					}
					if (numRupsForMagBin.get(numRupsForMagBin.size()-1)>0)
						numNonzeroMagBins++;
				}
				
				// Put together A matrix elements: A_avg_rate_per_mag_bin * x - A_rate_for_particular_mag_bin * x = 0
				// Each mag bin (that contains ruptures) for each subsection adds one row to A & d
				int magBinIndex=0;
				for (double m=minMag; m<maxMag; m=m+participationConstraintMagBinSize) {
					if (numRupsForMagBin.get(magBinIndex) > 0) {
						for (int rupIndex=0; rupIndex<rupturesForSection.size(); rupIndex++) {
							// Average rate per magnitude bin for this section
							int col = rupturesForSection.get(rupIndex);
							double val = participationSmoothnessConstraintWt/numNonzeroMagBins;	
							numNonZeroElements++;
							if (rupMeanMag[rupturesForSection.get(rupIndex)]>=m
									&& rupMeanMag[rupturesForSection.get(rupIndex)]
									              <m+participationConstraintMagBinSize) {
								// Subtract off rate for this mag bin (difference between average rate per mag bin
								// & rate for this mag bin is set to 0)
								val -= participationSmoothnessConstraintWt;
							}
							if (QUICK_GETS_SETS)
								A.setQuick(rowIndex, col, val);
							else
								A.set(rowIndex, col, val);
						}
						d[rowIndex] = 0;
						rowIndex++;
					}	
					magBinIndex++;				
				}	
				
			}
			if (D) {
				System.out.println("Adding Participation MFD Constraints took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
				System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
			}
		}
		
		
		// MFD Subsection nucleation MFD constraint
		if (config.getNucleationMFDConstraintWt() > 0.0) {
			double nucleationMFDConstraintWt = config.getNucleationMFDConstraintWt();
			if(D) System.out.println("\nAdding Subsection Nucleation MFD constraints to A matrix ...");
			numNonZeroElements = 0;
			
			// Loop over all subsections
			for (int sect=0; sect<numSections; sect++) {
				
				SectionMFD_constraint sectMFDConstraint = MFDConstraints.get(sect);
				if (sectMFDConstraint == null) continue; // Parent sections with Mmax<6 have no MFD constraint; skip these
				int numMagBins = sectMFDConstraint.getNumMags();
				List<Integer> rupturesForSect = rupSet.getRupturesForSection(sect);
				
				// Loop over MFD constraints for this subsection
				for (int magBin = 0; magBin<numMagBins; magBin++) {
					
					// Only include non-empty magBins in constraint
					if (sectMFDConstraint.getRate(magBin) > 0) {
					
						// Determine which ruptures are in this magBin
						List<Integer> rupturesForMagBin = new ArrayList<Integer>();
						for (int i=0; i<rupturesForSect.size(); i++) {
							double mag = rupSet.getMagForRup(rupturesForSect.get(i));
							if (sectMFDConstraint.isMagInBin(mag, magBin))
								rupturesForMagBin.add(rupturesForSect.get(i));
						}
					
					
						// Loop over ruptures in this subsection-MFD bin
						for (int i=0; i<rupturesForMagBin.size(); i++) {
							int rup  = rupturesForMagBin.get(i);
							double rupArea = rupSet.getAreaForRup(rup);
							double sectArea = rupSet.getAreaForSection(sect);
							if (QUICK_GETS_SETS)
								A.setQuick(rowIndex,rup,nucleationMFDConstraintWt * (sectArea / rupArea) / sectMFDConstraint.getRate(magBin));
							else
								A.set(rowIndex,rup,nucleationMFDConstraintWt * (sectArea / rupArea) / sectMFDConstraint.getRate(magBin));
							numNonZeroElements++;	
						}
						d[rowIndex]=nucleationMFDConstraintWt;
						rowIndex++;
					}
				}
			}
			if (D) {
				System.out.println("Adding Subsection Nucleation MFD Constraints took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
				System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
			}
		}
		
		
		// MFD Smoothing constraint - MFDs spatially smooth along adjacent subsections on a parent section (Laplacian smoothing)
		if (config.getMFDSmoothnessConstraintWt() > 0.0 || config.getMFDSmoothnessConstraintWtForPaleoParents() > 0.0) {  
			double MFDSmoothingConstraintWt = config.getMFDSmoothnessConstraintWt();
			double MFDSmoothingConstraintWtForPaleoParents = config.getMFDSmoothnessConstraintWtForPaleoParents();
			if(D) System.out.println("\nAdding MFD spatial smoothness constraints to A matrix ...");
			numNonZeroElements = 0;
			
			
			// Get list of parent IDs
			Map<Integer, List<FaultSectionPrefData>> parentSectsMap = Maps.newHashMap();
			for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
				Integer parentID = sect.getParentSectionId();
				List<FaultSectionPrefData> parentSects = parentSectsMap.get(parentID);
				if (parentSects == null) {
					parentSects = Lists.newArrayList();
					parentSectsMap.put(parentID, parentSects);
				}
				parentSects.add(sect);
			}
			
			// Get list of parent IDs that have a paleo data point (paleo event rate or paleo mean slip)
			ArrayList<Integer> paleoParents = new ArrayList<Integer>();
			if (config.getPaleoRateConstraintWt() > 0.0) {
				for (int i=0; i<paleoRateConstraints.size(); i++) {
					int paleoParentID = rupSet.getFaultSectionDataList().get(paleoRateConstraints.get(i).getSectionIndex()).getParentSectionId();
					paleoParents.add(paleoParentID);
				}
			}
			if (config.getPaleoSlipConstraintWt() > 0.0) {
				for (int i=0; i<aveSlipConstraints.size(); i++) {
					int paleoParentID = rupSet.getFaultSectionDataList().get(aveSlipConstraints.get(i).getSubSectionIndex()).getParentSectionId();
					paleoParents.add(paleoParentID);
				}
			}
			
			List<HashSet<Integer>> sectRupsHashes = Lists.newArrayList();
			for (int s=0; s<rupSet.getNumSections(); s++)
				sectRupsHashes.add(new HashSet<Integer>(rupSet.getRupturesForSection(s)));

			for (List<FaultSectionPrefData> sectsForParent : parentSectsMap.values()) {		
				
				// Does this parent sect have a paleo constraint?
				boolean parentSectIsPaleo=false;
				int parentID = rupSet.getFaultSectionDataList().get(sectsForParent.get(0).getSectionId()).getParentSectionId();
				if (paleoParents.contains(parentID)) parentSectIsPaleo = true; 
				// Use correct weight for this parent section depending whether it has paleo constraint or not
				double constraintWeight = MFDSmoothingConstraintWt;
				if (parentSectIsPaleo) constraintWeight = MFDSmoothingConstraintWtForPaleoParents;
				if (constraintWeight==0) continue;
				
				// Laplacian smoothing of event rates: r[i+1]-2*r[i]+r[i-1]=0 (minimize curvature of event rates)
				// Don't need to worry about smoothing for subsection pairs at edges b/c they always share the same ruptures (no ruptures have only 1 subsection in a given parent section)
				for (int j=1; j<sectsForParent.size()-2; j++) {
					int sect1 = sectsForParent.get(j-1).getSectionId(); 
					HashSet<Integer> sect1Hash = sectRupsHashes.get(sect1);
					int sect2 = sectsForParent.get(j).getSectionId(); 
					HashSet<Integer> sect2Hash = sectRupsHashes.get(sect2);
					int sect3 = sectsForParent.get(j+1).getSectionId(); 
					HashSet<Integer> sect3Hash = sectRupsHashes.get(sect3);
					
					List<Integer> sect1Rups = Lists.newArrayList();  
					List<Integer> sect2Rups = Lists.newArrayList();
					List<Integer> sect3Rups = Lists.newArrayList();
					
					// only rups that involve sect 1 but not in sect 2
					for (Integer sect1Rup : sect1Hash)
						if (!sect2Hash.contains(sect1Rup))
							sect1Rups.add(sect1Rup);
					// only rups that involve sect 2 but not sect 1, then add in rups that involve sect 2 but not sect 3
					// Apparent double counting is OK, that is the factor of 2 in the center of the Laplacian
					// Think of as: (r[i+1]-*r[i]) + (r[i-1]-r[i])=0 
					for (Integer sect2Rup : sect2Hash)
						if (!sect1Hash.contains(sect2Rup))
							sect2Rups.add(sect2Rup); 
					for (Integer sect2Rup : sect2Hash)
						if (!sect3Hash.contains(sect2Rup))
							sect2Rups.add(sect2Rup); 
					// only rups that involve sect 3 but sect 2
					for (Integer sect3Rup : sect3Hash) {
						if (!sect2Hash.contains(sect3Rup))
							sect3Rups.add(sect3Rup);
					}
					
					// Get section MFD constraint -- we will use the irregular mag binning for the constraint (but not the rates)
					SectionMFD_constraint sectMFDConstraint = MFDConstraints.get(sect2);
					if (sectMFDConstraint == null) continue; // Parent sections with Mmax<6 have no MFD constraint; skip these
					int numMagBins = sectMFDConstraint.getNumMags();
					// Loop over MFD constraints for this subsection
					for (int magBin = 0; magBin<numMagBins; magBin++) {
					
						// Determine which ruptures are in this magBin
						List<Integer> sect1RupsForMagBin = new ArrayList<Integer>();
						for (int i=0; i<sect1Rups.size(); i++) {
							double mag = rupSet.getMagForRup(sect1Rups.get(i));
							if (sectMFDConstraint.isMagInBin(mag, magBin))
								sect1RupsForMagBin.add(sect1Rups.get(i));
						}
						List<Integer> sect2RupsForMagBin = new ArrayList<Integer>();
						for (int i=0; i<sect2Rups.size(); i++) {
							double mag = rupSet.getMagForRup(sect2Rups.get(i));
							if (sectMFDConstraint.isMagInBin(mag, magBin))
								sect2RupsForMagBin.add(sect2Rups.get(i));
						}
						List<Integer> sect3RupsForMagBin = new ArrayList<Integer>();
						for (int i=0; i<sect3Rups.size(); i++) {
							double mag = rupSet.getMagForRup(sect3Rups.get(i));
							if (sectMFDConstraint.isMagInBin(mag, magBin))
								sect3RupsForMagBin.add(sect3Rups.get(i));
						}
						
						// Loop over ruptures in this subsection-MFD bin
						for (int rup: sect1RupsForMagBin) { 
							if (QUICK_GETS_SETS) 
								A.setQuick(rowIndex,rup,constraintWeight); 
							else
								A.set(rowIndex,rup,constraintWeight);
							numNonZeroElements++;
						}
						for (int rup: sect2RupsForMagBin) {
							if (QUICK_GETS_SETS) 
								A.setQuick(rowIndex,rup,-constraintWeight);
							else
								A.set(rowIndex,rup,-constraintWeight);
							numNonZeroElements++;
						}
						for (int rup: sect3RupsForMagBin) {
							if (QUICK_GETS_SETS) 
								A.setQuick(rowIndex,rup,constraintWeight);
							else
								A.set(rowIndex,rup,constraintWeight);
							numNonZeroElements++;
						}
						d[rowIndex]=0;
						rowIndex++;
					}
				}
			}
			if (D) {
				System.out.println("Adding MFD Smoothness Constraints took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
				System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
			}
		}
		
		
		// Constraint solution moment to equal deformation-model moment
		if (config.getMomentConstraintWt() > 0.0) {
			double momentConstraintWt = config.getMomentConstraintWt();
			double totalMomentTarget = rupSet.getTotalReducedMomentRate();
			numNonZeroElements = 0;
			for (int rup=0; rup<numRuptures; rup++)  {
				if (QUICK_GETS_SETS)
					A.setQuick(rowIndex,rup,momentConstraintWt * MagUtils.magToMoment(rupMeanMag[rup]));
				else
					A.set(rowIndex,rup,momentConstraintWt * MagUtils.magToMoment(rupMeanMag[rup]));
				numNonZeroElements++;
			}
			d[rowIndex]=momentConstraintWt * totalMomentTarget;
			rowIndex++;
			if (D) {
				System.out.println("Adding Moment Constraint took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
			}
			System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements);
		}
		
		
		// Constraint rupture-rate for M~6 Parkfield earthquakes
		// The Parkfield eqs are defined as rates of 6, 7, and 8 subsection ruptures in the Parkfield parent section (which has 8 subsections in total)
		// THIS CONSTRAINT WILL NOT WORK IF SUBSECTIONS DRASTICALLY CHANGE IN SIZE OR IF PARENT-SECT-IDS CHANGE!
		if (config.getParkfieldConstraintWt() > 0.0) {
			if(D) System.out.println("\nAdding Parkfield rupture-rate constraints to A matrix ...");
			double ParkfieldConstraintWt = config.getParkfieldConstraintWt();
			double ParkfieldMeanRate = 1.0/25.0; // Bakun et al. (2005)
			
			// Find Parkfield M~6 ruptures
			List<Integer> parkfieldRups = findParkfieldRups(rupSet);
			
			// Put together A, d elements
			numNonZeroElements = 0;
			for (int r=0; r<parkfieldRups.size(); r++)  {
				int rup = parkfieldRups.get(r);
				if (QUICK_GETS_SETS) 
					A.setQuick(rowIndex,rup,ParkfieldConstraintWt);
				else
					A.set(rowIndex,rup,ParkfieldConstraintWt);
				numNonZeroElements++;
			}
			d[rowIndex]=ParkfieldConstraintWt * ParkfieldMeanRate;
			rowIndex++;
			if (D) {
				System.out.println("Adding Parkfield Constraint took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
			}
			System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements+"\n");
		}
		

		// Constrain paleoseismically-visible event rates along parent sections to be smooth
		if (config.getEventRateSmoothnessWt() > 0.0) {
			if(D) System.out.println("\nAdding Event Rate Smoothness Constraint for Each Parent Section ...");
			double eventRateSmoothnessWt = config.getEventRateSmoothnessWt();
			numNonZeroElements = 0;
			
			// Get list of parent IDs
			List<Integer> parentIDs = new ArrayList<Integer>();
			for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
				int parentID = sect.getParentSectionId();
				if (!parentIDs.contains(parentID))
					parentIDs.add(parentID);
			}

			for (int parentID: parentIDs) {		
		
				// Find subsection IDs for given parent section
				ArrayList<Integer> sectsForParent = new ArrayList<Integer>();
				for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
					int sectParentID = sect.getParentSectionId();
					if (sectParentID == parentID)
						sectsForParent.add(sect.getSectionId());
				}
				
				// Constrain the event rate of each neighboring subsection pair (with same parent section) to be approximately equal
				for (int j=0; j<sectsForParent.size()-1; j++) {
					int sect1 = sectsForParent.get(j);
					int sect2 = sectsForParent.get(j+1);
					List<Integer> sect1Rups = Lists.newArrayList(rupSet.getRupturesForSection(sect1));  
					List<Integer> sect2Rups = Lists.newArrayList(rupSet.getRupturesForSection(sect2));
					
					for (int rup: sect1Rups) { 
						double probPaleoVisible = paleoProbabilityModel.getProbPaleoVisible(rupSet, rup, sect1);	
						if (QUICK_GETS_SETS) 
							A.setQuick(rowIndex,rup,probPaleoVisible*eventRateSmoothnessWt); 
						else
							A.set(rowIndex,rup,probPaleoVisible*eventRateSmoothnessWt);
						numNonZeroElements++;
					}
					for (int rup: sect2Rups) {
						double probPaleoVisible = paleoProbabilityModel.getProbPaleoVisible(rupSet, rup, sect2);
						if (QUICK_GETS_SETS) 
							A.setQuick(rowIndex,rup,-probPaleoVisible*eventRateSmoothnessWt);
						else
							A.set(rowIndex,rup,-probPaleoVisible*eventRateSmoothnessWt);
						numNonZeroElements++;
					}
					d[rowIndex] = 0;
					rowIndex++;
				}
			}
			if (D) {
				System.out.println("Adding Event-Rate Smoothness Constraint took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
			}
			System.out.println("Number of nonzero elements in A matrix = "+numNonZeroElements+"\n");
			
		}
		
		// Check that the number of rows of A is correct
		if (numRows != rowIndex) {
			System.out.println("Current rowIndex = "+rowIndex+"; numRows of A = "+numRows);
			throw new IllegalStateException("Number of constraints does not match # of rows of A");
		}
		
		
		
		if (minimumRuptureRates != null) {
			// apply the minimum rupture rates
			if (D) System.out.println("Applying minimum rupture rates.");
			
			// This is the offset data vector: d = d-A*minimumRuptureRates
			A.forEachNonZero(new IntIntDoubleFunction() {
				
				@Override
				public synchronized double apply(int row, int col, double val) {
					d[row] -= val * minimumRuptureRates[col];
					return val;
				}
			});
			if (d_ineq != null) {
				// This is the offset data vector for MFD inequality constraint:
				// d_ineq = d_ineq-A*minimumRuptureRates
				
				A_ineq.forEachNonZero(new IntIntDoubleFunction() {
					
					@Override
					public synchronized double apply(int row, int col, double val) {
						d_ineq[row] -= val * minimumRuptureRates[col];
						return val;
					}
				});
			}
			
			// also adjust the initial solution by the minimum rates
			initial = Arrays.copyOf(initial, numRuptures);
			for (int i=0; i<numRuptures; i++) {
				double adjustedVal = initial[i] - minimumRuptureRates[i];
				if (adjustedVal < 0)
					adjustedVal = 0;
				initial[i] = adjustedVal;
			}
			
			if (D) {
				System.out.println("Applying minimum rupture rates took "+getTimeStr(watch)+".");
				watch.reset();
				watch.start();
			}
		}
		if (D) {
			watch.stop();
			watch_total.stop();
			System.out.println("Generating inputs took "+getTimeStr(watch_total)+".");
		}
	}
	
	public static List<IDPairing> getRupSmoothingPairings(FaultSystemRupSet rupSet) {
		List<IDPairing> pairings = Lists.newArrayList();
		int numRuptures = rupSet.getNumRuptures();
		// This is a list of each rupture as a HashSet for quick contains(...) operations
		List<HashSet<Integer>> rupHashList = Lists.newArrayList();
		// This is a list of the parent sections for each rupture as a HashSet
		List<HashSet<Integer>> rupParentsHashList = Lists.newArrayList();
		// This is a mapping from each unique set of parent sections, to the ruptures which involve all/only those parent sections
		Map<HashSet<Integer>, List<Integer>> parentsToRupsMap = Maps.newHashMap();
		for (int r=0; r<numRuptures; r++) {
			// create the hashSet for the rupture
			rupHashList.add(new HashSet<Integer>(rupSet.getSectionsIndicesForRup(r)));
			// build the hashSet of parents
			HashSet<Integer> parentIDs = new HashSet<Integer>();
			for (FaultSectionPrefData sect : rupSet.getFaultSectionDataForRupture(r))
				parentIDs.add(sect.getParentSectionId());	// this won't have duplicates since it's a hash set
			rupParentsHashList.add(parentIDs);
			// now add this rupture to the list of ruptures that involve this set of parents
			List<Integer> rupsForParents = parentsToRupsMap.get(parentIDs);
			if (rupsForParents == null) {
				rupsForParents = Lists.newArrayList();
				parentsToRupsMap.put(parentIDs, rupsForParents);
			}
			rupsForParents.add(r);
		}
		if (D) System.out.println("Rupture rate smoothing constraint: "+parentsToRupsMap.size()+" unique parent set combinations");

		// Find set of rupture pairs for smoothing
		for(int rup1=0; rup1<numRuptures; rup1++) {
			List<Integer> sects = rupSet.getSectionsIndicesForRup(rup1);
			HashSet<Integer> rup1Parents = rupParentsHashList.get(rup1);
			ruptureLoop:
				for(Integer rup2 : parentsToRupsMap.get(rup1Parents)) { // We only loop over ruptures that involve the same set of parents
					// Only keep pair if rup1 < rup2 (don't need to include pair in each direction)
					if (rup2 <= rup1) // Only keep pair if rup1 < rup2 (don't need to include pair in each direction)
						continue;
					HashSet<Integer> sects2 = rupHashList.get(rup2);
					// Check that ruptures are same size
					if (sects.size() != sects2.size()) continue ruptureLoop;
					// Check that ruptures differ by at most one subsection
					int numSectsDifferent = 0;
					for(int i=0; i<sects.size(); i++) {
						if (!sects2.contains(sects.get(i))) numSectsDifferent++;
						if (numSectsDifferent > 1) continue ruptureLoop;
					}
					// The pair passes!
					pairings.add(new IDPairing(rup1, rup2));
				}
		}
		
		return pairings;
	}
	
	public double[] adjustSolutionForMinimumRates(double[] solution) {
		return adjustSolutionForMinimumRates(solution, minimumRuptureRates);
	}
	
	public static double[] adjustSolutionForMinimumRates(double[] solution, double[] minimumRuptureRates) {
		solution = Arrays.copyOf(solution, solution.length);
		
		if (minimumRuptureRates != null) {
			Preconditions.checkState(minimumRuptureRates.length == solution.length,
					"minimum rates size mismatch!");
			for (int i=0; i<solution.length; i++) {
				solution[i] = solution[i] + minimumRuptureRates[i];
			}
		}
		
		return solution;
	}
	
	/**
	 * This returns the normalized distance along a rupture that a paleoseismic trench
	 * is located (Glenn's x/L).  It is between 0 and 0.5.
	 * This currently puts the trench in the middle of the subsection.
	 * We need this for the UCERF3 probability of detecting a rupture in a trench.
	 * @return
	 */
	public static double getDistanceAlongRupture(
			List<FaultSectionPrefData> sectsInRup, int targetSectIndex) {
		return getDistanceAlongRupture(sectsInRup, targetSectIndex, null);
	}
	
	public static double getDistanceAlongRupture(
			List<FaultSectionPrefData> sectsInRup, int targetSectIndex,
			Map<Integer, Double> traceLengthCache) {
		double distanceAlongRup = 0;
		
		double totalLength = 0;
		double lengthToRup = 0;
		boolean reachConstraintLoc = false;
		
		// Find total length (km) of fault trace and length (km) from one end to the paleo trench location
		for (int i=0; i<sectsInRup.size(); i++) {
			FaultSectionPrefData sect = sectsInRup.get(i);
			int sectIndex = sect.getSectionId();
			Double sectLength = null;
			if (traceLengthCache != null) {
				sectLength = traceLengthCache.get(sectIndex);
				if (sectLength == null) {
					sectLength = sect.getFaultTrace().getTraceLength();
					traceLengthCache.put(sectIndex, sectLength);
				}
			} else {
				sectLength = sect.getFaultTrace().getTraceLength();
			}
			totalLength+=sectLength;
			if (sectIndex == targetSectIndex) {
				reachConstraintLoc = true;
				// We're putting the trench in the middle of the subsection for now
				lengthToRup+=sectLength/2;
			}
			// We haven't yet gotten to the trench subsection so keep adding to lengthToRup
			if (reachConstraintLoc == false)
				lengthToRup+=sectLength;
		}
		
		if (!reachConstraintLoc) // check to make sure we came across the trench subsection in the rupture
			throw new IllegalStateException("Paleo site subsection was not included in rupture subsections");
		
		// Normalized distance along the rainbow (Glenn's x/L) - between 0 and 1
		distanceAlongRup = lengthToRup/totalLength;
		// Adjust to be between 0 and 0.5 (since rainbow is symmetric about 0.5)
		if (distanceAlongRup>0.5)
			distanceAlongRup=1-distanceAlongRup;
		
		return distanceAlongRup;
	}
	
	private static String getTimeStr(Stopwatch watch) {
		return (float) watch.elapsed(TimeUnit.SECONDS) + " seconds";
	}
	
	private static DoubleMatrix2D buildMatrix(Class<? extends DoubleMatrix2D> clazz, int rows, int cols) {
		if (clazz == null || clazz.equals(SparseDoubleMatrix2D.class))
			// default
			return new SparseDoubleMatrix2D(rows, cols);
		else if (clazz.equals(SparseRCDoubleMatrix2D.class))
			return new SparseRCDoubleMatrix2D(rows, cols);
		else if (clazz.equals(SparseCCDoubleMatrix2D.class))
			return new SparseCCDoubleMatrix2D(rows, cols);
		else
			throw new IllegalArgumentException("Unknown matrix type: "+clazz);
	}
	
	/**
	 * Column compress the A matrix
	 */
	public void columnCompress() {
		A = getColumnCompressed(A);
		if (A_ineq != null)
			A_ineq = getColumnCompressed(A_ineq);
	}
	
	private static SparseCCDoubleMatrix2D getColumnCompressed(DoubleMatrix2D mat) {
		if (mat instanceof SparseCCDoubleMatrix2D)
			return (SparseCCDoubleMatrix2D)mat;
		if (mat instanceof SparseRCDoubleMatrix2D)
			return ((SparseRCDoubleMatrix2D)mat).getColumnCompressed();
		if (mat instanceof SparseDoubleMatrix2D)
			return ((SparseDoubleMatrix2D)mat).getColumnCompressed(true);
		throw new RuntimeException("Can't column compress matrix: "+mat);
	}
	
	public void writeZipFile(File file) throws IOException {
		File tempDir = FileUtils.createTempDir();
		writeZipFile(file, FileUtils.createTempDir(), true);
		tempDir.delete();
	}
	
	/**
	 * Writes the inputs to the given zip file, storing the binary files
	 * in the given directory and optionally cleaning up (deleting them)
	 * when done.
	 * 
	 * @param file
	 * @param storeDir
	 * @param cleanup
	 */
	public void writeZipFile(File zipFile, File storeDir, boolean cleanup) throws IOException {
		if(D) System.out.println("Saving to files...");
		ArrayList<String> fileNames = new ArrayList<String>();
		
		fileNames.add("d.bin");			
		MatrixIO.doubleArrayToFile(d, new File(storeDir, "d.bin"));
		if(D) System.out.println("d.bin saved");
		
		fileNames.add("a.bin");			
		MatrixIO.saveSparse(A, new File(storeDir, "a.bin"));
		if(D) System.out.println("a.bin saved");
		
		fileNames.add("initial.bin");	
		MatrixIO.doubleArrayToFile(initial, new File(storeDir, "initial.bin"));
		if(D) System.out.println("initial.bin saved");
		
		if (d_ineq != null) {
			fileNames.add("d_ineq.bin");	
			MatrixIO.doubleArrayToFile(d_ineq, new File(storeDir, "d_ineq.bin"));
			if(D) System.out.println("d_ineq.bin saved");
		}
		
		if (A_ineq != null) {
			fileNames.add("a_ineq.bin");	
			MatrixIO.saveSparse(A_ineq,new File(storeDir, "a_ineq.bin"));
			if(D) System.out.println("a_ineq.bin saved");
		}
		
		if (minimumRuptureRates != null) {
			fileNames.add("minimumRuptureRates.bin");	
			MatrixIO.doubleArrayToFile(minimumRuptureRates,new File(storeDir, "minimumRuptureRates.bin"));
			if(D) System.out.println("minimumRuptureRates.bin saved");
		}
		
		CSVFile<String> rangeCSV = new CSVFile<String>(true);
		for (int i=0; i<rangeEndRows.size(); i++)
			rangeCSV.addLine(rangeEndRows.get(i)+"", rangeNames.get(i));
		fileNames.add("energyRanges.csv");
		rangeCSV.writeToFile(new File(storeDir, "energyRanges.csv"));
		if(D) System.out.println("energyRanges.csv saved");
		
		FileUtils.createZipFile(zipFile.getAbsolutePath(), storeDir.getAbsolutePath(), fileNames);
		if(D) System.out.println("Zip file saved");
		if (cleanup) {
			if(D) System.out.println("Cleaning up");
			for (String fileName : fileNames) {
				new File(storeDir, fileName).delete();
			}
		}
	}

	public FaultSystemRupSet getRupSet() {
		return rupSet;
	}

	public void setRupSet(InversionFaultSystemRupSet rupSet) {
		this.rupSet = rupSet;
	}

	public InversionConfiguration getConfig() {
		return config;
	}

	public List<PaleoRateConstraint> getPaleoRateConstraints() {
		return paleoRateConstraints;
	}

	public double[] getImprobabilityConstraint() {
		return improbabilityConstraint;
	}

	public PaleoProbabilityModel getPaleoProbabilityModel() {
		return paleoProbabilityModel;
	}

	public DoubleMatrix2D getA() {
		return A;
	}

	public DoubleMatrix2D getA_ineq() {
		return A_ineq;
	}

	public double[] getD() {
		return d;
	}

	public double[] getD_ineq() {
		return d_ineq;
	}

	public double[] getInitial() {
		return initial;
	}

	public double[] getMinimumRuptureRates() {
		return minimumRuptureRates;
	}

	public List<Integer> getRangeEndRows() {
		return rangeEndRows;
	}

	public List<String> getRangeNames() {
		return rangeNames;
	}

	public boolean isAPrioriConstraintForZeroRates() {
		return aPrioriConstraintForZeroRates;
	}

	public void setAPrioriConstraintForZeroRates(
			boolean aPrioriConstraintForZeroRates) {
		this.aPrioriConstraintForZeroRates = aPrioriConstraintForZeroRates;
	}
	
	public static List<Integer> findParkfieldRups(FaultSystemRupSet rupSet) {
		int parkfieldParentSectID = 32;
		
		// Find Parkfield M~6 ruptures
		List<Integer> potentialRups = rupSet.getRupturesForParentSection(parkfieldParentSectID);
		List<Integer> parkfieldRups = new ArrayList<Integer>();
		if (potentialRups == null) {
			System.out.println("Warning: parkfield not found...removed?");
			return parkfieldRups;
		}
		rupLoop:
			for (int i=0; i<potentialRups.size(); i++) {
				List<Integer> sects = rupSet.getSectionsIndicesForRup(potentialRups.get(i));
				// Make sure there are 6-8 subsections
				if (sects.size()<6 || sects.size()>8)
					continue rupLoop;
				// Make sure each section in rup is in Parkfield parent section
				for (int s=0; s<sects.size(); s++) {
					int parent = rupSet.getFaultSectionData(sects.get(s)).getParentSectionId();
					if (parent != parkfieldParentSectID)
						continue rupLoop;
				}
				parkfieldRups.add(potentialRups.get(i));
				if (D) System.out.println("Parkfield rup: "+potentialRups.get(i));
			}
		if (D) System.out.println("Number of M~6 Parkfield rups = "+parkfieldRups.size());
		return parkfieldRups;
	}
}