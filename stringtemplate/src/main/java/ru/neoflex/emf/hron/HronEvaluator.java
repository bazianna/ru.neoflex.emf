package ru.neoflex.emf.hron;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class HronEvaluator extends HronBaseListener {
    Resource resource;
    HronSupport support;
    ParseTreeProperty<EObject> eObjects = new ParseTreeProperty<>();
    Stack<EObject> objectStack = new Stack<>();
    Stack<EStructuralFeature> featureStack = new Stack<>();
    Map<String, EObject> labeledEObjects = new HashMap<>();
    String nsPrefix;

    public HronEvaluator(Resource resource, HronSupport support) {
        this.resource = resource;
        this.support = support;
    }

    void error(String msg, Token token) {
        throw new IllegalArgumentException(msg + " [line:" + token.getLine() + ", pos:" + token.getCharPositionInLine() + "]");
    }

    @Override
    public void enterEObject(HronParser.EObjectContext ctx) {
        EObject eObject;
        HronParser.EClassContext eClassCtx = ctx.eClass();
        if (objectStack.size() == 0) {
            if (eClassCtx == null) {
                error("Class not specified", ctx.start);
            }
            EClass eClass = getEClass(eClassCtx);
            eObject = EcoreUtil.create(eClass);
        }
        else {
            EStructuralFeature sf = featureStack.peek();
            if (!(sf instanceof EReference) || !((EReference)sf).isContainment()) {
                error(String.format("Feature '%s' has to be containment reference to contains EObject", sf.getName()), ctx.start);
            }
            EReference eReference = (EReference) sf;
            EClass eClass = eReference.getEReferenceType();
            if (eClassCtx != null) {
                eClass = getEClass(eClassCtx);
            }
            eObject = EcoreUtil.create(eClass);
            EObject owner = objectStack.peek();
            if (sf.isMany()) {
                ((List)owner.eGet(sf)).add(eObject);
            }
            else {
                owner.eSet(sf, eObject);
            }
        }
        objectStack.push(eObject);
        if (ctx.label() != null) {
            String label = ctx.label().getText();
            if (labeledEObjects.containsKey(label)) {
                error(String.format("Duplicate label '%s'", label), ctx.start);
            }
            labeledEObjects.put(label, eObject);
        }
        eObjects.put(ctx, eObject);
    }

    private EClass getEClass(HronParser.EClassContext eClassCtx) {
        if (eClassCtx.ID().size() == 1) {
            if (nsPrefix == null) {
                error(String.format("NsPrefix not defined : %s", eClassCtx.getText()), eClassCtx.start);
            }
            return support.lookupEClass(resource.getResourceSet(), nsPrefix, eClassCtx.ID(0).getText());
        }
        return support.lookupEClass(resource.getResourceSet(), eClassCtx.ID(0).getText(), eClassCtx.ID(1).getText());
    }

    @Override
    public void exitEObject(HronParser.EObjectContext ctx) {
        objectStack.pop();
    }

    @Override
    public void enterEFeature(HronParser.EFeatureContext ctx) {
        EObject eObject = objectStack.peek();
        EClass eClass = eObject.eClass();
        String feature = ctx.ID().getText();
        EStructuralFeature sf = eClass.getEStructuralFeature(feature);
        if (sf == null) {
            error(String.format("Feature '%s' not found in '%s'", feature, eClass.getName()), ctx.start);
        }
        featureStack.push(sf);
    }

    @Override
    public void exitEFeature(HronParser.EFeatureContext ctx) {
        featureStack.pop();
    }

    @Override
    public void enterResource(HronParser.ResourceContext ctx) {
        if (ctx.nsPrefix() != null) {
            nsPrefix = ctx.nsPrefix().getText();
        }
    }

    @Override
    public void exitResource(HronParser.ResourceContext ctx) {
        ctx.eObject().forEach(eObjectContext -> {
            EObject eObject = eObjects.get(eObjectContext);
            resource.getContents().add(eObject);
        });
    }

    @Override
    public void enterAttribute(HronParser.AttributeContext ctx) {
        EObject eObject = objectStack.peek();
        EStructuralFeature sf = featureStack.peek();
        if (!(sf instanceof EAttribute)) {
            error(String.format("EReference '%s' can't contains attribute", sf.getName()), ctx.start);
        }
        EAttribute eAttribute = (EAttribute) sf;
        String literal = ctx.STRING().getText().replaceAll("(^\\\"|\\\"$)", "");
        Object value = EcoreUtil.createFromString(eAttribute.getEAttributeType(), literal);
        if (eAttribute.isMany()) {
            ((List)eObject.eGet(eAttribute)).add(value);
        }
        else {
            eObject.eSet(eAttribute, value);
        }
    }
}
