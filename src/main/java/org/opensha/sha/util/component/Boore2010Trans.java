package org.opensha.sha.util.component;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.imr.param.OtherParams.Component;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class Boore2010Trans extends ComponentTranslation {
	
	public static List<Boore2010Trans> getAllConverters() {
		List<Boore2010Trans> convs = Lists.newArrayList();
		
		// many more available, see paper and table
		convs.add(new Boore2010Trans(Component.GMRotI50, Component.RotD50, 11));
		convs.add(new Boore2010Trans(Component.GMRotI50, Component.RotD100, 56));
		
		return convs;
	}
	
	private Component from;
	private Component to;
	private int column;
	
	private DiscretizedFunc convFunc;
	
	private static final String FILE_NAME = "boore_2010_conversions.xls";
	private static HSSFSheet sheet;
	
	private Boore2010Trans(Component from, Component to, int column) {
		this.from = from;
		this.to = to;
		this.column = column;
	}
	
	private void loadSheet() {
		// load sheet if necessary
		synchronized (ComponentTranslation.class) {
			if (sheet == null) {
				try {
					POIFSFileSystem fs = new POIFSFileSystem(
							ComponentTranslation.class.getResource(FILE_NAME).openStream());
					HSSFWorkbook wb = new HSSFWorkbook(fs);
					sheet = wb.getSheetAt(0);
				} catch (IOException e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
		}
		Preconditions.checkNotNull(sheet);
	}
	
	private synchronized DiscretizedFunc getConversionFunc() {
		if (convFunc == null) {
			loadSheet();
			convFunc = new ArbitrarilyDiscretizedFunc();
			
			int lastRowIndex = sheet.getLastRowNum();
			for (int rowIndex=1; rowIndex<=lastRowIndex; rowIndex++) {
				HSSFRow row = sheet.getRow(rowIndex);
				double period = row.getCell(0).getNumericCellValue();
				double factor = row.getCell(column).getNumericCellValue();
				convFunc.set(period, factor);
			}
		}
		return convFunc;
	}

	@Override
	public Component getFromComponent() {
		return from;
	}

	@Override
	public Component getToComponent() {
		return to;
	}

	@Override
	public double getScalingFactor(double period)
			throws IllegalArgumentException {
		assertValidPeriod(period);
		return getConversionFunc().getInterpolatedY(period);
	}

	@Override
	public double getMinPeriod() {
		return getConversionFunc().getMinX();
	}

	@Override
	public double getMaxPeriod() {
		return getConversionFunc().getMaxX();
	}
	
	public static void main(String[] args) {
		List<DiscretizedFunc> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		
		Color[] colors = {Color.BLACK, Color.BLUE, Color.RED, Color.GREEN};
		
		List<Boore2010Trans> convs = getAllConverters();
		for (int i=0; i<convs.size(); i++) {
			Boore2010Trans trans = convs.get(i);
			funcs.add(trans.getConversionFunc());
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, PlotSymbol.CIRCLE, 4f, colors[i]));
		}
		PlotSpec spec = new PlotSpec(funcs, chars, "Conversion Factors", "Period", "Ratio");
		GraphWindow gw = new GraphWindow(spec);
		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
		gw.setXLog(true);
	}

	@Override
	public String getName() {
		return "Boore 2010";
	}

}
