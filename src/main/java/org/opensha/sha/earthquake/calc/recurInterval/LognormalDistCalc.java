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

package org.opensha.sha.earthquake.calc.recurInterval;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;


/**
 * <b>Title:</b> LognormalDistCalc.java <p>
 * <b>Description:</p>.
 <p>
 *
 * @author Edward Field
 * @created    July, 2007
 * @version 1.0
 */

public final class LognormalDistCalc extends EqkProbDistCalc implements ParameterChangeListener {
	 
	
	
	public LognormalDistCalc() {
		NAME = "Lognormal";
		super.initAdjParams();
	}
	

	
	
	/*
	 * This computes the PDF and then the cdf from the pdf using 
	 * Trapezoidal integration. 
	 */
	protected void computeDistributions() {
		
		// make these null
		integratedCDF = null;
		integratedOneMinusCDF = null;

		pdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		cdf = new EvenlyDiscretizedFunc(0,numPoints,deltaX);
		// set first y-values to zero
		pdf.set(0,0);
		cdf.set(0,0);
		
		// convert mean and aperiodicity to mu and sigma
		double sigma = Math.sqrt(Math.log(aperiodicity*aperiodicity+1));
//		double mu = Math.log(mean/Math.exp(sigma*sigma/2));
		double mu = Math.log(mean)-(sigma*sigma/2);
		
		double temp1 = sigma*Math.sqrt(2.0*Math.PI);
		double temp2 = 2.0*sigma*sigma;
		double t,pd,cd=0;
		for(int i=1; i< pdf.size(); i++) { // skip first point because it's NaN
			t = cdf.getX(i);
			pd = Math.exp(-(Math.log(t)-mu)*(Math.log(t)-mu)/temp2)/(temp1*t);
			if(Double.isNaN(pd)){
				pd=0;
				System.out.println("pd=0 for i="+i);
			}
			cd += deltaX*(pd+pdf.getY(i-1))/2;  // Trapizoidal integration
			pdf.set(i,pd);
			cdf.set(i,cd);
		}
		upToDate = true;
	}

	
	/**
	 *  Main method for running tests.
	 */
	public static void main(String args[]) {
		
	}
}

