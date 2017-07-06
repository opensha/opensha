package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.util.List;

import com.google.common.base.Preconditions;

/**
 * Utility class for splitting a set of Point2D data into function bins by some scalar. Useful
 * for coloring many points by a CPT or scaling point size by magnitude without having a separate
 * dataset/plot charactersitc for each point.
 * @author kevin
 *
 */
public class XY_DatasetBinner {
	
	public static XY_DataSet[] bin(List<Point2D> points, List<Double> scalars,
			double min, int num, double delta) {
		return bin(points, scalars, new EvenlyDiscretizedFunc(min, num, delta));
	}
	
	public static XY_DataSet[] bin(List<Point2D> points, List<Double> scalars, EvenlyDiscretizedFunc binFunc) {
		Preconditions.checkArgument(points.size() == scalars.size());
		
		XY_DataSet[] ret = new XY_DataSet[binFunc.size()];
		
		for (int i=0; i<ret.length; i++)
			ret[i] = new DefaultXY_DataSet();
		
		for (int i=0; i<points.size(); i++) {
			double scalar = scalars.get(i);
			int index = binFunc.getClosestXIndex(scalar);
			ret[index].set(points.get(i));
		}
		
		return ret;
	}
	
	public static XY_DataSet[][] bin2D(List<Point2D> points, List<Double> scalars1, List<Double> scalars2,
			EvenlyDiscretizedFunc binFunc1, EvenlyDiscretizedFunc binFunc2) {
		Preconditions.checkArgument(points.size() == scalars1.size());
		Preconditions.checkArgument(points.size() == scalars2.size());
		
		XY_DataSet[][] ret = new XY_DataSet[binFunc1.size()][binFunc2.size()];
		
		for (int i=0; i<ret.length; i++)
			for (int j=0; j<ret[i].length; j++)
				ret[i][j] = new DefaultXY_DataSet();
		
		for (int i=0; i<points.size(); i++) {
			double scalar1 = scalars1.get(i);
			double scalar2 = scalars2.get(i);
			int index1 = binFunc1.getClosestXIndex(scalar1);
			int index2 = binFunc2.getClosestXIndex(scalar2);
			ret[index1][index2].set(points.get(i));
		}
		
		return ret;
	}
	
}
