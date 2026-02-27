package org.opensha.sha.earthquake;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.dom4j.Element;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.geo.Region;
import org.opensha.commons.metadata.MetadataLoader;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.event.TimeSpanChangeListener;
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Class provides a basic implementation of an Earthquake RUpture FOrecast
 * (ERF).
 * 
 * @author Ned Field
 * @version $Id: AbstractERF.java 11385 2016-08-24 22:40:54Z kmilner $
 */
public abstract class AbstractERF implements
		ERF,
		TimeSpanChangeListener,
		ParameterChangeListener,
		XMLSaveable {

	private static final long serialVersionUID = 1L;

	/** Adjustable params for the forecast. */
	protected ParameterList adjustableParams = new ParameterList();
	
	/** DUration of the forecast. */
	protected TimeSpan timeSpan;

	/** Flag indiacting whether any parameter has changed. */
	protected boolean parameterChangeFlag = true;
	
	/** fields for nth rupture info */
	protected int totNumRups=-1;
	protected ArrayList<int[]> nthRupIndicesForSource;	// this gives the nth indices for a given source
	protected int[] srcIndexForNthRup;
	protected int[] rupIndexForNthRup;


	/**
	 * Get the region for which this forecast is applicable
	 * @return : Geographic region object specifying the applicable region of forecast
	 */
	public Region getApplicableRegion() {
		return null;
	}

	/**
	 * This function returns the parameter with specified name from adjustable param list
	 * @param paramName : Name of the parameter needed from adjustable param list
	 * @return : ParamterAPI instance
	 */
	@SuppressWarnings("rawtypes")
	public Parameter getParameter(String paramName) {
		return getAdjustableParameterList().getParameter(paramName);
	}

	/**
	 * set the TimeSpan in the ERF
	 * @param timeSpan : TimeSpan object
	 */
	public void setTimeSpan(TimeSpan time) {
		// set the start time
		if (!time.getStartTimePrecision().equalsIgnoreCase(TimeSpan.NONE))
			this.timeSpan.setStartTime(time.getStartTimeCalendar());
		//set the duration as well
		this.timeSpan.setDuration(time.getDuration(), time.getDurationUnits());
	}

	/**
	 * return the time span object
	 *
	 * @return : time span object is returned which contains start time and duration
	 */
	public TimeSpan getTimeSpan() {
		return this.timeSpan;
	}

	@SuppressWarnings("unchecked")
	public void setParameter(String name, Object value){
		setParameter(this, name, value);
	}
	
	/**
	 * This method can be used by all other ERF implementations;
	 * 
	 * Loops over all the adjustable parameters and set parameter with the given
	 * name to the given value.
	 * First checks if the parameter is contained within the ERF adjustable parameter
	 * list or TimeSpan adjustable parameters list. If not then IllegalArgumentException is thrown.
	 * @param erf
	 * @param name String Name of the Adjustable Parameter
	 * @param value Object Parameeter Value
	 * @throws IllegalArgumentException if ERF doesn't contain parameter.
	 * value.
	 */
	public static void setParameter(BaseERF erf, String name, Object value) {
		TimeSpan timeSpan = erf.getTimeSpan();
		if(erf.getAdjustableParameterList().containsParameter(name))
			erf.getAdjustableParameterList().getParameter(name).setValue(value);
		else if(timeSpan.getAdjustableParams().containsParameter(name))
			timeSpan.getAdjustableParams().getParameter(name).setValue(value);
		else 
			throw new IllegalArgumentException("Parameter '"+name+"' not present in ERF adjustable parameter list!");
	}

	/**
	 *  Function that must be implemented by all Timespan Listeners for
	 *  ParameterChangeEvents.
	 *
	 * @param  event  The Event which triggered this function call
	 */
	public void timeSpanChange(EventObject event) {
		parameterChangeFlag = true;
	}



	/**
	 *  This is the main function of this interface. Any time a control
	 *  paramater or independent paramater is changed by the user in a GUI this
	 *  function is called, and a paramater change event is passed in.
	 *
	 *  This sets the flag to indicate that the sources need to be updated
	 *
	 * @param  event
	 */
	public void parameterChange(ParameterChangeEvent event) {
		parameterChangeFlag = true;
	}

	/**
	 * Get number of ruptures for source at index iSource
	 * This method iterates through the list of 3 vectors for charA , charB and grB
	 * to find the the element in the vector to which the source corresponds
	 * @param iSource index of source whose ruptures need to be found
	 */
	public int getNumRuptures(int iSource) {
		return getSource(iSource).getNumRuptures();
	}

	/**
	 * Get the ith rupture of the source. this method DOES NOT return reference
	 * to the object. So, when you call this method again, result from previous
	 * method call is valid. This behavior is in contrast with
	 * getRupture(int source, int i) method
	 *
	 * @param source
	 * @param i
	 * @return
	 */
	public ProbEqkRupture getRuptureClone(int iSource, int nRupture) {
		return getSource(iSource).getRuptureClone(nRupture);
	}

	/**
	 * Get the ith rupture of the source. this method DOES NOT return reference
	 * to the object. So, when you call this method again, result from previous
	 * method call is valid. This behavior is in contrast with
	 * getRupture(int source, int i) method
	 *
	 * @param source
	 * @param i
	 * @return
	 */
	public ProbEqkRupture getRupture(int iSource, int nRupture) {
		return getSource(iSource).getRupture(nRupture);
	}

	/**
	 * Return the earthquake source at index i. This methos DOES NOT return the
	 * reference to the class variable. So, when you call this method again,
	 * result from previous method call is still valid. This behavior is in contrast
	 * with the behavior of method getSource(int i)
	 *
	 * @param iSource : index of the source needed
	 *
	 * @return Returns the ProbEqkSource at index i
	 *
	 * FIX:FIX :: This function has not been implemented yet. Have to give a thought on that
	 *
	 */
	public ProbEqkSource getSourceClone(int iSource) {
		return null;
	}

	/**
	 *
	 * @return the adjustable ParameterList for the ERF
	 */
	public ParameterList getAdjustableParameterList() {
		return this.adjustableParams;
	}

	/**
	 * sets the value for the parameter change flag
	 * @param flag
	 */
	public void setParameterChangeFlag(boolean flag) {
		this.parameterChangeFlag = flag;
	}

	public static final String XML_METADATA_NAME = "ERF";
	
	protected static void baseERF_ToXML(BaseERF erf, Element erfEl) {
		erfEl.addAttribute("className", erf.getClass().getName());
		ListIterator<Parameter<?>> paramIt = erf.getAdjustableParameterList().getParametersIterator();
		Element paramsElement = erfEl.addElement(AbstractParameter.XML_GROUP_METADATA_NAME);
		while (paramIt.hasNext()) {
			Parameter<?> param = paramIt.next();
			paramsElement = param.toXMLMetadata(paramsElement);
		}
		erfEl = erf.getTimeSpan().toXMLMetadata(erfEl);
	}
	
	protected static BaseERF baseERF_FromXML(Element el) throws InvocationTargetException {
		String className = el.attribute("className").getValue();
//		System.out.println("Loading ERF: " + className);
		BaseERF erf = (BaseERF)MetadataLoader.createClassInstance(className);

		// add params
//		System.out.println("Setting params...");
		Element paramsElement = el.element(AbstractParameter.XML_GROUP_METADATA_NAME);
		ParameterList.setParamsInListFromXML(erf.getAdjustableParameterList(), paramsElement);

		erf.setTimeSpan(TimeSpan.fromXMLMetadata(el.element("TimeSpan")));
		
		return erf;
	}

	public Element toXMLMetadata(Element root) {
		Element xml = root.addElement(AbstractERF.XML_METADATA_NAME);
		
		baseERF_ToXML(this, xml);

		return root;
	}
	
	

	public static AbstractERF fromXMLMetadata(Element el) throws InvocationTargetException {
		return (AbstractERF)baseERF_FromXML(el);
	}

	/**
	 * This draws a random event set.  Non-poisson sources are not yet implemented
	 * @return
	 */
	public ArrayList<EqkRupture> drawRandomEventSet(Site site, Collection<SourceFilter> sourceFilters) {
		boolean doFilter = site != null && sourceFilters != null && !sourceFilters.isEmpty();
		ArrayList<EqkRupture> rupList = new ArrayList<EqkRupture>();
		for(int s=0; s<this.getNumSources(); s++) {
			ProbEqkSource source = getSource(s);
			if (site != null && source instanceof SiteAdaptiveSource)
				source = ((SiteAdaptiveSource)source).getForSite(site);
			if (doFilter) {
				boolean skip = false;
				double dist = source.getMinDistance(site);
				for (SourceFilter filter : sourceFilters) {
					if (filter.canSkipSource(source, site, dist)) {
						skip = true;
						break;
					}
				}
				if (skip)
					continue;
				ArrayList<ProbEqkRupture> rups = source.drawRandomEqkRuptures();
				for (ProbEqkRupture rup : rups) {
					skip = false;
					for (SourceFilter filter : sourceFilters) {
						if (filter.canSkipRupture(rup, site)) {
							skip = true;
							break;
						}
					}
					if (!skip)
						rupList.add(rup);
				}
			} else {
				rupList.addAll(source.drawRandomEqkRuptures());
			}
		}
		return rupList;
	}

	/**
	 * This specifies what types of Tectonic Regions are included in the ERF.
	 * This default implementation includes only ACTIVE_SHALLOW, so it should 
	 * be overridden in subclasses if other types are used
	 * @return : ArrayList<TectonicRegionType>
	 */
	public ArrayList<TectonicRegionType> getIncludedTectonicRegionTypes(){
		ArrayList<TectonicRegionType> list = new ArrayList<TectonicRegionType>();
		list.add(TectonicRegionType.ACTIVE_SHALLOW);
		return list;
	}

	@Override
	public int compareTo(BaseERF o) {
		return getName().compareToIgnoreCase(o.getName());
	}
	
	@Override
	public List<ProbEqkSource> getSourceList() {
		ArrayList<ProbEqkSource> list = new ArrayList<ProbEqkSource>();
		for(int s=0;s<getNumSources();s++)
			list.add(this.getSource(s));
		return list;
		
	}
	
	@Override
	public Iterator<ProbEqkSource> iterator() {
//		return getSourceList().iterator();
		return new Iterator<ProbEqkSource>() {
			
			private int index = 0;

			@Override
			public boolean hasNext() {
				return index < getNumSources();
			}

			@Override
			public ProbEqkSource next() {
				return getSource(index++);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException("Not supported by this iterator");
			}
		};
	}	

}
