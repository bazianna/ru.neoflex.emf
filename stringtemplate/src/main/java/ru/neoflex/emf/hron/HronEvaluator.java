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
    Stack<String> nsPrefixes = new Stack<>();

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public enum Phase {
        CONTAINMENT,
        NONCONTAINMENT
    }
    private Phase phase = Phase.CONTAINMENT;

    public HronEvaluator(Resource resource, HronSupport support) {
        this.resource = resource;
        this.support = support;
    }

    private void error(String msg, Token token) {
        throw new IllegalArgumentException(msg + " [line:" + token.getLine() + ", pos:" + token.getCharPositionInLine() + "]");
    }

    private EClass getEClass(HronParser.EClassContext eClassCtx) {
        EClass eClass;
        if (eClassCtx.ID().size() == 1) {
            if (nsPrefixes.isEmpty()) {
                error(String.format("NsPrefix not defined : %s", eClassCtx.getText()), eClassCtx.start);
            }
            eClass = support.lookupEClass(resource.getResourceSet(), nsPrefixes.peek(), eClassCtx.ID(0).getText());
        }
        else {
            eClass = support.lookupEClass(resource.getResourceSet(), eClassCtx.ID(0).getText(), eClassCtx.ID(1).getText());
        }
        if (eClass == null) {
            error("EClass not found " + eClassCtx.getText(), eClassCtx.start);
        }

        return eClass;
    }

    @Override
    public void enterEObject(HronParser.EObjectContext ctx) {
        HronParser.EClassContext eClassCtx = ctx.eClass();
        EObject eObject = eObjects.get(ctx);
        if (eObject == null) {
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
            if (ctx.label() != null) {
                String label = ctx.label().ID().getText();
                if (labeledEObjects.containsKey(label)) {
                    error(String.format("Duplicate label '%s'", label), ctx.start);
                }
                labeledEObjects.put(label, eObject);
            }
            eObjects.put(ctx, eObject);
        }
        objectStack.push(eObject);
        if (eClassCtx != null && eClassCtx.ID().size() > 1) {
            nsPrefixes.push(eClassCtx.ID(0).getText());
        }
    }

    @Override
    public void exitEObject(HronParser.EObjectContext ctx) {
        HronParser.EClassContext eClassCtx = ctx.eClass();
        if (eClassCtx != null && eClassCtx.ID().size() > 1) {
            nsPrefixes.pop();
        }
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
        if (phase == Phase.CONTAINMENT) {
            if (ctx.nsPrefix() != null) {
                nsPrefixes.push(ctx.nsPrefix().getText());
            }
        }
    }

    @Override
    public void exitResource(HronParser.ResourceContext ctx) {
        if (phase == Phase.CONTAINMENT) {
            ctx.eObject().forEach(eObjectContext -> {
                EObject eObject = eObjects.get(eObjectContext);
                resource.getContents().add(eObject);
            });
            nsPrefixes.pop();
        }
    }

    @Override
    public void enterAttribute(HronParser.AttributeContext ctx) {
        if (phase == Phase.CONTAINMENT) {
            EObject eObject = objectStack.peek();
            EStructuralFeature sf = featureStack.peek();
            if (!(sf instanceof EAttribute)) {
                error(String.format("EReference '%s' can't contains attribute", sf.getName()), ctx.start);
            }
            EAttribute eAttribute = (EAttribute) sf;
            String literal = ctx.STRING().getText()
                    .replaceAll("(^\\\"|\\\"$)", "")
                    .replaceAll("\\\\\"", "\"")
                    .replaceAll("\\\\\\\\", "\\");
            Object value = EcoreUtil.createFromString(eAttribute.getEAttributeType(), literal);
            if (eAttribute.isMany()) {
                ((List)eObject.eGet(eAttribute)).add(value);
            }
            else {
                eObject.eSet(eAttribute, value);
            }
        }
    }

    @Override
    public void enterLabelRef(HronParser.LabelRefContext ctx) {
        if (phase == Phase.NONCONTAINMENT) {
            EObject eObject = objectStack.peek();
            EStructuralFeature sf = featureStack.peek();
            if (!(sf instanceof EReference) || ((EReference)sf).isContainment()) {
                error(String.format("Feature '%s' has to be non-containment reference to refers to labeled EObject", sf.getName()), ctx.start);
            }
            EReference eReference = (EReference) sf;
            String label = ctx.ID().getText();
            EObject refObject = labeledEObjects.get(label);
            if (refObject == null) {
                error(String.format("EObject for label '%s' not found", label), ctx.start);
            }
            if (eReference.isMany()) {
                ((List)eObject.eGet(eReference)).add(refObject);
            }
            else {
                eObject.eSet(eReference, refObject);
            }
        }
    }

    @Override
    public void enterExtRef(HronParser.ExtRefContext ctx) {
        if (phase == Phase.NONCONTAINMENT) {
            EObject eObject = objectStack.peek();
            EStructuralFeature sf = featureStack.peek();
            if (!(sf instanceof EReference) || ((EReference)sf).isContainment()) {
                error(String.format("Feature '%s' has to be non-containment reference to refers to external EObject", sf.getName()), ctx.start);
            }
            EReference eReference = (EReference) sf;
            EClass eClass = getEClass(ctx.eClass());
            String name = ctx.STRING().getText().replaceAll("(^\\\"|\\\"$)", "");
            EObject refObject = support.lookupEObject(resource.getResourceSet(), eClass, name);
            if (refObject == null) {
                error(String.format("EObject for reference to '%s' not found", name), ctx.start);
            }
            String path = ctx.path() == null ? null : ctx.path().getText();
            if (path != null) {
                refObject = EcoreUtil.getEObject(refObject, path);
                if (refObject == null) {
                    error(String.format("EObject for path '%s' not found", path), ctx.start);
                }
            }
            if (eReference.isMany()) {
                ((List)eObject.eGet(eReference)).add(refObject);
            }
            else {
                eObject.eSet(eReference, refObject);
            }
        }
    }
}
