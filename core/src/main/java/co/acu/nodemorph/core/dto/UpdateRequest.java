package co.acu.nodemorph.core.dto;

import org.apache.sling.api.resource.ResourceResolver;

import java.util.Map;

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
}
