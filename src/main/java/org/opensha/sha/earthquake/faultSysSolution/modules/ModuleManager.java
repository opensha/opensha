package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ClassUtils;
import org.opensha.commons.data.Named;

import com.google.common.base.Preconditions;

/**
 * This class handles modules and mapping them to any assignable super classes.
 * 
 * @author kevin
 *
 * @param <E>
 */
public class ModuleManager<E extends Named> {
	
	private Class<E> baseClass;
	
	private List<E> modules;
	private Map<Class<? extends E>, E> mappings;

	public ModuleManager(Class<E> baseClass) {
		this.baseClass = baseClass;
		
		modules = new ArrayList<>();
		mappings = new HashMap<>();
	}
	
	/**
	 * Adds the given module and maps it as the value to any eligible super-classes
	 * 
	 * @param module
	 */
	public void addModule(E module) {
		for (int m=modules.size(); --m>=0;) {
			E oModule = modules.get(m);
			if (oModule.getClass().equals(module.getClass())) {
				System.out.println("Overriding previous modlue: "+oModule.getName());
				modules.remove(m);
				List<Class<? extends E>> oldMappings = new ArrayList<>();
				for (Class<? extends E> clazz : mappings.keySet())
					if (mappings.get(clazz).equals(oModule))
						oldMappings.add(clazz);
				for (Class<? extends E> oldMapping : oldMappings)
					mappings.remove(oldMapping);
			}
		}
		modules.add(module);
		mapModule(module, module.getClass());
		
		// add any super-classes
		for (Class<?> clazz : ClassUtils.getAllSuperclasses(module.getClass())) {
			if (baseClass.isAssignableFrom(clazz) && !clazz.equals(baseClass)) {
				// this is a super-interface
				mapModule(module, clazz);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void mapModule(E module, Class<?> clazz) {
		if (mappings.containsKey(clazz))
			System.out.println("Warning: overriding module "+clazz.getName()+" with "+module.getName());
		else
			System.out.println("Mapping "+clazz.getName()+" to "+module.getName());
		Preconditions.checkState(baseClass.isAssignableFrom(clazz));
		mappings.put((Class<E>)clazz, module);
	}
	
	/**
	 * 
	 * @param clazz
	 * @return true if a module exists for the given class
	 */
	public boolean hasModule(Class<? extends E> clazz) {
		return mappings.containsKey(clazz);
	}
	
	/**
	 * Retrieve a module matching the given type
	 * 
	 * @param <M>
	 * @param clazz type of module to get
	 * @return module matching that type, or null if no matches exist
	 */
	@SuppressWarnings("unchecked")
	public <M extends E> M getModule(Class<M> clazz) throws IllegalStateException {
		E module = mappings.get(clazz);
		return (M)module;
	}
	
	/**
	 * 
	 * @return unmodifiable view of the current modules
	 */
	public List<E> getModules() {
		return Collections.unmodifiableList(modules);
	}

}
