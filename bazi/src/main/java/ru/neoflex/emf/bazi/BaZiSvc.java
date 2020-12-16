package ru.neoflex.emf.bazi;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.neoflex.emf.bazi.calendar.Calendar;
import ru.neoflex.emf.bazi.calendar.CalendarFactory;
import ru.neoflex.emf.bazi.calendar.Year;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.util.logging.Logger;

@Service
public class BaZiSvc {

    private static final Logger logger = Logger.getLogger(BaZiSvc.class.getName());

    @Autowired
    DBServerSvc dbServerSvc;

    @PostConstruct
    void init() {
    }

    public static void createCalendar(String path, DBServerSvc dbServerSvc) throws Exception {
        String dir = System.getProperty("user.dir") + path;
        logger.info(dir);
        FileInputStream fileInputStream = new FileInputStream(dir);
        Workbook wb = new XSSFWorkbook(fileInputStream);
        String result = wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue();
        logger.info(result);
        fileInputStream.close();


        dbServerSvc.getDbServer().inTransaction(false, tx -> {
            Calendar calendar = CalendarFactory.eINSTANCE.createCalendar();
            Year year = CalendarFactory.eINSTANCE.createYear();
            year.setName(2020);
            calendar.getYear().add(year);
            org.eclipse.emf.ecore.resource.Resource eObjects = tx.createResource();
            eObjects.getContents().add(calendar);
            eObjects.save(null);
            return DBServerSvc.createJsonHelper().toJson(eObjects);
        });

    }

}
