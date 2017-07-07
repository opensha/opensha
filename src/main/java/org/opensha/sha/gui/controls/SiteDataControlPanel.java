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

package org.opensha.sha.gui.controls;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.data.siteData.gui.beans.OrderedSiteDataGUIBean;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.ControlPanel;
import org.opensha.sha.gui.beans.IMR_MultiGuiBean;
import org.opensha.sha.gui.beans.Site_GuiBean;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.event.ScalarIMRChangeEvent;
import org.opensha.sha.imr.event.ScalarIMRChangeListener;
import org.opensha.sha.util.SiteTranslator;

public class SiteDataControlPanel extends ControlPanel implements ScalarIMRChangeListener,
					ActionListener, ChangeListener {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static final String NAME = "Set Site Params from Web Services";
	
	private IMR_MultiGuiBean imrGuiBean;
	private Site_GuiBean siteGuiBean;
	private OrderedSiteDataGUIBean dataGuiBean;
	
	private JPanel mainPanel = new JPanel(new BorderLayout());
	
	private JButton setButton = new JButton("Set IMR Params");
	private JButton viewButton = new JButton("View Data");
	
	private static final String SET_ALL_IMRS = "All IMRs";
	private static final String SET_SELECTED_IMR = "Selected IMR(s) only";
	private static final String SET_DEFAULT = SET_SELECTED_IMR;
	private JComboBox allSingleComboBox;
	
	private Collection<? extends ScalarIMR> selectedIMRs;
	private Collection<? extends ScalarIMR> allIMRs;
	
	private SiteTranslator trans = null;
	
	private JFrame frame;
	
	public SiteDataControlPanel(Component parent, IMR_MultiGuiBean imrGuiBean,
            Site_GuiBean siteGuiBean) {
		super(NAME);
		this.imrGuiBean = imrGuiBean;
		this.siteGuiBean = siteGuiBean;
	}
	
	public void doinit() {
		frame = new JFrame();
		imrGuiBean.addIMRChangeListener(this);
		allIMRs = imrGuiBean.getIMRs();
		selectedIMRs = imrGuiBean.getIMRMap().values();
		
		dataGuiBean = new OrderedSiteDataGUIBean(
				OrderedSiteDataProviderList.createCachedSiteDataProviderDefaults(), imrGuiBean.getIMRMap().values());
		
		viewButton.addActionListener(this);
		setButton.addActionListener(this);
		
		String[] comboItems = { SET_SELECTED_IMR, SET_ALL_IMRS };
		allSingleComboBox = new JComboBox(comboItems);
		allSingleComboBox.setMaximumSize(new Dimension(200, 30));
		allSingleComboBox.setSelectedItem(SET_DEFAULT);
		allSingleComboBox.addActionListener(this);
		
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
		
		bottomPanel.add(allSingleComboBox);
		bottomPanel.add(setButton);
		bottomPanel.add(viewButton);
		
		mainPanel.add(dataGuiBean, BorderLayout.CENTER);
		mainPanel.add(bottomPanel, BorderLayout.SOUTH);
		
		updateIMRs();
		dataGuiBean.getProviderList().addChangeListener(this);
		
		frame.setContentPane(mainPanel);
		frame.setSize(OrderedSiteDataGUIBean.width, 600);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	}
	
	private void updateIMRs() {
		if (isSetAllSelected()) {
//			System.out.println("Setting for all!");
			dataGuiBean.setIMR(allIMRs);
		} else {
//			System.out.println("Setting for selected!");
			dataGuiBean.setIMR(selectedIMRs);
		}
		enableButtons();
	}
	
	public boolean isSetAllSelected() {
		return SET_ALL_IMRS.equals(allSingleComboBox.getSelectedItem());
	}

	public void imrChange(ScalarIMRChangeEvent event) {
		selectedIMRs = event.getNewIMRs().values();
		updateIMRs();
	}
	
	public void setSiteParams() {
		ArrayList<SiteDataValue<?>> data = loadData();
		
		if (data == null || data.size() == 0)
			return;
		
		for (SiteDataValue<?> val : data) {
			System.out.println(val);
		}
		
		if (trans == null) {
			trans = new SiteTranslator();
		}
		
		if (isSetAllSelected())
			trans.setAllSiteParams(allIMRs, data);
		else
			trans.setAllSiteParams(selectedIMRs, data);
		
		this.siteGuiBean.getParameterListEditor().refreshParamEditor();
		frame.dispose();
	}
	
	public void displayData(ArrayList<SiteDataValue<?>> datas) {
		OrderedSiteDataGUIBean.showDataDisplayDialog(datas, frame, siteGuiBean.getSite().getLocation());
	}
	
	private ArrayList<SiteDataValue<?>> loadData() {
		return loadData(false);
	}
	
	private ArrayList<SiteDataValue<?>> loadData(boolean all) {
		OrderedSiteDataProviderList list = dataGuiBean.getProviderList();
		Location loc = siteGuiBean.getSite().getLocation();
		
		if (all)
			return list.getAllAvailableData(loc);
		else
			return list.getBestAvailableData(loc);
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == setButton) {
			this.setSiteParams();
		} else if (e.getSource() == viewButton) {
			ArrayList<SiteDataValue<?>> data = loadData(true);
			this.displayData(data);
		} else if (e.getSource() == allSingleComboBox) {
			updateIMRs();
		}
	}
	
	private void enableButtons() {
		boolean enable = dataGuiBean.getProviderList().isAtLeastOneEnabled();
		setButton.setEnabled(enable);
		viewButton.setEnabled(enable);
	}

	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == dataGuiBean.getProviderList())
			enableButtons();
	}

	@Override
	public Window getComponent() {
		return frame;
	}
}
