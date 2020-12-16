package ru.neoflex.emf.bazi;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.hql.internal.ast.util.TokenPrinters;
import org.kie.api.io.Resource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.neoflex.emf.base.HbServer;
import ru.neoflex.emf.bazi.calendar.Calendar;
import ru.neoflex.emf.bazi.calendar.CalendarFactory;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

@Service
public class BaZiSvc {

    private static final Logger logger = Logger.getLogger(BaZiSvc.class.getName());

    @Autowired
    DBServerSvc dbServerSvc;

    @PostConstruct
    void init() {
    }

    public static Resource createCalendar(String path) throws IOException {
        String dir = System.getProperty("user.dir") + path;
        logger.info(dir);
        FileInputStream fileInputStream = new FileInputStream(dir);
        Workbook wb = new XSSFWorkbook(fileInputStream);
        String result = wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue();
        logger.info(result);
        fileInputStream.close();

        Calendar calendar = CalendarFactory.eINSTANCE.createCalendar();

        Resource resource = (Resource) calendar.eResource();
        return resource;
    }

}
