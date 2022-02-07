package ru.neoflex.emf.bazi;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.kie.internal.builder.DecisionTableInputType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import ru.neoflex.emf.bazi.natalChart.InputParams;
import ru.neoflex.emf.bazi.natalChart.NatalChartFactory;
import ru.neoflex.emf.bazi.natalChart.NatalChartPackage;
import ru.neoflex.emf.bazi.natalChart.Sex;
import ru.neoflex.emf.bazi.natalChart.impl.InputParamsImpl;
import ru.neoflex.emf.bazi.natalChart.impl.NatalChartImpl;
import ru.neoflex.emf.drools.DroolsSvc;
import ru.neoflex.emf.restserver.DBServerSvc;
import ru.neoflex.emf.restserver.JsonHelper;
import ru.neoflex.emf.timezonedb.TimezoneDBSvc;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

@RestController()
@RequestMapping("/bazi")
public class BaziController {
    final
    DroolsSvc droolsSvc;
    final
    DBServerSvc dbServerSvc;
    final
    TimezoneDBSvc timezoneDBSvc;

    private static final Logger logger = LoggerFactory.getLogger(BaziController.class);

    public BaziController(DroolsSvc droolsSvc, DBServerSvc dbServerSvc, TimezoneDBSvc timezoneDBSvc) {
        this.droolsSvc = droolsSvc;
        this.dbServerSvc = dbServerSvc;
        this.timezoneDBSvc = timezoneDBSvc;
    }

    @PostConstruct
    void init() {
        dbServerSvc.getDbServer().registerEPackage(NatalChartPackage.eINSTANCE);
        droolsSvc.getGlobals().add(new AbstractMap.SimpleEntry<>("dbServerSvc", dbServerSvc));
        droolsSvc.getGlobals().add(new AbstractMap.SimpleEntry<>("timezoneDBSvc", timezoneDBSvc));
        droolsSvc.getResourceFactories().add(() -> {
            List<Resource> resources = new ArrayList<>();
            resources.add(DroolsSvc.createClassPathResource("drools/baseRules.drl", null));
            resources.add(DroolsSvc.createDecisionTableResource("drools/baZiDate.xls", DecisionTableInputType.XLS));
            resources.add(DroolsSvc.createClassPathResource("drools/gods.drl", null));
            resources.add(DroolsSvc.createClassPathResource("drools/qiPhase.drl", null));
            resources.add(DroolsSvc.createClassPathResource("drools/spirits.drl", null));
            resources.add(DroolsSvc.createClassPathResource("drools/spiritsCalc.drl", null));
            resources.add(DroolsSvc.createDecisionTableResource("drools/conclusion.xls", DecisionTableInputType.XLS));
            resources.add(DroolsSvc.createClassPathResource("drools/conclusions.drl", null));


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
    JsonNode getNatalChart(@RequestParam String name,
                           Integer minutes,
                           Integer hour,
                           Integer day,
                           Integer month,
                           Integer year,
                           String placeOfBirth,
                           Integer sex,
                           boolean hourNotKnown
    ) throws Exception {
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            InputParams parameters = NatalChartFactory.eINSTANCE.createInputParams();
            parameters.setName(name);
            parameters.setMinutes(minutes);
            parameters.setHour(hour);
            parameters.setDay(day);
            parameters.setMonth(month);
            parameters.setYear(year);
            parameters.setPlaceOfBirth(placeOfBirth);
            parameters.setSex(Sex.get(sex));
            parameters.setHourNotKnown(hourNotKnown);
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
//                eObjects.getContents().removeIf(eObject -> !(eObject instanceof NatalChart));
                eObjects.load(null);
                return DBServerSvc.createJsonHelper().toJson(eObjects);
            }
            finally {
                kieSession.dispose();
            }
        });
    }
    JsonHelper jsonHelper = DBServerSvc.createJsonHelper();

    @PostMapping("/createWordPoi")
    void createWord(Long id) throws Exception {
        dbServerSvc.getDbServer().inTransaction(false, tx -> {
            ResourceSet rs = tx.getResourceSet();
            URI uri = tx.getHbServer().createURI(id);
            org.eclipse.emf.ecore.resource.Resource resource = rs.getResource(uri, true);

            if (resource.getContents().get(0).getClass().getName().equals("ru.neoflex.emf.bazi.natalChart.impl.NatalChartImpl")) {
                String folder = System.getProperty("user.dir") + "\\bazi\\src\\main\\resources\\resultFiles\\";
                String fileName = "newFile.docx";

                File f = new File(folder);
                if (!f.exists()) {
                    logger.info("create folder " + folder);
                    f.mkdirs();
                }

                File fn = new File(folder + fileName);
                if (fn.exists()) {
                    logger.info(fileName + " deleted");
                    fn.delete();
                }

                try (XWPFDocument doc = new XWPFDocument()) {

                    // get user name from NatalChart
                    String name = ((InputParamsImpl) ((NatalChartImpl) resource.getContents().get(0)).getInputParams()).getName();
//                    String description = ((NatalChartImpl) resource.getContents().get(0)).getConclusions().get(0).getDescription();
//                    String title = ((NatalChartImpl) resource.getContents().get(0)).getConclusions().get(0).getTitle();
                    String description = "TEST";
                    String title = "YES";

                    // create a paragraph
                    XWPFParagraph p1 = doc.createParagraph();
                    p1.setAlignment(ParagraphAlignment.CENTER);

                    // set font
                    XWPFRun r1 = p1.createRun();
                    r1.setBold(true);
                    r1.setItalic(true);
                    r1.setFontSize(14);
                    r1.setFontFamily("New Roman");
                    r1.setText("Приветствую, " + name);
                    r1.addBreak();

                    // create a paragraph
                    XWPFParagraph p2 = doc.createParagraph();
                    p2.setAlignment(ParagraphAlignment.LEFT);

                    // set font
                    XWPFRun r2 = p2.createRun();
                    r2.setText(title + ": " + description);

                    // save it to .docx file
                    try (FileOutputStream out = new FileOutputStream(folder + fileName)) {
                        doc.write(out);
                        logger.info("file saved to path: " + folder + fileName);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
                return jsonHelper.toJson(resource);
            }
            else {
                logger.info("file id NOT class NatalChart");
                return null;
            }
        });


    }


}
