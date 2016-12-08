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
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;

class GitHubSCMFile extends SCMFile {

    private TypeInfo info;
    private final GHRepository repo;
    private final String ref;
    private transient Object metadata;
    private transient boolean resolved;

    GitHubSCMFile(GHRepository repo, String ref) {
        super();
        type(Type.DIRECTORY);
        info = TypeInfo.DIRECTORY_ASSUMED; // we have not resolved the metadata yet
        this.repo = repo;
        this.ref = ref;
    }

    private GitHubSCMFile(@NonNull GitHubSCMFile parent, String name, TypeInfo info) {
        super(parent, name);
        this.info = info;
        this.repo = parent.repo;
        this.ref = parent.ref;
    }

    private GitHubSCMFile(@NonNull GitHubSCMFile parent, String name, GHContent metadata) {
        super(parent, name);
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

    private Object metadata() throws IOException {
        if (metadata == null && !resolved) {
            try {
                switch (info) {
                    case DIRECTORY_ASSUMED:
                        metadata = repo.getDirectoryContent(getPath(), ref);
                        info = TypeInfo.DIRECTORY_CONFIRMED;
                        resolved = true;
                        break;
                    case DIRECTORY_CONFIRMED:
                        metadata = repo.getDirectoryContent(getPath(), ref);
                        resolved = true;
                        break;
                    case NON_DIRECTORY_CONFIRMED:
                        metadata = repo.getFileContent(getPath(), ref);
                        resolved = true;
                        break;
                    case UNRESOLVED:
                        try {
                            metadata = repo.getFileContent(getPath(), ref);
                            info = TypeInfo.NON_DIRECTORY_CONFIRMED;
                            resolved = true;
                        } catch (IOException e) {
                            if (e.getCause() instanceof IOException
                                    && e.getCause().getCause() instanceof JsonMappingException) {
                                metadata = repo.getDirectoryContent(getPath(), ref);
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
    public SCMFile child(String path) {
        int index = path.indexOf('/');
        if (index == -1) {
            if (".".equals(path)) {
                return this;
            }
            if ("..".equals(path)) {
                SCMFile parent = parent();
                return parent == null ? this : parent;
            }
            return new GitHubSCMFile(this, path, TypeInfo.UNRESOLVED);
        }
        String name = path.substring(0, index);
        SCMFile next;
        if (".".equals(name)) {
            next = this;
        } else if ("..".equals(name)) {
            SCMFile parent = parent();
            next = parent == null ? this : parent;
        } else {
            next = new GitHubSCMFile(this, name, TypeInfo.DIRECTORY_ASSUMED);
        }
        String restOfPath = path.substring(index + 1);
        return StringUtils.isBlank(restOfPath) ? next : next.child(restOfPath);
    }

    @NonNull
    @Override
    public Iterable<SCMFile> children() throws IOException {
        List<GHContent> content = repo.getDirectoryContent(getPath(), ref);
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
            return ((GHContent)metadata).read();
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
