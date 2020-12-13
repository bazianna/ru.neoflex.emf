package ru.neoflex.emf.hron;

import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

public class HronEvaluator extends HronBaseListener {
    Resource resource;
    HronSupport support;
    ParseTreeProperty<EObject> eObjects = new ParseTreeProperty<>();

    public HronEvaluator(Resource resource, HronSupport support) {
        this.resource = resource;
        this.support = support;
    }
}
