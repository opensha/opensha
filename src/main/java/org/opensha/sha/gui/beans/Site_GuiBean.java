package org.opensha.sha.gui.beans;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.opensha.commons.data.Site;
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
import org.opensha.sha.imr.ScalarIMR;

/**
 * <p>
 * Title:SiteParamListEditor
 * </p>
 * <p>
 * Description: this class will make the site parameter editor.
 * </p>
 * <p>
 * Copyright: Copyright (c) 2002
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author Nitin Gupta & Vipin Gupta
 * @date Oct 29, 2002
 * @version 1.0
 */

public class Site_GuiBean extends JPanel implements ParameterChangeListener,
		ParameterChangeFailListener {

	// for debug purposes
	protected final static String C = "SiteParamList";

	/**
	 * Latitude and longitude are added to the site paraattenRelImplmeters
	 */
	public final static String LONGITUDE = "Longitude";
	public final static String LATITUDE = "Latitude";

	/**
	 * Site object
	 */
	private Site site;

	// title for site paramter panel
	protected final static String SITE_PARAMS = "Set Site Params";

	private ParameterList parameterList = new ParameterList();
	private ParameterListEditor parameterEditor;

	/**
	 * Longitude and Latitude paramerts to be added to the site params list
	 */
	private DoubleParameter longitude = new DoubleParameter(LONGITUDE,
			Double.valueOf(-360), Double.valueOf(360), Double.valueOf(-118.243));
	private DoubleParameter latitude = new DoubleParameter(LATITUDE,
			Double.valueOf(-90), Double.valueOf(90), Double.valueOf(34.053));
	private GridBagLayout gridBagLayout1 = new GridBagLayout();

	/**
	 * constuctor which builds up mapping between IMRs and their related sites
	 */
	public Site_GuiBean() {

		setMinimumSize(new Dimension(140,100));
		setPreferredSize(new Dimension(160,100));
		
		// add the longitude and latitude paramters
		parameterList.addParameter(longitude);
		parameterList.addParameter(latitude);
		latitude.addParameterChangeListener(this);
		longitude.addParameterChangeListener(this);
		latitude.addParameterChangeFailListener(this);
		longitude.addParameterChangeFailListener(this);

		// maake the new site object
		site = new Site(new Location(((Double) latitude.getValue())
				.doubleValue(), ((Double) longitude.getValue()).doubleValue()));
		parameterEditor = new ParameterListEditor(parameterList);
		parameterEditor.setTitle(SITE_PARAMS);
		try {
			jbInit();
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.add(parameterEditor, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0,
				GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(
						0, 0, 0, 0), 0, 0));
	}

	/**
	 * This function adds the site params to the existing list. Parameters are
	 * NOT cloned. If paramter with same name already exists, then it is not
	 * added
	 * 
	 * @param it
	 *            : Iterator over the site params in the IMR
	 */
	public void addSiteParams(Iterator it) {
		AbstractParameter tempParam;
		while (it.hasNext()) {
			tempParam = (AbstractParameter) it.next();
			if (!parameterList.containsParameter(tempParam)) { // if this does
																// not exist
																// already
				parameterList.addParameter(tempParam);
				/*
				 * if(tempParam instanceof StringParameter) { // if it
				 * Stringparamter, set its initial values StringParameter
				 * strConstraint = (StringParameter)tempParam;
				 * tempParam.setValue(strConstraint.getAllowedStrings().get(0));
				 * }
				 */
			}
			if (!site.containsParameter(tempParam))
				site.addParameter(tempParam);
		}

		remove(parameterEditor);
		parameterEditor = new ParameterListEditor(parameterList);
		parameterEditor.setTitle(SITE_PARAMS);
		this.add(parameterEditor, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0,
				GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(
						0, 0, 0, 0), 0, 0));
	}

	/**
	 * This function adds the site params to the existing list. Parameters are
	 * cloned. If paramter with same name already exists, then it is not added
	 * 
	 * @param it
	 *            : Iterator over the site params in the IMR
	 */
	public void addSiteParamsClone(Iterator it) {
		AbstractParameter tempParam;
		while (it.hasNext()) {
			tempParam = (AbstractParameter) it.next();
			if (!parameterList.containsParameter(tempParam)) { // if this does
																// not exist
																// already
				AbstractParameter cloneParam = (AbstractParameter) tempParam.clone();
				/*
				 * if(tempParam instanceof StringParameter) { StringParameter
				 * strConstraint = (StringParameter)tempParam;
				 * cloneParam.setValue
				 * (strConstraint.getAllowedStrings().get(0)); }
				 */
				parameterList.addParameter(cloneParam);
				site.addParameter(cloneParam);
			}
		}
		this.remove(parameterEditor);
		parameterEditor = new ParameterListEditor(parameterList);
		parameterEditor.setTitle(SITE_PARAMS);
		this.add(parameterEditor, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0,
				GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(
						4, 4, 4, 4), 0, 0));

	}

	/**
	 * This function removes the previous site parameters and adds as passed in
	 * iterator
	 * 
	 * @param it
	 */
	public void replaceSiteParams(Iterator it) {

		// make the new site object
		site = new Site(new Location(((Double) latitude.getValue())
				.doubleValue(), ((Double) longitude.getValue()).doubleValue()));
		// first remove all the parameters ewxcept latitude and longitude
		Iterator<String> siteIt = parameterList.getParameterNamesIterator();
		while (siteIt.hasNext()) { // remove all the parameters except latitdue
									// and longitude
			String paramName = siteIt.next();
			if (!paramName.equalsIgnoreCase(LATITUDE)
					&& !paramName.equalsIgnoreCase(LONGITUDE)) {
				parameterList.removeParameter(paramName);
			}
		}
		// now add all the new params
		addSiteParams(it);

	}

	/**
	 * Display the site params based on the site passed as the parameter
	 */
	public void setSite(Site site) {
		this.site = site;
		Iterator it = site.getParametersIterator();
		replaceSiteParams(it);
	}

	/**
	 * get the site object from the site params
	 * 
	 * @return
	 */
	public Site getSite() {
		return site;
	}

	/**
	 * get the clone of site object from the site params
	 * 
	 * @return
	 */
	public Site getSiteClone() {
		Site newSite = new Site(new Location(((Double) latitude.getValue())
				.doubleValue(), ((Double) longitude.getValue()).doubleValue()));
		Iterator it = site.getParametersIterator();

		// clone the paramters
		while (it.hasNext())
			newSite.addParameter((Parameter) ((Parameter) it.next())
					.clone());
		return site;
	}

	/**
	 * this function when longitude or latitude are updated So, we update the
	 * site object as well
	 * 
	 * @param e
	 */
	public void parameterChange(ParameterChangeEvent e) {
		site.setLocation(new Location(((Double) latitude.getValue())
				.doubleValue(), ((Double) longitude.getValue()).doubleValue()));
	}

	/**
	 * Shown when a Constraint error is thrown on a ParameterEditor
	 * 
	 * @param e
	 *            Description of the Parameter
	 */
	public void parameterChangeFailed(ParameterChangeFailEvent e) {

		String S = C + " : parameterChangeFailed(): ";

		StringBuffer b = new StringBuffer();

		Parameter param = (Parameter) e.getSource();

		ParameterConstraint constraint = param.getConstraint();
		String oldValueStr = e.getOldValue().toString();
		String badValueStr = e.getBadValue().toString();
		String name = param.getName();

		b.append("The value ");
		b.append(badValueStr);
		b.append(" is not permitted for '");
		b.append(name);
		b.append("'.\n");
		b.append("Resetting to ");
		b.append(oldValueStr);
		b.append(". The constraints are: \n");
		b.append(constraint.toString());

		JOptionPane.showMessageDialog(this, b.toString(),
				"Cannot Change Value", JOptionPane.INFORMATION_MESSAGE);
	}

	private void jbInit() throws Exception {
		this.setLayout(gridBagLayout1);
		this.setBackground(Color.white);
	}

	/**
	 * 
	 * @return the site ParamListEditor
	 */
	public ParameterListEditor getParameterListEditor() {
		return parameterEditor;
	}
}
