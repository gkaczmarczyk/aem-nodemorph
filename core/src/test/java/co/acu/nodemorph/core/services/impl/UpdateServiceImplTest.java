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
package co.acu.nodemorph.core.services.impl;

import co.acu.nodemorph.core.dto.UpdateRequest;
import co.acu.nodemorph.core.dto.UpdateResult;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.SearchResult;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({AemContextExtension.class, MockitoExtension.class})
class UpdateServiceImplTest {

    private static final String BASE_PATH = "/content/we-retail/language-masters/en/experience";

    private final AemContext context = new AemContext();
    private UpdateServiceImpl updateService;

    @Mock
    private QueryBuilder queryBuilder;

    @Mock
    private Query query;

    @Mock
    private SearchResult searchResult;

    @BeforeEach
    void setUp() {
        context.registerService(QueryBuilder.class, queryBuilder);

        updateService = context.registerInjectActivateService(new UpdateServiceImpl());

        context.load().json("/co/acu/nodemorph/core/services/impl/UpdateServiceImplTest.json", BASE_PATH);
        context.currentPage(BASE_PATH);

        lenient().when(queryBuilder.createQuery(any(PredicateGroup.class), any())).thenReturn(query);
        lenient().when(query.getResult()).thenReturn(searchResult);
        lenient().when(searchResult.getResources()).thenReturn(Collections.emptyIterator());
    }

    @Test
    void testAddNewPropertyToAllNodes() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH + "/skitouring");
        params.put("operation", "add");
        params.put("properties", "category=Adventure"); // New property
        params.put("pageOnly", "false");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource skitouring = context.resourceResolver().getResource(BASE_PATH + "/skitouring");
        assertNotNull(skitouring, "Skitouring resource should exist");
        when(searchResult.getResources()).thenReturn(Collections.singletonList(skitouring).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content", result.path); // Updated path
        assertEquals("Set category=Adventure", result.action);
        assertEquals("Done", result.status);

        ValueMap props = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content").getValueMap();
        assertEquals("Adventure", props.get("category", String.class));
    }

    @Test
    void testAddUpdateExistingProperty() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH + "/skitouring");
        params.put("operation", "add");
        params.put("properties", "jcr:title=Ski Touring Adventures"); // Update existing property
        params.put("pageOnly", "true"); // Targets jcr:content
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource skitouring = context.resourceResolver().getResource(BASE_PATH + "/skitouring");
        assertNotNull(skitouring, "Skitouring resource should exist");
        when(searchResult.getResources()).thenReturn(Collections.singletonList(skitouring).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content", result.path);
        assertEquals("Set jcr:title=Ski Touring Adventures", result.action);
        assertEquals("Done", result.status);

        ValueMap props = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content").getValueMap();
        assertEquals("Ski Touring Adventures", props.get("jcr:title", String.class));
    }

    @Test
    void testAddNewPropertyWithNodeMatch() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH);
        params.put("operation", "add");
        params.put("properties", "difficulty=Hard"); // New property
        params.put("matchType", "node");
        params.put("jcrNodeName", "jcr:content"); // Only update nodes named "jcr:content"
        params.put("pageOnly", "false");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource skitouringContent = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content");
        Resource arcticContent = context.resourceResolver().getResource(BASE_PATH + "/arctic-surfing-in-lofoten/jcr:content");
        assertNotNull(skitouringContent, "Skitouring jcr:content should exist");
        assertNotNull(arcticContent, "Arctic jcr:content should exist");
        when(searchResult.getResources()).thenReturn(Arrays.asList(
                context.resourceResolver().getResource(BASE_PATH + "/skitouring"),
                skitouringContent,
                context.resourceResolver().getResource(BASE_PATH + "/arctic-surfing-in-lofoten"),
                arcticContent
        ).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(2, results.size()); // Only jcr:content nodes should match
        UpdateResult result1 = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content", result1.path);
        assertEquals("Set difficulty=Hard", result1.action);
        assertEquals("Done", result1.status);

        UpdateResult result2 = results.get(1);
        assertEquals(BASE_PATH + "/arctic-surfing-in-lofoten/jcr:content", result2.path);
        assertEquals("Set difficulty=Hard", result2.action);
        assertEquals("Done", result2.status);

        ValueMap props1 = skitouringContent.getValueMap();
        assertEquals("Hard", props1.get("difficulty", String.class));
        ValueMap props2 = arcticContent.getValueMap();
        assertEquals("Hard", props2.get("difficulty", String.class));
    }

    @Test
    void testAddUpdateExistingPropertyWithNodeMatch() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH);
        params.put("operation", "add");
        params.put("properties", "test=updated"); // Update existing property
        params.put("matchType", "node");
        params.put("jcrNodeName", "jcr:content");
        params.put("pageOnly", "false");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource arcticContent = context.resourceResolver().getResource(BASE_PATH + "/arctic-surfing-in-lofoten/jcr:content");
        Resource wildernessContent = context.resourceResolver().getResource(BASE_PATH + "/hours-of-wilderness/jcr:content");
        assertNotNull(arcticContent, "Arctic jcr:content should exist");
        assertNotNull(wildernessContent, "Wilderness jcr:content should exist");
        when(searchResult.getResources()).thenReturn(Arrays.asList(
                context.resourceResolver().getResource(BASE_PATH + "/arctic-surfing-in-lofoten"),
                arcticContent,
                context.resourceResolver().getResource(BASE_PATH + "/hours-of-wilderness"),
                wildernessContent
        ).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(2, results.size());
        UpdateResult result1 = results.get(0);
        assertEquals(BASE_PATH + "/arctic-surfing-in-lofoten/jcr:content", result1.path);
        assertEquals("Set test=updated", result1.action);
        assertEquals("Done", result1.status);

        UpdateResult result2 = results.get(1);
        assertEquals(BASE_PATH + "/hours-of-wilderness/jcr:content", result2.path);
        assertEquals("Set test=updated", result2.action);
        assertEquals("Done", result2.status);

        ValueMap props1 = arcticContent.getValueMap();
        assertEquals("updated", props1.get("test", String.class));
        ValueMap props2 = wildernessContent.getValueMap();
        assertEquals("updated", props2.get("test", String.class));
    }

    @Test
    void testReplacePropertyValue() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH + "/skitouring");
        params.put("operation", "replace");
        params.put("propName", "jcr:title");
        params.put("find", "Skitouring");
        params.put("replace", "Ski Touring");
        params.put("pageOnly", "true");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource skitouring = context.resourceResolver().getResource(BASE_PATH + "/skitouring");
        assertNotNull(skitouring, "Skitouring resource should exist");
        when(searchResult.getResources()).thenReturn(Collections.singletonList(skitouring).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content", result.path);
        assertEquals("Replace jcr:title: Skitouring → Ski Touring", result.action);
        assertEquals("Done", result.status);

        ValueMap props = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content").getValueMap();
        assertEquals("Ski Touring", props.get("jcr:title", String.class));
    }

    @Test
    void testReplacePropertyAcrossNodes() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH);
        params.put("operation", "replace");
        params.put("propName", "test");
        params.put("find", "added");
        params.put("replace", "modified");
        params.put("pageOnly", "true");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        List<Resource> nodes = Arrays.asList(
                context.resourceResolver().getResource(BASE_PATH + "/skitouring"),
                context.resourceResolver().getResource(BASE_PATH + "/arctic-surfing-in-lofoten"),
                context.resourceResolver().getResource(BASE_PATH + "/hours-of-wilderness")
        );
        when(searchResult.getResources()).thenReturn(nodes.iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(3, results.size());
        UpdateResult result1 = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content", result1.path);
        assertEquals("Replace test: added → modified", result1.action);
        assertEquals("Done", result1.status);

        UpdateResult result2 = results.get(1);
        assertEquals(BASE_PATH + "/arctic-surfing-in-lofoten/jcr:content", result2.path);
        assertEquals("Replace test: added → modified", result2.action);
        assertEquals("Done", result2.status);

        UpdateResult result3 = results.get(2);
        assertEquals(BASE_PATH + "/hours-of-wilderness/jcr:content", result3.path);
        assertEquals("Replace test: added → modified", result3.action);
        assertEquals("Done", result3.status);

        ValueMap props1 = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content").getValueMap();
        assertEquals("modified", props1.get("test", String.class));
        ValueMap props2 = context.resourceResolver().getResource(BASE_PATH + "/arctic-surfing-in-lofoten/jcr:content").getValueMap();
        assertEquals("modified", props2.get("test", String.class));
        ValueMap props3 = context.resourceResolver().getResource(BASE_PATH + "/hours-of-wilderness/jcr:content").getValueMap();
        assertEquals("modified", props3.get("test", String.class));
    }

    @Test
    void testReplacePropertyValuePartialMatch() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH + "/skitouring");
        params.put("operation", "replace");
        params.put("propName", "jcr:title");
        params.put("find", "touring");
        params.put("replace", " Adventure");
        params.put("pageOnly", "false");
        params.put("dryRun", "false");
        params.put("partialMatch", "true");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource skitouring = context.resourceResolver().getResource(BASE_PATH + "/skitouring");
        assertNotNull(skitouring, "Skitouring resource should exist");

        when(searchResult.getResources()).thenReturn(Collections.singletonList(skitouring).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content", result.path);
        assertEquals("Replace jcr:title: Skitouring → Ski Adventure", result.action);
        assertEquals("Done", result.status);

        ValueMap props = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content").getValueMap();
        assertEquals("Ski Adventure", props.get("jcr:title", String.class));
    }

    @Test
    void testSimpleNodeCopy() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH + "/skitouring");
        params.put("operation", "copy");
        params.put("copyType", "node");
        params.put("source", "hero_image");
        params.put("target", "hero_image_copy");
        params.put("pageOnly", "false");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource heroImage = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content/root/hero_image");
        assertNotNull(heroImage, "Hero image resource should exist");
        when(searchResult.getResources()).thenReturn(Collections.singletonList(heroImage).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content/root/hero_image", result.path);
        assertEquals("Copy node hero_image to hero_image_copy", result.action);
        assertEquals("Done", result.status);

        Resource copiedNode = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content/root/hero_image_copy");
        assertNotNull(copiedNode, "Copied node should exist");
    }

    @Test
    void testRelativeNodeCopy() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH + "/skitouring");
        params.put("operation", "copy");
        params.put("copyType", "node");
        params.put("source", "hero_image");
        params.put("target", "../hero_image_2");
        params.put("pageOnly", "false");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource heroImage = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content/root/hero_image");
        assertNotNull(heroImage, "Hero image resource should exist");
        when(searchResult.getResources()).thenReturn(Collections.singletonList(heroImage).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content/root/hero_image", result.path);
        assertEquals("Copy node hero_image to ../hero_image_2", result.action);
        assertEquals("Done", result.status);

        Resource copiedNode = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content/hero_image_2");
        assertNotNull(copiedNode, "Copied node should exist at parent level");
    }

    @Test
    void testPathedNodeCopy() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH);
        params.put("operation", "copy");
        params.put("copyType", "node");
        params.put("source", "skitouring/jcr:content");
        params.put("target", "skitouring/jcr:content_copy");
        params.put("pageOnly", "false");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource jcrContent = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content");
        assertNotNull(jcrContent, "jcr:content resource should exist");

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH, result.path);
        assertEquals("Copy node skitouring/jcr:content to skitouring/jcr:content_copy", result.action);
        assertEquals("Done", result.status);

        Resource copiedNode = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content_copy");
        assertNotNull(copiedNode, "Copied node should exist");
    }

    @Test
    void testNodeCopyDryRun() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH + "/skitouring/jcr:content/root");
        params.put("operation", "copy");
        params.put("copyType", "node");
        params.put("source", "hero_image");
        params.put("target", "hero_image_copy");
        params.put("pageOnly", "false");
        params.put("dryRun", "true");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource heroImage = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content/root/hero_image");
        assertNotNull(heroImage, "Hero image resource should exist");
        when(searchResult.getResources()).thenReturn(Collections.singletonList(heroImage).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content/root/hero_image", result.path);
        assertEquals("Copy node hero_image to hero_image_copy", result.action);
        assertEquals("Pending", result.status);

        Resource copiedNode = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content/root/hero_image_copy");
        assertNull(copiedNode, "Copied node should not exist in dry run");
    }

    @Test
    void testDeleteSingleProperty() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH + "/skitouring");
        params.put("operation", "delete");
        params.put("propNames", "test"); // Delete the "test" property
        params.put("pageOnly", "true");  // Target jcr:content
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource skitouring = context.resourceResolver().getResource(BASE_PATH + "/skitouring");
        assertNotNull(skitouring, "Skitouring resource should exist");
        when(searchResult.getResources()).thenReturn(Collections.singletonList(skitouring).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content", result.path);
        assertEquals("Delete properties: test", result.action);
        assertEquals("Done", result.status);

        ValueMap props = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content").getValueMap();
        assertFalse(props.containsKey("test"), "Property 'test' should be deleted");
        assertNotNull(props.get("cq:lastModified"), "cq:lastModified should be updated");
        assertEquals(context.resourceResolver().getUserID(), props.get("cq:lastModifiedBy", String.class));
    }

    @Test
    void testDeleteMultiplePropertiesAcrossNodes() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH);
        params.put("operation", "delete");
        params.put("propNames", "test,category"); // Delete "test" and "category" (category may not exist)
        params.put("pageOnly", "true");
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        List<Resource> nodes = Arrays.asList(
                context.resourceResolver().getResource(BASE_PATH + "/skitouring"),
                context.resourceResolver().getResource(BASE_PATH + "/arctic-surfing-in-lofoten"),
                context.resourceResolver().getResource(BASE_PATH + "/hours-of-wilderness")
        );
        when(searchResult.getResources()).thenReturn(nodes.iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(3, results.size()); // All three nodes have "test" to delete

        UpdateResult result1 = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content", result1.path);
        assertEquals("Delete properties: test", result1.action); // Only "test" exists
        assertEquals("Done", result1.status);

        UpdateResult result2 = results.get(1);
        assertEquals(BASE_PATH + "/arctic-surfing-in-lofoten/jcr:content", result2.path);
        assertEquals("Delete properties: test", result2.action);
        assertEquals("Done", result2.status);

        UpdateResult result3 = results.get(2);
        assertEquals(BASE_PATH + "/hours-of-wilderness/jcr:content", result3.path);
        assertEquals("Delete properties: test", result3.action);
        assertEquals("Done", result3.status);

        ValueMap props1 = context.resourceResolver().getResource(BASE_PATH + "/skitouring/jcr:content").getValueMap();
        assertFalse(props1.containsKey("test"), "Property 'test' should be deleted from skitouring");
        assertFalse(props1.containsKey("category"), "Property 'category' should not exist");

        ValueMap props2 = context.resourceResolver().getResource(BASE_PATH + "/arctic-surfing-in-lofoten/jcr:content").getValueMap();
        assertFalse(props2.containsKey("test"), "Property 'test' should be deleted from arctic");
        assertFalse(props2.containsKey("category"), "Property 'category' should not exist");

        ValueMap props3 = context.resourceResolver().getResource(BASE_PATH + "/hours-of-wilderness/jcr:content").getValueMap();
        assertFalse(props3.containsKey("test"), "Property 'test' should be deleted from wilderness");
        assertFalse(props3.containsKey("category"), "Property 'category' should not exist");
    }

    @Test
    void testCreateNewNodeDryRun() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH);
        params.put("operation", "create");
        params.put("newNodeName", "teaser");
        params.put("newNodeType", "nt:unstructured");
        params.put("newNodeProperties", "jcr:title=New Teaser");
        params.put("parentMatchCondition", "jcr:content");
        params.put("dryRun", "true");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource match = context.resourceResolver().getResource(BASE_PATH + "/hours-of-wilderness/jcr:content");
        when(searchResult.getResources()).thenReturn(Collections.singletonList(match).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals("Pending", result.status);
        assertEquals("Would create nt:unstructured", result.action);

        Resource newNode = context.resourceResolver().getResource(BASE_PATH + "/hours-of-wilderness/jcr:content/teaser");
        assertNull(newNode, "Node should not be created during dry run");
    }

    @Test
    void testCreateNewNodeUnderMatchedParent() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH);
        params.put("operation", "create");
        params.put("newNodeName", "teaser");
        params.put("newNodeType", "nt:unstructured");
        params.put("newNodeProperties", "jcr:title=New Teaser\nteaserText=Exploring the wilds of Western Australia");
        params.put("parentMatchCondition", "jcr:content"); // Optional filter: match parent node name
        params.put("dryRun", "false");

        UpdateRequest request = new UpdateRequest(params, context.resourceResolver());
        Resource wilderness = context.resourceResolver().getResource(BASE_PATH + "/hours-of-wilderness/jcr:content");
        assertNotNull(wilderness, "jcr:content node should exist");

        when(searchResult.getResources()).thenReturn(Collections.singletonList(wilderness).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH + "/hours-of-wilderness/jcr:content/teaser", result.path);
        assertEquals("Created node of type nt:unstructured", result.action);
        assertEquals("Done", result.status);

        Resource newNode = context.resourceResolver().getResource(BASE_PATH + "/hours-of-wilderness/jcr:content/teaser");
        assertNotNull(newNode, "Newly created node should exist");

        ValueMap props = newNode.getValueMap();
        assertEquals("New Teaser", props.get("jcr:title", String.class));
        assertEquals("Exploring the wilds of Western Australia", props.get("teaserText", String.class));
    }

}
