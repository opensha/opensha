package org.opensha.sha.imr;

import java.util.Random;
import java.util.function.Function;

import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.PointSurface.SiteSpecificDistanceCorrected;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;


/**
 * <b>Title:</b> ScalarIMR<br>
 * <b>Description:</b> ScalarIntensityMeasureRelationshipAPI extends IntensityMeasureParameterAPI
 * for the case where the intensity-measure type is a scalar value (DoubleParameter).
 *
 * @author     Edward H. Field
 * @created    February 21, 2002
 * @version    1.0
 */

public interface ScalarIMR extends IntensityMeasureRelationship {

	/**
	 * This returns a random IML
	 * @return
	 */
	public double getRandomIML(Random random);

	/**
	 * This returns metadata for all parameters (only showing the independent parameters
	 * relevant for the presently chosen imt).  This could exist in the parent class.
	 * @return
	 */
	public String getAllParamMetadata();

	/**
	 * This method sets the user-defined distance beyond which ground motion is
	 * set to effectively zero (the mean is a large negative value).
	 * @param maxDist
	 */
	public void setUserMaxDistance(double maxDist);

	/**
	 *  Sets the value of the selected intensityMeasure;
	 *
	 * @param  iml                     The new intensityMeasureLevel value
	 * @exception  ParameterException  Description of the Exception
	 */
	public void setIntensityMeasureLevel(Double iml) throws ParameterException;

	/**
	 *  This calculates the intensity-measure level associated with probability
	 *  held by the exceedProbParam given the mean and standard deviation.  Note
	 *  that this does not store the answer in the value of the internally held
	 *  intensity-measure parameter.
	 *
	 * @return                         The intensity-measure level
	 */
	public double getIML_AtExceedProb();

	/**
	 *  This calculates the intensity-measure level associated with 
	 *  given probability and the calculated mean and standard deviation
	 * (and according to the chosen truncation type and level).  Note
	 *  that this does not store the answer in the value of the internally 
	 *  held intensity-measure parameter.
	 * @param exceedProb : Sets the Value of the exceed Prob param with this value.
	 * @return                         The intensity-measure level
	 * @exception  ParameterException  Description of the Exception
	 */
	public double getIML_AtExceedProb(double exceedProb);

	/**
	 *  This returns the mean intensity-measure level for the current
	 *  set of parameters.
	 *
	 * @return    The mean value
	 */
	public double getMean();

	/**
	 *  This returns the standard deviation (stdDev) of the intensity-measure
	 *  level for the current set of parameters.
	 *
	 * @return    The stdDev value
	 */
	public double getStdDev();
	
	/**
	 * This returns the mean intensity-measure for the given earthquake rupture, properly handling the case of
	 * multiple point-source distance corrections (and returning an average value across them).
	 * @param eqkRupture
	 * @return
	 */
	public default double getMean(EqkRupture eqkRupture) {
		return calculateAverageValueAcrossDistanceCorrections(this, eqkRupture, S->S.getMean());
	}
	
	/**
	 * This returns the standard deviation of the intensity-measure for the given earthquake rupture, properly handling
	 * the case of multiple point-source distance corrections (and returning an average value across them).
	 * @param eqkRupture
	 * @return
	 */
	public default double getStdDev(EqkRupture eqkRupture) {
		return calculateAverageValueAcrossDistanceCorrections(this, eqkRupture, S->S.getStdDev());
	}
	
	private static double calculateAverageValueAcrossDistanceCorrections(ScalarIMR gmm, EqkRupture eqkRupture, Function<ScalarIMR, Double> calculator) {
		RuptureSurface surf = eqkRupture.getRuptureSurface();
		if (surf instanceof PointSurface.DistanceCorrectable) {
			// point surface with distance corrections
			Location siteLoc = gmm.getSite().getLocation();
			WeightedList<SurfaceDistances> surfs = ((PointSurface.DistanceCorrectable)surf).getCorrectedDistances(siteLoc);
			Preconditions.checkState(surfs.isNormalized());
			
			double avgValue = 0d;
			for (int s=0; s<surfs.size(); s++) {
				WeightedValue<SurfaceDistances> dists = surfs.get(s);
				
				if (s == 0 || !(gmm instanceof ErgodicIMR)) {
					// first time, need to set the full rupture
					SiteSpecificDistanceCorrected corrSurf = new SiteSpecificDistanceCorrected((PointSurface)surf, siteLoc, dists.value);
					gmm.setEqkRupture(new EqkRupture(eqkRupture.getMag(), eqkRupture.getAveRake(), corrSurf, eqkRupture.getHypocenterLocation()));
				} else {
					// subsequent time(s), only need to set the distances
					((ErgodicIMR)gmm).setPropagationEffectParams(dists.value);
				}
				
				avgValue += calculator.apply(gmm)*dists.weight;
			}
			
			// clear the eqkRupture object so that we don't leave a stale site-specific corrected instance
			gmm.setEqkRupture(null);
			
			return avgValue;
		} else {
			// no special treatment
			gmm.setEqkRupture(eqkRupture);
			
			return calculator.apply(gmm);
		}
	}

	/**
	 *  This fills in the exceedance probability for multiple intensityMeasure
	 *  levels (often called a "hazard curve"); the levels are obtained from
	 *  the X values of the input function, and Y values are filled in with the
	 *  associated exceedance probabilities.
	 *
	 * @param  intensityMeasureLevel  The function to be filled in
	 * @return                        The same function
	 */
	public DiscretizedFunc getExceedProbabilities(
			DiscretizedFunc intensityMeasureLevels
	);

	/**
	 * This calculates the intensity-measure level for each SA Period
	 * associated with the given probability.  The x values in the
	 * returned function correspond to the periods supported by the IMR.
	 * @param exceedProb
	 * @return DiscretizedFuncAPI - the IML function
	 */
	public DiscretizedFunc getSA_IML_AtExceedProbSpectrum(double exceedProb) throws
	ParameterException,
	IMRException;


	/**
	 *  This calculates the exceed-probability at each SA Period for
	 *  the supplied intensity-measure level (a hazard spectrum).  The x values 
	 *  in the returned function correspond to the periods supported by the IMR.
	 *
	 * @return     DiscretizedFuncAPI - The hazard spectrum
	 */
	public DiscretizedFunc getSA_ExceedProbSpectrum(double iml) throws ParameterException,
	IMRException ;



	/**
	 *  This calculates the probability that the supplied intensity-measure level
	 *  will be exceeded given the mean and stdDev computed from current independent
	 *  parameter values.  Note that the answer is not stored in the internally held
	 *  exceedProbParam (this latter param is used only for the
	 *  getIML_AtExceedProb() method).
	 *
	 * @return                         The exceedProbability value
	 * @exception  ParameterException  Description of the Exception
	 * @exception  IMRException        Description of the Exception
	 */
	public double getExceedProbability(double iml);

	/**
	 * This returns (iml-mean)/stdDev, ignoring any truncation.  This gets the iml
	 * from the value in the Intensity-Measure Parameter.
	 * @return double
	 */
	public double getEpsilon();


	/**
	 * This returns (iml-mean)/stdDev, ignoring any truncation.
	 *
	 * @param iml double
	 * @return double
	 */
	public double getEpsilon(double iml);

	/**
	 *  Returns a list of all the Parameters that the Mean calculation depends upon.
	 *  (not including the intensity-measure related parameters and their internal,
	 *  independent parameters).
	 *
	 * @return    The Independent Params list
	 */
	public ParameterList getMeanIndependentParams();

	/**
	 *  Returns a list of all the Parameters that the StdDev calculation depends upon
	 *  (not including the intensity-measure related parameters and their internal,
	 *  independent parameters).
	 *
	 * @return    The Independent Parameters list
	 */
	public ParameterList getStdDevIndependentParams();

	/**
	 *  Returns a list of all the Parameters that the exceedProb calculation
	 *  depends upon (not including the intensity-measure related parameters and
	 *  their internal, independent parameters).
	 *
	 * @return    The Independent Params list
	 */
	public ParameterList getExceedProbIndependentParams();

	/**
	 *  Returns a list of all the Parameters that the IML-at-exceed-
	 *  probability calculation depends upon. (not including the intensity-measure
	 *  related paramters and their internal, independent parameters).
	 *
	 * @return    The Independent Params list
	 */
	public ParameterList getIML_AtExceedProbIndependentParams();

	/**
	 * This method sets the location in the site.
	 * This is helpful because it allows to  set the location within the
	 * site without setting the Site Parameters. Thus allowing the capability
	 * of setting the site once and changing the location of the site to do the
	 * calculations.
	 * After setting the location within the site, it calls the method
	 * setPropagationEffectsParams().
	 */
	public void setSiteLocation(Location loc);

	/**
	 * Allows to reset the change listeners on the parameters
	 */
	public void resetParameterEventListeners();

	/**
	 * Tells whether the given tectonic region is supported
	 * @param tectRegion
	 * @return
	 */
	public boolean isTectonicRegionSupported(TectonicRegionType tectRegion);

}
