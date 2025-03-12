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
    public String nodeName;
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
        this.nodeName = params.get("nodeName");
        this.resolver = resolver;
    }

    public List<NodeProperty> getUpdateProperties() {
        List<NodeProperty> props = new ArrayList<>();
        if (properties != null && !properties.trim().isEmpty()) {
            parseProperties(properties).forEach((k, v) -> props.add(new NodeProperty(k, v)));
        }
        return props;
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
