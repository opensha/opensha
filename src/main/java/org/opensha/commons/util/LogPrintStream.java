package org.opensha.commons.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class LogPrintStream extends PrintStream {
	
	private FileWriter fw;
	
	public LogPrintStream(OutputStream stream, FileWriter fw) {
		super(stream);
		this.fw = fw;
	}
	
	public void write(int i) {
		super.write(i);
		try {
			fw.write(i);
		} catch (IOException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
    }

    public void write(byte[] bytes, int i, int j) {
    	super.write(bytes,i,j);
    	try {
			fw.write(new String(bytes,i,j));
		} catch (IOException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
    }

    public void write(byte[] bytes) throws IOException {
        super.write(bytes);
        try {
			fw.write(new String(bytes));
		} catch (IOException e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
    }
}
