package org.opensha.sha.gui.beans;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;

import javax.swing.JOptionPane;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.region.SitesInGriddedRegion;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValueList;
import org.opensha.commons.data.siteData.gui.beans.OrderedSiteDataGUIBean;
import org.opensha.commons.data.siteData.impl.WillsMap2000;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeFailEvent;
import org.opensha.commons.param.event.ParameterChangeFailListener;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.gui.infoTools.CalcProgressBar;
import org.opensha.sha.util.SiteTranslator;


/**
 * <p>Title:SitesInGriddedRectangularRegionGuiBean </p>
 * <p>Description: This creates the Gridded Region parameter Editor with Site Params
 * for the selected Attenuation Relationship in the Application.
 * </p>
 * @author Nitin Gupta & Vipin Gupta
 * @date March 11, 2003
 * @version 1.0
 */



public class SitesInGriddedRectangularRegionGuiBean extends ParameterListEditor implements
ParameterChangeFailListener, ParameterChangeListener, Serializable {

	// for debug purposes
	protected final static String C = "SiteParamList";

	/**
	 * Latitude and longitude are added to the site attenRelImplmeters
	 */
	public final static String MIN_LONGITUDE = "Min Longitude";
	public final static String MAX_LONGITUDE = "Max Longitude";
	public final static String MIN_LATITUDE =  "Min  Latitude";
	public final static String MAX_LATITUDE =  "Max  Latitude";
	public final static String GRID_SPACING =  "Grid Spacing";
	public final static String SITE_PARAM_NAME = "Set Site Params";

	public final static String DEFAULT = "Default  ";

	// title for site paramter panel
	public final static String GRIDDED_SITE_PARAMS = "Set Gridded Region Params";

	//Site Params ArrayList
	ArrayList<Parameter<?>> siteParams ;

	//Static String for setting the site Params
	public final static String SET_ALL_SITES = "Apply same site parameter(s) to all locations";
	public final static String USE_SITE_DATA = "Use site data providers";
	// these are kept for compatibility
	public final static String SET_SITE_USING_WILLS_SITE_TYPE = "Use the CGS Preliminary Site Conditions Map of CA (web service)";
	public final static String SET_SITES_USING_SCEC_CVM = "Use both CGS Map and SCEC Basin Depth (web services)";

	/**
	 * Longitude and Latitude parameters to be added to the site params list
	 */
	private DoubleParameter minLon = new DoubleParameter(MIN_LONGITUDE,
			Double.valueOf(-360), Double.valueOf(360),Double.valueOf(-119.5));
	private DoubleParameter maxLon = new DoubleParameter(MAX_LONGITUDE,
			Double.valueOf(-360), Double.valueOf(360),Double.valueOf(-117));
	private DoubleParameter minLat = new DoubleParameter(MIN_LATITUDE,
			Double.valueOf(-90), Double.valueOf(90), Double.valueOf(33.5));
	private DoubleParameter maxLat = new DoubleParameter(MAX_LATITUDE,
			Double.valueOf(-90), Double.valueOf(90), Double.valueOf(34.7));
	private DoubleParameter gridSpacing = new DoubleParameter(GRID_SPACING,
			Double.valueOf(.01666),Double.valueOf(1.0),new String("Degrees"),Double.valueOf(.1));


	//StringParameter to set site related params
	private StringParameter siteParam;

	//SiteTranslator
	SiteTranslator siteTrans = new SiteTranslator();

	private OrderedSiteDataGUIBean dataGuiBean;
	private OrderedSiteDataProviderList defaultProviderList = null;

	//instance of class EvenlyGriddedRectangularGeographicRegion
	private SitesInGriddedRegion sites;

	/**
	 * constuctor which builds up mapping between IMRs and their related sites
	 */
	public SitesInGriddedRectangularRegionGuiBean() {
		this(null);
	}

	/**
	 * constuctor which builds up mapping between IMRs and their related sites
	 */
	public SitesInGriddedRectangularRegionGuiBean(OrderedSiteDataGUIBean dataGuiBean) {
		this.dataGuiBean = dataGuiBean;

		if (dataGuiBean == null) {
			defaultProviderList = OrderedSiteDataProviderList.createCompatibilityProviders(false);
		}

		//defaultVs30.setInfo(this.VS30_DEFAULT_INFO);
		//parameterList.addParameter(defaultVs30);
		minLat.addParameterChangeFailListener(this);
		minLon.addParameterChangeFailListener(this);
		maxLat.addParameterChangeFailListener(this);
		maxLon.addParameterChangeFailListener(this);
		gridSpacing.addParameterChangeFailListener(this);

		//creating the String Param for user to select how to get the site related params
		ArrayList siteOptions = new ArrayList();
		siteOptions.add(SET_ALL_SITES);
		if (dataGuiBean == null) {
			siteOptions.add(SET_SITE_USING_WILLS_SITE_TYPE);
			siteOptions.add(SET_SITES_USING_SCEC_CVM);
		} else {
			siteOptions.add(USE_SITE_DATA);
		}
		siteParam = new StringParameter(SITE_PARAM_NAME,siteOptions,(String)siteOptions.get(0));
		siteParam.addParameterChangeListener(this);

		// add the longitude and latitude paramters
		parameterList = new ParameterList();
		parameterList.addParameter(minLon);
		parameterList.addParameter(maxLon);
		parameterList.addParameter(minLat);
		parameterList.addParameter(maxLat);
		parameterList.addParameter(gridSpacing);
		parameterList.addParameter(siteParam);
		editorPanel.removeAll();
		addParameters();
		createAndUpdateSites();

		try {
			jbInit();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}


	/**
	 * This function adds the site params to the existing list.
	 * Parameters are NOT cloned.
	 * If paramter with same name already exists, then it is not added
	 *
	 * @param it : Iterator over the site params in the IMR
	 */
	public void addSiteParams(Iterator it) {
		AbstractParameter tempParam;
		ArrayList siteTempVector= new ArrayList();
		while(it.hasNext()) {
			tempParam = (AbstractParameter)it.next();
			if(!parameterList.containsParameter(tempParam)) { // if this does not exist already
				parameterList.addParameter(tempParam);
				//adding the parameter to the vector,
				//ArrayList is used to pass the add the site parameters to the gridded region sites.
				siteTempVector.add(tempParam);
			}
		}
		//adding the Site Params to the ArrayList, so that we can add those later if we want to.
		siteParams = siteTempVector;
		sites.addSiteParams(siteTempVector.iterator());
		setSiteParamsVisible();
	}

	/**
	 * This function adds the site params to the existing list.
	 * Parameters are cloned.
	 * If paramter with same name already exists, then it is not added
	 *
	 * @param it : Iterator over the site params in the IMR
	 */
	public void addSiteParamsClone(Iterator it) {
		AbstractParameter tempParam;
		ArrayList v= new ArrayList();
		while(it.hasNext()) {
			tempParam = (AbstractParameter)it.next();
			if(!parameterList.containsParameter(tempParam)) { // if this does not exist already
				AbstractParameter cloneParam = (AbstractParameter)tempParam.clone();
				parameterList.addParameter(cloneParam);
				//adding the cloned parameter in the siteList.
				v.add(cloneParam);
			}
		}
		sites.addSiteParams(v.iterator());
		setSiteParamsVisible();
	}

	/**
	 * This function removes the previous site parameters and adds as passed in iterator
	 *
	 * @param it
	 */
	public void replaceSiteParams(Iterator it) {
		// first remove all the parameters except latitude and longitude
		Iterator siteIt = parameterList.getParameterNamesIterator();
		while(siteIt.hasNext()) { // remove all the parameters except latitude and longitude and gridSpacing
			String paramName = (String)siteIt.next();
			if(!paramName.equalsIgnoreCase(MIN_LATITUDE) &&
					!paramName.equalsIgnoreCase(MIN_LONGITUDE) &&
					!paramName.equalsIgnoreCase(MAX_LATITUDE) &&
					!paramName.equalsIgnoreCase(MAX_LONGITUDE) &&
					!paramName.equalsIgnoreCase(GRID_SPACING) &&
					!paramName.equalsIgnoreCase(SITE_PARAM_NAME))
				parameterList.removeParameter(paramName);
		}
		//removing the existing sites Params from the gridded Region sites
		sites.removeSiteParams();

		// now add all the new params
		addSiteParams(it);
	}



	/**
	 * gets the iterator of all the sites
	 *
	 * @return
	 */ // not currently used
//	public Iterator getAllSites() {
//		return gridRectRegion.getSitesIterator();
//	}


	/**
	 * get the clone of site object from the site params
	 *
	 * @return
	 */
	public Iterator<Site> getSitesClone() {
		Iterator<Location> lIt = sites.getRegion().getNodeList().iterator();
		ArrayList<Site> newSiteVector=new ArrayList<Site>();
		while(lIt.hasNext())
			newSiteVector.add(new Site(lIt.next()));

		ListIterator it  = parameterList.getParametersIterator();
		// clone the paramters
		while(it.hasNext()){
			Parameter tempParam= (Parameter)it.next();
			for(int i=0;i<newSiteVector.size();++i){
				if(!((Site)newSiteVector.get(i)).containsParameter(tempParam))
					((Site)newSiteVector.get(i)).addParameter((Parameter)tempParam.clone());
			}
		}
		return newSiteVector.iterator();
	}

	/**
	 * this function updates the GriddedRegion object after checking with the latest
	 * lat and lons and gridSpacing
	 * So, we update the site object as well
	 *
	 */
	private void updateGriddedSiteParams() {

		ArrayList v= new ArrayList();
		createAndUpdateSites();
		//getting the site params for the first element of the siteVector
		//becuase all the sites will be having the same site Parameter
		Iterator it = siteParams.iterator();
		while(it.hasNext())
			v.add(((Parameter)it.next()).clone());
		sites.addSiteParams(v.iterator());
	}



	/**
	 * Shown when a Constraint error is thrown on a ParameterEditor
	 *
	 * @param  e  Description of the Parameter
	 */
	public void parameterChangeFailed( ParameterChangeFailEvent e ) {


		String S = C + " : parameterChangeFailed(): ";



		StringBuffer b = new StringBuffer();

		Parameter param = ( Parameter ) e.getSource();


		ParameterConstraint constraint = param.getConstraint();
		String oldValueStr = e.getOldValue().toString();
		String badValueStr = e.getBadValue().toString();
		String name = param.getName();

		b.append( "The value ");
		b.append( badValueStr );
		b.append( " is not permitted for '");
		b.append( name );
		b.append( "'.\n" );
		b.append( "Resetting to ");
		b.append( oldValueStr );
		b.append( ". The constraints are: \n");
		b.append( constraint.toString() );

		JOptionPane.showMessageDialog(
				this, b.toString(),
				"Cannot Change Value", JOptionPane.INFORMATION_MESSAGE
		);
	}

	/**
	 * This function is called when value a parameter is changed
	 * @param e Description of the parameter
	 */
	public void parameterChange(ParameterChangeEvent e){
		Parameter param = ( Parameter ) e.getSource();

		if(param.getName().equals(SITE_PARAM_NAME))
			setSiteParamsVisible();
	}

	/**
	 * This method creates the gridded region with the min -max Lat and Lon
	 * It also checks if the Max Lat is less than Min Lat and
	 * Max Lat is Less than Min Lonb then it throws an exception.
	 * @return
	 */
	private void createAndUpdateSites() {

		double minLatitude= minLat.getValue();
		double maxLatitude= maxLat.getValue();
		double minLongitude=minLon.getValue();
		double maxLongitude=maxLon.getValue();
		//checkLatLonParamValues();
		GriddedRegion eggr = 
			new GriddedRegion(
					new Location(minLatitude,minLongitude),
					new Location(maxLatitude,maxLongitude),
					gridSpacing.getValue(),
					new Location(minLatitude, minLongitude));
//					new Location(0,0));
		sites= new SitesInGriddedRegion(eggr);
	}


	/**
	 *
	 * @return the object for the SitesInGriddedRectangularRegion class
	 */
	public SitesInGriddedRegion getGriddedRegionSite() {
		updateGriddedSiteParams();
		if(((String)siteParam.getValue()).equals(SET_ALL_SITES)) {
			//if the site params does not need to be set from the CVM
			sites.setSameSiteParams();
//			Site site = gridRectRegion.getSite(0);
//			ListIterator<ParameterAPI> it = site.getParametersIterator();
//			while (it.hasNext()) {
//				ParameterAPI param = it.next();
//				param.setValue(parameterList.getParameter(param.getName()).getValue());
//			}
		}

		//if the site Params needs to be set from the WILLS Site type and SCEC basin depth
		else{
			try{
				setSiteParamsFromCVM();
			}catch(Exception e){
				throw new RuntimeException("Server is down , please try again later", e);
			}
			ArrayList defaultSiteParams = new ArrayList();
			for(int i=0;i<siteParams.size();++i){
				Parameter tempParam = (Parameter)((Parameter)siteParams.get(i)).clone();
				tempParam.setValue(parameterList.getParameter(this.DEFAULT+tempParam.getName()).getValue());
				defaultSiteParams.add(tempParam);
			}
			sites.setDefaultSiteParams(defaultSiteParams);
		}
		return sites;
	}


	/**
	 * Make the site params visible depending on the choice user has made to
	 * set the site Params
	 */
	private void setSiteParamsVisible(){

		//getting the Gridded Region site Object ParamList Iterator
		Iterator it = parameterList.getParametersIterator();

		//if the user decides to go in with filling all the sites with the same site parameter,
		//then make that site parameter visible to te user
		if(((String)siteParam.getValue()).equals(SET_ALL_SITES)){
			while(it.hasNext()){
				//removing the default Site Type Params if same site is to be applied to whole region
				Parameter tempParam= (Parameter)it.next();
				if(tempParam.getName().startsWith(this.DEFAULT))
					parameterList.removeParameter(tempParam.getName());
			}
			//Adding the Site related params to the ParameterList
			ListIterator it1 = siteParams.listIterator();
			while(it1.hasNext()){
				Parameter tempParam = (Parameter)it1.next();
				if(!parameterList.containsParameter(tempParam.getName()))
					parameterList.addParameter(tempParam);
			}
		}
		//if the user decides to fill the values from the CVM
		else {
			//editorPanel.getParameterEditor(this.VS30_DEFAULT).setVisible(true);
			while(it.hasNext()){
				//adds the default site Parameters becuase each site will have different site types and default value
				//has to be given if site lies outside the bounds of CVM
				Parameter tempParam= (Parameter)it.next();
				if(!tempParam.getName().equalsIgnoreCase(this.MAX_LATITUDE) &&
						!tempParam.getName().equalsIgnoreCase(this.MIN_LATITUDE) &&
						!tempParam.getName().equalsIgnoreCase(this.MAX_LONGITUDE) &&
						!tempParam.getName().equalsIgnoreCase(this.MIN_LONGITUDE) &&
						!tempParam.getName().equalsIgnoreCase(this.GRID_SPACING) &&
						!tempParam.getName().equalsIgnoreCase(this.SITE_PARAM_NAME)){

					//removing the existing site Params from the List and adding the
					//new Site Param with site as being defaults
					parameterList.removeParameter(tempParam.getName());
					if(!tempParam.getName().startsWith(this.DEFAULT))
						//getting the Site Param Value corresponding to the Will Site Class "DE" for the seleted IMR  from the SiteTranslator
						siteTrans.setParameterValue(tempParam,WillsMap2000.WILLS_DE,Double.NaN);

					//creating the new Site Param, with "Default " added to its name, with existing site Params
					Parameter newParam = (Parameter)tempParam.clone();
					//If the parameterList already contains the site param with the "Default" name, then no need to change the existing name.
					if(!newParam.getName().startsWith(this.DEFAULT))
						newParam.setName(this.DEFAULT+newParam.getName());
					//making the new parameter to uneditable same as the earlier site Param, so that
					//only its value can be changed and not it properties
					newParam.setNonEditable();
					newParam.addParameterChangeFailListener(this);

					//adding the parameter to the List if not already exists
					if(!parameterList.containsParameter(newParam.getName()))
						parameterList.addParameter(newParam);
				}
			}
		}
		
		if (dataGuiBean != null) {
			OrderedSiteDataProviderList list = dataGuiBean.getProviderList();
//			list.enableOnlyFirstForEachType();
//			System.out.println(list);
			dataGuiBean.refreshAll();
//			System.out.println(list);
		}

		//creating the ParameterList Editor with the updated ParameterList
		editorPanel.removeAll();
		addParameters();
		editorPanel.validate();
		editorPanel.repaint();
		setTitle(GRIDDED_SITE_PARAMS);
		this.refreshParamEditor();
	}

	/**
	 * set the Site Params from the CVM
	 */
	private void setSiteParamsFromCVM(){

		// give latitude and longitude to the servlet
		Double lonMin = (Double)parameterList.getParameter(MIN_LONGITUDE).getValue();
		Double lonMax = (Double)parameterList.getParameter(MAX_LONGITUDE).getValue();
		Double latMin = (Double)parameterList.getParameter(MIN_LATITUDE).getValue();
		Double latMax = (Double)parameterList.getParameter(MAX_LATITUDE).getValue();
		Double gridSpacing = (Double)parameterList.getParameter(GRID_SPACING).getValue();

		// if values in longitude and latitude are invalid
		if(lonMin == null || latMin == null) {
			JOptionPane.showMessageDialog(this,"Check the values in longitude and latitude");
			return ;
		}
		
		OrderedSiteDataProviderList dataProviders = null;
		if (dataGuiBean != null) {
			dataProviders = dataGuiBean.getProviderList();
		} else {
			dataProviders = defaultProviderList;
		}

		CalcProgressBar calcProgress = new CalcProgressBar("Setting Gridded Region sites","Getting the site paramters from the CVM");
		if(((String)siteParam.getValue()).equals(SET_SITE_USING_WILLS_SITE_TYPE)) {
			//if we are setting each site using the Wills site type. basin depth is taken as default.
			dataProviders = (OrderedSiteDataProviderList)dataProviders.clone();
			for (int i=0; i<dataProviders.size(); i++) {
				if (dataProviders.getProvider(i).getDataType().equals(SiteData.TYPE_DEPTH_TO_2_5))
					dataProviders.setEnabled(i, false);
			}
		}
		try {
			sites.setSiteParamsForRegion(dataProviders);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		calcProgress.dispose();
	}

	/**
	 * This function makes sure that Lat and Lon params are within the
	 * range and min values are not greater than max values, ie. checks
	 * if the user has filled in the correct values.
	 */
	/*private void checkLatLonParamValues() throws ParameterException{

    double minLatitude= ((Double)minLat.getValue()).doubleValue();
    double maxLatitude= ((Double)maxLat.getValue()).doubleValue();
    double minLongitude=((Double)minLon.getValue()).doubleValue();
    double maxLongitude=((Double)maxLon.getValue()).doubleValue();

    if(maxLatitude <= minLatitude){
      throw new ParameterException("Max Lat. must be greater than Min Lat");
    }

    if(maxLongitude <= minLongitude){
      throw new ParameterException("Max Lon. must be greater than Min Lon");
    }

  }*/

}
