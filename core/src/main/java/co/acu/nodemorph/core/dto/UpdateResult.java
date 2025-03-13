package co.acu.nodemorph.core.dto;

public class UpdateResult {
    public String path;
    public String action;
    public String status;
    public String message;

    public UpdateResult(String path, String action, String status) {
        this.path = path;
        this.action = action;
        this.status = status;
    }

    public UpdateResult(String path, String action, String status, String message) {
        this.path = path;
        this.action = action;
        this.status = status;
        this.message = message;
    }

}
