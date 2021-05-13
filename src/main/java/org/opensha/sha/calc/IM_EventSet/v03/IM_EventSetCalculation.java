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

package org.opensha.sha.calc.IM_EventSet.v03;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.geo.Location;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.imr.AbstractIMR;
import org.opensha.sha.imr.ScalarIMR;

public class IM_EventSetCalculation implements XMLSaveable {
	
	public static final String XML_METADATA_NAME = "IMEventSetCalculation";
	public static final String XML_ERFS_NAME = "ERFs";
	public static final String XML_IMRS_NAME = "IMRs";
	public static final String XML_IMTS_NAME = "IMTs";
	public static final String XML_IMT_String_NAME = "IMTString";
	public static final String XML_SITES_NAME = "Sites";
	public static final String XML_SITE_NAME = "Site";
	public static final String XML_SITE_DATA_VALS_NAME = "SiteDataValues";
	
	private ArrayList<Location> sites;
	private ArrayList<ArrayList<SiteDataValue<?>>> sitesData;
	private ArrayList<ERF> erfs;
	private ArrayList<ScalarIMR> attenRels;
	private ArrayList<String> imts;
	private OrderedSiteDataProviderList providers;
	
	public IM_EventSetCalculation(ArrayList<Location> sites, ArrayList<ArrayList<SiteDataValue<?>>> sitesData,
			ArrayList<ERF> erfs, ArrayList<ScalarIMR> attenRels,
			ArrayList<String> imts, OrderedSiteDataProviderList providers) {
		this.sites = sites;
		this.sitesData = sitesData;
		this.erfs = erfs;
		this.attenRels = attenRels;
		this.imts = imts;
		this.providers = providers;
	}

	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		// ERFs
		Element erfsEL = el.addElement(XML_ERFS_NAME);
		for (ERF erf : erfs) {
			if (erf instanceof XMLSaveable) {
				XMLSaveable xmlERF = (XMLSaveable)erf;
				xmlERF.toXMLMetadata(erfsEL);
			} else {
				throw new RuntimeException("ERF cannot to be saved to XML!");
			}
		}
		
		// IMRs
		Element imrsEL = el.addElement(XML_IMRS_NAME);
		for (ScalarIMR attenRel : attenRels) {
			attenRel.toXMLMetadata(imrsEL);
		}
		
		// IMTs
		Element imtsEL = el.addElement(XML_IMTS_NAME);
		for (String imt : imts) {
			Element imtEl = imtsEL.addElement(XML_IMT_String_NAME);
			imtEl.addAttribute("value", imt);
		}
		
		// Sites
		Element sitesEl = el.addElement(XML_SITES_NAME);
		for (int i=0; i<sites.size(); i++) {
			Element siteEl = sitesEl.addElement(XML_SITE_NAME);
			Location loc = sites.get(i);
			loc.toXMLMetadata(siteEl);
			ArrayList<SiteDataValue<?>> siteDatas = sitesData.get(i);
			if (siteDatas.size() > 0) {
				Element siteDatasEl = siteEl.addElement(XML_SITE_DATA_VALS_NAME);
				for (SiteDataValue<?> val : siteDatas) {
					val.toXMLMetadata(siteDatasEl);
				}
			}
		}
		
		// Providers
		providers.toXMLMetadata(el);
		
		return root;
	}
	
	public static IM_EventSetCalculation fromXMLMetadata(Element eventSetEl) {
		// ERFs
		Element erfsEl = eventSetEl.element(XML_ERFS_NAME);
		Iterator<Element> erfElIt = erfsEl.elementIterator();
		ArrayList<ERF> erfs = new ArrayList<ERF>();
		while (erfElIt.hasNext()) {
			Element erfEl = erfElIt.next();
			try {
				ERF erf = AbstractERF.fromXMLMetadata(erfEl);
				erfs.add(erf);
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// IMRs
		Element imrsEl = eventSetEl.element(XML_IMRS_NAME);
		Iterator<Element> imrElIt = imrsEl.elementIterator();
		ArrayList<ScalarIMR> imrs = new ArrayList<ScalarIMR>();
		while (imrElIt.hasNext()) {
			Element imrEl = imrElIt.next();
			try {
				ScalarIMR imr = (ScalarIMR) AbstractIMR.fromXMLMetadata(imrEl, null);
				imrs.add(imr);
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// IMTs
		Element imtsEl = eventSetEl.element(XML_IMTS_NAME);
		Iterator<Element> imtElIt = imtsEl.elementIterator();
		ArrayList<String> imts = new ArrayList<String>();
		while (imtElIt.hasNext()) {
			Element imtEl = imtElIt.next();
			String imt = imtEl.attributeValue("value");
			imts.add(imt);
		}
		
		// Sites
		Element sitesEl = eventSetEl.element(XML_SITES_NAME);
		Iterator<Element> siteElIt = sitesEl.elementIterator();
		ArrayList<Location> sites = new ArrayList<Location>();
		ArrayList<ArrayList<SiteDataValue<?>>> sitesData = new ArrayList<ArrayList<SiteDataValue<?>>>();
		while (siteElIt.hasNext()) {
			Element siteEl = siteElIt.next();
			Element locEl = siteEl.element(Location.XML_METADATA_NAME);
			Location loc = Location.fromXMLMetadata(locEl);
			ArrayList<SiteDataValue<?>> siteData = new ArrayList<SiteDataValue<?>>();
			Element dataEl = siteEl.element(XML_SITE_DATA_VALS_NAME);
			if (dataEl != null) {
				Iterator<Element> dataIt = dataEl.elementIterator();
				while (dataIt.hasNext()) {
					Element valEl = dataIt.next();
					SiteDataValue<?> val = SiteDataValue.fromXMLMetadata(valEl);
					siteData.add(val);
				}
			}
			sites.add(loc);
			sitesData.add(siteData);
		}
		
		// Data Providers
		Element providersEl = eventSetEl.element(OrderedSiteDataProviderList.XML_METADATA_NAME);
		OrderedSiteDataProviderList providers = null;
		if (providersEl != null) {
			try {
				providers = OrderedSiteDataProviderList.fromXMLMetadata(providersEl);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return new IM_EventSetCalculation(sites, sitesData, erfs, imrs, imts, providers);
	}

	public ArrayList<Location> getSites() {
		return sites;
	}

	public ArrayList<ArrayList<SiteDataValue<?>>> getSitesData() {
		return sitesData;
	}

	public ArrayList<ERF> getErfs() {
		return erfs;
	}

	public ArrayList<ScalarIMR> getIMRs() {
		return attenRels;
	}

	public ArrayList<String> getIMTs() {
		return imts;
	}
	
	public OrderedSiteDataProviderList getProviders() {
		return providers;
	}
	
	public static void main(String args[]) throws MalformedURLException, DocumentException {
		Document doc = XMLUtils.loadDocument("/tmp/im.xml");
		Element eventSetEl = doc.getRootElement().element(XML_METADATA_NAME);
		IM_EventSetCalculation calc = fromXMLMetadata(eventSetEl);
		
		for (ERF erf : calc.getErfs()) {
			System.out.println("Loaded ERF: " + erf.getName());
		}
		for (ScalarIMR imr : calc.getIMRs()) {
			System.out.println("Loaded IMR: " + imr.getName());
		}
		for (String imt : calc.getIMTs()) {
			System.out.println("Loaded IMT: " + imt);
		} 
		for (int i=0; i<calc.getSites().size(); i++) {
			Location loc = calc.getSites().get(i);
			System.out.println("Loaded site: " + loc.getLatitude() + "," + loc.getLongitude());
			ArrayList<SiteDataValue<?>> siteData = calc.getSitesData().get(i);
			for (SiteDataValue<?> val : siteData) {
				System.out.println("\t" + val.getDataType() + ": " + val.getValue() +
						" (" + val.getDataMeasurementType() + ")");
			}
		}
		for (SiteData<?> provider : calc.getProviders()) {
			System.out.println("Loaded Site Data Provider: " + provider.getName());
		}
	}

}
