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

@Component(service = UpdateService.class)
public class UpdateServiceImpl implements UpdateService {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateServiceImpl.class);

    @Reference
    private QueryBuilder queryBuilder;

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
                    // Direct path resolution for single-node copy
                    copySingleNode(request, resolver, results);
                } else {
                    // Query-based copy operations
                    processCopyOperation(request, nodes, usesNodeName, results);
                }
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

    private Resource getModifiableTarget(Resource node, boolean pageOnly) {
        Resource target = pageOnly ? node.getChild("jcr:content") : node;
        if (target != null && target.getResourceType().equals("cq:Page")) {
            target = target.getChild("jcr:content");
        }
        return target;
    }

    private boolean matchesNode(UpdateRequest request, Resource node) {
        return !"node".equals(request.matchType) || request.jcrNodeName == null || request.jcrNodeName.isEmpty() || node.getName().equals(request.jcrNodeName);
    }

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
