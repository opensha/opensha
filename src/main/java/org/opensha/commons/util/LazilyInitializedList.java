package org.opensha.commons.util;

import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import com.google.common.base.Preconditions;

/**
 * Lazily initialized list that will only load the backing data when needed. If the size is specified via the constructor,
 * then size() calls will not trigger data loading.
 * 
 * @author kevin
 *
 * @param <E>
 */
public class LazilyInitializedList<E> extends AbstractList<E> {
	
	private int size;
	private Callable<List<E>> callable;
	private List<E> list;

	public LazilyInitializedList(Callable<List<E>> callable) {
		this(-1, callable);
	}

	public LazilyInitializedList(int size, Callable<List<E>> callable) {
		this.size = size;
		this.callable = callable;
	}
	
	private void checkInit() {
		if (list == null) {
			synchronized (this) {
				if (list == null) {
					List<E> list;
					try {
						list = callable.call();
					} catch (Exception e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					if (size < 0)
						size = list.size();
					else
						Preconditions.checkState(list.size() == size,
								"Expected list of size=%s, have size=%s", size, list.size());
					this.list = list;
				}
			}
		}
	}

	@Override
	public E get(int index) {
		checkInit();
		return list.get(index);
	}

	@Override
	public int size() {
		if (size < 0)
			checkInit();
		if (list == null)
			// this size was passed in, return without loading actual list
			return size;
		return list.size();
	}

	@Override
	public boolean add(E e) {
		checkInit();
		return list.add(e);
	}

	@Override
	public E set(int index, E element) {
		checkInit();
		return list.set(index, element);
	}

	@Override
	public void add(int index, E element) {
		checkInit();
		list.add(index, element);
	}

	@Override
	public E remove(int index) {
		checkInit();
		return list.remove(index);
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		checkInit();
		return list.addAll(index, c);
	}

}
