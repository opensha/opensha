/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.gcim.calc;

import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 * <p>Title: GcimCalculatorAPI</p>
 * <p>Description: This class defines interface for Gcim calculator.
 * Implementation of this interface computes GCIM distributions for various
 * IMi's conditioned on a hazard curve for IMj based on the
 * input parameters imr, site and eqkRupforecast.  See Bradley (2010, Earthquake Engineering
 * and Structural Dynamics, in press) for a complete discussion of GCIM.<p>
 * @author : Brendon Bradley
 * @created July 3 2010
 * @version 1.0
 */
public interface GcimCalculatorAPI extends Remote{

	/**
	 * This method gets the contribution of each rupture in the ERF toward the probability of IML=iml
	 *
	 * @throws java.rmi.RemoteException
	 * @throws IOException
	 */
	public void getRuptureContributions(double iml, Site site,
			HashMap<TectonicRegionType, ScalarIMR> imrjMap, AbstractERF eqkRupForecast,
			double maxDist, ArbitrarilyDiscretizedFunc magDistFilter) throws java.rmi.RemoteException;
			
	/**
	 * this function obtains the GCIM distributions for multiple IMs by successively calling
	 * the getSingleGcim method
	 * Returns true if it was successfully else return false
	 *
	 * @param imri: selected IMRi object (that for which the distribution is desired i.e. IMi)
	 * @param imCorrelationRelationship: selected correlation object for IMi and IMj
	 * @param maxDist: maxDist of sources to consider
	 * @param magDistFilter: Magnitude-Distance filter for sources
	 * @return boolean
	 */
	public boolean getMultipleGcims(int numIMi,	
			ArrayList<HashMap<TectonicRegionType, ScalarIMR>> imiAttenRels,
			ArrayList<String> imiTypes, ArrayList<HashMap<TectonicRegionType, ImCorrelationRelationship>> imijCorrRels,
			double maxDist, ArbitrarilyDiscretizedFunc magDistFilter);

	/**
	 * this function performs the GCIM computations to obtain the conditional distribution
	 * of a single input intensity measure.
	 * Returns true if it was successfully else return false
	 *
	 *@param imiNumber - the imi counter used for storing results in array
	 * @param imri: selected IMRi object (that for which the distribution is desired i.e. IMi)
	 * @param imCorrelationRelationship: selected correlation object for IMi and IMj
	 * @param maxDist: maxDist of sources to consider
	 * @param magDistFilter: Magnitude-Distance filter for sources
	 * @return boolean
	 */
	public boolean getSingleGcim(int imiNumber, HashMap<TectonicRegionType, ScalarIMR> imriMap,
			HashMap<TectonicRegionType, ImCorrelationRelationship> imijCorrRelMap,
			double maxDist, ArbitrarilyDiscretizedFunc magDistFilter);
	
	/**
	 * This method sets the approximate CDF values for which the gcim distributions are calculated for
	 * @param zmin - the minimum z (normalised CDF value) to compute gcim for
	 * @param zmax - the maximum z (normalised CDF value) to compute gcim for
	 * @param dz -   the increment of z (normalised CDF value) which determines the number of points
	 */
	public void setApproxCDFvalues(double zmin, double zmax, double dz);
	
	/**
	 * This method sets the approximate CDF values for which the gcim distributions are calculated for
	 * using defaults zmin=-3, zmax=3, and dz=0.2 which gives 51 points
	 */
	public void setApproxCDFvalues();
	
	/**
	 * This method gets the Approx CDFvalues
	 */
	public double[] getApproxCDFvalues();
	
	/**
	 * This method sets the dimensions of the imiArray and cdfIMi_IMjArrays
	 */
	public void setGcimOutputDimensions();
	
}
