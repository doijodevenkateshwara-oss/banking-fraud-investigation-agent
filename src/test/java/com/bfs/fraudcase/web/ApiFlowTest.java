package com.bfs.fraudcase.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * REST contract: intake → investigation → evidence/traces/citations → review → audit, plus authorisation.
 *
 * @author Venkateshwara Doijode
 */
@AutoConfigureMockMvc
class ApiFlowTest extends IntegrationTestBase {

    static final String PW = "test-only-password";
    static final String SAMPLE = """
            {"maskedCustomerRef":"CUST-••••-4821","transactionRef":"TXN-2026-784102","amount":48750.00,"currency":"INR",
             "occurredAt":"2026-11-06T08:42:00+05:30","status":"POSTED","channel":"CARD_NOT_PRESENT",
             "maskedMerchant":"NORTHSTAR DIGITAL ••••73","issueType":"MERCHANT_NOT_RECOGNISED",
             "customerStatement":"I do not recognise this purchase and still have my card.",
             "syntheticDataConfirmed":true,"reviewRequired":true}""";

    @Autowired
    MockMvc mvc;

    static RequestPostProcessor as(String user) {
        return httpBasic(user, PW);
    }

    private String createSample() throws Exception {
        String body = mvc.perform(post("/api/cases").with(as("agent01")).contentType(MediaType.APPLICATION_JSON).content(SAMPLE))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.cardPossession").value("IN_POSSESSION"))
                .andExpect(jsonPath("$.analystId").value("agent01"))
                .andReturn().getResponse().getContentAsString();
        return (String) Json.read(body, Map.class).get("caseId");
    }

    @Test
    void guideSampleRequestFlowsEndToEnd() throws Exception {
        String id = createSample();
        mvc.perform(post("/api/cases/" + id + "/investigations").with(as("investigator01")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.classification").value("UNAUTHORISED_TRANSACTION"))
                .andExpect(jsonPath("$.recommendedRoute").value("FRAUD_INVESTIGATION"))
                .andExpect(jsonPath("$.humanReviewRequired").value(true))
                .andExpect(jsonPath("$.missingInformation", hasItem("authentication challenge outcome")))
                .andExpect(jsonPath("$.policyCitations[*].score", everyItem(greaterThanOrEqualTo(0.72))));
        mvc.perform(get("/api/cases/" + id).with(as("reviewer08"))).andExpect(jsonPath("$.status").value("READY_FOR_REVIEW"));
        mvc.perform(get("/api/cases/" + id + "/evidence").with(as("reviewer08"))).andExpect(jsonPath("$[0].provenance").value("INTAKE"));
        mvc.perform(get("/api/cases/" + id + "/tool-traces").with(as("reviewer08"))).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/cases/" + id + "/citations").with(as("reviewer08")))
                .andExpect(jsonPath("$[*].chunkId", hasItem("cnp-032"))).andExpect(jsonPath("$[0].version").exists());
        mvc.perform(get("/api/cases/" + id + "/recommendation").with(as("reviewer08"))).andExpect(jsonPath("$.schemaValidation").value("PASS"));
        mvc.perform(post("/api/cases/" + id + "/messages").with(as("investigator01")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Which evidence is missing?\"}")).andExpect(jsonPath("$.answer", containsString("authentication")));

        // T12: override/review without reason is blocked
        mvc.perform(post("/api/cases/" + id + "/reviews").with(as("reviewer08")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"CLOSE_NO_ACTION\",\"reason\":\"\",\"checklist\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists())
                .andExpect(jsonPath("$.fieldErrors.checklist").exists());
        // OVERRIDE requires the team-lead role
        String checklist = Json.write(com.bfs.fraudcase.application.ReviewCommand.REQUIRED_CHECKLIST);
        mvc.perform(post("/api/cases/" + id + "/reviews").with(as("reviewer08")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"OVERRIDE\",\"overrideRoute\":\"MANUAL_REVIEW\",\"reason\":\"Override for test purposes\",\"checklist\":" + checklist + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/cases/" + id + "/reviews").with(as("reviewer08")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"ESCALATE_FRAUD_INVESTIGATION\",\"reason\":\"Independent device and location indicators support escalation.\",\"checklist\":" + checklist + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reviewerId").value("reviewer08"))
                .andExpect(jsonPath("$.finalRoute").value("FRAUD_INVESTIGATION"));
        mvc.perform(get("/api/cases/" + id).with(as("reviewer08"))).andExpect(jsonPath("$.status").value("ROUTED"));
        mvc.perform(get("/api/cases/" + id + "/audit").with(as("auditor01")))
                .andExpect(jsonPath("$[*].eventType", hasItem("REVIEW_DISPOSITION_RECORDED")));
        mvc.perform(get("/api/audit/verify").with(as("auditor01"))).andExpect(jsonPath("$.intact").value(true));
    }

    @Test
    void rolesAreEnforcedOnEveryAction() throws Exception {
        String id = createSample();
        mvc.perform(get("/api/cases/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/cases/" + id + "/investigations").with(as("agent01"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/cases/" + id + "/investigations").with(as("reviewer08"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/cases").with(as("reviewer08")).contentType(MediaType.APPLICATION_JSON).content(SAMPLE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit/verify").with(as("reviewer08"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/cases/" + id + "/investigations").with(as("investigator01"))).andExpect(status().isAccepted());
        // separation of duties: an investigator who is also listed as reviewer cannot dispose of their own case
        mvc.perform(post("/api/cases/" + id + "/investigations").with(as("investigator01"))).andExpect(status().isConflict());
    }

    @Test
    void invalidInputAndUnknownEnumsFailSafelyWithoutStackTraces() throws Exception {
        mvc.perform(post("/api/cases").with(as("agent01")).contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE.replace("CUST-••••-4821", "4111111111111111")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.maskedCustomerRef").exists());
        mvc.perform(post("/api/cases").with(as("agent01")).contentType(MediaType.APPLICATION_JSON)
                        .content(SAMPLE.replace("\"POSTED\"", "\"TELEPORTED\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", not(containsString("Exception"))));
        mvc.perform(get("/api/cases/CASE-2026-9999").with(as("reviewer08"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/cases").with(as("reviewer08"))).andExpect(header().exists("X-Correlation-Id"));
    }
}
