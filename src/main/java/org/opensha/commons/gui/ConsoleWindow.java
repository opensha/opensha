package org.opensha.commons.gui;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class ConsoleWindow {
	
	private JDialog frame;
	private JTextArea text = new JTextArea();
	private JScrollPane scroll = new JScrollPane(text);
	
	public ConsoleWindow() {
		this(false);
	}
	
	public ConsoleWindow(boolean noFrame) {
		System.setErr(new ConsoleStream(System.err));
		System.setOut(new ConsoleStream(System.out));
		initGUI(noFrame);
	}
	
	public void initGUI(boolean noFrame) {
		if (!noFrame) {
			frame = new JDialog(new JFrame(), "Console Window");
			frame.setLocationRelativeTo(null);
			frame.setSize(800,500);
			frame.add(scroll);
		}
		text.setEditable(false);
	}
	
	public JScrollPane getScrollPane() {
		return scroll;
	}
	
	public JTextArea getTextArea() {
		return text;
	}
	
	public void setVisible(boolean show) {
		if (frame != null) {
			frame.setLocationRelativeTo(null);
			text.setCaretPosition(0);
			text.setCaretPosition(text.getText().length());
			frame.setVisible(show);
		}
	}
	
	private class ConsoleStream extends PrintStream {
		
		public ConsoleStream(OutputStream stream) {
			super(stream);
		}
		
		private void write(String s) {
			// I hope text is synchronized
			text.append(s);
			text.setCaretPosition(text.getText().length());
		}
		
		public void write(int i) {
			write(new String(new byte[]{(byte)i}));
			super.write(i);
	    }

	    public void write(byte[] bytes, int i, int j) {
	    	write(new String(bytes,i,j));
	    	super.write(bytes,i,j);
	    }

	    public void write(byte[] bytes) throws IOException {
	    	write(new String(bytes));
	        super.write(bytes);
	    }
	}
}
