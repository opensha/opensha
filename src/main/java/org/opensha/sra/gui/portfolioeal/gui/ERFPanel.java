package org.opensha.sra.gui.portfolioeal.gui;
import java.util.ArrayList;

import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.gui.beans.ERF_GuiBean;

/**
 * This class creates an instance of <code>ERF_GuiBean</code>, and implements it as a 
 * JPanel.
 * 
 * @author Jeremy Leakakos
 */
public class ERFPanel {
	
	private ERF_GuiBean erfPanel;
	
	/**
	 * The default constructor.  An ERF_GuiBean is created, which is called by the main view.  Since
	 * the class ERF_GuiBean is already a JPanel, there is no UI formatting done in this class.
	 */
	public ERFPanel() {
		
		try {
			erfPanel = new ERF_GuiBean(ERF_Ref.get(false, ServerPrefUtils.SERVER_PREFS));
		}
		catch ( Exception e ) {
			e.printStackTrace();
		}
		
		erfPanel.getParameter("Eqk Rup Forecast").addParameterChangeListener(BCR_ApplicationFacade.getBCR());
	}
	
	/**
	 * @return This is the instance of ERF_GuiBean that is to be used in the main program
	 */
	public ERF_GuiBean getPanel() {
		return erfPanel;
	}

}
