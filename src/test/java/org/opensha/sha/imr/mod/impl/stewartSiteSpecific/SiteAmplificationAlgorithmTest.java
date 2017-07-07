package org.opensha.sha.imr.mod.impl.stewartSiteSpecific;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.imr.attenRelImpl.ngaw2.BSSA_2014;
import org.opensha.sha.imr.attenRelImpl.ngaw2.FaultStyle;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_GMM;
import org.opensha.sha.imr.attenRelImpl.ngaw2.NGAW2_Wrappers.BSSA_2014_Wrapper;
import org.opensha.sha.imr.mod.impl.stewartSiteSpecific.StewartSiteSpecificMod.Params;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RakeParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceJBParameter;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * This tests the site amplification algorithm itself, not necessarily it's integration with OpenSHA GMPEs 
 * @author kevin
 *
 */
public class SiteAmplificationAlgorithmTest {
	
	private StewartSiteSpecificMod mod;
	private BSSA_2014_Wrapper gmpeWrap;
	
	private static final double precision_fraction = 0.01; // 1%
	
	private CSVFile<String> csv;
	
	@Before
	public void setUp() throws IOException {
		mod = new StewartSiteSpecificMod();
		gmpeWrap = new BSSA_2014_Wrapper();
		
		csv = CSVFile.readStream(SiteAmplificationAlgorithmTest.class.getResourceAsStream("Test.csv"), false);
	}

	@Test
	public void test() {
		ParameterList refParams = StewartSiteSpecificMod.getDefaultReferenceSiteParams();
		double period = 1d;
		
		gmpeWrap.setParamDefaults();
		gmpeWrap.setIntensityMeasure(SA_Param.NAME);
		SA_Param.setPeriodInSA_Param(gmpeWrap.getIntensityMeasure(), period);
//		gmpeWrap.setIntensityMeasure(PGA_Param.NAME);
		mod.setIMT_IMT(gmpeWrap, gmpeWrap.getIntensityMeasure());
		
		for (int rowIndex=2; rowIndex<csv.getNumRows(); rowIndex++) {
			List<String> line = csv.getLine(rowIndex);
			
			int col = 0;
			double mw = Double.parseDouble(line.get(col++));
			String faultType = line.get(col++);
			double rjb = Double.parseDouble(line.get(col++));
			double refVs30 = Double.parseDouble(line.get(col++));
			double f1 = Double.parseDouble(line.get(col++));
			double f2 = Double.parseDouble(line.get(col++));
			double f3 = Double.parseDouble(line.get(col++));
			double refZ1 = Double.parseDouble(line.get(col++));
			double F = Double.parseDouble(line.get(col++));
			double pgaMedian = Double.parseDouble(line.get(col++));
			double pgaSigma = Double.parseDouble(line.get(col++));
			double pgaTau = Double.parseDouble(line.get(col++));
			double pgaPhi = Double.parseDouble(line.get(col++));
			double sa1Median = Double.parseDouble(line.get(col++));
			double sa1Sigma = Double.parseDouble(line.get(col++));
			double sa1Tau = Double.parseDouble(line.get(col++));
			double sa1Phi = Double.parseDouble(line.get(col++));
			Preconditions.checkState(col == line.size());
			
			String testCaseStr = "Mw="+mw+", FT="+faultType+", rJB="+rjb+", Vs30ref="+refVs30
					+", f1="+f1+", f2="+f2+", f3="+f3+", Z1ref="+refZ1+", F="+F;
			System.out.println("Testing "+testCaseStr);
			
			refParams.setValue(Vs30_Param.NAME, refVs30);
			refParams.setValue(DepthTo1pt0kmPerSecParam.NAME, refZ1);
			mod.setReferenceSiteParams(refParams);
			mod.setIMRParams(gmpeWrap);
			
			gmpeWrap.getParameter(MagParam.NAME).setValue(mw);
			gmpeWrap.getParameter(DistanceJBParameter.NAME).setValue(rjb);
			
			FaultStyle fs;
			Double rake;
			if (faultType.equals("U")) {
				rake = null;
				fs = FaultStyle.UNKNOWN;
			} else if (faultType.equals("R")) {
				rake = 90d;
				fs = FaultStyle.REVERSE;
			} else if (faultType.equals("SS")) {
				rake = 180d;
				fs = FaultStyle.STRIKE_SLIP;
			} else if (faultType.equals("N")) {
				rake = -90d;
				fs = FaultStyle.NORMAL;
			} else throw new IllegalStateException("Unkown fault style: "+faultType);
			gmpeWrap.getParameter(RakeParam.NAME).setValue(rake);
			
			Map<Params, Double> siteAmpParams = Maps.newHashMap();
			siteAmpParams.put(Params.F1, f1);
			siteAmpParams.put(Params.F2, f2);
			siteAmpParams.put(Params.F3, f3);
			siteAmpParams.put(Params.F, F);
			mod.setSiteAmpParams(period, siteAmpParams);
			
			double calcSa1Median = Math.exp(mod.getModMean(gmpeWrap));
			double calcSa1Sigma = mod.getModStdDev(gmpeWrap);
			System.out.println("File ref PGA: "+pgaMedian);
			
			assertEquals(sa1Median, calcSa1Median, sa1Median*precision_fraction);
			assertEquals(sa1Sigma, calcSa1Sigma, sa1Sigma*precision_fraction);
			
			System.out.println("*********");
		}
	}

}
