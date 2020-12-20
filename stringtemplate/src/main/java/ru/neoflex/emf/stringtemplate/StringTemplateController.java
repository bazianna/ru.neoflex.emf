package ru.neoflex.emf.stringtemplate;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.emf.common.util.URI;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.emf.hron.HbHronSupport;
import ru.neoflex.emf.hron.HronResource;
import ru.neoflex.emf.hron.HronResourceSet;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

@RestController()
@RequestMapping("/stringtemplate")
public class StringTemplateController {
    Logger logger = Logger.getLogger(StringTemplateController.class.getName());
    final DBServerSvc dbServerSvc;

    public StringTemplateController(DBServerSvc dbServerSvc) {
        this.dbServerSvc = dbServerSvc;
    }

    @PostConstruct
    void init() {
        dbServerSvc.getDbServer().registerEPackage(StringtemplatePackage.eINSTANCE);
    }

    @PostMapping(value = "/parseHron", consumes = {"text/plain"})
    public ObjectNode parseHron(@RequestBody String sql) throws Exception {
        ByteArrayInputStream is = new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8));
        HronResourceSet rs = new HronResourceSet(new HbHronSupport(dbServerSvc.getDbServer()));
        HronResource resource = (HronResource) rs.createResource(URI.createURI("temp.hron"));
        resource.load(is, null);
        rs.resolveAllReferences();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        resource.save(os, null);
        String code = os.toString("utf-8");
        logger.info(code);
        return DBServerSvc.createJsonHelper().toJson(resource);
    }
}
