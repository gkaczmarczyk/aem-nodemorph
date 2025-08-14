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
package co.acu.nodemorph.core.dto;

import co.acu.nodemorph.core.utils.NodeMorphUtils;
import org.apache.sling.api.resource.ResourceResolver;

import java.util.ArrayList;
import java.util.List;
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
    public String jcrNodeName;
    public String newNodeName;
    public String newNodeType;
    public String parentMatchCondition;
    public String newNodeProperties;
    public boolean isPartialMatch;
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
        this.newNodeName = params.get("newNodeName");
        this.newNodeType = params.get("newNodeType");
        this.parentMatchCondition = params.get("parentMatchCondition");
        this.newNodeProperties = params.get("newNodeProperties");
        this.isPartialMatch = Boolean.parseBoolean(params.get("partialMatch"));
        this.resolver = resolver;
    }

    public List<NodeProperty> getUpdateProperties() {
        List<NodeProperty> props = new ArrayList<>();
        if (properties != null && !properties.trim().isEmpty()) {
            NodeMorphUtils.parseProperties(properties).forEach((k, v) -> props.add(new NodeProperty(k, v)));
        }
        return props;
    }

    public Map<String, Object> getNewNodeProperties() {
        return NodeMorphUtils.parseProperties(this.newNodeProperties);
    }

}
