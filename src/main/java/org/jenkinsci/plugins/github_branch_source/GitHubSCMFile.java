/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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
 *
 */

package org.jenkinsci.plugins.github_branch_source;

import com.fasterxml.jackson.databind.JsonMappingException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import jenkins.scm.api.SCMFile;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

class GitHubSCMFile extends SCMFile {

    private TypeInfo info;
    private final GitHubClosable closable;
    private final GHRepository repo;
    private final String ref;
    private transient Object metadata;
    private transient boolean resolved;

    GitHubSCMFile(GitHubClosable closable, GHRepository repo, String ref) {
        super();
        this.closable = closable;
        type(Type.DIRECTORY);
        info = TypeInfo.DIRECTORY_ASSUMED; // we have not resolved the metadata yet
        this.repo = repo;
        this.ref = ref;
    }

    private GitHubSCMFile(@NonNull GitHubSCMFile parent, String name, TypeInfo info) {
        super(parent, name);
        this.closable = parent.closable;
        this.info = info;
        this.repo = parent.repo;
        this.ref = parent.ref;
    }

    private GitHubSCMFile(@NonNull GitHubSCMFile parent, String name, GHContent metadata) {
        super(parent, name);
        this.closable = parent.closable;
        this.repo = parent.repo;
        this.ref = parent.ref;
        if (metadata.isDirectory()) {
            info = TypeInfo.DIRECTORY_CONFIRMED;
            // we have not listed the children yet, but we know it is a directory
        } else {
            info = TypeInfo.NON_DIRECTORY_CONFIRMED;
            this.metadata = metadata;
            resolved = true;
        }
    }

    private void checkOpen() throws IOException {
        if (!closable.isOpen() || (!resolved && repo == null)) {
            throw new IOException("Closed");
        }
    }

    private Object metadata() throws IOException {
        if (metadata == null && !resolved) {
            try {
                switch (info) {
                    case DIRECTORY_ASSUMED:
                        metadata = repo.getDirectoryContent(
                                getPath(), ref.indexOf('/') == -1 ? ref : Constants.R_REFS + ref);
                        info = TypeInfo.DIRECTORY_CONFIRMED;
                        resolved = true;
                        break;
                    case DIRECTORY_CONFIRMED:
                        metadata = repo.getDirectoryContent(
                                getPath(), ref.indexOf('/') == -1 ? ref : Constants.R_REFS + ref);
                        resolved = true;
                        break;
                    case NON_DIRECTORY_CONFIRMED:
                        metadata =
                                repo.getFileContent(getPath(), ref.indexOf('/') == -1 ? ref : Constants.R_REFS + ref);
                        resolved = true;
                        break;
                    case UNRESOLVED:
                        checkOpen();
                        try {
                            metadata = repo.getFileContent(
                                    getPath(), ref.indexOf('/') == -1 ? ref : Constants.R_REFS + ref);
                            info = TypeInfo.NON_DIRECTORY_CONFIRMED;
                            resolved = true;
                        } catch (IOException e) {
                            // Upcoming version of github-api hoists JsonMappingException up one level
                            // Support both the old and the new structure
                            if (e.getCause() instanceof JsonMappingException
                                    || e.getCause() != null
                                            && e.getCause().getCause() instanceof JsonMappingException) {
                                metadata = repo.getDirectoryContent(
                                        getPath(), ref.indexOf('/') == -1 ? ref : Constants.R_REFS + ref);
                                info = TypeInfo.DIRECTORY_CONFIRMED;
                                resolved = true;
                            } else {
                                throw e;
                            }
                        }
                        break;
                }
            } catch (FileNotFoundException e) {
                metadata = null;
                resolved = true;
            }
        }
        return metadata;
    }

    @NonNull
    @Override
    protected SCMFile newChild(String name, boolean assumeIsDirectory) {
        return new GitHubSCMFile(this, name, assumeIsDirectory ? TypeInfo.DIRECTORY_ASSUMED : TypeInfo.UNRESOLVED);
    }

    @NonNull
    @Override
    public Iterable<SCMFile> children() throws IOException {
        checkOpen();
        List<GHContent> content =
                repo.getDirectoryContent(getPath(), ref.indexOf('/') == -1 ? ref : Constants.R_REFS + ref);
        List<SCMFile> result = new ArrayList<>(content.size());
        for (GHContent c : content) {
            result.add(new GitHubSCMFile(this, c.getName(), c));
        }
        return result;
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        // TODO see if we can find a way to implement it
        return 0L;
    }

    @NonNull
    @Override
    protected Type type() throws IOException, InterruptedException {
        Object metadata = metadata();
        if (metadata instanceof List) {
            return Type.DIRECTORY;
        }
        if (metadata instanceof GHContent) {
            GHContent content = (GHContent) metadata;
            if ("symlink".equals(content.getType())) {
                return Type.LINK;
            }
            if (content.isFile()) {
                return Type.REGULAR_FILE;
            }
            return Type.OTHER;
        }
        return Type.NONEXISTENT;
    }

    @NonNull
    @Override
    public InputStream content() throws IOException, InterruptedException {
        Object metadata = metadata();
        if (metadata instanceof List) {
            throw new IOException("Directory");
        }
        if (metadata instanceof GHContent) {
            return ((GHContent) metadata).read();
        }
        throw new FileNotFoundException(getPath());
    }

    private enum TypeInfo {
        UNRESOLVED,
        DIRECTORY_ASSUMED,
        DIRECTORY_CONFIRMED,
        NON_DIRECTORY_CONFIRMED;
    }
}
