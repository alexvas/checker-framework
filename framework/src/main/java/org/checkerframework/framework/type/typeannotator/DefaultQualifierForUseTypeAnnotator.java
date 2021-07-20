package org.checkerframework.framework.type.typeannotator;

import org.checkerframework.checker.signature.qual.CanonicalName;
import org.checkerframework.framework.qual.DefaultQualifierForUse;
import org.checkerframework.framework.qual.NoDefaultQualifierForUse;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.util.AnnotationMirrorSet;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;

/** Implements support for {@link DefaultQualifierForUse} and {@link NoDefaultQualifierForUse}. */
public class DefaultQualifierForUseTypeAnnotator extends TypeAnnotator {

    /** Creates an DefaultQualifierForUseTypeAnnotator for {@code typeFactory} */
    public DefaultQualifierForUseTypeAnnotator(AnnotatedTypeFactory typeFactory) {
        super(typeFactory);
    }

    @Override
    public Void visitDeclared(AnnotatedDeclaredType type, Void aVoid) {
        Element element = type.getUnderlyingType().asElement();
        Set<AnnotationMirror> annosToApply = getDefaultAnnosForUses(element);
        type.addMissingAnnotations(annosToApply);
        return super.visitDeclared(type, aVoid);
    }

    /**
     * Cache of elements to the set of annotations that should be applied to unannotated uses of the
     * element.
     */
    protected Map<Element, Set<AnnotationMirror>> elementToDefaults =
            CollectionUtils.createLRUCache(100);

    /** Clears all caches. */
    public void clearCache() {
        elementToDefaults.clear();
    }

    /** Returns the set of qualifiers that should be applied to unannotated uses of this element. */
    protected Set<AnnotationMirror> getDefaultAnnosForUses(Element element) {
        if (typeFactory.shouldCache && elementToDefaults.containsKey(element)) {
            return elementToDefaults.get(element);
        }
        Set<AnnotationMirror> explictAnnos = getExplicitAnnos(element);
        Set<AnnotationMirror> defaultAnnos = getDefaultQualifierForUses(element);
        Set<AnnotationMirror> noDefaultAnnos = getHierarchiesNoDefault(element);
        AnnotationMirrorSet annosToApply = new AnnotationMirrorSet();

        for (AnnotationMirror top : typeFactory.getQualifierHierarchy().getTopAnnotations()) {
            if (AnnotationUtils.containsSame(noDefaultAnnos, top)) {
                continue;
            }
            AnnotationMirror defaultAnno =
                    typeFactory
                            .getQualifierHierarchy()
                            .findAnnotationInHierarchy(defaultAnnos, top);
            if (defaultAnno != null) {
                annosToApply.add(defaultAnno);
            } else {
                AnnotationMirror explict =
                        typeFactory
                                .getQualifierHierarchy()
                                .findAnnotationInHierarchy(explictAnnos, top);
                if (explict != null) {
                    annosToApply.add(explict);
                }
            }
        }
        // If parsing stub files, then the annosToApply is incomplete, so don't cache them.
        if (typeFactory.shouldCache
                && !typeFactory.stubTypes.isParsing()
                && !typeFactory.ajavaTypes.isParsing()) {
            elementToDefaults.put(element, annosToApply);
        }
        return annosToApply;
    }

    /** Return the annotations explicitly written on the element. */
    protected Set<AnnotationMirror> getExplicitAnnos(Element element) {
        AnnotatedTypeMirror explicitAnnoOnDecl = typeFactory.fromElement(element);
        return explicitAnnoOnDecl.getAnnotations();
    }

    /**
     * Return the default qualifiers for uses of {@code element} as specified by a {@link
     * DefaultQualifierForUse} annotation.
     *
     * <p>Subclasses may override to use an annotation other than {@link DefaultQualifierForUse}.
     *
     * @param element an element
     * @return the default qualifiers for uses of {@code element}
     */
    protected Set<AnnotationMirror> getDefaultQualifierForUses(Element element) {
        AnnotationMirror defaultQualifier =
                typeFactory.getDeclAnnotation(element, DefaultQualifierForUse.class);
        if (defaultQualifier == null) {
            return Collections.emptySet();
        }
        return supportedAnnosFromAnnotationMirror(
                AnnotationUtils.getElementValueClassNames(defaultQualifier, "value", true));
    }

    /**
     * Returns top annotations in hierarchies for which no default for use qualifier should be
     * added.
     */
    protected Set<AnnotationMirror> getHierarchiesNoDefault(Element element) {
        AnnotationMirror noDefaultQualifier =
                typeFactory.getDeclAnnotation(element, NoDefaultQualifierForUse.class);
        if (noDefaultQualifier == null) {
            return Collections.emptySet();
        }
        return supportedAnnosFromAnnotationMirror(
                AnnotationUtils.getElementValueClassNames(noDefaultQualifier, "value", true));
    }

    /**
     * Returns the set of qualifiers supported by this type system from the value element of {@code
     * annotationMirror}.
     *
     * @param annotationMirror a non-null annotation with a value element that is an array of
     *     annotation classes
     * @return the set of qualifiers supported by this type system from the value element of {@code
     *     annotationMirror}
     * @deprecated use {@link #supportedAnnosFromAnnotationMirror(List)}
     */
    @Deprecated // 2021-03-21
    protected final AnnotationMirrorSet supportedAnnosFromAnnotationMirror(
            AnnotationMirror annotationMirror) {
        return supportedAnnosFromAnnotationMirror(
                AnnotationUtils.getElementValueClassNames(annotationMirror, "value", true));
    }

    /**
     * Returns the set of qualifiers supported by this type system from the value element of {@code
     * annotationMirror}.
     *
     * @param annoClassNames a list of annotation class names
     * @return the set of qualifiers supported by this type system from the value element of {@code
     *     annotationMirror}
     */
    protected final AnnotationMirrorSet supportedAnnosFromAnnotationMirror(
            List<@CanonicalName Name> annoClassNames) {
        AnnotationMirrorSet supportAnnos = new AnnotationMirrorSet();
        for (Name annoName : annoClassNames) {
            AnnotationMirror anno =
                    AnnotationBuilder.fromName(typeFactory.getElementUtils(), annoName);
            if (typeFactory.isSupportedQualifier(anno)) {
                supportAnnos.add(anno);
            }
        }
        return supportAnnos;
    }
}