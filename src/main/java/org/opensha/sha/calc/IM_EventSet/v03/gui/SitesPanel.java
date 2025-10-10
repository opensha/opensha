package org.opensha.sha.calc.IM_EventSet.v03.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.imr.IntensityMeasureRelationship;
import org.opensha.sha.imr.ScalarIMR;

public class SitesPanel extends JPanel implements ListSelectionListener, ActionListener {
	
	protected JList sitesList;
	protected JList siteDataList;
	
	protected JButton addSiteButton = new JButton("Add Site");
	protected JButton removeSiteButton = new JButton("Remove Site(s)");
	protected JButton editSiteButton = new JButton("Edit Site");

    // List of locations for each site
	private ArrayList<Location> locs;
    // List of site data parameters set for each site
	private ArrayList<ArrayList<SiteDataValue<?>>> dataLists;
    // List of all supported site data parameters for building new sites
    private ParameterList siteDataParams;

	public SitesPanel() {
		super();
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		locs = new ArrayList<Location>();
		dataLists = new ArrayList<ArrayList<SiteDataValue<?>>>();
        siteDataParams = new ParameterList();

		sitesList = new JList();
		sitesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		sitesList.setSelectedIndex(0);
		sitesList.addListSelectionListener(this);
		
		JScrollPane listScroller = new JScrollPane(sitesList);
		listScroller.setPreferredSize(new Dimension(250, 650));
		
		JPanel northPanel = new JPanel(new BorderLayout());
		northPanel.add(new JLabel("Sites"), BorderLayout.NORTH);
		northPanel.add(listScroller, BorderLayout.CENTER);
		
		JPanel buttonPanel = new JPanel(new BorderLayout());
		JPanel leftButtonPanel = new JPanel();
		leftButtonPanel.setLayout(new BoxLayout(leftButtonPanel, BoxLayout.X_AXIS));
		leftButtonPanel.add(addSiteButton);
		leftButtonPanel.add(removeSiteButton);
		buttonPanel.add(leftButtonPanel, BorderLayout.WEST);
		buttonPanel.add(editSiteButton, BorderLayout.EAST);
		removeSiteButton.setEnabled(false);
		addSiteButton.addActionListener(this);
		removeSiteButton.addActionListener(this);
		editSiteButton.addActionListener(this);
		
		northPanel.add(buttonPanel, BorderLayout.SOUTH);
		
		siteDataList = new JList();
		siteDataList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		siteDataList.setSelectedIndex(0);
		siteDataList.addListSelectionListener(this);
		
		JScrollPane dataListScroller = new JScrollPane(siteDataList);
		dataListScroller.setPreferredSize(new Dimension(250, 150));
		
		JPanel southPanel = new JPanel(new BorderLayout());
		southPanel.add(new JLabel("Site Data Values"), BorderLayout.NORTH);
		southPanel.add(dataListScroller, BorderLayout.CENTER);
		
		JPanel dataButtonPanel = new JPanel();
		dataButtonPanel.setLayout(new BoxLayout(dataButtonPanel, BoxLayout.X_AXIS));

		southPanel.add(dataButtonPanel, BorderLayout.SOUTH);
		
		rebuildSiteList();
		
		this.add(northPanel);
		this.add(southPanel);
	}

    /**
     * Execute on any value in the SitesPanel being changed
     * @param e the event that characterizes the change.
     */
	public void valueChanged(ListSelectionEvent e) {
		if (e.getSource().equals(sitesList)) {
			// value changed in the sites list
			rebuildSiteDataList();
		}
	}

	private void checkEnableRemoveSite() {
		removeSiteButton.setEnabled(!sitesList.isSelectionEmpty());
	}

    private void checkEnableEditSite() {
        editSiteButton.setEnabled(!sitesList.isSelectionEmpty());
    }

    private void replaceSite(int index, Location loc, ArrayList<SiteDataValue<?>> vals) {
        locs.set(index, loc);
        dataLists.set(index, vals);
        rebuildSiteList();
    }

	protected void addSite(Location loc, ArrayList<SiteDataValue<?>> data) {
		this.locs.add(loc);
		if (data == null) {
			data = new ArrayList<>();
		}
		this.dataLists.add(data);
		this.rebuildSiteList();
	}
	
	public void clear() {
		locs.clear();
		dataLists.clear();
		this.rebuildSiteList();
	}
	
	private void removeSite(int i) {
		locs.remove(i);
		dataLists.remove(i);
		this.rebuildSiteList();
	}
	
	private void removeSite(int indices[]) {
		Arrays.sort(indices);
		for (int i=indices.length-1; i>=0; i--) {
			locs.remove(i);
			dataLists.remove(i);
		}
		this.rebuildSiteList();
	}
	
	private void rebuildSiteList() {
		Object data[] = new String[locs.size()];
		for (int i=0; i<locs.size(); i++) {
			Location loc = locs.get(i);
			data[i] = (i+1) + ". " + loc.getLatitude() + ", " + loc.getLongitude();
		}
//		System.out.println("rebuilding with length " + data.length);
		sitesList.setListData(data);
		checkEnableRemoveSite();
        checkEnableEditSite();
		rebuildSiteDataList();
		this.validate();
	}
	
	private int getSingleSelectedIndex() {
		int[] selection = sitesList.getSelectedIndices();
		if (selection.length > 1)
			return -1;
		return selection[0];
	}
	
	private void rebuildSiteDataList() {
		if (sitesList.isSelectionEmpty()) {
			Object[] data = new String[1];
			data[0] = "(no site(s) selected)";
			siteDataList.setListData(data);
			return;
		}
		int index = getSingleSelectedIndex();
		if (index < 0) {
			Object[] data = new String[1];
			data[0] = "(multiple selected)";
			siteDataList.setListData(data);
			return;
		}
		ArrayList<SiteDataValue<?>> vals = dataLists.get(index);
		Object[] data = new String[vals.size()];
		for (int i=0; i<vals.size(); i++) {
			SiteDataValue<?> val = vals.get(i);
			data[i] = getDataListString(i, val);
		}
		siteDataList.setListData(data);
		checkEnableRemoveSite();
        checkEnableEditSite();
	}
	
	public static String getDataListString(int index, SiteDataValue<?> val) {
		return (index+1) + ". " + val.getDataType() + ": " + val.getValue();
	}

    /**
     * Execute when a button is pressed in the SitesPanel
     * @param e the event to be processed
     */
	public void actionPerformed(ActionEvent e) {
		if (e.getSource().equals(addSiteButton)) {
            // Don't allow user to add sites if IMRs aren't selected
            if (siteDataParams.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Must have at least 1 IMR selected to add sites", "Cannot add site",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
			// adding a site
            // Unique set of site data params per new site added
			AddSitePanel siteAdd = new AddSitePanel(siteDataParams);
			int selection = JOptionPane.showConfirmDialog(this, siteAdd, "Add Site",
					JOptionPane.OK_CANCEL_OPTION);
			if (selection == JOptionPane.OK_OPTION) {
				Location loc = siteAdd.getSiteLocation();
				ArrayList<SiteDataValue<?>> vals = siteAdd.getDataVals();
				System.out.println("Adding site: " + loc + " (" + vals.size() + " vals)");
				this.addSite(loc, vals);
				rebuildSiteDataList();
			}
		} else if (e.getSource().equals(removeSiteButton)) {
            // removing a site
            int indices[] = sitesList.getSelectedIndices();
            removeSite(indices);
        } else if (e.getSource().equals(editSiteButton)) {
            // edit the selected site
            int siteIndex = sitesList.getSelectedIndex();
            AddSitePanel siteAdd = new AddSitePanel(siteDataParams, dataLists.get(siteIndex), locs.get(siteIndex));
            int selection = JOptionPane.showConfirmDialog(this, siteAdd, "Add Site",
                    JOptionPane.OK_CANCEL_OPTION);
            if (selection == JOptionPane.OK_OPTION) {
                Location loc = siteAdd.getSiteLocation();
                ArrayList<SiteDataValue<?>> vals = siteAdd.getDataVals();
                System.out.println("Editing site: " + loc + " (" + vals.size() + " vals)");
                this.replaceSite(siteIndex, loc, vals);
                rebuildSiteDataList();
            }
        }
	}

    /**
     * Dynamically builds the site data parameters from the selected IMRs.
     * Should be invoked by the IMR_ChooserPanel.
     */
    public void updateSiteDataParams(List<ScalarIMR> imrs) {
        int oldSiteDataParamsSize = siteDataParams.size();
        siteDataParams = ParameterList.union(imrs.stream()
                .map(IntensityMeasureRelationship::getSiteParams)
                .toArray(ParameterList[]::new));
        // Remove any selected site data values now unsupported across all sites
        if (siteDataParams.size() < oldSiteDataParamsSize) {
            ArrayList<ArrayList<SiteDataValue<?>>> newDataList = new ArrayList<>();
            HashSet<String> invalidatedSiteData = new HashSet<>();
            for (List<SiteDataValue<?>> siteVals : dataLists) {
                ArrayList<SiteDataValue<?>> newVals = new ArrayList<>();
                for (SiteDataValue<?> val : siteVals) {
                    if (siteDataParams.containsParameter(val.getDataType())) {
                        newVals.add(val);
                    } else {
                        invalidatedSiteData.add(val.getDataType());
                    }
                }
                newDataList.add(newVals);
            }
            dataLists = newDataList;
            // Notify user that previously selected site data types were invalidated
            if (!invalidatedSiteData.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "The following site data parameters were previously set and have been removed across all sites added so far.\n"
                            + "Removed Site Data Types: " + String.join(",", invalidatedSiteData) + "\n"
                            + "Site data parameters are generated from the selected IMRs. To avoid this in the future, select all desired IMRs before adding sites.",
                        "Site Data Parameter Removal",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }
       // Update GUI
        rebuildSiteDataList();
    }

    public ArrayList<Location> getLocs() {
        return locs;
    }

    public ArrayList<ArrayList<SiteDataValue<?>>> getDataLists() {
        return dataLists;
    }

	/**
	 * tester main method
	 * @param args
	 */
	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(400, 600);

		SitesPanel sites = new SitesPanel();
		
		sites.addSite(new Location(34, -118), null);
		ArrayList<SiteDataValue<?>> vals = new ArrayList<>();
		vals.add(new SiteDataValue<Double>(SiteData.TYPE_VS30, SiteData.TYPE_FLAG_INFERRED, 760.0));
		sites.addSite(new Location(34, -118.1), vals);
		
		frame.setContentPane(sites);
		frame.setVisible(true);
	}
}
