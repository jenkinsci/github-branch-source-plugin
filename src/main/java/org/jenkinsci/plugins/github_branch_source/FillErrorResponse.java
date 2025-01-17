package org.jenkinsci.plugins.github_branch_source;

import hudson.Util;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

// TODO replace with corresponding core functionality once Jenkins core has JENKINS-42443
class FillErrorResponse extends IOException implements HttpResponse {

    private final boolean clearList;

    public FillErrorResponse(String message, boolean clearList) {
        super(message);
        this.clearList = clearList;
    }

    @Override
    public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException {
        rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        rsp.setContentType("text/html;charset=UTF-8");
        rsp.setHeader("X-Jenkins-Select-Error", clearList ? "clear" : "retain");
        rsp.getWriter()
                .print("<div class='error'><img src='"
                        + req.getContextPath()
                        + Jenkins.RESOURCE_PATH
                        + "/images/none.gif' height=16 width=1>"
                        + Util.escape(getMessage())
                        + "</div>");
    }
}
