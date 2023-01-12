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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;

import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.jcr.repoinit.impl.RepoInitException;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Test the creation of paths with specific node types */
@RunWith(Parameterized.class)
public class CreatePathsTest {

    @Parameters(name = "{1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][] { 
                 { Boolean.TRUE, "ensure nodes" }, { Boolean.FALSE, "create path" }
           });
    }

    private final String baseCreateNodesStatement;
    private final boolean strict;

    public CreatePathsTest(boolean strict, String createNodesStatement) {
        this.strict = strict;
        this.baseCreateNodesStatement = createNodesStatement + " ";
    }

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);
    
    private TestUtil U;

    private static final String TEST_ID = UUID.randomUUID().toString();
    private static final String NS_PREFIX = CreatePathsTest.class.getSimpleName();
    private static final String NS_URI = "uri:" + NS_PREFIX + ":" + TEST_ID;

    @Before
    public void setup() throws RepositoryException, IOException {
        U = new TestUtil(context);
        RepositoryUtil.registerSlingNodeTypes(U.adminSession);
    }

    /**
     * @param path the path to create
     */
    protected void assertSimplePath(final String path) throws RepositoryException, RepoInitParsingException {
        U.parseAndExecute(baseCreateNodesStatement + path);
        U.assertNodeExists(path);
    }

    @Test
    public void createSimplePath() throws Exception {
        assertSimplePath("/one/two/three");
    }

    @Test
    public void createSimplePathWithNamespace() throws Exception {
        assertSimplePath("/rep:policy/one");
    }

    @Test
    public void createSimplePathWithAtSymbol() throws Exception {
        assertSimplePath("/one/@two/three");
    }

    @Test
    public void createSimplePathWithPlusSymbol() throws Exception {
        assertSimplePath("/one/+two/three");
    }

    @Test
    public void createPathWithTypes() throws Exception {
        final String path = "/four/five(sling:Folder)/six(nt:folder)";
        U.parseAndExecute(baseCreateNodesStatement + path);
        U.assertNodeExists("/four", "nt:unstructured");
        U.assertNodeExists("/four/five", "sling:Folder");
        U.assertNodeExists("/four/five/six", "nt:folder");
    }

    @Test
    public void createPathWithSpecificDefaultType() throws Exception {
        final String path = "/seven/eight(nt:unstructured)/nine";
        U.parseAndExecute(baseCreateNodesStatement + "(sling:Folder) " + path);
        U.assertNodeExists("/seven", "sling:Folder");
        U.assertNodeExists("/seven/eight", "nt:unstructured");
        U.assertNodeExists("/seven/eight/nine", "sling:Folder");
    }

    @Test
    public void createPathWithJcrDefaultType() throws Exception {
        final String path = "/ten/eleven(sling:Folder)/twelve";
        U.parseAndExecute(baseCreateNodesStatement + path);
        U.assertNodeExists("/ten", "nt:unstructured");
        U.assertNodeExists("/ten/eleven", "sling:Folder");
        U.assertNodeExists("/ten/eleven/twelve", "sling:Folder");
    }

    @Test
    public void createPathWithMixins() throws Exception {
        final String path = "/eleven(mixin mix:lockable)/twelve(mixin mix:referenceable,mix:shareable)/thirteen";
        U.parseAndExecute(baseCreateNodesStatement + path);
        U.assertNodeExists("/eleven", Collections.singletonList("mix:lockable"));
        U.assertNodeExists("/eleven/twelve", Arrays.asList("mix:shareable", "mix:referenceable"));
    }

    @Test
    public void createPathWithJcrDefaultTypeAndMixins() throws Exception {
        final String path = "/twelve/thirteen(mixin mix:lockable)/fourteen";
        U.parseAndExecute(baseCreateNodesStatement + "(nt:unstructured)" + path);
        U.assertNodeExists("/twelve", "nt:unstructured", Collections.<String>emptyList());
        U.assertNodeExists("/twelve/thirteen", "nt:unstructured", Collections.singletonList("mix:lockable"));
        U.assertNodeExists("/twelve/thirteen/fourteen", "nt:unstructured", Collections.<String>emptyList());
    }

    @Test
    public void createPathWithJcrTypeAndMixins() throws Exception {
        final String path = "/thirteen(nt:unstructured)/fourteen(nt:unstructured mixin mix:lockable)/fifteen(mixin mix:lockable)";
        U.parseAndExecute(baseCreateNodesStatement + path);
        U.assertNodeExists("/thirteen", "nt:unstructured", Collections.<String>emptyList());
        U.assertNodeExists("/thirteen/fourteen", "nt:unstructured", Collections.singletonList("mix:lockable"));
        U.assertNodeExists("/thirteen/fourteen/fifteen", "nt:unstructured", Collections.singletonList("mix:lockable"));
    }
    
    @Test
    public void createPathNoDefaultPrimaryType() throws Exception {
        U.adminSession.getRootNode().addNode("folder", "nt:folder");
        U.parseAndExecute(baseCreateNodesStatement + "/folder/subfolder/subfolder2");
        
        Node subFolder = U.adminSession.getNode("/folder/subfolder");
        assertEquals("sling:Folder", subFolder.getPrimaryNodeType().getName());

        Node subFolder2 = subFolder.getNode("subfolder2");
        assertEquals("sling:Folder", subFolder2.getPrimaryNodeType().getName());
    }

    @Test
    public void createPathWherePropertyExists() throws Exception {
        final Node folder = U.adminSession.getRootNode().addNode("cpwpe", "nt:unstructured");
        folder.setProperty("nodeOrProperty", "someValue");
        folder.getSession().save();
        final String fullPath = "/cpwpe/nodeOrProperty";
        if (strict) {
            try {
                U.parseAndExecute(baseCreateNodesStatement + fullPath);
                fail("Creating a node at a path where a property exists already should have thrown an exception");
            } catch (RepoInitException e) {
                assertTrue(e.getMessage().contains("There is a property with the name of the to be created node already at"));
            }
        } else {
            U.parseAndExecute("create path " + fullPath);
            assertTrue(U.adminSession.propertyExists(fullPath));
        }
    }
 
    /**
     * SLING-11736 adjust existing node types
     */
    @Test
    public void createPathWhereNodeExists() throws Exception {
        final Node folder = U.adminSession.getRootNode().addNode("mynode", "nt:folder");
        folder.addMixin("mix:mimeType");
        folder.getSession().save();
        final String fullPath = "/mynode";
        U.parseAndExecute(baseCreateNodesStatement + fullPath + " (nt:unstructured mixin mix:mimeType,mix:language)");
        if (strict) {
            U.assertNodeExists("/mynode", "nt:unstructured", Arrays.asList("mix:mimeType", "mix:language"));
        } else {
            U.assertNodeExists("/mynode", "nt:folder", Arrays.asList("mix:mimeType"));
        }
    }

    /**
     * SLING-10740 create path statement for node type with a mandatory property
     */
    @Test
    public void createPathWithMandatoryProperty() throws Exception {
        // register a nodetype with a required property
        U = new TestUtil(context);
        U.parseAndExecute("register namespace (" + NS_PREFIX + ") " + NS_URI);
        String registerNodetypeCndStatement = "register nodetypes\n"
                + "<<===\n"
                + "<<  <" + NS_PREFIX + "='" + NS_URI + "'>\n"
                + "<<  [" + NS_PREFIX + ":foo]\n"
                + "<<    - displayName (String) mandatory\n"
                + "===>>\n";
        U.parseAndExecute(registerNodetypeCndStatement);

        // create the path with the mandatory property populated
        final String path = String.format("/one(%s:foo)", NS_PREFIX);
        String createPathCndStatement = baseCreateNodesStatement + path + " with properties\n"
                + "  default displayName{String} to \"Hello\"\n"
                + "end\n";
        U.parseAndExecute(createPathCndStatement);

        //verify it worked
        U.assertNodeExists("/one");
        ValueFactory vf = U.adminSession.getValueFactory();
        U.assertSVPropertyExists("/one", "displayName", vf.createValue("Hello"));
    }

}
