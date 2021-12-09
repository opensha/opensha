package org.opensha.commons.util.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.ClassUtils;
import org.opensha.commons.data.Named;

import com.google.common.base.Preconditions;

/**
 * Container of {@link OpenSHA_Module} instances. Modules are added via {@link #addModule(OpenSHA_Module)}
 * and retrieved by their classes via {@link #getModule(Class)}.
 * <p>
 * When you add a module, all super-classes and super-interfaces of your module that also implement
 * {@link OpenSHA_Module} will be registered, so if you have the following classes:
 * 
 * <pre>
 * abstract class AbstractCustomModule implements OpenSHA_Module {
 * 	// abstract module code
 * }
 * 
 *  class CustomModuleImpl extends AbstractCustomModule {
 * 	// concrete module code
 * }
 * </pre>
 * 
 * ...and you add a module that is an instance of {@code CustomModuleImpl}, you can retrieve it via either
 * {@code getModule(CustomModuleImpl.class)} or {@code getModule(AbstractCustomModule.class)}. Helper classes or
 * interfaces that should never be mapped to a concrete module implementation should be marked with the
 * {@link ModuleHelper} annotation, and will be excluded from any mappings. 
 * <p>
 * TODO: consider synchronization
 * 
 * @author kevin
 *
 * @param <E>
 */
public class ModuleContainer<E extends OpenSHA_Module> {
	
	protected List<E> modules;
	private Map<Class<? extends E>, E> mappings;
	
	private List<Callable<E>> availableModules;
	private Map<Class<? extends E>, Callable<E>> availableMappings;
	
	public ModuleContainer() {
		modules = new ArrayList<>();
		mappings = new ConcurrentHashMap<>();
		availableModules = new ArrayList<>();
		availableMappings = new HashMap<>();
	}
	
	/**
	 * Helper method to determine if a module exists that maps to the given class, equivalent to
	 * {@code getModule(clazz) != null}. If there is a not-yet-loaded available module that matches
	 * this type, it will be loaded and will return true if loading is successful. Use
	 * {@link #hasAvailableModule(Class)} instead if you do not wish to load available modules.
	 * 
	 * @param clazz
	 * @return true if a module exists for the given class, false otherwise
	 */
	public boolean hasModule(Class<? extends E> clazz) {
		return getModule(clazz) != null;
	}
	
	/**
	 * Helper method to determine if a module exists that maps to the each of the given classes. If there are a
	 * not-yet-loaded available module that match these types, they will be loaded and will return true if loading
	 * is successful.
	 * 
	 * @param clases
	 * @return true if a module exists for each of the given classes, false otherwise
	 */
	public boolean hasAllModules(Collection<Class<? extends E>> classes) {
		boolean hasAll = true;
		for (Class<? extends E> clazz : classes) {
			if (!hasModule(clazz)) {
				hasAll = false;
				break;
			}
		}
		return hasAll;
	}
	
	/**
	 * Throws an {@link IllegalStateException} if the supplied module is not present, otherwise returns that module
	 * 
	 * @param clazz
	 * @return requested module
	 * @throws IllegalStateException if given module is not present
	 */
	public <M extends E> M requireModule(Class<M> clazz) throws IllegalStateException {
		M module = getModule(clazz);
		if (module == null)
			throw new IllegalStateException("Missing required module: "+clazz.getName());
		return module;
	}
	
	/**
	 * Retrieves a module that matches the given class if it exists, otherwise null.
	 * <p>
	 * If no current instance matches, but an available module loader does, then we will attempt to load that available
	 * module first. 
	 * 
	 * @param <M> the type to be returned
	 * @param clazz module class to look up
	 * @return module mapping the given class, or null if no match
	 */
	@SuppressWarnings("unchecked")
	public <M extends E> M getModule(Class<M> clazz) {
		return getModule(clazz, true);
	}
	
	/**
	 * Retrieves a module that matches the given class if it exists, otherwise null.
	 * <p>
	 * If no current instance matches, but an available module loader does, then we will attempt to load that available
	 * module first. 
	 * 
	 * @param <M> the type to be returned
	 * @param clazz module class to look up
	 * @param loadAvailable if true, load available modules if needed
	 * @return module mapping the given class, or null if no match
	 */
	@SuppressWarnings("unchecked")
	public <M extends E> M getModule(Class<M> clazz, boolean loadAvailable) {
		E module = mappings.get(clazz);
		if (loadAvailable && module == null) {
			// need to synchronize here as another thread could otherwise try to lead in this module
			synchronized (this) {
				if (!availableMappings.isEmpty()) {
					// see if we have it, and then load it lazily
					Callable<E> call = availableMappings.get(clazz);
					if (call != null && loadAvailableModule(call))
						return getModule(clazz);
				}
			}
		}
		return (M)module;
	}
	
	/**
	 * Adds the given module to this container, and maps any applicable super-classes/interfaces (that also implement
	 * {@link OpenSHA_Module} and aren't tagged with the {@link ModuleHelper} annotation) to this instance.
	 * 
	 * @param module
	 */
	public synchronized void addModule(E module) {
		Preconditions.checkState(module != this, "Cannot add a module to itself!");
		SubModule<ModuleContainer<E>> subModule = null;
		if (module instanceof SubModule<?>) {
			// make sure that the module is applicable to this container, and either set it or clone it
			
			subModule = getAsSubModule(module);
			
			module = checkCopySubModule(subModule);
			if (module != subModule)
				subModule = getAsSubModule(module);
		}
		
		List<Class<? extends OpenSHA_Module>> assignableClasses = getAssignableClasses(module.getClass());
		
		// fully remove any duplicate associations
		// this removes any module that is assignable from any class we are about to map
		for (Class<? extends OpenSHA_Module> clazz : assignableClasses)
			removeModuleInstances(clazz);
		
		modules.add(module);
		
		for (Class<? extends OpenSHA_Module> clazz : assignableClasses)
			mapModule(module, clazz);
		
		if (subModule != null && subModule.getParent() == null)
			subModule.setParent(this);
		
		// remove any available modules that are mapped to this
		removeAvailableModuleInstances(module.getClass());
	}
	
	@SuppressWarnings("unchecked")
	protected SubModule<ModuleContainer<E>> getAsSubModule(E module) {
		try {
			return (SubModule<ModuleContainer<E>>)module;
		} catch (Exception e) {
			throw new IllegalStateException("Can't add a sub-module that is not applicable to this container", e);
		}
	}
	
	@SuppressWarnings("unchecked")
	private E checkCopySubModule(SubModule<ModuleContainer<E>> subModule) {
		ModuleContainer<E> parent = subModule.getParent();
		if (parent != null && !parent.equals(this)) {
			debug("Getting copy of sub-module '"+subModule.getName()+"' with updated parent");
			subModule = subModule.copy(this);
		}
		return (E)subModule;
	}
	
	/**
	 * @param moduleClass
	 * @return list of all valid classes that can map to the given module class
	 */
	@SuppressWarnings("unchecked") // is actually checked
	private static List<Class<? extends OpenSHA_Module>> getAssignableClasses(Class<? extends OpenSHA_Module> moduleClass) {
		List<Class<? extends OpenSHA_Module>> assignableClasses = new ArrayList<>();
		assignableClasses.add(moduleClass);
		for (Class<?> clazz : ClassUtils.getAllSuperclasses(moduleClass))
			if (isValidModuleSubclass(clazz))
				// this is a super-class
				assignableClasses.add((Class<OpenSHA_Module>)clazz);
		for (Class<?> clazz : ClassUtils.getAllInterfaces(moduleClass))
			if (isValidModuleSubclass(clazz))
				// this is a super-interface
				assignableClasses.add((Class<OpenSHA_Module>)clazz);
		return assignableClasses;
	}
	
	/**
	 * 
	 * @param clazz
	 * @return true if this is an OpenSHA_Module instance and is not a ModuleHelper
	 */
	private static boolean isValidModuleSubclass(Class<?> clazz) {
		if (!OpenSHA_Module.class.isAssignableFrom(clazz))
			// this is not a module
			return false;
		if (clazz.getAnnotation(ModuleHelper.class) != null)
			// this is a helper class that should not be mapped, skip
			return false;
		return true;
	}
	
	/**
	 * Maps the given module as an implementation of the given class
	 * 
	 * @param module
	 * @param clazz
	 */
	@SuppressWarnings("unchecked")
	private void mapModule(E module, Class<? extends OpenSHA_Module> clazz) {
		Preconditions.checkState(clazz.getAnnotation(ModuleHelper.class) == null,
				"Cannot map a class that implements @ModuleHelper: %s", clazz.getName());
		if (mappings.containsKey(clazz))
			debug("Overriding module type '"+clazz.getName()+"' with: "+module.getName());
		else
			debug("Mapping module type '"+clazz.getName()+"' to: "+module.getName());
		Preconditions.checkState(OpenSHA_Module.class.isAssignableFrom(clazz));
		mappings.put((Class<E>)clazz, module);
	}
	
	/**
	 * Remove the given module and any mappings to that module.
	 * 
	 * @param module
	 * @return true if the module was present
	 */
	public synchronized boolean removeModule(E module) {
		boolean ret = modules.remove(module);
		if (ret)
			Preconditions.checkState(removeMappings(module));
		return ret;
	}
	
	/**
	 * Remove any modules (including any un-loaded available modules) that are assignable to the given class.
	 * 
	 * @param clazz module class to unload
	 * @return true if any mappings were removed
	 */
	public synchronized boolean removeModuleInstances(Class<? extends OpenSHA_Module> clazz) {
		boolean ret = false;
		for (int m=modules.size(); --m>=0;) {
			E module = modules.get(m);
			if (clazz.isAssignableFrom(module.getClass())) {
				debug("Removing module '"+module.getName()+"' with type '"+module.getClass().getName()
						+"' as it is assignable from '"+clazz.getName()+"'");
				modules.remove(m);
				Preconditions.checkState(removeMappings(module));
				ret = true;
			}
		}
		
		if (removeAvailableModuleInstances(clazz))
			ret = true;
		
		return ret;
	}
	
	private boolean removeMappings(E module) {
		List<Class<? extends E>> oldMappings = new ArrayList<>();
		for (Class<? extends E> clazz : mappings.keySet())
			if (mappings.get(clazz).equals(module))
				oldMappings.add(clazz);
		boolean ret = false;
		for (Class<? extends E> oldMapping : oldMappings)
			ret |= mappings.remove(oldMapping) != null;
		return ret;
	}
	
	/**
	 * Removes all modules (including any available modules not yet loaded)
	 */
	public synchronized void clearModules() {
		modules.clear();
		mappings.clear();
		availableMappings.clear();
		availableModules.clear();
	}
	
	/*
	 * Available modules that can be lazily initialized
	 */
	
	/**
	 * Adds an available module that will be lazily loaded when {@link ModuleContainer#getModule(Class)} or
	 * {@link ModuleContainer#hasModule(Class)} is called and no module is presently loaded that matches the given class.
	 * 
	 * @param call
	 * @param moduleClass
	 */
	@SuppressWarnings("unchecked")
	public synchronized <M extends E> void addAvailableModule(Callable<? extends OpenSHA_Module> call, Class<M> moduleClass) {
		List<Class<? extends OpenSHA_Module>> assignableClasses = getAssignableClasses(moduleClass);
		
		// fully remove any duplicate associations
		// this removes any available module that is assignable from any class we are about to map
		for (Class<? extends OpenSHA_Module> clazz : assignableClasses)
			removeAvailableModuleInstances(clazz);
		
		availableModules.add((Callable<E>)call);
		
		for (Class<? extends OpenSHA_Module> clazz : assignableClasses)
			mapAvailableModule(call, clazz);
	}
	
	/**
	 * Similar to {@link #addAvailableModule(Callable, Class)} except that the available module will only be registered
	 * if no matching module or available module exists that maps to this class.
	 * <p>
	 * Alias to:<code>
	 * if (!hasAvailableModule(moduleClass)) addAvailableModule(call, moduleClass);
	 * </code>
	 * 
	 * @param call
	 * @param moduleClass
	 */
	public synchronized <M extends E> void offerAvailableModule(Callable<M> call, Class<M> moduleClass) {
		if (!hasAvailableModule(moduleClass))
			addAvailableModule(call, moduleClass);
	}
	
	/**
	 * Determine if a module exists that maps to the given class, or if we have an available
	 * module that is not yet loaded of that type. This differs from {@link ModuleContainer#hasModule(Class)}
	 * in that it only tests for existence of an available module, but will not load it. Use this if you only
	 * need to test that a module is available, but loading might be expensive.
	 * 
	 * @param clazz
	 * @return true if a module exists for the given class, false otherwise
	 */
	public synchronized boolean hasAvailableModule(Class<? extends E> clazz) {
		E module = mappings.get(clazz);
		if (module != null)
			return true;
		
		// see if we have a loader for it
		Callable<E> call = availableMappings.get(clazz);
		if (call != null)
			return true;
		return false;
	}
	
	/**
	 * Loads and maps any available modules that have not yet been initialized
	 * 
	 * @see {@link #addAvailableModule(Callable, Class)}
	 */
	public synchronized void loadAllAvailableModules() {
		// wrap in new list, as the load method modifies this list
		List<Callable<? extends E>> available = new ArrayList<>(availableModules);
		for (Callable<? extends E> call : available)
			if (availableModules.contains(call))
				loadAvailableModule(call);
	}
	
	/**
	 * Attempts to load the given available module
	 * 
	 * @param call (must have already been registered via {@linkp #addAvailableModule(Callable, Class)})
	 * @return true if loading succeeded, otherwise false
	 * @throws IllegalStateException if call is not already registered as an available module
	 */
	public synchronized boolean loadAvailableModule(Callable<? extends E> call) {
		Preconditions.checkState(availableModules.remove(call));
		E module = null;
		try {
			debug("Lazily loading available module...");
			module = call.call();
		} catch (Exception e) {
			e.printStackTrace();
			debug("WARNING: failed to lazily load a module (see exception above)", true);
		}
		
		// remove this available module, whether or not it was successful
		availableModules.remove(call);
		List<Class<? extends E>> oldMappings = new ArrayList<>();
		for (Class<? extends E> oClazz : availableMappings.keySet())
			if (availableMappings.get(oClazz).equals(call))
				oldMappings.add(oClazz);
		for (Class<? extends E> oldMapping : oldMappings)
			availableMappings.remove(oldMapping);
		
		if (module != null) {
			// register it and report success
			addModule(module);
			return true;
		}
		// failed
		return false;
	}
	
	@SuppressWarnings("unchecked")
	private void mapAvailableModule(Callable<? extends OpenSHA_Module> call, Class<? extends OpenSHA_Module> clazz) {
		Preconditions.checkState(clazz.getAnnotation(ModuleHelper.class) == null,
				"Cannot map a class that implements @ModuleHelper: %s", clazz.getName());
		if (availableMappings.containsKey(clazz))
			debug("Overriding available module with type: "+clazz.getName());
		else
			debug("Mapping available module with type: "+clazz.getName());
		Preconditions.checkState(OpenSHA_Module.class.isAssignableFrom(clazz));
		availableMappings.put((Class<E>)clazz, (Callable<E>)call);
	}
	
	/**
	 * Removes any available modules that have been added for the given class
	 * 
	 * @param clazz
	 * @return true if an available module was removed
	 */
	public synchronized boolean removeAvailableModuleInstances(Class<? extends OpenSHA_Module> clazz) {
		boolean ret = false;
		
		HashSet<Callable<E>> removeCalls = new HashSet<>();
		for (Class<? extends E> moduleClass : availableMappings.keySet()) {
			if (clazz.isAssignableFrom(moduleClass)) {
				debug("Removing available module with type '"+moduleClass.getName()
						+"' as it is assignable from '"+clazz.getName()+"'");
				removeCalls.add(availableMappings.get(moduleClass));
			}
		}
		
		for (Callable<E> call : removeCalls) {
			Preconditions.checkState(availableModules.remove(call), "Callable already removed?");
			Preconditions.checkState(removeAvailableMappings(call), "No mappings existed to callable");
			ret = true;
		}
		return ret;
	}
	
	private boolean removeAvailableMappings(Callable<E> call) {
		List<Class<? extends E>> oldMappings = new ArrayList<>();
		for (Class<? extends E> clazz : availableMappings.keySet())
			if (availableMappings.get(clazz).equals(call))
				oldMappings.add(clazz);
		boolean ret = false;
		for (Class<? extends E> oldMapping : oldMappings)
			ret |= availableMappings.remove(oldMapping) != null;
		return ret;
	}
	
	/**
	 * @return unmodifiable view of the current modules, will load any not-yet loaded modules first
	 */
	public List<E> getModules() {
		return getModules(true);
	}
	
	/**
	 * @param loadAvailable if true, any available but not-yet loaded modules will be loaded first
	 * @return unmodifiable view of the current modules
	 */
	public synchronized List<E> getModules(boolean loadAvailable) {
//		System.out.println("GET CALLED, loadAvailabe="+loadAvailable+", loaded="+modules.size()
//				+", available="+availableModules.size());
		if (loadAvailable)
			loadAllAvailableModules();
		return Collections.unmodifiableList(modules);
	}
	
	/**
	 * 
	 * @return unmodifiable view of the current available module loaders
	 */
	public synchronized List<Callable<E>> getAvailableModules() {
		return Collections.unmodifiableList(availableModules);
	}
	
	/**
	 * Returns all modules that are assignable to the given type. For example, if you wanted to retrieve the
	 * set of modules that implement {@link ArchivableModule}, call this method with that type. The given
	 * type can be any interface or abstract class.
	 * 
	 * @param type
	 * @param loadAvailable if true, any available matching but not-yet loaded modules will be loaded first
	 * @return list of matching modules (will be empty if no matches)
	 */
	public synchronized List<E> getModulesAssignableTo(Class<?> type, boolean loadAvailable) {
		List<E> ret = new ArrayList<>();
		
		// check loaded modules
//		System.out.println("Looking for modules assignable to "+type.getName());
		for (E module : getModules(false)) {
//			System.out.println("\tTesting module "+module.getName()+" of type "+module.getClass());
			if (type.isAssignableFrom(module.getClass())) {
//				System.out.println("\t\tMATCH!");
				ret.add(module);
//			} else {
//				System.out.println("\t\tnot a match");
			}
		}
		
		if (loadAvailable) {
			// check available modules
			Map<Callable<E>, Class<? extends E>> matchingCalls = new HashMap<>();
			for (Class<? extends E> moduleClass : availableMappings.keySet()) {
				Callable<E> call = availableMappings.get(moduleClass);
				if (!matchingCalls.containsKey(call) && type.isAssignableFrom(moduleClass))
					matchingCalls.put(call, moduleClass);
			}
			
			for (Callable<E> call : matchingCalls.keySet()) {
				if (loadAvailableModule(call)) {
					E module = getModule(matchingCalls.get(call));
					Preconditions.checkNotNull(module);
					ret.add(module);
				}
			}
		}
		
		return ret;
	}
	
	/**
	 * This returns a unique prefix to be used if this container also a {@link OpenSHA_Module} and is written as member
	 * of a {@link ModuleArchive}. This allows nested file structures within an archive. Default implementation returns
	 * null, and must be overridden to supply a non-empty prefix if this is ever included as a module within a parent
	 * archive.
	 * <p>
	 * Implementations may wish to return a string ending with a forward slash ('/'), which will create a new directory
	 * for all files within an archive.
	 * 
	 * @return prefix that will be applied to files for all modules if this container is written to an archive
	 */
	protected String getNestingPrefix() {
		return null;
	}
	
	private void debug(String message) {
		debug(message, false);
	}
	
	private void debug(String message, boolean err) {
		if (this instanceof Named)
			message = ((Named)this).getName()+":\t"+message;
		if (err)
			System.err.println(message);
		else
			System.out.println(message);
	}

}
