package ru.neoflex.emf.restserver;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xcore.XcoreStandaloneSetup;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@SpringBootTest
public class XcoreTests {

    @Test
    public void loadXcore() throws IOException {
        XcoreStandaloneSetup.doSetup();
        ResourceSet rs = new XtextResourceSet();
        URI uri = URI.createURI("classpath:/metamodel/model.xcore");
        Resource resource = rs.getResource(uri, true);
        Assert.notNull(resource);
        EcoreResourceFactoryImpl ef = new EcoreResourceFactoryImpl();
        Resource er = rs.createResource(URI.createURI("model.ecore"));
        er.getContents().add(EcoreUtil.copy(resource.getContents().get(2)));
        Assert.notNull(er);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        er.save(os, null);
        String ecore = os.toString();
        Assert.notNull(ecore);
    }
}
