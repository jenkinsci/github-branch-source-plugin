package org.jenkinsci.plugins.github_branch_source;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

abstract class LazySet<E> extends AbstractSet<E> {
    private Set<E> delegate;

    protected abstract Set<E> create();

    private synchronized Set<E> delegate() {
        if (delegate == null) {
            delegate = create();
        }
        return delegate;
    }

    @Override
    public int size() {
        return delegate().size();
    }

    @Override
    public boolean isEmpty() {
        return delegate().isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate().contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return delegate().iterator();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate().toArray(a);
    }

    @Override
    public boolean add(E e) {
        return delegate().add(e);
    }

    @Override
    public boolean remove(Object o) {
        return delegate().remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return delegate().addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return delegate().retainAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate().removeAll(c);
    }

    @Override
    public void clear() {
        delegate().clear();
    }

    @Override
    public boolean equals(Object o) {
        return delegate().equals(o);
    }

    @Override
    public int hashCode() {
        return delegate().hashCode();
    }
}
