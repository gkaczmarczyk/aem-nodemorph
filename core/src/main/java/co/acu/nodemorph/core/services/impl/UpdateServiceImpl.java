package co.acu.nodemorph.core.services.impl;

import co.acu.nodemorph.core.services.UpdateService;
import co.acu.nodemorph.core.dto.UpdateRequest;
import co.acu.nodemorph.core.dto.UpdateResult;
import co.acu.nodemorph.core.dto.NodeProperty;
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
import java.util.stream.Collectors;

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

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("path", request.path);
            if (request.pageOnly) {
                queryParams.put("type", "cq:Page");
            } else if ("property".equals(request.matchType) && request.ifProp != null && !request.ifProp.isEmpty()) {
                queryParams.put("property", request.ifProp);
                queryParams.put("property.value", request.ifValue);
                queryParams.put("type", "nt:base");
            } else if ("node".equals(request.matchType) && request.jcrNodeName != null && !request.jcrNodeName.isEmpty()) {
                queryParams.put("nodename", request.jcrNodeName);
                queryParams.put("type", "nt:base");
            }
            queryParams.put("p.limit", "-1");

            PredicateGroup predicate = PredicateGroup.create(queryParams);
            Query query = queryBuilder.createQuery(predicate, session);
            Iterator<Resource> nodeIterator = query.getResult().getResources();
            List<Resource> nodes = new ArrayList<>();
            nodeIterator.forEachRemaining(nodes::add);

            //
            // Process Operations
            //
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

                if (!request.dryRun && !results.stream().allMatch(r -> "Failed".equals(r.status))) {
                    resolver.commit();
                }
            }

        } catch (PersistenceException e) {
            LOG.error("Failed to commit changes", e);
            results.add(new UpdateResult(request.path, "Error: Save failed", "Failed"));
        }

        return results;
    }

}
