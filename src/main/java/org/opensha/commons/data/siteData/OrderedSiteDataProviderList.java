package org.opensha.commons.data.siteData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.dom4j.Document;
import org.dom4j.Element;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.siteData.impl.US_3secTopography;
import org.opensha.commons.data.siteData.impl.CS_Study18_8_BasinDepth;
import org.opensha.commons.data.siteData.impl.CVM2BasinDepth;
import org.opensha.commons.data.siteData.impl.CVM4BasinDepth;
import org.opensha.commons.data.siteData.impl.CVM4i26BasinDepth;
import org.opensha.commons.data.siteData.impl.CVM4i26_M01_TaperBasinDepth;
import org.opensha.commons.data.siteData.impl.CVMHBasinDepth;
import org.opensha.commons.data.siteData.impl.CVM_CCAi6BasinDepth;
import org.opensha.commons.data.siteData.impl.CVM_Vs30;
import org.opensha.commons.data.siteData.impl.MeanTopoSlope;
import org.opensha.commons.data.siteData.impl.SRTM30PlusTopoSlope;
import org.opensha.commons.data.siteData.impl.SRTM30PlusTopography;
import org.opensha.commons.data.siteData.impl.SRTM30TopoSlope;
import org.opensha.commons.data.siteData.impl.SRTM30Topography;
import org.opensha.commons.data.siteData.impl.ThompsonVs30_2018;
import org.opensha.commons.data.siteData.impl.USGSBayAreaBasinDepth;
import org.opensha.commons.data.siteData.impl.USGS_SFBay_BasinDepth_v21p1;
import org.opensha.commons.data.siteData.impl.WaldAllenGlobalVs30;
import org.opensha.commons.data.siteData.impl.WillsMap2000;
import org.opensha.commons.data.siteData.impl.WillsMap2000TranslatedVs30;
import org.opensha.commons.data.siteData.impl.WillsMap2006;
import org.opensha.commons.data.siteData.impl.WillsMap2015;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.XMLUtils;

import com.google.common.base.Preconditions;

public class OrderedSiteDataProviderList implements Iterable<SiteData<?>>, XMLSaveable, Cloneable {
	
	public static final String XML_METADATA_NAME = "OrderedSiteDataProviderList";
	
	// ordered list of providers...0 is highest priority
	private ArrayList<SiteData<?>> providers;
	private ArrayList<Boolean> enabled = new ArrayList<Boolean>();
	private boolean checkValues = true;
	
	private ArrayList<ChangeListener> changeListeners = null;
	
	public OrderedSiteDataProviderList(ArrayList<SiteData<?>> providers) {
		this.providers = providers;
		for (int i=0; i<providers.size(); i++)
			enabled.add(true);
	}
	
	/**
	 * Returns the best provider with data for the given location, or null if no
	 * provider is suitable for the given location.
	 * 
	 * @return
	 */
	public SiteData<?> getProviderForLocation(Location loc) {
		for (int i=0; i<providers.size(); i++) {
			if (!this.isEnabled(i))
				continue;
			SiteData<?> data = providers.get(i);
			
			Region region = data.getApplicableRegion();
			// skip this one if the site's not in it's applicable region
			if (data.hasDataForLocation(loc, checkValues)) {
				return data;
			}
		}
		return null;
	}
	
	/**
	 * Returns the best data value for the given location, with metadata
	 * 
	 * @param loc
	 * @return
	 * @throws IOException
	 */
	public SiteDataValue<?> getPreferredValue(Location loc) throws IOException {
		for (int i=0; i<providers.size(); i++) {
			if (!this.isEnabled(i))
				continue;
			SiteData provider = providers.get(i);
			
			SiteDataValue<?> val = this.getCheckedDataFromProvider(provider, loc);
			
			if (val != null)
				return val;
		}
		return null;
	}
	
	private SiteDataValue<?> getCheckedDataFromProvider(SiteData provider, Location loc) throws IOException {
		return getCheckedDataFromProvider(provider, loc, checkValues);
	}
	
	private SiteDataValue<?> getCheckedDataFromProvider(SiteData provider, Location loc, boolean checkValid) throws IOException {
		if (provider.hasDataForLocation(loc, false)) {
			SiteDataValue<?> val = provider.getAnnotatedValue(loc);
			if (!checkValid || provider.isValueValid(val.getValue())) {
				return val;
			}
		}
		return null;
	}
	
	public ArrayList<SiteDataValueList<?>> getAllAvailableData(List<Site> sites) throws IOException {
		LocationList locs = new LocationList();
		for (Site site : sites) {
			locs.add(site.getLocation());
		}
		return getAllAvailableData(locs);
	}
	
	public ArrayList<SiteData<?>> getEnabledProviders() {
		ArrayList<SiteData<?>> en = new ArrayList<SiteData<?>>();
		for (int i=0; i<providers.size(); i++)
			if (isEnabled(i))
				en.add(providers.get(i));
		return en;
	}
	
	public ArrayList<SiteDataValueList<?>> getAllAvailableData(LocationList locs) throws IOException {
		ArrayList<SiteDataValueList<?>> datas = new ArrayList<SiteDataValueList<?>>();
		
		for (SiteData<?> prov : getEnabledProviders()) {
			
			datas.add(prov.getAnnotatedValues(locs));
		}
		
		return datas;
	}
	
	/**
	 * This method returns a list of the data from every enabled provider
	 * 
	 * @param loc
	 * @return
	 */
	public ArrayList<SiteDataValue<?>> getAllAvailableData(Location loc) {
		ArrayList<SiteDataValue<?>> vals = new ArrayList<SiteDataValue<?>>();
		
		for (int i=0; i<providers.size(); i++) {
			if (!this.isEnabled(i))
				continue;
			SiteData<?> provider = providers.get(i);
			try {
				SiteDataValue<?> val = provider.getAnnotatedValue(loc);
				if (val != null) {
					vals.add(val);
				}
			} catch (IOException e) {
				throw new RuntimeException("IOException with "+provider.getShortName(), e);
//				System.out.println("IOException...skipping provider: " + provider.getShortName());
//				continue;
			}
		}
		
		return vals;
	}
	
	/**
	 * This method returns a list of the best available data for this location,
	 * where "best" is defined by the order of this provider list.
	 * 
	 * The result will have, at most, one of each site data type (so, for example,
	 * if there are multiple Vs30 sources, only the "best" one will be used).
	 * 
	 * @param loc
	 * @return
	 */
	public ArrayList<SiteDataValue<?>> getBestAvailableData(Location loc) {
		return doGetBestAvailableData(loc, null);
	}
	
	/**
	 * This method returns a list of the best available data for this location,
	 * where "best" is defined by the order of this provider list.
	 * 
	 * The result will return the best data value of the given type, or null
	 * if no match.
	 * 
	 * @param loc
	 * @param dataType
	 * @return
	 */
	public SiteDataValue<?> getBestAvailableData(Location loc, String dataType) {
		Preconditions.checkNotNull(dataType, "Must specify data type");
		List<SiteDataValue<?>> vals = doGetBestAvailableData(loc, dataType);
		if (vals.isEmpty())
			return null;
		Preconditions.checkState(vals.size() == 1);
		return vals.get(0);
	}
	
	private ArrayList<SiteDataValue<?>> doGetBestAvailableData(Location loc, String dataType) {
		ArrayList<SiteDataValue<?>> vals = new ArrayList<SiteDataValue<?>>();
		ArrayList<String> completedTypes = new ArrayList<String>();
		
		for (int i=0; i<providers.size(); i++) {
			if (!this.isEnabled(i))
				continue;
			SiteData<?> provider = providers.get(i);
			String type = provider.getDataType();
			if (dataType != null && !dataType.equals(type))
				continue;
			// if we already have this data type, then skip it
			if (completedTypes.contains(type))
				continue;
			
			try {
				SiteDataValue<?> val = this.getCheckedDataFromProvider(provider, loc);
				if (val != null) {
					vals.add(val);
					completedTypes.add(type);
				}
			} catch (IOException e) {
				throw new RuntimeException("IOException with "+provider.getShortName(), e);
//				System.out.println("IOException...skipping provider: " + provider.getShortName());
//				continue;
			}
		}
		
		return vals;
	}
	
	public void enableOnlyFirstForEachType() {
		ArrayList<String> doneTypes = new ArrayList<String>();
		
		for (int i=0; i<size(); i++) {
			String type = getProvider(i).getDataType();
			boolean enabled = true;
			for (String oldType : doneTypes) {
				if (oldType.equals(type)) {
					enabled = false;
					break;
				}
			}
			this.setEnabled(i, enabled);
			if (enabled)
				doneTypes.add(type);
		}
		fireChangeEvent();
	}
	
	public int size() {
		return providers.size();
	}
	
	public ArrayList<SiteData<?>> getList() {
		ArrayList<SiteData<?>> list = new ArrayList<SiteData<?>>();
		for (int i=0; i<providers.size(); i++) {
			if (!this.isEnabled(i))
				continue;
			SiteData<?> data = providers.get(i);
			list.add(data);
		}
		return list;
	}
	
	public int getIndexOf(SiteData<?> data) {
		return providers.indexOf(data);
	}
	
	public SiteData<?> remove(int index) {
		this.enabled.remove(index);
		return this.providers.remove(index);
	}
	
	public void add(SiteData<?> data) {
		this.providers.add(data);
		this.enabled.add(true);
	}
	
	public void add(int index, SiteData<?> data) {
		this.providers.add(index, data);
		this.enabled.add(index, true);
	}
	
	public void set(int index, SiteData<?> data) {
		this.providers.set(index, data);
		fireChangeEvent();
	}
	
	public void promote(int index) {
		this.swap(index, index - 1);
		fireChangeEvent();
	}
	
	public void demote(int index) {
		this.swap(index, index + 1);
		fireChangeEvent();
	}
	
	public void swap(int index1, int index2) {
		SiteData<?> one = providers.get(index1);
		boolean enabledOne = enabled.get(index1);
		SiteData<?> two = providers.get(index2);
		boolean enabledTwo = enabled.get(index2);
		
		providers.set(index1, two);
		providers.set(index2, one);
		
		enabled.set(index1, Boolean.valueOf(enabledTwo));
		enabled.set(index2, Boolean.valueOf(enabledOne));
	}
	
	public SiteData<?> getProvider(int index) {
		return providers.get(index);
	}
	
	public boolean isEnabled(int index) {
		return enabled.get(index);
	}
	
	public void setEnabled(int index, boolean enabled) {
		this.enabled.set(index, enabled);
		fireChangeEvent();
	}
	
	public boolean isAtLeastOneEnabled() {
		for (Boolean en : enabled) {
			if (en)
				return true;
		}
		return false;
	}

	public Iterator<SiteData<?>> iterator() {
		return providers.iterator();
	}
	
	/**
	 * Creates the default list of site data providers:
	 * 
	 * <UL>
	 * <LI> 1. Wills 2006 (servlet access)
	 * <LI> 2. Topographic Slope Vs30 (Wald and Allen 2007/2008) (servlet access)
	 * <LI> 3. CVM 4 Iteration 26 Depth 2.5 (servlet access)
	 * <LI> 4. CVM 4 Iteration 26 Depth 1.0 (servlet access)
	 * <LI> 5. CVM CCA Iteration 6 Depth 2.5 (servlet access)
	 * <LI> 6. CVM CCA Iteration 6 Depth 1.0 (servlet access)
	 * <LI> 7. CVM 4 Depth 2.5 (servlet access)
	 * <LI> 8. CVM 4 Depth 1.0 (servlet access)
	 * <LI> 9. CVM H 11.9.1 Depth 2.5
	 * <LI> 10. CVM H 11.9.1 Depth 1.0
	 * <LI> 11. USGS Bay Area Depth 2.5 (servlet access)
	 * <LI> 12. USGS Bay Area Depth 1.0 (servlet access)
	 * <LI> 13. Vs30 from CVMs (servlet access)
	 * <LI> 14. CVM 2 Depth 2.5 (servlet access)
	 * </UL>
	 * 
	 * @return
	 */
	public static OrderedSiteDataProviderList createSiteDataProviderDefaults() {
		ArrayList<SiteData<?>> providers = new ArrayList<SiteData<?>>();
		
		/*		Wills 2015			*/
		try {
			providers.add(new WillsMap2015());
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		Thompson 2018		*/
		try {
			providers.add(new ThompsonVs30_2018());
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		Wills 2006			*/
		try {
			providers.add(new WillsMap2006());
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		Topographic Slope	*/
		try {
			providers.add(new WaldAllenGlobalVs30());
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CVM 4i26 Depth 2.5		*/
		try {
			providers.add(new CVM4i26BasinDepth(SiteData.TYPE_DEPTH_TO_2_5));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CVM 4i26 Depth 1.0		*/
		try {
			providers.add(new CVM4i26BasinDepth(SiteData.TYPE_DEPTH_TO_1_0));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CVM CCA i6 Depth 2.5		*/
		try {
			providers.add(new CVM_CCAi6BasinDepth(SiteData.TYPE_DEPTH_TO_2_5));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CVM CCA i6 Depth 1.0		*/
		try {
			providers.add(new CVM_CCAi6BasinDepth(SiteData.TYPE_DEPTH_TO_1_0));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CVM 4 Depth 2.5		*/
		try {
			providers.add(new CVM4BasinDepth(SiteData.TYPE_DEPTH_TO_2_5));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CVM 4 Depth 1.0		*/
		try {
			providers.add(new CVM4BasinDepth(SiteData.TYPE_DEPTH_TO_1_0));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CVM H Depth 2.5		*/
		try {
			providers.add(new CVMHBasinDepth(SiteData.TYPE_DEPTH_TO_2_5));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CVM H Depth 1.0		*/
		try {
			providers.add(new CVMHBasinDepth(SiteData.TYPE_DEPTH_TO_1_0));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CCA i6 Depth 2.5		*/
		try {
			providers.add(new CVM_CCAi6BasinDepth(SiteData.TYPE_DEPTH_TO_2_5));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CCA i6 Depth 1.0		*/
		try {
			providers.add(new CVM_CCAi6BasinDepth(SiteData.TYPE_DEPTH_TO_1_0));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		USGS SF Bay Area v21.1 Depth 2.5		*/
		try {
			providers.add(new USGS_SFBay_BasinDepth_v21p1(SiteData.TYPE_DEPTH_TO_2_5));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		USGS SF Bay Area v21.1 Depth 1.0		*/
		try {
			providers.add(new USGS_SFBay_BasinDepth_v21p1(SiteData.TYPE_DEPTH_TO_1_0));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		USGS Bay Area Depth 2.5		*/
		try {
			providers.add(new USGSBayAreaBasinDepth(SiteData.TYPE_DEPTH_TO_2_5));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		USGS Bay Area Depth 1.0		*/
		try {
			providers.add(new USGSBayAreaBasinDepth(SiteData.TYPE_DEPTH_TO_1_0));
		} catch (IOException e) {
			e.printStackTrace();
		}
		int numEnabled = providers.size();
		// below here disabled by default
		/*		Vs30 from CVMs		*/
		try {
			providers.add(new CVM_Vs30(CVM_Vs30.CVM_DEFAULT));
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		CVM 2 Depth 2.5		*/
		try {
			providers.add(new CVM2BasinDepth());
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*		Wills 2000			*/
		providers.add(new WillsMap2000());
		
		OrderedSiteDataProviderList list = new OrderedSiteDataProviderList(providers);
		for (int i=numEnabled; i<list.size(); i++)
			list.setEnabled(i, false);
		
		return list;
	}
	
	/**
	 * Creates the default list of site data providers, but with wills classes translated to doubles so that they can
	 * be mapped.
	 * 
	 * @return
	 */
	public static OrderedSiteDataProviderList createSiteDataMapProviders() {
		ArrayList<SiteData<?>> providers = createDebugSiteDataProviders().getList();
		
		for (int i=0; i<providers.size(); i++) {
			if (providers.get(i).getName() == WillsMap2000.NAME) {
				WillsMap2000TranslatedVs30 wills = new WillsMap2000TranslatedVs30();
				providers.set(i, wills);
				break;
			}
		}
		
		try {
			providers.add(new US_3secTopography());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			providers.add(new SRTM30PlusTopography());
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			providers.add(new SRTM30PlusTopoSlope());
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			providers.add(new SRTM30Topography());
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			providers.add(new SRTM30TopoSlope());
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			providers.add(new MeanTopoSlope());
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			providers.add(new CS_Study18_8_BasinDepth(SiteData.TYPE_DEPTH_TO_2_5));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			providers.add(new CS_Study18_8_BasinDepth(SiteData.TYPE_DEPTH_TO_1_0));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			providers.add(new CVM4i26_M01_TaperBasinDepth(SiteData.TYPE_DEPTH_TO_2_5));
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			providers.add(new CVM4i26_M01_TaperBasinDepth(SiteData.TYPE_DEPTH_TO_1_0));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return new OrderedSiteDataProviderList(providers);
	}
	
	/**
	 * Same as createSiteDataProviderDefaults, but returns a cached version of each one
	 * 
	 * @return
	 */
	public static OrderedSiteDataProviderList createCachedSiteDataProviderDefaults() {
		OrderedSiteDataProviderList list = createSiteDataProviderDefaults();
		for (int i=0; i<list.size(); i++) {
			CachedSiteDataWrapper<?> cached = new CachedSiteDataWrapper(list.getProvider(i));
			list.set(i, cached);
		}
		return list;
	}
	
	/**
	 * Creates a list with just Vs30 from Wills and Depth to 2.5 for compatibility with the old
	 * pieces of code which were hardcoded to only use those 2. 
	 * 
	 * @return
	 */
	public static OrderedSiteDataProviderList createCompatibilityProviders(boolean useOldData) {
		ArrayList<SiteData<?>> providers = new ArrayList<SiteData<?>>();
		
		if (useOldData) {
			/*		Wills 2000			*/
			providers.add(new WillsMap2000());
			/*		CVM 2				*/
			try {
				providers.add(new CVM2BasinDepth());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			/*		Wills 2006			*/
			try {
				providers.add(new WillsMap2006());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			/*		CVM 4				*/
			try {
				providers.add(new CVM4BasinDepth(SiteData.TYPE_DEPTH_TO_2_5));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		
		return new OrderedSiteDataProviderList(providers);
	}
	
	/**
	 * Creates the debugging list of site data providers:
	 * 
	 * @return
	 */
	public static OrderedSiteDataProviderList createDebugSiteDataProviders() {
		return createSiteDataProviderDefaults();
	}
	
	public void printList() {
		int size = size();
		for (int i=0; i<size; i++) {
			SiteData<?> provider = this.getProvider(i);
			boolean enabled = this.isEnabled(i);
			
			if (enabled)
				System.out.println(i + ". " + provider);
			else
				System.out.println(i + ". <disabled> " + provider);
		}
	}
	
	/**
	 * Removes all providers that are currently disabled
	 */
	public void removeDisabledProviders() {
		ArrayList<SiteData<?>> newProvs = new ArrayList<SiteData<?>>();
		ArrayList<Boolean> newEnabled = new ArrayList<Boolean>();
		
		for (int i=0; i<providers.size(); i++) {
			if (this.isEnabled(i)) {
				newProvs.add(providers.get(i));
				newEnabled.add(Boolean.valueOf(true));
			}
		}
		
		this.providers = newProvs;
		this.enabled = newEnabled;
		fireChangeEvent();
	}
	
	public String toString() {
		String str =  "OrderedSiteDataProviderList - " + size() + " providers.\n";
		for (int i=0; i<size(); i++) {
			SiteData<?> prov = this.getProvider(i);
			String enabledStr;
			if (isEnabled(i))
				enabledStr = " (enabled)";
			else
				enabledStr = " (disabled)";
			str += "\n" + i + ". " + prov.getName() + ": " + prov.getDataType() + enabledStr;
		}
		return str;
	}
	
	public void addChangeListener(ChangeListener listener) {
		if (changeListeners == null)
			changeListeners = new ArrayList<ChangeListener>();
		if (!changeListeners.contains(listener))
			changeListeners.add(listener);
	}
	
	private void fireChangeEvent() {
		if (changeListeners == null || changeListeners.size() == 0)
			return;
		ChangeEvent e = new ChangeEvent(this);
		for (ChangeListener listener : changeListeners) {
			listener.stateChanged(e);
		}
	}
	
	/**
	 * Makes a shallow copy of this OrderedSiteDataProviderList
	 */
	@Override
	public OrderedSiteDataProviderList clone() {
		ArrayList<SiteData<?>> list = (ArrayList<SiteData<?>>)this.providers.clone();
		
		OrderedSiteDataProviderList newList = new OrderedSiteDataProviderList(list);
		
		for (int i=0; i<this.size(); i++) {
			newList.setEnabled(i, this.isEnabled(i));
		}
		
		return newList;
	}

	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		Element listEl = el.addElement("ProviderList");
		
		for (int i=0; i<size(); i++) {
			SiteData<?> provider = this.getProvider(i);
			
			Element provEl = listEl.addElement("Provider");
			provEl.addAttribute("Priority", i + "");
			provEl.addAttribute("Enabled", this.isEnabled(i) + "");
			
			provider.toXMLMetadata(provEl);
		}
		
		return root;
	}
	
	public static OrderedSiteDataProviderList fromXMLMetadata(Element orderedListEl) throws IOException {
		Element listEl = orderedListEl.element("ProviderList");
		
		Iterator<Element> it = listEl.elementIterator();
		
		ArrayList<SiteData<?>> providers = new ArrayList<SiteData<?>>();
		ArrayList<Boolean> enableds = new ArrayList<Boolean>();
		ArrayList<Integer> priorities = new ArrayList<Integer>();
		
		while (it.hasNext()) {
			Element provEl = it.next();
			int priority = Integer.parseInt(provEl.attributeValue("Priority"));
			boolean enabled = Boolean.parseBoolean(provEl.attributeValue("Enabled"));
			Element subEl = provEl.element(SiteData.XML_METADATA_NAME);
			
			SiteData<?> provider = AbstractSiteData.fromXMLMetadata(subEl);
			
			providers.add(provider);
			priorities.add(priority);
			enableds.add(enabled);
		}
		
		ArrayList<SiteData<?>> ordered = new ArrayList<SiteData<?>>();
		ArrayList<Boolean> orderedEnableds = new ArrayList<Boolean>();
		
		for (int i=0; i<providers.size(); i++) {
			SiteData<?> provider = null;
			boolean enabled = false;
			for (int j=0; j<priorities.size(); j++) {
				int priority = priorities.get(j);
				if (priority == i) {
					provider = providers.get(j);
					enabled = enableds.get(j);
					break;
				}
			}
			
			if (provider ==  null) {
				throw new RuntimeException("Malformed list!");
			}
			
			ordered.add(provider);
			orderedEnableds.add(enabled);
		}
		
		OrderedSiteDataProviderList list = new OrderedSiteDataProviderList(ordered);
		
		for (int i=0; i<orderedEnableds.size(); i++) {
			list.setEnabled(i, orderedEnableds.get(i));
		}
		
		return list;
	}
	
	/**
	 * This will merge a new list with the current list. If duplicates are found, the settings
	 * from the new list will be used (parameters, as well as enabled state).
	 * 
	 * @param newList
	 */
	public void mergeWith(OrderedSiteDataProviderList newList) {
		ArrayList<SiteData<?>> toAdd = new ArrayList<SiteData<?>>();
		
		for (int j=0; j<newList.size(); j++) {
			SiteData<?> newProv = newList.getProvider(j);
			boolean shouldAdd = true;
			for (int i=0; i<this.size(); i++) {
				SiteData<?> curProv = this.getProvider(i);
				if (curProv.getName().equals(newProv.getName())) {
					this.set(i, newProv);
					shouldAdd = false;
					this.setEnabled(i, newList.isEnabled(j));
					break;
				}
			}
			if (shouldAdd)
				this.add(newProv);
		}
	}
	
	public static void main(String args[]) throws IOException {
		System.out.println("Orig:");
		OrderedSiteDataProviderList list = createSiteDataProviderDefaults();
		list.setEnabled(0, false);
		list.setEnabled(3, false);
		list.printList();
		
		Document doc = XMLUtils.createDocumentWithRoot();
		Element root = doc.getRootElement();
		
		list.toXMLMetadata(root);
		
		Element el = root.element(XML_METADATA_NAME);
		
		XMLUtils.writeDocumentToFile(new File("/tmp/list.xml"), doc);
		
		list = OrderedSiteDataProviderList.fromXMLMetadata(el);
		
		System.out.println("After:");
		
		list.printList();
	}
}
