package org.metaborg.core.language;

import org.apache.commons.vfs2.FileObject;

import rx.functions.Func1;

/**
 * Represents a facet that can identify resources languages.
 */
public class IdentificationFacet implements IFacet {
    private final Func1<FileObject, Boolean> identifier;


    /**
     * Creates an identification facet using an identification function.
     * 
     * @param identifier
     *            The identification function.
     */
    public IdentificationFacet(Func1<FileObject, Boolean> identifier) {
        this.identifier = identifier;
    }


    public boolean identify(FileObject file) {
        return identifier.call(file);
    }
}
