package org.opensha.sha.earthquake.faultSysSolution.modules;

import org.opensha.commons.util.modules.helpers.TextBackedModule;

public class InfoModule implements TextBackedModule {
	
	private String info;

	public InfoModule(String info) {
		this.info = info;
	}

	@Override
	public String getFileName() {
		return "info.txt";
	}

	@Override
	public String getName() {
		return "Info";
	}

	@Override
	public String getText() {
		return info;
	}

	@Override
	public void setText(String text) {
		this.info = text;
	}

	@Override
	public String toString() {
		return info;
	}

}
