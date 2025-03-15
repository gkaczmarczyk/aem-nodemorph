package co.acu.nodemorph.core.utils;

import co.acu.nodemorph.core.dto.UpdateRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.HashMap;
import java.util.Map;

public class NodeMorphUtils {

    /**
     * Constructs a map of query parameters for a JCR query based on the update request’s configuration.
     * The resulting map is used to identify nodes in the repository that match the operation’s criteria,
     * such as path, node type, property conditions, or node names. Tailors the query to the operation
     * type (add, replace, copy) and additional filters like pageOnly or matchType.
     *
     * @param request the {@link UpdateRequest} containing operation details, path, and filtering criteria
     *                (e.g., pageOnly, matchType, propName, ifProp, jcrNodeName).
     * @return a map of query parameters compatible with {@link PredicateGroup#create}, including keys
     *         like "path", "type", "property", "nodename", and "p.limit".
     * @throws IllegalArgumentException if the replace operation’s propName contains slashes or spaces,
     *         which are invalid in JCR property names.
     */
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

    /**
     * Resolves a relative or absolute path to an absolute JCR path based on a given base path.
     * Handles three cases: relative paths appended to the base, absolute paths returned as-is,
     * and parent traversals (../) resolved by navigating up the resource hierarchy. Ensures
     * robust path handling for node operations like copying or property updates.
     *
     * @param basePath the base JCR path (e.g., "/content/we-retail/en/experience") from which to
     *                 resolve the relative path.
     * @param relativePath the path to resolve, which may be relative (e.g., "skitouring"), absolute
     *                     (e.g., "/content/dam"), or parent-traversing (e.g., "../parent").
     * @param resolver the {@link ResourceResolver} used to access the repository and resolve parent
     *                 resources for "../" traversals.
     * @return the fully resolved absolute JCR path (e.g., "/content/we-retail/en/experience/skitouring").
     * @throws IllegalArgumentException if a parent traversal ("../") cannot be resolved because the
     *         base path has no parent (e.g., at repository root).
     */
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
