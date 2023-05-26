package com.complexible.pinto.factory;

import java.beans.PropertyDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>Default implementation of a {@link MapFactory} which relies on {@link Class#newInstance()} and falls back
 * to creating a {@link LinkedHashMap} when that fails.</p>
 *
 * @author Michael Grove
 * @version 1.0
 * @since 1.0
 */
public interface MapFactory {
    Map create(final PropertyDescriptor theDescriptor);
}