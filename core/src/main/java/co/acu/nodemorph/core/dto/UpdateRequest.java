package co.acu.nodemorph.core.dto;

import org.apache.sling.api.resource.ResourceResolver;

import java.util.*;
import java.util.stream.Collectors;

public class UpdateRequest {
    public String path;
    public String operation;
    public boolean pageOnly;
    public boolean dryRun;
    public String properties;
    public String propNames;
    public String propName;
    public String find;
    public String replace;
    public String ifProp;
    public String ifValue;
    public String setProps;
    public String copyType;
    public String source;
    public String target;
    public String matchType;
    public String jcrNodeName;
    public ResourceResolver resolver;

    private static final String[] WRITABLE_PROPERTIES = {"properties"};

    public UpdateRequest(Map<String, String> params, ResourceResolver resolver) {
        this.path = params.get("path");
        this.operation = params.get("operation");
        this.pageOnly = Boolean.parseBoolean(params.get("pageOnly"));
        this.dryRun = Boolean.parseBoolean(params.get("dryRun"));
        this.properties = params.get("properties");
        this.propNames = params.get("propNames");
        this.propName = params.get("propName");
        this.find = params.get("find");
        this.replace = params.get("replace");
        this.ifProp = params.get("ifProp");
        this.ifValue = params.get("ifValue");
        this.setProps = params.get("setProps");
        this.copyType = params.get("copyType");
        this.source = params.get("source");
        this.target = params.get("target");
        this.matchType = params.get("matchType");
        this.jcrNodeName = params.get("jcrNodeName");
        this.resolver = resolver;
    }

    public List<NodeProperty> getUpdateProperties() {
        List<NodeProperty> props = new ArrayList<>();
        if (properties != null && !properties.trim().isEmpty()) {
            parseProperties(properties).forEach((k, v) -> props.add(new NodeProperty(k, v)));
        }
        return props;
    }

    /**
     * Parses a string of property definitions into a key-value map for use in node update operations.
     * Supports single-value properties (e.g., "key=value") and multi-value properties (e.g., "key=[val1, val2]"),
     * where multi-values are split into a string array. This method is critical for interpreting user-provided
     * property inputs in a flexible, human-readable format, such as those passed via a UI or script.
     *
     * <p>Example inputs:
     * <ul>
     *   <li>"title=New Title" → { "title": "New Title" }</li>
     *   <li>"tags=[tag1, tag2, tag3]" → { "tags": ["tag1", "tag2", "tag3"] }</li>
     *   <li>"title=New Title\ntags=[tag1, tag2]" → { "title": "New Title", "tags": ["tag1", "tag2"] }</li>
     * </ul>
     *
     * <p>Lines are split by newlines, and each line is expected to follow a "key=value" format. Empty lines
     * or malformed entries (e.g., missing "=") are skipped without error. Whitespace is trimmed from keys
     * and values to ensure clean data.
     *
     * @param properties the raw string containing property definitions, potentially spanning multiple lines.
     *                   May be null or empty, in which case an empty map is returned.
     * @return a map where keys are property names and values are either strings (for single values) or
     *         string arrays (for multi-value properties enclosed in square brackets).
     */
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
