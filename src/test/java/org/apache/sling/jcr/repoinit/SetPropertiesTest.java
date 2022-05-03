/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jcr.repoinit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.jcr.repoinit.impl.UserUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;


/** Test the setting of properties on nodes */
public class SetPropertiesTest {
    
    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);
    
    private TestUtil U;
    private ValueFactory vf;
    private static final String pathPrefix = "/one/two/";
    private static final String path1 = pathPrefix + UUID.randomUUID();
    private static final String path2 = pathPrefix + UUID.randomUUID();
    private static final String path3 = pathPrefix + UUID.randomUUID();

    @Before
    public void setup() throws RepositoryException, IOException, RepoInitParsingException {
        U = new TestUtil(context);
        vf = U.adminSession.getValueFactory();
        RepositoryUtil.registerSlingNodeTypes(U.adminSession);
        for(String p : new String[] { path1, path2, path3 }) {
            U.parseAndExecute("create path " + p);
            U.assertNodeExists(p);
        }
    }

    @Test
    public void setStringPropertyTest() throws Exception {
        U.parseAndExecute("set properties on " + path1 + " \n set sling:ResourceType{String} to /x/y/z \n end");
        Value expectedValue = vf.createValue("/x/y/z");
        U.assertSVPropertyExists(path1, "sling:ResourceType", expectedValue);
    }

    @Test
    public void setMultiplePropertiesTest() throws Exception {
        final String setProps =
                "set properties on " + path2 + "\n"
                        + "set sling:ResourceType{String} to /x/y/z \n"
                        + "set allowedTemplates to /d/e/f/*, m/n/* \n"
                        + "set someInteger{Long} to 42 \n"
                        + "set someFlag{Boolean} to true \n"
                        + "set someDate{Date} to \"2020-03-19T11:39:33.437+05:30\" \n"
                        + "set customSingleValueQuotedStringProp to \"hello, you!\" \n"
                + "end"
                ;
        U.parseAndExecute(setProps);
        Value expectedValue1 = vf.createValue("/x/y/z");
        U.assertSVPropertyExists(path2, "sling:ResourceType", expectedValue1);
        Value[] expectedValues2 = new Value[2];
        expectedValues2[0] = vf.createValue("/d/e/f/*");
        expectedValues2[1] = vf.createValue("m/n/*");
        U.assertMVPropertyExists(path2, "allowedTemplates", expectedValues2);
        Value expectedValue3 = vf.createValue("42", PropertyType.valueFromName("Long"));
        U.assertSVPropertyExists(path2, "someInteger", expectedValue3);
        Value expectedValue4 = vf.createValue("true", PropertyType.valueFromName("Boolean"));
        U.assertSVPropertyExists(path2, "someFlag", expectedValue4);
        Value expectedValue5 = vf.createValue("2020-03-19T11:39:33.437+05:30", PropertyType.valueFromName("Date"));
        U.assertSVPropertyExists(path2, "someDate", expectedValue5);
        Value expectedValue6 = vf.createValue("hello, you!");
        U.assertSVPropertyExists(path2, "customSingleValueQuotedStringProp", expectedValue6);
    }

    @Test
    public void setPropertyOnNonExistentPathTest() throws Exception {
        String nonExistingPath =  "/someNonExistingPath/A/B";
        try {
            U.parseAndExecute("set properties on " + nonExistingPath + " \n set sling:ResourceType{String} to /x/y/z \n end");
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue("expected repository exception",  e.getMessage().contains("Unable to set properties on path [" + nonExistingPath + "]:"));
        }
    }

    @Test
    public void setDefaultProperties() throws Exception {
        final String setPropsA =
                "set properties on " + path3 + "\n"
                        + "set one to oneA\n"
                        + "default two to twoA\n"
                + "end"
                ;
        U.parseAndExecute(setPropsA);
        U.assertSVPropertyExists(path3, "one", vf.createValue("oneA"));
        U.assertSVPropertyExists(path3, "two", vf.createValue("twoA"));

        final String setPropsB =
                "set properties on " + path3 + "\n"
                        + "set one to oneB\n"
                        + "default two to twoB\n"
                + "end"
                ;

        U.parseAndExecute(setPropsB);
        U.assertSVPropertyExists(path3, "one", vf.createValue("oneB"));
        U.assertSVPropertyExists(path3, "two", vf.createValue("twoA"));
    }

    @Test
    public void setUserProperties() throws Exception {
        String userid = "user" + UUID.randomUUID();

        U.assertUser("before creating user", userid, false);
        U.parseAndExecute("create user " + userid);
        U.assertUser("after creating user", userid, true);

        assertAuthorizableProperties(userid);
        assertAuthorizablePropertiesAgain(userid);
    }

    @Test
    public void setSubTreeUserProperties() throws Exception {
        String userid = "user" + UUID.randomUUID();

        U.assertUser("before creating user", userid, false);
        U.parseAndExecute("create user " + userid);
        U.assertUser("after creating user", userid, true);

        assertAuthorizableSubTreeProperties(userid);
        assertAuthorizableSubTreePropertiesAgain(userid);
    }

    @Test
    public void setGroupProperties() throws Exception {
        String groupid = "group" + UUID.randomUUID();

        U.assertGroup("before creating group", groupid, false);
        U.parseAndExecute("create group " + groupid);
        U.assertGroup("after creating group", groupid, true);

        assertAuthorizableProperties(groupid);
        assertAuthorizablePropertiesAgain(groupid);
    }

    @Test
    public void setSubTreeGroupProperties() throws Exception {
        String groupid = "group" + UUID.randomUUID();

        U.assertGroup("before creating group", groupid, false);
        U.parseAndExecute("create group " + groupid);
        U.assertGroup("after creating group", groupid, true);

        assertAuthorizableSubTreeProperties(groupid);
        assertAuthorizableSubTreePropertiesAgain(groupid);
    }

    @Test
    public void unknownAuthorizable() throws Exception {
        final String setProps =
            "set properties on authorizable(nonExistingUser)\n"
            + "set one to oneA\n"
            + "end"
        ;
        try {
            U.parseAndExecute(setProps);
            fail("Expecting execution to fail");
        } catch(RuntimeException asExpected) {
            // all good
        }
    }

    @Test
    public void typoInFunctionName() throws Exception {
        String userid = "user" + UUID.randomUUID();
        U.parseAndExecute("create user " + userid);

        // The parser accepts any function name in paths,
        // make sure we fail in the right way
        final String setProps =
            "set properties on UNauthorizable(" + userid + ")\n"
            + "set one to oneA\n"
            + "end"
        ;
        try {
            U.parseAndExecute(setProps);
            fail("Expecting execution to fail");
        } catch(RuntimeException asExpected) {
            // all good
        }
    }

    /**
     * SLING-11293 "set default properties" instruction to change autocreated property value
     */
    @Test
    public void setAutocreatedDefaultPropertiesFromPrimaryType() throws Exception {
        registerSling11293NodeType();

        // create the test node
        String name = UUID.randomUUID().toString();
        String testPath = pathPrefix + name;
        Node parentNode = U.getAdminSession()
            .getNode(Paths.get(testPath).getParent().toString());
        parentNode.addNode(name, "slingtest:sling11293");
        changeAutocreatedDefaultProperties(testPath);
    }

    /**
     * SLING-11293 "set default properties" instruction to change autocreated property value
     */
    @Test
    public void setAutocreatedDefaultPropertiesFromMixinType() throws Exception {
        registerSling11293NodeType();

        // create the test node
        String name = UUID.randomUUID().toString();
        String testPath = pathPrefix + name;
        Node parentNode = U.getAdminSession()
            .getNode(Paths.get(testPath).getParent().toString());
        parentNode.addNode(name).addMixin("slingtest:sling11293mixin");
        changeAutocreatedDefaultProperties(testPath);
    }

    protected void changeAutocreatedDefaultProperties(String testPath)
            throws RepositoryException, RepoInitParsingException {
        U.assertNodeExists(testPath);
        // verify the initial autocreated property values
        U.assertSVPropertyExists(testPath, "singleVal", vf.createValue("autocreated value"));
        U.assertMVPropertyExists(testPath, "multiVal", new Value[] {
                vf.createValue("autocreated value1"),
                vf.createValue("autocreated value2")
        });

        // setting "default" the first time should change the value as the value is now
        //  the same as the autocreated default values
        final String setPropsA =
                "set properties on " + testPath + "\n" +
                        "default singleVal to sChanged1a\n" +
                        "default multiVal to mChanged1a, mChanged2a\n" +
                "end";
        U.parseAndExecute(setPropsA);
        U.assertSVPropertyExists(testPath, "singleVal", vf.createValue("sChanged1a"));
        U.assertMVPropertyExists(testPath, "multiVal", new Value[] {
                vf.createValue("mChanged1a"),
                vf.createValue("mChanged2a")
        });

        // setting again should do nothing as the value is now
        //  not the same as the autocreated default values
        final String setPropsB =
                "set properties on " + testPath + "\n" +
                        "default singleVal to sChanged1b\n" +
                        "default multiVal to mChanged1b, mChanged2b\n" +
                "end";
        U.parseAndExecute(setPropsB);
        U.assertSVPropertyExists(testPath, "singleVal", vf.createValue("sChanged1a"));
        U.assertMVPropertyExists(testPath, "multiVal", new Value[] {
                vf.createValue("mChanged1a"),
                vf.createValue("mChanged2a")
        });
    }

    /**
     * SLING-11293 "set default properties" instruction to change autocreated property value
     * for an authorizable
     */
    @Test
    public void setAutocreatedDefaultUserProperties() throws Exception {
        registerSling11293NodeType();

        String userid = "user" + UUID.randomUUID();

        U.assertUser("before creating user", userid, false);
        U.parseAndExecute("create user " + userid);
        U.assertUser("after creating user", userid, true);

        final Authorizable a = UserUtil.getUserManager(U.getAdminSession()).getAuthorizable(userid);

        // create the test node
        String name = UUID.randomUUID().toString();
        U.getAdminSession()
            .getNode(a.getPath())
            .addNode(name, "slingtest:sling11293");
        String testPath = a.getPath() + "/" + name;
        U.assertNodeExists(testPath);
        // verify the initial autocreated property values
        U.assertSVPropertyExists(testPath, "singleVal", vf.createValue("autocreated value"));
        U.assertMVPropertyExists(testPath, "multiVal", new Value[] {
                vf.createValue("autocreated value1"),
                vf.createValue("autocreated value2")
        });

        // setting "default" the first time should change the value as the value is now
        //  the same as the autocreated default values
        final String setPropsA =
                "set properties on authorizable(" + userid + ")/" + name + "\n" +
                        "default singleVal to sChanged1a\n" +
                        "default multiVal to mChanged1a, mChanged2a\n" +
                "end";
        U.parseAndExecute(setPropsA);
        U.assertAuthorizableSVPropertyExists(userid, name + "/singleVal", vf.createValue("sChanged1a"));
        U.assertAuthorizableMVPropertyExists(userid, name + "/multiVal", new Value[] {
                vf.createValue("mChanged1a"),
                vf.createValue("mChanged2a")
        });

        // setting again should do nothing as the value is now
        //  not the same as the autocreated default values
        final String setPropsB =
                "set properties on authorizable(" + userid + ")/" + name + "\n" +
                        "default singleVal to sChanged1b\n" +
                        "default multiVal to mChanged1b, mChanged2b\n" +
                "end";
        U.parseAndExecute(setPropsB);
        U.assertAuthorizableSVPropertyExists(userid, name + "/singleVal", vf.createValue("sChanged1a"));
        U.assertAuthorizableMVPropertyExists(userid, name + "/multiVal", new Value[] {
                vf.createValue("mChanged1a"),
                vf.createValue("mChanged2a")
        });
    }

    protected void registerSling11293NodeType()
            throws RepositoryException, RepoInitParsingException, NoSuchNodeTypeException {
        final String registerNodeTypes =
                "register nodetypes\n" +
                "<<===\n" +
                "<< <slingtest='http://sling.apache.org/ns/test/repoinit-it/v1.0'>\n" +
                "<< [slingtest:sling11293] > nt:unstructured\n" +
                "<<    - singleVal (String) = 'autocreated value'\n" +
                "<<       autocreated\n" +
                "<<    - multiVal (String) = 'autocreated value1', 'autocreated value2'\n" +
                "<<       multiple autocreated\n" +
                "<< [slingtest:sling11293mixin]\n" +
                "<<    mixin\n" +
                "<<    - singleVal (String) = 'autocreated value'\n" +
                "<<       autocreated\n" +
                "<<    - multiVal (String) = 'autocreated value1', 'autocreated value2'\n" +
                "<<       multiple autocreated\n" +
                "===>>";
        U.parseAndExecute(registerNodeTypes);
        NodeType nodeType = U.getAdminSession().getWorkspace().getNodeTypeManager().getNodeType("slingtest:sling11293");
        assertNotNull(nodeType);
        NodeType mixinType = U.getAdminSession().getWorkspace().getNodeTypeManager().getNodeType("slingtest:sling11293mixin");
        assertNotNull(mixinType);
    }

    /**
     * Set properties on an authorizable and then verify that the values were set
     */
    protected void assertAuthorizableProperties(String id) throws RepositoryException, RepoInitParsingException {
        final String setPropsA =
                "set properties on authorizable(" +id + ")\n"
                        + "set one to oneA\n"
                        + "set dbl{Double} to 3.14\n"
                        + "default two to twoA\n"
                        + "set nested/one to oneA\n"
                        + "default nested/two to twoA\n"
                        + "set three to threeA, \"threeB\", threeC\n"
                        + "default four to fourA, \"fourB\"\n"
                        + "set nested/three to threeA, \"threeB\", threeC\n"
                        + "default nested/four to fourA, \"fourB\"\n"
                + "end";

        U.parseAndExecute(setPropsA);

        U.assertAuthorizableSVPropertyExists(id, "one", vf.createValue("oneA"));
        U.assertAuthorizableSVPropertyExists(id, "dbl", vf.createValue(3.14));
        U.assertAuthorizableSVPropertyExists(id, "nested/one", vf.createValue("oneA"));
        U.assertAuthorizableSVPropertyExists(id, "two", vf.createValue("twoA"));
        U.assertAuthorizableSVPropertyExists(id, "nested/two", vf.createValue("twoA"));
        U.assertAuthorizableMVPropertyExists(id, "three", new Value[] {
                vf.createValue("threeA"),
                vf.createValue("threeB"),
                vf.createValue("threeC")
                });
        U.assertAuthorizableMVPropertyExists(id, "nested/three", new Value[] {
                vf.createValue("threeA"),
                vf.createValue("threeB"),
                vf.createValue("threeC")
                });
        U.assertAuthorizableMVPropertyExists(id, "four", new Value[] {
                vf.createValue("fourA"),
                vf.createValue("fourB")
                });
        U.assertAuthorizableMVPropertyExists(id, "nested/four", new Value[] {
                vf.createValue("fourA"),
                vf.createValue("fourB")
                });
    }

    /**
     * Change values for existing properties on an authorizable and then verify that the values were set
     * or not as appropriate
     */
    protected void assertAuthorizablePropertiesAgain(String id) throws RepositoryException, RepoInitParsingException {
        final String setPropsA =
                "set properties on authorizable(" + id + ")\n"
                        + "set one to changed_oneA\n"
                        + "default two to changed_twoA\n"
                        + "set nested/one to changed_oneA\n"
                        + "default nested/two to changed_twoA\n"
                        + "set three to changed_threeA, \"changed_threeB\", changed_threeC\n"
                        + "default four to changed_fourA, \"changed_fourB\"\n"
                        + "set nested/three to changed_threeA, \"changed_threeB\", changed_threeC\n"
                        + "default nested/four to changed_fourA, \"changed_fourB\"\n"
                + "end";

        U.parseAndExecute(setPropsA);

        U.assertAuthorizableSVPropertyExists(id, "one", vf.createValue("changed_oneA"));
        U.assertAuthorizableSVPropertyExists(id, "nested/one", vf.createValue("changed_oneA"));
        U.assertAuthorizableSVPropertyExists(id, "two", vf.createValue("twoA"));
        U.assertAuthorizableSVPropertyExists(id, "nested/two", vf.createValue("twoA"));
        U.assertAuthorizableMVPropertyExists(id, "three", new Value[] {
                vf.createValue("changed_threeA"),
                vf.createValue("changed_threeB"),
                vf.createValue("changed_threeC")
                });
        U.assertAuthorizableMVPropertyExists(id, "nested/three", new Value[] {
                vf.createValue("changed_threeA"),
                vf.createValue("changed_threeB"),
                vf.createValue("changed_threeC")
                });
        U.assertAuthorizableMVPropertyExists(id, "four", new Value[] {
                vf.createValue("fourA"),
                vf.createValue("fourB")
                });
        U.assertAuthorizableMVPropertyExists(id, "nested/four", new Value[] {
                vf.createValue("fourA"),
                vf.createValue("fourB")
                });
    }

    /**
     * Set properties on a subtree of an authorizable and then verify that the values were set
     */
    protected void assertAuthorizableSubTreeProperties(String id)
            throws RepositoryException, RepoInitParsingException {
        final String setPropsA =
                "set properties on authorizable(" + id + ")/nested\n"
                        + "set one to oneA\n"
                        + "default two to twoA\n"
                + "end";

        U.parseAndExecute(setPropsA);

        U.assertAuthorizableSVPropertyExists(id, "nested/one", vf.createValue("oneA"));
        U.assertAuthorizableSVPropertyExists(id, "nested/two", vf.createValue("twoA"));
    }

    /**
     * Change values for existing properties on a subtree of an authorizable and then verify 
     * that the values were set or not as appropriate
     */
    protected void assertAuthorizableSubTreePropertiesAgain(String id)
            throws RepositoryException, RepoInitParsingException {
        final String setPropsA =
                "set properties on authorizable(" + id + ")/nested\n"
                        + "set one to changed_oneA\n"
                        + "default two to changed_twoA\n"
                + "end";

        U.parseAndExecute(setPropsA);

        U.assertAuthorizableSVPropertyExists(id, "nested/one", vf.createValue("changed_oneA"));
        U.assertAuthorizableSVPropertyExists(id, "nested/two", vf.createValue("twoA"));
    }

}
