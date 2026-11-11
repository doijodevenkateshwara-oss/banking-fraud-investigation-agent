package com.bfs.fraudcase.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Human-review form backing object.
 *
 * @author Venkateshwara Doijode
 */
public class ReviewForm {
    private String disposition;
    private String overrideRoute;
    private String reason = "";
    private List<String> checklist = new ArrayList<>();

    public String getDisposition() { return disposition; }
    public void setDisposition(String v) { disposition = v; }
    public String getOverrideRoute() { return overrideRoute; }
    public void setOverrideRoute(String v) { overrideRoute = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = v == null ? "" : v; }
    public List<String> getChecklist() { return checklist; }
    public void setChecklist(List<String> v) { checklist = v == null ? new ArrayList<>() : v; }
}
