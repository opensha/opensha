package org.opensha.sha.imr.attenRelImpl.ngaw2;

import java.util.Collection;

import org.opensha.commons.data.Named;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Interface for NGAW2 ground motion models.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public interface NGAW2_GMM extends Named {
	
	public ScalarGroundMotion calc();
	
	public void set_IMT(IMT imt);
	
	// order below should be followed in calc(args)
	
	public void set_Mw(double Mw);
	
	public void set_rJB(double rJB);
	public void set_rRup(double rRup);
	public void set_rX(double rX);
	
	public void set_dip(double dip);
	public void set_width(double width);
	public void set_zTop(double zTop);
	public void set_zHyp(double zHyp);

	public void set_vs30(double vs30);
	public void set_vsInf(boolean vsInf);
	public void set_z2p5(double z2p5);
	public void set_z1p0(double z1p0);
	
	public void set_fault(FaultStyle style);
	
	public TectonicRegionType get_TRT();
	
	public Collection<IMT> getSupportedIMTs();
		
}
