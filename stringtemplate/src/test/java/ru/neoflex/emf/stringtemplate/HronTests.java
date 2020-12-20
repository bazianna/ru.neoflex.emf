package ru.neoflex.emf.stringtemplate;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.neoflex.emf.hron.HbHronSupport;
import ru.neoflex.emf.hron.HronResourceSet;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.util.logging.Logger;

@SpringBootTest(properties = {
        "db-name=hrontest"
}, classes = {ru.neoflex.emf.restserver.DBServerSvc.class})
public class HronTests {
    Logger logger = Logger.getLogger(HronTests.class.getName());

    @Autowired
    private DBServerSvc dbServerSvc;

    @PostConstruct
    void init() {
        dbServerSvc.getDbServer().registerEPackage(StringtemplatePackage.eINSTANCE);
    }

    @Test
    public void loadMyClass() throws Exception {
        HronResourceSet rs = new HronResourceSet(new HbHronSupport(dbServerSvc.getDbServer()));
        URI uri = URI.createURI(this.getClass().getClassLoader().getResource("module1.hron").toString());
        Resource resource = rs.createResource(uri);
        resource.load(null);
        rs.resolveAllReferences();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        resource.save(os, null);
        String s = os.toString("utf-8");
        logger.info(s);
    }
}