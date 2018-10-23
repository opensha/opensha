/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.util.binFile;

public class BinaryMesh2DCalculator {
	
	public enum MeshOrder {
		FAST_XY,
		FAST_YX;
	}
	
	public enum DataType {
		SHORT(2),
		INT(4),
		LONG(8),
		FLOAT(4),
		DOUBLE(8);
		
		private int bytes;
		private DataType(int bytes) {
			this.bytes = bytes;
		}
		
		public int getNumBytes() {
			return bytes;
		}
	}
	
	protected long nx;
	protected long ny;
	private DataType numType;
	
	private long maxFilePos;
	
	private MeshOrder meshOrder = MeshOrder.FAST_XY;
	
	private int numBytesPerPoint;
	
	public BinaryMesh2DCalculator(DataType numType, long nx, long ny) {
		
		numBytesPerPoint = numType.getNumBytes();
		
		this.nx = nx;
		this.ny = ny;
		
		this.maxFilePos = calcMaxFilePos();
		
		this.numType = numType;
	}
	
	public long calcMeshIndex(long x, long y) {
		if (meshOrder == MeshOrder.FAST_XY) {
			return nx * y + x;
		} else { // FAST_YX
			return ny * x + y;
		}
	}
	
	public long calcFileX(long pos) {
		return calcMeshX(pos / numBytesPerPoint);
	}
	
	public long calcMeshX(long index) {
		if (meshOrder == MeshOrder.FAST_XY) {
			return index % nx;
		} else {
			return index / ny;
		}
	}
	
	public long calcFileY(long pos) {
		return calcMeshY(pos / numBytesPerPoint);
	}
	
	public long calcMeshY(long index) {
		if (meshOrder == MeshOrder.FAST_XY) {
			return index / nx;
		} else {
			return index % ny;
		}
	}
	
	public long calcFileIndex(long x, long y) {
		return numBytesPerPoint * this.calcMeshIndex(x, y);
	}
	
	public long getNX() {
		return nx;
	}

	public void setNX(int nx) {
		this.nx = nx;
		maxFilePos = calcMaxFilePos();
	}

	public long getNY() {
		return ny;
	}

	public void setNY(int ny) {
		this.ny = ny;
		maxFilePos = calcMaxFilePos();
	}

	public long getMaxFilePos() {
		return maxFilePos;
	}
	
	private long calcMaxFilePos() {
		return (nx*ny - 1) * numBytesPerPoint;
	}

	public MeshOrder getMeshOrder() {
		return meshOrder;
	}

	public void setMeshOrder(MeshOrder meshOrder) {
		this.meshOrder = meshOrder;
	}
	
	public DataType getType() {
		return numType;
	}

}
