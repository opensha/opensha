package org.opensha.sha.calc.hazardMap.components;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.calc.hazardMap.BinaryRandomAccessHazardCurveWriter;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * This class stores hazard curves in an NSHMP (I think) binary format as such:
 * 
 * <br><code>[num x vals] [x1] [x2] ... [xN] [lat1] [lon1] [y1] [y2] ... [yN] [lat2]
 * [lon2] [y1] [y2] ... [yN]... etc</code><br>
 * Everything is an 8 byte double except for <num x vals> which is a 4 byte integer. 
 * @author kevin
 *
 */
public class BinaryCurveArchiver implements CurveResultsArchiver {
	
	public static final String XML_METADATA_NAME = "BinaryFileCurveArchiver";
	
	private File outputDir;
	
	private Map<String, BinaryRandomAccessHazardCurveWriter> filesMap;
	
	public static final ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
	
	/**
	 * @param outputDir directory where binary files will be stored
	 * @param numSites total number of sites
	 * @param xValsMap this is a mapping from IMR/IMT names to x values. These values must match the
	 * CurveMetadata.getShortLabel() values, and each curve with a given label will be written to
	 * outputDir/[label].bin. This is necessary for single calculations with multiple IMTs. If used
	 * in conjunction with the standard HazardCurveDriver then IMRs will be labeld "imr1", "imr2", etc...
	 */
	public BinaryCurveArchiver(File outputDir, int numSites, Map<String, DiscretizedFunc> xValsMap) {
		this.outputDir = outputDir;
		if (!outputDir.exists())
			outputDir.mkdir();
		this.filesMap = Maps.newHashMap();
		for (String imrName : xValsMap.keySet()) {
			
			File outputFile = new File(outputDir, imrName+".bin");
			DiscretizedFunc xVals = xValsMap.get(imrName);
			BinaryRandomAccessHazardCurveWriter file =
					new BinaryRandomAccessHazardCurveWriter(outputFile, numSites, xVals);
			filesMap.put(imrName, file);
		}
	}
	
	/**
	 * This method should be called by exactly one process. In a distributed environment it should be called
	 * only on the root node, before any curves have been calculated. It creates each binary file and initialized
	 * the X values, while filling in Y values for each curve with NaNs.
	 */
	public void initialize() {
		for (BinaryRandomAccessHazardCurveWriter file : filesMap.values()) {
			try {
				file.initialize();
			} catch (IOException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
	}

	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);
		
		el.addAttribute("outputDir", outputDir.getAbsolutePath());
		
		return root;
	}
	
	public static BinaryCurveArchiver fromXMLMetadata(Element archiverEl, int numSites,
			Map<String, DiscretizedFunc> xValsMap) throws IOException {
		String outputDir = archiverEl.attributeValue("outputDir");
		return new BinaryCurveArchiver(new File(outputDir), numSites, xValsMap);
	}

	@Override
	public synchronized void archiveCurve(DiscretizedFunc curve,
			CurveMetadata meta) throws IOException {
		String imrName = meta.getShortLabel();
		BinaryRandomAccessHazardCurveWriter file = filesMap.get(imrName);
		file.writeCurve(meta.getIndex(), meta.getSite().getLocation(), curve);
	}

	@Override
	public synchronized boolean isCurveCalculated(CurveMetadata meta,
			DiscretizedFunc xVals) {
		try {
			String imrName = meta.getShortLabel();
			return filesMap.get(imrName).isCurveCalculated(meta.getIndex(), meta.getSite().getLocation());
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}

	@Override
	public File getStoreDir() {
		return outputDir;
	}

	@Override
	public void close() {
		for (BinaryRandomAccessHazardCurveWriter file : filesMap.values())
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

}
