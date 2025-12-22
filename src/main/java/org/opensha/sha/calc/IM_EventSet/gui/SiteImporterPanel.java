package org.opensha.sha.calc.IM_EventSet.gui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.IM_EventSet.SiteFileLoader;
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

	private final JComboBox<String> typeChooser;
	private final JComboBox<String> measChooser;

	private final JTextField fileField = new JTextField();
	private final JButton browseButton = new JButton("Browse");
	private JFileChooser chooser;
	
	private boolean lonFirst = false;

	private final ArrayList<String> siteDataTypes = new ArrayList<>();

	private ArrayList<Location> locs;
    private ArrayList<ParameterList> siteDataParams;

    private ParameterList defaultSiteDataParams;
    private final SiteTranslator siteTrans = new SiteTranslator();

    private static final File cwd = new File(System.getProperty("user.dir"));

    // TODO: Add "Set Params from Web Services" here, similar to in AddSitePanel
    //       Distinction being that these would set params that aren't explicitly defined in the file
    // TODO: Confirm desired behavior with Kevin

    /**
     * Constructor creates the UI panel for importing sites from a file
     * @param defaultSiteDataParams - Params used for creating a new site
     */
	public SiteImporterPanel(ParameterList defaultSiteDataParams) {
		this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        this.defaultSiteDataParams = defaultSiteDataParams;

        typeChooser = new JComboBox<>(SiteFileLoader.allSiteDataTypes);

        chooser = new JFileChooser(cwd);
		
		updateLabel();
		
		reverseButton.addActionListener(this);
		addButton.addActionListener(this);
		removeButton.addActionListener(this);
		removeButton.setEnabled(false);

		JPanel buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(reverseButton, BorderLayout.WEST);
		JPanel rightButtonPanel = new JPanel();
		rightButtonPanel.setLayout(new BoxLayout(rightButtonPanel, BoxLayout.X_AXIS));
		rightButtonPanel.add(typeChooser);
		rightButtonPanel.add(addButton);
		rightButtonPanel.add(removeButton);
		buttonPanel.add(rightButtonPanel, BorderLayout.EAST);
		
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
		
		JPanel browsePanel = new JPanel(new BorderLayout());
		browsePanel.add(fileField, BorderLayout.CENTER);
		browsePanel.add(browseButton, BorderLayout.EAST);
		fileField.setColumns(40);
		browseButton.addActionListener(this);
		JPanel newBrowsePanel = new JPanel();
		newBrowsePanel.add(browsePanel);
		
		this.add(newBrowsePanel);
		this.setSize(700, 150);
	}
	
	private void updateLabel() {
		StringBuilder label = new StringBuilder("File format: ");
		if (lonFirst)
			label.append("<Latitude> <Longitude>");
		else
			label.append("<Longitude> <Latitude>");
		
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

        siteDataParams = new ArrayList<>();
		for (ArrayList<SiteDataValue<?>> siteVals : loader.getValsList()) {
            ParameterList params = (ParameterList) defaultSiteDataParams.clone();
            params.forEach(param -> siteTrans.setParameterValue(param, siteVals));
            siteDataParams.add(params);
        }
	}

    public void setDefaultSiteDataParams(ParameterList defaultSiteDataParams) {
        this.defaultSiteDataParams = defaultSiteDataParams;
    }
	
	public ArrayList<Location> getLocs() {
		return locs;
	}

	public ArrayList<ParameterList> getSiteData() {
		return siteDataParams;
	}

	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ParameterList params = new ParameterList();
		frame.setContentPane(new SiteImporterPanel(params));
		frame.setSize(700, 150);
		
		frame.setVisible(true);
	}
}
