package org.opensha.sha.imr.attenRelImpl.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.impl.DoubleDiscreteParameter;
import org.opensha.commons.param.impl.WarningDoubleParameter;
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

public class Verify_ZhaoEtAl_2006 {

	private static boolean D = false;

	public static void main(String[] args) throws IOException {
		double aveDip;
		double lowerSeisDepth;
		double upperSeisDept;
		double rake;
		String fle;
		String outDir, outFle;
		double dst;
		String tecRegStr;
		double dep;
		String soilStr;
		String focMechStr;

		// General settings
		outDir = "test/org/opensha/sha/imr/attenRelImpl/test/AttenRelResultSetFiles/ZhaoEtAl_2006/";

		// Repository
		String dir = outDir;

		// Instantiate the GMPE
		ZhaoEtAl_2006_AttenRel imr = new ZhaoEtAl_2006_AttenRel(null);   
		if (D) System.out.println("--- Set Defaults");
		imr.setParamDefaults();

		// -----------------------------------------------------------------------------------------
		// Create rupture 
		aveDip = 90.0; lowerSeisDepth = 5.0; upperSeisDept = 10.0; rake = 90.0;
		FaultTrace ftrace = new FaultTrace("test");
		ftrace.add(new Location(45.00,10.00)); ftrace.add(new Location(46.00,10.00));

		// Calculate magnitude from the fault trace 
		WC1994_MagLengthRelationship magLenRel = new WC1994_MagLengthRelationship();
		double mag = magLenRel.getMedianMag(ftrace.getTraceLength(),rake);

		// Create fault surface 
		SimpleFaultData fltDat =  new SimpleFaultData(aveDip,upperSeisDept,lowerSeisDepth,ftrace);
		StirlingGriddedSurface fltSurf = new StirlingGriddedSurface(fltDat,5.0);

		// Find hypocenter
		double mLo = 0.0;
		double mLa = 0.0;
		LocationList locl = fltSurf.getEvenlyDiscritizedListOfLocsOnSurface();
		Iterator<Location> iter = locl.iterator();
		double cnt = 0.0;
		while (iter.hasNext()){
			cnt++;
			mLo += iter.next().getLongitude();
			mLa += iter.next().getLatitude();
		}

		// Create the hypocenter location
		mLo = mLo/cnt;
		mLa = mLa/cnt;
		double depth = 20;
		Location hypo = new Location(mLa,mLo,depth);

		// Create the earthquake rupture 
		EqkRupture rup = new EqkRupture(mag,rake,fltSurf,hypo);
		imr.setEqkRupture(rup);
		if (D) System.out.println("--- Set Earthquake rupture");



		// -----------------------------------------------------------------------------------------
		// Verification 1 - Active shallow
		System.out.printf(" -1-------------------------------------------------------------\n");
		mag = 6.5;
		dst = 20.0;
		dep = 10.0;
		// Set the rupture
		rup = new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
		// Site conditions
		soilStr = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		imr.getParameter(imr.SITE_TYPE_NAME).setValue(soilStr);
		// Tectonic region
		tecRegStr = TectonicRegionType.ACTIVE_SHALLOW.toString();
		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
		// Focal mechanism
		focMechStr = ZhaoEtAl_2006_AttenRel.FLT_FOC_MECH_REVERSE;
		imr.getParameter(FaultTypeParam.NAME).setValue(focMechStr);
		// Magnitude
		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
		// Distance 
		((AbstractDoublePropEffectParam)imr.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(new Double(dst));
		// Verification table
		fle = "zhao_r20.0_m6.5_dep10.0_shallow_reverse_site1.dat";
		// Output file 
		outFle = outDir+ String.format("spcCheck_r%.1f_m%.1f_dep%.1f_shallow_reverse_rock.txt",dst,mag,dep);
		// Message
		System.out.printf(" ---------------------------------------------------------------\n");
		System.out.printf(" Checking spectral accelerations for m=%.1f rRup=%.1f[km] depth=%.1f[km]\n",mag,dst,dep);
		System.out.printf(" Tectonic region: %s\n",tecRegStr.trim());
		System.out.printf(" Soil conditions: %s\n",soilStr.trim());
		System.out.printf(" Focal mechanism: %s\n",focMechStr);
		// Checking ...
		CheckGMPE(imr,ReadTable(dir+fle),outFle);	
		// -----------------------------------------------------------------------------------------

		// -----------------------------------------------------------------------------------------
		// Verification 2 - Active shallow
		System.out.printf(" -2-------------------------------------------------------------\n");
		mag = 6.5;
		dst = 20.0;
		dep = 10.0;
		// Set the rupture
		rup = new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
		// Site conditions
		soilStr = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		imr.getParameter(imr.SITE_TYPE_NAME).setValue(soilStr);
		// Tectonic region
		tecRegStr = TectonicRegionType.ACTIVE_SHALLOW.toString();
		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
		// Focal mechanism
		focMechStr = ZhaoEtAl_2006_AttenRel.FLT_FOC_MECH_NORMAL;
		imr.getParameter(FaultTypeParam.NAME).setValue(focMechStr);
		// Magnitude
		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
		// Distance 
		((AbstractDoublePropEffectParam)imr.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(new Double(dst));
		// Verification table
		fle = "zhao_r20.0_m6.5_dep10.0_shallow_normal_site1.dat";
		// Output file 
		outFle = outDir+ String.format("spcCheck_r%.1f_m%.1f_dep%.1f_shallow_normal_rock.txt",dst,mag,dep);
		// Message
		System.out.printf(" ---------------------------------------------------------------\n");
		System.out.printf(" Checking spectral accelerations for m=%.1f rRup=%.1f[km] depth=%.1f[km]\n",mag,dst,dep);
		System.out.printf(" Tectonic region: %s\n",tecRegStr.trim());
		System.out.printf(" Soil conditions: %s\n",soilStr.trim());
		System.out.printf(" Focal mechanism: %s\n",focMechStr);
		// Checking ...
		CheckGMPE(imr,ReadTable(dir+fle),outFle);	
		// -----------------------------------------------------------------------------------------

		// -----------------------------------------------------------------------------------------
		// Verification 3 - Interface
		System.out.printf(" -3-------------------------------------------------------------\n");
		mag = 6.5;
		dst = 22.3;
		dep = 20.0;
		// Set the rupture
		aveDip = 90.0; lowerSeisDepth = 15.0; upperSeisDept = 25.0; rake = 90.0;
		ftrace = new FaultTrace("test");
		ftrace.add(new Location(45.00,10.00)); ftrace.add(new Location(46.00,10.00));
		fltDat 	= new SimpleFaultData(aveDip,upperSeisDept,lowerSeisDepth,ftrace);
		fltSurf	= new StirlingGriddedSurface(fltDat,5.0);
		rup   	= new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
		// Set site conditions 
		soilStr = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		imr.getParameter(ZhaoEtAl_2006_AttenRel.SITE_TYPE_NAME).setValue(soilStr);
		// Set tectonic region
		tecRegStr = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
		// Magnitude
		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
		// Distance 
		((AbstractDoublePropEffectParam)imr.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(new Double(dst));
		// Verification table
		fle = "zhao_r22.3_m6.5_dep20.0_interf_site1.dat";
		// Output file
		outFle = outDir+ String.format("spcCheck_r%.1f_m%.1f_dep%.1f_interface_rock.txt",dst,mag,dep);
		// Message 
		System.out.printf(" ---------------------------------------------------------------\n");
		System.out.printf(" Checking spectral accelerations for m=%.1f rRup=%.1f[km] depth=%.1f[km]\n",mag,dst,dep);
		System.out.printf(" Tectonic region: %s\n",tecRegStr);
		System.out.printf(" Soil conditions: %s\n",soilStr);
		// Checking ...
		CheckGMPE(imr,ReadTable(dir+fle),outFle);	
		// -----------------------------------------------------------------------------------------

		// -----------------------------------------------------------------------------------------
		// Verification 4
		System.out.printf(" -4-------------------------------------------------------------\n");
		mag = 6.5;
		dst = 22.3;
		dep = 20.0;
		// Set the rupture
		rup = new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
		// Site conditions
		soilStr = ZhaoEtAl_2006_AttenRel.SITE_TYPE_HARD_SOIL;
		imr.getParameter(imr.SITE_TYPE_NAME).setValue(soilStr);
		// Tectonic region
		tecRegStr = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
		// Magnitude
		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
		// Distance 
		((AbstractDoublePropEffectParam)imr.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(new Double(dst));
		// Verification table
		fle = "zhao_r22.3_m6.5_dep20.0_interf_site2.dat";
		// Output file 
		outFle = outDir+ String.format("spcCheck_r%.1f_m%.1f_dep%.1f_interface_hard_soil.txt",dst,mag,dep);
		// Message
		System.out.printf(" ---------------------------------------------------------------\n");
		System.out.printf(" Checking spectral accelerations for m=%.1f rRup=%.1f[km] depth=%.1f[km]\n",mag,dst,dep);
		System.out.printf(" Tectonic region: %s\n",tecRegStr);
		System.out.printf(" Soil conditions: %s\n",soilStr);
		// Checking ...
		CheckGMPE(imr,ReadTable(dir+fle),outFle);	
		// -----------------------------------------------------------------------------------------

		// -----------------------------------------------------------------------------------------
		// Verification 5
		System.out.printf(" -5-------------------------------------------------------------\n");
		mag = 6.5;
		dst = 22.3;
		dep = 20.0;
		// Set the rupture
		rup = new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
		// Site conditions
		soilStr = ZhaoEtAl_2006_AttenRel.SITE_TYPE_MEDIUM_SOIL;
		imr.getParameter(imr.SITE_TYPE_NAME).setValue(soilStr);
		// Tectonic region
		tecRegStr = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
		// Magnitude
		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
		// Distance 
		((AbstractDoublePropEffectParam)imr.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(new Double(dst));
		// Verification table
		fle = "zhao_r22.3_m6.5_dep20.0_interf_site3.dat";
		// Output file 
		outFle = outDir+ String.format("spcCheck_r%.1f_m%.1f_dep%.1f_interface_medium_soil.txt",dst,mag,dep);
		// Message
		System.out.printf(" ---------------------------------------------------------------\n");
		System.out.printf(" Checking spectral accelerations for m=%.1f rRup=%.1f[km] depth=%.1f[km]\n",mag,dst,dep);
		System.out.printf(" Tectonic region: %s\n",tecRegStr);
		System.out.printf(" Soil conditions: %s\n",soilStr);
		// Checking ...
		CheckGMPE(imr,ReadTable(dir+fle),outFle);	
		// -----------------------------------------------------------------------------------------

		// -----------------------------------------------------------------------------------------
		// Verification 6
		System.out.printf(" -6-------------------------------------------------------------\n");
		mag = 6.5;
		dst = 22.3;
		dep = 20.0;
		// Set the rupture
		rup = new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
		// Site conditions
		soilStr = ZhaoEtAl_2006_AttenRel.SITE_TYPE_SOFT_SOIL;
		imr.getParameter(imr.SITE_TYPE_NAME).setValue(soilStr);
		// Tectonic region
		tecRegStr = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
		// Magnitude
		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
		// Distance 
		((AbstractDoublePropEffectParam)imr.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(new Double(dst));
		// Verification table
		fle = "zhao_r22.3_m6.5_dep20.0_interf_site4.dat";
		// Output file 
		outFle = outDir+ String.format("spcCheck_r%.1f_m%.1f_dep%.1f_interface_soft_soil.txt",dst,mag,dep);
		// Message
		System.out.printf(" ---------------------------------------------------------------\n");
		System.out.printf(" Checking spectral accelerations for m=%.1f rRup=%.1f[km] depth=%.1f[km]\n",mag,dst,dep);
		System.out.printf(" Tectonic region: %s\n",tecRegStr);
		System.out.printf(" Soil conditions: %s\n",soilStr.trim());
		// Checking ...
		CheckGMPE(imr,ReadTable(dir+fle),outFle);	
		// -----------------------------------------------------------------------------------------

		// -----------------------------------------------------------------------------------------
		// Verification 7
		System.out.printf(" -7-------------------------------------------------------------\n");
		mag = 6.5;
		dst = 22.3;
		dep = 20.0;
		// Set the rupture
		rup = new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
		// Site conditions
		soilStr = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		imr.getParameter(imr.SITE_TYPE_NAME).setValue(soilStr);
		// Tectonic region
		tecRegStr = TectonicRegionType.SUBDUCTION_SLAB.toString();
		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
		// Magnitude
		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
		// Distance 
		((AbstractDoublePropEffectParam)imr.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(new Double(dst));
		// Verification table
		fle = "zhao_r22.3_m6.5_dep20.0_slab_site1.dat";
		// Output file 
		outFle = outDir+ String.format("spcCheck_r%.1f_m%.1f_dep%.1f_slab_rock.txt",dst,mag,dep);
		// Message
		System.out.printf(" ---------------------------------------------------------------\n");
		System.out.printf(" Checking spectral accelerations for m=%.1f rRup=%.1f[km] depth=%.1f[km]\n",mag,dst,dep);
		System.out.printf(" Tectonic region: %s\n",tecRegStr);
		System.out.printf(" Soil conditions: %s\n",soilStr.trim());
		// Checking ...
		CheckGMPE(imr,ReadTable(dir+fle),outFle);	
		// -----------------------------------------------------------------------------------------        

		//		// -----------------------------------------------------------------------------------------
		//        // Verification 8
		//        System.out.printf(" -8-------------------------------------------------------------\n");
		//		mag = 5.0;
		//		dst = 30.0;
		//		dep = 30.0;
		//		// Set the rupture
		//		rup = new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
		//		// Site conditions
		//		soilStr = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		//        imr.getParameter(imr.SITE_TYPE_NAME).setValue(soilStr);
		//        // Tectonic region
		//		tecRegStr = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		//		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
		//		// Magnitude
		//		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
		//		// Distance 
		//		((WarningDoublePropagationEffectParameter)imr.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(new Double(dst));
		//		// Verification table
		//        fle = "zhao_r30.0_m5.0_dep30_interf_site1.dat";
		//        // Output file 
		//        outFle = outDir+ String.format("spcCheck_r%.1f_m%.1f_dep%.1f_interface_rock.txt",dst,mag,dep);
		//        // Message
		//        System.out.printf(" ---------------------------------------------------------------\n");
		//        System.out.printf(" Checking spectral accelerations for m=%.1f rRup=%.1f[km] depth=%.1f[km]\n",mag,dst,dep);
		//        System.out.printf(" Tectonic region: %s\n",tecRegStr);
		//        System.out.printf(" Soil conditions: %s\n",soilStr.trim());
		//        // Checking ...
		//        CheckGMPE(imr,ReadTable(dir+fle),outFle);	
		//		// -----------------------------------------------------------------------------------------      

		// -----------------------------------------------------------------------------------------
		// Verification 9
		System.out.printf(" -9-------------------------------------------------------------\n");
		mag = 6.5;
		dst = 30.0;
		dep = 20.0;
		// Set the rupture
		aveDip = 90.0; lowerSeisDepth = dep-5.0; upperSeisDept = dep+5.0; rake = 90.0;
		ftrace = new FaultTrace("test");
		ftrace.add(new Location(45.00,10.00)); ftrace.add(new Location(46.00,10.00));
		fltDat 	= new SimpleFaultData(aveDip,upperSeisDept,lowerSeisDepth,ftrace);
		fltSurf	= new StirlingGriddedSurface(fltDat,5.0);
		rup   	= new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
		// Site conditions
		soilStr = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		imr.getParameter(imr.SITE_TYPE_NAME).setValue(soilStr);
		// Tectonic region
		tecRegStr = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
		// Magnitude
		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
		// Distance 
		((AbstractDoublePropEffectParam)imr.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(new Double(dst));
		// Verification table
		fle = "zhao_r30.0_m6.5_dep20.0_interf_site1.dat";
		// Output file 
		outFle = outDir+ String.format("spcCheck_r%.1f_m%.1f_dep%.1f_interface_rock.txt",dst,mag,dep);
		// Message
		System.out.printf(" ---------------------------------------------------------------\n");
		System.out.printf(" Checking spectral accelerations for m=%.1f rRup=%.1f[km] depth=%.1f[km]\n",mag,dst,dep);
		System.out.printf(" Tectonic region: %s\n",tecRegStr);
		System.out.printf(" Soil conditions: %s\n",soilStr.trim());
		// Checking ...
		CheckGMPE(imr,ReadTable(dir+fle),outFle);	
		// ----------------------------------------------------------------------------------------- 

		// -----------------------------------------------------------------------------------------
		// Verification 10
		System.out.printf(" -10------------------------------------------------------------\n");
		mag = 7.0;
		dst = 150.0;
		dep = 130.0;
		// Set the rupture
		aveDip = 90.0; lowerSeisDepth = dep-5.0; upperSeisDept = dep+5.0; rake = 90.0;
		ftrace = new FaultTrace("test");
		ftrace.add(new Location(45.00,10.00)); ftrace.add(new Location(46.00,10.00));
		fltDat 	= new SimpleFaultData(aveDip,upperSeisDept,lowerSeisDepth,ftrace);
		fltSurf	= new StirlingGriddedSurface(fltDat,5.0);
		rup   	= new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
		// Site conditions
		soilStr = ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK;
		imr.getParameter(imr.SITE_TYPE_NAME).setValue(soilStr);
		// Tectonic region
		tecRegStr = TectonicRegionType.SUBDUCTION_SLAB.toString();
		imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
		// Magnitude
		((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
		// Distance 
		((AbstractDoublePropEffectParam)imr.getParameter(DistanceRupParameter.NAME)).setValueIgnoreWarning(new Double(dst));
		// Verification table
		fle = "zhao_r150.0_m7.0_dep130.0_slab_site1.dat";
		// Output file 
		outFle = outDir+ String.format("spcCheck_r%.1f_m%.1f_dep%.1f_slab_rock.txt",dst,mag,dep);
		// Message
		System.out.printf(" ---------------------------------------------------------------\n");
		System.out.printf(" Checking spectral accelerations for m=%.1f rRup=%.1f[km] depth=%.1f[km]\n",mag,dst,dep);
		System.out.printf(" Tectonic region: %s\n",tecRegStr);
		System.out.printf(" Soil conditions: %s\n",soilStr.trim());
		// Checking ...
		CheckGMPE(imr,ReadTable(dir+fle),outFle);	
		// ----------------------------------------------------------------------------------------- 


		// Compute spectrum 
		boolean SPE = false;
		if (SPE) {	
			dst = 30.0; mag = 5.5; dep = 30.0;
			dst = 30.0; mag = 8.5; dep = 60.0;
			rup = new EqkRupture(mag,rake,fltSurf,new Location(mLa,mLo,dep)); imr.setEqkRupture(rup);
			System.out.println("hypocenter:"+rup.getHypocenterLocation());
			// 
			((WarningDoubleParameter)imr.getParameter(MagParam.NAME)).setValueIgnoreWarning(new Double(mag));
			imr.getParameter(FaultTypeParam.NAME).setValue(ZhaoEtAl_2006_AttenRel.FLT_FOC_MECH_REVERSE);
			tecRegStr = TectonicRegionType.SUBDUCTION_INTERFACE.toString();
			imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecRegStr);
			// 
			//			System.out.printf("\nSpectrum - m:%5.2f - dst: %6.2f - Hard Rock - Interface event \n",mag,dst);
			//			imr.getParameter(imr.SITE_TYPE_NAME).setValue(ZhaoEtAl_2006_AttenRel.SITE_TYPE_HARD_ROCK);
			//			outFle = outDir+ String.format("spectrum_HR_m%.1f_r%.0f_interface_hard_rock.txt",mag,dst);
			//			System.out.printf("  Output file: %s \n",outFle);
			//			ComputeSpectrum(imr,dst,tecRegStr,outFle);
			// Compute spectrum 
			System.out.printf("\nSpectrum - m:%5.2f - dst: %6.2f - Rock - Interface event \n",mag,dst);
			imr.getParameter(imr.SITE_TYPE_NAME).setValue(ZhaoEtAl_2006_AttenRel.SITE_TYPE_ROCK);
			outFle = outDir+ String.format("spectrum_Rock_m%.1f_r%.0f_dep%.1f_interface_rock.txt",mag,dst,dep);
			System.out.printf("  Output file: %s \n",outFle);
			ComputeSpectrum(imr,dst,tecRegStr,outFle);
			//			// Compute spectrum 
			//			System.out.printf("\nSpectrum - m:%5.2f - dst: %6.2f - Hard soil - Interface event \n",mag,dst);
			//			imr.getParameter(imr.SITE_TYPE_NAME).setValue(ZhaoEtAl_2006_AttenRel.SITE_TYPE_HARD_SOIL);
			//			outFle = outDir+ String.format("spectrum_HR_m%.1f_r%.0f_interface_hard_soil.txt",mag,dst);
			//			System.out.printf("  Output file: %s \n",outFle);
			//			ComputeSpectrum(imr,dst,tecRegStr,outFle);
			//			// Compute spectrum 
			//			System.out.printf("\nSpectrum - m:%5.2f - dst: %6.2f - Medium soil - Interface event \n",mag,dst);
			//			imr.getParameter(imr.SITE_TYPE_NAME).setValue(ZhaoEtAl_2006_AttenRel.SITE_TYPE_MEDIUM_SOIL);
			//			outFle = outDir+ String.format("spectrum_HR_m%.1f_r%.0f_interface_medium_soil.txt",mag,dst);
			//			System.out.printf("  Output file: %s \n",outFle);
			//			ComputeSpectrum(imr,dst,tecRegStr,outFle);
			//			// Compute spectrum 
			//			System.out.printf("\nSpectrum - m:%5.2f - dst: %6.2f - Soft soil - Interface event \n",mag,dst);
			//			imr.getParameter(imr.SITE_TYPE_NAME).setValue(ZhaoEtAl_2006_AttenRel.SITE_TYPE_SOFT_SOIL);
			//			outFle = outDir+ String.format("spectrum_HR_m%.1f_r%.0f_interface_soft_soil.txt",mag,dst);
			//			System.out.printf("  Output file: %s \n",outFle);
			//			ComputeSpectrum(imr,dst,tecRegStr,outFle);
		}

	}

	/**
	 * 
	 * @param flepath
	 * @return
	 */
	public static ArrayList<Double[]> ReadTable(String flepath){
		ArrayList<Double[]> dat = new ArrayList<Double[]>();
		String currentLine, line;
		String[] strArr;
		int cnt = 0;

		// Try to read 'flepath'
		try {
			// Open buffer
			BufferedReader input =  new BufferedReader(new FileReader(flepath));	
			try {
				// Read lines
				while ((currentLine = input.readLine()) != null) {
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
			} finally {
				input.close();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Return the final array list 
		return dat;
	}

	/**
	 * @return 
	 * @throws IOException 
	 * 
	 */
	private static void CheckGMPE(AttenuationRelationship imr,ArrayList<Double[]> dat, String fleNme) throws IOException {

		ArrayList<Double> per = getPeriods(imr);
		BufferedWriter outFle = new BufferedWriter(new FileWriter(fleNme));

		// Maximum absolute difference
		double maxDffPrc = -1e10; 

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
			//	        System.out.println("stdDev: "+imr.getStdDev());

			double sigma = imr.getStdDev();
			double gmZhao = dat.get(i)[1];
			double dff = Math.abs(gmOpenSHA-gmZhao);
			double dffSig = Math.abs(sigma-dat.get(i)[3]);
			double prc = (gmOpenSHA-gmZhao)/gmZhao*100.0;

			// Computing difference
			if (Math.abs(prc)>maxDffPrc)maxDffPrc=Math.abs(prc);

			// Writing output file

			outFle.write(String.format("%5.2f %7.3f %7.3f  %7.5f %+7.5f sigma %7.5f %7.5f\n",
					tmp,gmOpenSHA,gmZhao,dff,prc,sigma,dffSig));
		}	
		outFle.close();
		// Print maximum difference 
		System.out.printf("  > abs max difference (in percent): %7.4f\n",maxDffPrc);
	}

	/**
	 * 
	 * @param imr
	 * @param dst
	 * @param vs30
	 * @param stressDrop
	 * @throws IOException  
	 */
	public static void ComputeSpectrum(ScalarIMR imr, double dst,
			String tecEnvStr, String fleNme) throws IOException {
		ArrayList<Double> per = getPeriods(imr);
		BufferedWriter outFle = new BufferedWriter(new FileWriter(fleNme));

		for (int i = 0; i < per.size(); i++){	 
			double tmp = per.get(i);
			if (tmp == 0.0){
				imr.setIntensityMeasure(PGA_Param.NAME);
			} else {
				imr.setIntensityMeasure(SA_Param.NAME);
				imr.getParameter(PeriodParam.NAME).setValue(tmp);
			}
			imr.getParameter(TectonicRegionTypeParam.NAME).setValue(tecEnvStr);
			if (D) System.out.println("ccc:"+imr.getParameter(TectonicRegionTypeParam.NAME).getValue());
			double gm = imr.getMean();
			if (D) System.out.println("ccc:"+imr.getParameter(TectonicRegionTypeParam.NAME).getValue());
			outFle.write(String.format("%7.3f %7.3e\n",per.get(i), Math.exp(gm)));
		}	
		outFle.close();
	}

	/**
	 * 
	 * @param imr
	 * @return
	 */
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