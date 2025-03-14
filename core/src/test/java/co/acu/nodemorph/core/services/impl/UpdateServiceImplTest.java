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

        when(queryBuilder.createQuery(any(PredicateGroup.class), any())).thenReturn(query);
        when(query.getResult()).thenReturn(searchResult);
        when(searchResult.getResources()).thenReturn(Collections.emptyIterator());
    }

    @Test
    void testSimpleNodeCopy() {
        Map<String, String> params = new HashMap<>();
        params.put("path", BASE_PATH + "/skitouring/jcr:content/root");
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
        params.put("path", BASE_PATH + "/skitouring/jcr:content/root");
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
        when(searchResult.getResources()).thenReturn(Collections.singletonList(jcrContent).iterator());

        List<UpdateResult> results = updateService.processUpdate(request);

        assertEquals(1, results.size());
        UpdateResult result = results.get(0);
        assertEquals(BASE_PATH + "/skitouring/jcr:content", result.path);
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

}
