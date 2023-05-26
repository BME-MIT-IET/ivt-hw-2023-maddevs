package com.complexible.pinto.factory;


import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.beans.PropertyDescriptor;
import java.util.*;

/**
 * <p>Default implementation of a {@link CollectionFactory}.  Uses {@link Class#newInstance()}, but when that
 * fails, it will fall back to creating a default type for each basic type of {@code Collection}.  For {@code List}
 * an {@link ArrayList} is used, for {@code Set} a {@link LinkedHashSet}, for {@code SortedSet} a {@link TreeSet}, and
 * for any other type of {@code Collection}, a {@link LinkedHashSet}.</p>
 *
 * @author Michael Grove
 * @version 1.0
 * @since 1.0
 */
public  class DefaultCollectionFactory implements CollectionFactory {
    /**
     * {@inheritDoc}
     */
    @Override
    public Collection create(final PropertyDescriptor thePropertyDescriptor) {
        final Class<?> aType = thePropertyDescriptor.getPropertyType();
        try {
            // try creating a new instance.  this will work if they've specified a concrete type *and* it has a
            // default constructor, which is true of all the core collections.
            return (Collection) aType.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            if (List.class.isAssignableFrom(aType)) {
                return Lists.newArrayList();
            } else if (Set.class.isAssignableFrom(aType)) {
                if (SortedSet.class.isAssignableFrom(aType)) {
                    return Sets.newTreeSet();
                } else {
                    return Sets.newLinkedHashSet();
                }
            } else if (Collection.class.equals(aType)) {
                return Sets.newLinkedHashSet();
            } else {
                // what else could there be?
                throw new RuntimeException("Unknown or unsupported collection type for a field: " + aType);
            }
        }
    }
}