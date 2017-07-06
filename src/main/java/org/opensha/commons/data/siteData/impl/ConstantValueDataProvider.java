package org.opensha.commons.data.siteData.impl;

import java.io.IOException;

import org.opensha.commons.data.siteData.AbstractSiteData;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;

/**
 * This can be used if you need a constant value in the site data provider framework
 * @author kevin
 *
 * @param <Element>
 */
public class ConstantValueDataProvider<Element> extends
		AbstractSiteData<Element> {
	
	private String dataType;
	private String dataMeasurmentType;
	private Element value;
	private String name;
	private String shortName;
	
	public ConstantValueDataProvider(String dataType, String dataMeasurmentType, Element value) {
		this(dataType, dataMeasurmentType, value, "Constant Value Provider", "ConstVal");
	}
	
	public ConstantValueDataProvider(String dataType, String dataMeasurmentType, Element value, String name, String shortName) {
		this.dataType = dataType;
		this.dataMeasurmentType = dataMeasurmentType;
		this.value = value;
		this.name = name;
		this.shortName = shortName;
	}

	@Override
	public Region getApplicableRegion() {
		return Region.getGlobalRegion();
	}

	@Override
	public double getResolution() {
		return 0;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getDataType() {
		return dataType;
	}

	@Override
	public String getDataMeasurementType() {
		return dataMeasurmentType;
	}

	@Override
	public Location getClosestDataLocation(Location loc) throws IOException {
		return loc;
	}

	@Override
	public Element getValue(Location loc) throws IOException {
		return value;
	}

	@Override
	public boolean isValueValid(Element el) {
		return value.equals(el);
	}

	@Override
	public String getMetadata() {
		return "Constant valued provider. Value: "+value;
	}

}
