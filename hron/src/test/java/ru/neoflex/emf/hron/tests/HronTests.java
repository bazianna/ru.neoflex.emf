package ru.neoflex.emf.hron.tests;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.Assert;
import org.junit.Test;
import ru.neoflex.emf.hron.HronPackage;
import ru.neoflex.emf.hron.HronResourceSet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class HronTests {
    public static final Logger logger = Logger.getLogger(HronTests.class.getName());
    private final EPackage.Registry packageRegistry = new EPackageRegistryImpl(EPackage.Registry.INSTANCE);

    {
        packageRegistry.put(HronPackage.eINSTANCE.getNsPrefix(), HronPackage.eINSTANCE);
    }

    @Test
    public void simpleTest() throws IOException, URISyntaxException {
        HronResourceSet rs = new HronResourceSet(null);
        rs.setPackageRegistry(packageRegistry);
        URI uri1 = URI.createURI(this.getClass().getClassLoader().getResource("module1.hron").toString());
        Resource resource1 = rs.createResource(uri1);
        resource1.load(null);
        URI uri2 = URI.createURI(this.getClass().getClassLoader().getResource("module2.hron").toString());
        Resource resource2 = rs.createResource(uri2);
        resource2.load(null);
        rs.resolveAllReferences();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        resource1.save(os, null);
        String s = os.toString("utf-8");
        logger.info(s);
        String old = new String(
                Files.readAllBytes(Paths.get(this.getClass().getClassLoader().getResource("module1.hron").toURI())
                ), StandardCharsets.UTF_8);
        Assert.assertEquals(old, s);
    }
}
