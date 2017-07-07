  package org.opensha.sha.gcim.imr.attenRelImpl.ASI_WrapperAttenRel;
  
  import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.BA_2008_AttenRel;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
  /**
   * This tests DistJB numerical precision with respect to the f_hngR term.  Looks OK now.
   * @param args
   */
  
  public class Test_BA08_ASI_AttenRel {
	  public static void main(String[] args) {

		  Location loc1 = new Location(-0.1, 0.0, 0);
		  Location loc2 = new Location(+0.1, 0.0, 0);
		  FaultTrace faultTrace = new FaultTrace("test");
		  faultTrace.add(loc1);
		  faultTrace.add(loc2);	  
		  StirlingGriddedSurface surface = new StirlingGriddedSurface(faultTrace, 45.0,0,10,1);
		  EqkRupture rup = new EqkRupture();
		  rup.setMag(7.8);
		  rup.setAveRake(90);
		  rup.setRuptureSurface(surface);
	  
		  BA_2008_ASI_AttenRel attenRel = new BA_2008_ASI_AttenRel(null);
		  attenRel.setParamDefaults();
		  attenRel.setIntensityMeasure("ASI");
		  attenRel.setEqkRupture(rup);
	  
		  Site site = new Site();
		  site.addParameter(attenRel.getParameter(Vs30_Param.NAME));
	  
		  Location loc;
		  for(double dist=0.0; dist<=0.3; dist+=0.01) {
			  loc = new Location(0,dist);
			  site.setLocation(loc);
			  attenRel.setSite(site);
//		      System.out.print((float)dist+"\t");
			  
			  double mu = attenRel.getMean();
			  double sigma = attenRel.getStdDev();
			  System.out.println("  "+dist + " " + Math.exp(mu) + " " + sigma);
			  
		  }
	  }
	  
	  
  }