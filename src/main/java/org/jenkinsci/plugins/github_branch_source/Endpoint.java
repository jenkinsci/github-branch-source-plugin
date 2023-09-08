/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.github_branch_source;

import com.fasterxml.jackson.core.JsonParseException;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMName;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/** @author Stephen Connolly */
public class Endpoint extends AbstractDescribableImpl<Endpoint> {
    /** Common prefixes that we should remove when inferring a display name. */
    private static final String[] COMMON_PREFIX_HOSTNAMES = {"git.", "github.", "vcs.", "scm.", "source."};

    private final String name;
    private final String apiUri;

    @DataBoundConstructor
    public Endpoint(String apiUri, String name) {
        this.apiUri = GitHubConfiguration.normalizeApiUri(Util.fixEmptyAndTrim(apiUri));
        if (StringUtils.isBlank(name)) {
            this.name = SCMName.fromUrl(this.apiUri, COMMON_PREFIX_HOSTNAMES);
        } else {
            this.name = name.trim();
        }
    }

    private Object readResolve() throws ObjectStreamException {
        if (!apiUri.equals(GitHubConfiguration.normalizeApiUri(apiUri))) {
            return new Endpoint(apiUri, name);
        }
        return this;
    }

    @NonNull
    public String getApiUri() {
        return apiUri;
    }

    @CheckForNull
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Endpoint{");
        sb.append("apiUrl='").append(apiUri).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Endpoint)) {
            return false;
        }

        Endpoint endpoint = (Endpoint) o;

        if (!Objects.equals(apiUri, endpoint.apiUri)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return apiUri != null ? apiUri.hashCode() : 0;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Endpoint> {

        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        @Override
        public String getDisplayName() {
            return "";
        }

        @RequirePOST
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckApiUri(@QueryParameter String apiUri) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (Util.fixEmptyAndTrim(apiUri) == null) {
                return FormValidation.warning("You must specify the API URL");
            }
            try {
                URL api = new URL(apiUri);
                GitHub github = GitHub.connectToEnterpriseAnonymously(api.toString());
                github.checkApiUrlValidity();
                LOGGER.log(Level.FINE, "Trying to configure a GitHub Enterprise server");
                // For example: https://api.github.com/ or https://github.mycompany.com/api/v3/ (with
                // private mode disabled).
                return FormValidation.ok("GitHub Enterprise server verified");
            } catch (MalformedURLException mue) {
                // For example: https:/api.github.com
                LOGGER.log(Level.WARNING, "Trying to configure a GitHub Enterprise server: " + apiUri, mue.getCause());
                return FormValidation.error("The endpoint does not look like a GitHub Enterprise (malformed URL)");
            } catch (JsonParseException jpe) {
                LOGGER.log(Level.WARNING, "Trying to configure a GitHub Enterprise server: " + apiUri, jpe.getCause());
                return FormValidation.error(
                        "The endpoint does not look like a GitHub Enterprise (invalid JSON response)");
            } catch (FileNotFoundException fnt) {
                // For example: https://github.mycompany.com/server/api/v3/ gets a FileNotFoundException
                LOGGER.log(Level.WARNING, "Getting HTTP Error 404 for " + apiUri);
                return FormValidation.error("The endpoint does not look like a GitHub Enterprise (page not found");
            } catch (IOException e) {
                // For example: https://github.mycompany.com/api/v3/ or
                // https://github.mycompany.com/api/v3/mypath
                if (e.getMessage().contains("private mode enabled")) {
                    LOGGER.log(Level.FINE, e.getMessage());
                    return FormValidation.warning("Private mode enabled, validation disabled");
                }
                LOGGER.log(Level.WARNING, e.getMessage());
                return FormValidation.error(
                        "The endpoint does not look like a GitHub Enterprise (verify network and/or try again later)");
            }
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckName(@QueryParameter String name) {
            if (Util.fixEmptyAndTrim(name) == null) {
                return FormValidation.warning("A name is recommended to help differentiate similar endpoints");
            }
            return FormValidation.ok();
        }
    }
}
