package ru.neoflex.emf.bazi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ru.neoflex.emf.restserver.DBServerSvc;
import ru.neoflex.emf.restserver.JsonHelper;
import ru.neoflex.nfcore.bazi.natalChart.*;

import javax.annotation.PostConstruct;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

@RestController()
@RequestMapping("/bazi")
public class BaziController {
    @Autowired
    DroolsSvc droolsSvc;
    @Autowired
    DBServerSvc dbServerSvc;

    @PostConstruct
    void init() {
        droolsSvc.getGlobals().add(new AbstractMap.SimpleEntry<String, Object>("dbServerSvc", dbServerSvc));
        droolsSvc.getResourceFactories().add(() -> {
            List<Resource> resources = new ArrayList<>();
            resources.add(DroolsSvc.createClassPathResource("baseRules.drl", null));
            return resources;
        });
    }

    @GetMapping("/natalChart")
    JsonNode getNatalChart(String name,
                           Integer minutes,
                           Integer hour,
                           Integer day,
                           Integer month,
                           Integer year,
                           Integer UTC,
                           String placeOfBirth,
                           Sex sex,
                           boolean joinedRatHour,
                           TimeCategory timeCategory
    ) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            Parameters parameters = NatalChartFactory.eINSTANCE.createParameters();
            parameters.setName(name);
            parameters.setMinutes(minutes);
            parameters.setHour(hour);
            parameters.setDay(day);
            parameters.setMonth(month);
            parameters.setYear(year);
            parameters.setUTC(UTC);
            parameters.setPlaceOfBirth(placeOfBirth);
            parameters.setSex(sex);
            parameters.setJoinedRatHour(joinedRatHour);
            parameters.setTimeCategory(timeCategory);
            KieSession kieSession = droolsSvc.createSession();
            try {
                kieSession.setGlobal("tx", tx);
                kieSession.insert(parameters);
                kieSession.fireAllRules();
                ResourceSet rs = tx.getResourceSet();
                URI uri = tx.getDbServer().createURI();
                org.eclipse.emf.ecore.resource.Resource resource = rs.createResource(uri);
                QueryResults queryResults = kieSession.getQueryResults("NatalCharts");
                for (QueryResultsRow row: queryResults) {
                    Object o = row.get("$natalChart");
                    if (o instanceof NatalChart) {
                        resource.getContents().add((EObject) o);
                    }
                }
                resource.save(null);
                return new JsonHelper().toJson(resource);
            }
            finally {
                kieSession.dispose();
            }
        });
    }


}
