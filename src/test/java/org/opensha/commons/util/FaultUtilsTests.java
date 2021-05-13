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

package org.opensha.commons.util;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.util.FaultUtils;



public class FaultUtilsTests {

	public FaultUtilsTests() {
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	@Test(expected=InvalidRangeException.class)
	public void testAssertValidStrike() {
		double strike1=  -1.0;
		FaultUtils.assertValidStrike(strike1);
	}
	
	@Test
	public void testLengthBasedAngleAverage() {
		ArrayList<Double> scalars = new ArrayList<Double>();
		
		scalars.add(1d);
		scalars.add(1d);
		
		ArrayList<Double> angles = new ArrayList<Double>();
		angles.add(-175d);
		angles.add(175d);
		
		double avg = FaultUtils.getScaledAngleAverage(scalars, angles);
		
		assertEquals(180d, avg, 0.1d);
		
		scalars = new ArrayList<Double>();
		
		scalars.add(1d);
		scalars.add(1d);
		
		angles = new ArrayList<Double>();
		angles.add(-10d);
		angles.add(10d);
		
		avg = FaultUtils.getScaledAngleAverage(scalars, angles);
		
		assertEquals(0d, avg, 0.1d);
		
		scalars = new ArrayList<Double>();
		
		scalars.add(1d);
		scalars.add(2d);
		
		angles = new ArrayList<Double>();
		angles.add(90d);
		angles.add(180d);
		
		avg = FaultUtils.getScaledAngleAverage(scalars, angles);
		
		assertTrue(avg > 135 && avg < 180);
	}
}
