package org.opensha.commons.metadata;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.dom4j.Element;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;

public class MetadataLoader implements ParameterChangeWarningListener {

	public MetadataLoader() {
	}
	
	/**
	 * Calls the static fromXMLMetadata(Element) method on the given class
	 * @param el
	 * @param className
	 * @return
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	public static Object loadXMLwithReflection(Element el, String className)
			throws ClassNotFoundException, NoSuchMethodException, SecurityException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Class<?> clazz = Class.forName(className);
		
		Method method = clazz.getMethod("fromXMLMetadata", Element.class);
		return method.invoke(null, el);
	}

	/**
	 * Creates a class instance from a string of the full class name including packages.
	 * This is how you dynamically make objects at runtime if you don't know which\
	 * class beforehand.
	 *
	 */
	public static Object createClassInstance(String className) throws InvocationTargetException{
		return createClassInstance(className, null, null);
	}
	
	/**
	 * Creates a class instance from a string of the full class name including packages.
	 * This is how you dynamically make objects at runtime if you don't know which\
	 * class beforehand.
	 *
	 */
	public static Object createClassInstance(String className, ArrayList<Object> args) throws InvocationTargetException{
		return createClassInstance(className, null, null);
	}
	
	/**
	 * Creates a class instance from a string of the full class name including packages.
	 * This is how you dynamically make objects at runtime if you don't know which\
	 * class beforehand.
	 *
	 */
	public static Object createClassInstance(String className, ArrayList<Object> args, ArrayList<String> argNames) throws InvocationTargetException{
		try {
			Object[] paramObjects;;
			Class[] params;
			if (args == null) {
				paramObjects = new Object[]{};
				params = new Class[]{};
			} else {
				paramObjects = new Object[args.size()];
				params = new Class[args.size()];
				for (int i=0; i<args.size(); i++) {
					Object obj = args.get(i);
					paramObjects[i] = obj;
					if (argNames == null) {
						params[i] = obj.getClass();
					} else {
						String name = argNames.get(i);
						params[i] = Class.forName(name);
					}
				}
			}
			
			Class newClass = Class.forName( className );
			Constructor con = newClass.getDeclaredConstructor(params);
			con.setAccessible(true);
			Object obj = con.newInstance( paramObjects );
			return obj;
		} catch (InvocationTargetException e) {
			throw e;
		} catch (Exception e ) {
			throw new RuntimeException(e);
		}
	}

	public void parameterChangeWarning(ParameterChangeWarningEvent event) {
		// TODO Auto-generated method stub
		
	}
}
