package co.acu.nodemorph.core.dto;

public class NodeProperty {

    private final String key;
    private final Object value;

    public NodeProperty(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public Object getValue() { return value; }

}
