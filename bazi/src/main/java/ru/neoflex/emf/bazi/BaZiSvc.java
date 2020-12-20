package ru.neoflex.emf.bazi;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.springframework.stereotype.Service;
import ru.neoflex.emf.bazi.calendar.Calendar;
import ru.neoflex.emf.bazi.calendar.CalendarFactory;
import ru.neoflex.emf.bazi.calendar.CalendarPackage;
import ru.neoflex.emf.bazi.calendar.Year;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.util.logging.Logger;

@Service
public class BaZiSvc {
    private static final Logger logger = Logger.getLogger(BaZiSvc.class.getName());

    @PostConstruct
    void init() {
    }

    public static void createCalendar(String path, DBServerSvc dbServerSvc) throws Exception {



        dbServerSvc.getDbServer().inTransaction(false, tx -> {
            Calendar calendar = CalendarFactory.eINSTANCE.createCalendar();


            String dir = System.getProperty("user.dir") + path;
            FileInputStream fileInputStream = new FileInputStream(dir);
            Workbook wb = new XSSFWorkbook(fileInputStream);


            Integer cell = Integer.valueOf(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            ResourceSet rs = tx.getResourceSet();
            Resource resource = dbServerSvc.getDbServer().findBy(rs, CalendarPackage.Literals.YEAR, cell.toString());
            if (resource.getContents().size() > 0) {
                Year year = CalendarFactory.eINSTANCE.createYear();
                year.setName(cell);
                calendar.getYear().add(year);
            }

            fileInputStream.close();




            org.eclipse.emf.ecore.resource.Resource eObjects = tx.createResource();
            eObjects.getContents().add(calendar);
            eObjects.save(null);
            return DBServerSvc.createJsonHelper().toJson(eObjects);
        });

    }

}
