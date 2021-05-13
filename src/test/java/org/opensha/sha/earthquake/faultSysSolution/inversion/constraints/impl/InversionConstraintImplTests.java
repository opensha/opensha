package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.collect.Range;

import cern.colt.list.tdouble.DoubleArrayList;
import cern.colt.list.tint.IntArrayList;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.erf.FSS_ERF_ParamTest;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.inversion.UCERF3InversionInputGenerator;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.SectionMFD_constraint;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

public class InversionConstraintImplTests {
	
	private static InversionFaultSystemRupSet rupSet;
	private static int numRuptures;
	private static int numSections;
	private static Random r = new Random();
	
	private static UCERF3InversionConfiguration config;
	
	private static IncrementalMagFreqDist testMFD;
	
	private static HashSet<Integer> allParents;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		rupSet = FSS_ERF_ParamTest.buildSmallTestRupSet();
		numRuptures = rupSet.getNumRuptures();
		numSections = rupSet.getNumSections();
		
		System.out.println("Test rupSet has "+numRuptures+" rups and "+numSections+" sects");
		System.out.println("Mag range: "+rupSet.getMinMag()+" "+rupSet.getMaxMag());
		
		config = UCERF3InversionConfiguration.forModel(InversionModels.CHAR_CONSTRAINED, rupSet);
		
		testMFD = new GutenbergRichterMagFreqDist(1d, 10d, 5.05, 8.95, 50);
		
		allParents = new HashSet<>();
		for (FaultSection sect : rupSet.getFaultSectionDataList())
			allParents.add(sect.getParentSectionId());
	}

	@Test 
	public void testCreate_SlipRateUncertaintyAdjustedConstraint() {
		
		double[] slipRates = rupSet.getSlipRateForAllSections();
		double[] stdDevs = rupSet.getSlipRateStdDevForAllSections();
		
		int weight = 100;
		int weightScalingOrderOfMagnitude = 2;
		
		SlipRateUncertaintyInversionConstraint constr = new SlipRateUncertaintyInversionConstraint(
				weight, weightScalingOrderOfMagnitude, rupSet, slipRates, stdDevs);
		
		testConstraint(constr);

		//This constraint can produce a CSV for inspection
		assertEquals("Counts inconsistent for CSV data "+constr.getName(), 
				constr.getSubSectionWeightingsCSV().getNumRows(), 
				rupSet.getFaultSectionDataList().size() +1 ); // there's a header row
	}
	
	@Test
	public void testAPriori() {
		double[] aPrioriRates = new double[numRuptures];
		for (int r=0; r<numRuptures; r++)
			aPrioriRates[r] = 0.1d*(r % 10);
		APrioriInversionConstraint constr = new APrioriInversionConstraint(1d, 0d, aPrioriRates);
		
		testConstraint(constr);
		
		constr = new APrioriInversionConstraint(0d, 1d, aPrioriRates);
		
		testConstraint(constr);
		
		constr = new APrioriInversionConstraint(1d, 1d, aPrioriRates);
		
		testConstraint(constr);
	}

	@Test
	public void testMFDEquality() {
		List<MFD_InversionConstraint> eqConstr = config.getMfdEqualityConstraints();
		for (MFD_InversionConstraint mfd : eqConstr)
			mfd.setMagFreqDist(testMFD);
		MFDEqualityInversionConstraint constr = new MFDEqualityInversionConstraint(
				rupSet, 1d, eqConstr, null);
		
		testConstraint(constr);
	}

	@Test
	public void testMFDInquality() {
		List<MFD_InversionConstraint> ineqConstr = config.getMfdInequalityConstraints();
		for (MFD_InversionConstraint mfd : ineqConstr)
			mfd.setMagFreqDist(testMFD);
//		System.out.println("have "+ineqConstr.size()+" ineq constraints");
		MFDInequalityInversionConstraint constr = new MFDInequalityInversionConstraint(
				rupSet, 1d, ineqConstr);
		
		testConstraint(constr);
	}

	@Test
	public void testMFDLaplace() {
		ArrayList<SectionMFD_constraint> constraints =
				FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
		MFDLaplacianSmoothingInversionConstraint constr = new MFDLaplacianSmoothingInversionConstraint(
				rupSet, 1d, 0d, null, constraints);
		
		testConstraint(constr);
		
		constr = new MFDLaplacianSmoothingInversionConstraint(
				rupSet, 0d, 1d, allParents, constraints);
		
		testConstraint(constr);
		
		constr = new MFDLaplacianSmoothingInversionConstraint(
				rupSet, 1d, 1d, allParents, constraints);
		
		testConstraint(constr);
	}

	@Test
	public void testMFDParticSmooth() {
		MFDParticipationSmoothnessInversionConstraint constr =
				new MFDParticipationSmoothnessInversionConstraint(rupSet, 1d, 0.2);
		
		testConstraint(constr);
	}

	@Test
	public void testMFDSubSectNucl() {
		ArrayList<SectionMFD_constraint> constraints =
				FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
		MFDSubSectNuclInversionConstraint constr = new MFDSubSectNuclInversionConstraint(
				rupSet, 1d, constraints);
		
		testConstraint(constr);
	}
	
	@Test
	public void testPaleoRate() throws IOException {
		UCERF3_PaleoProbabilityModel paleoProbModel = UCERF3_PaleoProbabilityModel.load();
		List<PaleoRateConstraint> paleoRateConstraints = new ArrayList<>();
		paleoRateConstraints.add(new PaleoRateConstraint(
				"", null, r.nextInt(numSections), 1d,
				0.5d, 1.5d));
		paleoRateConstraints.add(new PaleoRateConstraint(
				"", null, r.nextInt(numSections), 2d,
				1d, 3d));
		PaleoRateInversionConstraint constr = new PaleoRateInversionConstraint(
				rupSet, 1d, paleoRateConstraints, paleoProbModel);
		
		testConstraint(constr);
	}

	@Test
	public void testPaleoSlip() {
		List<AveSlipConstraint> constraints = new ArrayList<>();
		constraints.add(new AveSlipConstraint(r.nextInt(numSections), "",
				1d, 1.5d, 0.5d, null));
		constraints.add(new AveSlipConstraint(r.nextInt(numSections), "",
				3d, 4d, 2d, null));
		PaleoSlipInversionConstraint constr = new PaleoSlipInversionConstraint(
				rupSet, 1d, constraints, rupSet.getSlipRateForAllSections());
		
		testConstraint(constr);
	}

	@Test
	public void testPaleoVisibleEventRateSmooth() throws IOException {
		UCERF3_PaleoProbabilityModel paleoProbModel = UCERF3_PaleoProbabilityModel.load();
		PaleoVisibleEventRateSmoothnessInversionConstraint constr =
				new PaleoVisibleEventRateSmoothnessInversionConstraint(rupSet, 1d, paleoProbModel);
		
		testConstraint(constr);
	}

	@Test
	public void testParkfield() throws IOException {
		List<Integer> parkfieldRups = UCERF3InversionInputGenerator.findParkfieldRups(rupSet);
		ParkfieldInversionConstraint constr = new ParkfieldInversionConstraint(1d, 1d/25d,
				parkfieldRups);
		
		testConstraint(constr);
	}

	@Test
	public void testMinimization() throws IOException {
		List<Integer> rupIndexes = new ArrayList<>();
		for (int r=0; r<numRuptures; r++)
			if (r % 3 == 0)
				rupIndexes.add(r);
		RupRateMinimizationConstraint constr = new RupRateMinimizationConstraint(1d, rupIndexes);
		
		testConstraint(constr);
	}

	@Test
	public void testRateSmoothing() throws IOException {
		RupRateSmoothingInversionConstraint constr = new RupRateSmoothingInversionConstraint(1d, rupSet);
		
		testConstraint(constr);
	}

	@Test
	public void testSlipRate() throws IOException {
		double[] targetSlipRates = rupSet.getSlipRateForAllSections();
		
		SlipRateInversionConstraint constr = new SlipRateInversionConstraint(
				1d, 1d, SlipRateConstraintWeightingType.BOTH, rupSet, targetSlipRates);
		
		testConstraint(constr);
		
		constr = new SlipRateInversionConstraint(
				1d, 1d, SlipRateConstraintWeightingType.NORMALIZED_BY_SLIP_RATE, rupSet, targetSlipRates);
		
		testConstraint(constr);
		
		constr = new SlipRateInversionConstraint(
				1d, 1d, SlipRateConstraintWeightingType.UNNORMALIZED, rupSet, targetSlipRates);
		
		testConstraint(constr);
	}

	@Test
	public void testTotalMoment() throws IOException {
		TotalMomentInversionConstraint constr = new TotalMomentInversionConstraint(
				rupSet, 1d, rupSet.getTotalReducedMomentRate());
		
		testConstraint(constr);
	}
	
	private void testConstraint(InversionConstraint constraint) {
		constraint.setQuickGetSets(false);
		int numRows = constraint.getNumRows();
		System.out.println(constraint.getName()+" has "+numRows+" rows");
		assertTrue("numRows="+numRows+" for "+constraint.getName(), numRows > 0);
		DoubleMatrix2D A1 = new SparseDoubleMatrix2D(numRows, numRuptures);
		double[] d1 = new double[numRows];
		long count1 = constraint.encode(A1, d1, 0); 
		int offsetBefore = r.nextInt(1000);
		int offsetAfter = r.nextInt(1000);
		Range<Integer> offsetRowRange = Range.closedOpen(offsetBefore, offsetBefore+numRows);
		DoubleMatrix2D A2 = new SparseDoubleMatrix2D(numRows+offsetBefore+offsetAfter, numRuptures);
		double[] d2 = new double[numRows+offsetBefore+offsetAfter];
		long count2 = constraint.encode(A2, d2, offsetBefore);
		assertEquals("Counts inconsistent for multiple encodings of "+constraint.getName(), count1, count2);
		assertTrue("Count="+count1+" for "+constraint.getName(), count1 > 0);
		
		IntArrayList rows1 = new IntArrayList();
		IntArrayList cols1 = new IntArrayList();
		DoubleArrayList vals1 = new DoubleArrayList();
		
		A1.getNonZeros(rows1, cols1, vals1);
		
		assertEquals("Count is wrong for zero-offset "+constraint.getName(), count1, vals1.size());
		
		IntArrayList rows2 = new IntArrayList();
		IntArrayList cols2 = new IntArrayList();
		DoubleArrayList vals2 = new DoubleArrayList();
		for (int r=0; r<rows2.size(); r++) {
			int row2 = rows2.get(r);
			assertTrue("offset wrote outside of bounds: row="+row2+", range="+offsetRowRange,
					offsetRowRange.contains(row2));
		}
		
		A2.getNonZeros(rows2, cols2, vals2);
		
		assertEquals("Count is wrong for offset "+constraint.getName(), count2, vals2.size());
		
		for (int i=0; i<rows1.size(); i++) {
			int row1 = rows1.get(i);
			int col = cols1.get(i);
			double val1 = vals1.get(i);
			assertTrue("zero-offset wrote outside of bounds: row="+row1+", numRows="+numRows, row1 < numRows);
			
			int row2 = row1+offsetBefore;
			double val2 = A2.get(row2, col);
			assertEquals("Value mismatch between offset and non-offset for "+constraint.getName(),
					val1, val2, 1e-15);
		}
	}

}
