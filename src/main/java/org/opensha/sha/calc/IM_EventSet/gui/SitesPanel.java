package org.opensha.sha.calc.IM_EventSet.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
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

import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.gui.beans.OrderedSiteDataGUIBean;
import org.opensha.commons.data.siteData.impl.WillsMap2000;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.imr.IntensityMeasureRelationship;
import org.opensha.sha.imr.ScalarIMR;

/**
 * Panel to display and edit a list of multiple sites and their associated site data.
 * This panel contains buttons to add, remove, and edit sites.
 * Add or edit a site via the AddSitePanel.
 */
public class SitesPanel extends JPanel implements ListSelectionListener, ActionListener {
	
	protected JList sitesList;
	protected JList siteDataList;
	
	protected JButton addSiteButton = new JButton("Add Site");
	protected JButton removeSiteButton = new JButton("Remove Site(s)");
	protected JButton editSiteButton = new JButton("Edit Site");
    protected JButton importSitesButton = new JButton("Import Sites From File");
    protected JButton exportSitesButton = new JButton("Export Sites");

    // List of locations for each site
	private ArrayList<Location> locs;
    // List of all site data parameters for each site
    private ArrayList<ParameterList> siteDataParams;
    // Default site data parameters to use for new sites
    private ParameterList defaultSiteDataParams;
    // Set sites with selected site data providers
    private final OrderedSiteDataGUIBean siteDataGUIBean;

    private SiteImporterPanel imp;
    private SiteExporterPanel exp;

	public SitesPanel(OrderedSiteDataGUIBean siteDataGUIBean) {
		super();
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        this.siteDataGUIBean = siteDataGUIBean;

		locs = new ArrayList<Location>();
        siteDataParams = new ArrayList<>();
        defaultSiteDataParams = new ParameterList();

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
        JPanel rightButtonPanel = new JPanel();
		leftButtonPanel.setLayout(new BoxLayout(leftButtonPanel, BoxLayout.X_AXIS));
        rightButtonPanel.setLayout(new BoxLayout(rightButtonPanel, BoxLayout.X_AXIS));
        rightButtonPanel.add(editSiteButton, BorderLayout.EAST);
        rightButtonPanel.add(importSitesButton, BorderLayout.EAST);
        rightButtonPanel.add(exportSitesButton, BorderLayout.EAST);
		leftButtonPanel.add(addSiteButton);
		leftButtonPanel.add(removeSiteButton);
		buttonPanel.add(leftButtonPanel, BorderLayout.WEST);
        buttonPanel.add(rightButtonPanel, BorderLayout.EAST);
		removeSiteButton.setEnabled(false);
		addSiteButton.addActionListener(this);
		removeSiteButton.addActionListener(this);
		editSiteButton.addActionListener(this);
        importSitesButton.addActionListener(this);
        exportSitesButton.addActionListener(this);
		
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

    private void checkEnableExportSite() {
        exportSitesButton.setEnabled(!locs.isEmpty());
    }

    private void replaceSite(int index, Location loc, ParameterList params) {
        locs.set(index, loc);
        siteDataParams.set(index, params);
        rebuildSiteList();
    }

	protected void addSite(Location loc, ParameterList params) {
		locs.add(loc);
        siteDataParams.add(params);
		rebuildSiteList();
	}
	
	public void clear() {
		locs.clear();
		siteDataParams.clear();
		rebuildSiteList();
	}
	
	private void removeSite(int i) {
		locs.remove(i);
		siteDataParams.remove(i);
		rebuildSiteList();
	}
	
	private void removeSite(int[] indices) {
		Arrays.sort(indices);
		for (int i=indices.length-1; i>=0; i--) {
			locs.remove(i);
			siteDataParams.remove(i);
		}
		rebuildSiteList();
	}
	
	private void rebuildSiteList() {
		Object[] data = new String[locs.size()];
		for (int i=0; i<locs.size(); i++) {
			Location loc = locs.get(i);
			data[i] = (i+1) + ". " + loc.getLatitude() + ", " + loc.getLongitude();
		}
//		System.out.println("rebuilding with length " + data.length);
		sitesList.setListData(data);
		checkEnableRemoveSite();
        checkEnableEditSite();
        checkEnableExportSite();
		rebuildSiteDataList();
		this.validate();
	}
	
	private int getSingleSelectedIndex() {
		int[] selection = sitesList.getSelectedIndices();
		if (selection.length > 1)
			return -1;
		return selection[0];
	}
	
	public void rebuildSiteDataList() {
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
        ParameterList params = siteDataParams.get(index);
		Object[] data = new String[params.size()];
        int i = 0;
        for (Parameter<?> param : params) {
           data[i] = getDataListString(i++, param);
        }
		siteDataList.setListData(data);
		checkEnableRemoveSite();
        checkEnableEditSite();
        checkEnableExportSite();
	}
	
	public static String getDataListString(int index, Parameter<?> param) {
        return (index+1) + ". " + param.getName() + ": " + param.getValue();
	}

    /**
     * Prompts the user with a confirmation dialog to add or edit a site
     * using the provided <code>AddSitePanel</code>.
     * <p>
     * This method reprompts the user with the same panel while preserving
     * selections made by the user in the previous dialog. This is required
     * to prevent duplicate locations.
     * An exception for the duplicate location check includes the provided
     * <code>exception</code> parameter. This is required to enable editing
     * the same site.
     * </p>
     * @param siteAdd preserved <code>AddSitePanel</code> with user selections for a new site
     * @param exception location to exclude from duplicate location check
     * @return user selection decision (<code>OK</code> or <code>CANCEL</code>)
     */
    private int promptNewSite(AddSitePanel siteAdd, Location exception) {
        int selection = JOptionPane.showConfirmDialog(this, siteAdd, "Add Site",
                JOptionPane.OK_CANCEL_OPTION);
        if (selection == JOptionPane.OK_OPTION) {
            Location loc = siteAdd.getSiteLocation();
            // Locations are considered duplicates if they have the same latitude and longitude.
            // We do not consider depth in this check.
            boolean allowDuplicateSite =
                    (exception != null && exception.getLongitude() == loc.getLongitude() && exception.getLatitude() == loc.getLatitude());
            if (!allowDuplicateSite && locs.contains(loc)) {
                JOptionPane.showMessageDialog(this, "Site with coordinates (" + loc.getLatitude() + ", " + loc.getLongitude() + ") already exists", "Cannot add site",
                        JOptionPane.ERROR_MESSAGE);
                return promptNewSite(siteAdd, exception);
            }
        }
        return selection;
    }

    private int promptNewSite(AddSitePanel siteAdd) {
        return promptNewSite(siteAdd, null);
    }

    /**
     * Logic for importing sites from a file
     */
    private void importSites() {
        if (imp == null)
            imp = new SiteImporterPanel(defaultSiteDataParams, siteDataGUIBean.getProviderList());
        else
            imp.setDefaultSiteDataParams(defaultSiteDataParams);

        int selection = JOptionPane.showConfirmDialog(this, imp, "Import Sites",
                JOptionPane.OK_CANCEL_OPTION);
        if (selection == JOptionPane.OK_OPTION) {
            File file = imp.getSelectedFile();
            if (file.exists()) {
                try {
                    imp.importFile(file);
                    ArrayList<Location> importedLocs = imp.getLocs();
                    ArrayList<ParameterList> importedVals = imp.getSiteData();
                    for (int i = 0; i < importedLocs.size(); i++) {
                       Location impLoc = importedLocs.get(i);
                       ParameterList impVals = importedVals.get(i);
                        // Check for existing sites to overwrite values
                        if (locs.contains(impLoc)) {
                            this.replaceSite(locs.indexOf(impLoc), impLoc, impVals);
                        } else {
                            locs.add(impLoc);
                            siteDataParams.add(impVals);
                        }

                    }
                    this.rebuildSiteList();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(this, "I/O error reading file!",
                            "I/O error reading file!", JOptionPane.ERROR_MESSAGE);
                } catch (ParseException e1) {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(this, e1.getMessage(),
                            "Error Parsing File", JOptionPane.ERROR_MESSAGE);
                } catch (NumberFormatException e1) {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(this, e1.getMessage(),
                            "Error Parsing Number", JOptionPane.ERROR_MESSAGE);
                } catch (InvalidRangeException e1) {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(this, e1.getMessage(),
                            "Invalid location", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "File '" + file.getPath() + "' doesn't exist!",
                        "File Not Found", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Logic for exporting sites to a file
     */
    private void exportSites() {
        if (exp == null)
            exp = new SiteExporterPanel(locs, siteDataParams);
        else
            exp.updateSiteData(locs, siteDataParams);
        int selection = JOptionPane.showConfirmDialog(this, exp, "Export Sites",
                JOptionPane.OK_CANCEL_OPTION);
        if (selection == JOptionPane.OK_OPTION) {
            try {
                exp.exportFile();
            } catch (IOException e1) {
                e1.printStackTrace();
                JOptionPane.showMessageDialog(this, "I/O error reading file!",
                        "I/O error reading file!", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Execute when a button is pressed in the SitesPanel
     * @param e the event to be processed
     */
	public void actionPerformed(ActionEvent e) {
        OrderedSiteDataProviderList providers = siteDataGUIBean.getProviderList();
		if (e.getSource().equals(addSiteButton)) {
            // Don't allow user to add sites if IMRs aren't selected
            if (defaultSiteDataParams.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Must have at least 1 IMR selected to add sites", "Cannot add site",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
			// adding a site
            // Unique set of site data params per new site added
            ParameterList params = (ParameterList)defaultSiteDataParams.clone();
			AddSitePanel siteAdd = new AddSitePanel(params, providers);
            int selection = promptNewSite(siteAdd);
			if (selection == JOptionPane.OK_OPTION) {
				Location loc = siteAdd.getSiteLocation();
				System.out.println("Adding site: " + loc + " (" + params.size() + " vals)");
                addSite(loc, params);
				rebuildSiteDataList();
			}
		} else if (e.getSource().equals(removeSiteButton)) {
            // removing a site
            int[] indices = sitesList.getSelectedIndices();
            removeSite(indices);
        } else if (e.getSource().equals(editSiteButton)) {
            // edit the selected site
            int siteIndex = sitesList.getSelectedIndex();
            ParameterList params = siteDataParams.get(siteIndex);
            AddSitePanel siteAdd = new AddSitePanel(params, providers, locs.get(siteIndex));
            int selection = promptNewSite(siteAdd, /*exception=*/locs.get(siteIndex));
            if (selection == JOptionPane.OK_OPTION) {
                Location loc = siteAdd.getSiteLocation();
                System.out.println("Editing site: " + loc + " (" + params.size() + " vals)");
                this.replaceSite(siteIndex, loc, params);
                rebuildSiteDataList();
            }
        } else if (e.getSource().equals(importSitesButton)) {
            // Don't allow user to add sites if IMRs aren't selected
            if (defaultSiteDataParams.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Must have at least 1 IMR selected to import sites", "Cannot import sites",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            importSites();
        } else if (e.getSource().equals(exportSitesButton)) {
            exportSites();
        }
	}

    /**
     * Dynamically builds the site data parameters from the selected IMRs.
     * @param imrs
     * @param params
     * @return the newly updated list
     */
    private ParameterList updateSiteDataParams(List<ScalarIMR> imrs, ParameterList params) {
        ParameterList oldParams = (ParameterList)params.clone();
        ParameterList newParams = ParameterList.union(imrs.stream()
                .map(IntensityMeasureRelationship::getSiteParams)
                .toArray(ParameterList[]::new));
        // Copy any existing parameter values from the old list
        for (Parameter<?> oldParam : oldParams) {
            if (newParams.containsParameter(oldParam)) {
                newParams.setValue(oldParam.getName(), oldParam.getValue());
            }
        }
        return newParams;
    }

    /**
     * List of supported types is needed to find which types become unsupported in updateSiteDataParams.
     * @return a list of the names of all supported site data types
     */
    private List<String> getSiteDataTypes() {
        ArrayList<String> supportedTypes = new ArrayList<>();
        for (Parameter<?> param : defaultSiteDataParams) {
            supportedTypes.add(param.getName());
        }
        return supportedTypes;
    }

    /**
     * Dynamically builds the site data parameters from the selected IMRs.
     * Should be invoked by the IMR_ChooserPanel.
     */
    public void updateSiteDataParams(List<ScalarIMR> imrs) {
        // Update default site params for creating new sites and note which types are now invalid.
        List<String> invalidatedSiteDataTypes = getSiteDataTypes();
        System.out.println(invalidatedSiteDataTypes);
        defaultSiteDataParams = updateSiteDataParams(imrs, defaultSiteDataParams);
        List<String> newSiteDataTypes = getSiteDataTypes();
        invalidatedSiteDataTypes.removeAll(newSiteDataTypes);

        // Update existing sites with the updated parameter types
        ArrayList<ParameterList> newSiteDataParams = new ArrayList<>();
        for (ParameterList params : siteDataParams) {
            newSiteDataParams.add(updateSiteDataParams(imrs, params));
        }
        siteDataParams = newSiteDataParams;

        // Notify user that previously selected site data types were invalidated
        if (!invalidatedSiteDataTypes.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "The following site data parameters were previously set and have been removed across all sites added so far.\n"
                        + "Removed Site Data Types: " + String.join(",", invalidatedSiteDataTypes) + "\n"
                        + "Site data parameters are generated from the selected IMRs. To avoid this in the future, select all desired IMRs before adding sites.",
                    "Site Data Parameter Removal",
                    JOptionPane.INFORMATION_MESSAGE);
        }

       // Update GUI
        rebuildSiteDataList();
    }

    public ArrayList<Location> getLocs() {
        return locs;
    }

    /**
     * Gets the list of site data parameters.
     * Each parameter list represents all of the site data and its values for each site.
     * @return
     */
    public ArrayList<ParameterList> getSiteDataParams() {
        return siteDataParams;
    }

	/**
	 * tester main method
	 * @param args
	 */
	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(400, 600);

        // Creating a SitesPanel requires a site data gui bean to monitor for provider changes
        // To mock this we'll create a new gui bean with only one provider (WillsMap2000).
        ArrayList<SiteData<?>> params = new ArrayList<>();
        params.add(new WillsMap2000());
        OrderedSiteDataProviderList providers = new OrderedSiteDataProviderList(params);
		SitesPanel sites = new SitesPanel(new OrderedSiteDataGUIBean(providers));
		sites.addSite(new Location(34, -118), null);

		frame.setContentPane(sites);
		frame.setVisible(true);
	}
}
