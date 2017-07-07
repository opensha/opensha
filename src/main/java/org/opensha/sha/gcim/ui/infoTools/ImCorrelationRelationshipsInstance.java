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

package org.opensha.sha.gcim.ui.infoTools;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.opensha.commons.data.Named;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.sha.gcim.imCorrRel.ImCorrelationRelationship;
import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.Baker07_ImCorrRel;
import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.BakerJayaram08_ImCorrRel;
import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.Bradley11_ImCorrRel;
import org.opensha.sha.gcim.imCorrRel.imCorrRelImpl.GodaAtkinson09_ImCorrRel;

/**
 * <p>Title: ImCorrelationRelationshipsInstance </p>
 * <p>Description: Creates the list of the ImCorrelationRelationships Objects from
 * their classnames.</p>
 * @author : Brendon Bradley
 * @created 13 June 2010
 * @version 1.0
 */

public class ImCorrelationRelationshipsInstance {

	private static final String C= "ImCorrelationRelationshipsInstance";
    
	//arrayList to store the supported ImCorrelation Class Names with their full package structure.
	private ArrayList<String> supportedImCorrRelClasses;
	
	public static ArrayList<String> getDefaultIMCorrRelClassNames() {
		ArrayList<String> supportedImCorrRelClasses = new ArrayList<String>();
		
		//adds all the ImCorrRel classes to the ArrayList
		// ******** ORDER THEM BY YEAR, NEWEST FIRST ********
		// 2011
		supportedImCorrRelClasses.add(Bradley11_ImCorrRel.class.getName()); 
		
		// 2010 
		
		// 2009
		supportedImCorrRelClasses.add(GodaAtkinson09_ImCorrRel.class.getName());
		
		// 2008
		
		supportedImCorrRelClasses.add(BakerJayaram08_ImCorrRel.class.getName());

		// 2007
//		supportedImCorrRelClasses.add(Baker07_ImCorrRel.class.getName());  //This is commented out for 
		//now while GCIM IMik correlations are hard-coded (i.e. so the Bradley11 relation is default 
		//for the PGA-SA correlation) //TODO remove once hard-coding removed
		
		// 2006

		
		// OTHER

		return supportedImCorrRelClasses;
	}

	/**
	 * class default constructor
	 */
	public ImCorrelationRelationshipsInstance(){
		this(getDefaultIMCorrRelClassNames());
	}
	
	/**
	 * constructor for giving your own custom class names
	 */
	public ImCorrelationRelationshipsInstance(ArrayList<String> classNames){
		setIMCorrRel_ClassNames(classNames);
	}

	/**
	 * This method takes in a custom list of IMCorrRel class names that are used when
	 * createIMCorrRelClassInstance is called.
	 * 
	 * @param classNames an ArrayList of IMCorrRel class names to be included.
	 */

	public void setIMCorrRel_ClassNames(ArrayList<String> classNames) {
		supportedImCorrRelClasses = classNames;
	}


	/**
	 * Creates a class instance from a string of the full class name including packages.
	 * This is how you dynamically make objects at runtime if you don't know which\
	 * class beforehand. For example (for AttenRels), if you wanted to create a BJF_1997_AttenRel you can do
	 * it the normal way:<P>
	 *
	 * <code>BJF_1997_AttenRel imr = new BJF_1997_AttenRel()</code><p>
	 *
	 * If your not sure the user wants this one or AS_1997_AttenRel you can use this function
	 * instead to create the same class by:<P>
	 *
	 * <code>BJF_1997_AttenRel imr =
	 * (BJF_1997_AttenRel)ClassUtils.createNoArgConstructorClassInstance("org.opensha.sha.imt.attenRelImpl.BJF_1997_AttenRel");
	 * </code><p>
	 *
	 */

	public ArrayList<ImCorrelationRelationship> 
			createImCorrRelClassInstance(ParameterChangeWarningListener listener){
		
		ArrayList<ImCorrelationRelationship> ImCorrRelObjects = 
			new ArrayList<ImCorrelationRelationship>();
		String S = C + ": createImCorrRelClassInstance(): ";
		int size = supportedImCorrRelClasses.size();
		
		for(int i=0;i< size;++i){
			Object obj = createImCorrRelClassInstance(listener, supportedImCorrRelClasses.get(i));
			ImCorrRelObjects.add((ImCorrelationRelationship)obj);
		}
		
		Collections.sort(ImCorrRelObjects, new ImCorrRelComparator());
		return ImCorrRelObjects;
	}

	/**
	 * Creates a class instance from a string of the full class name including packages.
	 * This is how you dynamically make objects at runtime if you don't know which\
	 * class beforehand. For example, if you wanted to create a BJF_1997_AttenRel you can do
	 * it the normal way:<P>
	 *
	 * <code>BJF_1997_AttenRel imr = new BJF_1997_AttenRel()</code><p>
	 *
	 * If your not sure the user wants this one or AS_1997_AttenRel you can use this function
	 * instead to create the same class by:<P>
	 *
	 * <code>BJF_1997_AttenRel imr =
	 * (BJF_1997_AttenRel)ClassUtils.createNoArgConstructorClassInstance("org.opensha.sha.imt.attenRelImpl.BJF_1997_AttenRel");
	 * </code><p>
	 *
	 */

	public ImCorrelationRelationship createImCorrRelClassInstance( org.opensha.commons.param.event.ParameterChangeWarningListener listener, String className){
		String S = C + ": createIMRClassInstance(): ";
		try {
		    // KLUDGY why is this class hardcoded and dynamically loaded
//			Class listenerClass = Class.forName( "org.opensha.commons.param.event.ParameterChangeWarningListener" );
//			Object[] paramObjects = new Object[]{ listener };
//			Class[] params = new Class[]{ listenerClass };
			Class imCorrRelClass = Class.forName(className);
//			Constructor con = imCorrRelClass.getConstructor( params );
			Constructor con = imCorrRelClass.getConstructor();
//			Object obj = con.newInstance( paramObjects );
			Object obj = con.newInstance();
			return (ImCorrelationRelationship)obj;
		} catch ( ClassCastException e ) {
			System.out.println(S + e.toString());
			throw new RuntimeException( S + e.toString() );
		} catch ( ClassNotFoundException e ) {
			System.out.println(S + e.toString());
			throw new RuntimeException( S + e.toString() );
		} catch ( NoSuchMethodException e ) {
			System.out.println(S + e.toString());
			throw new RuntimeException( S + e.toString() );
		} catch ( InvocationTargetException e ) {
			e.printStackTrace();
			System.out.println(S + e.toString());
			throw new RuntimeException( S + e.toString() );
		} catch ( IllegalAccessException e ) {
			System.out.println(S + e.toString());
			throw new RuntimeException( S + e.toString() );
		} catch ( InstantiationException e ) {
			System.out.println(S + e.toString());
			throw new RuntimeException( S + e.toString() );
		}
	}

	private static class ImCorrRelComparator implements 
			Comparator<Named> {

		public int compare(
				Named imcorrRel1,
				Named imcorrRel2) {
			return imcorrRel1.getName().compareToIgnoreCase(imcorrRel2.getName());
		}
	}

}
