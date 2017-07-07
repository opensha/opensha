package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.analysis;

import java.io.File;
import java.io.FileOutputStream;
import java.text.DecimalFormat;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncLevelParam;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;

/**
 * This class manages the calculation of a set of hazard curves along a
 * the specified longitudinal profile. It is implemented as a runnable so
 * that multiple profiles may be run simultaneously.
 * 
 * @author pmpowers
 */
public class HazardProfileCalculator implements ParameterChangeWarningListener {

	private final double GRID_SPACING = 0.05;
	private final DoubleParameter VS_30_PARAM;
	private final DepthTo2pt5kmPerSecParam DEPTH_2_5KM_PARAM = new DepthTo2pt5kmPerSecParam(2.0);
	private final DepthTo1pt0kmPerSecParam DEPTH_1_0KM_PARAM = new DepthTo1pt0kmPerSecParam();
	private final Vs30_TypeParam VS_30_TYPE_PARAM = new Vs30_TypeParam(); // inferred by default

	private final DecimalFormat latLonFormat = new DecimalFormat("0.00");
	private final DecimalFormat periodFormat = new DecimalFormat("0.0");
	
	private MeanUCERF2 meanUCERF2;
	private ScalarIMR imr;
	private HazardCurveCalculator hazardCurveCalculator;
	
	/*
	 * Creates a spreadsheet for the specified latitude and IMT; calculates 2% 
	 * and 10% Prob of Exceedance at multiple longitudes using Log-Log 
	 * interpolation.
	 */
	HazardProfileCalculator(
			double lat,
			double minLon,
			double maxLon,
			String imrID,
			double period,
			double vs30,
			ArbitrarilyDiscretizedFunc function,
			String outDir) {
		
		VS_30_PARAM = new DoubleParameter("Vs30", vs30);

		try {

			// init
			String imt = (period == 0) ? "PGA" : "SA";
			String imtString = imt;
			imtString += (period != 0) ? 
				"_" + periodFormat.format(period) + "sec" : "";
			
			System.out.println("Setting up ERF: " + imtString + " at " + lat);
			setupERF();
			System.out.println("Setting up IMR: " + imtString + " at " + lat);
			setupIMR(imrID);

			imr.setIntensityMeasure(imt);
			if (imt != "PGA") {
				imr.getParameter(PeriodParam.NAME).setValue(period);
			}

			HSSFWorkbook wb  = new HSSFWorkbook();
			HSSFSheet sheet = wb.createSheet(); // Sheet for displaying the Total Rates
			sheet.createRow(0);
			int numX_Vals = function.size();
			for(int i=0; i<numX_Vals; ++i)
				sheet.createRow(i+1).createCell((short)0).setCellValue(function.getX(i));
			
			int twoPercentProbRoIndex = numX_Vals+2;
			int tenPercentProbRoIndex = numX_Vals+3;
			
			sheet.createRow(twoPercentProbRoIndex).createCell((short)0).setCellValue("2% PE");
			sheet.createRow(tenPercentProbRoIndex).createCell((short)0).setCellValue("10% PE");
			
			hazardCurveCalculator = new HazardCurveCalculator();
			File outputDir = new File(outDir);
			outputDir.mkdirs();
			String outputFileName = outDir + "/" + imrID + "_" + periodFormat.format(lat) + 
					"_" + imtString + "_vs" + (new Double(vs30)).intValue() + ".xls";
			// Do for First Lat
			double twoPercentProb, tenPercentProb;
			int colIndex=1;
			for(double lon=minLon; lon<=maxLon; lon+=GRID_SPACING, ++colIndex) {
				System.out.println("Doing Site :" + latLonFormat.format(lat) + 
					"," + latLonFormat.format(lon) + " " + imtString);
				
				// ensure that DEPTH_2_5KM_PARAM value iis set from default
				DEPTH_2_5KM_PARAM.setValueAsDefault();
				// set DEPTH_1_0KM_PARAM based on vs30; this could conceivably
				if (vs30 == 760.0) {
					DEPTH_1_0KM_PARAM.setValue(40.0);
				} else if (vs30 == 259.0) {
					DEPTH_1_0KM_PARAM.setValue(330.0);
				}
				Site site = new Site(new Location(lat, lon));
				site.addParameter(VS_30_PARAM);
				site.addParameter(DEPTH_2_5KM_PARAM); // used by CB2008
				site.addParameter(DEPTH_1_0KM_PARAM); // used by CY2008
				site.addParameter(VS_30_TYPE_PARAM);  
				
				// do log of X axis values
				DiscretizedFunc hazFunc = new ArbitrarilyDiscretizedFunc();
				for(int i=0; i<numX_Vals; ++i)
					hazFunc.set(Math.log(function.getX(i)), 1);
				
				// Note here that hazardCurveCalculator accepts the Log of X-Values
				hazardCurveCalculator.getHazardCurve(hazFunc, site, imr, meanUCERF2);
				
				// Unlog the X-Values before doing interpolation. The Y Values we get from hazardCurveCalculator are unmodified
				DiscretizedFunc newFunc = new ArbitrarilyDiscretizedFunc();
				for(int i=0; i<numX_Vals; ++i)
					newFunc.set(function.getX(i), hazFunc.getY(i));
				
				try {
					twoPercentProb = newFunc.getFirstInterpolatedX_inLogXLogYDomain(0.02);
				} catch (InvalidRangeException ire) {
					twoPercentProb = 0.0;
				}
				try {
					tenPercentProb = newFunc.getFirstInterpolatedX_inLogXLogYDomain(0.1);
				} catch (InvalidRangeException ire) {
					tenPercentProb = 0.0;
				}
				sheet.getRow(0).createCell((short)colIndex).setCellValue(latLonFormat.format(lon));
				for(int i=0; i<numX_Vals; ++i)
					sheet.createRow(i+1).createCell((short)colIndex).setCellValue(newFunc.getY(i));

				sheet.createRow(twoPercentProbRoIndex).createCell((short)colIndex).setCellValue(twoPercentProb);
				sheet.createRow(tenPercentProbRoIndex).createCell((short)colIndex).setCellValue(tenPercentProb);
				
			}
			FileOutputStream fileOut = new FileOutputStream(outputFileName);
			wb.write(fileOut);
			fileOut.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
	
	private void setupERF() {
		meanUCERF2 = new MeanUCERF2();
		meanUCERF2.setParameter(MeanUCERF2.RUP_OFFSET_PARAM_NAME, new Double(5.0));
		meanUCERF2.setParameter(MeanUCERF2.CYBERSHAKE_DDW_CORR_PARAM_NAME, false);
		meanUCERF2.setParameter(UCERF2.PROB_MODEL_PARAM_NAME, UCERF2.PROB_MODEL_POISSON);
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_NAME, UCERF2.BACK_SEIS_INCLUDE);
		meanUCERF2.setParameter(UCERF2.BACK_SEIS_RUP_NAME, UCERF2.BACK_SEIS_RUP_CROSSHAIR);
		meanUCERF2.setParameter(UCERF2.FLOATER_TYPE_PARAM_NAME, UCERF2.CENTERED_DOWNDIP_FLOATER);
		meanUCERF2.getTimeSpan().setDuration(50.0);
		meanUCERF2.updateForecast();
	}
	
	private void setupIMR(String id) {
		if (id == "BA") {
			imr = new BA_2008_AttenRel(this);
		} else if (id == "CB") {
			imr = new CB_2008_AttenRel(this);
		} else if (id == "CY"){
			imr = new CY_2008_AttenRel(this);
		}
		imr.setParamDefaults();
		imr.getParameter(SigmaTruncTypeParam.NAME).setValue(SigmaTruncTypeParam.SIGMA_TRUNC_TYPE_1SIDED);
		imr.getParameter(SigmaTruncLevelParam.NAME).setValue(3.0);
	}
	
	@Override
	public void parameterChangeWarning(ParameterChangeWarningEvent e) {
		String S = " : parameterChangeWarning(): ";
		WarningParameter param = e.getWarningParameter();
		param.setValueIgnoreWarning(e.getNewValue());
	}
	
}
