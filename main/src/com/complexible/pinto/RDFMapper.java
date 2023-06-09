/*
 * Copyright (c) 2015 Complexible Inc. <http://complexible.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.complexible.pinto;
import com.complexible.common.base.Option;
import com.complexible.common.base.Options;
import com.complexible.common.beans.Beans;
import com.complexible.common.openrdf.model.Models2;
import com.complexible.common.openrdf.model.Statements;
import com.complexible.common.openrdf.util.ModelBuilder;
import com.complexible.common.openrdf.util.ResourceBuilder;
import com.complexible.common.reflect.Classes;
import com.complexible.common.reflect.Methods;
import com.complexible.common.util.Namespaces;
import com.complexible.common.utils.Dates2;
import com.complexible.pinto.annotations.Iri;
import com.complexible.pinto.annotations.RdfId;
import com.complexible.pinto.annotations.RdfProperty;
import com.complexible.pinto.annotations.RdfsClass;
import com.complexible.pinto.factory.CollectionFactory;
import com.complexible.pinto.factory.DefaultCollectionFactory;
import com.complexible.pinto.factory.DefaultMapFactory;
import com.complexible.pinto.factory.MapFactory;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.*;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.apache.commons.beanutils.FluentPropertyBeanIntrospector;
import org.apache.commons.beanutils.PropertyUtils;
import org.openrdf.model.*;
import org.openrdf.model.impl.SimpleValueFactory;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.model.vocabulary.XMLSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.WildcardTypeImpl;

import java.beans.PropertyDescriptor;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


/**
 * <p>Mapper for turning Java beans into RDF and RDF into Java beans.</p>
 *
 * @author Michael Grove
 * @version 2.0
 * @since 1.0
 */
public final class RDFMapper {

    public static final String DEFAULT_NAMESPACE = "tag:complexible:pinto:";
    public static final String DEFAULT_PREFIX = "";
    public static final IRI KEY = SimpleValueFactory.getInstance().createIRI(DEFAULT_NAMESPACE, "_key");
    public static final IRI VALUE = SimpleValueFactory.getInstance().createIRI(DEFAULT_NAMESPACE, "_value");
    public static final IRI HAS_ENTRY = SimpleValueFactory.getInstance().createIRI(DEFAULT_NAMESPACE, "_hasEntry");
    /**
     * The logger for monitoring and debugging purposes
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(RDFMapper.class);
    private static final ImmutableSet<IRI> INTEGER_TYPES = ImmutableSet.of(XMLSchema.INT, XMLSchema.INTEGER, XMLSchema.POSITIVE_INTEGER,
            XMLSchema.NEGATIVE_INTEGER, XMLSchema.NON_NEGATIVE_INTEGER,
            XMLSchema.NON_POSITIVE_INTEGER, XMLSchema.UNSIGNED_INT);
    private static final ImmutableSet<IRI> LONG_TYPES = ImmutableSet.of(XMLSchema.LONG, XMLSchema.UNSIGNED_LONG);
    private static final ImmutableSet<IRI> FLOAT_TYPES = ImmutableSet.of(XMLSchema.FLOAT, XMLSchema.DECIMAL);
    private static final ImmutableSet<IRI> SHORT_TYPES = ImmutableSet.of(XMLSchema.SHORT, XMLSchema.UNSIGNED_SHORT);
    private static final ImmutableSet<IRI> BYTE_TYPES = ImmutableSet.of(XMLSchema.BYTE, XMLSchema.UNSIGNED_BYTE);

    static {
        PropertyUtils.addBeanIntrospector(new FluentPropertyBeanIntrospector());
    }

    private final ImmutableBiMap<IRI, Class> mMappings;
    private final ImmutableMap<Class<?>, Function<Object, Resource>> mIdFunctions;
    private final ValueFactory mValueFactory;
    private final Options mMappingOptions;
    private final CollectionFactory mCollectionFactory;
    private final MapFactory mMapFactory;
    private final Map<String, String> mNamespaces;
    private final String mDefaultNamespace;
    private final Map<Class<?>, RDFCodec<?>> mCodecs;

    private RDFMapper(final Map<IRI, Class> theMappings,
                      final Map<Class<?>, Function<Object, Resource>> theIdFunctions,
                      final ValueFactory theValueFactory,
                      final Map<String, String> theNamespaces,
                      final CollectionFactory theFactory, final MapFactory theMapFactory,
                      final Map<Class<?>, RDFCodec<?>> theCodecs, final Options theMappingOptions) {

        mCollectionFactory = theFactory;
        mMapFactory = theMapFactory;
        mValueFactory = theValueFactory;
        mNamespaces = theNamespaces;
        mCodecs = theCodecs;
        mMappingOptions = theMappingOptions;

        mMappings = ImmutableBiMap.copyOf(theMappings);
        mIdFunctions = ImmutableMap.copyOf(theIdFunctions);

        mDefaultNamespace = mNamespaces.get(DEFAULT_PREFIX);
    }

    private static boolean isIgnored(final PropertyDescriptor thePropertyDescriptor) {
        // we'll ignore getClass() on the bean
        return thePropertyDescriptor.getName().equals("class")
                && thePropertyDescriptor.getReadMethod().getDeclaringClass() == Object.class
                && thePropertyDescriptor.getReadMethod().getReturnType().equals(Class.class);
    }

    /**
     * Create a new {@link RDFMapper} with the default settings
     *
     * @return a new {@code RDFMapper}
     */
    public static RDFMapper create() {
        return builder().build();
    }

    /**
     * Return a {@link Builder} for configurating and creating a new {@link RDFMapper}
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private Object processValue(Model theGraph, Value value) {
        if (value instanceof Literal) {
            return valueToObject(value, theGraph, null);
        } else {
            return readValue(theGraph, type(theGraph, (Resource) value), (Resource) value);
        }
    }

    private void processMapEntry(Model theGraph, Value aMapEntry, Map<Object, Object> aMap) {
        final Value aKey = theGraph.stream().filter(Statements.subjectIs((Resource) aMapEntry).and(Statements.predicateIs(KEY))).map(Statement::getObject).findFirst().orElse(null);
        final Value aValue = theGraph.stream().filter(Statements.subjectIs((Resource) aMapEntry).and(Statements.predicateIs(VALUE))).map(Statement::getObject).findFirst().orElse(null);


        Object aKeyObj = processValue(theGraph, aKey);
        Object aValueObj = processValue(theGraph, aValue);

        if (aKeyObj == null || aValueObj == null) {
            LOGGER.warn("Skipping map entry, key or value could not be created.");
            return;
        }

        aMap.put(aKeyObj, aValueObj);
    }

    private <T> T newInstance(final Class<T> theClass) {
        try {
            return theClass.newInstance();
        }
        catch (InstantiationException  |  IllegalAccessException e) {
            throw new RDFMappingException(String.format("Could not create an instance of %s, it does not have a default constructor", theClass),e);
        }
    }

    /**
     * Read the object from the RDF.
     * <p>
     * If there is more than one resource in the graph, you should use {@link #readValue(Model, Class, Resource)} and
     * specify the identifier of the object you wish to read.  Otherwise, an {@link RDFMappingException} will be thrown
     * to indicate that it's not clear what resource should be read.
     *
     * @param theGraph the RDF
     * @param theClass the type of the object to read
     * @return the object
     * @throws RDFMappingException if the object could not be created
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(final Model theGraph, final Class<T> theClass) {
        RDFCodec<T> aCodec = (RDFCodec<T>) mCodecs.get(theClass);

        final Collection<Resource> aSubjects = theGraph.subjects();

        if (aSubjects.size() > 1) {
            throw new RDFMappingException("Multiple subjects found, need to specify the identifier of the object to create.");
        } else if (aSubjects.isEmpty()) {
            return aCodec == null ? newInstance(theClass)
                    : aCodec.readValue(theGraph, SimpleValueFactory.getInstance().createBNode());
        }

        final Resource aSubj = aSubjects.iterator().next();

        if (aCodec != null) {
            return aCodec.readValue(theGraph, aSubj);
        } else {
            return readValue(theGraph, theClass, aSubj);
        }
    }

    /**
     * Read the object from the RDF
     *
     * @param theGraph the RDF
     * @param theClass the type of the object to read
     * @param theObj   the identifier of the object to create
     * @return the object
     * @throws RDFMappingException if the object could not be created
     */
    public <T> T readValue(final Model theGraph, final Class<T> theClass, final Resource theObj) {
        if (theClass == null) {
            return null;
        }

        final T aInst = newInstance(theClass);

        if (aInst instanceof Identifiable) {
            ((Identifiable)aInst).id(theObj);
        }

        for (PropertyDescriptor aDescriptor : PropertyUtils.getPropertyDescriptors(aInst)) {
            try{
                if (isIgnored(aDescriptor)) {
                    continue;
                }

                final IRI aProperty = getProperty(aDescriptor);

                Collection<Value> aValues = theGraph.stream().filter(Statements.subjectIs(theObj).and(Statements.predicateIs(aProperty))).map(Statement::getObject).collect(Collectors.toList());

                Object aObj;

                if (aValues.isEmpty()) {
                    continue;
                }
                else if (Collection.class.isAssignableFrom(aDescriptor.getPropertyType())) {
                    final Collection aIterable = mCollectionFactory.create(aDescriptor);

                    Collection<Value> aElems = Lists.newArrayListWithCapacity(aValues.size());

                    // this will allow the mixing of RDF lists of values with single values.  in "well-formed" data that
                    // kind of mixing probably won't ever happen.  but it's easier/better to be lax about what we'll accept
                    // here, and this will cover one or more list assertions as well as multiple property assertions forming
                    // the list as well as the mix of both
                    for (Value aValue : aValues) {
                        if (aValue instanceof Resource && Models2.isList(theGraph, (Resource) aValue)) {
                            aElems.addAll(Models2.asList(theGraph, (Resource) aValue));
                        }
                        else {
                            aElems.add(aValue);
                        }
                    }

                    aElems.stream()
                            .map(toObject(theGraph, aDescriptor)::apply)
                            .forEach(aIterable::add);

                    aObj = aIterable;
                }
                else if (Map.class.isAssignableFrom(aDescriptor.getPropertyType())) {
                    Value aPropValue = handleCardinalityViolations(aDescriptor, aValues);


                    final Map aMap = mMapFactory.create(aDescriptor);

                    for (Value aMapEntry : theGraph.filter((Resource) aPropValue, HAS_ENTRY, null).objects()) {
                        processMapEntry(theGraph, aMapEntry, aMap);
                    }

                    aObj = aMap;
                }
                else {
                    final Value aValue = handleCardinalityViolations(aDescriptor, aValues);

                    aObj = valueToObject(aValue, theGraph, aDescriptor);
                }


                // this will fail spectacularly if there is a mismatch between the incoming RDF and what the bean
                // defines.  we can either check that eagerly and fail spectacularly then, or do it here and be
                // lazy.  we'll go with lazy
                PropertyUtils.setProperty(aInst, aDescriptor.getName(), aObj);
            }
            catch (IllegalAccessException e) {
                throw new RDFMappingException("Illegal access while setting property: " + aDescriptor.getName(), e);
            }
            catch (InvocationTargetException e) {
                throw new RDFMappingException("Exception thrown by an invoked method or constructor while setting property: " + aDescriptor.getName(), e);
            }
            catch (NoSuchMethodException e) {
                throw new RDFMappingException("No such method while setting property: " + aDescriptor.getName(), e);
            }
            catch (Exception e) {
                Throwables.propagateIfInstanceOf(e, RDFMappingException.class);
                throw new RDFMappingException(e);
            }
        }

        return aInst;
    }

    private Value handleCardinalityViolations(PropertyDescriptor aDescriptor, Collection<Value> aValues) {
        if (aValues.size() > 1) {
            if (mMappingOptions.is(MappingOptions.IGNORE_CARDINALITY_VIOLATIONS)) {
                LOGGER.warn("Property type of {} is {}, expected a single value, but {} were found.  MappingOptions is set to ignore this, so using only the first value.",
                        aDescriptor.getName(), aDescriptor.getPropertyType(), aValues.size());
            } else {
                throw new RDFMappingException(String.format("%s values found, but property type is %s",
                        aValues.size(), aDescriptor.getPropertyType()));
            }
        }

        return aValues.iterator().next();
    }

    private Class type(final Model theGraph, final Resource theValue) {
        final Iterable<Resource> aTypes = Models2.getTypes(theGraph, theValue);
        for (Resource aType : aTypes) {
            final Class aClass = mMappings.get(aType);
            if (aClass != null) {
                return aClass;
            }
        }

        return null;
    }

    private Function<Value, Object> toObject(final Model theGraph, final PropertyDescriptor theDescriptor) {
        return theInput -> valueToObject(theInput, theGraph, theDescriptor);
    }

    private String expand(final String theValue) {
        final int aIndex = theValue.indexOf(":");
        if (aIndex != -1) {
            String aPrefix = theValue.substring(0, aIndex);
            String aLocalName = theValue.substring(aIndex + 1);

            final String aNS = mNamespaces.get(aPrefix);
            if (aNS != null) {
                return aNS + aLocalName;
            }
        }

        return theValue;
    }

    /**
     * Write the given value as RDF.
     *
     * @param theValue the value to write
     * @return the value serialized as RDF
     * @throws UnidentifiableObjectException thrown when an rdf:ID cannot be created for {@code theValue}
     * @throws RDFMappingException           indicates a general error, such as issues transforming a property value
     *                                       into RDF.
     */
    public <T> Model writeValue(final T theValue) {
        return write(theValue).model();
    }

    @SuppressWarnings("unchecked")
    private <T> ResourceBuilder write(final T theValue) {
        // before we do anything, do we have a custom codec for this?
        RDFCodec aCodec = mCodecs.get(theValue.getClass());
        if (aCodec != null) {
            final Value aResult = aCodec.writeValue(theValue);

            if (aResult instanceof ResourceBuilder) {
                return (ResourceBuilder) aResult;
            } else {
                return new ResourceBuilder(id(theValue)).addType(getType(theValue)).addProperty(VALUE, aResult);
            }
        }

        final Resource aId = id(theValue);

        final IRI aType = getType(theValue);

        try {
            final ModelBuilder aGraph = new ModelBuilder(mValueFactory);

            ResourceBuilder aBuilder = aGraph.instance(aType, aId);

            for (Map.Entry<String, Object> aEntry : PropertyUtils.describe(theValue).entrySet()) {
                final PropertyDescriptor aDescriptor = PropertyUtils.getPropertyDescriptor(theValue, aEntry.getKey());

                if (isIgnored(aDescriptor)) {
                    continue;
                }

                final IRI aProperty = getProperty(aDescriptor);

                if (aProperty == null) {
                    continue;
                }

                final Object aObj = aEntry.getValue();

                if (aObj != null) {
                    setValue(aGraph, aBuilder, aDescriptor, aProperty, aObj);
                }
            }

            return aBuilder;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            Throwables.propagateIfInstanceOf(e, RDFMappingException.class);
            throw new RDFMappingException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void setValue(final ModelBuilder theGraph, final ResourceBuilder theBuilder,
                          final PropertyDescriptor thePropertyDescriptor,
                          final IRI theProperty, final Object theObj) {

        if (Beans.isPrimitive(theObj)) {
            theBuilder.addProperty(theProperty, toLiteral(theObj, getPropertyAnnotation(thePropertyDescriptor)));
        }
        else if (Enum.class.isAssignableFrom(theObj.getClass())) {
            theBuilder.addProperty(theProperty, enumToURI((Enum) theObj));
        }
        else if (Collection.class.isAssignableFrom(theObj.getClass())) {
            handleCollection(theGraph, theBuilder, theProperty, theObj, thePropertyDescriptor);

        }
        else if (Map.class.isAssignableFrom(theObj.getClass())) {
            Map aMap = (Map) theObj;

            if (!aMap.isEmpty()) {
                ResourceBuilder aRes = theGraph.instance();
                for (Map.Entry aMapEntry : (Set<Map.Entry>) aMap.entrySet()) {
                    ResourceBuilder aEntryRes = theGraph.instance();

                    setValue(theGraph, aEntryRes, null, KEY, aMapEntry.getKey());
                    setValue(theGraph, aEntryRes, null, VALUE, aMapEntry.getValue());

                    aRes.addProperty(HAS_ENTRY, aEntryRes);
                }

                theBuilder.addProperty(theProperty, aRes);
            }
        }
        else {
            RDFCodec aCodex = mCodecs.get(theObj.getClass());
            if (aCodex != null) {
                final Value aValue = aCodex.writeValue(theObj);

                if (aValue instanceof ResourceBuilder) {
                    theBuilder.addProperty(theProperty, (ResourceBuilder) aValue);
                }
                else {
                    theBuilder.addProperty(theProperty, aValue);
                }
            }
            else {
                theBuilder.addProperty(theProperty, write(theObj));
            }
        }
    }


    private void handleCollection(final ModelBuilder theGraph, final ResourceBuilder theBuilder,
                                  final IRI theProperty, final Object theObj,
                                  final PropertyDescriptor thePropertyDescriptor) {

        final Collection aCollection = (Collection) theObj;

        if (serializeCollectionsAsRDFList(thePropertyDescriptor)) {
            List<Value> aList = Lists.newArrayListWithExpectedSize(aCollection.size());

            for (Object aVal : aCollection) {
                if (Beans.isPrimitive(aVal)) {
                    aList.add(toLiteral(aVal, getPropertyAnnotation(thePropertyDescriptor)));
                }
                else {
                    ResourceBuilder aIndividual = write(aVal);
                    aList.add(aIndividual.getResource());
                    theBuilder.model().addAll(aIndividual.model());
                }
            }

            if (!aList.isEmpty()) {
                theBuilder.addProperty(theProperty, Models2.toList(aList, theBuilder.model()));
            }
        }
        else {
            for (Object aVal : aCollection) {
                // this would not handle collections of collections, does that matter?
                if (Beans.isPrimitive(aVal)) {
                    theBuilder.addProperty(theProperty, toLiteral(aVal, getPropertyAnnotation(thePropertyDescriptor)));
                }
                else {
                    theBuilder.addProperty(theProperty, write(aVal));
                }
            }
        }

    }

    private IRI enumToURI(final Enum theEnum) {
        try {
            final Iri aAnnotation = theEnum.getClass().getField(theEnum.name()).getAnnotation(Iri.class);

            if (aAnnotation != null) {
                return iri(aAnnotation.value());
            } else {
                return mValueFactory.createIRI(mDefaultNamespace, theEnum.name());
            }
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Field not found for enum " + theEnum.name() + " in " + theEnum.getClass().getName(), e);
        }
    }

    private boolean serializeCollectionsAsRDFList(final PropertyDescriptor thePropertyDescriptor) {
        if (mMappingOptions.is(MappingOptions.SERIALIZE_COLLECTIONS_AS_LISTS)) {
            return true;
        }

        RdfProperty aProperty = getPropertyAnnotation(thePropertyDescriptor);

        return aProperty != null && aProperty.isList();
    }

    private IRI getType(final Object theObj) {
        return getType(theObj.getClass());
    }

    private IRI getType(final Class<?> theClass) {
        IRI aType = mMappings.inverse().get(theClass);

        if (aType != null) {
            return aType;
        }

        RdfsClass aClass = theClass.getAnnotation(RdfsClass.class);

        if (aClass != null) {
            return iri(aClass.value());
        }

        return null;
    }

    private Object valueToObject(final Value theValue, final Model theGraph, final PropertyDescriptor theDescriptor) {
        if (theValue instanceof Literal) {
            return handleLiteral(theValue, theDescriptor);
        }
        else if (theDescriptor != null && Enum.class.isAssignableFrom(theDescriptor.getPropertyType())) {
            return handleEnum(theValue, theDescriptor);
        }
        else {
            Resource aResource = (Resource) theValue;

            final Class aClass = pinpointClass(theGraph, aResource, theDescriptor);

            RDFCodec aCodec = mCodecs.get(aClass);
            if (aCodec != null) {
                return aCodec.readValue(theGraph, aResource);
            }
            else {
                return readValue(theGraph, aClass, aResource);
            }
        }
    }

    private Object handleLiteral(final Value theValue, final PropertyDescriptor theDescriptor) {
        final Literal aLit = (Literal) theValue;

        final IRI aDatatype = aLit.getDatatype() != null ? aLit.getDatatype() : null;

        if (aDatatype == null || XMLSchema.STRING.equals(aDatatype) || RDFS.LITERAL.equals(aDatatype)) {
            String aStr = aLit.getLabel();

            if (theDescriptor != null && Character.TYPE.isAssignableFrom(theDescriptor.getPropertyType())) {
                if (aStr.length() == 1) {
                    return aStr.charAt(0);
                }
                else {
                    throw new RDFMappingException("Bean type is char, but value is a a string.");
                }
            }
            else {
                return aStr;
            }
        }
        else if (XMLSchema.BOOLEAN.equals(aDatatype)) {
            return Boolean.valueOf(aLit.getLabel());
        }
        else if (INTEGER_TYPES.contains(aDatatype)) {
            return Integer.parseInt(aLit.getLabel());
        }
        else if (LONG_TYPES.contains(aDatatype)) {
            return Long.parseLong(aLit.getLabel());
        }
        else if (XMLSchema.DOUBLE.equals(aDatatype)) {
            return Double.valueOf(aLit.getLabel());
        }
        else if (FLOAT_TYPES.contains(aDatatype)) {
            return Float.valueOf(aLit.getLabel());
        }
        else if (SHORT_TYPES.contains(aDatatype)) {
            return Short.valueOf(aLit.getLabel());
        }
        else if (BYTE_TYPES.contains(aDatatype)) {
            return Byte.valueOf(aLit.getLabel());
        }
        else if (XMLSchema.ANYURI.equals(aDatatype)) {
            try {
                return new java.net.URI(aLit.getLabel());
            }
            catch (URISyntaxException e) {
                LOGGER.warn("URI syntax exception converting literal value which is not a valid URI {} ", aLit.getLabel());
                return null;
            }
        }
        else if (XMLSchema.DATE.equals(aDatatype) || XMLSchema.DATETIME.equals(aDatatype)) {
            return Dates2.asDate(aLit.getLabel());
        }
        else if (XMLSchema.TIME.equals(aDatatype)) {
            return new Date(Long.parseLong(aLit.getLabel()));
        }
        else {
            throw new RuntimeException("Unsupported or unknown literal datatype: " + aLit);
        }
    }
    private Object handleEnum(final Value theValue, final PropertyDescriptor theDescriptor) {
        IRI aURI = (IRI) theValue;
        Object[] aEnums = theDescriptor.getPropertyType().getEnumConstants();
        for (Object aObj : aEnums) {
            if (((Enum) aObj).name().equals(aURI.getLocalName())) {
                return aObj;
            }
        }

        for (Field aField : theDescriptor.getPropertyType().getFields()) {
            Iri aAnnotation = aField.getAnnotation(Iri.class);
            if (aAnnotation != null && aURI.equals(iri(aAnnotation.value()))) {
                for (Object aObj : aEnums) {
                    if (((Enum) aObj).name().equals(aField.getName())) {
                        return aObj;
                    }
                }

                // if the uri in the Iri annotation equals the value we're converting, but there was no field
                // match, something bad has happened
                throw new RDFMappingException("Expected enum value not found");
            }
        }

        LOGGER.info("{} maps to the enum {}, but does not correspond to any of the values of the enum.",
                aURI, theDescriptor.getPropertyType());

        return null;
    }

    private Class pinpointClass(final Model theGraph, final Resource theResource, final PropertyDescriptor theDescriptor) {
        Class aClass = theDescriptor.getPropertyType();

        if (Collection.class.isAssignableFrom(aClass)) {
            // if the field we're assigning from is a collection, try and figure out the type of the thing
            // we're creating from the collection

            Type[] aTypes = null;

            if (theDescriptor.getReadMethod().getGenericParameterTypes().length > 0) {
                // should this be the return type? eg new Type[] { theDescriptor.getReadMethod().getGenericReturnType() };
                aTypes = theDescriptor.getReadMethod().getGenericParameterTypes();
            } else if (theDescriptor.getWriteMethod().getGenericParameterTypes().length > 0) {
                aTypes = theDescriptor.getWriteMethod().getGenericParameterTypes();
            }

            if (aTypes != null && aTypes.length >= 1) {
                // first type argument to a collection is usually the one we care most about
                if (aTypes[0] instanceof ParameterizedType && ((ParameterizedType) aTypes[0]).getActualTypeArguments().length > 0) {
                    Type aType = ((ParameterizedType) aTypes[0]).getActualTypeArguments()[0];

                    if (aType instanceof Class) {
                        aClass = (Class) aType;
                    } else if (aType instanceof WildcardTypeImpl) {
                        WildcardTypeImpl aWildcard = (WildcardTypeImpl) aType;
                        // trying to suss out super v extends w/o resorting to string munging.
                        if (aWildcard.getLowerBounds().length == 0 && aWildcard.getUpperBounds().length > 0) {
                            // no lower bounds afaik indicates ? extends Foo
                            aClass = ((Class) aWildcard.getUpperBounds()[0]);
                        } else if (aWildcard.getLowerBounds().length > 0) {
                            // lower & upper bounds I believe indicates something of the form Foo super Bar
                            aClass = ((Class) aWildcard.getLowerBounds()[0]);
                        } else {
                            // shoot, we'll try the string hack that Adrian posted on the mailing list.
                            try {
                                aClass = Class.forName(aType.toString().split(" ")[2].substring(0, aTypes[0].toString().split(" ")[2].length() - 1));
                            } catch (Exception e) {
                                // everything has failed, let aClass be the default (theClass) and hope for the best
                            }
                        }
                    } else {
                        // punt? wtf else could it be?
                        try {
                            aClass = Class.forName(aType.toString());
                        } catch (ClassNotFoundException e) {
                            // oh well, we did the best we can
                        }
                    }
                } else if (aTypes[0] instanceof Class) {
                    aClass = (Class) aTypes[0];
                }
            } else {
                LOGGER.info("Could not find type for collection %s", aClass);
            }
        } else if (!Classes.isInstantiable(aClass) || !Classes.hasDefaultConstructor(aClass)) {

            Class<?> aCurr = null;
            final Iterable<Resource> aRdfTypes = Models2.getTypes(theGraph, theResource);
            for (Resource aType : aRdfTypes) {
                Class<?> aMappedClass = mMappings.get(aType);
                if (aMappedClass != null) {
                    if (aCurr == null) {
                        aCurr = aMappedClass;
                    } else if (aCurr.isAssignableFrom(aMappedClass)) {
                        // we want the most specific class, that's likely to be what's instantiable
                        aCurr = aMappedClass;
                    }
                }
            }

            if (aCurr != null) {
                aClass = aCurr;
            }
        }

        return aClass;
    }


    /**
     * Converts the given object to a Value using the specified RDF property annotation.
     * The method uses the datatype specified in the annotation to create an IRI,
     * or the type of the object to create a literal value.
     *
     * @param theObj the object to be converted to a Value.
     * @param theAnnotation the RDF property annotation that provides additional
     *                      information for creating the Value.
     * @return the created Value, or null if an IRI cannot be created using the
     *         annotation's datatype.
     * @throws RDFMappingException if the object type is unsupported.
     */
    private Value toLiteral(final Object theObj, final RdfProperty theAnnotation) {
        if (theAnnotation != null && !Strings.isNullOrEmpty(theAnnotation.datatype())) {
            final IRI aURI = iri(theAnnotation.datatype());

            if (aURI == null) {
                return null;
            }

            return mValueFactory.createLiteral(theObj.toString(), aURI);
        } else if (theObj instanceof Boolean) {
            return mValueFactory.createLiteral((Boolean) theObj);
        } else if (theObj instanceof Integer) {
            return mValueFactory.createLiteral(((Integer) theObj).intValue());
        } else if (theObj instanceof Long) {
            return mValueFactory.createLiteral(((Long) theObj).longValue());
        } else if (theObj instanceof Short) {
            return mValueFactory.createLiteral(((Short) theObj).shortValue());
        } else if (theObj instanceof Double) {
            return mValueFactory.createLiteral((Double) theObj);
        } else if (theObj instanceof Float) {
            return mValueFactory.createLiteral(((Float) theObj).floatValue());
        } else if (theObj instanceof Date) {
            return mValueFactory.createLiteral(Dates2.datetimeISO(((Date) theObj)));
        } else if (theObj instanceof String) {
            if (theAnnotation != null && !theAnnotation.language().equals("")) {
                return mValueFactory.createLiteral((String) theObj, theAnnotation.language());
            } else {
                return mValueFactory.createLiteral((String) theObj, XMLSchema.STRING);
            }
        } else if (theObj instanceof Character) {
            return mValueFactory.createLiteral(String.valueOf(theObj), XMLSchema.STRING);
        } else if (theObj instanceof java.net.URI) {
            return mValueFactory.createLiteral(theObj.toString(), XMLSchema.ANYURI);
        }

        throw new RDFMappingException("Unknown or unsupported primitive type: " + theObj.getClass().getName());
    }

    private RdfProperty getPropertyAnnotation(final PropertyDescriptor thePropertyDescriptor) {
        Method aMethod = null;

        if (thePropertyDescriptor == null) {
            return null;
        }

        if (Methods.annotated(RdfProperty.class).test(thePropertyDescriptor.getReadMethod())) {
            aMethod = thePropertyDescriptor.getReadMethod();
        } else if (Methods.annotated(RdfProperty.class).test(thePropertyDescriptor.getWriteMethod())) {
            aMethod = thePropertyDescriptor.getWriteMethod();
        }

        if (aMethod == null) {
            return null;
        } else {
            return aMethod.getAnnotation(RdfProperty.class);
        }
    }

    private IRI getProperty(final PropertyDescriptor thePropertyDescriptor) {
        final RdfProperty aAnnotation = getPropertyAnnotation(thePropertyDescriptor);

        if (aAnnotation == null || Strings.isNullOrEmpty(aAnnotation.value())) {
            return mValueFactory.createIRI(mDefaultNamespace + thePropertyDescriptor.getName());
        } else {
            return iri(aAnnotation.value());
        }
    }

    /**
     * Expand the URI from a QName, if applicable, returning the URI
     *
     * @param theURI the uri or qname
     * @return the uri, qname expanded into a uri, or null if the uri/qname is not valid or is null
     * @throws RDFMappingException if {@link MappingOptions#IGNORE_INVALID_ANNOTATIONS} is true and the URI/qname
     *                             is not valid.
     */
    private IRI iri(final String theURI) {
        try {
            if (Strings.isNullOrEmpty(theURI)) {
                return null;
            }

            return mValueFactory.createIRI(expand(theURI));
        } catch (IllegalArgumentException e) {
            final String aMsg = String.format("An invalid uri \"%s\" was used, ignoring property with annotation", theURI);

            if (mMappingOptions.is(MappingOptions.IGNORE_INVALID_ANNOTATIONS)) {
                LOGGER.info(aMsg);
                return null;
            } else {
                throw new RDFMappingException(aMsg);
            }
        }
    }

    /**
     * Get or generate an rdf:ID for the given object
     *
     * @param theT the object
     * @return the rdf:ID
     */
    private <T> Resource id(final T theT) {
        if (theT instanceof Identifiable) {
            Identifiable aIdentifiable = (Identifiable) theT;

            if (aIdentifiable.id() != null) {
                return aIdentifiable.id();
            }
        }

        final Iterable<String> aProps = () -> StreamSupport.stream(Beans.getDeclaredMethods(theT.getClass()).spliterator(), false)
                .filter(Methods.annotated(RdfId.class))
                .map(Methods.property())
                .iterator();

        // Sort the properties so they're always iterated over in the same order.  since the hash is sensitive
        // to iteration order, the same inputs but in a different order yields a different hashed value, and thus
        // a different ID, even though it's the *same* resource.
        final List<String> aSorted = Ordering.natural().sortedCopy(aProps);

        Resource aId = null;
        if (!Iterables.isEmpty(aSorted)) {
            Hasher aFunc = Hashing.md5().newHasher();
            for (String aProp : aSorted) {
                try {
                    final Object aValue = PropertyUtils.getProperty(theT, aProp);

                    if (aValue == null) {
                        continue;
                    }

                    aFunc.putString(aValue.toString(), Charsets.UTF_8);
                } catch (Exception e) {
                    Throwables.propagateIfInstanceOf(e, RDFMappingException.class);
                    throw new RDFMappingException(e);
                }
            }

            aId = mValueFactory.createIRI(mDefaultNamespace + aFunc.hash().toString());
        }

        for (Map.Entry<Class<?>, Function<Object, Resource>> aEntry : mIdFunctions.entrySet()) {
            if (aEntry.getKey().isAssignableFrom(theT.getClass())) {
                aId = aEntry.getValue().apply(theT);
                break;
            }
        }

        if (aId == null && mMappingOptions.is(MappingOptions.REQUIRE_IDS)) {
            throw new UnidentifiableObjectException(String.format("No identifier was found for %s!  The instance should " +
                    "implement Identifiable, have one or more properties " +
                    "annotated with @RdfId, or have an id function provided " +
                    "to the mapper.", theT));
        } else {
            if (aId == null) {
                aId = mValueFactory.createIRI(mDefaultNamespace + Hashing.md5().newHasher()
                        .putString(theT.toString(), Charsets.UTF_8)
                        .hash().toString());
            }

            if (theT instanceof Identifiable) {
                ((Identifiable) theT).id(aId);
            }

            return aId;
        }
    }

    /**
     * Builder object for creating an {@link RDFMapper}
     *
     * @author Michael Grove
     * @version 1.0
     * @since 1.0
     */
    public static class Builder {
        private static final Pattern PREFIX_REGEX = Pattern.compile("^([a-z]|[A-Z]|_){1}(\\w|-|\\.)*$");

        private final Map<IRI, Class> mMappings = Maps.newHashMap();

        private final Map<Class<?>, Function<Object, Resource>> mIdFunctions = Maps.newHashMap();
        private final Options mOptions = Options.combine(MappingOptions.DEFAULTS);
        private final Map<String, String> mNamespaces = Maps.newHashMap();
        private final Map<Class<?>, RDFCodec<?>> mCodecs = Maps.newHashMap();
        private ValueFactory mValueFactory = SimpleValueFactory.getInstance();
        private CollectionFactory mCollectionFactory = new DefaultCollectionFactory();
        private MapFactory mMapFactory = new DefaultMapFactory();

        public Builder() {
            mNamespaces.put("", DEFAULT_NAMESPACE);

            mNamespaces.put("dc", Namespaces.DC);
            mNamespaces.put("foaf", Namespaces.FOAF);
            mNamespaces.put("owl", Namespaces.OWL);
            mNamespaces.put("rdf", Namespaces.RDF);
            mNamespaces.put("rdfs", Namespaces.RDFS);
            mNamespaces.put("skos", Namespaces.SKOS);
            mNamespaces.put("xsd", Namespaces.XSD);
        }

        /**
         * Specify the factory to use for creating instances of {@link Collection}
         *
         * @param theFactory the factory
         * @return this builder
         */
        public Builder collectionFactory(final CollectionFactory theFactory) {
            mCollectionFactory = theFactory;
            return this;
        }

        /**
         * Specify the factory to use for creating instances of {@link Map}
         *
         * @param theMapFactory the factory
         * @return this builder
         */
        public Builder mapFactory(final MapFactory theMapFactory) {
            mMapFactory = theMapFactory;
            return this;
        }

        /**
         * Specify the {@link ValueFactory} used by the mapper
         *
         * @param theFactory the ValueFactory
         * @return this builder
         */
        public Builder valueFactory(final ValueFactory theFactory) {
            mValueFactory = theFactory;
            return this;
        }

        /**
         * Add a new namespace to the builder
         *
         * @param thePrefix    the namespace prefix
         * @param theNamespace the namespace URI
         * @return this builder
         */
        public Builder namespace(final String thePrefix, final String theNamespace) {
            try {
                new java.net.URI(theNamespace);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("namespace must be a valid URI", e);
            }

            if (thePrefix.length() > 0 && !PREFIX_REGEX.matcher(thePrefix).find()) {
                throw new IllegalArgumentException(thePrefix + " is not a valid namespace prefix");
            }

            mNamespaces.put(thePrefix, theNamespace);

            return this;
        }

        /**
         * Add a new namespace to the mapper
         *
         * @param theNamespace the namespace to add
         * @return this builder
         */
        public Builder namespace(final Namespace theNamespace) {
            return namespace(theNamespace.getPrefix(), theNamespace.getPrefix());
        }

        /**
         * Specify a list of namespaces to be used by the mapper
         *
         * @param theNamespaces the namespaces
         * @return this builder
         */
        public Builder namespaces(final Iterable<Namespace> theNamespaces) {
            for (Namespace aNamespace : theNamespaces) {
                namespace(aNamespace);
            }

            return this;
        }

        /**
         * Set an option
         *
         * @param theOption the option
         * @param theValue  the value
         * @return this object
         * @see MappingOptions
         */
        public <T> Builder set(final Option<T> theOption, final T theValue) {
            mOptions.set(theOption, theValue);
            return this;
        }

        /**
         * Specify the provided type corresponds to instances of the given Java class.
         *
         * @param theClassURI the rdf:type URI
         * @param theClass    the corresponding Java class
         * @return this builder
         */
        public Builder map(final IRI theClassURI, final Class theClass) {
            Preconditions.checkNotNull(theClassURI);
            Preconditions.checkNotNull(theClass);

            if (mMappings.containsKey(theClassURI)) {
                throw new IllegalStateException(String.format("%s is already mapped to %s",
                        theClassURI, mMappings.get(theClassURI)));
            }

            mMappings.put(theClassURI, theClass);

            return this;
        }

        /**
         * Add a codec to the mapper
         *
         * @param theClass the class mapped by the codec
         * @param theCodec the codec
         * @param <T>      the class type
         * @return this object
         */
        public <T> Builder codec(final Class<T> theClass, final RDFCodec<T> theCodec) {
            mCodecs.put(theClass, theCodec);
            return this;
        }

        /**
         * Create the mapper
         *
         * @return the new mapper
         */
        public RDFMapper build() {
            return new RDFMapper(mMappings, mIdFunctions, mValueFactory, mNamespaces, mCollectionFactory,
                    mMapFactory, mCodecs, mOptions);
        }
    }




}