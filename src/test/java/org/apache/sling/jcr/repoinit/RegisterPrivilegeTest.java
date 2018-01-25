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

import org.apache.jackrabbit.api.JackrabbitWorkspace;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.jcr.RepositoryException;
import javax.jcr.security.Privilege;
import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegisterPrivilegeTest {
    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private TestUtil U;

    @Before
    public void setup() throws RepositoryException, RepoInitParsingException {
        U = new TestUtil(context);
        U.parseAndExecute("register privilege withoutabstract_withoutaggregates1");
        U.parseAndExecute("register privilege withoutabstract_withoutaggregates2");
        U.parseAndExecute("register privilege withoutabstract_withoutaggregates3");
        U.parseAndExecute("register privilege withabstract_withoutaggregates as abstract");
        U.parseAndExecute("register privilege withoutabstract_withaggregates with withoutabstract_withoutaggregates1,withoutabstract_withoutaggregates2");
        U.parseAndExecute("register privilege withabstract_withaggregates as abstract with withoutabstract_withoutaggregates1,withoutabstract_withoutaggregates3");
    }

    @After
    public void cleanup() throws RepositoryException, RepoInitParsingException {
        U.cleanup();
    }

    @Test
    public void testRegisterPrivilegeWithoutAbstractWithoutAggregates() throws Exception {
        Privilege privilege = ((JackrabbitWorkspace) U.getAdminSession().getWorkspace()).getPrivilegeManager().getPrivilege("withoutabstract_withoutaggregates1");

        assertFalse(privilege.isAbstract());
        assertTrue(privilege.getDeclaredAggregatePrivileges().length == 0);
    }

    @Test
    public void testRegisterPrivilegeWitAbstractWithoutAggregates() throws Exception {
        Privilege privilege = ((JackrabbitWorkspace) U.getAdminSession().getWorkspace()).getPrivilegeManager().getPrivilege("withabstract_withoutaggregates");

        assertTrue(privilege.isAbstract());
        assertTrue(privilege.getDeclaredAggregatePrivileges().length == 0);
    }

    @Test
    public void testRegisterPrivilegeWithoutAbstractWithAggregates() throws Exception {
        Privilege privilege = ((JackrabbitWorkspace) U.getAdminSession().getWorkspace()).getPrivilegeManager().getPrivilege("withoutabstract_withaggregates");

        assertFalse(privilege.isAbstract());
        assertTrue(privilege.getDeclaredAggregatePrivileges().length == 2);

        Set<String> names = new HashSet<>();
        for (Privilege p : privilege.getDeclaredAggregatePrivileges()) {
            names.add(p.getName());
        }
        assertEquals(names, new HashSet<String>(asList("withoutabstract_withoutaggregates1","withoutabstract_withoutaggregates2")));
    }

    @Test
    public void testRegisterPrivilegeWitAbstractWithAggregates() throws Exception {
        Privilege privilege = ((JackrabbitWorkspace) U.getAdminSession().getWorkspace()).getPrivilegeManager().getPrivilege("withabstract_withaggregates");

        assertTrue(privilege.isAbstract());
        assertTrue(privilege.getDeclaredAggregatePrivileges().length == 2);

        Set<String> names = new HashSet<>();
        for (Privilege p : privilege.getDeclaredAggregatePrivileges()) {
            names.add(p.getName());
        }
        assertEquals(names, new HashSet<String>(asList("withoutabstract_withoutaggregates1","withoutabstract_withoutaggregates3")));
    }

}
