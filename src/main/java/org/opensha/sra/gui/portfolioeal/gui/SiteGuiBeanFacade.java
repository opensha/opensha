package org.opensha.sra.gui.portfolioeal.gui;

import java.util.ArrayList;
import java.util.Iterator;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.gui.beans.Site_GuiBean;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;
import org.opensha.sha.util.SiteTranslator;

/**
 * This class is a facade to Site_GuiBean.  It is used to allow access to only part of Site_GuiBean,
 * and change functionality where necessary.
 * 
 * @author Jeremy Leakakos
 *
 */
@SuppressWarnings("serial")
public class SiteGuiBeanFacade extends Site_GuiBean {

	private ParameterList parameterList = new ParameterList();

	/**
	 * The default constructor for SiteGuiBeanFacade.  It mirrors the functionality of Site_GuiBean 
	 * by calling super.
	 * 
	 * @see Site_GuiBean
	 */
	public SiteGuiBeanFacade() {
		super();	
	}

	@Override
	public void addSiteParams(Iterator it) {
		ArrayList<String> invisibles = new ArrayList<String>();
		ArrayList<Parameter<?>> paramsToShow = new ArrayList<Parameter<?>>();
		while (it.hasNext()) {
			Parameter<?> p = (Parameter<?>)it.next();
			// we don't want to show Vs30, or parameters that can be set by Vs30 because they will be overridden by
			// the value in the portfolio file.
			if (p.getName().equals(Vs30_Param.NAME)) {
//				System.out.println("SKIPPING: "+p.getName());
				invisibles.add(p.getName());
			} else if (SiteTranslator.DATA_TYPE_PARAM_NAME_MAP.isValidMapping(SiteData.TYPE_VS30, p.getName())
					&& !p.getName().equals(Vs30_TypeParam.NAME)) { // still want to show vs 30 type, since this isn't set by the porftolio
//				System.out.println("SKIPPING: "+p.getName());
				invisibles.add(p.getName());
			}
//			System.out.println("Adding param: "+p.getName());
			paramsToShow.add(p);
		}
		super.addSiteParams(paramsToShow.iterator());
		for (String name : invisibles)
			getParameterListEditor().setParameterVisible(name, false);
//		System.out.println();
	}
}
