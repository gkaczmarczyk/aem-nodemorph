package co.acu.nodemorph.core.servlets;

import co.acu.nodemorph.core.dto.UpdateRequest;
import co.acu.nodemorph.core.dto.UpdateResult;
import co.acu.nodemorph.core.services.UpdateService;
import com.google.gson.Gson;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
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
@SlingServletResourceTypes(
        resourceTypes = "aemnodemorph/admin/components/aemnodemorph",
        selectors = "update",
        extensions = "json",
        methods = "POST"
)
public class UpdateServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = -8752096293782235901L;

    private static final Logger LOG = LoggerFactory.getLogger(UpdateServlet.class);

    @Reference
    private UpdateService updateService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
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
