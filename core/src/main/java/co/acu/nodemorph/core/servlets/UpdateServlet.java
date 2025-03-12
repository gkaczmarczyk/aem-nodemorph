package co.acu.nodemorph.core.servlets;

import co.acu.nodemorph.core.dto.UpdateRequest;
import co.acu.nodemorph.core.dto.UpdateResult;
import co.acu.nodemorph.core.services.UpdateService;
import com.google.gson.Gson;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = Servlet.class)
@SlingServletPaths("/bin/nodemorph/update")
public class UpdateServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 8639876788048675540L;

    private static final Logger LOG = LoggerFactory.getLogger(UpdateServlet.class);

    @Reference
    private UpdateService updateService;

    @Reference
    private SlingSettingsService slingSettings;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        if (!slingSettings.getRunModes().contains("author")) {
            LOG.warn("UpdateServlet accessed on non-author instance");
            response.sendError(SlingHttpServletResponse.SC_FORBIDDEN, "Not allowed");
            return;
        }

        LOG.info("UpdateServlet hit with params: {}", request.getParameterMap());
        Map<String, String> params = request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()[0]));
        UpdateRequest updateRequest = new UpdateRequest(params, request.getResourceResolver());

        List<UpdateResult> results = updateService.processUpdate(updateRequest);

        response.setContentType("application/json");
        response.getWriter().write(new Gson().toJson(new UpdateResponse(results.size(), results)));
    }

    private static class UpdateResponse {
        int total;
        List<UpdateResult> actions;

        UpdateResponse(int total, List<UpdateResult> actions) {
            this.total = total;
            this.actions = actions;
        }
    }

}
