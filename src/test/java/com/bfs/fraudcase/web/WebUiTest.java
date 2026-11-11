package com.bfs.fraudcase.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bfs.fraudcase.application.CaseIntakeCommand;
import com.bfs.fraudcase.support.IntegrationTestBase;
import com.bfs.fraudcase.support.Scenarios;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The six UI capabilities, CSRF, role-based controls, output encoding, error and empty states.
 *
 * @author Venkateshwara Doijode
 */
@AutoConfigureMockMvc
class WebUiTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;

    static RequestPostProcessor investigator() {
        return user("investigator01").roles("SERVICE_AGENT", "INVESTIGATOR");
    }

    static RequestPostProcessor reviewer() {
        return user("reviewer08").roles("REVIEWER");
    }

    @Test
    void sixScreensRenderEvidenceCitationsAndAdvisoryControls() throws Exception {
        String id = investigate(Scenarios.T01).caseId();
        mvc.perform(get("/cases/new?sample=true").with(investigator())).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("SYNTHETIC DATA"), containsString("CUST-••••-4821"),
                        containsString("Save draft"), containsString("Start investigation"), containsString("for=\"customerStatement\""))));
        mvc.perform(get("/cases/" + id + "/investigation").with(reviewer())).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("getDeviceSignals"), containsString("SUCCEEDED"),
                        containsString("Operational trace only"), containsString("Unresolved question"))));
        mvc.perform(get("/cases/" + id + "/recommendation").with(reviewer())).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("UNAUTHORISED_TRANSACTION"), containsString("FRAUD_INVESTIGATION"),
                        containsString("humanReviewRequired = "), containsString("Open human review"), containsString("PASS"))));
        mvc.perform(get("/cases/" + id + "/evidence").with(reviewer())).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Observed evidence"), containsString("Customer claims"),
                        containsString("Generated summary"), containsString("New device observed"))));
        mvc.perform(get("/cases/" + id + "/citations").with(reviewer())).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("POL-CNP-017"), containsString("cnp-032"),
                        containsString("Open passage"), containsString("Withdrawn versions excluded"))));
        mvc.perform(get("/cases/" + id + "/review").with(reviewer())).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Reviewer rationale"), containsString("REVIEW_DISPOSITION_RECORDED"),
                        containsString("No automatic financial action"), not(containsString("value=\"OVERRIDE\"")))));
        mvc.perform(get("/cases/" + id + "/review").with(user("lead01").roles("REVIEWER", "TEAM_LEAD")))
                .andExpect(content().string(containsString("value=\"OVERRIDE\"")));
    }

    @Test
    void reviewWithoutReasonIsBlockedAndValidReviewIsRecorded() throws Exception {
        String id = investigate(Scenarios.T02).caseId();
        mvc.perform(post("/cases/" + id + "/review").with(reviewer()).with(csrf()).param("disposition", "ROUTE_MERCHANT_DISPUTE").param("reason", ""))
                .andExpect(status().isOk()).andExpect(content().string(containsString("rationale of at least 10 characters is mandatory")));
        var post = post("/cases/" + id + "/review").with(reviewer()).with(csrf()).param("disposition", "ROUTE_MERCHANT_DISPUTE")
                .param("reason", "Customer recognises the merchant; refund dispute belongs to dispute operations.");
        for (String item : com.bfs.fraudcase.application.ReviewCommand.REQUIRED_CHECKLIST) {
            post.param("checklist", item);
        }
        mvc.perform(post).andExpect(redirectedUrl("/cases/" + id + "/review"));
        mvc.perform(get("/cases/" + id + "/review").with(reviewer())).andExpect(content().string(containsString("ROUTE_MERCHANT_DISPUTE")));
    }

    @Test
    void csrfIsRequiredAndAnonymousUsersAreRedirectedToLogin() throws Exception {
        mvc.perform(post("/cases").with(investigator()).param("action", "create")).andExpect(status().isForbidden());
        mvc.perform(get("/cases")).andExpect(status().is3xxRedirection());
    }

    @Test
    void intakeShowsFieldErrorsAndEncodesOutput() throws Exception {
        mvc.perform(post("/cases").with(investigator()).with(csrf()).param("action", "create")
                        .param("maskedCustomerRef", "<img src=x onerror=alert(1)>").param("customerStatement", "<script>x</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("full identifiers are not permitted"),
                        containsString("Markup or scripts are not permitted"), not(containsString("<img src=x")),
                        containsString("&lt;img src=x"))));
        CaseIntakeCommand c = Scenarios.T01.command();
        String id = cases.intake(new CaseIntakeCommand(c.maskedCustomerRef(), "Karnataka", c.customerLocation(), null,
                c.transactionRef(), c.amount(), c.currency(), c.occurredAt(), c.transactionStatus(), c.paymentChannel(),
                c.maskedMerchant(), c.issueType(), "Statement with ampersand & quotes \" here", null, null, true, true), INVESTIGATOR).caseId();
        mvc.perform(get("/cases/" + id).with(reviewer())).andExpect(content().string(containsString("ampersand &amp; quotes &quot; here")));
    }

    @Test
    void emptyAndErrorStatesAreSafe() throws Exception {
        String id = create(Scenarios.T03);
        mvc.perform(get("/cases/" + id + "/recommendation").with(reviewer())).andExpect(content().string(containsString("No validated recommendation")));
        mvc.perform(get("/cases/CASE-2026-9999").with(reviewer())).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Exception"))));
        mvc.perform(post("/cases/" + id + "/investigations").with(reviewer()).with(csrf())).andExpect(status().isForbidden());
    }
}
