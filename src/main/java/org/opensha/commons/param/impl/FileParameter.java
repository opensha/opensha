package org.opensha.commons.param.impl;

import java.io.File;

import org.dom4j.Element;
import org.opensha.commons.param.AbstractParameter;
import org.opensha.commons.param.editor.ParameterEditor;
import org.opensha.commons.param.editor.impl.FileParameterEditor;

public class FileParameter extends AbstractParameter<File> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private FileParameterEditor editor;
	private File initialDir;
	private boolean directorySelect = false;
	private boolean showHiddenFiles = false;
	
	public FileParameter(String name) {
		this(name, null);
	}
	
	public FileParameter(String name, File file) {
		super(name, null, null, file);
	}

	@Override
	public ParameterEditor<File> getEditor() {
		if (editor == null)
			editor = new FileParameterEditor(this);
		return editor;
	}

	@Override
	public Object clone() {
		return new FileParameter(this.getName(), this.getValue());
	}

	@Override
	public boolean setIndividualParamValueFromXML(Element el) {
		File file = new File(el.attributeValue("value"));
		setValue(file);
		return true;
	}
	
	public void setDefaultInitialDir(File initialDir) {
		this.initialDir = initialDir;
	}
	
	public File getDefaultInitialDir() {
		return initialDir;
	}

	public boolean isDirectorySelect() {
		return directorySelect;
	}

	public void setDirectorySelect(boolean directorySelect) {
		this.directorySelect = directorySelect;
	}
	
	public boolean isShowHiddenFiles() {
		return showHiddenFiles;
	}
	
	public void setShowHiddenFiles(boolean showHiddenFiles) {
		this.showHiddenFiles = showHiddenFiles;
	}

}
