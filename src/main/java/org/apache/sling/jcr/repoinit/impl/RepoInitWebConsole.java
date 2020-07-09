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
package org.apache.sling.jcr.repoinit.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.felix.webconsole.AbstractWebConsolePlugin;
import org.apache.felix.webconsole.WebConsoleConstants;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.jcr.repoinit.JcrRepoInitOpsProcessor;
import org.apache.sling.repoinit.parser.RepoInitParser;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.repoinit.parser.operations.Operation;
import org.apache.sling.xss.XSSAPI;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = Servlet.class, property = {
        Constants.SERVICE_DESCRIPTION + "=Apache Sling Service User Manager Web Console Plugin",
        WebConsoleConstants.PLUGIN_LABEL + "=" + RepoInitWebConsole.LABEL,
        WebConsoleConstants.PLUGIN_TITLE + "=" + RepoInitWebConsole.TITLE,
        WebConsoleConstants.PLUGIN_CATEGORY + "=Sling" })
public class RepoInitWebConsole extends AbstractWebConsolePlugin {

    private static final Logger log = LoggerFactory.getLogger(RepoInitWebConsole.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private XSSAPI xss;

    @Reference
    private RepoInitParser parser;

    @Reference
    private JcrRepoInitOpsProcessor processor;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory resolverFactory;

    private static final long serialVersionUID = 1L;
    public static final String LABEL = "repoinit";
    public static final String TITLE = "RepoInit";

    private static final String PN_STATEMENTS = "statements";
    private static final String PN_EXECUTE = "execute";

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    protected void renderContent(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Map<String, String> parameters = readRequest(request);
        parameters.put("result", "");
        writeResponse(response, parameters);

    }

    private Map<String, String> readRequest(HttpServletRequest request) {

        Map<String, String> parameters = new HashMap<>();
        String statements = Optional.ofNullable(request.getParameter(PN_STATEMENTS)).orElse("");
        parameters.put(PN_STATEMENTS, xss.encodeForHTML(statements));
        parameters.put(PN_EXECUTE, "true".equals(request.getParameter(PN_EXECUTE)) ? "checked" : "");
        return parameters;
    }

    private void writeResponse(HttpServletResponse response, Map<String, String> parameters) throws IOException {
        String consoleHtml = IOUtils.toString(this.getBundle().getEntry("console.html").openStream(),
                StandardCharsets.UTF_8);
        StrSubstitutor sub = new StrSubstitutor(parameters);
        IOUtils.write(sub.replace(consoleHtml), response.getWriter());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Map<String, String> parameters = readRequest(request);

        StringReader statements = Optional.ofNullable(request.getParameter(PN_STATEMENTS)).map(StringReader::new)
                .orElse(new StringReader(""));
        List<String> messages = new ArrayList<>();
        List<Operation> operations = null;
        try {
            operations = parser.parse(statements);
            messages.add("PARSING SUCCEEDED: ");
            operations.stream().map(Operation::toString).forEach(messages::add);
        } catch (RepoInitParsingException e) {
            messages.add("PARSING FAILED: ");
            messages.add(e.toString());
        }

        if (operations != null && "true".equals(request.getParameter(PN_EXECUTE))) {
            messages.add("");
            Session session;
            try {
                session = getResourceResolver(request).adaptTo(Session.class);
                processor.apply(session, operations);
                session.save();
                messages.add("EXECUTION SUCCEEDED!");
            } catch (Exception e) {
                messages.add("EXECUTION FAILED: ");
                messages.add(e.toString());
            }
        }
        parameters.put("result", messages.stream().collect(Collectors.joining("\n")));

        PrintWriter pw = startResponse(request, response);

        renderTopNavigation(request, pw);

        pw.println("<div id='content'>");
        writeResponse(response, parameters);
        pw.println("</div>");

        endResponse(pw);
    }

    private boolean needsAdministrativeResolver(HttpServletRequest request) {
        Object resolver = request.getAttribute("org.apache.sling.auth.core.ResourceResolver");
        return !(resolver instanceof ResourceResolver);
    }

    @SuppressWarnings("deprecation")
    private ResourceResolver getResourceResolver(HttpServletRequest request) throws LoginException {
        ResourceResolver resolver = null;
        if (needsAdministrativeResolver(request)) {
            try {
                log.warn("Resource resolver not available in request, falling back to adminstrative resource resolver");
                resolver = resolverFactory.getAdministrativeResourceResolver(null);
            } catch (LoginException le) {
                throw new LoginException(
                        "Unable to get Administrative Resource Resolver, add the bundle org.apache.sling.jcr.repoinit.parser.webconsole in the Apache Sling Login Admin Whitelist",
                        le);
            }
        } else {
            resolver = (ResourceResolver) request.getAttribute("org.apache.sling.auth.core.ResourceResolver");
        }

        return resolver;
    }

}