package co.acu.nodemorph.core.utils;

import co.acu.nodemorph.core.dto.NodeProperty;
import co.acu.nodemorph.core.dto.UpdateRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NodeMorphUtilsTest {

    private UpdateRequest buildRequest(Map<String, String> params) {
        ResourceResolver mockResolver = mock(ResourceResolver.class);
        return new UpdateRequest(params, mockResolver);
    }

    @Test
    void testGetQueryParamMap_PageOnly() {
        Map<String, String> params = Map.of(
                "path", "/content/site",
                "pageOnly", "true"
        );
        UpdateRequest request = buildRequest(params);

        Map<String, String> result = NodeMorphUtils.getQueryParamMap(request);
        assertEquals("cq:Page", result.get("type"));
        assertEquals("/content/site", result.get("path"));
    }

    @Test
    void testGetQueryParamMap_MatchByProperty() {
        Map<String, String> params = Map.of(
                "path", "/content",
                "matchType", "property",
                "ifProp", "sling:resourceType",
                "ifValue", "my/type"
        );
        UpdateRequest request = buildRequest(params);

        Map<String, String> result = NodeMorphUtils.getQueryParamMap(request);
        assertEquals("sling:resourceType", result.get("property"));
        assertEquals("my/type", result.get("property.value"));
        assertEquals("nt:base", result.get("type"));
    }

    @Test
    void testGetQueryParamMap_ReplaceThrowsForInvalidPropName() {
        Map<String, String> params = Map.of(
                "path", "/content",
                "operation", "replace",
                "propName", "some/invalid name"
        );
        UpdateRequest request = buildRequest(params);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                NodeMorphUtils.getQueryParamMap(request));
        assertTrue(exception.getMessage().contains("Invalid property name"));
    }

    @Test
    void testResolvePath_WithParentTraversal() {
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource base = mock(Resource.class);
        Resource parent = mock(Resource.class);

        when(resolver.getResource("/content/site/page")).thenReturn(base);
        when(base.getParent()).thenReturn(parent);
        when(parent.getPath()).thenReturn("/content/site");

        String resolved = NodeMorphUtils.resolvePath("/content/site/page", "../node", resolver);
        assertEquals("/content/site/node", resolved);
    }

    @Test
    void testResolvePath_AbsoluteAndRelative() {
        ResourceResolver resolver = mock(ResourceResolver.class);

        assertEquals("/etc/designs", NodeMorphUtils.resolvePath("/base/path", "/etc/designs", resolver));
        assertEquals("/base/path/assets", NodeMorphUtils.resolvePath("/base/path", "assets", resolver));
    }

    @Test
    void testParseProperties_SingleAndMultiValue() {
        String input = "title=Home\ncolors=[red, green, blue]\n";
        Map<String, Object> props = NodeMorphUtils.parseProperties(input);

        assertEquals("Home", props.get("title"));
        assertInstanceOf(String[].class, props.get("colors"));
        String[] colors = (String[]) props.get("colors");
        assertArrayEquals(new String[]{"red", "green", "blue"}, colors);
    }

    @Test
    void testParseToNodeProperties() {
        String input = "author=Greg\nroles=[admin,editor]";
        List<NodeProperty> props = NodeMorphUtils.parseToNodeProperties(input);

        assertEquals(2, props.size());

        NodeProperty author = props.stream().filter(p -> p.getKey().equals("author")).findFirst().orElse(null);
        assertNotNull(author);
        assertEquals("Greg", author.getValue());

        NodeProperty roles = props.stream().filter(p -> p.getKey().equals("roles")).findFirst().orElse(null);
        assertNotNull(roles);
        assertInstanceOf(String[].class, roles.getValue());
    }

}
