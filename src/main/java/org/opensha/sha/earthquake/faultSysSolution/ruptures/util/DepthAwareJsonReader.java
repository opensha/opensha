package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.io.IOException;
import java.io.Reader;

import com.google.gson.stream.JsonReader;

public class DepthAwareJsonReader extends JsonReader {
	
	private int curDepth = 0;

	public DepthAwareJsonReader(Reader in) {
		super(in);
	}

	@Override
	public void beginArray() throws IOException {
		// TODO Auto-generated method stub
		super.beginArray();
	}

	@Override
	public void endArray() throws IOException {
		// TODO Auto-generated method stub
		super.endArray();
	}

	@Override
	public void beginObject() throws IOException {
		// TODO Auto-generated method stub
		super.beginObject();
	}

	@Override
	public void endObject() throws IOException {
		// TODO Auto-generated method stub
		super.endObject();
	}

}
