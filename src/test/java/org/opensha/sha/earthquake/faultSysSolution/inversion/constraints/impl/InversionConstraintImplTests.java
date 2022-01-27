package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.RateCombiner;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.SegmentationModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateSegmentationConstraint.Shaw07JumpDistSegModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.collect.Range;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.MFD_WeightedInversionConstraint;
import scratch.UCERF3.utils.U3SectionMFD_constraint;
import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.U3PaleoRateConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;


public class InversionConstraintImplTests {
	
	private static InversionFaultSystemRupSet rupSet;
	private static int numRuptures;
	private static int numSections;
	private static Random r = new Random();
	
	private static UCERF3InversionConfiguration config;
	
	private static IncrementalMagFreqDist testMFD;
	
	private static HashSet<Integer> allParents;
	
	private static Gson gson;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		rupSet = FSS_ERF_ParamTest.buildSmallTestRupSet();
		numRuptures = rupSet.getNumRuptures();
		numSections = rupSet.getNumSections();
		
		for (int s=0; s<numSections; s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			double stdDev = sect.getOrigSlipRateStdDev();
			if (Double.isNaN(stdDev) || stdDev == 0d)
				sect.setSlipRateStdDev(sect.getOrigAveSlipRate()/3d);
		}
		
		System.out.println("Test rupSet has "+numRuptures+" rups and "+numSections+" sects");
		System.out.println("Mag range: "+rupSet.getMinMag()+" "+rupSet.getMaxMag());
		
		config = UCERF3InversionConfiguration.forModel(InversionModels.CHAR_CONSTRAINED, rupSet,
				rupSet.getFaultModel(), rupSet.getInversionTargetMFDs());
		
		testMFD = new GutenbergRichterMagFreqDist(1d, 10d, 5.05, 8.95, 50);
		
		allParents = new HashSet<>();
		for (FaultSection sect : rupSet.getFaultSectionDataList())
			allParents.add(sect.getParentSectionId());
		gson = new GsonBuilder().registerTypeHierarchyAdapter(InversionConstraint.class,
				new InversionConstraint.Adapter(rupSet)).create();
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
	public void testMFD() {
		List<IncrementalMagFreqDist> mfds = new ArrayList<>();
		for (IncrementalMagFreqDist origMFD : config.getMfdEqualityConstraints()) {
			mfds.add(testMFD.deepClone());
			IncrementalMagFreqDist mfd = testMFD.deepClone();
			mfd.setRegion(origMFD.getRegion());
			mfds.add(mfd);
		}
		
		for (ConstraintWeightingType weight : ConstraintWeightingType.values()) {
			List<IncrementalMagFreqDist> myMFDs = new ArrayList<>(mfds);
			if (weight == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY)
				for (int i=0; i<myMFDs.size(); i++)
					myMFDs.set(i, UncertainIncrMagFreqDist.relStdDev(mfds.get(i), M->0.1));
			MFDInversionConstraint constr = new MFDInversionConstraint(
					rupSet, 1d, false, weight, myMFDs, null);
			
			testConstraint(constr);
			
			constr = new MFDInversionConstraint(
					rupSet, 1d, true, weight, myMFDs, null);
			
			testConstraint(constr);
		}
	}

	@Test
	public void testMFDLaplace() {
		ArrayList<U3SectionMFD_constraint> constraints =
				FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
		MFDLaplacianSmoothingInversionConstraint constr = new MFDLaplacianSmoothingInversionConstraint(
				rupSet, 1d, null, constraints);
		
		testConstraint(constr);
		
		HashSet<Integer> oneParent = new HashSet<>();
		oneParent.add(allParents.iterator().next());
		
		constr = new MFDLaplacianSmoothingInversionConstraint(
				rupSet, 1d, oneParent, constraints);
		
		testConstraint(constr);
	}

	@Test
	public void testLaplace() {
		LaplacianSmoothingInversionConstraint constr = new LaplacianSmoothingInversionConstraint(
				rupSet, 1d);
		
		testConstraint(constr);
		
		HashSet<Integer> oneParent = new HashSet<>();
		oneParent.add(allParents.iterator().next());
		
		constr = new LaplacianSmoothingInversionConstraint(rupSet, 1d, oneParent);
		
		testConstraint(constr);
	}

	@Test
	public void testMFDParticSmooth() {
		MFDParticipationSmoothnessInversionConstraint constr =
				new MFDParticipationSmoothnessInversionConstraint(rupSet, 1d, 0.2);
		
		testConstraint(constr);
	}

	@Test
	public void testU3MFDSubSectNucl() {
		ArrayList<U3SectionMFD_constraint> constraints =
				FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
		U3MFDSubSectNuclInversionConstraint constr = new U3MFDSubSectNuclInversionConstraint(
				rupSet, 1d, constraints);
		
		testConstraint(constr);
	}

	@Test
	public void testMFDSubSect() {
		ArrayList<IncrementalMagFreqDist> constraints = new ArrayList<>();
		
		for (int s=0; s<numSections; s++) {
			double minMag = rupSet.getMinMagForSection(s);
			double maxMag = rupSet.getMaxMagForSection(s);
			GutenbergRichterMagFreqDist gr = new GutenbergRichterMagFreqDist(testMFD.getMinX(), testMFD.size(), testMFD.getDelta(),
					testMFD.getX(testMFD.getClosestXIndex(minMag)), testMFD.getX(testMFD.getClosestXIndex(maxMag)),
					FaultMomentCalc.getMoment(rupSet.getAreaForSection(s), rupSet.getFaultSectionData(s).getOrigAveSlipRate()*1e3), 1d);
			constraints.add(UncertainIncrMagFreqDist.constantRelStdDev(gr, 0.1));
		}
		for (ConstraintWeightingType weightType : ConstraintWeightingType.values()) {
			SubSectMFDInversionConstraint constr = new SubSectMFDInversionConstraint(
					rupSet, 1d, weightType, constraints, true);
			
			testConstraint(constr);
		}
	}
	
	@Test
	public void testPaleoRate() throws IOException {
		UCERF3_PaleoProbabilityModel paleoProbModel = UCERF3_PaleoProbabilityModel.load();
		List<SectMappedUncertainDataConstraint> paleoRateConstraints = new ArrayList<>();
		paleoRateConstraints.add(new SectMappedUncertainDataConstraint("", r.nextInt(numSections), "", null,
				1d, BoundedUncertainty.fromMeanAndBounds(UncertaintyBoundType.TWO_SIGMA, 1d, 0.5d, 1.5d)));
		paleoRateConstraints.add(new SectMappedUncertainDataConstraint("", r.nextInt(numSections), "", null,
				2d, BoundedUncertainty.fromMeanAndBounds(UncertaintyBoundType.TWO_SIGMA, 2d, 1d, 3d)));
		PaleoRateInversionConstraint constr = new PaleoRateInversionConstraint(
				rupSet, 1d, paleoRateConstraints, paleoProbModel);
		
		testConstraint(constr);
	}

	@Test
	public void testPaleoSlip() {
		List<U3AveSlipConstraint> constraints = new ArrayList<>();
		constraints.add(new U3AveSlipConstraint(r.nextInt(numSections), "",
				1d, 1.5d, 0.5d, null));
		constraints.add(new U3AveSlipConstraint(r.nextInt(numSections), "",
				3d, 4d, 2d, null));
		PaleoSlipInversionConstraint constr = new PaleoSlipInversionConstraint(
				rupSet, 1d, constraints, U3AveSlipConstraint.slip_prob_model, false);
		
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
		
		SlipRateInversionConstraint constr = new SlipRateInversionConstraint(
				1d, ConstraintWeightingType.UNNORMALIZED, rupSet);
		
		testConstraint(constr);
		
		constr = new SlipRateInversionConstraint(
				1d, ConstraintWeightingType.NORMALIZED, rupSet);
		
		testConstraint(constr);
		
		constr = new SlipRateInversionConstraint(
				1d, ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, rupSet);
		
		testConstraint(constr);
	}

	@Test
	public void testTotalMoment() throws IOException {
		TotalMomentInversionConstraint constr = new TotalMomentInversionConstraint(
				rupSet, 1d, rupSet.getTotalReducedMomentRate());
		
		testConstraint(constr);
	}

	@Test
	public void testSlipSegmentation() throws IOException {
		SegmentationModel segModel = new SlipRateSegmentationConstraint.Shaw07JumpDistSegModel(1d, 3d);
		SlipRateSegmentationConstraint constr = new SlipRateSegmentationConstraint(
				rupSet, segModel, RateCombiner.MIN, 1d, false, false);
		
		testConstraint(constr);
		
		constr = new SlipRateSegmentationConstraint(rupSet, segModel, RateCombiner.MIN, 1d, true, false);
		
		testConstraint(constr);
		
		constr = new SlipRateSegmentationConstraint(rupSet, segModel, RateCombiner.MIN, 1d, true, false, true, true);
		
		testConstraint(constr);
	}

	@Test
	public void testRelBValue() throws IOException {
		RelativeBValueConstraint constr = new RelativeBValueConstraint(rupSet, 1d, 1d);
		
		testConstraint(constr);
	}

	@Test
	public void testSectTotalRate() throws IOException {
		double[] targetRates = new double[rupSet.getNumSections()];
		double[] targetRateStdDevs = new double[rupSet.getNumSections()];
		for (int s=0; s<targetRates.length; s++) {
			targetRates[s] = Math.random();
			targetRateStdDevs[s] = 0.1*targetRates[s];
		}
		
		for (boolean nucleation : new boolean[] { false, true} ) {
			for (ConstraintWeightingType weightType : ConstraintWeightingType.values()) {
				SectionTotalRateConstraint constr = new SectionTotalRateConstraint(rupSet, 1d, weightType,
						targetRates, targetRateStdDevs, nucleation);
				
				testConstraint(constr);
			}
		}
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
		
		// now test serialization
		String json = gson.toJson(constraint);
		InversionConstraint deserialized = gson.fromJson(json, constraint.getClass());
		String json2 = gson.toJson(deserialized);
//		System.out.println("=========== ORIG");
//		System.out.println(json);
//		System.out.println("=========== DESERIALIZED");
//		System.out.println(json2);
//		System.out.println("===========");
		assertTrue("re-seraialization JSON isn't idential", json.equals(json2));
		
		assertEquals("post-serialization numRows mismatch", numRows, deserialized.getNumRows());
		DoubleMatrix2D A3 = new SparseDoubleMatrix2D(numRows, numRuptures);
		double[] d3 = new double[numRows];
		long count3 = deserialized.encode(A3, d3, 0); 
		
		IntArrayList rows3 = new IntArrayList();
		IntArrayList cols3 = new IntArrayList();
		DoubleArrayList vals3 = new DoubleArrayList();
		
		A3.getNonZeros(rows3, cols3, vals3);

		assertEquals("Count is wrong for deserialized zero-offset "+constraint.getName(), count3, vals3.size());
		assertEquals("Count is wrong for deserialized zero-offset "+constraint.getName(), count3, count1);
		
		for (int i=0; i<rows3.size(); i++) {
			int row = rows3.get(i);
			int col = cols3.get(i);
			double val = vals3.get(i);
			
			double origVal = A1.get(row, col);
			assertEquals("Deserialized A value mismatch for row="+row+", col="+col, origVal, val, 1e-16);
		}
		for (int i=0; i<d3.length; i++) {
			assertEquals("Deserialized d value mismatch for row="+i, d1[i], d3[i], 1e-16);
		}
	}

}
