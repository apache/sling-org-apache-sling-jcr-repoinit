/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.jcr.repoinit.it;

import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.junit.Before;
import org.junit.Test;
import org.junit.Test.None;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Basic integration test of the repoinit parser and execution
 *  services, reading statements from a text file.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class RepoInitTextIT extends RepoInitTestSupport {

    private static final String FRED_WILMA = "fredWilmaService";
    private static final String ANOTHER = "anotherService";
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String GROUP_A = "grpA";
    private static final String GROUP_B = "grpB";
    private static final String GROUP_C = "grpC";
    private static final String PROP_A = "pathArray";
    private static final String PROP_B = "someInteger";
    private static final String PROP_C = "someFlag";
    private static final String PROP_D = "someDate";
    private static final String PROP_E = "customSingleValueStringProp";
    private static final String PROP_F = "customSingleValueQuotedStringProp";
    private static final String PROP_G = "stringArray";
    private static final String PROP_H = "quotedA";
    private static final String PROP_I = "quotedMix";
    private static final String PROP_NODE_PATH = "/proptest/X/Y";

    public static final String REPO_INIT_FILE = "/repoinit.txt";

    @Inject
    private RepoInitParser parser;

    @Inject
    private JcrRepoInitOpsProcessor processor;

    @Before
    public void setup() throws Exception {
        setupSession();

        // Execute some repoinit statements
        final InputStream is = getClass().getResourceAsStream(REPO_INIT_FILE);
        assertNotNull("Expecting " + REPO_INIT_FILE, is);
        try {
            processor.apply(session, parser.parse(new InputStreamReader(is, "UTF-8")));
            session.save();
        } finally {
            is.close();
        }

        // The repoinit file causes those nodes to be created
        assertTrue("Expecting test nodes to be created", session.itemExists("/acltest/A/B"));
    }

    @Test
    public void serviceUserCreatedWithHomePath() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                assertTrue("Expecting user " + FRED_WILMA, U.userExists(session, FRED_WILMA));
                final String path = U.getHomePath(session, FRED_WILMA);
                assertTrue("Expecting path " + path, session.itemExists(path));
                return null;
            }
        };
    }

    @Test
    public void fredWilmaAcl() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                assertFalse("Expecting no write access to A", U.canWrite(session, FRED_WILMA, "/acltest/A"));
                assertTrue("Expecting write access to A/B", U.canWrite(session, FRED_WILMA, "/acltest/A/B"));
                return null;
            }
        };
    }

    @Test
    public void anotherUserAcl() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                assertTrue("Expecting write access to A", U.canWrite(session, ANOTHER, "/acltest/A"));
                assertFalse("Expecting no write access to B", U.canWrite(session, ANOTHER, "/acltest/A/B"));
                return null;
            }
        };
    }

    @Test(expected = None.class)
    public void namespaceAndCndRegistered() throws Exception {
        final String nodeName = "ns-" + UUID.randomUUID();
        session.getRootNode().addNode(nodeName, "slingtest:unstructured");
        session.save();
    }

    @Test
    public void fredWilmaHomeAcl() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                assertTrue("Expecting user " + FRED_WILMA, U.userExists(session, FRED_WILMA));
                final String path = U.getHomePath(session, FRED_WILMA);
                assertTrue("Expecting path " + path, session.itemExists(path));
                assertTrue("Expecting write access for alice", U.canWrite(session, ALICE, path));
                assertFalse("Expecting no access for bob", U.canRead(session, BOB, path));
                return null;
            }
        };
    }

    @Test
    public void groupMembership() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                assertTrue(
                        "Expecting user " + FRED_WILMA + "to be member of " + GROUP_A,
                        U.isMember(session, FRED_WILMA, GROUP_A));
                assertTrue(
                        "Expecting user " + ALICE + "to be member of " + GROUP_A, U.isMember(session, ALICE, GROUP_A));
                assertTrue(
                        "Expecting user " + ANOTHER + "to be member of " + GROUP_B,
                        U.isMember(session, ANOTHER, GROUP_B));
                assertFalse(
                        "Expecting user " + BOB + "not to be member of " + GROUP_B, U.isMember(session, BOB, GROUP_B));
                assertFalse(
                        "Expecting group " + GROUP_A + "not to be member of " + GROUP_B,
                        U.isMember(session, GROUP_A, GROUP_B));
                assertTrue(
                        "Expecting group " + GROUP_A + "to be member of " + GROUP_C,
                        U.isMember(session, GROUP_A, GROUP_C));
                assertTrue("Expecting user " + BOB + "to be member of " + GROUP_C, U.isMember(session, BOB, GROUP_C));
                assertTrue(
                        "Expecting group " + GROUP_B + "to be member of " + GROUP_C,
                        U.isMember(session, GROUP_B, GROUP_C));
                return null;
            }
        };
    }

    @Test
    public void setProperties() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                ValueFactory vf = session.getValueFactory();
                Value[] expectedValues1 = new Value[2];
                expectedValues1[0] = vf.createValue("/d/e/f/*");
                expectedValues1[1] = vf.createValue("m/n/*");
                assertTrue(
                        "Expecting array type property " + PROP_A + " to be present ",
                        U.hasProperty(session, PROP_NODE_PATH, PROP_A, expectedValues1));

                Value expectedValue2 = vf.createValue("42", PropertyType.valueFromName("Long"));
                assertTrue(
                        "Expecting Long type default property " + PROP_B + " to be present ",
                        U.hasProperty(session, PROP_NODE_PATH, PROP_B, expectedValue2));

                Value expectedValue3 = vf.createValue("true", PropertyType.valueFromName("Boolean"));
                assertTrue(
                        "Expecting bool type property " + PROP_C + " to be present ",
                        U.hasProperty(session, PROP_NODE_PATH, PROP_C, expectedValue3));

                Value expectedValue4 =
                        vf.createValue("2020-03-19T11:39:33.437+05:30", PropertyType.valueFromName("Date"));
                assertTrue(
                        "Expecting date type property " + PROP_D + " to be present ",
                        U.hasProperty(session, PROP_NODE_PATH, PROP_D, expectedValue4));

                Value expectedValue5 = vf.createValue("test");
                assertTrue(
                        "Expecting string type property " + PROP_E + " to be present ",
                        U.hasProperty(session, PROP_NODE_PATH, PROP_E, expectedValue5));

                Value expectedValue6 = vf.createValue("hello, you!");
                assertTrue(
                        "Expecting quoted string type property " + PROP_F + " to be present ",
                        U.hasProperty(session, PROP_NODE_PATH, PROP_F, expectedValue6));

                Value[] expectedValues7 = new Value[2];
                expectedValues7[0] = vf.createValue("test1");
                expectedValues7[1] = vf.createValue("test2");
                assertTrue(
                        "Expecting string array type property " + PROP_G + " to be present ",
                        U.hasProperty(session, PROP_NODE_PATH, PROP_G, expectedValues7));

                Value expectedValue8 = vf.createValue("Here's a \"double quoted string\" with suffix");
                assertTrue(
                        "Expecting quoted string type property " + PROP_H + " to be present ",
                        U.hasProperty(session, PROP_NODE_PATH, PROP_H, expectedValue8));

                Value[] expectedValues9 = new Value[3];
                expectedValues9[0] = vf.createValue("quoted");
                expectedValues9[1] = vf.createValue("non-quoted");
                expectedValues9[2] = vf.createValue("the last \" one");
                assertTrue(
                        "Expecting string array type property " + PROP_I + " to be present ",
                        U.hasProperty(session, PROP_NODE_PATH, PROP_I, expectedValues9));

                return null;
            }
        };
    }

    @Test
    public void setAuthorizableProperties() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                if (!(session instanceof JackrabbitSession)) {
                    throw new IllegalArgumentException("Session is not a JackrabbitSession");
                }
                UserManager um = ((JackrabbitSession) session).getUserManager();

                Authorizable[] authorizables =
                        new Authorizable[] {um.getAuthorizable(ALICE), um.getAuthorizable(GROUP_A)};

                for (Authorizable authorizable : authorizables) {
                    assertNotNull("Expected authorizable to not be null", authorizable);
                    ValueFactory vf = session.getValueFactory();
                    Value[] expectedValues1 = new Value[2];
                    expectedValues1[0] = vf.createValue("/d/e/f/*");
                    expectedValues1[1] = vf.createValue("m/n/*");
                    assertTrue(
                            "Expecting array type property " + PROP_A + " to be present ",
                            U.hasProperty(authorizable, PROP_A, expectedValues1));

                    Value expectedValue2 = vf.createValue("42", PropertyType.valueFromName("Long"));
                    assertTrue(
                            "Expecting Long type default property " + PROP_B + " to be present ",
                            U.hasProperty(authorizable, PROP_B, expectedValue2));

                    Value expectedValue3 = vf.createValue("true", PropertyType.valueFromName("Boolean"));
                    assertTrue(
                            "Expecting bool type property " + PROP_C + " to be present ",
                            U.hasProperty(authorizable, PROP_C, expectedValue3));

                    Value expectedValue4 =
                            vf.createValue("2020-03-19T11:39:33.437+05:30", PropertyType.valueFromName("Date"));
                    assertTrue(
                            "Expecting date type property " + PROP_D + " to be present ",
                            U.hasProperty(authorizable, PROP_D, expectedValue4));

                    Value expectedValue5 = vf.createValue("test");
                    assertTrue(
                            "Expecting string type property " + PROP_E + " to be present ",
                            U.hasProperty(authorizable, PROP_E, expectedValue5));

                    Value expectedValue6 = vf.createValue("hello, you!");
                    assertTrue(
                            "Expecting quoted string type property " + PROP_F + " to be present ",
                            U.hasProperty(authorizable, PROP_F, expectedValue6));

                    Value[] expectedValues7 = new Value[2];
                    expectedValues7[0] = vf.createValue("test1");
                    expectedValues7[1] = vf.createValue("test2");
                    assertTrue(
                            "Expecting string array type property " + PROP_G + " to be present ",
                            U.hasProperty(authorizable, PROP_G, expectedValues7));

                    Value expectedValue8 = vf.createValue("Here's a \"double quoted string\" with suffix");
                    assertTrue(
                            "Expecting quoted string type property " + PROP_H + " to be present ",
                            U.hasProperty(authorizable, PROP_H, expectedValue8));

                    Value[] expectedValues9 = new Value[3];
                    expectedValues9[0] = vf.createValue("quoted");
                    expectedValues9[1] = vf.createValue("non-quoted");
                    expectedValues9[2] = vf.createValue("the last \" one");
                    assertTrue(
                            "Expecting string array type property " + PROP_I + " to be present ",
                            U.hasProperty(authorizable, PROP_I, expectedValues9));

                    Value nestedExpectedValue = vf.createValue("42", PropertyType.valueFromName("Long"));
                    assertTrue(
                            "Expecting Long type default property nested/" + PROP_B + " to be present ",
                            U.hasProperty(authorizable, "nested/" + PROP_B, nestedExpectedValue));
                }

                return null;
            }
        };
    }

    @Test
    public void setAuthorizableSubTreeProperties() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                if (!(session instanceof JackrabbitSession)) {
                    throw new IllegalArgumentException("Session is not a JackrabbitSession");
                }
                UserManager um = ((JackrabbitSession) session).getUserManager();

                Authorizable[] authorizables =
                        new Authorizable[] {um.getAuthorizable(BOB), um.getAuthorizable(GROUP_B)};

                for (Authorizable authorizable : authorizables) {
                    assertNotNull("Expected authorizable to not be null", authorizable);
                    ValueFactory vf = session.getValueFactory();
                    Value[] expectedValues1 = new Value[2];
                    expectedValues1[0] = vf.createValue("/d/e/f/*");
                    expectedValues1[1] = vf.createValue("m/n/*");
                    assertTrue(
                            "Expecting array type property nested/" + PROP_A + " to be present ",
                            U.hasProperty(authorizable, "nested/" + PROP_A, expectedValues1));

                    Value expectedValue2 = vf.createValue("42", PropertyType.valueFromName("Long"));
                    assertTrue(
                            "Expecting Long type default property nested/" + PROP_B + " to be present ",
                            U.hasProperty(authorizable, "nested/" + PROP_B, expectedValue2));

                    Value expectedValue3 = vf.createValue("true", PropertyType.valueFromName("Boolean"));
                    assertTrue(
                            "Expecting bool type property nested/" + PROP_C + " to be present ",
                            U.hasProperty(authorizable, "nested/" + PROP_C, expectedValue3));

                    Value expectedValue4 =
                            vf.createValue("2020-03-19T11:39:33.437+05:30", PropertyType.valueFromName("Date"));
                    assertTrue(
                            "Expecting date type property nested/" + PROP_D + " to be present ",
                            U.hasProperty(authorizable, "nested/" + PROP_D, expectedValue4));

                    Value expectedValue5 = vf.createValue("test");
                    assertTrue(
                            "Expecting string type property nested/" + PROP_E + " to be present ",
                            U.hasProperty(authorizable, "nested/" + PROP_E, expectedValue5));

                    Value expectedValue6 = vf.createValue("hello, you!");
                    assertTrue(
                            "Expecting quoted string type property nested/" + PROP_F + " to be present ",
                            U.hasProperty(authorizable, "nested/" + PROP_F, expectedValue6));

                    Value[] expectedValues7 = new Value[2];
                    expectedValues7[0] = vf.createValue("test1");
                    expectedValues7[1] = vf.createValue("test2");
                    assertTrue(
                            "Expecting string array type property nested/" + PROP_G + " to be present ",
                            U.hasProperty(authorizable, "nested/" + PROP_G, expectedValues7));

                    Value expectedValue8 = vf.createValue("Here's a \"double quoted string\" with suffix");
                    assertTrue(
                            "Expecting quoted string type property nested/" + PROP_H + " to be present ",
                            U.hasProperty(authorizable, "nested/" + PROP_H, expectedValue8));

                    Value[] expectedValues9 = new Value[3];
                    expectedValues9[0] = vf.createValue("quoted");
                    expectedValues9[1] = vf.createValue("non-quoted");
                    expectedValues9[2] = vf.createValue("the last \" one");
                    assertTrue(
                            "Expecting string array type property nested/" + PROP_I + " to be present ",
                            U.hasProperty(authorizable, "nested/" + PROP_I, expectedValues9));

                    Value nestedExpectedValue = vf.createValue("42", PropertyType.valueFromName("Long"));
                    assertTrue(
                            "Expecting Long type default property nested/nested/" + PROP_B + " to be present ",
                            U.hasProperty(authorizable, "nested/nested/" + PROP_B, nestedExpectedValue));
                }

                return null;
            }
        };
    }

    @Test
    public void forcedMultiValue() throws Exception {
        new Retry() {
            @Override
            public Void call() throws Exception {
                Node node = session.getNode("/forcedMultiValue");
                assertMultiProperty(node, "multiValue", new String[] {"item1", "item2"});
                assertMultiProperty(node, "singleMultiValue", new String[] {"single"});
                assertMultiProperty(node, "emptyMultiValue", new String[0]);
                assertLongMultiProperty(node, "longMultiValue", new Long[] {1243L, 5678L});
                assertLongMultiProperty(node, "singleLongMultiValue", new Long[] {1243L});
                assertLongMultiProperty(node, "emptyMultiValue", new Long[0]);
                return null;
            }
        };
    }

    private static void assertMultiProperty(Node node, String propertyName, Object[] expectedValues) throws Exception {
        Property prop = node.getProperty(propertyName);
        assertTrue("Property " + propertyName + " is not multiple", prop.isMultiple());
        Object[] actualValues = Stream.of(prop.getValues())
                .map(value -> {
                    try {
                        return value.getString();
                    } catch (IllegalStateException | RepositoryException e) {
                        return null;
                    }
                })
                .toArray();
        assertArrayEquals("Unexpected values for property " + propertyName, expectedValues, actualValues);
    }

    private static void assertLongMultiProperty(Node node, String propertyName, Object[] expectedValues)
            throws Exception {
        Property prop = node.getProperty(propertyName);
        assertTrue("Property " + propertyName + " is not multiple", prop.isMultiple());
        Object[] actualValues = Stream.of(prop.getValues())
                .map(value -> {
                    try {
                        return value.getLong();
                    } catch (IllegalStateException | RepositoryException e) {
                        return null;
                    }
                })
                .toArray();
        assertArrayEquals("Unexpected values for property " + propertyName, expectedValues, actualValues);
    }
}
