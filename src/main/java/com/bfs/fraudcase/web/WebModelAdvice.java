package com.bfs.fraudcase.web;

import com.bfs.fraudcase.application.AuthorizationException;
import com.bfs.fraudcase.application.IllegalStateTransitionException;
import com.bfs.fraudcase.application.NotFoundException;
import com.bfs.fraudcase.config.CapstoneProperties;
import com.bfs.fraudcase.config.SecurityConfig;
import com.bfs.fraudcase.domain.Actor;
import com.bfs.fraudcase.domain.Role;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Shared model attributes and safe error pages for the Thymeleaf UI.
 *
 * @author Venkateshwara Doijode
 */
@ControllerAdvice(basePackageClasses = WebModelAdvice.class)
public class WebModelAdvice {

    private static final Logger log = LoggerFactory.getLogger(WebModelAdvice.class);
    private final CapstoneProperties props;

    public WebModelAdvice(CapstoneProperties props) {
        this.props = props;
    }

    @ModelAttribute
    void common(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            Actor a = SecurityConfig.actor(auth);
            model.addAttribute("currentUser", a.id());
            model.addAttribute("currentRoles", a.roles().stream().map(Enum::name).sorted().toList());
            model.addAttribute("isAgent", a.has(Role.SERVICE_AGENT) || a.has(Role.INVESTIGATOR));
            model.addAttribute("isInvestigator", a.has(Role.INVESTIGATOR));
            model.addAttribute("isReviewer", a.has(Role.REVIEWER));
            model.addAttribute("isLead", a.has(Role.TEAM_LEAD));
            model.addAttribute("isAuditor", a.has(Role.AUDITOR) || a.has(Role.TEAM_LEAD));
        }
        model.addAttribute("modelName", props.model().modelName());
    }

    @ExceptionHandler(AuthorizationException.class)
    String forbidden(AuthorizationException e, Model model, HttpServletResponse response) {
        return error(model, response, 403, "Not permitted", e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    String notFound(NotFoundException e, Model model, HttpServletResponse response) {
        return error(model, response, 404, "Not found", e.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    String conflict(IllegalStateTransitionException e, Model model, HttpServletResponse response) {
        return error(model, response, 409, "Action not available in the current state", e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    String unexpected(RuntimeException e, Model model, HttpServletResponse response) {
        log.error("UI request failed type={}", e.getClass().getSimpleName());
        return error(model, response, 500, "Something went wrong",
                "The request could not be completed. The case was left in a safe state; please retry.");
    }

    private String error(Model model, HttpServletResponse response, int status, String title, String message) {
        common(model);
        response.setStatus(status);
        model.addAttribute("status", status);
        model.addAttribute("title", title);
        model.addAttribute("message", message);
        model.addAttribute("correlationId", MDC.get("correlationId"));
        return "error";
    }
}
