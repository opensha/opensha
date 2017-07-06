package org.opensha.commons.data;

import java.util.ArrayList;

import javax.swing.JFrame;

import org.opensha.commons.gui.WeightedListGUI;

public class WeightedListGUILauncher {

	private static class TestClass implements Named {

		private String name;
		public TestClass(String name) {
			this.name = name;
		}
		
		@Override
		public String getName() {
			return name;
		}
		
	}
	
	public static void main(String[] args) {
		ArrayList<TestClass> objects = new ArrayList<TestClass>();
		ArrayList<Double> weights = new ArrayList<Double>();
		objects.add(new TestClass("Item 1"));
		weights.add(0.25);
		objects.add(new TestClass("Item 2"));
		weights.add(0.25);
		objects.add(new TestClass("Item 3"));
		weights.add(0.25);
		objects.add(new TestClass("Item 4"));
		weights.add(0.25);
		
		WeightedList<TestClass> list =
			new WeightedList<TestClass>(objects, weights);
		
		WeightedListGUI gui = new WeightedListGUI(list);
		
		JFrame frame = new JFrame();
		frame.setSize(400, 600);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setContentPane(gui);
		frame.setVisible(true);
	}

}
