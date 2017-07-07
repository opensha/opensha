package org.opensha.sha.earthquake.observedEarthquake.parsers.ngaWest;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.apache.poi.hssf.usermodel.HSSFRow;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.faultSurface.ApproxEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;

public class NGAWestEqkRupture extends ObsEqkRupture {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private int id;
	private String name;
	private GregorianCalendar date;
//	private double mag;
	private String magType;
	private double magUncertaintyKegan, magUncertaintyStatistical, magUncertaintyStudyClass;
	private int magSampleSize;
	private double mo;
	private FocalMechanism focalMech;
	private int mechBasedOnRakeAngle;
	private double pPlungeDegrees;
	private double pTrendDegrees;
	private double tPlungeDegrees;
	private double tTrendDegrees;
//	private Location hypocenter;
	
	private enum CoseismicSurfaceRupture {
		YES_KNOWN,
		YES_INFERRED,
		NO_KNOWN,
		NO_INFERRED,
		UNKNOWN;
	}
	private CoseismicSurfaceRupture coseismicSurfaceRupture;
	private String surfaceRuptureInferrenceBasis;
	private boolean finiteRuptureModel;
	private double depthToTopOfFiniteRupture;
	private double finiteFaultRuptureLength;
	private double finiteFaultRuptureWidth;
	
	private static GregorianCalendar parseDate(int year, String monthDayStr, String hourMinStr) {
		int month, day, hour, min;
		if (monthDayStr.length() == 3) {
			month = Integer.parseInt(monthDayStr.substring(0, 1));
			day = Integer.parseInt(monthDayStr.substring(1));
		} else {
			month = Integer.parseInt(monthDayStr.substring(0, 2));
			day = Integer.parseInt(monthDayStr.substring(2));
		}
		
		if (hourMinStr == null)
			hourMinStr = "";
		
		if (hourMinStr.length() <= 2) {
			hour = 0;
			if (hourMinStr.isEmpty())
				min = 0;
			else
				min = Integer.parseInt(hourMinStr);
		} else if (hourMinStr.length() == 3) {
			hour = Integer.parseInt(hourMinStr.substring(0, 1));
			min = Integer.parseInt(hourMinStr.substring(1));
		} else if (hourMinStr.length() == 4) {
			hour = Integer.parseInt(hourMinStr.substring(0, 2));
			min = Integer.parseInt(hourMinStr.substring(2));
		} else {
			throw new RuntimeException("invalid hour/min string: "+hourMinStr);
		}
		
		GregorianCalendar cal = new  GregorianCalendar(year, month-1, day, hour, min);
		cal.setTimeZone(TimeZone.getTimeZone("UTC"));
		return cal;
	}
	
	public NGAWestEqkRupture(HSSFRow row, String dataSource) {
		id = (int)row.getCell(0).getNumericCellValue();
		
		// parse the date
		name = row.getCell(1).getStringCellValue().trim();
		int year = (int)row.getCell(2).getNumericCellValue();
		String monthDayStr;
		try {
			monthDayStr = row.getCell(3).getStringCellValue();
		} catch (Exception e) {
			monthDayStr = (int)row.getCell(3).getNumericCellValue() + "";
		}
		String hourMinStr;
		try {
			hourMinStr = row.getCell(4).getStringCellValue();
		} catch (Exception e) {
			try {
				hourMinStr = (int)row.getCell(4).getNumericCellValue() + "";
			} catch (NullPointerException npe) {
				hourMinStr = "";
			}
		}
		date = parseDate(year, monthDayStr, hourMinStr);
		
		mag = row.getCell(5).getNumericCellValue();
		
		magType = row.getCell(6).getStringCellValue();
		
		try {
			magUncertaintyKegan = row.getCell(7).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			magUncertaintyStatistical = row.getCell(8).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			magUncertaintyStudyClass = row.getCell(10).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			magSampleSize = (int)row.getCell(9).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			mo = row.getCell(11).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			aveRake = row.getCell(14).getNumericCellValue();
			double strike = row.getCell(12).getNumericCellValue();
			double dip = row.getCell(13).getNumericCellValue();
			focalMech = new FocalMechanism(strike, dip, aveRake);
		} catch (NullPointerException e) {}
		
		try {
			mechBasedOnRakeAngle = (int)row.getCell(15).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			pPlungeDegrees = row.getCell(16).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			pTrendDegrees = row.getCell(17).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			tPlungeDegrees = row.getCell(18).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			tTrendDegrees = row.getCell(19).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			double lat = row.getCell(20).getNumericCellValue();
			double lon = row.getCell(21).getNumericCellValue();
			double dep;
			try {
				dep = row.getCell(22).getNumericCellValue();
			} catch (NullPointerException e) {
				dep = 0;
			}
			hypocenterLocation = new Location(lat, lon, dep);
		} catch (NullPointerException e) {}
		
		try {
			int csr = (int)row.getCell(23).getNumericCellValue();
			if (csr == 0)
				coseismicSurfaceRupture = CoseismicSurfaceRupture.NO_KNOWN;
			else
				coseismicSurfaceRupture = CoseismicSurfaceRupture.YES_KNOWN;
		} catch (NullPointerException e) {
			try {
				int csr = (int)row.getCell(24).getNumericCellValue();
				if (csr == 0)
					coseismicSurfaceRupture = CoseismicSurfaceRupture.NO_INFERRED;
				else
					coseismicSurfaceRupture = CoseismicSurfaceRupture.YES_INFERRED;
			} catch (NullPointerException e2) {
				coseismicSurfaceRupture = CoseismicSurfaceRupture.UNKNOWN;
			}
		};
		
		try {
			surfaceRuptureInferrenceBasis = row.getCell(25).getStringCellValue();
		} catch (NullPointerException e) {}
		
		try {
			finiteRuptureModel = (int)row.getCell(26).getNumericCellValue() == 1;
		} catch (NullPointerException e) {
			finiteRuptureModel = false;
		}
		
		try {
			depthToTopOfFiniteRupture = row.getCell(27).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			finiteFaultRuptureLength = row.getCell(28).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		try {
			finiteFaultRuptureWidth = row.getCell(29).getNumericCellValue();
		} catch (NullPointerException e) {}
		
		setEventId(id+"");
		setOriginTimeCal(date);
		setRuptureSurface(ruptureSurface);
	}
//
//	public List<AbstractEvenlyGriddedSurface> getFiniteRuptureSurfaces() {
//		return finiteRuptureSurfaces;
//	}
//
//	public void setFiniteRuptureSurfaces(List<AbstractEvenlyGriddedSurface> finiteRuptureSurfaces) {
//		this.finiteRuptureSurfaces = finiteRuptureSurfaces;
//		this.evenlyGriddedFiniteRuptureSurfaces = null;
//	}
//	
//	public List<AbstractEvenlyGriddedSurface> getEvenlyGriddedFiniteRuptureSurfaces() {
//		if (evenlyGriddedFiniteRuptureSurfaces == null && finiteRuptureSurfaces != null) {
//			evenlyGriddedFiniteRuptureSurfaces = new ArrayList<AbstractEvenlyGriddedSurface>();
//			for (AbstractEvenlyGriddedSurface surf : finiteRuptureSurfaces) {
//				FaultTrace top = new FaultTrace(name);
//				FaultTrace bottom = new FaultTrace(name);
//				int botRow = surf.getNumRows()-1;
//				for (int col=0; col<surf.getNumCols(); col++) {
//					top.add(surf.get(0, col));
//					bottom.add(surf.get(botRow, col));
//				}
//				AbstractEvenlyGriddedSurface evenSurface = new ApproxEvenlyGriddedSurface(top, bottom, 1.0d);
//				evenlyGriddedFiniteRuptureSurfaces.add(evenSurface);
//			}
//		}
//		return evenlyGriddedFiniteRuptureSurfaces;
//	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public GregorianCalendar getDate() {
		return date;
	}

	public double getMag() {
		return mag;
	}

	public String getMagType() {
		return magType;
	}

	public double getMagUncertaintyKegan() {
		return magUncertaintyKegan;
	}

	public double getMagUncertaintyStatistical() {
		return magUncertaintyStatistical;
	}

	public double getMagUncertaintyStudyClass() {
		return magUncertaintyStudyClass;
	}

	public int getMagSampleSize() {
		return magSampleSize;
	}

	public double getMo() {
		return mo;
	}

	public FocalMechanism getFocalMech() {
		return focalMech;
	}

	public int getMechBasedOnRakeAngle() {
		return mechBasedOnRakeAngle;
	}

	public double getpPlungeDegrees() {
		return pPlungeDegrees;
	}

	public double getpTrendDegrees() {
		return pTrendDegrees;
	}

	public double gettPlungeDegrees() {
		return tPlungeDegrees;
	}

	public double gettTrendDegrees() {
		return tTrendDegrees;
	}

	public CoseismicSurfaceRupture getCoseismicSurfaceRupture() {
		return coseismicSurfaceRupture;
	}

	public String getSurfaceRuptureInferrenceBasis() {
		return surfaceRuptureInferrenceBasis;
	}

	public boolean isFiniteRuptureModel() {
		return finiteRuptureModel;
	}

	public double getDepthToTopOfFiniteRupture() {
		return depthToTopOfFiniteRupture;
	}

	public double getFiniteFaultRuptureLength() {
		return finiteFaultRuptureLength;
	}

	public double getFiniteFaultRuptureWidth() {
		return finiteFaultRuptureWidth;
	}

	@Override
	public String toString() {
		return "NGAWestEqkRupture [id=" + id + "\tname=" + name + "\tdate="
				+ date + "\tmag=" + mag + "\tmagType=" + magType
				+ "\tmagUncertaintyKegan=" + magUncertaintyKegan
				+ "\tmagUncertaintyStatistical=" + magUncertaintyStatistical
				+ "\tmagUncertaintyStudyClass=" + magUncertaintyStudyClass
				+ "\tmagSampleSize=" + magSampleSize + "\tmo=" + mo
				+ "\tfocalMech=" + focalMech + "\tmechBasedOnRakeAngle="
				+ mechBasedOnRakeAngle + "\tpPlungeDegrees=" + pPlungeDegrees
				+ "\tpTrendDegrees=" + pTrendDegrees + "\ttPlungeDegrees="
				+ tPlungeDegrees + "\ttTrendDegrees=" + tTrendDegrees
				+ "\thypocenter=" + hypocenterLocation + "\tcoseismicSurfaceRupture="
				+ coseismicSurfaceRupture + "\tsurfaceRuptureInferrenceBasis="
				+ surfaceRuptureInferrenceBasis + "\tfiniteRuptureModel="
				+ finiteRuptureModel + "\tdepthToTopOfFiniteRupture="
				+ depthToTopOfFiniteRupture + "\tfiniteFaultRuptureLength="
				+ finiteFaultRuptureLength + "\tfiniteFaultRuptureWidth="
				+ finiteFaultRuptureWidth + "\tfiniteRuptureSurfaces="
				+ ruptureSurface + "]";
	}

}
