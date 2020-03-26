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

import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;
import javax.jcr.Value;
import java.io.IOException;


/** Test the setting of properties on nodes */
public class SetPropertiesTest {
    
    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);
    
    private TestUtil U;
    final String path1 = "/one/two/three";
    final String path2 = "/one/two/four";

    @Before
    public void setup() throws RepositoryException, IOException, RepoInitParsingException {
        U = new TestUtil(context);
        RepositoryUtil.registerSlingNodeTypes(U.adminSession);
        U.parseAndExecute("create path " + path1);
        U.assertNodeExists(path1);
        U.parseAndExecute("create path " + path2);
        U.assertNodeExists(path2);
    }

    @Test
    public void setStringPropertyTest() throws Exception {
        U.parseAndExecute("set properties on " + path1 + " \n set sling:ResourceType{String} to /x/y/z \n end");
        ValueFactory vf = U.adminSession.getValueFactory();
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
        ValueFactory vf = U.adminSession.getValueFactory();
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

}
