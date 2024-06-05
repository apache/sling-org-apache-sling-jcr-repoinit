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
package org.apache.sling.jcr.repoinit.impl;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.ConstraintViolationException;

import java.util.Collections;
import java.util.List;

import org.apache.sling.repoinit.parser.operations.AddMixins;
import org.apache.sling.repoinit.parser.operations.CreatePath;
import org.apache.sling.repoinit.parser.operations.EnsureNodes;
import org.apache.sling.repoinit.parser.operations.PathSegmentDefinition;
import org.apache.sling.repoinit.parser.operations.PropertyLine;
import org.apache.sling.repoinit.parser.operations.RemoveMixins;
import org.apache.sling.repoinit.parser.operations.SetProperties;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OperationVisitor which processes only operations related to nodes (but not its properties)
 * @see NodePropertiesVisitor NodePropertiesVisitor, for operations related to node properties
 */
public class NodeVisitor extends DoNothingVisitor {
    private static final Logger slog = LoggerFactory.getLogger(NodeVisitor.class);

    /**
     * Create a visitor using the supplied JCR Session.
     *
     * @param s must have sufficient rights to create nodes
     */
    protected NodeVisitor(Session s) {
        super(s);
    }

    @Override
    public void visitEnsureNodes(EnsureNodes en) {
        createNodes(en.getDefinitions(), en.getPropertyLines(), true);
    }

    @Override
    public void visitCreatePath(CreatePath cp) {
        createNodes(cp.getDefinitions(), cp.getPropertyLines(), false);
    }

    private void createNodes(
            List<PathSegmentDefinition> pathSegmentDefinitions, List<PropertyLine> propertyLines, boolean strict) {
        StringBuilder parentPathBuilder = new StringBuilder();
        for (PathSegmentDefinition psd : pathSegmentDefinitions) {
            String parentPath = parentPathBuilder.toString();
            final String fullPath = String.format("%s/%s", parentPath, psd.getSegment());
            try {
                final Node node;
                if (strict) {
                    if (session.nodeExists(fullPath)) {
                        log.info("Node at {} already exists, checking/adjusting its types", fullPath);
                        node = session.getNode(fullPath);
                        if (psd.getPrimaryType() != null
                                && !node.getPrimaryNodeType().getName().equals(psd.getPrimaryType())) {
                            log.info("Adjusting primary type of node {} to {}", fullPath, psd.getPrimaryType());
                            node.setPrimaryType(psd.getPrimaryType());
                        }
                    } else if (!session.propertyExists(fullPath)) {
                        final Node parent = parentPath.equals("") ? session.getRootNode() : session.getNode(parentPath);
                        log.info("Creating node {} with primary type {}", fullPath, psd.getPrimaryType());
                        node = addChildNode(parent, psd);

                    } else {
                        throw new RepoInitException(
                                "There is a property with the name of the to be created node already at " + fullPath
                                        + ", therefore bailing out here as potentially not supported by the underlying JCR");
                    }
                } else {
                    if (session.itemExists(fullPath)) {
                        log.info(
                                "Path already exists, nothing to do (and not checking its primary type for now): {}",
                                fullPath);
                        node = null;
                    } else {
                        final Node parent = parentPath.equals("") ? session.getRootNode() : session.getNode(parentPath);
                        log.info("Creating node {} with primary type {}", fullPath, psd.getPrimaryType());
                        node = addChildNode(parent, psd);
                    }
                }

                if (node != null) {
                    List<String> mixins = psd.getMixins();
                    if (mixins != null) {
                        log.info("Adding mixins {} to node {}", mixins, fullPath);
                        for (String mixin : mixins) {
                            node.addMixin(mixin);
                        }
                    }
                }
            } catch (Exception e) {
                report(e, "CreatePath execution failed at " + psd + ": " + e);
            }
            parentPathBuilder.append("/").append(psd.getSegment());
        }
        if (!propertyLines.isEmpty()) {
            // delegate to the NodePropertiesVisitor to set the properties
            SetProperties sp =
                    new SetProperties(Collections.singletonList(parentPathBuilder.toString()), propertyLines);
            NodePropertiesVisitor npv = new NodePropertiesVisitor(session);
            npv.visitSetProperties(sp);
        }
        try {
            if (session.hasPendingChanges()) {
                session.save();
            }
        } catch (Exception e) {
            report(e, "Session.save failed: " + e);
        }
    }

    @Override
    public void visitAddMixins(AddMixins am) {
        List<String> paths = am.getPaths();
        if (paths != null) {
            for (String absPath : paths) {
                try {
                    if (!session.itemExists(absPath)) {
                        log.warn("Path does not exist, not adding mixins: {}", absPath);
                    } else {
                        List<String> mixins = am.getMixins();
                        if (mixins != null) {
                            Node node = session.getNode(absPath);
                            log.info("Adding mixins {} to node {}", mixins, absPath);
                            for (String mixin : mixins) {
                                node.addMixin(mixin);
                            }
                        }
                    }
                } catch (Exception e) {
                    report(e, "AddMixins execution failed at " + absPath + ": " + e);
                }
            }
        }
    }

    @Override
    public void visitRemoveMixins(RemoveMixins rm) {
        List<String> paths = rm.getPaths();
        if (paths != null) {
            for (String absPath : paths) {
                try {
                    if (!session.itemExists(absPath)) {
                        log.warn("Path does not exist, not removing mixins: {}", absPath);
                    } else {
                        List<String> mixins = rm.getMixins();
                        if (mixins != null) {
                            Node node = session.getNode(absPath);
                            log.info("Removing mixins {} from node {}", mixins, absPath);
                            for (String mixin : mixins) {
                                node.removeMixin(mixin);
                            }
                        }
                    }
                } catch (Exception e) {
                    report(e, "RemoveMixins execution failed at " + absPath + ": " + e);
                }
            }
        }
    }

    @NotNull
    private static Node addChildNode(@NotNull Node parent, @NotNull PathSegmentDefinition psd)
            throws RepositoryException {
        String primaryType = psd.getPrimaryType();
        if (primaryType == null) {
            try {
                return parent.addNode(psd.getSegment());
            } catch (ConstraintViolationException e) {
                // assume that no default primary type could be detected -> retry with a default
                slog.info("Adding Node without node type failed ('{}'), retry with sling:Folder", e.getMessage());
                return parent.addNode(psd.getSegment(), "sling:Folder");
            }
        } else {
            return parent.addNode(psd.getSegment(), psd.getPrimaryType());
        }
    }
}
