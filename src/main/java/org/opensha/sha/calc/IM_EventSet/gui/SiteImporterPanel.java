package org.opensha.sha.calc.IM_EventSet.gui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.IM_EventSet.SiteFileLoader;
import org.opensha.sha.imr.param.SiteParams.DepthTo1pt0kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.DepthTo2pt5kmPerSecParam;
import org.opensha.sha.imr.param.SiteParams.Vs30_Param;
import org.opensha.sha.imr.param.SiteParams.Vs30_TypeParam;
import org.opensha.sha.util.SiteTranslator;

/**
 * Panel for importing sites in bulk from a file
 * <p>
 * The <code>SiteImporterPanel</code> allows a user to specify the configuration
 * of site files and provide the path to the site file to import from.
 * Site files are read into memory with the <code>SiteFileLoader</code> and then
 * used to configure a set of site parameters. It is the responsibility of the
 * calling application, the <code>SitesPanel</code>, to then merge these imported
 * sites with existing sites for use in IM Event Set calculations.
 * </p>
 */
public class SiteImporterPanel extends JPanel implements ActionListener {

	private final JLabel formatLabel = new JLabel();
    private final JLabel measLabel = new JLabel("Site Data Measurement Type: ");

	private final JButton reverseButton = new JButton("Swap lat/lon");
	private final JButton addButton = new JButton("Add Site Data Column");
	private final JButton removeButton = new JButton("Remove Site Data Column");

    private final JCheckBox setFromWebCheck = new JCheckBox("Set Params from Web Services");

	private final JComboBox<String> typeChooser;
	private final JComboBox<String> measChooser;

	private final JTextField fileField = new JTextField();
	private final JButton browseButton = new JButton("Browse");
	private final JFileChooser chooser;
	
	private boolean lonFirst = true;

	private final ArrayList<String> siteDataTypes = new ArrayList<>(List.of(SiteFileLoader.allSiteDataTypes));

	private ArrayList<Location> locs;
    private ArrayList<ParameterList> userParams;

    private ParameterList defaultSiteDataParams;
    private final SiteTranslator siteTrans = new SiteTranslator();
    private final OrderedSiteDataProviderList providers;

    private static final File cwd = new File(System.getProperty("user.dir"));

    /**
     * Constructor creates the UI panel for importing sites from a file
     * @param defaultSiteDataParams - Params used for creating a new site
     * @param providers - site data providers to retrieve from if checkbox to set params from web services is checked
     */
	public SiteImporterPanel(ParameterList defaultSiteDataParams, OrderedSiteDataProviderList providers) {
		this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        this.providers = providers;
        this.defaultSiteDataParams = defaultSiteDataParams;

        typeChooser = new JComboBox<>();

        chooser = new JFileChooser(cwd);
		
		updateLabel();
		
		reverseButton.addActionListener(this);
		addButton.addActionListener(this);
		removeButton.addActionListener(this);
		addButton.setEnabled(false);

		JPanel buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(reverseButton, BorderLayout.WEST);
		JPanel rightButtonPanel = new JPanel();
		rightButtonPanel.setLayout(new BoxLayout(rightButtonPanel, BoxLayout.X_AXIS));
		rightButtonPanel.add(typeChooser);
		rightButtonPanel.add(addButton);
		rightButtonPanel.add(removeButton);
		buttonPanel.add(rightButtonPanel, BorderLayout.EAST);

        JPanel setFromWebPanel = new JPanel();
        setFromWebPanel.setLayout(new BoxLayout(setFromWebPanel, BoxLayout.X_AXIS));
        setFromWebPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JTextArea infoText = new JTextArea(
                "Checking the box to the left will retrieve the best available data for each site's location\n"
                        + "with the values from the web services. Values provided from the imported file are not overwritten.\n"
                        + "See the \"Site Data Providers\" pane to add or remove selected providers.\n");
        infoText.setEditable(false);
        infoText.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setFromWebPanel.add(setFromWebCheck);
        setFromWebPanel.add(Box.createHorizontalStrut(10));
        setFromWebPanel.add(infoText);


        measChooser = new JComboBox<>(new String[]{
            SiteData.TYPE_FLAG_INFERRED,
            SiteData.TYPE_FLAG_MEASURED
        });
		JPanel measPanel = new JPanel();
		measPanel.setLayout(new BoxLayout(measPanel, BoxLayout.X_AXIS));
		measLabel.setEnabled(false);
		measChooser.setEnabled(false);
		measPanel.add(measLabel);
		measPanel.add(measChooser);
		JPanel newMeasPanel = new JPanel();
		newMeasPanel.add(measPanel);
		
		JPanel labelPanel = new JPanel();
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
		labelPanel.add(formatLabel);
		this.add(labelPanel);
		JPanel newButtonPanel = new JPanel();
		newButtonPanel.add(buttonPanel);
		this.add(newButtonPanel);
		this.add(newMeasPanel);
		this.add(new JSeparator(JSeparator.HORIZONTAL));
        JPanel newSetFromWebPanel = new JPanel();
        newSetFromWebPanel.add(setFromWebPanel);
        this.add(newSetFromWebPanel);
        this.add(new JSeparator(JSeparator.HORIZONTAL));

		JPanel browsePanel = new JPanel(new BorderLayout());
        JLabel chooserLabel = new JLabel("Input File: ");
        browsePanel.add(chooserLabel, BorderLayout.WEST);
		browsePanel.add(fileField, BorderLayout.CENTER);
		browsePanel.add(browseButton, BorderLayout.EAST);
		fileField.setColumns(40);
		browseButton.addActionListener(this);
		JPanel newBrowsePanel = new JPanel();
		newBrowsePanel.add(browsePanel);
		
		this.add(newBrowsePanel);
		this.setSize(1000, 350);
	}
	
	private void updateLabel() {
		StringBuilder label = new StringBuilder("File format: ");
		if (lonFirst)
            label.append("<Longitude> <Latitude>");
		else
            label.append("<Latitude> <Longitude>");

		for (String dataType : siteDataTypes) {
			label.append(" <").append(dataType).append(">");
		}
		formatLabel.setText(label.toString());
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource().equals(reverseButton)) {
			lonFirst = !lonFirst;
		} else if (e.getSource().equals(addButton)) {
			int selected = typeChooser.getSelectedIndex();
			siteDataTypes.add((String)typeChooser.getSelectedItem());
			typeChooser.removeItemAt(selected);
		} else if (e.getSource().equals(removeButton)) {
			int index = siteDataTypes.size() - 1;
			typeChooser.addItem(siteDataTypes.get(index));
			siteDataTypes.remove(index);
		} else if (e.getSource().equals(browseButton)) {
			int returnVal = chooser.showOpenDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = chooser.getSelectedFile();
				fileField.setText(file.getAbsolutePath());
			}
		}
		addButton.setEnabled(typeChooser.getItemCount() > 0);
		boolean hasTypes = !siteDataTypes.isEmpty();
		measLabel.setEnabled(hasTypes);
		measChooser.setEnabled(hasTypes);
		removeButton.setEnabled(hasTypes);
		updateLabel();
	}
	
	public File getSelectedFile() {
		String fileName = fileField.getText();
        return new File(fileName);
	}

	public void importFile(File file) throws IOException, ParseException {
		SiteFileLoader loader = new SiteFileLoader(lonFirst, (String)measChooser.getSelectedItem(), siteDataTypes);
		
		loader.loadFile(file);
		
		locs = loader.getLocs();

        userParams = new ArrayList<>();
		for (ArrayList<SiteDataValue<?>> siteVals : loader.getValsList()) {
            ParameterList params = (ParameterList) defaultSiteDataParams.clone();
            params.forEach(param -> siteTrans.setParameterValue(param, siteVals));
            userParams.add(params);
        }
	}

    public void setDefaultSiteDataParams(ParameterList defaultSiteDataParams) {
        this.defaultSiteDataParams = defaultSiteDataParams;
    }
	
	public ArrayList<Location> getLocs() {
		return locs;
	}

    /**
     * Retrieves site data from the sites file
     * <p>
     * If provider data is retrieved, it's merged with imported site data while preserving
     * any site data values the user has requested.
     * </p>
     * @return
     */
	public ArrayList<ParameterList> getSiteData() {
        if (!setFromWebCheck.isSelected()) {
            return userParams;
        }

        ArrayList<ParameterList> siteData = new ArrayList<>();

        // Merge user and provider data, while preserving selected Vs30, Z1.0, and Z2.5 data from sites file
        for (int i = 0; i < userParams.size(); i++) {
            if (userParams.get(i) == null) continue;
            ParameterList mergedData = (ParameterList) userParams.get(i).clone();
            Location loc = getLocs().get(i);
            if (loc == null) continue;

            ArrayList<SiteDataValue<?>> provData = providers.getBestAvailableData(loc);
            for (Parameter<?> param : mergedData) {
                if (param.getName().equals(Vs30_TypeParam.NAME)) continue; // Always explicitly set by user
                if (siteDataTypes.contains(SiteData.TYPE_VS30) && param.getName().equals(Vs30_Param.NAME)) continue;
                if (siteDataTypes.contains(SiteData.TYPE_DEPTH_TO_1_0) && param.getName().equals(DepthTo1pt0kmPerSecParam.NAME)) continue;
                if (siteDataTypes.contains(SiteData.TYPE_DEPTH_TO_2_5) && param.getName().equals(DepthTo2pt5kmPerSecParam.NAME)) continue;
                // We only use provider data if the user didn't request to import that data from the site file
                siteTrans.setParameterValue(param, provData);
            }
            siteData.add(mergedData);
        }
		return siteData;
	}

	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ParameterList params = new ParameterList();
		frame.setContentPane(new SiteImporterPanel(
                params, OrderedSiteDataProviderList.createSiteDataProviderDefaults()));
		frame.setSize(1000, 350);
		
		frame.setVisible(true);
	}
}
