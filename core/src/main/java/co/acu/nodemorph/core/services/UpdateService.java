package co.acu.nodemorph.core.services;

import co.acu.nodemorph.core.dto.UpdateRequest;
import co.acu.nodemorph.core.dto.UpdateResult;
import java.util.List;

public interface UpdateService {
    List<UpdateResult> processUpdate(UpdateRequest request);
}
