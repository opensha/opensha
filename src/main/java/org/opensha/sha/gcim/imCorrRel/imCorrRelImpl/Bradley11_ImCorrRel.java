package org.opensha.sha.gcim.imCorrRel.imCorrRelImpl;


import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.ASI_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.CAV_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.DSI_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.Ds575_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.Ds595_Param;
import org.opensha.sha.gcim.imr.param.IntensityMeasureParams.SI_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.DampingParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodInterpolatedParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_InterpolatedParam;
import org.opensha.sha.imr.param.OtherParams.TectonicRegionTypeParam;
import org.opensha.sha.util.TectonicRegionType;

/**
 * <b>Title:</b>Bradley11_ImCorrRel<br>
 *
 * <b>Description:</b>  This implements the Bradley 2011 correlation
 * relationships between: 
 * (1) SA-PGA; (2) SA-SI; (3) SA-ASI; (4) PGA-SI; (5) PGA-ASI; (6) SI-ASI; 
 * (7) PGV-PGA; (8) PGV-SI; (9) PGV-ASI; (10) PGV-SA;
 * (11) DSI-PGA; (12) DSI-PGV; (13) DSI-ASI; (14) DSI-SI; (15) DSI-SA;
 * (16) CAV-PGA; (17) CAV-PGV; (18) CAV-ASI; (19) CAV-SI; (20) CAV-DSI; (21) CAV-SA
 * (22) Ds575-PGA; (23) Ds575-PGV; (24) Ds575-ASI; (25) Ds575-SI; (26) Ds575-DSI; (27) Ds575-CAV; (28) Ds575-SA;
 * (29) Ds595-PGA; (30) Ds595-PGV; (31) Ds595-ASI; (32) Ds595-SI; (33) Ds595-DSI; (34) Ds595-CAV; (35) Ds595-SA; (36) Ds595-Ds575;
 * See: Bradley BA (2011) Empirical Correlation between velocity and acceleration 
 * spectrum intensities, spectral and peak ground acceleration intensity measures 
 * from shallow crustal earthquakes (in prep) <p>
 * 
 * * See: Bradley BA (2011) Inclusion of PGV in ground motion selection... (in prep) <p>
 * 
 * * See: Bradley BA (2011) DSI... (in prep) <p>
 * 
 * * See: Bradley BA (2011) CAV... (in prep) <p>
 * 
 * * See: Bradley BA (2011) Ds... (in prep) <p>
 *
 * @author Brendon Bradley
 * @version 1.2 12 October 2010 - implemented correlations for DSI, CAV, and Ds with PGA, PGV, ASI, SI, SA
 * 			1.1 1 October 2010 - implemented correlations with PGV and PGA, SA, SI, ASI
 * 			1.0 1 JUly 2010 - implemented SA, PGA, SI, ASI correlations
 * 
 * 
 * verified against the matlab code developed based on the above reference
 */

public class Bradley11_ImCorrRel extends ImCorrelationRelationship {

    final static String C = "Bradley11_ImCorrRel";
    public final static String NAME = "Bradley (2011)";
    public final static String SHORT_NAME = "Bradley2011";
    private static final long serialVersionUID = 1234567890987654353L;
    
    public final static String TRT_ACTIVE_SHALLOW = TectonicRegionType.ACTIVE_SHALLOW.toString();
    
    private double t_min = 0.01, t_max = 10; //min and max periods
    
    /**
     * no-argument constructor.
     */
    public Bradley11_ImCorrRel() {

    	super();
        
    	initOtherParams();
      	initSupportedIntensityMeasureParams();
      	
      	this.ti = Double.NaN;
      	
    }
    
    /**
     * Computes the correlation coefficient between SI and PGA
     * @return pearson correlation coefficient between lnSI and lnPGA
     */
    public double getImCorrelation(){
    	
    	//(1) The SA and PGA correlation
    	if ((imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==PGA_Param.NAME)||
        		(imi.getName()==PGA_Param.NAME&&imj.getName()==SA_InterpolatedParam.NAME)) {
    		
    		//coefficients
    	    double[] a = {    1,  0.97};
    	    double[] b = {0.895,  0.25};
    	    double[] c = { 0.06,  0.80};
    	    double[] d = {  1.6,   0.8};
    	    double[] e = { 0.20,    10};
    	    
    		if (imi.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		else if (imj.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		for (int i=0; i<a.length; i++) {
    			if (ti <=e[i] ) {
    				return  (a[i]+b[i])/2-(a[i]-b[i])/2*Math.tanh(d[i]*Math.log(ti/c[i]));
    			}
    		}
    		return  Double.NaN;
    	//The standard deviation in the transformed z value is not presently considered	
    	}
    	
    	// (2) The SA and SI correlation
    	if ((imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==SI_Param.NAME)||
        		(imi.getName()==SI_Param.NAME&&imj.getName()==SA_InterpolatedParam.NAME)) {
    		
    		//coefficients
    	    double[] a = { 0.60,  0.38,  0.95};
    	    double[] b = { 0.38,  0.94,  0.68};
    	    double[] c = {0.045,  0.33,  3.10};
    	    double[] d = {  1.5,   1.4,   1.6};
    	    double[] e = {  0.1,   1.4,    10};
    	    
    		if (imi.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		else if (imj.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		for (int i=0; i<a.length; i++) {
    			if (ti <=e[i] ) {
    				return  (a[i]+b[i])/2-(a[i]-b[i])/2*Math.tanh(d[i]*Math.log(ti/c[i]));
    			}
    		}
    		return  Double.NaN;
    	//The standard deviation in the transformed z value is not presently considered	
    	}
    			
    	//(3) The SA and ASI correlation
    	else if ((imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==ASI_Param.NAME)||
        		(imi.getName()==ASI_Param.NAME&&imj.getName()==SA_InterpolatedParam.NAME)) {
    		
    		//coefficients
    	    double[] a = {0.927, 0.823, 1.05};
    	    double[] b = {0.823, 0.962, 0.29};
    	    double[] c = { 0.04,  0.14, 0.80};
    	    double[] d = {  1.8,   2.2,  1.0};
    	    double[] e = {0.075,   0.3,   10};
    	    
    		if (imi.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		else if (imj.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		for (int i=0; i<a.length; i++) {
    			if (ti <=e[i] ) {
    				return  (a[i]+b[i])/2-(a[i]-b[i])/2*Math.tanh(d[i]*Math.log(ti/c[i]));
    			}
    		}
    		return  Double.NaN;
    	//The standard deviation in the transformed z value is not presently considered
    	}
    	
    	// (4) The PGA and SI correlation
    	if ((imi.getName()==PGA_Param.NAME&&imj.getName()==SI_Param.NAME)||
        		(imi.getName()==SI_Param.NAME&&imj.getName()==PGA_Param.NAME)) {
    		return 0.599;
    		//The standard deviation in the transformed z value of 0.066 is not presently considered
    	}
    	
    	// (5) The PGA and ASI correlation
    	else if ((imi.getName()==ASI_Param.NAME&&imj.getName()==PGA_Param.NAME)||
        		(imi.getName()==PGA_Param.NAME&&imj.getName()==ASI_Param.NAME)) {
    		return 0.928;
        	//The standard deviation in the transformed z value of 0.058 is not presently considered
    	}
    	
    	// (6) The SI and ASI correlation
    	else if ((imi.getName()==ASI_Param.NAME&&imj.getName()==SI_Param.NAME)||
        		(imi.getName()==SI_Param.NAME&&imj.getName()==ASI_Param.NAME)) {
    		return 0.641;
        	//The standard deviation in the transformed z value of 0.051 is not presently considered
    	}
    	
    	// (7) The PGV and PGA correlation
    	else if ((imi.getName()==PGV_Param.NAME&&imj.getName()==PGA_Param.NAME)||
        		(imi.getName()==PGA_Param.NAME&&imj.getName()==PGV_Param.NAME)) {
    		return 0.733;
        	//The standard deviation in the transformed z value of 0.037 is not presently considered
    	}
    	
    	// (8) The PGV and SI correlation
    	else if ((imi.getName()==PGV_Param.NAME&&imj.getName()==SI_Param.NAME)||
        		(imi.getName()==SI_Param.NAME&&imj.getName()==PGV_Param.NAME)) {
    		return 0.886;
        	//The standard deviation in the transformed z value of 0.059 is not presently considered
    	}
    	
    	// (9) The PGV and ASI correlation
    	else if ((imi.getName()==PGV_Param.NAME&&imj.getName()==ASI_Param.NAME)||
        		(imi.getName()==ASI_Param.NAME&&imj.getName()==PGV_Param.NAME)) {
    		return 0.730;
        	//The standard deviation in the transformed z value of 0.044 is not presently considered
    	}
    	
    	//(10) The PGV and SA correlation
    	else if ((imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==PGV_Param.NAME)||
        		(imi.getName()==PGV_Param.NAME&&imj.getName()==SA_InterpolatedParam.NAME)) {
    		
    		//coefficients
    	    double[] a = { 0.73, 0.54, 0.80, 0.76};
    	    double[] b = { 0.54, 0.81, 0.76,  0.7};
    	    double[] c = {0.045, 0.28,  1.1,  5.0};
    	    double[] d = { 1.8,   1.5,  3.0,  3.2};
    	    double[] e = { 0.1,  0.75,  2.5, 10.0};
    	    
    		if (imi.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		else if (imj.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		for (int i=0; i<a.length; i++) {
    			if (ti <=e[i] ) {
    				return  (a[i]+b[i])/2-(a[i]-b[i])/2*Math.tanh(d[i]*Math.log(ti/c[i]));
    			}
    		}
    		return  Double.NaN;
    	//The standard deviation in the transformed z value is not presently considered
    	}
    	
    	//(11) The DSI and PGA correlation
    	else if ((imi.getName()==DSI_Param.NAME&&imj.getName()==PGA_Param.NAME)||
        		(imi.getName()==PGA_Param.NAME&&imj.getName()==DSI_Param.NAME)) {
    		return 0.395;
        	//The standard deviation in the transformed z value of 0.049 is not presently considered
    	}
    	
  	  	//(12) The DSI and PGV correlation
    	else if ((imi.getName()==DSI_Param.NAME&&imj.getName()==PGV_Param.NAME)||
        		(imi.getName()==PGV_Param.NAME&&imj.getName()==DSI_Param.NAME)) {
    		return 0.800;
        	//The standard deviation in the transformed z value of 0.062 is not presently considered
    	}
    	
  	  	//(13) The DSI and ASI correlation
    	else if ((imi.getName()==DSI_Param.NAME&&imj.getName()==ASI_Param.NAME)||
        		(imi.getName()==ASI_Param.NAME&&imj.getName()==DSI_Param.NAME)) {
    		return 0.376;
        	//The standard deviation in the transformed z value of 0.053 is not presently considered
    	}
    	
  	  	//(14) The DSI and SI correlation
    	else if ((imi.getName()==DSI_Param.NAME&&imj.getName()==SI_Param.NAME)||
        		(imi.getName()==SI_Param.NAME&&imj.getName()==DSI_Param.NAME)) {
    		return 0.782;
        	//The standard deviation in the transformed z value of 0.045 is not presently considered
    	}
    	
  	  	//(15) The DSI and SA correlation
    	else if ((imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==DSI_Param.NAME)||
        		(imi.getName()==DSI_Param.NAME&&imj.getName()==SA_InterpolatedParam.NAME)) {
    		
    		//coefficients
    	    double[] a = { 0.39, 0.19, 0.98};
    	    double[] b = {0.265,  1.2, 0.82};
    	    double[] c = { 0.04,  1.2,  6.1};
    	    double[] d = {  1.8,  0.6,  3.0};
    	    double[] e = { 0.15,  3.4, 10.0};
    	    
    		if (imi.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		else if (imj.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		for (int i=0; i<a.length; i++) {
    			if (ti <=e[i] ) {
    				return  (a[i]+b[i])/2-(a[i]-b[i])/2*Math.tanh(d[i]*Math.log(ti/c[i]));
    			}
    		}
    		return  Double.NaN;
    	//The standard deviation in the transformed z value is not presently considered
    	}
    	
    	//(16) The CAV and PGA correlation
    	else if ((imi.getName()==CAV_Param.NAME&&imj.getName()==PGA_Param.NAME)||
        		(imi.getName()==PGA_Param.NAME&&imj.getName()==CAV_Param.NAME)) {
    		return 0.700;
        	//The standard deviation in the transformed z value of 0.055 is not presently considered
    	}
    	
  	  	//(17) The CAV and PGV correlation
    	else if ((imi.getName()==CAV_Param.NAME&&imj.getName()==PGV_Param.NAME)||
        		(imi.getName()==PGV_Param.NAME&&imj.getName()==CAV_Param.NAME)) {
    		return 0.691;
        	//The standard deviation in the transformed z value of 0.043 is not presently considered
    	}
    	
  	  	//(18) The CAV and ASI correlation
    	else if ((imi.getName()==CAV_Param.NAME&&imj.getName()==ASI_Param.NAME)||
        		(imi.getName()==ASI_Param.NAME&&imj.getName()==CAV_Param.NAME)) {
    		return 0.703;
        	//The standard deviation in the transformed z value of 0.052 is not presently considered
    	}
    	
  	  	//(19) The CAV and SI correlation
    	else if ((imi.getName()==CAV_Param.NAME&&imj.getName()==SI_Param.NAME)||
        		(imi.getName()==SI_Param.NAME&&imj.getName()==CAV_Param.NAME)) {
    		return 0.681;
        	//The standard deviation in the transformed z value of 0.044 is not presently considered
    	}
    	
    	//(20) The CAV and DSI correlation
    	else if ((imi.getName()==CAV_Param.NAME&&imj.getName()==DSI_Param.NAME)||
        		(imi.getName()==DSI_Param.NAME&&imj.getName()==CAV_Param.NAME)) {
    		return 0.565;
        	//The standard deviation in the transformed z value of 0.043 is not presently considered
    	}
    	
  	  	//(21) The CAV and SA correlation
    	else if ((imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==CAV_Param.NAME)||
        		(imi.getName()==CAV_Param.NAME&&imj.getName()==SA_InterpolatedParam.NAME)) {
    		
    		//coefficients
    		double[] a= {  0.7, 0.635, 0.525};
    		double[] b= {0.635, 0.525, 0.39};
    		double[] c= {0.043,  0.95,  6.2};
    		double[] d= {  2.5,   3.0,  4.0};
    		double[] e= { 0.20,   3.0, 10.0};
    	    
    		if (imi.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		else if (imj.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		for (int i=1; i<a.length; i++) {
    			if (ti <=e[i] ) {
    				return  (a[i]+b[i])/2-(a[i]-b[i])/2*Math.tanh(d[i]*Math.log(ti/c[i]));
    			}
    		}
    		return Double.NaN;
    	//The standard deviation in the transformed z value is not presently considered
    	}
    	
    	//(22) The Ds575 and PGA correlation
    	else if ((imi.getName()==Ds575_Param.NAME&&imj.getName()==PGA_Param.NAME)||
        		(imi.getName()==PGA_Param.NAME&&imj.getName()==Ds575_Param.NAME)) {
    		return -0.442;
        	//The standard deviation in the transformed z value of 0.047 is not presently considered
    	}
    	
    	//(23) The Ds575 and PGV correlation
    	else if ((imi.getName()==Ds575_Param.NAME&&imj.getName()==PGV_Param.NAME)||
        		(imi.getName()==PGV_Param.NAME&&imj.getName()==Ds575_Param.NAME)) {
    		return -0.259;
        	//The standard deviation in the transformed z value of 0.039 is not presently considered
    	}
    	
    	//(24) The Ds575 and ASI correlation
    	else if ((imi.getName()==Ds575_Param.NAME&&imj.getName()==ASI_Param.NAME)||
        		(imi.getName()==ASI_Param.NAME&&imj.getName()==Ds575_Param.NAME)) {
    		return -0.411;
        	//The standard deviation in the transformed z value of 0.047 is not presently considered
    	}
    	
    	//(25) The Ds575 and SI correlation
    	else if ((imi.getName()==Ds575_Param.NAME&&imj.getName()==SI_Param.NAME)||
        		(imi.getName()==SI_Param.NAME&&imj.getName()==Ds575_Param.NAME)) {
    		return -0.131;
        	//The standard deviation in the transformed z value of 0.037 is not presently considered
    	}
    	
    	//(26) The Ds575 and DSI correlation
    	else if ((imi.getName()==Ds575_Param.NAME&&imj.getName()==DSI_Param.NAME)||
        		(imi.getName()==DSI_Param.NAME&&imj.getName()==Ds575_Param.NAME)) {
    		return 0.074;
        	//The standard deviation in the transformed z value of 0.055 is not presently considered
    	}
    	
    	//(27) The Ds575 and CAV correlation
    	else if ((imi.getName()==Ds575_Param.NAME&&imj.getName()==CAV_Param.NAME)||
        		(imi.getName()==CAV_Param.NAME&&imj.getName()==Ds575_Param.NAME)) {
    		return 0.077;
        	//The standard deviation in the transformed z value of 0.032 is not presently considered
    	}
    	
    	
    	//(28) The Ds575 and SA correlation
    	else if ((imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==Ds575_Param.NAME)||
        		(imi.getName()==Ds575_Param.NAME&&imj.getName()==SA_InterpolatedParam.NAME)) {
    		
    		//coefficients
    		double[] a= {-0.45, -0.39, -0.39, -0.06,  0.16,  0.0};
    		double[] b= { 0.01,  0.09,  0.30,  1.40,   6.5, 10.0};
    	    
    	    if (imi.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		else if (imj.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		for (int i=1; i<a.length; i++) {
    			if (ti <=b[i] ) {
    				return a[i-1] + (a[i]-a[i-1])/Math.log(b[i]/b[i-1])*Math.log(ti/b[i-1]);
    			}
    		}
    		return Double.NaN;
    	//The standard deviation in the transformed z value is not presently considered
    	}
    	
    	//(29) The Ds595 and PGA correlation
    	else if ((imi.getName()==Ds595_Param.NAME&&imj.getName()==PGA_Param.NAME)||
        		(imi.getName()==PGA_Param.NAME&&imj.getName()==Ds595_Param.NAME)) {
    		return -0.405;
        	//The standard deviation in the transformed z value of 0.051 is not presently considered
    	}
    	
    	//(30) The Ds595 and PGV correlation
    	else if ((imi.getName()==Ds595_Param.NAME&&imj.getName()==PGV_Param.NAME)||
        		(imi.getName()==PGV_Param.NAME&&imj.getName()==Ds595_Param.NAME)) {
    		return -0.211;
        	//The standard deviation in the transformed z value of 0.037 is not presently considered
    	}
    	
    	//(31) The Ds595 and ASI correlation
    	else if ((imi.getName()==Ds595_Param.NAME&&imj.getName()==ASI_Param.NAME)||
        		(imi.getName()==ASI_Param.NAME&&imj.getName()==Ds595_Param.NAME)) {
    		return -0.370;
        	//The standard deviation in the transformed z value of 0.044 is not presently considered
    	}
    	
    	//(32) The Ds595 and SI correlation
    	else if ((imi.getName()==Ds595_Param.NAME&&imj.getName()==SI_Param.NAME)||
        		(imi.getName()==SI_Param.NAME&&imj.getName()==Ds595_Param.NAME)) {
    		return -0.079;
        	//The standard deviation in the transformed z value of 0.038 is not presently considered
    	}
    	
    	//(33) The Ds595 and DSI correlation
    	else if ((imi.getName()==Ds595_Param.NAME&&imj.getName()==DSI_Param.NAME)||
        		(imi.getName()==DSI_Param.NAME&&imj.getName()==Ds595_Param.NAME)) {
    		return 0.163;
        	//The standard deviation in the transformed z value of 0.061 is not presently considered
    	}
    	
    	//(34) The Ds595 and CAV correlation
    	else if ((imi.getName()==Ds595_Param.NAME&&imj.getName()==CAV_Param.NAME)||
        		(imi.getName()==CAV_Param.NAME&&imj.getName()==Ds595_Param.NAME)) {
    		return 0.122;
        	//The standard deviation in the transformed z value of 0.036 is not presently considered
    	}
    	
    	//(35) The Ds595 and SA correlation
    	else if ((imi.getName()==SA_InterpolatedParam.NAME&&imj.getName()==Ds595_Param.NAME)||
        		(imi.getName()==Ds595_Param.NAME&&imj.getName()==SA_InterpolatedParam.NAME)) {
    		
    		//coefficients
    		double[] a= {-0.41, -0.41, -0.38, -0.35, -0.02,  0.23, 0.02};
    		double[] b= { 0.01,  0.04,  0.08,  0.26,  1.40,   6.0, 10.0};
    	    
    	    if (imi.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imi).getPeriodInterpolatedParam().getValue();
    		else if (imj.getName()==SA_InterpolatedParam.NAME)
    			ti = ((SA_InterpolatedParam) imj).getPeriodInterpolatedParam().getValue();
    		
    		for (int i=1; i<a.length; i++) {
    			if (ti <=b[i] ) {
    				return a[i-1] + (a[i]-a[i-1])/Math.log(b[i]/b[i-1])*Math.log(ti/b[i-1]);
    			}
    		}
    		return Double.NaN;
    	//The standard deviation in the transformed z value is not presently considered
    	}
    	
    	//(36) The Ds575 and Ds595 correlation
    	else if ((imi.getName()==Ds575_Param.NAME&&imj.getName()==Ds595_Param.NAME)||
        		(imi.getName()==Ds595_Param.NAME&&imj.getName()==Ds575_Param.NAME)) {
    		return 0.843;
        	//The standard deviation in the transformed z value of 0.044 is not presently considered
    	}
    	

    	//If the imi and imj are none of these return NaN
    	else {
    		return Double.NaN;
    	}
    
    }
    
    /**
     *  Creates other Parameters
     *  such as the tectonic region (and possibly others)
     */
    protected void initOtherParams() {
    	
    	// init other params defined in parent class
        super.initOtherParams();
        
    	// tectonic region
    	StringConstraint trtConstraint = new StringConstraint();
    	trtConstraint.addString(TRT_ACTIVE_SHALLOW);
    	//trtConstraint.setNonEditable();
		tectonicRegionTypeParam = new TectonicRegionTypeParam(trtConstraint,TRT_ACTIVE_SHALLOW); // Constraint and default value
		tectonicRegionTypeParam.setValueAsDefault();
		// add these to the list
		otherParams.replaceParameter(tectonicRegionTypeParam.NAME, tectonicRegionTypeParam);
    }

    /**
     *  Creates the supported IM parameters (ASI and PGA).
     *  Makes the parameters noneditable.
     */
    protected void initSupportedIntensityMeasureParams() {

      // Create saInterpParam for i:
  	  InterpPeriodiParam = new PeriodInterpolatedParam(t_min, t_max, 1.0, false);
  	  saiDampingParam = new DampingParam();
  	  saiInterpParam = new SA_InterpolatedParam(InterpPeriodiParam, saiDampingParam);
  	  saiInterpParam.setNonEditable();
  	  //Create pgaParam
	  pgaParam = new PGA_Param();
	  pgaParam.setNonEditable();
	  //Create siParam
  	  siParam = new SI_Param();
  	  siParam.setNonEditable();
      // Create asiParam:
      asiParam = new ASI_Param();
      asiParam.setNonEditable();
      // Create pgvParam:
      pgvParam = new PGV_Param();
      pgvParam.setNonEditable();
      // Create asiParam:
      dsiParam = new DSI_Param();
      dsiParam.setNonEditable();
      // Create cavParam:
      cavParam = new CAV_Param();
      cavParam.setNonEditable();
      // Create ds575Param:
      ds575Param = new Ds575_Param();
      ds575Param.setNonEditable();
      // Create ds595Param:
      ds595Param = new Ds595_Param();
      ds595Param.setNonEditable();
  	  

      //Now add the supported IMi and IMj params to the two lists 
	  supportedIMiParams.clear();       		supportedIMjParams.clear();
	  //(1) The SA and PGA correlation
	  supportedIMiParams.add(saiInterpParam);	supportedIMjParams.add(pgaParam);
	  supportedIMiParams.add(pgaParam);			supportedIMjParams.add(saiInterpParam);
  	  //(2) The SA and SI correlation	
	  supportedIMiParams.add(saiInterpParam);	supportedIMjParams.add(siParam);
	  supportedIMiParams.add(siParam);			supportedIMjParams.add(saiInterpParam);
  	  //(3) The SA and ASI correlation
	  supportedIMiParams.add(saiInterpParam);	supportedIMjParams.add(asiParam);
	  supportedIMiParams.add(asiParam);			supportedIMjParams.add(saiInterpParam);
  	  //(4) The PGA and SI correlation
	  supportedIMiParams.add(pgaParam);			supportedIMjParams.add(siParam);
	  supportedIMiParams.add(siParam);			supportedIMjParams.add(pgaParam);
  	  //(5) The PGA and ASI correlation
	  supportedIMiParams.add(pgaParam);			supportedIMjParams.add(asiParam);
	  supportedIMiParams.add(asiParam);			supportedIMjParams.add(pgaParam);
  	  //(6) The SI and ASI correlation
	  supportedIMiParams.add(siParam);			supportedIMjParams.add(asiParam);
	  supportedIMiParams.add(asiParam);			supportedIMjParams.add(siParam);
	  //(7) The PGV and PGA correlation
	  supportedIMiParams.add(pgvParam);			supportedIMjParams.add(pgaParam);
	  supportedIMiParams.add(pgaParam);			supportedIMjParams.add(pgvParam);
	  //(8) The PGV and SI correlation
	  supportedIMiParams.add(pgvParam);			supportedIMjParams.add(siParam);
	  supportedIMiParams.add(siParam);			supportedIMjParams.add(pgvParam);
	  //(9) The PGV and ASI correlation
	  supportedIMiParams.add(pgvParam);			supportedIMjParams.add(asiParam);
	  supportedIMiParams.add(asiParam);			supportedIMjParams.add(pgvParam);
	  //(10) The PGV and SA correlation
	  supportedIMiParams.add(pgvParam);			supportedIMjParams.add(saiInterpParam);
	  supportedIMiParams.add(saiInterpParam);	supportedIMjParams.add(pgvParam);
	  //(11) The DSI and PGA correlation
	  supportedIMiParams.add(dsiParam);			supportedIMjParams.add(pgaParam);
	  supportedIMiParams.add(pgaParam);			supportedIMjParams.add(dsiParam);
	  //(12) The DSI and PGV correlation
	  supportedIMiParams.add(dsiParam);			supportedIMjParams.add(pgvParam);
	  supportedIMiParams.add(pgvParam);			supportedIMjParams.add(dsiParam);
	  //(13) The DSI and ASI correlation
	  supportedIMiParams.add(dsiParam);			supportedIMjParams.add(asiParam);
	  supportedIMiParams.add(asiParam);			supportedIMjParams.add(dsiParam);
	  //(14) The DSI and SI correlation
	  supportedIMiParams.add(dsiParam);			supportedIMjParams.add(siParam);
	  supportedIMiParams.add(siParam);			supportedIMjParams.add(dsiParam);
	  //(15) The DSI and SA correlation
	  supportedIMiParams.add(dsiParam);			supportedIMjParams.add(saiInterpParam);
	  supportedIMiParams.add(saiInterpParam);	supportedIMjParams.add(dsiParam);
	  //(16) The CAV and PGA correlation
	  supportedIMiParams.add(cavParam);			supportedIMjParams.add(pgaParam);
	  supportedIMiParams.add(pgaParam);			supportedIMjParams.add(cavParam);
	  //(17) The CAV and PGV correlation
	  supportedIMiParams.add(cavParam);			supportedIMjParams.add(pgvParam);
	  supportedIMiParams.add(pgvParam);			supportedIMjParams.add(cavParam);
	  //(18) The CAV and ASI correlation
	  supportedIMiParams.add(cavParam);			supportedIMjParams.add(asiParam);
	  supportedIMiParams.add(asiParam);			supportedIMjParams.add(cavParam);
	  //(19) The CAV and SI correlation
	  supportedIMiParams.add(cavParam);			supportedIMjParams.add(siParam);
	  supportedIMiParams.add(siParam);			supportedIMjParams.add(cavParam);
	  //(20) The CAV and DSI correlation
	  supportedIMiParams.add(cavParam);			supportedIMjParams.add(dsiParam);
	  supportedIMiParams.add(dsiParam);			supportedIMjParams.add(cavParam);
	  //(21) The CAV and SA correlation
	  supportedIMiParams.add(cavParam);			supportedIMjParams.add(saiInterpParam);
	  supportedIMiParams.add(saiInterpParam);	supportedIMjParams.add(cavParam);
	  //(22) The Ds575 and PGA correlation
	  supportedIMiParams.add(ds575Param);		supportedIMjParams.add(pgaParam);
	  supportedIMiParams.add(pgaParam);			supportedIMjParams.add(ds575Param);
  	  //(23) The Ds575 and PGV correlation
	  supportedIMiParams.add(ds575Param);		supportedIMjParams.add(pgvParam);
	  supportedIMiParams.add(pgvParam);			supportedIMjParams.add(ds575Param);
  	  //(24) The Ds575 and ASI correlation
	  supportedIMiParams.add(ds575Param);		supportedIMjParams.add(asiParam);
	  supportedIMiParams.add(asiParam);			supportedIMjParams.add(ds575Param);
  	  //(25) The Ds575 and SI correlation
	  supportedIMiParams.add(ds575Param);		supportedIMjParams.add(siParam);
	  supportedIMiParams.add(siParam);			supportedIMjParams.add(ds575Param);
  	  //(26) The Ds575 and DSI correlation
	  supportedIMiParams.add(ds575Param);		supportedIMjParams.add(dsiParam);
	  supportedIMiParams.add(dsiParam);			supportedIMjParams.add(ds575Param);
  	  //(27) The Ds575 and CAV correlation
	  supportedIMiParams.add(ds575Param);		supportedIMjParams.add(cavParam);
	  supportedIMiParams.add(cavParam);			supportedIMjParams.add(ds575Param);
  	  //(28) The Ds575 and SA correlation
	  supportedIMiParams.add(ds575Param);		supportedIMjParams.add(saiInterpParam);
	  supportedIMiParams.add(saiInterpParam);	supportedIMjParams.add(ds575Param);
  	  //(29) The Ds595 and PGA correlation
	  supportedIMiParams.add(ds595Param);		supportedIMjParams.add(pgaParam);
	  supportedIMiParams.add(pgaParam);			supportedIMjParams.add(ds595Param);
  	  //(30) The Ds595 and PGV correlation
	  supportedIMiParams.add(ds595Param);		supportedIMjParams.add(pgvParam);
	  supportedIMiParams.add(pgvParam);			supportedIMjParams.add(ds595Param);
  	  //(31) The Ds595 and ASI correlation
	  supportedIMiParams.add(ds595Param);		supportedIMjParams.add(asiParam);
	  supportedIMiParams.add(asiParam);			supportedIMjParams.add(ds595Param);
  	  //(32) The Ds595 and SI correlation
	  supportedIMiParams.add(ds595Param);		supportedIMjParams.add(siParam);
	  supportedIMiParams.add(siParam);			supportedIMjParams.add(ds595Param);
  	  //(33) The Ds595 and DSI correlation
	  supportedIMiParams.add(ds595Param);		supportedIMjParams.add(dsiParam);
	  supportedIMiParams.add(dsiParam);			supportedIMjParams.add(ds595Param);
  	  //(34) The Ds595 and CAV correlation
	  supportedIMiParams.add(ds595Param);		supportedIMjParams.add(cavParam);
	  supportedIMiParams.add(cavParam);			supportedIMjParams.add(ds595Param);
  	  //(35) The Ds595 and SA correlation
	  supportedIMiParams.add(ds595Param);		supportedIMjParams.add(saiInterpParam);
	  supportedIMiParams.add(saiInterpParam);	supportedIMjParams.add(ds595Param);
  	  //(36) The Ds575 and Ds595 correlation
	  supportedIMiParams.add(ds595Param);		supportedIMjParams.add(ds575Param);
	  supportedIMiParams.add(ds575Param);		supportedIMjParams.add(ds595Param);
    }

    /**
     * Returns the name of the object
     *
     */
    public String getName() {
      return NAME;
    }
    
    /**
     * Returns the short name of the object
     *
     */
    public String getShortName() {
      return SHORT_NAME;
    }
}

