package ru.neoflex.emf.bazi;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.kie.internal.builder.DecisionTableInputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.neoflex.emf.bazi.natalChart.*;
import ru.neoflex.emf.drools.DroolsSvc;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RestController()
@RequestMapping("/bazi")
public class BaziController {
    final
    DroolsSvc droolsSvc;
    final
    DBServerSvc dbServerSvc;

    private static final Logger logger = LoggerFactory.getLogger(BaziController.class);

    public BaziController(DroolsSvc droolsSvc, DBServerSvc dbServerSvc) {
        this.droolsSvc = droolsSvc;
        this.dbServerSvc = dbServerSvc;
    }

    public Integer daysBetween(Date d1, Date d2){
        return (int)( (d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24));
    }

    @PostConstruct
    void init() {
        dbServerSvc.getDbServer().registerEPackage(NatalChartPackage.eINSTANCE);
        droolsSvc.getGlobals().add(new AbstractMap.SimpleEntry<>("dbServerSvc", dbServerSvc));
        droolsSvc.getResourceFactories().add(() -> {
            List<Resource> resources = new ArrayList<>();
            resources.add(DroolsSvc.createClassPathResource("drools/baseRules.drl", null));
            resources.add(DroolsSvc.createDecisionTableResource("drools/calendar.xls", DecisionTableInputType.XLS));
            resources.add(DroolsSvc.createDecisionTableResource("drools/hourPillar.xls", DecisionTableInputType.XLS));


//            try {
//                byte[] bazi = Files.readAllBytes(Paths.get(System.getProperty("user.dir"), "bazi", "rules", "bazi.drl"));
//                resources.add(DroolsSvc.createByteArrayResource("bazi.drl", null, bazi));
//            }
//            catch (IOException e) {
//                throw new IllegalArgumentException(e);
//            }
            return resources;
        });
        droolsSvc.setDebug(true);
    }

    @PostMapping("/refreshRules")
    void refreshRules() {
        droolsSvc.disposeContainer();
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
            InputParams parameters = NatalChartFactory.eINSTANCE.createInputParams();
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
                QueryResults queryResults = kieSession.getQueryResults("EObjects");
                org.eclipse.emf.ecore.resource.Resource eObjects = tx.createResource();
                for (QueryResultsRow row: queryResults) {
                    Object o = row.get("$eObject");
                    if (o instanceof EObject) {
                        EObject eObject = (EObject) o;
                        if (EcoreUtil.getRootContainer(eObject) == eObject) {
                            eObjects.getContents().add(eObject);
                        }
                    }
                }
                eObjects.save(null);
                eObjects.getContents().removeIf(eObject -> !(eObject instanceof NatalChart));
                eObjects.load(null);
                return DBServerSvc.createJsonHelper().toJson(eObjects);
            }
            finally {
                kieSession.dispose();
            }
        });
    }


}
