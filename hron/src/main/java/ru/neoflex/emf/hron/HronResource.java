package ru.neoflex.emf.hron;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.stringtemplate.v4.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HronResource extends ResourceImpl {
    ParseTree tree;
    HronEvaluator evaluator;
    ParseTreeWalker walker;
    private final Map<EObject, Map<String, Object>> eObjectToMapMap = new IdentityHashMap<>();

    public HronResource(URI uri) {
        super(uri);
    }

    protected void doSave(OutputStream outputStream, Map<?, ?> options) throws IOException {
        byte[] data = getBytes();
        outputStream.write(data);
    }

    public byte[] getBytes(Charset charset) {
        return toString().getBytes(charset);
    }

    public byte[] getBytes() {
        return getBytes(StandardCharsets.UTF_8);
    }

    public String toString() {
        eObjectToMapMap.clear();
        URL patternURL = this.getClass().getClassLoader().getResource("hron.stg");
        Objects.requireNonNull(patternURL, "hron.stg not found");
        STGroup hronStg = new STGroupFile(patternURL);
        ST hronSt = hronStg.getInstanceOf("resource");
        Map<String, Object> resourceMap = resourceToMap();
        hronSt.add("resource", resourceMap);
        StringWriter out = new StringWriter();
        STWriter wr = new AutoIndentWriter(out, "\n");
        wr.setLineWidth(STWriter.NO_WRAP);
        hronSt.write(wr, Locale.getDefault());
        return out.toString();
    }

    protected void doLoad(InputStream inputStream, Map<?, ?> options) throws IOException {
        CharStream input = CharStreams.fromStream(inputStream);
        HronLexer lexer = new HronLexer(input);
        ((List<ANTLRErrorListener>) lexer.getErrorListeners()).add(new ParseErrorListener());
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        HronParser parser = new HronParser(tokens);
        ((List<ANTLRErrorListener>) parser.getErrorListeners()).add(new ParseErrorListener());
        tree = parser.resource();
        evaluator = new HronEvaluator(this);
        walker = new ParseTreeWalker();
        evaluator.setPhase(HronEvaluator.Phase.CONTAINMENT);
        walker.walk(evaluator, tree);
    }

    public void resolve() {
        evaluator.setPhase(HronEvaluator.Phase.NONCONTAINMENT);
        walker.walk(evaluator, tree);
    }

    Map<String, Object> resourceToMap() {
        Map<String, Object> resourceElement = new HashMap<>();
        List<EObject> eObjects = getContents();
        List<Map<String, Object>> eObjectList = eObjectsToList(eObjects);
        resourceElement.put("eObjects", eObjectList);
        return resourceElement;
    }

    private List<Map<String, Object>> eObjectsToList(List<EObject> eObjects) {
        List<Map<String, Object>> eObjectList = new ArrayList<>();
        for (EObject eObject : eObjects) {
            Map<String, Object> eObjectElement = eObjectToMap(eObject);
            eObjectList.add(eObjectElement);
        }
        return eObjectList;
    }

    private Map<String, Object> refObjectToMap(EObject eObject, EObject refObject) {
        Map<String, Object> result = new HashMap<>();
        if (EcoreUtil.isAncestor(eObject.eResource(), refObject)) {
            Map<String, Object> refObjectMap = eObjectToMapMap.computeIfAbsent(refObject, k -> new HashMap<>());
            String label = eObject.eResource().getURIFragment(refObject).replaceAll("[^_0-9a-zA-Z]", "_");
            refObjectMap.put("label", label);
            result.put("label", label);
        } else {
            EObject rootObject = EcoreUtil.getRootContainer(refObject);
            EClass eClass = rootObject.eClass();
            String nsPrefix = eClass.getEPackage().getNsPrefix();
            String className = nsPrefix + "." + eClass.getName();
            result.put("eClass", className);
            String id = EcoreUtil.getID(rootObject);
            result.put("id", id);
            if (id == null) {
                throw new IllegalArgumentException(String.format("Id value for %s not found or not set", className));
            }
            if (rootObject != eObject) {
                result.put("path", EcoreUtil.getRelativeURIFragmentPath(rootObject, refObject));
            }
        }
        return result;
    }

    private List<Map<String, Object>> refObjectsToList(EObject eObject, List<EObject> refObjects) {
        List<Map<String, Object>> eObjectList = new ArrayList<>();
        for (EObject refObject : refObjects) {
            Map<String, Object> refObjectMap = refObjectToMap(eObject, refObject);
            eObjectList.add(refObjectMap);
        }
        return eObjectList;
    }

    private Map<String, Object> eObjectToMap(EObject eObject) {
        Map<String, Object> eObjectMap = eObjectToMapMap.computeIfAbsent(eObject, k -> new HashMap<>());
        EClass eClass = eObject.eClass();
        String nsPrefix = eClass.getEPackage().getNsPrefix();
        String className = nsPrefix + "." + eClass.getName();
        eObjectMap.put("eClass", className);
        List<Map<String, Object>> eFeatures = new ArrayList<>();
        eObjectMap.put("eFeatures", eFeatures);
        for (EStructuralFeature sf : eClass.getEAllStructuralFeatures()) {
            if (!sf.isTransient() && !sf.isDerived() && eObject.eIsSet(sf)) {
                Map<String, Object> eFeature = new HashMap<>();
                eFeature.put("name", sf.getName());
                if (sf instanceof EAttribute) {
                    EAttribute eAttribute = (EAttribute) sf;
                    EDataType eDataType = eAttribute.getEAttributeType();
                    if (!sf.isMany()) {
                        Object value = eObject.eGet(sf);
                        String attribute = getAttributeString(eDataType, value);
                        eFeature.put("attribute", attribute);
                    } else {
                        List<String> attributes = new ArrayList<>();
                        eFeature.put("attributes", attributes);
                        for (Object attributeObj : (List) eObject.eGet(sf)) {
                            String attribute = getAttributeString(eDataType, attributeObj);
                            attributes.add(attribute);
                        }
                    }
                } else {
                    EReference eReference = (EReference) sf;
                    if (eReference.isContainer()) {
                        continue;
                    }
                    if (!sf.isMany()) {
                        EObject refObject = (EObject) eObject.eGet(sf);
                        if (eReference.isContainment()) {
                            eFeature.put("eObject", Collections.singletonList(eObjectToMap(refObject)));
                        } else {
                            eFeature.put("refObject", Collections.singletonList(refObjectToMap(eObject, refObject)));
                        }
                    } else {
                        List<EObject> refObjects = (List<EObject>) eObject.eGet(sf);
                        if (eReference.isContainment()) {
                            eFeature.put("eObjects", eObjectsToList(refObjects));
                        } else {
                            eFeature.put("refObjects", refObjectsToList(eObject, refObjects));
                        }
                    }
                }
                eFeatures.add(eFeature);
            }
        }
        return eObjectMap;
    }

    private String getAttributeString(EDataType eDataType, Object value) {
        return EcoreUtil.convertToString(eDataType, value)
                .replaceAll("\"", "\\\"")
                .replaceAll("\\\\", "\\\\");
    }

    private static class ParseErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            throw new RuntimeException(String.format("%s [%d, %d]", msg, line, charPositionInLine));
        }
    }
}