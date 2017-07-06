package org.opensha.commons.data.function;

import java.awt.geom.Point2D;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.google.common.collect.Lists;

public abstract class AbstractXY_DataSet implements XY_DataSet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * Information about this function, will be used in making the legend from
	 * a parameter list of variables
	 */
	protected String info = "";

	/**
	 * Name of the function, useful for differentiation different instances
	 * of a function, such as in an array of functions.
	 */
	protected String name = "";
	
	@Override
	public String getName(){ return name; }
	
	@Override
	public void setName(String name){ this.name = name; }

	@Override
	public String getInfo(){ return info; }

	@Override
	public void setInfo(String info){ this.info = info; }
	
	//X and Y Axis name
	protected String xAxisName,yAxisName;
	
	@Override
	public void setXAxisName(String xName){
		xAxisName = xName;
	}

	@Override
	public String getXAxisName(){
		return xAxisName;
	}

	@Override
	public void setYAxisName(String yName){
		yAxisName = yName;
	}

	@Override
	public String getYAxisName(){
		return yAxisName;
	}
	
	@Override
	public double getClosestXtoY(double y) {
		double x = Double.NaN;
		double dist = Double.POSITIVE_INFINITY;
		for (int i=0; i<size(); i++) {
			double newY = getY(i);
			double newDist = Math.abs(newY - y);
			if (newDist < dist) {
				dist = newDist;
				x = getX(i);
			}
		}
		return x;
	}

	@Override
	public double getClosestYtoX(double x) {
		double y = Double.NaN;
		double dist = Double.POSITIVE_INFINITY;
		for (int i=0; i<size(); i++) {
			double newX = getX(i);
			double newDist = Math.abs(newX - x);
			if (newDist < dist) {
				dist = newDist;
				y = getY(i);
			}
		}
		return y;
	}
	
	@Override
	public boolean areAllXValuesInteger(double tolerance) {
		int num = size();
		double x, diff;
		for (int i = 0; i < num; ++i) {
			x = getX(i);
			diff = Math.abs(x - Math.rint(x));
			if (diff > tolerance) return false;
		}
		return true;
	}
	
	@Override
	public Iterator<Double> getXValuesIterator(){
		return new Iterator<Double>() {
			
			int index = 0;

			@Override
			public boolean hasNext() {
				return index < size();
			}

			@Override
			public Double next() {
				return getX(index++);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public Iterator<Double> getYValuesIterator(){
		return new Iterator<Double>() {
			
			int index = 0;

			@Override
			public boolean hasNext() {
				return index < size();
			}

			@Override
			public Double next() {
				return getY(index++);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	@Override
	public Iterator<Point2D> iterator() {
		return new Iterator<Point2D>() {
			
			int index = 0;

			@Override
			public boolean hasNext() {
				return index < size();
			}

			@Override
			public Point2D next() {
				return get(index++);
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	@Override
	public List<Double> xValues() {
		return new AbstractList<Double>() {
			@Override public Double get(int index) {
				return getX(index);
			}
			@Override public int size() {
				return AbstractXY_DataSet.this.size();
			}
			@Override public Iterator<Double> iterator() {
				final Iterator<Point2D> it = AbstractXY_DataSet.this.iterator();
				return new Iterator<Double>() {
					@Override public boolean hasNext() {
						return it.hasNext();
					}
					@Override public Double next() {
						return it.next().getX();
					}
					@Override public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	@Override
	public List<Double> yValues() {
		// doublecheck AbstractList docs and check/test immutability of list
		return new AbstractList<Double>() {
			@Override
			public Double get(int index) {
				return getY(index);
			}
			@Override
			public int size() {
				return AbstractXY_DataSet.this.size();
			}
			@Override public Iterator<Double> iterator() {
				final Iterator<Point2D> it = AbstractXY_DataSet.this.iterator();
				return new Iterator<Double>() {
					@Override public boolean hasNext() {
						return it.hasNext();
					}
					@Override public Double next() {
						return it.next().getY();
					}
					@Override public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	@Override
	public XY_DataSetList getDatasetsToPlot() {
		XY_DataSetList list = new XY_DataSetList();
		list.add(this);
		return list;
	}
	
	@Override
	public List<Integer> getPlotNumColorList() {
		return Lists.newArrayList(1);
	}


}
