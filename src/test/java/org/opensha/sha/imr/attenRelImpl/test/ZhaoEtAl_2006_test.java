package org.opensha.sha.imr.attenRelImpl.test;

import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.impl.DoubleDiscreteParameter;
import org.opensha.commons.param.impl.WarningDoubleParameter;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.ZhaoEtAl_2006_AttenRel;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.imr.param.PropagationEffectParams.AbstractDoublePropEffectParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.PropagationEffectParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.WarningDoublePropagationEffectParameter;
import org.opensha.sha.util.TectonicRegionType;

public class ZhaoEtAl_2006_test {

	private static final double THRESH = 0.06;

	private static final boolean failWhenAbove = true;

	ZhaoEtAl_2006_AttenRel imr;
	WC1994_MagLengthRelationship magLenRel;

	@Before
	public void setUp() {
		imr = new ZhaoEtAl_2006_AttenRel(null);
		imr.setParamDefaults();

		// Calculate magnitude from the fault trace 
		magLenRel = new WC1994_MagLengthRelationship();
	}

	private EqkRupture getRup(double mag, double depth, double aveDip, double lowerSeisDepth,
			double upperSeisDept, double rake) {
		
		FaultTrace ftrace = new FaultTrace("test");
		ftrace.add(new Location(45.00,10.00));
		ftrace.add(new Location(46.00,10.00));
		SimpleFaultData fltDat = new SimpleFaultData(aveDip,upperSeisDept,lowerSeisDepth,ftrace);
		StirlingGriddedSurface fltSurf	= new StirlingGriddedSurface(fltDat,5.0);
		
		// Find hypocenter
		double mLo = 0.0;
		double mLa = 0.0;
		LocationList locl = fltSurf.getEvenlyDiscritizedListOfLocsOnSurface();
		Iterator<Location> iter = locl.iterator();
		double cnt = 0.0;
		while (iter.hasNext()){
			cnt++;
			Location loc = iter.next();
			mLo += loc.getLongitude();
			mLa += loc.getLatitude();
		}

		// Create the hypocenter location
		mLo = mLo/cnt;
		mLa = mLa/cnt;
		
		Location hypo = new Location(mLa,mLo,depth);
		return new EqkRupture(mag,rake,fltSurf,hypo);
	}

	private void doTest(EqkRupture rup, double distance, double depth, String siteType, String trt, String fochMech, String fName) {
		// Set the rupture
		imr.setEqkRupture(rup);
		// Set site conditions 
		imr.getParameter(ZhaoEtAl_2006_AttenRel.SITE_TYPE_NAME).setValue(siteType);
		// Set tectonic region
		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(trt);
		if (fochMech != null)
			imr.getParameter(FaultTypeParam.NAME).setValue(fochMech);
		// Magnitude
		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(rup.getMag()));
		// Distance 
		((AbstractDoublePropEffectParam)imr.getParameter(DistanceRupParameter.NAME))
		.setValueIgnoreWarning(new Double(distance));

		System.out.println("Testing: "+fName);
		URL url = this.getClass().getResource("AttenRelResultSetFiles/ZhaoEtAl_2006/"+fName);

		checkResults(imr, readTable(url));
	}

	private static void checkResults(AttenuationRelationship imr, ArrayList<Double[]> dat) {

		ArrayList<Double> per = getPeriods(imr);

		// Maximum absolute difference
		double maxPDiffGM = 0;
		double maxPDiffSigma = 0;

		// Looping on the spectral ordinates
		for (int i = 0; i < per.size(); i++){	 
			double tmp = per.get(i);
			if (tmp == 0.0){
				imr.setIntensityMeasure(PGA_Param.NAME);
			} else {
				imr.setIntensityMeasure(SA_Param.NAME);
				imr.getParameter(PeriodParam.NAME).setValue(tmp);
			}
			double gmOpenSHA = Math.exp(imr.getMean());
			double sigmaOpenSHA = imr.getStdDev();
			double gmZhao = dat.get(i)[1];
			double sigmaZhao = dat.get(i)[3];

			double pDiffGM = DataUtils.getPercentDiff(gmOpenSHA, gmZhao);
			if (pDiffGM > maxPDiffGM)
				maxPDiffGM = pDiffGM;
			double pDiffSigma = DataUtils.getPercentDiff(sigmaOpenSHA, sigmaZhao);
			if (pDiffSigma > maxPDiffSigma)
				maxPDiffSigma = pDiffSigma;

			String gmDiffStr = "GM differs above thresh: gmOpenSHA="+gmOpenSHA+", gmZhao="+gmZhao
			+", pDiff="+pDiffGM;
			if (pDiffGM > THRESH) {
				if (failWhenAbove)
					fail(gmDiffStr);
			}

			String sigmaDiffStr = "Sigma differs above thresh: sigmaOpenSHA="+sigmaOpenSHA+", sigmaZhao="+sigmaZhao
			+", pDiff="+pDiffSigma;
			if (pDiffSigma > THRESH) {
				if (failWhenAbove)
					fail(sigmaDiffStr);
			}
		}

		System.out.println("max gm pdiff: " + maxPDiffGM);
		System.out.println("max sigma pdiff: " + maxPDiffSigma);
	}

	@Test
	public void testInterfaceRock() {
		double mag = 6.5;
		double distance = 22.3;
		double depth = 20.0;
		String siteType = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		String trt = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		String fochMech = null;
		
		EqkRupture rup = getRup(mag, depth, 90, 15, 25, 90);

		String fName = "zhao_r22.3_m6.5_dep20.0_interf_site1.dat";
		doTest(rup, distance, depth, siteType, trt, fochMech, fName);	
	}

	@Test
	public void testInterfaceHard() {
		double mag = 6.5;
		double distance = 22.3;
		double depth = 20.0;
		String siteType = ZhaoEtAl_2006_AttenRel.SITE_TYPE_HARD_SOIL;
		String trt = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		String fochMech = null;
		
		EqkRupture rup = getRup(mag, depth, 90, 15, 25, 90);

		String fName = "zhao_r22.3_m6.5_dep20.0_interf_site2.dat";
		doTest(rup, distance, depth, siteType, trt, fochMech, fName);	
	}

	@Test
	public void testInterfaceMedium() {
		double mag = 6.5;
		double distance = 22.3;
		double depth = 20.0;
		String siteType = ZhaoEtAl_2006_AttenRel.SITE_TYPE_MEDIUM_SOIL;
		String trt = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		String fochMech = null;
		
		EqkRupture rup = getRup(mag, depth, 90, 15, 25, 90);

		String fName = "zhao_r22.3_m6.5_dep20.0_interf_site3.dat";
		doTest(rup, distance, depth, siteType, trt, fochMech, fName);	
	}

	@Test
	public void testInterfaceSoft() {
		double mag = 6.5;
		double distance = 22.3;
		double depth = 20.0;
		String siteType = ZhaoEtAl_2006_AttenRel.SITE_TYPE_SOFT_SOIL;
		String trt = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		String fochMech = null;
		
		EqkRupture rup = getRup(mag, depth, 90, 15, 25, 90);

		String fName = "zhao_r22.3_m6.5_dep20.0_interf_site4.dat";
		doTest(rup, distance, depth, siteType, trt, fochMech, fName);	
	}

	@Test
	public void testActiveRockReverse() {
		double mag = 6.5;
		double distance = 20.0;
		double depth = 10.0;
		String siteType = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		String trt = TectonicRegionType.ACTIVE_SHALLOW.toString();
		String fochMech = ZhaoEtAl_2006_AttenRel.FLT_FOC_MECH_REVERSE;
		
		EqkRupture rup = getRup(mag, depth, 90, 5, 10, 90);

		String fName = "zhao_r20.0_m6.5_dep10.0_shallow_reverse_site1.dat";
		doTest(rup, distance, depth, siteType, trt, fochMech, fName);	
	}

	@Test
	public void testActiveRockNormal() {
		double mag = 6.5;
		double distance = 20.0;
		double depth = 10.0;
		String siteType = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		String trt = TectonicRegionType.ACTIVE_SHALLOW.toString();
		String fochMech = ZhaoEtAl_2006_AttenRel.FLT_FOC_MECH_NORMAL;

		EqkRupture rup = getRup(mag, depth, 90, 5, 10, 90);
		
		String fName = "zhao_r20.0_m6.5_dep10.0_shallow_normal_site1.dat";
		doTest(rup, distance, depth, siteType, trt, fochMech, fName);
	}

	@Test
	public void testSlabRock() {
		double mag = 6.5;
		double distance = 22.3;
		double depth = 20.0;
		String siteType = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		String trt = TectonicRegionType.SUBDUCTION_SLAB.toString();
		String fochMech = null;

		EqkRupture rup = getRup(mag, depth, 90, 15, 25, 90);
		
		String fName = "zhao_r22.3_m6.5_dep20.0_slab_site1.dat";
		doTest(rup, distance, depth, siteType, trt, fochMech, fName);
	}

	@Test
	public void testInterfaceRockDist30() {
		double mag = 6.5;
		double distance = 30.0;
		double depth = 20.0;
		String siteType = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		String trt = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		String fochMech = null;

		EqkRupture rup = getRup(mag, depth, 90, 15, 25, 90);
		
		String fName = "zhao_r30.0_m6.5_dep20.0_interf_site1.dat";
		doTest(rup, distance, depth, siteType, trt, fochMech, fName);
	}
	
	@Test
	public void testSlabRockDist150() {
		double mag = 7.0;
		double distance = 150;
		double depth = 130;
		String siteType = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		String trt = TectonicRegionType.SUBDUCTION_SLAB.toString();
		String fochMech = null;

		EqkRupture rup = getRup(mag, depth, 90, depth-5.0, depth+5.0, 90);
		
		String fName = "zhao_r150.0_m7.0_dep130.0_slab_site1.dat";
		doTest(rup, distance, depth, siteType, trt, fochMech, fName);
	}

	/**
	 * 
	 * @param flepath
	 * @return
	 */
	public static ArrayList<Double[]> readTable(URL filepath){
		ArrayList<Double[]> dat = new ArrayList<Double[]>();
		String line;
		String[] strArr;
		int cnt = 0;

		// Try to read 'flepath'
		try {
			// Read lines
			for (String currentLine : FileUtils.loadFile(filepath)) {
				cnt++;
				//    				if (cnt != 1) {
				// Split string after cleaning
				line = currentLine.trim(); strArr = line.split("\\s+");
				Double[] lineDat = new Double[strArr.length];
				for (int i = 0; i < strArr.length; i++){
					lineDat[i] = Double.valueOf(strArr[i]).doubleValue();
				}
				dat.add(lineDat);
				//    				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Return the final array list 
		return dat;
	}

	private static ArrayList<Double> getPeriods(ScalarIMR imr) {
		// Get the list of periods available for the selected IMR
		ArrayList<Double> per = new ArrayList<Double>();
		ListIterator<Parameter<?>> it = imr.getSupportedIntensityMeasuresIterator();
		while(it.hasNext()){
			Parameter tempParam = (Parameter)it.next();
			if (tempParam.getName().equalsIgnoreCase(SA_Param.NAME)){
				for (Parameter<?> independentParam : tempParam.getIndependentParameterList()) {
					if (independentParam.getName().equalsIgnoreCase(PeriodParam.NAME)){
						List<Double> saPeriodVector = ((DoubleDiscreteParameter)independentParam).getAllowedDoubles();

						for (int h=0; h<saPeriodVector.size(); h++){
							if (h == 0 && saPeriodVector.get(h)>0.0){
								per.add(0.0);
							}
							per.add(saPeriodVector.get(h));
						}

					}
				}
			}
		}
		return per;
	}

}
