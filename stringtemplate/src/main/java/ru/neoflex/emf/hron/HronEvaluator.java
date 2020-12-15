package ru.neoflex.emf.hron;

import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class HronEvaluator extends HronBaseListener {
    Resource resource;
    HronSupport support;
    ParseTreeProperty<EObject> eObjects = new ParseTreeProperty<>();
    Stack<EObject> objectStack = new Stack<>();
    Stack<EStructuralFeature> featureStack = new Stack<>();
    Map<String, EObject> labeledEObjects = new HashMap<>();

    public HronEvaluator(Resource resource, HronSupport support) {
        this.resource = resource;
        this.support = support;
    }

    void error(String msg, Token token) {
        throw new IllegalArgumentException(msg + " [" + token.getLine() + ", " + token.getCharPositionInLine() + "]");
    }

    @Override
    public void enterEObject(HronParser.EObjectContext ctx) {
        if (objectStack.size() == 0) {
            HronParser.EClassContext eClassCtx = ctx.eClass();
            if (eClassCtx == null) {
                error("Class not specified", ctx.start);
            }
            String nsPrefix = ctx.eClass().ID(0).getText();
            String name = ctx.eClass().ID(1).getText();
            EClass eClass = support.lookupEClass(resource.getResourceSet(), nsPrefix, name);
            EObject eObject = EcoreUtil.create(eClass);
            objectStack.push(eObject);
            if (ctx.label() != null) {
                String label = ctx.label().getText();
                if (labeledEObjects.containsKey(label)) {
                    error("Duplicate label " + label, ctx.start);
                }
                labeledEObjects.put(label, eObject);
            }
            eObjects.put(ctx, eObject);
        }
    }

    @Override
    public void exitEObject(HronParser.EObjectContext ctx) {
        objectStack.pop();
    }

    @Override
    public void enterEFeature(HronParser.EFeatureContext ctx) {
        EObject eObject = objectStack.peek();
        EClass eClass = eObject.eClass();
        EStructuralFeature sf = eClass.getEStructuralFeature(ctx.ID().getText());
        featureStack.push(sf);
    }

    @Override
    public void exitEFeature(HronParser.EFeatureContext ctx) {
        featureStack.pop();
    }

    @Override
    public void exitResource(HronParser.ResourceContext ctx) {
        ctx.eObject().forEach(eObjectContext -> {
            EObject eObject = eObjects.get(eObjectContext);
            resource.getContents().add(eObject);
        });
    }
}
