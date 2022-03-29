package ru.neoflex.emf.bazi;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ooxml.POIXMLDocument;
import org.apache.poi.xwpf.usermodel.*;
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
import ru.neoflex.emf.bazi.natalChart.*;
import ru.neoflex.emf.bazi.natalChart.impl.InputParamsImpl;
import ru.neoflex.emf.bazi.natalChart.impl.NatalChartImpl;
import ru.neoflex.emf.drools.DroolsSvc;
import ru.neoflex.emf.restserver.DBServerSvc;
import ru.neoflex.emf.restserver.JsonHelper;
import ru.neoflex.emf.timezonedb.TimezoneDBSvc;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
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
                eObjects.getContents().removeIf(eObject -> !(eObject instanceof NatalChart || eObject instanceof Conclusions));
                eObjects.save(null);
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

                String templatePath = System.getProperty("user.dir") + "\\bazi\\src\\main\\resources\\template.docx";
                try {
                    XWPFDocument doc = new XWPFDocument(POIXMLDocument.openPackage(templatePath));

                    // get user name from NatalChart
                    String name = ((InputParamsImpl) ((NatalChartImpl) resource.getContents().get(0)).getInputParams()).getName();

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

                    // get conclusions from NatalChart
                    Conclusions conclusions = ((NatalChartImpl) resource.getContents().get(0)).getConclusions();
                    for (Result res: conclusions.getResults()) {
                        // create a paragraph
                        XWPFParagraph p2 = doc.createParagraph();
                        p2.setAlignment(ParagraphAlignment.LEFT);
                        p2.setBorderBetween(Borders.SINGLE);

                        // set font
                        XWPFRun r2 = p2.createRun();
                        r2.setText(res.getTitle());
                        r2.setFontSize(13);
                        r2.addBreak();

                        if (res.getDescription().contains("•")) {
                            for (String description: res.getDescription().split("•")) {
                                XWPFRun r3 = p2.createRun();
                                r3.addBreak();
                                r3.setText("• " + description);
                                r3.setFontSize(9);
                                r3.addBreak();
                            }
                        }
                        else {
                            XWPFRun r3 = p2.createRun();
                            r3.addBreak();
                            r3.setText(res.getDescription());
                            r3.setFontSize(9);
                            r3.addBreak();
                        }

                    }

                    try (FileOutputStream out = new FileOutputStream(folder + fileName)) {
                        doc.write(out);
                        out.close();
                        logger.info("file saved to path: " + folder + fileName);
                    }
                }
                catch(FileNotFoundException e){
                    e.printStackTrace();
                }
                catch(IOException e){
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
