package org.opensha.sha.imr.attenRelImpl.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.param.impl.BooleanParameter;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.WarningDoubleParameter;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.attenRelImpl.AS_2008_AttenRel;
import org.opensha.sha.imr.param.EqkRuptureParams.AftershockParam;
import org.opensha.sha.imr.param.EqkRuptureParams.DipParam;
import org.opensha.sha.imr.param.EqkRuptureParams.FaultTypeParam;
import org.opensha.sha.imr.param.EqkRuptureParams.MagParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupTopDepthParam;
import org.opensha.sha.imr.param.EqkRuptureParams.RupWidthParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusDistX_OverRupParam;
import org.opensha.sha.imr.param.PropagationEffectParams.DistRupMinusJB_OverRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceRupParameter;
import org.opensha.sha.imr.param.PropagationEffectParams.HangingWallFlagParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;


public class AS_2008_test extends NGATest {

	private AS_2008_AttenRel as_2008 = null;

	private static final String RESULT_SET_PATH = "AttenRelResultSetFiles/NGA_ModelsTestFiles/AS08/";

	private String failMetadata = "";
	private String failLine = "";

	private ArrayList<String> testDataLines;
	public static void main(String[] args) {
		//		junit.swingui.TestRunner.run(AS_2008_test.class);
		AS_2008_test test = new AS_2008_test();
		try {
			test.runDiagnostics();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public AS_2008_test() {
		super(RESULT_SET_PATH);
	}

	public void setUp() {
		super.setUp();
		//create the instance of the CB_2006
		as_2008 = new AS_2008_AttenRel(this);
		as_2008.setParamDefaults();
		//testDataLines = FileUtils.loadFile(CB_2006_RESULTS);
	}

	public void tearDown() {
		super.tearDown();
	}

	private String getOpenSHAParams(AttenuationRelationship attenRel) {
		String str = "";

		str += "OpenSHA params:";
		if (attenRel.getIntensityMeasure().getName().equals(SA_Param.NAME))
			str += "\nSA period = " + attenRel.getParameter(PeriodParam.NAME).getValue();
		else
			str += "\nIM Type = " + attenRel.getIntensityMeasure().getName();
		str += "\nMag = " + attenRel.getParameter(MagParam.NAME).getValue();
		str += "\tRrup = " + attenRel.getParameter(DistanceRupParameter.NAME).getValue();
		str += "\t(Rrup-Rjb)/Rrup = " + attenRel.getParameter(DistRupMinusJB_OverRupParameter.NAME).getValue();
		str += "\nFault Type = " + attenRel.getParameter(FaultTypeParam.NAME).getValue();
		str += "\t(distRup-distX)/distRup = " + attenRel.getParameter(DistRupMinusDistX_OverRupParam.NAME).getValue();
		str += "\tDip = " + attenRel.getParameter(DipParam.NAME).getValue();
		str += "\nDDWidth = " + attenRel.getParameter(RupWidthParam.NAME).getValue();
		str += "\tzTor = " + attenRel.getParameter(RupTopDepthParam.NAME).getValue();
		str += "\tVs30 = " + attenRel.getParameter(Vs30_Param.NAME).getValue();
		str += "\tVs30 flag = " + attenRel.getParameter(Vs30_TypeParam.NAME).getValue();
		str += "\nDepthto1km/sec = " + attenRel.getParameter(DepthTo1pt0kmPerSecParam.NAME).getValue();
		str += "\tHanging Wall Flag: = " + attenRel.getParameter(HangingWallFlagParam.NAME).getValue();
		str += "\n";

		return str;
	}

	@Override
	public double doSingleFileTest(File file) {
		double discrep = 0;

		String fileName = file.getName();

		System.out.println("Testing file " + fileName);

		boolean isMedian = false;
		String testValString = "Std Dev";
		if(fileName.contains("MEDIAN"))  { // test mean
			isMedian = true; 
			testValString = "Mean";
		} else { // test Standard Deviation
			isMedian = false;
			/* set whether we are testing Std dev of geomteric mean or 
			 standard deviation of arbitrary horizontal component */
			if(fileName.contains("SIGTM")) {
				// Std Dev of arbitrary horizontal component
				as_2008.getParameter(FaultTypeParam.NAME).setValue(AS_2008_AttenRel.FLT_TYPE_STRIKE_SLIP);
				testValString = "Std Dev of geometric mean for known faulting";
			} else {
				//Std dev of geomteric mean
				as_2008.getParameter(FaultTypeParam.NAME).setValueAsDefault();
				//					as_2008.getParameter(FaultTypeParam.NAME).setValue(AS_2008_AttenRel.FLT_TYPE_UNKNOWN);
				testValString = "Std dev of geomteric mean for unspecified faulting";
			}
		}
		int index1 = fileName.indexOf(".OUT");
		String fltType = fileName.substring(index1-2, index1);
		fltType.replaceAll("_", "");


		if(fileName.contains("SS.OUT") && !fileName.contains("SIGTU"))
			as_2008.getParameter(FaultTypeParam.NAME).setValue(as_2008.FLT_TYPE_STRIKE_SLIP);
		else if(fileName.contains("RV.OUT"))
			as_2008.getParameter(FaultTypeParam.NAME).setValue(as_2008.FLT_TYPE_REVERSE);
		else if(fileName.contains("NM.OUT"))
			as_2008.getParameter(FaultTypeParam.NAME).setValue(as_2008.FLT_TYPE_NORMAL);
		else 
			//throw new RuntimeException("Unknown Fault Type");
			//				as_2008.getParameter(FaultTypeParam.NAME).setValue(as_2008.FLT_TYPE_UNKNOWN);
			as_2008.getParameter(FaultTypeParam.NAME).setValueAsDefault();

		BooleanParameter hangingWallFlagParam = (BooleanParameter)as_2008.getParameter(HangingWallFlagParam.NAME);
		if(fileName.contains("_FW"))
			hangingWallFlagParam.setValue(false);
		else
			hangingWallFlagParam.setValue(true);

		AftershockParam aftershockParam = (AftershockParam)as_2008.getParameter(AftershockParam.NAME);

		if (fileName.contains("_AS_"))
			aftershockParam.setValue(true);
		else
			aftershockParam.setValue(false);

		if (fileName.contains("SIGEST"))
			as_2008.getParameter(Vs30_TypeParam.NAME).setValue(Vs30_TypeParam.VS30_TYPE_INFERRED);
		else
			as_2008.getParameter(Vs30_TypeParam.NAME).setValue(Vs30_TypeParam.VS30_TYPE_MEASURED);

		try {
			testDataLines = FileUtils.loadFile(file.getAbsolutePath());
			int numLines = testDataLines.size();
			double period[] = this.loadPeriods((String)testDataLines.get(0));
			
			List<String> testLines = testDataLines.subList(1, numLines);
			if (numLines > max_num_tests) {
				System.out.println("Downsampling "+as_2008.getName()+" tests to "+max_num_tests);
				Collections.shuffle(testLines);
				testLines = testLines.subList(0, max_num_tests);
			}
			int count = -1;
			for (String fileLine : testLines) {
				count++;
				StringTokenizer st;
				double mag;
				//((WarningDoublePropagationEffectParameter)as_2008.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(Double.valueOf(rrup));
				double dist_jb;
				double vs30;
				try {
					//System.out.println("Doing "+j+" of "+numLines);
					st = new StringTokenizer(fileLine);
					mag = Double.parseDouble(st.nextToken().trim());
					((WarningDoubleParameter)as_2008.getParameter(MagParam.NAME)).setValueIgnoreWarning(Double.valueOf(mag));

					//Rrup is used for this one
					double rRup = Double.parseDouble(st.nextToken().trim());

					dist_jb = Double.parseDouble(st.nextToken().trim());

					if (dist_jb==9.0){
						dist_jb=10.0;
					} else if (dist_jb==4.5){
						dist_jb=5.0;
					}

					as_2008.getParameter(DistanceRupParameter.NAME).setValue(rRup);
					DistRupMinusJB_OverRupParameter distRupMinusJB_OverRupParam = (DistRupMinusJB_OverRupParameter)as_2008.getParameter(DistRupMinusJB_OverRupParameter.NAME);


					double rx = Double.parseDouble(st.nextToken()); // R(x) ( Horizontal distance from top of rupture perpendicular to fault strike)
					DoubleParameter distRupMinusDistX_OverRupParam = (DoubleParameter)as_2008.getParameter(DistRupMinusDistX_OverRupParam.NAME);

					if (rRup > 0) {
						distRupMinusJB_OverRupParam.setValueIgnoreWarning((rRup-dist_jb)/rRup);
						if(rx >= 0.0) {  // sign determines whether it's on the hanging wall (distX is always >= 0 in distRupMinusDistX_OverRupParam)
							distRupMinusDistX_OverRupParam.setValue((rRup-rx)/rRup);
						}
						else {
							distRupMinusDistX_OverRupParam.setValue((rRup+rx)/rRup);  // switch sign of distX here
						}
					} else {
						distRupMinusJB_OverRupParam.setValueIgnoreWarning(0.0);
						distRupMinusDistX_OverRupParam.setValue(0.0);
					}

					double dip = Double.parseDouble(st.nextToken()); // dip
					as_2008.getParameter(DipParam.NAME).setValue(Double.valueOf(dip));

					double w = Double.parseDouble(st.nextToken()); // W, width of rup plane
					// not sure what i should do here....
					if (w < RupWidthParam.MIN)
						as_2008.getParameter(RupWidthParam.NAME).setValue(Double.valueOf(RupWidthParam.MIN));
					else if (w > RupWidthParam.MAX)
						as_2008.getParameter(RupWidthParam.NAME).setValue(Double.valueOf(RupWidthParam.MAX));
					else
						as_2008.getParameter(RupWidthParam.NAME).setValue(Double.valueOf(w));
					//					as_2008.getParameter(AS_2008_AttenRel.RUP_WIDTH_NAME).setValue(Double.valueOf(AS_2008_AttenRel.RUP_WIDTH_DEFAULT));


					double ztor = Double.parseDouble(st.nextToken()); // Ztor, depth of top
					as_2008.getParameter(RupTopDepthParam.NAME).setValue(Double.valueOf(ztor));

					vs30 = Double.parseDouble(st.nextToken().trim());
					((WarningDoubleParameter)as_2008.getParameter(Vs30_Param.NAME)).setValueIgnoreWarning(Double.valueOf(vs30));

					double zsed = Double.parseDouble(st.nextToken()); // Zsed, sediment/basin depth
					as_2008.getParameter(DepthTo1pt0kmPerSecParam.NAME).setValue(Double.valueOf(zsed));


					as_2008.setIntensityMeasure(SA_Param.NAME);
					int num= period.length;
					double openSHA_Val, tested_Val;
					boolean skipTest = false;
					for(int k=0;k<num;++k){
						as_2008.getParameter(PeriodParam.NAME).setValue(Double.valueOf(period[k]));
						if(isMedian) openSHA_Val = Math.exp(as_2008.getMean());
						else openSHA_Val = as_2008.getStdDev();
						tested_Val = Double.parseDouble(st.nextToken().trim());
						double result = DataUtils.getPercentDiff(openSHA_Val, tested_Val);
						if (result > discrep)
							discrep = result;
						if(result > tolerance){
							String failedResultMetadata = "Results from file "+fileName+" failed for  calculation for " +
							"AS-2008 attenuation with the following parameter settings:\n"+
							"  \tSA at period = "+period[k]+"\n\tMag = "+(float)mag+
							"  rrup = "+(float)rRup+"  rjb = "+(float)dist_jb+"\n\t"+ "FaultType = "+fltType+
							"  rx = "+(float)rx+"  dip = "+(float)dip+"\n\t"+ "w = "+(float)w+
							"  ztor = "+(float)ztor+"  vs30 = "+(float)vs30+"\n\t"+ "zsed = "+(float)zsed+
							//							"\n\tSet distRupMinusJB_OverRupParam = " + as_2008.getParameter(DistRupMinusJB_OverRupParameter.NAME).getValue() + 
							"\n"+
							testValString+" from OpenSHA = "+openSHA_Val+"  should be = "+tested_Val;
							failLine = fileLine;
							failMetadata = "Line: " + fileLine;
							failMetadata += "\nTest number= "+"("+count+"/"+numLines+")"+" failed for "+failedResultMetadata;
							//							System.out.println("OpenSHA Median = "+medianFromOpenSHA+"   Target Median = "+targetMedian);
							failMetadata += "\n" + getOpenSHAParams(as_2008);

							return -1;
						}
					}

					as_2008.setIntensityMeasure(PGA_Param.NAME);
					if(isMedian) openSHA_Val = Math.exp(as_2008.getMean());
					else openSHA_Val = as_2008.getStdDev();
					tested_Val = Double.parseDouble(st.nextToken().trim());
					double result = org.opensha.commons.util.DataUtils.getPercentDiff(openSHA_Val, tested_Val);
					if (result > discrep)
						discrep = result;
					if(result > tolerance){
						String failedResultMetadata = "Results from file "+fileName+" failed for  calculation for " +
						"AS-2008 attenuation with the following parameter settings (PGA):\n"+
						"  \tMag = "+(float)mag+
						"  rrup = "+(float)rRup+"  rjb = "+(float)dist_jb+"\n\t"+ "FaultType = "+fltType+
						"  rx = "+(float)rx+"  dip = "+(float)dip+"\n\t"+ "w = "+(float)w+
						"  ztor = "+(float)ztor+"  vs30 = "+(float)vs30+"\n\t"+ "zsed = "+(float)zsed+
						//						"\n\tSet distRupMinusJB_OverRupParam = " + as_2008.getParameter(DistRupMinusJB_OverRupParameter.NAME).getValue() + 
						"\n"+
						testValString+" from OpenSHA = "+openSHA_Val+"  should be = "+tested_Val;
						failLine = fileLine;
						failMetadata = "Line: " + fileLine;
						failMetadata += "\nTest number= "+"("+count+"/"+numLines+")"+" failed for "+failedResultMetadata;
						//							System.out.println("OpenSHA Median = "+medianFromOpenSHA+"   Target Median = "+targetMedian);
						failMetadata += "\n" + getOpenSHAParams(as_2008);

						return -1;
					};
					as_2008.setIntensityMeasure(PGV_Param.NAME);
					if(isMedian) openSHA_Val = Math.exp(as_2008.getMean());
					else openSHA_Val = as_2008.getStdDev();
					tested_Val = Double.parseDouble(st.nextToken().trim());
					result = org.opensha.commons.util.DataUtils.getPercentDiff(openSHA_Val, tested_Val);
					if (result > discrep)
						discrep = result;
					if(result > tolerance){
						String failedResultMetadata = "Results from file "+fileName+" failed for  calculation for " +
						"AS-2008 attenuation with the following parameter settings (PGV):\n"+
						"  \tMag = "+(float)mag+
						"  rrup = "+(float)rRup+"  rjb = "+(float)dist_jb+"\n\t"+ "FaultType = "+fltType+
						"  rx = "+(float)rx+"  dip = "+(float)dip+"\n\t"+ "w = "+(float)w+
						"  ztor = "+(float)ztor+"  vs30 = "+(float)vs30+"\n\t"+ "zsed = "+(float)zsed+
						//						"\n\tSet distRupMinusJB_OverRupParam = " + as_2008.getParameter(DistRupMinusJB_OverRupParameter.NAME).getValue() + 
						"\n"+
						testValString+" from OpenSHA = "+openSHA_Val+"  should be = "+tested_Val;
						failLine = fileLine;
						failMetadata = "Line: " + fileLine;
						failMetadata += "\nTest number= "+"("+count+"/"+numLines+")"+" failed for "+failedResultMetadata;
						//							System.out.println("OpenSHA Median = "+medianFromOpenSHA+"   Target Median = "+targetMedian);
						failMetadata += "\n" + getOpenSHAParams(as_2008);

						return -1;
					}

				} catch (NumberFormatException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return -1;
				} catch (ConstraintException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return -1;
				} catch (ParameterException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return -1;
				}

			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		} catch (RuntimeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		}
		return discrep;
	}

	public String getLastFailMetadata() {
		return failMetadata;
	}

	public String getLastFailLine() {
		return failLine;
	}
}
