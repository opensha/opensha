package org.opensha.commons.param.editor.impl;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;

import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.editor.AbstractParameterEditor;
import org.opensha.commons.param.impl.FileParameter;

public class FileParameterEditor extends AbstractParameterEditor<File> implements ActionListener {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JButton browseButton;
	private JFileChooser chooser;
	
	private static final String Browse = "Browse";
	
	public FileParameterEditor(FileParameter param) {
		super(param);
		
		browseButton.addActionListener(this);
	}
	
	@Override
	public void setEnabled(boolean isEnabled) {
		browseButton.setEnabled(isEnabled);
	}

	private JButton getBrowseButton() {
		if (browseButton == null)
			browseButton = new JButton(Browse);
		return browseButton;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == browseButton) {
			if (chooser == null) {
				File initialDir = null;
				if (getParameter() instanceof FileParameter) {
					initialDir = ((FileParameter)getParameter()).getDefaultInitialDir();
				}
				chooser = new JFileChooser(initialDir);
			}
			if (getParameter() instanceof FileParameter && ((FileParameter)getParameter()).isDirectorySelect())
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			else
				chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			if (getParameter() instanceof FileParameter)
				chooser.setFileHidingEnabled(!((FileParameter)getParameter()).isShowHiddenFiles());
			int retVal = chooser.showOpenDialog(this);
			if (retVal == JFileChooser.APPROVE_OPTION) {
				File file = chooser.getSelectedFile();
				setValue(file);
			}
		}
	}

	@Override
	public boolean isParameterSupported(Parameter<File> param) {
		if (param == null)
			return false;
		return (param.getValue() == null && param.isNullAllowed()) || param.getValue() instanceof File;
	}

	@Override
	protected JComponent buildWidget() {
		JButton button = getBrowseButton();
		File file = getValue();
		if (file == null)
			button.setText(Browse);
		else {
			String name = file.getName();
			if (name.length() > 20) {
				int splitIndex = name.lastIndexOf('.');
				if (splitIndex > 0) {
					String main = name.substring(0, splitIndex);
					String ext = name.substring(splitIndex);
					if (main.length() > 20) {
						main = main.substring(0, 20);
						name = main+"(...)"+ext;
					}
				} else {
					name = name.substring(0, 20)+"(...)";
				}
			}
			button.setText(name);
		}
		return button;
	}

	@Override
	protected JComponent updateWidget() {
		return buildWidget();
	}
	
	public void setDefaultDir(File dir) {
		if (chooser == null)
			chooser = new JFileChooser();
		chooser.setCurrentDirectory(dir);
	}

}
