package org.opensha.commons.hpc.grid;

import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;

public class GridResources implements XMLSaveable {
	
	public static final String XML_METADATA_NAME = "GridResources";
	
	private SubmitHost submitHost;
	private ResourceProvider resourceProvider;
	private StorageHost storageHost;
	
	public GridResources(SubmitHost submitHost, ResourceProvider resourceProvider, StorageHost storageHost) {
		this.submitHost = submitHost;
		this.resourceProvider = resourceProvider;
		this.storageHost = storageHost;
	}

	public SubmitHost getSubmitHost() {
		return submitHost;
	}

	public ResourceProvider getResourceProvider() {
		return resourceProvider;
	}

	public StorageHost getStorageHost() {
		return storageHost;
	}
	
	public Element toXMLMetadata(Element root) {
		Element xml = root.addElement(XML_METADATA_NAME);
		
		xml = this.submitHost.toXMLMetadata(xml);
		xml = this.resourceProvider.toXMLMetadata(xml);
		xml = this.storageHost.toXMLMetadata(xml);
		
		return root;
	}
	
	public static GridResources fromXMLMetadata(Element resourcesElem) {
		SubmitHost submitHost = SubmitHost.fromXMLMetadata(resourcesElem.element(SubmitHost.XML_METADATA_NAME));
		ResourceProvider resourceProvider = ResourceProvider.fromXMLMetadata(resourcesElem.element(ResourceProvider.XML_METADATA_NAME));
		StorageHost storageHost = StorageHost.fromXMLMetadata(resourcesElem.element(StorageHost.XML_METADATA_NAME));
		
		return new GridResources(submitHost, resourceProvider, storageHost);
	}
	
	@Override
	public String toString() {
		String str = "";
		
		str += "Grid Resources" + "\n";
		str += GridJob.indentString(this.submitHost.toString()) + "\n";
		str += GridJob.indentString(this.resourceProvider.toString()) + "\n";
		str += GridJob.indentString(this.storageHost.toString());
		
		return str;
	}
}
