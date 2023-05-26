package com.complexible.pinto.factory;

import com.complexible.pinto.RDFMapper;

import java.beans.PropertyDescriptor;
import java.util.Collection;

/**
 * <p>A factory for creating instances of {@link Collection} </p>
 *
 * @author Michael Grove
 * @version 1.0
 * @see RDFMapper.DefaultCollectionFactory
 * @since 1.0
 */
public interface CollectionFactory {
    Collection create(final PropertyDescriptor thePropertyDescriptor);
}