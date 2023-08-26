package org.jenkinsci.plugins.github_branch_source;

import hudson.Util;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

// TODO replace with corresponding core functionality once Jenkins core has JENKINS-42443
class FillErrorResponse extends IOException implements HttpResponse {

    private final boolean clearList;

    public FillErrorResponse(String message, boolean clearList) {
        super(message);
        this.clearList = clearList;
    }

    @Override
    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node)
            throws IOException, ServletException {
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
