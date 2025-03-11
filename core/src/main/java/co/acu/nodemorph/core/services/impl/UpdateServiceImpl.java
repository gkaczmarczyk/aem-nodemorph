package co.acu.nodemorph.core.services.impl;

import co.acu.nodemorph.core.services.UpdateService;
import co.acu.nodemorph.core.dto.UpdateRequest;
import co.acu.nodemorph.core.dto.UpdateResult;
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

            // Build Query
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("path", request.path);
            if (request.pageOnly) {
                queryParams.put("type", "cq:Page");
            }
            queryParams.put("p.limit", "-1");

            Query query = queryBuilder.createQuery(PredicateGroup.create(queryParams), session);
            Iterator<Resource> nodeIterator = query.getResult().getResources();
            List<Resource> nodes = new ArrayList<>();
            nodeIterator.forEachRemaining(nodes::add); // Replace stream() for compatibility

            // Process Add Operation
            if ("add".equals(request.operation) && request.properties != null) {
                Map<String, Object> propsToAdd = parseProperties(request.properties);

                for (Resource node : nodes) {
                    Resource target = request.pageOnly ? node.getChild("jcr:content") : node;
                    if (target == null) {
                        continue;
                    }

                    String path = target.getPath();
                    ModifiableValueMap props = target.adaptTo(ModifiableValueMap.class);
                    if (props == null) {
                        results.add(new UpdateResult(path, "Error: Cannot modify node", "Failed"));
                        continue;
                    }

                    boolean matches = true;
                    if ("property".equals(request.matchType) && request.ifProp != null && !request.ifProp.isEmpty()) {
                        ValueMap currentProps = target.getValueMap();
                        String currentValue = currentProps.get(request.ifProp, String.class);
                        matches = request.ifValue.equals(currentValue);
                    } else if ("node".equals(request.matchType) && request.nodeName != null && !request.nodeName.isEmpty()) {
                        matches = target.getName().equals(request.nodeName);
                    }
                    if (!matches) {
                        continue;
                    }

                    for (Map.Entry<String, Object> entry : propsToAdd.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
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

    private Map<String, Object> parseProperties(String properties) {
        Map<String, Object> props = new HashMap<>();
        if (properties == null || properties.trim().isEmpty()) return props;

        String[] lines = properties.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (value.startsWith("[") && value.endsWith("]")) {
                    String[] values = value.substring(1, value.length() - 1).split(",");
                    for (int i = 0; i < values.length; i++) {
                        values[i] = values[i].trim();
                    }
                    props.put(key, values);
                } else {
                    props.put(key, value);
                }
            }
        }
        return props;
    }

}
