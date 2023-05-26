package com.complexible.common.beans;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Iterator;

import com.google.common.collect.AbstractIterator;

public final class Beans {

	private Beans() {
		throw new AssertionError();
	}

	private static <T> Iterable<T> getDeclaredElements(final Class<?> theClass, Class<T> elementType) {
		return () -> new AbstractIterator<T>() {
			private Class<?> mCurr = theClass;
			private T[] mElements = null;
			private int mIndex = 0;

			@Override
			protected T computeNext() {
				if (mElements == null || mIndex >= mElements.length) {
					if (mCurr != null) {
						if (elementType.equals(Field.class)) {
							mElements = (T[]) mCurr.getDeclaredFields();
						} else if (elementType.equals(Method.class)) {
							mElements = (T[]) mCurr.getDeclaredMethods();
						}
						mIndex = 0;
						mCurr = mCurr.getSuperclass();
					} else {
						return endOfData();
					}
				}
				return mElements[mIndex++];
			}
		};
	}

	public static Iterable<Field> getDeclaredFields(final Class<?> theClass) {
		return getDeclaredElements(theClass, Field.class);
	}

	public static Iterable<Method> getDeclaredMethods(final Class<?> theClass) {
		return getDeclaredElements(theClass, Method.class);
	}

	public static boolean isPrimitive(Object theObj) {
		return (theObj instanceof Boolean || theObj instanceof Integer || theObj instanceof Long
				|| theObj instanceof Short || theObj instanceof Double || theObj instanceof Float
				|| theObj instanceof Date || theObj instanceof String || theObj instanceof Character
				|| theObj instanceof java.net.URI);
	}

	public static boolean isPrimitive(Class<?> theObj) {
		return (Boolean.class.equals(theObj) || Integer.class.equals(theObj) || Long.class.equals(theObj)
				|| Short.class.equals(theObj) || Double.class.equals(theObj) || Float.class.equals(theObj)
				|| Date.class.equals(theObj) || String.class.equals(theObj) || Character.class.equals(theObj)
				|| java.net.URI.class.equals(theObj));
	}
}
