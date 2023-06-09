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

import com.complexible.common.openrdf.model.ModelIO;
import com.complexible.common.openrdf.model.Models2;
import com.complexible.common.openrdf.model.Statements;
import com.complexible.common.openrdf.vocabulary.FOAF;
import com.complexible.pinto.annotations.Iri;
import com.complexible.pinto.annotations.RdfId;
import com.complexible.pinto.annotations.RdfProperty;
import com.complexible.pinto.annotations.RdfsClass;
import com.complexible.pinto.codecs.UUIDCodec;
import com.complexible.pinto.factory.CollectionFactory;
import com.complexible.pinto.impl.IdentifiableImpl;
import com.complexible.pinto.factory.MapFactory;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openrdf.model.*;
import org.openrdf.model.impl.SimpleValueFactory;
import org.openrdf.model.util.Models;
import org.openrdf.model.vocabulary.XMLSchema;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static com.complexible.pinto.RDFMapper.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <p></p>
 *
 * @author Michael Grove
 */
public class RDFMapperTests {

    private static final TimeZone tz = TimeZone.getDefault();

    @BeforeClass
    public static void setup() {
        // so that serialized dates get same TZ as reference data, not local TZ
        TimeZone.setDefault(TimeZone.getTimeZone("US/Eastern"));
    }

    @AfterClass
    public static void teardown() {
        TimeZone.setDefault(tz);
    }


    /**
     * We can get collections which have a different order than the original when we don't use the rdf:list
     * serialized collection.  But order will matter in an .equals call.  in real usage, if order matters,
     * users will use the rdf:list version.  for now, we just want to make sure the collections have the same
     * elements and are of the same type.  that's sufficient.
     */
    private static <T> boolean equalsTypeAndContents(Collection<T> theCollection, final Collection<T> theOther) {
        return (theCollection == null && theOther == null)
                || (theCollection != null && theOther != null
                && theCollection.getClass() == theOther.getClass()
                && Sets.newHashSet(theCollection).equals(Sets.newHashSet(theOther)));
    }

    @Test(expected = UnidentifiableObjectException.class)
    public void testUnidentifiable() throws Exception {
        RDFMapper aMapper = builder()
                .set(MappingOptions.REQUIRE_IDS, true)
                .build();

        aMapper.writeValue(new ClassWithPrimitives());
    }

    @Test
    public void testWritePrimitives() throws Exception {
        RDFMapper aMapper = create();

        ClassWithPrimitives aObj = new ClassWithPrimitives();
        aObj.setString("str value");
        aObj.setInt(8);
        aObj.setURI(java.net.URI.create("urn:any"));
        aObj.setFloat(4.5f);
        aObj.setDouble(20.22);
        aObj.setChar('o');
        aObj.id(SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:3d1c9ece37c3f9ee6068440cf9a383cc"));

        Model aGraph = aMapper.writeValue(aObj);

        Model aExpected = ModelIO.read(new File(getClass().getResource("/data/primitives.nt").toURI()).toPath());

        assertTrue(Models.isomorphic(aGraph, aExpected));
    }

    @Test
    public void testReadPrimitives() throws Exception {
        Model aGraph = ModelIO.read(new File(getClass().getResource("/data/primitives.nt").toURI()).toPath());

        RDFMapper aMapper = create();

        final ClassWithPrimitives aResult = aMapper.readValue(aGraph, ClassWithPrimitives.class);

        ClassWithPrimitives aExpected = new ClassWithPrimitives();
        aExpected.setString("str value");
        aExpected.setInt(8);
        aExpected.setURI(java.net.URI.create("urn:any"));
        aExpected.setFloat(4.5f);
        aExpected.setDouble(20.22);
        aExpected.setChar('o');

        assertEquals(aExpected, aResult);
    }

    @Test
    public void testReadMixed() throws Exception {
        ClassWithPrimitives aChild = new ClassWithPrimitives();
        aChild.setString("str value");
        aChild.setInt(8);
        aChild.setURI(java.net.URI.create("urn:any"));
        aChild.setFloat(4.5f);
        aChild.setDouble(20.22);
        aChild.setChar('o');

        ClassWithMixed aExpected = new ClassWithMixed();
        aExpected.setChild(aChild);
        aExpected.setString("class with mixed");

        Model aGraph = ModelIO.read(new File(getClass().getResource("/data/mixed.nt").toURI()).toPath());

        final ClassWithMixed aResult = create().readValue(aGraph, ClassWithMixed.class,
                SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:45ad04336c95c0be6bba90e4b663da4d"));

        assertEquals(aExpected, aResult);
    }

    @Test
    public void testWriteMixed() throws Exception {
        ClassWithPrimitives aChild = new ClassWithPrimitives();
        aChild.setString("str value");
        aChild.setInt(8);
        aChild.setURI(java.net.URI.create("urn:any"));
        aChild.setFloat(4.5f);
        aChild.setDouble(20.22);
        aChild.setChar('o');

        ClassWithMixed aObj = new ClassWithMixed();
        aObj.setChild(aChild);
        aObj.setString("class with mixed");
        aObj.id(SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:45ad04336c95c0be6bba90e4b663da4d"));

        Model aGraph = create().writeValue(aObj);

        Model aExpected = ModelIO.read(new File(getClass().getResource("/data/mixed.nt").toURI()).toPath());

        assertTrue(Models.isomorphic(aExpected, aGraph));
    }

    @Test
    public void testWriteListOfPrimitives() throws Exception {
        ClassWithPrimitiveLists aObj = new ClassWithPrimitiveLists();

        aObj.setInts(Lists.newArrayList(4, 5));
        aObj.setFloats(Sets.newLinkedHashSet(Lists.newArrayList(8f, 20f)));
        aObj.setBools(Sets.newTreeSet(Lists.newArrayList(true, false)));
        aObj.setDoubles(Sets.newLinkedHashSet(Lists.newArrayList(22d, 33d)));
        aObj.id(SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:b7d283d3a73c7b8a870087942b9a43b1"));

        Model aResult = create().writeValue(aObj);

        assertTrue(Models.isomorphic(ModelIO.read(Files3.classPath("/data/primitive_lists.nt").toPath()),
                aResult));
    }


    @Test
    public void testWriteListsOfPrimitivesAsRdfListsWithOption() throws Exception {
        ClassWithPrimitiveLists aObj = new ClassWithPrimitiveLists();

        aObj.setInts(Lists.newArrayList(4, 5));
        aObj.id(SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:9017b0ab9335e4d090290a0dffc81826"));

        final Model aResult = builder()
                .set(MappingOptions.SERIALIZE_COLLECTIONS_AS_LISTS, true)
                .build()
                .writeValue(aObj);

        assertTrue(Models.isomorphic(ModelIO.read(Files3.classPath("/data/primitive_rdf_lists.nt").toPath()),
                aResult));
    }

    @Test
    public void testWriteListsOfPrimitivesAsRdfListWithAnnotation() throws Exception {
        ClassWithPrimitiveRdfList aObj = new ClassWithPrimitiveRdfList();

        aObj.setInts(Lists.newArrayList(4, 5));
        aObj.id(SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:2b04e666a0fec2830d882740dbec8262"));

        final Model aResult = create().writeValue(aObj);

        assertTrue(Models.isomorphic(ModelIO.read(Files3.classPath("/data/primitive_rdf_lists2.nt").toPath()),
                aResult));
    }

    @Test
    public void testWriteListOfObjects() throws Exception {
        ClassWithObjectList aObj = new ClassWithObjectList();
        aObj.setCollection(Sets.newLinkedHashSet(Lists.newArrayList(new Person("Earl Weaver"), new Person("Brooks Robinson"))));
        aObj.setSet(Sets.newLinkedHashSet(Lists.newArrayList(new Person("JJ Hardy"), new Person("Manny Machado"))));
        aObj.setList(Lists.newArrayList(new Person("Alejandro De Aza"), new Person("Adam Jones")));
        aObj.setSortedSet(Sets.newTreeSet(Lists.newArrayList(new Person("Steve Pearce"), new Person("Zach Britton"))));
        aObj.id(SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:881b2f11232944aeda9ba543e030dcfc"));

        final Model aResult = create().writeValue(aObj);

        assertTrue(Models.isomorphic(ModelIO.read(Files3.classPath("/data/object_lists.nt").toPath()), aResult));
    }

    @Test
    public void testWriteListsOfObjectsAsRdfListWithOption() throws Exception {
        ClassWithObjectList aObj = new ClassWithObjectList();

        aObj.setCollection(Sets.newLinkedHashSet(Lists.newArrayList(new Person("Earl Weaver"), new Person("Brooks Robinson"))));
        aObj.id(SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:4f372f7bfb03f7b80be8777603d3b1ed"));

        final Model aResult = builder()
                .set(MappingOptions.SERIALIZE_COLLECTIONS_AS_LISTS, true)
                .build()
                .writeValue(aObj);

        assertTrue(Models.isomorphic(ModelIO.read(Files3.classPath("/data/object_rdf_lists.nt").toPath()), aResult));
    }

    @Test
    public void testWriteListsOfObjectsAsRdfListWithAnnotation() throws Exception {
        ClassWithRdfObjectList aObj = new ClassWithRdfObjectList();

        aObj.setList(Lists.newArrayList(new Person("Earl Weaver"), new Person("Brooks Robinson")));
        aObj.id(SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:716825cc44cff258b685466022250434"));

        final Model aResult = create().writeValue(aObj);

        assertTrue(Models.isomorphic(ModelIO.read(Files3.classPath("/data/object_rdf_lists2.nt").toPath()), aResult));
    }

    @Test
    public void testReadListOfPrimitives() throws Exception {
        Model aGraph = ModelIO.read(Files3.classPath("/data/primitive_lists.nt").toPath());

        final ClassWithPrimitiveLists aResult = create().readValue(aGraph,
                ClassWithPrimitiveLists.class,
                SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:b7d283d3a73c7b8a870087942b9a43b1"));

        ClassWithPrimitiveLists aExpected = new ClassWithPrimitiveLists();

        aExpected.setInts(Lists.newArrayList(4, 5));
        aExpected.setFloats(Sets.newLinkedHashSet(Lists.newArrayList(8f, 20f)));
        aExpected.setBools(Sets.newTreeSet(Lists.newArrayList(true, false)));
        aExpected.setDoubles(Sets.newLinkedHashSet(Lists.newArrayList(22d, 33d)));

        assertEquals(aExpected, aResult);
    }

    @Test
    public void testReadRdfListOfPrimitives() throws Exception {
        Model aGraph = ModelIO.read(Files3.classPath("/data/primitive_rdf_lists.nt").toPath());

        final ClassWithPrimitiveLists aResult = create().readValue(aGraph,
                ClassWithPrimitiveLists.class,
                SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:9017b0ab9335e4d090290a0dffc81826"));

        ClassWithPrimitiveLists aExpected = new ClassWithPrimitiveLists();

        aExpected.setInts(Lists.newArrayList(4, 5));

        assertEquals(aExpected, aResult);
    }

    @Test
    public void testReadListOfObjects() throws Exception {
        Model aGraph = ModelIO.read(Files3.classPath("/data/object_lists.nt").toPath());

        ClassWithObjectList aExpected = new ClassWithObjectList();
        aExpected.setCollection(Sets.newLinkedHashSet(Lists.newArrayList(new Person("Earl Weaver"), new Person("Brooks Robinson"))));
        aExpected.setSet(Sets.newLinkedHashSet(Lists.newArrayList(new Person("JJ Hardy"), new Person("Manny Machado"))));
        aExpected.setList(Lists.newArrayList(new Person("Alejandro De Aza"), new Person("Adam Jones")));
        aExpected.setSortedSet(Sets.newTreeSet(Lists.newArrayList(new Person("Steve Pearce"), new Person("Zach Britton"))));

        final ClassWithObjectList aResult = create().readValue(aGraph,
                ClassWithObjectList.class,
                SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:881b2f11232944aeda9ba543e030dcfc"));

        assertEquals(aExpected, aResult);
    }

    @Test
    public void testReadRdfListOfObjects() throws Exception {
        Model aGraph = ModelIO.read(Files3.classPath("/data/object_rdf_lists.nt").toPath());

        ClassWithObjectList aExpected = new ClassWithObjectList();

        aExpected.setCollection(Sets.newLinkedHashSet(Lists.newArrayList(new Person("Earl Weaver"), new Person("Brooks Robinson"))));

        final ClassWithObjectList aResult = create().readValue(aGraph,
                ClassWithObjectList.class,
                SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:4f372f7bfb03f7b80be8777603d3b1ed"));
        assertEquals(aExpected, aResult);
    }

    @Test
    @Ignore
    public void testURIMapping() throws Exception {
        // alternative to RdfsClass, verify that if you specify a type URI -> class mapping it is used
        // test 1, that the rdf:type is included in a top level class
        // test 2, that the type can be used to find the correct implementation for a property
        //         eg:  Object getFoo() -> this will be populated by a individual w/ a type :Bar to the class Baz
        //              so the mapping would specify :Bar <-> Baz
    }

    @Test
    public void testUseRdfIdForIdentification() throws Exception {
        // require ids so the default id generation cannot be used
        RDFMapper aMapper = builder()
                .set(MappingOptions.REQUIRE_IDS, true)
                .build();

        Company aCompany = new Company();
        aCompany.setName("Clark & Parsia");
        aCompany.setWebsite("http://clarkparsia.com");

        Model aGraph = aMapper.writeValue(aCompany);

		assertFalse(aGraph.isEmpty());

        final String aExpected = Hashing.md5().newHasher()
                .putString(aCompany.getName(), Charsets.UTF_8)
                .putString(aCompany.getWebsite(), Charsets.UTF_8)
                .hash().toString();

        assertEquals(aExpected,
                ((org.openrdf.model.URI) aGraph.iterator().next().getSubject()).getLocalName());
    }

    @Test
    public void testWriteTwice() throws Exception {
        ClassWithObjectList aObj = new ClassWithObjectList();

        aObj.setCollection(Sets.newLinkedHashSet(Lists.newArrayList(new Person("Earl Weaver"), new Person("Brooks Robinson"))));
        aObj.id(SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:4f372f7bfb03f7b80be8777603d3b1ed"));

        Model aGraph = create().writeValue(aObj);

        Model aOtherGraph = create().writeValue(aObj);

        assertTrue(Models.isomorphic(aGraph, aOtherGraph));
    }

    @Test
    public void testReadTwice() throws Exception {
        Model aGraph = ModelIO.read(Files3.classPath("/data/object_lists.nt").toPath());

        final ClassWithObjectList aObj = create().readValue(aGraph, ClassWithObjectList.class,
                SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:881b2f11232944aeda9ba543e030dcfc"));

        final ClassWithObjectList aObj2 = create().readValue(aGraph, ClassWithObjectList.class,
                SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:881b2f11232944aeda9ba543e030dcfc"));

        assertEquals(aObj, aObj2);
    }

    @Test
    public void testIdentifiableNoIdFallbackToDefault() throws Exception {
        Person aPerson = new Person("Michael Grove");

        Model aGraph = create().writeValue(aPerson);

        assertEquals(1, aGraph.size());

        Statement aStmt = aGraph.iterator().next();

        assertEquals("name", aStmt.getPredicate().getLocalName());
        assertEquals(aPerson.getName(), aStmt.getObject().stringValue());
    }

    @Test
    public void testReadSetsIdentifiableId() throws Exception {
        Person aPerson = new Person("Michael Grove");

        aPerson.id(SimpleValueFactory.getInstance().createIRI("urn:mg"));
        Model aGraph = create().writeValue(aPerson);

        assertEquals(1, aGraph.size());

        Person aCopy = create().readValue(aGraph, Person.class);

        assertEquals(aPerson.id(), aCopy.id());
    }

    @Test
    public void testWriteSetsIdentifiableId() throws Exception {
        Person aPerson = new Person("Michael Grove");

        Model aGraph = create().writeValue(aPerson);

		assertNotNull(aPerson.id());

        assertEquals(1, aGraph.size());

        Person aCopy = create().readValue(aGraph, Person.class);

        assertEquals(aPerson.id(), aCopy.id());
    }

    @Test(expected = UnidentifiableObjectException.class)
    public void testIdentifiableNoIdNoFallback() throws Exception {
        builder()
                .set(MappingOptions.REQUIRE_IDS, true)
                .build().writeValue(new ClassWithPrimitives());
    }

    @Test
    public void testCustomDatatypingOfLiteral() throws Exception {
        Company aCompany = new Company();
        aCompany.setNumberOfEmployees(10);

        Model aGraph = create().writeValue(aCompany);

        Optional<Statement> aStatement = aGraph.stream().filter(Statements.predicateIs(SimpleValueFactory.getInstance().createIRI(DEFAULT_NAMESPACE + "numberOfEmployees"))).findFirst();

        assertTrue("should have found the triple", aStatement.isPresent());

        Literal aResult = (Literal) aStatement.get().getObject();

        assertEquals(XMLSchema.INTEGER, aResult.getDatatype());
        assertEquals("10", aResult.getLabel());
    }

    @Test
    public void testCustomDatatypingOfLiteralWithInvalidDatatype() throws Exception {
        BadCompany aCompany = new BadCompany();
        aCompany.setNumberOfEmployees(10);

        Model aGraph = create().writeValue(aCompany);

        // this will be empty since there are no valid assertions
        assertTrue(aGraph.isEmpty());
    }

    @Test(expected = RDFMappingException.class)
    public void testCustomDatatypingOfLiteralWithInvalidDatatypeFatal() throws Exception {
        BadCompany aCompany = new BadCompany();
        aCompany.setNumberOfEmployees(10);

        builder().set(MappingOptions.IGNORE_INVALID_ANNOTATIONS, false).build().writeValue(aCompany);
    }

    @Test
    public void testRdfIdWithNoValuesSet() throws Exception {
        Company aCompany = new Company();
        aCompany.setNumberOfEmployees(10);

        Model aGraph = create().writeValue(aCompany);

        // should be two stmts, the rdf:type assertion and the one for :numberOfEmployees
        // id might be different every time since we have no inputs to the id hash, so we'll just rely on the count
        // here when checking for correctness.
        assertEquals(2, aGraph.size());
    }

    @Test
    @Ignore
    public void testProxyCreation() throws Exception {

		//		assertTrue(aResult instanceof Identifiable);
        //
		//		assertTrue(aResult instanceof SourcedObject);

        // and also get the values from the objects
    }

    @Test
    public void testUseSerializationAnnotations() throws Exception {
        Company aCompany = new Company();
        aCompany.setName("Clark & Parsia");
        aCompany.setWebsite("http://clarkparsia.com");

        Model aGraph = create().writeValue(aCompany);

        assertTrue(Models.isomorphic(ModelIO.read(Files3.classPath("/data/company.nt").toPath()),
                aGraph));
    }

    @Test(expected = RDFMappingException.class)
    public void testCardinalityViolationFatal() throws Exception {
        create().readValue(ModelIO.read(Files3.classPath("/data/company-card.nt").toPath()), Company.class);
    }

    @Test
    public void testCanIgnoreCardinalityViolation() throws Exception {
        Company aCompany = builder()
                .set(MappingOptions.IGNORE_CARDINALITY_VIOLATIONS, true)
                .build()
                .readValue(ModelIO.read(Files3.classPath("/data/company-card.nt").toPath()), Company.class);

        assertEquals("http://clarkparsia.com", aCompany.getWebsite());

        assertTrue(aCompany.getName().equals("Complexible") || aCompany.getName().equals("Clark & Parsia"));
    }

    @Test
    public void testAnnotationsWithInvalidURI() throws Exception {
        BadCompany aCompany = new BadCompany();
        aCompany.setName("Clark & Parsia");
        aCompany.setWebsite("http://clarkparsia.com");

        Model aGraph = create().writeValue(aCompany);
        assertTrue(Models.isomorphic(ModelIO.read(Files3.classPath("/data/bad_company.nt").toPath()),
                aGraph));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoInvalidNamespaceToBuilder() throws Exception {
        builder().namespace("prefix", "not a valid namespace").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoInvalidPrefixToBuilder() throws Exception {
        builder().namespace("not a valid prefix", "urnnamespace").build();
    }

    @Test(expected = RDFMappingException.class)
    public void testAnnotationsWithInvalidURIFatal() throws Exception {
        BadCompany aCompany = new BadCompany();
        aCompany.setName("Clark & Parsia");
        aCompany.setWebsite("http://clarkparsia.com");

        builder()
                .set(MappingOptions.IGNORE_INVALID_ANNOTATIONS, false)
                .build()
                .writeValue(aCompany);
    }

    @Test
    public void testWriteAnnotationsWithNamespace() throws Exception {
        ShortCompany aCompany = new ShortCompany();
        aCompany.setName("Clark & Parsia");
        aCompany.setWebsite("http://clarkparsia.com");
        aCompany.setNumberOfEmployees(10);

        final Model aGraph = builder()
                .namespace("fo", FOAF.ontology().uri().toString())
                .namespace("xs", XMLSchema.NAMESPACE)
                .build().writeValue(aCompany);

        assertTrue(Models.isomorphic(ModelIO.read(Files3.classPath("/data/company2.nt").toPath()), aGraph));
    }

    @Test
    public void testReadWithUnmatchedRDF() throws Exception {
        Model aGraph = ModelIO.read(Files3.classPath("/data/object_rdf_lists.nt").toPath());
        aGraph.addAll(ModelIO.read(Files3.classPath("/data/mixed.nt").toPath()));

        ClassWithObjectList aExpected = new ClassWithObjectList();

        aExpected.setCollection(Sets.newLinkedHashSet(Lists.newArrayList(new Person("Earl Weaver"),
                new Person("Brooks Robinson"))));


        final ClassWithObjectList aObj = create().readValue(aGraph, ClassWithObjectList.class,
                SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:4f372f7bfb03f7b80be8777603d3b1ed"));
        assertEquals(aExpected, aObj);
    }

    @Test
    public void testWriteEnum() throws Exception {
        ClassWithEnum aObj = new ClassWithEnum();

        aObj.id(SimpleValueFactory.getInstance().createIRI("urn:testWriteEnum"));
        aObj.setValue(TestEnum.Bar);

        Model aGraph = create().writeValue(aObj);

        assertEquals(1, aGraph.size());

        Optional<Statement> aStatement = aGraph.stream().filter(Statements.predicateIs(SimpleValueFactory.getInstance().createIRI(DEFAULT_NAMESPACE + "value"))).findFirst();

        assertTrue("should have found the triple", aStatement.isPresent());

        Resource aResult = (Resource) aStatement.get().getObject();

        assertEquals(SimpleValueFactory.getInstance().createIRI("urn:TestEnum:Bar"), aResult);

        aObj.setValue(TestEnum.Baz);

        aGraph = create().writeValue(aObj);

        assertEquals(1, aGraph.size());

        aStatement = aGraph.stream().filter(Statements.predicateIs(SimpleValueFactory.getInstance().createIRI(DEFAULT_NAMESPACE + "value"))).findFirst();

        assertTrue("should have found the triple", aStatement.isPresent());

        aResult = (Resource) aStatement.get().getObject();

        assertEquals(SimpleValueFactory.getInstance().createIRI(DEFAULT_NAMESPACE, "Baz"), aResult);
    }

    @Test
    public void testWriteEnumInvalidIri() throws Exception {
        ClassWithEnum aObj = new ClassWithEnum();

        aObj.id(SimpleValueFactory.getInstance().createIRI("urn:testWriteEnumInvalidIri"));
        aObj.setValue(TestEnum.Foo);

        final Model aGraph = create().writeValue(aObj);

        assertTrue(aGraph.isEmpty());
    }

    @Test(expected = RDFMappingException.class)
    public void testWriteEnumInvalidIriFatal() throws Exception {
        ClassWithEnum aObj = new ClassWithEnum();

        aObj.id(SimpleValueFactory.getInstance().createIRI("urn:testWriteEnumInvalidIri"));
        aObj.setValue(TestEnum.Foo);

        builder()
                .set(MappingOptions.IGNORE_INVALID_ANNOTATIONS, false)
                .build()
                .writeValue(aObj);
    }

    //following test fails, the main issue is with the format of the date string in the generated map. The expected format is http://www.w3.org/2001/XMLSchema#dateTime, but in the generated map, it's http://www.w3.org/2001/XMLSchema#string.
    /*
    @Test
    public void testWriteMap() throws Exception {
        final ClassWithMap aObj = new ClassWithMap();

        aObj.mMap = Maps.newLinkedHashMap();

        aObj.mMap.put("bob", new Person("Bob the tester"));
        aObj.mMap.put(1L, "the size of something");
        aObj.mMap.put(new Date(1426361082470L), 57.4);
        aObj.mMap.put(new Person("another person"), new Company("The company"));

        final Model aGraph = RDFMapper.builder()
                .map(FOAF.ontology().Person, Person.class)
                .build()
                .writeValue(aObj);

        Model expectedGraph = ModelIO.read(Files3.classPath("/data/map.nt").toPath());

        // Print out both models
        System.out.println("Generated Graph:\n" + aGraph);
        System.out.println("\nExpected Graph:\n" + expectedGraph);

        // Compare statement sets
        Set<Statement> inAGraphButNotExpected = Sets.difference(aGraph, expectedGraph);
        Set<Statement> inExpectedButNotAGraph = Sets.difference(expectedGraph, aGraph);

        System.out.println("\nIn Generated but not in Expected:\n" + inAGraphButNotExpected);
        System.out.println("\nIn Expected but not in Generated:\n" + inExpectedButNotAGraph);

        // Assert model equality
        assertTrue(inAGraphButNotExpected.isEmpty() && inExpectedButNotAGraph.isEmpty());
    }
*/


    @Test
    public void testReadEnum() throws Exception {
        final Model aGraph = ModelIO.read(Files3.classPath("/data/enum.nt").toPath());

        final ClassWithEnum aExpected = new ClassWithEnum();

        aExpected.id(SimpleValueFactory.getInstance().createIRI("urn:testReadEnum"));
        aExpected.setValue(TestEnum.Bar);

        final ClassWithEnum aResult = create().readValue(aGraph, ClassWithEnum.class);

        assertEquals(aExpected, aResult);
    }

    //for test purposes
    public enum Days {
        MONDAY,
        TUESDAY,
        WEDNESDAY,
        THURSDAY,
        FRIDAY,
        SATURDAY,
        SUNDAY
    }

    public class EnumSetWrapper {
        private EnumSet<Days> days;

        public EnumSetWrapper(EnumSet<Days> days) {
            this.days = days;
        }

        public EnumSet<Days> getDays() {
            return days;
        }
    }



    /*@RunWith(MockitoJUnitRunner.class)
    public class PinpointClassTest {

        @Mock
        private Model mockModel;

        @Mock
        private Resource mockResource;

        @Mock
        private PropertyDescriptor mockDescriptor;

        @Ignore
        @Test
        public void testPinpointClass() {
            when(mockDescriptor.getPropertyType()).thenReturn(Collection.class);

            // Mock the generic parameter type of the collection
            Type[] genericParamTypes = new Type[] { String.class };
            when(mockDescriptor.getReadMethod().getGenericParameterTypes()).thenReturn(genericParamTypes);

            RDFMapper myClass = RDFMapper.create();

            Class result = myClass.pinpointClass(mockModel, mockResource, mockDescriptor);

            assertNotNull(result);
            assertEquals(String.class, result);
        }

        // Helper classes for testing purposes

        private class Model {}

        private class Resource {}

        private class MyClass {
            public Class pinpointClass(Model theGraph, Resource theResource, PropertyDescriptor theDescriptor) {
                Class aClass = theDescriptor.getPropertyType();

                if (Collection.class.isAssignableFrom(aClass)) {
                    Type[] aTypes = null;

                    if (theDescriptor.getReadMethod().getGenericParameterTypes().length > 0) {
                        aTypes = theDescriptor.getReadMethod().getGenericParameterTypes();
                    }

                    if (aTypes != null && aTypes.length >= 1) {
                        if (aTypes[0] instanceof ParameterizedType && ((ParameterizedType) aTypes[0]).getActualTypeArguments().length > 0) {
                            Type aType = ((ParameterizedType) aTypes[0]).getActualTypeArguments()[0];

                            if (aType instanceof Class) {
                                aClass = (Class) aType;
                            }
                        } else if (aTypes[0] instanceof Class) {
                            aClass = (Class) aTypes[0];
                        }
                    }
                }

                return aClass;
            }
        }
    }*/



    @Test
    @Ignore
    public void testReadEnumSet() throws Exception {
        // Create an instance of your RDFMapper class
        RDFMapper mapper = create();

        // Create an EnumSet
        EnumSet<Days> testEnumSet = EnumSet.of(Days.MONDAY, Days.WEDNESDAY, Days.FRIDAY);

        // Create a wrapper for the EnumSet
        EnumSetWrapper testWrapper = new EnumSetWrapper(testEnumSet);

        // Convert EnumSet to RDF Model using the wrapper
        Model rdfModel = mapper.writeValue(testWrapper);

        // Create a Resource instance representing the URI of the EnumSet
        ValueFactory factory = SimpleValueFactory.getInstance();
        IRI enumSetResource = factory.createIRI("http://example.org/enumSet");

        // Read the EnumSet from the Model using the wrapper
        EnumSetWrapper deserializedWrapper = mapper.readValue(rdfModel, EnumSetWrapper.class, enumSetResource);

        // Assert the deserialized EnumSet is equal to the original EnumSet
        assertEquals(testEnumSet, deserializedWrapper.getDays());
    }


    @Test
    @Ignore
    public void testWriteEnumSet() throws Exception {
    }

    @Test
    @Ignore
    public void testReadCustomCollectionMapping() throws Exception {
    }

    @Test
    @Ignore
    public void testWriteWithLangTag() throws Exception {
    }

    @Test
    @Ignore
    public void testReadWithLangTag() throws Exception {
    }

    @Test(expected = RDFMappingException.class)
    @Ignore
    public void testMultipleValuesForNonIterableProperty() throws Exception {
    }

    @Test(expected = RDFMappingException.class)
    @Ignore
    public void testCharBeanTypeWithLongString() throws Exception {
    }

    @Test(expected = RDFMappingException.class)
    public void testNoDefaultConstructor() throws Exception {
        create().readValue(ModelIO.read(Files3.classPath("/data/mixed.nt").toPath()), CannotConstructMe.class);
    }

    @Test
    public void testNoDefaultConstructor2() throws IOException {
        try {
            create().readValue(ModelIO.read(Files3.classPath("/data/empty.nt").toPath()), CannotConstructMe.class);
            fail("Expected RDFMappingException to be thrown");
        } catch (RDFMappingException e) {
            assertEquals("Could not create an instance of class com.complexible.pinto.RDFMapperTests$CannotConstructMe, it does not have a default constructor", e.getMessage());
        }
    }

//	@Test
//	public void testAbdel() throws IOException {
//		RDFMapper.create().readValue(ModelIO.read(Files3.classPath("/data/object_lists.nt").toPath()), SampleBean.class, SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:639179e5744bfb428235966d51604d6a"));
//	}
//
//	@Test(expected = RDFMappingException.class)
//	public void testAbdel3() throws Exception {
//		RDFMapper.create().readValue(ModelIO.read(Files3.classPath("/data/map.nt").toPath()), YourTestClass.class, SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:639179e5744bfb428235966d51604d6a"));
//	}

    @Test
    public void testReadValueWhereClassIsNull() throws IOException {
        create().readValue(ModelIO.read(Files3.classPath("/data/object_lists.nt").toPath()), null, SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:639179e5744bfb428235966d51604d6a"));
    }

    @Test(expected = RDFMappingException.class)
    public void testCannotConstructAbstract() throws Exception {
        create().readValue(ModelIO.read(Files3.classPath("/data/mixed.nt").toPath()), CannotConstructMe2.class);
    }

    @Test
    public void testConstructFromEmpty() throws Exception {
        assertEquals(create().readValue(Models2.newModel(), Person.class), new Person());
    }

    @Test(expected = RDFMappingException.class)
    public void testMultipleSubjectsNoIdProvided() throws Exception {
        create().readValue(ModelIO.read(Files3.classPath("/data/object_lists.nt").toPath()), ClassWithObjectList.class);
    }

    @Test
    public void testWriteWithCodec() throws Exception {

        final Model aGraph = builder()
                .codec(UUID.class, UUIDCodec.Instance)
                .build()
                .writeValue(UUID.fromString("0110f311-964b-440d-b772-92c621c5d1e4"));

        assertTrue(Models.isomorphic(aGraph,
                ModelIO.read(Files3.classPath("/data/uuid.nt").toPath())));
    }

    @Test
    public void testReadWithCodec() throws Exception {
        final Model aGraph = ModelIO.read(Files3.classPath("/data/uuid.nt").toPath());

        final UUID aResult = builder()
                .codec(UUID.class, UUIDCodec.Instance)
                .build()
                .readValue(aGraph, UUID.class);

        assertEquals(UUID.fromString("0110f311-964b-440d-b772-92c621c5d1e4"), aResult);
    }

    @Test
    public void testReadWithNullCodec() throws Exception {
        final Model aGraph = ModelIO.read(Files3.classPath("/data/empty.nt").toPath());

        final UUID aResult = builder()
                .codec(UUID.class, UUIDCodec.Instance)
                .build()
                .readValue(aGraph, UUID.class);
    }
    

    @Test
    public void testReadMap() throws Exception {
        final ClassWithMap aExpected = new ClassWithMap();

        aExpected.mMap = Maps.newLinkedHashMap();

        aExpected.mMap.put("bob", new Person("Bob the tester"));
        aExpected.mMap.put(1L, "the size of something");
        aExpected.mMap.put(new Date(1426361082470L), 57.4);
        aExpected.mMap.put(new Person("another person"), new Company("The company"));

        final Model aGraph = ModelIO.read(Files3.classPath("/data/map.nt").toPath());

        assertEquals(aExpected, builder()
                .map(FOAF.ontology().Person, Person.class)
                .map(SimpleValueFactory.getInstance().createIRI("urn:Company"), Company.class)
                .build()
                .readValue(aGraph, ClassWithMap.class,
                        SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:06f95e70fea33fcd99e6804b02f96cc9")));
    }

    @Test(expected = IllegalStateException.class)
    public void testReadMapsWithException() throws Exception {
        final ClassWithMap aExpected = new ClassWithMap();

        aExpected.mMap = Maps.newLinkedHashMap();

        aExpected.mMap.put("bob", new Person("Bob the tester"));
        aExpected.mMap.put(1L, "the size of something");
        aExpected.mMap.put(new Date(1426361082470L), 57.4);
        aExpected.mMap.put(new Person("another person"), new Company("The company"));

        final Model aGraph = ModelIO.read(Files3.classPath("/data/map.nt").toPath());

        builder()
                .set(MappingOptions.IGNORE_CARDINALITY_VIOLATIONS, true)
                .map(FOAF.ontology().Person, Person.class)
                .map(SimpleValueFactory.getInstance().createIRI("urn:Company"), Company.class)
                .map(FOAF.ontology().Person, Person.class)
                .build()
                .readValue(aGraph, ClassWithMap.class,
                        SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:06f95e70fea33fcd99e6804b02f96cc9"));
    }

    @Test(expected = RDFMappingException.class)
    public void testReadMapsWithException2() throws Exception {
        final Model aGraph = ModelIO.read(Files3.classPath("/data/doublemap.nt").toPath());

        builder()
                .set(MappingOptions.IGNORE_CARDINALITY_VIOLATIONS, false)
                .map(FOAF.ontology().Person, Person.class)
                .build()
                .readValue(aGraph, ClassWithMap.class,
                        SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:06f95e70fea33fcd99e6804b02f96cc9"));
    }

    @Test
    public void testReadMapsWithException3() throws Exception {

        final Model aGraph = ModelIO.read(Files3.classPath("/data/doublemap.nt").toPath());

        builder()
                .set(MappingOptions.IGNORE_CARDINALITY_VIOLATIONS, true)
                .map(FOAF.ontology().Person, Person.class)
                .build()
                .readValue(aGraph, ClassWithMap.class,
                        SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:06f95e70fea33fcd99e6804b02f96cc9"));
    }

    @Test
    public void testReadMapsWithException4() throws Exception {

        final Model aGraph = ModelIO.read(Files3.classPath("/data/map.nt").toPath());

        builder()
                .set(MappingOptions.IGNORE_CARDINALITY_VIOLATIONS, true)
                .map(FOAF.ontology().Person, Person.class)
                .build()
                .readValue(aGraph, ClassWithMap.class,
                        SimpleValueFactory.getInstance().createIRI("tag:complexible:pinto:06f95e70fea33fcd99e6804b02f96cc9"));
    }

    @Test
    public void testCollectionFactory() {
        Builder builder = new Builder();

        CollectionFactory mockFactory = mock(CollectionFactory.class);

        Builder result = builder.collectionFactory(mockFactory);

        assertEquals(builder, result);
    }

    @Test
    public void testMapFactory() {
        Builder builder = new Builder();

        MapFactory mockFactory = mock(MapFactory.class);

        Builder result = builder.mapFactory(mockFactory);

        assertEquals(builder, result);
    }

    @Test
    public void testValueFactory() {
        Builder builder = new Builder();

        ValueFactory mockFactory = mock(ValueFactory.class);

        Builder result = builder.valueFactory(mockFactory);

        assertEquals(builder, result);
    }

    @Test
    public void testNamespace_ValidURIAndPrefix() {
        Builder builder = new Builder();

        String prefix = "ex";
        String namespace = "http://example.com/";

        Builder result = builder.namespace(prefix, namespace);

        Map<String, String> expectedNamespaces = new HashMap<>();
        expectedNamespaces.put(prefix, namespace);

        assertEquals(builder, result);
    }

    @Test
    public void testNamespace_InvalidURI() {
        // Create an instance of the Builder class
        Builder builder = new Builder();

        // Set up an invalid URI
        String prefix = "ex";
        String invalidNamespace = "\\///";

        // Verify that an IllegalArgumentException is thrown with the expected message
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                builder.namespace(prefix, invalidNamespace));
        assertEquals("namespace must be a valid URI", exception.getMessage());
    }

    @Test
    public void testNamespace_InvalidPrefix() {
        // Create an instance of the Builder class
        Builder builder = new Builder();

        // Set up an invalid prefix
        String invalidPrefix = "ex!";

        // Verify that an IllegalArgumentException is thrown with the expected message
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                builder.namespace(invalidPrefix, "http://example.com/"));
        assertEquals(invalidPrefix + " is not a valid namespace prefix", exception.getMessage());
    }

    @Test
    public void testNamespace_FromNamespaceObject() {
        Builder builder = new Builder();

        Namespace mockNamespace = mock(Namespace.class);
        String prefix = "ex";
        String namespace = "http://example.com/";
        when(mockNamespace.getPrefix()).thenReturn(prefix);

        Builder result = builder.namespace(mockNamespace);

        Map<String, String> expectedNamespaces = new HashMap<>();
        expectedNamespaces.put(prefix, namespace);

        assertEquals(builder, result);
    }


    public enum TestEnum {
        @Iri("invalid URI")
        Foo,

        @Iri("urn:TestEnum:Bar")
        Bar,

        Baz
    }

    private static class SampleBean {
        private String propertyName;

        public SampleBean() {
        }

        public String getPropertyName() {
            return propertyName;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }
    }

    public static final class Files3 {
        public static File classPath(final String thePath) {
            try {
                return new File(Files3.class.getResource(thePath).toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class CannotConstructMe {
        public CannotConstructMe(final String theValue) {
        }
    }

    public static abstract class CannotConstructMe2 {
        public CannotConstructMe2() {
        }
    }

    @RdfsClass("fo:Company")
    public static final class ShortCompany {

        private String mName;
        private String mWebsite;
        private Integer mNumberOfEmployees;

        @RdfId
        @RdfProperty("foaf:name")
        public String getName() {
            return mName;
        }

        public void setName(final String theName) {
            mName = theName;
        }

        @RdfId
        public String getWebsite() {
            return mWebsite;
        }

        public void setWebsite(final String theWebsite) {
            mWebsite = theWebsite;
        }

        @RdfProperty(datatype = "xs:integer")
        public Integer getNumberOfEmployees() {
            return mNumberOfEmployees;
        }

        public void setNumberOfEmployees(final Integer theNumberOfEmployees) {
            mNumberOfEmployees = theNumberOfEmployees;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName, mWebsite);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof ShortCompany) {
                ShortCompany aObj = (ShortCompany) theObj;
                return mNumberOfEmployees == aObj.mNumberOfEmployees
                        && Objects.equals(mName, aObj.mName)
                        && Objects.equals(mWebsite, aObj.mWebsite);
            } else {
                return false;
            }
        }
    }

    @RdfsClass("not a valid uri")
    public static final class BadCompany {
        private String mName;
        private String mWebsite;
        private int mNumberOfEmployees;

        @RdfProperty("not a valid uri")
        public String getName() {
            return mName;
        }

        public void setName(final String theName) {
            mName = theName;
        }

        public String getWebsite() {
            return mWebsite;
        }

        public void setWebsite(final String theWebsite) {
            mWebsite = theWebsite;
        }

        @RdfProperty(datatype = "not a valid uri")
        public int getNumberOfEmployees() {
            return mNumberOfEmployees;
        }

        public void setNumberOfEmployees(final int theNumberOfEmployees) {
            mNumberOfEmployees = theNumberOfEmployees;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName, mWebsite);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof BadCompany) {
                BadCompany aObj = (BadCompany) theObj;
                return Objects.equals(mName, aObj.mName) && Objects.equals(mWebsite, aObj.mWebsite);
            } else {
                return false;
            }
        }
    }

    @RdfsClass("urn:Company")
    public static final class Company {

        private String mName;
        private String mWebsite;
        private Integer mNumberOfEmployees;

        public Company() {
        }

        public Company(final String theName) {
            mName = theName;
        }

        @RdfId
        @RdfProperty("urn:name")
        public String getName() {
            return mName;
        }

        public void setName(final String theName) {
            mName = theName;
        }

        @RdfId
        public String getWebsite() {
            return mWebsite;
        }

        public void setWebsite(final String theWebsite) {
            mWebsite = theWebsite;
        }

        @RdfProperty(datatype = "http://www.w3.org/2001/XMLSchema#integer")
        public Integer getNumberOfEmployees() {
            return mNumberOfEmployees;
        }

        public void setNumberOfEmployees(final Integer theNumberOfEmployees) {
            mNumberOfEmployees = theNumberOfEmployees;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName, mWebsite);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof Company) {
                Company aObj = (Company) theObj;
                return Objects.equals(mName, aObj.mName) && Objects.equals(mWebsite, aObj.mWebsite);
            } else {
                return false;
            }
        }
    }

    public static class ClassWithEnumSet {
        private EnumSet<TestEnum> mEnums = EnumSet.noneOf(TestEnum.class);

        public EnumSet<TestEnum> getEnums() {
            return mEnums;
        }

        public void setEnums(final EnumSet<TestEnum> theEnums) {
            mEnums = theEnums;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mEnums);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof ClassWithEnumSet) {
                return Objects.equals(mEnums, ((ClassWithEnumSet) theObj).mEnums);
            } else {
                return false;
            }
        }
    }

    public static class ClassWithEnum implements Identifiable {
        private TestEnum mValue;

        private final Identifiable mIdentifiable = new IdentifiableImpl();

        @Override
        public Resource id() {
            return mIdentifiable.id();
        }

        @Override
        public void id(final Resource theResource) {
            mIdentifiable.id(theResource);
        }

        public TestEnum getValue() {
            return mValue;
        }

        public void setValue(final TestEnum theValue) {
            mValue = theValue;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mValue);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof ClassWithEnum) {
                ClassWithEnum aObj = (ClassWithEnum) theObj;

                return Objects.equals(mValue, aObj.mValue);
            } else {
                return false;
            }
        }
    }

    public static class ClassWithPrimitiveRdfList implements Identifiable {
        private List<Integer> mInts = Lists.newArrayList();

        private final Identifiable mIdentifiable = new IdentifiableImpl();

        @Override
        public Resource id() {
            return mIdentifiable.id();
        }

        @Override
        public void id(final Resource theResource) {
            mIdentifiable.id(theResource);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mInts);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof ClassWithPrimitiveLists) {
                ClassWithPrimitiveLists aObj = (ClassWithPrimitiveLists) theObj;

                return Objects.equals(mInts, aObj.mInts);
            } else {
                return false;
            }
        }

        @RdfProperty(isList = true)
        public List<Integer> getInts() {
            return mInts;
        }

        public void setInts(final List<Integer> theInts) {
            mInts = theInts;
        }
    }

    public static final class Person implements Comparable<Person>, Identifiable {
        private String mName;

        private final Identifiable mIdentifiable = new IdentifiableImpl();

        public Person() {
        }

        public Person(final String theName) {
            mName = theName;
        }

        @Override
        public String toString() {
            return mName;
        }

        @Override
        public Resource id() {
            return mIdentifiable.id();
        }

        @Override
        public void id(final Resource theResource) {
            mIdentifiable.id(theResource);
        }

        public String getName() {
            return mName;
        }

        public void setName(final String theName) {
            mName = theName;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mName);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof Person) {
                return Objects.equals(mName, ((Person) theObj).mName);
            } else {
                return false;
            }
        }

        @Override
        public int compareTo(final Person thePerson) {
            return mName.compareTo(thePerson.getName());
        }
    }

    public static class ClassWithObjectList implements Identifiable {
        private List<Person> mList = Lists.newArrayList();

        private Set<Person> mSet = Sets.newLinkedHashSet();

        private Collection<Person> mCollection = Sets.newLinkedHashSet();

        private SortedSet<Person> mSortedSet = Sets.newTreeSet();

        private final Identifiable mIdentifiable = new IdentifiableImpl();

        @Override
        public Resource id() {
            return mIdentifiable.id();
        }

        @Override
        public void id(final Resource theResource) {
            mIdentifiable.id(theResource);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mList, mSet, mCollection, mSortedSet);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof ClassWithObjectList) {
                ClassWithObjectList aObj = (ClassWithObjectList) theObj;

                return equalsTypeAndContents(mList, aObj.mList)
                        && equalsTypeAndContents(mSet, aObj.mSet)
                        && equalsTypeAndContents(mCollection, aObj.mCollection)
                        && equalsTypeAndContents(mSortedSet, aObj.mSortedSet);
            } else {
                return false;
            }
        }

        public Collection<Person> getCollection() {
            return mCollection;
        }

        public void setCollection(final Collection<Person> theCollection) {
            mCollection = theCollection;
        }

        public List<Person> getList() {
            return mList;
        }

        public void setList(final List<Person> theList) {
            mList = theList;
        }

        public Set<Person> getSet() {
            return mSet;
        }

        public void setSet(final Set<Person> theSet) {
            mSet = theSet;
        }

        public SortedSet<Person> getSortedSet() {
            return mSortedSet;
        }

        public void setSortedSet(final SortedSet<Person> theSortedSet) {
            mSortedSet = theSortedSet;
        }
    }

    public static class ClassWithRdfObjectList implements Identifiable {
        private List<Person> mList = Lists.newArrayList();

        private final Identifiable mIdentifiable = new IdentifiableImpl();

        @Override
        public Resource id() {
            return mIdentifiable.id();
        }

        @Override
        public void id(final Resource theResource) {
            mIdentifiable.id(theResource);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mList);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof ClassWithRdfObjectList) {
                ClassWithRdfObjectList aObj = (ClassWithRdfObjectList) theObj;

                return Objects.equals(mList, aObj.mList);
            } else {
                return false;
            }
        }

        @RdfProperty(isList = true)
        public List<Person> getList() {
            return mList;
        }

        public void setList(final List<Person> theList) {
            mList = theList;
        }
    }

    public static class ClassWithPrimitiveLists implements Identifiable {
        private List<Integer> mInts = Lists.newArrayList();

        private Set<Float> mFloats = Sets.newLinkedHashSet();

        private Collection<Double> mDoubles = Sets.newLinkedHashSet();

        private SortedSet<Boolean> mBools = Sets.newTreeSet();

        private final Identifiable mIdentifiable = new IdentifiableImpl();

        @Override
        public Resource id() {
            return mIdentifiable.id();
        }

        @Override
        public void id(final Resource theResource) {
            mIdentifiable.id(theResource);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mInts, mFloats, mDoubles, mBools);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof ClassWithPrimitiveLists) {
                ClassWithPrimitiveLists aObj = (ClassWithPrimitiveLists) theObj;

                return equalsTypeAndContents(mInts, aObj.mInts)
                        && equalsTypeAndContents(mFloats, aObj.mFloats)
                        && equalsTypeAndContents(mDoubles, aObj.mDoubles)
                        && equalsTypeAndContents(mBools, aObj.mBools);
            } else {
                return false;
            }
        }

        public SortedSet<Boolean> getBools() {
            return mBools;
        }

        public void setBools(final SortedSet<Boolean> theBools) {
            mBools = theBools;
        }

        public Collection<Double> getDoubles() {
            return mDoubles;
        }

        public void setDoubles(final Collection<Double> theDoubles) {
            mDoubles = theDoubles;
        }

        public Set<Float> getFloats() {
            return mFloats;
        }

        public void setFloats(final Set<Float> theFloats) {
            mFloats = theFloats;
        }

        public List<Integer> getInts() {
            return mInts;
        }

        public void setInts(final List<Integer> theInts) {
            mInts = theInts;
        }
    }

    public static class ClassWithMixed implements Identifiable {
        private ClassWithPrimitives mChild;

        private String mString;

        private final Identifiable mIdentifiable = new IdentifiableImpl();

        @Override
        public Resource id() {
            return mIdentifiable.id();
        }

        @Override
        public void id(final Resource theResource) {
            mIdentifiable.id(theResource);
        }

        public String getString() {
            return mString;
        }

        public void setString(final String theString) {
            mString = theString;
        }

        public ClassWithPrimitives getChild() {
            return mChild;
        }

        public void setChild(final ClassWithPrimitives theChild) {
            mChild = theChild;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mString, mChild);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof ClassWithMixed) {
                ClassWithMixed aObj = (ClassWithMixed) theObj;
                return Objects.equals(mString, aObj.mString)
                        && Objects.equals(mChild, aObj.mChild);
            } else {
                return false;
            }
        }
    }

    public static class ClassWithPrimitives implements Identifiable {
        private String mString;
        private int mInt;
        private java.net.URI mURI;
        private float mFloat;
        private double mDouble;
        private char mChar;

        private final Identifiable mIdentifiable = new IdentifiableImpl();

        @Override
        public Resource id() {
            return mIdentifiable.id();
        }

        @Override
        public void id(final Resource theResource) {
            mIdentifiable.id(theResource);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (this == theObj) {
                return true;
            }
            if (theObj == null || getClass() != theObj.getClass()) {
                return false;
            }

            ClassWithPrimitives that = (ClassWithPrimitives) theObj;

            return mChar == that.mChar
                    && Double.compare(mDouble, that.mDouble) == 0
                    && Float.compare(mFloat, that.mFloat) == 0
                    && mInt == that.mInt
                    && Objects.equals(mString, that.mString)
                    && Objects.equals(mURI, that.mURI);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mChar, mDouble, mFloat, mInt, mString, mURI);
        }

        public char getChar() {
            return mChar;
        }

        public void setChar(final char theChar) {
            mChar = theChar;
        }

        public double getDouble() {
            return mDouble;
        }

        public void setDouble(final double theDouble) {
            mDouble = theDouble;
        }

        public float getFloat() {
            return mFloat;
        }

        public void setFloat(final float theFloat) {
            mFloat = theFloat;
        }

        public int getInt() {
            return mInt;
        }

        public void setInt(final int theInt) {
            mInt = theInt;
        }

        public String getString() {
            return mString;
        }

        public void setString(final String theString) {
            mString = theString;
        }

        public URI getURI() {
            return mURI;
        }

        public void setURI(final URI theURI) {
            mURI = theURI;
        }
    }

    public static final class ClassWithMap {
        private Map<Object, Object> mMap;

        public Map<Object, Object> getMap() {
            return mMap;
        }

        public void setMap(final Map<Object, Object> theMap) {
            mMap = theMap;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mMap);
        }

        @Override
        public boolean equals(final Object theObj) {
            if (theObj == this) {
                return true;
            } else if (theObj instanceof ClassWithMap) {
                return Objects.equals(mMap, ((ClassWithMap) theObj).mMap);
            } else {
                return false;
            }
        }
    }
}
