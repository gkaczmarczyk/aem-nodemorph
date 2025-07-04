/*
 * Copyright © 2025 Gregory Kaczmarczyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.acu.nodemorph.core.services.impl;

import co.acu.nodemorph.core.services.UpdateService;
import co.acu.nodemorph.core.dto.UpdateRequest;
import co.acu.nodemorph.core.dto.UpdateResult;
import co.acu.nodemorph.core.dto.NodeProperty;
import co.acu.nodemorph.core.utils.NodeMorphUtils;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import org.apache.sling.api.resource.*;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import java.util.*;

import static com.day.cq.commons.jcr.JcrConstants.NT_UNSTRUCTURED;

@Component(service = UpdateService.class)
public class UpdateServiceImpl implements UpdateService {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateServiceImpl.class);

    @Reference
    private QueryBuilder queryBuilder;

    /**
     * Processes a node update request by executing the specified operation (add, replace, or copy)
     * on a set of nodes identified via a JCR query. This method orchestrates the entire workflow:
     * querying nodes, applying the operation, and committing changes unless in dry-run mode.
     *
     * @param request the update request containing operation type, target path, properties, and
     *                configuration (e.g., dryRun, pageOnly). Must include a valid ResourceResolver.
     * @return a list of {@link UpdateResult} objects detailing the outcome of the operation for each
     *         affected node, including path, action taken, status (e.g., "Done", "Failed"), and
     *         optional error messages.
     * @throws IllegalArgumentException if the request contains invalid parameters (e.g., malformed query).
     * @throws RuntimeException if query execution fails due to underlying JCR issues.
     */
    @Override
    public List<UpdateResult> processUpdate(UpdateRequest request) {
        List<UpdateResult> results = new ArrayList<>();
        ResourceResolver resolver = request.resolver;

        if (resolver == null) {
            LOG.error("ResourceResolver is null");
            results.add(new UpdateResult(request.path, "Error: No user context", "Failed"));
            return results;
        }

        try {
            Session session = resolver.adaptTo(Session.class);
            Map<String, String> queryParams = NodeMorphUtils.getQueryParamMap(request);
            boolean usesNodeName = queryParams.containsKey("nodename");

            PredicateGroup predicate = PredicateGroup.create(queryParams);
            Query query = queryBuilder.createQuery(predicate, session);

            Iterator<Resource> nodeIterator;
            try {
                nodeIterator = query.getResult().getResources();
            } catch (Exception e) {
                throw new RuntimeException("Query execution failed", e);
            }
            List<Resource> nodes = new ArrayList<>();
            nodeIterator.forEachRemaining(nodes::add);

            // Process Operations
            if ("add".equals(request.operation)) {
                processAddOperation(request, nodes, results);
            } else if ("replace".equals(request.operation)) {
                processReplaceOperation(request, nodes, results);
            } else if ("copy".equals(request.operation)) {
                if (request.copyType == null || request.source == null || request.target == null) {
                    results.add(new UpdateResult(request.path, "Error: Missing copy parameters", "Failed"));
                    return results;
                }

                if ("node".equals(request.copyType) && request.source.contains("/")) {
                    copySingleNode(request, resolver, results);
                } else {
                    processCopyOperation(request, nodes, usesNodeName, results);
                }
            } else if ("create".equals(request.operation)) {
                processCreateOperation(request, nodes, results);
            } else if ("delete".equals(request.operation)) {
                processDeleteOperation(request, nodes, results);
            }

            if (!request.dryRun && !results.stream().allMatch(r -> "Failed".equals(r.status))) {
                resolver.commit();
            }
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid input", e);
            results.add(new UpdateResult(request.path, "Error: Invalid input", "Failed", e.getMessage()));
        } catch (PersistenceException pe) {
            LOG.error("Failed to commit changes", pe);
            results.add(new UpdateResult(request.path, "Error: Save failed", "Failed", pe.getMessage()));
        } catch (Exception e) {
            LOG.error("A node operation error has occurred", e);
            results.add(new UpdateResult(request.path, "Error: Unable to complete operation", "Failed", e.getMessage()));
        }

        return results;
    }

    /**
     * Handles the "add" operation by setting or updating properties on the target nodes.
     * Properties are applied to either the node itself or its jcr:content child, depending on the
     * pageOnly flag. Skips nodes that don’t match the request’s matchType criteria.
     *
     * @param request the update request specifying properties to add and configuration (e.g., dryRun, matchType).
     * @param nodes the list of nodes retrieved from the JCR query to process.
     * @param results the list to append operation outcomes to, including success or failure details.
     */
    private void processAddOperation(UpdateRequest request, List<Resource> nodes, List<UpdateResult> results) {
        List<NodeProperty> propsToAdd = request.getUpdateProperties();
        if (propsToAdd.isEmpty()) {
            results.add(new UpdateResult(request.path, "No properties to add", "Skipped"));
            return;
        }

        for (Resource node : nodes) {
            Resource target = getModifiableTarget(node, request.pageOnly);
            if (target == null) {
                results.add(new UpdateResult(node.getPath(), "Error: No modifiable target node", "Failed"));
                continue;
            }

            String path = target.getPath();
            ModifiableValueMap props = target.adaptTo(ModifiableValueMap.class);
            if (props == null) {
                results.add(new UpdateResult(path, "Error: Cannot modify node", "Failed"));
                continue;
            }

            if (!matchesNode(request, node)) {
                continue;
            }

            for (NodeProperty prop : propsToAdd) {
                String key = prop.getKey();
                Object value = prop.getValue();
                String action = String.format("Set %s=%s", key, value);
                updateProperty(request, path, props, key, value, action, results);
            }
        }
    }

    /**
     * Executes the "replace" operation by finding and replacing a specific property value across
     * target nodes. Only replaces the property if its current value matches the "find" string.
     *
     * @param request the update request containing propName, find, replace values, and configuration.
     * @param nodes the list of nodes to inspect and potentially modify.
     * @param results the list to record the outcome of each replacement attempt.
     */
    private void processReplaceOperation(UpdateRequest request, List<Resource> nodes, List<UpdateResult> results) {
        if (request.propName == null || request.find == null || request.replace == null) {
            results.add(new UpdateResult(request.path, "Error: Missing replace parameters", "Failed"));
            return;
        }

        for (Resource node : nodes) {
            Resource target = getModifiableTarget(node, request.pageOnly);
            if (target == null) {
                results.add(new UpdateResult(node.getPath(), "Error: No modifiable target node", "Failed"));
                continue;
            }

            String path = target.getPath();
            ModifiableValueMap props = target.adaptTo(ModifiableValueMap.class);
            if (props == null) {
                results.add(new UpdateResult(path, "Error: Cannot modify node", "Failed"));
                continue;
            }

            Object currentValue = props.get(request.propName);
            if (currentValue != null && currentValue.toString().equals(request.find)) {
                String action = String.format("Replace %s: %s → %s", request.propName, request.find, request.replace);
                updateProperty(request, path, props, request.propName, request.replace, action, results);
            }
        }
    }

    /**
     * Manages the "copy" operation for multiple nodes, supporting node, property, or property-to-path
     * copying based on the copyType. Resolves source and target paths dynamically unless nodename
     * is used in the query.
     *
     * @param request the update request specifying copyType, source, target, and configuration.
     * @param nodes the list of nodes to process for copying.
     * @param usesNodeName indicates if the query uses a nodename filter, affecting path resolution.
     * @param results the list to append copy operation outcomes to.
     * @throws PersistenceException if node creation or property updates fail during commit.
     */
    private void processCopyOperation(UpdateRequest request, List<Resource> nodes, boolean usesNodeName, List<UpdateResult> results) throws PersistenceException {
        for (Resource node : nodes) {
            Resource base = request.pageOnly ? node.getChild("jcr:content") : node;
            if (base == null) {
                results.add(new UpdateResult(node.getPath(), "Error: No base node", "Failed"));
                continue;
            }

            String basePath = base.getPath();
            switch (request.copyType) {
                case "node":
                    String sourcePath = usesNodeName ? basePath : NodeMorphUtils.resolvePath(basePath, request.source, request.resolver);
                    String targetPath = usesNodeName ? NodeMorphUtils.resolvePath(base.getParent().getPath(), request.target, request.resolver)
                            : NodeMorphUtils.resolvePath(basePath, request.target, request.resolver);
                    copyNode(request, basePath, sourcePath, targetPath, results);
                    break;
                case "property":
                    copyProperty(request, base, results);
                    break;
                case "propertyToPath":
                    copyPropertyToPath(request, base, results);
                    break;
                default:
                    results.add(new UpdateResult(basePath, "Error: Unknown copy type: " + request.copyType, "Failed"));
            }
        }
    }

    /**
     * Copies a single node from a source path to a target path within the request’s base path.
     * Used when the copy operation specifies a direct path in the source (e.g., "skitouring/jcr:content").
     *
     * @param request the update request with source and target paths relative to the base path.
     * @param resolver the ResourceResolver to access and modify the JCR repository.
     * @param results the list to record the copy operation’s outcome.
     * @throws PersistenceException if node creation or commit fails.
     */
    private void copySingleNode(UpdateRequest request, ResourceResolver resolver, List<UpdateResult> results) throws PersistenceException {
        String sourcePath = NodeMorphUtils.resolvePath(request.path, request.source, resolver);
        Resource sourceRes = resolver.getResource(sourcePath);
        if (sourceRes == null) {
            results.add(new UpdateResult(request.path, "Error: Source node not found: " + sourcePath, "Failed"));
            LOG.error("Source node not found: {}", sourcePath);
            return;
        }

        String targetPath = NodeMorphUtils.resolvePath(request.path, request.target, resolver);
        String targetParentPath = targetPath.substring(0, targetPath.lastIndexOf("/"));
        Resource targetParent = resolver.getResource(targetParentPath);
        if (targetParent == null) {
            results.add(new UpdateResult(request.path, "Error: Target parent does not exist: " + targetParentPath, "Failed"));
            LOG.error("Target parent not found: {}", targetParentPath);
            return;
        }

        String targetNodeName = targetPath.substring(targetPath.lastIndexOf("/") + 1);
        String action = String.format("Copy node %s to %s", request.source, request.target);
        if (request.dryRun) {
            results.add(new UpdateResult(request.path, action, "Pending"));
        } else {
            resolver.create(targetParent, targetNodeName, sourceRes.getValueMap());
            resolver.commit();
            results.add(new UpdateResult(request.path, action, "Done"));
            LOG.debug("Copied {} to {}", sourcePath, targetPath);
        }
    }

    /**
     * Copies a node from a source path to a target path relative to a base node. Ensures the target
     * parent exists before creating the new node.
     *
     * @param request the update request containing copy configuration.
     * @param basePath the base path of the node being processed.
     * @param sourcePath the absolute path of the source node to copy.
     * @param targetPath the absolute path where the node should be copied.
     * @param results the list to append the copy outcome to.
     * @throws PersistenceException if node creation fails.
     */
    private void copyNode(UpdateRequest request, String basePath, String sourcePath, String targetPath, List<UpdateResult> results) throws PersistenceException {
        Resource sourceRes = request.resolver.getResource(sourcePath);
        if (sourceRes == null) {
            results.add(new UpdateResult(basePath, "Error: Source node not found: " + sourcePath, "Failed"));
            return;
        }

        String targetParentPath = targetPath.substring(0, targetPath.lastIndexOf("/"));
        Resource targetParent = request.resolver.getResource(targetParentPath);
        if (targetParent == null) {
            results.add(new UpdateResult(basePath, "Error: Target parent does not exist: " + targetParentPath, "Failed"));
            return;
        }

        String targetNodeName = targetPath.substring(targetPath.lastIndexOf("/") + 1);
        String action = String.format("Copy node %s to %s", request.source, request.target);
        if (request.dryRun) {
            results.add(new UpdateResult(basePath, action, "Pending"));
        } else {
            request.resolver.create(targetParent, targetNodeName, sourceRes.getValueMap());
            results.add(new UpdateResult(basePath, action, "Done"));
        }
    }

    /**
     * Copies a property’s value from one key to another within the same node. Updates cq:lastModified
     * and cq:lastModifiedBy for cq:PageContent nodes.
     *
     * @param request the update request specifying source and target property names.
     * @param base the resource whose properties are being modified.
     * @param results the list to record the copy operation’s outcome.
     */
    private void copyProperty(UpdateRequest request, Resource base, List<UpdateResult> results) {
        ModifiableValueMap props = base.adaptTo(ModifiableValueMap.class);
        if (props == null) {
            results.add(new UpdateResult(base.getPath(), "Error: Cannot modify node", "Failed"));
            return;
        }

        Object propValue = props.get(request.source);
        if (propValue == null) {
            results.add(new UpdateResult(base.getPath(), "Error: Source property not found: " + request.source, "Failed"));
            return;
        }

        String action = String.format("Copy property %s=%s to %s", request.source, propValue, request.target);
        if (request.dryRun) {
            results.add(new UpdateResult(base.getPath(), action, "Pending"));
        } else {
            props.put(request.target, propValue);
            if (base.getResourceType().equals("cq:PageContent")) {
                props.put("cq:lastModified", Calendar.getInstance());
                props.put("cq:lastModifiedBy", request.resolver.getUserID());
            }
            results.add(new UpdateResult(base.getPath(), action, "Done"));
        }
    }

    /**
     * Copies a property from the base node to a new property on a target node created at a specified
     * path. Creates intermediate nodes if necessary and updates cq:lastModified metadata for pages.
     *
     * @param request the update request with source property and target path.
     * @param base the resource providing the source property value.
     * @param results the list to append the operation outcome to.
     * @throws PersistenceException if node creation or property updates fail.
     */
    private void copyPropertyToPath(UpdateRequest request, Resource base, List<UpdateResult> results) throws PersistenceException {
        String basePath = base.getPath();
        String sourcePath = basePath + "/" + request.source;
        String targetPath = basePath + "/" + request.target;

        Resource targetParent = request.resolver.getResource(targetPath.substring(0, targetPath.lastIndexOf("/")));
        if (targetParent == null) {
            if (!request.dryRun) {
                request.resolver.create(request.resolver.getResource(basePath),
                        request.target.substring(0, request.target.lastIndexOf("/")),
                        new HashMap<>());
                targetParent = request.resolver.getResource(targetPath.substring(0, targetPath.lastIndexOf("/")));
            }
        }
        if (targetParent == null) {
            results.add(new UpdateResult(basePath, "Error: Cannot create target parent: " + targetPath, "Failed"));
            return;
        }

        ModifiableValueMap targetProps = targetParent.adaptTo(ModifiableValueMap.class);
        Object sourcePropValue = base.adaptTo(ValueMap.class).get(request.source);
        if (sourcePropValue == null) {
            results.add(new UpdateResult(basePath, "Error: Source property not found: " + request.source, "Failed"));
            return;
        }

        String targetPropName = targetPath.substring(targetPath.lastIndexOf("/") + 1);
        String action = String.format("Copy property %s=%s to %s", request.source, sourcePropValue, request.target);
        if (request.dryRun) {
            results.add(new UpdateResult(basePath, action, "Pending"));
        } else {
            targetProps.put(targetPropName, sourcePropValue);
            if (targetParent.getResourceType().equals("cq:PageContent")) {
                targetProps.put("cq:lastModified", Calendar.getInstance());
                targetProps.put("cq:lastModifiedBy", request.resolver.getUserID());
            }
            results.add(new UpdateResult(basePath, action, "Done"));
        }
    }

    private void processCreateOperation(UpdateRequest request, List<Resource> nodes, List<UpdateResult> results) {
        if (request.newNodeName == null || request.newNodeName.isEmpty()) {
            results.add(new UpdateResult(request.path, "Error: Missing newNodeName", "Failed"));
            return;
        }

        String matchKey = null;
        String matchValue = null;
        if (request.parentMatchCondition != null && !request.parentMatchCondition.trim().isEmpty()) {
            Map<String, Object> matchProps = NodeMorphUtils.parseProperties(request.parentMatchCondition);
            if (!matchProps.isEmpty()) {
                Map.Entry<String, Object> entry = matchProps.entrySet().iterator().next();
                matchKey = entry.getKey();
                Object val = entry.getValue();
                if (val instanceof String) {
                    matchValue = (String) val;
                } else if (val instanceof String[] && ((String[]) val).length > 0) {
                    matchValue = ((String[]) val)[0];
                }
            }
        }

        for (Resource node : nodes) {
            if (matchKey != null && matchValue != null) {
                ValueMap nodeProps = node.getValueMap();
                Object val = nodeProps.get(matchKey);
                if (val == null || !val.toString().equals(matchValue)) {
                    continue;
                }
            }

            String newNodeName = request.newNodeName;
//            String type = request.newNodeType != null && !request.newNodeType.isEmpty() ? request.newNodeType : NT_UNSTRUCTURED;
            String type = Optional.ofNullable(request.newNodeType)
                    .filter(s -> !s.isBlank())
                    .orElse(NT_UNSTRUCTURED);

            try {
                Resource existing = node.getChild(newNodeName);
                if (existing != null) {
                    results.add(new UpdateResult(existing.getPath(), "Skipped: Node already exists", "Skipped"));
                    continue;
                }

                if (request.dryRun) {
                    results.add(new UpdateResult(node.getPath() + "/" + newNodeName, "Would create " + type, "Pending"));
                    continue;
                }

                Map<String, Object> props = new HashMap<>();
                props.put("jcr:primaryType", type);
                props.putAll(request.getNewNodeProperties());

                Resource created = request.resolver.create(node, newNodeName, props);
                results.add(new UpdateResult(created.getPath(), "Created node of type " + type, "Done"));

            } catch (PersistenceException e) {
                results.add(new UpdateResult(node.getPath(), "Error: " + e.getMessage(), "Failed"));
            }
        }
    }

    /**
     * Executes the "delete" operation by removing specified properties from nodes under the given path.
     * The properties to delete are provided as a comma-separated list in the request’s propNames field
     * (e.g., "key1,key2"). Iterates over all matching nodes, targeting either the node itself or its
     * jcr:content child based on the pageOnly flag, and removes the properties if they exist.
     *
     * @param request the update request containing the path, propNames (comma-separated property names),
     *                and configuration (e.g., dryRun, pageOnly).
     * @param nodes the list of nodes retrieved from the JCR query to process.
     * @param results the list to append deletion outcomes to, including success or failure details.
     */
    private void processDeleteOperation(UpdateRequest request, List<Resource> nodes, List<UpdateResult> results) {
        if (request.propNames == null || request.propNames.trim().isEmpty()) {
            results.add(new UpdateResult(request.path, "Error: No properties specified for deletion", "Failed"));
            return;
        }

        String[] propertiesToDelete = request.propNames.split(",");
        for (int i = 0; i < propertiesToDelete.length; i++) {
            propertiesToDelete[i] = propertiesToDelete[i].trim();
        }

        for (Resource node : nodes) {
            Resource target = getModifiableTarget(node, request.pageOnly);
            if (target == null) {
                results.add(new UpdateResult(node.getPath(), "Error: No modifiable target node", "Failed"));
                continue;
            }

            String path = target.getPath();
            ModifiableValueMap props = target.adaptTo(ModifiableValueMap.class);
            if (props == null) {
                results.add(new UpdateResult(path, "Error: Cannot modify node", "Failed"));
                continue;
            }

            boolean deletedAny = false;
            StringBuilder action = new StringBuilder("Delete properties: ");
            for (String propName : propertiesToDelete) {
                if (props.containsKey(propName)) {
                    if (deletedAny) action.append(", ");
                    action.append(propName);
                    if (!request.dryRun) {
                        props.remove(propName);
                        if (props.containsKey("jcr:primaryType") && "cq:PageContent".equals(props.get("jcr:primaryType"))) {
                            props.put("cq:lastModified", Calendar.getInstance());
                            props.put("cq:lastModifiedBy", request.resolver.getUserID());
                        }
                    }
                    deletedAny = true;
                }
            }

            if (deletedAny) {
                results.add(new UpdateResult(path, action.toString(), request.dryRun ? "Pending" : "Done"));
            }
        }
    }

    /**
     * Determines the target resource for property modifications. For cq:Page nodes, redirects to
     * jcr:content unless pageOnly explicitly limits to that child node.
     *
     * @param node the resource to evaluate as the modification target.
     * @param pageOnly if true, forces the target to be the jcr:content child; if false, may still
     *                 redirect cq:Page to jcr:content.
     * @return the modifiable target resource, or null if no valid target exists (e.g., no jcr:content).
     */
    private Resource getModifiableTarget(Resource node, boolean pageOnly) {
        Resource target = pageOnly ? node.getChild("jcr:content") : node;
        if (target != null && target.getResourceType().equals("cq:Page")) {
            target = target.getChild("jcr:content");
        }
        return target;
    }

    /**
     * Checks if a node matches the request’s matchType criteria, specifically for "node" type where
     * a jcrNodeName filter is applied.
     *
     * @param request the update request with matchType and optional jcrNodeName.
     * @param node the resource to check against the match criteria.
     * @return true if the node matches (or no specific match is required), false otherwise.
     */
    private boolean matchesNode(UpdateRequest request, Resource node) {
        return !"node".equals(request.matchType) || request.jcrNodeName == null || request.jcrNodeName.isEmpty() || node.getName().equals(request.jcrNodeName);
    }

    /**
     * Updates a single property on a node, adding metadata (cq:lastModified, cq:lastModifiedBy) for
     * cq:PageContent nodes. Supports dry-run mode by reporting "Pending" without modifying the node.
     *
     * @param request the update request with dryRun flag and resolver for user info.
     * @param path the path of the node being modified.
     * @param props the ModifiableValueMap to update with the new property.
     * @param key the property name to set.
     * @param value the value to assign to the property.
     * @param action a descriptive string of the operation (e.g., "Set key=value").
     * @param results the list to append the update outcome to.
     */
    private void updateProperty(UpdateRequest request, String path, ModifiableValueMap props, String key, Object value, String action, List<UpdateResult> results) {
        if (request.dryRun) {
            results.add(new UpdateResult(path, action, "Pending"));
        } else {
            props.put(key, value);
            if (props.containsKey("jcr:primaryType") && "cq:PageContent".equals(props.get("jcr:primaryType"))) {
                props.put("cq:lastModified", Calendar.getInstance());
                props.put("cq:lastModifiedBy", request.resolver.getUserID());
            }
            results.add(new UpdateResult(path, action, "Done"));
        }
    }

}
