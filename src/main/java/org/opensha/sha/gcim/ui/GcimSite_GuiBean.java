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

package org.opensha.sha.gcim.ui;

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
 * Description: this class will make the site parameter editor for the GCIM calculations.
 * It is based on the standard Site_GuiBean, but uses the main Hazard calc site parameters
 * As a constraint 

 * @author Brendon Bradley 
 * @date July 2010
 * @version 1.0
 */

public class GcimSite_GuiBean extends JPanel implements ParameterChangeListener,
		ParameterChangeFailListener {

	// for debug purposes
	protected final static String C = "SiteParamList";

	/**
	 * Latitude and longitude are added to the site paraattenRelImplmeters
	 */
	public final static String LONGITUDE = "Longitude";
	public final static String LATITUDE = "Latitude";

	GcimEditIMiControlPanel parent;
	/**
	 * Site objects
	 */
	private Site parentSite;
	private Site gcimSite;

	// title for site paramter panel
	protected final static String SITE_PARAMS = "Set Site Params";

	private ParameterList parameterList = new ParameterList();
	private ParameterListEditor parameterEditor;

	/**
	 * Longitude and Latitude paramerts to be added to the site params list
	 */
	private DoubleParameter longitude = new DoubleParameter(LONGITUDE,
			new Double(-360), new Double(360), new Double(-118.243));
	private DoubleParameter latitude = new DoubleParameter(LATITUDE,
			new Double(-90), new Double(90), new Double(34.053));
	private GridBagLayout gridBagLayout1 = new GridBagLayout();

	/**
	 * constuctor which builds up mapping between IMRs and their related sites
	 */
	public GcimSite_GuiBean(GcimEditIMiControlPanel parent, Site parentSite, Site gcimSite) {

		this.parent = parent;
		//The parent site for the main Hazard calc
		this.parentSite = parentSite;
		this.gcimSite = gcimSite;
		
		setMinimumSize(new Dimension(140,100));
		setPreferredSize(new Dimension(160,100));
		
		//modify the default lat, lon
		longitude.setValue(parentSite.getLocation().getLongitude());
		latitude.setValue(parentSite.getLocation().getLatitude());
		
		// add the longitude and latitude paramters
		parameterList.addParameter(longitude);
		parameterList.addParameter(latitude);
		latitude.addParameterChangeListener(this);
		longitude.addParameterChangeListener(this);
		latitude.addParameterChangeFailListener(this);
		longitude.addParameterChangeFailListener(this);

		parameterEditor = new ParameterListEditor(parameterList);
		parameterEditor.setEnabled(false);
//		parameterEditor.setEnabled(longitude.getName(), false);
//		parameterEditor.setEnabled(latitude.getName(), false);
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
			if (!parameterList.containsParameter(tempParam)) { // if this does not exist already
				tempParam.addParameterChangeListener(this);
				parameterList.addParameter(tempParam);
			}
		}
		remove(parameterEditor);
		constrainSiteParams();
		parameterEditor = new ParameterListEditor(parameterList);
		disableSiteParams();
		parameterEditor.setTitle(SITE_PARAMS);
		this.add(parameterEditor, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0,
				GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(
						0, 0, 0, 0), 0, 0));
	}
	
	/**
	 * This function constrains the site parameters for the IMR for the IMi which
	 * a GCIM distribution is desired.  The constraint is that those site parameters
	 * which are defined in the main hazard calc cannot be modified
	 * 
	 * @param it
	 *            : Iterator over the site params in the IMR for IMi
	 */
	public void constrainSiteParams() {
		Iterator<String> SiteIt = parameterList.getParameterNamesIterator();
		
		ListIterator<String> parentSiteParamIt, gcimSiteParamIt;
		
		//Loop over the site params for the IMR considered
		while (SiteIt.hasNext()) {
			String paramName = SiteIt.next().toString();
			if (!paramName.equalsIgnoreCase(LATITUDE) && !paramName.equalsIgnoreCase(LONGITUDE)) {
				//now loop over all of the parentSiteParams and if they match then override and disable from editing
				boolean siteParamInParentSiteParam = false;
				parentSiteParamIt = parentSite.getParameterNamesIterator();
				while (parentSiteParamIt.hasNext()) {
					String parentSiteParamName = parentSiteParamIt.next().toString();
					if (paramName==parentSiteParamName) {
						siteParamInParentSiteParam = true;
						//change the default value of the param to be equal to the parent value
						parameterList.setValue(parentSiteParamName, parentSite.getParameter(parentSiteParamName).getValue());
					}
				}
				if (!siteParamInParentSiteParam) {
					//now loop over all of the gcimSiteParams.  If the siteParam is in the gcimSiteParams list then
					//make the siteParam have this value as the default, if it is not then add it to gcimSiteParams
					boolean siteParamInGcimSiteParam = false;
					gcimSiteParamIt = gcimSite.getParameterNamesIterator();
					while (gcimSiteParamIt.hasNext()) {
						String gcimSiteParamName = gcimSiteParamIt.next().toString();
						if (paramName==gcimSiteParamName) {
							siteParamInGcimSiteParam = true;
							
							parameterList.setValue(gcimSiteParamName, gcimSite.getParameter(gcimSiteParamName).getValue());
							break;
						}
					}
					if (!siteParamInGcimSiteParam) {
						Iterator<Parameter<?>> it = parameterList.getParametersIterator();
						while (it.hasNext()) {
							Parameter<?> siteParam = it.next();
							if (siteParam.getName()==paramName) {
								//Add a ParameterChange listener (to the GcimEditIMiCtrlPanl and then add the param to the gcimSite
								siteParam.addParameterChangeListener(this);
								gcimSite.addParameter(siteParam);
							}
						}
					}
				}
			}
		}
		parent.updateGcimSite(gcimSite);
	}
	
	
	/**
	 * This function disables those site params which are defined in the Parent site
	 * 
	 * @param it
	 *            : Iterator over the site params in the IMR for IMi
	 */
	public void disableSiteParams() {
		Iterator<String> SiteIt = parameterList.getParameterNamesIterator();
		
		ListIterator<String> parentSiteParamIt, gcimSiteParamIt;
		
		//Loop over the site params for the IMR considered
		while (SiteIt.hasNext()) {
			String paramName = SiteIt.next().toString();
			if (!paramName.equalsIgnoreCase(LATITUDE) && !paramName.equalsIgnoreCase(LONGITUDE)) {
				//now loop over all of the parentSiteParams and if they match then override and disable from editing
				boolean siteParamInParentSiteParam = false;
				parentSiteParamIt = parentSite.getParameterNamesIterator();
				while (parentSiteParamIt.hasNext()) {
					String parentSiteParamName = parentSiteParamIt.next().toString();
					if (paramName==parentSiteParamName) {
						parameterList.getParameter(parentSiteParamName).getEditor().setEnabled(false);
						//parameterEditor.setEnabled(parentSiteParamName, false);
						break;
					}
				}
			}
		}
	}

	/**
	 * This function removes the previous site parameters and adds as passed in
	 * iterator
	 * 
	 * @param it
	 */
	public void replaceSiteParams(Iterator it) {

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
	 * this function called when any site parameters are changed so parse these changes onto the gcimSite obj
	 * 
	 * @param e
	 */
	public void parameterChange(ParameterChangeEvent e) {
		parent.updateGcimSite(gcimSite);
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
