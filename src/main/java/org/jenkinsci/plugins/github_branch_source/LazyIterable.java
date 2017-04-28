package org.jenkinsci.plugins.github_branch_source;

import java.util.Iterator;

abstract class LazyIterable<V> implements Iterable<V> {
    private Iterable<V> delegate;

    protected abstract Iterable<V> create();
    @Override
    public synchronized Iterator<V> iterator() {
        if (delegate == null) {
            delegate = create();
        }
        return delegate.iterator();
    }
}
