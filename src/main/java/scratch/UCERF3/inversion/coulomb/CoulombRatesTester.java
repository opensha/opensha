package scratch.UCERF3.inversion.coulomb;

import java.util.List;

import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Laugh test filter based on Coulomb Rates
 * 
 * Based on the following e-mail from Tom Parsons 2/28/12:
 * 
 * For example: for a rupture to pass it might have an average P>10%, a minimum P>5%, but if minimum DCFF> 1 bar, it still passes.
 * I just guessed at those numbers; maybe we pass >5%? I think we'd want to experiment to see what gets excluded
 * 
 * @author kevin
 *
 */
public class CoulombRatesTester implements XMLSaveable {
	
	public static final String XML_METADATA_NAME = "CoulombRatesTester";
	
	private boolean BUGGY_MIN_STRESS = false;
	
	public enum TestType {
		/** just test the coulomb values */
		COULOMB_STRESS,
		/** just test the shear values */
		SHEAR_STRESS,
		/** test that both pass */
		BOTH,
		/** test that either pass */
		EITHER;
	}
	
	private double minAverageProb;
	private double minIndividualProb;
	// if the minimum stress value is above this ceiling, it will be included no matter what
	private double minimumStressExclusionCeiling;
	private TestType testType;
	private boolean applyBranchesOnly;
	private boolean allowAnyWay;
	
	public CoulombRatesTester(TestType testType, double minAverageProb, double minIndividualProb,
			double minimumStressExclusionCeiling, boolean applyBranchesOnly, boolean allowAnyWay) {
		this.minAverageProb = minAverageProb;
		this.minIndividualProb = minIndividualProb;
		this.minimumStressExclusionCeiling = minimumStressExclusionCeiling;
		Preconditions.checkNotNull(testType, "Test type must be specified!");
		this.testType = testType;
		this.applyBranchesOnly = applyBranchesOnly;
		this.allowAnyWay = allowAnyWay;
	}
	
	public void setBuggyMinStress(boolean buggyMinStress) {
		if (buggyMinStress)
			System.err.println("WARNING: buggy coulomb min stress exclusion implementation being used.");
		this.BUGGY_MIN_STRESS = buggyMinStress;
	}
	
	public boolean isApplyBranchesOnly() {
		return applyBranchesOnly;
	}
	
	/**
	 * Tests the given rupture both directions for the specified criteria. This will return true if 
	 * the rupture passes in either direction.
	 * 
	 * @param rup
	 * @param rates
	 * @return
	 */
	public boolean doesRupturePass(List<CoulombRatesRecord> forwardRates, List<CoulombRatesRecord> backwardRates) {
		if (forwardRates.isEmpty())
			return true; // return true if no rates to check
		// check simple cases first
		if (testType == TestType.SHEAR_STRESS || testType == TestType.COULOMB_STRESS)
			return doesRupturePass(forwardRates, backwardRates, testType);
		// this means we need to test both!
		// first test coulomb
		boolean coulombPass = doesRupturePass(forwardRates, backwardRates, TestType.COULOMB_STRESS);
		// if coulomb passed and it's an "either" criteria, go ahead and pass
		if (testType == TestType.EITHER && coulombPass)
			return true;
		// if coulomb didn't pass and it's a "both" criteria, go ahead and fail 
		if (testType == TestType.BOTH && !coulombPass)
			return false;
		// getting here means passing depends only on shear passing
		return doesRupturePass(forwardRates, backwardRates, TestType.SHEAR_STRESS);
	}
	
	private boolean doesRupturePass(List<CoulombRatesRecord> forwardRates, List<CoulombRatesRecord> backwardRates, TestType type) {
		if (allowAnyWay)
			return doesRupturePassAnyWay(forwardRates, backwardRates, type);
		return doesRupturePassOneWay(forwardRates, type) || doesRupturePassOneWay(backwardRates, type);
	}
	
	private boolean doesRupturePassAnyWay(List<CoulombRatesRecord> forwardRates, List<CoulombRatesRecord> backwardRates, TestType type) {
//		// this is a temporary hack to allow for always using the better juncture
//		List<CoulombRatesRecord> bestRates = Lists.newArrayList();
//		for (int i=0; i<forwardRates.size(); i++) {
//			CoulombRatesRecord fwd = forwardRates.get(i);
//			CoulombRatesRecord bkw = backwardRates.get((backwardRates.size()-1)-i);
//			if (fwd.getCoulombStressChange()>=minimumStressExclusionCeiling)
//				bestRates.add(fwd);
//			else if (bkw.getCoulombStressChange()>=minimumStressExclusionCeiling)
//				bestRates.add(bkw);
//			else if (fwd.getCoulombStressProbability() > bkw.getCoulombStressProbability())
//				bestRates.add(fwd);
//			else
//				bestRates.add(bkw);
//		}
//		boolean ret = doesRupturePassOneWay(bestRates, type);
//		if (!ret) {
//			// check to make sure this is actually the case, woah
//			boolean trueFail = false;
//			for (int i=0; i<forwardRates.size(); i++) {
//				CoulombRatesRecord fwd = forwardRates.get(i);
//				CoulombRatesRecord bkw = backwardRates.get((backwardRates.size()-1)-i);
//				trueFail = !(fwd.getCoulombStressChange()>=minimumStressExclusionCeiling
//						|| bkw.getCoulombStressChange()>=minimumStressExclusionCeiling
//						|| fwd.getCoulombStressProbability()>=minIndividualProb
//						|| bkw.getCoulombStressProbability()>=minIndividualProb);
//				if (trueFail)
//					break;
//			}
//			Preconditions.checkState(trueFail);
//		}
//		return ret;
		
		
		// starting points can be before the first, in between any junctions, and after the last
		// 'i' here represents the first junction after the start of the rupture
		for (int i=0; i<=forwardRates.size(); i++) {
			List<CoulombRatesRecord> rates = Lists.newArrayList();
			// first add backward rates before index
			for (int j=0; j<i; j++)
				// backwardRates is reversed thus (size()-1) - j
				rates.add(backwardRates.get((backwardRates.size()-1)-j));
			// now add forward rates at or after i
			for (int j=i; j<forwardRates.size(); j++)
				rates.add(forwardRates.get(j));
			
			Preconditions.checkState(rates.size() == forwardRates.size());
			
			if (doesRupturePassOneWay(rates, testType))
				return true;
		}
		
		return false;
	}
	
	/**
	 * Tests if the given rupture passes. Note that this only tests one direction.
	 * @return
	 */
	private boolean doesRupturePassOneWay(List<CoulombRatesRecord> rates, TestType type) {
		
		double minStress = Double.POSITIVE_INFINITY;
		double minProb = Double.POSITIVE_INFINITY;
		double sumProbs = 0;
		
		int pairs = rates.size();
		
		int num = 0;
		for (CoulombRatesRecord record : rates) {
			double stress = getStress(type, record);
			double prob = getProbability(type, record);
			
			// see if the stress change is already above our ceiling, which means 
			// that we can ignore this record
			if (!BUGGY_MIN_STRESS && stress >= minimumStressExclusionCeiling)
				continue;
			
			if (stress < minStress)
				minStress = stress;
			if (prob < minProb)
				minProb = prob;
			sumProbs += prob;
			num++;
		}
		
		if (num == 0)
			// all tests were skipped for minimum exclusion ceiling
			return true;
		
		double avgProb = sumProbs / (double)num;
		
		// see if the minimum stress change is already above our ceiling (which means pass no matter what
		if (minStress > minimumStressExclusionCeiling)
			return true;
		
		return avgProb > minAverageProb && minProb > minIndividualProb;
	}
	
	private static double getStress(TestType type, CoulombRatesRecord record) {
		switch (type) {
		case COULOMB_STRESS:
			return record.getCoulombStressChange();
		case SHEAR_STRESS:
			return record.getShearStressChange();

		default:
			throw new IllegalStateException();
		}
	}
	
	private static double getProbability(TestType type, CoulombRatesRecord record) {
		switch (type) {
		case COULOMB_STRESS:
			return record.getCoulombStressProbability();
		case SHEAR_STRESS:
			return record.getShearStressProbability();

		default:
			throw new IllegalStateException();
		}
	}

	@Override
	public String toString() {
		return "CoulombRatesFilter [minAverageProb=" + minAverageProb
				+ ", minIndividualProb=" + minIndividualProb
				+ ", minimumStressExclusionCeiling="
				+ minimumStressExclusionCeiling + ", testType=" + testType
				+ "]";
	}

	public double getMinAverageProb() {
		return minAverageProb;
	}

	public void setMinAverageProb(double minAverageProb) {
		this.minAverageProb = minAverageProb;
	}

	public double getMinIndividualProb() {
		return minIndividualProb;
	}

	public void setMinIndividualProb(double minIndividualProb) {
		this.minIndividualProb = minIndividualProb;
	}

	public double getMinimumStressExclusionCeiling() {
		return minimumStressExclusionCeiling;
	}

	public void setMinimumStressExclusionCeiling(
			double minimumStressExclusionCeiling) {
		this.minimumStressExclusionCeiling = minimumStressExclusionCeiling;
	}

	public TestType getTestType() {
		return testType;
	}

	public void setTestType(TestType testType) {
		this.testType = testType;
	}

	public void setApplyBranchesOnly(boolean applyBranchesOnly) {
		this.applyBranchesOnly = applyBranchesOnly;
	}

	public boolean isAllowAnyWay() {
		return allowAnyWay;
	}

	public void setAllowAnyWay(boolean allowAnyWay) {
		this.allowAnyWay = allowAnyWay;
	}

	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		el.addAttribute("minAverageProb", minAverageProb+"");
		el.addAttribute("minIndividualProb", minIndividualProb+"");
		el.addAttribute("minimumStressExclusionCeiling", minimumStressExclusionCeiling+"");
		el.addAttribute("testType", testType.name()+"");
		el.addAttribute("applyBranchesOnly", applyBranchesOnly+"");
		el.addAttribute("allowAnyWay", allowAnyWay+"");
		if (BUGGY_MIN_STRESS)
			el.addAttribute("buggyMinStress", "true");
		
		return root;
	}
	
	public static CoulombRatesTester fromXMLMetadata(Element coulombEl) {
		double minAverageProb = Double.parseDouble(coulombEl.attributeValue("minAverageProb"));
		double minIndividualProb = Double.parseDouble(coulombEl.attributeValue("minIndividualProb"));
		double minimumStressExclusionCeiling = Double.parseDouble(coulombEl.attributeValue("minimumStressExclusionCeiling"));
		TestType testType = TestType.valueOf(coulombEl.attributeValue("testType"));
		boolean applyBranchesOnly = Boolean.parseBoolean(coulombEl.attributeValue("applyBranchesOnly"));
		boolean allowAnyWay = Boolean.parseBoolean(coulombEl.attributeValue("allowAnyWay"));
		CoulombRatesTester tester = new CoulombRatesTester(testType, minAverageProb, minIndividualProb,
				minimumStressExclusionCeiling, applyBranchesOnly, allowAnyWay);
		if (coulombEl.attribute("buggyMinStress") != null && Boolean.parseBoolean(coulombEl.attributeValue("buggyMinStress")))
			tester.BUGGY_MIN_STRESS = true;
		return tester;
	}

}
