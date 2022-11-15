package org.opensha.commons.hpc.mpj;

import java.io.File;
import java.util.Collection;

import org.dom4j.Element;
import org.opensha.commons.hpc.JavaShellScriptWriter;

public class NoMPJSingleNodeShellScriptWriter extends JavaShellScriptWriter {
	
	public NoMPJSingleNodeShellScriptWriter(File javaBin, int heapSizeMB, Collection<File> classpath) {
		super(javaBin, heapSizeMB, classpath);
	}

	@Override
	protected String getJVMArgs(String className) {
		return " -Dmpj.disable=\"true\" "+super.getJVMArgs(className);
	}

	@Override
	public Element toXMLMetadata(Element root) {
		throw new IllegalStateException();
	}
	
	public static NoMPJSingleNodeShellScriptWriter fromXMLMetadata(Element mpjEl) {
		throw new IllegalStateException();
	}

}
