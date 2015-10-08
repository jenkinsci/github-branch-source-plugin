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

package org.jenkinsci.plugins.github.multibranch;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.util.regex.Pattern;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Stephen Connolly
 */
public class Endpoint extends AbstractDescribableImpl<Endpoint> {
    private final String name;
    private final String apiUri;

    @DataBoundConstructor
    public Endpoint(String apiUri, String name) {
        this.apiUri = Util.fixEmptyAndTrim(apiUri);
        this.name = Util.fixEmptyAndTrim(name);
    }

    public String getApiUri() {
        return apiUri;
    }

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

        if (apiUri != null ? !apiUri.equals(endpoint.apiUri) : endpoint.apiUri != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return apiUri != null ? apiUri.hashCode() : 0;
    }

    @Extension
    public static class DesciptorImpl extends Descriptor<Endpoint> {

        @Override
        public String getDisplayName() {
            return "";
        }

        public FormValidation doCheckApiUrl(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error("You must specify the API URI");
            }
            Pattern enterprise = Pattern.compile("^https?://(([a-zA-Z]|[a-zA-Z][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*"
                    + "([A-Za-z]|[A-Za-z][A-Za-z0-9\\-]*[A-Za-z0-9])/api/v3/?$");
            if (!enterprise.matcher(value).matches()) {
                return FormValidation.warning(
                        "This does not look like a GitHub Enterprise API URI. Expecting something like "
                                + "https://[hostname or ip]/api/v3/");
            }
            // TODO validate connection
            return FormValidation.ok();
        }

        public FormValidation doCheckApiName(@QueryParameter String value) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.warning("Specifying a name is recommended");
            }
            return FormValidation.ok();
        }
    }
}
