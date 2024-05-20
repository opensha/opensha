package org.opensha.sha.gui.controls;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.ControlPanel;
import org.opensha.sha.gui.beans.Site_GuiBean;
import org.opensha.sha.util.NEHRP_TestCity;
/**
 * <p>Title: SitesOfInterest </p>
 * <p>Description: It displays a list of interesting sites which user can choose </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author unascribed
 * @version 1.0
 */

public class SitesOfInterestControlPanel extends ControlPanel {
	public static final String NAME = "Sites of Interest";

	private JLabel jLabel1 = new JLabel();
	private JComboBox sitesComboBox = new JComboBox();
	private GridBagLayout gridBagLayout1 = new GridBagLayout();
	private ArrayList<Double> latVector;
	private ArrayList<Double> lonVector;
	private Site_GuiBean siteGuiBean;
	private Component parent;
	
	private JFrame frame;

	/**
	 * Constructor
	 *
	 * @param parent : parent component which calls this control panel
	 * @param siteGuiBean : site gui bean to set the lat and lon
	 */
	public SitesOfInterestControlPanel(Component parent, Site_GuiBean siteGuiBean) {
		super(NAME);
		this.parent = parent;
		this.siteGuiBean = siteGuiBean;
	}
	
	public void doinit() {
		frame = new JFrame();
		try {
			latVector = new ArrayList<Double>();
			lonVector = new ArrayList<Double>();

			/*
			 * add interesting sites
			 */
			
			for (NEHRP_TestCity city : NEHRP_TestCity.getShortListCA()) {
				sitesComboBox.addItem("NEHRP city: " + city.toString());
				Location loc = city.location();
				latVector.add(loc.getLatitude());
				lonVector.add(loc.getLongitude());
			}

			//GEM test site
			sitesComboBox.addItem("GEM test site");
			latVector.add(Double.valueOf(40.0));
			lonVector.add(Double.valueOf(70.0));

			// los angeles
			sitesComboBox.addItem("Los Angeles Civic Center");
			latVector.add(Double.valueOf(34.055));
			lonVector.add(Double.valueOf(-118.2467));

			// san francisco
			sitesComboBox.addItem("San Francisco City Hall");
			latVector.add(Double.valueOf(37.775));
			lonVector.add(Double.valueOf(-122.4183));

			// san francisco
			sitesComboBox.addItem("San Francisco Class B");
			latVector.add(Double.valueOf(37.8));
			lonVector.add(Double.valueOf(-122.417));

			// san francisco
			sitesComboBox.addItem("San Francisco Class D");
			latVector.add(Double.valueOf(37.783));
			lonVector.add(Double.valueOf(-122.417));

			// Sierra Madre Fault Gap
			this.sitesComboBox.addItem("Sierra Madre Fault Gap");
			latVector.add(Double.valueOf(34.225));
			lonVector.add(Double.valueOf(-117.835));

			// Alaskan Pipeline
			this.sitesComboBox.addItem("Alaskan Pipeline");
			latVector.add(Double.valueOf(63.375));
			lonVector.add(Double.valueOf(-145.825));
			
			// Santiago, Chile
			this.sitesComboBox.addItem("Santiago, Chile");
			latVector.add(Double.valueOf(-33.45));
			lonVector.add(Double.valueOf(-70.6666667));

			jbInit();
			// show the window at center of the parent component
			frame.setLocation(parent.getX()+parent.getWidth()/2,
					parent.getY()+parent.getHeight()/2);
			// set lat and lon
			this.setLatAndLon();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private void jbInit() throws Exception {
		jLabel1.setFont(new java.awt.Font("Dialog", 1, 12));
		jLabel1.setForeground(Color.black);
		jLabel1.setText("Choose Site:");
		frame.getContentPane().setLayout(gridBagLayout1);
		frame.setTitle("Sites Of Interest");
		sitesComboBox.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				sitesComboBox_actionPerformed(e);
			}
		});
		frame.getContentPane().add(sitesComboBox,  new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(14, 6, 10, 12), 149, 2));
		frame.getContentPane().add(jLabel1,  new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(14, 5, 10, 0), 13, 11));
	}

	/**
	 * whenever user selects an interesting site, this function is called
	 * @param e
	 */
	void sitesComboBox_actionPerformed(ActionEvent e) {
		setLatAndLon();
	}

	/**
	 * to set lat and lon according to user selection
	 */
	private void setLatAndLon() {
		int index = this.sitesComboBox.getSelectedIndex();
		// set the lat and lon in the editor
		siteGuiBean.getParameterListEditor().getParameterList().getParameter(Site_GuiBean.LATITUDE).setValue(latVector.get(index));
		siteGuiBean.getParameterListEditor().getParameterList().getParameter(Site_GuiBean.LONGITUDE).setValue(lonVector.get(index));
		siteGuiBean.getParameterListEditor().refreshParamEditor();
	}

	@Override
	public Window getComponent() {
		return frame;
	}
}
