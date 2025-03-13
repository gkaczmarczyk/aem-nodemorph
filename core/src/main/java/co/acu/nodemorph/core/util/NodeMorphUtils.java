package co.acu.nodemorph.core.util;

import co.acu.nodemorph.core.dto.UpdateRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.HashMap;
import java.util.Map;

public class NodeMorphUtils {

    public static Map<String, String> getQueryParamMap(UpdateRequest request) {
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
        } else if ("replace".equals(request.operation) && request.propName != null && !request.propName.isEmpty()) {
            if (request.propName.contains("/") || request.propName.contains(" ")) {
                throw new IllegalArgumentException("Invalid property name: " + request.propName + " (slashes or spaces not allowed)");
            }
            queryParams.put("type", request.pageOnly ? "cq:Page" : "nt:base");
            queryParams.put("property", request.propName);
            queryParams.put("property.value", request.find);
        } else if ("copy".equals(request.operation)) {
            queryParams.put("type", "nt:base");
            if ("node".equals(request.copyType) && request.source != null && !request.source.isEmpty() &&
                    !request.source.contains("/") && !request.source.contains("..")) {
                queryParams.put("nodename", request.source);
            }
        }
        queryParams.put("p.limit", "-1");
        return queryParams;
    }

    public static String resolvePath(String basePath, String relativePath, ResourceResolver resolver) {
        if (relativePath.startsWith("../")) {
            Resource base = resolver.getResource(basePath);
            Resource parent = base.getParent();
            if (parent == null) {
                throw new IllegalArgumentException("Cannot resolve parent path beyond " + basePath);
            }
            String remaining = relativePath.substring(3); // Strip "../"
            return parent.getPath() + (remaining.isEmpty() ? "" : "/" + remaining);
        } else if (relativePath.startsWith("/")) {
            return relativePath; // Absolute path
        } else {
            return basePath + "/" + relativePath; // Relative to base
        }
    }

}
