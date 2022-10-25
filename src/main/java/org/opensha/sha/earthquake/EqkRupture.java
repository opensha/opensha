package org.opensha.sha.earthquake;

import java.util.ListIterator;

import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.PointSurface;


/**
 *
 * <b>Title:</b> EqkRupture<br>
 * <b>Description:</b> <br>
 *
 * @author Sid Hellman
 * @version 1.0
 */

public class EqkRupture implements java.io.Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/* *******************/
    /** @todo  Variables */
    /* *******************/

    /* Debbuging variables */
    protected final static String C = "EqkRupture";
    protected final static boolean D = false;

    protected double mag=Double.NaN;
    protected double aveRake=Double.NaN;

    protected Location hypocenterLocation = null;



    /** object to specify Rupture distribution and AveDip */
    protected RuptureSurface ruptureSurface = null;

    /** object to contain arbitrary parameters */
    protected ParameterList otherParams ;




    /* **********************/
    /** @todo  Constructors */
    /* **********************/

    public EqkRupture() {


    }

    public EqkRupture(
        double mag,
        double aveRake,
        RuptureSurface ruptureSurface,
	Location hypocenterLocation) throws InvalidRangeException{
      this.mag = mag;
      FaultUtils.assertValidRake(aveRake);
      this.hypocenterLocation = hypocenterLocation;
      this.aveRake = aveRake;
      this.ruptureSurface = ruptureSurface;
    }



    /* ***************************/
    /** @todo  Getters / Setters */
    /* ***************************/

    /**
     * This function doesn't create the ParameterList until the
     * first attempt to add a parameter is added. This is known as
     * Lazy Instantiation, where the class is not created until needed.
     * This is a common performance enhancement, because in general, not all
     * aspects of a program are used per user session.
     */
    public void addParameter(Parameter<?> parameter){
        if( otherParams == null) otherParams = new ParameterList();
        if(!otherParams.containsParameter(parameter)){
            otherParams.addParameter(parameter);
        }
        else{ otherParams.updateParameter(parameter); }
    }

    public void removeParameter(Parameter<?> parameter){
        if( otherParams == null) return;
        otherParams.removeParameter(parameter);
    }

    /**
     * SWR - Not crazy about the name, why not just getParametersIterator(),
     * same as the ParameterList it is calling. People don't know that they
     * have been Added, this doesn't convey any more information than the
     * short name to me.
     */
    public ListIterator<Parameter<?>> getAddedParametersIterator(){
        if( otherParams == null) return null;
        else{ return otherParams.getParametersIterator(); }
    }

    public double getMag() { return mag; }
    public void setMag(double mag) { this.mag = mag; }

    public double getAveRake() { return aveRake; }
    public void setAveRake(double aveRake) throws InvalidRangeException{
        FaultUtils.assertValidRake(aveRake);
        this.aveRake = aveRake;
    }


    public RuptureSurface getRuptureSurface() { return ruptureSurface; }


    /**
     * Note: Since this takes a GriddedSurfaceAPI both a
     * PointSurface and GriddedSurface can be set here
     */
    public void setRuptureSurface(RuptureSurface r) { ruptureSurface = r; }

    public Location getHypocenterLocation() { return hypocenterLocation; }
    public void setHypocenterLocation(Location h) { hypocenterLocation = h; }



    public void setPointSurface(Location location){
        PointSurface ps = new PointSurface(location);
        setPointSurface(ps);
    }

    public void setPointSurface(Location location, double aveDip ){
        setPointSurface(location);
        ((PointSurface)ruptureSurface).setAveDip(aveDip);
    }

    public void setPointSurface(Location location, double aveStrike, double aveDip){
        setPointSurface(location);
        ((PointSurface)ruptureSurface).setAveStrike(aveStrike);
        ((PointSurface)ruptureSurface).setAveDip(aveDip);
    }

    public void setPointSurface(PointSurface pointSurface){
        this.ruptureSurface = pointSurface;
    }

    public String getInfo() {
       String info = new String("\tMag. = " + (float) mag + "\n" +
                         "\tAve. Rake = " + (float) aveRake + "\n" +
                         "\tAve. Dip = " + (float) ruptureSurface.getAveDip() +
                         "\n" +
                         "\tHypocenter = " + hypocenterLocation + "\n");

      info += ruptureSurface.getInfo();
      return info;
    }

}
