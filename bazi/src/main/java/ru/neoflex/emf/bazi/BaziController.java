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
import java.util.*;

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
            resources.add(DroolsSvc.createDecisionTableResource("drools/baZiDate.xls", DecisionTableInputType.XLS));
//            resources.add(DroolsSvc.createDecisionTableResource("drools/hourPillar.xls", DecisionTableInputType.XLS));






            Date dayEndFull = null;
            try {
                DateFormat formatterFull = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                dayEndFull = formatterFull.parse("2.2.2020 20:02");
                Long days = dayEndFull.getTime();
                Double UTC = 12.5;

                Calendar cal = Calendar.getInstance();
                cal.setTime(dayEndFull);
                cal.add(Calendar.HOUR_OF_DAY, (int) Math.floor(UTC));
                cal.add(Calendar.MINUTE, (int)((UTC - Math.floor(UTC))*60));
                cal.getTime();

                Integer year = cal.get(Calendar.YEAR);
                Integer month = cal.get(Calendar.MONTH) + 1;
                Integer day = cal.get(Calendar.DAY_OF_MONTH);
                Integer hour = cal.get(Calendar.HOUR_OF_DAY);
                Integer minute = cal.get(Calendar.MINUTE);


            } catch (ParseException e) {
                e.printStackTrace();
            }

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
                           Double UTC,
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
            // TODO: parameters.setUTC(UTC);
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
