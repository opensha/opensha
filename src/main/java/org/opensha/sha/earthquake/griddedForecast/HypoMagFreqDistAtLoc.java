package org.opensha.sha.earthquake.griddedForecast;

import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * <p>Title: HypoMagFreqDistAtLoc</p>
 *
 * <p>Description: This allows user to store a set of MagFreqDists and associate focal mechanims for a given
 * location.</p>
 *
 * @author Nitin Gupta
 * @version 1.0
 */
public class HypoMagFreqDistAtLoc extends MagFreqDistsForFocalMechs implements java.io.Serializable{

  private Location location;

  /**
   * Class Constructor.
   * In this case the no focalMechanisms are specified.
   * @param magDist IncrementalMagFreqDist[] list of MagFreqDist for the given location.
   * @param loc Location
   */
  public HypoMagFreqDistAtLoc(IncrementalMagFreqDist[] magDist, Location loc) {
	  super(magDist);
	  location = loc;
  }
  
  
  /**
   * Class Constructor.
   * This is for passing in a single magFreqDist (don't have to create an array) and no focal mechanism.
   * @param magDist IncrementalMagFreqDist MagFreqDist for the given location.
   * @param loc Location
   */
  public HypoMagFreqDistAtLoc(IncrementalMagFreqDist magDist, Location loc) {
	  super(magDist);
	  location = loc;
  }

  /**
   * Class constructor.
   * This constructor allows user to give a list of focalMechanisms for a given location.
   * @param magDist IncrementalMagFreqDist[] list of magFreqDist, same as number of focal mechanisms.
   * @param loc Location Location
   * @param focalMechanism FocalMechanism[] list of focal mechanism for a given location.
   *
   */
  public HypoMagFreqDistAtLoc(IncrementalMagFreqDist[] magDist, Location loc,
                              FocalMechanism[] focalMechanism) {
	  super(magDist, focalMechanism);
	  location = loc;
  }

  /**
   * Class constructor.
   * This constructor allows user to give a single magDist and focalMechanism for a given location.
   * @param magDist IncrementalMagFreqDist
   * @param loc Location Location
   * @param focalMechanism FocalMechanism
   *
   */
  public HypoMagFreqDistAtLoc(IncrementalMagFreqDist magDist, Location loc, FocalMechanism focalMech) {
	  super(magDist,focalMech);
	  location = loc;
  }

  /**
   * Returns the Location at which MagFreqDist(s) is calculated.
   * @return Location
   */
  public Location getLocation() {
    return location;
  }
  

}
