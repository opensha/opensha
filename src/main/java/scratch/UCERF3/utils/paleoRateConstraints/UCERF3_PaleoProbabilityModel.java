package scratch.UCERF3.utils.paleoRateConstraints;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.utils.UCERF3_DataUtils;

import com.google.common.base.Preconditions;

public class UCERF3_PaleoProbabilityModel extends PaleoProbabilityModel {
	
	private EvenlyDiscrXYZ_DataSet xyz;
	private ArbitrarilyDiscretizedFunc dispMagFunc;
	
	private UCERF3_PaleoProbabilityModel(EvenlyDiscrXYZ_DataSet xyz, ArbitrarilyDiscretizedFunc dispMagFunc) {
		this.xyz = xyz;
		this.dispMagFunc = dispMagFunc;
	}
	
	@Override
	public double getProbPaleoVisible(FaultSystemRupSet rupSet, int rupIndex, int sectIndex) {
		return getProbPaleoVisible(rupSet.getMagForRup(rupIndex), rupSet.getFaultSectionDataForRupture(rupIndex), sectIndex);
	}
	
	@Override
	public double getProbPaleoVisible(double mag, List<FaultSectionPrefData> rupSects, int sectIndex) {
		return getProbPaleoVisible(mag, getDistAlongRup(rupSects, sectIndex));
	}

	@Override
	public double getProbPaleoVisible(double mag, double distAlongRup) {
		double maxX = xyz.getMaxX();
		if ((float)distAlongRup == (float)maxX)
			distAlongRup = maxX;
		Preconditions.checkArgument(distAlongRup >= xyz.getMinX() && distAlongRup <= maxX,
				"distance along rup must be between "+xyz.getMinX()+" and "+maxX+" (you supplied: "+distAlongRup+")");
		Preconditions.checkArgument(!Double.isNaN(mag), "magnitude cannot be NaN!");
		if (mag < xyz.getMinY())
			return 0;
		if (mag > xyz.getMaxY())
			return 1;
		return xyz.bilinearInterpolation(distAlongRup, mag);
	}
	
	public double getProbPaleoVisibleForSlip(double slip, double distAlongRup) {
		Preconditions.checkArgument(!Double.isNaN(slip), "slip cannot be NaN!");
		if (slip < dispMagFunc.getMinX())
			return 0;
		if (slip > dispMagFunc.getMaxX())
			return 1;
		return getProbPaleoVisible(dispMagFunc.getInterpolatedY(slip), distAlongRup);
	}
	
	public static UCERF3_PaleoProbabilityModel load() throws IOException {
		return fromURL(UCERF3_DataUtils.locateResource("paleoRateData", "pdetection2.txt"));
	}
	
	public static UCERF3_PaleoProbabilityModel fromFile(File file) throws IOException {
		try {
			return fromURL(file.toURI().toURL());
		} catch (MalformedURLException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public static UCERF3_PaleoProbabilityModel fromURL(URL url) throws IOException {
		double[] xVals = null;
		ArrayList<Double> yVals = new ArrayList<Double>();
		ArrayList<double[]> vals = new ArrayList<double[]>();
		ArbitrarilyDiscretizedFunc dispMagFunc = new ArbitrarilyDiscretizedFunc();
		int numVals = -1;
		for (String line : FileUtils.loadFile(url)) {
			StringTokenizer tok = new StringTokenizer(line.trim());
			if (numVals < 0)
				// minus 2 because we don't want the first two columns here
				numVals = tok.countTokens()-2;
			
			double[] lineVals = new double[numVals];
			double mag = Double.parseDouble(tok.nextToken());
			double disp = Double.parseDouble(tok.nextToken());
			for (int i=0; i<numVals; i++)
				lineVals[i] = Double.parseDouble(tok.nextToken());
			
			if (xVals == null) {
				// first line
				xVals = lineVals;
			} else {
				// regular line
				yVals.add(mag);
				vals.add(lineVals);
				dispMagFunc.set(disp, mag);
			}
		}
		double minX = StatUtils.min(xVals);
		EvenlyDiscrXYZ_DataSet xyz = new EvenlyDiscrXYZ_DataSet(
				xVals.length, yVals.size(), minX, Collections.min(yVals), Math.abs(xVals[1]-xVals[0]));
		
		for (int yInd=0; yInd<yVals.size(); yInd++)
			for (int xInd=0; xInd<xVals.length; xInd++)
				xyz.set(xVals[xInd], yVals.get(yInd), vals.get(yInd)[xInd]);
		
		for (int i=0; i<xyz.size(); i++)
			Preconditions.checkState(xyz.get(i) >= 0, "something didn't get set right!");
		Preconditions.checkState((float)xyz.getMaxX() == (float)StatUtils.max(xVals),
				"maxX is incorrect! "+(float)xyz.getMaxX()+" != "+(float)StatUtils.max(xVals));
		Preconditions.checkState((float)xyz.getMaxY() == Collections.max(yVals).floatValue(),
				"maxY is incorrect! "+(float)xyz.getMaxY()+" != "+Collections.max(yVals).floatValue());
		
		return new UCERF3_PaleoProbabilityModel(xyz, dispMagFunc);
	}
	
	public void writeTableData() {
		for(double mag=5d; mag <=8.05; mag+=0.5) {
			double aveSlip = dispMagFunc.getFirstInterpolatedX(mag);
			double p05=getProbPaleoVisible(mag,0.05);
			double p25=getProbPaleoVisible(mag,0.25);
			double p50=getProbPaleoVisible(mag,0.4999);
			System.out.println((float)aveSlip+"\t"+mag+"\t"+(float)p05+"\t"+(float)p25+"\t"+(float)p50);
		}
	}
	
	public static void main(String[] args) throws IOException {
		UCERF3_PaleoProbabilityModel model = load();
		model.writeTableData();
//		for (int yInd=0; yInd<model.xyz.getNumY(); yInd++) {
//			String line = null;
//			for (int xInd=0; xInd<model.xyz.getNumX(); xInd++) {
//				if (line == null)
//					line = "";
//				else
//					line += "\t";
//				line += model.xyz.get(xInd, yInd);
//			}
//			System.out.println(line);
//		}
	}

}
