package org.opensha.refFaultParamDb.gui.view;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

public class SectionInfoCSVFileWriter extends AbstractSectionInfoFileWriter {
	
	public enum SaveType {
		CSV_ALL_POINTS("CSV File - All Fault Surface Points"),
		CSV_TOP_BOTTOM_POINTS("CSV File - Top/Bottom Fault Surface Points"),
		CSV_TRACE_DESC("CSV File - Fault Trace Only");
		
		private String desc;
		
		private SaveType(String desc) {
			this.desc = desc;
		}
		
		public String getDescription() {
			return desc;
		}
		
		public static SaveType forDesc(String desc) {
			for (SaveType type : SaveType.values()) {
				if (type.getDescription().equals(desc))
					return type;
			}
			throw new IllegalStateException("Unknown save type: "+desc);
		}
	}

	private static String[] colNames =
		{ "Fault Section ID", "Fault Name", "Longitude", "Latitude", "Depth" };
	private SaveType saveType;
	
	private static boolean aseisReducesArea = true;
	
	public SectionInfoCSVFileWriter(DB_AccessAPI dbConnection, SaveType saveType) {
		super(dbConnection);
		this.saveType = saveType;
	}

	@Override
	public String getFaultAsString(FaultSectionPrefData prefData) {
		String line = "";
		String id = ""+prefData.getSectionId();
		String name = prefData.getSectionName();
		if (saveType == SaveType.CSV_TRACE_DESC) {
			for (Location loc : prefData.getFaultTrace()) {
				line += getLine(loc, id, name) + "\n";
			}
		} else {
			StirlingGriddedSurface surf =
				new StirlingGriddedSurface(prefData.getSimpleFaultData(aseisReducesArea), 1.0);
			for (int row=0; row<surf.getNumRows(); row++) {
				if (saveType == SaveType.CSV_TOP_BOTTOM_POINTS && row != 0 && row != (surf.getNumRows()-1))
					continue;
				for (int col=0; col<surf.getNumCols(); col++) {
					Location loc = surf.getLocation(row, col);
					line += getLine(loc, id, name) + "\n";
				}
			}
		}
		return line;
	}
	
	private static String getLine(Location loc, String id, String name) {
		String lat = ""+loc.getLatitude();
		String lon = ""+loc.getLongitude();
		String depth = ""+loc.getDepth();
		String[] vals = { id, name, lon, lat, depth };
		return CSVFile.getLineStr(vals);
	}

	@Override
	public String getFileHeader() {
		return CSVFile.getLineStr(colNames);
	}

}
