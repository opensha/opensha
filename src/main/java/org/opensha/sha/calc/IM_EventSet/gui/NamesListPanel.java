package org.opensha.sha.calc.IM_EventSet.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Abstract Swing panel for maintaining a scrollable list of strings with add/remove functionality.
 * Used as a base class for selection panels in the IM Event Set Calculator GUI, specifically
 * for managing lists of IMRs (Attenuation Relationships) and IMTs (Intensity Measure Types).
 * <p>
 * Provides standard UI layout with JList, add/remove buttons, and selection handling.
 * Subclasses implement domain-specific logic for adding/removing items and button enablement.
 * </p>
 */
public abstract class NamesListPanel extends JPanel implements ListSelectionListener, ActionListener {
	
	protected JList<String> namesList;
	
	protected JButton addButton = new JButton("Add");
	protected JButton removeButton = new JButton("Remove");
	
	/**
	 * A vertical column with a list of names that can be added or removed.
	 * This class is meant to be extended, the child class will implement the adder.
	 * By default, the list of values will be the first in the column, unless
	 * an upperPanel is provided.
	 * @param upperPanel: Optional first panel
	 * @param label: Name for set of values
	 */
	public NamesListPanel(JPanel upperPanel, String label) {
		super(new BorderLayout());
		
		namesList = new JList<>();
		namesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		namesList.setSelectedIndex(0);
		namesList.addListSelectionListener(this);
		
		JScrollPane listScroller = new JScrollPane(namesList);
		listScroller.setPreferredSize(new Dimension(250, 150));
		
		JPanel listPanel = new JPanel(new BorderLayout());
		listPanel.add(new JLabel(label), BorderLayout.NORTH);
		listPanel.add(listScroller, BorderLayout.CENTER);
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		buttonPanel.add(addButton);
		buttonPanel.add(removeButton);
		removeButton.setEnabled(false);
		addButton.addActionListener(this);
		removeButton.addActionListener(this);
		
		listPanel.add(buttonPanel, BorderLayout.SOUTH);
		
		if (upperPanel != null) {
			upperPanel.setBorder(
					BorderFactory.createEmptyBorder(0, 0, 10, 0));
			this.add(upperPanel, BorderLayout.NORTH);
			this.validate();
			this.add(listPanel, BorderLayout.CENTER);
		} else {
			this.add(listPanel, BorderLayout.NORTH);
		}
		
	}
	
	/**
	 * Default constructor with no upperPanel provided
	 * @param label
	 */
	public NamesListPanel(String label) {
		this(/*upperPanel=*/null, label);
	}
	
	/**
	 * Use to set the Center panel. If an upperPanel was provided previously,
	 * this would overwrite the listPanel (which is now at center).
	 * Only use if no upperPanel was specified.
	 * @param lowerPanel
	 */
	public void setLowerPanel(JPanel lowerPanel) {
		this.add(lowerPanel, BorderLayout.CENTER);
		this.validate();
	}
	
	public void valueChanged(ListSelectionEvent e) {
		removeButton.setEnabled(shouldEnableRemoveButton());
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == addButton) {
			addButton_actionPerformed();
			addButton.setEnabled(shouldEnableAddButton());
		} else if (e.getSource() == removeButton) {
			removeButton_actionPerformed();
			removeButton.setEnabled(shouldEnableRemoveButton());
			addButton.setEnabled(shouldEnableAddButton());
		}
	}
	
	public abstract void addButton_actionPerformed();
	
	public abstract void removeButton_actionPerformed();
	
	public abstract boolean shouldEnableAddButton();
	
	public boolean shouldEnableRemoveButton() {
		return !namesList.isSelectionEmpty();
	}

}
