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
                List<NodeProperty> propsToAdd = request.getUpdateProperties();

                if (propsToAdd.isEmpty()) {
                    results.add(new UpdateResult(request.path, "No properties to add", "Skipped"));
                    return results;
                }

                for (Resource node : nodes) {
                    Resource target = request.pageOnly ? node.getChild("jcr:content") : node;
                    if (target != null && target.getResourceType().equals("cq:Page")) {
                        target = target.getChild("jcr:content");
                    }
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

                    boolean matches = true;
                    if ("node".equals(request.matchType) && request.jcrNodeName != null && !request.jcrNodeName.isEmpty()) {
                        matches = node.getName().equals(request.jcrNodeName);
                    }
                    if (!matches) {
                        continue;
                    }

                    for (NodeProperty prop : propsToAdd) {
                        String key = prop.getKey();
                        Object value = prop.getValue();
                        String action = String.format("Set %s=%s", key, value);
                        if (request.dryRun) {
                            results.add(new UpdateResult(path, action, "Pending"));
                        } else {
                            props.put(key, value);
                            if (target.getResourceType().equals("cq:PageContent")) {
                                props.put("cq:lastModified", Calendar.getInstance());
                                props.put("cq:lastModifiedBy", resolver.getUserID());
                            }
                            results.add(new UpdateResult(path, action, "Done"));
                        }
                    }
                }
            } else if ("replace".equals(request.operation)) {
                if (request.propName == null || request.find == null || request.replace == null) {
                    results.add(new UpdateResult(request.path, "Error: Missing replace parameters", "Failed"));
                    return results;
                }

                for (Resource node : nodes) {
                    Resource target = request.pageOnly ? node.getChild("jcr:content") : node;
                    if (target != null && target.getResourceType().equals("cq:Page")) {
                        target = target.getChild("jcr:content");
                    }
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
                        if (request.dryRun) {
                            results.add(new UpdateResult(path, action, "Pending"));
                        } else {
                            props.put(request.propName, request.replace);
                            if (target.getResourceType().equals("cq:PageContent")) {
                                props.put("cq:lastModified", Calendar.getInstance());
                                props.put("cq:lastModifiedBy", resolver.getUserID());
                            }
                            results.add(new UpdateResult(path, action, "Done"));
                        }
                    }
                }
            } else if ("copy".equals(request.operation)) {
                if (request.copyType == null || request.source == null || request.target == null) {
                    results.add(new UpdateResult(request.path, "Error: Missing copy parameters", "Failed"));
                    return results;
                }

                for (Resource node : nodes) {
                    Resource base = request.pageOnly ? node.getChild("jcr:content") : node;
                    if (base == null) {
                        results.add(new UpdateResult(node.getPath(), "Error: No base node", "Failed"));
                        continue;
                    }

                    String basePath = base.getPath();
                    String sourcePath;
                    String targetPath;
                    Resource targetParent;
                    String action;

                    switch (request.copyType) {
                        case "node":
                            if (usesNodeName) {
                                sourcePath = basePath;
                                targetPath = NodeMorphUtils.resolvePath(base.getParent().getPath(), request.target, resolver);
                            } else {
                                sourcePath = NodeMorphUtils.resolvePath(basePath, request.source, resolver);
                                targetPath = NodeMorphUtils.resolvePath(basePath, request.target, resolver);
                            }
                            Resource sourceRes = resolver.getResource(sourcePath);
                            if (sourceRes == null) {
                                results.add(new UpdateResult(basePath, "Error: Source node not found: " + sourcePath, "Failed"));
                                continue;
                            }
                            String targetParentPath = targetPath.substring(0, targetPath.lastIndexOf("/"));
                            targetParent = resolver.getResource(targetParentPath);
                            if (targetParent == null) {
                                results.add(new UpdateResult(basePath, "Error: Target parent does not exist: " + targetParentPath, "Failed"));
                                continue;
                            }
                            String targetNodeName = targetPath.substring(targetPath.lastIndexOf("/") + 1);
                            action = String.format("Copy node %s to %s", request.source, request.target);
                            if (request.dryRun) {
                                results.add(new UpdateResult(basePath, action, "Pending"));
                            } else {
                                Resource copiedNode = resolver.create(targetParent, targetNodeName, sourceRes.getValueMap());
                                results.add(new UpdateResult(basePath, action, "Done"));
                            }
                            break;
                        case "property":
                            ModifiableValueMap props = base.adaptTo(ModifiableValueMap.class);
                            if (props == null) {
                                results.add(new UpdateResult(basePath, "Error: Cannot modify node", "Failed"));
                                continue;
                            }
                            Object propValue = props.get(request.source);
                            if (propValue == null) {
                                results.add(new UpdateResult(basePath, "Error: Source property not found: " + request.source, "Failed"));
                                continue;
                            }
                            action = String.format("Copy property %s=%s to %s", request.source, propValue, request.target);
                            if (request.dryRun) {
                                results.add(new UpdateResult(basePath, action, "Pending"));
                            } else {
                                props.put(request.target, propValue);
                                if (base.getResourceType().equals("cq:PageContent")) {
                                    props.put("cq:lastModified", Calendar.getInstance());
                                    props.put("cq:lastModifiedBy", resolver.getUserID());
                                }
                                results.add(new UpdateResult(basePath, action, "Done"));
                            }
                            break;
                        case "propertyToPath":
                            sourcePath = basePath + "/" + request.source;
                            targetPath = basePath + "/" + request.target;
                            targetParent = resolver.getResource(targetPath.substring(0, targetPath.lastIndexOf("/")));
                            if (targetParent == null) {
                                if (!request.dryRun) {
                                    resolver.create(resolver.getResource(basePath), request.target.substring(0, request.target.lastIndexOf("/")), new HashMap<>());
                                    targetParent = resolver.getResource(targetPath.substring(0, targetPath.lastIndexOf("/")));
                                }
                            }
                            if (targetParent == null) {
                                results.add(new UpdateResult(basePath, "Error: Cannot create target parent: " + targetPath, "Failed"));
                                continue;
                            }
                            ModifiableValueMap targetProps = targetParent.adaptTo(ModifiableValueMap.class);
                            Object sourcePropValue = base.adaptTo(ValueMap.class).get(request.source);
                            if (sourcePropValue == null) {
                                results.add(new UpdateResult(basePath, "Error: Source property not found: " + request.source, "Failed"));
                                continue;
                            }
                            String targetPropName = request.target.substring(request.target.lastIndexOf("/") + 1);
                            action = String.format("Copy property %s=%s to %s", request.source, sourcePropValue, request.target);
                            if (request.dryRun) {
                                results.add(new UpdateResult(basePath, action, "Pending"));
                            } else {
                                targetProps.put(targetPropName, sourcePropValue);
                                if (targetParent.getResourceType().equals("cq:PageContent")) {
                                    targetProps.put("cq:lastModified", Calendar.getInstance());
                                    targetProps.put("cq:lastModifiedBy", resolver.getUserID());
                                }
                                results.add(new UpdateResult(basePath, action, "Done"));
                            }
                            break;
                        default:
                            results.add(new UpdateResult(basePath, "Error: Unknown copy type: " + request.copyType, "Failed"));
                    }
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

}
