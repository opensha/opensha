package org.opensha.sha.calc.IM_EventSet.gui;

import org.opensha.commons.geo.Location;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.IM_EventSet.SiteFileLoader;
import org.opensha.sha.calc.IM_EventSet.SiteFileWriter;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

/**
 * Panel for exporting sites in bulk from a file
 * <p>
 * The <code>SiteExporterPanel</code> allows a user to specify the configuration
 * of site files and choose where to write the output.
 * Site files are written using the <code>SiteFileWriter</code>.
 * </p>
 */
public class SiteExporterPanel extends JPanel implements ActionListener {

    private final JLabel formatLabel = new JLabel();

    private final JButton reverseButton = new JButton("Swap lat/lon");
    private final JButton addButton = new JButton("Add Site Data Column");
    private final JButton removeButton = new JButton("Remove Site Data Column");

    private final JComboBox<String> typeChooser;

    private final JTextField fileField = new JTextField();
    private final JButton browseButton = new JButton("Browse");
    protected JFileChooser chooser;

    private boolean lonFirst = true;

    private final ArrayList<String> siteDataTypes = new ArrayList<>(List.of(SiteFileLoader.allSiteDataTypes));

    protected ArrayList<Location> locs;
    protected ArrayList<ParameterList> siteDataParams;

    private static final File cwd = new File(System.getProperty("user.dir"));

    /**
     * Constructor creates the UI panel for exporting sites from a file
     */
    public SiteExporterPanel(ArrayList<Location> locs, ArrayList<ParameterList> siteDataParams) {
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        this.locs = locs;
        this.siteDataParams = siteDataParams;

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

        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
        labelPanel.add(formatLabel);
        this.add(labelPanel);
        JPanel newButtonPanel = new JPanel();
        newButtonPanel.add(buttonPanel);
        this.add(newButtonPanel);
        this.add(new JSeparator(JSeparator.HORIZONTAL));

        JPanel browsePanel = new JPanel(new BorderLayout());
        JLabel chooserLabel = new JLabel("Output File: ");
        browsePanel.add(chooserLabel, BorderLayout.WEST);
        browsePanel.add(fileField, BorderLayout.CENTER);
        browsePanel.add(browseButton, BorderLayout.EAST);
        fileField.setColumns(40);
        browseButton.addActionListener(this);
        JPanel newBrowsePanel = new JPanel();
        newBrowsePanel.add(browsePanel);

        this.add(newBrowsePanel);
        this.setSize(700, 150);
    }

    public void updateSiteData(ArrayList<Location> locs, ArrayList<ParameterList> siteDataParams) {
        this.locs = locs;
        this.siteDataParams = siteDataParams;
    }

    /**
     * Write to selected file using <code>SiteFileWriter</code>
     */
    public void exportFile() throws IOException {
        SiteFileWriter writer = new SiteFileWriter(lonFirst, siteDataTypes);
        writer.writeFile(locs, siteDataParams, getSelectedFile());
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

    @Override
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
            int returnVal = chooser.showSaveDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                fileField.setText(file.getAbsolutePath());
            }
        }
        addButton.setEnabled(typeChooser.getItemCount() > 0);
        boolean hasTypes = !siteDataTypes.isEmpty();
        removeButton.setEnabled(hasTypes);
        updateLabel();
    }

    public File getSelectedFile() {
        String fileName = fileField.getText();
        return new File(fileName);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(new SiteExporterPanel(new ArrayList<>(), new ArrayList<>()));
        frame.setSize(700, 150);

        frame.setVisible(true);
    }

}
