package org.opensha.commons.data.siteData.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValueList;
import org.opensha.commons.data.siteData.gui.beans.OrderedSiteDataGUIBean;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.GMT_MapGenerator;
import org.opensha.commons.mapping.gmt.gui.GMT_MapGuiBean;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;

public class SiteDataMapApplet extends JPanel implements ActionListener, ListSelectionListener {
	
	private OrderedSiteDataGUIBean dataBean;
	private GMT_MapGuiBean mapBean;
	
	private JButton mapButton = new JButton("Create Map");
	private String mapButtonInfo =		"<html>This creates a map for each of the currently<br>" +
										"selected data sources.</html>";
	private JButton mapMultiButton = new JButton("Create Mosaic Map");
	private String mapMultiButtonInfo =	"<html>This creates single map from all of the currently selected data<br>" +
										"sources. At each point, the first valid value (in order of priority)<br>" +
										"is used.<br><br>" +
										"<b>Note that all data sources must be of the same data<br>" +
										"type to enable mosaic maps.</b></html>";
	private JButton regionButton = new JButton("Set Region from Data");
	private String regionButtonInfo =	"<html>This sets the region in the map attribes settings to equal<br>" +
										"the applicable region for the current data source(s).</html>";
	
	public SiteDataMapApplet() {
		dataBean = new OrderedSiteDataGUIBean(OrderedSiteDataProviderList.createSiteDataMapProviders());
		dataBean.addListSelectionListener(this);
		mapBean = new GMT_MapGuiBean();
		mapBean.getParameterList().getParameter(GMT_MapGenerator.LOG_PLOT_NAME).setValue(Boolean.valueOf(false));
		mapBean.getParameterList().getParameter(GMT_MapGenerator.GMT_SMOOTHING_PARAM_NAME).setValue(Boolean.valueOf(false));
		mapBean.getParameterList().getParameter(
				GMT_MapGenerator.TOPO_RESOLUTION_PARAM_NAME).setValue(GMT_MapGenerator.TOPO_RESOLUTION_NONE);
		mapBean.getParameterList().getParameter(
				GMT_MapGenerator.COAST_PARAM_NAME).setValue(GMT_MapGenerator.COAST_DRAW);
		mapBean.refreshParamEditor();
		
		this.setLayout(new BorderLayout());
		
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.X_AXIS));
		mapButton.setToolTipText(mapButtonInfo);
		bottomPanel.add(mapButton);
		mapMultiButton.setToolTipText(mapMultiButtonInfo);
		bottomPanel.add(mapMultiButton);
		regionButton.setToolTipText(regionButtonInfo);
		bottomPanel.add(regionButton);
		
		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.X_AXIS));
		
		centerPanel.add(dataBean);
		centerPanel.add(mapBean);
		this.add(centerPanel, BorderLayout.CENTER);
		this.add(bottomPanel, BorderLayout.SOUTH);
		
		mapButton.addActionListener(this);
		mapMultiButton.addActionListener(this);
		regionButton.addActionListener(this);
		
		this.setPreferredSize(new Dimension(900, 600));
		valueChanged(null);
//		this.setSize(500, 800);
	}
	
	private void makeMap(ArrayList<Double> zVals, LocationList locs, String label, String meta) {
		Parameter customParam = mapBean.getParameterList().getParameter(
				GMT_MapGenerator.CUSTOM_SCALE_LABEL_PARAM_CHECK_NAME);
		// if the user didn't specify a custom one, then do it for them
		boolean custom = (Boolean)customParam.getValue();
		if (!custom && label != null && label.length() > 0) {
			customParam.setValue(Boolean.valueOf(true));
			System.out.println("Label: " + label);
			label = "'" + label + "'";
			mapBean.getParameterList().getParameter(
					GMT_MapGenerator.SCALE_LABEL_PARAM_NAME).setValue(label);
		}
		
		ArbDiscrGeoDataSet xyz = new ArbDiscrGeoDataSet(true);
		
		
		for (int i=0; i<locs.size(); i++) {
			xyz.set(locs.get(i), zVals.get(i));
		}
		
		mapBean.makeMap(xyz, meta);
		
		customParam.setValue(custom);
	}
	
	private void makeCombinedMap() throws IOException {
		ArrayList<SiteData<?>> providers = dataBean.getSelectedProviders();
		ArrayList<SiteDataValueList<Double>> valListList = new ArrayList<SiteDataValueList<Double>>();
		
		GriddedRegion region = mapBean.getEvenlyGriddedGeographicRegion();
		LocationList locs = region.getNodeList();

		String meta = "Combined map from the following providers (sorted by priority):\n\n";
		for (int i=0; i<providers.size(); i++) {
			SiteData<Double> doubProvider = (SiteData<Double>)providers.get(i);
			meta += i + ". " + doubProvider.getName();
			ArrayList<Double> doubVals = doubProvider.getValues(locs);
			
			valListList.add(new SiteDataValueList<Double>(doubVals, doubProvider));
		}
		
		ArrayList<Double> zVals = new ArrayList<Double>();
		
		for (int i=0; i<locs.size(); i++) {
			Double val = Double.NaN;
			for (SiteDataValueList<Double> valList : valListList) {
				Double newVal = valList.getValue(i).getValue();
				if (!newVal.isNaN()) {
					val = newVal;
					break;
				}
			}
			zVals.add(val);
		}
		
		makeMap(zVals, locs, providers.get(0).getDataType(), meta);
	}
	
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == mapButton) {
			System.out.println("Making a map...");
			ArrayList<SiteData<?>> providers = dataBean.getSelectedProviders();
			if (providers.size() == 0) {
				System.out.println("No data provider selected!");
				return;
			}
			for (SiteData<?> provider : providers) {
				SiteData<Double> doubProvider = (SiteData<Double>)provider;
				try {
					String label = doubProvider.getName() + " -  " + doubProvider.getDataType();
					
					if (label.length() > 20)
						label = doubProvider.getShortName() + " - " + doubProvider.getDataType();
					if (label.length() > 20)
						label = doubProvider.getDataType();
					
					GriddedRegion region = mapBean.getEvenlyGriddedGeographicRegion();
					LocationList locs = region.getNodeList();
					ArrayList<Double> zVals = doubProvider.getValues(locs);
					
					String meta = doubProvider.getName();
					
					makeMap(zVals, locs, label, meta);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		} else if (e.getSource() == mapMultiButton) {
			try {
				makeCombinedMap();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		} else if (e.getSource() == regionButton) {
			SiteData<?> provider = dataBean.getSelectedProvider();
			if (provider == null) {
				System.out.println("No data provider selected!");
				return;
			}
			Region region = provider.getApplicableRegion();
			
			ParameterList paramList = mapBean.getParameterList();
			paramList.getParameter(GMT_MapGenerator.MIN_LAT_PARAM_NAME).setValue(Double.valueOf(region.getMinLat()));
			paramList.getParameter(GMT_MapGenerator.MAX_LAT_PARAM_NAME).setValue(Double.valueOf(region.getMaxLat()));
			paramList.getParameter(GMT_MapGenerator.MIN_LON_PARAM_NAME).setValue(Double.valueOf(region.getMinLon()));
			paramList.getParameter(GMT_MapGenerator.MAX_LON_PARAM_NAME).setValue(Double.valueOf(region.getMaxLon()));
			mapBean.refreshParamEditor();
		}
	}
	
	/**
	 * Main class for running this as a regular java application
	 * 
	 * @param args
	 */
	public static void main(String args[]) {
		JFrame frame = new JFrame();
		SiteDataMapApplet applet = new SiteDataMapApplet();
		frame.setContentPane(applet);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(applet.getPreferredSize());
		frame.setVisible(true);
	}

	public void valueChanged(ListSelectionEvent e) {
		boolean selected = dataBean.isSelected();
		mapButton.setEnabled(selected);
		regionButton.setEnabled(selected);
		// we enable the combined map button only if there are multiple providers,
		// and they all have the same type
		ArrayList<SiteData<?>> providers = dataBean.getSelectedProviders();
		if (selected && providers.size() > 1) {
			String type = providers.get(0).getDataType();
			boolean enable = true;
			for (SiteData<?> provider : providers) {
				if (!provider.getDataType().equals(type)) {
					enable = false;
					break;
				}
			}
			mapMultiButton.setEnabled(enable);
		} else {
			mapMultiButton.setEnabled(false);
		}
	}

}
