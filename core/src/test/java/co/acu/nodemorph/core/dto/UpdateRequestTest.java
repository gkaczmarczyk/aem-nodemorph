package co.acu.nodemorph.core.dto;

import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class UpdateRequestTest {

    @Mock
    private ResourceResolver mockResolver;

    @BeforeEach
    void setUp() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testBasicNodeCopyRequest() {
        // Scenario: Simple node copy request under a page
        Map<String, String> params = new HashMap<>();
        params.put("path", "/content/site/en/home");
        params.put("operation", "copy");
        params.put("copyType", "node");
        params.put("source", "hero_image");
        params.put("target", "hero_image_backup");
        params.put("pageOnly", "false");
        params.put("dryRun", "true");

        UpdateRequest request = new UpdateRequest(params, mockResolver);

        assertEquals("/content/site/en/home", request.path);
        assertEquals("copy", request.operation);
        assertEquals("node", request.copyType);
        assertEquals("hero_image", request.source);
        assertEquals("hero_image_backup", request.target);
        assertFalse(request.pageOnly);
        assertTrue(request.dryRun);
        assertSame(mockResolver, request.resolver);
        assertNull(request.properties, "Properties should be null when not provided");
        assertTrue(request.getUpdateProperties().isEmpty(), "No properties to parse");
    }

    @Test
    void testPropertyUpdateWithSingleValue() {
        // Scenario: Update a single property on a node
        Map<String, String> params = new HashMap<>();
        params.put("path", "/content/site/en/products");
        params.put("operation", "update");
        params.put("properties", "title=New Product Title");
        params.put("pageOnly", "true");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, mockResolver);

        assertEquals("/content/site/en/products", request.path);
        assertEquals("update", request.operation);
        assertTrue(request.pageOnly);
        assertFalse(request.dryRun);
        assertEquals("title=New Product Title", request.properties);

        List<NodeProperty> props = request.getUpdateProperties();
        assertEquals(1, props.size());
        NodeProperty prop = props.get(0);
        assertEquals("title", prop.getKey());
        assertEquals("New Product Title", prop.getValue());
    }

    @Test
    void testPropertyUpdateWithArrayValue() {
        // Scenario: Set a multi-value property (e.g., tags)
        Map<String, String> params = new HashMap<>();
        params.put("path", "/content/site/en/blog");
        params.put("operation", "update");
        params.put("properties", "tags=[tag1, tag2, tag3]");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, mockResolver);

        assertEquals("/content/site/en/blog", request.path);
        assertEquals("update", request.operation);
        assertEquals("tags=[tag1, tag2, tag3]", request.properties);

        List<NodeProperty> props = request.getUpdateProperties();
        assertEquals(1, props.size());
        NodeProperty prop = props.get(0);
        assertEquals("tags", prop.getKey());
        assertInstanceOf(String[].class, prop.getValue());
        String[] tags = (String[]) prop.getValue();
        assertArrayEquals(new String[]{"tag1", "tag2", "tag3"}, tags);
    }

    @Test
    void testComplexPropertyUpdateWithMultipleLines() {
        // Scenario: Multiple properties with mixed single and array values
        Map<String, String> params = new HashMap<>();
        params.put("path", "/content/site/en/events");
        params.put("operation", "update");
        params.put("properties", "title=Spring Festival\n" +
                "categories=[music, outdoor]\n" +
                "location=City Park");
        params.put("dryRun", "true");

        UpdateRequest request = new UpdateRequest(params, mockResolver);

        assertEquals("/content/site/en/events", request.path);
        assertTrue(request.dryRun);

        List<NodeProperty> props = request.getUpdateProperties();
        assertEquals(3, props.size());

        Map<String, Object> propMap = props.stream()
                .collect(Collectors.toMap(NodeProperty::getKey, NodeProperty::getValue));

        assertEquals("Spring Festival", propMap.get("title"));
        assertInstanceOf(String[].class, propMap.get("categories"));
        assertArrayEquals(new String[]{"music", "outdoor"}, (String[]) propMap.get("categories"));
        assertEquals("City Park", propMap.get("location"));
    }

    @Test
    void testFindAndReplaceRequest() {
        // Scenario: Find and replace a property value across nodes
        Map<String, String> params = new HashMap<>();
        params.put("path", "/content/site/en");
        params.put("operation", "replace");
        params.put("propName", "description");
        params.put("find", "old text");
        params.put("replace", "new text");
        params.put("pageOnly", "false");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, mockResolver);

        assertEquals("/content/site/en", request.path);
        assertEquals("replace", request.operation);
        assertEquals("description", request.propName);
        assertEquals("old text", request.find);
        assertEquals("new text", request.replace);
        assertFalse(request.pageOnly);
        assertFalse(request.dryRun);
        assertNull(request.properties);
        assertTrue(request.getUpdateProperties().isEmpty());
    }

    @Test
    void testConditionalPropertySet() {
        // Scenario: Set properties if a condition matches (e.g., for selective updates)
        Map<String, String> params = new HashMap<>();
        params.put("path", "/content/site/en/news");
        params.put("operation", "set");
        params.put("ifProp", "category");
        params.put("ifValue", "tech");
        params.put("setProps", "featured=true");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, mockResolver);

        assertEquals("/content/site/en/news", request.path);
        assertEquals("set", request.operation);
        assertEquals("category", request.ifProp);
        assertEquals("tech", request.ifValue);
        assertEquals("featured=true", request.setProps);
        assertFalse(request.dryRun);
        assertNull(request.properties); // setProps is separate
        assertTrue(request.getUpdateProperties().isEmpty(), "setProps isn't parsed by getUpdateProperties");
    }

    @Test
    void testEdgeCaseEmptyProperties() {
        // Scenario: Properties param is empty or whitespace
        Map<String, String> params = new HashMap<>();
        params.put("path", "/content/site/en");
        params.put("operation", "update");
        params.put("properties", "   \n  \t  ");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, mockResolver);

        assertEquals("   \n  \t  ", request.properties); // Raw value preserved
        assertTrue(request.getUpdateProperties().isEmpty(), "Empty/whitespace properties should yield no NodeProperties");
    }

    @Test
    void testMissingParams() {
        // Scenario: Minimal params, testing null/empty defaults
        Map<String, String> params = new HashMap<>();
        params.put("path", "/content/site/en"); // Only required field

        UpdateRequest request = new UpdateRequest(params, mockResolver);

        assertEquals("/content/site/en", request.path);
        assertNull(request.operation);
        assertFalse(request.pageOnly, "Default boolean should be false");
        assertFalse(request.dryRun, "Default boolean should be false");
        assertNull(request.properties);
        assertNull(request.source);
        assertNull(request.target);
        assertTrue(request.getUpdateProperties().isEmpty());
    }

}
