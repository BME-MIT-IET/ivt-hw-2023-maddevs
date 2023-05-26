package com.complexible.pinto.factory;

import com.complexible.pinto.factory.MapFactory;
import com.google.common.collect.Maps;

import java.beans.PropertyDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.complexible.common.io.MMapUtil.LOGGER;


public  class DefaultMapFactory implements MapFactory {
    @Override
    public Map create(final PropertyDescriptor theDescriptor) {
        final Class<?> aType = theDescriptor.getPropertyType();

        try {
            // try creating a new instance.  this will work if they've specified a concrete type *and* it has a
            // default constructor, which is true of all the core maps.
            return (Map) aType.newInstance();
        } catch (Throwable e) {
            LOGGER.warn("{} uses a map type, but it cannot be instantiated, using a default LinkedHashMap", theDescriptor);
        }

        return Maps.newLinkedHashMap();
    }
}