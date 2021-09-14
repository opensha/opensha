package org.opensha.commons.util.modules;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.Test;

import com.google.common.base.Preconditions;

public class ModuleContainerTest {
	
	/* 
	 * These methods test adding and retrieval logic
	 */

	@Test
	public void testAddRetrieve() {
		System.out.println("*** testAddRetrieve() ***");
		System.out.println("Testing simple adding and retrieval");
		ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
		
		assertEquals("Container should start empty", 0, container.getModules(false).size());
		
		Module_A_A_A aaaInst1 = new Module_A_A_A();
		assertFalse("Should not initially contain interface when empty", containsInstance(container, Module_A.class));
		container.addModule(aaaInst1);
		
		assertEquals("Container should now have 1 module", 1, container.getModules(false).size());
		assertTrue("Added module but it's not in the list", contains(container, aaaInst1));
		assertTrue("Cannot retrieve a module by its class", containsInstance(container, Module_A_A_A.class));
		assertTrue("Cannot retrieve a module by its abstract superclass", containsInstance(container, Module_A_A.class));
		assertTrue("Cannot retrieve a module by its superinterface", containsInstance(container, Module_A.class));
		
		Module_B_A baInst1 = new Module_B_A();
		assertFalse("Should not initially contain interface when empty", containsInstance(container, Module_B.class));
		container.addModule(baInst1);
		assertEquals("Container should now have 2 modules", 2, container.getModules(false).size());
		assertTrue("Added module but it's not in the list", contains(container, baInst1));
		assertTrue("Cannot retrieve a module by its class", containsInstance(container, Module_B_A.class));
		assertTrue("Cannot retrieve a module by its superclass", containsInstance(container, Module_B.class));
		System.out.println("*** END testAddRetrieve() ***");
	}
	
	@Test
	public void testReplaceWithSame() {
		System.out.println("*** testReplaceWithSame() ***");
		System.out.println("Testing replacing a module with another instance of the same class");
		ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
		
		assertEquals("Container should start empty", 0, container.getModules(false).size());
		
		Module_A_A_A aaaInst1 = new Module_A_A_A();
		assertFalse("Should not initially contain interface when empty", containsInstance(container, Module_A.class));
		container.addModule(aaaInst1);
		
		assertEquals("Container should now have 1 module", 1, container.getModules(false).size());
		assertTrue("Added module but it's not in the list", contains(container, aaaInst1));
		
		Module_A_A_A aaaInst2 = new Module_A_A_A();
		container.addModule(aaaInst2);
		assertEquals("Container should still have 1 module after replace", 1, container.getModules(false).size());
		assertTrue("Replaced module but new version is not in the list", contains(container, aaaInst2));
		assertFalse("Replaced module but old version is still in the list", contains(container, aaaInst1));
		assertTrue("Cannot retrieve replaced module by its class", containsInstance(container, Module_A_A_A.class));
		System.out.println("*** END testReplaceWithSame() ***");
	}
	
	@Test
	public void testReplaceWithSubclass() {
		System.out.println("*** testReplaceWithSubclass() ***");
		System.out.println("Testing replacing a module with a sub class instance of the original class");
		ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
		
		assertEquals("Container should start empty", 0, container.getModules(false).size());
		
		Module_B bInst = new Module_B();
		assertFalse("Should not initially contain interface when empty", containsInstance(container, Module_B.class));
		container.addModule(bInst);
		
		assertEquals("Container should now have 1 module", 1, container.getModules(false).size());
		assertTrue("Added module but it's not in the list", contains(container, bInst));
		
		Module_B_A baInst = new Module_B_A();
		assertFalse("Should not be able to retrieve subclass that was not added", containsInstance(container, Module_B_A.class));
		
		container.addModule(baInst);
		assertTrue("Added module but it's not in the list", contains(container, baInst));
		assertFalse("Replaced module but it is still in the list", contains(container, bInst));
		assertEquals("Container should still have 1 module", 1, container.getModules(false).size());
		System.out.println("*** END testReplaceWithSubclass() ***");
	}
	
	@Test
	public void testReplaceWithSuperclass() {
		System.out.println("*** testReplaceWithSuperclass() ***");
		System.out.println("Testing replacing a module with a super class instance of the original class");
		ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
		
		assertEquals("Container should start empty", 0, container.getModules(false).size());

		Module_B_A baInst = new Module_B_A();
		container.addModule(baInst);
		
		assertEquals("Container should now have 1 module", 1, container.getModules(false).size());
		assertTrue("Added module but it's not in the list", contains(container, baInst));

		Module_B bInst = new Module_B();
		
		container.addModule(bInst);
		assertTrue("Added module but it's not in the list", contains(container, bInst));
		assertFalse("Replaced module but it is still in the list", contains(container, baInst));
		assertFalse("Replaced module but other implementation is still in the list", containsInstance(container, Module_B_A.class));
		assertEquals("Container should still have 1 module", 1, container.getModules(false).size());
		System.out.println("*** END testReplaceWithSuperclass() ***");
	}
	
	@Test
	public void testReplaceWithAltImpl() {
		System.out.println("*** testReplaceWithAltImpl() ***");
		System.out.println("Testing replacing a module with an alternative implementation");
		ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
		
		assertEquals("Container should start empty", 0, container.getModules(false).size());

		Module_A_A_A aaaInst = new Module_A_A_A();
		container.addModule(aaaInst);
		
		assertEquals("Container should now have 1 module", 1, container.getModules(false).size());
		assertTrue("Added module but it's not in the list", contains(container, aaaInst));

		Module_A_B abInst = new Module_A_B();
		
		container.addModule(abInst);
		assertTrue("Added alt module but it's not in the list", contains(container, abInst));
		assertFalse("Replaced module but it is still in the list", contains(container, aaaInst));
		assertFalse("Replaced module but other implementation is still in the list", containsInstance(container, Module_A_A_A.class));
		assertFalse("Replaced module but other implementation is still in the list", containsInstance(container, Module_A_A.class));
		assertEquals("Container should still have 1 module", 1, container.getModules(false).size());
		System.out.println("*** END testReplaceWithAltImpl() ***");
	}
	
	@Test
	public void testHelperNotMapped() {
		System.out.println("*** testHelperNotMapped() ***");
		System.out.println("Testing that classes with ModuleHelper annotation are never mapped");
		ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
		
		Module_C cInst = new Module_C();
		container.addModule(cInst);
		
		assertTrue("Module should now have C", containsInstance(container, Module_C.class));
		assertFalse("Should not be able to retrieve with helper class", container.hasModule(Helper.class));

		System.out.println("*** END testHelperNotMapped() ***");
	}
	
	@Test
	public void testAddMultipleSameHelper() {
		System.out.println("*** testAddMultipleSameHelper() ***");
		System.out.println("Testing that we can add multiple modules that implement the same helper class");
		ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
		
		Module_C cInst = new Module_C();
		container.addModule(cInst);
		
		assertTrue("Module should now have C", contains(container, cInst));
		assertTrue("Module should now have C", containsInstance(container, Module_C.class));
		
		Module_D dInst = new Module_D();
		container.addModule(dInst);
		
		assertTrue("Module should now have D", contains(container, dInst));
		assertTrue("Module should now have D", containsInstance(container, Module_D.class));
		
		assertTrue("Module should still have C", contains(container, cInst));
		assertTrue("Module should still have C", containsInstance(container, Module_C.class));

		System.out.println("*** END testAddMultipleSameHelper() ***");
	}
	
	/**
	 * Checks if this exact instance exists in the module, checked with '=='
	 * 
	 * @param container
	 * @param module
	 * @return
	 */
	private static boolean contains(ModuleContainer<? extends OpenSHA_Module> container, OpenSHA_Module module) {
		for (OpenSHA_Module module2 : container.getModules(false))
			if (module == module2)
				return true;
		return false;
	}
	
	/**
	 * Checks if this a module exists in the container that is assignable from the given class
	 * 
	 * @param container
	 * @param module
	 * @return
	 */
	private static <E extends OpenSHA_Module> boolean containsInstance(ModuleContainer<E> container,
			Class<? extends E> clazz) {
		boolean ret = false;
		for (OpenSHA_Module module : container.getModules(false))
			if (clazz.isAssignableFrom(module.getClass()))
				ret = true;
		assertEquals("container.hasModule(Class) does not match manual check", ret, container.hasModule(clazz));
		return ret;
	}
	
	/*
	 * Module A hierarchy:
	 * 
	 * interface A
	 * 	- Abstract class A_A
	 *    - class A_A_A
	 *  - class A_B
	 * 
	 */
	
	private static interface Module_A extends OpenSHA_Module {
		
		public default String getName() {
			return "Module A";
		}
	}
	
	private static abstract class Module_A_A implements Module_A {}
	
	private static class Module_A_B implements Module_A {}
	
	private static class Module_A_A_A extends Module_A_A {}
	
	
	/*
	 * Module B hierarchy:
	 * 
	 * class B
	 *  - Subclass B_A
	 * 
	 */
	
	private static class Module_B implements OpenSHA_Module {
		public String getName() {
			return "Module B";
		}
	}
	
	private static class Module_B_A extends Module_B {}
	
	@ModuleHelper
	private static interface Helper extends OpenSHA_Module {}
	
	/*
	 * Module C hierarchy:
	 * 
	 * class C implements Helper
	 */
	
	private static class Module_C implements Helper {
		public String getName() {
			return "Module C";
		}
	}
	
	/*
	 * Module D hierarchy:
	 * 
	 * class D implements Helper
	 */
	
	private static class Module_D implements Helper {
		public String getName() {
			return "Module D";
		}
	}
	
	/* 
	 * These methods test available module logic
	 */

	@Test
	public void testAddRetrieveAvailable() {
		System.out.println("*** testAddRetrieveAvailable() ***");
		System.out.println("Testing adding and then loading/retrieving an available module");
		
		List<Class<? extends Module_A>> retrievals = new ArrayList<>();
		retrievals.add(Module_A_A_A.class);
		retrievals.add(Module_A_A.class);
		retrievals.add(Module_A.class);
		
		for (Class<? extends Module_A> retrievalClass : retrievals) {
			ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
			
			assertEquals("Container should start empty", 0, container.getModules(false).size());
			
			Module_A_A_A aaaInst1 = new Module_A_A_A();
			container.addAvailableModule(new Loader<>(aaaInst1), Module_A_A_A.class);
			
			assertEquals("Container should still be empty after adding avaialable", 0, container.getModules(false).size());
			assertEquals("Container should now have 1 available module", 1, container.getAvailableModules().size());
			
			Module_A retrieved = container.getModule(retrievalClass);
			assertNotNull("Didn't sucessfully load available module", retrieved);
			assertTrue(aaaInst1 == retrieved);
			
			assertEquals("Container should now have 1 loaded module", 1, container.getModules(false).size());
			assertEquals("Container should now have no available modules", 0, container.getAvailableModules().size());
		}
		
		System.out.println("*** END testAddRetrieveAvailable() ***");
	}

	@Test
	public void testHasAvailable() {
		System.out.println("*** testHasAvailable() ***");
		System.out.println("Testing has*Module() with available modules");
		
		ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
		
		assertEquals("Container should start empty", 0, container.getModules(false).size());
		
		Module_A_A_A aaaInst1 = new Module_A_A_A();
		container.addAvailableModule(new Loader<>(aaaInst1), Module_A_A_A.class);
		
		assertEquals("Container should still be empty after adding avaialable", 0, container.getModules(false).size());
		assertEquals("Container should now have 1 available module", 1, container.getAvailableModules().size());
		
		assertTrue("hasAvailableModule() should return true", container.hasAvailableModule(aaaInst1.getClass()));
		
		assertEquals("hasAvailableModule() should not load the available module", 0, container.getModules(false).size());
		assertEquals("hasAvailableModule() should not load the available module", 1, container.getAvailableModules().size());
		
		assertTrue("hasModule() should return true", container.hasModule(aaaInst1.getClass()));
		
		assertEquals("hasModule() should load the available module", 1, container.getModules(false).size());
		assertEquals("hasModule() should load the available module", 0, container.getAvailableModules().size());
		
		assertTrue("hasAvailableModule() should return true even after loaded", container.hasAvailableModule(aaaInst1.getClass()));
		
		System.out.println("*** END testHasAvailable() ***");
	}

	@Test
	public void testManuallyLoadAvailable() {
		System.out.println("*** testManuallyLoadAvailable() ***");
		System.out.println("Testing that manually loading a module clears out any available that match");
		ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
		
		assertEquals("Container should start empty", 0, container.getModules(false).size());
		
		Module_A_A_A aaaInst1 = new Module_A_A_A();
		container.addAvailableModule(new Loader<>(aaaInst1), Module_A_A_A.class);
		
		assertEquals("Container should still be empty after adding avaialable", 0, container.getModules(false).size());
		assertEquals("Container should now have 1 available module", 1, container.getAvailableModules().size());
		
		container.addModule(aaaInst1);
		
		assertEquals("Container should now have 1 loaded module", 1, container.getModules(false).size());
		assertEquals("Container should now have no available modules", 0, container.getAvailableModules().size());
		
		// now do it again but load in an alternate implementation
		container.clearModules();
		
		container.addAvailableModule(new Loader<>(aaaInst1), Module_A_A_A.class);
		
		assertEquals("Container should still be empty after adding avaialable", 0, container.getModules(false).size());
		assertEquals("Container should now have 1 available module", 1, container.getAvailableModules().size());
		
		Module_A_B abInst = new Module_A_B();
		container.addModule(abInst);
		
		assertEquals("Container should now have 1 loaded module", 1, container.getModules(false).size());
		assertEquals("Container should now have no available modules", 0, container.getAvailableModules().size());
		assertTrue("Should have loaded in the alternate", contains(container, abInst));
		
		System.out.println("*** END testManuallyLoadAvailable() ***");
	}
	
	private static class Loader<E extends OpenSHA_Module> implements Callable<E> {
		
		private E instance;
		private boolean loaded = false;

		public Loader(E instance) {
			this.instance = instance;
		}

		@Override
		public E call() throws Exception {
			System.out.println("Loading module "+instance.getName());
			Preconditions.checkState(loaded == false, "Loaded multiple times?");
			loaded = true;
			return instance;
		}
		
	}
	
	/* 
	 * These methods test sub-module logic
	 */

	@Test
	public void testLoadSubModule() {
		System.out.println("*** testLoadSubModule() ***");
		System.out.println("Testing that loading a sub module sets the parent");
		ModuleContainer<OpenSHA_Module> container = new ModuleContainer<>();
		
		Module_E eInst1 = new Module_E();
		container.addModule(eInst1);
		
		assertEquals("Container should now have 1 loaded module", 1, container.getModules(false).size());
		assertTrue("Added module but it's not in the list", contains(container, eInst1));
		assertTrue("Cannot retrieve a module by its class", containsInstance(container, Module_E.class));
		
		assertNotNull("Parent wasn't set when sub-module was added", eInst1.parent);
		assertTrue("Parent wasn't set correctly when sub-module was added", eInst1.parent == container);
		
		// add again and make sure that it didn't call copy
		container.addModule(eInst1);
		
		assertEquals("Container should now have 1 loaded module", 1, container.getModules(false).size());
		assertTrue("Added module but it's not in the list", contains(container, eInst1));
		assertTrue("Cannot retrieve a module by its class", containsInstance(container, Module_E.class));
		
		// now load it into a new container, make sure it was copied
		ModuleContainer<OpenSHA_Module> container2 = new ModuleContainer<>();
		container2.addModule(eInst1);
		
		assertEquals("Container should now have 1 loaded module", 1, container2.getModules(false).size());
		assertTrue("Cannot retrieve a module by its class", containsInstance(container2, Module_E.class));
		Module_E eInst2 = container2.getModule(Module_E.class);
		assertTrue("Sub-module wasn't copied when added to a new container", eInst1 != eInst2);
		assertTrue("Added module but it's not in the list", contains(container2, eInst2));
		
		assertNotNull("Parent wasn't set when sub-module was added", eInst2.parent);
		assertTrue("Parent wasn't set correctly when sub-module was added", eInst2.parent == container2);
		
		System.out.println("*** END testLoadSubModule() ***");
	}
	
	private static class Module_E implements SubModule<ModuleContainer<?>> {
		
		private ModuleContainer<?> parent;
		
		public String getName() {
			return "Module E";
		}

		@Override
		public void setParent(ModuleContainer<?> parent) throws IllegalStateException {
			System.out.println(getName()+": setParent called");
			this.parent = parent;
		}

		@Override
		public ModuleContainer<?> getParent() {
			System.out.println(getName()+": getParent() called");
			return this.parent; 
		}

		@Override
		public SubModule<ModuleContainer<?>> copy(ModuleContainer<?> newParent) throws IllegalStateException {
			System.out.println(getName()+": copy() called");
			Module_E copy = new Module_E();
			copy.parent = newParent;
			return copy;
		}
	}
	
	/* 
	 * This tests that we can't create an infinite module loop
	 */

	@Test
	public void testAddModuleToItself() {
		System.out.println("*** testAddModuleToItself() ***");
		System.out.println("Testing ...");
		
		Module_F f = new Module_F();
		try {
			f.addModule(f);
			fail("Should have thrown an IllegalStateException");
		} catch (IllegalStateException e) {
			System.out.println("Caught expected IllegalStateException: "+e.getMessage());
		}

		System.out.println("*** END testAddModuleToItself() ***");
	}
	
	private static class Module_F extends ModuleContainer<OpenSHA_Module> implements OpenSHA_Module {
		
		private ModuleContainer<?> parent;
		
		public String getName() {
			return "Module E";
		}
	}

}
