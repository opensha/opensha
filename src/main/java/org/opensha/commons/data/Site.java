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

package org.opensha.commons.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.geo.Location;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;

/**
 *  <b>Title:</b> Site<p>
 *
 *  <b>Description:</b> This class hold the information about a geographical site.
 *  It has been generalized by extendign a ParameterList so that any site parameters
 *  can be contained within this Site object. The Site object is really a specialized
 *  collection of parameters associated with a Location object, i.e. latitude and
 *  longitude.<p>
 *
 *  Each Parameter within the list represents a site parameter used by an
 *  IntensityMeasureRelationship. The constructor will create some default
 *  Parameters for previously published relationships (e.g. AS_1997,
 *  Campbell_1997, Sadigh_1997, Field_1997, and Abrahamson_2000), but a method
 *  will also be provided so that one can add others if so desired. <p>
 *
 *  An IntensityMeasureRalationship object will request whatever Parameter values
 *  it needs from the Site object that is passed into the IMR. <p>
 *
 *  Provide methods for setting all site parameters from, for example, Vs30
 *  (authors must approve how this is done)? <p>
 *
 * Note that the Site class implements NamedObjectAPI. This is usefull for giving
 * a Site a name, such as "Los Angeles".<p>
 *
 * @author     Sid Hellman, Steven W. Rock
 * @created    February 26, 2002
 * @version    1.0
 */

public class Site extends ParameterList implements Named,Serializable,XMLSaveable {

    /** Class name - used for debugging */
    protected final static String C = "Site";
    
    public static String XML_METADATA_NAME = "Site";
    public static String XML_PARAMS_NAME = "SiteParams";
    public static String XML_METADATA_LIST_NAME = "Sites";

    /** Boolean when set prints out debugging statements */
    protected final static boolean D = false;

    /** Name of the site.  */
    public String name;

    /** Location of this site */
    protected Location location;

    /** No-Arg Constructor for the Site object. Currenlty does nothing. */
    public Site() { }

    /** Constructor for the Site object that sets the location. */
    public Site( Location location ) { this.location = location; }


    /**
     *  Constructor for the Site object that sets the site location
     *  and the name.
     *
     * @param  location  Site location
     * @param  name      Site name
     */
    public Site( Location location, String name ) {
        this.location = location;
        this.name = name;
    }


    /** Sets the name of the Site. */
    public void setName( String name ) { this.name = name; }

    /** Returns the name of the Site. */
    public String getName() { return name; }


    /** Sets the location of this Site. */
    public void setLocation( Location location ) { this.location = location; }

    /** Returns the location of this Site. */
    public Location getLocation() { return location; }



    /**
     * Represents the current state of the Site parameters and variables as a
     * String. Useful for debugging. Prints out the name, Location and all the
     * parameters in the list.
     *
     * @return name, location and all parameters as string
     */
    public String toString(){


        StringBuffer b = new StringBuffer();
        b.append(C);
        //b.append('\n');
        b.append(" : ");


        b.append("Name = ");
        b.append(name);
        //b.append('\n');
        b.append(" : ");

        b.append("Location = ");
        b.append(location.toString());
        //b.append('\n');
        b.append(" : ");

        b.append("Parameters = ");
        b.append( super.toString() );

        return b.toString();

    }

    
    @Override
    public boolean equals(Object obj) {
    	if (this == obj) return true;
    	if (!(obj instanceof Site)) return false;
    	Site s = (Site) obj;
    	if (name == null && s.name != null) return false;
    	if (name != null && !name.equalsIgnoreCase(s.name)) return false;
    	if (location == null && s.location != null) return false;
    	if (location != null && !location.equals(s.location)) return false;
    	if (size() != s.size()) return false;
    	for (Parameter<?> p1 : this) {
    		if (!s.containsParameter(p1.getName())) return false;
    		Parameter<?> p2 = s.getParameter(p1.getName());
    		if (!p1.equals(p2)) return false;
    	}
    	return true;
    	
//        // Check each individual Parameter
//        ListIterator it = this.getParametersIterator();
//        while(it.hasNext()){
//
//            // This list's parameter
//            Parameter param1 = (Parameter)it.next();
//
//            // List may not contain parameter with this list's parameter name
//            if ( !s.containsParameter(param1.getName()) ) return false;
//
//            // Found two parameters with same name, check equals, actually redundent,
//            // because that is what equals does
//            Parameter param2 = (Parameter) s.getParameter(param1.getName());
//            if( !param1.equals(param2) ) return false;
//
//            // Now try compare to to see if value the same, can fail if two values
//            // are different, or if the value object types are different
//            try{ if( param1.compareTo( param2 ) != 0 ) return false; }
//            catch(ClassCastException ee) { return false; }
//
//        }
//
//        return true;

    }


    /**
     * Returns a copy of this list, therefore any changes to the copy
     * cannot affect this original list. The name, Location and each
     * parameter is cloned. <p>
     *
     * Note: Cloning this object then calling equals() would return true.
     * They are different instances, but have the same values. Modifying
     * one would not affect the second. Equal yet disparet.<p>
     */
    public Object clone(){


        String S = C + ": clone(): ";

        Site site = new Site();
        site.setName( this.getName() );
        site.setLocation(location.clone());

        if( this.size() < 1 ) return site;

        int size = params.size();
        for(int i =0;i<size;++i) {

            Parameter param = (Parameter)params.get(i);
            site.addParameter( (Parameter)param.clone() );
        }

        return site;

    }

	public Element toXMLMetadata(Element root) {
		Element siteEl = root.addElement(XML_METADATA_NAME);
		siteEl = getLocation().toXMLMetadata(siteEl);
		Element paramsEl = siteEl.addElement(XML_PARAMS_NAME);
		ListIterator<Parameter<?>> paramIt = getParametersIterator();
		if (name != null && !name.isEmpty())
			siteEl.addAttribute("name", name);
		while (paramIt.hasNext()) {
			Parameter param = paramIt.next();
			paramsEl = param.toXMLMetadata(paramsEl);
		}
		return root;
	}
	
	public static Site fromXMLMetadata(Element siteEl, Iterable<Parameter<?>> paramsToAdd) {
		Element locEl = siteEl.element(Location.XML_METADATA_NAME);
		Location loc = Location.fromXMLMetadata(locEl);
		Site site = new Site(loc);
		
		Attribute nameAtt = siteEl.attribute("name");
		if (nameAtt != null)
			site.setName(nameAtt.getStringValue());
		
		for (Parameter param : paramsToAdd) {
			site.addParameter((Parameter)param.clone());
		}
		
		Element paramsEl = siteEl.element(XML_PARAMS_NAME);
		
		ParameterList.setParamsInListFromXML(site, paramsEl);
		
		return site;
	}
	
	public static Element writeSitesToXML(List<Site> sites, Element root) {
		Element sitesEl = root.addElement(XML_METADATA_LIST_NAME);
		for (Site site : sites) {
			sitesEl = site.toXMLMetadata(sitesEl);
		}
		return root;
	}
	
	public static ArrayList<Site> loadSitesFromXML(Element sitesEl, ArrayList<Parameter<?>> paramsToAdd) {
		Iterator<Element> it = sitesEl.elementIterator(XML_METADATA_NAME);
		
		System.out.println("Params to add:");
		for (Parameter<?> param : paramsToAdd)
			System.out.println(param.getName());
		
		ArrayList<Site> sites = new ArrayList<Site>();
		
		while (it.hasNext()) {
			Element siteEl = it.next();
			sites.add(fromXMLMetadata(siteEl, paramsToAdd));
		}
		
		return sites;
	}

}
