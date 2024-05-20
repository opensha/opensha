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

import org.opensha.commons.gui.ControlPanel;
import org.opensha.commons.param.editor.impl.ParameterListEditor;
import org.opensha.sha.gui.beans.SitesInGriddedRectangularRegionGuiBean;
import org.opensha.sha.gui.beans.SitesInGriddedRegionGuiBean;
/**
 * <p>Title: SitesOfInterest </p>
 * <p>Description: It displays a list of interesting sites which user can choose </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author unascribed
 * @version 1.0
 */

public class RegionsOfInterestControlPanel extends ControlPanel {
	
	public static final String NAME = "Regions of Interest";
	
	private JLabel jLabel1 = new JLabel();
	private JComboBox regionsComboBox = new JComboBox();
	private GridBagLayout gridBagLayout1 = new GridBagLayout();
	private ArrayList minLatVector = new ArrayList();
	private ArrayList maxLatVector = new ArrayList();
	private ArrayList minLonVector = new ArrayList();
	private ArrayList maxLonVector = new ArrayList();
	private ParameterListEditor regionGuiBean;
	
	private Component parent;
	
	private JFrame frame;

	/**
	 * Constructor
	 *
	 * @param parent : parent component which calls this control panel
	 * @param siteGuiBean : site gui bean to set the lat and lon
	 */
	public RegionsOfInterestControlPanel(Component parent,
			ParameterListEditor regionGuiBean) {
		super(NAME);
		this.regionGuiBean = regionGuiBean;
		this.parent = parent;
	}
	
	public void doinit() {
		if (!(regionGuiBean instanceof SitesInGriddedRectangularRegionGuiBean || regionGuiBean instanceof SitesInGriddedRegionGuiBean)) {
			throw new RuntimeException("The ParameterListEditor given to the RegionsOfInterestControlPanel is not an instance of SitesInGriddedRegionGuiBean or SitesInGriddedRectangularRegionGuiBean!");
		}
		try {
			/*
			 * add interesting regions
			 */


			regionsComboBox.addItem("GEM Test Region");
			minLatVector.add(Double.valueOf(35));
			maxLatVector.add(Double.valueOf(45));
			minLonVector.add(Double.valueOf(65));
			maxLonVector.add(Double.valueOf(85));

			regionsComboBox.addItem("SF Bay Area");
			minLatVector.add(Double.valueOf(36.5500));
			maxLatVector.add(Double.valueOf(39.6167));
			minLonVector.add(Double.valueOf(-124.7333));
			maxLonVector.add(Double.valueOf(-120.1333));

			regionsComboBox.addItem("Greater LA Region");
			minLatVector.add(Double.valueOf(33.5));
			maxLatVector.add(Double.valueOf(34.7));
			minLonVector.add(Double.valueOf(-119.5));
			maxLonVector.add(Double.valueOf(-117.0));

			regionsComboBox.addItem("San Simeon Region");
			minLatVector.add(Double.valueOf(34.872466));
			maxLatVector.add(Double.valueOf(36.539133));
			minLonVector.add(Double.valueOf(-121.844633));
			maxLonVector.add(Double.valueOf(-119.361300));


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
		frame = new JFrame();
		jLabel1.setFont(new java.awt.Font("Dialog", 1, 12));
		jLabel1.setForeground(Color.black);
		jLabel1.setText("Choose Region:");
		frame.getContentPane().setLayout(gridBagLayout1);
		frame.setTitle(NAME);
		regionsComboBox.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				regionsComboBox_actionPerformed(e);
			}
		});
		frame.getContentPane().add(regionsComboBox,  new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0
				,GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(14, 6, 10, 12), 149, 2));
		frame.getContentPane().add(jLabel1,  new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0
				,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(14, 5, 10, 0), 13, 11));
	}

	/**
	 * whenever user selects an interesting site, this function is called
	 * @param e
	 */
	void regionsComboBox_actionPerformed(ActionEvent e) {
		setLatAndLon();
	}

	/**
	 * to set lat and lon according to user selection
	 */
	private void setLatAndLon() {
		int index = this.regionsComboBox.getSelectedIndex();
		if (regionGuiBean instanceof SitesInGriddedRegionGuiBean) {
			regionGuiBean.getParameterList().getParameter(SitesInGriddedRegionGuiBean.REGION_SELECT_NAME).setValue(SitesInGriddedRegionGuiBean.RECTANGULAR_NAME);
		}
		// set the lat and lon in the editor
		regionGuiBean.getParameterList().getParameter(SitesInGriddedRectangularRegionGuiBean.MIN_LATITUDE).setValue(minLatVector.get(index));
		regionGuiBean.getParameterList().getParameter(SitesInGriddedRectangularRegionGuiBean.MAX_LATITUDE).setValue(maxLatVector.get(index));
		regionGuiBean.getParameterList().getParameter(SitesInGriddedRectangularRegionGuiBean.MIN_LONGITUDE).setValue(minLonVector.get(index));
		regionGuiBean.getParameterList().getParameter(SitesInGriddedRectangularRegionGuiBean.MAX_LONGITUDE).setValue(maxLonVector.get(index));
		regionGuiBean.refreshParamEditor();
	}

	@Override
	public Window getComponent() {
		return frame;
	}


}
